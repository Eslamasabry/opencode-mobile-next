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
import 'support/connection_v2_fixtures.dart';

class _RealHttpOverrides extends HttpOverrides {}

class _DelayedRequestController extends ConnectionController {
  _DelayedRequestController(
    super.store, {
    required this.ready,
    required this.replacementApi,
    required this.replacementRepository,
  });

  final Completer<void> ready;
  final OpenCodeApi replacementApi;
  final ProductRepository replacementRepository;

  @override
  Future<OpenCodeApi?> prepareActionTransport() async {
    await ready.future;
    api = replacementApi;
    repository = replacementRepository;
    return replacementApi;
  }
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

  for (final replacement in ['scope', 'contents', 'resolved then reused']) {
    test('request replies cannot cross $replacement while waking', () async {
      final api = V2Api();
      final ready = Completer<void>();
      final controller = _DelayedRequestController(
        await memoryProfileStore(),
        ready: ready,
        replacementApi: api,
        replacementRepository: QuestionRepository(),
      );
      addTearDown(controller.dispose);
      void ask({String session = 'session-1'}) {
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'permission.v2.asked',
            properties: {
              'id': 'permission-1',
              'sessionID': session,
              'action': 'bash',
              'resources': ['git status'],
            },
          ),
        );
        for (final id in ['question-1', 'question-2']) {
          controller.handleEventForTesting(
            EventEnvelope(
              type: 'question.v2.asked',
              properties: _question(id, session),
            ),
          );
        }
      }

      ask();
      final operations = [
        controller.answerPermission('permission-1', 'once'),
        controller.answerQuestion('question-1', [
          ['Yes'],
        ]),
        controller.rejectQuestion('question-2'),
      ];
      if (replacement == 'scope') {
        controller.locationRevision++;
        ask();
      } else if (replacement == 'contents') {
        ask(session: 'session-2');
      } else {
        for (final id in ['permission-1', 'question-1', 'question-2']) {
          controller.handleEventForTesting(
            EventEnvelope(
              type: id.startsWith('permission')
                  ? 'permission.v2.replied'
                  : 'question.v2.replied',
              properties: {'requestID': id},
            ),
          );
        }
        ask();
      }
      ready.complete();
      await Future.wait(operations);
      expect(api.permissionReplies, isEmpty);
      expect(api.questionReplies, isEmpty);
      expect(api.questionRejects, isEmpty);
      expect(controller.permissions, contains('permission-1'));
      expect(controller.questions, hasLength(2));
    });
  }

  test(
    'question answer and reject share a write slot before transport wake',
    () async {
      final api = V2Api();
      final ready = Completer<void>();
      final controller = _DelayedRequestController(
        await memoryProfileStore(),
        ready: ready,
        replacementApi: api,
        replacementRepository: QuestionRepository(),
      );
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: _question('question-1', 'session-1'),
        ),
      );
      final answer = controller.answerQuestion('question-1', [
        ['Yes'],
      ]);
      final reject = controller.rejectQuestion('question-1');
      ready.complete();
      await Future.wait([answer, reject]);
      expect(api.questionReplies, hasLength(1));
      expect(api.questionRejects, isEmpty);
    },
  );

  test(
    'late question failure after remote resolution does not surface an error',
    () async {
      final write = Completer<void>();
      final api = V2Api()
        ..questionWrite = write
        ..answerQuestionError = ApiException(
          'Temporary outage',
          statusCode: 503,
        );
      final controller = ConnectionController(await memoryProfileStore())
        ..api = api
        ..repository = QuestionRepository();
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: _question('question-1', 'session-1'),
        ),
      );
      final answer = controller.answerQuestion('question-1', [
        ['Yes'],
      ]);
      await Future<void>.delayed(Duration.zero);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.replied',
          properties: {'requestID': 'question-1'},
        ),
      );
      write.complete();
      await answer;
      expect(controller.questions, isEmpty);
    },
  );

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
                'location': {
                  'directory': '/work',
                  'workspaceID': 'workspace-1',
                  'project': {'id': 'project-1', 'directory': '/work'},
                },
                'data': [
                  {
                    'id': 'permission-1',
                    'sessionID': 'session-1',
                    'action': 'bash',
                    'resources': ['git status'],
                    'save': ['git *'],
                    'source': {
                      'type': 'tool',
                      'messageID': 'message-1',
                      'callID': 'call-1',
                    },
                  },
                ],
              }),
            );
            break;
          case '/api/question/request':
            request.response.write(
              jsonEncode({
                'location': {
                  'directory': '/work',
                  'workspaceID': 'workspace-1',
                  'project': {'id': 'project-1', 'directory': '/work'},
                },
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

  test('loose successful V2 request envelopes remain compatible', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/api/permission/request') {
          request.response.write(
            jsonEncode({
              'location': {'directory': '/work', 'workspace': 'workspace-1'},
              'data': [
                {
                  'id': 'permission-loose',
                  'sessionID': 'session-1',
                  'action': 'edit',
                  'resources': ['lib/main.dart'],
                  'source': {'messageID': 'message-1', 'callID': 'call-1'},
                },
              ],
            }),
          );
        } else {
          request.response.write(
            jsonEncode({
              'location': {'directory': '/work', 'workspace': 'workspace-1'},
              'data': [_question('question-loose', 'session-1')],
            }),
          );
        }
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      )..setLocation(directory: '/work', workspace: 'workspace-1');
      try {
        final permissions = await api.pendingPermissionsV2();
        final questions = await api.pendingQuestionsV2();

        expect(permissions.single.id, 'permission-loose');
        expect(permissions.single.permission, 'edit');
        expect(permissions.single.always, isEmpty);
        expect(permissions.single.tool?.callID, 'call-1');
        expect(questions.single['id'], 'question-loose');
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('generated V2 request errors retain OpenCode identity', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        final isList = request.method == 'GET';
        final isPermission = request.uri.path.contains('permission');
        request.response.statusCode = isList
            ? HttpStatus.badRequest
            : HttpStatus.notFound;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': isList
                ? 'InvalidRequestError'
                : isPermission
                ? 'PermissionNotFoundError'
                : 'QuestionNotFoundError',
            'requestID': isList
                ? 'request-list'
                : isPermission
                ? 'permission-1'
                : 'question-1',
            'message': 'request rejected',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        for (final call in <Future<Object?> Function()>[
          api.pendingPermissionsV2,
          api.pendingQuestionsV2,
        ]) {
          await expectLater(
            call(),
            throwsA(
              isA<ApiException>()
                  .having((error) => error.statusCode, 'statusCode', 400)
                  .having(
                    (error) => error.errorTag,
                    'errorTag',
                    'InvalidRequestError',
                  )
                  .having(
                    (error) => error.requestID,
                    'requestID',
                    'request-list',
                  ),
            ),
          );
        }

        await expectLater(
          api.respondPermissionV2('session-1', 'permission-1', 'once'),
          throwsA(
            isA<ApiException>().having(
              (error) => error.isPermissionNotFound('permission-1'),
              'permission identity',
              isTrue,
            ),
          ),
        );
        for (final call in <Future<void> Function()>[
          () => api.answerQuestionV2('session-1', 'question-1', const [
            ['Yes'],
          ]),
          () => api.rejectQuestionV2('session-1', 'question-1'),
        ]) {
          await expectLater(
            call(),
            throwsA(
              isA<ApiException>().having(
                (error) => error.isQuestionNotFound('question-1'),
                'question identity',
                isTrue,
              ),
            ),
          );
        }
      } finally {
        api.close();
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
      final api = V2Api(
        permissions: [_permission('permission-1', 'session-1')],
        questions: [
          _question('question-1', 'session-1'),
          _question('question-2', 'session-2'),
        ],
      );
      final repository = QuestionRepository();
      final controller = ConnectionController(await memoryProfileStore())
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

  test('permission and question replies wait for wake transport', () async {
    final retainedApi = V2Api();
    final replacementApi = V2Api();
    final retainedRepository = QuestionRepository();
    final replacementRepository = QuestionRepository();
    final ready = Completer<void>();
    final controller =
        _DelayedRequestController(
            await memoryProfileStore(),
            ready: ready,
            replacementApi: replacementApi,
            replacementRepository: replacementRepository,
          )
          ..api = retainedApi
          ..repository = retainedRepository;
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
    for (final id in ['question-1', 'question-2']) {
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.v2.asked',
          properties: _question(id, 'session-1'),
        ),
      );
    }

    final permission = controller.answerPermission('permission-1', 'once');
    final answer = controller.answerQuestion('question-1', const [
      ['Yes'],
    ]);
    final reject = controller.rejectQuestion('question-2');
    await Future<void>.delayed(Duration.zero);

    expect(retainedApi.permissionReplies, isEmpty);
    expect(retainedApi.questionReplies, isEmpty);
    expect(retainedApi.questionRejects, isEmpty);

    ready.complete();
    await Future.wait([permission, answer, reject]);

    expect(replacementApi.permissionReplies, [
      ('session-1', 'permission-1', 'once'),
    ]);
    expect(replacementApi.questionReplies.single.$2, 'question-1');
    expect(replacementApi.questionRejects, [('session-1', 'question-2')]);
  });

  test('partial V2 hydration failure preserves V2 reply provenance', () async {
    final api = V2Api()
      ..legacyPermissions = []
      ..permissionV2Error = ApiException('V2 permission list unavailable')
      ..questionV2Error = ApiException('V2 question list unavailable');
    final repository = QuestionRepository(legacyUnavailable: false);
    final controller = ConnectionController(await memoryProfileStore())
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
      final api = V2Api()
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
      final repository = QuestionRepository()
        ..answerError = ProductException(
          'Could not send the answer',
          cause: ApiException(
            'Question not found',
            statusCode: 404,
            errorTag: 'QuestionNotFoundError',
            requestID: 'question-3',
          ),
        );
      final controller = ConnectionController(await memoryProfileStore())
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
    final streams = <FakeEventStream>[];
    final api = V2Api();
    final repository = QuestionRepository(legacyUnavailable: false);
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (_) => api,
      repositoryFactory: (_) => repository,
      eventStreamFactory:
          ({required api, required onEvent, required onStatus, onError}) {
            final stream = FakeEventStream(
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
      final api = V2Api();
      final repository = QuestionRepository(legacyUnavailable: false)
        ..questionsCompleter = Completer<List<PendingQuestion>>();
      final controller = ConnectionController(await memoryProfileStore())
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
    final api = V2Api()
      ..sessionsCompleter = Completer<List<Session>>()
      ..statusesCompleter = Completer<Map<String, String>>();
    final controller = ConnectionController(await memoryProfileStore())
      ..api = api;
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
    final api = V2Api()..statusesCompleter = Completer<Map<String, String>>();
    final controller = ConnectionController(await memoryProfileStore())
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
    // Once could be the moment between two steps of a run still going.
    expect(controller.busySessions, contains('session-1'));

    await controller.reconcileBusySessionsForTesting();
    expect(controller.busySessions, isNot(contains('session-1')));
  });

  test('a run missing from one status read, between two steps, stays '
      'running', () async {
    final api = V2Api()..statusesCompleter = Completer<Map<String, String>>();
    final controller = ConnectionController(await memoryProfileStore())
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

    final gap = controller.reconcileBusySessionsForTesting();
    api.statusesCompleter!.complete(const {});
    await gap;
    api.statusesCompleter = Completer()..complete({'session-1': 'busy'});
    await controller.reconcileBusySessionsForTesting();
    api.statusesCompleter = Completer()..complete(const {});
    await controller.reconcileBusySessionsForTesting();

    // Idle, busy, idle: never idle twice running, so never ended.
    expect(controller.busySessions, contains('session-1'));
  });
}
