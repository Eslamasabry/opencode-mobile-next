import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
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
  _ControlledApi(this.label) : super(baseUrl: 'http://127.0.0.1:1');

  final String label;
  final healthResult = Completer<Health>();
  Completer<List<Session>>? sessionsResult;
  Object? sessionsFailure;
  int sessionsCalls = 0;
  bool closed = false;

  @override
  Future<Health> health() => healthResult.future;

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

  @override
  Future<List<AgentInfo>> agents() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

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
  Future<List<PendingQuestion>> listQuestions() async => const [];
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

  testWidgets('location replacement scopes and atomically restarts SSE', (
    tester,
  ) async {
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

  test('OpenCode API scopes SSE and permission replies to location', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <Uri>[];
      server.listen((request) async {
        requests.add(request.uri);
        if (request.uri.path == '/event') {
          request.response.headers.contentType = ContentType(
            'text',
            'event-stream',
          );
          request.response.write(
            'data: ${jsonEncode({'type': 'server.connected'})}\n\n',
          );
        } else if (request.uri.path == '/config/providers') {
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({'providers': <Object>[]}));
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
        await api.respondPermission('permission-1', 'once');
        await api.providers();
        await api.agents();

        expect(requests, hasLength(4));
        for (final uri in requests) {
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
