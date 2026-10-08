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

class _ImageWire extends _StreamWire {
  final bool selectedVision;
  final String answer;
  final bool retainImage;
  final bool foreignFinal;
  final bool wrongSelection;
  Map<String, dynamic>? selectedModel;
  List<dynamic> sentParts = [];
  _ImageWire({
    this.selectedVision = true,
    this.answer = '**BLUE.**',
    this.retainImage = true,
    this.foreignFinal = false,
    this.wrongSelection = false,
  }) : super('image');

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    if (path == '/provider') {
      return {
        'all': [
          {
            'id': 'other',
            'models': {
              'vision': {
                'capabilities': {
                  'input': {'image': true},
                },
              },
            },
          },
          {
            'id': 'saved',
            'models': {
              'selected': {
                'capabilities': {
                  'input': {'image': selectedVision},
                },
              },
              'same-provider-vision': {
                'capabilities': {
                  'input': {'image': true},
                },
              },
            },
          },
        ],
        'connected': ['other', 'saved'],
        'default': {'other': 'vision'},
      };
    }
    if (path == '/config') {
      return {
        'model': 'saved/selected',
        'provider': {'fixture-secret': 'never-emit-config-body'},
      };
    }
    if (path == '/session/$session/prompt_async') {
      final value = body as Map;
      prompt = value['messageID'] as String;
      selectedModel = Map<String, dynamic>.from(value['model'] as Map);
      sentParts = value['parts'] as List;
      return null;
    }
    if (path == '/session/$session/message') {
      return [
        {
          'info': {'id': prompt, 'sessionID': session, 'role': 'user'},
          'parts': retainImage ? sentParts : [sentParts.first],
        },
        {
          'info': {
            'id': 'msg_intermediate',
            'sessionID': session,
            'role': 'assistant',
            'parentID': prompt,
            'time': {'created': 1, 'completed': 2},
            'finish': 'tool-calls',
          },
          'parts': [
            {'type': 'text', 'text': 'Intermediate narration.'},
          ],
        },
        {
          'info': {
            'id': 'msg_final',
            'sessionID': session,
            'role': 'assistant',
            'parentID': prompt,
            'time': {'created': 3, 'completed': 4},
            'finish': 'stop',
            'providerID': wrongSelection
                ? 'foreign-provider'
                : selectedModel!['providerID'],
            'modelID': selectedModel!['modelID'],
          },
          'parts': [
            {
              'type': 'text',
              'text': answer,
              'sessionID': session,
              'messageID': foreignFinal ? 'msg_foreign' : 'msg_final',
            },
          ],
        },
      ];
    }
    return super.request(method, path, body: body, query: query);
  }
}

Map<String, dynamic> _permissionRecord({
  String status = 'error',
  bool executed = false,
  bool synthetic = false,
  String callID = 'call_actual',
  String output = '',
}) {
  final message = _tool(
    tool: 'bash',
    completed: false,
    status: status,
    executed: executed,
    synthetic: synthetic,
    callID: callID,
  );
  final state = ((message['parts'] as List).single as Map)['state'] as Map;
  state['input'] = {'command': 'printf harmless'};
  state['output'] = output;
  return message;
}

Future<void> _permissionVerify(
  List<Map<String, dynamic>> history, {
  bool allow = false,
  List<Map<String, dynamic>> pending = const [],
}) => oc1VerifyPermissionOutcome(
  () async => history,
  () async => pending,
  sessionID: 'ses_owned',
  promptID: 'msg_prompt',
  messageID: 'msg_assistant',
  callID: 'call_actual',
  requestID: 'per_owned',
  command: 'printf harmless',
  marker: 'harmless',
  allow: allow,
  timeout: const Duration(milliseconds: 10),
);

class _AbortWire extends _StreamWire {
  final String response;
  final titles = <String>[];
  bool interrupted = false;
  String originalPrompt = '';
  String followPrompt = '';
  String followText = '';
  String answer = '';
  Map<String, dynamic> followModel = {};
  _AbortWire(this.response) : super('abort');

  Map<String, dynamic> textPart(String messageID, String text) => {
    'type': 'text',
    'sessionID': session,
    'messageID': messageID,
    'text': text,
  };

