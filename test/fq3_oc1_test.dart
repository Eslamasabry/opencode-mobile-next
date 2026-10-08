import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/oc1.dart';

Map<String, dynamic> _delta({
  String session = 'ses_owned',
  String field = 'text',
  String delta = 'hello',
}) => {
  'type': 'message.part.delta',
  'properties': {
    'sessionID': session,
    'messageID': 'msg_assistant',
    'partID': 'prt_owned',
    'field': field,
    'delta': delta,
  },
};

Map<String, dynamic> _tool({
  String role = 'assistant',
  String parent = 'msg_prompt',
  String session = 'ses_owned',
  String status = 'completed',
  bool synthetic = false,
  bool executed = true,
  String tool = 'oc-ui_show',
  String partMessage = 'msg_assistant',
  String callID = 'call_actual',
  bool completed = true,
}) => {
  'info': {
    'id': 'msg_assistant',
    'role': role,
    'sessionID': session,
    'parentID': parent,
    'time': {'created': 1, if (completed) 'completed': 2},
  },
  'parts': [
    {
      'type': 'tool',
      'tool': tool,
      'callID': callID,
      'messageID': partMessage,
      'sessionID': session,
      'synthetic': synthetic,
      'state': {
        'status': status,
        'executed': executed,
        'input': {
          'v': 1,
          'id': 'fq3-owned',
          'ask': {'kind': 'confirm'},
        },
      },
    },
  ],
};

class _DiscoveryWire extends Fq3Wire {
  final calls = <String>[];
  final bool rawFailure;
  String title = '';
  _DiscoveryWire({this.rawFailure = false})
    : super(baseUrl: 'http://127.0.0.1:1', password: 'unused-test-password');
  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    calls.add('$method $path');
    if (rawFailure) throw StateError('private server response and credentials');
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
            'id': 'provider',
            'models': {
              'model': {
                'capabilities': {
                  'input': {'image': true},
                },
              },
            },
          },
        ],
        'connected': ['provider'],
        'default': {'provider': 'model'},
      };
    }
    throw const ProbeFailure('unexpected_fixture_request');
  }
}

class _StreamWire extends _DiscoveryWire {
  final String evidence;
  int created = 0;
  String session = '';
  String prompt = '';
  String text = '';
  _StreamWire(this.evidence);

  @override
  Future<void> openEvents(String path, {Map<String, String>? query}) async {
    if (evidence == 'stale') {
      events.add({
        'type': 'message.part.delta',
        'properties': {
          'sessionID': 'ses_stream2',
          'messageID': 'msg_reply',
          'field': 'text',
          'delta': 'old',
        },
      });
    }
  }

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    if (method == 'POST' && path == '/session') {
      session = 'ses_stream${++created}';
      title = (body as Map)['title'] as String;
      return {'id': session, 'title': title};
    }
    if (path == '/session/$session') return {'id': session, 'title': title};
    if (path == '/session/$session/prompt_async') {
      final value = body as Map;
      prompt = value['messageID'] as String;
      text = ((value['parts'] as List).first as Map)['text'] as String;
      if (evidence != 'stale') {
        events.add({
          'type': 'message.part.delta',
          'properties': {
            'sessionID': session,
            'messageID': evidence == 'user_delta' ? prompt : 'msg_reply',
            'field': 'text',
            'delta': 'live',
          },
        });
      }
      return null;
    }
    if (path == '/session/$session/message') {
      return [
        {
          'info': {'id': prompt, 'sessionID': session, 'role': 'user'},
          'parts': [
            {'type': 'text', 'text': text},
          ],
        },
        {
          'info': {
            'id': 'msg_reply',
            'sessionID': session,
            'role': 'assistant',
            'parentID': prompt,
            'time': {'created': 1, 'completed': 2},
            'finish': 'stop',
          },
          'parts': [
            {'type': 'text', 'text': text.split(': ').last},
          ],
        },
      ];
    }
    return super.request(method, path, body: body, query: query);
  }
}

