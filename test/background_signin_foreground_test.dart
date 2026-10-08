import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _unpaused = <String, dynamic>{
  'supported': true,
  'active': false,
  'paused': false,
  'reason': 'none',
  'at': null,
  'canResume': false,
};
const _running = <String, dynamic>{
  'supported': true,
  'active': true,
  'paused': false,
  'reason': 'none',
  'at': null,
  'canResume': false,
};
const _paused = <String, dynamic>{
  'supported': true,
  'active': false,
  'paused': true,
  'reason': 'timeLimit',
  'at': 1791417600000,
  'canResume': true,
};

Map<String, dynamic> _status(bool active) => {
  'enabled': active,
  'active': active,
  'notificationGranted': true,
  'batteryOptimizationIgnored': false,
  'backgroundPause': active ? _running : _unpaused,
};

class _World {
  _World(this.preferences) {
    controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async {
        calls.add(method);
        expect(arguments, isNull);
        switch (method) {
          case 'getBackgroundPause':
            if (pauseGate != null) return pauseGate!.future;
            return pause;
          case 'enable':
            if (enableError != null) throw enableError!;
            if (enableGate != null) {
              final receipt = await enableGate!.future;
              nativeActive = receipt['active'] == true;
              return receipt;
            }
            nativeActive = ackActive;
            return _status(ackActive);
          case 'disable':
            if (disableError != null) throw disableError!;
            nativeActive = disableAckActive;
            return _status(disableAckActive);
          case 'getStatus':
            return _status(nativeActive);
          default:
            return const {};
        }
      },
    );
  }
  final SharedPreferences preferences;
  late final BackgroundLiveController controller;
  final calls = <String>[];
  Map<String, dynamic> pause = _unpaused;
  Completer<Map<String, dynamic>>? pauseGate;
  Completer<Map<String, dynamic>>? enableGate;
  Object? enableError;
  Object? disableError;
  bool ackActive = true;
  bool nativeActive = false;
  bool disableAckActive = false;
}

