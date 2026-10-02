import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
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

  Completer<ProvidersResponse>? providersResult;
  int providersCalls = 0;

  @override
  Future<ProvidersResponse> providers() {
    providersCalls += 1;
    return providersResult?.future ??
        Future.value(ProvidersResponse(providers: const []));
  }

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

/// Integrations answer only when the test completes [integrations]; counts
/// catalog loads, so a test can see what waits for what.
class _SlowIntegrationsRepository extends _TestRepository {
  _SlowIntegrationsRepository(super.api, this.integrations, this.catalogLoads);

  final Completer<List<IntegrationInfo>>? integrations;
  final List<String> catalogLoads;

  @override
  Future<List<IntegrationInfo>> listIntegrations() =>
      integrations?.future ?? Future.value(const []);

  @override
  Future<CatalogSnapshot> loadCatalog() {
    catalogLoads.add('catalog');
    return super.loadCatalog();
  }
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

  testWidgets(
    'healthy versionless transport remains ready through reconnect and retires on disconnect',
    (tester) async {
      final api = _ControlledApi('versionless');
      final streams = <_FakeEventStream>[];
      final controller = ConnectionController(
        await _store(),
        apiFactory: (_) => api,
        repositoryFactory: _repositoryFactory,
        eventStreamFactory: _streamFactory(streams),
      );
      addTearDown(controller.dispose);
      final connecting = controller.connect(_profile('versionless'));
      await tester.pump();
      expect(controller.hasConnectedServer, isFalse);
      api.healthResult.complete(Health(healthy: true));
      await connecting;
      expect(controller.version, isNull);
      expect(controller.hasConnectedServer, isTrue);
      streams.single.emitStatus(StreamStatus.reconnecting);
      expect(controller.hasConnectedServer, isTrue);
      await controller.disconnect();
      expect(controller.hasConnectedServer, isFalse);
    },
  );

