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
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
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
      // back to Chats rather than shifting Settings left.
      expect(tester.widget<KitNav>(find.byType(KitNav)).selected, 0);
      expect(find.text('Files'), findsNothing);
      expect(find.text('Chats'), findsWidgets);
      expect(find.text('Inbox'), findsNothing);
      expect(find.text('Settings'), findsOneWidget);

      // The project catalog is not queried when project management is absent.
      expect(repository.listProjectsCalls, 0);

      // The shell's KitNav keeps the logical destination mapping when
      // unsupported Project is absent, including the medium-window rail.
      expect(find.byType(KitNavRail), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('home-shell-tab-settings')));
      await tester.pumpAndSettle();
      expect(tester.widget<KitNav>(find.byType(KitNav)).selected, 1);
      expect(
        tester.widget<KitNav>(find.byType(KitNav)).destinations[1].label,
        'Settings',
      );
      // The tab is the hub itself; what is about the app survives a Codex
      // connection, what needs the server catalog is absent.
      expect(
        find.byKey(const ValueKey('settings-group-server')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('settings-category-appearance')),
        findsOneWidget,
      );
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
        home: Scaffold(
          body: SettingsScreen(controller: controller, embedded: true),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // One Model row (R3); the catalog screen is found by search.
    expect(
      find.byKey(const ValueKey('settings-model-and-mode')),
      findsOneWidget,
    );
    // The catalog stays: Providers and accounts, and Tools, which holds
    // MCP and Commands & tools (slice-P3.10).
    expect(find.text('Providers and accounts'), findsOneWidget);
    expect(find.byKey(const ValueKey('settings-tools')), findsOneWidget);
  });
}
