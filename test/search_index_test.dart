import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/ai_setup_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Ledger pages of kind screen/tab that search deliberately does not list,
/// each with the reason. Everything else must be found by its title.
const _excluded = <String, String>{
  // Not reachable by choice: the app shows them on its own.
  'home-shell': 'the frame around the three tabs; each tab is found on its own',
  'activity':
      'the former Inbox: now part of Chats (Needs you, Running); nothing opens it',
  'root-connecting': 'shown automatically while a saved server connects',
  'bootstrap-gate': 'startup failure screen; nothing is connected yet',
  'servers-welcome': 'first run only, before any server exists',
  'manage-space':
      'opened by Android Settings › Storage, not from inside the app',
  'termux-migration':
      'offered only to a Termux user, from This phone and the Termux server row',
  'team-migration':
      'shown on its own when an old team was left on after the update',
  // Need a conversation: the conversation menu and its command launcher are
  // their search (phase 4 adds them to this index through the registry).
  'chat': 'a conversation; opened from Chats or All conversations',
  'active-context': 'needs an open conversation',
  'active-context-message': 'needs an open conversation and a message',
  'prompt-editor': 'needs an open conversation (composer)',
  'run-result': 'needs an open conversation',
  'session-context': 'needs an open conversation',
  'session-export': 'needs an open conversation',
  'session-note': 'needs an open conversation',
  'session-relations': 'needs an open conversation',
  'markdown-code-reader': 'needs a code block in a transcript',
  'web-sources': 'adds a source to the open conversation',
  'staged-revert': 'needs a staged revert in an open conversation',
  // Need something picked first.
  'projects': 'a picker that returns the chosen project to Files',
  'shell-output': 'the output of one command that was just run',
  'terminal-surface': 'one terminal process; opened from Terminal',
  'diff-view': 'one file of a review; opened from Changes',
  'external-agent-detail': 'one external agent; opened from External agents',
  'external-task': 'one task of one external agent',
  'add-agent': 'a form inside External agents',
  'profile-editor': 'a form inside Saved servers; owned by phase 3b',
  'pairing-scanner': 'a step of adding a server',
  'team-project-overview':
      'One selected project; opened from AI Team projects.',
  'team-project-conversation':
      'One project task; opened from its project or notification.',
  'team-project-board': 'Task graph for one selected project.',
  'team-project-timeline': 'Audit events for one selected project.',
  'team-project-servers': 'Placement controls for one selected project.',
  'team-agent': 'one agent of one AI Team run',
  'chat-watching-live':
      'one AI Team agent whose conversation the server cannot read; '
      'opened from that agent',
  'team-role': 'one role of the AI Team; opened from Agents',
  'team-board':
      "needs the AI Team; opened from the home's board icon or "
      "'View board' row",
  'team-conversation':
      'one AI Team task\'s conversation; opened from the '
      'team page, the board or the Work tab',
  'phone-setup-progress':
      'a step of phone setup; opened from On this phone '
      'or a setup notification',
  'phone-setup-ready':
      'a step of phone setup; shown automatically when a '
      'first setup finishes',
};

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

  @override
  bool get usageStatisticsSupported => true;

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
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Workstation',
        'baseUrl': 'http://localhost:4096',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = _Api(capabilities)
    ..repository = _Repository()
    ..status = StreamStatus.connected;
}

final _en = lookupAppLocalizations(const Locale('en'));

/// Records what the shell is asked for, the way HomeScreen would.
class _Shell extends StatefulWidget {
  const _Shell({required this.child, required this.seen});
  final Widget child;
  final List<Intent> seen;

  @override
  State<_Shell> createState() => _ShellState();
}

