import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/nudges.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api(this._capabilities) : super(baseUrl: 'http://localhost');

  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _Repository
    implements ProductRepository, SessionImportGateway, UsageStatisticsGateway {
  @override
  bool get sessionImportSupported => true;

  _Repository({this.usageStatisticsSupported = true});

  @override
  final bool usageStatisticsSupported;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller({
  ServerCapabilities capabilities = ServerCapabilities.allV1,
  bool savedServer = true,
  bool usageStatistics = true,
  String baseUrl = 'http://localhost:4096',
}) async {
  SharedPreferences.setMockInitialValues({
    if (savedServer) ...{
      'oc.profiles': jsonEncode([
        {
          'id': 'profile-1',
          'name': 'Workstation',
          'baseUrl': baseUrl,
          'username': '',
        },
      ]),
      'oc.activeProfile': 'profile-1',
    },
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = _Api(capabilities)
    ..repository = _Repository(usageStatisticsSupported: usageStatistics)
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  Locale locale = const Locale('en'),
  SettingsGroup? initialGroup,
}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: SettingsScreen(controller: controller, initialGroup: initialGroup),
);

final _en = lookupAppLocalizations(const Locale('en'));

Finder _row(String key) => find.byKey(ValueKey(key));

/// The ids the app-wide search (the header's command launcher, which reads
/// the same index the hub is drawn from) finds for [query]. Settings has no
/// search field of its own any more.
List<String> _found(ConnectionController controller, String query) => [
  for (final entry in searchEntries(
    _en,
    SearchScope(controller: controller),
    query,
  ))
    entry.id,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // The phone is the product; Termux, voice, Tailscale and the background
  // service rows only exist there.
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    // ProfileStore reads passwords through flutter_secure_storage, whose
    // unmocked channel never answers inside testWidgets.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('the five groups appear in order, each keyed', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    // target-ia §1.3: the server under its own name (canvas), Agent,
    // Conversations, This app, then an unlabelled last panel.
    const slugs = ['server', 'agent', 'conversations', 'this-app', 'help'];
    expect(SettingsGroup.values.map((group) => group.slug), slugs);
    var previous = double.negativeInfinity;
    for (final slug in slugs) {
      final group = _row('settings-group-$slug');
      expect(group, findsOneWidget, reason: slug);
      final top = tester.getTopLeft(group).dy;
      expect(top, greaterThan(previous), reason: '$slug is out of order');
      previous = top;
    }
  });

  testWidgets(
    'Disconnect is not on Settings; the server page names it and explains it',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // No floating Disconnect below the Connection group any more.
      expect(_row('settings-disconnect'), findsNothing);
      expect(find.text(_en.e7SettingsUi8), findsNothing);

      await tester.ensureVisible(_row('settings-category-server'));
      await tester.pumpAndSettle();
      await tester.tap(_row('settings-category-server'));
      await tester.pumpAndSettle();

      final disconnect = _row('server-disconnect');
      await tester.scrollUntilVisible(
        disconnect,
        200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(
        find.descendant(
          of: disconnect,
          matching: find.text('Disconnect from Workstation'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: disconnect,
          matching: find.text(
            'Stops live updates from Workstation. Conversations stay on '
            'Workstation; unsent messages stay on this phone until you '
            'reconnect.',
          ),
        ),
        findsOneWidget,
      );
      // Last on the page, one section gap below the rest: no divider.
      expect(_row('server-disconnect-divider'), findsNothing);
      expect(
        tester.getTopLeft(disconnect).dy,
        greaterThan(
          tester
              .getBottomLeft(find.byKey(const Key('server-authentication')))
              .dy,
        ),
      );
      // The page is titled with the server; no identity row repeats it.
      expect(find.byKey(const Key('server-identity')), findsNothing);
    },
  );

  testWidgets(
    'the launcher finds disconnect and lands on the server page row',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      final scope = SearchScope(controller: controller);
      final result = searchEntries(
        _en,
        scope,
        'disconnect',
      ).firstWhere((entry) => entry.id == 'inside-server-disconnect');
      expect(result.parent, _en.settingsHubThisServer);

      unawaited(
        result.open(tester.element(find.byType(SettingsScreen)), scope),
      );
      await tester.pumpAndSettle();
      expect(find.byType(ServerSettingsScreen), findsOneWidget);
      final row = _row('server-disconnect');
      expect(row, findsOneWidget);
      // Opened at the row: it is on screen without scrolling.
      final screen = tester.view.physicalSize / tester.view.devicePixelRatio;
      expect(tester.getBottomLeft(row).dy, lessThanOrEqualTo(screen.height));
    },
  );

  testWidgets('search finds every hub row by its title and spec keywords', (
    tester,
  ) async {
    final controller = await _controller(
      capabilities: const ServerCapabilities(agentAccount: true),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    // query -> keys of rows that must be among the results.
    final cases = <String, List<String>>{
      // Titles.
      _en.settingsHubThisServer: ['settings-category-server'],
      _en.activitySavedServers: ['settings-saved-servers'],
      _en.onboardingTermuxSetup: ['search-result-settings-on-this-phone'],
      _en.settingsHubAccounts: ['search-result-settings-accounts'],
      _en.settingsHubProvidersRow: ['settings-providers'],
      _en.a2aTitle: ['search-result-settings-external-agents'],
      _en.tailscaleTitle: ['search-result-settings-tailscale'],
      _en.e7SettingsUi8: ['search-result-inside-server-disconnect'],
      _en.settingsHubModelRow: ['settings-model-and-mode'],
      _en.settingsHubModelAndMode: ['settings-model-and-mode'],
      _en.e7SettingsUi35: ['default-shell-settings-entry'],
      _en.e7SettingsUi74: ['search-result-saved-permissions-entry'],
      _en.automationTitle: ['settings-automation'],
      _en.chatUiTranscriptDisplay: [
        'settings-show-reasoning',
        'settings-show-timestamps',
      ],
      _en.settingsHubShowReasoning: ['settings-show-reasoning'],
      _en.settingsHubShowTimestamps: ['settings-show-timestamps'],
      _en.settingsHubVoice: ['settings-voice'],
      _en.settingsHubGroupNotifications: ['settings-category-background'],
      _en.keepRunningTitle: ['settings-keep-running'],
      _en.e7AppearanceTitle: ['settings-category-appearance'],
      _en.libraryModelsAgentsTitle: ['search-result-settings-models'],
      _en.libraryProvidersTitle: ['settings-providers'],
      _en.settingsHubToolsRow: ['settings-tools'],
      _en.libraryMcpTitle: ['search-result-settings-mcp', 'settings-tools'],
      _en.libraryCommandsToolsTitle: [
        'search-result-settings-commands-tools',
        'settings-tools',
      ],
      // Plugins is gone as a page; its words lead to the AI Team.
      _en.teamUiPluginsTitle: ['settings-tools', 'settings-ai-team'],
      _en.importTitle: ['search-result-library-import-session'],
      _en.settingsHubGroupUsage: ['settings-category-usage'],
      _en.usageSectionSpent: ['settings-category-usage'],
      _en.usageSectionRemaining: ['settings-category-usage'],
      _en.settingsHubPrivacyRow: ['settings-category-privacy'],
      _en.onboardingSetupGuide: ['settings-setup-guide'],
      _en.capabilityScreenTitle: ['settings-server-capabilities'],
      'not available': ['settings-server-capabilities'],
      _en.e7LibraryReportABug: ['library-report-bug'],
      _en.e7SettingsUi88: ['library-report-bug'],
      _en.e7SettingsUi92: ['search-result-settings-privacy-data-use'],
      _en.privacyPolicyTitle: ['search-result-settings-privacy-data-use'],
      _en.e7SettingsUi94: ['search-result-settings-voice-notices'],
      _en.e7SettingsUi96: ['settings-about-notices'],
      _en.aboutTitle: ['settings-about-notices'],
      // Keywords from the phase 2 spec.
      'host': ['settings-category-server', 'settings-saved-servers'],
      'url': ['settings-category-server', 'settings-saved-servers'],
      'password': ['settings-category-server', 'settings-saved-servers'],
      'profile': ['settings-category-server', 'settings-saved-servers'],
      'termux': ['search-result-settings-on-this-phone'],
      'local': ['search-result-settings-on-this-phone'],
      'on-device': ['search-result-settings-on-this-phone'],
      'alerts': ['settings-category-background'],
      'quiet': ['settings-category-background'],
      // Battery lives on Keep running, not on Notifications.
      'battery': ['search-result-inside-keep-running-battery'],
      'background': ['settings-category-background'],
      'check-in': ['settings-category-background'],
      // What sits inside the one Notifications screen.
      'quiet hours': ['settings-category-background'],
      'wi-fi': ['settings-category-background'],
      'finished': ['settings-category-background'],
      'approvals': [
        'settings-category-background',
        'settings-automation',
        'search-result-saved-permissions-entry',
      ],
      'monitor': ['settings-category-background'],
      'theme': ['settings-category-appearance'],
      'dark': ['settings-category-appearance'],
      'language': ['settings-category-appearance'],
      'arabic': ['settings-category-appearance'],
      'provider': ['search-result-settings-models', 'settings-providers'],
      'api key': ['settings-providers'],
      'account': ['settings-providers'],
      'mcp': ['search-result-settings-mcp', 'settings-tools'],
      'tools': ['settings-tools'],
      'skills': ['settings-tools', 'search-result-settings-commands-tools'],
      'plugins': ['settings-tools', 'settings-ai-team'],
      'a2a': ['settings-tools', 'search-result-settings-external-agents'],
      'thinking': ['settings-show-reasoning'],
      'timestamps': ['settings-show-timestamps'],
      'cost': ['settings-category-usage'],
      'tokens': ['settings-category-usage'],
      'budget': ['settings-category-usage'],
      'quota': ['settings-category-usage', 'settings-category-background'],
      'limit': ['settings-category-usage'],
      'threshold': ['settings-category-usage'],
      'quota monitoring': ['settings-category-usage'],
      'drafts': ['settings-category-privacy'],
      'queue': ['settings-category-privacy'],
      'read state': ['settings-category-privacy'],
      'guide': ['settings-setup-guide'],
      'bug': ['library-report-bug'],
      'diagnostics': ['library-report-bug'],
      'version': ['settings-about-notices'],
      'licenses': [
        'search-result-settings-voice-notices',
        'settings-about-notices',
      ],
    };

    for (final entry in cases.entries) {
      final ids = _found(controller, entry.key);
      for (final key in entry.value) {
        final id = key.startsWith('search-result-')
            ? key.substring('search-result-'.length)
            : key;
        expect(ids, contains(id), reason: '"${entry.key}" -> $key');
      }
    }
  });

  testWidgets('search still answers to the retired nouns', (tester) async {
    // Phase 1D renamed session/chat to conversation and profile to server. A
    // person who learned the old words must still find the rows.
    final controller = await _controller();
    addTearDown(controller.dispose);
    for (final word in ['session', 'chat', 'conversation']) {
      expect(
        _found(controller, word),
        contains('settings-model-and-mode'),
        reason: word,
      );
    }
    for (final word in ['profile', 'connection']) {
      expect(
        _found(controller, word),
        contains('settings-saved-servers'),
        reason: word,
      );
    }
  });

  testWidgets('About › Show tips again puts every one-time tip back', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    // A person past first run who has already seen and dismissed a tip.
    await controller.store.prefs.setBool(NudgeRegistry.firstReplySeenKey, true);
    final nudges = controller.nudges;
    expect(nudges.offer(NudgeId.compact, scope: 'ses_1'), isTrue);
    await nudges.dismiss(NudgeId.compact);
    expect(nudges.wasShown(NudgeId.compact), isTrue);
    expect(nudges.offer(NudgeId.compact, scope: 'ses_1'), isFalse);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    // It lives on About now (target-ia §1.3 row 22); search finds it there.
    expect(_row('settings-show-tips-again'), findsNothing);
    final scope = SearchScope(controller: controller);
    for (final query in [_en.discoverShowTipsAgain, 'tips', 'hints']) {
      final hits = searchEntries(_en, scope, query);
      final result = hits.firstWhere(
        (entry) => entry.id == 'settings-show-tips-again',
        orElse: () => throw TestFailure('"$query" finds no tips row'),
      );
      expect(result.parent, _en.aboutTitle, reason: query);
    }
    // The result opens About arrived at the row.
    unawaited(
      searchEntries(_en, scope, 'tips')
          .firstWhere((entry) => entry.id == 'settings-show-tips-again')
          .open(tester.element(find.byType(SettingsScreen)), scope),
    );
    await tester.pumpAndSettle();
    expect(find.byType(AboutScreen), findsOneWidget);
    final row = _row('settings-show-tips-again');
    expect(row, findsOneWidget);
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(find.text(_en.discoverShowTipsDone), findsOneWidget);
    // It acts in place: no further screen was pushed.
    expect(find.byType(AboutScreen), findsOneWidget);
    expect(find.byType(SnackBar), findsNothing);
    expect(nudges.wasShown(NudgeId.compact), isFalse);
    expect(nudges.offer(NudgeId.compact, scope: 'ses_1'), isTrue);
  });

  testWidgets(
    'the last panel is the guide, Report a problem, Available and About',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      final last = _row('settings-group-help');
      for (final key in [
        'settings-setup-guide',
        'library-report-bug',
        'settings-server-capabilities',
        'settings-about-notices',
      ]) {
        expect(find.descendant(of: last, matching: _row(key)), findsOneWidget);
      }
      // No Help page any more: its rows are hub rows or live on About.
      expect(_row('settings-help'), findsNothing);
      for (final key in [
        'app-diagnostics-entry',
        'settings-show-tips-again',
        'settings-privacy-data-use',
        'settings-voice-notices',
        'settings-models',
        'settings-tailscale',
        'settings-external-agents',
        'settings-mcp',
        'settings-commands-tools',
        'settings-category-plugins',
        'settings-accounts',
        'settings-transcript-display',
        'library-import-session',
      ]) {
        expect(_row(key), findsNothing, reason: '$key is not a hub row');
      }
      // The demo stays reachable once a server is saved: on the guide.
      await tester.ensureVisible(_row('settings-setup-guide'));
      await tester.pumpAndSettle();
      await tester.tap(_row('settings-setup-guide'));
      await tester.pumpAndSettle();
      expect(find.byType(GuideScreen), findsOneWidget);
      final demo = _row('settings-try-demo');
      await tester.scrollUntilVisible(
        demo,
        200,
        scrollable: find
            .descendant(
              of: find.byType(GuideScreen),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(
        find.descendant(of: demo, matching: find.text(_en.settingsTryDemo)),
        findsOneWidget,
      );
    },
  );

  testWidgets('keyboard shortcuts are an About row on desktop only', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(_row('library-keyboard-shortcuts'), findsNothing);

    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.linux,
    );
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    for (final query in [_en.e7LibraryKeyboardShortcuts, 'hotkeys']) {
      expect(_found(controller, query), contains('library-keyboard-shortcuts'));
    }
  });

  group('rows the server cannot serve are absent', () {
    const agentSetupRows = [
      'settings-models',
      'settings-providers',
      'settings-mcp',
      'settings-commands-tools',
      'library-import-session',
    ];

    for (final backend in {
      'Codex': codexServerCapabilities,
      'Paseo': paseoServerCapabilities,
    }.entries) {
      testWidgets('${backend.key} hides the server catalog rows', (
        tester,
      ) async {
        final capabilities = backend.value;
        final controller = await _controller(capabilities: capabilities);
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller));
        await tester.pumpAndSettle();

        expect(capabilities.serverCatalog, isFalse);
        expect(_row('settings-models'), findsNothing);
        // One row for whoever the model is paid through: the Codex account
        // on Codex, nothing on Paseo.
        expect(
          _row('settings-providers'),
          capabilities.agentAccount ? findsOneWidget : findsNothing,
        );
        expect(_row('settings-mcp'), findsNothing);
        expect(_row('settings-commands-tools'), findsNothing);
        expect(_row('library-terminal'), findsNothing);
        // Neither runtime keeps a list of standing grants the app can read.
        expect(_row('saved-permissions-entry'), findsNothing);
        // Import lives in All conversations' menu, not in Settings.
        expect(_row('library-import-session'), findsNothing);
        expect(
          _row('default-shell-settings-entry'),
          capabilities.shellSettings ? findsOneWidget : findsNothing,
        );
        // No exception any more: the shell row is absent, not disabled, and
        // Help → "Available on this server" says why (rule 7).
        expect(_row('gated-shell-settings'), findsNothing);
        expect(_row('settings-server-capabilities'), findsOneWidget);
        // The Providers row is the account's door; no second row for it.
        expect(_row('settings-accounts'), findsNothing);
        // Tools stays: plugins and external agents are not the server's.
        expect(_row('settings-tools'), findsOneWidget);
        // Absent rows are absent from search too, not dead results.
        expect(_found(controller, 'mcp'), isNot(contains('settings-mcp')));
        // What is about the app, not the server, survives.
        expect(_row('settings-category-appearance'), findsOneWidget);
        expect(_row('settings-category-server'), findsOneWidget);
      });
    }

    testWidgets('a group with no rows is absent', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.linux,
      );
      final controller = await _controller(
        capabilities: const ServerCapabilities(
          serverCatalog: false,
          terminal: false,
          sessionImportExport: false,
        ),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      for (final key in agentSetupRows) {
        expect(_row(key), findsNothing, reason: key);
      }
      // Plugins ("In this app") still needs only a saved server.
      expect(_row('settings-group-server'), findsOneWidget);
      // Notifications stays off Android: saved-server monitoring and
      // check-ins work in the open app, so its one row is never empty. The
      // background summary is what goes.
      expect(_row('settings-category-background'), findsOneWidget);
      expect(find.textContaining('Background:'), findsNothing);

      // Usage has one row, for "Spent" or "Remaining". With neither usage
      // statistics nor a saved server the group is gone, not an empty header.
      final bare = await _controller(
        savedServer: false,
        usageStatistics: false,
      );
      addTearDown(bare.dispose);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(_app(bare));
      await tester.pumpAndSettle();
      expect(_row('settings-category-usage'), findsNothing);
      expect(_row('settings-group-help'), findsOneWidget);
    });
  });

  testWidgets(
    'the default model uses the catalog name and explains its scope',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.selectedModel = ModelRef(
        providerID: 'opencode',
        modelID: 'nemotron-free',
      );
      controller.catalog = const CatalogSnapshot(
        providers: [],
        agents: [],
        models: [
          CatalogModel(
            id: 'nemotron-free',
            providerID: 'opencode',
            name: 'Nemotron Ultra',
            enabled: true,
            status: 'active',
            contextLimit: 100000,
            outputLimit: 8000,
            reasoning: true,
            attachments: false,
            tools: true,
            variants: [],
          ),
        ],
      );
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      // Once, as the Model row's value (one Model row, R3).
      expect(find.textContaining('Nemotron Ultra'), findsOneWidget);
      expect(find.textContaining('opencode/nemotron-free'), findsNothing);
      expect(find.textContaining('New conversations:'), findsNothing);
    },
  );

  testWidgets('the hub carries no pending badge of its own', (tester) async {
    final controller = await _controller()
      ..permissions = {
        'perm-1': PermissionRequest(
          id: 'perm-1',
          sessionID: 'ses_run',
          permission: 'edit',
          patterns: const ['lib/main.dart'],
        ),
      };
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    // One global badge only, and it is not here. Nor does the hub repeat
    // pending work as a destination of its own.
    expect(find.byType(Badge), findsNothing);
    expect(find.text('Mission Control'), findsNothing);
    expect(find.text('Requests'), findsNothing);
  });

  testWidgets('Terminal is not a Settings row: it lives on the Project tab', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(controller.capabilities.terminal, isTrue);
    expect(_row('library-terminal'), findsNothing);
    expect(find.text('Terminal'), findsNothing);
    expect(_found(controller, 'terminal'), isNot(contains('library-terminal')));
  });

  testWidgets('an entry point can open the hub scrolled to a group', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller, initialGroup: SettingsGroup.help));
    await tester.pumpAndSettle();

    final help = tester.getRect(_row('settings-group-help'));
    expect(help.top, lessThan(700));
    expect(help.top, greaterThanOrEqualTo(0));
    expect(tester.getRect(_row('settings-group-server')).bottom, lessThan(0));
  });

  group('screen-settings-1: two panes from expanded', () {
    Future<void> wide(WidgetTester tester, Size size) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }

    testWidgets('the groups are the list and a group fills the detail', (
      tester,
    ) async {
      await wide(tester, const Size(1280, 800));
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      final list = find.byKey(const ValueKey('settings-list-pane'));
      final detail = find.byKey(const ValueKey('settings-detail-pane'));
      expect(list, findsOneWidget);
      expect(detail, findsOneWidget);
      for (final group in SettingsGroup.values) {
        expect(
          find.descendant(
            of: list,
            matching: _row('settings-index-${group.slug}'),
          ),
          findsOneWidget,
          reason: group.slug,
        );
      }
      // The first group is open; its rows sit in the detail pane only.
      expect(
        find.descendant(of: detail, matching: _row('settings-saved-servers')),
        findsOneWidget,
      );
      expect(_row('settings-setup-guide'), findsNothing);

      await tester.tap(_row('settings-index-help'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(of: detail, matching: _row('settings-setup-guide')),
        findsOneWidget,
      );
      expect(_row('settings-saved-servers'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('an entry point opens its group in the detail pane', (
      tester,
    ) async {
      await wide(tester, const Size(1280, 800));
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(controller, initialGroup: SettingsGroup.help),
      );
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('settings-detail-pane')),
          matching: _row('settings-group-help'),
        ),
        findsOneWidget,
      );
    });

    testWidgets('a phone keeps one pane', (tester) async {
      await wide(tester, const Size(412, 915));
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('settings-list-pane')), findsNothing);
      expect(_row('settings-group-server'), findsOneWidget);
    });

    testWidgets('Show tips again says so on its row, not in a snackbar', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AboutScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(_row('settings-show-tips-again'));
      await tester.pumpAndSettle();
      await tester.tap(_row('settings-show-tips-again'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: _row('settings-show-tips-again'),
          matching: find.text(_en.discoverShowTipsDone),
        ),
        findsOneWidget,
      );
      expect(find.byType(SnackBar), findsNothing);
    });
  });

  group('slice-P3.10: the Settings IA', () {
    testWidgets('each group holds at most five rows, none twice', (
      tester,
    ) async {
      tester.view
        ..physicalSize = const Size(412, 3000)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller(
        capabilities: const ServerCapabilities(agentAccount: true),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      final titles = <String>[];
      var total = 0;
      for (final group in SettingsGroup.values) {
        final panel = _row('settings-group-${group.slug}');
        expect(panel, findsOneWidget, reason: group.slug);
        final rows = find.descendant(
          of: panel,
          matching: find.byType(KitArrival),
        );
        final count = rows.evaluate().length;
        expect(count, inInclusiveRange(1, 5), reason: group.slug);
        total += count;
        for (final row in tester.widgetList<KitArrival>(rows)) {
          titles.add(row.id);
        }
      }
      // Android, OpenCode 1, nothing set up on this phone: every target-ia
      // row but the setup assistant (P2.2, not built yet) and This phone,
      // which joins the hub once the phone is set up (before that, setting
      // it up is one of Add server's ways, R3).
      // Agents is on every phone (where it cannot run it says why).
      expect(total, 19);
      expect(titles.toSet().length, titles.length);
      // The pairs that used to sit side by side are one row each now.
      for (final gone in [
        'settings-mcp',
        'settings-commands-tools',
        'settings-category-plugins',
        'settings-accounts',
        'settings-transcript-display',
        'settings-help',
        'settings-privacy-data-use',
        'settings-voice-notices',
      ]) {
        expect(titles, isNot(contains(gone)), reason: gone);
      }
    });

    testWidgets('no dead aliases: nothing claims font size or older drafts', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(
        _found(controller, 'font size'),
        isNot(contains('settings-category-appearance')),
      );
      expect(
        _found(controller, 'older drafts'),
        isNot(contains('settings-category-privacy')),
      );
    });

    testWidgets('This phone joins the hub once the phone is set up', (
      tester,
    ) async {
      final controller = await _controller(
        baseUrl: 'http://127.0.0.1:${TermuxBridge.managedServerPort}',
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: _row('settings-group-server'),
          matching: _row('settings-this-phone'),
        ),
        findsOneWidget,
      );
      // No second way to set it up again from search.
      expect(
        _found(controller, 'termux'),
        isNot(contains('settings-on-this-phone')),
      );
      expect(_row('settings-this-phone'), findsOneWidget);
    });

    testWidgets('the transcript switches act on the hub', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(controller.transcriptReasoningExpanded, isFalse);
      expect(controller.transcriptTimestampsVisible, isFalse);

      final reasoning = _row('settings-show-reasoning');
      await tester.ensureVisible(reasoning);
      await tester.pumpAndSettle();
      await tester.tap(reasoning);
      await tester.pumpAndSettle();
      expect(controller.transcriptReasoningExpanded, isTrue);
      // In place: no sheet, no page.
      expect(find.byType(SettingsScreen), findsOneWidget);
      expect(find.text(_en.chatUiTranscriptDisplay), findsNothing);

      await tester.tap(_row('settings-show-timestamps'));
      await tester.pumpAndSettle();
      expect(controller.transcriptTimestampsVisible, isTrue);
      // Stored, so a new conversation reads the same values.
      expect(controller.store.transcriptReasoningExpanded, isTrue);
      expect(controller.store.transcriptTimestampsVisible, isTrue);
    });

    testWidgets('rows the server hides are one line with a Why', (
      tester,
    ) async {
      tester.view
        ..physicalSize = const Size(412, 3000)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller(
        capabilities: paseoServerCapabilities,
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // Paseo has neither a provider catalog nor saved permissions: the
      // Agent and Conversations groups each say so once, the rest do not.
      expect(_row('settings-unavailable-server'), findsNothing);
      expect(_row('settings-unavailable-this-app'), findsNothing);
      expect(_row('settings-unavailable-help'), findsNothing);
      final agent = _row('settings-unavailable-agent');
      expect(agent, findsOneWidget);
      expect(
        find.descendant(
          of: agent,
          matching: find.text(_en.settingsHubUnavailableCount(1)),
        ),
        findsOneWidget,
      );
      expect(_row('settings-unavailable-conversations'), findsOneWidget);

      await tester.tap(_row('settings-unavailable-why-agent'));
      await tester.pumpAndSettle();
      expect(find.byType(ServerCapabilitiesScreen), findsOneWidget);
    });

    testWidgets('OpenCode 1 hides nothing, so there is no line', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(find.byType(KitGroupNote), findsNothing);
    });

    testWidgets('Tools is one page: MCP, the catalog and external agents', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await tester.ensureVisible(_row('settings-tools'));
      await tester.pumpAndSettle();
      await tester.tap(_row('settings-tools'));
      await tester.pumpAndSettle();
      expect(find.byType(CapabilitiesScreen), findsOneWidget);
      for (final label in [
        _en.libraryMcpTitle,
        _en.runResultsCommandsTitle,
        _en.e7SettingsDetailUi25,
        _en.e7SettingsDetailUi26,
        _en.e7SettingsDetailUi27,
        _en.a2aTitle,
      ]) {
        expect(_row('capabilities-tab-$label'), findsOneWidget, reason: label);
      }
      expect(_row('tools-unavailable'), findsNothing);
      // The first tab is MCP; External agents is one tap away, on the same
      // page.
      expect(_row('tools-section-mcp'), findsOneWidget);
      await tester.tap(_row('capabilities-tab-${_en.a2aTitle}'));
      await tester.pumpAndSettle();
      expect(_row('tools-section-externalAgents'), findsOneWidget);
      expect(_row('external-agents-empty'), findsOneWidget);
    });

    testWidgets('Codex Tools keeps external agents, says why', (tester) async {
      final controller = await _controller(
        capabilities: codexServerCapabilities,
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: CapabilitiesScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      expect(_row('capabilities-tab-${_en.libraryMcpTitle}'), findsNothing);
      expect(_row('tools-section-mcp'), findsNothing);
      expect(_row('settings-category-plugins'), findsNothing);
      expect(_row('tools-section-externalAgents'), findsOneWidget);
      expect(find.text(_en.settingsHubUnavailableCount(2)), findsOneWidget);
      await tester.tap(_row('tools-unavailable-why'));
      await tester.pumpAndSettle();
      expect(find.byType(ServerCapabilitiesScreen), findsOneWidget);
    });

    testWidgets('the privacy policy opens from Privacy and data', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      final privacy = _row('settings-category-privacy');
      await tester.ensureVisible(privacy);
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: privacy,
          matching: find.text(_en.settingsHubPrivacyRow),
        ),
        findsOneWidget,
      );
      await tester.tap(privacy);
      await tester.pumpAndSettle();
      final policy = _row('privacy-policy');
      await tester.scrollUntilVisible(
        policy,
        200,
        scrollable: find.byType(Scrollable).last,
      );
      expect(
        find.descendant(
          of: policy,
          matching: find.text(_en.privacyPolicyTitle),
        ),
        findsOneWidget,
      );
      await tester.tap(policy);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('privacy-policy-viewer')),
        findsOneWidget,
      );
    });

    testWidgets('About holds the voice licences under Open source', (
      tester,
    ) async {
      tester.view
        ..physicalSize = const Size(412, 2400)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await tester.tap(_row('settings-about-notices'));
      await tester.pumpAndSettle();
      expect(find.byType(AboutScreen), findsOneWidget);
      expect(
        find.descendant(
          of: _row('about-open-source'),
          matching: _row('settings-voice-notices'),
        ),
        findsOneWidget,
      );
      expect(_row('about-tabs'), findsNothing);
      expect(_row('settings-show-tips-again'), findsOneWidget);
    });
  });

  group('layout at 320 dp and 2.5x text', () {
    for (final locale in const [Locale('en'), Locale('ar')]) {
      testWidgets('no overflow in ${locale.languageCode}', (tester) async {
        const phone = Size(320, 640);
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = phone;
        addTearDown(tester.view.reset);
        final controller = await _controller(
          capabilities: const ServerCapabilities(agentAccount: true),
        );
        addTearDown(controller.dispose);

        await tester.pumpWidget(
          MediaQuery(
            data: const MediaQueryData(
              size: phone,
              textScaler: TextScaler.linear(AppTheme.maxTextScale),
            ),
            child: _app(controller, locale: locale),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(
          Directionality.of(tester.element(_row('settings-group-help'))),
          locale.languageCode == 'ar' ? TextDirection.rtl : TextDirection.ltr,
        );

        // Walk the whole hub so every row has been laid out and painted.
        final scrollable = find
            .descendant(
              of: find.byKey(const ValueKey('settings-hub-list')),
              matching: find.byType(Scrollable),
            )
            .first;
        for (final group in SettingsGroup.values) {
          await tester.scrollUntilVisible(
            _row('settings-group-${group.slug}'),
            300,
            scrollable: scrollable,
          );
          expect(tester.takeException(), isNull, reason: group.slug);
        }
        // No row is wider than the phone.
        for (final tile in tester.widgetList<KitRow>(find.byType(KitRow))) {
          final box = tester.renderObject<RenderBox>(find.byWidget(tile));
          expect(box.size.width, lessThanOrEqualTo(phone.width));
        }
      });
    }
  });
}
