import 'dart:async';
import 'dart:convert';
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

class _V2Api extends OpenCodeApi {
  _V2Api({
    this.healthy = true,
    this.permissions = const [],
    this.questions = const [],
    this.providersResult,
    this.agentsResult = const [],
  }) : super(baseUrl: 'http://127.0.0.1:1');

  final bool healthy;
  List<PermissionRequest> permissions;
  List<Map<String, dynamic>> questions;
  List<PermissionRequest>? legacyPermissions;
  Object? permissionV2Error;
  Object? questionV2Error;
  Object? answerQuestionError;
  Object? rejectQuestionError;
  List<Session> sessionsResult = const [];
  final ProvidersResponse? providersResult;
  final List<AgentInfo> agentsResult;
  Completer<List<Session>>? sessionsCompleter;
  Completer<Map<String, String>>? statusesCompleter;
  Completer<Health>? healthCompleter;
  int healthCalls = 0;
  bool closed = false;
  final List<(String, String, String)> permissionReplies = [];
  final List<(String, String, List<List<String>>)> questionReplies = [];
  final List<(String, String)> questionRejects = [];

  @override
  Future<Health> health() {
    healthCalls += 1;
    return healthCompleter?.future ??
        Future.value(Health(healthy: healthy, version: 'v2'));
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() =>
      legacyPermissions == null
      ? Future.error(ApiException('legacy unavailable', statusCode: 404))
      : Future.value(legacyPermissions);

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async {
    if (permissionV2Error case final error?) throw error;
    return permissions;
  }

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async {
    if (questionV2Error case final error?) throw error;
    return questions;
  }

  @override
  Future<List<Session>> sessions() =>
      sessionsCompleter?.future ?? Future.value(sessionsResult);

  @override
  Future<Map<String, String>> sessionStatuses() =>
      statusesCompleter?.future ?? Future.value(const {});

  @override
  Future<ProvidersResponse> providers() async =>
      providersResult ?? ProvidersResponse(providers: const []);

  @override
  Future<List<AgentInfo>> agents() async => agentsResult;

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply,
  ) async {
    permissionReplies.add((sessionID, requestID, reply));
  }

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    if (answerQuestionError case final error?) throw error;
    questionReplies.add((sessionID, requestID, answers));
  }

  @override
  Future<void> rejectQuestionV2(String sessionID, String requestID) async {
    if (rejectQuestionError case final error?) throw error;
    questionRejects.add((sessionID, requestID));
  }

  @override
  void close() {
    closed = true;
    super.close();
  }
}

class _QuestionRepository implements ProductRepository {
  _QuestionRepository({this.legacyUnavailable = true});

  final bool legacyUnavailable;
  List<PendingQuestion> questions = const [];
  Completer<List<PendingQuestion>>? questionsCompleter;
  Object? answerError;
  Object? rejectError;
  int listCalls = 0;
  final List<String> answers = [];
  final List<String> rejects = [];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<PendingQuestion>> listQuestions() {
    listCalls += 1;
    if (questionsCompleter != null) return questionsCompleter!.future;
    return legacyUnavailable
        ? Future.error(StateError('legacy unavailable'))
        : Future.value(questions);
  }

  @override
  Future<void> answerQuestion(String id, List<List<String>> values) async {
    if (answerError case final error?) throw error;
    answers.add(id);
  }

  @override
  Future<void> rejectQuestion(String id) async {
    if (rejectError case final error?) throw error;
    rejects.add(id);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeEventStream extends EventStream {
  _FakeEventStream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });

  bool disposed = false;

  @override
  void start() => onStatus(StreamStatus.connected);

  @override
  Future<void> dispose() async {
    disposed = true;
  }

  void emitStatus(StreamStatus status) => onStatus(status);
}

class _FailingProfileStore extends ProfileStore {
  _FailingProfileStore({required super.prefs});

  @override
  Future<void> setActiveId(String? id) =>
      Future.error(StateError('disk is read-only'));
}

