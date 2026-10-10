import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/none.dart';

/// Every source file TEAM-101 adds; each must stay free of OpenCode API
/// imports so the plugin never grows into the server gateway.
const _team101Sources = [
  'lib/domain/orchestration_gateway.dart',
  'lib/orchestration/models/project.dart',
  'lib/orchestration/models/run.dart',
  'lib/orchestration/models/work.dart',
  'lib/orchestration/models/agent.dart',
  'lib/orchestration/models/gate.dart',
  'lib/orchestration/models/activity_event.dart',
  'lib/orchestration/models/usage.dart',
  'lib/orchestration/events/orchestration_event.dart',
  'lib/orchestration/events/cursor.dart',
  'lib/orchestration/adapters/none.dart',
];

const _garbage = <String?>[
  null,
  '',
  '   ',
  'garbage',
  'OPEN?!',
  'in_progres',
  '\u{1F916}',
  'unknown',
  'null',
];

void main() {
  group('state enums are total', () {
    test('every enum has an unknown member', () {
      expect(WorkState.values, contains(WorkState.unknown));
      expect(RunState.values, contains(RunState.unknown));
      expect(RunKind.values, contains(RunKind.unknown));
      expect(AgentState.values, contains(AgentState.unknown));
      expect(GateKind.values, contains(GateKind.unknown));
    });

    test('fromProvider never throws and maps garbage to unknown', () {
      for (final value in _garbage) {
        expect(
          WorkState.fromProvider(value),
          WorkState.unknown,
          reason: 'WorkState "$value"',
        );
        expect(
          RunState.fromProvider(value),
          RunState.unknown,
          reason: 'RunState "$value"',
        );
        expect(
          RunKind.fromProvider(value),
          RunKind.unknown,
          reason: 'RunKind "$value"',
        );
        expect(
          AgentState.fromProvider(value),
          AgentState.unknown,
          reason: 'AgentState "$value"',
        );
        expect(
          GateKind.fromProvider(value),
          GateKind.unknown,
          reason: 'GateKind "$value"',
        );
      }
    });

    test('product names round-trip through fromProvider', () {
      for (final state in WorkState.values) {
        if (state == WorkState.unknown) continue;
        expect(WorkState.fromProvider(state.name), state, reason: state.name);
      }
      for (final state in RunState.values) {
        if (state == RunState.unknown) continue;
        expect(RunState.fromProvider(state.name), state, reason: state.name);
      }
      for (final state in AgentState.values) {
        if (state == AgentState.unknown) continue;
        expect(AgentState.fromProvider(state.name), state, reason: state.name);
      }
      for (final kind in GateKind.values) {
        if (kind == GateKind.unknown) continue;
        expect(GateKind.fromProvider(kind.name), kind, reason: kind.name);
      }
      for (final kind in RunKind.values) {
        if (kind == RunKind.unknown) continue;
        expect(RunKind.fromProvider(kind.name), kind, reason: kind.name);
      }
    });

    test('Gas City bead status strings map per the work table', () {
      expect(WorkState.fromProvider('open'), WorkState.queued);
      expect(WorkState.fromProvider('in_progress'), WorkState.working);
      expect(WorkState.fromProvider('closed'), WorkState.completed);
      expect(WorkState.fromProvider('In-Progress'), WorkState.working);
      expect(WorkState.fromProvider(' in progress '), WorkState.working);
      expect(WorkState.fromProvider('needsInput'), WorkState.needsInput);
      expect(WorkState.fromProvider('needs-review'), WorkState.review);
      expect(WorkState.fromProvider('needs-input'), WorkState.needsInput);
      expect(WorkState.fromProvider('canceled'), WorkState.cancelled);
    });

    test('Gas City agent and session states map per the agent table', () {
      expect(AgentState.fromProvider('idle'), AgentState.idle);
      expect(AgentState.fromProvider('stopped'), AgentState.stopped);
      expect(AgentState.fromProvider('suspended'), AgentState.stopped);
      expect(AgentState.fromProvider('active'), AgentState.working);
      expect(AgentState.fromProvider('running'), AgentState.working);
      expect(AgentState.fromProvider('error'), AgentState.crashed);
    });

    test('run states and kinds map from provider strings', () {
      expect(RunState.fromProvider('running'), RunState.working);
      expect(RunState.fromProvider('pending'), RunState.planning);
      expect(RunState.fromProvider('done'), RunState.completed);
      expect(RunState.fromProvider('error'), RunState.failed);
      expect(RunState.fromProvider('canceled'), RunState.cancelled);
      expect(RunKind.fromProvider('convoy'), RunKind.batch);
      expect(RunKind.fromProvider('formula'), RunKind.formula);
    });

    test('gate kinds map from provider interaction kinds', () {
      expect(GateKind.fromProvider('select'), GateKind.choice);
      expect(GateKind.fromProvider('confirm'), GateKind.confirmation);
      // The supervisor's own example kind: answered with allow or deny.
      expect(GateKind.fromProvider('tool-approval'), GateKind.confirmation);
      expect(GateKind.fromProvider('free_text'), GateKind.freeText);
      expect(GateKind.fromProvider('text'), GateKind.freeText);
      expect(GateKind.fromProvider('gate'), GateKind.gateBead);
      expect(GateKind.fromProvider('run_failed'), GateKind.runFailed);
      expect(GateKind.fromProvider('review-ready'), GateKind.reviewReady);
    });

    test('WorkState.derive follows the state-table precedence', () {
      expect(WorkState.derive(status: 'open'), WorkState.queued);
      expect(WorkState.derive(status: 'open', isReady: true), WorkState.ready);
      expect(
        WorkState.derive(status: 'in_progress', isBlocked: true),
        WorkState.blocked,
      );
      expect(
        WorkState.derive(status: 'in_progress', sessionWaiting: true),
        WorkState.waiting,
      );
      expect(
        WorkState.derive(status: 'in_progress', needsInput: true),
        WorkState.needsInput,
      );
      expect(
        WorkState.derive(status: 'in_progress', needsReview: true),
        WorkState.review,
      );
      expect(
        WorkState.derive(status: 'in_progress', lastError: 'boom'),
        WorkState.failed,
      );
      expect(
        WorkState.derive(status: 'closed', closedReason: 'cancelled'),
        WorkState.cancelled,
      );
      expect(
        WorkState.derive(status: 'closed', isBlocked: true, lastError: 'x'),
        WorkState.completed,
        reason: 'a closed bead stays completed whatever else is set',
      );
      expect(WorkState.derive(status: 'weird'), WorkState.unknown);
      expect(WorkState.derive(status: null), WorkState.unknown);
    });
  });

  group('models retain raw and rawState', () {
    const raw = <String, Object?>{
      'id': 'gc-1',
      'status': 'weird-status',
      'metadata': {'gc.routed_to': 'polecat-1', 'branch': 'feat/x'},
    };

    test('WorkItem', () {
      const item = WorkItem(
        id: 'gc-1',
        title: 'Do the thing',
        state: WorkState.unknown,
        rawState: 'weird-status',
        raw: raw,
      );
      expect(item.raw, same(raw));
      expect(item.rawState, 'weird-status');
      expect((item.raw['metadata'] as Map)['gc.routed_to'], 'polecat-1');
      expect(item.labels, isEmpty);
      expect(item.dependsOn, isEmpty);
    });

    test('OrchestrationRun', () {
      const run = OrchestrationRun(
        id: 'run-1',
        title: 'formula',
        state: RunState.unknown,
        rawState: 'warming',
        raw: raw,
      );
      expect(run.raw, same(raw));
      expect(run.rawState, 'warming');
      expect(run.kind, RunKind.unknown);
    });

    test('OrchestrationAgent', () {
      const agent = OrchestrationAgent(
        id: 'gastown__polecat-1',
        name: 'polecat-1',
        state: AgentState.stopped,
        rawState: 'suspended',
        raw: raw,
      );
      expect(agent.raw, same(raw));
      expect(agent.rawState, 'suspended');
    });

    test('OrchestrationGate', () {
      const gate = OrchestrationGate(
        id: 'gate-1',
        kind: GateKind.unknown,
        rawKind: 'mystery',
        title: 'Pick one',
        raw: raw,
      );
      expect(gate.raw, same(raw));
      expect(gate.rawKind, 'mystery');
      expect(gate.choices, isEmpty);
    });

    test('OrchestrationProject, ActivityEvent and OrchestrationUsage', () {
      const project = OrchestrationProject(id: 'rig', name: 'rig', raw: raw);
      const event = ActivityEvent(type: 'bead.updated', seq: 7, raw: raw);
      const usage = OrchestrationUsage(raw: raw);
      expect(project.raw, same(raw));
      expect(event.raw, same(raw));
      expect(event.seq, 7);
      expect(usage.raw, same(raw));
      expect(usage.isEmpty, isTrue);
      expect(const OrchestrationUsage(workOpen: 1).isEmpty, isFalse);
    });
  });

  group('events', () {
    test('every subclass carries seq, raw and a type', () {
      const raw = <String, Object?>{'seq': 3};
      const events = <OrchestrationEvent>[
        BeadChanged(beadId: 'b', change: BeadChange.closed, seq: 3, raw: raw),
        RunChanged(runId: 'r', state: RunState.failed, seq: 3, raw: raw),
        SessionChanged(
          sessionId: 's',
          change: SessionChange.woke,
          seq: 3,
          raw: raw,
        ),
        GateChanged(gateId: 'g', resolved: true, seq: 3, raw: raw),
        ActivityAppended(
          event: ActivityEvent(type: 'order.fired', seq: 3),
          seq: 3,
          raw: raw,
        ),
        StreamHeartbeat(seq: 3, raw: raw),
        UnknownOrchestrationEvent(type: 'mail.read', seq: 3, raw: raw),
      ];
      for (final event in events) {
        expect(event.seq, 3, reason: event.runtimeType.toString());
        expect(event.raw, same(raw), reason: event.runtimeType.toString());
        expect(event.type, isNotEmpty, reason: event.runtimeType.toString());
      }
      expect(events[0].type, 'bead.closed');
      expect(events[2].type, 'session.woke');
      expect(events[3].type, 'gate.resolved');
      expect(events[4].type, 'order.fired');
      expect(events[6].type, 'mail.read');
    });

    test('sealed switch is exhaustive over the union', () {
      String describe(OrchestrationEvent event) => switch (event) {
        BeadChanged() => 'bead',
        RunChanged() => 'run',
        SessionChanged() => 'session',
        GateChanged() => 'gate',
        ActivityAppended() => 'activity',
        StreamHeartbeat() => 'heartbeat',
        StreamHeadOnlyReplay() => 'head-only',
        RequestResult() => 'request',
        UnknownOrchestrationEvent() => 'unknown',
      };
      expect(
        describe(const StreamHeadOnlyReplay(requestedSeq: 9, firstSeq: 20)),
        'head-only',
      );
      expect(describe(const StreamHeartbeat()), 'heartbeat');
      expect(
        describe(const UnknownOrchestrationEvent(type: 'controller.started')),
        'unknown',
      );
    });

    test('EventCursor resume helpers', () {
      expect(EventCursor.none.isEmpty, isTrue);
      expect(EventCursor.none.resumeHeaders(), isEmpty);
      expect(EventCursor.none.resumeQuery(), isEmpty);

      final cursor = EventCursor.none.advance(41).advance(42).advance(40);
      expect(cursor.seq, 42, reason: 'never moves backwards');
      expect(cursor.resumeHeaders(), {'Last-Event-ID': '42'});
      expect(cursor.resumeQuery(), {'after_seq': '42'});
      expect(cursor.advance(null), cursor);

      const opaque = EventCursor(lastEventId: 'evt-abc');
      expect(opaque.resumeHeaders(), {'Last-Event-ID': 'evt-abc'});
      expect(opaque.resumeQuery(), isEmpty);
      expect(opaque, const EventCursor(lastEventId: 'evt-abc'));
    });
  });

  group('capabilities constants are consistent', () {
    test('none is all false, fixture is all true', () {
      expect(
        OrchestrationCapabilities.none.asMap().values,
        everyElement(isFalse),
      );
      // The project-level switches belong to the project fixture's own set
      // (the in-app engine); the run fixture leaves them off.
      const projectOnly = {
        'projectLifecycle',
        'livingSpec',
        'projectLanes',
        'projectPlacement',
        'projectVerification',
        'projectMergeQueue',
        'projectPromotion',
        'projectResume',
        'projectBudgets',
        'projectDigest',
      };
      final fixture = OrchestrationCapabilities.fixture.asMap();
      expect(
        fixture.entries
            .where((e) => !projectOnly.contains(e.key))
            .map((e) => e.value),
        everyElement(isTrue),
      );
      expect(
        fixture.entries
            .where((e) => projectOnly.contains(e.key))
            .map((e) => e.value),
        everyElement(isFalse),
      );
      expect(OrchestrationCapabilities.none.anyControl, isFalse);
      expect(OrchestrationCapabilities.fixture.anyControl, isTrue);
    });

    test('asMap covers every field named in the architecture plan', () {
      const names = [
        'projects',
        'projectLifecycle',
        'livingSpec',
        'projectLanes',
        'projectPlacement',
        'projectVerification',
        'projectMergeQueue',
        'projectPromotion',
        'projectResume',
        'projectBudgets',
        'projectDigest',
        'runs',
        'runSteps',
        'workGraph',
        'workReady',
        'agents',
        'agentOutput',
        'sessionLink',
        'gatesInteractions',
        'gatesBeads',
        'usage',
        'eventStream',
        'eventReplay',
        'controlRespond',
        'controlMessage',
        'controlAgent',
        'controlCancelRun',
        'controlAssign',
        'controlCreateWork',
        'controlProject',
        'controlProjectRemove',
        'controlAgentKill',
        'scheduledJobs',
        'controlScheduledJobs',
        'changes',
        'verification',
        'mergeReadiness',
        'phoneHost',
      ];
      expect(OrchestrationCapabilities.none.asMap().keys, orderedEquals(names));
    });

    test('gascityFront is gascityRead plus exactly the control switches and '
        'the merge roles (TEAM-205)', () {
      final read = OrchestrationCapabilities.gascityRead.asMap();
      final front = OrchestrationCapabilities.gascityFront.asMap();
      for (final entry in read.entries) {
        if (entry.value) {
          expect(front[entry.key], isTrue, reason: entry.key);
        }
      }
      final added = front.entries
          .where((e) => e.value && !read[e.key]!)
          .map((e) => e.key)
          .toSet();
      expect(added, {
        'controlRespond',
        'controlMessage',
        'controlAgent',
        'controlCancelRun',
        'controlAssign',
        'controlProject',
        'controlProjectRemove',
        'controlAgentKill',
        'controlScheduledJobs',
        'changes',
        'verification',
        'mergeReadiness',
      });
      expect(OrchestrationCapabilities.gascityRead.anyControl, isFalse);
      expect(OrchestrationCapabilities.gascityFront.anyControl, isTrue);
    });

    test('read path covers what the PC spike proved', () {
      const read = OrchestrationCapabilities.gascityRead;
      expect(read.agents, isTrue);
      expect(read.workGraph, isTrue);
      expect(read.workReady, isTrue);
      expect(read.eventStream, isTrue);
      expect(read.eventReplay, isTrue);
      // `/usage` is read by the run Overview and agent Runtime (TEAM-113).
      expect(read.usage, isTrue);
      expect(OrchestrationCapabilities.gascityFront.usage, isTrue);
      expect(read.phoneHost, isFalse);
    });

    test('plan-named aliases point at the same constants', () {
      expect(noneCapabilities, same(OrchestrationCapabilities.none));
      expect(
        gascityReadCapabilities,
        same(OrchestrationCapabilities.gascityRead),
      );
      expect(
        gascityFrontCapabilities,
        same(OrchestrationCapabilities.gascityFront),
      );
      expect(fixtureCapabilities, same(OrchestrationCapabilities.fixture));
    });
  });

  group('NullOrchestrationGateway', () {
    const gateway = NullOrchestrationGateway();

    test('exposes no capabilities and no host', () {
      expect(gateway.capabilities, same(OrchestrationCapabilities.none));
      expect(gateway.host, isNull);
      expect(gateway.isClosed, isFalse);
    });

    test('reads are empty and never throw', () async {
      expect(await gateway.projects(), isEmpty);
      expect(await gateway.runs(), isEmpty);
      expect(await gateway.runs(projectId: 'p'), isEmpty);
      expect(await gateway.run('r'), isNull);
      expect(await gateway.work(), isEmpty);
      expect(await gateway.readyWork(), isEmpty);
      expect(await gateway.workItem('w'), isNull);
      expect(await gateway.agents(), isEmpty);
      expect(await gateway.agent('a'), isNull);
      expect(await gateway.gates(), isEmpty);
      expect(await gateway.usage(), isNull);
      expect(await gateway.activity(afterSeq: 5, limit: 10), isEmpty);
    });

    test('event stream is empty and closes at once', () async {
      expect(await gateway.events().toList(), isEmpty);
      expect(
        await gateway.events(resumeFrom: const EventCursor(seq: 9)).toList(),
        isEmpty,
      );
    });

    test(
      'controls answer with a rejected receipt carrying the request id',
      () async {
        final receipts = [
          await gateway.respond(
            'g',
            const GateResponse.confirmation(confirmed: true),
            requestId: 'req-1',
          ),
          await gateway.message('a', 'hi', requestId: 'req-2'),
          await gateway.controlAgent(
            'a',
            AgentControlAction.stop,
            requestId: 'req-3',
          ),
          await gateway.cancelRun('r', requestId: 'req-4'),
          await gateway.assign('w', agentId: 'a', requestId: 'req-5'),
        ];
        for (var i = 0; i < receipts.length; i++) {
          expect(receipts[i].status, MutationReceiptStatus.rejected);
          expect(receipts[i].isAccepted, isFalse);
          expect(receipts[i].id, 'req-${i + 1}');
          expect(receipts[i].message, isNotEmpty);
          expect(receipts[i].raw, isEmpty);
        }
      },
    );

    test('close is a no-op', () async {
      await gateway.close();
      expect(gateway.isClosed, isFalse);
    });
  });

  group('MutationReceipt and host identity', () {
    test('status enum covers the four outcomes', () {
      expect(MutationReceiptStatus.values, [
        MutationReceiptStatus.pending,
        MutationReceiptStatus.accepted,
        MutationReceiptStatus.rejected,
        MutationReceiptStatus.timedOut,
      ]);
    });

    test('host identity is a value', () {
      const a = OrchestrationHostIdentity(
        provider: 'gascity',
        version: '0.9.0',
        city: 'gastown',
        url: 'http://127.0.0.1:8090',
        hostMode: OrchestrationHostMode.computer,
      );
      const b = OrchestrationHostIdentity(
        provider: 'gascity',
        version: '0.9.0',
        city: 'gastown',
        url: 'http://127.0.0.1:8090',
        hostMode: OrchestrationHostMode.computer,
      );
      expect(a, b);
      expect(a.hashCode, b.hashCode);
      expect(OrchestrationHostMode.values, [
        OrchestrationHostMode.computer,
        OrchestrationHostMode.phone,
      ]);
    });
  });

  group('source hygiene', () {
    test('no TEAM-101 file imports lib/api or lib/api2', () {
      final offenders = <String>[];
      for (final path in _team101Sources) {
        final file = File(path);
        expect(file.existsSync(), isTrue, reason: '$path is missing');
        final imports = RegExp(
          r'''^\s*(import|export)\s+['"]([^'"]+)['"]''',
          multiLine: true,
        ).allMatches(file.readAsStringSync()).map((m) => m.group(2)!);
        for (final uri in imports) {
          final normalized = uri.replaceAll('package:opencode_mobile/', 'lib/');
          if (RegExp(r'(^|/)api2?/').hasMatch(normalized) ||
              normalized.contains('opencode_sdk')) {
            offenders.add('$path -> $uri');
          }
        }
      }
      expect(offenders, isEmpty);
    });

    test('new files are plain Dart (no Flutter imports)', () {
      for (final path in _team101Sources) {
        final source = File(path).readAsStringSync();
        expect(source, isNot(contains('package:flutter/')), reason: path);
      }
    });
  });

  group('agent output (TEAM-111)', () {
    test('mergeAgentOutput folds cumulative, overlapping and new chunks', () {
      expect(mergeAgentOutput('', 'abc'), 'abc');
      expect(mergeAgentOutput('abc', ''), 'abc');
      // A cumulative transcript appends only its new tail.
      expect(
        mergeAgentOutput('line1\nline2\n', 'line1\nline2\nline3\n'),
        'line1\nline2\nline3\n',
      );
      // A pane capture overlapping the end appends what follows the overlap.
      expect(
        mergeAgentOutput('one\ntwo\nthree\n', 'three\nfour\n'),
        'one\ntwo\nthree\nfour\n',
      );
      // A repeat changes nothing; an unrelated chunk is appended whole.
      expect(mergeAgentOutput('one\ntwo\n', 'one\n'), 'one\ntwo\n');
      expect(mergeAgentOutput('one\n', 'xyz\n'), 'one\nxyz\n');
      // The tail is bounded.
      expect(mergeAgentOutput('abcdef', 'ghij', limit: 5), 'fghij');
    });

    test('parseAgentTranscript reads markers, repeats and prose', () {
      const transcript =
          ' Let\n me\n look\n.\n[tool: bash]\n[tool: ls src]\n[tool: ls src]\n'
          'a.dart\nb.dart\n[tool: pytest -q tests]\n2 passed\n[tool: read]\n'
          '[tool: src/a.dart]\nclass A {}\n Then\n I\n will\n fix\n it\n.\n'
          '[tool: edit]\n[tool: src/a.dart]\n';
      final blocks = parseAgentTranscript(transcript);
      expect(blocks.length, 4);
      expect((blocks[0] as AgentProse).text, 'Let me look.');
      final group = blocks[1] as AgentStepGroup;
      expect(group.steps.length, 3);
      expect(group.steps[0].command, 'ls src');
      expect(group.steps[0].kind, AgentStepKind.command);
      expect(group.steps[0].output, 'a.dart\nb.dart');
      expect(group.steps[1].kind, AgentStepKind.test);
      expect(group.steps[1].output, '2 passed');
      expect(group.steps[2].tool, 'read');
      expect(group.steps[2].kind, AgentStepKind.read);
      expect(group.steps[2].output, 'class A {}');
      expect((blocks[2] as AgentProse).text, 'Then I will fix it.');
      final edit = blocks[3] as AgentStepGroup;
      expect(edit.steps.single.kind, AgentStepKind.edit);
      expect(parseAgentTranscript(''), isEmpty);
      expect(parseAgentTranscript('\n\n'), isEmpty);
    });
  });
}
