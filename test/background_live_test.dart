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
}
