// P9.4 "Search that finds any setting": the shared index upgrade as a
// person uses it. Rows inside pages are found by their own words (animations,
// heat, crash, battery), with one typo, in either language; the retired
// aliases lead nowhere; and a row result opens its page arrived at the row.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show Health;
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ServerCapabilities;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/keep_running_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'revamp/screen_system_1_fixtures.dart';

final _en = lookupAppLocalizations(const Locale('en'));

/// Answers the hub's health check without a network.
class _Api extends SystemApi {
  _Api() : super(ServerCapabilities.allV1);

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

/// A connected controller; [termux] adds the phone's own OpenCode, run by
/// Termux, as a saved server.
Future<ConnectionController> _controller({bool termux = true}) async {
  mockSecureStorage();
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Workstation',
        'baseUrl': 'http://localhost:4096',
        'username': '',
      },
      if (termux)
        {
          'id': 'profile-2',
          'name': 'This phone',
          'baseUrl': 'http://127.0.0.1:4096',
          'username': '',
        },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return ConnectionController(store)
    ..api = _Api()
    ..repository = SystemRepository()
    ..status = StreamStatus.connected;
}

SearchScope _scope(
  ConnectionController controller, {
  bool thermalGuard = true,
  PlatformCapabilities platform = const PlatformCapabilities.android(),
}) => SearchScope(
  controller: controller,
  platform: platform,
  hasShell: true,
  thermalGuard: thermalGuard,
);

List<String> _ids(
  ConnectionController controller,
  String query, {
  AppLocalizations? l10n,
  bool thermalGuard = true,
  PlatformCapabilities platform = const PlatformCapabilities.android(),
}) => [
  for (final entry in searchEntries(
    l10n ?? _en,
    _scope(controller, thermalGuard: thermalGuard, platform: platform),
    query,
  ))
    entry.id,
];

Finder _key(String key) => find.byKey(ValueKey(key));

