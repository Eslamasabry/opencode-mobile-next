import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/builtin/phone_server_healing.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Store extends ProfileStore {
  _Store({
    required super.prefs,
    required this.saved,
    required this.savedActiveId,
  });

  final List<ServerProfile> saved;
  String? savedActiveId;
  final updates = ChangeNotifier();

  @override
  List<ServerProfile> get profiles => saved;

  @override
  String? get activeId => savedActiveId;

  @override
  Listenable get changes => updates;
}

class _Connection extends ConnectionController {
  _Connection(super.store) : super(isIsolated: true);

  final blocked = <String>{};
  final connections = <String>[];
  final acts = <({String profileId, AutomaticActKind kind, String eventId})>[];

  @override
  bool isProfileReadable(String id) =>
      !blocked.contains(id) &&
      store.profiles.any((profile) => profile.id == id);

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connections.add(profile.id);
    // A busy server right after boot: the connect times out once.
    if (refuseConnects > 0) {
      refuseConnects--;
      lastError = 'Cannot reach the server: receive timeout';
    } else {
      lastError = null;
    }
  }

  int refuseConnects = 0;

  @override
  Future<bool> recordServerAct({
    required String profileId,
    required AutomaticActKind kind,
    required String eventId,
    required DateTime at,
  }) async {
    if (!isProfileReadable(profileId)) return false;
    if (!acts.any((act) => act.eventId == eventId)) {
      acts.add((profileId: profileId, kind: kind, eventId: eventId));
    }
    return true;
  }
}

class _Linux extends BuiltinLinux {
  bool running = false;
  bool healthy = true;
  bool wanted = true;
  bool dies = false;
  int generation = 0;
  int restarts = 0;
  int starts = 0;
  Completer<void>? startGate;
  final startEntered = Completer<void>();
  bool idleSupported = false;
  bool idleValid = true;
  bool idleStopped = false;
  bool helperStopped = false;
  int idleGeneration = 0;
  int idleResumes = 0;
  int idleCompletes = 0;
  Completer<void>? idleGate;
  final idleEntered = Completer<void>();
  final observations = <({String profileId, bool? busy})>[];

  @override
  Future<void> observePhoneAgentWork({
    required String profileId,
    required bool? busy,
  }) async {
    observations.add((profileId: profileId, busy: busy));
  }

  @override
  Future<BuiltinLinuxStatus> resumeIdleStoppedPhoneServer({
    required String profileId,
    required int expectedIdleGeneration,
  }) async {
    idleResumes++;
    if (!idleEntered.isCompleted) idleEntered.complete();
    await idleGate?.future;
    if (!wanted ||
        profileId != 'phone' ||
        idleGeneration != expectedIdleGeneration) {
      throw const BuiltinLinuxException('Unavailable');
    }
    running = true;
    idleStopped = false;
    return status();
  }

  @override
  Future<BuiltinLinuxStatus> completePhoneServerIdleResume({
    required String profileId,
    required int expectedIdleGeneration,
  }) async {
    if (!wanted ||
        profileId != 'phone' ||
        idleGeneration != expectedIdleGeneration) {
      throw const BuiltinLinuxException('Unavailable');
    }
    idleCompletes++;
    if (helperStopped) idleGeneration = 0;
    helperStopped = false;
    return status();
  }

  /// The tracked process's age as native code reports it.
  Duration? uptime;

  /// The running process rejects our password.
  bool rejects = false;

  /// A fresh start answers (false: it runs but stays silent).
  bool answersAfterStart = true;

