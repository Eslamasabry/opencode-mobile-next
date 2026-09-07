import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;

class _Socket implements CodexSocket {
  final input = StreamController<Object?>.broadcast(sync: true);
  final sent = <Map<String, dynamic>>[];
  void Function(Map<String, dynamic>)? onSend;
  bool closed = false;

  @override
  Stream<Object?> get messages => input.stream;

  @override
  void send(String message) {
    if (closed) throw StateError('closed');
    final frame = jsonDecode(message) as Map<String, dynamic>;
    sent.add(frame);
    onSend?.call(frame);
  }

  void result(Object id, Map<String, dynamic> value) {
    input.add(jsonEncode({'id': id, 'result': value}));
  }

  void event(String method, Map<String, dynamic> params, {Object? id}) {
    input.add(jsonEncode({'method': method, 'params': params, 'id': ?id}));
  }

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    await input.close();
  }
}

Map<String, dynamic> _turn({required String id, String status = 'completed'}) =>
    {
      'id': id,
      'status': status,
      'startedAt': 100,
      'completedAt': status == 'completed' ? 101 : null,
      'items': [
        {
          'id': '$id-user',
          'type': 'userMessage',
          'content': [
            {'type': 'text', 'text': 'Saved history'},
          ],
        },
        {'id': '$id-agent', 'type': 'agentMessage', 'text': 'Authoritative'},
      ],
    };

Map<String, dynamic> _thread(
  String id, {
  List<Object?> turns = const [],
  String name = 'Journey thread',
}) => {
  'id': id,
  'cwd': '/project',
  'name': name,
  'createdAt': 100,
  'updatedAt': 101,
  'status': {'type': 'idle'},
  'turns': turns,
};

class _JourneyFixture {
  final first = _Socket();
  final replacement = _Socket();
  late final transport = CodexTransport(
    endpoint: 'wss://fixture.invalid',
    token: 'journey-token',
    socketFactory: (_, _) async => first.closed ? replacement : first,
  );
  late final gateway = CodexGateway(
    transport: transport,
    directory: '/project',
  );
  final threads = <String, Map<String, dynamic>>{
    'thread-existing': _thread(
      'thread-existing',
      turns: [_turn(id: 'turn-history')],
      name: 'Existing thread',
    ),
    'thread-new': _thread('thread-new', name: 'New thread'),
  };
  bool failNextTurn = false;

  _JourneyFixture() {
    first.onSend = (request) => _respond(first, request);
    replacement.onSend = (request) => _respond(replacement, request);
  }

  void _respond(_Socket socket, Map<String, dynamic> request) {
    final id = request['id'];
    final method = request['method'];
    if (id == null || method is! String) return;
    switch (method) {
      case 'initialize':
        socket.result(id, {'userAgent': 'codex/0.153.4'});
      case 'thread/read':
      case 'thread/resume':
        final threadID = request['params']['threadId'] as String;
        socket.result(id, {'thread': threads[threadID]!});
      case 'thread/start':
        socket.result(id, {'thread': threads['thread-new']!});
      case 'turn/start':
        final threadID = request['params']['threadId'] as String;
        if (failNextTurn) {
          failNextTurn = false;
          unawaited(socket.close());
          return;
        }
        final turnID = threadID == 'thread-new' ? 'turn-new' : 'turn-retry';
        socket.result(id, {'turn': _turn(id: turnID, status: 'inProgress')});
      case 'turn/interrupt':
        socket.result(id, const {});
      default:
        socket.result(id, const {});
    }
  }

  void close() => gateway.close();
}

Future<void> _settle() => pumpEventQueue(times: 5);