Matcher _failure(AgentSignInForegroundFailure reason) => throwsA(
  isA<AgentSignInForegroundException>().having(
    (e) => e.reason,
    'reason',
    reason,
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    final oldPlatform = debugDefaultTargetPlatformOverride;
    final oldCapabilities = debugPlatformCapabilities;
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    debugPlatformCapabilities = const PlatformCapabilities.android();
    SharedPreferences.setMockInitialValues({});
    addTearDown(() {
      debugDefaultTargetPlatformOverride = oldPlatform;
      debugPlatformCapabilities = oldCapabilities;
    });
  });

  Future<_World> world() async {
    final w = _World(await SharedPreferences.getInstance());
    addTearDown(w.controller.dispose);
    return w;
  }

  test(
    'sign-in lease acknowledges foreground without changing user preference',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      expect(lease.active, isFalse);
      await lease.ready;
      expect(lease.active, isTrue);
      expect(w.controller.active, isTrue);
      expect(w.controller.enabled, isFalse);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isNull,
      );
      expect(w.calls, ['getBackgroundPause', 'enable']);
      await lease.release();
      expect(lease.active, isFalse);
      expect(w.calls, ['getBackgroundPause', 'enable', 'disable']);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isNull,
      );
    },
  );

  test(
    'concurrent reservations join one native enable and last release stops it',
    () async {
      final w = await world();
      w.enableGate = Completer<Map<String, dynamic>>();
      final first = w.controller.reserveAgentSignInForeground();
      final second = w.controller.reserveAgentSignInForeground();
      await pumpEventQueue();
      expect(w.calls, ['getBackgroundPause', 'enable']);
      expect(first.active, isFalse);
      expect(second.active, isFalse);
      w.enableGate!.complete(_status(true));
      await Future.wait([first.ready, second.ready]);
      await first.release();
      expect(second.active, isTrue);
      expect(w.calls.where((v) => v == 'disable'), isEmpty);
      await second.release();
      expect(w.calls.where((v) => v == 'disable'), hasLength(1));
    },
  );

  test(
    'borrows existing active service without enabling or disabling it',
    () async {
      final w = await world();
      w.pause = _running;
      w.nativeActive = true;
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      await lease.release();
      expect(w.calls, ['getBackgroundPause']);
      expect(w.controller.active, isTrue);
      expect(w.controller.enabled, isFalse);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isNull,
      );
    },
  );

  test(
    'unknown or durable paused status never starts foreground work',
    () async {
      for (final pause in [
        <String, dynamic>{'supported': false},
        _paused,
      ]) {
        final w = await world();
        w.pause = pause;
        final lease = w.controller.reserveAgentSignInForeground();
        await expectLater(
          lease.ready,
          _failure(
            pause == _paused
                ? AgentSignInForegroundFailure.paused
                : AgentSignInForegroundFailure.unsupported,
          ),
        );
        expect(lease.active, isFalse);
        expect(w.calls, ['getBackgroundPause']);
        await lease.release();
      }
    },
  );

  test(
    'notification denial and inactive acknowledgement fail with typed reasons',
    () async {
      for (final permission in [true, false]) {
        final w = await world();
        if (permission) {
          w.enableError = PlatformException(
            code: 'notification_denied',
            message: 'synthetic-private',
          );
        } else {
          w.ackActive = false;
        }
        final lease = w.controller.reserveAgentSignInForeground();
        await expectLater(
          lease.ready,
          _failure(
            permission
                ? AgentSignInForegroundFailure.permission
                : AgentSignInForegroundFailure.unavailable,
          ),
        );
        expect(lease.active, isFalse);
        expect(w.controller.enabled, isFalse);
        expect(
          w.preferences.getBool(BackgroundLiveController.preferenceKey),
          isNull,
        );
        await lease.release();
      }
    },
  );

  test(
    'cancellation before pause receipt completes readiness promptly and sends no enable',
    () async {
      final w = await world();
      w.pauseGate = Completer<Map<String, dynamic>>();
      final lease = w.controller.reserveAgentSignInForeground();
      await pumpEventQueue();
      final failed = expectLater(
        lease.ready,
        _failure(AgentSignInForegroundFailure.cancelled),
      );
      await lease.release();
      await failed;
      w.pauseGate!.complete(_unpaused);
      await pumpEventQueue();
      expect(w.calls, ['getBackgroundPause']);
    },
  );

  test(
    'cancellation during native start ignores and cleans late successful acknowledgement',
    () async {
      final w = await world();
      w.enableGate = Completer<Map<String, dynamic>>();
      final lease = w.controller.reserveAgentSignInForeground();
      await pumpEventQueue();
      final failed = expectLater(
        lease.ready,
        _failure(AgentSignInForegroundFailure.cancelled),
      );
      await lease.release();
      await failed;
      w.enableGate!.complete(_status(true));
      await pumpEventQueue();
      expect(lease.active, isFalse);
      expect(w.calls, ['getBackgroundPause', 'enable', 'disable']);
      expect(w.controller.enabled, isFalse);
      expect(w.controller.active, isFalse);
    },
  );

  test(
    'explicit user enable during lease preserves service after release',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      expect(await w.controller.setEnabled(true), isTrue);
      await lease.release();
      expect(w.controller.enabled, isTrue);
      expect(w.controller.active, isTrue);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isTrue,
      );
      expect(w.calls.where((v) => v == 'disable'), isEmpty);
    },
  );

  test(
    'explicit user enable while acknowledgement is pending joins activation',
    () async {
      final w = await world();
      w.enableGate = Completer<Map<String, dynamic>>();
      final lease = w.controller.reserveAgentSignInForeground();
      await pumpEventQueue();
      final enabling = w.controller.setEnabled(true);
      w.enableGate!.complete(_status(true));
      await lease.ready;
      expect(await enabling, isTrue);
      await lease.release();
      expect(w.calls.where((v) => v == 'enable'), hasLength(1));
      expect(w.calls.where((v) => v == 'disable'), isEmpty);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isTrue,
      );
    },
  );

  test(
    'explicit disable loses lease immediately and prevents restart',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      var losses = 0;
      final subscription = lease.lost.listen((_) => losses++);
      expect(await w.controller.setEnabled(false), isFalse);
      expect(lease.active, isFalse);
      expect(losses, 1);
      await lease.release();
      expect(w.calls.where((v) => v == 'disable'), hasLength(1));
      expect(w.calls.where((v) => v == 'enable'), hasLength(1));
      await subscription.cancel();
    },
  );

  test(
    'native timeout loses all leases without automatically restarting',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      var losses = 0;
      final subscription = lease.lost.listen((_) => losses++);
      w.controller.handleNativeTimeout({
        'reason': 'systemTimeout',
        'backgroundPause': _paused,
      });
      expect(lease.active, isFalse);
      expect(losses, 1);
      await lease.release();
      expect(w.calls, ['getBackgroundPause', 'enable']);
      await subscription.cancel();
    },
  );

  test('inactive native status invalidates an active lease', () async {
    final w = await world();
    final lease = w.controller.reserveAgentSignInForeground();
    await lease.ready;
    w.nativeActive = false;
    await w.controller.refreshStatus();
    expect(lease.active, isFalse);
    await lease.release();
    expect(w.controller.enabled, isFalse);
    expect(w.calls.where((v) => v == 'enable'), hasLength(1));
  });

  test(
    'dispose cancels admission and cleans late native acknowledgement',
    () async {
      final w = await world();
      w.enableGate = Completer<Map<String, dynamic>>();
      final lease = w.controller.reserveAgentSignInForeground();
      await pumpEventQueue();
      final failed = expectLater(
        lease.ready,
        _failure(AgentSignInForegroundFailure.cancelled),
      );
      w.controller.dispose();
      await failed;
      w.enableGate!.complete(_status(true));
      await pumpEventQueue();
      expect(lease.active, isFalse);
      expect(w.calls.where((v) => v == 'disable'), hasLength(1));
      await lease.release();
    },
  );

  test(
    'active disposal waits for terminal cleanup to release service',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      var losses = 0;
      final subscription = lease.lost.listen((_) => losses++);
      w.controller.dispose();
      expect(lease.active, isFalse);
      expect(losses, 1);
      expect(w.calls, ['getBackgroundPause', 'enable']);
      await lease.release();
      expect(w.calls, ['getBackgroundPause', 'enable', 'disable']);
      await subscription.cancel();
    },
  );

  test(
    'failed owned stop reports typed failure and stays eligible for retry',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      w.disableError = PlatformException(
        code: 'unavailable',
        message: 'private',
      );
      await expectLater(
        lease.release(),
        _failure(AgentSignInForegroundFailure.unavailable),
      );
      expect(lease.active, isFalse);
      w.disableError = null;
      await lease.release();
      expect(w.calls.where((value) => value == 'disable'), hasLength(2));
      expect(w.controller.active, isFalse);
    },
  );

  test(
    'user disable wins busy native enable and cleans its late receipt',
    () async {
      final w = await world();
      w.enableGate = Completer<Map<String, dynamic>>();
      final enabling = w.controller.setEnabled(true);
      expect(w.controller.busy, isTrue);
      expect(w.controller.enabled, isTrue);
      expect(await w.controller.setEnabled(false), isFalse);
      expect(w.controller.enabled, isFalse);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
      expect(w.calls, ['enable', 'disable']);
      w.enableGate!.complete(_status(true));
      expect(await enabling, isFalse);
      expect(w.controller.enabled, isFalse);
      expect(w.controller.active, isFalse);
      expect(w.calls, ['enable', 'disable', 'disable']);
      expect(
        w.preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

  test(
    'platform support lost while reading pause fails pending admission',
    () async {
      final w = await world();
      w.pauseGate = Completer<Map<String, dynamic>>();
      final lease = w.controller.reserveAgentSignInForeground();
      await pumpEventQueue();
      final failed = expectLater(
        lease.ready,
        _failure(AgentSignInForegroundFailure.unsupported),
      );
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.iOS,
      );
      w.pauseGate!.complete(_unpaused);
      await failed;
      await lease.release();
      expect(lease.active, isFalse);
      expect(w.calls, ['getBackgroundPause']);
    },
  );

  test(
    'active disable receipt reports typed failure and retries owned stop',
    () async {
      final w = await world();
      final lease = w.controller.reserveAgentSignInForeground();
      await lease.ready;
      w.disableAckActive = true;
      await expectLater(
        lease.release(),
        _failure(AgentSignInForegroundFailure.unavailable),
      );
      expect(lease.active, isFalse);
      expect(w.controller.active, isTrue);
      w.disableAckActive = false;
      await lease.release();
      expect(w.calls.where((value) => value == 'disable'), hasLength(2));
      expect(w.controller.active, isFalse);
    },
  );
}
