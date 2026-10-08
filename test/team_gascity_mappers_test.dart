import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/dto/dto.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_mappers.dart';
import 'package:opencode_mobile/orchestration/dispatch.dart'
    show workHandedToMerge;
import 'support/team_gascity_mappers_fixtures.dart';

List<File> _files(String sub, String ext) =>
    Directory(
        '${fixtureRoot.path}/$sub',
      ).listSync().whereType<File>().where((f) => f.path.endsWith(ext)).toList()
      ..sort((a, b) => a.path.compareTo(b.path));

/// Every DTO decoder, so any object can be pushed through all of them.
final List<Object Function(Map<String, Object?>)> _allDecoders = [
  GcHealth.fromJson,
  GcStatus.fromJson,
  GcReadiness.fromJson,
  GcAgent.fromJson,
  GcAgentSession.fromJson,
  GcSession.fromJson,
  GcSessionTurn.fromJson,
  GcTurn.fromJson,
  GcBead.fromJson,
  GcDependency.fromJson,
  GcConvoy.fromJson,
  GcBeadGraph.fromJson,
  GcRun.fromJson,
  GcRunStep.fromJson,
  GcRunsList.fromJson,
  GcPendingInteraction.fromJson,
  GcWait.fromJson,
  GcUsage.fromJson,
  GcUsageTotals.fromJson,
  GcEvent.fromJson,
  GcEventsPage.fromJson,
  GcHeartbeat.fromJson,
  GcStreamFrame.fromJson,
  GcSlingRequest.fromJson,
  GcSlingResponse.fromJson,
  GcProblem.fromJson,
  (json) => GcList<GcBead>.fromJson(json, GcBead.fromJson),
];

/// Recording name → the decode + map path the gateway will use for it.
/// Every file in `recordings/` must have an entry; the test fails
/// otherwise so a new recording is never silently skipped.
final Map<String, Object? Function(Map<String, Object?>)> _recordingPaths = {
  'health': (j) => GcHealth.fromJson(j).version,
  'status': (j) {
    final status = GcStatus.fromJson(j);
    return [
      mapStatusCounts(status),
      mapRigs(status),
      mapHostIdentity(
        url: 'http://h',
        hostMode: OrchestrationHostMode.computer,
        status: status,
      ),
    ];
  },
  'readiness': (j) => GcReadiness.fromJson(j).items,
  'agents': (j) =>
      mapAgents(GcList.fromJson(j, GcAgent.fromJson).items, const []),
  'agent': (j) => mapAgent(GcAgent.fromJson(j)),
  'sessions': (j) =>
      mapAgents(const [], GcList.fromJson(j, GcSession.fromJson).items),
  'session': (j) => mapSession(GcSession.fromJson(j)),
  'session_transcript': (j) => GcSessionTurn.fromJson(j).turns,
  'bd_create': (j) => mapBead(GcBead.fromJson(j)),
  'bead_after_sling': (j) => mapBead(GcBead.fromJson(j)),
  'bead_handed_to_refinery': (j) => mapBead(GcBead.fromJson(j)),
  'beads': (j) =>
      mapBeadList(GcList.fromJson(j, GcBead.fromJson), includeInternal: true),
  'beads_ready': (j) => mapBeadList(GcList.fromJson(j, GcBead.fromJson)),
  'beads_graph': (j) {
    final graph = GcBeadGraph.fromJson(j);
    return [?graph.root, ...graph.beads].map(mapBead).toList();
  },
  'convoy': (j) => GcProblem.looksLikeProblem(j)
      ? GcProblem.fromJson(j).message
      : mapConvoy(GcConvoy.fromJson(j)),
  'convoys': (j) => mapConvoys(GcList.fromJson(j, GcConvoy.fromJson).items),
  'runs': (j) => mapRunsList(GcRunsList.fromJson(j)),
  'runs_with_formula': (j) => mapRunsList(GcRunsList.fromJson(j)),
  'run_formula': (j) => mapRunsList(
    GcRunsList.fromJson({
      'runs': <Object?>[j],
      'status_counts': const {},
    }),
  ),
  'bead_workflow_root': (j) => mapBead(GcBead.fromJson(j)),
  'pending': (j) => mapGates(
    pending: GcList.fromJson(j, GcPendingInteraction.fromJson).items,
  ),
  'waits': (j) => GcWorkContext.from(
    waits: GcList.fromJson(j, GcWait.fromJson, itemsKey: 'waits').items,
  ),
  'usage': (j) => mapUsage(GcUsage.fromJson(j)),
  'events_page': (j) => GcEventsPage.fromJson(j).items.map(mapEvent).toList(),
  'sling_response': (j) => GcSlingResponse.fromJson(j).isSlung,
  'sling_cli_result': (j) => GcSlingResponse.fromJson(j).isSlung,
  'formulas': (j) => GcProblem.fromJson(j).slug,
  // Not modelled by the plugin (config, providers, rigs); they still must
  // pass through the generic decoders without throwing.
  'config': (j) => GcStatus.fromJson(j),
  'providers': (j) => GcList.fromJson(j, GcReadinessItem.fromJson),
  'rigs': (j) => GcList.fromJson(j, GcBead.fromJson),
};

