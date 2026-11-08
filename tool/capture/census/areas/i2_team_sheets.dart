// Census scenes for the ledger part `i2-team-sheets`
// (docs/design/ui-ledger/parts/i2-team-sheets.json). See tool/capture/census_test.dart.
//
// The team scenes run over support/i1_team_core_world.dart (the recorded
// Gas City fixture with an invented team); the phone scenes over
// support/i2_team_sheets_phone.dart (a scripted on-phone runtime and a fake
// connection to this phone's server).
// ignore_for_file: invalid_use_of_visible_for_testing_member
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/ui/screens/team/team_page.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart'
    show TeamConversationScreen;
import 'package:opencode_mobile/ui/screens/team/task_details_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team/work_sheet.dart';

import '../census_core.dart';
import '../support/i1_team_core_world.dart';

Future<(OrchestrationController, CensusTeamGateway)> _team(
  CensusKit kit, {
  void Function(CensusTeamGateway gateway)? configure,
}) async {
  final (controller, gateway) = await teamController(configure: configure);
  kit.onDispose(controller.dispose);
  return (controller, gateway);
}

Future<OrchestrationController> _home(
  CensusKit kit, {
  void Function(CensusTeamGateway gateway)? configure,
}) async {
  final (team, _) = await _team(kit, configure: configure);
  await kit.pumpApp(TeamHomeScreen(controller: team, now: teamNow));
  return team;
}

Future<void> _gate(CensusKit kit, OrchestrationController team, String id) =>
    kit.present(
      (context) => showGateSheet(context, team, id, now: teamNow),
      settleFor: const Duration(seconds: 2),
    );

void _allGates(CensusTeamGateway g) => g.gateList = [
  teamChoiceGate(),
  teamConfirmGate(),
  teamTextGate(),
  teamFailedGate(),
];

void _review(CensusTeamGateway g, {bool ready = true}) {
  g
    ..runList = [teamReviewRun(), ...g.runList]
    ..workList = [...g.workList, ...teamReviewWork()]
    ..readiness[teamReviewRunId] = teamReadiness(ready: ready);
}

/// The review-ready task's conversation, scrolled to the Merge section.
Future<void> _merge(CensusKit kit, {bool ready = true}) async {
  final (team, _) = await _team(
    kit,
    configure: (g) => _review(g, ready: ready),
  );
  await kit.pumpApp(
    TeamConversationScreen(team: team, runId: teamReviewRunId, now: teamNow),
  );
  final section = find.byKey(const ValueKey('team-merge-section'));
  await kit.scrollTo(section);
  kit.expectVisible(section);
  await kit.tester.ensureVisible(section);
  await kit.settle();
}

Future<void> _openWork(CensusKit kit, String workId) async {
  final (team, _) = await _team(kit, configure: _review);
  await kit.pumpApp(
    TeamConversationScreen(team: team, runId: teamRunId, now: teamNow),
  );
  await kit.present(
    (context) => showWorkSheet(context, team, workId, now: teamNow),
    settleFor: const Duration(seconds: 2),
  );
}

/// Gives the team a task from the home and leaves the planning card.
Future<void> _planning(
  CensusKit kit,
  MutationReceiptStatus status, {
  bool late = false,
}) async {
  final (team, gateway) = await _team(kit, configure: (g) => g.gateList = []);
  gateway.controlStatus = status;
  if (status == MutationReceiptStatus.rejected) {
    gateway.controlMessage = 'The planner refused the message';
  }
  final start = teamClock;
  kit.onDispose(() => teamClock = start);
  await kit.pumpApp(TeamHomeScreen(controller: team, now: teamNow));
  await kit.tapKey('team-home-start-run');
  await kit.enterText(
    find.byKey(const ValueKey('team-start-run-objective')),
    'Add a dark mode toggle to Settings',
  );
  await kit.tapKey(
    'team-start-run-send',
    settleFor: const Duration(seconds: 2),
  );
  if (late) {
    teamClock = teamClock.add(const Duration(minutes: 31));
    await team.refresh();
    await kit.settle(const Duration(seconds: 2));
  }
  // slice-P5.1: the planning card is gone; the task's conversation (open
  // after Send) carries the planning state on its Now line.
  if (status != MutationReceiptStatus.rejected) {
    kit.expectVisible(find.byKey(const ValueKey('team-conversation-now')));
  }
}

