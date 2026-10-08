import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'support/connection_sse_fixtures.dart';

class _LocationRepository extends TestRepository {
  _LocationRepository(
    super.api, {
    required this.projectsByDirectory,
    this.workspaces = const [],
    this.projects = const [],
  });

  final Map<String, WorkspaceProject?> projectsByDirectory;
  final List<WorkspaceInfo> workspaces;

  /// The server's project list; an empty list means "no projects at all".
  final List<WorkspaceProject> projects;
  String? selectedDirectory;
  String? selectedWorkspace;

  @override
  void setLocation({String? directory, String? workspace}) {
    selectedDirectory = directory;
    selectedWorkspace = workspace;
    super.setLocation(directory: directory, workspace: workspace);
  }

  @override
  Future<WorkspaceProject?> loadCurrentProject() async =>
      projectsByDirectory[selectedDirectory];

  @override
  Future<List<WorkspaceProject>> listProjects() async => projects;

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => workspaces;
}

class _DestinationCalls {
  String? movedDirectory;
  bool? movedChanges;
  String? warpedWorkspaceID;
  bool? copiedChanges;
  ConsoleOrganization? organization;
  final List<String> reminders = [];
}

class _DestinationTestRepository extends TestRepository {
  _DestinationTestRepository(super.api, this.calls);

  final _DestinationCalls calls;

