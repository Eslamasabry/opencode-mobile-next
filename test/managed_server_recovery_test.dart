import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/managed_server_recovery.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FailingPreferences implements SharedPreferences {
  final values = <String, String>{};
  bool fail = false;
  bool failGatedWrite = false;
  Completer<void>? writeGate;
  Completer<void>? writeEntered;
  @override
  String? getString(String key) => values[key];
  @override
  Future<bool> setString(String key, String value) async {
    final gate = writeGate;
    if (gate != null) {
      writeGate = null;
      if (!(writeEntered?.isCompleted ?? true)) writeEntered!.complete();
      await gate.future;
      if (failGatedWrite) {
        failGatedWrite = false;
        throw StateError('synthetic gated disk failure');
      }
    }
    if (fail) throw StateError('synthetic disk failure');
    values[key] = value;
    return true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');
  late SharedPreferences prefs;
  late DateTime now;
  late String snapshot;
  final scripts = <String>[];
  Completer<Map<String, Object>>? pendingProbe;

  Map<String, Object> result(String output) => {
    'exitCode': 0,
    'stdout': output,
    'stderr': '',
  };
  String state(
    String phase, {
    String operation = 'original',
    String failure = '',
  }) =>
      'phase=$phase\nrunner=proot\nport=4096\nversion=1.18.29\npid=12\noperation=$operation\nfailure_kind=$failure\n';

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    now = DateTime.utc(2026, 9, 7);
    snapshot = state('ready');
    scripts.clear();
    pendingProbe = null;
    debugPlatformCapabilities = const PlatformCapabilities.android();
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      final script = (call.arguments as Map)['script'] as String;
      scripts.add(script);
      if (script == TermuxBridge.statusScript()) {
        return pendingProbe?.future ?? result(snapshot);
      }
      if (script.contains('exec "\$MANAGER" recovery-arm')) {
        return result(snapshot);
      }
      if (script.contains('"\$MANAGER" restart')) {
        final operation = RegExp(
          r'''restart '4096' '([^']+)' ''',
        ).firstMatch(script)!.group(1)!;
        snapshot = state('failed', operation: operation, failure: 'recovery');
        return {
          'exitCode': 1,
          'stdout': '',
          'stderr': 'synthetic startup failure',
        };
      }
      return result('');
    });
  });
  tearDown(() {
    ManagedServerRecovery.disposeForPreferences(prefs);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
    debugPlatformCapabilities = null;
  });

  ManagedServerRecovery service([String id = 'local']) =>
      ManagedServerRecovery.forProfile(prefs, id, now: () => now);

  testWidgets('opt out never probes; duplicate profiles share one owner', (
    tester,
  ) async {
    final recovery = service();
    expect(identical(recovery, service('duplicate')), isTrue);
    await tester.pump(const Duration(minutes: 1));
    expect(scripts, isEmpty);
    expect(recovery.enabled, isFalse);
  });

  testWidgets('three attempts are delayed and survive service recreation', (
    tester,
  ) async {
    var recovery = service();
    await recovery.setEnabled(true);
    snapshot = state('failed', failure: 'crash');
    await recovery.checkNow();
    expect(recovery.attempts, 0);
    await recovery.checkNow();
    expect(recovery.attempts, 0);
    for (var attempt = 1; attempt <= 3; attempt++) {
      now = now.add(const Duration(seconds: 60));
      await recovery.checkNow();
      expect(recovery.attempts, attempt);
      expect(recovery.paused, isFalse);
      ManagedServerRecovery.disposeForPreferences(prefs);
      recovery = service();
      expect(recovery.attempts, attempt);
    }
    now = now.add(const Duration(hours: 1));
    await recovery.checkNow();
    expect(recovery.exhausted, isTrue);
    expect(
      scripts.where((s) => s.contains('"\$MANAGER" restart')),
      hasLength(3),
    );
    final saved =
        jsonDecode(
              prefs.getString(ManagedServerRecovery.preferenceKey('local'))!,
            )
            as Map;
    expect(saved['attempts'], 3);
  });

  testWidgets(
    'deletion admission cancels a pending probe before restart dispatch',
    (tester) async {
      final recovery = service();
      await recovery.setEnabled(true);
      snapshot = state('failed', failure: 'crash');
      await recovery.checkNow();
      now = now.add(const Duration(minutes: 1));
      pendingProbe = Completer();
      final checking = recovery.checkNow();
      await tester.pump();
      final disabling = ManagedServerRecovery.disableForProfile(prefs, 'local');
      expect(recovery.enabled, isFalse);
      pendingProbe!.complete(result(snapshot));
      await checking;
      await disabling;
      expect(scripts.where((s) => s.contains('"\$MANAGER" restart')), isEmpty);
      ManagedServerRecovery.disposeForPreferences(prefs);
      expect(service().enabled, isFalse);
    },
  );

  testWidgets('background and changed operation prevent automatic restart', (
    tester,
  ) async {
    final recovery = service();
    await recovery.setEnabled(true);
    snapshot = state('failed', failure: 'crash');
    binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await recovery.checkNow();
    expect(scripts.where((s) => s == TermuxBridge.statusScript()), isEmpty);
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    snapshot = state('failed', failure: 'crash', operation: 'manual-restart');
    await recovery.checkNow();
    expect(recovery.paused, isTrue);
    expect(recovery.error, ManagedRecoveryError.ownershipChanged);
    expect(scripts.where((s) => s.contains('"\$MANAGER" restart')), isEmpty);
  });

  testWidgets(
    'background during a dispatched recovery revokes its permit before return',
    (tester) async {
      final recovery = service();
      await recovery.setEnabled(true);
      snapshot = state('failed', failure: 'crash');
      await recovery.checkNow();
      now = now.add(const Duration(minutes: 1));
      final nativeRestart = Completer<Map<String, Object>>();
      var dispatched = false;
      var revoked = false;
      binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
        call,
      ) async {
        final script = (call.arguments as Map)['script'] as String;
        scripts.add(script);
        if (script == TermuxBridge.statusScript()) return result(snapshot);
        if (script.contains('"\$MANAGER" restart')) {
          dispatched = true;
          return nativeRestart.future;
        }
        if (script.contains('exec "\$MANAGER" recovery-disarm')) {
          revoked = true;
        }
        return result('');
      });
      final checking = recovery.checkNow();
      await tester.pump();
      expect(dispatched, isTrue);

      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      expect(revoked, isTrue);
      expect(recovery.paused, isTrue);
      expect(recovery.error, ManagedRecoveryError.uncertainResult);

      nativeRestart.complete(result(''));
      await checking;
      expect(
        scripts.where((script) => script.contains('"\$MANAGER" restart')),
        hasLength(1),
      );
      final persisted =
          jsonDecode(
                prefs.getString(ManagedServerRecovery.preferenceKey('local'))!,
              )
              as Map;
      expect(persisted['paused'], isTrue);
      ManagedServerRecovery.disposeForPreferences(prefs);
      final restarted = service();
      expect(restarted.paused, isTrue);
      await restarted.checkNow();
      expect(
        scripts.where((script) => script.contains('"\$MANAGER" restart')),
        hasLength(1),
      );
    },
  );

  testWidgets(
    'unknown command outcome pauses without blindly spending remaining budget',
    (tester) async {
      final recovery = service();
      await recovery.setEnabled(true);
      binding.defaultBinaryMessenger.setMockMethodCallHandler(
        channel,
        (_) async => throw PlatformException(code: 'timeout'),
      );
      await recovery.checkNow();
      expect(recovery.paused, isTrue);
      expect(recovery.attempts, 0);
      ManagedServerRecovery.disposeForPreferences(prefs);
      expect(service().paused, isTrue);
    },
  );

  testWidgets('failed persistence still revokes a dispatched recovery permit', (
    tester,
  ) async {
    final failing = _FailingPreferences();
    final recovery = ManagedServerRecovery.forProfile(
      failing,
      'local',
      now: () => now,
    );
    addTearDown(() => ManagedServerRecovery.disposeForPreferences(failing));
    await recovery.setEnabled(true);
    snapshot = state('failed', failure: 'crash');
    await recovery.checkNow();
    now = now.add(const Duration(minutes: 1));
    final nativeRestart = Completer<Map<String, Object>>();
    var dispatched = false;
    var revoked = false;
    binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      final script = (call.arguments as Map)['script'] as String;
      if (script == TermuxBridge.statusScript()) return result(snapshot);
      if (script.contains('"\$MANAGER" restart')) {
        dispatched = true;
        return nativeRestart.future;
      }
      if (script.contains('exec "\$MANAGER" recovery-disarm')) revoked = true;
      return result('');
    });
    final checking = recovery.checkNow();
    await tester.pump();
    expect(dispatched, isTrue);
    failing.fail = true;
    await expectLater(
      ManagedServerRecovery.disableForProfile(failing, 'local'),
      throwsStateError,
    );
    expect(revoked, isTrue);
    expect(recovery.enabled, isFalse);
    nativeRestart.complete(result(''));
    await checking;
    expect(recovery.enabled, isFalse);
  });

  testWidgets(
    'failed attempt reservation does not spend budget and restart retries once',
    (tester) async {
      final failing = _FailingPreferences();
      final recovery = ManagedServerRecovery.forProfile(
        failing,
        'local',
        now: () => now,
      );
      addTearDown(() => ManagedServerRecovery.disposeForPreferences(failing));
      await recovery.setEnabled(true);
      snapshot = state('failed', failure: 'crash');
      await recovery.checkNow();
      now = now.add(const Duration(minutes: 1));
      final key = ManagedServerRecovery.preferenceKey('local');
      final before = failing.values[key];
      failing.fail = true;
      await recovery.checkNow();

      expect(recovery.attempts, 0);
      expect(recovery.paused, isTrue);
      expect(recovery.error, ManagedRecoveryError.settingsUnreadable);
      expect(failing.values[key], before);
      expect(
        scripts.where((script) => script.contains('"\$MANAGER" restart')),
        isEmpty,
      );

      failing.fail = false;
      ManagedServerRecovery.disposeForPreferences(failing);
      final restarted = ManagedServerRecovery.forProfile(
        failing,
        'local',
        now: () => now,
      );
      await restarted.checkNow();
      expect(restarted.attempts, 1);
      expect(
        scripts.where((script) => script.contains('"\$MANAGER" restart')),
        hasLength(1),
      );
      expect(restarted.paused, isFalse);
      ManagedServerRecovery.disposeForPreferences(failing);
    },
  );

  testWidgets(
    'stale reservation failure cannot restore state after stop supersedes it',
    (tester) async {
      final failing = _FailingPreferences();
      final recovery = ManagedServerRecovery.forProfile(
        failing,
        'local',
        now: () => now,
      );
      addTearDown(() => ManagedServerRecovery.disposeForPreferences(failing));
      await recovery.setEnabled(true);
      snapshot = state('failed', failure: 'crash');
      await recovery.checkNow();
      now = now.add(const Duration(minutes: 1));

      final gate = Completer<void>();
      addTearDown(() {
        if (!gate.isCompleted) gate.complete();
      });
      failing.writeGate = gate;
      failing.writeEntered = Completer<void>();
      failing.failGatedWrite = true;
      final checking = recovery.checkNow();
      await failing.writeEntered!.future;

      // Stop changes the enabled state while the reservation write is still
      // in flight. Its newer state must survive the stale write completion.
      await recovery.setEnabled(false);
      gate.complete();
      await checking;

      expect(recovery.enabled, isFalse);
      expect(recovery.attempts, 1);
      expect(recovery.nextAttemptAt, isNotNull);
      expect(
        scripts.where((script) => script.contains('"\$MANAGER" restart')),
        isEmpty,
      );
      final persisted =
          jsonDecode(
                failing.values[ManagedServerRecovery.preferenceKey('local')]!,
              )
              as Map;
      expect(persisted['enabled'], isFalse);
      expect(persisted['attempts'], 1);
    },
  );

  testWidgets('nonzero native revocation fails deletion and can be retried', (
    tester,
  ) async {
    final recovery = service();
    await recovery.setEnabled(true);
    var failRevoke = true;
    var revocations = 0;
    binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      final script = (call.arguments as Map)['script'] as String;
      if (script.contains('exec "\$MANAGER" recovery-disarm')) {
        revocations++;
        if (failRevoke) {
          return {
            'exitCode': 75,
            'stderr': 'synthetic ownership conflict',
            'stdout': '',
          };
        }
      }
      return result('');
    });
    await expectLater(
      ManagedServerRecovery.disableForProfile(prefs, 'local'),
      throwsA(isA<TermuxBridgeException>()),
    );
    expect(recovery.enabled, isFalse);
    expect(revocations, 1);
    failRevoke = false;
    await ManagedServerRecovery.disableForProfile(prefs, 'local');
    expect(revocations, 2);
  });
}
