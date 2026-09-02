import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttpOverrides extends HttpOverrides {}

class _ControlledApi extends OpenCodeApi {
  _ControlledApi() : super(baseUrl: 'http://127.0.0.1:1');

  final healthResult = Completer<Health>();

  @override
  Future<Health> health() => healthResult.future;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);

  @override
  Future<List<AgentInfo>> agents() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _FakeEventStream extends EventStream {
  _FakeEventStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  @override
  void start() => onStatus(StreamStatus.connecting);

  @override
  Future<void> dispose() async {}
}

/// What the scripted server answers; shared by every gateway the controller
/// builds for one connection, so a test can change it between phases.
class _ServerScript {
  _ServerScript({
    this.currentProjects = const {},
    this.projects = const [],
    this.projectsUnavailable = false,
    this.currentProjectFails = false,
  });

  /// `loadCurrentProject` answers keyed by the directory the gateway is
  /// scoped to; a missing key means the server knows no such project.
  final Map<String, WorkspaceProject?> currentProjects;
  List<WorkspaceProject> projects;
  bool projectsUnavailable;
  final bool currentProjectFails;
  final seenDirectories = <String?>[];
  int projectListCalls = 0;
}

class _LocationRepository extends SdkProductRepository {
  _LocationRepository(OpenCodeApi api, this.script) : super(api.sdkClient);

  final _ServerScript script;
  String? selectedDirectory;

  @override
  void setLocation({String? directory, String? workspace}) {
    selectedDirectory = directory;
    script.seenDirectories.add(directory);
    super.setLocation(directory: directory, workspace: workspace);
  }

  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];

  @override
  Future<WorkspaceProject?> loadCurrentProject() async {
    if (script.currentProjectFails) {
      throw const ProductException('Could not load the current project');
    }
    return script.currentProjects[selectedDirectory];
  }

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    script.projectListCalls += 1;
    if (script.projectsUnavailable) {
      throw const ProductException('Could not load projects');
    }
    return script.projects;
  }

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
}

WorkspaceProject _project(
  String id,
  String directory, {
  int updatedAt = 0,
  List<String> worktrees = const [],
}) => WorkspaceProject(
  id: id,
  name: id,
  directory: directory,
  worktrees: worktrees,
  updatedAt: updatedAt,
);

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

ServerProfile _profile() =>
    ServerProfile(id: 'server', name: 'server', baseUrl: 'http://127.0.0.1:1');

