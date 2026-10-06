// slice-P5.2 goldens (docs/qa/slice-P5.2-2026-09-27): the team page and its
// agents list for the owner's phone team (build 2055) — a lean team whose
// planner and supervisors the app keeps off, the host's patrols, today's
// spend — at 412x915 and 1280x800 with the app's real fonts; the agents
// list of a computer's team with paused agents after a wake the host did
// not confirm; and a task's Details saying its cost is not reported.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p52_golden_test.dart
// and look at every changed image before committing it.

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/task_details_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/team_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/team_golden_fixture.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

/// A phone tall enough to show a task's Details under its question.
const _tallPhone = Size(412, 1700);
final _clock = DateTime.utc(2026, 9, 27, 14, 2);

String _fixturePath() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate.path;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

class _Gateway extends FixtureOrchestrationGateway {
  _Gateway({
    required this.agentList,
    required bool phone,
    this.usageFigure,
    this.upkeep = false,
  }) : super(
         fixturePath: _fixturePath(),
         hostMode: phone
             ? OrchestrationHostMode.phone
             : OrchestrationHostMode.computer,
         url: phone ? 'http://127.0.0.1:8472' : 'http://dev-pc:7000',
       );

  final List<OrchestrationAgent> agentList;
  final OrchestrationUsage? usageFigure;
  final bool upkeep;

  @override
  Future<List<OrchestrationAgent>> agents() async => agentList;

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => [
    ...await super.runs(projectId: projectId),
    if (upkeep)
      for (var i = 1; i <= 4; i++)
        OrchestrationRun(
          id: 'wisp-$i',
          title: 'mol-witness-patrol',
          formula: 'mol-witness-patrol',
          state: RunState.planning,
          kind: RunKind.formula,
          isUpkeep: true,
        ),
  ];

  @override
  Future<OrchestrationUsage?> usage() async => usageFigure;

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) async =>
      MutationReceipt(id: requestId, status: MutationReceiptStatus.pending);
}

/// The owner's phone team: a worker at work, and the planner and three
/// supervisors the app keeps off to save the phone.
List<OrchestrationAgent> _leanTeam() => [
  OrchestrationAgent(
    id: 'demo-app/gastown.polecat',
    name: 'demo-app/gastown.furiosa',
    pool: 'gastown.polecat',
    state: AgentState.working,
    sessionRunning: true,
    lastActivity: _clock.subtract(const Duration(minutes: 1)),
  ),
  for (final name in [
    'gastown.mayor',
    'gastown.deacon',
    'gastown.boot',
    'demo-app/gastown.witness',
  ])
    OrchestrationAgent(
      id: name,
      name: name,
      pool: name.split('/').last,
      state: AgentState.stopped,
      rawState: 'suspended',
      suspended: true,
    ),
];

Future<OrchestrationController> _team(
  List<OrchestrationAgent> agents, {
  bool builtin = false,
  OrchestrationUsage? usage,
  bool upkeep = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final config = OrchestrationConfig(
    provider: builtin
        ? OrchestrationProvider.gascity
        : OrchestrationProvider.fixture,
    url: builtin ? 'http://127.0.0.1:8472' : 'http://dev-pc:7000',
    city: 'bright-lights',
    hostMode: builtin
        ? OrchestrationHostMode.phone
        : OrchestrationHostMode.computer,
    enabledAt: DateTime.utc(2026, 9, 10),
  );
  final team = OrchestrationController(
    profile: ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: 'http://127.0.0.1:4097',
      orchestration: config,
    ),
    config: config,
    store: OrchestrationStore(prefs),
    gatewayFactory: (_, _) => _Gateway(
      agentList: agents,
      phone: builtin,
      usageFigure: usage,
      upkeep: upkeep,
    ),
    probe: builtin
        ? (_) async => const ProbeFound(
            host: OrchestrationHostIdentity(
              provider: 'gascity',
              url: 'http://127.0.0.1:8472',
              hostMode: OrchestrationHostMode.phone,
              city: 'bright-lights',
            ),
            city: 'bright-lights',
          )
        : null,
    now: () => _clock,
  );
  await team.start();
  return team;
}

const _spend = OrchestrationUsage(
  evidence: OrchestrationUsageEvidence(
    available: true,
    recording: true,
    isEstimated: true,
    partial: true,
    today: OrchestrationUsageTotals(
      inputTokens: 10000,
      outputTokens: 2400,
      costUsdEstimate: 0.42,
      unpriced: 0,
    ),
  ),
);

enum _Shot {
  page('team_page', _phone),
  pageWide('team_page', _wide),
  agents('team_agents', _phone),
  agentsWide('team_agents', _wide),
  taskCost('task_details_cost', _tallPhone);

  const _Shot(this.state, this.size);
  final String state;
  final Size size;

  String get name {
    final sized = size == _phone || size == _tallPhone
        ? ''
        : '_${size.width.toInt()}x${size.height.toInt()}';
    return 'slice_p52_$state${sized}_light';
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final shot in _Shot.values) {
    testWidgets(shot.name, (tester) async {
      tester.view.physicalSize = shot.size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      resetTeamMoments();
      final OrchestrationController team;
      final Widget home;
      switch (shot) {
        case _Shot.page || _Shot.pageWide:
          team = await _team(
            _leanTeam(),
            builtin: true,
            usage: _spend,
            upkeep: true,
          );
          home = TeamHomeScreen(controller: team, now: () => _clock);
        case _Shot.agents || _Shot.agentsWide:
          team = await _team(_leanTeam(), builtin: true);
          home = TeamAgentsScreen(controller: team, now: () => _clock);
        case _Shot.taskCost:
          team = await teamSceneController(TeamScene.loaded);
          // Task details (P3.5: the run page is retired).
          home = Scaffold(
            body: SingleChildScrollView(
              child: TeamTaskDetails(
                controller: team,
                runId: teamSceneRunId,
                now: () => teamSceneClock,
              ),
            ),
          );
      }
      addTearDown(team.dispose);
      final boundary = GlobalKey();
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      try {
        await tester.pumpWidget(
          MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: true),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: RepaintBoundary(key: boundary, child: child),
            ),
            home: home,
          ),
        );
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(tester.takeException(), isNull);
        // Task details' elapsed time reads the wall clock, so only its body
        // (where the cost line is) is pinned.
        await expectLater(
          shot == _Shot.taskCost
              ? find.byKey(const ValueKey('team-task-details-body'))
              : find.byKey(boundary),
          matchesGoldenFile('goldens/${shot.name}.png'),
        );
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
    });
  }
}
