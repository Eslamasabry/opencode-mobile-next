// Census scenes for the ledger part `i1-team-core`
// (docs/design/ui-ledger/parts/i1-team-core.json). See tool/capture/census_test.dart.
//
// Every scene runs over the recorded Gas City fixture with a small invented
// team (support/i1_team_core_world.dart): "Offline-first sessions" (a batch that
// needs you), a formula run, a merged task, agents fox / wolf / mayor.
// ignore_for_file: invalid_use_of_visible_for_testing_member
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart'
    show TeamConversationScreen;
import 'package:opencode_mobile/ui/screens/team/task_details_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/team_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team/work_sheet.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamWatchLiveScreen;

import '../census_core.dart';
import '../support/i1_team_core_world.dart';

/// A started team controller, disposed when the shot ends.
Future<(OrchestrationController, CensusTeamGateway)> _team(
  CensusKit kit, {
  void Function(CensusTeamGateway gateway)? configure,
  bool onPhone = false,
  Future<ProbeVerdict> Function(OrchestrationConfig config)? probe,
  bool start = true,
}) async {
  final (controller, gateway) = await teamController(
    configure: configure,
    onPhone: onPhone,
    probe: probe,
    start: start,
  );
  kit.onDispose(controller.dispose);
  return (controller, gateway);
}

Future<void> _home(CensusKit kit, OrchestrationController team) =>
    kit.pumpApp(TeamHomeScreen(controller: team, now: teamNow));

/// The task's conversation (the host for its sheets).
Future<void> _conversation(
  CensusKit kit,
  OrchestrationController team, {
  String runId = teamRunId,
}) =>
    kit.pumpApp(TeamConversationScreen(team: team, runId: runId, now: teamNow));

/// Task details over the task's conversation.
Future<void> _details(
  CensusKit kit,
  OrchestrationController team, {
  String runId = teamRunId,
}) async {
  await _conversation(kit, team, runId: runId);
  await kit.present(
    (context) => showTeamTaskDetails(context, team, runId, now: teamNow),
    settleFor: const Duration(seconds: 2),
  );
}

Future<void> _agent(
  CensusKit kit,
  OrchestrationController team, {
  String agentId = 'fox',
}) =>
    kit.pumpApp(AgentScreen(controller: team, agentId: agentId, now: teamNow));

/// Nine more open tasks: a list long enough for search and filters.
void _busy(CensusTeamGateway g) {
  g.runList = [
    ...g.runList,
    for (var i = 1; i <= 9; i++)
      OrchestrationRun(
        id: 'busy-$i',
        title: const [
          'Rename the cart service',
          'Price rounding in the checkout',
          'Search results paging',
          'Wishlist sync',
          'Image lazy loading',
          'Order history export',
          'Coupon validation',
          'Accessibility labels on the product page',
          'Shipping estimate copy',
        ][i - 1],
        state: i.isEven ? RunState.waiting : RunState.working,
        kind: RunKind.formula,
        formula: 'mol-follow-up',
        stepCount: 3,
        completedSteps: i % 3,
        startedAt: teamClock.subtract(Duration(minutes: 20 + i * 7)),
        updatedAt: teamClock.subtract(Duration(minutes: 10 + i)),
      ),
  ];
}

/// Six tasks finished today.
void _manyDone(CensusTeamGateway g) {
  const titles = [
    'Add a health check route',
    'Tidy the README install steps',
    'Cache product images',
    'Fix the cart badge count',
    'Log slow checkout requests',
  ];
  g.runList = [
    ...g.runList,
    for (var i = 0; i < titles.length; i++)
      OrchestrationRun(
        id: 'done-$i',
        title: titles[i],
        state: RunState.completed,
        kind: RunKind.batch,
        stepCount: 2,
        completedSteps: 2,
        merged: true,
        startedAt: teamClock.subtract(Duration(hours: 2 + i, minutes: 30)),
        updatedAt: teamClock.subtract(Duration(hours: 1 + i)),
        finishedAt: teamClock.subtract(Duration(hours: 1 + i)),
      ),
  ];
}