const _garbage = <Object?>[
  null,
  '',
  'garbage',
  42,
  3.5,
  true,
  <Object?>[],
  <Object?>[1, 'x', null],
  <String, Object?>{},
  <String, Object?>{
    'nested': <String, Object?>{'deep': 1},
  },
];

/// Every key the DTOs read, so garbage can be planted under each.
const _knownKeys = [
  'id',
  'title',
  'status',
  'issue_type',
  'labels',
  'metadata',
  'dependencies',
  'is_blocked',
  'assignee',
  'created_at',
  'updated_at',
  'items',
  'total',
  'partial',
  'partial_errors',
  'runs',
  'status_counts',
  'run_id',
  'last_error',
  'scope',
  'name',
  'state',
  'running',
  'suspended',
  'session',
  'pool',
  'pack',
  'template',
  'session_name',
  'last_active',
  'last_activity',
  'seq',
  'type',
  'ts',
  'actor',
  'subject',
  'payload',
  'session_id',
  'data',
  'event',
  'turns',
  'today',
  'recent',
  'source',
  'work',
  'agents',
  'agent_details',
  'rig_details',
  'options',
  'prompt',
  'request_id',
  'kind',
  'waits',
  'version',
  'city',
  'uptime_sec',
  'detail',
  'code',
  'errors',
  'target',
  'bead',
  'run',
  'warnings',
  'progress',
  'convoy',
  'children',
  'root',
  'beads',
  'deps',
  'vars',
];

