import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/oc2.dart';

void main() {
  Future<ProbeRun> probe(
    _Wire wire,
    Set<String> capabilities, {
    Future<void> Function(String id)? onSessionCreated,
    String? model,
  }) async {
    addTearDown(wire.close);
    return runProtocol2(
      wire,
      ProbeOptions(
        directory: '/fq3/disposable',
        title: 'FQ3 owned',
        capabilities: capabilities,
        onSessionCreated: onSessionCreated,
        model: model,
      ),
    );
  }

  test(
    'model catalog waits for initial empty plugin snapshot to settle',
    () async {
      final wire = _Wire()..emptyCatalogReads = 2;
      addTearDown(wire.close);
      final models = await waitOc2ModelCatalog(
        wire,
        directory: '/fq3/disposable',
        interval: Duration.zero,
      );
      expect(models, hasLength(2));
      expect(wire.catalogReads, 3);
    },
  );

  test(
    'a requested model must become enabled before polling succeeds',
    () async {
      final wire = _Wire()..disableSecondaryReads = 1;
      addTearDown(wire.close);
      final models = await waitOc2ModelCatalog(
        wire,
        directory: '/fq3/disposable',
        model: 'test/two',
        interval: Duration.zero,
      );
      expect(models.any((m) => m['id'] == 'two'), isTrue);
      expect(wire.catalogReads, 2);
    },
  );

  test(
    'empty catalog after bounded settlement is a failed prerequisite',
    () async {
      final wire = _Wire()..emptyCatalogReads = 100;
      addTearDown(wire.close);
      await expectLater(
        waitOc2ModelCatalog(
          wire,
          directory: '/fq3/disposable',
          timeout: Duration.zero,
        ),
        throwsA(
          isA<ProbeFailure>().having(
            (e) => e.code,
            'fixed code',
            'enabled_model_missing',
          ),
        ),
      );
      expect(wire.catalogReads, 1);
    },
  );

  test(
    'missing requested model stays failed despite other enabled models',
    () async {
      final wire = _Wire();
      addTearDown(wire.close);
      await expectLater(
        waitOc2ModelCatalog(
          wire,
          directory: '/fq3/disposable',
          model: 'test/missing',
          timeout: Duration.zero,
        ),
        throwsA(
          isA<ProbeFailure>().having(
            (e) => e.code,
            'fixed code',
            'configured_model_unavailable',
          ),
        ),
      );
    },
  );

  test('stream requires completed inference and a fresh owned delta', () async {
    final wire = _Wire();
    final result = await probe(wire, {'stream'});
    expect(result.results['stream']?['state'], 'pass');
    expect(result.results.containsKey('abort'), isFalse);
    expect(result.historyCounts.values, contains(2));
  });

  test('rolling event cache retains fresh diagnostic observations', () async {
    final wire = _Wire()..rollingEventCache = true;
    final result = await probe(wire, {'image'});
    expect(result.results['image']?['state'], 'pass');
    expect(result.observations['image']?['executionSucceededCount'], 1);
    expect(result.observations['image']?['textDeltaCount'], 1);
    expect(result.observations['image']?['toolProgressCount'], 0);
  });

  test(
    'explicit enabled UI model overrides a retrying backend default',
    () async {
      final wire = _Wire()..uiSelectionFixture = true;
      final result = await probe(wire, {
        'stream',
      }, model: 'opencode/big-pickle');
      expect(result.results['models']?['state'], 'pass');
      expect(result.results['stream']?['state'], 'pass');
      expect(wire.createBodies.last['model'], {
        'id': 'big-pickle',
        'providerID': 'opencode',
      });
      final assistant = wire.transcripts['ses_2']!.singleWhere(
        (message) => message['type'] == 'assistant',
      );
      expect(assistant['model'], wire.createBodies.last['model']);
      expect(
        wire.events.where((e) => e['type'] == 'session.retry.scheduled'),
        isEmpty,
      );
    },
  );

  test(
    'enabled backend default retrying HTTP 503 remains unqualified',
    () async {
      final wire = _Wire()..uiSelectionFixture = true;
      final result = await probe(wire, {'stream'});
      expect(result.results['models']?['state'], 'pass');
      expect(result.results['stream']?['code'], 'timeout');
      expect(wire.createBodies.last['model'], _Wire.backendDefault);
      expect(
        wire.events.where((e) => e['type'] == 'session.retry.scheduled'),
        hasLength(10),
      );
      expect(
        wire.events.where((e) => e['type'] == 'session.text.delta'),
        isEmpty,
      );
      expect(wire.active, contains('ses_2'));
    },
  );

  test(
    'explicit working model admission still requires actual inference',
    () async {
      final wire = _Wire()
        ..uiSelectionFixture = true
        ..omitAssistant = true;
      final result = await probe(wire, {
        'stream',
      }, model: 'opencode/big-pickle');
      expect(result.results['stream']?['code'], 'completed_assistant_missing');
    },
  );

  test(
    'model switch records the retrying alternative without qualifying or falling back',
    () async {
      final wire = _Wire()..uiSelectionFixture = true;
      final result = await probe(wire, {
        'modelSwitch',
      }, model: 'opencode/big-pickle');
      expect(result.results['modelSwitch']?['state'], 'fail');
      expect(result.results['modelSwitch']?['code'], 'timeout');
      expect(result.results['modelSwitch']?['facts'], isEmpty);
      expect(wire.createBodies, hasLength(2));
      expect(wire.createBodies.last['model'], {
        'id': 'big-pickle',
        'providerID': 'opencode',
      });
      expect(wire.sessions['ses_2']?['model'], _Wire.backendDefault);
      expect(
        wire.paths.where((path) => path == '/api/session/ses_2/model'),
        hasLength(1),
      );
      expect(
        wire.paths.where((path) => path.endsWith('/prompt')),
        hasLength(1),
      );
      final observed = result.observations['modelSwitch'];
      expect(observed?['model'], 'opencode/exo-free');
      expect(observed?['stage'], 'await_terminal');
      expect(observed?['modelSelectedCount'], 1);
      expect(observed?['retryCount'], 10);
      expect(observed?['http503Retries'], 10);
      expect(observed?['terminalCount'], 0);
      expect(wire.active, contains('ses_2'));
    },
  );

  test(
    'nonvision selected model uses the retrying vision model and remains failed',
    () async {
      final wire = _Wire()..uiSelectionFixture = true;
      final result = await probe(wire, {'image'}, model: 'opencode/big-pickle');
      expect(result.results['image']?['state'], 'fail');
      expect(result.results['image']?['code'], 'timeout');
      expect(result.results['image']?['facts'], isEmpty);
      expect(wire.createBodies, hasLength(2));
      expect(wire.createBodies.last['model'], _Wire.backendDefault);
      expect(wire.imageSubmitted, isTrue);
      expect(
        wire.paths.where((path) => path.endsWith('/prompt')),
        hasLength(1),
      );
      final observed = result.observations['image'];
      expect(observed?['model'], 'opencode/exo-free');
      expect(observed?['stage'], 'await_terminal');
      expect(observed?['retryCount'], 10);
      expect(observed?['http503Retries'], 10);
      expect(observed?['terminalCount'], 0);
      expect(wire.active, contains('ses_2'));
    },
  );

  test(
    'retained model without an owned selection event fails at selection observation',
    () async {
      for (final mismatch in ['absent', 'foreign']) {
        final wire = _Wire()
          ..uiSelectionFixture = true
          ..selectionEventMismatch = mismatch;
        final result = await probe(wire, {
          'modelSwitch',
        }, model: 'opencode/big-pickle');
        expect(
          result.results['modelSwitch']?['state'],
          'fail',
          reason: mismatch,
        );
        expect(
          result.results['modelSwitch']?['code'],
          'timeout',
          reason: mismatch,
        );
        final readback = await wire.request('GET', '/api/session/ses_2');
        expect(readback['data']['model'], _Wire.backendDefault);
        expect(wire.paths.where((path) => path.endsWith('/prompt')), isEmpty);
        final observed = result.observations['modelSwitch'];
        expect(observed?['model'], 'opencode/exo-free', reason: mismatch);
        expect(observed?['stage'], 'await_selection', reason: mismatch);
        expect(observed?['modelSelectedCount'], 0, reason: mismatch);
        expect(observed?['retryCount'], 0, reason: mismatch);
        expect(observed?['terminalCount'], 0, reason: mismatch);
      }
    },
  );

  test(
    'owned session titles preserve phase title and add unique ordinals',
    () async {
      final wire = _Wire();
      final ledger = <String>[];
      final result = await probe(wire, {
        'stream',
        'modelSwitch',
      }, onSessionCreated: (id) async => ledger.add(id));
      expect(result.results['stream']?['state'], 'pass');
      expect(result.results['modelSwitch']?['state'], 'pass');
      expect(wire.createBodies.map((body) => body['title']), [
        'FQ3 owned-session-1',
        'FQ3 owned-session-2',
        'FQ3 owned-session-3',
      ]);
      expect(ledger, result.sessionIDs);
    },
  );

  test(
    'session ledger failure retains owned ID and fails before prompt',
    () async {
      final wire = _Wire();
      final ledger = <String>[];
      final result = await probe(
        wire,
        {},
        onSessionCreated: (id) async {
          ledger.add(id);
          throw const ProbeFailure('session_ledger_failed');
        },
      );
      expect(result.results['create']?['code'], 'session_ledger_failed');
      expect(result.sessionIDs, ['ses_1']);
      expect(ledger, result.sessionIDs);
      expect(wire.paths.where((path) => path.endsWith('/prompt')), isEmpty);
    },
  );

  test(
    'prompt admission and delta cannot substitute for assistant inference',
    () async {
      final result = await probe(_Wire()..omitAssistant = true, {'stream'});
      expect(result.results['stream']?['code'], 'completed_assistant_missing');
    },
  );

  test('foreign or stale delta cannot certify current stream', () async {
    final result = await probe(_Wire()..foreignDelta = true, {'stream'});
    expect(result.results['stream']?['code'], 'stream_delta_missing');
  });

  test('selected alternative must actually produce the answer', () async {
    final result = await probe(_Wire()..wrongInferenceModel = true, {
      'modelSwitch',
    });
    expect(
      result.results['modelSwitch']?['code'],
      'assistant_answer_not_verified',
    );
  });

  test(
    'abort requires a running session and a usable subsequent turn',
    () async {
      final result = await probe(_Wire(), {'abort'});
      expect(result.results['abort']?['state'], 'pass');
      final facts = result.results['abort']?['facts'] as Map;
      expect(facts['usableAfterAbort'], isTrue);
    },
  );

  test('idle interrupt false is never an abort pass', () async {
    final result = await probe(_Wire()..idleAbort = true, {'abort'});
    expect(result.results['abort']?['code'], 'abort_requires_running_session');
  });

  test(
    'execution start without an owned delta is not a mid-stream abort',
    () async {
      final wire = _Wire()..omitAbortDelta = true;
      final result = await probe(wire, {'abort'});
      expect(result.results['abort']?['code'], 'timeout');
      expect(wire.paths.where((p) => p.endsWith('/interrupt')), isEmpty);
    },
  );

  test(
    'switch prefers selected provider and image prefers selected model',
    () async {
      final wire = _Wire()..foreignCatalogFirst = true;
      final result = await probe(wire, {'modelSwitch', 'image'});
      expect(result.results['modelSwitch']?['state'], 'pass');
      expect(result.results['image']?['state'], 'pass');
      expect(wire.sessions['ses_2']?['model'], _Wire.secondary);
      expect(wire.sessions['ses_3']?['model'], _Wire.primary);
    },
  );

  test(
    'image fallback prefers selected provider before other providers',
    () async {
      final wire = _Wire()
        ..foreignCatalogFirst = true
        ..primaryImage = false;
      final result = await probe(wire, {'image'});
      expect(result.results['image']?['state'], 'pass');
      expect(wire.sessions['ses_2']?['model'], _Wire.secondary);
    },
  );

  test('reconnect refetches owned state without replay parameters', () async {
    final wire = _Wire();
    final result = await probe(wire, {'stream', 'reconnect'});
    expect(result.results['reconnect']?['state'], 'pass');
    expect(wire.reconnects, 1);
    expect(
      wire.eventQueries.every(
        (q) => q.keys.toSet().difference({'location[directory]'}).isEmpty,
      ),
      isTrue,
    );
    expect(
      wire.paths,
      containsAll([
        '/api/session',
        '/api/session/ses_2/message',
        '/api/session/ses_2/permission',
        '/api/session/ses_2/form',
      ]),
    );
  });

  test(
    'allow and deny use real own-session requests and distinct outcomes',
    () async {
      final wire = _Wire();
      final result = await probe(wire, {'permissionAllow', 'permissionDeny'});
      expect(result.results['permissionAllow']?['state'], 'pass');
      expect(result.results['permissionDeny']?['state'], 'pass');
      expect(wire.permissionReplies, ['once', 'reject']);
    },
  );

  test('stable MCP inventory must belong to the requested location', () async {
    for (final directory in ['/other/project', '', null, 7]) {
      final wire = _Wire()
        ..stable = true
        ..cardMcpDirectory = directory;
      final result = await probe(wire, {'cards'});
      expect(
        result.results['cards']?['code'],
        'cards_inventory_scope_mismatch',
      );
      expect(wire.paths.where((path) => path.endsWith('/prompt')), isEmpty);
      expect(wire.createBodies, hasLength(1));
    }
  });

  test('foreign permission cannot receive approval', () async {
    final wire = _Wire()..foreignPermission = true;
    final result = await probe(wire, {'permissionAllow'});
    expect(result.results['permissionAllow']?['state'], 'fail');
    expect(wire.permissionReplies, isEmpty);
  });

  test(
    'stable Ask rules are scoped to disposable permission sessions',
    () async {
      final wire = _Wire()..stable = true;
      final result = await probe(wire, {
        'stream',
        'permissionAllow',
        'permissionDeny',
      });
      expect(result.results['permissionAllow']?['state'], 'pass');
      expect(result.results['permissionDeny']?['state'], 'pass');
      expect(
        wire.createBodies.take(2).every((b) => !b.containsKey('permissions')),
        isTrue,
      );
      expect(wire.createBodies.skip(2).map((b) => b['permissions']), [
        [
          {'action': 'shell', 'resource': '*', 'effect': 'ask'},
        ],
        [
          {'action': 'shell', 'resource': '*', 'effect': 'ask'},
        ],
      ]);
      expect(wire.paths.any((p) => p.contains('/config')), isFalse);
    },
  );

  test(
    'beta permission probes do not send stable-only session rules',
    () async {
      final wire = _Wire();
      await probe(wire, {'permissionAllow', 'permissionDeny'});
      expect(
        wire.createBodies.every((b) => !b.containsKey('permissions')),
        isTrue,
      );
    },
  );

  test('image submission without semantic answer fails', () async {
    final result = await probe(_Wire()..wrongImageAnswer = true, {'image'});
    expect(result.results['image']?['code'], 'image_content_answer_missing');
  });

  test('image answer checks unlabeled pixel fixture colors', () async {
    final wire = _Wire();
    final result = await probe(wire, {'image'});
    expect(result.results['image']?['state'], 'pass');
    expect(wire.imageSubmitted, isTrue);
  });

  test(
    'retained card without fresh matching tool success cannot pass',
    () async {
      final result = await probe(_Wire()..fabricatedCard = true, {'cards'});
      expect(result.results['cards']?['state'], 'fail');
      expect(
        result.results['cards']?['facts'] as Map,
        isNot(containsPair('answerReceipt', true)),
      );
    },
  );

  test(
    'completed local MCP card with provider executed false passes',
    () async {
      final wire = _Wire()
        ..stable = true
        ..localCard = true;
      final result = await probe(wire, {'cards'});
      expect(result.results['cards']?['state'], 'pass');
      expect(
        result.results['cards']?['facts'],
        containsPair('cardsToolCall', true),
      );
      expect(
        result.results['cards']?['facts'],
        containsPair('answerReceipt', true),
      );
      final call = wire.transcripts.values
          .expand((messages) => messages)
          .where((m) => m['type'] == 'assistant')
          .expand((m) => m['content'] as List)
          .whereType<Map>()
          .singleWhere((part) => part['type'] == 'tool');
      expect(call['executed'], isFalse);
      expect((call['state'] as Map)['status'], 'completed');
      expect(
        wire.events.where((event) => event['type'] == 'session.tool.success'),
        hasLength(1),
      );
    },
  );

  test(
    'unavailable MCP helper fails before a card prompt or receipt',
    () async {
      for (final status in [
        'missing',
        'pending',
        'disabled',
        'failed',
        'needs_auth',
      ]) {
        final wire = _Wire()..cardMcpStatus = status;
        final result = await probe(wire, {'cards'});
        expect(result.results['cards']?['state'], 'fail', reason: status);
        expect(wire.paths.where((path) => path.endsWith('/prompt')), isEmpty);
      }
    },
  );

  test(
    'card success must be fresh and match session location message and call',
    () async {
      for (final mismatch in [
        'session',
        'location',
        'message',
        'call',
        'stale',
      ]) {
        final wire = _Wire()..cardSuccessMismatch = mismatch;
        final result = await probe(wire, {'cards'});
        expect(result.results['cards']?['state'], 'fail', reason: mismatch);
        expect(
          wire.transcripts.values
              .expand((messages) => messages)
              .where(
                (message) =>
                    message['type'] == 'user' &&
                    (message['text'] as String).startsWith('[oc-ui answer '),
              ),
          isEmpty,
          reason: mismatch,
        );
      }
    },
  );

  test(
    'pending errored or malformed retained card calls stay failed',
    () async {
      for (final state in [
        'pending',
        'running',
        'error',
        'wrong-input',
        'missing-id',
        'missing-output',
      ]) {
        final result = await probe(_Wire()..cardState = state, {'cards'});
        expect(result.results['cards']?['state'], 'fail', reason: state);
      }
      final wrongModel = await probe(_Wire()..wrongInferenceModel = true, {
        'cards',
      });
      expect(wrongModel.results['cards']?['state'], 'fail');
    },
  );

  test(
    'card answer is retained with actual tool call ID and acknowledged',
    () async {
      final wire = _Wire();
      final result = await probe(wire, {'cards'});
      expect(result.results['cards']?['state'], 'pass');
      final receipt = wire.transcripts.values
          .expand((m) => m)
          .where((m) => m['type'] == 'user')
          .map((m) => m['text'] as String)
          .firstWhere((t) => t.startsWith('[oc-ui answer '));
      expect(jsonDecode(receipt.split('\n').last), {
        'v': 1,
        'cardId': 'fq3-confirm',
        'callId': 'call_card',
        'value': {'confirm': true},
      });
    },
  );

  test('unretained answer receipt cannot pass cards', () async {
    final result = await probe(_Wire()..omitReceipt = true, {'cards'});
    expect(result.results['cards']?['code'], 'cards_user_receipt_missing');
  });
}

