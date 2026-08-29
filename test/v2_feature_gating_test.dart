/// The §7 feature-gating contract (`docs/opencode2-ui-design.md`).
///
/// Every case is paired: the same screen is pumped against a connection
/// reporting [ServerCapabilities.allV1] and against one reporting
/// [api2ServerCapabilities]. The v1 half is the regression bar — a v1 server
/// must see exactly what it saw before this layer existed — and the v2 half
/// asserts the locked treatment for that surface class:
///
/// - nav destinations, tiles and menu actions with no v2 backend: **hidden**
/// - settings rows: **shown disabled** with a one-line explainer
/// - v2-only features on a v1 server: **hidden**, no explainer
library;

import 'package:flutter/material.dart';
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
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/project_health_screen.dart';
import 'package:opencode_mobile/ui/screens/requests_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/tools_screen.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:opencode_mobile/ui/widgets/product_states.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A transport that speaks v1 and reports the v1 superset, like today's
/// [OpenCodeApi].
class _V1Api extends OpenCodeApi {
  _V1Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];
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
      FormatterHealth(name: 'dart format', extensions: ['.dart'], enabled: true),
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
  final view = TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
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

  group('hidden: nav destination with no v2 backend (§7 row 20)', () {
    testWidgets('v1 keeps the Tools tab in Commands & tools', (tester) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _app(CapabilitiesScreen(controller: controller)),
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey('capabilities-tab-Tools')),
        findsOneWidget,
      );
      expect(find.byType(Tab), findsNWidgets(4));
    });

    testWidgets('v2 drops the tab and keeps the screen', (tester) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _app(CapabilitiesScreen(controller: controller)),
      );
      await tester.pump();

      expect(find.byKey(const ValueKey('capabilities-tab-Tools')), findsNothing);
      expect(find.byType(ToolsScreen), findsNothing);
      // The surviving catalogs still have their tabs — the screen lives on.
      expect(find.byType(Tab), findsNWidgets(3));
      for (final tab in const ['Commands', 'Skills', 'References']) {
        expect(find.byKey(ValueKey('capabilities-tab-$tab')), findsOneWidget);
      }
    });
  });

  group('hidden: More tile whose screen has no backend (§7 row 1)', () {
    // The workspace hub is one long scroll; a tall surface builds every
    // sliver so presence/absence is what the assertions actually measure.
    setUp(() => _useTallSurface());

    testWidgets('v1 offers Managed workspaces', (tester) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _app(Scaffold(body: WorkspaceScreen(controller: controller))),
      );
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('managed-workspaces-entry')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('worktrees-entry')), findsOneWidget);
    });

    testWidgets('v2 hides the tile and leaves the rest of Coding', (
      tester,
    ) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _app(Scaffold(body: WorkspaceScreen(controller: controller))),
      );
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('managed-workspaces-entry')),
        findsNothing,
      );
      // No explainer for a hidden tile: the grid simply reflows.
      expect(find.textContaining('OpenCode 2'), findsNothing);
      expect(find.byKey(const ValueKey('worktrees-entry')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('project-health-entry')),
        findsOneWidget,
      );
    });
  });

  group('hidden: menu actions inside a surviving screen (§7 rows 10-12)', () {
    setUp(() => _useTallSurface());

    Future<void> openSessionMenu(WidgetTester tester) async {
      await tester.tap(find.byIcon(Icons.more_vert).first);
      await tester.pumpAndSettle();
    }

    testWidgets('v1 lists share and archive beside rename and delete', (
      tester,
    ) async {
      final repository = _Repository();
      final controller = await _controller(v2: false, repository: repository);
      addTearDown(controller.dispose);
      controller.sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'A session',
        directory: '/work/app',
      );

      await tester.pumpWidget(
        _app(Scaffold(body: WorkspaceScreen(controller: controller))),
      );
      await tester.pumpAndSettle();
      await openSessionMenu(tester);

      expect(find.text('Rename'), findsOneWidget);
      expect(find.text('Share'), findsOneWidget);
      expect(find.text('Archive'), findsOneWidget);
      expect(find.text('Delete'), findsOneWidget);
    });

    testWidgets('v2 drops share and archive, keeping the rest', (tester) async {
      final repository = _Repository();
      final controller = await _controller(v2: true, repository: repository);
      addTearDown(controller.dispose);
      controller.sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'A session',
        directory: '/work/app',
      );

      await tester.pumpWidget(
        _app(Scaffold(body: WorkspaceScreen(controller: controller))),
      );
      await tester.pumpAndSettle();
      await openSessionMenu(tester);

      expect(find.text('Share'), findsNothing);
      expect(find.text('Archive'), findsNothing);
      // Menus list possible actions only — no disabled rows, no explainers.
      expect(find.text('Rename'), findsOneWidget);
      expect(find.text('Delete'), findsOneWidget);
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

      expect(find.text('LANGUAGE SERVICES'), findsOneWidget);
      expect(find.text('FORMATTERS'), findsOneWidget);
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

      expect(find.text('LANGUAGE SERVICES'), findsNothing);
      expect(find.text('FORMATTERS'), findsNothing);
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
      expect(tester.widget<ListTile>(row).enabled, isFalse);
      expect(find.text('Run `git init` from a terminal'), findsOneWidget);
    });
  });

  group('disabled settings rows carry an explainer (§7 rows 22, 24)', () {
    testWidgets('v1 keeps the default shell row live', (tester) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _app(CodingSettingsScreen(controller: controller)),
      );
      await tester.pump();

      expect(
        find.byKey(const ValueKey('default-shell-settings-entry')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('gated-shell-settings')), findsNothing);
    });

    testWidgets('v2 shows the shell row disabled and says why', (tester) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        _app(CodingSettingsScreen(controller: controller)),
      );
      await tester.pump();

      // The row survives — a vanished settings row reads as a bug.
      expect(
        find.byKey(const ValueKey('default-shell-settings-entry')),
        findsNothing,
      );
      final row = find.byKey(const ValueKey('gated-shell-settings'));
      expect(row, findsOneWidget);
      expect(tester.widget<ListTile>(row).enabled, isFalse);
      expect(find.text('Default shell'), findsOneWidget);
      expect(
        find.text("Shell selection isn't available on OpenCode 2 servers"),
        findsOneWidget,
      );

      // Tapping explains instead of doing nothing at all.
      await tester.tap(row);
      await tester.pumpAndSettle();
      expect(find.text('Requires an OpenCode 1 server'), findsOneWidget);
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
      expect(tester.widget<ListTile>(row).enabled, isFalse);
      expect(
        find.text('Upgrade from the machine running the server'),
        findsOneWidget,
      );
    });

    testWidgets('v2 disables the diagnostics send button in place', (
      tester,
    ) async {
      final v1 = await _controller(v2: false);
      addTearDown(v1.dispose);
      await tester.pumpWidget(_app(AppDiagnosticsScreen(controller: v1)));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('gated-client-diagnostics')),
        findsNothing,
      );

      final v2 = await _controller(v2: true);
      addTearDown(v2.dispose);
      await tester.pumpWidget(_app(AppDiagnosticsScreen(controller: v2)));
      await tester.pumpAndSettle();

      final send = find.byKey(const ValueKey('send-app-diagnostics'));
      expect(send, findsOneWidget);
      expect(tester.widget<FilledButton>(send).onPressed, isNull);
      expect(
        find.byKey(const ValueKey('gated-client-diagnostics')),
        findsOneWidget,
      );
      expect(
        find.text("This server doesn't accept client logs"),
        findsOneWidget,
      );
      // Copy still works: only the server-bound action is gated.
      expect(
        find.byKey(const ValueKey('copy-app-diagnostics')),
        findsOneWidget,
      );
    });
  });

  group('hidden: v2-only features on a v1 server (§7 rule 5)', () {
    testWidgets('a v2 connection lists a pending form', (tester) async {
      final controller = await _controller(v2: true);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(_formCreated());

      await tester.pumpWidget(_app(RequestsScreen(controller: controller)));
      await tester.pumpAndSettle();

      expect(find.text('Connect to Sentry'), findsOneWidget);
      expect(find.text('Nothing needs attention'), findsNothing);
    });

    testWidgets('the same form stays hidden on a v1 connection', (
      tester,
    ) async {
      final controller = await _controller(v2: false);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(_formCreated());

      await tester.pumpWidget(_app(RequestsScreen(controller: controller)));
      await tester.pumpAndSettle();

      expect(find.text('Connect to Sentry'), findsNothing);
      // Silently hidden: no explainer for a feature the user has never seen.
      expect(find.textContaining('OpenCode 2'), findsNothing);
      expect(find.text('Nothing needs attention'), findsOneWidget);
    });
  });

  group('GatedRow copy', () {
    testWidgets('names the generation the feature needs', (tester) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: ListView(
              children: const [
                GatedRowTile(
                  feature: 'example',
                  title: 'Example',
                  explainer: gatedOnV2Explainer,
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final row = find.byKey(const ValueKey('gated-example'));
      expect(row, findsOneWidget);
      expect(tester.widget<ListTile>(row).enabled, isFalse);
      expect(
        find.text('Not available on OpenCode 2 servers'),
        findsOneWidget,
      );
      // Capability gating, not plan gating: no upsell, no call to action.
      expect(find.byType(FilledButton), findsNothing);

      await tester.tap(row);
      await tester.pumpAndSettle();
      expect(find.text('Requires an OpenCode 1 server'), findsOneWidget);
    });
  });
}
