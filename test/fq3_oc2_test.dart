import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/oc2.dart';

void main() {
  Future<ProbeRun> probe(_Wire wire, Set<String> capabilities) async {
    addTearDown(wire.close);
    return runProtocol2(
      wire,
      ProbeOptions(
        directory: '/fq3/disposable',
        title: 'FQ3 owned',
        capabilities: capabilities,
      ),
    );
  }

  test('stream requires completed inference and a fresh owned delta', () async {
    final wire = _Wire();
    final result = await probe(wire, {'stream'});
    expect(result.results['stream']?['state'], 'pass');
    expect(result.results.containsKey('abort'), isFalse);
    expect(result.historyCounts.values, contains(2));
  });

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
          {'action': 'bash', 'resource': '*', 'effect': 'ask'},
        ],
        [
          {'action': 'bash', 'resource': '*', 'effect': 'ask'},
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
    'card receipt requires an actual executed completed assistant call',
    () async {
      final result = await probe(_Wire()..fabricatedCard = true, {'cards'});
      expect(result.results['cards']?['code'], 'cards_tool_call_missing');
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
  bool foreignPermission = false;
  bool stable = false;
  bool wrongImageAnswer = false;
  bool fabricatedCard = false;
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

  @override
  bool get isStableOc2 => stable;

  void emit(String type, String id, [Map<String, dynamic> data = const {}]) {
    events.add({
      'id': 'evt_${events.length}',
      'type': type,
      'location': {'directory': '/fq3/disposable'},
      'data': {'sessionID': id, ...data},
    });
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
      transcripts[id]!.add(assistant(id, text, content: content));
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
      expect(query, {'location[directory]': '/fq3/disposable'});
      return {
        'data': [
          for (final model in [
            if (foreignCatalogFirst) {'id': 'external', 'providerID': 'other'},
            primary,
            secondary,
          ])
            {
              ...model,
              'enabled': true,
              'capabilities': {
                'input': [
                  'text',
                  if (model != primary || primaryImage) 'image',
                ],
              },
            },
        ],
      };
    }
    if (path == '/api/model/default') return {'data': primary};
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
        'model': data['model'] ?? primary,
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
      emit('session.model.selected', id, {'model': data['model']});
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
      (tool.first as Map)['state'] = {
        'status': reply == 'once' ? 'completed' : 'error',
      };
      complete(id, 'Outcome reported.');
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
    if (text.contains('10000')) {
      if (!omitAbortDelta) emit('session.text.delta', id, {'delta': '1\n'});
      if (idleAbort) active.remove(id);
      return {
        'data': {'id': 'inbox'},
      };
    }
    if (text.contains('Use the bash tool')) {
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
              'name': 'bash',
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
        'action': 'bash',
        'resources': [command],
        'source': {'type': 'tool', 'id': 'call_permission'},
      };
      pending[id] = request;
      emit('permission.asked', id, request);
      return {
        'data': {'id': 'inbox'},
      };
    }
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
            'id': 'call_card',
            'name': 'oc-ui_show',
            'executed': !fabricatedCard,
            'state': {
              'status': 'completed',
              'input': {
                'v': 1,
                'id': 'fq3-confirm',
                'ask': {'kind': 'confirm'},
              },
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
