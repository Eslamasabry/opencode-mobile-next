import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    final previousTarget = debugDefaultTargetPlatformOverride;
    final previousCapabilities = debugPlatformCapabilities;
    addTearDown(() {
      debugDefaultTargetPlatformOverride = previousTarget;
      debugPlatformCapabilities = previousCapabilities;
    });
  });

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

  for (final caps in const [
    PlatformCapabilities(platform: TargetPlatform.iOS),
    PlatformCapabilities.linuxDesktop(),
    PlatformCapabilities(platform: TargetPlatform.windows),
    PlatformCapabilities(platform: TargetPlatform.macOS),
    PlatformCapabilities(platform: TargetPlatform.fuchsia),
    PlatformCapabilities(platform: TargetPlatform.android, isWeb: true),
  ]) {
    for (final saved in <bool?>[null, false, true]) {
      test(
        'unsupported $caps ignores saved $saved without changing it',
        () async {
          debugDefaultTargetPlatformOverride = caps.platform;
          debugPlatformCapabilities = caps;
          SharedPreferences.setMockInitialValues({
            BackgroundLiveController.preferenceKey: ?saved,
          });
          final preferences = await SharedPreferences.getInstance();
          const channel = MethodChannel('oc/background');
          final messenger =
              TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
          final calls = <String>[];
          messenger.setMockMethodCallHandler(channel, (call) async {
            calls.add(call.method);
            return const {'enabled': true, 'active': true};
          });
          addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
          final controller = BackgroundLiveController(preferences: preferences);
          addTearDown(controller.dispose);

          expect(
            controller.enabled,
            isFalse,
            reason: 'safe even before restore',
          );
          expect(controller.active, isFalse);
          await controller.restore();
          expect(controller.enabled, isFalse);
          expect(await controller.setEnabled(true), isFalse);
          await controller.refreshStatus();
          expect(
            await controller.requestBatteryOptimizationExemption(),
            isFalse,
          );
          expect(await controller.setEnabled(false), isFalse);
          expect(controller.enabled, isFalse);
          expect(controller.active, isFalse);
          expect(controller.notificationGranted, isFalse);
          expect(controller.batteryOptimizationIgnored, isFalse);
          expect(controller.busy, isFalse);
          expect(controller.lastError, isNull);
          expect(controller.stoppedByAndroidTimeout, isFalse);
          expect(calls, isEmpty);

          await preferences.reload();
          expect(
            preferences.getBool(BackgroundLiveController.preferenceKey),
            saved,
          );
          final recreated = BackgroundLiveController(preferences: preferences);
          addTearDown(recreated.dispose);
          expect(recreated.enabled, isFalse);
          expect(recreated.active, isFalse);
        },
      );
    }
  }

  for (final operation in ['restore', 'enable']) {
    test(
      'unsupported $operation clears a previously active Android state',
      () async {
        SharedPreferences.setMockInitialValues({
          BackgroundLiveController.preferenceKey: true,
        });
        final preferences = await SharedPreferences.getInstance();
        final calls = <String>[];
        final controller = BackgroundLiveController(
          preferences: preferences,
          invoke: (method, [arguments]) async {
            calls.add(method);
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
        expect(controller.active, isTrue);
        var changes = 0;
        controller.addListener(() => changes++);

        debugPlatformCapabilities = const PlatformCapabilities(
          platform: TargetPlatform.iOS,
        );
        if (operation == 'restore') {
          await controller.restore();
        } else {
          expect(await controller.setEnabled(true), isFalse);
        }

        expect(controller.enabled, isFalse);
        expect(controller.active, isFalse);
        expect(controller.notificationGranted, isFalse);
        expect(controller.batteryOptimizationIgnored, isFalse);
        expect(changes, 1);
        expect(calls, ['enable']);
        await preferences.reload();
        expect(
          preferences.getBool(BackgroundLiveController.preferenceKey),
          isTrue,
        );

        // A transient unsupported gate must not erase the real Android opt-in.
        debugPlatformCapabilities = const PlatformCapabilities.android();
        final recreated = BackgroundLiveController(
          preferences: preferences,
          invoke: (method, [arguments]) async {
            calls.add(method);
            return const {'enabled': true, 'active': true};
          },
        );
        addTearDown(recreated.dispose);
        await recreated.restore();
        expect(recreated.enabled, isTrue);
        expect(recreated.active, isTrue);
        expect(calls, ['enable', 'enable']);
      },
    );
  }

  for (final failure in [false, true]) {
    test(
      'late native enable ${failure ? 'failure' : 'status'} cannot undo an unsupported gate',
      () async {
        SharedPreferences.setMockInitialValues({
          BackgroundLiveController.preferenceKey: true,
        });
        final preferences = await SharedPreferences.getInstance();
        final pending = Completer<Map<String, dynamic>>();
        final calls = <String>[];
        final controller = BackgroundLiveController(
          preferences: preferences,
          invoke: (method, [arguments]) {
            calls.add(method);
            return pending.future;
          },
        );
        addTearDown(controller.dispose);
        final enabling = controller.setEnabled(true);
        expect(controller.busy, isTrue);
        debugPlatformCapabilities = const PlatformCapabilities(
          platform: TargetPlatform.iOS,
        );
        // Even a retry while Android's old request is busy must fail closed.
        expect(await controller.setEnabled(true), isFalse);
        if (failure) {
          pending.completeError(PlatformException(code: 'unavailable'));
        } else {
          pending.complete(const {
            'enabled': true,
            'active': true,
            'notificationGranted': true,
            'batteryOptimizationIgnored': true,
          });
        }

        expect(await enabling, isFalse);
        expect(controller.enabled, isFalse);
        expect(controller.active, isFalse);
        expect(controller.notificationGranted, isFalse);
        expect(controller.batteryOptimizationIgnored, isFalse);
        expect(controller.busy, isFalse);
        expect(controller.lastError, isNull);
        expect(calls, ['enable']);
        await preferences.reload();
        expect(
          preferences.getBool(BackgroundLiveController.preferenceKey),
          isTrue,
        );
      },
    );
  }

  test(
    'an unavailable Android channel still rejects enable and reports it',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      final controller = BackgroundLiveController(
        preferences: preferences,
        invoke: (method, [arguments]) async => throw MissingPluginException(),
      );
      addTearDown(controller.dispose);

      expect(await controller.setEnabled(true), isFalse);
      expect(controller.enabled, isFalse);
      expect(controller.active, isFalse);
      expect(controller.busy, isFalse);
      expect(
        controller.lastError,
        'Background live mode is available in the Android app.',
      );
      expect(
        preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

  test(
    'malformed Android status fields do not grant background capabilities',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      final controller = BackgroundLiveController(
        preferences: preferences,
        invoke: (method, [arguments]) async => const {
          'enabled': 'true',
          'active': 1,
          'notificationGranted': [],
          'batteryOptimizationIgnored': null,
        },
      );
      addTearDown(controller.dispose);

      expect(await controller.setEnabled(true), isFalse);
      expect(controller.enabled, isFalse);
      expect(controller.active, isFalse);
      expect(controller.notificationGranted, isFalse);
      expect(controller.batteryOptimizationIgnored, isFalse);
      expect(
        preferences.getBool(BackgroundLiveController.preferenceKey),
        isFalse,
      );
    },
  );

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
    expect(BackgroundLiveController.timeoutMethod, 'backgroundServiceTimeout');
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