  testWidgets('a reconnect the stream made by itself is filed for While you '
      'were away; the first connect is not', (tester) async {
    AutomaticActivityController.resetShared();
    addTearDown(AutomaticActivityController.resetShared);
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secure, (_) async => null);
    addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([
        {
          'id': 'laptop',
          'name': 'Laptop',
          'baseUrl': 'http://127.0.0.1:1',
          'username': '',
        },
      ]),
      'oc.activeProfile': 'laptop',
    });
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await store.load();
    final api = _ControlledApi('laptop');
    final streams = <_FakeEventStream>[];
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(streams),
    );
    addTearDown(controller.dispose);
    final connecting = controller.connect(store.profiles.single);
    await tester.pump();
    api.healthResult.complete(Health(healthy: true));
    await connecting;
    streams.single.emitStatus(StreamStatus.connected);
    await tester.pump();
    expect(controller.automaticActsHere, isEmpty);

    streams.single.emitStatus(StreamStatus.reconnecting);
    expect(controller.automaticActsHere, isEmpty);
    streams.single.emitStatus(StreamStatus.connected);
    await tester.pump();
    final acts = controller.automaticActsHere;
    expect(acts, hasLength(1));
    expect(acts.single.kind, AutomaticActKind.reconnect);
    expect(acts.single.summary, 'Laptop');
    expect(store.prefs.getString('oc.automaticActivity.laptop'), isNotNull);
    await controller.disconnect();
  });

  testWidgets(
    'disabled reconnect retires transport and never records a recovery',
    (tester) async {
      AutomaticActivityController.resetShared();
      addTearDown(AutomaticActivityController.resetShared);
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(secure, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
      SharedPreferences.setMockInitialValues({
        'oc.profiles': jsonEncode([
          {
            'id': 'laptop',
            'name': 'Laptop',
            'baseUrl': 'http://127.0.0.1:1',
            'username': '',
          },
        ]),
        'oc.activeProfile': 'laptop',
      });
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      await store.load();
      final api = _ControlledApi('laptop');
      final streams = <_FakeEventStream>[];
      final controller = ConnectionController(
        store,
        apiFactory: (_) => api,
        repositoryFactory: _repositoryFactory,
        eventStreamFactory: _streamFactory(streams),
      );
      addTearDown(controller.dispose);
      final connecting = controller.connect(store.profiles.single);
      await tester.pump();
      api.healthResult.complete(Health(healthy: true));
      await connecting;
      streams.single.emitStatus(StreamStatus.connected);
      await tester.pump();
      expect(controller.automaticActsHere, isEmpty);

      await AutomationPolicyController.forProfile(
        store.prefs,
        'laptop',
      ).setBehavior(AutomationBehavior.reconnect, false);
      streams.single.emitStatus(StreamStatus.reconnecting);
      expect(streams.single.disposed, isTrue);
      expect(api.closed, isTrue);
      expect(controller.status, StreamStatus.disconnected);
      streams.single.emitStatus(StreamStatus.connected);
      await tester.pump();
      expect(controller.automaticActsHere, isEmpty);
      controller.suspendForLifecycle();
      await controller.resumeFromLifecycle();
      expect(api.healthCalls, 1);
      await controller.disconnect();
    },
  );

  testWidgets(
    'policy change during lifecycle health prevents channel restart',
    (tester) async {
      final store = await _store();
      final apis = <_ControlledApi>[];
      final streams = <_FakeEventStream>[];
      final controller = ConnectionController(
        store,
        apiFactory: (_) {
          final api = _ControlledApi('server');
          apis.add(api);
          return api;
        },
        repositoryFactory: _repositoryFactory,
        eventStreamFactory: _streamFactory(streams),
      );
      final connect = controller.connect(_profile('server'));
      await tester.pump();
      apis.single.healthResult.complete(Health(healthy: true));
      await connect;
      streams.single.emitStatus(StreamStatus.connected);
      controller.suspendForLifecycle();
      final resume = controller.resumeFromLifecycle();
      await tester.pump();
      expect(apis, hasLength(2));
      await AutomationPolicyController.forProfile(
        store.prefs,
        'server',
      ).setBehavior(AutomationBehavior.reconnect, false);
      apis.last.healthResult.complete(Health(healthy: true));
      await resume;
      expect(streams, hasLength(1));
      expect(controller.status, StreamStatus.disconnected);
      expect(controller.automaticActsHere, isEmpty);
      controller.dispose();
    },
  );

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

    scopedStreams.single.emitStatus(StreamStatus.connected);
    globalStreams.single.emitStatus(StreamStatus.reconnecting);
    await AutomationPolicyController.forProfile(
      controller.store.prefs,
      'server',
    ).setBehavior(AutomationBehavior.reconnect, false);
    expect(globalStreams.single.disposed, isTrue);
    expect(scopedStreams.single.disposed, isFalse);
    controller.dispose();
    expect(scopedStreams.single.disposed, isTrue);
    expect(globalStreams.single.disposed, isTrue);
  });

  testWidgets('retry status can veto EventStream before another request', (
    tester,
  ) async {
    final api = _FailingStreamApi();
    late EventStream stream;
    stream = EventStream(
      api: api,
      onEvent: (_) {},
      onStatus: (status) {
        if (status == StreamStatus.reconnecting) unawaited(stream.dispose());
      },
    );
    stream.start();
    await tester.pump();
    await tester.pump(const Duration(seconds: 20));
    expect(api.calls, 1);
    api.close();
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

  testWidgets('a stream that goes silent after a heartbeat is replaced', (
    tester,
  ) async {
    // OpenCode 1 writes a heartbeat every 10 s. A connection that stops
    // writing without closing used to read as live forever, and the chat
    // showed a running reply only once it had finished.
    final bodies = <StreamController<Uint8List>>[];
    final api = _StreamApi((_) {
      final body = StreamController<Uint8List>();
      bodies.add(body);
      return body.stream;
    });
    final statuses = <StreamStatus>[];
    final stream = EventStream(
      api: api,
      onEvent: (_) {},
      onStatus: statuses.add,
    );

    stream.start();
    await tester.pump();
    expect(statuses.last, StreamStatus.connected);
    // Silence before any heartbeat proves nothing: older servers send none.
    await tester.pump(const Duration(seconds: 40));
    expect(api.calls, 1);

    bodies.single.add(
      Uint8List.fromList(
        utf8.encode(
          'data: ${jsonEncode({'type': 'server.heartbeat', 'properties': {}})}'
          '\n\n',
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 30));
    expect(api.calls, 1, reason: 'still inside the silence limit');

    await tester.pump(const Duration(seconds: 5, milliseconds: 100));
    expect(statuses.last, StreamStatus.reconnecting);
    await tester.pump(const Duration(seconds: 2));
    expect(api.calls, 2);
    expect(statuses.last, StreamStatus.connected);

    // A cancel settles on a later frame under the test clock.
    unawaited(stream.dispose());
    for (final body in bodies) {
      unawaited(body.close());
    }
    // Let the retired connection's backoff-reset timer run out.
    await tester.pump(const Duration(seconds: 31));
    api.close();
  });

  testWidgets('while the folder stream is down, the server-wide stream '
      'carries that folder’s events live', (tester) async {
    final apis = <_ControlledApi>[];
    final scopedStreams = <_FakeEventStream>[];
    final globalStreams = <_FakeEventStream>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: _repositoryFactory,
      eventStreamFactory: _streamFactory(scopedStreams),
      globalEventStreamFactory: _streamFactory(globalStreams),
    );
    final connect = controller.connect(_profile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    unawaited(controller.selectLocation(directory: '/work/app'));
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }
    apis.last.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(controller.directory, '/work/app');

    final seen = <EventEnvelope>[];
    final subscription = controller.events.listen(seen.add);
    EventEnvelope part(String directory, String text) => EventEnvelope(
      type: 'message.part.updated',
      directory: directory,
      properties: {
        'sessionID': 'ses_1',
        'part': {
          'id': 'prt_1',
          'messageID': 'msg_1',
          'sessionID': 'ses_1',
          'type': 'text',
          'text': text,
        },
      },
    );

    // The folder stream has not connected: this folder's words arrive
    // through the server-wide stream; another folder's never do.
    expect(controller.status, isNot(StreamStatus.connected));
    globalStreams.last.emit(part('/work/app/', 'live'));
    globalStreams.last.emit(part('/work/other', 'elsewhere'));
    await tester.pump();
    expect(seen.map((event) => event.properties['part']['text']), ['live']);

    // Once the folder stream is up it is the only source, so nothing
    // arrives twice. (Set directly: the reconnect refreshes that a status
    // change starts are not what this test is about.)
    controller.status = StreamStatus.connected;
    globalStreams.last.emit(part('/work/app', 'twice'));
    await tester.pump();
    expect(seen, hasLength(1));

    unawaited(subscription.cancel());
    controller.dispose();
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

  testWidgets('empty project catalog preserves the selected location', (
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

    expect(controller.directory, '/deleted/worktree');
    expect(controller.workspace, isNull);
    expect(controller.locationNotice, contains('Your selection was kept'));
    expect(store.locationFor('server')?.directory, '/deleted/worktree');
    controller.dispose();
  });

  testWidgets('missing workspace retains the selected remote scope', (
    tester,
  ) async {
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
    expect(controller.workspace, 'deleted-workspace');
    expect(
      controller.locationNotice,
      contains('Couldn’t verify this workspace'),
    );
    expect(store.locationFor('server')?.directory, '/work/acme');
    expect(store.locationFor('server')?.workspace, 'deleted-workspace');
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

  // Issue #87: a phone-hosted server felt 20 seconds slow to every screen.
  testWidgets('a slow Termux wake lock does not hold up the health check', (
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
    // Termux answers RUN_COMMAND slowly, or only at its 10-second timeout.
    final wakeLock = Completer<void>();
    var wakeLockCalls = 0;
    final controller = ConnectionController(
      store,
      backgroundLive: backgroundLive,
      localWakeLockEnsurer: () {
        wakeLockCalls += 1;
        return wakeLock.future;
      },
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
    expect(wakeLockCalls, 1);
    expect(apis.single.healthCalls, 1);
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    expect(controller.version, '1');

    // Waking the app asks for the lock again but acts without it.
    controller.suspendForLifecycle();
    final action = controller.prepareActionTransport();
    await tester.pump();
    expect(wakeLockCalls, 2);
    expect(apis.single.healthCalls, 2);
    expect(await action, same(apis.single));
    controller.dispose();
  });

  // The built-in Linux server runs inside this app, not in Termux: asking
  // Termux for a wake lock would launch Termux for nothing.
  testWidgets('the built-in Linux server never asks Termux for a wake lock', (
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
      eventStreamFactory: _streamFactory([]),
    );

    final connect = controller.connect(
      ServerProfile(
        id: 'builtin',
        name: 'builtin',
        baseUrl: BuiltinLinux.serverUrl,
      ),
    );
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    controller.suspendForLifecycle();
    final action = controller.prepareActionTransport();
    await tester.pump();
    expect(await action, same(apis.single));
    expect(wakeLockCalls, 0);
    controller.dispose();
  });

  testWidgets('opening another folder does not wait for the catalog', (
    tester,
  ) async {
    final apis = <_ControlledApi>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        // After the first connect, the catalog never answers: a phone-hosted
        // OpenCode 1 takes seconds to build its 6 MB provider list.
        if (apis.isNotEmpty) api.providersResult = Completer();
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
    await tester.pump();
    final shownBefore = controller.providers;

    var opened = false;
    unawaited(
      controller
          .selectLocation(directory: '/work/other')
          .then((_) => opened = true),
    );
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }
    final folderApi = apis.last;
    folderApi.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }

    // Open with the catalog still loading, and the server's models kept on
    // screen meanwhile instead of an empty picker.
    expect(opened, isTrue);
    expect(controller.locationLoading, isFalse);
    expect(controller.directory, '/work/other');
    expect(controller.providers, same(shownBefore));
    controller.dispose();
  });

  testWidgets('the first connect does not wait for the one-time provider '
      'runtime refresh; the catalog loads after it', (tester) async {
    // Android 15 run, 2026-09-24: 7.5 s of the 12.2 s "Start OpenCode" setup
    // step was this refresh, before any conversation could load.
    final apis = <_ControlledApi>[];
    final integrations = Completer<List<IntegrationInfo>>();
    final catalogLoads = <String>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) =>
          _SlowIntegrationsRepository(api, integrations, catalogLoads),
      eventStreamFactory: _streamFactory([]),
    );
    var connected = false;
    unawaited(
      controller.connect(_profile('server')).then((_) => connected = true),
    );
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(connected, isTrue);
    expect(apis.single.sessionsCalls, greaterThan(0));
    expect(catalogLoads, isEmpty);

    integrations.complete(const []);
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(catalogLoads, isNotEmpty);
    controller.dispose();
  });

  testWidgets('a new folder opens before the one-time provider runtime '
      'refresh, and its catalog loads after it', (tester) async {
    // Android 15 run, 2026-09-24: creating a project took 7.6 s, 6.8 s of it
    // this refresh (/api/integration, then /provider) that only the model
    // list needs.
    final apis = <_ControlledApi>[];
    final integrations = Completer<List<IntegrationInfo>>();
    final catalogLoads = <String>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (profile) {
        final api = _ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: (api) => _SlowIntegrationsRepository(
        api,
        apis.length > 1 ? integrations : null,
        catalogLoads,
      ),
      eventStreamFactory: _streamFactory([]),
    );
    final connect = controller.connect(_profile('server'));
    await tester.pump();
    apis.single.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump();

    var opened = false;
    unawaited(
      controller
          .selectLocation(directory: '/work/new-project')
          .then((_) => opened = true),
    );
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }
    apis.last.healthResult.complete(Health(healthy: true, version: '1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(opened, isTrue);
    expect(controller.locationLoading, isFalse);
    expect(controller.directory, '/work/new-project');
    final before = catalogLoads.length;

    integrations.complete(const []);
    for (var i = 0; i < 10; i++) {
      await tester.pump();
    }
    expect(catalogLoads.length, greaterThan(before));
    controller.dispose();
  });

  testWidgets('an action after a wake does not wait for the catalog reload', (
    tester,
  ) async {
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
    await tester.pump();
    // A catalog younger than ten minutes is not reloaded on a wake at all;
    // this one is stale.
    await tester.pump(const Duration(minutes: 11));

    controller.suspendForLifecycle();
    final action = controller.prepareActionTransport();
    final woken = apis.last;
    expect(woken, isNot(same(apis.first)));
    // OpenCode 1's /provider is megabytes a phone-hosted server takes
    // seconds to build; the sessions list is cheap.
    woken
      ..sessionsResult = Completer<List<Session>>()
      ..providersResult = Completer<ProvidersResponse>();
    Object? actionApi;
    unawaited(action.then((value) => actionApi = value));
    woken.healthResult.complete(Health(healthy: true, version: '1'));
    await tester.pump();

    expect(actionApi, same(woken));
    // The catalog waits for the cheap reloads so the single-threaded server
    // does not make them queue behind it.
    expect(woken.sessionsCalls, 1);
    expect(woken.providersCalls, 0);
    woken.sessionsResult!.complete([Session(id: 'session-1')]);
    await tester.pump();
    expect(controller.sessionsById, contains('session-1'));
    expect(woken.providersCalls, 1);
    woken.providersResult!.complete(ProvidersResponse(providers: const []));
    await tester.pump();
    expect(controller.catalogLoading, isFalse);
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
