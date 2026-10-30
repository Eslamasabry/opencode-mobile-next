// The Conversations list and the chat agree on whether an OpenCode 2 run is
// still going.
//
// Emulator, build 2561 (2026-10-20), "In-app Ubuntu" on OpenCode 2: a
// multi-step turn finished and the chat went idle, but its row on the
// Conversations list kept saying "Working" for minutes. The list adds the
// server's active-session read (`GET /api/session/active`) to the live run
// state. A read taken while the turn ran listed it, and OpenCode 2 sends no
// `session.updated` that would read again, so the stale "busy" outlived the
// run's end. A live run end now outranks any earlier read.
//
// The events below are the real v2 wire shapes of a live 2.0.28 turn
// (docs/opencode2-protocol-notes.md §3.4 and "Live on stable 2.0.28"), put
// through the real v2 adapter. The one `/api/event` stream feeds both the
// project channel and the server-wide channel.
import 'support/fake_server_operations.dart';
import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_event_channel.dart';
import 'support/fake_server_gateway.dart';

class _RealHttpOverrides extends HttpOverrides {}

const _here = '/work/app';
const _session = 'ses_turn';

class _Script {
  /// What `GET /api/session/active` answers now.
  Map<String, String> statuses = {};

  /// When set, the next status read waits for it.
  Completer<Map<String, String>>? heldStatuses;
  List<GlobalSessionResult> global = [];
}

class _Api extends FakeServerGateway {
  _Api(this.script) : super(baseUrl: 'http://127.0.0.1:1');
  final _Script script;
  final health_ = Completer<Health>();

  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;

  @override
  Future<Health> health() => health_.future;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async {
    final held = script.heldStatuses;
    if (held != null) {
      script.heldStatuses = null;
      return held.future;
    }
    return Map.of(script.statuses);
  }

  @override
  Future<ProvidersResponse> providers() async =>
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
  Future<List<FileNode>> listFiles(String path) async => const [];
}

class _Repo extends FakeServerOperations {
  _Repo(super.api, this.script);
  final _Script script;

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
  Future<WorkspaceProject?> loadCurrentProject() async => null;

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async => ServerPage(items: script.global);
}

/// The server's one event stream, as the app's two channels receive it.
class _Wire {
  void Function(EventEnvelope)? project;
  void Function(EventEnvelope)? server;
  final _projectAdapter = Api2EventAdapter();
  final _serverAdapter = Api2EventAdapter();

  /// One v2 event of [sessionID] in [directory]. The project channel sees it
  /// only when [directory] is the open project; the server-wide channel
  /// always does, stamped with its folder.
  void send(
    String type, [
    Map<String, dynamic> data = const {},
    String sessionID = _session,
    String directory = _here,
  ]) {
    final json = {
      'type': type,
      'location': {'directory': directory},
      'data': {'sessionID': sessionID, ...data},
    };
    if (directory == _here) {
      for (final event in adaptApi2EventJson(_projectAdapter, json)) {
        project!(event);
      }
    }
    for (final event in adaptApi2EventJson(_serverAdapter, json)) {
      server!(
        EventEnvelope(
          type: event.type,
          properties: event.properties,
          directory: directory,
        ),
      );
    }
  }

  void stepStarted(String step, {String sessionID = _session, String? dir}) =>
      send(
        'session.step.started',
        {
          'assistantMessageID': step,
          'agent': 'build',
          'model': {'id': 'minimax-m3.1-flash-free', 'providerID': 'opencode'},
        },
        sessionID,
        dir ?? _here,
      );

  void stepEnded(
    String step, {
    String finish = 'tool-calls',
    String sessionID = _session,
    String? dir,
  }) => send(
    'session.step.ended',
    {
      'assistantMessageID': step,
      'finish': finish,
      'cost': 0,
      'tokens': {
        'input': 10,
        'output': 5,
        'reasoning': 0,
        'cache': {'read': 0, 'write': 0},
      },
    },
    sessionID,
    dir ?? _here,
  );
}

class _Channel extends FakeEventChannel {
  _Channel({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  @override
  void start() => onStatus(StreamStatus.connected);

  @override
  Future<void> dispose() async {}
}

Future<(ConnectionController, _Wire)> _connect(
  WidgetTester tester,
  _Script script,
) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  final apis = <_Api>[];
  final wire = _Wire();
  final controller = ConnectionController(
    store,
    apiFactory: (_) {
      final api = _Api(script);
      apis.add(api);
      return api;
    },
    repositoryFactory: (api) => _Repo(api, script),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) {
          wire.project = onEvent;
          return _Channel(
            api: api,
            onEvent: onEvent,
            onStatus: onStatus,
            onError: onError,
          );
        },
    globalEventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) {
          wire.server = onEvent;
          return _Channel(
            api: api,
            onEvent: onEvent,
            onStatus: onStatus,
            onError: onError,
          );
        },
  );
  final connect = controller.connect(
    ServerProfile(id: 'server', name: 'server', baseUrl: 'http://127.0.0.1:1'),
  );
  await tester.pump();
  apis.first.health_.complete(Health(healthy: true, version: '2.0.28'));
  await connect;
  await tester.pump();
  // Let the connect-time feed read settle before the turn begins.
  await tester.pump(const Duration(seconds: 3));
  await controller.refreshChatFeed();
  return (controller, wire);
}

GlobalSessionResult _global(String id, String directory) => GlobalSessionResult(
  session: Session(
    id: id,
    title: id,
    directory: directory,
    time: SessionTime(created: 1, updated: 1),
  ),
  projectDirectory: directory,
);

