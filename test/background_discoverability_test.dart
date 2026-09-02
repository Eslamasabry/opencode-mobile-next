import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store, {super.backgroundLive});

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> refreshSessions() async {}

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}

  @override
  Future<void> refreshPendingForms() async {}
}

Future<_Controller> _controller({required bool enabled}) async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: enabled,
  });
  final preferences = await SharedPreferences.getInstance();
  final live = BackgroundLiveController(
    preferences: preferences,
    liveStatusDebounce: Duration.zero,
    invoke: (method, [arguments]) async => {
      'enabled': method != 'disable' && (enabled || method == 'enable'),
      'active': method != 'disable' && (enabled || method == 'enable'),
      'notificationGranted': true,
      'batteryOptimizationIgnored': true,
    },
  );
  await live.restore();
  return _Controller(ProfileStore(prefs: preferences), backgroundLive: live)
    ..repository = _Repository()
    ..status = StreamStatus.connected;
}

void main() {
  testWidgets('an empty inbox points at background updates when they are off', (
    tester,
  ) async {
    final controller = await _controller(enabled: false);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: ActivityScreen(controller: controller, embedded: true)),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('activity-all-clear')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('activity-background-hint')),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const ValueKey('activity-background-settings')));
    await tester.pumpAndSettle();
    expect(find.text('Notifications & background'), findsOneWidget);
    expect(find.text('Stay connected in the background'), findsOneWidget);
    expect(find.byKey(const ValueKey('background-status-row')), findsOneWidget);
    expect(find.text('Off'), findsOneWidget);
  });

  testWidgets('the hint disappears once background updates are on', (
    tester,
  ) async {
    final controller = await _controller(enabled: true);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: ActivityScreen(controller: controller, embedded: true)),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('activity-all-clear')), findsOneWidget);
    expect(find.byKey(const ValueKey('activity-background-hint')), findsNothing);
  });

  testWidgets('the settings hub summarises the background state', (
    tester,
  ) async {
    final controller = await _controller(enabled: true);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    expect(find.text('On · running now'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('settings-category-background')));
    await tester.pumpAndSettle();
    expect(find.text('Running now'), findsOneWidget);

    // Paused from the notification: off, without the Android-timeout notice.
    controller.backgroundLive.handleNativeTimeout(const {
      'reason': BackgroundLiveController.pauseReason,
    });
    await tester.pumpAndSettle();
    expect(find.text('Off'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('background-timeout-notice')),
      findsNothing,
    );

    // Stopped by Android: the row says so and restarts on tap.
    controller.backgroundLive.handleNativeTimeout(const {
      'reason': 'systemTimeout',
    });
    await tester.pumpAndSettle();
    expect(find.text('Stopped by Android — tap to restart'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('background-status-row')));
    await tester.pumpAndSettle();
    expect(find.text('Running now'), findsOneWidget);
    expect(controller.keepLiveInBackground, isTrue);
  });
}
