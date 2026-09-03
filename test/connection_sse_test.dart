import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttpOverrides extends HttpOverrides {}

class _ControlledApi extends OpenCodeApi {
  _ControlledApi(this.label) : super(baseUrl: 'http://127.0.0.1:1');

  final String label;
  final healthResult = Completer<Health>();
  Completer<List<Session>>? sessionsResult;
  Object? sessionsFailure;
  int sessionsCalls = 0;
  int healthCalls = 0;
  Object? healthFailure;
  bool closed = false;

  @override
  Future<Health> health() {
    healthCalls += 1;
    final failure = healthFailure;
    if (failure != null) return Future.error(failure);
    return healthResult.future;
  }

  @override
  Future<List<Session>> sessions() {
    sessionsCalls += 1;
    final failure = sessionsFailure;
    if (failure != null) return Future.error(failure);
    return sessionsResult?.future ?? Future.value(const []);
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);

  // The v1 catalog load also reads the runtime view; answer it locally so
  // the test never reaches the network.
  @override
  Future<ProvidersResponse> configuredProviders() async =>
      ProvidersResponse(providers: const []);

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

  @override
  void close() {
    closed = true;
    super.close();
  }
}

class _FailingStreamApi extends OpenCodeApi {
  _FailingStreamApi() : super(baseUrl: 'http://127.0.0.1:1');

  int calls = 0;

  @override
  Future<Response<ResponseBody>> openEventStream({CancelToken? cancelToken}) {
    calls += 1;
    return Future.error(ApiException('stream unavailable'));
  }
}

class _StreamApi extends OpenCodeApi {
  _StreamApi(this.createStream) : super(baseUrl: 'http://127.0.0.1:1');

  final Stream<Uint8List> Function(int call) createStream;
  int calls = 0;

  @override
  Future<Response<ResponseBody>> openEventStream({CancelToken? cancelToken}) {
    calls += 1;
    return Future.value(
      Response(
        requestOptions: RequestOptions(path: '/event'),
        statusCode: 200,
        data: ResponseBody(createStream(calls), 200),
      ),
    );
  }

  @override
  Future<Response<ResponseBody>> openGlobalEventStream({
    CancelToken? cancelToken,
  }) {
    calls += 1;
    return Future.value(
      Response(
        requestOptions: RequestOptions(path: '/global/event'),
        statusCode: 200,
        data: ResponseBody(createStream(calls), 200),
      ),
    );
  }
}

class _FakeEventStream extends EventStream {
  _FakeEventStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  bool started = false;
  bool disposed = false;

  @override
  void start() {
    started = true;
    onStatus(StreamStatus.connecting);
  }

  @override
  Future<void> dispose() async {
    disposed = true;
  }

  void emitStatus(StreamStatus value) => onStatus(value);
  void emit(EventEnvelope value) => onEvent(value);
}

class _TestRepository extends SdkProductRepository {
  _TestRepository(OpenCodeApi api) : super(api.sdkClient);

  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
}

class _LocationRepository extends _TestRepository {
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

class _DestinationTestRepository extends _TestRepository {
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

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

ServerProfile _profile(String id) =>
    ServerProfile(id: id, name: id, baseUrl: 'http://127.0.0.1:1');

EventStreamFactory _streamFactory(List<_FakeEventStream> streams) {
  return ({required api, required onEvent, required onStatus, onError}) {
    final stream = _FakeEventStream(
      api: api,
      onEvent: onEvent,
      onStatus: onStatus,
      onError: onError,
    );
    streams.add(stream);
    return stream;
  };
}

ProductRepositoryFactory get _repositoryFactory =>
    (api) => _TestRepository(api);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('latest overlapping connect owns all commits and transport', (
    tester,
  ) async {
    final apis = <_ControlledApi>[];
    final streams = <_FakeEventStream>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi(profile.id);
        apis.add(api);
        return api;
      },
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(streams),
    );

    final firstConnect = controller.connect(_profile('first'));
    await tester.pump();
    final secondConnect = controller.connect(_profile('second'));
    await tester.pump();

