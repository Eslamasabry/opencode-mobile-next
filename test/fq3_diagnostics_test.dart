import 'package:flutter_test/flutter_test.dart';
import '../tool/qa/fq3/diagnostics.dart';

void main() {
  Map<String, dynamic> reply() => {
    'type': 'assistant',
    'sessionID': 'ses_Own',
    'finish': 'stop',
    'time': {'completed': 123},
    'content': [
      {'type': 'text', 'text': 'FQ3B_OK'},
    ],
  };
  Map<String, Object> facts(List<Map<String, dynamic>> messages) =>
      oc2DiagnosticReplyFacts(
        messages,
        sessionID: 'ses_Own',
        expectedToken: 'FQ3B_OK',
      );
  test(
    'app-managed OC2 type/content reply is completed, role/parts is not',
    () {
      expect(facts([reply()])['expectedTokenInCompletedReply'], true);
      expect(
        facts([
          {
            'role': 'assistant',
            'time': {'completed': 123},
            'parts': [
              {'type': 'text', 'text': 'FQ3B_OK'},
            ],
          },
        ])['completedReply'],
        false,
      );
    },
  );
  test(
    'admission, user echo, incomplete and failed replies cannot qualify',
    () {
      for (final change in <Map<String, dynamic>>[
        {'type': 'user'},
        {'time': {}},
        {
          'time': {'completed': 0},
        },
        {
          'time': {'completed': double.nan},
        },
        {'finish': null},
        {
          'error': {'message': 'DO-NOT-EXPORT-provider-secret'},
        },
      ]) {
        final message = reply()..addAll(change);
        final result = facts([message]);
        expect(result['completedReply'], false);
        expect(result['expectedTokenInCompletedReply'], false);
        expect(
          result.values.every((value) => value is bool || value is int),
          true,
        );
      }
    },
  );
  test('foreign replies and synthetic echoes cannot qualify own output', () {
    expect(
      facts([reply()..['sessionID'] = 'ses_Foreign'])['foreignMessages'],
      1,
    );
    expect(
      facts([reply()..['sessionID'] = 'ses_Foreign'])['completedReply'],
      false,
    );
    final message = reply()
      ..['content'] = [
        {'type': 'text', 'text': 'FQ3B_OK', 'synthetic': true},
        {'type': 'text', 'text': 'DO-NOT-EXPORT-provider-secret'},
      ];
    final result = facts([message]);
    expect(result['completedReply'], true);
    expect(result['expectedTokenInCompletedReply'], false);
    expect(result.values.every((value) => value is bool || value is int), true);
  });
}
