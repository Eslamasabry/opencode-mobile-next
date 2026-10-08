import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/notification_preferences.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_monitor.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/profile_monitor_fixture.dart';

final _en = lookupAppLocalizations(const Locale('en'));
const _hash =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

/// What the quota monitor stored for one source before the merge: its own
/// alerts, Wi-Fi and quiet-hours answers.
const _legacyQuotaRules = QuotaMonitorRules(
  source: _hash,
  account: _hash,
  token: _hash,
  notifications: true,
);

Future<ConnectionController> _controller({
  Map<String, Object> legacy = const {},
  int servers = 2,
  bool notificationGranted = true,
  // Android has stopped the service at its time limit: its own answer to
  // the next read of the pause says so.
  bool timedOut = false,
  void Function(String method)? onInvoke,
}) async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
    ...legacy,
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
      // What Android says about the background connection: still allowed,
      // or stopped at its time limit (then every status carries it).
      final pause = {
        'supported': true,
        'active': false,
        'paused': timedOut,
        'reason': timedOut ? 'timeLimit' : 'none',
        'at': timedOut ? 1791428400000 : null,
        'canResume': timedOut,
      };
      if (method == 'getBackgroundPause') return pause;

      onInvoke?.call(method);
      if (method == 'showCodingAlert') {
        return {'shown': notificationGranted};
      }
      return {
        'enabled': !timedOut && method != 'disable',
        'active': !timedOut && method != 'disable',
        'notificationGranted': notificationGranted,
        'batteryOptimizationIgnored': false,
        if (timedOut) 'backgroundPause': pause,
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

Widget _app(Widget home, {Locale locale = const Locale('en')}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Finder _key(String key) => find.byKey(ValueKey(key));

Future<void> _tapSwitch(WidgetTester tester, String key) async {
  final target = _key(key);
  await tester.ensureVisible(target);
  await tester.pump();
  await tester.tap(find.descendant(of: target, matching: find.byType(Switch)));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

/// The monitor keeps a poll timer while its controller lives; the binding
/// checks for pending timers before tear-downs run, so end each test here.
Future<void> _finish(
  WidgetTester tester,
  ConnectionController controller,
) async {
  await tester.pumpWidget(const SizedBox.shrink());
  controller.dispose();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('four sections, in the spec order, each with its rows', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();

    var previous = double.negativeInfinity;
    // The merged page: what notifies, quiet hours, saved servers, then the
    // background connection.
    for (final slug in ['what', 'quiet', 'servers', 'background']) {
      final section = _key('notifications-section-$slug');
      expect(section, findsOneWidget, reason: slug);
      final top = tester.getTopLeft(section).dy;
      expect(top, greaterThan(previous), reason: '$slug is out of order');
      previous = top;
    }
    Finder inside(String slug, String key) => find.descendant(
      of: _key('notifications-section-$slug'),
      matching: _key(key),
    );
    for (final key in [
      'notify-finished-runs',
      'notify-requests',
      'notify-check-ins',
      'notify-quota-alerts',
    ]) {
      expect(inside('what', key), findsOneWidget, reason: key);
    }
    expect(inside('quiet', 'notify-quiet-hours'), findsOneWidget);
    for (final key in [
      'background-live-switch',
      'background-battery-row',
      'background-status-row',
    ]) {
      expect(inside('background', key), findsOneWidget, reason: key);
    }
    for (final key in [
      'monitor-enabled-profile-1',
      'monitor-enabled-profile-2',
      'notify-wifi-only',
    ]) {
      expect(inside('servers', key), findsOneWidget, reason: key);
    }
    // Exactly one of each shared control on the whole screen.
    // The switch names itself; no section header repeats it (R4).
    expect(find.text(_en.monitorQuiet), findsOneWidget);
    expect(find.text(_en.notifyWifiOnly), findsOneWidget);
    // "Check in after" and "Notify" appear only once they apply.
    expect(_key('notify-check-in-after'), findsNothing);
    expect(_key('monitor-notify-profile-1'), findsNothing);
    await _finish(tester, controller);
  });

  testWidgets('rows this device cannot do are absent, and so are empty '
      'sections', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.linux,
    );
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();

    // No notifications and no background service off Android.
    expect(_key('notify-finished-runs'), findsNothing);
    expect(_key('notify-requests'), findsNothing);
    expect(_key('notify-quota-alerts'), findsNothing);
    expect(_key('notifications-section-quiet'), findsNothing);
    expect(_key('notifications-section-background'), findsNothing);
    expect(find.text(_en.notifySectionBackground), findsNothing);
    expect(_key('notify-wifi-only'), findsNothing);
    // What still works in the open app stays.
    expect(_key('notify-check-ins'), findsOneWidget);
    expect(find.text(_en.monitorCheckInDetailForeground), findsOneWidget);
    expect(_key('monitor-enabled-profile-1'), findsOneWidget);

    await _tapSwitch(tester, 'monitor-enabled-profile-1');
    expect(controller.profileMonitor.rulesFor('profile-1').enabled, isTrue);
    // "Notify" needs notifications.
    expect(_key('monitor-notify-profile-1'), findsNothing);
    await _finish(tester, controller);
  });

  testWidgets('with no saved server the servers section keeps only Wi-Fi', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller(servers: 0);
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();
    expect(_key('notifications-section-servers'), findsOneWidget);
    expect(_key('notify-wifi-only'), findsOneWidget);
    expect(find.text(_en.monitorScope, findRichText: true), findsNothing);

    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.linux,
    );
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();
    expect(_key('notifications-section-servers'), findsNothing);
    expect(find.text(_en.notifySectionServers), findsNothing);
    await _finish(tester, controller);
  });

  testWidgets('the one quiet-hours pair is what both consumers read', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();

    final night = DateTime(2026, 1, 1, 23);
    final noon = DateTime(2026, 1, 1, 12);
    expect(_key('notify-quiet-start'), findsNothing);
    expect(controller.quotaMonitor.quietFor(_legacyQuotaRules, night), isFalse);

    await _tapSwitch(tester, 'notify-quiet-hours');
    expect(_key('notify-quiet-start'), findsOneWidget);
    expect(_key('notify-quiet-end'), findsOneWidget);

    // Saved-server monitoring, every server, and the quota monitor: the same
    // stored pair, not a copy each.
    for (final id in ['profile-1', 'profile-2']) {
      final rules = controller.profileMonitor.rulesFor(id);
      expect(rules.quietStart, 22 * 60, reason: id);
      expect(rules.quietEnd, 8 * 60, reason: id);
      expect(rules.quietAt(night), isTrue);
      expect(rules.quietAt(noon), isFalse);
    }
    expect(controller.quotaMonitor.quietFor(_legacyQuotaRules, night), isTrue);
    expect(controller.quotaMonitor.quietFor(_legacyQuotaRules, noon), isFalse);
    final prefs = controller.store.prefs;
    expect(prefs.getInt(NotificationPreferences.quietStartKey), 22 * 60);
    expect(prefs.getInt(NotificationPreferences.quietEndKey), 8 * 60);

    // Editing the start edits it for both.
    await tester.tap(_key('notify-quiet-start'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.keyboard_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).first, '11');
    await tester.enterText(find.byType(TextField).last, '30');
    // The picker's button says what it does (477e2075): "Set".
    await tester.tap(find.text(_en.notifyQuietSet));
    await tester.pumpAndSettle();
    final start = prefs.getInt(NotificationPreferences.quietStartKey)!;
    expect(start % 720, 11 * 60 + 30);
    expect(controller.profileMonitor.rulesFor('profile-2').quietStart, start);
    expect(controller.sharedNotifyRules.quietStart, start);

    await _tapSwitch(tester, 'notify-quiet-hours');
    expect(controller.profileMonitor.rulesFor('profile-1').quietStart, isNull);
    expect(controller.quotaMonitor.quietFor(_legacyQuotaRules, night), isFalse);
    await _finish(tester, controller);
  });

  testWidgets('the one Wi-Fi-only toggle is what both consumers read', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();

    expect(controller.profileMonitor.rulesFor('profile-1').wifiOnly, isFalse);
    expect(controller.quotaMonitor.wifiOnlyFor(_legacyQuotaRules), isFalse);
    await _tapSwitch(tester, 'notify-wifi-only');
    expect(controller.profileMonitor.rulesFor('profile-1').wifiOnly, isTrue);
    expect(controller.profileMonitor.rulesFor('profile-2').wifiOnly, isTrue);
    expect(controller.quotaMonitor.wifiOnlyFor(_legacyQuotaRules), isTrue);
    expect(
      controller.store.prefs.getBool(NotificationPreferences.wifiOnlyKey),
      isTrue,
    );
    await _finish(tester, controller);
  });

  testWidgets('legacy values show on first open and migrate on first edit', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller(
      legacy: {
        ProfileMonitor.rulesKey('profile-2'): jsonEncode(
          const ProfileNotifyRules(
            enabled: true,
            quietStart: 21 * 60,
            quietEnd: 7 * 60,
            checkInAfterMinutes: 60,
          ).toJson(),
        ),
        ProviderQuotaMonitor.key('profile-1'): jsonEncode({
          'version': 1,
          'rules': {
            QuotaProvider.codex.name: const QuotaMonitorRules(
              source: _hash,
              account: _hash,
              token: _hash,
              notifications: true,
              wifiOnly: true,
              quietStart: 22 * 60,
              quietEnd: 8 * 60,
            ).toJson(),
          },
        }),
      },
    );
    expect(controller.notificationPreferences.migrated, isFalse);
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();

    // Not migrated yet, and the screen already tells the truth.
    Switch toggle(String key) => tester.widget<Switch>(
      find.descendant(of: _key(key), matching: find.byType(Switch)),
    );
    expect(toggle('notify-quiet-hours').value, isTrue);
    expect(toggle('notify-wifi-only').value, isTrue);
    expect(toggle('notify-quota-alerts').value, isTrue);
    expect(toggle('notify-check-ins').value, isTrue);
    expect(_key('notify-check-in-after'), findsOneWidget);
    // KitPickerRow isolates the value's direction (477e2075).
    expect(find.textContaining(_en.monitorMinutes(60)), findsOneWidget);

    await _tapSwitch(tester, 'notify-wifi-only');
    expect(controller.notificationPreferences.migrated, isTrue);
    final shared = controller.notificationPreferences.shared!;
    // The saved-server pair beat the quota monitor's 22:00–08:00.
    expect(shared.quietStart, 21 * 60);
    expect(shared.quietEnd, 7 * 60);
    expect(shared.wifiOnly, isFalse);
    expect(shared.quotaAlerts, isTrue);
    expect(shared.checkInAfterMinutes, 60);
    await _finish(tester, controller);
  });

  testWidgets('"what notifies me" and per-server rows write through', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller(servers: 1);
    await tester.pumpWidget(
      _app(NotificationsSettingsScreen(controller: controller)),
    );
    await tester.pump();

    await _tapSwitch(tester, 'notify-finished-runs');
    expect(controller.notificationPreferences.finishedRuns, isFalse);
    await _tapSwitch(tester, 'notify-requests');
    expect(controller.notificationPreferences.requests, isFalse);
    await _tapSwitch(tester, 'notify-quota-alerts');
    expect(controller.sharedNotifyRules.quotaAlerts, isTrue);

    await _tapSwitch(tester, 'notify-check-ins');
    expect(
      controller.profileMonitor.rulesFor('profile-1').checkInAfterMinutes,
      30,
    );
    await tester.tap(_key('notify-check-in-after'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(_en.monitorMinutes(15)).last);
    await tester.pumpAndSettle();
    expect(
      controller.profileMonitor.rulesFor('profile-1').checkInAfterMinutes,
      15,
    );
    expect(
      controller.store.prefs.getInt(NotificationPreferences.checkInKey),
      15,
    );

    await _tapSwitch(tester, 'monitor-enabled-profile-1');
    expect(controller.profileMonitor.rulesFor('profile-1').enabled, isTrue);
    await _tapSwitch(tester, 'monitor-notify-profile-1');
    final stored = jsonDecode(
      controller.store.prefs.getString(ProfileMonitor.rulesKey('profile-1'))!,
    );
    expect(stored['enabled'], isTrue);
    expect(stored['notifications'], isFalse);
    await _finish(tester, controller);
  });

  testWidgets('the hub row opens this screen', (tester) async {
    final controller = await _controller();
    await tester.pumpWidget(_app(SettingsScreen(controller: controller)));
    await tester.pumpAndSettle();
    final row = _key('settings-category-background');
    await tester.ensureVisible(row);
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: row,
        matching: find.text(_en.settingsHubGroupNotifications),
      ),
      findsOneWidget,
    );
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(find.byType(NotificationsSettingsScreen), findsOneWidget);
    expect(_key('notifications-settings'), findsOneWidget);
    await _finish(tester, controller);
  });

  group('P0.6: Android blocking this app\'s notifications', () {
    testWidgets('shows the notice, blocks the switches, and Open Android '
        'settings calls the platform', (tester) async {
      tester.view.physicalSize = const Size(400, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final calls = <String>[];
      final controller = await _controller(
        notificationGranted: false,
        onInvoke: calls.add,
      );
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pump();

      expect(_key('notifications-blocked-notice'), findsOneWidget);
      // Said once, in the notice (R3); each switch keeps its own line.
      expect(find.text(_en.notifyBlockedTitle), findsOneWidget);
      expect(find.text(_en.notifyFinishedRunsDetail), findsOneWidget);
      expect(find.text(_en.notifyTurnOnInAndroid), findsOneWidget);
      // The row is a KitSwitchRow since 477e2075; its switch is the control.
      final finishedRuns = tester.widget<Switch>(
        find.descendant(
          of: _key('notify-finished-runs'),
          matching: find.byType(Switch),
        ),
      );
      expect(finishedRuns.onChanged, isNull);

      await tester.ensureVisible(_key('notifications-open-settings'));
      await tester.pump();
      await tester.tap(_key('notifications-open-settings'));
      await tester.pump();
      expect(calls, contains('openAppSettings'));
      await _finish(tester, controller);
    });

    testWidgets('is absent and the switches work once granted', (tester) async {
      tester.view.physicalSize = const Size(400, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller();
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pump();
      expect(_key('notifications-blocked-notice'), findsNothing);
      // The row is a KitSwitchRow since 477e2075; its switch is the control.
      final finishedRuns = tester.widget<Switch>(
        find.descendant(
          of: _key('notify-finished-runs'),
          matching: find.byType(Switch),
        ),
      );
      expect(finishedRuns.onChanged, isNotNull);
      await _finish(tester, controller);
    });

    testWidgets('re-checks on resume, the honest state rule', (tester) async {
      tester.view.physicalSize = const Size(400, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      final preferences = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: preferences);
      await store.load();
      // Flips as if the person granted the permission in Android settings
      // and returned to the app; the fake platform reports whatever this
      // is set to on every call, exactly like the real one would.
      var granted = false;
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
            'enabled': true,
            'active': true,
            'notificationGranted': granted,
            'batteryOptimizationIgnored': false,
          };
        },
      );
      await live.restore();
      final controller = ConnectionController(
        store,
        backgroundLive: live,
        monitorGatewayFactory: (_) => (
          gateway: MonitorTestGateway(),
          operations: MonitorTestOperations(),
        ),
      );
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pump();
      expect(_key('notifications-blocked-notice'), findsOneWidget);

      granted = true;
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(_key('notifications-blocked-notice'), findsNothing);
      await _finish(tester, controller);
    });

    testWidgets('Send a test notification proves delivery', (tester) async {
      tester.view.physicalSize = const Size(400, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final calls = <String>[];
      final controller = await _controller(onInvoke: calls.add);
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pump();
      expect(find.text(_en.notifySendTest), findsOneWidget);
      await tester.ensureVisible(_key('notify-send-test'));
      await tester.pump();
      await tester.tap(_key('notify-send-test'));
      await tester.pumpAndSettle();
      expect(calls, contains('showCodingAlert'));
      await _finish(tester, controller);
    });

    testWidgets('proves the block too: blocked, nothing is shown', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(400, 3000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller(notificationGranted: false);
      await tester.pumpWidget(
        _app(NotificationsSettingsScreen(controller: controller)),
      );
      await tester.pump();
      expect(await controller.backgroundLive.sendTestNotification(), isFalse);
      await _finish(tester, controller);
    });
  });

  group('layout at 320 dp and 2.5x text', () {
    // The timeout notice is an Android row: lay it out as Android, not as
    // the host the tests run on.
    setUp(
      () => debugPlatformCapabilities = const PlatformCapabilities.android(),
    );
    tearDown(() => debugPlatformCapabilities = null);

    for (final locale in const [Locale('en'), Locale('ar')]) {
      testWidgets('no overflow in ${locale.languageCode}', (tester) async {
        const phone = Size(320, 640);
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = phone;
        addTearDown(tester.view.reset);
        // Everything on: every conditional row is laid out.
        final controller = await _controller(
          timedOut: true,
          legacy: {
            for (final id in ['profile-1', 'profile-2'])
              ProfileMonitor.rulesKey(id): jsonEncode(
                const ProfileNotifyRules(
                  enabled: true,
                  quietStart: 21 * 60,
                  quietEnd: 7 * 60,
                  checkInAfterMinutes: 60,
                ).toJson(),
              ),
          },
        );
        controller.backgroundLive.handleNativeTimeout(const {
          'reason': 'systemTimeout',
        });

        await tester.pumpWidget(
          MediaQuery(
            data: const MediaQueryData(
              size: phone,
              textScaler: TextScaler.linear(AppTheme.maxTextScale),
            ),
            child: _app(
              NotificationsSettingsScreen(controller: controller),
              locale: locale,
            ),
          ),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));
        expect(tester.takeException(), isNull);
        expect(
          Directionality.of(tester.element(_key('notifications-settings'))),
          locale.languageCode == 'ar' ? TextDirection.rtl : TextDirection.ltr,
        );

        final scrollable = find.byType(Scrollable).first;
        for (final key in [
          'background-timeout-notice',
          'notify-check-in-after',
          'notify-quota-alerts',
          'notify-quiet-end',
          'background-status-row',
          'monitor-notify-profile-2',
          'notify-wifi-only',
        ]) {
          await tester.ensureVisible(_key(key));
          await tester.pump();
          expect(tester.takeException(), isNull, reason: key);
        }
        await tester.drag(scrollable, const Offset(0, -4000));
        await tester.pump();
        expect(tester.takeException(), isNull);
        for (final tile in tester.widgetList<ListTile>(find.byType(ListTile))) {
          final box = tester.renderObject<RenderBox>(find.byWidget(tile));
          expect(box.size.width, lessThanOrEqualTo(phone.width));
        }
        await _finish(tester, controller);
      });
    }
  });
}
