import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/oc2.dart';

// Stable 2.0.10 source: ShellTool, Permission, SessionStep, and
// SessionMessageUpdater at b8cedc1a7a5e2916bbb65dc1d4b620729c261638.
// This fixture models a genuine assistant call, not Permission.ask admission.
void main() {
  Future<ProbeRun> probe(_PermissionWire wire, {required bool allow}) {
    addTearDown(wire.close);
    return runProtocol2(
      wire,
      ProbeOptions(
        directory: _PermissionWire.directory,
        title: 'FQ3 permission contract',
        model: 'opencode/big-pickle',
        capabilities: {allow ? 'permissionAllow' : 'permissionDeny'},
      ),
    );
  }

  test(
    'stable Allow proves shell ask, owned reply and command output',
    () async {
      final wire = _PermissionWire();
      final run = await probe(wire, allow: true);
      expect(run.results['permissionAllow']?['state'], 'pass');
      expect(
        run.results['permissionAllow']?['facts'],
        containsPair('toolOutcomeVerified', true),
      );
      expect(wire.createBodies.last['permissions'], [
        {'action': 'shell', 'resource': '*', 'effect': 'ask'},
      ]);
      expect(wire.promptText, contains('Use the shell tool'));
      expect(wire.replies, ['once']);
      expect(wire.pending, isEmpty);
      expect(wire.active, isEmpty);
      expect(wire.pendingReadsAfterReply, greaterThan(0));
      expect(wire.activeReadsAfterReply, greaterThan(0));
      expect(
        wire.events.any((e) => e['type'] == 'session.tool.success'),
        isTrue,
      );
    },
  );

  test(
    'stable plain Reject proves aborted call and interrupted idle',
    () async {
      final wire = _PermissionWire();
      final run = await probe(wire, allow: false);
      expect(run.results['permissionDeny']?['state'], 'pass');
      expect(wire.replies, ['reject']);
      expect(wire.pending, isEmpty);
      expect(wire.active, isEmpty);
      expect(wire.pendingReadsAfterReply, greaterThan(0));
      expect(wire.activeReadsAfterReply, greaterThan(0));
      expect(
        wire.events.where((e) => e['type'] == 'session.execution.succeeded'),
        isEmpty,
      );
      final tool = wire.transcripts[wire.probeID]!.last['content'] as List;
      expect((tool.single as Map)['state'], containsPair('status', 'error'));
      expect(wire.events.last['type'], 'session.execution.interrupted');
      expect((wire.events.last['data'] as Map)['reason'], 'shutdown');
    },
  );

  for (final fault in [
    _Fault.foreignAskedSession,
    _Fault.foreignPendingRequest,
    _Fault.foreignSourceCall,
    _Fault.wrongCommand,
    _Fault.foreignReplySession,
    _Fault.foreignReplyRequest,
    _Fault.missingReply,
    _Fault.missingRetainedCall,
    _Fault.pendingRetained,
    _Fault.activeRetained,
  ]) {
    for (final allow in [true, false]) {
      test('${allow ? 'Allow' : 'Deny'} rejects ${fault.name}', () async {
        final wire = _PermissionWire(fault: fault);
        final run = await probe(wire, allow: allow);
        final result =
            run.results[allow ? 'permissionAllow' : 'permissionDeny'];
        expect(result?['state'], 'fail');
        expect(result?['facts'], isEmpty);
      });
    }
  }

  for (final fault in [
    _Fault.missingSuccess,
    _Fault.foreignSuccessCall,
    _Fault.foreignSuccessSession,
    _Fault.wrongOutput,
  ]) {
    test('Allow rejects ${fault.name}', () async {
      final run = await probe(_PermissionWire(fault: fault), allow: true);
      expect(run.results['permissionAllow']?['state'], 'fail');
    });
  }

  for (final fault in [
    _Fault.interruptedAlone,
    _Fault.foreignFailureCall,
    _Fault.foreignFailureSession,
    _Fault.wrongFailureType,
    _Fault.runningRetainedCall,
    _Fault.successAfterReject,
    _Fault.progressAfterReject,
    _Fault.commandStartAfterReject,
  ]) {
    test('Deny rejects ${fault.name}', () async {
      final run = await probe(_PermissionWire(fault: fault), allow: false);
      expect(run.results['permissionDeny']?['state'], 'fail');
    });
  }
}

enum _Fault {
  none,
  foreignAskedSession,
  foreignPendingRequest,
  foreignSourceCall,
  wrongCommand,
  foreignReplySession,
  foreignReplyRequest,
  missingReply,
  missingRetainedCall,
  pendingRetained,
  activeRetained,
  missingSuccess,
  foreignSuccessCall,
  foreignSuccessSession,
  wrongOutput,
  interruptedAlone,
  foreignFailureCall,
  foreignFailureSession,
  wrongFailureType,
  runningRetainedCall,
  successAfterReject,
  progressAfterReject,
  commandStartAfterReject,
}