Future<ProfileStore> _store([Map<String, Object> values = const {}]) async {
  SharedPreferences.setMockInitialValues(values);
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

PermissionRequest _permission(String id, String sessionID) => PermissionRequest(
  id: id,
  sessionID: sessionID,
  permission: 'bash',
  patterns: const ['git status'],
);

Map<String, dynamic> _question(String id, String sessionID) => {
  'id': id,
  'sessionID': sessionID,
  'questions': [
    {
      'header': 'Confirm',
      'question': 'Continue?',
      'multiple': false,
      'custom': true,
      'options': [
        {'label': 'Yes', 'description': 'Continue'},
      ],
    },
  ],
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('V2 HTTP hydration envelopes and session-scoped mutations', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests =
          <
            ({
              String method,
              String path,
              Map<String, String> query,
              Object? body,
            })
          >[];
      server.listen((request) async {
        final body = await utf8.decoder.bind(request).join();
        requests.add((
          method: request.method,
          path: request.uri.path,
          query: request.uri.queryParameters,
          body: body.isEmpty ? null : jsonDecode(body),
        ));
        request.response.headers.contentType = ContentType.json;
        switch (request.uri.path) {
          case '/api/permission/request':
            request.response.write(
              jsonEncode({
                'location': {'directory': '/work', 'workspace': 'workspace-1'},
                'data': [
                  {
                    'id': 'permission-1',
                    'sessionID': 'session-1',
                    'action': 'bash',
                    'resources': ['git status'],
                    'save': ['git *'],
                    'source': {'messageID': 'message-1', 'callID': 'call-1'},
                  },
                ],
              }),
            );
            break;
          case '/api/question/request':
            request.response.write(
              jsonEncode({
                'location': {'directory': '/work', 'workspace': 'workspace-1'},
                'data': [_question('question-1', 'session-1')],
              }),
            );
            break;
          default:
            request.response.write('true');
        }
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        )..setLocation(directory: '/work', workspace: 'workspace-1');
        final permissions = await api.pendingPermissionsV2();
        final questions = await api.pendingQuestionsV2();
        await api.respondPermissionV2('session-1', 'permission-1', 'always');
        await api.answerQuestionV2('session-1', 'question-1', const [
          ['Yes'],
        ]);
        await api.rejectQuestionV2('session-1', 'question-2');

        expect(permissions.single.permission, 'bash');
        expect(permissions.single.always, ['git *']);
        expect(permissions.single.tool?.callID, 'call-1');
        expect(questions.single['id'], 'question-1');
        expect(requests.map((request) => request.method), [
          'GET',
          'GET',
          'POST',
          'POST',
          'POST',
        ]);
        expect(requests.map((request) => request.path), [
          '/api/permission/request',
          '/api/question/request',
          '/api/session/session-1/permission/permission-1/reply',
          '/api/session/session-1/question/question-1/reply',
          '/api/session/session-1/question/question-2/reject',
        ]);
        expect(requests[2].body, {'reply': 'always'});
        expect(requests[0].query, {
          'location[directory]': '/work',
          'location[workspace]': 'workspace-1',
        });
        expect(requests[1].query, requests[0].query);
        expect(requests[2].query, isEmpty);
        expect(requests[3].body, {
          'answers': [
            ['Yes'],
          ],
        });
        expect(requests[4].body, isNull);
        api.close();
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('V2 hydration rejects mismatched and malformed envelopes', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/api/permission/request') {
          request.response.write(
            jsonEncode({
              'location': {'directory': '/other'},
              'data': <Object?>[],
            }),
          );
        } else {
          request.response.write(
            jsonEncode({
              'location': {'directory': '/work'},
              'data': {'not': 'a list'},
            }),
          );
        }
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        )..setLocation(directory: '/work');
        await expectLater(
          api.pendingPermissionsV2(),
          throwsA(
            isA<ApiException>().having(
              (error) => error.message,
              'message',
              contains('Mismatched'),
            ),
          ),
        );
        await expectLater(
          api.pendingQuestionsV2(),
          throwsA(
            isA<ApiException>().having(
              (error) => error.message,
              'message',
              contains('Malformed'),
            ),
          ),
        );
        api.close();
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test(
    'V2-only hydration routes mutations and blocks stale resurrection',
    () async {
      final api = _V2Api(
        permissions: [_permission('permission-1', 'session-1')],
        questions: [
          _question('question-1', 'session-1'),
          _question('question-2', 'session-2'),
        ],
      );
      final repository = _QuestionRepository();
      final controller = ConnectionController(await _store())
        ..api = api
        ..repository = repository;
      addTearDown(controller.dispose);

      await controller.refreshPendingPermissions();
      await controller.refreshPendingQuestions();
      await controller.answerPermission('permission-1', 'once');
      await controller.answerQuestion('question-1', const [
        ['Yes'],
      ]);
      await controller.rejectQuestion('question-2');

      expect(api.permissionReplies, [('session-1', 'permission-1', 'once')]);
      expect(api.questionReplies, hasLength(1));
      expect(api.questionReplies.single.$1, 'session-1');
      expect(api.questionReplies.single.$2, 'question-1');
      expect(api.questionReplies.single.$3, [
        ['Yes'],
      ]);
      expect(api.questionRejects, [('session-2', 'question-2')]);
      expect(repository.answers, isEmpty);
      expect(repository.rejects, isEmpty);
      expect(controller.permissions, isEmpty);
      expect(controller.questions, isEmpty);

      // A lagging global snapshot must not resurrect requests resolved locally.
      await controller.refreshPendingQuestions();
      expect(controller.questions, isEmpty);
    },
  );

  test('partial V2 hydration failure preserves V2 reply provenance', () async {
    final api = _V2Api()
      ..legacyPermissions = []
      ..permissionV2Error = ApiException('V2 permission list unavailable')
      ..questionV2Error = ApiException('V2 question list unavailable');
    final repository = _QuestionRepository(legacyUnavailable: false);
    final controller = ConnectionController(await _store())
      ..api = api
      ..repository = repository;
    addTearDown(controller.dispose);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.v2.asked',
        properties: const {
          'id': 'permission-1',
          'sessionID': 'session-1',
          'action': 'bash',
          'resources': ['git status'],
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'question.v2.asked',
        properties: _question('question-1', 'session-1'),
      ),
    );

    await controller.refreshPendingPermissions();
    await controller.refreshPendingQuestions();
    await controller.answerPermission('permission-1', 'once');
    await controller.answerQuestion('question-1', const [
      ['Yes'],
    ]);

    expect(api.permissionReplies, [('session-1', 'permission-1', 'once')]);
    expect(api.questionReplies.single.$1, 'session-1');
    expect(api.questionReplies.single.$2, 'question-1');
    expect(repository.answers, isEmpty);
  });

  test(
    'QuestionNotFoundError resolves V2 and legacy requests locally',
    () async {
      final api = _V2Api()
        ..answerQuestionError = ApiException(
          'Question not found',
          statusCode: 404,
          errorTag: 'QuestionNotFoundError',
          requestID: 'question-1',
        )
        ..rejectQuestionError = ApiException(
          'Question not found',
          statusCode: 404,
          errorTag: 'QuestionNotFoundError',
          requestID: 'question-2',
        );
      final repository = _QuestionRepository()
        ..answerError = ProductException(
          'Could not send the answer',
          cause: ApiException(
            'Question not found',
            statusCode: 404,
            errorTag: 'QuestionNotFoundError',
            requestID: 'question-3',
          ),
        );
      final controller = ConnectionController(await _store())
        ..api = api
        ..repository = repository;
      addTearDown(controller.dispose);
      for (final id in ['question-1', 'question-2']) {
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'question.v2.asked',
            properties: _question(id, 'session-1'),
          ),
        );
      }
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.asked',
          properties: _question('question-3', 'session-1'),
        ),
      );

      await expectLater(
        controller.answerQuestion('question-1', const [
          ['Yes'],
        ]),
        completes,
      );
      await expectLater(controller.rejectQuestion('question-2'), completes);
      await expectLater(
        controller.answerQuestion('question-3', const [
          ['Yes'],
        ]),
        completes,
      );
      expect(controller.questions, isEmpty);
    },
  );

  testWidgets('SSE reconnect refreshes pending questions', (tester) async {
    final streams = <_FakeEventStream>[];
    final api = _V2Api();
    final repository = _QuestionRepository(legacyUnavailable: false);
    final controller = ConnectionController(
      await _store(),
      apiFactory: (_) => api,
      repositoryFactory: (_) => repository,
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) {
            final stream = _FakeEventStream(
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
    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    final callsBeforeReconnect = repository.listCalls;
    final refreshRevisionBeforeReconnect = controller.dataRefreshRevision;

    streams.single.emitStatus(StreamStatus.reconnecting);
    streams.single.emitStatus(StreamStatus.connected);
    await tester.pump();

    expect(repository.listCalls, callsBeforeReconnect + 1);
    expect(controller.dataRefreshRevision, refreshRevisionBeforeReconnect + 1);
    controller.dispose();
  });

  testWidgets(
    'question hydration merges an independent concurrent SSE request',
    (tester) async {
      final api = _V2Api();
      final repository = _QuestionRepository(legacyUnavailable: false)
        ..questionsCompleter = Completer<List<PendingQuestion>>();
      final controller = ConnectionController(await _store())
        ..api = api
        ..repository = repository;
      addTearDown(controller.dispose);

      final refresh = controller.refreshPendingQuestions();
      await tester.pump();
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: _question('question-live', 'session-live'),
        ),
      );
      repository.questionsCompleter!.complete([
        PendingQuestion.fromJson(
          _question('question-snapshot', 'session-snapshot'),
        ),
      ]);
      await refresh;

      expect(
        controller.questions.keys,
        containsAll(['question-live', 'question-snapshot']),
      );
      await controller.answerQuestion('question-live', const [
        ['Yes'],
      ]);
      expect(api.questionReplies.single.$1, 'session-live');
    },
  );

  testWidgets('session snapshots merge around newer per-session events', (
    tester,
  ) async {
    final api = _V2Api()
      ..sessionsCompleter = Completer<List<Session>>()
      ..statusesCompleter = Completer<Map<String, String>>();
    final controller = ConnectionController(await _store())..api = api;
    addTearDown(controller.dispose);

    final refresh = controller.refreshSessions();
    api.sessionsCompleter!.complete([
      Session(id: 'session-1', title: 'stale'),
      Session(id: 'session-2', title: 'snapshot'),
    ]);
    await tester.pump();
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.updated',
        properties: const {
          'info': {'id': 'session-1', 'title': 'live'},
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.created',
        properties: const {
          'info': {'id': 'session-3', 'title': 'new'},
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: const {
          'sessionID': 'session-3',
          'status': {'type': 'busy'},
        },
      ),
    );
    api.statusesCompleter!.complete({'session-1': 'idle', 'session-2': 'busy'});
    await refresh;

    expect(controller.sessionsById['session-1']?.title, 'live');
    expect(controller.sessionsById['session-2']?.title, 'snapshot');
    expect(controller.sessionsById['session-3']?.title, 'new');
    expect(controller.busySessions, containsAll(['session-2', 'session-3']));
  });

  test('connected stream reconciles a missed idle event', () async {
    final api = _V2Api()..statusesCompleter = Completer<Map<String, String>>();
    final controller = ConnectionController(await _store())
      ..api = api
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: const {
          'sessionID': 'session-1',
          'status': {'type': 'busy'},
        },
      ),
    );
    expect(controller.busySessions, contains('session-1'));

    final reconciliation = controller.reconcileBusySessionsForTesting();
    api.statusesCompleter!.complete(const {});
    await reconciliation;

    expect(controller.busySessions, isNot(contains('session-1')));
  });

  testWidgets('catalog replaces stale saved model and agent', (tester) async {
    final store = await _store({
      'oc.model.server': 'removed-provider|removed-model',
      'oc.agent.server': 'removed-agent',
    });
    final api = _V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'provider-1',
            name: 'Provider',
            modelIDs: const ['model-1'],
          ),
        ],
        defaultProviderID: 'missing-default',
        defaultModelID: 'missing-model',
      ),
      agentsResult: [AgentInfo(name: 'build')],
    );
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (_) => _QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              _FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(controller.selectedModel?.providerID, 'provider-1');
    expect(controller.selectedModel?.modelID, 'model-1');
    expect(controller.selectedAgent, 'build');
    expect(store.modelFor('server'), ('provider-1', 'model-1'));
    expect(store.agentFor('server'), 'build');
    controller.dispose();
  });

  testWidgets('Termux profile keeps a valid model returned by OpenCode', (
    tester,
  ) async {
    final store = await _store({'oc.model.termux': 'opencode|big-pickle'});
    final api = _V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'opencode',
            name: 'OpenCode Zen',
            modelIDs: const ['nemotron-3.5-lightning-free', 'big-pickle'],
          ),
        ],
        defaultProviderID: 'opencode',
        defaultModelID: 'big-pickle',
      ),
    );
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (_) => _QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              _FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'termux',
        name: 'This device (Termux)',
        baseUrl: 'http://127.0.0.1:4096',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(controller.selectedModel?.providerID, 'opencode');
    expect(controller.selectedModel?.modelID, 'big-pickle');
    expect(store.modelFor('termux'), ('opencode', 'big-pickle'));
    expect(store.modelWasExplicitlySelected('termux'), isFalse);
    controller.dispose();
  });

  testWidgets('Termux profile keeps an explicit valid OpenCode model', (
    tester,
  ) async {
    final store = await _store({
      'oc.model.termux': 'opencode|big-pickle',
      'oc.modelExplicit.termux': true,
    });
    final api = _V2Api(
      providersResult: ProvidersResponse(
        providers: [
          ProviderInfo(
            id: 'opencode',
            name: 'OpenCode Zen',
            modelIDs: const ['nemotron-3.5-lightning-free', 'big-pickle'],
          ),
        ],
        defaultProviderID: 'opencode',
        defaultModelID: 'big-pickle',
      ),
    );
    final controller = ConnectionController(
      store,
      apiFactory: (_) => api,
      repositoryFactory: (_) => _QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              _FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);

    await controller.connect(
      ServerProfile(
        id: 'termux',
        name: 'This device (Termux)',
        baseUrl: 'http://127.0.0.1:4096',
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(controller.selectedModel?.modelID, 'big-pickle');
    expect(store.modelFor('termux'), ('opencode', 'big-pickle'));
    expect(store.modelWasExplicitlySelected('termux'), isTrue);
    controller.dispose();
  });

  testWidgets(
    'health false and active-profile persistence failure fail closed',
    (tester) async {
      final unhealthyApi = _V2Api(healthy: false);
      final unhealthyController = ConnectionController(
        await _store(),
        apiFactory: (_) => unhealthyApi,
        repositoryFactory: (_) => _QuestionRepository(),
      );
      addTearDown(unhealthyController.dispose);
      await unhealthyController.connect(
        ServerProfile(
          id: 'unhealthy',
          name: 'Unhealthy',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      expect(unhealthyController.api, isNull);
      expect(unhealthyController.status, StreamStatus.disconnected);
      expect(unhealthyController.lastError, contains('unhealthy'));
      expect(unhealthyApi.closed, isTrue);

      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final unwritableApi = _V2Api();
      final unwritableController = ConnectionController(
        _FailingProfileStore(prefs: prefs),
        apiFactory: (_) => unwritableApi,
        repositoryFactory: (_) => _QuestionRepository(),
      );
      addTearDown(unwritableController.dispose);
      await unwritableController.connect(
        ServerProfile(
          id: 'unwritable',
          name: 'Unwritable',
          baseUrl: 'http://127.0.0.1:1',
        ),
      );
      await tester.pump();
      expect(unwritableController.api, isNull);
      expect(unwritableController.status, StreamStatus.disconnected);
      expect(unwritableController.lastError, contains('Could not save'));
      expect(unwritableApi.healthCalls, 0);
      expect(unwritableApi.closed, isTrue);
    },
  );

  testWidgets('lifecycle suspend and concurrent resume recreate one location', (
    tester,
  ) async {
    final apis = <_V2Api>[];
    final streams = <_FakeEventStream>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (_) {
        final api = _V2Api();
        apis.add(api);
        return api;
      },
      repositoryFactory: (_) => _QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) {
            final stream = _FakeEventStream(
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
    final profile = ServerProfile(
      id: 'server',
      name: 'Server',
      baseUrl: 'http://127.0.0.1:1',
    );
    await controller.connect(profile);
    await tester.pump();
    await controller.selectLocation(
      directory: '/work/project',
      workspace: 'workspace-1',
    );
    expect(apis, hasLength(2));
    final refreshRevisionBeforeResume = controller.dataRefreshRevision;

    final suspendedApi = apis.last;
    final suspendedStream = streams.last;
    controller.suspendForLifecycle();
    expect(suspendedApi.closed, isTrue);
    expect(suspendedStream.disposed, isTrue);
    expect(controller.api, isNull);
    expect(controller.directory, '/work/project');
    expect(controller.workspace, 'workspace-1');

    final firstResume = controller.resumeFromLifecycle();
    final secondResume = controller.resumeFromLifecycle();
    expect(secondResume, same(firstResume));
    await Future.wait([firstResume, secondResume]);

    expect(apis, hasLength(3));
    expect(apis.last.directory, '/work/project');
    expect(apis.last.workspace, 'workspace-1');
    expect(apis.last.healthCalls, 1);
    expect(controller.status, StreamStatus.connected);
    expect(controller.dataRefreshRevision, refreshRevisionBeforeResume + 1);
    controller.dispose();
  });

  testWidgets('a second suspend invalidates an in-flight lifecycle resume', (
    tester,
  ) async {
    final delayedHealth = Completer<Health>();
    final apis = <_V2Api>[];
    final controller = ConnectionController(
      await _store(),
      apiFactory: (_) {
        final api = _V2Api();
        if (apis.length == 1) api.healthCompleter = delayedHealth;
        apis.add(api);
        return api;
      },
      repositoryFactory: (_) => _QuestionRepository(legacyUnavailable: false),
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) =>
              _FakeEventStream(
                api: api,
                onEvent: onEvent,
                onStatus: onStatus,
                onError: onError,
              ),
    );
    addTearDown(controller.dispose);
    await controller.connect(
      ServerProfile(
        id: 'server',
        name: 'Server',
        baseUrl: 'http://127.0.0.1:1',
      ),
    );
    controller.suspendForLifecycle();

    final staleResume = controller.resumeFromLifecycle();
    expect(apis, hasLength(2));
    controller.suspendForLifecycle();
    expect(apis[1].closed, isTrue);
    final currentResume = controller.resumeFromLifecycle();
    expect(apis, hasLength(3));

    delayedHealth.complete(Health(healthy: true, version: 'stale'));
    await Future.wait([staleResume, currentResume]);
    expect(controller.api, same(apis[2]));
    expect(controller.version, 'v2');
    expect(controller.status, StreamStatus.connected);
    controller.dispose();
  });
}