    expect(apis[0].closed, isTrue);
    apis[1].healthResult.complete(Health(healthy: true, version: 'second'));
    await secondConnect;
    apis[0].healthResult.complete(Health(healthy: true, version: 'first'));
    await firstConnect;
    await tester.pump();

    expect(controller.api, same(apis[1]));
    expect(controller.version, 'second');
    expect(controller.store.activeId, 'second');
    expect(streams, hasLength(1));
    controller.dispose();
  });

  testWidgets('replacement stream ignores obsolete status and events', (
    tester,
  ) async {
    final apis = <_ControlledApi>[];
    final streams = <_FakeEventStream>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi(profile.id);
        apis.add(api);
        return api;
      },
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(streams),
    );

    final firstConnect = controller.connect(_profile('first'));
    await tester.pump();
    apis[0].healthResult.complete(Health(healthy: true, version: 'first'));
    await firstConnect;
    final oldStream = streams.single;

    final secondConnect = controller.connect(_profile('second'));
    expect(oldStream.disposed, isTrue);
    oldStream.emitStatus(StreamStatus.connected);
    oldStream.emit(
      EventEnvelope(
        type: 'session.created',
        properties: const {
          'info': {'id': 'old-session'},
        },
      ),
    );
    expect(controller.status, StreamStatus.connecting);
    expect(controller.sessionsById, isEmpty);

    await tester.pump();
    apis[1].healthResult.complete(Health(healthy: true, version: 'second'));
    await secondConnect;
    expect(streams, hasLength(2));
    controller.dispose();
  });

  testWidgets('global installation stream is isolated from chat state', (
    tester,
  ) async {
    final api = _ControlledApi('server');
    final scopedStreams = <_FakeEventStream>[];
    final globalStreams = <_FakeEventStream>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (_) => api,
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(scopedStreams),
      globalEventStreamFactory: _streamFactory(globalStreams),
    );

    final connect = controller.connect(_profile('server'));
    await tester.pump();
    api.healthResult.complete(Health(healthy: true, version: '1.18.23'));
    await connect;

    expect(scopedStreams, hasLength(1));
    expect(globalStreams, hasLength(1));
    scopedStreams.single.emitStatus(StreamStatus.connected);
    globalStreams.single.emitStatus(StreamStatus.disconnected);
    expect(controller.status, StreamStatus.connected);
    final worktreeEvent = controller.events.firstWhere(
      (event) => event.type == 'worktree.ready',
    );

    globalStreams.single.emit(
      EventEnvelope(
        type: 'session.created',
        properties: const {
          'info': {'id': 'wrong-global-session'},
        },
      ),
    );
    expect(controller.sessionsById, isEmpty);

    globalStreams.single.emit(
      EventEnvelope(
        type: 'installation.update-available',
        properties: const {'version': '1.19.0'},
      ),
    );
    expect(controller.availableServerVersion, '1.19.0');

    globalStreams.single.emit(
      EventEnvelope(
        type: 'worktree.ready',
        directory: '/data/worktree/project-1/mobile-review',
        project: 'project-1',
        properties: const {
          'name': 'mobile-review',
          'branch': 'opencode/mobile-review',
        },
      ),
    );
    final forwarded = await worktreeEvent;
    expect(forwarded.directory, '/data/worktree/project-1/mobile-review');

    controller.dispose();
    expect(scopedStreams.single.disposed, isTrue);
    expect(globalStreams.single.disposed, isTrue);
  });

  testWidgets('disposing EventStream cancels retry and suppresses callbacks', (
    tester,
  ) async {
    final api = _FailingStreamApi();
    final statuses = <StreamStatus>[];
    final stream = EventStream(
      api: api,
      onEvent: (_) {},
      onStatus: statuses.add,
    );

    stream.start();
    await tester.pump();
    expect(statuses, [StreamStatus.connecting, StreamStatus.reconnecting]);
    await stream.dispose();
    final statusCount = statuses.length;
    await tester.pump(const Duration(seconds: 2));

    expect(api.calls, 1);
    expect(statuses, hasLength(statusCount));
    api.close();
  });

  testWidgets('global event stream unwraps installation payloads', (
    tester,
  ) async {
    final payload = utf8.encode(
      'data: ${jsonEncode({
        'directory': '/work/app',
        'project': 'project-1',
        'workspace': 'workspace-1',
        'payload': {
          'type': 'installation.update-available',
          'properties': {'version': '1.19.0'},
        },
      })}\n\n',
    );
    final api = _StreamApi((_) => Stream.value(Uint8List.fromList(payload)));
    final events = <EventEnvelope>[];
    final stream = EventStream(
      api: api,
      global: true,
      onEvent: events.add,
      onStatus: (_) {},
    );

    stream.start();
    await tester.pump();
    await tester.pump();

    expect(events, hasLength(1));
    expect(events.single.type, 'installation.update-available');
    expect(events.single.properties['version'], '1.19.0');
    expect(events.single.directory, '/work/app');
    expect(events.single.project, 'project-1');
    expect(events.single.workspace, 'workspace-1');
    await stream.dispose();
    api.close();
  });

  testWidgets('immediate HTTP 200 closes retain exponential retry backoff', (
    tester,
  ) async {
    final api = _StreamApi((_) => const Stream<Uint8List>.empty());
    final stream = EventStream(api: api, onEvent: (_) {}, onStatus: (_) {});

    stream.start();
    await tester.pump();
    await tester.pump();
    await tester.pump();
    expect(api.calls, 1);

    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    expect(api.calls, 2);
    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    expect(api.calls, 2);
    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    expect(api.calls, 3);
    await tester.pump(const Duration(seconds: 3));
    await tester.pump();
    expect(api.calls, 3);
    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    expect(api.calls, 4);

    await stream.dispose();
    api.close();
  });

  testWidgets('stream error is reported once with no uncaught zone error', (
    tester,
  ) async {
    final api = _StreamApi(
      (_) => Stream<Uint8List>.multi((source) {
        source.addError(StateError('stream failed'));
        source.close();
      }),
    );
    final reportedErrors = <Object>[];
    final uncaughtErrors = <Object>[];
    late EventStream stream;

    await runZonedGuarded(() async {
      stream = EventStream(
        api: api,
        onEvent: (_) {},
        onStatus: (_) {},
        onError: reportedErrors.add,
      );
      stream.start();
      await tester.pump();
      await tester.pump();
      await tester.pump();
      await stream.dispose();
    }, (error, _) => uncaughtErrors.add(error));

    expect(reportedErrors, hasLength(1));
    expect(uncaughtErrors, isEmpty);
    api.close();
  });

  testWidgets('oversized unterminated data is discarded and parser recovers', (
    tester,
  ) async {
    final controller = StreamController<Uint8List>();
    final api = _StreamApi((_) => controller.stream);
    final events = <EventEnvelope>[];
    final stream = EventStream(api: api, onEvent: events.add, onStatus: (_) {});
    stream.start();
    await tester.pump();

    controller.add(
      Uint8List.fromList(utf8.encode('data: ${'x' * (9 * 1024 * 1024)}')),
    );
    await tester.pump();
    expect(events, isEmpty);

    controller.add(
      Uint8List.fromList(
        utf8.encode('\ndata: {"type":"server.recovered"}\n\n'),
      ),
    );
    await tester.pump();
    expect(events.single.type, 'server.recovered');

    await controller.close();
    await tester.pump();
    await stream.dispose();
    api.close();
  });

  testWidgets('valid event larger than 64 KiB is delivered intact', (
    tester,
  ) async {
    final controller = StreamController<Uint8List>();
    final api = _StreamApi((_) => controller.stream);
    final events = <EventEnvelope>[];
    final stream = EventStream(api: api, onEvent: events.add, onStatus: (_) {});
    stream.start();
    await tester.pump();

    final text = 'large-output-' * (128 * 1024 ~/ 13);
    controller.add(
      Uint8List.fromList(
        utf8.encode(
          'data: ${jsonEncode({
            'type': 'message.part.updated',
            'properties': {'text': text},
          })}\n\n',
        ),
      ),
    );
    await tester.pump();

    expect(events, hasLength(1));
    expect(events.single.type, 'message.part.updated');
    expect(events.single.properties['text'], text);

    await controller.close();
    await tester.pump();
    await stream.dispose();
    api.close();
  });

  testWidgets('stream error cancels a source that does not close', (
    tester,
  ) async {
    var cancellations = 0;
    final controller = StreamController<Uint8List>(
      sync: true,
      onCancel: () => cancellations += 1,
    );
    final api = _StreamApi((_) => controller.stream);
    final reportedErrors = <Object>[];
    final stream = EventStream(
      api: api,
      onEvent: (_) {},
      onStatus: (_) {},
      onError: reportedErrors.add,
    );
    stream.start();
    await tester.pump();

    controller.addError(StateError('source remains open'));
    await tester.pump();
    await tester.pump();

    expect(reportedErrors, hasLength(1));
    expect(cancellations, 1);

    await stream.dispose();
    // Intentionally leave the synthetic source open: the regression is that
    // EventStream must cancel it rather than relying on the source to close.
    api.close();
  });

  testWidgets('chunk-split UTF-8 SSE event is decoded without corruption', (
    tester,
  ) async {
    final controller = StreamController<Uint8List>();
    final api = _StreamApi((_) => controller.stream);
    final events = <EventEnvelope>[];
    final stream = EventStream(api: api, onEvent: events.add, onStatus: (_) {});
    stream.start();
    await tester.pump();

    final bytes = utf8.encode(
      ': keepalive\r\ndata: ${jsonEncode({
        'type': 'message.é',
        'properties': {'text': '你好'},
      })}\r\n\r\n',
    );
    for (final byte in bytes) {
      controller.add(Uint8List.fromList([byte]));
    }
    await tester.pump();

    expect(events, hasLength(1));
    expect(events.single.type, 'message.é');
    expect(events.single.properties['text'], '你好');

    await controller.close();
    await tester.pump();
    await stream.dispose();
    api.close();
  });

  testWidgets('location replacement scopes and atomically restarts SSE', (
    tester,
  ) async {
    final apis = <_ControlledApi>[];
    final streams = <_FakeEventStream>[];
    final store = await _store();
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(streams),
    );

    final connect = controller.connect(_profile('server'));
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
    final store = await _store();
    await store.setLocation(
      'server',
      directory: '/work/acme',
      workspace: 'workspace-1',
    );
    final apis = <_ControlledApi>[];
    final repositories = <_LocationRepository>[];
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
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
      eventStreamFactory: _streamFactory([]),
    );

    final connect = controller.connect(_profile('server'));
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
    final store = await _store();
    await store.setLocation('first', directory: '/work/first');
    await store.setLocation('second', directory: '/work/second');
    final apis = <_ControlledApi>[];
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
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) =>
          _LocationRepository(api, projectsByDirectory: projects),
      eventStreamFactory: _streamFactory([]),
    );

    final firstConnect = controller.connect(_profile('first'));
    await tester.pump();
    apis[0].healthResult.complete(Health(healthy: true, version: '1'));
    await firstConnect;
    expect(controller.directory, '/work/first');

    final secondConnect = controller.connect(_profile('second'));
    await tester.pump();
    apis[2].healthResult.complete(Health(healthy: true, version: '1'));
    await secondConnect;

    expect(apis, hasLength(4));
    expect(controller.directory, '/work/second');
    expect(store.locationFor('first')?.directory, '/work/first');
    expect(store.locationFor('second')?.directory, '/work/second');
    controller.dispose();
  });

  testWidgets('confirmed stale project falls back and clears saved location', (
    tester,
  ) async {
    final store = await _store();
    await store.setLocation('server', directory: '/deleted/worktree');
    final api = _ControlledApi('server');
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (api) => _LocationRepository(
        api,
        projectsByDirectory: const {'/deleted/worktree': null},
        projects: const [],
      ),
      eventStreamFactory: _streamFactory([]),
    );

    final connect = controller.connect(_profile('server'));
    await tester.pump();
    api.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    expect(controller.directory, isNull);
    expect(controller.workspace, isNull);
    expect(controller.locationNotice, contains('no longer available'));
    expect(store.locationFor('server'), isNull);
    controller.dispose();
  });

  testWidgets('missing workspace restores its project locally', (tester) async {
    final store = await _store();
    await store.setLocation(
      'server',
      directory: '/work/acme',
      workspace: 'deleted-workspace',
    );
    final apis = <_ControlledApi>[];
    final controller = ConnectionController(
      store,
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
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
      eventStreamFactory: _streamFactory([]),
    );

    final connect = controller.connect(_profile('server'));
    await tester.pump();
    apis.first.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    expect(controller.directory, '/work/acme');
    expect(controller.workspace, isNull);
    expect(controller.locationNotice, contains('opened locally'));
    expect(store.locationFor('server')?.directory, '/work/acme');
    expect(store.locationFor('server')?.workspace, isNull);
    controller.dispose();
  });

  testWidgets(
    'manual reconnect is coalesced and preserves the selected location',
    (tester) async {
      final apis = <_ControlledApi>[];
      final streams = <_FakeEventStream>[];
      final controller = ConnectionController(
        await _store(),
        apiFactory: (profile) {
          final api = _ControlledApi('${profile.id}-${apis.length}');
          apis.add(api);
          return api;
        },
        repositoryFactory: _repositoryFactory,
        eventStreamFactory: _streamFactory(streams),
      );

      final connect = controller.connect(_profile('server'));
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
      final apis = <_ControlledApi>[];
      final controller = ConnectionController(
        await _store(),
        apiFactory: (profile) {
          final api = _ControlledApi('${profile.id}-${apis.length}');
          apis.add(api);
          return api;
        },
        repositoryFactory: _repositoryFactory,
        eventStreamFactory: _streamFactory([]),
      );

      final connect = controller.connect(_profile('server'));
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
      apis[failedApiIndex].healthFailure = ApiException('server unavailable');
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
    final apis = <_ControlledApi>[];
    final streams = <_FakeEventStream>[];
    final repositories = <_DestinationTestRepository>[];
    final calls = _DestinationCalls();
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) {
        final repository = _DestinationTestRepository(api, calls);
        repositories.add(repository);
        return repository;
      },
      eventStreamFactory: _streamFactory(streams),
    );

    final connect = controller.connect(_profile('server'));
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

  testWidgets('v2 requests and PTY lifecycle update reducer signals', (
    tester,
  ) async {
    final controller = ConnectionController(await _store());

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.asked',
        properties: const {
          'id': 'permission-1',
          'sessionID': 'session-1',
          'action': 'bash',
          'resources': ['git status'],
          'save': ['git *'],
          'metadata': {'cwd': '/work'},
          'source': {
            'type': 'tool',
            'messageID': 'message-1',
            'callID': 'call-1',
          },
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.asked',
        properties: const {
          'id': 'question-1',
          'sessionID': 'session-1',
          'questions': [
            {
              'header': 'Choice',
              'question': 'Continue?',
              'multiple': false,
              'custom': true,
              'options': [
                {'label': 'Yes', 'description': 'Continue'},
              ],
            },
          ],
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.created',
        properties: const {
          'sessionID': 'session-1',
          'info': {'id': 'session-1', 'title': 'New'},
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'pty.exited',
        properties: const {'id': 'pty-1', 'exitCode': 0},
      ),
    );

    expect(controller.permissions['permission-1']?.permission, 'bash');
    expect(controller.permissions['permission-1']?.patterns, ['git status']);
    expect(controller.permissions['permission-1']?.tool?.callID, 'call-1');
    expect(controller.questions, contains('question-1'));
    expect(controller.sessionsById, contains('session-1'));
    expect(controller.ptyRevision, 1);
    expect(controller.lastPtyEvent?.type, 'pty.exited');

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.replied',
        properties: const {
          'sessionID': 'session-1',
          'requestID': 'permission-1',
          'reply': 'once',
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.rejected',
        properties: const {'sessionID': 'session-1', 'requestID': 'question-1'},
      ),
    );
    expect(controller.permissions, isEmpty);
    expect(controller.questions, isEmpty);
    controller.dispose();
  });

  test(
    'installation events retain exact available and installed versions',
    () async {
      final controller = ConnectionController(await _store())
        ..version = '1.18.23';

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.update-available',
          properties: const {'version': 'latest'},
        ),
      );
      expect(controller.availableServerVersion, isNull);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.update-available',
          properties: const {'version': '1.19.0'},
        ),
      );
      expect(controller.availableServerVersion, '1.19.0');

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.updated',
          properties: const {'version': '1.19.0'},
        ),
      );
      expect(controller.availableServerVersion, isNull);
      expect(controller.installedServerVersion, '1.19.0');
      expect(controller.version, '1.18.23');

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'server.connected',
          properties: const {'version': '1.19.0'},
        ),
      );
      expect(controller.version, '1.19.0');
      expect(controller.installedServerVersion, isNull);
      controller.dispose();
    },
  );

  testWidgets('polling runs only while SSE is unavailable', (tester) async {
    final api = _ControlledApi('poll');
    final controller = ConnectionController(await _store())..api = api;
    controller.enablePollingFallback();

    expect(controller.pollingFallbackEnabled, isTrue);
    controller.status = StreamStatus.connected;
    await tester.pump(const Duration(seconds: 5));
    expect(api.sessionsCalls, 0);

    controller.status = StreamStatus.reconnecting;
    await tester.pump(const Duration(seconds: 5));
    await tester.pump();
    expect(api.sessionsCalls, 1);
    controller.dispose();
  });

  testWidgets('failed new-location refresh cannot publish old sessions', (
    tester,
  ) async {
    final apis = <_ControlledApi>[];
    final streams = <_FakeEventStream>[];
    final oldSessions = Completer<List<Session>>();
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        if (apis.isEmpty) {
          api.sessionsResult = oldSessions;
        } else {
          api.sessionsFailure = ApiException('new location unavailable');
        }
        apis.add(api);
        return api;
      },
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(streams),
    );

    final connect = controller.connect(_profile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();
    expect(apis.single.sessionsCalls, 1);

    final selection = controller.selectLocation(directory: '/new');
    oldSessions.complete([Session(id: 'old-session')]);
    await selection;
    await tester.pump();

    expect(controller.sessionsById, isEmpty);
    expect(controller.sessionsError, contains('new location unavailable'));
    expect(controller.locationError, isNotNull);
    controller.dispose();
  });

  testWidgets(
    'keep-live wake reconciliation is shared with foreground actions',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final backgroundLive = BackgroundLiveController(
        preferences: store.prefs,
        invoke: (method, [arguments]) async => const {
          'enabled': true,
          'active': true,
          'notificationGranted': true,
          'batteryOptimizationIgnored': false,
        },
      );
      final apis = <_ControlledApi>[];
      final streams = <_FakeEventStream>[];
      var wakeLockCalls = 0;
      final controller = ConnectionController(
        store,
        backgroundLive: backgroundLive,
        localWakeLockEnsurer: () async => wakeLockCalls += 1,
        apiFactory: (profile) {
          final api = _ControlledApi(profile.id);
          apis.add(api);
          return api;
        },
        repositoryFactory: _repositoryFactory,
        eventStreamFactory: _streamFactory(streams),
      );

      final connect = controller.connect(_profile('server'));
      await tester.pump();
      apis.single.healthResult.complete(Health(healthy: true, version: '1'));
      await connect;
      await tester.pump();
      expect(wakeLockCalls, 1);
      final api = apis.single;
      final healthCallsBeforeWake = api.healthCalls;

      controller.suspendForLifecycle();
      expect(controller.lifecycleSuspended, isFalse);

      final resume = controller.resumeFromLifecycle();
      final actionApi = controller.prepareActionTransport();
      await resume;
      expect(await actionApi, same(api));
      expect(api.healthCalls, healthCallsBeforeWake + 1);
      expect(wakeLockCalls, 2);

      expect(await controller.prepareActionTransport(), same(api));
      expect(api.healthCalls, healthCallsBeforeWake + 1);
      controller.dispose();
    },
  );

  testWidgets('stale keep-live transport is rebuilt before an action', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final backgroundLive = BackgroundLiveController(
      preferences: store.prefs,
      invoke: (method, [arguments]) async => const {
        'enabled': true,
        'active': true,
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      },
    );
    final apis = <_ControlledApi>[];
    final streams = <_FakeEventStream>[];
    var wakeLockCalls = 0;
    final controller = ConnectionController(
      store,
      backgroundLive: backgroundLive,
      localWakeLockEnsurer: () async => wakeLockCalls += 1,
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(streams),
    );

    final connect = controller.connect(_profile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    final staleApi = apis.single
      ..healthFailure = ApiException('stale transport');
    controller.suspendForLifecycle();
    final actionApi = controller.prepareActionTransport();
    await tester.pump();

    expect(apis, hasLength(2));
    expect(staleApi.closed, isTrue);
    apis.last.healthResult.complete(Health(healthy: true, version: '2'));
    expect(await actionApi, same(apis.last));
    expect(controller.version, '2');
    expect(wakeLockCalls, 3);
    controller.dispose();
  });

  testWidgets('keep-live wake lock is limited to loopback profiles', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final backgroundLive = BackgroundLiveController(
      preferences: store.prefs,
      invoke: (method, [arguments]) async => const {
        'enabled': true,
        'active': true,
        'notificationGranted': true,
        'batteryOptimizationIgnored': false,
      },
    );
    final api = _ControlledApi('remote');
    var wakeLockCalls = 0;
    final controller = ConnectionController(
      store,
      backgroundLive: backgroundLive,
      localWakeLockEnsurer: () async => wakeLockCalls += 1,
      apiFactory: (_) => api,
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory([]),
    );
    final profile = ServerProfile(
      id: 'remote',
      name: 'remote',
      baseUrl: 'https://opencode.example.test',
    );

    final connect = controller.connect(profile);
    await tester.pump();
    api.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;

    controller.suspendForLifecycle();
    await controller.resumeFromLifecycle();
    expect(wakeLockCalls, 0);
    controller.dispose();
  });

  test('OpenCode API scopes SSE and permission replies to location', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <Uri>[];
      server.listen((request) async {
        requests.add(request.uri);
        if (request.uri.path == '/event' ||
            request.uri.path == '/global/event') {
          request.response.headers.contentType = ContentType(
            'text',
            'event-stream',
          );
          request.response.write(
            'data: ${jsonEncode({'type': 'server.connected'})}\n\n',
          );
        } else if (request.uri.path == '/provider') {
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              'all': <Object>[],
              'connected': <Object>[],
              'default': <String, Object>{},
            }),
          );
        } else if (request.uri.path == '/agent') {
          request.response.headers.contentType = ContentType.json;
          request.response.write('[]');
        } else {
          request.response.headers.contentType = ContentType.json;
          request.response.write('true');
        }
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
      try {
        final response = await api.openEventStream();
        await response.data!.stream.drain<void>();
        final globalResponse = await api.openGlobalEventStream();
        await globalResponse.data!.stream.drain<void>();
        await api.respondPermission('permission-1', 'once');
        await api.providers();
        await api.agents();

        expect(requests, hasLength(5));
        final globalEvent = requests.singleWhere(
          (uri) => uri.path == '/global/event',
        );
        expect(globalEvent.queryParameters, isEmpty);
        for (final uri in requests.where(
          (uri) => uri.path != '/global/event',
        )) {
          expect(uri.queryParameters['directory'], '/work/acme');
          expect(uri.queryParameters['workspace'], 'workspace-1');
        }
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
