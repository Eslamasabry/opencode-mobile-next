import 'support/fake_server_capabilities.dart';
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'support/fake_event_channel.dart';
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

import 'support/settings_doors.dart';

class _Api extends FakeServerGateway {
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
  ServerCapabilities capabilities = testServerCapabilities,
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
          'flavor': 'v2',
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
  Widget? home,
}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home:
      home ??
      SettingsScreen(controller: controller, initialGroup: initialGroup),
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

    // Vision 2026-10-20: a search and five one-word doors, Server, Agent,
    // Conversations, This app and Help, that never change with the server.
    const slugs = ['connections', 'ai', 'chat', 'this-app', 'help'];
    expect(SettingsGroup.values.map((group) => group.slug), slugs);
    expect(_row('settings-hub-search-field'), findsOneWidget);
    for (final word in [
      _en.settingsHubServer,
      _en.settingsHubGroupAgent,
      _en.settingsHubGroupConversations,
      _en.settingsHubGroupThisApp,
      _en.settingsHubHelp,
    ]) {
      expect(find.text(word), findsOneWidget, reason: word);
    }
    // No section headers and no rows of the groups on the hub itself.
    expect(_row('settings-saved-servers'), findsNothing);
    expect(_row('settings-model-and-mode'), findsNothing);
    var previous = double.negativeInfinity;
    for (final slug in slugs) {
      final group = _row('settings-hub-row-$slug');
      expect(group, findsOneWidget, reason: slug);
      final top = tester.getTopLeft(group).dy;
      expect(top, greaterThan(previous), reason: '$slug is out of order');
      previous = top;
    }
  });

  testWidgets(
    'Disconnect is not on Settings; the server page names it and puts it last',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // No floating Disconnect below the Connection group any more.
      expect(_row('settings-disconnect'), findsNothing);
      expect(find.text(_en.e7SettingsUi8), findsNothing);
      expect(_row('server-disconnect'), findsNothing);

      await tester.pumpWidget(
        _app(
          controller,
          home: ServerPageScreen(
            controller: controller,
            profileId: 'profile-1',
          ),
        ),
      );
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
      // Destructive and last: Remove follows Disconnect, nothing after it.
      expect(
        tester.getTopLeft(_row('server-remove')).dy,
        greaterThan(tester.getBottomLeft(disconnect).dy),
      );
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
      expect(result.parent, _en.settingsV3Servers);

      unawaited(
        result.open(tester.element(find.byType(SettingsScreen)), scope),
      );
      await tester.pumpAndSettle();
      expect(find.byType(ServerPageScreen), findsOneWidget);
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
      _en.settingsV3StatusVersion: ['settings-category-server'],
      _en.settingsV3Servers: ['settings-saved-servers'],
      _en.onboardingTermuxSetup: ['search-result-settings-on-this-phone'],
      _en.settingsHubAccounts: ['search-result-settings-accounts'],
      _en.settingsHubProvidersRow: ['settings-providers'],
      _en.a2aTitle: ['search-result-settings-external-agents'],
      _en.tailscaleTitle: ['search-result-settings-tailscale'],
      _en.e7SettingsUi8: ['search-result-inside-server-disconnect'],
      _en.settingsHubModelRow: ['settings-model-and-mode'],
      _en.settingsHubModelAndMode: ['settings-model-and-mode'],
      _en.settingsV3Shell: ['default-shell-settings-entry'],
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

  testWidgets('the last panel is the guide, Report a problem and About', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await openSettingsGroup(tester, SettingsGroup.help);
    final last = _row('settings-group-help');
    for (final key in [
      'settings-setup-guide',
      'library-report-bug',
      'settings-about-notices',
    ]) {
      expect(find.descendant(of: last, matching: _row(key)), findsOneWidget);
    }
    // What this server supports moved to the server's own page.
    expect(_row('settings-server-capabilities'), findsNothing);
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
  });

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
        final pages = await visitSettingsGroups(tester);
        final ids = settingsRowIds(pages);
        expect(ids, isNot(contains('settings-models')));
        // One row for whoever the model is paid through: the Codex account
        // on Codex, nothing on Paseo.
        expect(ids.contains('settings-providers'), capabilities.agentAccount);
        expect(ids, isNot(contains('settings-mcp')));
        expect(ids, isNot(contains('settings-commands-tools')));
        expect(ids, isNot(contains('library-terminal')));
        // Neither runtime keeps a list of standing grants the app can read.
        expect(ids, isNot(contains('saved-permissions-entry')));
        // Import lives in All conversations' menu, not in Settings.
        expect(ids, isNot(contains('library-import-session')));
        expect(
          ids.contains('default-shell-settings-entry'),
          capabilities.shellSettings,
        );
        // No exception any more: the shell row is absent, not disabled, and
        // the page whose rows went says so in one line with a Why (rule 7).
        expect(ids, isNot(contains('gated-shell-settings')));
        expect(
          pages.notes.values.fold<int>(0, (a, b) => a + b),
          greaterThanOrEqualTo(1),
        );
        // The Providers row is the account's door; no second row for it.
        expect(ids, isNot(contains('settings-accounts')));
        // Tools stays: plugins and external agents are not the server's.
        expect(ids, contains('settings-tools'));
        // Absent rows are absent from search too, not dead results.
        expect(_found(controller, 'mcp'), isNot(contains('settings-mcp')));
        // What is about the app, not the server, survives.
        expect(ids, contains('settings-category-appearance'));
        expect(ids, contains('settings-saved-servers'));
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

      final ids = settingsRowIds(await visitSettingsGroups(tester));
      for (final key in agentSetupRows) {
        expect(ids, isNot(contains(key)), reason: key);
      }
      // Plugins ("In this app") still needs only a saved server.
      expect(settingsDoor(SettingsGroup.connections), findsOneWidget);
      // Notifications stays off Android: saved-server monitoring and
      // check-ins work in the open app, so its row is never empty. The
      // Background row is Android's alone and goes.
      expect(ids, contains('settings-category-notifications'));
      expect(ids, isNot(contains('settings-category-background')));
      expect(find.textContaining('Background:'), findsNothing);

      // Usage has one row, for "Spending" or "Plans". With neither usage
      // statistics nor a saved server the group is gone, not an empty header.
      final bare = await _controller(
        savedServer: false,
        usageStatistics: false,
      );
      addTearDown(bare.dispose);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(_app(bare));
      await tester.pumpAndSettle();
      final bareIds = settingsRowIds(await visitSettingsGroups(tester));
      expect(bareIds, isNot(contains('settings-category-usage')));
      expect(settingsDoor(SettingsGroup.help), findsOneWidget);
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
      await openSettingsGroup(tester, SettingsGroup.ai);
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

  testWidgets('an entry point on a phone opens its group\'s page', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller, initialGroup: SettingsGroup.help));
    await tester.pumpAndSettle();

    // The page itself, titled with the group's word: no hub doors above it.
    expect(find.byType(SettingsGroupScreen), findsOneWidget);
    expect(find.text(_en.settingsHubHelp), findsOneWidget);
    expect(settingsDoor(SettingsGroup.help), findsNothing);
    final guide = tester.getRect(_row('settings-setup-guide'));
    expect(guide.top, greaterThanOrEqualTo(0));
    expect(guide.bottom, lessThan(700));
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
          find.descendant(of: list, matching: settingsDoor(group)),
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

      await tester.tap(settingsDoor(SettingsGroup.help));
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
      expect(settingsDoor(SettingsGroup.connections), findsOneWidget);
      expect(_row('settings-saved-servers'), findsNothing);
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
      final pages = await visitSettingsGroups(tester);
      for (final group in SettingsGroup.values) {
        final ids = pages.rows[group];
        expect(ids, isNotNull, reason: group.slug);
        final count = ids!.length;
        expect(count, inInclusiveRange(2, 6), reason: group.slug);
        total += count;
        titles.addAll(ids);
      }
      // Android, OpenCode 1, nothing set up on this phone: every target-ia
      // row but the setup assistant (P2.2, not built yet) and This phone,
      // which joins the hub once the phone is set up (before that, setting
      // it up is one of Add server's ways, R3).
      // Agents is on every phone (where it cannot run it says why).
      // Phrase suggestions (the chat's own-phrase chips) is one Conversations row.
      // Help › Tips (the teaching system, 2026-10-21) is one Help row.
      expect(total, 22);
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

    testWidgets(
      'This phone is always on the hub and says whether it is set up',
      (tester) async {
        final controller = await _controller(
          baseUrl: 'http://127.0.0.1:${TermuxBridge.managedServerPort}',
        );
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller));
        await tester.pumpAndSettle();
        await openSettingsGroup(tester, SettingsGroup.connections);
        expect(
          find.descendant(
            of: _row('settings-group-connections'),
            matching: _row('settings-this-phone'),
          ),
          findsOneWidget,
        );
        // The app is connected to the server this phone hosts: it is running.
        expect(
          find.descendant(
            of: _row('settings-this-phone'),
            matching: find.text(_en.settingsV3PhoneRunning),
          ),
          findsOneWidget,
        );
        // No second way to set it up again from search.
        expect(
          _found(controller, 'termux'),
          isNot(contains('settings-on-this-phone')),
        );
        expect(_row('settings-this-phone'), findsOneWidget);
      },
    );

    testWidgets('the transcript switches act on Conversations', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.chat);
      expect(controller.transcriptReasoningExpanded, isFalse);
      expect(controller.transcriptTimestampsVisible, isFalse);

      final reasoning = _row('settings-show-reasoning');
      await tester.ensureVisible(reasoning);
      await tester.pumpAndSettle();
      await tester.tap(reasoning);
      await tester.pumpAndSettle();
      expect(controller.transcriptReasoningExpanded, isTrue);
      // In place: no sheet, no further page.
      expect(find.byType(SettingsGroupScreen), findsOneWidget);
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

      // Rows never move because a server lacks a feature (N6): the hub keeps
      // its five doors, and a group's page counts what it lost in one line.
      expect(find.byType(KitGroupNote), findsNothing);
      final pages = await visitSettingsGroups(tester);
      final withNote = [
        for (final MapEntry(key: group, value: notes) in pages.notes.entries)
          if (notes > 0) group,
      ];
      expect(withNote, isNotEmpty);
      expect(pages.notes.values.every((notes) => notes <= 1), isTrue);
      await openSettingsGroup(tester, withNote.first);
      final note = _row('settings-unavailable');
      expect(
        find.descendant(
          of: note,
          matching: find.textContaining('available on this server'),
        ),
        findsOneWidget,
      );

      await tester.ensureVisible(_row('settings-unavailable-why'));
      await tester.tap(_row('settings-unavailable-why'));
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
      final pages = await visitSettingsGroups(tester);
      expect(pages.notes.values.every((notes) => notes == 0), isTrue);
    });

    testWidgets('Tools is one page: MCP, the catalog and external agents', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await openSettingsGroup(tester, SettingsGroup.ai);
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
      await openSettingsGroup(tester, SettingsGroup.thisApp);
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
      await openSettingsGroup(tester, SettingsGroup.help);
      await tester.tap(_row('settings-about-notices'));
      await tester.pumpAndSettle();
      expect(find.byType(AboutScreen), findsOneWidget);
      // The four parts are rows of About itself, below Open source: the
      // Voice licenses page is gone.
      for (final part in const [
        'whisper',
        'sherpa-onnx',
        'onnx-runtime',
        'record',
      ]) {
        expect(
          find.descendant(
            of: _row('about-voice-licences'),
            matching: _row('voice-notice-$part'),
          ),
          findsOneWidget,
          reason: part,
        );
      }
      expect(find.text(_en.voiceNoticesTitle), findsOneWidget);
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
          Directionality.of(tester.element(settingsDoor(SettingsGroup.help))),
          locale.languageCode == 'ar' ? TextDirection.rtl : TextDirection.ltr,
        );
        void noRowWiderThanThePhone() {
          for (final tile in tester.widgetList<KitRow>(find.byType(KitRow))) {
            final box = tester.renderObject<RenderBox>(find.byWidget(tile));
            expect(box.size.width, lessThanOrEqualTo(phone.width));
          }
        }

        noRowWiderThanThePhone();
        // Every group's page, walked to its end so each row is laid out.
        for (final group in SettingsGroup.values) {
          await openSettingsGroup(tester, group);
          expect(tester.takeException(), isNull, reason: group.slug);
          final list = find.byKey(
            ValueKey('settings-group-page-${group.slug}'),
          );
          await tester.drag(list, const Offset(0, -3000));
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull, reason: group.slug);
          noRowWiderThanThePhone();
          Navigator.of(tester.element(list)).pop();
          await tester.pumpAndSettle();
        }
        // No row is wider than the phone.
        for (final tile in tester.widgetList<KitRow>(find.byType(KitRow))) {
          final box = tester.renderObject<RenderBox>(find.byWidget(tile));
          expect(box.size.width, lessThanOrEqualTo(phone.width));
        }
      });
    }
  });

  group('vision 2026-10-20: a search and five words', () {
    String serverLine(WidgetTester tester) => tester
        .widget<Text>(find.byKey(const ValueKey('settings-hub-server-line')))
        .textSpan!
        .toPlainText();

    testWidgets('Server says where the server runs and how it is connected', (
      tester,
    ) async {
      final phone = await _controller(baseUrl: 'http://127.0.0.1:4097');
      addTearDown(phone.dispose);
      await tester.pumpWidget(_app(phone));
      await tester.pumpAndSettle();
      expect(
        serverLine(tester),
        _en.settingsHubServerLine(
          _en.settingsHubServerOnPhone,
          _en.e7WorkspaceConnected,
        ),
      );
      // Only Server has a second line: no summaries under the other words.
      for (final group in SettingsGroup.values.skip(1)) {
        expect(
          find.descendant(of: settingsDoor(group), matching: find.byType(Text)),
          findsOneWidget,
          reason: group.slug,
        );
      }

      final computer = await _controller();
      addTearDown(computer.dispose);
      computer.status = StreamStatus.disconnected;
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(_app(computer));
      await tester.pumpAndSettle();
      // Never red and never a guess: the switcher's own word.
      expect(
        serverLine(tester),
        _en.settingsHubServerLine(
          KitBidi.auto('Workstation'),
          _en.e7WorkspaceOffline,
        ),
      );

      final none = await _controller(savedServer: false);
      addTearDown(none.dispose);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(_app(none));
      await tester.pumpAndSettle();
      expect(serverLine(tester), _en.settingsHubServerNone);
      // The offline controller's quiet reconnect waits 15 s.
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 16));
    });

    testWidgets('each word opens its group\'s page, titled with the word', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      final words = {
        SettingsGroup.connections: _en.settingsHubServer,
        SettingsGroup.ai: _en.settingsHubGroupAgent,
        SettingsGroup.chat: _en.settingsHubGroupConversations,
        SettingsGroup.thisApp: _en.settingsHubGroupThisApp,
        SettingsGroup.help: _en.settingsHubHelp,
      };
      for (final MapEntry(key: group, value: word) in words.entries) {
        await openSettingsGroup(tester, group);
        final page = find.byType(SettingsGroupScreen);
        expect(
          find.descendant(of: page, matching: find.text(word)),
          findsOneWidget,
          reason: word,
        );
        expect(_row('settings-group-${group.slug}'), findsOneWidget);
        await closeSettingsGroup(tester);
      }
    });

    testWidgets('the search finds a setting inside a page and opens it', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      await tester.enterText(_row('settings-hub-search-field'), 'quiet hours');
      // The query settles after typing stops.
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pumpAndSettle();
      // The five words give way to what matches, each with where it lives.
      expect(settingsDoor(SettingsGroup.help), findsNothing);
      final result = _row('settings-hub-result-inside-notifications-quiet');
      expect(result, findsOneWidget);
      // Settings only: the app's places stay with the header's search.
      expect(
        find.descendant(
          of: _row('settings-hub-results'),
          matching: find.textContaining('Project'),
        ),
        findsNothing,
      );
      await tester.tap(result);
      await tester.pumpAndSettle();
      expect(find.byType(NotificationsSettingsScreen), findsOneWidget);
      await tester.pageBack();
      await tester.pumpAndSettle();

      // Nothing matches: one plain state, then the words are back on clear.
      await tester.enterText(_row('settings-hub-search-field'), 'xylophone');
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pumpAndSettle();
      expect(_row('settings-hub-no-match'), findsOneWidget);
      expect(find.text(_en.kitSearchResults(0)), findsNothing);
      await tester.tap(_row('kit-search-clear'));
      await tester.pumpAndSettle();
      expect(settingsDoor(SettingsGroup.help), findsOneWidget);
    });

    testWidgets('at 320 dp and 2x text the words keep the row', (tester) async {
      tester.view
        ..physicalSize = const Size(320, 900)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 900),
            textScaler: TextScaler.linear(2),
          ),
          child: _app(controller),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      // No glyph and no chevron: "Conversations" is never cut mid-word.
      final door = settingsDoor(SettingsGroup.chat);
      expect(
        find.descendant(of: door, matching: find.byType(Icon)),
        findsNothing,
      );
      expect(
        find.descendant(
          of: door,
          matching: find.byIcon(AppIconography.chevronRight),
        ),
        findsNothing,
      );
      // The word itself is whole (the capture checks it in the real font;
      // the test font draws every letter as a wide square).
      expect(
        find.descendant(
          of: door,
          matching: find.text(_en.settingsHubGroupConversations),
        ),
        findsOneWidget,
      );
    });
  });
}
