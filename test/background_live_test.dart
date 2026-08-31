import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('background live mode persists only after Android enables it', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final calls = <String>[];
    final controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async {
        calls.add(method);
        return {
          'enabled': method == 'enable',
          'active': method == 'enable',
          'notificationGranted': true,
          'batteryOptimizationIgnored': false,
        };
      },
    );
    addTearDown(controller.dispose);

    expect(await controller.setEnabled(true), isTrue);
    expect(controller.active, isTrue);
    expect(preferences.getBool(BackgroundLiveController.preferenceKey), isTrue);

    expect(await controller.setEnabled(false), isFalse);
    expect(controller.active, isFalse);
    expect(
      preferences.getBool(BackgroundLiveController.preferenceKey),
      isFalse,
    );
    expect(calls, ['enable', 'disable']);
  });

  test(
    'denied notification permission leaves background mode disabled',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      final controller = BackgroundLiveController(
        preferences: preferences,
        invoke: (method, [arguments]) async => throw PlatformException(
          code: 'notification_denied',
          message: 'Notification access is required.',
        ),
      );
      addTearDown(controller.dispose);

      expect(await controller.setEnabled(true), isFalse);
      expect(controller.enabled, isFalse);
      expect(controller.lastError, 'Notification access is required.');
      expect(
        preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

  test('restores an enabled preference through the native service', () async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final preferences = await SharedPreferences.getInstance();
    var restored = false;
    final controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async {
        restored = method == 'enable';
        return const {
          'enabled': true,
          'active': true,
          'notificationGranted': true,
          'batteryOptimizationIgnored': true,
        };
      },
    );
    addTearDown(controller.dispose);

    await controller.restore();

    expect(restored, isTrue);
    expect(controller.enabled, isTrue);
    expect(controller.active, isTrue);
    expect(controller.batteryOptimizationIgnored, isTrue);
  });

  test('coding alerts use the exact privacy-safe native contract', () async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final preferences = await SharedPreferences.getInstance();
    final calls = <(String, Map<String, dynamic>?)>[];
    final controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async {
        calls.add((method, arguments));
        if (method == 'showCodingAlert') return const {'shown': true};
        if (method == 'dismissCodingAlert') return const {'dismissed': true};
        return const {
          'enabled': true,
          'active': true,
          'notificationGranted': true,
          'batteryOptimizationIgnored': false,
        };
      },
    );
    addTearDown(controller.dispose);
    await controller.restore();

    expect(
      await controller.showCodingAlert(
        kind: CodingAlertKind.question,
        sessionID: 'session-1',
        key: 'input:session-1',
      ),
      isTrue,
    );
    expect(await controller.dismissCodingAlert('input:session-1'), isTrue);

    expect(calls[1].$1, 'showCodingAlert');
    expect(calls[1].$2, {
      'kind': 'question',
      'sessionID': 'session-1',
      'key': 'input:session-1',
      'quickReply': false,
      'requestID': '',
    });
    expect(calls[2].$1, 'dismissCodingAlert');
    expect(calls[2].$2, {'key': 'input:session-1'});
  });

  test('disabled live mode never posts a coding alert', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final calls = <String>[];
    final controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async {
        calls.add(method);
        return const {'shown': true};
      },
    );
    addTearDown(controller.dispose);

    expect(
      await controller.showCodingAlert(
        kind: CodingAlertKind.complete,
        sessionID: 'session-1',
        key: 'status:session-1',
      ),
      isFalse,
    );
    expect(calls, isEmpty);
  });

  test(
    'consumes a typed notification destination once Android provides it',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      var consumed = false;
      final controller = BackgroundLiveController(
        preferences: preferences,
        invoke: (method, [arguments]) async {
          if (method == 'consumeCodingAlertOpen' && !consumed) {
            consumed = true;
            return const {'kind': 'complete', 'sessionID': 'session-1'};
          }
          return const {};
        },
      );
      addTearDown(controller.dispose);

      final target = await controller.consumeCodingAlertOpen();
      expect(target?.kind, CodingAlertKind.complete);
      expect(target?.sessionID, 'session-1');
      // Notification taps carry no profile discriminator.
      expect(target?.profileID, '');
      expect(await controller.consumeCodingAlertOpen(), isNull);
    },
  );

  test('a widget-tap destination keeps its profile discriminator', () {
    final target = CodingAlertOpen.fromPlatform({
      'kind': 'complete',
      'sessionID': 'session-1',
      'profileID': ' server-1 ',
    });
    expect(target?.kind, CodingAlertKind.complete);
    expect(target?.sessionID, 'session-1');
    expect(target?.profileID, 'server-1');
  });

  test('an Android foreground-service timeout turns live mode off', () async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final preferences = await SharedPreferences.getInstance();
    final controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async => {
        'enabled': true,
        'active': true,
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      },
    );
    addTearDown(controller.dispose);
    await controller.restore();
    expect(controller.enabled, isTrue);
    expect(controller.active, isTrue);

    var notifications = 0;
    controller.addListener(() => notifications++);

    // Android 15 stopped the service. Before this event existed the
    // preference stayed true over a dead service, so the switch read "on"
    // while nothing was connected.
    controller.handleNativeTimeout(const {
      'enabled': false,
      'active': false,
      'reason': 'systemTimeout',
    });

    expect(controller.enabled, isFalse);
    expect(controller.active, isFalse);
    expect(controller.stoppedByAndroidTimeout, isTrue);
    expect(notifications, 1, reason: 'the UI has to hear about it at once');
    // Nothing failed, so this is not an error the user can retry away.
    expect(controller.lastError, isNull);
    await Future<void>.delayed(Duration.zero);
    expect(
      preferences.getBool(BackgroundLiveController.preferenceKey),
      isFalse,
      reason: 'the persisted preference must not outlive the service',
    );
  });

  test('turning live mode back on clears the timeout notice', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final controller = BackgroundLiveController(
      preferences: preferences,
      invoke: (method, [arguments]) async => {
        'enabled': method == 'enable',
        'active': method == 'enable',
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      },
    );
    addTearDown(controller.dispose);

    controller.handleNativeTimeout(const {'reason': 'systemTimeout'});
    expect(controller.stoppedByAndroidTimeout, isTrue);

    expect(await controller.setEnabled(true), isTrue);
    expect(controller.stoppedByAndroidTimeout, isFalse);
  });

  test('the timeout method name matches the native contract', () {
    // BackgroundConnectionService.onTimeout pushes this exact name; a
    // rename on either side silently stops the event from arriving.
    expect(
      BackgroundLiveController.timeoutMethod,
      'backgroundServiceTimeout',
    );
    final kotlin = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/'
      'BackgroundConnectionService.kt',
    ).readAsStringSync();
    expect(
      kotlin,
      contains(
        'const val METHOD_TIMEOUT = '
        '"${BackgroundLiveController.timeoutMethod}"',
      ),
    );
    expect(kotlin, contains('notifyDartOfTimeout()'));
  });
}