Future<ConnectionController> _connect(
  WidgetTester tester,
  ProfileStore store,
  _ServerScript script,
) async {
  final apis = <_ControlledApi>[];
  final controller = ConnectionController(
    store,
    apiFactory: (_) {
      final api = _ControlledApi();
      apis.add(api);
      return api;
    },
    repositoryFactory: (api) => _LocationRepository(api, script),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _FakeEventStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  final connect = controller.connect(_profile());
  await tester.pump();
  apis.first.healthResult.complete(Health(healthy: true, version: '1'));
  await connect;
  await tester.pump();
  return controller;
}

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);

  test('directory paths compare without their trailing separators', () {
    expect(
      ConnectionController.normalizeDirectoryPath('/work/acme/'),
      '/work/acme',
    );
    expect(
      ConnectionController.normalizeDirectoryPath('/work/acme//'),
      '/work/acme',
    );
    expect(ConnectionController.normalizeDirectoryPath('/'), '/');
    expect(ConnectionController.normalizeDirectoryPath(r'C:\'), r'C:\');
    expect(
      ConnectionController.normalizeDirectoryPath('/Work/Acme'),
      '/Work/Acme',
    );
    expect(
      ConnectionController.sameDirectoryPath('/work/acme/', '/work/acme'),
      isTrue,
    );
    expect(
      ConnectionController.sameDirectoryPath('/work/acme', '/work/Acme'),
      isFalse,
    );
    final project = _project('p', '/work/acme/', worktrees: ['/work/acme-wt']);
    expect(
      ConnectionController.projectContainsDirectory(project, '/work/acme'),
      isTrue,
    );
    expect(
      ConnectionController.projectContainsDirectory(project, '/work/acme/lib/'),
      isTrue,
    );
    expect(
      ConnectionController.projectContainsDirectory(project, '/work/acme-wt/'),
      isTrue,
    );
    expect(
      ConnectionController.projectContainsDirectory(project, '/work/other'),
      isFalse,
    );
    expect(
      ConnectionController.projectContainsDirectory(
        _project('global', '/'),
        '/work/acme',
      ),
      isFalse,
    );
  });

  testWidgets('relaunch restores the saved directory', (tester) async {
    final store = await _store();
    await store.setLocation('server', directory: '/work/acme');
    final controller = await _connect(
      tester,
      store,
      _ServerScript(
        currentProjects: {'/work/acme': _project('acme', '/work/acme')},
      ),
    );

    expect(controller.directory, '/work/acme');
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/work/acme');
    controller.dispose();
  });

  testWidgets('a trailing slash on the saved directory still restores it', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/work/acme/');
    final script = _ServerScript(
      currentProjects: {'/work/acme': _project('acme', '/work/acme')},
    );
    final controller = await _connect(tester, store, script);

    expect(script.seenDirectories, contains('/work/acme'));
    expect(script.seenDirectories, isNot(contains('/work/acme/')));
    expect(controller.directory, '/work/acme');
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/work/acme');
    controller.dispose();
  });

  testWidgets(
    'a trailing slash on the server side still matches the project list',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/acme');
      final controller = await _connect(
        tester,
        store,
        _ServerScript(
          // The current-project lookup falls through to the catch-all root
          // (what a server does for a plain folder), so the list decides.
          currentProjects: {'/work/acme': _project('global', '/')},
          projects: [_project('acme', '/work/acme/', updatedAt: 5)],
        ),
      );

      expect(controller.directory, '/work/acme');
      expect(controller.locationNotice, isNull);
      controller.dispose();
    },
  );

  testWidgets('a missing directory falls back to the newest project', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/deleted/worktree');
    final controller = await _connect(
      tester,
      store,
      _ServerScript(
        projects: [
          _project('old', '/work/old', updatedAt: 10),
          _project('newest', '/work/newest', updatedAt: 30),
          _project('global', '/', updatedAt: 99),
          _project('middle', '/work/middle', updatedAt: 20),
        ],
      ),
    );

    expect(controller.directory, '/work/newest');
    expect(controller.workspace, isNull);
    expect(controller.locationNotice, contains('/deleted/worktree'));
    expect(controller.locationNotice, contains('opened newest instead'));
    expect(store.locationFor('server')?.directory, '/work/newest');
    controller.dispose();
  });

  testWidgets(
    'an unavailable project list restores optimistically and re-checks later',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/acme');
      final script = _ServerScript(
        currentProjectFails: true,
        projectsUnavailable: true,
      );
      final controller = await _connect(tester, store, script);

      expect(controller.directory, '/work/acme');
      expect(controller.pendingLocationRevalidation, isTrue);
      expect(store.locationFor('server')?.directory, '/work/acme');

      // The list loads later and still knows the directory: nothing moves.
      script.projectsUnavailable = false;
      script.projects = [_project('acme', '/work/acme', updatedAt: 1)];
      await controller.revalidateRestoredLocation();
      await tester.pump();
      expect(controller.directory, '/work/acme');
      expect(controller.pendingLocationRevalidation, isFalse);
      expect(controller.locationNotice, isNull);
      controller.dispose();
    },
  );

  testWidgets(
    'a re-check that proves the directory gone opens the newest project',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/gone');
      final script = _ServerScript(
        currentProjectFails: true,
        projectsUnavailable: true,
      );
      final controller = await _connect(tester, store, script);
      expect(controller.directory, '/work/gone');

      script.projectsUnavailable = false;
      script.projects = [
        _project('a', '/work/a', updatedAt: 1),
        _project('b', '/work/b', updatedAt: 2),
      ];
      await controller.revalidateRestoredLocation();
      await tester.pump();

      expect(controller.directory, '/work/b');
      expect(controller.locationNotice, contains('/work/gone'));
      expect(controller.locationNotice, contains('opened b instead'));
      expect(store.locationFor('server')?.directory, '/work/b');
      controller.dispose();
    },
  );

  testWidgets('a server with no projects returns to its root with a notice', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/deleted/worktree');
    final controller = await _connect(tester, store, _ServerScript());

    expect(controller.directory, isNull);
    expect(controller.locationNotice, contains('no longer available'));
    expect(store.locationFor('server'), isNull);
    controller.dispose();
  });
}
