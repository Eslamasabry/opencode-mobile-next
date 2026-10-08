import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

class _Daemon extends FakeDaemon {
  _Daemon(this.trace);
  final List<String> trace;
  @override
  void send(String message) {
    final frame = jsonDecode(message) as Map<String, dynamic>;
    if (frame['type'] == 'hello') {
      hello = frame;
      push('status', {'status': 'server_info', 'version': '0.9.2'});
      return;
    }
    if (frame['type'] != 'ping') {
      trace.add('rpc:${(frame['message'] as Map)['type']}');
    }
    super.send(message);
  }
}

class _World {
  _World({bool hooks = true}) {
    daemon = _Daemon(trace);
    daemon.handlers['create_agent_request'] = (_) =>
        ('create_agent_response', {'agent': agentJson('a1')});
    daemon.handlers['fetch_agent_request'] = (request) => (
      'fetch_agent_response',
      {'agent': agentJson(request['agentId'] as String)},
    );
    daemon.handlers['resume_agent_request'] = (_) =>
        ('resume_agent_response', {'agent': agentJson('a2')});
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': <Object>[],
            'modes': <Object>[],
          },
        ],
      },
    );
    daemon.handlers['send_agent_message_request'] = (request) => (
      'send_agent_message_response',
      {'agentId': request['agentId'], 'accepted': true},
    );
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: '/work/app',
      beforePayloadUse: hooks ? acquire : null,
      afterPayloadUse: hooks ? release : null,
    );
  }

  final trace = <String>[];
  late final _Daemon daemon;
  late final PaseoGateway gateway;
  Completer<void>? pending;
  bool deny = false;
  bool releaseFails = false;
  int active = 0;
  int released = 0;

  Future<void> acquire() async {
    trace.add('acquire');
    if (deny) throw StateError('synthetic-private-detail');
    active++;
    if (pending != null) await pending!.future;
  }

  void release() {
    trace.add('release');
    released++;
    active--;
    if (releaseFails) throw StateError('synthetic-release-private-detail');
  }
}