class _ShellState extends State<_Shell> with AppShortcutSurface<_Shell> {
  @override
  bool onAppShortcut(Intent intent) {
    widget.seen.add(intent);
    return true;
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

Widget _app(
  ConnectionController controller, {
  Locale locale = const Locale('en'),
  List<Intent>? shell,
}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: shell == null
      ? null
      : (context, child) => AppShortcutScope(signals: _signals, child: child!),
  home: shell == null
      ? SettingsScreen(controller: controller)
      : _Shell(
          seen: shell,
          child: SettingsScreen(controller: controller),
        ),
);

final _signals = AppShortcutSignals();

Finder _key(String key) => find.byKey(ValueKey(key));

Set<String> _ids(Iterable<SearchEntry> entries) => {
  for (final entry in entries) entry.id,
};

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

  group('ledger coverage', () {
    final ledger =
        jsonDecode(File('docs/design/ui-ledger/ledger.json').readAsStringSync())
            as Map<String, dynamic>;
    final pages = [
      for (final page in ledger['pages'] as List)
        if (const {'screen', 'tab'}.contains((page as Map)['kind']))
          page.cast<String, dynamic>(),
    ];
    final entries = allSearchEntries(_en);

    /// A title as a person would type it: one alternative of "A | B", without
    /// a trailing "({count})".
    List<String> titles(String title) => [
      for (final part in title.split(' | '))
        part.replaceAll(RegExp(r'\s*\(\{[a-z]+\}\)$'), '').trim(),
    ];

    test('every screen and tab is found by its title, or is excluded', () {
      final missing = <String>[];
      var findable = 0;
      for (final page in pages) {
        final id = page['id'] as String;
        if (_excluded.containsKey(id)) continue;
        final failed = [
          for (final title in titles(page['title'] as String))
            if (!entries.any(
              (entry) => entry.pages.contains(id) && entry.matches(title),
            ))
              title,
        ];
        if (failed.isEmpty) {
          findable++;
        } else {
          missing.add('$id: ${failed.join(' / ')}');
        }
      }
      expect(missing, isEmpty, reason: 'not findable by title');
      // The numbers the phase report quotes.
      expect(findable + _excluded.length, pages.length);
      // ignore: avoid_print
      print(
        'search coverage: $findable findable + ${_excluded.length} excluded '
        '= ${pages.length} screen/tab pages',
      );
    });

    test('exclusions are real pages, have a reason, and are not indexed', () {
      final ids = {for (final page in pages) page['id'] as String};
      for (final excluded in _excluded.entries) {
        expect(ids, contains(excluded.key), reason: 'stale exclusion');
        expect(excluded.value.trim(), isNotEmpty);
        expect(
          entries.where((entry) => entry.pages.contains(excluded.key)),
          isEmpty,
          reason: '${excluded.key} is indexed; drop the exclusion',
        );
      }
    });

    test('every indexed page id exists in the ledger', () {
      final ids = {
        for (final page in ledger['pages'] as List) (page as Map)['id'],
      };
      for (final entry in entries) {
        for (final page in entry.pages) {
          // The capability list is added to the ledger with its own step.
          expect(ids, contains(page), reason: '${entry.id} -> $page');
        }
      }
    });

    test('ids are unique and every entry can be found by its own title', () {
      expect(_ids(entries).length, entries.length);
      for (final entry in entries) {
        expect(entry.matches(entry.title), isTrue, reason: entry.id);
        expect(entry.title.trim(), isNotEmpty, reason: entry.id);
      }
      final ar = allSearchEntries(lookupAppLocalizations(const Locale('ar')));
      expect(_ids(ar), _ids(entries));
      for (final entry in ar) {
        expect(entry.matches(entry.title), isTrue, reason: 'ar ${entry.id}');
      }
    });

    test('each result names the page that holds it (reachability audit)', () {
      final byId = {for (final entry in entries) entry.id: entry};
      expect(
        byId['settings-privacy-data-use']!.parent,
        _en.settingsHubPrivacyRow,
      );
      expect(byId['ai-team']!.parent, _en.librarySettingsTitle);
      // Background checks folded into Notifications (slice-close-misc).
      expect(byId['inside-servers-monitor'], isNull);
      expect(
        byId['inside-notifications-servers']!.matches(
          _en.monitorBackgroundChecks,
        ),
        isTrue,
      );
      expect(byId['settings-try-demo']!.parent, _en.onboardingSetupGuide);
      expect(byId['settings-mcp']!.parent, _en.settingsHubToolsRow);
      expect(byId['settings-external-agents']!.parent, _en.settingsHubToolsRow);
      expect(byId['settings-voice-notices']!.parent, _en.aboutTitle);
      expect(byId['settings-show-tips-again']!.parent, _en.aboutTitle);
      expect(byId['settings-try-demo']!.matches('demo'), isTrue);
      expect(byId['archived-conversations']!.matches('archived'), isTrue);
      expect(byId['archived-conversations']!.pages, ['global-sessions']);
      expect(byId['settings-models']!.pages, ['model-picker-sheet']);
      final servers = byId['settings-saved-servers']!;
      expect(servers.pages, ['servers']);
      expect(servers.keywords, isNot(contains(_en.attentionTitle)));
    });
  });

  group('gates: a result the server or device cannot open is absent', () {
    SearchScope scope(
      ConnectionController controller, {
      PlatformCapabilities platform = const PlatformCapabilities.android(),
      bool hasShell = true,
      bool hasTeam = false,
      bool desktop = false,
    }) => SearchScope(
      controller: controller,
      platform: platform,
      hasShell: hasShell,
      hasTeam: hasTeam,
      desktop: desktop,
    );

    test(
      'OpenCode 1 on a phone has everything but desktop and AI Team',
      () async {
        final controller = await _controller();
        addTearDown(controller.dispose);
        final ids = _ids(searchIndex(_en, scope(controller)));
        expect(
          ids,
          containsAll([
            'tab-chats',
            'tab-files',
            'project-files',
            'project-changes',
            'project-terminal',
            'project-health',
            'project-worktrees',
            'project-search',
            'all-conversations',
            'settings-mcp',
            'inside-capabilities-tools',
            'inside-notifications-quiet',
            'inside-phone-running-now',
            'settings-on-this-phone',
          ]),
        );
        expect(ids, isNot(contains('ai-team')));
        expect(ids, isNot(contains('library-keyboard-shortcuts')));
        expect(ids, isNot(contains('settings-accounts')));
        expect(
          _ids(searchIndex(_en, scope(controller, hasTeam: true))),
          contains('ai-team'),
        );
      },
    );

    test('AI setup is found by name where the server shares its setup, '
        'and absent where its row is', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final found = searchEntries(_en, scope(controller), 'ai setup');
      expect(found.first.id, 'inside-server-ai-setup');
      expect(found.first.parent, _en.settingsHubThisServer);
      expect(
        _ids(searchEntries(_en, scope(controller), 'suggestions')),
        contains('inside-server-ai-setup'),
      );

      final codex = await _controller(capabilities: codexServerCapabilities);
      addTearDown(codex.dispose);
      expect(
        _ids(searchIndex(_en, scope(codex))),
        isNot(contains('inside-server-ai-setup')),
      );
      // The server, not this device, hides it: the hub can say so.
      final entry = allSearchEntries(
        _en,
      ).singleWhere((entry) => entry.id == 'inside-server-ai-setup');
      expect(entry.hiddenByServer(scope(codex)), isTrue);
    });

    testWidgets('the AI setup result opens the AI setup page', (tester) async {
      final controller = await tester.runAsync(_controller);
      addTearDown(controller!.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => searchEntries(
                _en,
                scope(controller),
                'AI setup',
              ).first.open(context, scope(controller)),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byType(AiSetupScreen), findsOneWidget);
      expect(
        tester.widget<AiSetupScreen>(find.byType(AiSetupScreen)).serverName,
        'Workstation',
      );
    });

    for (final backend in {
      'Codex': codexServerCapabilities,
      'Paseo': paseoServerCapabilities,
    }.entries) {
      test('${backend.key} drops the catalog and the Project tools', () async {
        final capabilities = backend.value;
        final controller = await _controller(capabilities: capabilities);
        addTearDown(controller.dispose);
        final index = searchIndex(_en, scope(controller));
        final ids = _ids(index);
        for (final id in [
          'settings-models',
          'settings-mcp',
          'settings-commands-tools',
          'inside-capabilities-commands',
          'inside-capabilities-tools',
          'inside-capabilities-skills',
          'inside-capabilities-references',
        ]) {
          expect(ids, isNot(contains(id)), reason: id);
        }
        final tools = ProjectHub.toolsFor(capabilities);
        for (final tool in ProjectTool.values) {
          expect(
            ids.contains('project-${tool.name}'),
            tools.contains(tool),
            reason: tool.name,
          );
        }
        expect(ids.contains('tab-files'), tools.isNotEmpty);
        expect(
          ids.contains('all-conversations'),
          capabilities.globalSessionSearch,
        );
        // Providers and accounts is the Codex account's door; there is no
        // second row for it.
        expect(ids.contains('settings-providers'), capabilities.agentAccount);
        expect(ids, isNot(contains('settings-accounts')));
        // Typing the name of something absent finds nothing that opens it.
        expect(
          searchEntries(
            _en,
            scope(controller),
            _en.libraryMcpTitle,
          ).where((entry) => entry.pages.contains('integrations')),
          isEmpty,
        );
        // What is about the app, not the server, survives.
        expect(
          ids,
          containsAll([
            'settings-category-appearance',
            'inside-appearance-language',
            'tab-chats',
          ]),
        );
      });
    }

    test('OpenCode 2 keeps the catalog', () async {
      final controller = await _controller(
        capabilities: api2ServerCapabilities,
      );
      addTearDown(controller.dispose);
      final ids = _ids(searchIndex(_en, scope(controller)));
      expect(
        ids.contains('settings-mcp'),
        api2ServerCapabilities.serverCatalog,
      );
      expect(ids.contains('project-terminal'), api2ServerCapabilities.terminal);
      expect(
        ids.contains('inside-capabilities-tools'),
        api2ServerCapabilities.serverCatalog &&
            api2ServerCapabilities.toolInventory,
      );
    });

    test('a desktop has no phone-only results and gains shortcuts', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final ids = _ids(
        searchIndex(
          _en,
          scope(
            controller,
            platform: const PlatformCapabilities(
              platform: TargetPlatform.linux,
            ),
            desktop: true,
          ),
        ),
      );
      for (final id in [
        'settings-on-this-phone',
        'inside-phone-running-now',
        'inside-phone-storage',
        'settings-tailscale',
        'settings-voice',
        'settings-voice-notices',
        'inside-notifications-quiet',
        'inside-notifications-background',
      ]) {
        expect(ids, isNot(contains(id)), reason: id);
      }
      expect(ids, contains('library-keyboard-shortcuts'));
    });

    test('without the shell, what lives in its tabs is absent', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final ids = _ids(searchIndex(_en, scope(controller, hasShell: false)));
      for (final id in [
        'tab-chats',
        'tab-files',
        'tab-settings',
        'project-files',
        'project-search',
      ]) {
        expect(ids, isNot(contains(id)), reason: id);
      }
      // Tools that are screens of their own still open from anywhere.
      expect(ids, containsAll(['project-terminal', 'project-changes']));
    });