void main() {
  group('recordings round-trip', () {
    final files = _files('recordings', '.json');

    test('fixture directory has recordings', () {
      expect(files, isNotEmpty);
    });

    test('every recording has a mapping path (none skipped)', () {
      final names = files.map(
        (f) => f.uri.pathSegments.last.replaceAll('.json', ''),
      );
      expect(names.toSet(), equals(_recordingPaths.keys.toSet()));
    });

    for (final file in files) {
      final name = file.uri.pathSegments.last.replaceAll('.json', '');
      test('$name decodes and maps without throwing', () {
        final json = readJson(file);
        final path = _recordingPaths[name];
        expect(path, isNotNull, reason: 'no mapping path for $name');
        expect(() => path!(json), returnsNormally);
        for (final decode in _allDecoders) {
          expect(
            () => decode(json),
            returnsNormally,
            reason: '$decode on $name',
          );
        }
      });
    }
  });

  group('event logs round-trip', () {
    final files = _files('events', '.ndjson');

    test('fixture directory has event logs', () {
      expect(files, isNotEmpty);
    });

    for (final file in files) {
      final name = file.uri.pathSegments.last;
      test('$name: every line decodes and maps', () {
        final lines = file.readAsLinesSync().where((l) => l.trim().isNotEmpty);
        var count = 0;
        var heartbeats = 0;
        var unknown = 0;
        for (final line in lines) {
          final json = readMap(jsonDecode(line));
          final frame = GcStreamFrame.fromJson(json);
          final mapped = mapStreamFrame(frame);
          if (mapped is StreamHeartbeat) heartbeats++;
          if (mapped is UnknownOrchestrationEvent) unknown++;
          if (frame.isCityEvent) {
            final event = frame.toEvent();
            expect(event.type, isNot('unknown'));
            mapActivity(event);
            mapEvent(event);
            // Embedded beads are full bead documents: they must map too.
            final bead = event.payloadBead;
            if (bead != null) mapBead(GcBead.fromJson(bead));
          }
          if (frame.isTurn) {
            final turn = GcSessionTurn.fromJson(frame.data);
            expect(turn.id, isNotEmpty);
          }
          for (final decode in _allDecoders) {
            decode(json);
            decode(frame.data);
          }
          count++;
        }
        expect(count, greaterThan(0));
        expect(unknown, 0, reason: 'recorded types are all modelled');
        if (name.startsWith('session-')) expect(heartbeats, greaterThan(0));
      });
    }

    test('city events map to the documented product events', () {
      final seen = <Type, Set<String>>{};
      for (final file in files) {
        for (final line in file.readAsLinesSync()) {
          if (line.trim().isEmpty) continue;
          final frame = GcStreamFrame.fromJson(readMap(jsonDecode(line)));
          if (!frame.isCityEvent) continue;
          final event = frame.toEvent();
          final mapped = mapEvent(event);
          seen.putIfAbsent(mapped.runtimeType, () => {}).add(event.type);
          expect(mapped.seq, event.seq);
          expect(mapped.raw, same(event.raw));
        }
      }
      expect(
        seen[BeadChanged],
        containsAll(['bead.created', 'bead.updated', 'bead.closed']),
      );
      expect(
        seen[SessionChanged],
        containsAll(['session.woke', 'session.stopped']),
      );
      expect(seen[RunChanged], contains('convoy.closed'));
      expect(
        seen[ActivityAppended],
        containsAll(['order.fired', 'order.completed', 'order.failed']),
      );
      expect(seen.containsKey(UnknownOrchestrationEvent), isFalse);
    });

    test('heartbeat frames become StreamHeartbeat with a timestamp', () {
      final frame = GcStreamFrame.fromJson({
        'event': 'heartbeat',
        'data': {'timestamp': '2026-09-10T18:34:07Z'},
      });
      final mapped = mapStreamFrame(frame);
      expect(mapped, isA<StreamHeartbeat>());
      expect(
        (mapped as StreamHeartbeat).timestamp,
        DateTime.utc(2026, 9, 10, 18, 34, 7),
      );
      // A nameless frame carrying only a timestamp is a heartbeat too.
      final bare = GcStreamFrame.fromJson({
        'data': {'timestamp': '2026-09-10T18:34:07Z'},
      });
      expect(mapStreamFrame(bare), isA<StreamHeartbeat>());
    });
  });

  group('tolerance', () {
    test('every decoder survives garbage under every known key', () {
      for (final decode in _allDecoders) {
        for (final value in _garbage) {
          final json = {for (final key in _knownKeys) key: value};
          expect(
            () => decode(json),
            returnsNormally,
            reason: '$decode with $value',
          );
          expect(() => decode(const {}), returnsNormally);
        }
      }
    });

    test('mappers survive garbage DTOs', () {
      final json = {for (final key in _knownKeys) key: 'garbage'};
      expect(() => mapBead(GcBead.fromJson(json)), returnsNormally);
      expect(() => mapConvoy(GcConvoy.fromJson(json)), returnsNormally);
      expect(() => mapRun(GcRun.fromJson(json)), returnsNormally);
      expect(() => mapRunsList(GcRunsList.fromJson(json)), returnsNormally);
      expect(
        () =>
            mapAgent(GcAgent.fromJson(json), session: GcSession.fromJson(json)),
        returnsNormally,
      );
      expect(() => mapSession(GcSession.fromJson(json)), returnsNormally);
      expect(
        () => mapPendingInteraction(GcPendingInteraction.fromJson(json)),
        returnsNormally,
      );
      expect(
        () => mapUsage(GcUsage.fromJson(json), status: GcStatus.fromJson(json)),
        returnsNormally,
      );
      expect(() => mapEvent(GcEvent.fromJson(json)), returnsNormally);
      expect(
        () => mapStreamFrame(GcStreamFrame.fromJson(json)),
        returnsNormally,
      );
      expect(() => mapActivity(GcEvent.fromJson(json)), returnsNormally);
    });

    test('unknown enum strings map to unknown and keep the raw string', () {
      final bead = GcBead.fromJson(const {
        'id': 'b1',
        'title': 't',
        'status': 'quantum',
      });
      final work = mapBead(bead);
      expect(work.state, WorkState.unknown);
      expect(work.rawState, 'quantum');
      expect(work.raw['status'], 'quantum');

      final run = mapRun(
        GcRun.fromJson(const {
          'run_id': 'r1',
          'title': 'r',
          'status': 'teleporting',
        }),
      );
      expect(run.state, RunState.unknown);
      expect(run.rawState, 'teleporting');

      final agent = mapAgent(
        GcAgent.fromJson(const {
          'name': 'a',
          'state': 'levitating',
          'running': true,
        }),
      );
      expect(agent.state, AgentState.unknown);
      expect(agent.rawState, 'levitating');

      final gate = mapPendingInteraction(
        GcPendingInteraction.fromJson(const {
          'request_id': 'q',
          'kind': 'riddle',
        }),
      );
      expect(gate.kind, GateKind.unknown);
      expect(gate.rawKind, 'riddle');
      expect(gate.title, 'riddle');

      final event = mapEvent(
        GcEvent.fromJson(const {'seq': 7, 'type': 'martian.landed'}),
      );
      expect(event, isA<UnknownOrchestrationEvent>());
      expect(event.type, 'martian.landed');
      expect(event.seq, 7);
      expect(event.raw['type'], 'martian.landed');
    });

    test('unknown fields are ignored and kept in raw', () {
      final bead = GcBead.fromJson(const {
        'id': 'b1',
        'title': 't',
        'status': 'open',
        'brand_new_field': {'x': 1},
      });
      expect(bead.raw['brand_new_field'], isA<Map<String, Object?>>());
      expect(mapBead(bead).raw['brand_new_field'], isNotNull);
    });

    test('problem+json decodes and is recognised', () {
      final problem = GcProblem.fromJson(loadRecording('convoy'));
      expect(GcProblem.looksLikeProblem(loadRecording('convoy')), isTrue);
      expect(GcProblem.looksLikeProblem(loadRecording('health')), isFalse);
      expect(problem.status, 404);
      expect(problem.slug, 'convoy-not-found');
      expect(problem.isNotFound, isTrue);
      expect(problem.message, 'bead gc-3 is not a convoy');
    });
  });

  group('recorded shapes', () {
    test('bead oc-loy handed to the refinery', () {
      final bead = GcBead.fromJson(loadRecording('bead_handed_to_refinery'));
      expect(bead.branch, 'polecat/oc-loy');
      expect(bead.routedTo, isNull, reason: 'empty gc.routed_to reads as null');
      expect(bead.sessionId, 'bl-48k');
      expect(bead.sessionName, 'gastown__polecat-bl-48k');
      expect(bead.target, 'master');
      expect(bead.mergeStrategy, 'local');
      expect(bead.workDir, endsWith('polecats/gastown.furiosa'));

      final item = mapBead(bead);
      expect(item.id, 'oc-loy');
      expect(item.branch, 'polecat/oc-loy');
      expect(item.assignee, 'ocproof/gastown.refinery');
      expect(item.sessionId, 'bl-48k');
      expect(item.sessionName, 'gastown__polecat-bl-48k');
      expect(item.target, 'master');
      expect(item.mergeStrategy, 'local');
      expect(item.projectId, 'ocproof');
      // TEAM-117: in the refinery's hands the bead waits for the merge.
      expect(item.state, WorkState.review);
      expect(workHandedToMerge(item), isTrue);
      expect(item.rawState, 'open');
      // The recorded bead carries no `updated_at`: the strip must not
      // invent one.
      expect(item.updatedAt, isNull);
      expect(item.createdAt, DateTime.utc(2026, 9, 10, 18, 44, 45));
    });

    test('bead after sling keeps routing in assignee', () {
      final item = mapBead(GcBead.fromJson(loadRecording('bead_after_sling')));
      expect(item.assignee, 'ocproof/gastown.polecat');
      expect(item.routedTo, 'ocproof/gastown.polecat');
      expect(item.branch, isNull);
    });

    test('runs.json with partial: true yields an empty list and the flag', () {
      final list = GcRunsList.fromJson(loadRecording('runs'));
      expect(list.partial, isTrue);
      expect(list.partialErrors, ['run projection is warming']);
      expect(list.statusCounts['pending'], 0);
      final mapped = mapRunsList(list);
      expect(mapped.items, isEmpty);
      expect(mapped.partial, isTrue);
      expect(mapped.partialErrors, ['run projection is warming']);
      // Nothing recorded is upkeep, and nothing recorded is hidden.
      expect(mapped.items.where((r) => r.isUpkeep), isEmpty);
    });

    test('convoys track beads and become batch runs', () {
      final convoys = GcList.fromJson(
        loadRecording('convoys'),
        GcConvoy.fromJson,
      ).items;
      expect(convoys.single.trackedIds, ['oc-loy']);
      final loy = mapBead(
        GcBead.fromJson(loadRecording('bead_handed_to_refinery')),
      );
      final runs = mapConvoys(convoys, work: [loy]);
      expect(runs.single.id, 'oc-xru');
      expect(runs.single.kind, RunKind.batch);
      // The refinery holds the bead (assignee + session): waiting for
      // the merge (TEAM-117), not working; and the batch is named by
      // its work, the `sling-` title kept raw.
      expect(runs.single.state, RunState.waiting);
      expect(runs.single.title, 'Add subtract function to calc.py');
      expect(runs.single.raw['title'], 'sling-oc-loy');
      expect(runs.single.isUpkeep, isFalse);
      expect(runs.single.stepCount, 1);
      final context = GcWorkContext.from(convoys: convoys);
      expect(
        mapBead(
          GcBead.fromJson(loadRecording('bead_handed_to_refinery')),
          context: context,
        ).runId,
        'oc-xru',
      );
    });

    test('beads.json: internal beads are filtered unless asked for', () {
      final list = GcList.fromJson(loadRecording('beads'), GcBead.fromJson);
      expect(list.items, hasLength(14));
      expect(list.partial, isFalse);
      final work = mapBeadList(list);
      expect(work.items.map((w) => w.id), ['gc-2', 'gc-1', 'gc-19']);
      expect(mapBeadList(list, includeInternal: true).items, hasLength(14));
      final ready = GcList.fromJson(
        loadRecording('beads_ready'),
        GcBead.fromJson,
      ).items;
      final context = GcWorkContext.from(ready: ready);
      final states = {
        for (final w in mapBeads(list.items, context: context)) w.id: w.state,
      };
      expect(states['gc-19'], WorkState.ready);
      expect(states['gc-1'], WorkState.queued);
    });

    test('beads_graph root is a closed order bead', () {
      final graph = GcBeadGraph.fromJson(loadRecording('beads_graph'));
      final root = mapBead(graph.root!);
      expect(root.state, WorkState.completed);
      expect(root.closedReason, startsWith('order dispatch completed'));
      expect(root.labels, contains('exec-failed'));
    });

    test('status maps to host identity, counts and rigs', () {
      final status = GcStatus.fromJson(loadRecording('status'));
      final health = GcHealth.fromJson(loadRecording('health'));
      final host = mapHostIdentity(
        url: 'http://127.0.0.1:8372',
        hostMode: OrchestrationHostMode.computer,
        health: health,
        status: status,
      );
      expect(host.provider, 'gascity');
      expect(host.version, '1.4.1');
      expect(host.city, 'bright-lights');
      expect(health.isOk, isTrue);
      final counts = mapStatusCounts(status);
      expect(counts.workOpen, 29);
      expect(counts.workReady, 10);
      expect(counts.workInProgress, 0);
      expect(counts.activeAgents, 5);
      expect(status.agentDetails, hasLength(14));
      expect(status.agentDetails.where((a) => a.running), hasLength(5));
      final rigs = mapRigs(status);
      expect(rigs.single.id, 'ocproof');
      expect(rigs.single.directory, '/home/eslam/Storage/Code/oc-bg-proof');
      expect(
        mapHostIdentity(
          url: 'u',
          hostMode: OrchestrationHostMode.phone,
          status: status,
        ).city,
        'bright-lights',
      );
    });

    test('usage is labelled estimated', () {
      final usage = GcUsage.fromJson(loadRecording('usage'));
      expect(usage.isEstimate, isTrue);
      expect(usage.today?.computeFacts, 13);
      expect(usage.today?.wallSeconds, closeTo(4578.69, 0.01));
      final mapped = mapUsage(
        usage,
        status: GcStatus.fromJson(loadRecording('status')),
      );
      expect(mapped.isEstimated, isTrue);
      expect(mapped.source, 'local_estimate');
      expect(mapped.inputTokens, 0);
      expect(mapped.costUsd, 0);
      expect(mapped.workOpen, 29);
      expect(mapped.capturedAt, isNotNull);
      expect(mapped.isEmpty, isFalse);
    });

    test('events page is newest first and maps to activity', () {
      final page = GcEventsPage.fromJson(loadRecording('events_page'));
      expect(page.total, 1443);
      expect(page.maxSeq, 1443);
      expect(page.nextCursor, isNotNull);
      final activity = page.items.map(mapActivity).toList();
      expect(activity.first.seq, 1443);
      expect(activity.first.summary, 'order gate-sweep completed');
      expect(activity.first.actor, 'controller');
      final events = page.items.map(mapEvent);
      expect(events, everyElement(isA<ActivityAppended>()));
    });

    test('event payloads carry ids the product events need', () {
      final woke = mapEvent(
        GcEvent.fromJson(const {
          'seq': 999,
          'type': 'session.woke',
          'actor': 'gc',
          'subject': 'gastown.boot',
          'payload': {},
          'session_id': 'bl-2e9',
        }),
      );
      expect(woke, isA<SessionChanged>());
      expect((woke as SessionChanged).sessionId, 'bl-2e9');
      expect(woke.change, SessionChange.woke);
      expect(woke.agentId, 'gastown.boot');

      final stopped = mapEvent(
        GcEvent.fromJson(const {
          'seq': 969,
          'type': 'session.stopped',
          'subject': 'gastown.boot',
          'payload': {
            'session_id': 'bl-2e9',
            'template': 'gastown.boot',
            'reason': 'exited gracefully',
          },
        }),
      );
      expect((stopped as SessionChanged).sessionId, 'bl-2e9');
      expect(stopped.change, SessionChange.stopped);

      final closed = mapEvent(
        GcEvent.fromJson(const {
          'seq': 1352,
          'type': 'bead.closed',
          'subject': 'oc-a31',
          'payload': {
            'bead': {
              'id': 'oc-a31',
              'title': 'sling-oc-cq6',
              'status': 'closed',
            },
          },
          'run_id': 'oc-a31',
        }),
      );
      expect((closed as BeadChanged).beadId, 'oc-a31');
      expect(closed.change, BeadChange.closed);

      final noSubject = mapEvent(
        GcEvent.fromJson(const {
          'type': 'bead.created',
          'payload': {
            'bead': {'id': 'oc-new'},
          },
        }),
      );
      expect((noSubject as BeadChanged).beadId, 'oc-new');

      final convoyClosed = mapEvent(
        GcEvent.fromJson(const {
          'seq': 1353,
          'type': 'convoy.closed',
          'subject': 'oc-a31',
        }),
      );
      expect((convoyClosed as RunChanged).runId, 'oc-a31');
      expect(convoyClosed.state, RunState.completed);

      final failed = mapEvent(
        GcEvent.fromJson(const {
          'seq': 960,
          'type': 'order.failed',
          'subject': 'order-tracking-sweep',
          'message': 'context canceled',
        }),
      );
      expect(
        (failed as ActivityAppended).event.summary,
        'order order-tracking-sweep failed: context canceled',
      );
    });

    test('session stream turn frames decode the transcript', () {
      final file = File('${fixtureRoot.path}/events/session-refinery.ndjson');
      final frames = file
          .readAsLinesSync()
          .where((l) => l.isNotEmpty)
          .map((l) => GcStreamFrame.fromJson(readMap(jsonDecode(l))));
      final turn = frames.firstWhere((f) => f.isTurn);
      final decoded = GcSessionTurn.fromJson(turn.data);
      expect(decoded.id, 'bl-wisp-qqpj');
      expect(decoded.template, 'ocproof/gastown.refinery');
      expect(decoded.turns, isNotEmpty);
      expect(decoded.turns.first.role, 'output');
      final mapped = mapStreamFrame(turn);
      expect(mapped, isA<ActivityAppended>());
      expect((mapped as ActivityAppended).event.type, 'session.turn');
      expect(mapped.event.subject, 'bl-wisp-qqpj');
      final pending = mapStreamFrame(
        GcStreamFrame.fromJson(const {
          'event': 'pending',
          'data': {'request_id': 'q9', 'kind': 'choice'},
        }),
      );
      expect((pending as GateChanged).gateId, 'q9');
      expect(pending.resolved, isFalse);
    });

    test('sling request and response', () {
      final request = GcSlingRequest(
        target: 'ocproof/gastown.polecat',
        bead: 'oc-loy',
        force: true,
      );
      expect(request.toJson(), {
        'target': 'ocproof/gastown.polecat',
        'bead': 'oc-loy',
        'force': true,
      });
      final response = GcSlingResponse.fromJson(
        loadRecording('sling_response'),
      );
      expect(response.isSlung, isTrue);
      expect(response.bead, 'oc-loy');
      expect(response.mode, 'direct');
      final cli = GcSlingResponse.fromJson(loadRecording('sling_cli_result'));
      expect(cli.isSlung, isTrue);
      expect(cli.bead, 'gc-1');
      expect(cli.convoyId, 'gc-5');
    });

    test('readiness keyed items decode', () {
      final readiness = GcReadiness.fromJson(loadRecording('readiness'));
      expect(readiness.items.map((i) => i.name), [
        'claude',
        'codex',
        'gemini',
        'github_cli',
      ]);
      expect(readiness.items.every((i) => i.isConfigured), isTrue);
      expect(readiness.items.last.kind, 'tool');
    });

    test('no adapter file imports the OpenCode API layers', () {
      final dir = Directory('lib/orchestration/adapters/gascity');
      for (final file in dir.listSync(recursive: true).whereType<File>()) {
        final source = file.readAsStringSync();
        expect(source, isNot(contains('/api/')), reason: file.path);
        expect(source, isNot(contains('/api2/')), reason: file.path);
        expect(
          source,
          isNot(contains('package:opencode_sdk')),
          reason: file.path,
        );
      }
    });
  });
}
