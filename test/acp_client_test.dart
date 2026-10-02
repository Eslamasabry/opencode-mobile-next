import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/acp/acp_client.dart';

class _Agent {
  final input = StreamController<List<int>>();
  final outgoing = <Map<String, dynamic>>[];
  final sent = StreamController<Map<String, dynamic>>.broadcast();
  Future<void> Function(Map<String, dynamic>)? onWrite;
  late AcpClient client;

  _Agent({
    Future<String?> Function(AcpPermissionRequest)? permission,
    Duration timeout = const Duration(seconds: 2),
    int maxFrameBytes = 1024 * 1024,
  }) {
    client = AcpClient(
      input.stream,
      (bytes) async {
        final message = jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>;
        outgoing.add(message);
        sent.add(message);
        if (onWrite != null) {
          await onWrite!(message);
        } else if (message['method'] == 'initialize') {
          reply(message, {
            'protocolVersion': 1,
            'agentCapabilities': {'loadSession': true},
            'authMethods': [],
          });
        } else if (message['method'] == 'session/new') {
          reply(message, {'sessionId': 's${message['id']}'});
        }
      },
      requestTimeout: timeout,
      maxFrameBytes: maxFrameBytes,
      onPermission: permission,
    );
  }

  void emit(Map<String, Object?> message, {bool fragmented = false}) {
    final bytes = utf8.encode('${jsonEncode(message)}\n');
    if (fragmented) {
      for (final byte in bytes) {
        input.add([byte]);
      }
    } else {
      input.add(bytes);
    }
  }

  void reply(Map<String, dynamic> request, Map<String, Object?> result) =>
      emit({'jsonrpc': '2.0', 'id': request['id'], 'result': result});

  Future<Map<String, dynamic>> waitFor(
    bool Function(Map<String, dynamic>) match,
  ) async {
    for (final message in outgoing) {
      if (match(message)) return message;
    }
    return sent.stream.firstWhere(match).timeout(const Duration(seconds: 1));
  }

  void permission({Object id = 'p', String sessionId = 's1'}) => emit({
    'jsonrpc': '2.0',
    'id': id,
    'method': 'session/request_permission',
    'params': {
      'sessionId': sessionId,
      'toolCall': {'toolCallId': 'tool', 'future': true},
      'options': [
        {'optionId': 'yes', 'name': 'Allow once', 'kind': 'allow_once'},
        {'optionId': 'no', 'name': 'Reject', 'kind': 'reject_once'},
        {'optionId': 'future', 'name': 'Future', 'kind': 'new_kind'},
      ],
    },
  });

  Future<String> session() async {
    await client.initialize();
    return (await client.newSession(cwd: '/fake/project')).sessionId;
  }

  Future<void> close() async {
    await client.dispose();
    await input.close();
    await sent.close();
  }
}

Matcher _failure(AcpFailure failure) =>
    isA<AcpException>().having((error) => error.failure, 'failure', failure);

