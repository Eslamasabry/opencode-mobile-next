import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/connection_sse_fixtures.dart';

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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'healthy versionless transport remains ready through reconnect and retires on disconnect',
    (tester) async {
      final api = ControlledApi('versionless');
      final streams = <FakeEventStream>[];
      final controller = ConnectionController(
        await memoryProfileStore(),
        apiFactory: (_) => api,
        repositoryFactory: testRepositoryFactory,
        eventStreamFactory: streamFactory(streams),
      );
      addTearDown(controller.dispose);
      final connecting = controller.connect(testProfile('versionless'));
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
    final api = ControlledApi('laptop');
    final streams = <FakeEventStream>[];
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(streams),
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
      final api = ControlledApi('laptop');
      final streams = <FakeEventStream>[];
      final controller = ConnectionController(
        store,
        apiFactory: (_) => api,
        repositoryFactory: testRepositoryFactory,
        eventStreamFactory: streamFactory(streams),
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
      final store = await memoryProfileStore();
      final apis = <ControlledApi>[];
      final streams = <FakeEventStream>[];
      final controller = ConnectionController(
        store,
        apiFactory: (_) {
          final api = ControlledApi('server');
          apis.add(api);
          return api;
        },
        repositoryFactory: testRepositoryFactory,
        eventStreamFactory: streamFactory(streams),
      );
      final connect = controller.connect(testProfile('server'));
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
    final apis = <ControlledApi>[];
    final streams = <FakeEventStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi(profile.id);
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(streams),
    );

    final firstConnect = controller.connect(testProfile('first'));
    await tester.pump();
    final secondConnect = controller.connect(testProfile('second'));
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
    final apis = <ControlledApi>[];
    final streams = <FakeEventStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi(profile.id);
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(streams),
    );

    final firstConnect = controller.connect(testProfile('first'));
    await tester.pump();
    apis[0].healthResult.complete(Health(healthy: true, version: 'first'));
    await firstConnect;
    final oldStream = streams.single;

    final secondConnect = controller.connect(testProfile('second'));
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
    final api = ControlledApi('server');
    final scopedStreams = <FakeEventStream>[];
    final globalStreams = <FakeEventStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) => api,
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(scopedStreams),
      globalEventStreamFactory: streamFactory(globalStreams),
    );

    final connect = controller.connect(testProfile('server'));
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
    final apis = <ControlledApi>[];
    final scopedStreams = <FakeEventStream>[];
    final globalStreams = <FakeEventStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) {
        final api = ControlledApi('${profile.id}-${apis.length}');
        apis.add(api);
        return api;
      },
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory(scopedStreams),
      globalEventStreamFactory: streamFactory(globalStreams),
    );
    final connect = controller.connect(testProfile('server'));
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
}
