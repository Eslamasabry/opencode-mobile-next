import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttpOverrides extends HttpOverrides {}

class _ControlledApi extends OpenCodeApi {
  _ControlledApi(this.script) : super(baseUrl: 'http://127.0.0.1:1');

  final _ServerScript script;

  final healthResult = Completer<Health>();

  @override
  Future<Health> health() => healthResult.future;

  @override
  Future<List<Session>> sessions() async {
    await script.sessionsGate?.future;
    if (script.sessionsFail) throw ApiException('Session refresh unavailable');
    return const [];
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<FileNode>> listFiles(String path) async {
    switch (script.folderProbe) {
      case _FolderProbe.exists:
        return const [];
      case _FolderProbe.missing:
        throw ApiException('List files failed', statusCode: 404);
      case _FolderProbe.unreachable:
        throw ApiException('Connection failed');
    }
  }

  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);

  // The v1 catalog load also reads the runtime view; answer it locally so
  // the test never reaches the network.
  @override
  Future<ProvidersResponse> configuredProviders() async =>
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
enum _FolderProbe { exists, missing, unreachable }

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
  Completer<void>? sessionsGate;
  bool sessionsFail = false;
  bool workspacesUnavailable = false;
  _FolderProbe folderProbe = _FolderProbe.unreachable;
  List<WorkspaceInfo> workspaces = const [];
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
  Future<List<WorkspaceInfo>> listWorkspaces() async {
    if (script.workspacesUnavailable) {
      throw const ProductException('Workspace list unavailable');
    }
    return script.workspaces;
  }
}

class _DelayedLocationStore extends ProfileStore {
  _DelayedLocationStore(SharedPreferences prefs) : super(prefs: prefs);

  Completer<void>? locationGate;
  bool locationWriteStarted = false;