/// Scrolls the screen's list until [finder] is on screen.
Future<void> _reveal(CensusKit kit, Finder finder) async {
  if (finder.evaluate().isEmpty || finder.hitTestable().evaluate().isEmpty) {
    try {
      await kit.tester.scrollUntilVisible(
        finder,
        120,
        scrollable: find.byType(Scrollable).first,
        maxScrolls: 120,
      );
    } catch (_) {}
  }
  if (finder.evaluate().isNotEmpty) {
    await kit.tester.ensureVisible(finder.first);
  }
  await kit.settle();
}

// -- Plugins (a computer host) ----------------------------------------------

ProbeFound _found() => const ProbeFound(
  host: OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://100.100.1.2:8372',
    hostMode: OrchestrationHostMode.computer,
    version: '1.4.1',
    city: 'bright-lights',
  ),
  version: '1.4.1',
  city: 'bright-lights',
  readOnly: false,
);

Future<void> _plugins(
  CensusKit kit, {
  Future<ProbeVerdict> Function(String url, {String? city})? probe,
  bool teamOn = false,
}) async {
  final conn = await kit.connected();
  // A tailnet address: discovery never probes plain http on a LAN host.
  conn.profile!.baseUrl = 'http://100.100.1.2:4096';
  if (teamOn) {
    final (team, _) = await teamController(prefs: conn.store.prefs);
    conn.profile!.orchestration = team.config;
    conn.adoptOrchestrationForTesting(team);
  }
  await kit.pumpApp(
    TeamPage(
      connection: conn,
      probe:
          probe ?? (url, {city}) async => const ProbeUnreachable(error: 'no'),
      now: teamNow,
    ),
    controller: conn,
  );
}

Future<void> _hostForm(
  CensusKit kit, {
  Future<ProbeVerdict> Function(String url, {String? city})? probe,
}) async {
  await _plugins(kit, probe: probe);
  await kit.tapKey('team-intro-address');
  kit.expectVisible(find.byKey(const ValueKey('team-host-form')));
}

/// The planner is suspended and the host cannot create work itself.
void _plannerOff(CensusTeamGateway g) => g
  ..gateList = []
  ..capabilitiesOverride = OrchestrationCapabilities.gascityFront
  ..agentList = [
    const OrchestrationAgent(
      id: 'gastown.mayor',
      name: 'mayor',
      state: AgentState.stopped,
      pool: 'gastown.mayor',
      suspended: true,
    ),
    ...g.agentList.skip(1),
  ];