  Map<String, dynamic> assistant(
    String id,
    String parent,
    List<Map<String, dynamic>> parts, {
    int completed = 4,
    String finish = 'stop',
    String modelID = 'model',
  }) => {
    'info': {
      'id': id,
      'sessionID': session,
      'role': 'assistant',
      'parentID': parent,
      'providerID': 'provider',
      'modelID': modelID,
      'time': {'created': completed - 1, 'completed': completed},
      'finish': finish,
    },
    'parts': parts,
  };

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    if (path == '/session' && method == 'POST') {
      titles.add((body as Map)['title'] as String);
      return super.request(method, path, body: body, query: query);
    }
    if (path == '/session/$session/prompt_async') {
      final value = body as Map;
      final id = value['messageID'] as String;
      final sentText =
          ((value['parts'] as List).first as Map)['text'] as String;
      if (originalPrompt.isEmpty) {
        originalPrompt = id;
        events.add({
          'type': 'message.part.delta',
          'properties': {
            'sessionID': session,
            'messageID': 'msg_aborted',
            'field': 'text',
            'delta': '1\n2\n',
          },
        });
      } else {
        followPrompt = id;
        followText = sentText;
        answer = sentText.split(': ').last;
        followModel = Map<String, dynamic>.from(value['model'] as Map);
      }
      return null;
    }
    if (path == '/session/status') {
      return interrupted
          ? {}
          : {
              session: {'type': 'busy'},
            };
    }
    if (path == '/session/$session/abort') {
      interrupted = true;
      return true;
    }
    if (path == '/session/$session/message') {
      return [
        {
          'info': {'id': originalPrompt, 'sessionID': session, 'role': 'user'},
          'parts': [textPart(originalPrompt, 'Write numbers.')],
        },
        {
          'info': {
            'id': 'msg_aborted',
            'sessionID': session,
            'role': 'assistant',
            'parentID': originalPrompt,
            'providerID': 'provider',
            'modelID': 'model',
            'time': {'created': 1, if (interrupted) 'completed': 2},
            if (interrupted) 'error': {'name': 'MessageAbortedError'},
          },
          'parts': [
            textPart(
              'msg_aborted',
              response == 'old_parent_only' ? answer : '1\n2',
            ),
          ],
        },
        if (followPrompt.isNotEmpty) ...[
          {
            'info': {'id': followPrompt, 'sessionID': session, 'role': 'user'},
            'parts': [textPart(followPrompt, followText)],
          },
          if (response == 'pre_final')
            assistant(
              'msg_prefinal',
              followPrompt,
              [textPart('msg_prefinal', answer)],
              completed: 3,
              finish: 'tool-calls',
            ),
          assistant('msg_follow', followPrompt, [
            textPart(
              'msg_follow',
              const [
                    'noncompliant',
                    'foreign_part',
                    'pre_final',
                    'old_parent_only',
                  ].contains(response)
                  ? 'The task is stopped.'
                  : answer,
            ),
            if (response == 'foreign_part') textPart('msg_aborted', answer),
          ], modelID: response == 'wrong_model' ? 'other-model' : 'model'),
        ],
      ];
    }
    return super.request(method, path, body: body, query: query);
  }
}

