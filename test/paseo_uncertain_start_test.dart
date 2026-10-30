// A first message whose create request got no answer leaves a chat that can
// recover.
//
// Audit 2026-10-27: a cold daemon past the 90 s create timeout (or a link
// that dropped mid-request) marked the new Claude Code chat "delivery
// unknown". Only a successful history read cleared that, and a draft's read
// asked the daemon for the draft's own id, which it never knew, so every
// retry failed at once and the chat was dead for good.
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _directory = '/root/projects/FinanceHub3';

final _now = DateTime.now().toUtc().toIso8601String();

Map<String, dynamic> _agent(String id) => {
  'id': id,
  'provider': 'claude',
  'cwd': _directory,
  'model': 'claude-opus-5-5',
  'title': 'Started on the daemon',
  'status': 'idle',
  'createdAt': _now,
  'updatedAt': _now,
  'lastUserMessageAt': _now,
};

/// The id the app gave the first message (it chooses one per send).
var _firstMessageID = '';

FakePaseoSocket _daemon({List<Map<String, dynamic>> agents = const []}) {
  final socket = FakePaseoSocket();
  socket.handlers['fetch_agents_request'] = (_) => (
    'fetch_agents_response',
    {
      'entries': [
        for (final agent in agents) {'agent': agent},
      ],
      'pageInfo': {'hasMore': false, 'nextCursor': null, 'prevCursor': null},
    },
  );
  socket.handlers['get_providers_snapshot_request'] = (_) => (
    'get_providers_snapshot_response',
    {
      'entries': [
        {
          'provider': 'claude',
          'status': 'ready',
          'enabled': true,
          'models': [
            {'id': 'claude-opus-5-5', 'label': 'Opus 5.5', 'isDefault': true},
          ],
          'modes': [
            {'id': 'default'},
          ],
          'defaultModeId': 'default',
        },
      ],
    },
  );
  socket.handlers['fetch_agent_request'] = (request) => (
    'fetch_agent_response',
    {
      'agent': [
        for (final agent in agents)
          if (agent['id'] == request['agentId']) agent,
      ].firstOrNull,
    },
  );
  socket.handlers['fetch_agent_timeline_request'] = (request) => (
    'fetch_agent_timeline_response',
    {
      'agentId': request['agentId'],
      'entries': [
        {
          'seqStart': 1,
          'timestamp': _now,
          'provider': 'claude',
          'item': {
            'type': 'user_message',
            'text': 'reply with the word ready',
            'messageId': _firstMessageID,
          },
        },
      ],
    },
  );
  socket.handlers['send_agent_message_request'] = (request) => (
    'send_agent_message_response',
    {'agentId': request['agentId'], 'accepted': true, 'error': null},
  );
  socket.handlers['create_agent_request'] = (_) =>
      ('create_agent_response', {'agent': _agent('created-again')});
  return socket;
}

void main() {
  late FakePaseoSocket first;
  late FakePaseoSocket second;
  late PaseoGateway gateway;

  /// Sends the draft's first message on [first], which never answers the
  /// create; the link then drops, so the outcome is unknown.
  Future<String> startUncertain() async {
    final draft = await gateway.createSession();
    first.handlers['create_agent_request'] = (request) {
      _firstMessageID = request['clientMessageId'] as String;
      scheduleMicrotask(first.close);
      return null;
    };
    await expectLater(
      gateway.promptAsync(draft.id, text: 'reply with the word ready'),
      throwsA(
        isA<PaseoFailure>().having(
          (f) => f.kind,
          'kind',
          PaseoFailureKind.deliveryUnknown,
        ),
      ),
    );
    return draft.id;
  }

  void build(List<Map<String, dynamic>> onDaemon) {
    first = _daemon();
    second = _daemon(agents: onDaemon);
    var connections = 0;
    gateway = PaseoGateway(
      directory: _directory,
      defaultProviderModes: const {'claude': 'default'},
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:4099/ws',
        socketFactory: (_, _) async => connections++ == 0 ? first : second,
      ),
    );
    addTearDown(() async {
      gateway.close();
      await first.close();
      await second.close();
    });
  }

  test('the agent the daemon did start becomes the chat, and the first '
      'message is not sent twice', () async {
    build([_agent('started-real')]);
    final id = await startUncertain();

    final history = await gateway.messages(id);

    expect(history.single.parts.single.text, 'reply with the word ready');
    await gateway.promptAsync(id, text: 'and now?');
    expect(second.of('create_agent_request'), isEmpty);
    expect(
      second.of('send_agent_message_request').single['agentId'],
      'started-real',
    );
  });

  test('when the daemon started nothing, the chat may send again', () async {
    build(const []);
    final id = await startUncertain();

    expect(await gateway.messages(id), isEmpty);
    await gateway.promptAsync(id, text: 'reply with the word ready');
    expect(second.of('create_agent_request'), hasLength(1));
  });

  test('a send before any reload settles it too', () async {
    build(const []);
    final id = await startUncertain();

    await gateway.promptAsync(id, text: 'reply with the word ready');
    expect(second.of('create_agent_request'), hasLength(1));
  });
}
