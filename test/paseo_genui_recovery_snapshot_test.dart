import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _directory = '/work/cards';
const _created = '2026-10-25T08:00:00Z';
const _prompt = '2026-10-26T08:00:00Z';

void main() {
  late FakePaseoSocket socket;
  late PaseoGateway gateway;
  late Map<String, dynamic> agent;

  setUp(() {
    agent = {
      'id': 'conversation',
      'provider': 'claude',
      'cwd': _directory,
      'title': 'Conversation',
      'status': 'idle',
      'createdAt': _created,
      'updatedAt': _prompt,
      'lastUserMessageAt': _prompt,
      'activeTurn': null,
      'pendingPermissions': <Object>[],
    };
    socket = FakePaseoSocket();
    socket.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agent},
        ],
        'pageInfo': {'hasMore': false},
      },
    );
    gateway = PaseoGateway(
      directory: _directory,
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:4099',
        socketFactory: (_, _) async => socket,
      ),
    );
  });

  tearDown(() => gateway.close());

  test(
    'card recovery snapshot reads cached prompt without network IO',
    () async {
      expect(gateway.genUiRecoverySnapshot('conversation'), isNull);
      expect(socket.sent, isEmpty);
      await gateway.sessionPage();
      final requests = socket.sent.length;
      expect(gateway.genUiRecoverySnapshot('conversation'), (
        promptStamp: _prompt,
        idle: true,
      ));
      expect(gateway.genUiRecoverySnapshot('unknown'), isNull);
      expect(socket.sent, hasLength(requests));
    },
  );

  test('never prompted card recovery falls back to creation stamp', () async {
    for (final invalid in [null, '', '   ', 12]) {
      agent['lastUserMessageAt'] = invalid;
      await gateway.sessionPage();
      expect(
        gateway.genUiRecoverySnapshot('conversation')?.promptStamp,
        _created,
      );
    }
    agent['createdAt'] = '';
    await gateway.sessionPage();
    expect(gateway.genUiRecoverySnapshot('conversation')?.promptStamp, isNull);
  });

  test('card recovery sees a changed prompt but ignores updatedAt', () async {
    await gateway.sessionPage();
    agent['updatedAt'] = '2026-10-26T09:00:00Z';
    await gateway.sessionPage();
    expect(gateway.genUiRecoverySnapshot('conversation')?.promptStamp, _prompt);
    agent['lastUserMessageAt'] = '2026-10-26T10:00:00Z';
    await gateway.sessionPage();
    expect(
      gateway.genUiRecoverySnapshot('conversation')?.promptStamp,
      '2026-10-26T10:00:00Z',
    );
  });

  test('running initializing and active turns are not idle coverage', () async {
    for (final status in ['running', 'initializing']) {
      agent['status'] = status;
      await gateway.sessionPage();
      expect(gateway.genUiRecoverySnapshot('conversation')?.idle, isFalse);
    }
    agent['status'] = 'idle';
    agent['activeTurn'] = {'turnId': 'active', 'startedAt': _prompt};
    await gateway.sessionPage();
    expect(gateway.genUiRecoverySnapshot('conversation')?.idle, isFalse);
  });

  test('closed and failed agents without a turn can be covered', () async {
    for (final status in ['closed', 'error']) {
      agent['status'] = status;
      await gateway.sessionPage();
      expect(gateway.genUiRecoverySnapshot('conversation')?.idle, isTrue);
    }
  });

  test('a live turn overrides an idle cached agent snapshot', () async {
    await gateway.sessionPage();
    socket.push('agent_stream', {
      'agentId': 'conversation',
      'event': {'type': 'turn_started'},
    });
    await Future<void>.delayed(Duration.zero);
    expect(gateway.genUiRecoverySnapshot('conversation')?.idle, isFalse);
    socket.push('agent_stream', {
      'agentId': 'conversation',
      'event': {'type': 'turn_completed'},
    });
    await Future<void>.delayed(Duration.zero);
    expect(gateway.genUiRecoverySnapshot('conversation')?.idle, isTrue);
  });

  test(
    'daemon and draft aliases share prompt and pending turn metadata',
    () async {
      socket.handlers['get_providers_snapshot_request'] = (_) => (
        'get_providers_snapshot_response',
        {
          'cwd': _directory,
          'generatedAt': _prompt,
          'entries': [
            {
              'provider': 'claude',
              'status': 'ready',
              'enabled': true,
              'source': 'builtin',
              'models': <Object>[],
              'modes': <Object>[],
            },
          ],
        },
      );
      socket.handlers['create_agent_request'] = (_) =>
          ('create_agent_response', {'agent': agent});
      final draft = await gateway.createSession();
      // A draft's first prompt goes through promptAsync; promptWithMessageID
      // only answers existing conversations.
      await gateway.promptAsync(draft.id, text: 'Make a card');
      expect(gateway.daemonSessionId(draft.id), 'conversation');
      expect(gateway.genUiRecoverySnapshot(draft.id), (
        promptStamp: _prompt,
        idle: false,
      ));
      expect(
        gateway.genUiRecoverySnapshot('conversation'),
        gateway.genUiRecoverySnapshot(draft.id),
      );
      socket.push('agent_stream', {
        'agentId': 'conversation',
        'event': {'type': 'turn_completed'},
      });
      await Future<void>.delayed(Duration.zero);
      expect(gateway.genUiRecoverySnapshot('conversation'), (
        promptStamp: _prompt,
        idle: true,
      ));
    },
  );
}
