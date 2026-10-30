// A send an agent host refused keeps its reason and sorts it into what the
// person can do (emulator, 2026-10-27: a Kilo chat said only "The request
// could not finish." and Send again hit the same wall forever).
//
// The wire shapes follow @getpaseo/server 0.11.2 (read, never run):
// session.js handleSendAgentMessageRequest answers
// `send_agent_message_response {accepted: false, error}` with the error's
// message; agent-projections.js gives a loaded agent's payload an
// `activeTurn` key and a saved record none (its status is the record's
// `lastStatus`, "idle" included).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_run_failure.dart';
import 'package:opencode_mobile/domain/product_failure.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _dir = '/work/app';
const _model = 'kilo/google/gemini-3-pro-image';

Map<String, dynamic> _kilo({bool loaded = true}) => {
  ...agentJson('a1', loaded: loaded),
  'provider': 'kilo',
  'persistence': {'provider': 'kilo', 'sessionId': 'ses_kilo'},
  'model': _model,
  'title': 'Say pong',
  'currentModeId': 'code',
  'availableModes': [
    {'id': 'code', 'label': 'Code'},
  ],
};

Map<String, dynamic> _claudeRecord() => {
  ...agentJson('c1', loaded: false),
  'persistence': {'provider': 'claude', 'sessionId': 'native-c1'},
};