  @override
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) async {
    calls.movedDirectory = directory;
    calls.movedChanges = moveChanges;
  }

  @override
  Future<void> warpSession(
    String sessionID, {
    required String? workspaceID,
    required bool copyChanges,
  }) async {
    calls.warpedWorkspaceID = workspaceID;
    calls.copiedChanges = copyChanges;
  }

  @override
  Future<void> switchConsoleOrganization(
    ConsoleOrganization organization,
  ) async {
    calls.organization = organization;
  }

  @override
  Future<void> addSessionLocationReminder(
    String sessionID,
    String directory,
  ) async {
    calls.reminders.add(directory);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('location replacement scopes and atomically restarts SSE', (
    tester,
  ) async {
    final apis = <ControlledApi>[];
    final streams = <FakeEventStream>[];
    final store = await memoryProfileStore();
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(streams),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    final oldStream = streams.single;
    final oldApi = apis.single;

    final selection = controller.selectLocation(
      directory: '/work/acme',
      workspace: 'workspace-1',
    );

    expect(oldStream.disposed, isTrue);
    expect(oldApi.closed, isTrue);
    expect(apis.last.directory, '/work/acme');
    expect(apis.last.workspace, 'workspace-1');
    expect(streams, hasLength(2));
    expect(controller.locationLoading, isTrue);
    await selection;
    expect(controller.locationLoading, isFalse);
    expect(store.locationFor('server')?.directory, '/work/acme');
    expect(store.locationFor('server')?.workspace, 'workspace-1');
    controller.dispose();
  });

  testWidgets('cold connect restores one verified per-server location', (
    tester,
  ) async {
    final store = await memoryProfileStore();
    await store.setLocation(
      'server',
      directory: '/work/acme',
      workspace: 'workspace-1',
    );
    final apis = <ControlledApi>[];
    final repositories = <_LocationRepository>[];
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) {
        final repository = _LocationRepository(
          api,
          projectsByDirectory: {
            '/work/acme': const WorkspaceProject(
              id: 'project-1',
              name: 'Acme',
              directory: '/work/acme',
              worktrees: [],
              updatedAt: 1,
            ),
          },
          workspaces: const [
            WorkspaceInfo(
              id: 'workspace-1',
              projectID: 'project-1',
              name: 'Phone',
              type: 'remote',
              directory: '/work/acme',
            ),
          ],
        );
        repositories.add(repository);
        return repository;
      },
      eventStreamFactory: streamFactory([]),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.first.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    expect(apis, hasLength(2));
    expect(apis.last.directory, '/work/acme');
    expect(apis.last.workspace, 'workspace-1');
    expect(controller.directory, '/work/acme');
    expect(controller.workspace, 'workspace-1');
    expect(controller.locationNotice, isNull);
    expect(repositories.first.selectedDirectory, isNull);
    expect(repositories.first.selectedWorkspace, isNull);
    controller.dispose();
  });

  testWidgets('server switching restores only that profile location', (
    tester,
  ) async {
    final store = await memoryProfileStore();
    await store.setLocation('first', directory: '/work/first');
    await store.setLocation('second', directory: '/work/second');
    final apis = <ControlledApi>[];
    final projects = {
      '/work/first': const WorkspaceProject(
        id: 'project-first',
        name: 'First',
        directory: '/work/first',
        worktrees: [],
        updatedAt: 1,
      ),
      '/work/second': const WorkspaceProject(
        id: 'project-second',
        name: 'Second',
        directory: '/work/second',
        worktrees: [],
        updatedAt: 1,
      ),
    };
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) =>
          _LocationRepository(api, projectsByDirectory: projects),
      eventStreamFactory: streamFactory([]),
    );

    final firstConnect = controller.connect(testProfile('first'));
    await tester.pump();
    apis[0].healthResult.complete(Health(healthy: true, version: '1'));
    await firstConnect;
    expect(controller.directory, '/work/first');

    final secondConnect = controller.connect(testProfile('second'));
    await tester.pump();
    apis[2].healthResult.complete(Health(healthy: true, version: '1'));
    await secondConnect;

    expect(apis, hasLength(4));
    expect(controller.directory, '/work/second');
    expect(store.locationFor('first')?.directory, '/work/first');
    expect(store.locationFor('second')?.directory, '/work/second');
    controller.dispose();
  });

  testWidgets('empty project catalog preserves the selected location', (
    tester,
  ) async {
    final store = await memoryProfileStore();
    await store.setLocation('server', directory: '/deleted/worktree');
    final api = ControlledApi('server');
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (api) => _LocationRepository(
        api,
        projectsByDirectory: const {'/deleted/worktree': null},
        projects: const [],
      ),
      eventStreamFactory: streamFactory([]),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    api.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    expect(controller.directory, '/deleted/worktree');
    expect(controller.workspace, isNull);
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/deleted/worktree');
    controller.dispose();
  });

  testWidgets('missing workspace retains the selected remote scope', (
    tester,
  ) async {
    final store = await memoryProfileStore();
    await store.setLocation(
      'server',
      directory: '/work/acme',
      workspace: 'deleted-workspace',
    );
    final apis = <ControlledApi>[];
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) => _LocationRepository(
        api,
        projectsByDirectory: {
          '/work/acme': const WorkspaceProject(
            id: 'project-1',
            name: 'Acme',
            directory: '/work/acme',
            worktrees: [],
            updatedAt: 1,
          ),
        },
      ),
      eventStreamFactory: streamFactory([]),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.first.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    expect(controller.directory, '/work/acme');
    expect(controller.workspace, 'deleted-workspace');
    expect(controller.locationNotice, isNull);
    expect(store.locationFor('server')?.directory, '/work/acme');
    expect(store.locationFor('server')?.workspace, 'deleted-workspace');
    controller.dispose();
  });

  testWidgets(
    'manual reconnect is coalesced and preserves the selected location',
    (tester) async {
      final apis = <ControlledApi>[];
      final streams = <FakeEventStream>[];
      final controller = ConnectionController(
        await memoryProfileStore(),
        apiFactory: (profile) {
          final api = ControlledApi('${profile.id}-${apis.length}');
          apis.add(api);
          return api;
        },
        repositoryFactory: testRepositoryFactory,
        eventStreamFactory: streamFactory(streams),
      );

      final connect = controller.connect(testProfile('server'));
      await tester.pump();
      apis.single.healthResult.complete(Health(healthy: true, version: '1'));
      await connect;
      await controller.selectLocation(
        directory: '/work/acme',
        workspace: 'workspace-1',
      );
      controller.sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'Retained chat',
      );

      final firstRetry = controller.retryConnection();
      final secondRetry = controller.retryConnection();
      await tester.pump();

      expect(secondRetry, same(firstRetry));
      expect(apis, hasLength(3));
      expect(apis.last.directory, '/work/acme');
      expect(apis.last.workspace, 'workspace-1');
      expect(controller.directory, '/work/acme');
      expect(controller.workspace, 'workspace-1');
      expect(controller.sessionsById, contains('session-1'));
      expect(controller.manualReconnectInProgress, isTrue);

      apis.last.healthResult.complete(Health(healthy: true, version: '2'));
      await firstRetry;
      await tester.pump();

      expect(controller.api, same(apis.last));
      expect(controller.version, '2');
      expect(controller.directory, '/work/acme');
      expect(controller.workspace, 'workspace-1');
      expect(controller.manualReconnectInProgress, isFalse);
      controller.dispose();
    },
  );

  testWidgets(
    'failed manual reconnect retains stale data and can retry again',
    (tester) async {
      final apis = <ControlledApi>[];
      final controller = ConnectionController(
        await memoryProfileStore(),
        apiFactory: (profile) {
          final api = ControlledApi('${profile.id}-${apis.length}');
          apis.add(api);
          return api;
        },
        repositoryFactory: testRepositoryFactory,
        eventStreamFactory: streamFactory([]),
      );

      final connect = controller.connect(testProfile('server'));
      await tester.pump();
      apis.single.healthResult.complete(Health(healthy: true, version: '1'));
      await connect;
      await controller.selectLocation(
        directory: '/work/acme',
        workspace: 'workspace-1',
      );
      controller.sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'Retained chat',
      );

      final failedApiIndex = apis.length;
      final failedRetry = controller.retryConnection();
      // The health check is already in flight; it fails on the wire.
      apis[failedApiIndex].healthResult.completeError(
        ApiException('server unavailable'),
      );
      await tester.pump();
      await failedRetry;
      await tester.pump();

      expect(controller.status, StreamStatus.disconnected);
      expect(controller.api, isNull);
      expect(controller.connectionError, contains('server unavailable'));
      expect(controller.directory, '/work/acme');
      expect(controller.workspace, 'workspace-1');
      expect(controller.sessionsById, contains('session-1'));

      final successfulRetry = controller.retryConnection();
      await tester.pump();
      expect(apis.last.directory, '/work/acme');
      expect(apis.last.workspace, 'workspace-1');
      apis.last.healthResult.complete(Health(healthy: true, version: '2'));
      await successfulRetry;
      expect(controller.api, same(apis.last));
      expect(controller.version, '2');
      controller.dispose();
    },
  );

  testWidgets('move, warp, and org rebuild the authoritative transport', (
    tester,
  ) async {
    final apis = <ControlledApi>[];
    final streams = <FakeEventStream>[];
    final repositories = <_DestinationTestRepository>[];
    final calls = _DestinationCalls();
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) {
        final repository = _DestinationTestRepository(api, calls);
        repositories.add(repository);
        return repository;
      },
      eventStreamFactory: streamFactory(streams),
    );

    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;

    await controller.moveSessionToDirectory(
      'session-1',
      directory: '/work/copy',
      moveChanges: true,
    );
    expect(calls.movedDirectory, '/work/copy');
    expect(calls.movedChanges, isTrue);
    expect(controller.directory, '/work/copy');
    expect(controller.workspace, isNull);
    expect(repositories, hasLength(2));
    expect(calls.reminders, ['/work/copy']);

    await controller.warpSessionToWorkspace(
      'session-1',
      directory: '/remote/review',
      workspaceID: 'workspace-2',
      copyChanges: false,
    );
    expect(calls.warpedWorkspaceID, 'workspace-2');
    expect(calls.copiedChanges, isFalse);
    expect(controller.directory, '/remote/review');
    expect(controller.workspace, 'workspace-2');
    expect(repositories, hasLength(3));
    expect(calls.reminders, ['/work/copy', '/remote/review']);

    const organization = ConsoleOrganization(
      accountID: 'account-1',
      accountEmail: 'dev@example.com',
      accountUrl: 'https://console.example.com',
      orgID: 'org-2',
      orgName: 'Review org',
      active: false,
    );
    final switching = controller.switchConsoleOrganization(organization);
    await tester.pump();
    expect(apis, hasLength(4));
    apis.last.healthResult.complete(Health(healthy: true, version: '2'));
    await switching;

    expect(calls.organization, organization);
    expect(controller.version, '2');
    expect(controller.directory, '/remote/review');
    expect(controller.workspace, 'workspace-2');
    expect(repositories, hasLength(4));
    expect(streams, hasLength(4));
    controller.dispose();
  });
}