    test('title matches rank above keyword matches', () async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final results = searchEntries(_en, scope(controller), 'terminal');
      expect(results.first.id, 'project-terminal');
      expect(_ids(results), contains('default-shell-settings-entry'));
      expect(searchEntries(_en, scope(controller), '   '), isEmpty);
    });
  });

  group('Settings search', () {
    // Settings has no search field of its own: the header's command launcher
    // reads this same index, so what it finds is what these tests read.
    List<SearchEntry> found(ConnectionController controller, String query) =>
        searchEntries(_en, SearchScope(controller: controller), query);

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

    testWidgets(
      'finds a setting inside a screen and opens it at that section',
      (tester) async {
        tester.view.physicalSize = const Size(390, 500);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final controller = await _controller();
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller));
        await tester.pumpAndSettle();

        // The door and the thing itself.
        final results = found(controller, 'quiet hours');
        expect(_ids(results), contains('settings-category-background'));
        final result = results.firstWhere(
          (entry) => entry.id == 'inside-notifications-quiet',
        );
        expect(result.parent, _en.settingsHubGroupNotifications);

        await openResult(
          tester,
          controller,
          'quiet hours',
          'inside-notifications-quiet',
        );
        await tester.pumpAndSettle();
        expect(_key('notifications-settings'), findsOneWidget);
        // On a 500 dp tall phone Quiet hours starts below the fold; the result
        // opens the screen already scrolled to it.
        final section = tester.getRect(_key('notifications-section-quiet'));
        expect(section.top, greaterThanOrEqualTo(0));
        expect(section.top, lessThan(500));
        expect(
          tester.getRect(_key('notifications-section-what')).top,
          lessThan(section.top),
        );
        expect(
          tester
              .state<ScrollableState>(
                find.descendant(
                  of: _key('notifications-settings'),
                  matching: find.byType(Scrollable),
                ),
              )
              .position
              .pixels,
          greaterThan(0),
        );
      },
    );

    testWidgets('language and theme open Appearance', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      for (final entry in {
        'language': 'inside-appearance-language',
        'theme': 'inside-appearance-theme',
      }.entries) {
        expect(
          _ids(found(controller, entry.key)),
          contains(entry.value),
          reason: entry.key,
        );
      }
      await openResult(tester, controller, 'theme', 'inside-appearance-theme');
      await tester.pumpAndSettle();
      expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
      expect(_key('theme-pack-${'opencode'}'), findsWidgets);
    });

    testWidgets('budget and always allowed are found', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      expect(
        _ids(found(controller, 'budget')),
        containsAll(['inside-usage-budgets', 'settings-category-usage']),
      );
      // Inside Notifications and background, still found by its own name.
      final always = found(controller, 'always allowed');
      expect(_ids(always), contains('saved-permissions-entry'));
      expect(
        always.firstWhere((e) => e.id == 'saved-permissions-entry').parent,
        _en.settingsHubGroupNotifications,
      );
    });

    testWidgets(
      'Keep running, What runs by itself and Plugins lead somewhere sensible',
      (tester) async {
        final controller = await _controller();
        addTearDown(controller.dispose);
        expect(
          _ids(found(controller, 'keep running')),
          contains('settings-keep-running'),
        );
        expect(
          _ids(found(controller, _en.automationTitle)),
          contains('settings-automation'),
        );
        // Plugins was a page holding only the AI Team row; its words lead
        // to the AI Team.
        expect(
          _ids(found(controller, _en.teamUiPluginsTitle)),
          contains('settings-ai-team'),
        );
        expect(
          _ids(found(controller, _en.teamUiPluginsTitle)),
          isNot(contains('settings-category-plugins')),
        );
      },
    );

    testWidgets('a tab result asks the shell for that tab', (tester) async {
      final seen = <Intent>[];
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, shell: seen));
      await tester.pumpAndSettle();

      await openResult(tester, controller, _en.shellTabFiles, 'tab-files');
      await tester.pump();
      expect(seen.single, isA<SelectDestinationIntent>());
      expect((seen.single as SelectDestinationIntent).index, 1);

      seen.clear();
      await openResult(tester, controller, _en.readerUiFiles, 'project-files');
      await tester.pump();
      expect((seen.single as OpenProjectToolIntent).tool, ProjectTool.files);
      // The hub never offers a way to itself.
      expect(
        _ids(found(controller, _en.librarySettingsTitle)),
        isNot(contains('tab-settings')),
      );
    });

    testWidgets('without a shell the tabs are not offered; tools still are', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final scope = SearchScope(controller: controller, hasShell: false);
      expect(
        _ids(searchEntries(_en, scope, _en.shellTabChats)),
        isNot(contains('tab-chats')),
      );
      expect(
        _ids(searchEntries(_en, scope, 'terminal')),
        contains('project-terminal'),
      );
    });

    testWidgets('Codex: absent results stay absent', (tester) async {
      final controller = await _controller(
        capabilities: codexServerCapabilities,
      );
      addTearDown(controller.dispose);
      final scope = SearchScope(controller: controller, hasShell: true);
      for (final query in ['terminal', 'skills', 'worktrees', 'files']) {
        final ids = _ids(searchEntries(_en, scope, query));
        expect(
          ids.where((id) => id.startsWith('inside-capabilities')),
          isEmpty,
          reason: query,
        );
      }
    });
  });

  group('desktop command palette', () {
    testWidgets('finds a command by a keyword that is not shown', (
      tester,
    ) async {
      var opened = 0;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => showCommandPalette(context, [
                DesktopCommand(
                  label: 'Notifications',
                  icon: Icons.notifications,
                  keywords: 'quiet hours battery',
                  onInvoke: () => opened++,
                ),
                DesktopCommand(
                  label: 'Appearance',
                  icon: Icons.palette,
                  keywords: 'theme language',
                  onInvoke: () {},
                ),
              ]),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: _key('desktop-command-palette'),
          matching: find.byType(TextField),
        ),
        'quiet',
      );
      await tester.pump();
      expect(find.text('Notifications'), findsOneWidget);
      expect(find.text('Appearance'), findsNothing);
      await tester.tap(find.text('Notifications'));
      await tester.pumpAndSettle();
      expect(opened, 1);
    });
  });
}
