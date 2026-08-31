import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/models.dart';

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

  test('replays a captured prompt round-trip into typed events', () {
    final envelopes = capturedEnvelopes();
    expect(envelopes.length, greaterThan(15));
    expect(envelopes.any((e) => e.event is UnknownApi2Event), isFalse,
        reason: 'every captured event type must be recognized');

    expect(envelopes.first.event, isA<Api2ServerConnectedEvent>());

    final created = envelopes
        .firstWhere((e) => e.event is Api2SessionCreatedEvent);
    final createdEvent = created.event as Api2SessionCreatedEvent;
    expect(createdEvent.sessionID, startsWith('ses_'));
    expect(createdEvent.title, 'api2 fixture probe');
    expect(createdEvent.version, '0.0.0-beta-18600');
    expect(created.durable?.seq, 0);
    expect(created.durable?.aggregateID, createdEvent.sessionID);
    expect(created.location?.directory, '/home/dev/projects/oc_app');
    expect(created.isDurable, isTrue);

    final enqueued = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionInboxEvent>()
        .firstWhere((e) => e.phase == Api2Phase.enqueued);
    expect(enqueued.inboxID, startsWith('msg_'));
    expect(enqueued.item?.type, 'user');
    expect(enqueued.item?.promptText, contains('pong'));
    expect(enqueued.delivery, Api2Delivery.steer);

    final steps = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionStepEvent>()
        .toList();
    expect(steps.any((s) => s.phase == Api2Phase.started), isTrue);
    expect(steps.any((s) => s.phase == Api2Phase.streamed), isTrue);
    final stepStarted =
        steps.firstWhere((s) => s.phase == Api2Phase.started);
    expect(stepStarted.assistantMessageID, startsWith('msg_'));
    expect(stepStarted.agent, 'build');
    expect(stepStarted.model?.providerID, 'openai');
    final stepEnded = steps.firstWhere((s) => s.phase == Api2Phase.ended);
    expect(stepEnded.finish, isNotNull);
    expect(stepEnded.tokens?.input, greaterThan(0));

    final textEvents = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionTextEvent>()
        .toList();
    final delta = textEvents.firstWhere((e) => e.phase == Api2Phase.delta);
    expect(delta.delta, isNotEmpty);
    final ended = textEvents.firstWhere((e) => e.phase == Api2Phase.ended);
    expect(ended.text, contains('pong'));
    final deltaEnvelope = envelopes
        .firstWhere((e) => e.type == 'session.text.delta');
    expect(deltaEnvelope.isDurable, isFalse,
        reason: 'deltas are ephemeral: no durable marker');

    final toolInput = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionToolInputEvent>()
        .toList();
    expect(toolInput.any((e) => e.phase == Api2Phase.started), isTrue);
    expect(
      toolInput.firstWhere((e) => e.phase == Api2Phase.ended).text,
      isNotNull,
    );

    final called = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionToolCalledEvent>()
        .first;
    expect(called.callID, isNotEmpty);
    expect(called.input['path'], 'README.md');

    final success = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionToolResultEvent>()
        .first;
    expect(success.succeeded, isTrue);
    expect(success.content.whereType<Api2ToolResultText>().first.text,
        contains('OpenCode for Android'));

    final usage = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionUsageUpdatedEvent>()
        .first;
    expect(usage.tokens.input, greaterThan(0));

    final execution = envelopes
        .map((e) => e.event)
        .whereType<Api2SessionExecutionEvent>()
        .toList();
    expect(execution.first.phase, Api2Phase.started);
    expect(execution.last.phase, Api2Phase.succeeded);
    expect(execution.last.finished, isTrue);

    expect(
      envelopes.map((e) => e.event).whereType<Api2SessionDeletedEvent>(),
      isNotEmpty,
    );
  });

  test('unknown event types fall back to UnknownApi2Event without throwing', () {
    final envelope = Api2EventEnvelope.fromJson({
      'id': 'evt_1',
      'created': 5,
      'type': 'session.quantum.entangled',
      'data': {'sessionID': 'ses_1', 'spin': 'up'},
      'durable': {'aggregateID': 'ses_1', 'seq': 9, 'version': 1},
    });
    expect(envelope.type, 'session.quantum.entangled');
    final unknown = envelope.event as UnknownApi2Event;
    expect(unknown.raw['spin'], 'up');
    expect(envelope.durable?.seq, 9);
    expect(envelope.data['sessionID'], 'ses_1');

    final malformed = Api2EventEnvelope.fromJson({
      'type': 'session.text.delta',
      'data': {'ordinal': 'not-a-number'},
    });
    expect(malformed.event, isA<Api2SessionTextEvent>());
    expect((malformed.event as Api2SessionTextEvent).ordinal, 0);

    final empty = Api2EventEnvelope.fromJson(const {});
    expect(empty.type, '');
    expect(empty.event, isA<UnknownApi2Event>());
  });

  test('parses the log.synced boundary marker (bare, no envelope)', () {
    final envelope = Api2EventEnvelope.fromJson({
      'type': 'log.synced',
      'aggregateID': 'ses_fb53',
      'seq': 10,
    });
    final synced = envelope.event as Api2LogSyncedEvent;
    expect(synced.aggregateID, 'ses_fb53');
    expect(synced.seq, 10);

    final emptyLog = Api2EventEnvelope.fromJson({
      'type': 'log.synced',
      'aggregateID': 'ses_empty',
    });
    expect((emptyLog.event as Api2LogSyncedEvent).seq, isNull);
  });

  test('parses permission, form, status, mcp, and vcs events', () {
    final asked = Api2EventEnvelope.fromJson({
      'type': 'permission.asked',
      'data': {
        'id': 'per_1',
        'sessionID': 'ses_1',
        'action': 'bash',
        'resources': ['rm -rf*'],
        'source': {'type': 'tool', 'messageID': 'msg_1', 'id': 'call_1'},
      },
    }).event as Api2PermissionAskedEvent;
    expect(asked.request.action, 'bash');
    expect(asked.request.resources, ['rm -rf*']);

    final replied = Api2EventEnvelope.fromJson({
      'type': 'permission.replied',
      'data': {'sessionID': 'ses_1', 'requestID': 'per_1', 'reply': 'once'},
    }).event as Api2PermissionRepliedEvent;
    expect(replied.reply, 'once');

    final formCreated = Api2EventEnvelope.fromJson({
      'type': 'form.created',
      'data': {
        'form': {
          'id': 'frm_1',
          'sessionID': 'ses_1',
          'title': 'Pick one',
          'fields': [
            {'key': 'a', 'type': 'boolean'},
          ],
        },
      },
    }).event as Api2FormCreatedEvent;
    expect(formCreated.form.fields.single.type, Api2FormFieldType.boolean);

    final formReplied = Api2EventEnvelope.fromJson({
      'type': 'form.replied',
      'data': {
        'id': 'frm_1',
        'sessionID': 'ses_1',
        'answer': {'a': true},
      },
    }).event as Api2FormRepliedEvent;
    expect(formReplied.answer['a'], isTrue);

    final status = Api2EventEnvelope.fromJson({
      'type': 'session.status',
      'data': {
        'sessionID': 'ses_1',
        'status': {
          'type': 'retry',
          'attempt': 2,
          'message': 'overloaded',
          'next': 1787961300000,
        },
      },
    }).event as Api2SessionStatusEvent;
    expect(status.status, Api2SessionRunStatus.retry);
    expect(status.attempt, 2);
    expect(status.message, 'overloaded');

    final idle = Api2EventEnvelope.fromJson({
      'type': 'session.idle',
      'data': {'sessionID': 'ses_1'},
    }).event as Api2SessionStatusEvent;
    expect(idle.status, Api2SessionRunStatus.idle);

    final futureStatus = Api2EventEnvelope.fromJson({
      'type': 'session.status',
      'data': {
        'sessionID': 'ses_1',
        'status': {'type': 'hibernating'},
      },
    }).event as Api2SessionStatusEvent;
    expect(futureStatus.status, Api2SessionRunStatus.unknown);
    expect(futureStatus.raw['type'], 'hibernating');

    final mcp = Api2EventEnvelope.fromJson({
      'type': 'mcp.status.changed',
      'data': {'server': 'github'},
    }).event as Api2McpChangedEvent;
    expect(mcp.kind, 'status');
    expect(mcp.server, 'github');

    final vcs = Api2EventEnvelope.fromJson({
      'type': 'vcs.branch.updated',
      'data': {'branch': 'port/api2-core'},
    }).event as Api2VcsBranchUpdatedEvent;
    expect(vcs.branch, 'port/api2-core');

    final refresh = Api2EventEnvelope.fromJson({
      'type': 'agent.updated',
      'data': {},
    }).event as Api2RefreshHintEvent;
    expect(refresh.topic, 'agent.updated');

    final interrupted = Api2EventEnvelope.fromJson({
      'type': 'session.execution.interrupted',
      'data': {'sessionID': 'ses_1', 'reason': 'user'},
    }).event as Api2SessionExecutionEvent;
    expect(interrupted.phase, Api2Phase.interrupted);
    expect(interrupted.reason, 'user');

    final failed = Api2EventEnvelope.fromJson({
      'type': 'session.execution.failed',
      'data': {
        'sessionID': 'ses_1',
        'error': {'type': 'overloaded_error', 'message': 'try later', 'status': 529},
      },
    }).event as Api2SessionExecutionEvent;
    expect(failed.error?.type, 'overloaded_error');
    expect(failed.error?.status, 529);
  });
}
