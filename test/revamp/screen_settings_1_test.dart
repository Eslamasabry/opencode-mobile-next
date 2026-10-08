// Behaviour of screen-settings-1's rebuilt Notifications and Privacy pages:
// what the map records asked for (quiet time picker says which end it
// sets, same start and end explained, no saved servers said, monitoring
// mechanics folded into Details, the counted delete verb, the policy row)
// and the kit-only replacements for snackbars.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/profile_monitor_fixture.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Finder _key(String key) => find.byKey(ValueKey(key));

Future<ConnectionController> _notifyController({int servers = 0}) async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  for (var i = 1; i <= servers; i++) {
    await store.upsert(
      ServerProfile(
        id: 'profile-$i',
        name: 'Server $i',
        baseUrl: 'https://server$i.example',
        username: '',
        password: '',
      ),
    );
  }
  final live = BackgroundLiveController(
    preferences: preferences,
    liveStatusDebounce: Duration.zero,
    invoke: (method, [arguments]) async {
      if (method == 'getBackgroundPause') {
        return const {
          'supported': true,
          'active': false,
          'paused': false,
          'reason': 'none',
          'at': null,
          'canResume': false,
        };
      }
      return {
        'enabled': method != 'disable',
        'active': method != 'disable',
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      };
    },
  );
  await live.restore();
  return ConnectionController(
    store,
    backgroundLive: live,
    monitorGatewayFactory: (_) =>
        (gateway: MonitorTestGateway(), operations: MonitorTestOperations()),
  );
}

class _PrivacyController extends ConnectionController {
  _PrivacyController(super.store) : super(isIsolated: true);

  int queued = 2;
  int drafts = 3;
  bool clearWorks = true;

  @override
  int get totalQueuedPromptCount => queued;

  @override
  int get totalSessionDraftCount => drafts;

  @override
  int get queuedPromptBytes => 2048;

  @override
  bool get queuedPromptStorageReadable => true;

  @override
  int get sessionDraftBytes => 512;

  @override
  Future<bool> clearAllSessionDrafts() async {
    if (clearWorks) drafts = 0;
    notifyListeners();
    return clearWorks;
  }
}

Future<_PrivacyController> _privacyController() async {
  SharedPreferences.setMockInitialValues({});
  return _PrivacyController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  );
}

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  group('notifications-settings', () {
    testWidgets('no saved servers is said, not an empty section', (
      tester,
    ) async {
      final controller = await _notifyController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(_key('notify-no-servers'));
      await tester.pumpAndSettle();
      expect(find.text(_en.notifyNoServersTitle), findsOneWidget);
      // Nothing is watched, so there is no monitoring mechanics fold.
      expect(_key('notifications-monitor-details'), findsNothing);
    });

    testWidgets('how monitoring works is folded into Details', (tester) async {
      final controller = await _notifyController(servers: 1);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      final fold = _key('notifications-monitor-details');
      await tester.ensureVisible(fold);
      await tester.pumpAndSettle();
      expect(find.text(_en.notifyMonitorDetails), findsOneWidget);
      expect(find.text(_en.monitorScope), findsNothing);
      await tester.tap(find.text(_en.notifyMonitorDetails));
      await tester.pumpAndSettle();
      expect(find.textContaining(_en.monitorScope), findsOneWidget);
      expect(_key('notify-no-servers'), findsNothing);
    });

    testWidgets('the quiet time picker says which end it sets', (tester) async {
      final controller = await _notifyController();
      addTearDown(controller.dispose);
      await controller.updateSharedNotifyRules(
        (rules) => rules.copyWith(quietStart: 22 * 60, quietEnd: 7 * 60),
      );
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(_key('notify-quiet-end'));
      await tester.pumpAndSettle();
      await tester.tap(_key('notify-quiet-end'));
      await tester.pumpAndSettle();
      expect(find.text(_en.notifyQuietEndPicker), findsOneWidget);
      expect(find.text(_en.notifyQuietSet), findsOneWidget);
    });

    testWidgets('the same start and end are explained as all day', (
      tester,
    ) async {
      final controller = await _notifyController();
      addTearDown(controller.dispose);
      await controller.updateSharedNotifyRules(
        (rules) => rules.copyWith(quietStart: 9 * 60, quietEnd: 9 * 60),
      );
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(_key('notify-quiet-end'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: _key('notify-quiet-end'),
          matching: find.text(_en.notifyQuietAllDay, findRichText: true),
        ),
        findsOneWidget,
      );
    });
  });

  group('privacy-settings', () {
    testWidgets('the delete confirmation carries the counted verb', (
      tester,
    ) async {
      final controller = await _privacyController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(PrivacySettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();

      await tester.tap(_key('clear-session-drafts'));
      await tester.pumpAndSettle();
      expect(find.text(_en.e7SettingsUi85), findsOneWidget);
      expect(find.text('Delete 3 drafts'), findsOneWidget);
      await tester.tap(find.text('Delete 3 drafts'));
      await tester.pumpAndSettle();

      expect(controller.drafts, 0);
      // Said on the page, never in a snackbar.
      expect(find.byType(SnackBar), findsNothing);
      expect(
        find.descendant(
          of: _key('privacy-clear-result'),
          matching: find.text(_en.e7SettingsUi86),
        ),
        findsOneWidget,
      );
    });

    testWidgets('a failed delete says so and keeps the drafts', (tester) async {
      final controller = await _privacyController()
        ..clearWorks = false;
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(PrivacySettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      await tester.tap(_key('clear-session-drafts'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete 3 drafts'));
      await tester.pumpAndSettle();
      expect(controller.drafts, 3);
      expect(find.text(_en.e7SettingsUi87), findsOneWidget);
    });

    testWidgets('the policy is one row away', (tester) async {
      final controller = await _privacyController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(PrivacySettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      expect(_key('privacy-policy'), findsOneWidget);
    });
  });
}