void main() {
  test(
    'OC1 delta witness rejects foreign sessions and nontext or empty data',
    () {
      expect(oc1StreamMessage(_delta(), 'ses_owned'), 'msg_assistant');
      expect(
        oc1StreamMessage({
          'directory': '/owned',
          'payload': _delta(),
        }, 'ses_owned'),
        'msg_assistant',
      );
      expect(
        oc1StreamMessage(_delta(session: 'ses_other'), 'ses_owned'),
        isNull,
      );
      expect(oc1StreamMessage(_delta(field: 'reasoning'), 'ses_owned'), isNull);
      expect(oc1StreamMessage(_delta(delta: ''), 'ses_owned'), isNull);
      expect(
        oc1StreamMessage({
          'type': 'message.updated',
          'properties': {
            'info': {'id': 'msg_assistant'},
          },
        }, 'ses_owned'),
        isNull,
      );
    },
  );

  test(
    'OC1 cards require executed assistant tool in exact session and turn',
    () {
      final valid = _tool();
      expect(
        oc1CompletedTool(
          [valid],
          'ses_owned',
          'msg_prompt',
          'oc-ui_show',
        )?['callID'],
        'call_actual',
      );
      for (final rejected in [
        _tool(role: 'user'),
        _tool(parent: 'msg_other'),
        _tool(session: 'ses_other'),
        _tool(status: 'running'),
        _tool(status: 'error'),
        _tool(synthetic: true),
        _tool(executed: false),
        _tool(tool: 'oc_ui_show'),
        _tool(partMessage: 'msg_other'),
        _tool(callID: ''),
        _tool(completed: false),
      ]) {
        expect(
          oc1CompletedTool([rejected], 'ses_owned', 'msg_prompt', 'oc-ui_show'),
          isNull,
        );
      }
    },
  );

  test(
    'OC1 selected phase retains owned history identifiers and skips actions',
    () async {
      final wire = _DiscoveryWire();
      addTearDown(wire.close);
      final run = await runProtocol1(
        wire,
        const ProbeOptions(
          directory: '/owned',
          title: 'fq3-owned',
          capabilities: {},
        ),
      );
      expect(run.observedVersion, '1.18.32');
      expect(run.sessionIDs, ['ses_owned']);
      expect(run.results.keys, ['version', 'create', 'models']);
      expect(run.results.values.every((r) => r['state'] == 'pass'), isTrue);
      expect(wire.calls, [
        'GET /global/health',
        'POST /session',
        'GET /session/ses_owned',
        'GET /provider',
        'GET /config',
      ]);
      expect(wire.title, startsWith('fq3-owned-oc1-create-'));
    },
  );

  test('OC1 adapter never reports arbitrary thrown server text', () async {
    final wire = _DiscoveryWire(rawFailure: true);
    addTearDown(wire.close);
    final run = await runProtocol1(
      wire,
      const ProbeOptions(
        directory: '/owned',
        title: 'fq3-owned',
        capabilities: {},
      ),
    );
    expect(
      run.results.values.every(
        (r) => r['state'] == 'fail' && r['code'] == 'scenario_failed',
      ),
      isTrue,
    );
    expect(run.results.toString(), isNot(contains('private server')));
    expect(run.results.toString(), isNot(contains('credentials')));
  });

  for (final evidence in ['assistant_delta', 'user_delta', 'stale']) {
    test(
      'OC1 stream correlates completion with fresh assistant delta: $evidence',
      () async {
        final wire = _StreamWire(evidence);
        addTearDown(wire.close);
        final run = await runProtocol1(
          wire,
          const ProbeOptions(
            directory: '/owned',
            title: 'fq3-owned',
            capabilities: {'stream'},
          ),
        );
        final result = run.results['stream']!;
        if (evidence == 'assistant_delta') {
          expect(result['state'], 'pass');
          expect(result['facts'], {
            'streamedDelta': true,
            'completedReply': true,
            'asserted': true,
          });
        } else {
          expect(result['state'], 'fail');
          expect(result['code'], 'oc1_no_assistant_delta');
        }
      },
    );
  }
}
