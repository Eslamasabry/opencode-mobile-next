import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/oc1.dart';
import '../tool/qa/fq3/common.dart';

class _CatalogWire extends Fq3Wire {
  final bool baselineListed;
  final calls = <String>[];
  String title = '';
  _CatalogWire(this.baselineListed)
    : super(baseUrl: 'http://127.0.0.1:1', password: 'unused');

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    calls.add('$method $path');
    if (path == '/global/health') {
      return {'healthy': true, 'version': '1.18.32'};
    }
    if (path == '/session' && method == 'POST') {
      title = (body as Map)['title'] as String;
      return {'id': 'ses_owned', 'title': title};
    }
    if (path == '/session/ses_owned') {
      return {'id': 'ses_owned', 'title': title};
    }
    if (path == '/provider') {
      return {
        'all': [
          {
            'id': 'opencode',
            'models': {'big-pickle': {}},
          },
          if (baselineListed)
            {
              'id': 'zai-coding-plan',
              'models': {'glm-5.3': {}},
            },
        ],
        'connected': ['opencode', if (baselineListed) 'zai-coding-plan'],
        'default': {'opencode': 'big-pickle'},
      };
    }
    throw const ProbeFailure('unexpected_fixture_request');
  }
}

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
  test(
    'baseline preference uses live listed model rather than catalog order',
    () async {
      final wire = _CatalogWire(true);
      addTearDown(wire.close);
      final run = await runProtocol1(
        wire,
        const ProbeOptions(
          directory: '/root/projects/test',
          title: 'fq3-test',
          baselineModel: baselineOc1Model,
          capabilities: {},
        ),
      );
      expect(run.selectedModel, baselineOc1Model);
      expect(run.modelSelection, {
        'source': 'baseline',
        'requested': baselineOc1Model,
        'selected': baselineOc1Model,
        'baselineAvailable': true,
        'inferenceAvailable': true,
      });
      expect(
        run.results.values.every((result) => result['state'] == 'pass'),
        isTrue,
      );
    },
  );

  test(
    'absent baseline preserves structural passes and blocks unsent inference',
    () async {
      final wire = _CatalogWire(false);
      addTearDown(wire.close);
      final run = await runProtocol1(
        wire,
        const ProbeOptions(
          directory: '/root/projects/test',
          title: 'fq3-test',
          baselineModel: baselineOc1Model,
          capabilities: {'stream'},
        ),
      );
      expect(run.selectedModel, 'opencode/big-pickle');
      expect(run.modelSelection?['source'], 'catalog-fallback');
      expect(run.modelSelection?['inferenceAvailable'], false);
      for (final key in ['version', 'create', 'models']) {
        expect(run.results[key]?['state'], 'pass');
      }
      expect(run.results['stream'], {
        'state': 'blocked',
        'code': 'provider_unavailable',
        'classification': 'provider',
        'originalCode': 'oc1_baseline_model_unavailable',
        'facts': <String, Object?>{},
      });
      expect(wire.calls.any((call) => call.contains('prompt_async')), false);
    },
  );

  test('explicit manual model overrides the baseline preference', () async {
    final wire = _CatalogWire(true);
    addTearDown(wire.close);
    final run = await runProtocol1(
      wire,
      const ProbeOptions(
        directory: '/root/projects/test',
        title: 'fq3-test',
        model: 'opencode/big-pickle',
        baselineModel: baselineOc1Model,
        capabilities: {},
      ),
    );
    expect(run.selectedModel, 'opencode/big-pickle');
    expect(run.modelSelection?['source'], 'explicit');
  });

  test(
    'provider status classifies failures without hiding malformed requests',
    () {
      final failure = <String, Object?>{
        'state': 'fail',
        'code': 'oc1_prompt_error',
        'facts': <String, Object?>{},
      };
      for (final status in [400, 404, 422]) {
        expect(
          oc1ClassifyResult('cards', failure, {
            'errorName': 'APIError',
            'httpStatus': status,
          }),
          same(failure),
        );
      }
      for (final status in [401, 403, 429, 500, 503]) {
        final result = oc1ClassifyResult('cards', failure, {
          'errorName': 'APIError',
          'httpStatus': status,
        });
        expect(result['state'], 'blocked');
        expect(result['classification'], 'provider');
        expect(result['originalCode'], 'oc1_prompt_error');
      }
      expect(
        oc1ClassifyResult('models', failure, {
          'errorName': 'ProviderAuthError',
        }),
        same(failure),
      );
      final mismatch = <String, Object?>{
        'state': 'fail',
        'code': 'oc1_cards_input_mismatch',
        'facts': <String, Object?>{},
      };
      expect(
        oc1ClassifyResult('cards', mismatch, {
          'errorName': 'APIError',
          'httpStatus': 403,
        }),
        same(mismatch),
      );
    },
  );

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
