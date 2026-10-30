import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/transcript_settle.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

Map<String, dynamic> _entry(int seq, Map<String, dynamic> item) => {
  'seqStart': seq,
  'seqEnd': seq,
  'timestamp': '2026-10-11T10:00:0$seq.000Z',
  'provider': 'claude',
  'item': item,
};

Map<String, dynamic> _read(String status) => {
  'type': 'tool_call',
  'callId': 'c1',
  'name': 'Read',
  'status': status,
  'detail': {'type': 'read', 'filePath': '/p/widget_test.dart'},
};

List<MessageWithParts> _paseo(List<Map<String, dynamic>> items, bool busy) =>
    paseoTimelineMessages('a', {
      'entries': [
        for (var i = 0; i < items.length; i++) _entry(i + 1, items[i]),
      ],
    }, busy: busy);

bool _hasRunningCompaction(List<MessageWithParts> messages) => messages.any(
  (m) =>
      m.parts.any((p) => p.type == 'v2:compaction' && p.toolName == 'running'),
);

String _status(List<MessageWithParts> messages) => messages
    .expand((m) => m.parts)
    .firstWhere((p) => p.type == 'tool')
    .toolState
    .status;

MessageWithParts _msg(String id, String role, List<Part> parts) =>
    MessageWithParts(
      info: MessageInfo(id: id, sessionID: 's', role: role),
      parts: parts,
    );

Part _tool(String status, {Map<String, dynamic>? metadata}) => Part(
  id: 't',
  type: 'tool',
  toolName: 'bash',
  toolState: ToolState(status: status, metadata: metadata),
);

void main() {
  group('compaction line', () {
    final real = [
      {'type': 'user_message', 'text': 'go', 'messageId': 'u1'},
      {'type': 'compaction', 'status': 'loading'},
      {'type': 'compaction', 'status': 'completed', 'trigger': 'auto'},
      {'type': 'assistant_message', 'text': 'continuing', 'messageId': 'a1'},
    ];

    test('ends once the compaction notice and later work arrive', () {
      final messages = _paseo(real, true);
      expect(_hasRunningCompaction(messages), isTrue);
      final settled = settleStaleLive(messages, busy: true);
      expect(_hasRunningCompaction(settled), isFalse);
      // One message per message: the transcript reads rows, parts and turn
      // owners by the same index.
      expect(settled, hasLength(messages.length));
      expect(
        settled.any((m) => m.parts.any((p) => p.toolName == compactionPassed)),
        isTrue,
      );
      expect(
        settled.any((m) => m.parts.any((p) => p.toolName == 'completed')),
        isTrue,
      );
    });

    test('stays while it is the newest thing and the agent is busy', () {
      final messages = _paseo(real.sublist(0, 2), true);
      expect(
        _hasRunningCompaction(settleStaleLive(messages, busy: true)),
        isTrue,
      );
    });

    test('ends when the turn has ended', () {
      final messages = _paseo(real.sublist(0, 2), false);
      expect(
        _hasRunningCompaction(settleStaleLive(messages, busy: false)),
        isFalse,
      );
    });
  });

  group('tool spinners', () {
    test('a tool under later words of its turn is finished', () {
      final messages = _paseo([
        {'type': 'user_message', 'text': 'go', 'messageId': 'u1'},
        _read('running'),
        {'type': 'assistant_message', 'text': 'Read it.', 'messageId': 'a1'},
      ], true);
      expect(_status(messages), 'running');
      expect(_status(settleStaleLive(messages, busy: true)), 'completed');
    });

    test('a tool of the newest step with no words after it keeps running', () {
      final messages = [
        _msg('u', 'user', [Part(type: 'text', text: 'go')]),
        _msg('a', 'assistant', [_tool('running')]),
      ];
      expect(
        identical(settleStaleLive(messages, busy: true), messages),
        isTrue,
      );
    });

    test('a tool is finished when the turn has ended', () {
      final messages = [
        _msg('u', 'user', [Part(type: 'text', text: 'go')]),
        _msg('a', 'assistant', [_tool('running')]),
      ];
      expect(
        settleStaleLive(messages, busy: false)[1].parts.single.toolState.status,
        'completed',
      );
    });

    test('words of an earlier turn do not finish a later turn tool', () {
      final messages = [
        _msg('u1', 'user', [Part(type: 'text', text: 'one')]),
        _msg('a1', 'assistant', [Part(type: 'text', text: 'done')]),
        _msg('u2', 'user', [Part(type: 'text', text: 'two')]),
        _msg('a2', 'assistant', [_tool('running')]),
      ];
      expect(
        identical(settleStaleLive(messages, busy: true), messages),
        isTrue,
      );
    });

    test('a background process keeps running', () {
      final messages = [
        _msg('u', 'user', [Part(type: 'text', text: 'go')]),
        _msg('a', 'assistant', [
          _tool('running', metadata: {'background': true}),
          Part(type: 'text', text: 'Started it.'),
        ]),
      ];
      expect(
        identical(settleStaleLive(messages, busy: true), messages),
        isTrue,
      );
    });

    test('a late running report does not revive a finished call', () {
      final messages = _paseo([
        {'type': 'user_message', 'text': 'go', 'messageId': 'u1'},
        _read('completed'),
        _read('running'),
      ], false);
      expect(_status(messages), 'completed');
    });
  });
}
