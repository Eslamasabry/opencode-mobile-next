/// The §7 feature-gating contract (`docs/opencode2-ui-design.md`).
///
/// Every case is paired: the same screen is pumped against a connection
/// reporting [ServerCapabilities.allV1] and against one reporting
/// [api2ServerCapabilities]. The v1 half is the regression bar — a v1 server
/// must see exactly what it saw before this layer existed — and the v2 half
/// asserts the locked treatment for that surface class:
///
/// - unsupported tiles and menu actions: **hidden**
/// - catalog Tools tab: retained with an explainer (screen-library-2 revamp)
/// - settings rows: **shown disabled** with a one-line explainer
/// - v2-only features on a v1 server: **hidden**, no explainer
library;

import 'support/complete_message_history.dart';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/project_health_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/tools_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A transport that speaks v1 and reports the v1 superset, like today's
/// [OpenCodeApi].
class _V1Api extends OpenCodeApi with CompleteMessageHistory {
  _V1Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  // The Settings hub probes health on open; keep it off the network.
  @override
  Future<Health> health() async => Health(healthy: true, version: '1.0.0');
}

/// A transport reporting the exact OpenCode 2 capability truth. Only the
/// capability surface matters here — the gates never call it.
class _V2Api extends _V1Api {
  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;
}

class _Repository implements ProductRepository {
  List<WorkspaceProject> projects = const [
    WorkspaceProject(
      id: 'project-1',
      name: 'OpenCode Mobile',
      directory: '/work/app',
      worktrees: [],
      updatedAt: 1,
    ),
  ];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  // A growable copy: the workspace hub sorts what it is handed.
  Future<List<WorkspaceProject>> listProjects() async => [...projects];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: 'bash', options: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Health repository with git absent, so the Initialize Git action renders.
class _HealthRepository implements ProductRepository {
  VersionControlHealth versionControl = const VersionControlHealth(
    setupState: VersionControlSetupState.absent,
    changes: [],
  );
  int languageServiceCalls = 0;
  int formatterCalls = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<VersionControlHealth> loadVersionControlHealth() async =>
      versionControl;

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async {
    languageServiceCalls += 1;
    return const [
      LanguageServiceHealth(
        id: 'dart',
        name: 'Dart analysis server',
        root: '/work/app',
        status: 'connected',
      ),
    ];
  }

  @override
  Future<List<FormatterHealth>> listFormatters() async {
    formatterCalls += 1;
    return const [
      FormatterHealth(
        name: 'dart format',
        extensions: ['.dart'],
        enabled: true,
      ),
    ];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Keeps the injected state still: the screens under test each kick a
/// refresh on first frame, and these tests are about what the capability
/// flags render, not about what a fake transport answers.
class _GatingController extends ConnectionController {
  _GatingController(super.store);

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> refreshPendingPermissions() async {}

  @override
  Future<void> refreshPendingQuestions() async {}

  @override
  Future<void> refreshPendingForms() async {}

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    this.directory = directory;
    this.workspace = workspace;
  }

  @override
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => selectLocation(directory: directory, workspace: workspace);

  @override
  Future<void> selectInitialLocation({String? directory, String? workspace}) =>
      selectLocation(directory: directory, workspace: workspace);
}

Future<_GatingController> _controller({
  required bool v2,
  ProductRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _GatingController(ProfileStore(prefs: preferences))
    ..api = v2 ? _V2Api() : _V1Api()
    ..repository = repository ?? _Repository()
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
}

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// Gives the current test a phone-width but very tall surface, so a long
/// scrolling hub builds all of its slivers and "findsNothing" means hidden
/// rather than merely below the fold.
void _useTallSurface() {
  final view =
      TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
  view.physicalSize = const Size(400, 3000);
  view.devicePixelRatio = 1;
  addTearDown(view.resetPhysicalSize);
  addTearDown(view.resetDevicePixelRatio);
}

EventEnvelope _formCreated() => EventEnvelope(
  type: 'form.v2.created',
  properties: {
    'form': {
      'id': 'frm_1',
      'sessionID': 'session-1',
      'title': 'Connect to Sentry',
      'fields': [
        {'key': 'confirm', 'type': 'boolean', 'title': 'Confirm'},
      ],
    },
  },
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, null);
  });