final i2TeamSheetsArea = CensusArea(
  'i2-team-sheets',
  shots: [
    // -- Gate sheet --------------------------------------------------------------
    CensusShot('gate-sheet', state: 'decision', (kit) async {
      final team = await _home(kit, configure: _allGates);
      await _gate(kit, team, 'req-schema-1');
      await kit.tapKey('team-gate-option-0');
      kit.expectVisible(find.byKey(const ValueKey('team-gate-sheet')));
    }, note: 'Over the AI Team home; the first answer picked.'),
    CensusShot('gate-sheet', state: 'confirmation', (kit) async {
      final team = await _home(kit, configure: _allGates);
      await _gate(kit, team, 'req-migrate');
      kit.expectVisible(find.byKey(const ValueKey('team-gate-sheet')));
    }),
    CensusShot('gate-sheet', state: 'free-text', (kit) async {
      final team = await _home(kit, configure: _allGates);
      await _gate(kit, team, 'req-name');
      await kit.enterText(
        find.byKey(const ValueKey('team-gate-composer')),
        'Offline — changes will sync when you are back',
      );
      kit.expectVisible(find.byKey(const ValueKey('team-gate-sheet')));
    }),
    CensusShot('gate-sheet', state: 'run-failed', (kit) async {
      final team = await _home(kit, configure: _allGates);
      await _gate(kit, team, 'run-failed-upgrade');
      kit.expectVisible(find.byKey(const ValueKey('team-gate-sheet')));
    }),
    CensusShot('gate-sheet-confirm-sheet', state: 'deny', (kit) async {
      final team = await _home(kit, configure: _allGates);
      await _gate(kit, team, 'req-migrate');
      await kit.tapKey('team-gate-deny');
      kit.expectVisible(find.byKey(const ValueKey('team-gate-confirm')));
    }),
    CensusShot('gate-sheet-confirm-sheet', state: 'cancel-work', (kit) async {
      final team = await _home(kit, configure: _allGates);
      await _gate(kit, team, 'run-failed-upgrade');
      await kit.tap(find.byKey(const ValueKey('kit-actions-more')).last);
      await kit.tap(find.byKey(const ValueKey('team-gate-run-cancel')).last);
      kit.expectVisible(find.byKey(const ValueKey('team-gate-confirm')));
    }),

    // -- Merge -------------------------------------------------------------------
    CensusShot('embedded-team-merge-section', state: 'ready', (kit) async {
      await _merge(kit);
    }, note: 'Host: the run Overview of a review-ready task, scrolled down.'),
    CensusShot('embedded-team-merge-section', state: 'not-ready', (kit) async {
      await _merge(kit, ready: false);
    }, note: 'Host: the run Overview; tests failing, review pending.'),
    CensusShot('embedded-team-merge-section', state: 'armed', (kit) async {
      await _merge(kit);
      await kit.tapKey(
        'team-merge-merge',
        settleFor: const Duration(milliseconds: 500),
      );
      kit.expectVisible(find.byKey(const ValueKey('team-merge-armed-hint')));
    }, note: 'Host: the run Overview; Merge tapped once (armed for 8 s).'),
    CensusShot('team-merge-confirm-sheet', (kit) async {
      await _merge(kit);
      await kit.tapKey(
        'team-merge-merge',
        settleFor: const Duration(milliseconds: 500),
      );
      await kit.tapKey('team-merge-merge');
      kit.expectVisible(find.byKey(const ValueKey('team-merge-confirm-sheet')));
    }),
    CensusShot('team-merge-approve-sheet', (kit) async {
      await _merge(kit);
      await kit.tapKey('team-merge-approve');
      kit.expectVisible(find.byKey(const ValueKey('team-merge-approve-sheet')));
    }),
    CensusShot('team-merge-changes-sheet', (kit) async {
      await _merge(kit);
      await kit.tapKey('team-merge-review');
      kit.expectVisible(find.byKey(const ValueKey('team-merge-changes')));
    }),

    // -- Work --------------------------------------------------------------------
    CensusShot('work-sheet', state: 'working', (kit) async {
      await _openWork(kit, 'w-sync');
      kit.expectVisible(find.byKey(const ValueKey('team-work-sheet')));
    }, note: 'Over the task\'s conversation.'),
    CensusShot('work-sheet', state: 'blocked', (kit) async {
      await _openWork(kit, 'w-conflict');
      kit.expectVisible(find.byKey(const ValueKey('team-work-sheet')));
    }),
    CensusShot('work-sheet', state: 'review', (kit) async {
      await _openWork(kit, 'oc-w2');
      kit.expectVisible(find.byKey(const ValueKey('team-work-sheet')));
    }),
    CensusShot('work-sheet', state: 'missing', (kit) async {
      await _openWork(kit, 'w-gone');
      kit.expectVisible(find.byKey(const ValueKey('team-work-sheet-missing')));
    }),
    CensusShot('embedded-work-graph', (kit) async {
      final (team, _) = await _team(kit);
      await kit.pumpApp(
        TeamConversationScreen(team: team, runId: teamRunId, now: teamNow),
      );
      await kit.present(
        (context) =>
            showTeamTaskDetails(context, team, teamRunId, now: teamNow),
        settleFor: const Duration(seconds: 2),
      );
      kit.expectVisible(find.byKey(const ValueKey('team-task-details-graph')));
    }, note: 'Host: Task details, the steps as the graph in rows.'),

    // -- Start a run ---------------------------------------------------------------
    CensusShot('start-run-sheet', state: 'empty', (kit) async {
      await _home(kit, configure: (g) => g.gateList = []);
      await kit.tapKey('team-home-start-run');
      kit.expectVisible(find.byKey(const ValueKey('team-start-run-sheet')));
    }),
    CensusShot('start-run-sheet', state: 'filled', (kit) async {
      await _home(kit, configure: (g) => g.gateList = []);
      await kit.tapKey('team-home-start-run');
      await kit.enterText(
        find.byKey(const ValueKey('team-start-run-objective')),
        'Add a dark mode toggle to Settings and remember the choice',
      );
      await kit.tapKey('team-start-run-supervision-high');
      kit.expectVisible(find.byKey(const ValueKey('team-start-run-sheet')));
    }),
    CensusShot('start-run-sheet', state: 'planner-off', (kit) async {
      await _home(kit, configure: _plannerOff);
      await kit.tapKey('team-home-start-run');
      kit.expectVisible(
        find.byKey(const ValueKey('team-start-run-planner-off')),
      );
    }),
    CensusShot('start-run-sheet', state: 'direct-task', (kit) async {
      await _home(
        kit,
        configure: (g) => g
          ..gateList = []
          ..agentList = [
            const OrchestrationAgent(
              id: 'gastown.mayor',
              name: 'mayor',
              state: AgentState.stopped,
              pool: 'gastown.mayor',
              suspended: true,
            ),
            ...g.agentList.skip(1),
          ],
      );
      await kit.tapKey('team-home-start-run');
      kit.expectVisible(find.byKey(const ValueKey('team-start-run-direct')));
    }, note: 'Planner off on a host that creates work: the direct task form.'),
    CensusShot('team-conversation', state: 'planning', (kit) async {
      await _planning(kit, MutationReceiptStatus.accepted);
    }, note: 'The task\'s conversation after Send to planner.'),
    CensusShot('team-conversation', state: 'planning-31-min', (kit) async {
      await _planning(kit, MutationReceiptStatus.accepted, late: true);
    }, note: 'The task\'s conversation, 31 minutes later.'),
    CensusShot('team-conversation', state: 'planning-unconfirmed', (kit) async {
      await _planning(kit, MutationReceiptStatus.pending);
    }, note: 'The task\'s conversation; the host did not confirm.'),
    CensusShot('team-home', state: 'planning-refused', (kit) async {
      await _planning(kit, MutationReceiptStatus.rejected);
    }, note: 'Host: the AI Team home; the host refused the message.'),

    // -- Hosts ------------------------------------------------------------------------
    CensusShot('team-host-sheet', state: 'empty', (kit) async {
      await _hostForm(kit);
    }, note: 'Over the AI Team page, off.'),
    CensusShot('team-host-sheet', state: 'testing', (kit) async {
      await _hostForm(
        kit,
        probe: (url, {city}) => Completer<ProbeVerdict>().future,
      );
      await kit.enterText(
        find.byKey(const ValueKey('team-host-url')),
        'http://100.100.1.2:8372',
      );
      await kit.tapKey('team-host-submit');
    }, note: 'The probe has not answered yet.'),
    CensusShot('team-host-sheet', state: 'not-found', (kit) async {
      await _hostForm(
        kit,
        probe: (url, {city}) async => const ProbeUnreachable(
          error: 'Connection refused (http://100.100.1.2:8372)',
        ),
      );
      await kit.enterText(
        find.byKey(const ValueKey('team-host-url')),
        'http://100.100.1.2:8372',
      );
      await kit.enterText(
        find.byKey(const ValueKey('team-host-city')),
        'bright-lights',
      );
      await kit.tapKey('team-host-submit');
      kit.expectVisible(find.byKey(const ValueKey('team-host-verdict')));
    }),
    CensusShot('team-host-guide-sheet', (kit) async {
      await _home(kit, configure: _plannerOff);
      await kit.tapKey('team-home-start-run');
      await kit.tapKey('team-start-run-host-guide');
      kit.expectVisible(find.byKey(const ValueKey('team-host-guide')));
    }, note: 'Opened from Start a run while the planner is off.'),
    CensusShot('team-turn-off-sheet', (kit) async {
      await _plugins(kit, teamOn: true);
      await kit.tapKey('team-home-more', settleFor: const Duration(seconds: 2));
      await kit.tapKey('team-home-turn-off');
      kit.expectVisible(find.byKey(const ValueKey('team-turn-off-sheet')));
    }),
    CensusShot('embedded-team-discovery-card', (kit) async {
      await _plugins(kit, probe: (url, {city}) async => _found());
      kit.expectVisible(find.byKey(const ValueKey('team-intro-turn-on')));
    }, note: 'The AI Team page, off, with the found host: Turn on.'),
    CensusShot('team-host-details-sheet', (kit) async {
      await _home(kit);
      await kit.tapKey('team-home-info');
      kit.expectText('Technical details');
    }, note: 'From the AI Team home top bar.'),
    CensusShot('embedded-team-technical-value', (kit) async {
      final (team, _) = await _team(kit);
      await kit.pumpApp(
        AgentScreen(controller: team, agentId: 'fox', now: teamNow),
      );
      final row = find.byKey(const ValueKey('team-agent-technical'));
      await _reveal(kit, row);
      await kit.tap(row, scroll: false);
      await _reveal(kit, find.byKey(const ValueKey('team-agent-technical')));
      await kit.tester.drag(
        find.byType(Scrollable).first,
        const Offset(0, -300),
      );
      await kit.settle();
      kit.expectVisible(find.byType(Scrollable));
    }, note: 'Host: the agent screen, Technical details opened.'),
  ],
);