  final agentHolds = <({String profileId, String leaseId, bool on})>[];
  @override
  Future<BuiltinWorkLeaseStatus> setPhoneAgentChatWorkLease({
    required String profileId,
    required String leaseId,
    required bool on,
    Duration hold = const Duration(minutes: 15),
  }) async {
    agentHolds.add((profileId: profileId, leaseId: leaseId, on: on));
    return BuiltinWorkLeaseStatus(held: on);
  }

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: running,
    serverRestartWanted: wanted,
    serverRecoveryGeneration: generation,
    serverUptime: running ? uptime : null,
    serverIdlePolicySupported: idleSupported,
    serverIdleEnabled: idleSupported,
    serverIdleMinutes: idleSupported ? 5 : null,
    serverIdleStopped: idleStopped,
    serverIdleHelperStopped: helperStopped,
    serverIdleGeneration: idleSupported ? idleGeneration : null,
    serverIdleReceiptValid: idleValid,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');

  @override
  Future<void> restartServer(
    String script, {
    int port = 4097,
    required int expectedGeneration,
  }) async {
    if (!wanted || generation != expectedGeneration) {
      throw const BuiltinLinuxException('The server cannot restart.');
    }
    restarts++;
    running = !dies;
  }

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    starts++;
    if (!startEntered.isCompleted) startEntered.complete();
    await startGate?.future;
    wanted = true;
    running = !dies;
    healthy = answersAfterStart;
    rejects = false;
    uptime = Duration.zero;
  }

  @override
  Future<void> confirmServerRecovery({required int expectedGeneration}) async {
    if (!wanted || !running || expectedGeneration != generation) {
      throw const BuiltinLinuxException('The restart is not confirmed.');
    }
  }

  @override
  Future<void> cancelServerRecovery() async => generation++;
}

class _NativeHealingLinux extends _Linux {
  _NativeHealingLinux({this.attempts = 3, this.scheduled = false});
  int attempts;
  bool scheduled;
  int resets = 0;

  Map<String, Object?> get budget => {
    'version': 1,
    'attempts': attempts,
    'pending': false,
    'revision': 0,
    'eventId': null,
    'recoveryGeneration': null,
    'confirmedAt': null,
  };

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: running,
    serverRestartWanted: wanted,
    serverRecoveryGeneration: generation,
    serverRecoveryAuthority: true,
    serverRecoveryScheduled: scheduled,
  );

  @override
  Future<Map<Object?, Object?>> stageServerRecovery(
    String profileId,
    Map<String, Object?> legacyBudget,
  ) async => budget;
  @override
  Future<Map<Object?, Object?>> bindServerRecovery({
    required String profileId,
    required bool enabled,
    Map<String, Object?>? legacyBudget,
  }) async => budget;
  @override
  Future<List<Map<Object?, Object?>>> serverRecoveryReceipts(
    String profileId,
  ) async => [];
  @override
  Future<Map<Object?, Object?>> serverRecoveryBudget(String profileId) async =>
      budget;
  @override
  Future<Map<Object?, Object?>> confirmManualServerStart(
    String profileId,
  ) async {
    resets++;
    attempts = 0;
    return budget;
  }

  @override
  Future<void> unbindServerRecovery(
    String profileId, {
    bool delete = false,
  }) async {}
}

class _DelayedNativeBindingLinux extends _NativeHealingLinux {
  final bindingGate = Completer<void>();
  final bindingRequested = Completer<void>();
  String? boundOwner;
  bool restorationArmedOnStart = false;

  @override
  Future<Map<Object?, Object?>> bindServerRecovery({
    required String profileId,
    required bool enabled,
    Map<String, Object?>? legacyBudget,
  }) async {
    if (!bindingRequested.isCompleted) bindingRequested.complete();
    await bindingGate.future;
    boundOwner = enabled ? profileId : null;
    return budget;
  }

  @override
  Future<void> unbindServerRecovery(
    String profileId, {
    bool delete = false,
  }) async {
    if (boundOwner == profileId) boundOwner = null;
  }

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    restorationArmedOnStart = boundOwner == restoreRecipe?.profileId;
    await super.startServer(script, port: port, restoreRecipe: restoreRecipe);
  }
}