void main() {
  test(
    'denied acquisition prevents draft create and does not release',
    () async {
      final w = _World();
      addTearDown(w.gateway.close);
      final draft = await w.gateway.createSession();
      w.deny = true;
      await expectLater(
        w.gateway.promptAsync(draft.id, text: 'hello'),
        throwsA(
          isA<PaseoFailure>().having(
            (e) => e.kind,
            'kind',
            PaseoFailureKind.unavailable,
          ),
        ),
      );
      expect(w.daemon.of('create_agent_request'), isEmpty);
      expect(w.released, 0);
      expect(w.active, 0);
    },
  );

  test(
    'acquires before first create await and holds until RPC acknowledgement',
    () async {
      final w = _World();
      addTearDown(w.gateway.close);
      final draft = await w.gateway.createSession();
      w.daemon.handlers['create_agent_request'] = (_) => null;
      final sending = w.gateway.promptAsync(draft.id, text: 'hello');
      expect(w.active, 1);
      while (w.daemon.of('create_agent_request').isEmpty) {
        await pumpEventQueue();
      }
      expect(w.trace.first, 'acquire');
      expect(w.active, 1);
      expect(w.released, 0);
      w.daemon.push('create_agent_response', {
        'requestId': w.daemon.of('create_agent_request').single['requestId'],
        'agent': agentJson('a1'),
      });
      await sending;
      expect(w.active, 0);
      expect(w.released, 1);
      expect(w.trace.last, 'release');
    },
  );

  test('held acquisition blocks all draft RPC work until admitted', () async {
    final w = _World();
    addTearDown(w.gateway.close);
    final draft = await w.gateway.createSession();
    w.pending = Completer<void>();
    final sending = w.gateway.promptAsync(draft.id, text: 'hello');
    await pumpEventQueue();
    expect(w.daemon.sent, isEmpty);
    expect(w.released, 0);
    w.pending!.complete();
    await sending;
    expect(w.daemon.of('create_agent_request'), hasLength(1));
    expect(w.released, 1);
  });

  test(
    'resume acquires before fetching and releases after daemon request',
    () async {
      final w = _World();
      addTearDown(w.gateway.close);
      await w.gateway.resumeHostAgentChat('a1');
      expect(w.trace.first, 'acquire');
      expect(w.trace, contains('rpc:resume_agent_request'));
      expect(w.trace.last, 'release');
      expect(w.active, 0);
      expect(w.released, 1);
    },
  );

  test('correlated existing-session prompt also leases actual send', () async {
    final w = _World();
    addTearDown(w.gateway.close);
    await w.gateway.promptWithMessageID(
      'a1',
      messageID: 'answer',
      text: 'hello',
    );
    expect(w.trace.first, 'acquire');
    expect(w.daemon.of('send_agent_message_request'), hasLength(1));
    expect(w.trace.last, 'release');
    expect(w.active, 0);
    expect(w.released, 1);
  });

  test(
    'denied existing send and resume dispatch no RPC and never release',
    () async {
      final w = _World();
      addTearDown(w.gateway.close);
      w.deny = true;
      await expectLater(
        w.gateway.promptAsync('a1', text: 'hello'),
        throwsA(isA<PaseoFailure>()),
      );
      await expectLater(
        w.gateway.resumeHostAgentChat('a1'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(w.daemon.sent, isEmpty);
      expect(w.released, 0);
      expect(w.active, 0);
    },
  );

  test('RPC failure releases without replacing its fixed error', () async {
    final w = _World();
    addTearDown(w.gateway.close);
    final draft = await w.gateway.createSession();
    w.daemon.handlers['create_agent_request'] = (_) =>
        ('create_agent_response', {'error': 'synthetic-private-daemon-detail'});
    w.releaseFails = true;
    await expectLater(
      w.gateway.promptAsync(draft.id, text: 'hello'),
      throwsA(isA<PaseoFailure>()),
    );
    expect(w.active, 0);
    expect(w.released, 1);
    expect(w.trace.last, 'release');
  });

  test(
    'scope changed during admission releases and dispatches no RPC',
    () async {
      final w = _World();
      addTearDown(w.gateway.close);
      final draft = await w.gateway.createSession();
      w.pending = Completer<void>();
      final sending = w.gateway.promptAsync(draft.id, text: 'hello');
      final failed = expectLater(sending, throwsA(isA<PaseoFailure>()));
      w.gateway.setLocation(directory: '/work/other');
      w.pending!.complete();
      await failed;
      expect(w.daemon.sent, isEmpty);
      expect(w.released, 1);
      expect(w.active, 0);
    },
  );

  test(
    'gateway closed during admission releases and dispatches no RPC',
    () async {
      final w = _World();
      final draft = await w.gateway.createSession();
      w.pending = Completer<void>();
      final sending = w.gateway.promptAsync(draft.id, text: 'hello');
      final failed = expectLater(sending, throwsA(isA<PaseoFailure>()));
      w.gateway.close();
      w.pending!.complete();
      await failed;
      expect(w.daemon.sent, isEmpty);
      expect(w.released, 1);
      expect(w.active, 0);
    },
  );

  test(
    'default callbacks keep ordinary create resume and send paths working',
    () async {
      final w = _World(hooks: false);
      addTearDown(w.gateway.close);
      final draft = await w.gateway.createSession();
      await w.gateway.promptAsync(draft.id, text: 'hello');
      await w.gateway.resumeHostAgentChat(draft.id);
      await w.gateway.promptWithMessageID(
        'a2',
        messageID: 'follow',
        text: 'next',
      );
      expect(w.daemon.of('create_agent_request'), hasLength(1));
      expect(w.daemon.of('resume_agent_request'), hasLength(1));
      expect(w.daemon.of('send_agent_message_request'), hasLength(1));
      expect(w.released, 0);
    },
  );
}