/// Events for the "Offline-first sessions" task (what the host reported).
void _emitTimeline(CensusTeamGateway g) {
  var seq = 9000;
  Map<String, Object?> raw(
    String type,
    String subject,
    Duration ago, [
    Map<String, Object?> payload = const {},
  ]) => {
    'seq': ++seq,
    'type': type,
    'ts': teamClock.subtract(ago).toIso8601String(),
    'subject': subject,
    'payload': payload,
  };
  final events = <OrchestrationEvent>[
    BeadChanged(
      beadId: 'w-storage',
      change: BeadChange.closed,
      seq: seq + 1,
      raw: raw('bead.closed', 'w-storage', const Duration(hours: 2), {
        'bead': {'id': 'w-storage', 'status': 'closed'},
      }),
    ),
    SessionChanged(
      sessionId: 'bl-5qc',
      change: SessionChange.woke,
      agentId: 'fox',
      seq: seq + 1,
      raw: raw('session.woke', 'fox', const Duration(minutes: 90)),
    ),
    BeadChanged(
      beadId: 'w-sync',
      change: BeadChange.updated,
      seq: seq + 1,
      raw: raw('bead.updated', 'w-sync', const Duration(minutes: 80), {
        'bead': {'id': 'w-sync', 'status': 'in_progress', 'assignee': 'fox'},
      }),
    ),
    GateChanged(
      gateId: 'req-schema-1',
      seq: seq + 1,
      raw: raw('request.created', 'req-schema-1', const Duration(minutes: 12)),
    ),
    BeadChanged(
      beadId: 'w-conflict',
      change: BeadChange.updated,
      seq: seq + 1,
      raw: raw('bead.updated', 'w-conflict', const Duration(minutes: 9), {
        'bead': {'id': 'w-conflict', 'status': 'blocked'},
      }),
    ),
    BeadChanged(
      beadId: 'w-sync',
      change: BeadChange.updated,
      seq: seq + 1,
      raw: raw('bead.updated', 'w-sync', const Duration(minutes: 5), {
        'bead': {'id': 'w-sync', 'status': 'in_progress'},
      }),
    ),
  ];
  events.forEach(g.emitEvent);
}

/// A routed step nobody has picked up for four minutes: the host has not
/// started an agent yet.
WorkItem _routedStep() => WorkItem(
  id: 'w-banner',
  title: 'Offline banner on the session list',
  state: WorkState.fromProvider('open'),
  rawState: 'open',
  projectId: 'shopfront',
  runId: teamRunId,
  assignee: 'shopfront/gastown.polecat',
  updatedAt: teamClock.subtract(const Duration(minutes: 4)),
  createdAt: teamClock.subtract(const Duration(minutes: 4)),
  raw: const {
    'id': 'w-banner',
    'status': 'open',
    'metadata': {'gc.routed_to': 'shopfront/gastown.polecat'},
  },
);

/// A step whose agent hit the provider's usage limit.
void _providerLimit(CensusTeamGateway g) {
  g.workList = [
    ...g.workList,
    WorkItem(
      id: 'w-limit',
      title: 'Offline banner on the session list',
      state: WorkState.fromProvider('in_progress'),
      rawState: 'in_progress',
      projectId: 'shopfront',
      runId: teamRunId,
      assignee: 'gastown__polecat-bl-48k',
      sessionId: 'bl-48k',
      updatedAt: teamClock.subtract(const Duration(minutes: 2)),
      raw: const {
        'id': 'w-limit',
        'status': 'in_progress',
        'assignee': 'gastown__polecat-bl-48k',
        'metadata': {
          'gc.routed_to': 'shopfront/gastown.polecat',
          'gc.session_id': 'bl-48k',
          'gc.session_name': 'gastown__polecat-bl-48k',
        },
      },
    ),
  ];
  g.agentList = [
    ...g.agentList,
    const OrchestrationAgent(
      id: 'gastown.furiosa',
      name: 'shopfront/gastown.furiosa',
      state: AgentState.working,
      sessionId: 'bl-48k',
      pool: 'shopfront/gastown.polecat',
    ),
  ];
  g.outputs['bl-48k'] = const [
    AgentOutputText('Starting ACP…\n'),
    AgentOutputText('The usage limit has been reached. Try again at 10:30.\n'),
  ];
}

