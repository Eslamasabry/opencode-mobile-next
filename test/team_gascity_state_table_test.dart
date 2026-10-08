import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/dto/dto.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_mappers.dart';
import 'package:opencode_mobile/orchestration/dispatch.dart'
    show isRefineryName, workHandedToMerge;
import 'support/team_gascity_mappers_fixtures.dart';

void main() {
  group('state table (04 §4)', () {
    GcBead bead(Map<String, Object?> extra) => GcBead.fromJson({
      'id': 'w1',
      'title': 'Work',
      'status': 'open',
      ...extra,
    });

    test('work: queued', () {
      expect(mapBead(bead(const {})).state, WorkState.queued);
    });
    test('work: ready when in the ready set', () {
      final context = GcWorkContext.from(ready: [bead(const {})]);
      expect(mapBead(bead(const {}), context: context).state, WorkState.ready);
      expect(
        mapBead(bead(const {'id': 'other'}), context: context).state,
        WorkState.queued,
      );
    });
    test('work: working', () {
      expect(
        mapBead(bead(const {'status': 'in_progress'})).state,
        WorkState.working,
      );
    });
    test('work: waiting when its session is parked in /waits', () {
      final context = GcWorkContext.from(
        waits: [
          GcWait.fromJson(const {
            'id': 'wt',
            'session_id': 'bl-48k',
            'state': 'waiting',
          }),
        ],
      );
      final item = mapBead(
        bead(const {
          'status': 'in_progress',
          'metadata': {'gc.session_id': 'bl-48k'},
        }),
        context: context,
      );
      expect(item.state, WorkState.waiting);
    });
    test('work: blocked via is_blocked', () {
      final item = mapBead(
        bead(const {'status': 'in_progress', 'is_blocked': true}),
      );
      expect(item.state, WorkState.blocked);
      expect(item.isBlocked, isTrue);
    });
    test('work: needs input when its session has a pending interaction', () {
      final context = GcWorkContext.from(
        pending: [
          GcPendingInteraction.fromJson(const {
            'request_id': 'q1',
            'kind': 'choice',
            'session_id': 'bl-48k',
          }),
        ],
      );
      final item = mapBead(
        bead(const {
          'status': 'in_progress',
          'metadata': {'gc.session_id': 'bl-48k'},
        }),
        context: context,
      );
      expect(item.state, WorkState.needsInput);
    });
    test('work: review via needs-review label', () {
      final item = mapBead(
        bead(const {
          'status': 'in_progress',
          'labels': ['needs-review'],
        }),
      );
      expect(item.state, WorkState.review);
      expect(item.labels, contains('needs-review'));
    });
    test('work: failed via last_error (metadata and run step)', () {
      expect(
        mapBead(
          bead(const {
            'status': 'in_progress',
            'metadata': {'last_error': 'boom'},
          }),
        ).state,
        WorkState.failed,
      );
      expect(
        mapBead(
          bead(const {'status': 'in_progress'}),
          context: const GcWorkContext(runErrors: {'w1': 'step exploded'}),
        ).state,
        WorkState.failed,
      );
      // An empty last_error (what nudge beads carry) is not a failure.
      expect(
        mapBead(
          bead(const {
            'status': 'in_progress',
            'metadata': {'last_error': ''},
          }),
        ).state,
        WorkState.working,
      );
    });
    test('work: completed', () {
      final item = mapBead(
        bead(const {
          'status': 'closed',
          'metadata': {'close_reason': 'convoy autoclose: all children closed'},
        }),
      );
      expect(item.state, WorkState.completed);
      expect(item.closedReason, 'convoy autoclose: all children closed');
    });
    test('work: cancelled via closed_reason', () {
      expect(
        mapBead(
          bead(const {
            'status': 'closed',
            'metadata': {'closed_reason': 'cancelled'},
          }),
        ).state,
        WorkState.cancelled,
      );
      expect(
        mapBead(
          bead(const {
            'status': 'closed',
            'metadata': {'close_reason': 'cancelled'},
          }),
        ).state,
        WorkState.cancelled,
      );
    });

    OrchestrationRun run(
      String status, [
      Map<String, Object?> extra = const {},
    ]) => mapRun(
      GcRun.fromJson({
        'run_id': 'r1',
        'title': 'Run',
        'status': status,
        ...extra,
      }),
    );

    test(
      'run: planning',
      () => expect(run('pending').state, RunState.planning),
    );
    test('run: working', () => expect(run('active').state, RunState.working));
    test('run: waiting', () => expect(run('waiting').state, RunState.waiting));
    test('run: blocked (convoy with a blocked item)', () {
      final convoy = GcConvoy.fromJson(const {
        'id': 'c1',
        'title': 'sling-w1',
        'status': 'open',
        'issue_type': 'convoy',
        'dependencies': [
          {'issue_id': 'c1', 'depends_on_id': 'w1', 'type': 'tracks'},
        ],
      });
      final blocked = mapBead(
        bead(const {'status': 'in_progress', 'is_blocked': true}),
      );
      final mapped = mapConvoy(convoy, work: {'w1': blocked});
      expect(mapped.kind, RunKind.batch);
      expect(mapped.state, RunState.blocked);
      expect(mapped.stepCount, 1);
      expect(mapped.completedSteps, 0);
    });
    test('run: failed', () {
      final failed = run('failed', {
        'last_error': {'code': 'step_failed', 'message': 'tests red'},
      });
      expect(failed.state, RunState.failed);
      expect(failed.lastError, 'tests red');
      expect(failed.kind, RunKind.formula);
    });
    test(
      'run: completed',
      () => expect(run('completed').state, RunState.completed),
    );
    test('run: cancelled (canceled, canceling, skipped)', () {
      expect(run('canceled').state, RunState.cancelled);
      expect(run('canceling').state, RunState.cancelled);
      expect(run('skipped').state, RunState.cancelled);
    });
    test('run: convoy derives from tracked work', () {
      GcConvoy convoy([
        String status = 'open',
        Map<String, Object?> extra = const {},
      ]) => GcConvoy.fromJson({
        'id': 'c1',
        'title': 'sling-w1',
        'status': status,
        'issue_type': 'convoy',
        'dependencies': [
          {'issue_id': 'c1', 'depends_on_id': 'w1', 'type': 'tracks'},
          {'issue_id': 'c1', 'depends_on_id': 'w2', 'type': 'tracks'},
        ],
        ...extra,
      });
      WorkItem w(String id, Map<String, Object?> extra) =>
          mapBead(bead({'id': id, ...extra}));
      RunState state(Map<String, WorkItem> work, [GcConvoy? c]) =>
          mapConvoy(c ?? convoy(), work: work).state;

      // TEAM-115: nothing started is "waiting for an agent", never
      // planning (that word is the formula runs' `pending`).
      expect(state({}), RunState.waiting);
      expect(
        state({'w1': w('w1', const {}), 'w2': w('w2', const {})}),
        RunState.waiting,
      );
      expect(
        state({
          'w1': w('w1', const {'status': 'in_progress'}),
          'w2': w('w2', const {}),
        }),
        RunState.working,
      );
      expect(
        state({
          'w1': w('w1', const {
            'status': 'in_progress',
            'labels': ['needs-review'],
          }),
          'w2': w('w2', const {}),
        }),
        RunState.working,
      );
      expect(
        state({
          'w1': w('w1', const {
            'status': 'in_progress',
            'metadata': {'last_error': 'x'},
          }),
          'w2': w('w2', const {}),
        }),
        RunState.failed,
      );
      expect(
        state({
          'w1': w('w1', const {'status': 'closed'}),
          'w2': w('w2', const {'status': 'closed'}),
        }),
        RunState.completed,
      );
      expect(state({}, convoy('closed')), RunState.completed);
      expect(
        state(
          {},
          convoy('closed', const {
            'metadata': {'close_reason': 'cancelled by operator'},
          }),
        ),
        RunState.cancelled,
      );
    });

    test('run: batch state table (TEAM-115)', () {
      GcConvoy convoy(List<String> ids) => GcConvoy.fromJson({
        'id': 'c1',
        'title': 'sling-${ids.first}',
        'status': 'open',
        'issue_type': 'convoy',
        'dependencies': [
          for (final id in ids)
            {'issue_id': 'c1', 'depends_on_id': id, 'type': 'tracks'},
        ],
      });
      WorkItem w(
        String id,
        Map<String, Object?> extra, {
        GcWorkContext context = const GcWorkContext(),
      }) => mapBead(bead({'id': id, ...extra}), context: context);
      RunState state(List<WorkItem> items) => mapConvoy(
        convoy([for (final i in items) i.id]),
        work: {for (final i in items) i.id: i},
      ).state;

      // Queued, ready or open with only a pool routing: nobody has it.
      expect(state([w('a', const {})]), RunState.waiting);
      expect(
        state([
          w('a', const {}, context: const GcWorkContext(readyIds: {'a'})),
        ]),
        RunState.waiting,
      );
      expect(
        state([
          w('a', const {
            'metadata': {'gc.routed_to': 'ocproof/gastown.polecat'},
          }),
        ]),
        RunState.waiting,
      );
      // An assignee or a live session means an agent has it: working.
      expect(
        state([
          w('a', const {'assignee': 'ocproof/gastown.polecat-1'}),
        ]),
        RunState.working,
      );
      expect(
        state([
          w('a', const {
            'metadata': {'gc.session_id': 'bl-48k'},
          }),
        ]),
        RunState.working,
      );
      // TEAM-117: the refinery holding an open bead is the merge queue,
      // not an agent working it. The item reads as review pending and
      // the batch waits (for the merge); another item still working
      // keeps the batch working, a blocked one blocks it.
      final handed = w('a', const {'assignee': 'ocproof/gastown.refinery'});
      expect(handed.state, WorkState.review);
      expect(workHandedToMerge(handed), isTrue);
      expect(state([handed]), RunState.waiting);
      expect(
        state([
          handed,
          w('b', const {
            'status': 'in_progress',
            'metadata': {'gc.session_id': 'bl-49k'},
          }),
        ]),
        RunState.working,
      );
      expect(
        state([
          handed,
          w('b', const {'status': 'in_progress', 'is_blocked': true}),
        ]),
        RunState.blocked,
      );
      expect(
        state([
          handed,
          w('b', const {'status': 'closed'}),
        ]),
        RunState.waiting,
      );
      // A closed bead the refinery still names is done, not pending;
      // the pool routing alone is not a hand-off.
      final merged = w('a', const {
        'status': 'closed',
        'assignee': 'ocproof/gastown.refinery',
      });
      expect(merged.state, WorkState.completed);
      expect(workHandedToMerge(merged), isFalse);
      expect(
        workHandedToMerge(
          w('a', const {
            'metadata': {'gc.routed_to': 'ocproof/gastown.refinery'},
          }),
        ),
        isFalse,
      );
      expect(isRefineryName('ocproof/gastown.refinery'), isTrue);
      expect(isRefineryName('gastown.refinery'), isTrue);
      expect(isRefineryName('ocproof/gastown.polecat'), isFalse);
      expect(
        state([
          w('a', const {'status': 'in_progress'}),
          w('b', const {}),
        ]),
        RunState.working,
      );
      // Blocked or needing input: blocked.
      expect(
        state([
          w('a', const {'status': 'in_progress', 'is_blocked': true}),
          w('b', const {'status': 'in_progress'}),
        ]),
        RunState.blocked,
      );
      expect(
        state([
          w('a', const {
            'status': 'in_progress',
            'metadata': {'gc.session_id': 's1'},
          }, context: const GcWorkContext(needsInputSessions: {'s1'})),
        ]),
        RunState.blocked,
      );
      // Failed wins; all done is completed.
      expect(
        state([
          w('a', const {
            'status': 'in_progress',
            'metadata': {'last_error': 'boom'},
          }),
          w('b', const {'status': 'closed'}),
        ]),
        RunState.failed,
      );
      expect(
        state([
          w('a', const {'status': 'closed'}),
          w('b', const {'status': 'closed'}),
        ]),
        RunState.completed,
      );
      // Partly done with the rest unclaimed is still waiting for an agent.
      expect(
        state([
          w('a', const {'status': 'closed'}),
          w('b', const {}),
        ]),
        RunState.waiting,
      );
    });

    test('run: batch title is the work\'s title (TEAM-115)', () {
      GcConvoy convoy(String title, List<String> ids) => GcConvoy.fromJson({
        'id': 'oc-sv1',
        'title': title,
        'status': 'open',
        'issue_type': 'convoy',
        'dependencies': [
          for (final id in ids)
            {'issue_id': 'oc-sv1', 'depends_on_id': id, 'type': 'tracks'},
        ],
      });
      final docstring = mapBead(
        bead(const {
          'id': 'oc-ckg',
          'title': 'Add a docstring to multiply() in calc.py',
        }),
      );
      final tests = mapBead(
        bead(const {'id': 'oc-2', 'title': 'Write tests for calc.py'}),
      );
      // The live shape: `sling-<bead>` tracking one bead.
      final single = mapConvoy(
        convoy('sling-oc-ckg', ['oc-ckg']),
        work: {'oc-ckg': docstring},
      );
      expect(single.title, 'Add a docstring to multiply() in calc.py');
      expect(single.raw['title'], 'sling-oc-ckg', reason: 'raw kept');
      expect(single.state, RunState.waiting);
      // Several tracked items: the first plus a count.
      expect(
        mapConvoy(
          convoy('sling-oc-ckg', ['oc-ckg', 'oc-2']),
          work: {'oc-ckg': docstring, 'oc-2': tests},
        ).title,
        'Add a docstring to multiply() in calc.py + 1 more',
      );
      // An untitled convoy takes the work's title too.
      expect(
        mapConvoy(convoy('', ['oc-ckg']), work: {'oc-ckg': docstring}).title,
        'Add a docstring to multiply() in calc.py',
      );
      // A person's own convoy title is kept.
      expect(
        mapConvoy(
          convoy('Calc polish', ['oc-ckg']),
          work: {'oc-ckg': docstring},
        ).title,
        'Calc polish',
      );
      // Nothing to name it by: the host's title, else the id.
      expect(
        mapConvoy(convoy('sling-oc-ckg', ['oc-ckg'])).title,
        'sling-oc-ckg',
      );
      expect(mapConvoy(convoy('', [])).title, 'oc-sv1');
    });

    test('run: upkeep detection (TEAM-115)', () {
      bool upkeep(Map<String, Object?> json) => mapRun(
        GcRun.fromJson({'run_id': 'r', 'title': '', ...json}),
      ).isUpkeep;

      // The live `/runs` shape: every item a pack patrol wisp.
      const live = <String, Object?>{
        'run_id': 'oc-wisp-m63',
        'title': 'mol-refinery-patrol',
        'status': 'pending',
        'target': 'workflow',
        'scope': <String, Object?>{},
        'started_at': '2026-09-11T08:00:00Z',
        'updated_at': '2026-09-11T08:00:00Z',
      };
      final mapped = mapRun(GcRun.fromJson(live));
      expect(mapped.isUpkeep, isTrue);
      expect(mapped.state, RunState.planning);
      expect(mapped.title, 'mol-refinery-patrol', reason: 'raw title kept');
      expect(mapped.raw, live);

      expect(upkeep(const {'title': 'mol-deacon-patrol'}), isTrue);
      expect(upkeep(const {'title': 'mol-witness-patrol'}), isTrue);
      expect(upkeep(const {'title': 'mol-shutdown-dance'}), isTrue);
      expect(upkeep(const {'title': 'mol-digest-generate'}), isTrue);
      expect(upkeep(const {'formula': 'mol-refinery-patrol'}), isTrue);
      expect(upkeep(const {'title': 'order: gate-sweep'}), isTrue);
      expect(upkeep(const {'title': 'nudge:nudge-50666b477fd6'}), isTrue);
      // A scope-less workflow wisp naming no work is upkeep...
      expect(
        upkeep(const {'title': 'wisp', 'target': 'workflow', 'scope': {}}),
        isTrue,
      );
      // ...but one with a scope or tracked work is the person's.
      expect(
        upkeep(const {
          'title': 'wisp',
          'target': 'workflow',
          'scope': {'kind': 'rig', 'ref': 'ocproof'},
        }),
        isFalse,
      );
      expect(
        upkeep(const {
          'title': 'wisp',
          'target': 'workflow',
          'scope': {},
          'bead': 'oc-ckg',
        }),
        isFalse,
      );
      expect(upkeep(const {'title': 'Ship the calc feature'}), isFalse);
      expect(
        upkeep(const {'title': 'mol-polish', 'formula': 'mol-polish'}),
        isFalse,
      );
      expect(upkeep(const {'title': 'Add a docstring (patrol)'}), isFalse);
      // A failed upkeep run raises no gate.
      expect(
        gateFromRun(
          mapRun(
            GcRun.fromJson(const {
              'run_id': 'r',
              'title': 'mol-refinery-patrol',
              'status': 'failed',
              'last_error': 'boom',
            }),
          ),
        ),
        isNull,
      );
      expect(
        gateFromRun(
          mapRun(
            GcRun.fromJson(const {
              'run_id': 'r',
              'title': 'Ship it',
              'status': 'failed',
              'last_error': 'boom',
            }),
          ),
        ),
        isNotNull,
      );
    });

    OrchestrationAgent agent(
      Map<String, Object?> extra, {
      GcSession? session,
      GcAgentContext context = const GcAgentContext(),
    }) => mapAgent(
      GcAgent.fromJson({'name': 'gastown.mayor', ...extra}),
      session: session,
      context: context,
    );

    test('agent: working', () {
      expect(
        agent(const {'state': 'active', 'running': true}).state,
        AgentState.working,
      );
      final session = GcSession.fromJson(const {
        'id': 's1',
        'state': 'active',
        'running': true,
      });
      expect(
        agent(const {'running': true}, session: session).state,
        AgentState.working,
      );
    });
    test('agent: idle', () {
      expect(
        agent(const {'state': 'idle', 'running': true}).state,
        AgentState.idle,
      );
    });
    test('agent: waiting when its session has a pending interaction', () {
      final session = GcSession.fromJson(const {
        'id': 'bl-8jc',
        'state': 'active',
        'running': true,
        'session_name': 'gastown__mayor',
      });
      final context = GcAgentContext.from(
        pending: [
          GcPendingInteraction.fromJson(const {
            'request_id': 'q',
            'kind': 'confirm',
            'session_id': 'bl-8jc',
          }),
        ],
      );
      expect(
        agent(
          const {'state': 'idle', 'running': true},
          session: session,
          context: context,
        ).state,
        AgentState.waiting,
      );
    });
    test('agent: blocked when its session is parked in /waits', () {
      final session = GcSession.fromJson(const {
        'id': 'bl-8jc',
        'state': 'active',
        'running': true,
      });
      final context = GcAgentContext.from(
        waits: [
          GcWait.fromJson(const {
            'id': 'wt',
            'session_id': 'bl-8jc',
            'state': 'waiting',
          }),
        ],
      );
      expect(
        agent(
          const {'state': 'idle', 'running': true},
          session: session,
          context: context,
        ).state,
        AgentState.blocked,
      );
    });
    test('agent: stopped (stopped, suspended, not running)', () {
      expect(
        agent(const {'state': 'stopped', 'running': false}).state,
        AgentState.stopped,
      );
      expect(
        agent(const {
          'state': 'idle',
          'running': true,
          'suspended': true,
        }).state,
        AgentState.stopped,
      );
      expect(
        agent(const {'state': 'suspended', 'running': false}).state,
        AgentState.stopped,
      );
      expect(agent(const {'running': false}).state, AgentState.stopped);
    });
    test('agent: a running session outranks a stopped agent word', () {
      // On the phone (no tmux server) /agents says "stopped" for the pool
      // template while /sessions shows its session active and running
      // (emulator proof 2026-09-12, session ph-7bt on bead as-2m5).
      final live = GcSession.fromJson(const {
        'id': 'ph-7bt',
        'state': 'active',
        'running': true,
      });
      expect(
        agent(const {
          'state': 'stopped',
          'running': false,
        }, session: live).state,
        AgentState.working,
      );
      final gone = GcSession.fromJson(const {
        'id': 'ph-7bt',
        'state': 'active',
        'running': false,
      });
      expect(
        agent(const {
          'state': 'stopped',
          'running': false,
        }, session: gone).state,
        AgentState.stopped,
      );
    });
    test('agent: the session is the live truth; the agent list is the '
        'fallback', () {
      // The owner's phone (build 2054, 2026-09-25), read over adb: the
      // worker's agent entry exactly as /agents listed it (lagging: pool
      // sessions do not show there) ...
      const fields = {
        'available': true,
        'display_name': 'OpenCode',
        'name': 'demo-app/gastown.furiosa',
        'pack': 'gastown',
        'pack_derived': true,
        'pool': 'demo-app/gastown.polecat',
        'provider': 'opencode',
        'rig': 'demo-app',
        'running': false,
        'state': 'stopped',
        'suspended': false,
      };
      // ... alone it is stopped: nothing says otherwise.
      expect(agent(fields).state, AgentState.stopped);
      // ... while /sessions had its session active and running on a bead.
      final session = GcSession.fromJson(const {
        'id': 'ph-yqt',
        'session_name': 'gastown__polecat-ph-yqt',
        'alias': 'demo-app/gastown.furiosa',
        'state': 'active',
        'running': true,
        'active_bead': 'da-r7d',
        'created_at': '2026-09-25T19:51:05Z',
      });
      final live = agent(fields, session: session);
      expect(live.state, AgentState.working);
      expect(live.currentWorkId, 'da-r7d');
      // A session that is not running is not working, whatever it says.
      final gone = GcSession.fromJson(const {
        'id': 'ph-yqt',
        'state': 'active',
        'running': false,
      });
      expect(agent(fields, session: gone).state, AgentState.stopped);
    });
    test('agent: crashed', () {
      expect(
        agent(const {'state': 'error', 'running': false}).state,
        AgentState.crashed,
      );
      final session = GcSession.fromJson(const {
        'id': 's1',
        'state': 'crashed',
        'running': false,
      });
      expect(
        agent(const {
          'state': 'idle',
          'running': false,
        }, session: session).state,
        AgentState.crashed,
      );
      expect(mapSession(session).state, AgentState.crashed);
    });
    test('agent: session name, last activity and pool template', () {
      final mapped = mapAgent(
        GcAgent.fromJson(const {
          'name': 'gastown.dog-1',
          'state': 'idle',
          'running': true,
          'pool': 'gastown.dog',
          'pack': 'gastown',
          'provider': 'opencode',
          'active_bead': 'gc-19',
          'session': {
            'name': 'gastown__dog-1',
            'last_activity': '2026-09-10T21:43:20Z',
            'attached': false,
          },
        }),
      );
      expect(mapped.sessionName, 'gastown__dog-1');
      expect(mapped.lastActivity, DateTime.utc(2026, 9, 10, 21, 43, 20));
      expect(mapped.pool, 'gastown.dog');
      expect(mapped.pack, 'gastown');
      expect(mapped.provider, 'opencode');
      expect(mapped.currentWorkId, 'gc-19');
    });
    test('agents join sessions and unclaimed sessions become agents', () {
      final agents = GcList.fromJson(
        loadRecording('agents'),
        GcAgent.fromJson,
      ).items;
      final sessions = [
        ...GcList.fromJson(loadRecording('sessions'), GcSession.fromJson).items,
        GcSession.fromJson(loadRecording('session')),
      ];
      final mapped = mapAgents(agents, sessions);
      final mayor = mapped.firstWhere((a) => a.id == 'gastown.mayor');
      expect(mayor.sessionId, 'bl-8jc');
      expect(mayor.sessionName, 'gastown__mayor');
      expect(mayor.state, AgentState.idle);
      final polecat = mapped.firstWhere((a) => a.id == 'gc-58');
      expect(polecat.name, 'ocproof/gastown.furiosa');
      expect(polecat.pool, 'ocproof/gastown.polecat');
      expect(polecat.state, AgentState.working);
      final refinery = mapped.firstWhere((a) => a.id == 'bl-wisp-qqpj');
      expect(refinery.name, 'ocproof/gastown.refinery');
      expect(refinery.pool, isNull);
      // TEAM-115: the three unspawned dog slots and the core helper are
      // not agents; every entry comes back with includeSlots.
      expect(mapped.length, agents.length + sessions.length - 3 - 4);
      expect(
        mapAgents(agents, sessions, includeSlots: true).length,
        agents.length + sessions.length - 3,
      );
    });

    test('agents: only the live ones from agents.json (TEAM-115)', () {
      final agents = GcList.fromJson(
        loadRecording('agents'),
        GcAgent.fromJson,
      ).items;
      final sessions = GcList.fromJson(
        loadRecording('sessions'),
        GcSession.fromJson,
      ).items;
      final mapped = mapAgents(agents, sessions);
      expect(mapped.map((a) => a.name).toList(), [
        'gastown.boot',
        'gastown.deacon',
        'gastown.mayor',
        'ocproof/gastown.refinery',
        'ocproof/gastown.witness',
      ]);
      expect(mapped.every((a) => a.state != AgentState.stopped), isTrue);
      expect(mapped.every((a) => !a.suspended), isTrue);
    });

    test('agents: the live city shape keeps suspended, drops slots', () {
      // `/agents` on the PC city as the owner saw it (TEAM-115).
      final agents = [
        for (final json in <Map<String, Object?>>[
          {
            'name': 'bd.dog-1',
            'state': 'stopped',
            'pool': 'bd.dog',
            'pack': 'bd',
          },
          {
            'name': 'bd.dog-2',
            'state': 'stopped',
            'pool': 'bd.dog',
            'pack': 'bd',
          },
          {
            'name': 'core.control-dispatcher',
            'state': 'stopped',
            'pack': 'core',
          },
          {
            'name': 'ocproof/core.control-dispatcher',
            'state': 'stopped',
            'pack': 'core',
          },
          for (final n in ['a', 'b', 'c', 'd', 'e'])
            {
              'name': 'ocproof/gastown.$n',
              'state': 'stopped',
              'pool': 'ocproof/gastown.polecat',
              'pack': 'gastown',
            },
          {
            'name': 'ocproof/gastown.refinery',
            'state': 'idle',
            'running': true,
            'pack': 'gastown',
            'session': {'name': 'ocproof--gastown__refinery'},
          },
          {
            'name': 'ocproof/gastown.witness',
            'state': 'stopped',
            'pack': 'gastown',
          },
          {'name': 'gastown.boot', 'state': 'suspended', 'suspended': true},
          {'name': 'gastown.deacon', 'state': 'suspended', 'suspended': true},
          {'name': 'gastown.mayor', 'state': 'suspended', 'suspended': true},
        ])
          GcAgent.fromJson(json),
      ];
      expect(agents, hasLength(14));
      final mapped = mapAgents(agents, const []);
      expect(mapped.map((a) => a.name).toList(), [
        'ocproof/gastown.refinery',
        'ocproof/gastown.witness',
        'gastown.boot',
        'gastown.deacon',
        'gastown.mayor',
      ]);
      final refinery = mapped.first;
      expect(refinery.state, AgentState.idle);
      expect(refinery.suspended, isFalse);
      // A named agent that is merely stopped is kept (switched off).
      expect(mapped[1].state, AgentState.stopped);
      expect(mapped[1].suspended, isFalse);
      for (final agent in mapped.skip(2)) {
        expect(agent.state, AgentState.stopped);
        expect(agent.suspended, isTrue);
      }
      // A running helper or a running slot is an agent.
      expect(
        mapAgents([
          GcAgent.fromJson(const {
            'name': 'bd.dog-1',
            'state': 'active',
            'running': true,
            'pool': 'bd.dog',
            'pack': 'bd',
          }),
          GcAgent.fromJson(const {
            'name': 'core.control-dispatcher',
            'state': 'idle',
            'running': true,
            'pack': 'core',
          }),
        ], const []).map((a) => a.name),
        ['bd.dog-1', 'core.control-dispatcher'],
      );
    });

    test('gate: choice', () {
      final gate = mapPendingInteraction(
        GcPendingInteraction.fromJson(const {
          'request_id': 'q1',
          'kind': 'choice',
          'session_id': 'bl-48k',
          'prompt': 'Which branch?',
          'options': [
            'master',
            {'label': 'develop'},
          ],
        }),
      );
      expect(gate.kind, GateKind.choice);
      expect(gate.id, 'q1');
      expect(gate.agentId, 'bl-48k');
      expect(gate.title, 'Which branch?');
      expect(gate.choices, ['master', 'develop']);
    });
    test('gate: confirmation', () {
      final gate = mapPendingInteraction(
        GcPendingInteraction.fromJson(const {
          'request_id': 'q2',
          'kind': 'confirm',
        }),
      );
      expect(gate.kind, GateKind.confirmation);
      expect(gate.title, 'Confirm to continue');
    });
    test('gate: free text', () {
      final gate = mapPendingInteraction(
        GcPendingInteraction.fromJson(const {
          'request_id': 'q3',
          'kind': 'text',
        }),
      );
      expect(gate.kind, GateKind.freeText);
    });
    test('gate: gate bead', () {
      final gate = gateFromBead(
        bead(const {'issue_type': 'gate', 'description': 'Merge to master?'}),
      );
      expect(gate?.kind, GateKind.gateBead);
      expect(gate?.workId, 'w1');
      expect(gate?.prompt, 'Merge to master?');
      expect(
        gateFromBead(bead(const {'issue_type': 'gate', 'status': 'closed'})),
        isNull,
      );
      expect(
        gateFromBead(
          bead(const {
            'labels': ['gc:gate'],
          }),
        )?.kind,
        GateKind.gateBead,
      );
      expect(gateFromBead(bead(const {})), isNull);
    });
    test('gate: run failed', () {
      final failed = run('failed', {
        'last_error': {'code': 'x', 'message': 'tests red'},
      });
      final gate = gateFromRun(failed);
      expect(gate?.kind, GateKind.runFailed);
      expect(gate?.runId, 'r1');
      expect(gate?.prompt, 'tests red');
      expect(gateFromRun(run('completed')), isNull);
    });
    test('gate: review ready', () {
      final gate = gateFromBead(
        bead(const {
          'status': 'in_progress',
          'labels': ['needs-review'],
        }),
      );
      expect(gate?.kind, GateKind.reviewReady);
      expect(gate?.workId, 'w1');
    });
    test('mapGates collects every source', () {
      final gates = mapGates(
        pending: [
          GcPendingInteraction.fromJson(const {
            'request_id': 'q',
            'kind': 'choice',
          }),
        ],
        beads: [
          bead(const {'issue_type': 'gate'}),
          bead(const {
            'id': 'w2',
            'labels': ['needs-review'],
          }),
          bead(const {'id': 'w3'}),
        ],
        runs: [run('failed'), run('active')],
      );
      expect(gates.map((g) => g.kind), [
        GateKind.choice,
        GateKind.gateBead,
        GateKind.reviewReady,
        GateKind.runFailed,
      ]);
    });
  });
}
