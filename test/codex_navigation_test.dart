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
      expect(find.byKey(const ValueKey('workspace-terminal')), findsNothing);

      await tester.tap(find.text('Activity'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('current-tab-title')))
            .data,
        'Activity',
      );

      await tester.tap(find.text('More'));
      await tester.pumpAndSettle();
      expect(find.text('Settings'), findsOneWidget);
      expect(find.text('Models & agents'), findsNothing);
      expect(find.byKey(const ValueKey('library-terminal')), findsNothing);
      expect(
        find.byKey(const ValueKey('library-import-session')),
        findsNothing,
      );
    },
  );
}