class _LedgerWire extends _DiscoveryWire {
  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    if (path == '/session/ses_owned') {
      calls.add('$method $path');
      throw const ProbeFailure('fixture_retained_read_failed');
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
      expect(wire.title, 'fq3-owned-session-1');
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

  test(
    'OC1 deny verifies blocked original tool while assistant is still continuing',
    () async {
      final continuing = _permissionRecord();
      expect((continuing['info'] as Map)['time'], {'created': 1});
      await _permissionVerify([continuing]);
    },
  );

  test(
    'OC1 allow requires executed matching tool output and cleared request',
    () async {
      await _permissionVerify([
        _permissionRecord(
          status: 'completed',
          executed: true,
          output: 'harmless',
        ),
      ], allow: true);
      await expectLater(
        _permissionVerify([
          _permissionRecord(
            status: 'completed',
            executed: false,
            output: 'harmless',
          ),
        ], allow: true),
        throwsA(
          isA<ProbeFailure>().having(
            (e) => e.code,
            'code',
            'oc1_permission_outcome_mismatch',
          ),
        ),
      );
    },
  );

  test(
    'OC1 permission reply event cannot substitute for terminal owned tool outcome',
    () async {
      for (final history in <List<Map<String, dynamic>>>[
        [],
        [_permissionRecord(callID: 'call_other')],
        [_permissionRecord(synthetic: true)],
        [_tool(role: 'user', tool: 'bash')],
      ]) {
        await expectLater(
          _permissionVerify(history),
          throwsA(
            isA<ProbeFailure>().having(
              (e) => e.code,
              'code',
              'oc1_permission_tool_not_correlated',
            ),
          ),
        );
      }
      await expectLater(
        _permissionVerify([_permissionRecord(status: 'running')]),
        throwsA(
          isA<ProbeFailure>().having(
            (e) => e.code,
            'code',
            'oc1_permission_outcome_timeout',
          ),
        ),
      );
      await expectLater(
        _permissionVerify(
          [_permissionRecord()],
          pending: [
            {'id': 'per_owned', 'sessionID': 'ses_owned'},
          ],
        ),
        throwsA(
          isA<ProbeFailure>().having(
            (e) => e.code,
            'code',
            'oc1_permission_outcome_timeout',
          ),
        ),
      );
    },
  );

  for (final selectedVision in [true, false]) {
    test(
      'OC1 image prefers saved provider and verifies formatted final blue: $selectedVision',
      () async {
        final wire = _ImageWire(selectedVision: selectedVision);
        addTearDown(wire.close);
        final run = await runProtocol1(
          wire,
          const ProbeOptions(
            directory: '/owned',
            title: 'fq3-owned',
            capabilities: {'image'},
          ),
        );
        expect(run.results['image']!['state'], 'pass');
        expect(run.results['image']!['facts'], {
          'imageAnswerVerified': true,
          'asserted': true,
        });
        expect(wire.selectedModel, {
          'providerID': 'saved',
          'modelID': selectedVision ? 'selected' : 'same-provider-vision',
        });
        expect(
          run.results.toString(),
          isNot(contains('never-emit-config-body')),
        );
      },
    );
  }

  for (final retainImage in [true, false]) {
    test(
      'OC1 image rejects wrong color or missing retained attachment: $retainImage',
      () async {
        final wire = _ImageWire(
          answer: retainImage ? '**red**' : '**blue**',
          retainImage: retainImage,
        );
        addTearDown(wire.close);
        final run = await runProtocol1(
          wire,
          const ProbeOptions(
            directory: '/owned',
            title: 'fq3-owned',
            capabilities: {'image'},
          ),
        );
        expect(run.results['image']!['state'], 'fail');
        expect(
          run.results['image']!['code'],
          retainImage
              ? 'oc1_image_content_unverified'
              : 'oc1_image_not_retained',
        );
      },
    );
  }

  for (final foreignFinal in [true, false]) {
    test(
      'OC1 image rejects foreign final part or wrong model: $foreignFinal',
      () async {
        final wire = _ImageWire(
          foreignFinal: foreignFinal,
          wrongSelection: !foreignFinal,
        );
        addTearDown(wire.close);
        final run = await runProtocol1(
          wire,
          const ProbeOptions(
            directory: '/owned',
            title: 'fq3-owned',
            capabilities: {'image'},
          ),
        );
        expect(run.results['image']!['state'], 'fail');
        expect(
          run.results['image']!['code'],
          foreignFinal
              ? 'oc1_image_content_unverified'
              : 'oc1_image_selection_unobserved',
        );
      },
    );
  }

  for (final response in [
    'valid',
    'foreign_part',
    'pre_final',
    'wrong_model',
    'noncompliant',
    'old_parent_only',
  ]) {
    test(
      'OC1 abort follow-up uses only final owned selected-model reply: $response',
      () async {
        final wire = _AbortWire(response);
        addTearDown(wire.close);
        final run = await runProtocol1(
          wire,
          const ProbeOptions(
            directory: '/owned',
            title: 'fq3-run-opencode-abort',
            capabilities: {'abort'},
          ),
        );
        expect(wire.originalPrompt, isNot(wire.followPrompt));
        expect(wire.followModel, {
          'providerID': 'provider',
          'modelID': 'model',
        });
        final result = run.results['abort']!;
        if (response == 'valid') {
          expect(result['state'], 'pass');
          expect(result['facts'], {
            'interrupted': true,
            'usableAfterAbort': true,
            'asserted': true,
          });
        } else {
          expect(result['state'], 'fail');
          expect(
            result['code'],
            response == 'foreign_part'
                ? 'oc1_after_abort_stale_parts'
                : response == 'wrong_model'
                ? 'oc1_after_abort_model_mismatch'
                : 'oc1_after_abort_reply_mismatch',
          );
        }
        expect(wire.titles, [
          'fq3-run-opencode-abort-session-1',
          'fq3-run-opencode-abort-session-2',
        ]);
      },
    );
  }

  test(
    'OC1 records validated owned session before retained-session read can fail',
    () async {
      final wire = _LedgerWire();
      addTearDown(wire.close);
      final ledger = <String>[];
      final callbackStages = <String>[];
      final run = await runProtocol1(
        wire,
        ProbeOptions(
          directory: '/owned',
          title: 'fq3-run-opencode-create',
          capabilities: const {},
          onSessionCreated: (id) async {
            ledger.add(id);
            callbackStages.add(wire.calls.last);
          },
        ),
      );
      expect(ledger, ['ses_owned']);
      expect(run.sessionIDs, ledger);
      expect(callbackStages, ['POST /session']);
      expect(run.results['create']!['code'], 'fixture_retained_read_failed');
      expect(wire.title, 'fq3-run-opencode-create-session-1');
    },
  );
}