class _PermissionWire extends Fq3Wire {
  static const directory = '/fq3/permission-contract';
  static const model = {'providerID': 'opencode', 'id': 'big-pickle'};
  static const requestID = 'per_owned';
  static const callID = 'call_owned';
  static const messageID = 'msg_owned';
  final _Fault fault;
  final sessions = <String, Map<String, dynamic>>{};
  final transcripts = <String, List<Map<String, dynamic>>>{};
  final createBodies = <Map<String, dynamic>>[];
  final pending = <String, Map<String, dynamic>>{};
  final active = <String>{};
  final replies = <String>[];
  String? probeID;
  String? promptText;
  String command = '';
  int pendingReadsAfterReply = 0;
  int activeReadsAfterReply = 0;

  _PermissionWire({this.fault = _Fault.none})
    : super(baseUrl: 'http://127.0.0.1:1', password: 'fixture');

  @override
  bool get isStableOc2 => true;

  void emit(String type, String id, [Map<String, dynamic> data = const {}]) {
    events.add({
      'id': 'evt_${events.length}',
      'type': type,
      'location': {'directory': directory},
      'data': {'sessionID': id, ...data},
    });
  }

  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    final data = body as Map? ?? const {};
    if (method == 'GET' && path == '/api/health') {
      return {'healthy': true, 'version': '2.0.10'};
    }
    if (method == 'GET' && path == '/api/model') {
      expect(query, {'location[directory]': directory});
      return {
        'data': [
          {...model, 'enabled': true},
        ],
      };
    }
    if (method == 'GET' && path == '/api/session/active') {
      if (replies.isNotEmpty) activeReadsAfterReply++;
      return {
        'data': {
          for (final id in active) id: {'type': 'running'},
        },
      };
    }
    if (method == 'POST' && path == '/api/session') {
      final id = 'ses_${sessions.length + 1}';
      final info = <String, dynamic>{
        'id': id,
        'location': {'directory': directory},
        'model': data['model'] ?? model,
      };
      createBodies.add(Map<String, dynamic>.from(data));
      sessions[id] = info;
      transcripts[id] = [];
      return {'data': info};
    }
    final parts = path.split('/');
    if (parts.length < 4 || parts[2] != 'session') {
      throw const ProbeFailure('unexpected_fixture_request');
    }
    final id = parts[3];
    if (!sessions.containsKey(id)) {
      throw const ProbeFailure('foreign_fixture_session');
    }
    if (method == 'GET' && parts.length == 4) return {'data': sessions[id]};
    if (method == 'GET' && parts[4] == 'message') {
      return {'data': transcripts[id]};
    }
    if (method == 'GET' && parts[4] == 'permission') {
      if (replies.isNotEmpty) pendingReadsAfterReply++;
      return {
        'data': [if (pending[id] != null) pending[id]],
      };
    }
    if (method == 'POST' && parts[4] == 'prompt') {
      probeID = id;
      promptText = data['text'] as String;
      final rules = sessions.length > 1
          ? createBodies.last['permissions']
          : null;
      if (rules is! List ||
          rules.length != 1 ||
          (rules.single as Map)['action'] != 'shell' ||
          (rules.single as Map)['resource'] != '*' ||
          (rules.single as Map)['effect'] != 'ask' ||
          !promptText!.contains('Use the shell tool')) {
        throw const ProbeFailure('stable_shell_contract_mismatch');
      }
      command = promptText!.contains('FQ3_ALLOW')
          ? 'printf FQ3_ALLOW'
          : 'printf FQ3_DENY';
      startCall(id);
      return {
        'data': {'id': 'inbox_owned'},
      };
    }
    if (method == 'POST' &&
        parts.length == 7 &&
        parts[4] == 'permission' &&
        parts[6] == 'reply') {
      expect(parts[5], requestID);
      // Adapter vocabulary is beta reply. Fq3Wire performs the separately
      // tested stable HTTP decision mapping; native events still use reply.
      final reply = data['reply'] as String;
      expect(reply, anyOf('once', 'reject'));
      expect(data.keys, ['reply']);
      replies.add(reply);
      settleCall(id, allow: reply == 'once');
      return null;
    }
    throw const ProbeFailure('unexpected_fixture_request');
  }

  void startCall(String id) {
    active.add(id);
    emit('session.execution.started', id);
    transcripts[id]!.add({
      'id': messageID,
      'sessionID': id,
      'type': 'assistant',
      'model': model,
      'time': {'created': 1},
      'content': [
        {
          'type': 'tool',
          'id': callID,
          'name': 'shell',
          'executed': false,
          'state': {
            'status': 'running',
            'input': {
              'command': fault == _Fault.wrongCommand
                  ? 'printf OTHER'
                  : command,
            },
            'metadata': <String, dynamic>{},
          },
        },
      ],
    });
    final request = <String, dynamic>{
      'id': requestID,
      'sessionID': id,
      'action': 'shell',
      'resources': [command],
      'save': ['printf *'],
      'source': {
        'type': 'tool',
        'messageID': messageID,
        'id': fault == _Fault.foreignSourceCall ? 'call_foreign' : callID,
      },
    };
    pending[id] = fault == _Fault.foreignPendingRequest
        ? {...request, 'id': 'per_foreign', 'sessionID': 'ses_foreign'}
        : request;
    emit('permission.asked', id, {
      ...request,
      if (fault == _Fault.foreignAskedSession) 'sessionID': 'ses_foreign',
    });
  }

  void settleCall(String id, {required bool allow}) {
    if (fault != _Fault.pendingRetained) pending.remove(id);
    if (fault != _Fault.missingReply) {
      emit(
        'permission.replied',
        fault == _Fault.foreignReplySession ? 'ses_foreign' : id,
        {
          'requestID': fault == _Fault.foreignReplyRequest
              ? 'per_foreign'
              : requestID,
          'reply': allow ? 'once' : 'reject',
        },
      );
    }
    final assistant = transcripts[id]!.last;
    final tool = (assistant['content'] as List).single as Map<String, dynamic>;
    final input = (tool['state'] as Map)['input'];
    final failure = {
      'type': fault == _Fault.wrongFailureType ? 'tool.execution' : 'aborted',
      'message': 'The user declined this tool call',
    };
    final output = [
      {
        'type': 'text',
        'text': fault == _Fault.wrongOutput ? 'OTHER' : 'FQ3_ALLOW',
      },
    ];
    tool['state'] = allow
        ? {'status': 'completed', 'input': input, 'content': output}
        : {'status': 'error', 'input': input, 'error': failure};
    assistant['time'] = {'created': 1, 'completed': 2};
    assistant['finish'] = allow ? 'stop' : 'error';
    if (!allow) {
      assistant['error'] = {'type': 'aborted', 'message': 'Step interrupted'};
    }
    if (fault == _Fault.runningRetainedCall ||
        fault == _Fault.interruptedAlone) {
      tool['state'] = {'status': 'running', 'input': input, 'metadata': {}};
    }
    if (fault == _Fault.missingRetainedCall) assistant['content'] = [];
    if (allow) {
      if (fault != _Fault.missingSuccess) {
        emit(
          'session.tool.success',
          fault == _Fault.foreignSuccessSession ? 'ses_foreign' : id,
          {
            'assistantMessageID': messageID,
            'id': fault == _Fault.foreignSuccessCall ? 'call_foreign' : callID,
            'content': output,
            'executed': false,
          },
        );
      }
      emit('session.execution.succeeded', id);
    } else {
      if (fault != _Fault.interruptedAlone) {
        emit(
          'session.tool.failed',
          fault == _Fault.foreignFailureSession ? 'ses_foreign' : id,
          {
            'assistantMessageID': messageID,
            'id': fault == _Fault.foreignFailureCall ? 'call_foreign' : callID,
            'error': failure,
            'executed': false,
          },
        );
      }
      if (fault == _Fault.successAfterReject) {
        emit('session.tool.success', id, {
          'assistantMessageID': messageID,
          'id': callID,
          'content': output,
          'executed': false,
        });
      }
      if (fault == _Fault.progressAfterReject) {
        emit('session.tool.progress', id, {
          'assistantMessageID': messageID,
          'id': callID,
          'metadata': {'shellID': 'shell_owned'},
        });
      }
      if (fault == _Fault.commandStartAfterReject) {
        emit('session.shell.started', id, {
          'shell': {
            'id': 'shell_owned',
            'command': command,
            'status': 'running',
          },
        });
      }
      emit('session.step.failed', id, {
        'assistantMessageID': messageID,
        'error': {'type': 'aborted', 'message': 'Step interrupted'},
      });
      emit('session.execution.interrupted', id, {'reason': 'shutdown'});
    }
    if (fault != _Fault.activeRetained) active.remove(id);
  }

  @override
  Future<void> openEvents(String path, {Map<String, String>? query}) async {
    expect(path, '/api/event');
    expect(query, {'location[directory]': directory});
  }

  @override
  Future<Map<String, dynamic>> waitFor(
    bool Function(Map<String, dynamic>) predicate, {
    Duration timeout = const Duration(seconds: 60),
  }) async =>
      events.where(predicate).lastOrNull ??
      (throw const ProbeFailure('fixture_event_missing'));

  @override
  Future<void> closeEvents() async {}
}