  @override
  Future<void> setLocation(
    String profileId, {
    String? directory,
    String? workspace,
  }) async {
    locationWriteStarted = true;
    await locationGate?.future;
    await super.setLocation(
      profileId,
      directory: directory,
      workspace: workspace,
    );
  }
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
  _ServerScript script, {
  Future<void> Function(ConnectionController)? duringConnect,
  ServerProfile? profile,
}) async {
  final apis = <_ControlledApi>[];
  final controller = ConnectionController(
    store,
    apiFactory: (_) {
      final api = _ControlledApi(script);
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
  final connect = controller.connect(profile ?? _profile());
  await tester.pump();
  await duringConnect?.call(controller);
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

  testWidgets(
    'explicit project B survives a fresh controller with only server cwd in the catalog',
    (tester) async {
      final store = await _store();
      final script = _ServerScript(
        currentProjects: {'/work/b': _project('global', '/')},
        projects: [_project('server-cwd', '/work/a', updatedAt: 99)],
      );
      final first = await _connect(tester, store, script);
      await first.selectLocation(directory: '/work/b');
      expect(store.locationFor('server')?.directory, '/work/b');
      first.dispose();

      final restartedStore = ProfileStore(prefs: store.prefs);
      final restarted = await _connect(tester, restartedStore, script);
      expect(restarted.directory, '/work/b');
      expect(restartedStore.locationFor('server')?.directory, '/work/b');
      restarted.dispose();
    },
  );

  testWidgets(
    'initial discovery cannot replace a saved selection during connect or after restore',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/b');
      final controller = await _connect(
        tester,
        store,
        _ServerScript(currentProjects: {'/work/b': _project('b', '/work/b')}),
        duringConnect: (controller) =>
            controller.selectInitialLocation(directory: '/work/a'),
      );
      expect(controller.directory, '/work/b');
      await controller.selectInitialLocation(directory: '/work/a');
      expect(controller.directory, '/work/b');
      expect(store.locationFor('server')?.directory, '/work/b');
      controller.dispose();
    },
  );

  testWidgets(
    'project selection is saved before a slow failed refresh completes',
    (tester) async {
      final store = await _store();
      final script = _ServerScript();
      final controller = await _connect(tester, store, script);
      await controller.selectLocation(directory: '/work/a');
      script.sessionsGate = Completer<void>();
      script.sessionsFail = true;
      final selecting = controller.selectLocation(directory: '/work/b');
      await tester.pump();
      expect(controller.locationLoading, isTrue);
      expect(store.locationFor('server')?.directory, '/work/b');
      script.sessionsGate!.complete();
      await selecting;
      expect(
        controller.locationError,
        'This folder could not be opened. Try again.',
      );
      expect(
        controller.locationFailure.toString(),
        contains('Session refresh unavailable'),
      );
      expect(store.locationFor('server')?.directory, '/work/b');
      controller.dispose();
    },
  );

  testWidgets(
    'explicit project choices restore independently for each profile',
    (tester) async {
      final store = await _store();
      final script = _ServerScript();
      final first = await _connect(tester, store, script);
      await first.selectLocation(directory: '/work/b');
      first.dispose();
      final otherProfile = ServerProfile(
        id: 'other',
        name: 'other',
        baseUrl: 'http://127.0.0.1:1',
      );
      final other = await _connect(
        tester,
        ProfileStore(prefs: store.prefs),
        script,
        profile: otherProfile,
      );
      await other.selectLocation(directory: '/work/c');
      other.dispose();
      final restarted = await _connect(
        tester,
        ProfileStore(prefs: store.prefs),
        script,
      );
      expect(restarted.directory, '/work/b');
      expect(store.locationFor('other')?.directory, '/work/c');
      restarted.dispose();
    },
  );

  for (final unavailable in [false, true]) {
    testWidgets(
      'remote workspace stays selected when its catalog is ${unavailable ? 'unavailable' : 'missing the entry'}',
      (tester) async {
        final store = await _store();
        await store.setLocation(
          'server',
          directory: '/work/b',
          workspace: 'remote-b',
        );
        final project = _project('b', '/work/b');
        final script = _ServerScript(
          currentProjects: {'/work/b': project},
          projects: [project],
        )..workspacesUnavailable = unavailable;
        final controller = await _connect(tester, store, script);
        expect(controller.directory, '/work/b');
        expect(controller.workspace, 'remote-b');
        expect(store.locationFor('server')?.workspace, 'remote-b');
        expect(controller.locationNotice, isNull);
        expect(controller.pendingLocationRevalidation, isTrue);

        script.workspacesUnavailable = false;
        script.workspaces = const [
          WorkspaceInfo(
            id: 'remote-b',
            projectID: 'b',
            name: 'Remote B',
            type: 'remote',
            directory: '/work/b',
          ),
        ];
        await controller.revalidateRestoredLocation();
        expect(controller.directory, '/work/b');
        expect(controller.workspace, 'remote-b');
        expect(controller.locationNotice, isNull);
        expect(controller.pendingLocationRevalidation, isFalse);
        controller.dispose();
      },
    );
  }

  testWidgets(
    'profile deletion drains a delayed location write without resurrecting its selection',
    (tester) async {
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(secure, (_) async => null);
      const background = MethodChannel('oc/background');
      messenger.setMockMethodCallHandler(background, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
      addTearDown(() => messenger.setMockMethodCallHandler(background, null));
      final original = await _store();
      final store = _DelayedLocationStore(original.prefs);
      await store.upsert(_profile());
      await store.setLocation('keeper', directory: '/work/keeper');
      final controller = await _connect(tester, store, _ServerScript());
      store.locationWriteStarted = false;
      store.locationGate = Completer<void>();
      final selecting = controller.selectLocation(directory: '/work/b');
      await tester.pump();
      expect(store.locationWriteStarted, isTrue);
      var deletionFinished = false;
      final deleting = controller.deleteProfileAndLocalData('server').then((
        result,
      ) {
        deletionFinished = true;
        return result;
      });
      await tester.pump();
      expect(deletionFinished, isFalse);
      store.locationGate!.complete();
      await selecting;
      final result = await deleting;
      expect(result.removedProfile, isTrue);
      expect(store.locationFor('server'), isNull);
      expect(store.profileScopedPreferenceKeys('server'), isEmpty);
      final restartedStore = ProfileStore(prefs: store.prefs);
      await restartedStore.load();
      expect(restartedStore.locationFor('server'), isNull);
      expect(restartedStore.locationFor('keeper')?.directory, '/work/keeper');
      controller.dispose();
    },
  );

  testWidgets('relaunch restores the saved directory', (tester) async {
    final store = await _store();
    await store.setLocation('server', directory: '/work/acme');
    int? attempt;
    int? transport;
    final controller = await _connect(
      tester,
      store,
      _ServerScript(
        currentProjects: {'/work/acme': _project('acme', '/work/acme')},
      ),
      duringConnect: (controller) async {
        attempt = controller.connectionAttemptRevision;
        transport = controller.connectionRevision;
      },
    );

    expect(controller.directory, '/work/acme');
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/work/acme');
    expect(controller.connectionRevision, greaterThan(transport!));
    expect(
      controller.connectionAttemptRevision,
      attempt,
      reason: 'Saved-location bootstrap remains cancelable by its caller',
    );
    await controller.disconnect(keepActive: true);
    expect(controller.connectionAttemptRevision, greaterThan(attempt!));
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

  testWidgets('catalog omission keeps the saved directory without a notice', (
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

    expect(controller.directory, '/deleted/worktree');
    expect(controller.workspace, isNull);
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/deleted/worktree');
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
    'a later catalog omission cannot replace the selected directory',
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

      expect(controller.directory, '/work/gone');
      expect(controller.locationNotice, isNull);
      expect(store.locationFor('server')?.directory, '/work/gone');
      controller.dispose();
    },
  );

  testWidgets(
    'a folder the server can read stays selected with no notice even when no project lists it',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/fresh');
      final script = _ServerScript()..folderProbe = _FolderProbe.exists;
      final controller = await _connect(tester, store, script);

      expect(controller.directory, '/work/fresh');
      expect(controller.locationNotice, isNull);
      expect(controller.pendingLocationRevalidation, isFalse);
      expect(store.locationFor('server')?.directory, '/work/fresh');
      controller.dispose();
    },
  );

  testWidgets(
    'a folder that no longer exists is dropped quietly, never with a banner',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/gone');
      final script = _ServerScript()..folderProbe = _FolderProbe.missing;
      final controller = await _connect(tester, store, script);

      expect(controller.directory, isNull);
      expect(controller.locationNotice, isNull);
      expect(controller.pendingLocationRevalidation, isFalse);
      expect(store.locationFor('server'), isNull);
      controller.dispose();
    },
  );

  testWidgets(
    'when the folder check cannot run the selection is kept silently and rechecked later',
    (tester) async {
      final store = await _store();
      await store.setLocation('server', directory: '/work/fresh');
      final script = _ServerScript();
      final controller = await _connect(tester, store, script);

      expect(controller.directory, '/work/fresh');
      expect(controller.locationNotice, isNull);
      expect(controller.pendingLocationRevalidation, isTrue);

      script.folderProbe = _FolderProbe.exists;
      await controller.revalidateRestoredLocation();
      expect(controller.directory, '/work/fresh');
      expect(controller.pendingLocationRevalidation, isFalse);
      expect(controller.locationNotice, isNull);
      controller.dispose();
    },
  );

  testWidgets('an empty catalog keeps the saved directory without a notice', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/deleted/worktree');
    final controller = await _connect(tester, store, _ServerScript());

    expect(controller.directory, '/deleted/worktree');
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/deleted/worktree');
    controller.dispose();
  });

  testWidgets(
    'a saved home folder is forgotten instead of restored, with a notice',
    (tester) async {
      // Older builds could save the server's own home folder as the
      // location. It is never a workspace: the app forgets it and asks for
      // a project folder, without even consulting the server about it.
      final store = await _store();
      await store.setLocation('server', directory: '/root/');
      final script = _ServerScript(
        currentProjects: {'/root': _project('global', '/root', updatedAt: 99)},
        projects: [_project('global', '/root', updatedAt: 99)],
      );
      final controller = await _connect(tester, store, script);

      expect(controller.directory, isNull);
      expect(controller.workspaceChoiceRequired, isTrue);
      expect(controller.locationNotice, contains('home folder'));
      expect(store.locationFor('server'), isNull);
      expect(script.seenDirectories.whereType<String>(), isEmpty);
      controller.dispose();
    },
  );

  testWidgets('a home-folder project never replaces a missing directory', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/deleted/worktree');
    final controller = await _connect(
      tester,
      store,
      _ServerScript(
        projects: [
          _project('global', '/root', updatedAt: 99),
          _project('home', '/home/eslam', updatedAt: 98),
        ],
      ),
    );

    expect(controller.directory, '/deleted/worktree');
    expect(controller.workspaceChoiceRequired, isFalse);
    expect(store.locationFor('server')?.directory, '/deleted/worktree');
    controller.dispose();
  });

  testWidgets('selecting a home folder is refused and leaves the location', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/work/acme');
    final script = _ServerScript(
      currentProjects: {'/work/acme': _project('acme', '/work/acme')},
      projects: [_project('acme', '/work/acme')],
    );
    final controller = await _connect(tester, store, script);
    expect(controller.directory, '/work/acme');

    await controller.selectLocation(directory: '/root');
    await tester.pump();

    expect(controller.directory, '/work/acme');
    expect(controller.locationError, contains('home folder'));
    expect(controller.workspaceChoiceRequired, isFalse);
    expect(store.locationFor('server')?.directory, '/work/acme');
    controller.dispose();
  });

  testWidgets(
    'an existing conversation in the home folder can still be opened',
    (tester) async {
      // Search all sessions may open an earlier conversation stored in the
      // home folder. That rescopes the connection for reading it, but the
      // folder is never remembered and Workspace still asks for a project.
      final store = await _store();
      await store.setLocation('server', directory: '/work/acme');
      final script = _ServerScript(
        currentProjects: {'/work/acme': _project('acme', '/work/acme')},
        projects: [_project('acme', '/work/acme')],
      );
      final controller = await _connect(tester, store, script);

      await controller.selectLocationForExistingSession(directory: '/root');
      await tester.pump();

      expect(controller.directory, '/root');
      expect(controller.locationError, isNull);
      expect(controller.workspaceChoiceRequired, isTrue);
      expect(store.locationFor('server')?.directory, '/work/acme');
      controller.dispose();
    },
  );
}
