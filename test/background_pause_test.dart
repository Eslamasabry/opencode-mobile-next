import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/domain/background_pause.dart';
import 'package:opencode_mobile/domain/diagnostics_error.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _at = 1791428400000;

Map<String, dynamic> _pause({
  bool active = false,
  BackgroundPauseReason reason = BackgroundPauseReason.timeLimit,
}) => {
  'supported': true,
  'active': active,
  'paused': !active && reason != BackgroundPauseReason.none,
  'reason': active ? 'none' : reason.name,
  'at': active || reason == BackgroundPauseReason.none ? null : _at,
  'canResume': !active && reason != BackgroundPauseReason.none,
};

Map<String, dynamic> _status({bool active = false}) => {
  'enabled': active,
  'active': active,
  'notificationGranted': true,
  'batteryOptimizationIgnored': false,
  'backgroundPause': _pause(active: active),
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    final previous = debugPlatformCapabilities;
    debugPlatformCapabilities = const PlatformCapabilities.android();
    addTearDown(() => debugPlatformCapabilities = previous);
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
  });

  Future<BackgroundLiveController> controller(
    BackgroundMethodInvoker invoke,
  ) async {
    final value = BackgroundLiveController(
      preferences: await SharedPreferences.getInstance(),
      invoke: invoke,
    );
    addTearDown(value.dispose);
    return value;
  }

  for (final reason in BackgroundPauseReason.values.where(
    (reason) => reason != BackgroundPauseReason.none,
  )) {
    test(
      'cold restore keeps $reason paused despite an old enabled preference',
      () async {
        final calls = <String>[];
        final value = await controller((method, [arguments]) async {
          calls.add(method);
          return _pause(reason: reason);
        });
        await value.restore();
        expect(calls, ['getBackgroundPause']);
        expect(value.enabled, isFalse);
        expect(value.active, isFalse);
        expect(value.backgroundPause.reason, reason);
        expect(value.backgroundPause.at!.isUtc, isTrue);
        expect(value.backgroundPause.at!.millisecondsSinceEpoch, _at);
        expect(value.backgroundPause.canResume, isTrue);
        expect(
          value.preferences.getBool(BackgroundLiveController.preferenceKey),
          isFalse,
        );
      },
    );
  }

  test(
    'an unreadable receipt cannot silently restore the old opt-in',
    () async {
      final calls = <String>[];
      final value = await controller((method, [arguments]) async {
        calls.add(method);
        throw PlatformException(code: 'storage', message: 'private-secret');
      });
      await value.restore();
      expect(calls, ['getBackgroundPause']);
      expect(value.enabled, isFalse);
      expect(value.backgroundPause.supported, isFalse);
      expect(value.lastError, isNull);
      expect(
        value.preferences.getBool(BackgroundLiveController.preferenceKey),
        isTrue,
      );
    },
  );

  test(
    'Resume clears a pause only after native foreground activation',
    () async {
      final calls = <String>[];
      final value = await controller((method, [arguments]) async {
        calls.add(method);
        return method == 'getBackgroundPause'
            ? _pause()
            : _status(active: true);
      });
      await value.restore();
      final result = await value.resumeBackground();
      expect(result.error, isNull);
      expect(result.state.active, isTrue);
      expect(result.state.paused, isFalse);
      expect(result.state.reason, BackgroundPauseReason.none);
      expect(value.stoppedByAndroidTimeout, isFalse);
      expect(value.enabled, isTrue);
      expect(
        value.preferences.getBool(BackgroundLiveController.preferenceKey),
        isTrue,
      );
      expect(calls, ['getBackgroundPause', 'enable']);
    },
  );

  test(
    'an accepted but inactive start keeps the pause and returns resumeFailed',
    () async {
      final value = await controller((method, [arguments]) async {
        if (method == 'getBackgroundPause') return _pause();
        return {..._status(), 'enabled': true};
      });
      await value.restore();
      final before = value.backgroundPause;
      final result = await value.resumeBackground();
      expect(result.error, DiagnosticsError.resumeFailed);
      expect(result.state, before);
      expect(value.enabled, isFalse);
      expect(
        value.preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

  for (final code in [
    'notification_denied',
    'foreground_service_failed',
    'permission_in_progress',
  ]) {
    test(
      'Resume $code preserves the pause without native error text',
      () async {
        final value = await controller((method, [arguments]) async {
          if (method == 'getBackgroundPause') return _pause();
          throw PlatformException(code: code, message: 'private-password');
        });
        await value.restore();
        final result = await value.resumeBackground();
        expect(
          result.error,
          code == 'permission_in_progress'
              ? DiagnosticsError.busy
              : DiagnosticsError.resumeFailed,
        );
        expect(result.state.paused, isTrue);
        expect(value.lastError, isNull);
        expect(
          value.preferences.getBool(BackgroundLiveController.preferenceKey),
          isFalse,
        );
      },
    );
  }

  test(
    'a second Resume cannot request another service while one is pending',
    () async {
      final pending = Completer<Map<String, dynamic>>();
      var starts = 0;
      final value = await controller((method, [arguments]) {
        if (method == 'getBackgroundPause') return Future.value(_pause());
        starts++;
        return pending.future;
      });
      await value.restore();
      final first = value.resumeBackground();
      expect((await value.resumeBackground()).error, DiagnosticsError.busy);
      expect(starts, 1);
      pending.complete(_status(active: true));
      expect((await first).error, isNull);
    },
  );

  test(
    'a late receipt read cannot overwrite a newer successful Resume',
    () async {
      final pending = Completer<Map<String, dynamic>>();
      var reads = 0;
      final value = await controller((method, [arguments]) {
        if (method == 'getBackgroundPause') {
          return ++reads == 1 ? Future.value(_pause()) : pending.future;
        }
        return Future.value(_status(active: true));
      });
      await value.restore();
      final oldRead = value.refreshBackgroundPause();
      expect((await value.resumeBackground()).state.active, isTrue);
      pending.complete(_pause());
      expect((await oldRead).active, isTrue);
      expect(value.active, isTrue);
      expect(value.enabled, isTrue);
    },
  );

  test(
    'a timeout during Resume wins over the older successful start reply',
    () async {
      final pending = Completer<Map<String, dynamic>>();
      final value = await controller(
        (method, [arguments]) => method == 'getBackgroundPause'
            ? Future.value(_pause(reason: BackgroundPauseReason.interrupted))
            : pending.future,
      );
      await value.restore();
      final resuming = value.resumeBackground();
      value.handleNativeTimeout({
        'reason': 'systemTimeout',
        'backgroundPause': _pause(),
      });
      pending.complete(_status(active: true));
      final result = await resuming;
      expect(result.error, DiagnosticsError.resumeFailed);
      expect(result.state.reason, BackgroundPauseReason.timeLimit);
      expect(value.active, isFalse);
      expect(value.enabled, isFalse);
      await Future<void>.delayed(Duration.zero);
      expect(
        value.preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

  test('notification Pause keeps its intentional semantics', () async {
    final value = await controller((method, [arguments]) async => _pause());
    await value.restore();
    value.handleNativeTimeout(const {'reason': 'userPause'});
    expect(value.backgroundPause.paused, isFalse);
    expect(value.backgroundPause.reason, BackgroundPauseReason.none);
    expect(value.stoppedByAndroidTimeout, isFalse);
  });

  test(
    'an older enable reply cannot roll back an explicit notification Pause',
    () async {
      final pending = Completer<Map<String, dynamic>>();
      final value = await controller((method, [arguments]) => pending.future);
      // Cold saved opt-in, with no confirmed service yet: this is the same
      // enabled/inactive combination in which normal restore requests a start.
      expect(value.enabled, isTrue);
      final enabling = value.setEnabled(true);
      value.handleNativeTimeout(const {'reason': 'userPause'});
      pending.complete(_status(active: true));
      expect(await enabling, isFalse);
      expect(value.enabled, isFalse);
      expect(value.active, isFalse);
      expect(value.backgroundPause.paused, isFalse);
      expect(value.stoppedByAndroidTimeout, isFalse);
      expect(value.lastError, isNull);
      await Future<void>.delayed(Duration.zero);
      expect(
        value.preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

  test(
    'a late Resume error respects a platform that became unsupported',
    () async {
      final pending = Completer<Map<String, dynamic>>();
      final value = await controller(
        (method, [arguments]) => method == 'getBackgroundPause'
            ? Future.value(_pause())
            : pending.future,
      );
      await value.restore();
      final resuming = value.resumeBackground();
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      pending.completeError(
        PlatformException(code: 'failed', message: 'private-secret'),
      );
      final result = await resuming;
      expect(result.error, DiagnosticsError.unavailable);
      expect(result.state.supported, isFalse);
      expect(value.backgroundPause.supported, isFalse);
      expect(value.active, isFalse);
      expect(value.enabled, isFalse);
      expect(value.lastError, isNull);
    },
  );

  test('intentional opt-out clears a previously durable pause', () async {
    final value = await controller(
      (method, [arguments]) async => method == 'getBackgroundPause'
          ? _pause()
          : {'enabled': false, 'active': false},
    );
    await value.restore();
    await value.setEnabled(false);
    expect(value.backgroundPause.paused, isFalse);
    expect(value.backgroundPause.reason, BackgroundPauseReason.none);
  });

  test(
    'unsupported platforms do not start, read or change the Android opt-in',
    () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      final calls = <String>[];
      final value = await controller((method, [arguments]) async {
        calls.add(method);
        return _status(active: true);
      });
      await value.restore();
      expect(
        (await value.resumeBackground()).error,
        DiagnosticsError.unavailable,
      );
      expect(value.backgroundPause.supported, isFalse);
      expect(calls, isEmpty);
      expect(
        value.preferences.getBool(BackgroundLiveController.preferenceKey),
        isTrue,
      );
    },
  );

  test(
    'native pause parsing rejects malformed and secret-bearing categories',
    () {
      for (final malformed in [
        {..._pause(), 'reason': 'private-password'},
        {..._pause(), 'at': 0},
        {..._pause(), 'at': double.infinity},
        {..._pause(), 'active': 'true'},
        {..._pause(), 'canResume': 1},
      ]) {
        expect(BackgroundPauseState.fromPlatform(malformed).supported, isFalse);
      }
      expect(
        BackgroundPauseState.fromPlatform(_pause(active: true)).paused,
        isFalse,
      );
    },
  );
}