Future<void> _openWork(
  CensusKit kit,
  OrchestrationController team,
  String workId,
) async {
  await kit.present(
    (context) => showWorkSheet(context, team, workId, now: teamNow),
    settleFor: const Duration(seconds: 2),
  );
}

final i1TeamCoreArea = CensusArea(
  'i1-team-core',
  shots: [
    // -- AI Team home ------------------------------------------------------
    CensusShot('team-home', state: 'loaded', (kit) async {
      final (team, _) = await _team(kit);
      await _home(kit, team);
      kit.expectVisible(find.byKey(const ValueKey('team-home')));
      kit.expectText('Offline-first sessions');
    }),
    CensusShot('team-home', state: 'empty', (kit) async {
      final (team, _) = await _team(
        kit,
        configure: (g) => g
          ..runList = []
          ..workList = []
          ..gateList = []
          ..agentList = [g.agentList.first],
      );
      await _home(kit, team);
      kit.expectVisible(find.byKey(const ValueKey('team-home-runs-empty')));
    }),
    CensusShot('team-home', state: 'error', (kit) async {
      final (team, _) = await _team(
        kit,
        probe: (_) async => const ProbeUnreachable(
          error: 'Connection refused (http://pop-os:7000)',
        ),
      );
      await _home(kit, team);
      kit.expectVisible(find.byKey(const ValueKey('team-home')));
      if (team.phase != OrchestrationPhase.failed) {
        throw CensusMismatch('team did not fail: ${team.phase}');
      }
    }),
    CensusShot(
      'team-home',
      state: 'not-answering',
      (kit) async {
        final (team, _) = await _team(
          kit,
          probe: (_) => Completer<ProbeVerdict>().future,
          start: false,
        );
        await kit.pumpApp(
          TeamHomeScreen(controller: team, now: teamNow),
          settleFor: const Duration(seconds: 10),
        );
        kit.expectVisible(find.byKey(const ValueKey('team-home')));
      },
      note: 'The host probe never answers; past the 8 s rule.',
    ),
    CensusShot(
      'team-home-runs-tab',
      state: 'search-open',
      (kit) async {
        final (team, _) = await _team(kit, configure: _busy);
        await _home(kit, team);
        await kit.tapKey('team-home-search-open');
        kit.expectVisible(find.byKey(const ValueKey('team-home-search')));
      },
      note:
          'The redesign removed the Runs / Agents / Needs you segments: the '
          'tasks are one list on the home. Twelve tasks, so the search and '
          'the filters are offered.',
    ),
    CensusShot(
      'team-home-runs-tab',
      state: 'filtered',
      (kit) async {
        final (team, _) = await _team(kit, configure: _busy);
        await _home(kit, team);
        await kit.tapKey('team-home-search-open');
        await kit.tapKey('team-home-filter-menu');
        await kit.tapKey('team-home-filter-completed');
        kit.expectVisible(find.byKey(const ValueKey('team-home-search')));
      },
      note: 'Filters › Completed on the long task list.',
    ),
    CensusShot(
      'team-home-runs-tab',
      state: 'done-expanded',
      (kit) async {
        final (team, _) = await _team(kit, configure: _manyDone);
        await _home(kit, team);
        await kit.tap(find.byKey(const ValueKey('team-home-completed-more')));
        await kit.scrollTo(find.byKey(const ValueKey('team-home-agents-row')));
        kit.expectVisible(
          find.byKey(const ValueKey('team-home-completed-group')),
        );
      },
      note: 'Six tasks finished today; the rows past the first three shown.',
    ),
    CensusShot(
      'team-home-agents-tab',
      (kit) async {
        final (team, _) = await _team(kit, configure: _busy);
        await _home(kit, team);
        await kit.scrollTo(find.byKey(const ValueKey('team-home-agents-row')));
        kit.expectVisible(find.byKey(const ValueKey('team-home-agents-row')));
      },
      note:
          'The Agents segment is gone in the redesign: the home ends with one '
          'agents row that opens the Agents screen (team-agents).',
    ),
    CensusShot(
      'team-home-needs-you-tab',
      state: 'several',
      (kit) async {
        final (team, gateway) = await _team(
          kit,
          configure: (g) => g.gateList = [
            teamChoiceGate(),
            teamConfirmGate(),
            teamTextGate(),
            teamFailedGate(),
          ],
        );
        gateway.controlStatus = MutationReceiptStatus.pending;
        await team.answerGate(
          'req-name',
          const GateResponse.text('Offline — changes will sync later'),
        );
        await _home(kit, team);
        kit.expectVisible(find.byKey(const ValueKey('team-home-needs-you')));
      },
      note:
          'The Needs you segment is now the block at the top of the home: '
          'four open questions, one answered but unconfirmed (receipt chip).',
    ),

    // -- Task details (P3.5: the run page and its tabs are retired; a task
    // is its conversation) ------------------------------------------------
    CensusShot('team-task-details', state: 'batch', (kit) async {
      final (team, _) = await _team(kit);
      await _details(kit, team);
      kit.expectVisible(find.byKey(const ValueKey('team-task-details')));
    }),
    CensusShot('team-task-details', state: 'formula', (kit) async {
      final (team, _) = await _team(kit);
      await _details(kit, team, runId: teamFormulaRunId);
      kit.expectVisible(find.byKey(const ValueKey('team-task-details')));
    }),
    CensusShot(
      'team-task-details',
      state: 'reported',
      (kit) async {
        final (team, gateway) = await _team(kit);
        await _conversation(kit, team);
        _emitTimeline(gateway);
        await kit.settle(const Duration(seconds: 1));
        await kit.present(
          (context) =>
              showTeamTaskDetails(context, team, teamRunId, now: teamNow),
          settleFor: const Duration(seconds: 2),
        );
        await kit.tapKey('team-task-details-technical');
        kit.expectVisible(
          find.byKey(const ValueKey('team-task-details-reported')),
        );
      },
      note: 'Technical details open: what the host reported (the Timeline).',
    ),
    CensusShot('team-task-details', state: 'missing', (kit) async {
      final (team, _) = await _team(kit);
      await _details(kit, team, runId: 'oc-gone');
      kit.expectVisible(
        find.byKey(const ValueKey('team-task-details-missing')),
      );
    }),

    // -- Agents ----------------------------------------------------------------
    CensusShot('team-agents', state: 'loaded', (kit) async {
      final (team, _) = await _team(kit);
      await kit.pumpApp(TeamAgentsScreen(controller: team, now: teamNow));
      kit.expectVisible(find.byKey(const ValueKey('team-agents')));
    }),
    CensusShot('team-agents', state: 'suspended-open', (kit) async {
      final (team, _) = await _team(kit);
      await kit.pumpApp(TeamAgentsScreen(controller: team, now: teamNow));
      await kit.tapKey('team-home-suspended-group');
      kit.expectVisible(find.byKey(const ValueKey('team-agents')));
    }),
    CensusShot('team-agent', state: 'top', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team);
      kit.expectVisible(find.byKey(const ValueKey('team-agent-header')));
    }),
    CensusShot('team-agent', state: 'controls', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team);
      await kit.scrollTo(find.byKey(const ValueKey('team-agent-controls')));
      await kit.tester.ensureVisible(
        find.byKey(const ValueKey('team-agent-controls')),
      );
      await kit.settle();
      kit.expectVisible(find.byKey(const ValueKey('team-agent-controls')));
    }),
    CensusShot(
      'team-agent',
      state: 'unconfirmed',
      (kit) async {
        final (team, gateway) = await _team(kit);
        gateway.controlStatus = MutationReceiptStatus.pending;
        await team.controlAgent('fox', AgentControlAction.nudge);
        await _agent(kit, team);
        await kit.scrollTo(find.byKey(const ValueKey('team-agent-receipt')));
        await kit.tester.ensureVisible(
          find.byKey(const ValueKey('team-agent-receipt')),
        );
        await kit.settle();
        kit.expectVisible(find.byKey(const ValueKey('team-agent-receipt')));
      },
      note: 'After a Nudge the host did not confirm: the receipt chip.',
    ),
    CensusShot('team-agent', state: 'stopped', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team, agentId: 'gastown.deacon');
      kit.expectVisible(find.byKey(const ValueKey('team-agent-header')));
    }, note: 'A pool agent suspended on the host.'),
    CensusShot('team-agent-details-sheet', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team);
      await kit.tapKey('team-agent-more');
      await kit.tapKey('team-agent-details');
      kit.expectVisible(find.byKey(const ValueKey('team-agent-details-sheet')));
    }),
    CensusShot('team-agent-reassign-sheet', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team);
      await _tapAgentControl(kit, 'team-agent-control-reassign');
      kit.expectVisible(
        find.byKey(const ValueKey('team-agent-reassign-sheet')),
      );
    }),
    CensusShot('team-agent-stop-confirm-sheet', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team);
      await _tapAgentControl(kit, 'team-agent-control-stop');
      kit.expectVisible(find.byKey(const ValueKey('team-agent-stop-confirm')));
    }),
    CensusShot('team-agent-restart-confirm-sheet', (kit) async {
      final (team, _) = await _team(kit);
      await _agent(kit, team);
      await _tapAgentControl(kit, 'team-agent-control-restart');
      kit.expectVisible(
        find.byKey(const ValueKey('team-agent-restart-confirm')),
      );
    }),
    // team-agent-output merged into the chat's watching mode (slice-P3.6):
    // the same watching page drawn from the team's live output.
    CensusShot(
      'chat-watching-live',
      state: 'live',
      (kit) async {
        final (team, _) = await _team(kit);
        await kit.pumpApp(TeamWatchLiveScreen(team: team, agentId: 'fox'));
        kit.expectVisible(find.byKey(const ValueKey('chat-watching-live')));
        kit.expectVisible(
          find.byKey(const ValueKey('chat-watching-live-text')),
        );
      },
      note: 'The recorded polecat transcript, following the end.',
    ),
    CensusShot(
      'chat-watching-live',
      state: 'ended',
      (kit) async {
        final (team, _) = await _team(kit);
        await kit.pumpApp(TeamWatchLiveScreen(team: team, agentId: 'wolf'));
        kit.expectVisible(find.byKey(const ValueKey('chat-watching-live')));
      },
      note: 'A session the host no longer serves (404).',
    ),
    CensusShot(
      'chat-watching-live',
      state: 'scrolled-up',
      (kit) async {
        final (team, _) = await _team(kit);
        await kit.pumpApp(TeamWatchLiveScreen(team: team, agentId: 'fox'));
        await kit.tester.drag(
          find.byKey(const ValueKey('chat-watching-live-list')),
          const Offset(0, 600),
        );
        await kit.settle();
        kit.expectVisible(find.byKey(const ValueKey('chat-watching-live')));
      },
      note: 'Dragged away from the end: Jump to latest.',
    ),

    // -- The step's Now line (slice-P5.1: it replaced the dispatch cycle
    // strip, its How sheet and its Stop confirmation) ----------------------
    CensusShot(
      'work-sheet',
      state: 'now-line-host-not-started',
      (kit) async {
        final (team, _) = await _team(
          kit,
          configure: (g) => g.workList = [...g.workList, _routedStep()],
        );
        await _conversation(kit, team);
        await _openWork(kit, team, 'w-banner');
        kit.expectVisible(find.byKey(const ValueKey('team-work-sheet-now')));
      },
      note: 'Host: the Work sheet over the task\'s conversation.',
    ),
    CensusShot(
      'work-sheet',
      state: 'now-line-provider-limit',
      (kit) async {
        final (team, _) = await _team(kit, configure: _providerLimit);
        await _conversation(kit, team);
        await _openWork(kit, team, 'w-limit');
        kit.expectVisible(find.byKey(const ValueKey('team-work-sheet-now')));
      },
      note: 'Host: the Work sheet over the task\'s conversation.',
    ),
  ],
);

/// Taps an agent control, through the action block's More menu when it
/// is folded there.
Future<void> _tapAgentControl(CensusKit kit, String key) async {
  final target = find.byKey(ValueKey(key));
  await kit.scrollTo(find.byKey(const ValueKey('team-agent-controls')));
  await kit.tester.ensureVisible(
    find.byKey(const ValueKey('team-agent-controls')),
  );
  await kit.settle();
  if (target.evaluate().isEmpty) {
    await kit.tap(
      find.descendant(
        of: find.byKey(const ValueKey('team-agent-controls')),
        matching: find.byKey(const ValueKey('kit-actions-more')),
      ),
    );
  }
  await kit.tap(target, scroll: false);
}