ChatStatus _row(ConnectionController controller, [String id = _session]) =>
    chatFeedSourceOf(
      controller,
    ).chatFeed().items.firstWhere((item) => item.sessionID == id).status;

/// The person's prompt and the first two steps of a turn, with a feed read
/// in the middle (pull to refresh, or the read a permission or question
/// event schedules) while the server lists the run as active.
Future<void> _runTwoSteps(
  ConnectionController controller,
  _Wire wire,
  _Script script,
) async {
  wire.send('session.inbox.enqueued', {
    'inboxID': 'msg_u1',
    'item': {
      'id': 'msg_u1',
      'type': 'user',
      'payload': {'text': 'Refactor the settings screen'},
      'delivery': 'steer',
    },
  });
  script.statuses = {_session: 'busy'};
  wire.send('session.execution.started');
  wire.send('session.inbox.delivered', {'inboxID': 'msg_u1'});
  wire.stepStarted('msg_step1');
  await controller.refreshChatFeed();
  expect(_row(controller), ChatStatus.running);
  wire.stepEnded('msg_step1');
  // The gap between two steps: still the same run.
  expect(controller.busySessions, contains(_session));
  expect(_row(controller), ChatStatus.running);
  wire.stepStarted('msg_step2');
  wire.stepEnded('msg_step2');
  expect(_row(controller), ChatStatus.running);
}

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);

  testWidgets('a multi-step turn that ends leaves the list row idle, as the '
      'chat is', (tester) async {
    final script = _Script()..global = [_global(_session, _here)];
    final (controller, wire) = await _connect(tester, script);
    await _runTwoSteps(controller, wire, script);

    wire.stepStarted('msg_step3');
    wire.stepEnded('msg_step3', finish: 'stop');
    script.statuses = {};
    wire.send('session.execution.succeeded');
    await tester.pump();

    expect(controller.busySessions, isNot(contains(_session)));
    expect(_row(controller), isNot(ChatStatus.running));
    // Still so long after, with nothing else arriving.
    await tester.pump(const Duration(minutes: 1));
    expect(_row(controller), isNot(ChatStatus.running));
    controller.dispose();
  });

  testWidgets('a run the person stops leaves the list row idle', (
    tester,
  ) async {
    final script = _Script()..global = [_global(_session, _here)];
    final (controller, wire) = await _connect(tester, script);
    await _runTwoSteps(controller, wire, script);

    // Stop on 2.0.28: the step fails as aborted, then the execution is
    // interrupted by the user.
    wire.stepStarted('msg_step3');
    wire.send('session.step.failed', {
      'assistantMessageID': 'msg_step3',
      'error': {'type': 'aborted', 'message': 'Step interrupted'},
    });
    script.statuses = {};
    wire.send('session.execution.interrupted', {'reason': 'user'});
    await tester.pump();

    expect(controller.busySessions, isNot(contains(_session)));
    expect(_row(controller), isNot(ChatStatus.running));
    expect(_row(controller), isNot(ChatStatus.failed));
    controller.dispose();
  });

  testWidgets('a finished reply whose run goes on keeps the row working', (
    tester,
  ) async {
    final script = _Script()..global = [_global(_session, _here)];
    final (controller, wire) = await _connect(tester, script);
    await _runTwoSteps(controller, wire, script);

    // The step that reads like the last one finishes its message, but the
    // server keeps the run going into another step.
    wire.stepStarted('msg_step3');
    wire.stepEnded('msg_step3', finish: 'stop');
    expect(controller.busySessions, contains(_session));
    expect(_row(controller), ChatStatus.running);
    await controller.refreshChatFeed();
    expect(_row(controller), ChatStatus.running);
    wire.stepStarted('msg_step4');
    expect(_row(controller), ChatStatus.running);

    script.statuses = {};
    wire.stepEnded('msg_step4', finish: 'stop');
    wire.send('session.execution.succeeded');
    await tester.pump();
    expect(_row(controller), isNot(ChatStatus.running));
    controller.dispose();
  });

  testWidgets('a run in another project that ends leaves its row idle', (
    tester,
  ) async {
    const other = 'ses_other';
    const there = '/work/site';
    final script = _Script()..global = [_global(other, there)];
    final (controller, wire) = await _connect(tester, script);

    script.statuses = {other: 'busy'};
    wire.send('session.execution.started', const {}, other, there);
    wire.stepStarted('msg_o1', sessionID: other, dir: there);
    await controller.refreshChatFeed();
    expect(_row(controller, other), ChatStatus.running);

    wire.stepEnded('msg_o1', finish: 'stop', sessionID: other, dir: there);
    script.statuses = {};
    wire.send('session.execution.succeeded', const {}, other, there);
    await tester.pump();
    expect(_row(controller, other), isNot(ChatStatus.running));
    controller.dispose();
  });

  testWidgets('a status read in flight when the run ends cannot bring '
      '"Working" back', (tester) async {
    final script = _Script()..global = [_global(_session, _here)];
    final (controller, wire) = await _connect(tester, script);
    await _runTwoSteps(controller, wire, script);

    // The read leaves while the server still lists the run, and answers
    // only after the run has ended.
    final held = Completer<Map<String, String>>();
    script.heldStatuses = held;
    final read = controller.refreshChatFeed();
    await tester.pump();
    script.statuses = {};
    wire.send('session.execution.succeeded');
    held.complete({_session: 'busy'});
    await read;
    await tester.pump();

    expect(_row(controller), isNot(ChatStatus.running));
    controller.dispose();
  });
}
