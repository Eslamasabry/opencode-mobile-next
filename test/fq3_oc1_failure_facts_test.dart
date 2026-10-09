import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/oc1.dart';

Map<String, dynamic> assistant({
  String session = 'ses_owned',
  String prompt = 'msg_prompt',
  String role = 'assistant',
  Object? error,
  bool tool = false,
  bool synthetic = false,
}) => {
  'info': {
    'id': 'msg_assistant',
    'sessionID': session,
    'parentID': prompt,
    'role': role,
    'synthetic': synthetic,
    'error': ?error,
  },
  'parts': [
    if (tool)
      {
        'type': 'tool',
        'tool': 'bash',
        'sessionID': session,
        'messageID': 'msg_assistant',
        'callID': 'call_owned',
        'state': {
          'status': 'running',
          'input': {'command': 'private command must not escape'},
        },
      },
  ],
};

Map<String, Object?> permission(List<Map<String, dynamic>> history) =>
    oc1PermissionFailureFacts(
      history,
      sessionID: 'ses_owned',
      promptID: 'msg_prompt',
    );

void main() {
  test('APIError projects only safe nested server metadata', () {
    final facts = oc1FailureFacts({
      'name': 'APIError',
      'message': 'private message',
      'data': {
        'message': 'private body',
        'statusCode': 429,
        'isRetryable': true,
        'responseHeaders': {'Authorization': 'private credential'},
        'responseBody': 'private reply',
      },
    });
    expect(facts, {
      'errorName': 'APIError',
      'httpStatus': 429,
      'retryable': true,
    });
    expect(jsonEncode(facts), isNot(contains('private')));
  });

  test('unknown names and malformed numeric or retry fields never escape', () {
    for (final status in [99, 600, 401.5, '401', true, double.infinity]) {
      expect(
        oc1FailureFacts({
          'name': 'private arbitrary error',
          'status': status,
          'retryable': 'private value',
          'stack': 'private stack',
        }),
        isEmpty,
      );
    }
    expect(oc1FailureFacts('private raw exception'), isEmpty);
  });

  test(
    'bounded nested data accepts integer numeric status and false retry',
    () {
      expect(
        oc1FailureFacts({
          'name': 'ProviderAuthError',
          'data': {
            'data': {'status': 403.0, 'retryable': false},
          },
        }),
        {
          'errorName': 'ProviderAuthError',
          'httpStatus': 403,
          'retryable': false,
        },
      );
      expect(
        oc1FailureFacts({
          'data': {
            'data': {
              'data': {
                'data': {'status': 500},
              },
            },
          },
        }),
        isEmpty,
      );
    },
  );

  test('permission timeout identifies a real nested assistant APIError', () {
    final facts = permission([
      assistant(
        error: {
          'name': 'APIError',
          'data': {
            'statusCode': 503,
            'isRetryable': true,
            'message': 'private inference error',
          },
        },
      ),
    ]);
    expect(facts, {
      'permissionTimeoutKind': 'assistant_error',
      'errorName': 'APIError',
      'httpStatus': 503,
      'retryable': true,
    });
    expect(jsonEncode(facts), isNot(contains('private')));
  });

  test(
    'foreign user stale and synthetic errors cannot explain owned timeout',
    () {
      final error = {
        'name': 'APIError',
        'data': {'statusCode': 401},
      };
      expect(
        permission([
          assistant(session: 'ses_foreign', error: error),
          assistant(prompt: 'msg_old', error: error),
          assistant(role: 'user', error: error),
          assistant(synthetic: true, error: error),
        ]),
        {
          'permissionTimeoutKind': 'tool_not_requested',
          'permissionToolObserved': false,
        },
      );
    },
  );

  test(
    'owned bash call separates event wait failure from no observed tool',
    () {
      expect(permission([assistant(tool: true)]), {
        'permissionTimeoutKind': 'event_missing',
        'permissionToolObserved': true,
      });
      expect(permission([assistant()]), {
        'permissionTimeoutKind': 'tool_not_requested',
        'permissionToolObserved': false,
      });
    },
  );
}