void main() {
  test(
    'fragmented UTF8 handshake preserves unknown additions and safe capabilities',
    () async {
      final agent = _Agent();
      addTearDown(agent.close);
      agent.onWrite = (request) async {
        agent.emit({
          'jsonrpc': '2.0',
          'id': request['id'],
          'result': {
            'protocolVersion': 1,
            'agentCapabilities': {'future': 'مرحبا'},
            'authMethods': [],
            'extension': 7,
          },
        }, fragmented: true);
      };
      final result = await agent.client.initialize();
      expect(result.fields['extension'], 7);
      expect(result.capabilities.fields['future'], 'مرحبا');
      expect(result.capabilities.loadSession, false);
      final params = agent.outgoing.single['params'] as Map;
      expect(params['clientCapabilities'], {
        'fs': {'readTextFile': false, 'writeTextFile': false},
        'terminal': false,
      });
    },
  );

  test('rejects unsupported negotiated version and closes', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    agent.onWrite = (request) async =>
        agent.reply(request, {'protocolVersion': 2, 'agentCapabilities': {}});
    await expectLater(
      agent.client.initialize(),
      throwsA(_failure(AcpFailure.version)),
    );
    await expectLater(
      agent.client.initialize(),
      throwsA(_failure(AcpFailure.closed)),
    );
  });

  test('session requires initialization and absolute host cwd', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    await expectLater(
      agent.client.newSession(cwd: '/fake'),
      throwsA(_failure(AcpFailure.state)),
    );
    await agent.client.initialize();
    await expectLater(
      agent.client.newSession(cwd: 'relative'),
      throwsArgumentError,
    );
    final session = await agent.client.newSession(cwd: '/fake');
    expect(session.sessionId, 's1');
    expect((agent.outgoing.last['params'] as Map)['mcpServers'], isEmpty);
  });

  test('correlates concurrent replies arriving out of order', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    await agent.client.initialize();
    agent.onWrite = (_) async {};
    final first = agent.client.newSession(cwd: '/one');
    final second = agent.client.newSession(cwd: '/two');
    final requests = <Map<String, dynamic>>[];
    await agent.waitFor((m) {
      if (m['method'] == 'session/new' && !requests.contains(m)) {
        requests.add(m);
      }
      return requests.length == 2;
    });
    agent.reply(requests[1], {'sessionId': 'second'});
    agent.reply(requests[0], {'sessionId': 'first'});
    expect((await first).sessionId, 'first');
    expect((await second).sessionId, 'second');
  });

  test(
    'prompt streams text, diffs, terminal refs and unknown updates',
    () async {
      final agent = _Agent();
      addTearDown(agent.close);
      final session = await agent.session();
      final updates = <AcpSessionUpdate>[];
      final subscription = agent.client.updates.listen(updates.add);
      addTearDown(subscription.cancel);
      agent.onWrite = (request) async {
        if (request['method'] != 'session/prompt') return;
        for (final update in [
          {
            'sessionUpdate': 'agent_message_chunk',
            'content': {'type': 'text', 'text': 'hello'},
          },
          {
            'sessionUpdate': 'tool_call',
            'toolCallId': 't',
            'content': [
              {
                'type': 'diff',
                'path': '/fake/file',
                'oldText': null,
                'newText': 'new',
              },
              {'type': 'terminal', 'terminalId': 'term'},
            ],
          },
          {'sessionUpdate': 'future_update', 'extra': 42},
        ]) {
          agent.emit({
            'jsonrpc': '2.0',
            'method': 'session/update',
            'params': {'sessionId': session, 'update': update},
          });
        }
        agent.reply(request, {'stopReason': 'end_turn', 'future': true});
      };
      final result = await agent.client.prompt(
        sessionId: session,
        text: 'question',
      );
      await Future<void>.delayed(Duration.zero);
      expect(result.stopReason, 'end_turn');
      expect(result.fields['future'], true);
      expect(updates[0].content!.text, 'hello');
      expect(updates[1].toolCall!.content[0].newText, 'new');
      expect(updates[1].toolCall!.content[1].terminalId, 'term');
      expect(updates[2].kind, 'future_update');
      expect(updates[2].update['extra'], 42);
    },
  );

  for (final choice in <String?>[null, 'yes', 'no', 'not-offered', 'future']) {
    test('permission selection validates offered option: $choice', () async {
      final agent = _Agent(
        permission: choice == null ? null : (_) async => choice,
      );
      addTearDown(agent.close);
      final session = await agent.session();
      final prompt = agent.client.prompt(sessionId: session, text: 'question');
      final request = await agent.waitFor(
        (m) => m['method'] == 'session/prompt',
      );
      agent.permission(sessionId: session);
      final reply = await agent.waitFor((m) => m['id'] == 'p');
      final outcome = (reply['result'] as Map)['outcome'] as Map;
      if (choice == 'yes' || choice == 'no') {
        expect(outcome, {'outcome': 'selected', 'optionId': choice});
      } else {
        expect(outcome, {'outcome': 'cancelled'});
      }
      agent.reply(request, {'stopReason': 'end_turn'});
      await prompt;
    });
  }

  test('cancel denies pending permission and ignores late grant', () async {
    final selection = Completer<String?>();
    final handlerStarted = Completer<void>();
    final agent = _Agent(
      permission: (_) {
        handlerStarted.complete();
        return selection.future;
      },
    );
    addTearDown(agent.close);
    final session = await agent.session();
    final prompt = agent.client.prompt(sessionId: session, text: 'question');
    final request = await agent.waitFor((m) => m['method'] == 'session/prompt');
    agent.permission(sessionId: session);
    await handlerStarted.future;
    await agent.client.cancel(session);
    selection.complete('yes');
    final reply = await agent.waitFor((m) => m['id'] == 'p');
    expect(reply['result'], {
      'outcome': {'outcome': 'cancelled'},
    });
    agent.reply(request, {'stopReason': 'cancelled'});
    expect((await prompt).stopReason, 'cancelled');
    expect(agent.outgoing.where((m) => m['id'] == 'p').length, 1);
    final cancel = agent.outgoing.firstWhere(
      (m) => m['method'] == 'session/cancel',
    );
    expect(cancel.containsKey('id'), false);
  });

  test('permission callback exceptions deny without exposing error', () async {
    final agent = _Agent(
      permission: (_) async => throw StateError('fake-secret'),
    );
    addTearDown(agent.close);
    final session = await agent.session();
    final prompt = agent.client.prompt(sessionId: session, text: 'question');
    final request = await agent.waitFor((m) => m['method'] == 'session/prompt');
    agent.permission(sessionId: session);
    final reply = await agent.waitFor((m) => m['id'] == 'p');
    expect(reply['result'], {
      'outcome': {'outcome': 'cancelled'},
    });
    agent.reply(request, {'stopReason': 'end_turn'});
    await prompt;
  });

  test(
    'unsupported fs terminal and future requests return method not found',
    () async {
      final agent = _Agent();
      addTearDown(agent.close);
      await agent.client.initialize();
      for (final method in [
        'fs/read_text_file',
        'terminal/create',
        'future/method',
      ]) {
        agent.emit({
          'jsonrpc': '2.0',
          'id': method,
          'method': method,
          'params': {},
        });
        final reply = await agent.waitFor((m) => m['id'] == method);
        expect((reply['error'] as Map)['code'], -32601);
      }
    },
  );

  test('RPC exception excludes remote message and data', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    agent.onWrite = (request) async => agent.emit({
      'jsonrpc': '2.0',
      'id': request['id'],
      'error': {
        'code': -32000,
        'message': 'fake-secret',
        'data': {'key': 'fake-secret'},
      },
    });
    try {
      await agent.client.initialize();
      fail('expected remote failure');
    } on AcpException catch (error) {
      expect(error.failure, AcpFailure.rpc);
      expect(error.rpcCode, -32000);
      expect(error.toString(), isNot(contains('fake-secret')));
    }
  });

  test('EOF completes every pending request', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    await agent.client.initialize();
    agent.onWrite = (_) async {};
    final first = expectLater(
      agent.client.newSession(cwd: '/one'),
      throwsA(_failure(AcpFailure.closed)),
    );
    final second = expectLater(
      agent.client.newSession(cwd: '/two'),
      throwsA(_failure(AcpFailure.closed)),
    );
    await agent.input.close();
    await Future.wait([first, second]);
  });

  test('timeout closes and never replays a prompt', () async {
    final agent = _Agent(timeout: const Duration(milliseconds: 50));
    addTearDown(agent.close);
    final session = await agent.session();
    await expectLater(
      agent.client.prompt(sessionId: session, text: 'question'),
      throwsA(_failure(AcpFailure.timeout)),
    );
    await expectLater(
      agent.client.prompt(sessionId: session, text: 'question'),
      throwsA(_failure(AcpFailure.closed)),
    );
    expect(
      agent.outgoing.where((m) => m['method'] == 'session/prompt').length,
      1,
    );
  });

  test('oversized unterminated input rejects the pending request', () async {
    final agent = _Agent(maxFrameBytes: 512);
    addTearDown(agent.close);
    agent.onWrite = (_) async => agent.input.add(List.filled(513, 65));
    await expectLater(
      agent.client.initialize(),
      throwsA(_failure(AcpFailure.capacity)),
    );
  });

  test('oversized outgoing prompt is not written', () async {
    final agent = _Agent(maxFrameBytes: 512);
    addTearDown(agent.close);
    final session = await agent.session();
    await expectLater(
      agent.client.prompt(sessionId: session, text: 'x' * 600),
      throwsA(_failure(AcpFailure.capacity)),
    );
    expect(
      agent.outgoing.where((m) => m['method'] == 'session/prompt'),
      isEmpty,
    );
  });

  for (final bytes in [
    utf8.encode('{fake-secret}\n'),
    [255, 10],
  ]) {
    test('malformed wire frame fails safely: ${bytes.length} bytes', () async {
      final agent = _Agent();
      addTearDown(agent.close);
      agent.onWrite = (_) async => agent.input.add(bytes);
      await expectLater(
        agent.client.initialize(),
        throwsA(_failure(AcpFailure.protocol)),
      );
    });
  }

  test('malformed matched result does not strand the pending waiter', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    agent.onWrite = (request) async =>
        agent.emit({'jsonrpc': '2.0', 'id': request['id'], 'result': 1});
    await expectLater(
      agent.client.initialize(),
      throwsA(_failure(AcpFailure.protocol)),
    );
  });

  test('write failure is sanitized and terminates pending request', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    agent.onWrite = (_) async => throw StateError('fake-secret');
    await expectLater(
      agent.client.initialize(),
      throwsA(_failure(AcpFailure.transport)),
    );
  });

  test('dispose abandons pending call and closes update stream', () async {
    final agent = _Agent();
    addTearDown(agent.close);
    agent.onWrite = (_) async {};
    final closed = agent.client.updates.drain<void>();
    final result = expectLater(
      agent.client.initialize(),
      throwsA(_failure(AcpFailure.closed)),
    );
    await agent.client.dispose();
    await result;
    await closed;
  });

  test(
    'authentication uses only explicitly selected advertised method',
    () async {
      final agent = _Agent();
      addTearDown(agent.close);
      agent.onWrite = (request) async => agent.reply(
        request,
        request['method'] == 'initialize'
            ? {
                'protocolVersion': 1,
                'agentCapabilities': {},
                'authMethods': [
                  {'id': 'login', 'name': 'Log in'},
                ],
              }
            : {},
      );
      await agent.client.initialize();
      expect(agent.outgoing.length, 1);
      await expectLater(
        agent.client.authenticate('other'),
        throwsA(_failure(AcpFailure.state)),
      );
      await agent.client.authenticate('login');
      expect(agent.outgoing.last['params'], {'methodId': 'login'});
    },
  );

  test(
    'terminal and future authentication cannot be invoked by this client',
    () async {
      final agent = _Agent();
      addTearDown(agent.close);
      agent.onWrite = (request) async => agent.reply(request, {
        'protocolVersion': 1,
        'agentCapabilities': {},
        'authMethods': [
          {'id': 'terminal-login', 'name': 'Log in', 'type': 'terminal'},
          {'id': 'future-login', 'name': 'Log in', 'type': 'future'},
        ],
      });
      await agent.client.initialize();
      await expectLater(
        agent.client.authenticate('terminal-login'),
        throwsA(_failure(AcpFailure.state)),
      );
      await expectLater(
        agent.client.authenticate('future-login'),
        throwsA(_failure(AcpFailure.state)),
      );
      expect(agent.outgoing.length, 1);
    },
  );

  test(
    'unsupported request flood closes instead of growing write queue',
    () async {
      final agent = _Agent();
      addTearDown(agent.close);
      await agent.client.initialize();
      final blockedWrite = Completer<void>();
      agent.onWrite = (_) => blockedWrite.future;
      final closed = agent.client.updates.drain<void>();
      for (var id = 0; id < 65; id++) {
        agent.emit({
          'jsonrpc': '2.0',
          'id': 'unsupported-$id',
          'method': 'future/method',
          'params': {},
        });
      }
      await closed;
      blockedWrite.complete();
      await expectLater(
        agent.client.newSession(cwd: '/fake'),
        throwsA(_failure(AcpFailure.closed)),
      );
    },
  );
}