ServerProfile _phone(String id) => ServerProfile(
  id: id,
  name: 'This phone',
  baseUrl: BuiltinLinux.serverUrl,
  username: BuiltinLinux.serverUsername,
)..password = 'synthetic-password';

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Store store;
  late _Connection connection;
  late _Linux linux;
  late BuiltinServerStarter starter;
  PhoneServerHealing? healing;
  late ServerProfile phone;

  setUp(() async {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    AutomationPolicyController.resetShared();
    phone = _phone('phone');
    store = _Store(prefs: prefs, saved: [phone], savedActiveId: phone.id);
    connection = _Connection(store);
    linux = _Linux();
    starter = BuiltinServerStarter(
      linux: linux,
      readyTimeout: Duration.zero,
      pollInterval: Duration.zero,
    );
    serverProbe = ({required baseUrl, username, password}) async {
      if (linux.running && linux.rejects) {
        return const ServerProbeResult.failure('Rejected', needsPassword: true);
      }
      return linux.running && linux.healthy
          ? const ServerProbeResult.success('1')
          : const ServerProbeResult.failure('Unavailable');
    };
  });

  tearDown(() {
    healing?.dispose();
    healing = null;
    starter.dispose();
    connection.dispose();
    store.updates.dispose();
    AutomationPolicyController.resetShared();
    debugPlatformCapabilities = null;
    serverProbe = probeServerConnection;
  });

  PhoneServerHealing bind({
    bool? Function(String)? localAgentWorkBusy,
    Future<void> Function({
      required String profileId,
      required int expectedIdleGeneration,
    })?
    resumeAgentsAfterIdle,
  }) => healing = PhoneServerHealing(
    connection: connection,
    starter: starter,
    localAgentWorkBusy: localAgentWorkBusy,
    resumeAgentsAfterIdle: resumeAgentsAfterIdle,
    createRecovery: (record) => BuiltinServerRecovery(
      store: store,
      linux: linux,
      starter: starter,
      onRestart: record,
    ),
  );

  Future<void> idleReceipt({bool helper = true}) async {
    linux.idleSupported = true;
    linux.idleStopped = true;
    linux.helperStopped = helper;
    linux.idleGeneration = 9;
    await prefs.setString(
      BuiltinServerRecovery.keyFor(phone.id),
      jsonEncode({'version': 1, 'attempts': 3, 'pending': false}),
    );
    store.savedActiveId = phone.id;
  }

  test(
    'foreground idle resume awaits helper and preserves exhausted budget',
    () async {
      await idleReceipt();
      final helpers = <int>[];
      final gate = Completer<void>();
      final owner = bind(
        resumeAgentsAfterIdle:
            ({required profileId, required expectedIdleGeneration}) async {
              expect(profileId, phone.id);
              expect(linux.running, isTrue);
              helpers.add(expectedIdleGeneration);
              await gate.future;
            },
      )..setForeground(true);
      final launch = owner.startForLaunch(phone);
      await Future<void>.delayed(Duration.zero);
      expect(helpers, [9]);
      expect(linux.idleCompletes, 0);
      expect(linux.starts, 0);
      expect(linux.restarts, 0);
      gate.complete();
      await launch;
      expect(linux.idleResumes, 1);
      expect(linux.idleCompletes, 1);
      expect(linux.starts, 0);
      expect(linux.restarts, 0);
      expect(starter.manualReadyCount, 0);
      expect(
        jsonDecode(
          prefs.getString(BuiltinServerRecovery.keyFor(phone.id))!,
        )['attempts'],
        3,
      );
    },
  );

  test(
    'idle prior helper absent never calls BA and retains generation',
    () async {
      await idleReceipt(helper: false);
      var helpers = 0;
      final owner = bind(
        resumeAgentsAfterIdle:
            ({required profileId, required expectedIdleGeneration}) async {
              helpers++;
            },
      )..setForeground(true);
      await owner.startForLaunch(phone);
      expect(helpers, 0);
      expect(linux.idleCompletes, 1);
      expect(linux.idleGeneration, 9);
      expect(linux.starts, 0);
    },
  );

  test(
    'completed idle generation cannot refill budget through stale launch',
    () async {
      await idleReceipt(helper: false);
      linux.idleStopped = false;
      linux.running = true;
      linux.healthy = false;
      linux.uptime = const Duration(minutes: 5);
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.starts, 0);
      expect(linux.restarts, 0);
      expect(starter.manualReadyCount, 0);
      expect(
        jsonDecode(
          prefs.getString(BuiltinServerRecovery.keyFor(phone.id))!,
        )['attempts'],
        3,
      );
    },
  );

  for (final interruption in [
    'pause',
    'pause and resume',
    'deletion',
    'transfer',
  ]) {
    test(
      'legacy launch cannot retry or connect after $interruption during its own claim',
      () async {
        await prefs.setString(
          BuiltinServerRecovery.keyFor(phone.id),
          jsonEncode({'version': 1, 'attempts': 3, 'pending': false}),
        );
        linux.dies = true;
        linux.startGate = Completer<void>();
        final owner = bind()..setForeground(true);
        final launching = owner.startForLaunch(phone);
        await linux.startEntered.future;
        switch (interruption) {
          case 'pause':
            owner.setForeground(false);
          case 'pause and resume':
            owner.setForeground(false);
            owner.setForeground(true);
          case 'deletion':
            store.saved.clear();
            store.updates.notifyListeners();
          case 'transfer':
            final other = _phone('other');
            store.saved.add(other);
            await AutomationPolicyController.forProfile(
              prefs,
              other.id,
            ).setBehavior(AutomationBehavior.restartPhoneServer, false);
            await prefs.setString(PhoneServerHealing.ownerKey, other.id);
            store.updates.notifyListeners();
        }
        linux.startGate!.complete();
        await launching;
        expect(linux.starts, 1);
        expect(connection.connections, isEmpty);
      },
    );
  }

  test('malformed native idle receipt cannot reach launch fallback', () async {
    await idleReceipt();
    linux.idleValid = false;
    final owner = bind()..setForeground(true);
    await owner.startForLaunch(phone);
    expect(owner.idleResumeFailure, 'idle_resume_unavailable');
    expect(linux.idleResumes, 0);
    expect(linux.starts, 0);
    expect(linux.restarts, 0);
  });

  test(
    'failed helper restore keeps idle generation and forbids manual fallback',
    () async {
      await idleReceipt();
      final owner = bind(
        resumeAgentsAfterIdle:
            ({required profileId, required expectedIdleGeneration}) async {
              throw StateError('synthetic private details');
            },
      )..setForeground(true);
      await owner.startForLaunch(phone);
      expect(owner.idleResumeFailure, 'idle_resume_unavailable');
      expect(linux.helperStopped, isTrue);
      expect(linux.idleCompletes, 0);
      expect(linux.starts, 0);
      expect(linux.restarts, 0);
    },
  );

  for (final interruption in [
    'pause',
    'deletion',
    'transfer',
    'dispose',
    'stop',
  ]) {
    test(
      'idle server completion cannot restore helper after $interruption',
      () async {
        await idleReceipt();
        linux.idleGate = Completer<void>();
        var helpers = 0;
        final owner = bind(
          resumeAgentsAfterIdle:
              ({required profileId, required expectedIdleGeneration}) async {
                helpers++;
              },
        )..setForeground(true);
        final checking = owner.check(phone);
        await linux.idleEntered.future;
        switch (interruption) {
          case 'pause':
            owner.setForeground(false);
          case 'deletion':
            store.saved.clear();
            store.updates.notifyListeners();
          case 'transfer':
            final second = _phone('second');
            store.saved.add(second);
            await prefs.setString(PhoneServerHealing.ownerKey, second.id);
            store.updates.notifyListeners();
          case 'dispose':
            owner.dispose();
            healing = null;
          case 'stop':
            linux.wanted = false;
        }
        linux.idleGate!.complete();
        await checking;
        expect(helpers, 0);
        expect(linux.idleCompletes, 0);
        expect(linux.starts, 0);
        expect(linux.restarts, 0);
      },
    );
  }

  test(
    'local agent truth owns a separate lease across background and unknown',
    () async {
      await prefs.setString(
        'oc.phoneAgentOwner.${phone.id}',
        'shared_agent_owner',
      );
      bool? busy = true;
      final queried = <String>[];
      final owner = bind(
        localAgentWorkBusy: (profileId) {
          queried.add(profileId);
          return busy;
        },
      );
      await Future<void>.delayed(Duration.zero);
      expect(queried.toSet(), {phone.id});
      expect(linux.agentHolds, hasLength(1));
      expect(linux.agentHolds.single.profileId, 'shared_agent_owner');
      final leaseId = linux.agentHolds.single.leaseId;
      owner.setForeground(false);
      busy = null;
      store.updates.notifyListeners();
      await Future<void>.delayed(Duration.zero);
      expect(linux.agentHolds.where((call) => !call.on), isEmpty);
      busy = false;
      store.updates.notifyListeners();
      await Future<void>.delayed(Duration.zero);
      expect(linux.agentHolds.last, (
        profileId: 'shared_agent_owner',
        leaseId: leaseId,
        on: false,
      ));
      expect(linux.starts, 0);
      expect(linux.restarts, 0);
    },
  );

  test(
    'agent lease closes when its runtime profile becomes unreadable',
    () async {
      bind(localAgentWorkBusy: (_) => true);
      await Future<void>.delayed(Duration.zero);
      final lease = linux.agentHolds.single;
      connection.blocked.add(phone.id);
      store.updates.notifyListeners();
      await Future<void>.delayed(Duration.zero);
      expect(linux.agentHolds.last, (
        profileId: lease.profileId,
        leaseId: lease.leaseId,
        on: false,
      ));
    },
  );

  test(
    'missing or throwing local agent inventory never creates a CPU lease',
    () async {
      bind(
        localAgentWorkBusy: (_) =>
            throw StateError('synthetic unavailable inventory'),
      );
      await Future<void>.delayed(Duration.zero);
      expect(linux.agentHolds, isEmpty);
    },
  );

  test(
    'app-lifetime owner heals only in foreground and reconnects once',
    () async {
      final owner = bind();
      await owner.check(phone);
      expect(linux.restarts, 0);
      expect(connection.acts, isEmpty);

      owner.setForeground(true);
      await owner.check(phone);
      expect(linux.restarts, 1);
      expect(connection.acts, hasLength(1));
      expect(connection.acts.single.kind, AutomaticActKind.restart);
      expect(connection.connections, [phone.id]);
      await owner.check(phone);
      await owner.connectIfNeeded(phone);
      expect(connection.acts, hasLength(1));
      expect(connection.connections, [phone.id]);

      owner.setForeground(false);
      linux.running = false;
      await owner.check(phone);
      expect(linux.restarts, 1);
    },
  );

  test(
    'persisted runtime owner wins over another selected local profile',
    () async {
      final other = _phone('other');
      store.saved.add(other);
      store.savedActiveId = other.id;
      await prefs.setString(PhoneServerHealing.ownerKey, phone.id);
      final owner = bind()..setForeground(true);
      await owner.check(phone);
      await owner.check(other);
      expect(linux.restarts, 1);
      expect(connection.acts.single.profileId, phone.id);
      expect(connection.connections, isEmpty);
      expect(
        prefs.containsKey(BuiltinServerRecovery.keyFor(other.id)),
        isFalse,
      );
    },
  );

  test(
    'a restart confirmed after its initial timeout still reconnects',
    () async {
      linux.healthy = false;
      final owner = bind()..setForeground(true);
      await owner.check(phone);
      expect(linux.restarts, 1);
      expect(starter.failureFor(phone), isNotNull);
      expect(connection.acts, isEmpty);
      expect(connection.connections, isEmpty);

      linux.healthy = true;
      await owner.check(phone);
      expect(linux.restarts, 1);
      expect(starter.failureFor(phone), isNull);
      expect(connection.acts, hasLength(1));
      expect(connection.connections, [phone.id]);
    },
  );

  test(
    'explicit Start waits for native owner binding before its launch',
    () async {
      final native = _DelayedNativeBindingLinux()..wanted = false;
      linux = native;
      starter.dispose();
      starter = BuiltinServerStarter(
        linux: native,
        readyTimeout: Duration.zero,
        pollInterval: Duration.zero,
      );
      bind().setForeground(true);
      final starting = starter.start(phone);
      await native.bindingRequested.future;
      try {
        expect(native.starts, 0);
      } finally {
        native.bindingGate.complete();
        await starting;
      }
      expect(native.restorationArmedOnStart, isTrue);
      expect(await starting, isNull);
    },
  );

  for (final transfer in [false, true]) {
    test(
      'late native binding cannot launch after ${transfer ? 'owner transfer' : 'profile deletion'}',
      () async {
        final native = _DelayedNativeBindingLinux()..wanted = false;
        linux = native;
        starter.dispose();
        starter = BuiltinServerStarter(
          linux: native,
          readyTimeout: Duration.zero,
          pollInterval: Duration.zero,
        );
        bind().setForeground(true);
        final starting = starter.start(phone);
        await native.bindingRequested.future;
        if (transfer) {
          final other = _phone('other');
          store.saved.add(other);
          await prefs.setString(PhoneServerHealing.ownerKey, other.id);
          store.updates.notifyListeners();
        } else {
          connection.blocked.add(phone.id);
        }
        native.bindingGate.complete();
        expect(await starting, isNotNull);
        expect(native.starts, 0);
      },
    );
  }

  test(
    'ambiguous legacy profiles wait for an explicit Start to claim ownership',
    () async {
      final other = _phone('other');
      store.saved.add(other);
      final owner = bind()..setForeground(true);
      await owner.check(phone);
      await owner.check(other);
      expect(linux.restarts, 0);
      expect(connection.acts, isEmpty);

      expect(await starter.start(other), isNull);
      expect(prefs.getString(PhoneServerHealing.ownerKey), other.id);
      await owner.check(other);
      expect(connection.acts, isEmpty);
      linux.running = false;
      await owner.check(other);
      expect(linux.restarts, 1);
      expect(connection.acts.single.profileId, other.id);
    },
  );

  test(
    'the shared restart policy and explicit Stop each prevent healing',
    () async {
      final policy = AutomationPolicyController.forProfile(prefs, phone.id);
      await policy.setBehavior(AutomationBehavior.restartPhoneServer, false);
      final owner = bind()..setForeground(true);
      await owner.check(phone);
      expect(linux.restarts, 0);

      linux.wanted = false;
      await policy.setBehavior(AutomationBehavior.restartPhoneServer, true);
      await owner.check(phone);
      expect(linux.restarts, 0);
      expect(connection.acts, isEmpty);
    },
  );

  test(
    'explicit connect works with automatic reconnect off and shares deduplication',
    () async {
      final policy = AutomationPolicyController.forProfile(prefs, phone.id);
      await policy.setBehavior(AutomationBehavior.reconnect, false);
      linux.wanted = false;
      final owner = bind()..setForeground(true);
      await owner.check(phone);

      await owner.connectIfNeeded(phone);
      expect(connection.connections, isEmpty);
      await owner.connectIfNeeded(phone, automatic: false);
      expect(connection.connections, [phone.id]);

      await policy.setBehavior(AutomationBehavior.reconnect, true);
      await owner.connectIfNeeded(phone);
      await owner.connectIfNeeded(phone, automatic: false);
      expect(connection.connections, [phone.id]);
      expect(linux.restarts, 0);
    },
  );

  test(
    'profile deletion admission cannot be reopened by a store notification',
    () async {
      connection.blocked.add(phone.id);
      final owner = bind()..setForeground(true);
      store.updates.notifyListeners();
      await owner.check(phone);
      expect(linux.restarts, 0);
      expect(connection.acts, isEmpty);
      expect(
        prefs.containsKey(BuiltinServerRecovery.keyFor(phone.id)),
        isFalse,
      );

      connection.blocked.clear();
      connection.notifyListeners();
      await owner.check(phone);
      expect(linux.restarts, 1);
      expect(connection.acts, hasLength(1));
    },
  );

  test(
    'native exhausted budget survives opening and owner recreation',
    () async {
      starter.dispose();
      final native = _NativeHealingLinux();
      linux = native;
      starter = BuiltinServerStarter(
        linux: linux,
        readyTimeout: Duration.zero,
        pollInterval: Duration.zero,
      );
      var owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(native.starts, 0);
      expect(native.restarts, 0);
      expect(native.attempts, 3);
      expect(native.resets, 0);
      expect(owner.recovery.value.phase, BuiltinRecoveryPhase.exhausted);
      owner.dispose();
      healing = null;
      owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(native.starts, 0);
      expect(native.restarts, 0);
      expect(native.attempts, 3);
      expect(native.resets, 0);
    },
  );

  test('opening cannot supersede a scheduled native retry', () async {
    starter.dispose();
    final native = _NativeHealingLinux(attempts: 1, scheduled: true);
    linux = native;
    starter = BuiltinServerStarter(
      linux: linux,
      readyTimeout: Duration.zero,
      pollInterval: Duration.zero,
    );
    final owner = bind()..setForeground(true);
    await owner.startForLaunch(phone);
    expect(native.starts, 0);
    expect(native.restarts, 0);
    expect(native.attempts, 1);
    expect(native.resets, 0);
    expect(owner.recovery.value.phase, BuiltinRecoveryPhase.waiting);
  });

  group('the launch start (QA B1)', () {
    Future<void> exhaust() => prefs.setString(
      BuiltinServerRecovery.keyFor(phone.id),
      jsonEncode({'version': 1, 'attempts': 3, 'pending': false}),
    );

    setUp(() => PhoneServerHealing.launchRetryDelay = Duration.zero);
    tearDown(
      () => PhoneServerHealing.launchRetryDelay = const Duration(seconds: 2),
    );

    test('starts a stopped server past an exhausted crash budget, '
        'connects, and gives the budget back once it answers', () async {
      await exhaust();
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.restarts, 0, reason: 'the crash budget stays spent');
      expect(linux.starts, 1);
      expect(linux.running, isTrue);
      expect(connection.connections, [phone.id]);
      await owner.check(phone);
      expect(owner.recovery.value.attempts, 0);
    });

    test('waits for the first resume before it does anything', () async {
      await exhaust();
      final owner = bind();
      var done = false;
      final launching = owner.startForLaunch(phone).then((_) => done = true);
      await Future<void>.delayed(Duration.zero);
      expect(done, isFalse);
      expect(linux.starts, 0);
      owner.setForeground(true);
      await launching;
      expect(linux.starts, 1);
      expect(connection.connections, [phone.id]);
    });

    test('leaves an explicitly stopped server stopped', () async {
      await exhaust();
      linux.wanted = false;
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.starts + linux.restarts, 0);
    });

    test('leaves it stopped when restarting the phone server is off', () async {
      await exhaust();
      await AutomationPolicyController.forProfile(
        prefs,
        phone.id,
      ).setBehavior(AutomationBehavior.restartPhoneServer, false);
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.starts + linux.restarts, 0);
    });

    test('only connects to a server that already runs', () async {
      linux.running = true;
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.starts + linux.restarts, 0);
      expect(connection.connections, [phone.id]);
    });

    test(
      'tries once more after a fast failure, then leaves the reason',
      () async {
        await exhaust();
        linux.dies = true;
        final owner = bind()..setForeground(true);
        await owner.startForLaunch(phone);
        expect(linux.starts, 2);
        expect(starter.failureFor(phone)?.problem, BuiltinStartProblem.exited);
        expect(connection.connections, isEmpty);
        // Once per app process: a later Try again does not start it again.
        await owner.startForLaunch(phone);
        expect(linux.starts, 2);
      },
    );

    test('a timeout is not tried again on its own', () async {
      await exhaust();
      linux.answersAfterStart = false;
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.starts, 1);
      expect(starter.failureFor(phone)?.problem, BuiltinStartProblem.timedOut);
    });

    test('a failed crash restart counts as the first try', () async {
      linux.dies = true;
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.restarts + linux.starts, 2);
    });
  });

  group('a running server that does not answer (QA-03)', () {
    setUp(() {
      PhoneServerHealing.launchPollInterval = Duration.zero;
      PhoneServerHealing.reconnectBackoff = Duration.zero;
    });
    tearDown(() {
      PhoneServerHealing.launchPollInterval = const Duration(seconds: 2);
      PhoneServerHealing.reconnectBackoff = const Duration(seconds: 10);
      PhoneServerHealing.staleAfter = const Duration(seconds: 90);
    });

    test('one that rejects our password is replaced at launch', () async {
      linux
        ..running = true
        ..rejects = true
        ..uptime = const Duration(seconds: 5);
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      expect(linux.starts, 1);
      expect(connection.connections, [phone.id]);
    });

    test(
      'one older than the stale age that stays silent is replaced',
      () async {
        linux
          ..running = true
          ..healthy = false
          ..uptime = const Duration(minutes: 5);
        final owner = bind()..setForeground(true);
        await owner.startForLaunch(phone);
        expect(linux.starts, 1);
        expect(connection.connections, [phone.id]);
      },
    );

    test(
      'a young one that is still booting is waited for, not killed',
      () async {
        PhoneServerHealing.staleAfter = const Duration(minutes: 10);
        linux
          ..running = true
          ..healthy = false
          ..uptime = const Duration(seconds: 3);
        final owner = bind()..setForeground(true);
        var polls = 0;
        serverProbe = ({required baseUrl, username, password}) async {
          // It starts answering on the third question.
          if (++polls >= 3) linux.healthy = true;
          return linux.healthy
              ? const ServerProbeResult.success('1')
              : const ServerProbeResult.failure('Unavailable');
        };
        await owner.startForLaunch(phone);
        expect(linux.starts + linux.restarts, 0);
        expect(connection.connections, [phone.id]);
      },
    );

    test(
      'a young one that never answers is replaced once it is stale',
      () async {
        PhoneServerHealing.staleAfter = const Duration(milliseconds: 30);
        linux
          ..running = true
          ..healthy = false
          ..uptime = Duration.zero;
        final owner = bind()..setForeground(true);
        await owner.startForLaunch(phone);
        expect(linux.starts, 1);
      },
    );

    test('a healthy server whose connect failed is connected again by the '
        'health poll, once per back-off', () async {
      PhoneServerHealing.reconnectBackoff = const Duration(milliseconds: 200);
      linux.running = true;
      connection.refuseConnects = 1;
      final owner = bind()..setForeground(true);
      await owner.startForLaunch(phone);
      await owner.check(phone);
      expect(connection.connections, [phone.id]);
      expect(linux.starts + linux.restarts, 0);

      await Future<void>.delayed(const Duration(milliseconds: 250));
      await owner.check(phone);
      expect(connection.connections, [phone.id, phone.id]);
      // Connected now: the next health poll does not connect again.
      await owner.check(phone);
      expect(connection.connections, [phone.id, phone.id]);
    });

    test(
      'the back-off holds automatic reconnects; Try again does not wait',
      () async {
        PhoneServerHealing.reconnectBackoff = const Duration(hours: 1);
        linux.running = true;
        connection.refuseConnects = 5;
        final owner = bind()..setForeground(true);
        await owner.startForLaunch(phone);
        await owner.check(phone);
        await owner.connectIfNeeded(phone);
        expect(connection.connections, hasLength(1));
        await owner.startForLaunch(phone, retry: true);
        expect(connection.connections, hasLength(2));
      },
    );
  });
}
