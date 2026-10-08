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

  /// The tracked process's age as native code reports it.
  Duration? uptime;

  /// The running process rejects our password.
  bool rejects = false;

  /// A fresh start answers (false: it runs but stays silent).
  bool answersAfterStart = true;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: running,
    serverRestartWanted: wanted,
    serverRecoveryGeneration: generation,
    serverUptime: running ? uptime : null,
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

  PhoneServerHealing bind() => healing = PhoneServerHealing(
    connection: connection,
    starter: starter,
    createRecovery: (record) => BuiltinServerRecovery(
      store: store,
      linux: linux,
      starter: starter,
      onRestart: record,
    ),
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