  group('the accessor reports transport truth', () {
    test('an unattached controller reports the v1 superset', () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      final controller = ConnectionController(ProfileStore(prefs: preferences));
      addTearDown(controller.dispose);

      // Nothing may vanish while a connection is still being made.
      expect(controller.capabilities.sessionShare, isTrue);
      expect(controller.capabilities.toolInventory, isTrue);
      // v2-only flags stay off until a v2 transport says otherwise.
      expect(controller.capabilities.forms, isFalse);
      expect(controller.capabilities.inbox, isFalse);
    });

    test('an attached v2 gateway narrows the flags', () async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);

      expect(controller.capabilities.sessionShare, isFalse);
      expect(controller.capabilities.toolInventory, isFalse);
      expect(controller.capabilities.forms, isTrue);
    });
  });

  group('catalog navigation preserves the explained Tools gate', () {
    testWidgets('catalog tabs stay reachable on a narrow large-text phone', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: CapabilitiesScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      final references = find.byKey(
        const ValueKey('capabilities-tab-References'),
      );
      await Scrollable.ensureVisible(tester.element(references));
      await tester.pumpAndSettle();
      await tester.tap(references);
      await tester.pumpAndSettle();
      // MCP · Commands · Tools · Skills · References · External agents.
      expect(
        tester.widget<KitTabSwitcher>(find.byType(KitTabSwitcher)).index,
        4,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('v1 keeps the Tools tab in Commands & tools', (tester) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);

      await tester.pumpWidget(_app(CapabilitiesScreen(controller: controller)));
      await tester.pump();

      expect(
        find.byKey(const ValueKey('capabilities-tab-Tools')),
        findsOneWidget,
      );
      for (final label in const ['Commands', 'Tools', 'Skills', 'References']) {
        expect(find.byKey(ValueKey('capabilities-tab-$label')), findsOneWidget);
      }
    });

    testWidgets(
      'v2 keeps the Tools tab and explains its unavailable inventory',
      (tester) async {
        final controller = await _controller(v2: true);
        addTearDown(controller.dispose);

        await tester.pumpWidget(
          _app(CapabilitiesScreen(controller: controller)),
        );
        await tester.pump();

        final tools = find.byKey(const ValueKey('capabilities-tab-Tools'));
        expect(tools, findsOneWidget);
        await tester.tap(tools);
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('capabilities-tools-unavailable')),
          findsOneWidget,
        );
        expect(find.byType(ToolsScreen), findsNothing);
        // The other catalogs remain reachable beside the explanation.
        for (final tab in const ['Commands', 'Skills', 'References']) {
          expect(find.byKey(ValueKey('capabilities-tab-$tab')), findsOneWidget);
        }
      },
    );
  });

  group('hidden: More tile whose screen has no backend (§7 row 1)', () {
    // The workspace hub is one long scroll; a tall surface builds every
    // sliver so presence/absence is what the assertions actually measure.
    setUp(() => _useTallSurface());

    // Every project management destination is a Project tab tool (Manage
    // project merged into the tab, slice-P3.11a); the gating rule applies
    // to the tab's rows.
    Future<void> openProjectTab(
      WidgetTester tester,
      ConnectionController controller,
    ) async {
      await tester.pumpWidget(_app(ProjectHub(controller: controller)));
      await tester.pumpAndSettle();
    }

    testWidgets('v1 offers Managed workspaces', (tester) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);

      await openProjectTab(tester, controller);

      expect(
        find.byKey(const ValueKey('project-hub-workspaces')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('project-hub-worktrees')),
        findsOneWidget,
      );
    });

    testWidgets('v2 hides the tile and leaves the rest of Coding', (
      tester,
    ) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);

      await openProjectTab(tester, controller);

      expect(
        find.byKey(const ValueKey('project-hub-workspaces')),
        findsNothing,
      );
      // No explainer for a hidden tile: the list simply reflows.
      expect(find.textContaining('OpenCode 2'), findsNothing);
      expect(
        find.byKey(const ValueKey('project-hub-worktrees')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('project-hub-health')), findsOneWidget);
      // Switching project stays beside the project's name.
      expect(find.byKey(const ValueKey('project-hub-menu')), findsOneWidget);
    });
  });

  group('hidden: health sections + disabled git init (§7 rows 17-19)', () {
    testWidgets('v1 shows both status sections and the live action', (
      tester,
    ) async {
      final repository = _HealthRepository();

      await tester.pumpWidget(
        _app(ProjectHealthScreen(repository: repository)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Language services'), findsOneWidget);
      expect(find.text('Formatters'), findsOneWidget);
      expect(find.text('Dart analysis server'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('initialize-git-repository')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('gated-git-init')), findsNothing);
    });

    testWidgets('v2 hides both sections and explains git init in place', (
      tester,
    ) async {
      final repository = _HealthRepository();

      await tester.pumpWidget(
        _app(
          ProjectHealthScreen(
            repository: repository,
            capabilities: api2ServerCapabilities,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Language services'), findsNothing);
      expect(find.text('Formatters'), findsNothing);
      // A hidden section spends no request.
      expect(repository.languageServiceCalls, 0);
      expect(repository.formatterCalls, 0);
      // Version control survives, so the screen still earns its place.
      expect(find.byKey(const ValueKey('git-not-initialized')), findsOneWidget);

      // Health screens explain rather than vanish.
      expect(
        find.byKey(const ValueKey('initialize-git-repository')),
        findsNothing,
      );
      final row = find.byKey(const ValueKey('gated-git-init'));
      expect(row, findsOneWidget);
      expect(tester.widget<KitRow>(row).enabled, isFalse);
      expect(find.text('Run `git init` from a terminal'), findsOneWidget);
    });
  });

  group('settings rows the server cannot serve (§7 rows 22, 23, 24)', () {
    testWidgets('v1 keeps the default shell row live', (tester) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);

      await tester.pumpWidget(_app(SettingsScreen(controller: controller)));
      await tester.pump();

      expect(
        find.byKey(const ValueKey('default-shell-settings-entry')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('gated-shell-settings')), findsNothing);
    });

    testWidgets('v2 hides the shell row; Help says it is not available', (
      tester,
    ) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);

      await tester.pumpWidget(_app(SettingsScreen(controller: controller)));
      await tester.pump();

      // §7 row 22 used to keep this row visible-but-disabled, because a
      // vanished settings row reads as a bug. UX plan rule 7 (hide, don't
      // disable) now applies to it like every other row, and the explanation
      // moved to one place that covers all hidden rows: Settings → Help →
      // "Available on this server", which lists the shell under "Not
      // available on this server".
      expect(
        find.byKey(const ValueKey('default-shell-settings-entry')),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('gated-shell-settings')), findsNothing);
      expect(find.text('Default shell'), findsNothing);
      // Not a dead search result either.
      final shellHits = [
        for (final entry in searchEntries(
          lookupAppLocalizations(const Locale('en')),
          SearchScope(controller: controller),
          'shell',
        ))
          entry.id,
      ];
      expect(shellHits, isNot(contains('default-shell-settings-entry')));
      // "shell" is one of the words that find the explanation instead.
      expect(shellHits, contains('settings-server-capabilities'));
      // It is a Help row of the hub.
      final help = find.byKey(const ValueKey('settings-server-capabilities'));
      expect(help, findsOneWidget);

      // The hub is longer than the test window: bring the row into view.
      await tester.ensureVisible(help);
      await tester.pumpAndSettle();
      await tester.tap(help);
      await tester.pumpAndSettle();
      // R16 puts missing features first in one list, each with its own state.
      final unavailable = find.byKey(
        const ValueKey('capability-unavailable-shell'),
      );
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('capability-unavailable-shell')),
        200,
        scrollable: find
            .descendant(
              of: find.byKey(const ValueKey('server-capabilities')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(
        find.descendant(of: unavailable, matching: find.text('Default shell')),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: unavailable,
          matching: find.textContaining(
            'Not on this server',
            findRichText: true,
          ),
        ),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('capability-available-shell')),
        findsNothing,
      );
    });

    testWidgets('v2 points server updates at the host machine (§7 row 23)', (
      tester,
    ) async {
      final v1 = await _controller(v2: false);
      addTearDown(v1.dispose);
      await tester.pumpWidget(_app(ServerSettingsScreen(controller: v1)));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('server-updates-tile')), findsOneWidget);
      expect(find.byKey(const ValueKey('gated-remote-upgrade')), findsNothing);

      final v2 = await _controller(v2: true);
      addTearDown(v2.dispose);
      await tester.pumpWidget(_app(ServerSettingsScreen(controller: v2)));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('server-updates-tile')), findsNothing);
      final row = find.byKey(const ValueKey('gated-remote-upgrade'));
      expect(row, findsOneWidget);
      expect(tester.widget<KitRow>(row).enabled, isFalse);
      expect(
        find.text('Upgrade from the machine running the server'),
        findsOneWidget,
      );
    });

    testWidgets('Report a problem is the same page on v1 and v2', (
      tester,
    ) async {
      // P8.2 merged the server-log send into Report a problem, so no
      // capability decides what the page offers.
      for (final isV2 in [false, true]) {
        final controller = await _controller(v2: isV2);
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          _app(AppDiagnosticsScreen(controller: controller)),
        );
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('send-app-diagnostics')),
          findsNothing,
        );
        expect(
          find.byKey(const ValueKey('report-problem-review')),
          findsOneWidget,
        );
      }
    });
  });

  group('hidden: v2-only features on a v1 server (§7 rule 5)', () {
    testWidgets('a v2 connection keeps a pending form', (tester) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(_formCreated());
      expect(controller.forms, isNotEmpty);
      // Connecting schedules one debounced inventory refresh; let it run.
      await tester.pump(const Duration(seconds: 3));
    });

    testWidgets('the same form stays hidden on a v1 connection', (
      tester,
    ) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(_formCreated());
      expect(controller.forms, isEmpty);
      expect(controller.unifiedAttentionCount, 0);
      // Connecting schedules one debounced inventory refresh; let it run.
      await tester.pump(const Duration(seconds: 3));
    });
  });
}