/// Controlled wire fixtures intentionally separate admission, execution,
/// event ownership, retained tool state, and semantic assistant output.
class _Wire extends Fq3Wire {
  _Wire() : super(baseUrl: 'http://127.0.0.1:1', password: 'test-only');
  bool omitAssistant = false;
  bool foreignDelta = false;
  bool wrongInferenceModel = false;
  bool idleAbort = false;
  bool omitAbortDelta = false;
  bool foreignCatalogFirst = false;
  bool primaryImage = true;
  bool uiSelectionFixture = false;
  String? selectionEventMismatch;
  int emptyCatalogReads = 0;
  int disableSecondaryReads = 0;
  int catalogReads = 0;
  bool foreignPermission = false;
  bool stable = false;
  bool rollingEventCache = false;
  Object? cardMcpDirectory = '/fq3/disposable';
  bool wrongImageAnswer = false;
  bool fabricatedCard = false;
  bool localCard = false;
  String cardMcpStatus = 'connected';
  String? cardSuccessMismatch;
  String cardState = 'completed';
  bool omitReceipt = false;
  bool imageSubmitted = false;
  int reconnects = 0;
  final paths = <String>[];
  final eventQueries = <Map<String, String>>[];
  final permissionReplies = <String>[];
  final createBodies = <Map<String, dynamic>>[];
  final transcripts = <String, List<Map<String, dynamic>>>{};
  final sessions = <String, Map<String, dynamic>>{};
  final pending = <String, Map<String, dynamic>>{};
  final active = <String>{};
  int messageNumber = 0;
  static const primary = {'id': 'one', 'providerID': 'test'};
  static const secondary = {'id': 'two', 'providerID': 'test'};
  static const backendDefault = {'id': 'exo-free', 'providerID': 'opencode'};
  static const uiModel = {
    'id': 'opencode/big-pickle',
    'modelID': 'big-pickle',
    'providerID': 'opencode',
  };

