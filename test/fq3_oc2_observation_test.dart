import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/oc2_observation.dart';

const _directory = '/fq3/disposable';
const _session = 'ses_owned';

String _repeat(String value, int times) => List.filled(times, value).join();

Map<String, dynamic> _event(
  String type, {
  String sessionID = _session,
  String directory = _directory,
  Map<String, dynamic> data = const {},
}) => {
  'type': type,
  'location': {'directory': directory},
  'data': {'sessionID': sessionID, ...data},
};

Oc2ProbeObservation _observation() =>
    Oc2ProbeObservation(capability: 'image', directory: _directory);

Matcher _failure(String code) =>
    isA<ProbeFailure>().having((failure) => failure.code, 'fixed code', code);

void main() {
  test('only fresh events from the owned session and location are counted', () {
    final events = [
      _event('session.execution.succeeded'),
      _event('session.retry.scheduled', sessionID: 'ses_foreign'),
      _event('session.tool.success', directory: '/other/project'),
      {
        'type': 'permission.asked',
        'data': {'sessionID': _session},
      },
      {
        'type': 'permission.asked',
        'location': {'directory': _directory},
        'data': 'untrusted',
      },
      _event(
        'session.retry.scheduled',
        data: {
          'error': {'status': 503},
        },
      ),
      _event('session.execution.interrupted'),
      _event('session.tool.called'),
      _event('session.tool.success'),
      _event('permission.asked'),
      _event('permission.replied'),
    ];
    final result = _observation().snapshot(
      events,
      sessionID: _session,
      eventStart: 1,
    );
    expect(result['executionSucceededCount'], 0);
    expect(result['terminalCount'], 1);
    expect(result['retryCount'], 1);
    expect(result['http503Retries'], 1);
    expect(result['toolCalledCount'], 1);
    expect(result['toolSuccessCount'], 1);
    expect(result['permissionAskedCount'], 2);
    expect(result['permissionRepliedCount'], 1);
  });

  test('owned global terminals count without accepting foreign scope', () {
    final result = _observation().snapshot(
      [
        {
          'type': 'session.execution.failed',
          'data': {'sessionID': _session},
        },
        {
          'type': 'session.execution.failed',
          'data': {'sessionID': 'ses_other'},
        },
        {
          'type': 'session.execution.failed',
          'location': null,
          'data': {'sessionID': _session},
        },
        _event('session.execution.failed', directory: '/foreign'),
      ],
      sessionID: _session,
      eventStart: 0,
    );
    expect(result['executionFailedCount'], 1);
    expect(result['terminalCount'], 1);
  });

  test('failure kinds are fixed counters and never raw provider errors', () {
    final result = _observation().snapshot(
      [
        _event(
          'session.execution.failed',
          data: {
            'error': {
              'type': 'provider.no-route',
              'message': 'Model unavailable: test-secret',
            },
          },
        ),
        _event(
          'session.execution.failed',
          data: {
            'error': {'type': 'provider.auth', 'message': 'test-secret'},
          },
        ),
        _event(
          'session.execution.failed',
          data: {
            'error': {
              'type': 'provider.no-route',
              'message': 'untrusted test-secret',
            },
          },
        ),
      ],
      sessionID: _session,
      eventStart: 0,
    );
    expect(result['providerNoRouteCount'], 2);
    expect(result['modelUnavailableCount'], 1);
    expect(result['providerAuthCount'], 1);
    expect(jsonEncode(result), isNot(contains('test-secret')));
  });

  test('card diagnostics distinguish nested ask and helper rejection', () {
    final result = _observation().snapshot(
      [
        _event(
          'session.tool.called',
          data: {
            'input': {
              'v': 1,
              'id': 'fq3-confirm',
              'body': [
                {
                  'type': 'text',
                  'text': 'test-secret',
                  'ask': {'kind': 'confirm'},
                },
              ],
            },
          },
        ),
        _event(
          'session.tool.called',
          data: {
            'input': {
              'v': 1,
              'id': 'fq3-confirm',
              'ask': {'kind': 'confirm'},
            },
          },
        ),
        _event(
          'session.tool.failed',
          data: {
            'error': {
              'type': 'tool.execution',
              'message': 'Agent card unavailable or invalid.',
            },
          },
        ),
        _event(
          'session.tool.failed',
          sessionID: 'ses_foreign',
          data: {
            'error': {'message': 'Agent card unavailable or invalid.'},
          },
        ),
      ],
      sessionID: _session,
      eventStart: 0,
    );
    expect(result['probeCardNestedAskCount'], 1);
    expect(result['probeCardTopLevelConfirmCount'], 1);
    expect(result['cardHelperRejectedCount'], 1);
    expect(jsonEncode(result), isNot(contains('test-secret')));
  });

  test('retained card input counts identify malformed model arguments', () {
    final observation = _observation();
    observation.recordRetainedCardCalls([
      {
        'type': 'assistant',
        'content': [
          {
            'type': 'tool',
            'name': 'oc-ui_show',
            'state': {
              'status': 'error',
              'input': {
                'v': 1,
                'id': 'fq3-confirm',
                'body': [
                  {
                    'ask': {'kind': 'confirm'},
                    'text': 'test-secret',
                  },
                ],
              },
              'error': {'message': 'Agent card unavailable or invalid.'},
            },
          },
          {
            'type': 'tool',
            'name': 'foreign',
            'state': {
              'input': {'v': 1},
            },
          },
        ],
      },
      {
        'type': 'user',
        'content': [
          {'type': 'tool', 'name': 'oc-ui_show'},
        ],
      },
    ]);
    final result = observation.snapshot([], sessionID: _session, eventStart: 0);
    expect(result['retainedShowCallCount'], 1);
    expect(result['retainedShowVersionValidCount'], 1);
    expect(result['retainedShowIDValidCount'], 1);
    expect(result['retainedShowTopLevelConfirmCount'], 0);
    expect(result['retainedShowNestedAskCount'], 1);
    expect(result['retainedShowHelperRejectedCount'], 1);
    expect(jsonEncode(result), isNot(contains('test-secret')));
  });

  test(
    'retry counts accept structured statuses and exact internal status lines only',
    () {
      final errors = <Object>[
        {'status': 503},
        {'statusCode': 503.0},
        {
          'data': {'statusCode': 503},
        },
        {'message': 'HTTP/1.1 503 Service Unavailable'},
        {'status': '503'},
        {'status': 503.1},
        {'message': 'request 503 contained Bearer test-secret'},
        {'message': '${_repeat('HTTP 503 ', 20)}test-secret'},
        {'status': 429, 'message': 'retry later'},
        'HTTP 503',
      ];
      final result = _observation().snapshot(
        [
          for (final error in errors)
            _event('session.retry.scheduled', data: {'error': error}),
        ],
        sessionID: _session,
        eventStart: 0,
      );
      expect(result['retryCount'], errors.length);
      expect(result['http503Retries'], 4);
    },
  );

  test(
    'counter whitelist never exports raw event content or qualification facts',
    () {
      final observation = _observation()
        ..checkpoint('await_terminal')
        ..selectModel('opencode/vision-model');
      final result = observation.snapshot(
        [
          _event(
            'session.text.delta',
            data: {'delta': 'test-secret transcript'},
          ),
          _event(
            'session.tool.failed',
            data: {
              'error': {'message': 'test-secret provider failure'},
            },
          ),
          _event(
            'untrusted-test-secret',
            data: {'model': 'Bearer test-secret'},
          ),
        ],
        sessionID: _session,
        eventStart: 0,
      );
      expect(result['stage'], 'await_terminal');
      expect(result['model'], 'opencode/vision-model');
      expect(result['textDeltaCount'], 1);
      expect(result['toolFailedCount'], 1);
      expect(jsonEncode(result), isNot(contains('test-secret')));
      expect(jsonEncode(result), isNot(contains(_directory)));
      expect(jsonEncode(result), isNot(contains(_session)));
      expect(result.keys, isNot(contains('asserted')));
      expect(result.keys, isNot(contains('state')));
      expect(
        result.values.every(
          (value) =>
              value is int ||
              value == 'await_terminal' ||
              value == 'opencode/vision-model',
        ),
        isTrue,
      );
    },
  );

  test('large event bursts saturate each counter independently', () {
    final result = _observation().snapshot(
      [
        ...List.generate(
          10005,
          (_) => _event('session.retry.scheduled', data: {'status': 503}),
        ),
        ...List.generate(10005, (_) => _event('session.execution.failed')),
        _event('session.tool.success'),
      ],
      sessionID: _session,
      eventStart: 0,
    );
    expect(result['retryCount'], 10000);
    expect(result['http503Retries'], 10000);
    expect(result['terminalCount'], 10000);
    expect(result['executionFailedCount'], 10000);
    expect(result['toolSuccessCount'], 1);
  });

  test(
    'each snapshot honors its boundary without retaining earlier counts',
    () {
      final observation = _observation();
      final events = [_event('permission.asked')];
      expect(
        observation.snapshot(
          events,
          sessionID: _session,
          eventStart: 0,
        )['permissionAskedCount'],
        1,
      );
      observation.checkpoint('reply_permission');
      events.add(_event('permission.replied'));
      final result = observation.snapshot(
        events,
        sessionID: _session,
        eventStart: 1,
      );
      expect(result['permissionAskedCount'], 0);
      expect(result['permissionRepliedCount'], 1);
      expect(result['stage'], 'reply_permission');
      expect(result.containsKey('model'), isFalse);
    },
  );

  test('invalid stages and model references refuse with fixed errors', () {
    final observation = _observation()
      ..checkpoint('verify_outcome')
      ..selectModel('opencode/big-pickle');
    expect(
      () => observation.checkpoint('provider failure test-secret'),
      throwsA(_failure('invalid_observation_stage')),
    );
    for (final model in [
      'Bearer test-secret',
      'https://provider/model',
      'provider/model?key=secret',
      'provider/model\n',
      '${_repeat('p', 97)}/model',
      'provider/${_repeat('m', 129)}',
    ]) {
      expect(
        () => observation.selectModel(model),
        throwsA(_failure('invalid_observation_model')),
      );
    }
    final result = observation.snapshot([], sessionID: _session, eventStart: 0);
    expect(result['stage'], 'verify_outcome');
    expect(result['model'], 'opencode/big-pickle');
  });

  test(
    'bounded public references remain valid without normalization or fallback',
    () {
      final model = '${_repeat('p', 96)}/${_repeat('m', 128)}';
      final observation = _observation()..selectModel(model);
      expect(
        observation.snapshot([], sessionID: _session, eventStart: 0)['model'],
        model,
      );
    },
  );

  test('malformed caller scope fails without reflecting caller text', () {
    expect(
      () =>
          Oc2ProbeObservation(capability: 'test-secret', directory: _directory),
      throwsA(_failure('invalid_observation_capability')),
    );
    for (final directory in [
      'relative',
      '/path\nsecret',
      '/path\u0000secret',
      '/${_repeat('d', 4096)}',
    ]) {
      expect(
        () => Oc2ProbeObservation(capability: 'image', directory: directory),
        throwsA(_failure('invalid_observation_directory')),
      );
    }
    final observation = _observation();
    expect(
      () => observation.snapshot([], sessionID: _session, eventStart: -1),
      throwsA(_failure('invalid_observation_start')),
    );
    expect(
      () =>
          observation.snapshot([], sessionID: 'session\nsecret', eventStart: 0),
      throwsA(_failure('invalid_observation_session')),
    );
  });
}