void main() {
  test(
    'connects, sends existing and new prompts, streams, approves, aborts, and recovers authoritatively',
    () async {
      final fixture = _JourneyFixture();
      final statuses = <StreamStatus>[];
      final events = <EventEnvelope>[];
      final channel = fixture.gateway.openEventChannel(
        onEvent: events.add,
        onStatus: statuses.add,
      );
      try {
        channel.start();
        await _settle();
        expect(fixture.transport.connected, isTrue);
        expect(statuses, contains(StreamStatus.connected));

        final existing = await fixture.gateway.messages('thread-existing');
        expect(existing.last.info.id, 'turn-history-agent');

        await fixture.gateway.promptAsync(
          'thread-existing',
          text: 'Inspect the existing thread',
        );
        final existingSend = fixture.first.sent.lastWhere(
          (request) => request['method'] == 'turn/start',
        );
        expect(existingSend['params']['threadId'], 'thread-existing');
        expect(
          existingSend['params']['input'][0]['text'],
          'Inspect the existing thread',
        );
        expect(
          (await fixture.gateway.sessionStatuses())['thread-existing'],
          'busy',
        );

        fixture.first.event('turn/started', {
          'threadId': 'thread-existing',
          'turn': _turn(id: 'turn-retry', status: 'inProgress'),
        });
        fixture.first.event('item/agentMessage/delta', {
          'threadId': 'thread-existing',
          'turnId': 'turn-retry',
          'itemId': 'turn-retry-agent',
          'delta': 'streamed answer',
        });
        fixture.first.event('turn/completed', {
          'threadId': 'thread-existing',
          'turn': _turn(id: 'turn-retry'),
        });
        await _settle();
        expect(
          events.where((event) => event.type == 'message.part.delta'),
          hasLength(1),
        );
        expect(
          (await fixture.gateway.sessionStatuses())['thread-existing'],
          'idle',
        );
        expect(
          events.where((event) => event.type == 'session.idle'),
          hasLength(1),
        );

        fixture.first.event('turn/started', {
          'threadId': 'thread-existing',
          'turn': _turn(id: 'turn-retry', status: 'inProgress'),
        });
        fixture.first.event('item/commandExecution/requestApproval', {
          'threadId': 'thread-existing',
          'turnId': 'turn-retry',
          'itemId': 'command-1',
          'command': 'echo approved',
        }, id: 41);
        final rejected = (await fixture.gateway.pendingPermissions()).single;
        await fixture.gateway.respondPermission(
          rejected.id,
          'reject',
          legacySessionID: 'thread-existing',
        );
        expect(fixture.first.sent.last, {
          'id': 41,
          'result': {'decision': 'decline'},
        });

        fixture.first.event('item/commandExecution/requestApproval', {
          'threadId': 'thread-existing',
          'turnId': 'turn-retry',
          'itemId': 'command-2',
          'command': 'echo once',
        }, id: 42);
        final approved = (await fixture.gateway.pendingPermissions()).single;
        await fixture.gateway.respondPermission(approved.id, 'once');
        expect(fixture.first.sent.last, {
          'id': 42,
          'result': {'decision': 'accept'},
        });

        fixture.threads['thread-existing'] = _thread(
          'thread-existing',
          turns: [_turn(id: 'turn-abort', status: 'inProgress')],
          name: 'Existing thread',
        );
        await fixture.gateway.abort('thread-existing');
        final interrupt = fixture.first.sent.lastWhere(
          (request) => request['method'] == 'turn/interrupt',
        );
        expect(interrupt['params'], {
          'threadId': 'thread-existing',
          'turnId': 'turn-abort',
        });
        fixture.threads['thread-existing'] = _thread(
          'thread-existing',
          turns: [_turn(id: 'turn-history')],
          name: 'Existing thread',
        );

        final created = await fixture.gateway.createSession();
        expect(created.id, 'thread-new');
        await fixture.gateway.promptAsync('thread-new', text: 'Start new work');
        final newSend = fixture.first.sent.lastWhere(
          (request) => request['method'] == 'turn/start',
        );
        expect(newSend['params']['threadId'], 'thread-new');
        expect(newSend['params']['input'][0]['text'], 'Start new work');

        fixture.failNextTurn = true;
        await expectLater(
          fixture.gateway.promptAsync(
            'thread-existing',
            text: 'Delivery may be uncertain',
          ),
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
          hasLength(3),
        );

        await Future<void>.delayed(const Duration(milliseconds: 1100));
        await _settle();
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'initialize',
          ),
          hasLength(1),
        );
        final replacementReads = fixture.replacement.sent
            .where((request) => request['method'] == 'thread/read')
            .toList();
        final replacementResumes = fixture.replacement.sent
            .where((request) => request['method'] == 'thread/resume')
            .toList();
        expect(replacementReads, hasLength(2));
        expect(replacementResumes, hasLength(2));
        expect(
          replacementResumes.map((request) => request['params']['threadId']),
          containsAll(['thread-existing', 'thread-new']),
        );
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'turn/start',
          ),
          isEmpty,
        );

        final authoritative = await fixture.gateway.messages('thread-existing');
        expect(authoritative.last.info.id, 'turn-history-agent');
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'turn/start',
          ),
          isEmpty,
        );
        await fixture.gateway.promptAsync(
          'thread-existing',
          text: 'Retry after history refresh',
        );
        expect(
          fixture.replacement.sent.where(
            (request) => request['method'] == 'turn/start',
          ),
          hasLength(1),
        );
      } finally {
        await channel.dispose();
        fixture.close();
      }
    },
  );
}
