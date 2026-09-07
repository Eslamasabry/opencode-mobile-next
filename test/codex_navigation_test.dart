import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _CodexApi extends OpenCodeApi {
  _CodexApi(this._capabilities) : super(baseUrl: 'http://localhost');

  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _CodexRepository implements ProductRepository {
  int listProjectsCalls = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    listProjectsCalls++;
    return [];
  }

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

class _CodexProfileStore extends ProfileStore {
  _CodexProfileStore({required super.prefs})
    : profile = ServerProfile(
        id: 'codex-test',
        name: 'Codex test server',
        baseUrl: 'http://localhost',
        backend: ServerBackend.codex,
      );

  final ServerProfile profile;

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;
}

Future<ConnectionController> _controller(_CodexRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final controller =
      ConnectionController(_CodexProfileStore(prefs: preferences))
        ..api = _CodexApi(
          const ServerCapabilities(
            fileBrowsing: false,
            terminal: false,
            projectManagement: false,
            globalSessionSearch: false,
            sessionImportExport: false,
            serverCatalog: false,
          ),
        )
        ..repository = repository
        ..status = StreamStatus.connected
        ..directory = '/workspace/project';
  controller.locationNotice = 'Using the configured Codex folder';
  controller.sessionsById['pinned-1'] = Session(
    id: 'pinned-1',
    title: 'Pinned Codex session',
    directory: controller.directory,
  );
  await controller.setSessionPinned(
    'pinned-1',
    true,
    locationRevision: controller.locationRevision,
  );
  return controller;
}

Widget _app(ConnectionController controller) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: const MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: HomeScreen(initialTab: 1),
  ),
);

/// The hit-testable tap target of a NavigationRail destination, found from
/// its label. The rail's destination ink is a private InkResponse subclass,
/// so match by type hierarchy rather than exact type.
Finder _railDestination(String label) => find
    .ancestor(
      of: find.text(label),
      matching: find.byWidgetPredicate((widget) => widget is InkResponse),
    )
    .first
    .hitTestable();

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Codex hides unsupported destinations while keeping logical tab IDs',
    (tester) async {
      final repository = _CodexRepository();
      final controller = await _controller(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // Files is logical destination 1, so an initial Files selection falls
      // back to Workspace rather than shifting Activity or More left.
      expect(find.byKey(const ValueKey('current-tab-title')), findsOneWidget);
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'Workspace',
      );
      expect(find.text('Files'), findsNothing);
      expect(find.text('Workspace'), findsWidgets);
      expect(find.text('Activity'), findsOneWidget);
      expect(find.text('More'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('location-recovery-notice')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('restricted-directory-context')),
        findsOneWidget,
      );
      expect(find.text('PINNED'), findsOneWidget);
      expect(find.text('Pinned Codex session'), findsOneWidget);

      // The project catalog is not queried when project management is absent.
      expect(repository.listProjectsCalls, 0);
      expect(find.byKey(const ValueKey('search-all-sessions')), findsNothing);
      // Terminal is a section-menu entry, so its absence only means
      // something with the menu open.
      await tester.tap(find.byKey(const ValueKey('workspace-section-menu')));
      await tester.pumpAndSettle();
      expect(find.text('Reload recent sessions'), findsOneWidget);
      expect(find.byKey(const ValueKey('workspace-terminal')), findsNothing);
      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();
      expect(find.text('Reload recent sessions'), findsNothing);
      // The menu route is gone: only the shell's navigator page remains.
      expect(
        find.byWidgetPredicate((widget) => widget is PopupMenuItem),
        findsNothing,
      );

      // At the 800px test surface the shell uses a NavigationRail, whose
      // labels are zero-size semantics-only boxes; tap the destination's
      // ink well, which is what a pointer actually reaches.
      await tester.tap(_railDestination('Activity'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'Activity',
      );
      expect(
        tester
            .widget<NavigationRail>(find.byType(NavigationRail))
            .selectedIndex,
        1,
        reason: 'Files is hidden, so Activity is the second rail destination',
      );

      await tester.tap(_railDestination('More'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'More',
      );
      expect(
        tester
            .widget<NavigationRail>(find.byType(NavigationRail))
            .selectedIndex,
        2,
      );
      expect(find.text('Settings'), findsOneWidget);
      expect(find.text('Models & agents'), findsNothing);
      expect(find.text('Providers'), findsNothing);
      expect(find.text('MCP'), findsNothing);
      expect(find.text('Commands & tools'), findsNothing);
      expect(find.byKey(const ValueKey('library-terminal')), findsNothing);
      expect(
        find.byKey(const ValueKey('library-import-session')),
        findsNothing,
      );
    },
  );

  testWidgets('OpenCode keeps server catalogs even without credential writes', (
    tester,
  ) async {
    final controller = await _controller(_CodexRepository());
    addTearDown(controller.dispose);
    controller.api = _CodexApi(
      const ServerCapabilities(
        integrationCredentials: false,
        mcpConfigWrites: false,
        mcpOAuth: false,
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: LibraryScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Models & agents'), findsOneWidget);
    expect(find.text('Providers'), findsOneWidget);
    expect(find.text('MCP'), findsOneWidget);
    expect(find.text('Commands & tools'), findsOneWidget);
  });
}
