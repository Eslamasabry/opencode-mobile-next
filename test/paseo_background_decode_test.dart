// A whole Paseo history (one frame of up to 8 MB) is decoded and mapped on a
// background isolate so opening a long chat does not freeze the UI
// (docs/qa/json-decode-2026-10-28). These tests pin that the result, the
// order of messages and the failures are the same as inline.
import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

/// A socket that answers `hello` and lets a test push any text frame.
class _RawSocket implements PaseoSocket {
  final _incoming = StreamController<Object?>();
  final sent = <Map<String, dynamic>>[];

  @override
  int? closeCode;

  @override
  Stream<Object?> get messages => _incoming.stream;

  @override
  void send(String message) {
    final json = jsonDecode(message) as Map<String, dynamic>;
    if (json['type'] == 'hello') {
      push(_frame('status', {'status': 'server_info', 'version': '0.8.0'}));
    } else if (json['type'] == 'session') {
      sent.add(json['message'] as Map<String, dynamic>);
    }
  }

  void push(String frame) => _incoming.add(frame);

  @override
  Future<void> close() async {
    if (!_incoming.isClosed) await _incoming.close();
  }
}

String _frame(String type, Map<String, dynamic> payload) => jsonEncode({
  'type': 'session',
  'message': {'type': type, 'payload': payload},
});

/// [turns] turns of a Claude Code history with every common item kind and
/// tool outputs long enough to pass the background thresholds.
Map<String, dynamic> _timeline(int turns) {
  final entries = <Map<String, dynamic>>[];
  var seq = 0;
  void add(Map<String, dynamic> item) => entries.add({
    'provider': 'claude',
    'item': item,
    'timestamp': DateTime.utc(2026, 10, 1, 0, 0, seq).toIso8601String(),
    'seqStart': ++seq,
  });
  for (var t = 0; t < turns; t++) {
    add({'type': 'user_message', 'text': 'Prompt $t ✓', 'messageId': 'u$t'});
    add({'type': 'reasoning', 'text': 'Thinking about "$t"\n\tnext'});
    add({
      'type': 'tool_call',
      'callId': 'sh$t',
      'name': 'Bash',
      'status': 'completed',
      'detail': {
        'type': 'shell',
        'command': 'flutter test',
        'output': 'line $t\n' * 120,
        'exitCode': t.isEven ? 0 : 1,
      },
    });
    add({
      'type': 'tool_call',
      'callId': 'ed$t',
      'name': 'Edit',
      'status': 'completed',
      'detail': {
        'type': 'edit',
        'filePath': '/work/app/lib/a$t.dart',
        'oldString': 'old $t',
        'newString': 'new $t',
      },
    });
    add({
      'type': 'tool_call',
      'callId': 'gr$t',
      'name': 'Grep',
      'status': 'completed',
      'detail': {'type': 'search', 'pattern': 'retry', 'content': 'a\nb'},
    });
    add({
      'type': 'assistant_message',
      'text': 'Done $t — مرحبا',
      'messageId': 'm$t',
    });
  }
  return {
    'agentId': 'a1',
    'epoch': 'e1',
    'endCursor': {'epoch': 'e1', 'seq': seq},
    'hasOlder': false,
    'hasNewer': false,
    'entries': entries,
  };
}

List<Object?> _summary(List<MessageWithParts> messages) => [
  for (final m in messages)
    {
      'info': paseoMessageJson(m.info),
      'parts': [for (final p in m.parts) paseoPartJson(p)],
    },
];

