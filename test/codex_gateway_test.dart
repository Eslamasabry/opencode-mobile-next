import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/codex/mappers.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;

class _Socket implements CodexSocket {
  final input = StreamController<Object?>.broadcast(sync: true);
  final sent = <Map<String, dynamic>>[];
  void Function(Map<String, dynamic>)? handler;
  bool closed = false;
  @override
  Stream<Object?> get messages => input.stream;
  @override
  void send(String message) {
    if (closed) throw StateError('closed');
    final json = jsonDecode(message) as Map<String, dynamic>;
    sent.add(json);
    handler?.call(json);
  }

  void result(Object id, Map<String, dynamic> value) =>
      input.add(jsonEncode({'id': id, 'result': value}));
  void event(String method, Map<String, dynamic> params, {Object? id}) =>
      input.add(jsonEncode({'method': method, 'params': params, 'id': ?id}));
  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    await input.close();
  }
}

Map<String, dynamic> _thread({
  String cwd = '/project',
  List<Object?> turns = const [],
}) => {
  'id': 'thread-1',
  'cwd': cwd,
  'name': 'Fixture thread',
  'createdAt': 100,
  'updatedAt': 101,
  'status': {'type': 'idle'},
  'turns': turns,
};
Map<String, dynamic> _turn({String status = 'completed'}) => {
  'id': 'turn-1',
  'status': status,
  'startedAt': 100,
  'completedAt': status == 'completed' ? 101 : null,
  'items': [
    {
      'id': 'user-1',
      'type': 'userMessage',
      'content': [
        {'type': 'text', 'text': 'Hello'},
      ],
    },
    {'id': 'agent-1', 'type': 'agentMessage', 'text': 'Hello back'},
  ],
};

class _Fixture {
  final socket = _Socket();
  late final transport = CodexTransport(
    endpoint: 'ws://127.0.0.1:4099',
    token: 'fixture-token',
    socketFactory: (_, _) async => socket,
  );
  late final gateway = CodexGateway(
    transport: transport,
    directory: '/project',
  );
  Map<String, dynamic> thread = _thread(turns: [_turn()]);
  String? gatedMethod;
  Completer<void>? gate;

  void gateNext(String method) {
    gatedMethod = method;
    gate = Completer<void>();
  }

  void releaseGate() {
    final pending = gate;
    gate = null;
    gatedMethod = null;
    pending?.complete();
  }

  _Fixture() {
    socket.handler = (request) {
      final id = request['id'];
      if (id == null || request['method'] == null) return;
      final result = switch (request['method']) {
        'initialize' => <String, dynamic>{'userAgent': 'codex/0.153.4'},
        'thread/list' => <String, dynamic>{
          'data': [thread],
          'nextCursor': null,
        },
        'thread/read' ||
        'thread/resume' ||
        'thread/start' => <String, dynamic>{'thread': thread},
        'turn/start' => <String, dynamic>{'turn': _turn(status: 'inProgress')},
        _ => <String, dynamic>{},
      };
      if (request['method'] == gatedMethod) {
        final pending = gate!;
        gatedMethod = null;
        unawaited(pending.future.then((_) => socket.result(id, result)));
        return;
      }
      socket.result(id, result);
    };
  }
}

class _AuthenticationRecoveryFixture {
  final first = _Socket();
  var connectionAttempts = 0;
  late final transport = CodexTransport(
    endpoint: 'wss://fixture.invalid',
    token: 'fixture-token',
    socketFactory: (_, _) async {
      if (connectionAttempts++ == 0) return first;
      throw CodexFailure(CodexFailureKind.authentication);
    },
  );
  late final gateway = CodexGateway(
    transport: transport,
    directory: '/project',
  );

  _AuthenticationRecoveryFixture() {
    first.handler = (request) {
      if (request['method'] == 'initialize') {
        first.result(request['id'], {'userAgent': 'codex/0.153.4'});
      }
    };
  }
}

