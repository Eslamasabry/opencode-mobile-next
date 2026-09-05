import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';

List<Api2EventEnvelope> capturedEnvelopes() {
  final lines = File('test/fixtures/api2/events.sse').readAsLinesSync();
  final envelopes = <Api2EventEnvelope>[];
  for (final line in lines) {
    if (!line.startsWith('data:')) continue;
    final json = jsonDecode(line.substring(5).trim());
    envelopes.add(Api2EventEnvelope.fromJson(json as Map<String, dynamic>));
  }
  return envelopes;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('adapts the captured prompt round-trip into v1 envelopes', () {
    final adapter = Api2EventAdapter();
    final out = <EventEnvelope>[];
    for (final envelope in capturedEnvelopes()) {
      out.addAll(adapter.adapt(envelope));
    }
    final types = out.map((event) => event.type).toList();

    // Connection + session lifecycle.
    expect(types.first, 'server.connected');
    expect(types, contains('session.created'));
    expect(types, contains('session.deleted'));

    // Busy/idle transitions from execution events.
    final statuses = out
        .where((event) => event.type == 'session.status')
        .map((event) => (event.properties['status'] as Map)['type'].toString())
        .toList();
    expect(statuses.first, 'busy');
    expect(
      types.indexOf('session.idle'),
      greaterThan(types.indexOf('session.status')),
      reason: 'execution.succeeded must land as the v1 idle transition',
    );

    // The enqueued user prompt becomes a v1 user message + text part.
    final userUpdate = out.firstWhere(
      (event) =>
          event.type == 'message.updated' &&
          (event.properties['info'] as Map)['role'] == 'user',
    );
    final userInfo = userUpdate.properties['info'] as Map;
    expect(userInfo['id'], startsWith('msg_'));
    final userPart = out.firstWhere(
      (event) =>
          event.type == 'message.part.updated' &&
          (event.properties['part'] as Map)['messageID'] == userInfo['id'],
    );
    expect(
      ((userPart.properties['part'] as Map)['text'] as String),
      contains('pong'),
    );

    // Assistant streaming: step.started announces the message.
    final assistantUpdates = out
        .where(
          (event) =>
              event.type == 'message.updated' &&
              (event.properties['info'] as Map)['role'] == 'assistant',
        )
        .toList();
    expect(assistantUpdates, isNotEmpty);
    final first = assistantUpdates.first.properties['info'] as Map;
    expect(first['id'], startsWith('msg_'));
    expect(first['providerID'], 'openai');
    expect(first['modelID'], 'gpt-5.6-sol');
    expect((first['time'] as Map)['completed'], isNull);
    // step.ended marks completion with usage.
    final completed = assistantUpdates.firstWhere(
      (event) =>
          ((event.properties['info'] as Map)['time'] as Map)['completed'] !=
          null,
    );
    expect((completed.properties['info'] as Map)['tokens'], isA<Map>());

    // Text streaming: started (empty part) → delta → ended (full text).
    final textParts = out
        .where(
          (event) =>
              event.type == 'message.part.updated' &&
              (event.properties['part'] as Map)['type'] == 'text',
        )
        .toList();
    expect(textParts.length, greaterThanOrEqualTo(2));
    final deltas = out
        .where(
          (event) =>
              event.type == 'message.part.delta' &&
              event.properties['field'] == 'text',
        )
        .toList();
    expect(deltas, isNotEmpty);
    expect(deltas.first.properties['partID'], startsWith('text-'));
    expect(deltas.first.properties['delta'], isNotEmpty);
    final fullText = textParts
        .map((event) => (event.properties['part'] as Map)['text'] as String)
        .firstWhere((text) => text.contains('pong'));
    expect(fullText, contains('pong'));

    // Reasoning parts stream with their own IDs.
    expect(
      out.any(
        (event) =>
            event.type == 'message.part.updated' &&
            (event.properties['part'] as Map)['type'] == 'reasoning' &&
            ((event.properties['part'] as Map)['id'] as String).startsWith(
              'reasoning-',
            ),
      ),
      isTrue,
    );

    // Tool lifecycle: pending input → running (called) → completed with the
    // tool name remembered from input.started.
    final toolParts = out
        .where(
          (event) =>
              event.type == 'message.part.updated' &&
              (event.properties['part'] as Map)['type'] == 'tool',
        )
        .map((event) => event.properties['part'] as Map)
        .toList();
    expect(toolParts, isNotEmpty);
    expect(toolParts.first['tool'], 'read');
    expect((toolParts.first['state'] as Map)['status'], 'pending');
    final running = toolParts.firstWhere(
      (part) => (part['state'] as Map)['status'] == 'running',
    );
    expect(((running['state'] as Map)['input'] as Map)['path'], 'README.md');
    final done = toolParts.firstWhere(
      (part) => (part['state'] as Map)['status'] == 'completed',
    );
    expect(done['tool'], 'read');
    expect(done['callID'], isNotEmpty);
    expect(
      ((done['state'] as Map)['input'] as Map)['path'],
      'README.md',
      reason: 'input from tool.called must survive to the result',
    );
    expect((done['state'] as Map)['output'], contains('OpenCode for Android'));

    // Instruction deltas invalidate the note editor without exposing entries.
    final instructions = out.where(
      (e) => e.type == 'session.instructions.updated',
    );
    expect(instructions, isNotEmpty);
    expect(instructions.first.properties.keys, ['sessionID']);
    // Live usage passes through under its v2 type so the controller can
    // merge cost/tokens into the stored session.
    final usage = out.where((e) => e.type == 'session.usage.updated');
    expect(usage, hasLength(2));
    expect(usage.first.properties['sessionID'], startsWith('ses_'));
    expect(usage.first.properties['cost'], isA<num>());
    expect((usage.first.properties['tokens'] as Map)['input'], isA<int>());
    // Inbox bookkeeping passes through under its v2 type for the
    // pending-sends state.
    expect(types, contains('session.inbox.delivered'));
  });

  test('session.status idle maps to the v1 session.idle event', () {
    final adapter = Api2EventAdapter();
    final idle = adaptApi2EventJson(adapter, {
      'type': 'session.status',
      'data': {
        'sessionID': 'ses_x',
        'status': {'type': 'idle'},
      },
    });
    expect(idle.single.type, 'session.idle');
    expect(idle.single.properties['sessionID'], 'ses_x');

    final busy = adaptApi2EventJson(adapter, {
      'type': 'session.status',
      'data': {
        'sessionID': 'ses_x',
        'status': {'type': 'busy'},
      },
    });
    expect(busy.single.type, 'session.status');
    expect((busy.single.properties['status'] as Map)['type'], 'busy');
  });

  test('execution failure surfaces as session.error', () {
    final adapter = Api2EventAdapter();
    final out = adaptApi2EventJson(adapter, {
      'type': 'session.execution.failed',
      'data': {
        'sessionID': 'ses_x',
        'error': {'type': 'ProviderError', 'message': 'model exploded'},
      },
    });
    expect(out.single.type, 'session.error');
    expect(
      (out.single.properties['error'] as Map)['message'],
      'model exploded',
    );

    final done = adaptApi2EventJson(adapter, {
      'type': 'session.execution.succeeded',
      'data': {'sessionID': 'ses_x'},
    });
    expect(done.single.type, 'session.idle');

    final interrupted = adaptApi2EventJson(adapter, {
      'type': 'session.execution.interrupted',
      'data': {'sessionID': 'ses_x', 'reason': 'user'},
    });
    expect(interrupted.single.type, 'session.idle');
  });

  test('permission.asked maps to the v2-shaped v1 envelope', () {
    final adapter = Api2EventAdapter();
    final out = adaptApi2EventJson(adapter, {
      'type': 'permission.asked',
      'data': {
        'id': 'per_1',
        'sessionID': 'ses_x',
        'action': 'bash',
        'resources': ['rm -rf*'],
        'save': ['rm -rf*'],
        'metadata': {'command': 'rm -rf /tmp/x'},
        'source': {'type': 'tool', 'messageID': 'msg_1', 'id': 'call_1'},
      },
    });
    final envelope = out.single;
    expect(envelope.type, 'permission.v2.asked');
    expect(envelope.properties['id'], 'per_1');
    expect(envelope.properties['sessionID'], 'ses_x');
    expect(envelope.properties['action'], 'bash');
    expect(envelope.properties['resources'], ['rm -rf*']);
    expect(envelope.properties['save'], ['rm -rf*']);
    // The v1 PermissionTool parser reads callID from the source map.
    expect((envelope.properties['source'] as Map)['callID'], 'call_1');

    final replied = adaptApi2EventJson(adapter, {
      'type': 'permission.replied',
      'data': {'sessionID': 'ses_x', 'requestID': 'per_1', 'reply': 'once'},
    });
    expect(replied.single.type, 'permission.v2.replied');
    expect(replied.single.properties['requestID'], 'per_1');
  });

  test('tool input deltas append through the v1 delta contract', () {
    final adapter = Api2EventAdapter();
    adaptApi2EventJson(adapter, {
      'type': 'session.tool.input.started',
      'data': {
        'sessionID': 'ses_x',
        'assistantMessageID': 'msg_a',
        'id': 'call_1',
        'name': 'bash',
      },
    });
    final delta = adaptApi2EventJson(adapter, {
      'type': 'session.tool.input.delta',
      'data': {
        'sessionID': 'ses_x',
        'assistantMessageID': 'msg_a',
        'id': 'call_1',
        'delta': '{"command":',
      },
    }).single;
    expect(delta.type, 'message.part.delta');
    expect(delta.properties['partID'], 'call_1');
    expect(delta.properties['field'], 'state.raw');
    expect(delta.properties['delta'], '{"command":');
  });

  test('session renames and refresh hints map to their v1 names', () {
    final adapter = Api2EventAdapter();
    final renamed = adaptApi2EventJson(adapter, {
      'type': 'session.renamed',
      'data': {'sessionID': 'ses_x', 'title': 'New title'},
    }).single;
    expect(renamed.type, 'session.metadata.updated');
    expect((renamed.properties['info'] as Map)['title'], 'New title');

    expect(
      adaptApi2EventJson(adapter, {
        'type': 'catalog.updated',
        'data': <String, dynamic>{},
      }).single.type,
      'catalog.updated',
    );
    expect(
      adaptApi2EventJson(adapter, {
        'type': 'credential.updated',
        'data': <String, dynamic>{},
      }).single.type,
      'integration.connection.updated',
    );
    expect(
      adaptApi2EventJson(adapter, {
        'type': 'installation.update-available',
        'data': {'version': '0.0.0-beta-19000'},
      }).single.properties['version'],
      '0.0.0-beta-19000',
    );
    // Forms surface under the form.v2.* envelope family.
    expect(
      adaptApi2EventJson(adapter, {
        'type': 'form.created',
        'data': {
          'form': {
            'id': 'frm_1',
            'sessionID': 'ses_x',
            'fields': [
              {'key': 'a', 'type': 'string'},
            ],
          },
        },
      }).single.type,
      'form.v2.created',
    );
    // Unknown v2-only types emit nothing rather than inventing envelopes.
    expect(
      adaptApi2EventJson(adapter, {
        'type': 'made.up.event',
        'data': <String, dynamic>{},
      }),
      isEmpty,
    );
  });
}
