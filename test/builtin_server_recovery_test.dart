import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/builtin_server_recovery.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.all});
  final List<ServerProfile> all;
  @override
  List<ServerProfile> get profiles => all;
}

class _Linux extends BuiltinLinux {
  BuiltinLinuxStatus? idleStatus;
  bool wanted = true;
  bool running = false;
  bool failStart = false;
  int generation = 1;
  int starts = 0;
  int confirmations = 0;
  bool pending = false;
  Completer<void>? writeGate;
  Completer<void>? writeEntered;

  @override
  Future<BuiltinLinuxStatus> status() async =>
      idleStatus ??
      BuiltinLinuxStatus(
        installed: true,
        phase: BuiltinLinuxPhase.ready,
        serverRunning: running,
        serverRestartWanted: wanted,
        serverRecoveryGeneration: generation,
      );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    writeEntered?.complete();
    await writeGate?.future;
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> restartServer(
    String script, {
    int port = 4097,
    required int expectedGeneration,
  }) async {
    if (!wanted || expectedGeneration != generation) {
      throw const BuiltinLinuxException('The phone server could not restart.');
    }
    starts++;
    running = !failStart;
    pending = true;
  }

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    wanted = true;
    running = true;
  }

  @override
  Future<void> confirmServerRecovery({required int expectedGeneration}) async {
    if (!wanted || expectedGeneration != generation || !running) {
      throw const BuiltinLinuxException('The phone server could not restart.');
    }
    confirmations++;
    pending = false;
  }

  @override
  Future<void> cancelServerRecovery() async {
    generation++;
    if (pending) running = false;
    pending = false;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Store store;
  late _Linux linux;
  late BuiltinServerStarter starter;
  late BuiltinServerRecovery recovery;
  late ServerProfile phone;
  late DateTime now;
  late List<String> acts;
  late bool recordsAllowed;
  late List<DateTime> recordedAt;

  BuiltinServerRecovery makeRecovery() => BuiltinServerRecovery(
    store: store,
    linux: linux,
    starter: starter,
    now: () => now,
    onRestart: ({required profileId, required eventId, required at}) async {
      if (!recordsAllowed) return false;
      if (!acts.contains(eventId)) {
        acts.add(eventId);
        recordedAt.add(at);
      }
      return true;
    },
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    AutomationPolicyController.resetShared();
    phone =
        ServerProfile(
            id: 'phone',
            name: 'This phone',
            baseUrl: BuiltinLinux.serverUrl,
          )
          ..username = 'opencode'
          ..password = 'test-password';
    store = _Store(prefs: prefs, all: [phone]);
    linux = _Linux();
    starter = BuiltinServerStarter(
      linux: linux,
      readyTimeout: Duration.zero,
      pollInterval: Duration.zero,
    );
    now = DateTime.utc(2026, 9, 27);
    acts = [];
    recordsAllowed = true;
    recordedAt = [];
    serverProbe = ({required baseUrl, username, password}) async =>
        linux.running
        ? const ServerProbeResult.success('1')
        : const ServerProbeResult.failure('Unavailable');
    recovery = makeRecovery();
    recovery.setForeground(true);
  });

  tearDown(() {
    recovery.dispose();
    starter.dispose();
    serverProbe = probeServerConnection;
  });

  test(
    'confirmed crash recovery records one act and retains consumed budget',
    () async {
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(linux.confirmations, 1);
      expect(recovery.value.phase, BuiltinRecoveryPhase.ready);
      expect(recovery.value.attempts, 1);
      expect(acts, hasLength(1));
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(acts, hasLength(1));
    },
  );

  test(
    'idle and malformed supporting receipts cannot dispatch or change budget',
    () async {
      const original = '{"version":1,"attempts":2,"pending":false}';
      for (final status in [
        const BuiltinLinuxStatus(
          phase: BuiltinLinuxPhase.ready,
          installed: true,
          serverRestartWanted: true,
          serverRecoveryGeneration: 1,
          serverIdlePolicySupported: true,
        ),
        const BuiltinLinuxStatus(
          phase: BuiltinLinuxPhase.ready,
          installed: true,
          serverRestartWanted: true,
          serverRecoveryGeneration: 1,
          serverIdlePolicySupported: true,
          serverIdleMinutes: 5,
          serverIdleGeneration: 9,
          serverIdleStopped: true,
        ),
        const BuiltinLinuxStatus(
          phase: BuiltinLinuxPhase.ready,
          installed: true,
          serverRestartWanted: true,
          serverRecoveryGeneration: 1,
          serverIdlePolicySupported: true,
          serverIdleMinutes: 5,
          serverIdleGeneration: 9,
          serverRunning: true,
          serverIdleHelperStopped: true,
        ),
        const BuiltinLinuxStatus(
          phase: BuiltinLinuxPhase.ready,
          installed: true,
          serverRestartWanted: true,
          serverRecoveryGeneration: 1,
          serverIdlePolicySupported: true,
          serverIdleMinutes: 5,
          serverIdleGeneration: 9,
        ),
      ]) {
        await prefs.setString(BuiltinServerRecovery.keyFor(phone.id), original);
        linux.idleStatus = status;
        await recovery.check(phone);
        expect(linux.starts, 0);
        expect(linux.confirmations, 0);
        expect(acts, isEmpty);
        expect(recovery.value.phase, BuiltinRecoveryPhase.stopped);
        expect(
          prefs.getString(BuiltinServerRecovery.keyFor(phone.id)),
          original,
        );
      }
    },
  );

  test('explicit stop and background never dispatch a restart', () async {
    linux.wanted = false;
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.stopped);
    linux.wanted = true;
    recovery.setForeground(false);
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.paused);
    expect(linux.starts, 0);
  });

  test('shared restart and poll policy each prevent healing', () async {
    final policy = AutomationPolicyController.forProfile(prefs, phone.id);
    for (final behavior in [
      AutomationBehavior.restartPhoneServer,
      AutomationBehavior.pollRestartHealth,
    ]) {
      await policy.setBehavior(behavior, false);
      await recovery.check(phone);
      expect(linux.starts, 0);
      await policy.setBehavior(behavior, true);
    }
  });

  test('three failures exhaust across resume and owner recreation', () async {
    linux.failStart = true;
    for (var attempt = 1; attempt <= 3; attempt++) {
      await recovery.check(phone);
      expect(linux.starts, attempt);
      await recovery.check(phone);
      if (attempt < 3) {
        expect(recovery.value.phase, BuiltinRecoveryPhase.waiting);
      }
      now = now.add(const Duration(minutes: 1));
    }
    expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
    recovery.setForeground(false);
    recovery.setForeground(true);
    await recovery.check(phone);
    recovery.dispose();
    recovery = makeRecovery()..setForeground(true);
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
    expect(linux.starts, 3);
    expect(acts, isEmpty);
  });

  test('manual confirmed Start resets exhausted budget', () async {
    linux.failStart = true;
    for (var i = 0; i < 3; i++) {
      await recovery.check(phone);
      now = now.add(const Duration(minutes: 1));
    }
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.exhausted);
    linux.failStart = false;
    expect(await starter.start(phone), isNull);
    await recovery.check(phone);
    expect(recovery.value.attempts, 0);
    linux.running = false;
    await recovery.check(phone);
    expect(linux.starts, 4);
    expect(acts, hasLength(1));
  });

  test(
    'policy revoked during password write prevents native dispatch',
    () async {
      linux.writeGate = Completer<void>();
      linux.writeEntered = Completer<void>();
      final checking = recovery.check(phone);
      await linux.writeEntered!.future;
      await AutomationPolicyController.forProfile(
        prefs,
        phone.id,
      ).setBehavior(AutomationBehavior.restartPhoneServer, false);
      linux.writeGate!.complete();
      await checking;
      expect(linux.starts, 0);
      expect(acts, isEmpty);
    },
  );

  test(
    'explicit stop during password write wins native generation race',
    () async {
      linux.writeGate = Completer<void>();
      linux.writeEntered = Completer<void>();
      final checking = recovery.check(phone);
      await linux.writeEntered!.future;
      linux.wanted = false;
      linux.generation++;
      linux.writeGate!.complete();
      await checking;
      expect(linux.starts, 0);
      expect(acts, isEmpty);
    },
  );

  test('background during health confirmation cannot record success', () async {
    serverProbe = ({required baseUrl, username, password}) async {
      recovery.setForeground(false);
      return const ServerProbeResult.success('1');
    };
    await recovery.check(phone);
    expect(acts, isEmpty);
    expect(linux.confirmations, 0);
    expect(recovery.value.phase, BuiltinRecoveryPhase.paused);
  });

  test(
    'unhealthy live process is never overlapped with another attempt',
    () async {
      serverProbe = ({required baseUrl, username, password}) async =>
          const ServerProbeResult.failure('Unavailable');
      await recovery.check(phone);
      expect(linux.starts, 1);
      now = now.add(const Duration(hours: 1));
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(recovery.value.phase, BuiltinRecoveryPhase.unconfirmed);
      expect(acts, isEmpty);
    },
  );

  test(
    'late healthy answer confirms same attempt without another restart',
    () async {
      serverProbe = ({required baseUrl, username, password}) async =>
          const ServerProbeResult.failure('Unavailable');
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(acts, isEmpty);
      expect(starter.failureFor(phone), isNotNull);
      expect(starter.readyCount, 0);
      serverProbe = ({required baseUrl, username, password}) async =>
          const ServerProbeResult.success('1');
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(linux.confirmations, 1);
      expect(acts, hasLength(1));
      expect(starter.failureFor(phone), isNull);
      expect(starter.readyCount, 1);
      await recovery.check(phone);
      expect(starter.readyCount, 1);
      expect(acts, hasLength(1));
      expect(recovery.value.phase, BuiltinRecoveryPhase.ready);
      recovery.setForeground(false);
      await Future<void>.delayed(Duration.zero);
      expect(linux.running, isTrue);
    },
  );

  test(
    'bound phone owner rejects another profile at the same address',
    () async {
      final other = ServerProfile(
        id: 'other',
        name: 'Other runtime',
        baseUrl: BuiltinLinux.serverUrl,
      )..password = 'other-test-password';
      store.all.add(other);
      recovery.setForeground(false);
      recovery.setProfile(phone);
      await recovery.check(other);
      expect(linux.starts, 0);
      expect(recovery.value.profileId, isNot(other.id));
    },
  );

  test('manual owner persistence failure prevents native launch', () async {
    starter.beforeManualStart = (_) async {
      throw StateError('synthetic');
    };
    expect(await starter.start(phone), isNotNull);
    expect(linux.running, isFalse);
    expect(starter.manualReadyCount, 0);
  });

  test(
    'confirmed receipt logs after recreation even when the server stopped',
    () async {
      final confirmedAt = now;
      recordsAllowed = false;
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(recovery.value.phase, BuiltinRecoveryPhase.storageUnavailable);
      expect(acts, isEmpty);
      recovery.dispose();
      await Future<void>.delayed(Duration.zero);
      linux.running = false;
      linux.wanted = false;
      now = now.add(const Duration(minutes: 2));
      recordsAllowed = true;
      recovery = makeRecovery()..setForeground(true);
      await recovery.check(phone);
      expect(linux.starts, 1);
      expect(acts, hasLength(1));
      expect(recordedAt.map((at) => at.toUtc()), [confirmedAt.toUtc()]);
      expect(recovery.value.phase, BuiltinRecoveryPhase.stopped);
      await recovery.check(phone);
      expect(acts, hasLength(1));
    },
  );

  test(
    'failed healing still exposes installed server and safe failure for cards',
    () async {
      linux.failStart = true;
      expect(starter.recognises(phone), isFalse);
      await recovery.check(phone);
      expect(starter.recognises(phone), isTrue);
      expect(starter.failureFor(phone), isNotNull);
      expect(acts, isEmpty);
    },
  );

  test('malformed durable budget fails closed', () async {
    await prefs.setString(BuiltinServerRecovery.keyFor(phone.id), 'invalid');
    await recovery.check(phone);
    expect(recovery.value.phase, BuiltinRecoveryPhase.storageUnavailable);
    expect(linux.starts, 0);
  });

  test(
    'profile deletion during start drains before scoped preference sweep',
    () async {
      linux.writeGate = Completer<void>();
      linux.writeEntered = Completer<void>();
      final checking = recovery.check(phone);
      await linux.writeEntered!.future;
      final deleting = recovery.suspendProfile(phone.id);
      linux.writeGate!.complete();
      await checking;
      await deleting;
      store.all.clear();
      await prefs.remove(BuiltinServerRecovery.keyFor(phone.id));
      await recovery.check(phone);
      expect(linux.starts, 0);
      expect(
        prefs.containsKey(BuiltinServerRecovery.keyFor(phone.id)),
        isFalse,
      );
    },
  );
}