class _UncertainNewThreadFixture {
  final first = _Socket();
  final replacement = _Socket();
  late final transport = CodexTransport(
    endpoint: 'wss://fixture.invalid',
    token: 'fixture-token',
    socketFactory: (_, _) async => first.closed ? replacement : first,
  );
  late final gateway = CodexGateway(
    transport: transport,
    directory: '/project',
  );
  Map<String, dynamic> thread = _thread(turns: const []);
  bool loseNextTurn = false;
  final bool failEmptyThreadReads;

  _UncertainNewThreadFixture({this.failEmptyThreadReads = false}) {
    first.handler = (request) => _respond(first, request);
    replacement.handler = (request) => _respond(replacement, request);
  }

  void _respond(_Socket socket, Map<String, dynamic> request) {
    final id = request['id'];
    final method = request['method'];
    if (id == null || method is! String) return;
    if (method == 'initialize') {
      socket.result(id, {'userAgent': 'codex/0.153.4'});
    } else if (method == 'thread/start') {
      socket.result(id, {'thread': thread});
    } else if (method == 'thread/read' || method == 'thread/resume') {
      final threadID = request['params']['threadId'];
      if (threadID == 'thread-durable') {
        socket.result(id, {
          'thread': {
            ..._thread(turns: [_turn()]),
            'id': threadID,
          },
        });
      } else if (failEmptyThreadReads && (thread['turns'] as List).isEmpty) {
        socket.input.add(
          jsonEncode({
            'id': id,
            'error': {'code': -32603, 'message': 'Synthetic read failure'},
          }),
        );
      } else {
        socket.result(id, {'thread': thread});
      }
    } else if (method == 'turn/start') {
      if (loseNextTurn) {
        loseNextTurn = false;
        if (!failEmptyThreadReads) thread = _thread(turns: [_turn()]);
        unawaited(socket.close());
      } else {
        socket.result(id, {'turn': _turn(status: 'inProgress')});
      }
    }
  }
}

Future<void> _expectReadMutationScopeRace(
  Future<void> Function(_Fixture) operation, {
  required String forbiddenMethod,
  bool activeTurn = false,
}) async {
  final fixture = _Fixture();
  fixture.thread = _thread(
    turns: activeTurn ? [_turn(status: 'inProgress')] : [_turn()],
  );
  fixture.gateNext('thread/read');
  try {
    final pending = operation(fixture);
    await pumpEventQueue(times: 3);
    expect(
      fixture.socket.sent.where(
        (request) => request['method'] == 'thread/read',
      ),
      hasLength(1),
    );
    fixture.gateway.setLocation(directory: '/replacement');
    fixture.releaseGate();
    await expectLater(
      pending,
      throwsA(
        isA<CodexFailure>().having(
          (error) => error.kind,
          'kind',
          CodexFailureKind.scopeMismatch,
        ),
      ),
    );
    expect(
      fixture.socket.sent.where(
        (request) => request['method'] == forbiddenMethod,
      ),
      isEmpty,
    );
  } finally {
    fixture.releaseGate();
    fixture.gateway.close();
  }
}

class _RecoveryFixture {
  final first = _Socket();
  final second = _Socket();
  late final transport = CodexTransport(
    endpoint: 'wss://fixture.invalid',
    token: 'fixture-token',
    socketFactory: (_, _) async => first.closed ? second : first,
  );
  late final gateway = CodexGateway(
    transport: transport,
    directory: '/project',
  );

  final String? genericFailureMethod;

  _RecoveryFixture({this.genericFailureMethod}) {
    first.handler = (request) => _respond(first, request);
    second.handler = (request) => _respond(second, request, failResume: true);
  }

  void _respond(
    _Socket socket,
    Map<String, dynamic> request, {
    bool failResume = false,
  }) {
    final id = request['id'];
    if (id == null || request['method'] == null) return;
    if (failResume && request['method'] == genericFailureMethod) {
      socket.input.add(
        jsonEncode({
          'id': id,
          'error': {'code': -32603, 'message': 'Synthetic recovery failure'},
        }),
      );
      return;
    }
    final result = switch (request['method']) {
      'initialize' => <String, dynamic>{'userAgent': 'codex/0.153.4'},
      'thread/read' => <String, dynamic>{
        'thread': _thread(turns: [_turn()]),
      },
      'thread/resume' => <String, dynamic>{
        'thread': failResume && genericFailureMethod == null
            ? {..._thread(), 'id': 'other-thread'}
            : _thread(turns: [_turn()]),
      },
      _ => <String, dynamic>{},
    };
    socket.result(id, result);
  }
}