/// The arrival wash's opacity around the row keyed [row], or null.
double? _wash(WidgetTester tester, String row) {
  final opacity = find.descendant(
    of: find.ancestor(of: _key(row), matching: find.byType(KitArrival)),
    matching: find.byType(AnimatedOpacity),
  );
  if (opacity.evaluate().isEmpty) return null;
  return tester.widget<AnimatedOpacity>(opacity.first).opacity;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => debugPlatformCapabilities = const PlatformCapabilities.android());
  tearDown(() => debugPlatformCapabilities = null);

  group('the rows a person names', () {
    test('motion, heat, crash and battery lead to their rows', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      for (final (query, id) in [
        ('heat', 'inside-keep-running-thermal'),
        ('crash', 'inside-phone-crash-recovery'),
        ('battery', 'inside-keep-running-battery'),
        ('animations', 'inside-appearance-motion'),
        ('confetti', 'inside-appearance-motion'),
        ('glow', 'inside-appearance-glow'),
        ('border', 'inside-appearance-glow'),
      ]) {
        expect(_ids(controller, query).first, id, reason: query);
      }
      // "crash" also finds what went wrong, after the restart itself.
      expect(_ids(controller, 'crash'), contains('library-report-bug'));
      final byId = {
        for (final entry in searchIndex(_en, _scope(controller)))
          entry.id: entry,
      };
      final motion = byId['inside-appearance-motion']!;
      expect(motion.target?.pageId, 'appearance-settings');
      expect(motion.target?.rowId, 'effects-motion');
      expect(motion.parent, contains(_en.effectsSection));
      expect(byId.containsKey('inside-appearance-vibration'), isFalse);
      expect(byId.containsKey('inside-appearance-glass'), isFalse);
      expect(
        byId['inside-keep-running-thermal']!.target?.rowId,
        'keep-running-thermal',
      );
      expect(
        byId['inside-phone-crash-recovery']!.target?.rowId,
        'managed-recovery-option',
      );
    });

    test('one typo and a word begun still find the row', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      for (final query in ['ANIMA', 'batery']) {
        expect(
          _ids(controller, query).first,
          query.toLowerCase().startsWith('ani')
              ? 'inside-appearance-motion'
              : 'inside-keep-running-battery',
          reason: query,
        );
      }
      expect(_ids(controller, 'animations banana'), isEmpty);
      expect(_ids(controller, '  '), isEmpty);
    });

    test('bilingual aliases are kept, whichever language is shown', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final ar = lookupAppLocalizations(const Locale('ar'));
      // Arabic words while the app is in English, and back.
      expect(_ids(controller, 'حركة').first, 'inside-appearance-motion');
      expect(_ids(controller, 'حرارة').first, 'inside-keep-running-thermal');
      expect(
        _ids(controller, ar.settingsHubGroupNotifications),
        contains('settings-category-background'),
      );
      expect(
        _ids(controller, 'animations', l10n: ar).first,
        'inside-appearance-motion',
      );
      expect(
        _ids(controller, 'quiet hours', l10n: ar),
        contains('inside-notifications-quiet'),
      );
    });

    test('rows the phone does not have stay absent', () async {
      final controller = await _controller(termux: false);
      addTearDown(controller.dispose);
      // No heat guard running: no heat row (Keep running hides its switch).
      expect(
        _ids(controller, 'heat', thermalGuard: false),
        isNot(contains('inside-keep-running-thermal')),
      );
      // No Termux server: "crash" still finds the app's diagnostics.
      expect(_ids(controller, 'crash'), ['library-report-bug']);
      // A computer: neither Keep running nor its rows.
      final desktop = _ids(
        controller,
        'battery',
        platform: const PlatformCapabilities.linuxDesktop(),
      );
      expect(desktop, isNot(contains('inside-keep-running-battery')));
      expect(desktop, isNot(contains('settings-keep-running')));
    });

    test('the dead aliases lead nowhere', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      // Appearance has no text-size control; Privacy does not hold older
      // drafts; battery is Keep running's, not Notifications'.
      expect(
        _ids(controller, 'font'),
        isNot(contains('settings-category-appearance')),
      );
      expect(
        _ids(controller, 'text size'),
        isNot(contains('settings-category-appearance')),
      );
      expect(
        _ids(controller, 'Older drafts'),
        isNot(contains('settings-category-privacy')),
      );
      final battery = _ids(controller, 'battery');
      expect(battery, isNot(contains('settings-category-background')));
      expect(battery, isNot(contains('inside-notifications-background')));
    });
  });

  group('arrival', () {
    Widget app(ConnectionController controller) => ProviderScope(
      child: MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );

    /// Opens the result [id] of [query] the way the command launcher does:
    /// Settings has no search field of its own.
    Future<void> openResult(
      WidgetTester tester,
      ConnectionController controller,
      String query,
      String id,
    ) async {
      final context = tester.element(find.byType(SettingsScreen));
      final scope = SearchScope.of(context, controller);
      final entry = searchEntries(
        _en,
        scope,
        query,
      ).firstWhere((entry) => entry.id == id);
      unawaited(entry.open(context, scope));
    }

    void phone(WidgetTester tester) {
      tester.view
        ..physicalSize = const Size(390, 700)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }

    testWidgets('animations opens Appearance at the Motion row, once', (
      tester,
    ) async {
      phone(tester);
      // One saved server: no background monitor timers.
      final controller = await _controller(termux: false);
      addTearDown(controller.dispose);
      await tester.pumpWidget(app(controller));
      await tester.pumpAndSettle();

      final result = searchEntries(
        _en,
        _scope(controller),
        'animations',
      ).firstWhere((entry) => entry.id == 'inside-appearance-motion');
      expect(result.parent, contains(_en.effectsSection));
      await openResult(
        tester,
        controller,
        'animations',
        'inside-appearance-motion',
      );
      await tester.pumpAndSettle();

      expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
      final row = tester.getRect(_key('effects-motion'));
      expect(row.top, greaterThanOrEqualTo(0));
      expect(row.bottom, lessThanOrEqualTo(700));
      expect(_wash(tester, 'effects-motion'), 1);

      await tester.pump(KitArrival.hold);
      await tester.pumpAndSettle();
      expect(_wash(tester, 'effects-motion'), 0);
    });

    testWidgets(
      'battery opens Notifications and background at the battery step',
      (tester) async {
        phone(tester);
        mockKeepAlive(maker: 'Xiaomi');
        addTearDown(clearKeepAliveMock);
        // One saved server: no background monitor timers.
        final controller = await _controller(termux: false);
        addTearDown(controller.dispose);
        await tester.pumpWidget(app(controller));
        await tester.pumpAndSettle();

        await openResult(
          tester,
          controller,
          'battery',
          'inside-keep-running-battery',
        );
        await tester.pumpAndSettle();

        // Keep running is a section of Notifications and background now.
        expect(find.byType(NotificationsSettingsScreen), findsOneWidget);
        expect(find.byType(KeepRunningSection), findsOneWidget);
        expect(_key('keep-running-battery'), findsOneWidget);
        expect(_wash(tester, 'keep-running-battery'), 1);
        expect(_wash(tester, 'keep-running-autostart'), isNull);
        await tester.pump(KitArrival.hold);
        await tester.pumpAndSettle();
      },
    );
  });
}