void main() {
  group('sorting a refusal', () {
    for (final (reason, kind) in [
      ('Authentication required', AgentRunFailureKind.signIn),
      ('AuthRequired', AgentRunFailureKind.signIn),
      (
        'Internal error: You need to sign in to use this model.',
        AgentRunFailureKind.signIn,
      ),
      (
        '[System Error] Internal error: You need to sign in to use this '
            'model.\n\ncode: -32603',
        AgentRunFailureKind.signIn,
      ),
      (
        'Internal error: You need to sign in to use this model',
        AgentRunFailureKind.signIn,
      ),
      (
        'Failed to authenticate: OAuth session expired',
        AgentRunFailureKind.signIn,
      ),
      ('401 Unauthorized', AgentRunFailureKind.signIn),
      ('fx needs access. Run fx login to sign in', AgentRunFailureKind.signIn),
      ('Insufficient credits for this request', AgentRunFailureKind.noCredit),
      ('402 Payment Required', AgentRunFailureKind.noCredit),
      (
        'You exceeded your current quota, please check your plan',
        AgentRunFailureKind.noCredit,
      ),
      (
        'Model kilo/google/gemini-3-pro-image is not available on your plan',
        AgentRunFailureKind.modelUnavailable,
      ),
      ('Model not found: openai/gpt-5.6', AgentRunFailureKind.modelUnavailable),
      ('Unknown model gemini-9', AgentRunFailureKind.modelUnavailable),
      ('Free usage limit reached', AgentRunFailureKind.modelUnavailable),
      ('429 Too Many Requests', AgentRunFailureKind.busy),
      ('overloaded_error: Overloaded', AgentRunFailureKind.busy),
      ('The server is busy, try again later', AgentRunFailureKind.busy),
      (
        "API Error: Can't reach the API server — check your internet or DNS "
            '(EAI_AGAIN)',
        AgentRunFailureKind.connectionLost,
      ),
      ('API Error: Request timed out.', AgentRunFailureKind.connectionLost),
      (
        'API Error: Connection error. (ECONNRESET)',
        AgentRunFailureKind.connectionLost,
      ),
      (
        'API Error: 529 {"type":"error","error":{"type":"overloaded_error",'
            '"message":"Overloaded"}}',
        AgentRunFailureKind.busy,
      ),
      ('Agent a1 has no pending run', AgentRunFailureKind.unknown),
      ('', AgentRunFailureKind.unknown),
    ]) {
      test('"$reason" is ${kind.name}', () {
        expect(classifyAgentRunFailure(reason), kind);
      });
    }

    test('the error code the reason names is kept', () {
      expect(agentRunFailureCode('Internal error | code=-32603'), '-32603');
      expect(agentRunFailureCode('Provider run failed\n\ncode: 402'), '402');
      expect(agentRunFailureCode('HTTP 429 from the gateway'), '429');
      expect(agentRunFailureCode('Authentication required (-32000)'), '-32000');
      expect(agentRunFailureCode('Authentication required'), isNull);
      expect(
        agentRunFailureCode(
          '[System Error] Internal error: You need to sign in to use this '
          'model.\n\ncode: -32603\n\nInternal error | code=-32603',
        ),
        '-32603',
      );
    });
  });

  group('a Paseo send', () {
    late FakeDaemon daemon;
    late PaseoGateway gateway;
    late Map<String, dynamic> agent;
    late Map<String, dynamic> provider;
    late Map<String, dynamic> sendReply;
    late int savedWarmupAttempts;
    late Duration savedWarmupInterval;

    setUp(() {
      savedWarmupAttempts = providerWarmupAttempts;
      savedWarmupInterval = providerWarmupInterval;
      providerWarmupAttempts = 1;
      providerWarmupInterval = Duration.zero;
      daemon = FakeDaemon();
      agent = _kilo();
      provider = {
        'provider': 'kilo',
        'status': 'ready',
        'models': [
          {'provider': 'kilo', 'id': _model, 'label': 'Gemini 3 Pro Image'},
        ],
        'modes': [
          {'id': 'code', 'label': 'Code'},
        ],
      };
      sendReply = {
        'agentId': 'a1',
        'accepted': false,
        'error': 'Authentication required',
      };
      daemon.handlers['fetch_agents_request'] = (_) => (
        'fetch_agents_response',
        {
          'entries': [
            {'agent': agent},
            {'agent': _claudeRecord()},
          ],
          'pageInfo': {'hasMore': false},
        },
      );
      daemon.handlers['fetch_agent_request'] = (request) => (
        'fetch_agent_response',
        {'agent': request['agentId'] == 'c1' ? _claudeRecord() : agent},
      );
      daemon.handlers['get_providers_snapshot_request'] = (_) => (
        'get_providers_snapshot_response',
        {
          'entries': [
            provider,
            {'provider': 'claude', 'status': 'ready', 'models': <Object>[]},
          ],
        },
      );
      daemon.handlers['send_agent_message_request'] = (_) =>
          ('send_agent_message_response', sendReply);
      gateway = PaseoGateway(
        transport: PaseoTransport(
          endpoint: 'ws://127.0.0.1:6767',
          socketFactory: (_, _) async => daemon,
        ),
        directory: _dir,
      );
    });

    tearDown(() {
      providerWarmupAttempts = savedWarmupAttempts;
      providerWarmupInterval = savedWarmupInterval;
      gateway.close();
    });

    Future<Object> refused() async {
      await gateway.sessions();
      try {
        await gateway.promptAsync('a1', text: 'reply with the word banana');
      } catch (error) {
        return error;
      }
      fail('the send was not refused');
    }

    test('to a Kilo agent the helper holds reaches the helper, and its '
        'refusal keeps the reason, agent and model', () async {
      final error = await refused();
      // The chat's gateway did not create this agent; the helper holds it,
      // so the message goes (it used to be refused here as an old chat).
      expect(daemon.of('send_agent_message_request'), hasLength(1));
      expect(error, isA<PaseoFailure>());
      final run = agentRunFailureOf(error);
      expect(run, isNotNull);
      expect(run!.reason, 'Authentication required');
      expect(run.provider, 'kilo');
      expect(run.model, _model);
      expect(run.kind, AgentRunFailureKind.signIn);
      expect(conversationNeedsNewChat(error), isFalse);
      // Details still read the reason; the words never do.
      expect(
        ProductFailure.from(error).technicalDetails,
        contains('Authentication required'),
      );
    });

    test('a model the helper refuses is "model unavailable"', () async {
      sendReply = {
        'agentId': 'a1',
        'accepted': false,
        'error': 'Model $_model is not available without a Kilo account',
      };
      final run = agentRunFailureOf(await refused());
      expect(run?.kind, AgentRunFailureKind.modelUnavailable);
    });

    test('an agent whose sign-in the helper lists as missing says so before '
        'any message goes', () async {
      provider = {...provider, 'error': 'AuthRequired'};
      final error = await refused();
      expect(daemon.of('send_agent_message_request'), isEmpty);
      final run = agentRunFailureOf(error);
      expect(run?.reason, 'AuthRequired');
      expect(run?.kind, AgentRunFailureKind.signIn);
    });

    test('a saved Kilo record the helper does not hold still asks for a new '
        'conversation, never sends, and says so', () async {
      agent = _kilo(loaded: false);
      final error = await refused();
      expect(daemon.of('send_agent_message_request'), isEmpty);
      expect(
        error,
        isA<PaseoFailure>().having(
          (f) => f.kind,
          'kind',
          PaseoFailureKind.newChatRequired,
        ),
      );
      expect(conversationNeedsNewChat(error), isTrue);
      expect(agentRunFailureOf(error), isNull);
    });

    test('a saved Kilo record opens in place: reading its timeline loads it '
        'under its own id, and a follow-up goes to that agent (no resume, '
        'no second agent)', () async {
      agent = _kilo(loaded: false);
      daemon.handlers['fetch_agent_timeline_request'] = (request) => (
        'fetch_agent_timeline_response',
        {
          'agentId': request['agentId'],
          // session.js answers with the snapshot ensureAgentLoaded loaded.
          'agent': _kilo(loaded: true),
          'epoch': 'e1',
          'entries': <Object>[],
          'hasOlder': false,
          'hasNewer': false,
        },
      );
      sendReply = {'agentId': 'a1', 'accepted': true, 'error': null};
      await gateway.sessions();
      await gateway.messages('a1');
      await gateway.promptAsync('a1', text: 'Say pong');
      expect(daemon.of('resume_agent_request'), isEmpty);
      expect(daemon.of('create_agent_request'), isEmpty);
      expect(daemon.of('send_agent_message_request').single['agentId'], 'a1');
    });
  });
}
