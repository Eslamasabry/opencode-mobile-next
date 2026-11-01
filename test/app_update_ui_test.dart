// The app's own update as a person meets it: the status line, the dot on the
// Settings gear, the sheet the gear opens, the Updates page and its search.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/updates_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:opencode_mobile/update/app_release.dart';
import 'package:opencode_mobile/update/app_update_controller.dart';
import 'package:opencode_mobile/update/app_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/app_update_fakes.dart';
import 'support/fake_server_gateway.dart';

final _en = lookupAppLocalizations(const Locale('en'));

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  Future<List<WorkspaceProject>> listProjects() async => [];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];
  @override
  Future<List<TerminalProcess>> listTerminals() async => [];
  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => [
    ServerProfile(id: 'local', name: 'Laptop', baseUrl: 'http://localhost'),
  ];
  @override
  String? get activeId => 'local';
}

/// Counts what the person's choice reaches without downloading anything.
class _Spy extends AppUpdateController {
  _Spy({
    required super.source,
    required super.platform,
    required super.prefs,
    required super.installed,
    required super.cacheDir,
  });
  int updates = 0;

  @override
  Future<void> updateNow() async => updates++;
}

late Directory _dir;

AppUpdateController _controller({bool spy = false, int installed = 57}) {
  Future<InstalledApp> app() async => InstalledApp(
    packageName: 'io.github.eslamasabry.opencode_mobile',
    version: '2.1.0',
    build: installed,
  );
  final source = FakeUpdateSource()..latest = null;
  final platform = FakeUpdatePlatform();
  return spy
      ? _Spy(
          source: source,
          platform: platform,
          prefs: SharedPreferences.getInstance,
          installed: app,
          cacheDir: () async => _dir,
        )
      : AppUpdateController(
          source: source,
          platform: platform,
          prefs: SharedPreferences.getInstance,
          installed: app,
          cacheDir: () async => _dir,
        );
}

/// A controller that already knows a newer release.
Future<AppUpdateController> _withRelease({
  bool spy = false,
  FakeUpdateSource? source,
  FakeUpdatePlatform? platform,
}) async {
  final src =
      source ??
      (FakeUpdateSource()..latest = AppRelease.fromJson(updateReleaseJson()));
  final c = spy
      ? _Spy(
          source: src,
          platform: platform ?? FakeUpdatePlatform(),
          prefs: SharedPreferences.getInstance,
          installed: () async => const InstalledApp(
            packageName: 'io.github.eslamasabry.opencode_mobile',
            version: '2.1.0',
            build: 57,
          ),
          cacheDir: () async => _dir,
        )
      : AppUpdateController(
          source: src,
          platform: platform ?? FakeUpdatePlatform(),
          prefs: SharedPreferences.getInstance,
          installed: () async => const InstalledApp(
            packageName: 'io.github.eslamasabry.opencode_mobile',
            version: '2.1.0',
            build: 57,
          ),
          cacheDir: () async => _dir,
        );
  await c.start();
  return c;
}

Future<ConnectionController> _conn() async {
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(_Store(prefs: prefs))
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = '/srv/app';
}