  @override
  bool get isStableOc2 => stable;

  void emit(String type, String id, [Map<String, dynamic> data = const {}]) {
    events.add({
      'id': 'evt_${events.length}',
      'type': type,
      'location': {'directory': '/fq3/disposable'},
      'data': {'sessionID': id, ...data},
    });
    if (rollingEventCache && events.length > 2000) events.removeAt(0);
  }

  Map<String, dynamic> assistant(
    String id,
    String text, {
    List<Map<String, dynamic>>? content,
  }) => {
    'id': 'msg_${++messageNumber}',
    'type': 'assistant',
    'model': wrongInferenceModel
        ? {'id': 'wrong', 'providerID': 'test'}
        : sessions[id]!['model'],
    'time': {'created': 1, 'completed': 2},
    'finish': 'stop',
    'content':
        content ??
        [
          {'type': 'text', 'text': text},
        ],
  };

  void complete(String id, String text, {List<Map<String, dynamic>>? content}) {
    if (!omitAssistant) {
      final message = assistant(id, text, content: content);
      transcripts[id]!.add(message);
      if (content != null &&
          !fabricatedCard &&
          cardSuccessMismatch != 'stale') {
        for (final tool in content.where((part) => part['type'] == 'tool')) {
          emit(
            'session.tool.success',
            cardSuccessMismatch == 'session' ? 'ses_foreign' : id,
            {
              'id': cardSuccessMismatch == 'call' ? 'call_foreign' : tool['id'],
              'assistantMessageID': cardSuccessMismatch == 'message'
                  ? 'msg_foreign'
                  : message['id'],
              'content': [
                {
                  'type': 'text',
                  'text': 'Card accepted for display in OpenCode Mobile.',
                },
              ],
              'executed': !localCard,
            },
          );
          if (cardSuccessMismatch == 'location') {
            events.last['location'] = {'directory': '/foreign/project'};
          }
        }
      }
    }
    emit('session.text.delta', foreignDelta ? 'ses_foreign' : id, {
      'delta': text,
    });
    emit('session.execution.succeeded', id);
    active.remove(id);
  }

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    paths.add(path);
    expect(path, startsWith('/api/'));
    final data = body as Map? ?? {};
    if (path == '/api/health') return {'healthy': true, 'version': 'beta-test'};
    if (path == '/api/model') {
      catalogReads++;
      expect(query, {'location[directory]': '/fq3/disposable'});
      if (catalogReads <= emptyCatalogReads) return {'data': []};
      return {
        'data': [
          for (final model in [
            if (uiSelectionFixture) ...[
              backendDefault,
              uiModel,
            ] else ...[
              if (foreignCatalogFirst)
                {'id': 'external', 'providerID': 'other'},
              primary,
              secondary,
            ],
          ])
            {
              ...model,
              'enabled':
                  model != secondary || catalogReads > disableSecondaryReads,
              'capabilities': {
                'input': [
                  'text',
                  if (model != uiModel && (model != primary || primaryImage))
                    'image',
                ],
              },
            },
        ],
      };
    }
    if (path == '/api/model/default') {
      return {'data': uiSelectionFixture ? backendDefault : primary};
    }
    if (path == '/api/mcp') {
      expect(method, 'GET');
      expect(query, {'location[directory]': '/fq3/disposable'});
      return {
        'location': {'directory': cardMcpDirectory},
        'data': [
          if (cardMcpStatus != 'missing')
            {
              'name': 'oc-ui',
              'status': {'status': cardMcpStatus},
            },
        ],
      };
    }
    if (path == '/api/session/active') {
      return {
        'data': {
          for (final id in active) id: {'type': 'running'},
        },
      };
    }
    if (path == '/api/session') {
      if (method == 'GET') return {'data': sessions.values.toList()};
      createBodies.add(Map<String, dynamic>.from(data));
      final id = 'ses_${sessions.length + 1}';
      final info = {
        'id': id,
        'model':
            data['model'] ?? (uiSelectionFixture ? backendDefault : primary),
        'location': data['location'],
      };
      sessions[id] = info;
      transcripts[id] = [];
      return {'data': info};
    }
    final parts = path.split('/');
    final id = parts[3];
    expect(sessions.containsKey(id), isTrue, reason: 'Owned session only');
    if (parts.length == 4) return {'data': sessions[id]};
    final operation = parts[4];
    if (operation == 'model') {
      sessions[id]!['model'] = data['model'];
      if (selectionEventMismatch != 'absent') {
        emit(
          'session.model.selected',
          selectionEventMismatch == 'foreign' ? 'ses_foreign' : id,
          {'model': data['model']},
        );
      }
      return null;
    }
    if (operation == 'message') return {'data': transcripts[id]};
    if (operation == 'form') return {'data': []};
    if (operation == 'permission') {
      if (method == 'GET') {
        return {
          'data': [if (pending[id] != null) pending[id]],
        };
      }
      expect(parts[5], pending[id]!['id']);
      final reply = data['reply'] as String;
      permissionReplies.add(reply);
      final request = pending.remove(id)!;
      emit('permission.replied', id, {
        'requestID': request['id'],
        'reply': reply,
      });
      final tool = transcripts[id]!.last['content'] as List;
      final call = tool.first as Map;
      final input = (call['state'] as Map)['input'];
      call['state'] = {
        'status': reply == 'once' ? 'completed' : 'error',
        'input': input,
        if (reply == 'once')
          'content': [
            {'type': 'text', 'text': 'FQ3_ALLOW'},
            if (stable) {'type': 'text', 'text': 'Command exited with code 0.'},
          ],
        if (reply == 'once' && stable)
          'metadata': {'exit': 0, 'truncated': false},
        if (reply == 'reject')
          'error': {'type': 'aborted', 'message': 'Permission rejected.'},
      };
      if (stable) {
        call['executed'] = false;
        final message = transcripts[id]!.last;
        (message['time'] as Map)['completed'] = 2;
        emit(
          reply == 'once' ? 'session.tool.success' : 'session.tool.failed',
          id,
          {
            'id': call['id'],
            'assistantMessageID': message['id'],
            'executed': false,
            if (reply == 'once')
              'content': [
                {'type': 'text', 'text': 'FQ3_ALLOW'},
                {'type': 'text', 'text': 'Command exited with code 0.'},
              ],
            if (reply == 'once') 'metadata': {'exit': 0, 'truncated': false},
            if (reply == 'reject')
              'error': {'type': 'aborted', 'message': 'Permission rejected.'},
          },
        );
        if (reply == 'reject') {
          emit('session.execution.interrupted', id);
          active.remove(id);
          return null;
        }
      }
      complete(id, stable ? 'FQ3_ALLOW' : 'Outcome reported.');
      return null;
    }
    if (operation == 'interrupt') {
      final interrupted = active.remove(id);
      if (interrupted) emit('session.execution.interrupted', id);
      return {'interrupted': interrupted};
    }
    expect(operation, 'prompt');
    final text = data['text'] as String;
    if (!(omitReceipt && text.startsWith('[oc-ui answer '))) {
      transcripts[id]!.add({
        'id': 'msg_${++messageNumber}',
        'type': 'user',
        'text': text,
      });
    }
    emit('session.execution.started', id);
    active.add(id);
    if (data['files'] != null) {
      final uri = ((data['files'] as List).single as Map)['uri'] as String;
      expect(uri, startsWith('data:image/png;base64,'));
      expect(base64Decode(uri.split(',').last).take(8), [
        137,
        80,
        78,
        71,
        13,
        10,
        26,
        10,
      ]);
      imageSubmitted = true;
    }
    if (uiSelectionFixture &&
        (sessions[id]!['model'] as Map)['id'] == 'exo-free') {
      final unfinished = assistant(id, '');
      (unfinished['time'] as Map).remove('completed');
      unfinished.remove('finish');
      transcripts[id]!.add(unfinished);
      for (var attempt = 1; attempt <= 10; attempt++) {
        emit('session.retry.scheduled', id, {
          'attempt': attempt,
          'error': {'statusCode': 503},
        });
      }
      return {
        'data': {'id': 'inbox'},
      };
    }
    if (text.contains('10000')) {
      if (!omitAbortDelta) emit('session.text.delta', id, {'delta': '1\n'});
      if (idleAbort) active.remove(id);
      return {
        'data': {'id': 'inbox'},
      };
    }
    if (text.contains('Use the bash tool') ||
        text.contains('Use the shell tool')) {
      final name = stable ? 'shell' : 'bash';
      expect(text, contains('Use the $name tool'));
      final command = text.contains('FQ3_ALLOW')
          ? 'printf FQ3_ALLOW'
          : 'printf FQ3_DENY';
      transcripts[id]!.add(
        assistant(
          id,
          '',
          content: [
            {
              'type': 'tool',
              'id': 'call_permission',
              'name': name,
              'state': {
                'status': 'running',
                'input': {'command': command},
              },
            },
          ],
        ),
      );
      final request = {
        'id': 'per_1',
        'sessionID': foreignPermission ? 'ses_foreign' : id,
        'action': name,
        'resources': [command],
        'source': {
          'type': 'tool',
          'id': 'call_permission',
          'messageID': transcripts[id]!.last['id'],
        },
      };
      pending[id] = request;
      emit('permission.asked', id, request);
      return {
        'data': {'id': 'inbox'},
      };
    }
    if (data['files'] != null) {
      complete(
        id,
        wrongImageAnswer ? 'Image received.' : '{"left":"red","right":"blue"}',
      );
    } else if (text.contains('Call the oc-ui_show tool')) {
      complete(
        id,
        '',
        content: [
          {
            'type': 'tool',
            'id': cardState == 'missing-id' ? '' : 'call_card',
            'name': 'oc-ui_show',
            'executed': !localCard,
            'time': {'created': 1, 'ran': 1, 'completed': 2},
            'state': {
              'status': ['pending', 'running', 'error'].contains(cardState)
                  ? cardState
                  : 'completed',
              'input': {
                'v': 1,
                'id': cardState == 'wrong-input' ? 'other-card' : 'fq3-confirm',
                'title': 'FQ3 confirmation',
                'body': [
                  {'type': 'text', 'text': 'Confirm this disposable probe.'},
                ],
                'ask': {'kind': 'confirm'},
              },
              if (cardState != 'missing-output')
                'content': [
                  {
                    'type': 'text',
                    'text': 'Card accepted for display in OpenCode Mobile.',
                  },
                ],
            },
          },
        ],
      );
    } else {
      final answer = text.startsWith('[oc-ui answer ')
          ? 'FQ3_CARD_CONFIRMED'
          : RegExp(r'Reply exactly ([A-Z0-9_]+)\.').firstMatch(text)!.group(1)!;
      complete(id, answer);
    }
    return {
      'data': {'id': 'inbox'},
    };
  }

  @override
  Future<void> openEvents(String path, {Map<String, String>? query}) async {
    expect(path, '/api/event');
    eventQueries.add(Map.from(query ?? {}));
    if (rollingEventCache) {
      for (var i = 0; i < 2000; i++) {
        emit('session.tool.progress', 'ses_foreign');
      }
    }
    if (cardSuccessMismatch == 'stale') {
      emit('session.tool.success', 'ses_2', {
        'id': 'call_card',
        'assistantMessageID': 'msg_2',
        'content': [
          {
            'type': 'text',
            'text': 'Card accepted for display in OpenCode Mobile.',
          },
        ],
        'executed': true,
      });
    }
  }

  @override
  Future<Map<String, dynamic>> waitFor(
    bool Function(Map<String, dynamic>) predicate, {
    Duration timeout = const Duration(seconds: 60),
  }) async =>
      events.where(predicate).lastOrNull ??
      (throw const ProbeFailure('timeout'));

  @override
  Future<void> closeEvents() async {}

  @override
  void reconnectHttp() => reconnects++;
}
