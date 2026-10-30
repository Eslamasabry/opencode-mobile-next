import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/connection_sse_fixtures.dart';

/// A channel with a real retry timer of its own (vision 2026-10-20).
class _RetryingStream extends FakeEventStream implements RetryingEventChannel {
  _RetryingStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  final next = ValueNotifier<DateTime?>(null);

  @override
  ValueListenable<DateTime?> get nextAttempt => next;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('the link\'s own retry time reaches the snapshot while it is '
      'down, and leaves it when the link is up', (tester) async {
    final api = ControlledApi('laptop');
    final streams = <_RetryingStream>[];
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) => api,
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) {
            final stream = _RetryingStream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            );
            streams.add(stream);
            return stream;
          },
    );
    addTearDown(controller.dispose);
    final connecting = controller.connect(testProfile('laptop'));
    await tester.pump();
    api.healthResult.complete(Health(healthy: true));
    await connecting;
    final stream = streams.single;
    stream.emitStatus(StreamStatus.connected);
    expect(controller.connectionStatus.nextAttemptAt, isNull);

    var notified = 0;
    void count() => notified++;
    controller.addListener(count);
    stream.emitStatus(StreamStatus.reconnecting);
    final at = DateTime.now().add(const Duration(seconds: 8));
    notified = 0;
    stream.next.value = at;
    // A new retry time is news for the line that counts down to it.
    expect(notified, greaterThan(0));
    expect(controller.connectionStatus.nextAttemptAt, at);

    // Connected again: no try is due, whatever the channel still holds.
    stream.emitStatus(StreamStatus.connected);
    expect(controller.connectionStatus.nextAttemptAt, isNull);
    controller.removeListener(count);
    await controller.disconnect();
    await tester.pump(const Duration(seconds: 10));
  });

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
          'flavor': 'v2',
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
            'flavor': 'v2',
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

  testWidgets(
    'global events never substitute for the reconnecting scoped channel',
    (tester) async {
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

      // OC2 reconciles after reconnect; the global feed must not inject chat
      // events from an independently projected channel while scoped SSE is down.
      expect(controller.status, isNot(StreamStatus.connected));
      globalStreams.last.emit(part('/work/app/', 'live'));
      globalStreams.last.emit(part('/work/other', 'elsewhere'));
      await tester.pump();
      expect(seen, isEmpty);

      // Once the folder stream is up it is the only source, so nothing
      // arrives twice. (Set directly: the reconnect refreshes that a status
      // change starts are not what this test is about.)
      controller.status = StreamStatus.connected;
      scopedStreams.last.emit(part('/work/app', 'scoped'));
      globalStreams.last.emit(part('/work/app', 'twice'));
      await tester.pump();
      expect(seen.map((event) => event.properties['part']['text']), ['scoped']);

      unawaited(subscription.cancel());
      controller.dispose();
    },
  );
}