Future<void> _pumpHome(
  WidgetTester tester,
  ConnectionController conn,
  AppUpdateController updates, {
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        connProvider.overrideWithValue(conn),
        appUpdateProvider.overrideWithValue(updates),
      ],
      child: MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: AppShortcutScope(
          signals: AppShortcutSignals(),
          perform: (_) {},
          child: const HomeScreen(),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Finder get _gear => find.byKey(const ValueKey('home-shell-settings'));
double _dot(WidgetTester tester) => tester
    .widgetList<AnimatedOpacity>(find.byKey(const ValueKey('kit-update-dot')))
    .first
    .opacity;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    _dir = await Directory.systemTemp.createTemp('app_update_ui');
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    debugPlatformCapabilities = const PlatformCapabilities.android();
  });
  tearDown(() async {
    debugPlatformCapabilities = null;
    await _dir.delete(recursive: true);
  });

  group('the Settings gear', () {
    testWidgets(
      'carries a dot and "Settings, update available" while one waits',
      (tester) async {
        final conn = await _conn();
        addTearDown(conn.dispose);
        final updates = await _withRelease();
        await _pumpHome(tester, conn, updates);
        expect(_dot(tester), 1);
        expect(
          find.bySemanticsLabel('Settings, update available'),
          findsOneWidget,
        );
      },
    );

    testWidgets('is the plain gear when nothing waits', (tester) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      await _pumpHome(tester, conn, _controller());
      expect(_dot(tester), 0);
      expect(find.bySemanticsLabel('Settings, update available'), findsNothing);
      expect(find.bySemanticsLabel('Settings'), findsOneWidget);
    });

    testWidgets('Not now on the notice keeps the dot', (tester) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      final updates = await _withRelease();
      updates.notNow();
      await _pumpHome(tester, conn, updates);
      expect(_dot(tester), 1);
    });

    testWidgets('a tap asks: Update now or Open Settings', (tester) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      final updates = await _withRelease(spy: true);
      await _pumpHome(tester, conn, updates);
      await tester.tap(_gear);
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsOneWidget);
      expect(find.text('This release is faster.'), findsOneWidget);
      expect(find.text("What's new"), findsOneWidget);
      expect(find.text('Update now'), findsOneWidget);
      expect(find.text('Open Settings'), findsOneWidget);
      expect(find.byType(SettingsScreen), findsNothing);

      await tester.tap(find.text('Open Settings'));
      await tester.pumpAndSettle();
      expect(find.byType(SettingsScreen), findsOneWidget);
      expect((updates as _Spy).updates, 0);
    });

    testWidgets("What's new goes through the link check", (tester) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      final updates = await _withRelease(spy: true);
      await _pumpHome(tester, conn, updates);
      await tester.tap(_gear);
      await tester.pumpAndSettle();
      await tester.tap(find.text("What's new"));
      await tester.pumpAndSettle();
      // openExternalLink names where it goes before anything opens.
      expect(find.textContaining('github.com'), findsWidgets);
      expect((updates as _Spy).updates, 0);
    });

    testWidgets('the rail carries the dot on a wide screen', (tester) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      final updates = await _withRelease();
      await _pumpHome(tester, conn, updates, size: const Size(900, 900));
      expect(find.byType(KitNavRail), findsOneWidget);
      // Every destination has a dot slot; only Settings' is shown.
      final shown = tester
          .widgetList<AnimatedOpacity>(
            find.byKey(const ValueKey('kit-update-dot')),
          )
          .where((o) => o.opacity == 1);
      expect(shown, hasLength(1));
      expect(find.bySemanticsLabel('Settings, update available'), findsWidgets);
    });

    testWidgets('Update now starts the update and stays on the home', (
      tester,
    ) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      final updates = await _withRelease(spy: true);
      await _pumpHome(tester, conn, updates);
      await tester.tap(_gear);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Update now'));
      await tester.pumpAndSettle();
      expect((updates as _Spy).updates, 1);
      expect(find.byType(SettingsScreen), findsNothing);
    });

    testWidgets('Back closes the sheet and goes nowhere', (tester) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      final updates = await _withRelease(spy: true);
      await _pumpHome(tester, conn, updates);
      await tester.tap(_gear);
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsNothing);
      expect(find.byType(SettingsScreen), findsNothing);
      expect((updates as _Spy).updates, 0);
    });

    testWidgets('with nothing waiting the gear goes straight to Settings', (
      tester,
    ) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      await _pumpHome(tester, conn, _controller());
      await tester.tap(_gear);
      await tester.pumpAndSettle();
      expect(find.byType(SettingsScreen), findsOneWidget);
    });
  });

  group('the status line', () {
    Widget app(AppUpdateController c) => MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) =>
          AppUpdateNotice(controller: c, child: child!),
      home: KitScreen(
        topBar: const KitTopBar(title: 'Work'),
        body: ListView(children: const [Text('OpenCode')]),
      ),
    );

    testWidgets(
      'offers "Version 2.2.0 is ready"; Not now hides only the offer',
      (tester) async {
        final c = await _withRelease();
        await tester.pumpWidget(app(c));
        await tester.pumpAndSettle();
        expect(find.text('Version 2.2.0 is ready'), findsOneWidget);
        expect(find.text('Update'), findsOneWidget);
        await tester.tap(find.byTooltip('Not now'));
        await tester.pumpAndSettle();
        expect(find.text('Version 2.2.0 is ready'), findsNothing);
        expect(c.hasPendingRelease, isTrue);
      },
    );

    testWidgets('steps aside while the Updates page is open', (tester) async {
      final c = await _withRelease();
      final nav = GlobalKey<NavigatorState>();
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            navigatorKey: nav,
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) =>
                AppUpdateNotice(controller: c, child: child!),
            home: KitScreen(
              topBar: const KitTopBar(title: 'Work'),
              body: ListView(children: const [Text('OpenCode')]),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsOneWidget);
      unawaited(
        nav.currentState!.push(
          MaterialPageRoute<void>(builder: (_) => UpdatesScreen(controller: c)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsNothing);
      expect(find.text('2.2.0 is ready · 39 MB'), findsOneWidget);
      nav.currentState!.pop();
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsOneWidget);
    });

    testWidgets('a patch alone says to restart', (tester) async {
      final c = _controller();
      c.patchReady();
      await tester.pumpWidget(app(c));
      await tester.pumpAndSettle();
      expect(find.text('App update ready'), findsOneWidget);
    });

    testWidgets('a release replaces the ready patch line', (tester) async {
      final c = await _withRelease();
      c.patchReady();
      await tester.pumpWidget(app(c));
      await tester.pumpAndSettle();
      expect(find.text('Version 2.2.0 is ready'), findsOneWidget);
      expect(find.text('App update ready'), findsNothing);
    });
  });

  group('the Updates page', () {
    Future<void> show(WidgetTester tester, AppUpdateController c) async {
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: UpdatesScreen(controller: c),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('up to date: the version and when it looked', (tester) async {
      final src = FakeUpdateSource()..latest = null;
      final c = await _withRelease(source: src);
      await tester.runAsync(c.checkNow);
      await show(tester, c);
      expect(find.text('Version 2.1.0'), findsOneWidget);
      expect(find.text('OpenCode Mobile 2.1.0+57'), findsNothing);
      expect(find.text('Up to date · checked just now'), findsOneWidget);
      expect(find.byKey(const ValueKey('updates-update')), findsNothing);
    });

    testWidgets('available: the version, its size and an Update button', (
      tester,
    ) async {
      final c = await _withRelease();
      await show(tester, c);
      expect(find.textContaining('2.2.0 is ready · 39 MB'), findsOneWidget);
      expect(find.byKey(const ValueKey('updates-update')), findsOneWidget);
      expect(find.text('Check GitHub for new versions'), findsOneWidget);
      expect(
        find.text(
          'The app asks GitHub for the latest version number and sends nothing about you.',
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('updates-notes')), findsOneWidget);
    });

    testWidgets(
      'a refused download says so, and offers the release, not a retry',
      (tester) async {
        final platform = FakeUpdatePlatform()..signer = 'BB22';
        final c = await _withRelease(platform: platform);
        await tester.runAsync(c.updateNow);
        await show(tester, c);
        expect(
          find.text(
            "This download isn't signed like your app, so it wasn't installed.",
          ),
          findsOneWidget,
        );
        expect(find.textContaining('BB22'), findsNothing);
        expect(find.text('Open on GitHub'), findsOneWidget);
        expect(find.text('Try again'), findsNothing);
      },
    );

    testWidgets(
      'after a refusal: the line sits under the row, Details after, and the '
      'row says what is true',
      (tester) async {
        final platform = FakeUpdatePlatform()..signer = 'BB22';
        final c = await _withRelease(platform: platform);
        await tester.runAsync(c.updateNow);
        await show(tester, c);
        final row = tester.getTopLeft(
          find.byKey(const ValueKey('updates-state')),
        );
        final line = tester.getTopLeft(
          find.byKey(const ValueKey('updates-failure')),
        );
        final details = tester.getTopLeft(
          find.byKey(const ValueKey('updates-build-details')),
        );
        expect(row.dy, lessThan(line.dy));
        expect(line.dy, lessThan(details.dy));
        expect(find.text("2.2.0 couldn't be installed"), findsOneWidget);
        expect(find.textContaining('is ready'), findsNothing);
      },
    );

    testWidgets(
      'the short action leaves the title one line on a narrow phone',
      (tester) async {
        tester.view
          ..physicalSize = const Size(360, 900)
          ..devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final platform = FakeUpdatePlatform()..signer = 'BB22';
        final c = await _withRelease(platform: platform);
        await tester.runAsync(c.updateNow);
        await tester.pumpWidget(
          ProviderScope(
            child: MaterialApp(
              theme: AppTheme.dark(),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: UpdatesScreen(controller: c),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Open on GitHub'), findsOneWidget);
        // The test font is wide: two lines at most. The long label made three.
        expect(tester.getSize(find.text('Version 2.1.0')).height, lessThan(50));
      },
    );

    testWidgets('a network failure keeps Try again', (tester) async {
      final source = FakeUpdateSource()
        ..latest = AppRelease.fromJson(updateReleaseJson())
        ..downloadError = const SocketException('offline');
      final c = await _withRelease(source: source);
      await tester.runAsync(c.updateNow);
      await show(tester, c);
      expect(find.text('Try again'), findsOneWidget);
      expect(find.text('Open on GitHub'), findsNothing);
    });

    testWidgets('the build number is under Details, not in the title', (
      tester,
    ) async {
      final c = await _withRelease();
      await show(tester, c);
      expect(find.text('Version 2.1.0'), findsOneWidget);
      expect(find.textContaining('+57'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('updates-build-details')));
      await tester.pumpAndSettle();
      expect(find.textContaining('2.1.0+57'), findsOneWidget);
    });

    testWidgets('the switch turns checking off', (tester) async {
      final c = await _withRelease();
      await show(tester, c);
      await tester.tap(find.byKey(const ValueKey('updates-check-switch')));
      await tester.pumpAndSettle();
      expect(c.enabled, isFalse);
    });
  });

  group('settings search', () {
    Future<List<String>> ids(
      String query,
      PlatformCapabilities platform,
    ) async {
      final conn = await _conn();
      addTearDown(conn.dispose);
      return [
        for (final e in searchEntries(
          _en,
          SearchScope(
            controller: conn,
            platform: platform,
            hasShell: true,
            thermalGuard: true,
          ),
          query,
        ))
          e.id,
      ];
    }

    test(
      '"update" finds Updates on Android and not where Android cannot install',
      () async {
        expect(
          await ids('update', const PlatformCapabilities.android()),
          contains('settings-updates'),
        );
        expect(
          await ids('update', const PlatformCapabilities.linuxDesktop()),
          isNot(contains('settings-updates')),
        );
      },
    );
  });
}