void main() {
  group('long timeline mapping', () {
    test('maps to the same messages and tool calls as inline', () async {
      final payload = _timeline(40);
      expect(
        (payload['entries'] as List).length,
        greaterThanOrEqualTo(paseoBackgroundTimelineEntries),
      );
      final inlineTools = <String, Map<String, dynamic>>{};
      final inline = paseoTimelineMessages(
        'a1',
        payload,
        busy: true,
        toolCalls: inlineTools,
      );
      final (messages, tools) = await paseoTimelineReply(
        'a1',
        payload,
        busy: true,
      );
      expect(messages, hasLength(inline.length));
      expect(jsonEncode(_summary(messages)), jsonEncode(_summary(inline)));
      expect(jsonEncode(tools), jsonEncode(inlineTools));
    });

    test('a payload that does not map fails as it does inline', () async {
      final payload = {'entries': List.filled(20001, <String, dynamic>{})};
      PaseoFailure? inline;
      try {
        paseoReply(
          'fetch_agent_timeline_response',
          () => paseoTimelineMessages('a1', payload, busy: false),
        );
      } on PaseoFailure catch (error) {
        inline = error;
      }
      expect(inline, isNotNull);
      await expectLater(
        paseoTimelineReply('a1', payload, busy: false),
        throwsA(
          isA<PaseoFailure>()
              .having((e) => e.kind, 'kind', inline!.kind)
              .having((e) => e.reason, 'reason', inline.reason),
        ),
      );
    });
  });

  group('long frames', () {
    late _RawSocket socket;
    late PaseoTransport transport;

    setUp(() async {
      socket = _RawSocket();
      transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => socket,
      );
      await transport.connect();
    });

    tearDown(() => transport.close());

    test('answer with the same payload and keep arrival order', () async {
      final events = <String>[];
      transport.events.listen((e) => events.add(e.type));
      final reply = transport.request('fetch_agent_timeline_request', {
        'agentId': 'a1',
      });
      await pumpEventQueue();
      final id = socket.sent.single['requestId'];
      final payload = {..._timeline(40), 'requestId': id};
      final big = _frame('fetch_agent_timeline_response', payload);
      expect(big.length, greaterThan(PaseoTransport.backgroundDecodeChars));
      final bigEvent = _frame('agent_stream', {
        'text': 'x' * PaseoTransport.backgroundDecodeChars,
      });
      // A long push, a short one, the long answer, then a short push: all
      // handled in the order they arrived.
      socket
        ..push(bigEvent)
        ..push(_frame('agent_update', {'n': 1}))
        ..push(big)
        ..push(_frame('agent_deleted', {'n': 2}));
      expect(
        jsonEncode(await reply),
        jsonEncode(jsonDecode(big)['message']['payload']),
      );
      await pumpEventQueue();
      expect(events, ['agent_stream', 'agent_update', 'agent_deleted']);
    });

    test('malformed JSON drops the connection as a short frame does', () async {
      final lost = transport.disconnects.first;
      socket.push('{"type": "session", ${'x' * 70000}');
      await lost.timeout(const Duration(seconds: 5));
      expect(transport.connected, isFalse);
    });

    test('messages wait while a long reply is processed', () async {
      final events = <String>[];
      transport.events.listen((e) => events.add(e.type));
      final gate = Completer<void>();
      final held = transport.holdingMessages(() async {
        await gate.future;
        events.add('history');
      });
      socket.push(_frame('agent_update', {}));
      await pumpEventQueue();
      expect(events, isEmpty);
      gate.complete();
      await held;
      await pumpEventQueue();
      expect(events, ['history', 'agent_update']);
    });
  });

  test(
    'a long history opens with the same messages through the gateway',
    () async {
      final daemon = FakeDaemon();
      final payload = _timeline(40);
      daemon.handlers['fetch_agent_request'] = (_) =>
          ('fetch_agent_response', {'agent': agentJson('a1')});
      daemon.handlers['fetch_agent_timeline_request'] = (_) =>
          ('fetch_agent_timeline_response', payload);
      daemon.handlers['agent.provider_subagents.list.request'] = (_) =>
          ('agent.provider_subagents.list.response', {'subagents': <Object>[]});
      final gateway = PaseoGateway(
        transport: PaseoTransport(
          endpoint: 'ws://127.0.0.1:6767',
          socketFactory: (_, _) async => daemon,
        ),
        directory: '/work/app',
      );
      addTearDown(gateway.close);
      final messages = await gateway.messages('a1');
      final inline = paseoTimelineMessages('a1', payload, busy: false);
      expect(
        messages.map((m) => (m.info.id, m.parts.map((p) => p.text).join())),
        inline.map((m) => (m.info.id, m.parts.map((p) => p.text).join())),
      );
    },
  );
}
