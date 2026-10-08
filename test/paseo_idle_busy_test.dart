import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson, stream;

void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  var entries = <Map<String, dynamic>>[];

  setUp(() {
    daemon = FakeDaemon();
    entries = [
      {'agent': agentJson('local')},
      {'agent': agentJson('elsewhere', cwd: '/work/other')},
    ];
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': entries,
        'pageInfo': {'hasMore': false, 'nextCursor': null},
      },
    );
    daemon.handlers['agent.provider_subagents.list.request'] = (message) => (
      'agent.provider_subagents.list.response',
      {
        'parentAgentId': message['parentAgentId'],
        'subagents': <Object?>[],
        'error': null,
      },
    );
    gateway = PaseoGateway(
      trackLocalWork: true,
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: '/work/app',
    );
  });

  tearDown(() async {
    gateway.close();
    await Future<void>.delayed(Duration.zero);
  });

  test('complete all-helper idle inventory establishes known idle', () async {
    expect(gateway.localWorkBusy, isNull);
    final changes = <bool?>[];
    final subscription = gateway.localWorkChanges.listen((_) {
      changes.add(gateway.localWorkBusy);
    });
    await gateway.sessions();
    expect(gateway.localWorkBusy, isFalse);
    await Future<void>.delayed(Duration.zero);
    expect(changes, contains(false));
    await subscription.cancel();
  });

  test('live work outside the selected directory blocks helper idle', () async {
    await gateway.sessions();
    daemon.push(
      'agent_stream',
      stream('elsewhere', {
        'type': 'turn_started',
        'provider': 'claude',
      }, seq: 1),
    );
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isTrue);
    // A lagging idle list must not erase the streamed turn.
    await gateway.sessions();
    expect(gateway.localWorkBusy, isTrue);
    daemon.push(
      'agent_stream',
      stream('elsewhere', {
        'type': 'turn_completed',
        'provider': 'claude',
      }, seq: 2),
    );
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isFalse);
  });

  test('a partial inventory cannot authorize idle', () async {
    daemon.handlers['fetch_agents_request'] = (message) {
      final cursor = (message['page'] as Map)['cursor'];
      return (
        'fetch_agents_response',
        {
          'entries': cursor == null ? [entries.first] : [entries.last],
          'pageInfo': {
            'hasMore': cursor == null,
            'nextCursor': cursor == null ? 'page-2' : null,
          },
        },
      );
    };
    await gateway.sessionPage();
    expect(gateway.localWorkBusy, isNull);
    await gateway.sessionPage(cursor: 'page-2');
    expect(gateway.localWorkBusy, isFalse);
  });

  test('initializing and permissions elsewhere keep the helper busy', () async {
    entries.last['agent'] = agentJson(
      'elsewhere',
      cwd: '/work/other',
      status: 'initializing',
    );
    await gateway.sessions();
    expect(gateway.localWorkBusy, isTrue);
    daemon.push('agent_update', {
      'agent': agentJson('elsewhere', cwd: '/work/other'),
    });
    daemon.push('agent_permission_request', {
      'agentId': 'elsewhere',
      'request': {'id': 'permission-1'},
    });
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isTrue);
    daemon.push('agent_permission_resolved', {'requestId': 'permission-1'});
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isFalse);
  });

  test('subagent work elsewhere survives an idle parent snapshot', () async {
    await gateway.sessions();
    daemon.push('agent.provider_subagents.update', {
      'kind': 'upsert',
      'subagent': {
        'id': 'child',
        'parentAgentId': 'elsewhere',
        'status': 'running',
      },
    });
    await Future<void>.delayed(Duration.zero);
    await gateway.sessions();
    expect(gateway.localWorkBusy, isTrue);
    daemon.push('agent.provider_subagents.update', {
      'kind': 'upsert',
      'subagent': {
        'id': 'child',
        'parentAgentId': 'elsewhere',
        'status': 'completed',
      },
    });
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isFalse);
  });

  test(
    'malformed inventory is unknown while known work takes precedence',
    () async {
      entries.add({
        'agent': {'id': 'broken', 'status': 'unexpected'},
      });
      await expectLater(
        gateway.sessions(),
        throwsA(
          isA<PaseoFailure>().having(
            (failure) => failure.kind,
            'kind',
            PaseoFailureKind.invalidResponse,
          ),
        ),
      );
      expect(gateway.localWorkBusy, isNull);
      daemon.push(
        'agent_stream',
        stream('elsewhere', {'type': 'turn_started'}, seq: 2),
      );
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isTrue);
      daemon.push(
        'agent_stream',
        stream('elsewhere', {'type': 'turn_completed'}, seq: 1),
      );
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isTrue);
    },
  );

  test(
    'disconnect and closure revoke known idle without reading a clock',
    () async {
      await gateway.sessions();
      expect(gateway.localWorkBusy, isFalse);
      await daemon.close();
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isNull);
      gateway.close();
      expect(gateway.localWorkBusy, isNull);
    },
  );

  test('cold idle parent cannot conceal a running subagent', () async {
    daemon.handlers['agent.provider_subagents.list.request'] = (message) => (
      'agent.provider_subagents.list.response',
      {
        'parentAgentId': message['parentAgentId'],
        'subagents': message['parentAgentId'] == 'elsewhere'
            ? [
                {
                  'id': 'cold-child',
                  'parentAgentId': 'elsewhere',
                  'status': 'running',
                },
              ]
            : [],
        'error': null,
      },
    );
    await gateway.sessions();
    expect(gateway.localWorkBusy, isTrue);
  });

  test('unsupported subagent inventory cannot prove idle', () async {
    daemon.handlers['agent.provider_subagents.list.request'] = (_) =>
        ('agent.provider_subagents.list.response', {'error': 'unsupported'});
    await gateway.sessions();
    expect(gateway.localWorkBusy, isNull);
  });

  test(
    'a new idle agent needs cold child coverage before known idle',
    () async {
      await gateway.sessions();
      expect(gateway.localWorkBusy, isFalse);
      daemon.push('agent_update', {
        'agent': agentJson('new', cwd: '/work/new'),
      });
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isNull);
    },
  );

  test('empty complete helper inventory proves idle', () async {
    entries = [];
    await gateway.sessions();
    expect(gateway.localWorkBusy, isFalse);
    expect(daemon.of('agent.provider_subagents.list.request'), isEmpty);
  });

  test('new parent during child inventory cannot restore known idle', () async {
    var introduced = false;
    daemon.handlers['agent.provider_subagents.list.request'] = (message) {
      if (!introduced) {
        introduced = true;
        daemon.push('agent_update', {
          'agent': agentJson('new-parent', cwd: '/work/new'),
        });
      }
      return (
        'agent.provider_subagents.list.response',
        {
          'parentAgentId': message['parentAgentId'],
          'subagents': <Object?>[],
          'error': null,
        },
      );
    };
    await gateway.sessions();
    expect(gateway.localWorkBusy, isNull);
    expect(
      daemon
          .of('agent.provider_subagents.list.request')
          .where((request) => request['parentAgentId'] == 'new-parent'),
      isEmpty,
    );
  });

  test('running push during fetch outranks stale idle inventory', () async {
    daemon.handlers['fetch_agents_request'] = (_) {
      daemon.push('agent_update', {
        'agent': agentJson('elsewhere', cwd: '/work/other', status: 'running'),
      });
      return (
        'fetch_agents_response',
        {
          'entries': entries,
          'pageInfo': {'hasMore': false, 'nextCursor': null},
        },
      );
    };
    await gateway.sessions();
    expect(gateway.localWorkBusy, isTrue);
  });

  test(
    'closed parent status does not prove independent children idle',
    () async {
      entries.last['agent'] = agentJson(
        'elsewhere',
        cwd: '/work/other',
        status: 'error',
      );
      daemon.handlers['agent.provider_subagents.list.request'] = (message) => (
        'agent.provider_subagents.list.response',
        {
          'parentAgentId': message['parentAgentId'],
          'subagents': message['parentAgentId'] == 'elsewhere'
              ? [
                  {
                    'id': 'detached-child',
                    'parentAgentId': 'elsewhere',
                    'status': 'running',
                  },
                ]
              : [],
          'error': null,
        },
      );
      await gateway.sessions();
      expect(gateway.localWorkBusy, isTrue);
    },
  );

  test('malformed completion ordering cannot erase an active turn', () async {
    await gateway.sessions();
    daemon.push(
      'agent_stream',
      stream('elsewhere', {'type': 'turn_started'}, seq: 1),
    );
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isTrue);
    daemon.push('agent_stream', {
      'agentId': 'elsewhere',
      'seq': 'invalid',
      'epoch': 'epoch-1',
      'event': {'type': 'turn_completed'},
    });
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isTrue);
  });

  test('stale archived snapshot cannot erase active turn or child', () async {
    await gateway.sessions();
    daemon.push(
      'agent_stream',
      stream('elsewhere', {'type': 'turn_started'}, seq: 1),
    );
    daemon.push('agent.provider_subagents.update', {
      'kind': 'upsert',
      'subagent': {
        'id': 'live-child',
        'parentAgentId': 'elsewhere',
        'status': 'running',
      },
    });
    await Future<void>.delayed(Duration.zero);
    entries.last['agent'] = agentJson(
      'elsewhere',
      cwd: '/work/other',
      archivedAt: '2026-10-08T10:00:00Z',
    );
    await gateway.sessions();
    expect(gateway.localWorkBusy, isTrue);
    daemon.push(
      'agent_stream',
      stream('elsewhere', {'type': 'turn_completed'}, seq: 2),
    );
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isTrue);
    daemon.push('agent.provider_subagents.update', {
      'kind': 'upsert',
      'subagent': {
        'id': 'live-child',
        'parentAgentId': 'elsewhere',
        'status': 'completed',
      },
    });
    await Future<void>.delayed(Duration.zero);
    // The archived snapshot retired child coverage; live boundaries must
    // not reuse that retired proof until a fresh complete list reconciles it.
    expect(gateway.localWorkBusy, isNull);
    await gateway.sessions();
    expect(gateway.localWorkBusy, isFalse);
  });

  test('unseen completed parent needs child inventory before idle', () async {
    await gateway.sessions();
    daemon.push(
      'agent_stream',
      stream('unseen-parent', {'type': 'turn_started'}, seq: 1),
    );
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isTrue);
    daemon.push(
      'agent_stream',
      stream('unseen-parent', {'type': 'turn_completed'}, seq: 2),
    );
    await Future<void>.delayed(Duration.zero);
    expect(gateway.localWorkBusy, isNull);
  });

  test(
    'unseen permission parent needs child inventory after resolution',
    () async {
      await gateway.sessions();
      daemon.push('agent_permission_request', {
        'agentId': 'unseen-parent',
        'request': {'id': 'new-request'},
      });
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isTrue);
      daemon.push('agent_permission_resolved', {'requestId': 'new-request'});
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isNull);
    },
  );

  test(
    'unseen subagent parent needs inventory after child completion',
    () async {
      await gateway.sessions();
      void push(String status) =>
          daemon.push('agent.provider_subagents.update', {
            'kind': 'upsert',
            'subagent': {
              'id': 'unseen-child',
              'parentAgentId': 'unseen-parent',
              'status': status,
            },
          });
      push('running');
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isTrue);
      push('completed');
      await Future<void>.delayed(Duration.zero);
      expect(gateway.localWorkBusy, isNull);
    },
  );
}
