import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Covers the server fields the client used to drop: session usage, retry
/// backoff, tool timing, finish reasons / typed errors, model cost and
/// status, agent metadata, and permission metadata conveniences.

dynamic fixture(String name) =>
    jsonDecode(File('test/fixtures/api2/$name').readAsStringSync());

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

class _StatusApi extends OpenCodeApi {
  _StatusApi() : super(baseUrl: 'http://127.0.0.1:1');

  List<Session> sessionsResult = const [];
  Map<String, String> statuses = const {};
  Map<String, SessionRetryState> retries = const {};
  int retryCalls = 0;

  @override
  Future<Health> health() async => Health(healthy: true, version: 'v1');

  @override
  Future<List<Session>> sessions() async => sessionsResult;

  @override
  Future<Map<String, String>> sessionStatuses() async => statuses;

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async {
    retryCalls += 1;
    return retries;
  }
}

/// A v1 `Session` as `GET /session` returns it (contracts/opencode-openapi).
const v1SessionJson = <String, dynamic>{
  'id': 'ses_v1abc',
  'slug': 'repo-test',
  'projectID': 'proj_1',
  'directory': '/home/dev/projects/oc_app',
  'title': 'Repo test strategy overview',
  'version': '1.2.3',
  'agent': 'build',
  'model': {'id': 'claude-sonnet-4', 'providerID': 'anthropic'},
  'cost': 0.4275,
  'tokens': {
    'input': 59422,
    'output': 861,
    'reasoning': 61,
    'cache': {'read': 78336, 'write': 12},
  },
  'summary': {'additions': 42, 'deletions': 7, 'files': 3},
  'time': {
    'created': 1787952070398,
    'updated': 1787952242199,
    'compacting': 1787952240000,
  },
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('1. session usage', () {
    test('v1 Session keeps cost, tokens, summary, agent, model, compacting',
        () {
      final session = Session.fromJson(v1SessionJson);
      expect(session.cost, 0.4275);
      expect(session.tokens?.input, 59422);
      expect(session.tokens?.output, 861);
      expect(session.tokens?.reasoning, 61);
      expect(session.tokens?.cacheRead, 78336);
      expect(session.tokens?.cacheWrite, 12);
      expect(session.summary?.additions, 42);
      expect(session.summary?.deletions, 7);
      expect(session.summary?.files, 3);
      expect(session.agent, 'build');
      expect(session.model, 'anthropic/claude-sonnet-4');
      expect(session.time?.compacting, 1787952240000);
      expect(
        session.compactingSince,
        DateTime.fromMillisecondsSinceEpoch(1787952240000),
      );
    });

    test('v1 Session without usage fields stays null (old servers)', () {
      final session = Session.fromJson({
        'id': 'ses_min',
        'time': {'created': 1, 'updated': 2},
      });
      expect(session.cost, isNull);
      expect(session.tokens, isNull);
      expect(session.summary, isNull);
      expect(session.agent, isNull);
      expect(session.model, isNull);
      expect(session.compactingSince, isNull);
    });

    test('v2 mapApi2Session carries cost/tokens/agent/model', () {
      final page = Api2Page.fromJson(
        fixture('sessions_page.json'),
        Api2Session.fromJson,
      );
      final raw = (fixture('sessions_page.json')['data'] as List).first as Map;
      final session = mapApi2Session(page.data.first);
      expect(session.cost, (raw['cost'] as num).toDouble());
      expect(session.tokens?.input, (raw['tokens'] as Map)['input']);
      expect(
        session.tokens?.cacheRead,
        ((raw['tokens'] as Map)['cache'] as Map)['read'],
      );
      expect(session.agent, raw['agent']);
      expect(
        session.model,
        '${(raw['model'] as Map)['providerID']}/${(raw['model'] as Map)['id']}',
      );
      expect(session.summary, isNull);
      expect(session.compactingSince, isNull);
    });

    test('Session.copyWith keeps identity and replaces usage', () {
      final base = Session.fromJson(v1SessionJson);
      final next = base.copyWith(cost: 1.5, tokens: Tokens(input: 9));
      expect(next.id, base.id);
      expect(next.title, base.title);
      expect(next.directory, base.directory);
      expect(next.cost, 1.5);
      expect(next.tokens?.input, 9);
      expect(next.agent, 'build');
    });

    test('v2 session.usage.updated maps to a session.usage.updated envelope',
        () {
      final adapter = Api2EventAdapter();
      final out = adapter.adapt(
        Api2EventEnvelope.fromJson({
          'id': 'evt_1',
          'created': 1787961234000,
          'type': 'session.usage.updated',
          'data': {
            'sessionID': 'ses_1',
            'cost': 0.25,
            'tokens': {
              'input': 100,
              'output': 20,
              'reasoning': 5,
              'cache': {'read': 50, 'write': 3},
            },
          },
        }),
      );
      expect(out, hasLength(1));
      expect(out.single.type, 'session.usage.updated');
      expect(out.single.properties['sessionID'], 'ses_1');
      expect(out.single.properties['cost'], 0.25);
      final tokens = out.single.properties['tokens'] as Map;
      expect(tokens['input'], 100);
      expect((tokens['cache'] as Map)['write'], 3);
    });

    test('captured v2 stream emits usage updates instead of dropping them',
        () {
      final adapter = Api2EventAdapter();
      final out = <EventEnvelope>[];
      for (final line
          in File('test/fixtures/api2/events.sse').readAsLinesSync()) {
        if (!line.startsWith('data:')) continue;
        out.addAll(
          adapter.adapt(
            Api2EventEnvelope.fromJson(
              jsonDecode(line.substring(5).trim()) as Map<String, dynamic>,
            ),
          ),
        );
      }
      final usage =
          out.where((e) => e.type == 'session.usage.updated').toList();
      expect(usage, hasLength(2));
      expect(usage.first.properties['sessionID'], startsWith('ses_'));
    });

    test('ConnectionController merges live usage into the stored session',
        () async {
      final controller = ConnectionController(await _store());
      addTearDown(controller.dispose);
      var notified = 0;
      controller.addListener(() => notified += 1);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.created',
          properties: {'info': v1SessionJson},
        ),
      );
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.usage.updated',
          properties: const {
            'sessionID': 'ses_v1abc',
            'cost': 0.9,
            'tokens': {
              'input': 1,
              'output': 2,
              'reasoning': 3,
              'cache': {'read': 4, 'write': 5},
            },
          },
        ),
      );
      final session = controller.sessionsById['ses_v1abc']!;
      expect(session.cost, 0.9);
      expect(session.tokens?.input, 1);
      expect(session.tokens?.cacheWrite, 5);
      // Everything the usage event does not carry survives the merge.
      expect(session.title, 'Repo test strategy overview');
      expect(session.agent, 'build');
      expect(session.summary?.files, 3);
      expect(notified, 2);

      // Unknown sessions are ignored without throwing.
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.usage.updated',
          properties: const {'sessionID': 'ses_missing', 'cost': 1},
        ),
      );
      expect(controller.sessionsById.containsKey('ses_missing'), isFalse);
    });
  });

  group('2. retry state', () {
    const retryStatus = {
      'type': 'retry',
      'attempt': 3,
      'message': 'Rate limited by provider',
      'next': 1787961300000,
    };

    test('SessionRetryState parses the v1 status object', () {
      final retry = SessionRetryState.fromStatusJson(retryStatus)!;
      expect(retry.attempt, 3);
      expect(retry.message, 'Rate limited by provider');
      expect(retry.next, DateTime.fromMillisecondsSinceEpoch(1787961300000));
      expect(SessionRetryState.fromStatusJson({'type': 'busy'}), isNull);
      expect(SessionRetryState.fromStatusJson('retry'), isNull);
    });

    test('OpenCodeApi.sessionRetryStatesFromJson keeps only retry entries',
        () {
      final out = OpenCodeApi.sessionRetryStatesFromJson({
        'ses_a': {'type': 'idle'},
        'ses_b': {'type': 'busy'},
        'ses_c': retryStatus,
      });
      expect(out.keys, ['ses_c']);
      expect(out['ses_c']!.attempt, 3);
    });

    test('v2 session.status retry keeps attempt/message/next', () {
      final adapter = Api2EventAdapter();
      final out = adapter.adapt(
        Api2EventEnvelope.fromJson({
          'type': 'session.status',
          'data': {'sessionID': 'ses_1', 'status': retryStatus},
        }),
      );
      expect(out.single.type, 'session.status');
      final status = out.single.properties['status'] as Map;
      expect(status['type'], 'retry');
      expect(status['attempt'], 3);
      expect(status['next'], 1787961300000);
    });

    test('v2 session.retry.scheduled maps onto the retry status shape', () {
      final adapter = Api2EventAdapter();
      final out = adapter.adapt(
        Api2EventEnvelope.fromJson({
          'type': 'session.retry.scheduled',
          'data': {
            'sessionID': 'ses_1',
            'assistantMessageID': 'msg_1',
            'attempt': 2,
            'at': 1787961300000,
            'error': {
              'type': 'ServiceUnavailableError',
              'message': 'overloaded',
              'status': 503,
            },
          },
        }),
      );
      expect(out.single.type, 'session.status');
      final status = out.single.properties['status'] as Map;
      expect(status['type'], 'retry');
      expect(status['attempt'], 2);
      expect(status['message'], 'overloaded');
      expect(status['next'], 1787961300000);
    });

    test('controller tracks retryStates and clears them on busy/idle',
        () async {
      final controller = ConnectionController(await _store());
      addTearDown(controller.dispose);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: const {'sessionID': 'ses_1', 'status': retryStatus},
        ),
      );
      expect(controller.busySessions, contains('ses_1'));
      expect(controller.retryStates['ses_1']?.attempt, 3);
      expect(controller.retryStates['ses_1']?.message,
          'Rate limited by provider');

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: const {
            'sessionID': 'ses_1',
            'status': {'type': 'busy'},
          },
        ),
      );
      expect(controller.busySessions, contains('ses_1'));
      expect(controller.retryStates, isEmpty);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: const {'sessionID': 'ses_1', 'status': retryStatus},
        ),
      );
      expect(controller.retryStates, contains('ses_1'));
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.idle',
          properties: const {'sessionID': 'ses_1'},
        ),
      );
      expect(controller.busySessions, isNot(contains('ses_1')));
      expect(controller.retryStates, isEmpty);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: const {'sessionID': 'ses_2', 'status': retryStatus},
        ),
      );
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.error',
          properties: const {
            'sessionID': 'ses_2',
            'error': {'message': 'boom'},
          },
        ),
      );
      expect(controller.retryStates, isEmpty);
    });

    test('refreshSessions hydrates retry details from the v1 status endpoint',
        () async {
      final api = _StatusApi()
        ..sessionsResult = [Session(id: 'ses_1'), Session(id: 'ses_2')]
        ..statuses = {'ses_1': 'retry', 'ses_2': 'busy'}
        ..retries = {
          'ses_1': SessionRetryState.fromStatusJson(retryStatus)!,
        };
      final controller = ConnectionController(await _store())..api = api;
      addTearDown(controller.dispose);

      await controller.refreshSessions();
      expect(api.retryCalls, 1);
      expect(controller.busySessions, containsAll(['ses_1', 'ses_2']));
      expect(controller.retryStates.keys, ['ses_1']);
      expect(controller.retryStates['ses_1']?.attempt, 3);

      // No retrying session → no extra request, and stale entries clear.
      api.statuses = {'ses_1': 'busy', 'ses_2': 'idle'};
      await controller.refreshSessions();
      expect(api.retryCalls, 1);
      expect(controller.retryStates, isEmpty);
      expect(controller.busySessions, ['ses_1']);
    });
  });

  group('3. tool timing', () {
    test('v1 ToolState parses time{start,end,compacted}', () {
      final state = ToolState.fromJson({
        'status': 'completed',
        'input': {'command': 'ls'},
        'output': 'a\nb',
        'title': 'ls',
        'metadata': {},
        'time': {'start': 1000, 'end': 3500, 'compacted': 9000},
      }, toolName: 'bash');
      expect(state.startedAt, DateTime.fromMillisecondsSinceEpoch(1000));
      expect(state.completedAt, DateTime.fromMillisecondsSinceEpoch(3500));
      expect(state.duration, const Duration(milliseconds: 2500));
      expect(state.pruned, isTrue);
      expect(state.executed, isTrue);
    });

    test('running v1 tool has a start but no duration', () {
      final state = ToolState.fromJson({
        'status': 'running',
        'input': {},
        'time': {'start': 1000},
      });
      expect(state.startedAt, isNotNull);
      expect(state.completedAt, isNull);
      expect(state.duration, isNull);
      expect(state.pruned, isFalse);
    });

    test('pending v1 tool has no timing', () {
      final state = ToolState.fromJson({'status': 'pending', 'raw': '{'});
      expect(state.startedAt, isNull);
      expect(state.duration, isNull);
      expect(state.executed, isTrue);
    });

    test('v2 tool content maps executed and time through the v1 mapper', () {
      final content = Api2AssistantContent.fromJson({
        'type': 'tool',
        'id': 'call_1',
        'name': 'read',
        'executed': false,
        'state': {
          'status': 'completed',
          'input': {'path': 'README.md'},
          'content': [
            {'type': 'text', 'text': 'ok'},
          ],
        },
        'time': {
          'created': 1000,
          'ran': 1200,
          'completed': 1800,
          'pruned': 5000,
        },
      }) as Api2ToolCallContent;
      expect(content.time?.pruned, 5000);
      final parts = partsFromAssistantContent('msg_1', [content]);
      final state = parts.single.toolState;
      expect(state.executed, isFalse);
      expect(state.startedAt, DateTime.fromMillisecondsSinceEpoch(1200));
      expect(state.completedAt, DateTime.fromMillisecondsSinceEpoch(1800));
      expect(state.duration, const Duration(milliseconds: 600));
      expect(state.pruned, isTrue);
      expect(state.output, 'ok');
    });

    test('captured v2 assistant message keeps executed:false', () {
      final message = Api2Message.fromJson(
        Map<String, dynamic>.from(fixture('message_assistant.json')['data']),
      )!;
      final bundle = mapApi2Message(sessionID, message);
      final tool = bundle.parts.firstWhere((p) => p.type == 'tool');
      expect(tool.toolState.executed, isFalse);
      expect(tool.toolState.pruned, isFalse);
    });

    test('v2 tool.success event forwards executed', () {
      final adapter = Api2EventAdapter();
      final out = adapter.adapt(
        Api2EventEnvelope.fromJson({
          'type': 'session.tool.success',
          'data': {
            'sessionID': 'ses_1',
            'assistantMessageID': 'msg_1',
            'id': 'call_1',
            'content': [
              {'type': 'text', 'text': 'done'},
            ],
            'executed': false,
          },
        }),
      );
      final part = out.single.properties['part'] as Map;
      final state = ToolState.fromJson(part['state']);
      expect(state.executed, isFalse);
      expect(state.status, 'completed');
    });
  });

  group('4. finish reason and typed errors', () {
    test('MessageErrorKind.fromName classifies v1 error names', () {
      expect(
        MessageErrorKind.fromName('ContextOverflowError'),
        MessageErrorKind.contextOverflow,
      );
      expect(
        MessageErrorKind.fromName('MessageOutputLengthError'),
        MessageErrorKind.outputLength,
      );
      expect(
        MessageErrorKind.fromName('ProviderAuthError'),
        MessageErrorKind.providerAuth,
      );
      expect(
        MessageErrorKind.fromName('ContentFilterError'),
        MessageErrorKind.contentFilter,
      );
      expect(
        MessageErrorKind.fromName('MessageAbortedError'),
        MessageErrorKind.aborted,
      );
      expect(MessageErrorKind.fromName('APIError'), MessageErrorKind.unknown);
      expect(MessageErrorKind.fromName(null), isNull);
      expect(MessageErrorKind.fromName(''), isNull);
    });

    test('v1 MessageInfo exposes finish and errorKind, keeps errorText', () {
      final info = MessageInfo.fromJson({
        'id': 'msg_1',
        'sessionID': 'ses_1',
        'role': 'assistant',
        'finish': 'length',
        'error': {
          'name': 'ContextOverflowError',
          'data': {'message': 'Context window exceeded'},
        },
        'time': {'created': 1, 'completed': 2},
      });
      expect(info.finish, 'length');
      expect(info.errorKind, MessageErrorKind.contextOverflow);
      expect(info.errorText, 'Context window exceeded');

      final clean = MessageInfo.fromJson({
        'id': 'msg_2',
        'role': 'assistant',
        'finish': 'stop',
      });
      expect(clean.finish, 'stop');
      expect(clean.errorKind, isNull);
      expect(clean.errorText, isNull);
    });

    test('v2 assistant message maps finish and structured error type', () {
      final message = Api2Message.fromJson({
        'id': 'msg_v2',
        'type': 'assistant',
        'time': {'created': 1, 'completed': 2},
        'finish': 'error',
        'error': {
          'type': 'ProviderAuthError',
          'message': 'Invalid API key',
          'status': 401,
        },
        'content': [],
      })!;
      final info = mapApi2Message(sessionID, message).info;
      expect(info.finish, 'error');
      expect(info.errorKind, MessageErrorKind.providerAuth);
      expect(info.errorText, 'Invalid API key');

      final ok = mapApi2Message(
        sessionID,
        Api2Message.fromJson(
          Map<String, dynamic>.from(fixture('message_assistant.json')['data']),
        )!,
      ).info;
      expect(ok.errorKind, isNull);
    });

    test('v2 step.failed carries the error name for classification', () {
      final adapter = Api2EventAdapter();
      final out = adapter.adapt(
        Api2EventEnvelope.fromJson({
          'created': 10,
          'type': 'session.step.failed',
          'data': {
            'sessionID': 'ses_1',
            'assistantMessageID': 'msg_1',
            'error': {'type': 'ContentFilterError', 'message': 'blocked'},
          },
        }),
      );
      final info = MessageInfo.fromJson(
        Map<String, dynamic>.from(out.single.properties['info'] as Map),
      );
      expect(info.errorText, 'blocked');
      expect(info.errorKind, MessageErrorKind.contentFilter);
    });
  });

  group('5. model cost/status', () {
    test('ModelCost.fromJson reads the v1 per-million price object', () {
      final cost = ModelCost.fromJson({
        'input': 3,
        'output': 15,
        'cache': {'read': 0.3, 'write': 3.75},
      })!;
      expect(cost.inputPerMillion, 3);
      expect(cost.outputPerMillion, 15);
      expect(cost.cacheReadPerMillion, 0.3);
      expect(cost.cacheWritePerMillion, 3.75);
      expect(cost.isFree, isFalse);
      expect(ModelCost.fromJson(null), isNull);
      expect(ModelCost.fromJson({'input': 0, 'output': 0})!.isFree, isTrue);
    });

    test('v2 catalog model carries cost, status and release date', () {
      final raw = (fixture('models_sample.json')['data'] as List).first as Map;
      final model = Api2ModelInfo.fromJson(Map<String, dynamic>.from(raw))!;
      expect(model.cost, hasLength(1));
      final catalog = mapApi2CatalogModel(model);
      final expected = (raw['cost'] as List).first as Map;
      expect(catalog.cost?.inputPerMillion, expected['input']);
      expect(catalog.cost?.outputPerMillion, expected['output']);
      expect(
        catalog.cost?.cacheReadPerMillion,
        (expected['cache'] as Map)['read'],
      );
      expect(catalog.status, raw['status']);
      expect(
        catalog.released,
        DateTime.fromMillisecondsSinceEpoch((raw['time'] as Map)['released']),
      );
      expect(catalog.deprecated, isFalse);
    });

    test('v2 tiered cost list prefers the untiered base entry', () {
      final model = Api2ModelInfo.fromJson({
        'id': 'm',
        'providerID': 'p',
        'name': 'M',
        'status': 'deprecated',
        'time': {'released': 0},
        'cost': [
          {
            'tier': {'type': 'context', 'size': 200000},
            'input': 6,
            'output': 30,
            'cache': {'read': 0.6, 'write': 7.5},
          },
          {
            'input': 3,
            'output': 15,
            'cache': {'read': 0.3, 'write': 3.75},
          },
        ],
      })!;
      final catalog = mapApi2CatalogModel(model);
      expect(catalog.cost?.inputPerMillion, 3);
      expect(catalog.released, isNull);
      expect(catalog.deprecated, isTrue);
      expect(catalog.preview, isFalse);
    });
  });

  group('6. agent metadata', () {
    test('v1 AgentInfo parses color and model', () {
      final agent = AgentInfo.fromJson({
        'name': 'plan',
        'mode': 'primary',
        'color': '#22c55e',
        'model': {'modelID': 'gpt-5', 'providerID': 'openai'},
      });
      expect(agent.color, '#22c55e');
      expect(agent.model, 'openai/gpt-5');
      expect(AgentInfo.fromJson({'name': 'x'}).color, isNull);
    });

    test('v2 catalog agent carries color and model', () {
      final agent = Api2AgentInfo.fromJson({
        'id': 'plan',
        'name': 'Plan',
        'mode': 'primary',
        'hidden': false,
        'color': 'accent',
        'model': {'id': 'gpt-5', 'providerID': 'openai'},
        'permissions': [],
      })!;
      final catalog = mapApi2CatalogAgent(agent);
      expect(catalog.color, 'accent');
      expect(catalog.model, 'openai/gpt-5');
      expect(mapApi2Agent(agent).color, 'accent');
      expect(mapApi2Agent(agent).model, 'openai/gpt-5');

      final captured = Api2Page.fromJson(
        fixture('agents.json'),
        Api2AgentInfo.fromJson,
      );
      final build = mapApi2CatalogAgent(captured.data.first);
      expect(build.id, 'build');
      expect(build.color, isNull);
      expect(build.model, isNull);
    });
  });

  group('7. permission metadata getters', () {
    test('commandPreview reads metadata.command, else bash patterns', () {
      final withMetadata = PermissionRequest.fromJson({
        'id': 'per_1',
        'sessionID': 'ses_1',
        'permission': 'bash',
        'patterns': ['git *'],
        'metadata': {'command': 'git status --short'},
        'always': ['git *'],
      });
      expect(withMetadata.commandPreview, 'git status --short');
      expect(withMetadata.filePath, isNull);

      final patternsOnly = PermissionRequest(
        id: 'per_2',
        sessionID: 'ses_1',
        permission: 'bash',
        patterns: const ['npm test'],
      );
      expect(patternsOnly.commandPreview, 'npm test');

      final wildcard = PermissionRequest(
        id: 'per_3',
        sessionID: 'ses_1',
        permission: 'bash',
        patterns: const ['*'],
      );
      expect(wildcard.commandPreview, isNull);
    });

    test('filePath reads metadata.filePath, else file-scoped patterns', () {
      final edit = PermissionRequest.fromJson({
        'id': 'per_4',
        'sessionID': 'ses_1',
        'permission': 'edit',
        'patterns': ['/repo/lib/main.dart'],
        'metadata': {'filePath': '/repo/lib/main.dart', 'diff': '--- a'},
        'always': [],
      });
      expect(edit.filePath, '/repo/lib/main.dart');
      expect(edit.commandPreview, isNull);

      final external = PermissionRequest(
        id: 'per_5',
        sessionID: 'ses_1',
        permission: 'external_directory',
        patterns: const ['/tmp/out/*'],
      );
      expect(external.filePath, '/tmp/out/*');

      final lowercase = PermissionRequest(
        id: 'per_6',
        sessionID: 'ses_1',
        permission: 'write',
        metadata: const {'filepath': 'notes.md'},
      );
      expect(lowercase.filePath, 'notes.md');

      final unrelated = PermissionRequest(
        id: 'per_7',
        sessionID: 'ses_1',
        permission: 'webfetch',
        patterns: const ['https://example.com'],
      );
      expect(unrelated.filePath, isNull);
      expect(unrelated.commandPreview, isNull);
    });
  });
}

const sessionID = 'ses_fb534b6a0ffeJ5pNlwiesRdWV2';