void main() {
  test('Codex capabilities hide unsupported product operations', () {
    final capabilities = codexServerCapabilities;
    expect([
      capabilities.promptAttachments,
      capabilities.promptAgentMentions,
      capabilities.offlinePromptQueue,
      capabilities.fileBrowsing,
      capabilities.terminal,
      capabilities.projectManagement,
      capabilities.globalSessionSearch,
      capabilities.sessionDiff,
      capabilities.sessionFork,
      capabilities.sessionRevert,
      capabilities.sessionImportExport,
      capabilities.sessionNotes,
      capabilities.serverCatalog,
      capabilities.profileAttentionPolling,
    ], everyElement(isFalse));
  });

  test('endpoint policy rejects remote cleartext and URL credentials', () {
    for (final url in [
      'ws://192.168.1.8:4099',
      'wss://user:secret@example.com',
      'wss://example.com?token=secret',
      'wss://example.com/path',
      'https://example.com',
    ]) {
      expect(() => codexEndpoint(url), throwsA(isA<CodexFailure>()));
    }
    expect(codexEndpoint('ws://[::1]:4099').host, '::1');
    expect(codexEndpoint('wss://example.com').scheme, 'wss');
  });

  test(
    'bearer upgrade refuses redirects without forwarding credentials',
    () async {
      final target = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      var forwarded = false;
      final targetSub = target.listen((request) async {
        forwarded = true;
        request.response.statusCode = 401;
        await request.response.close();
      });
      final source = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final sourceSub = source.listen((request) async {
        expect(
          request.headers.value(HttpHeaders.authorizationHeader),
          'Bearer fixture-token',
        );
        request.response.statusCode = 302;
        request.response.headers.set(
          HttpHeaders.locationHeader,
          'http://127.0.0.1:${target.port}',
        );
        await request.response.close();
      });
      try {
        await expectLater(
          connectCodexSocket(
            Uri.parse('ws://127.0.0.1:${source.port}'),
            'fixture-token',
          ),
          throwsA(isA<CodexFailure>()),
        );
        expect(forwarded, isFalse);
      } finally {
        await sourceSub.cancel();
        await targetSub.cancel();
        await source.close(force: true);
        await target.close(force: true);
      }
    },
  );

  test(
    'real local upgrade sends one frame and receives a bound reply',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      WebSocket? accepted;
      final subscription = server.listen((request) async {
        expect(
          request.headers.value(HttpHeaders.authorizationHeader),
          'Bearer fixture-token',
        );
        final socket = await WebSocketTransformer.upgrade(request);
        accepted = socket;
        socket.listen((message) => socket.add(message));
      });
      CodexSocket? socket;
      try {
        socket = await connectCodexSocket(
          Uri.parse('ws://127.0.0.1:${server.port}'),
          'fixture-token',
        );
        final reply = socket.messages.first;
        socket.send('{"id":1,"result":{}}');
        expect(await reply, '{"id":1,"result":{}}');
      } finally {
        await socket?.close();
        await accepted?.close();
        await subscription.cancel();
        await server.close(force: true);
      }
    },
  );

  test('lost mutation response is uncertain and never retried', () async {
    final socket = _Socket();
    socket.handler = (request) {
      if (request['method'] == 'initialize') socket.result(request['id'], {});
      if (request['method'] == 'turn/start') unawaited(socket.close());
    };
    final transport = CodexTransport(
      endpoint: 'wss://fixture.invalid',
      token: 'fixture',
      socketFactory: (_, _) async => socket,
    );
    try {
      await expectLater(
        transport.request('turn/start', {
          'threadId': 'thread-1',
        }, mutation: true),
        throwsA(
          isA<CodexFailure>().having(
            (e) => e.kind,
            'kind',
            CodexFailureKind.deliveryUnknown,
          ),
        ),
      );
      expect(
        socket.sent.where((r) => r['method'] == 'turn/start'),
        hasLength(1),
      );
    } finally {
      await transport.close();
    }
  });

  test('server errors never expose a remote body', () async {
    final socket = _Socket();
    socket.handler = (request) {
      if (request['method'] == 'initialize') socket.result(request['id'], {});
      if (request['method'] == 'thread/list') {
        socket.input.add(
          jsonEncode({
            'id': request['id'],
            'error': {'code': -32600, 'message': 'fixture-secret-token'},
          }),
        );
      }
    };
    final transport = CodexTransport(
      endpoint: 'wss://fixture.invalid',
      token: 'fixture',
      socketFactory: (_, _) async => socket,
    );
    try {
      await expectLater(
        transport.request('thread/list', {}),
        throwsA(
          isA<CodexFailure>().having(
            (e) => e.toString().contains('fixture-secret'),
            'secret excluded',
            false,
          ),
        ),
      );
    } finally {
      await transport.close();
    }
  });

  test(
    'foreign project threads cannot enter a scoped list or send path',
    () async {
      final fixture = _Fixture()..thread = _thread(cwd: '/another-project');
      try {
        await expectLater(
          fixture.gateway.sessions(),
          throwsA(
            isA<CodexFailure>().having(
              (e) => e.kind,
              'kind',
              CodexFailureKind.scopeMismatch,
            ),
          ),
        );
        await expectLater(
          fixture.gateway.promptAsync('thread-1', text: 'Hi'),
          throwsA(isA<CodexFailure>()),
        );
        expect(
          fixture.socket.sent.where((r) => r['method'] == 'turn/start'),
          isEmpty,
        );
      } finally {
        fixture.gateway.close();
      }
    },
  );

  test('resume preserves item IDs, timestamps and cancelled outcome', () {
    final thread = _thread(turns: [_turn(status: 'interrupted')]);
    final messages = codexMessages(thread);
    expect(messages.map((m) => m.info.id), ['user-1', 'agent-1']);
    expect(messages.last.parts.single.text, 'Hello back');
    expect(messages.last.info.time?.created, 100000);
    expect(messages.last.info.finish, 'cancelled');
  });

  test(
    'approval requires matching scope and permits only one-shot decisions',
    () async {
      final fixture = _Fixture();
      try {
        await fixture.gateway.messages('thread-1');
        fixture.socket.event('turn/started', {
          'threadId': 'thread-1',
          'turn': _turn(status: 'inProgress'),
        });
        fixture.socket.event('item/commandExecution/requestApproval', {
          'threadId': 'thread-1',
          'turnId': 'turn-1',
          'itemId': 'command-1',
          'command': 'echo fixture',
          'startedAtMs': 1000,
        }, id: 9);
        final request = (await fixture.gateway.pendingPermissions()).single;
        expect(request.commandPreview, 'echo fixture');
        await expectLater(
          fixture.gateway.respondPermission(request.id, 'always'),
          throwsA(isA<CodexFailure>()),
        );
        await fixture.gateway.respondPermission(
          request.id,
          'once',
          legacySessionID: 'thread-1',
        );
        expect(fixture.socket.sent.last, {
          'id': 9,
          'result': {'decision': 'accept'},
        });
        await expectLater(
          fixture.gateway.respondPermission(request.id, 'once'),
          throwsA(isA<CodexFailure>()),
        );
      } finally {
        fixture.gateway.close();
      }
    },
  );

  test('location changes invalidate a previously displayed approval', () async {
    final fixture = _Fixture();
    try {
      await fixture.gateway.messages('thread-1');
      fixture.socket.event('turn/started', {
        'threadId': 'thread-1',
        'turn': _turn(status: 'inProgress'),
      });
      fixture.socket.event('item/commandExecution/requestApproval', {
        'threadId': 'thread-1',
        'turnId': 'turn-1',
        'itemId': 'command-1',
        'command': 'echo fixture',
      }, id: 7);
      final request = (await fixture.gateway.pendingPermissions()).single;
      fixture.gateway.setLocation(directory: '/new-project');
      await expectLater(
        fixture.gateway.respondPermission(request.id, 'once'),
        throwsA(isA<CodexFailure>()),
      );
      expect(await fixture.gateway.pendingPermissions(), isEmpty);
      expect(
        fixture.socket.sent.where(
          (r) => r['id'] == 7 && r.containsKey('result'),
        ),
        isEmpty,
      );
    } finally {
      fixture.gateway.close();
    }
  });

  test('socket loss invalidates a previously displayed approval', () async {
    final fixture = _Fixture();
    try {
      await fixture.gateway.messages('thread-1');
      fixture.socket.event('turn/started', {
        'threadId': 'thread-1',
        'turn': _turn(status: 'inProgress'),
      });
      fixture.socket.event('item/commandExecution/requestApproval', {
        'threadId': 'thread-1',
        'turnId': 'turn-1',
        'itemId': 'command-1',
        'command': 'echo fixture',
      }, id: 8);
      final request = (await fixture.gateway.pendingPermissions()).single;
      await fixture.socket.close();
      await pumpEventQueue(times: 5);

      await expectLater(
        fixture.gateway.respondPermission(request.id, 'once'),
        throwsA(
          isA<CodexFailure>().having(
            (error) => error.kind,
            'kind',
            CodexFailureKind.staleRequest,
          ),
        ),
      );
      expect(await fixture.gateway.pendingPermissions(), isEmpty);
    } finally {
      fixture.gateway.close();
    }
  });

  test(
    'streamed assistant item uses the same IDs as authoritative history',
    () async {
      final fixture = _Fixture();
      final events = <String, List<Map<String, dynamic>>>{};
      final channel = fixture.gateway.openEventChannel(
        onEvent: (e) {
          events.putIfAbsent(e.type, () => []).add(e.properties);
        },
        onStatus: (_) {},
      );
      try {
        await fixture.gateway.messages('thread-1');
        fixture.socket.event('turn/started', {
          'threadId': 'thread-1',
          'turn': _turn(status: 'inProgress'),
        });
        fixture.socket.event('item/started', {
          'threadId': 'thread-1',
          'turnId': 'turn-1',
          'item': {'id': 'agent-1', 'type': 'agentMessage', 'text': ''},
        });
        fixture.socket.event('item/agentMessage/delta', {
          'threadId': 'thread-1',
          'turnId': 'turn-1',
          'itemId': 'agent-1',
          'delta': 'Hello back',
        });
        expect(events['message.updated']!.single['info']['id'], 'agent-1');
        expect(events['message.part.delta']!.single['partID'], 'agent-1:0');
        expect(
          (await fixture.gateway.messages('thread-1')).last.parts.single.id,
          'agent-1:0',
        );
      } finally {
        await channel.dispose();
        fixture.gateway.close();
      }
    },
  );

  test(
    'partial reconnect remains degraded when a tracked thread cannot resume',
    () async {
      final fixture = _RecoveryFixture();
      final statuses = <StreamStatus>[];
      final channel = fixture.gateway.openEventChannel(
        onEvent: (_) {},
        onStatus: statuses.add,
      );
      try {
        channel.start();
        await pumpEventQueue(times: 5);
        await fixture.gateway.messages('thread-1');
        await fixture.first.close();
        await Future<void>.delayed(const Duration(milliseconds: 1100));

        expect(statuses, contains(StreamStatus.reconnecting));
        expect(
          statuses.where((status) => status == StreamStatus.connected),
          hasLength(1),
        );
        expect(statuses.last, StreamStatus.reconnecting);
      } finally {
        await channel.dispose();
        fixture.gateway.close();
      }
    },
  );

  for (final method in ['thread/read', 'thread/resume']) {
    test('generic $method failure keeps reconnect degraded', () async {
      final fixture = _RecoveryFixture(genericFailureMethod: method);
      final statuses = <StreamStatus>[];
      final channel = fixture.gateway.openEventChannel(
        onEvent: (_) {},
        onStatus: statuses.add,
      );
      try {
        channel.start();
        await pumpEventQueue(times: 5);
        await fixture.gateway.messages('thread-1');
        await fixture.first.close();
        await Future<void>.delayed(const Duration(milliseconds: 1100));
        await pumpEventQueue(times: 5);

        expect(fixture.transport.connected, isTrue);
        expect(
          fixture.second.sent.where((request) => request['method'] == method),
          hasLength(1),
        );
        expect(statuses.last, StreamStatus.reconnecting);
        expect(
          statuses.where((status) => status == StreamStatus.connected),
          hasLength(1),
        );
        expect(
          fixture.second.sent.where(
            (request) =>
                request['method'] == 'thread/start' ||
                request['method'] == 'turn/start',
          ),
          isEmpty,
        );
      } finally {
        await channel.dispose();
        fixture.gateway.close();
      }
    });
  }

  test('read to resume never crosses a location generation', () async {
    await _expectReadMutationScopeRace(
      (fixture) => fixture.gateway.messages('thread-1').then<void>((_) {}),
      forbiddenMethod: 'thread/resume',
    );
  });

  for (final uncertainFirstTurn in [false, true]) {
    test(
      uncertainFirstTurn
          ? 'an unavailable uncertain first turn still blocks reconnect recovery'
          : 'a never-dispatched empty thread retires before automatic recovery RPCs',
      () async {
        final fixture = _UncertainNewThreadFixture(failEmptyThreadReads: true);
        final statuses = <StreamStatus>[];
        final events = <String>[];
        final channel = fixture.gateway.openEventChannel(
          onEvent: (event) => events.add(event.type),
          onStatus: statuses.add,
        );
        try {
          channel.start();
          await pumpEventQueue(times: 5);
          await fixture.gateway.messages('thread-durable');
          final created = await fixture.gateway.createSession();

          if (uncertainFirstTurn) {
            fixture.loseNextTurn = true;
            await expectLater(
              fixture.gateway.promptAsync(created.id, text: 'First attempt'),
              throwsA(
                isA<CodexFailure>().having(
                  (error) => error.kind,
                  'kind',
                  CodexFailureKind.deliveryUnknown,
                ),
              ),
            );
          } else {
            await fixture.first.close();
          }
          await Future<void>.delayed(const Duration(milliseconds: 1100));
          await pumpEventQueue(times: 5);

          expect(
            statuses.last,
            uncertainFirstTurn
                ? StreamStatus.reconnecting
                : StreamStatus.connected,
          );
          expect(
            fixture.replacement.sent.where(
              (request) =>
                  request['method'] == 'thread/resume' &&
                  request['params']['threadId'] == 'thread-durable',
            ),
            hasLength(1),
          );
          expect(
            fixture.replacement.sent.where(
              (request) =>
                  request['method'] == 'thread/read' &&
                  request['params']['threadId'] == created.id,
            ),
            hasLength(uncertainFirstTurn ? 1 : 0),
          );
          expect(
            fixture.replacement.sent.where(
              (request) =>
                  request['method'] == 'thread/resume' &&
                  request['params']['threadId'] == created.id,
            ),
            isEmpty,
          );
          expect(
            fixture.replacement.sent.where(
              (request) =>
                  request['method'] == 'thread/start' ||
                  request['method'] == 'turn/start',
            ),
            isEmpty,
          );
          // Recovery must not emit deletion or mutate the session identity
          // used by the controller's separately persisted local draft.
          expect(events, isNot(contains('session.deleted')));
          expect(await fixture.gateway.sessionStatuses(), contains(created.id));

          if (!uncertainFirstTurn) {
            // Retired metadata must not satisfy explicit details/history
            // through the old empty-thread shortcut.
            for (final access in [
              () => fixture.gateway.session(created.id),
              () => fixture.gateway.messages(created.id),
            ]) {
              final before = fixture.replacement.sent.length;
              await expectLater(access(), throwsA(isA<CodexFailure>()));
              expect(fixture.replacement.sent, hasLength(before + 1));
              expect(fixture.replacement.sent.last['method'], 'thread/read');
            }
          }

          final readsBeforeExplicitSend = fixture.replacement.sent
              .where((request) => request['method'] == 'thread/read')
              .length;
          await expectLater(
            fixture.gateway.promptAsync(created.id, text: 'Explicit retry'),
            throwsA(
              isA<CodexFailure>().having(
                (error) => error.kind,
                'kind',
                uncertainFirstTurn
                    ? CodexFailureKind.deliveryUnknown
                    : CodexFailureKind.unavailable,
              ),
            ),
          );
          expect(
            fixture.replacement.sent
                .where((request) => request['method'] == 'thread/read')
                .length,
            readsBeforeExplicitSend + (uncertainFirstTurn ? 0 : 1),
          );
          expect(
            fixture.replacement.sent.where(
              (request) => request['method'] == 'turn/start',
            ),
            isEmpty,
          );
        } finally {
          await channel.dispose();
          fixture.gateway.close();
        }
      },
    );
  }

  test('read to delete never crosses a location generation', () async {
    await _expectReadMutationScopeRace(
      (fixture) => fixture.gateway.deleteSession('thread-1'),
      forbiddenMethod: 'thread/delete',
    );
  });

  test('read to rename never crosses a location generation', () async {
    await _expectReadMutationScopeRace(
      (fixture) => fixture.gateway.renameSession('thread-1', 'renamed'),
      forbiddenMethod: 'thread/name/set',
    );
  });

  test('read to abort never crosses a location generation', () async {
    await _expectReadMutationScopeRace(
      (fixture) => fixture.gateway.abort('thread-1'),
      forbiddenMethod: 'turn/interrupt',
      activeTurn: true,
    );
  });

  test(
    'reconnect authentication failure reaches the event error channel',
    () async {
      final fixture = _AuthenticationRecoveryFixture();
      final errors = <Object>[];
      final statuses = <StreamStatus>[];
      final channel = fixture.gateway.openEventChannel(
        onEvent: (_) {},
        onStatus: statuses.add,
        onError: errors.add,
      );
      try {
        channel.start();
        await pumpEventQueue(times: 5);
        expect(fixture.transport.connected, isTrue);
        await fixture.first.close();
        await Future<void>.delayed(const Duration(milliseconds: 1100));
        expect(errors, hasLength(1));
        expect(errors.single, isA<CodexFailure>());
        expect(
          (errors.single as CodexFailure).kind,
          CodexFailureKind.authentication,
        );
        expect(statuses, contains(StreamStatus.disconnected));
      } finally {
        await channel.dispose();
        fixture.gateway.close();
      }
    },
  );

  test(
    'uncertain first turn on a new thread requires history recovery without resend',
    () async {
      final fixture = _UncertainNewThreadFixture();
      try {
        final created = await fixture.gateway.createSession();
        fixture.loseNextTurn = true;
        await expectLater(
          fixture.gateway.promptAsync(created.id, text: 'first attempt'),
          throwsA(
            isA<CodexFailure>().having(
              (error) => error.kind,
              'kind',
              CodexFailureKind.deliveryUnknown,
            ),
          ),
        );
        expect(
          fixture.first.sent.where(
            (request) => request['method'] == 'turn/start',
          ),
          hasLength(1),
        );

        final recovered = await fixture.gateway.messages(created.id);
        expect(recovered, isNotEmpty);
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'thread/read',
          ),
          hasLength(1),
        );
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'thread/resume',
          ),
          hasLength(1),
        );
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'turn/start',
          ),
          isEmpty,
        );

        await fixture.gateway.promptAsync(created.id, text: 'after recovery');
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'turn/start',
          ),
          hasLength(1),
        );
      } finally {
        fixture.gateway.close();
      }
    },
  );
}
