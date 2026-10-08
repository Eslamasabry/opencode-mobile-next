import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/history.dart';

void main() {
  for (final oc2 in [false, true]) {
    final protocol = oc2 ? 'OC2' : 'OC1';
    String digest(List<Map<String, dynamic>> messages) =>
        historyProjection(messages, oc2: oc2);
    List<Map<String, dynamic>> fixture() => _fixture(oc2: oc2);
    Map<String, dynamic> info(Map<String, dynamic> message) =>
        oc2 ? message : message['info'] as Map<String, dynamic>;
    List<Map<String, dynamic>> items(Map<String, dynamic> message) =>
        (message[oc2 ? 'content' : 'parts'] as List)
            .cast<Map<String, dynamic>>();
    Map<String, dynamic> tool(List<Map<String, dynamic>> messages) =>
        items(messages[1]).last;

    test('$protocol same IDs with altered text differ', () {
      final before = fixture();
      final after = fixture();
      items(after[1]).first['text'] = 'Altered assistant output';
      expect(digest(after), isNot(digest(before)));
    });

    test('$protocol same IDs with altered role differ', () {
      final before = fixture();
      final after = fixture();
      info(after[1])[oc2 ? 'type' : 'role'] = 'user';
      expect(digest(after), isNot(digest(before)));
    });

    test('$protocol ordered IDs and content order are retained', () {
      final before = fixture();
      expect(digest(before.reversed.toList()), isNot(digest(before)));
      final after = fixture();
      after[1][oc2 ? 'content' : 'parts'] = items(after[1]).reversed.toList();
      expect(digest(after), isNot(digest(before)));
    });

    test('$protocol altered message ID differs', () {
      final before = fixture();
      final after = fixture();
      info(after[1])['id'] = 'msg_replacement';
      expect(digest(after), isNot(digest(before)));
    });

    for (final field in ['input', 'output', 'status']) {
      test('$protocol changed durable tool $field differs', () {
        final before = fixture();
        final after = fixture();
        final state = tool(after)['state'] as Map<String, dynamic>;
        if (field == 'input') {
          (state['input'] as Map)['id'] = 'another-card';
        } else if (field == 'status') {
          state['status'] = 'error';
        } else if (oc2) {
          (state['content'] as List).first['text'] = 'Changed tool result';
        } else {
          state['output'] = 'Changed tool result';
        }
        expect(digest(after), isNot(digest(before)));
      });
    }

    test('$protocol altered tool call ID differs', () {
      final before = fixture();
      final after = fixture();
      tool(after)[oc2 ? 'id' : 'callID'] = 'call_other';
      expect(digest(after), isNot(digest(before)));
    });

    test('$protocol tagged card receipt is durable transcript data', () {
      final before = fixture();
      final after = fixture();
      if (oc2) {
        after.last['text'] = (after.last['text'] as String).replaceFirst(
          'call_card',
          'call_wrong',
        );
      } else {
        final part = items(after.last).single;
        part['text'] = (part['text'] as String).replaceFirst(
          'call_card',
          'call_wrong',
        );
      }
      expect(digest(after), isNot(digest(before)));
    });

    test('$protocol known timestamps are excluded without mutating input', () {
      final before = fixture();
      final serialized = jsonEncode(before);
      final after = fixture();
      info(after[1])['time'] = {'created': 100, 'completed': 200};
      tool(after)['time'] = {'created': 100, 'completed': 200};
      (tool(after)['state'] as Map)['time'] = {'start': 100, 'end': 200};
      expect(digest(after), digest(before));
      expect(jsonEncode(before), serialized);
    });

    test('$protocol time inside tool input remains durable', () {
      final before = fixture();
      final after = fixture();
      ((tool(after)['state'] as Map)['input'] as Map)['time'] = 900;
      expect(digest(after), isNot(digest(before)));
    });

    test('$protocol JSON object key order does not alter fingerprint', () {
      final before = fixture();
      final reversed = (_reverseKeys(before) as List)
          .cast<Map<String, dynamic>>();
      expect(digest(reversed), digest(before));
      expect(digest(before), matches(RegExp(r'^[0-9a-f]{64}$')));
      expect(digest(before), isNot(contains('oc-ui answer')));
    });

    test('$protocol malformed identity fails with fixed safe error', () {
      final messages = fixture();
      info(messages.first).remove('id');
      expect(
        () => digest(messages),
        throwsA(
          isA<FormatException>().having(
            (e) => e.message,
            'fixed code',
            'invalid_history_identity',
          ),
        ),
      );
    });
  }
}

List<Map<String, dynamic>> _fixture({required bool oc2}) {
  const receipt =
      '[oc-ui answer fq3-card] Confirmed\n'
      '{"v":1,"cardId":"fq3-card","callId":"call_card",'
      '"value":{"confirm":true}}';
  final tool = <String, dynamic>{
    'type': 'tool',
    if (oc2) 'id': 'call_card' else 'callID': 'call_card',
    if (oc2) 'name': 'oc-ui_show' else 'tool': 'oc-ui_show',
    'executed': true,
    'time': {'created': 1, 'completed': 2},
    'state': {
      'status': 'completed',
      'input': {
        'v': 1,
        'id': 'fq3-card',
        'time': 20,
        'ask': {'kind': 'confirm'},
      },
      'time': {'start': 1, 'end': 2},
      if (oc2)
        'content': [
          {'type': 'text', 'text': 'Card accepted'},
        ]
      else
        'output': 'Card accepted',
    },
  };
  if (oc2) {
    return [
      {
        'id': 'msg_user',
        'type': 'user',
        'text': 'Please ask.',
        'time': {'created': 1},
      },
      {
        'id': 'msg_assistant',
        'type': 'assistant',
        'model': {'id': 'model', 'providerID': 'provider'},
        'finish': 'tool-calls',
        'time': {'created': 1, 'completed': 2},
        'content': [
          {'type': 'text', 'text': 'Please confirm.'},
          tool,
        ],
      },
      {
        'id': 'msg_receipt',
        'type': 'user',
        'text': receipt,
        'time': {'created': 3},
      },
    ];
  }
  return [
    {
      'info': {
        'id': 'msg_user',
        'role': 'user',
        'sessionID': 'ses_owned',
        'time': {'created': 1},
      },
      'parts': [
        {'id': 'prt_user', 'type': 'text', 'text': 'Please ask.'},
      ],
    },
    {
      'info': {
        'id': 'msg_assistant',
        'role': 'assistant',
        'sessionID': 'ses_owned',
        'finish': 'tool-calls',
        'time': {'created': 1, 'completed': 2},
      },
      'parts': [
        {'id': 'prt_text', 'type': 'text', 'text': 'Please confirm.'},
        tool,
      ],
    },
    {
      'info': {
        'id': 'msg_receipt',
        'role': 'user',
        'sessionID': 'ses_owned',
        'time': {'created': 3},
      },
      'parts': [
        {'id': 'prt_receipt', 'type': 'text', 'text': receipt},
      ],
    },
  ];
}

Object? _reverseKeys(Object? value) {
  if (value is Map) {
    return <String, dynamic>{
      for (final key in value.keys.toList().reversed)
        key as String: _reverseKeys(value[key]),
    };
  }
  if (value is List) return value.map(_reverseKeys).toList();
  return value;
}
