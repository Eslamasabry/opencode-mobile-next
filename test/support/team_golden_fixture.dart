// Scenes for the AI Team golden renders (test/goldens/team_golden_test.dart)
// and the QA before/after captures
// (tool/capture/aiteam_design_standard_test.dart): one controller per scene,
// the recorded Gas City fixture with its runs, work, agents and questions
// replaced by a small believable team, and a pinned clock.
//
// The scenes and the controller use only APIs that existed before the
// design-standard migration and the redesign, so the same scenes render the
// old screens for "before" (tool/capture/aiteam_redesign_test.dart);
// [TeamShot.agents] is the redesign's own agents list.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen, TeamWatchLiveScreen;
import 'package:opencode_mobile/ui/screens/team/team_agents_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/team_moments.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The scenes' clock.
final teamSceneClock = DateTime.utc(2026, 9, 11, 9, 41);

/// The run every run-screen scene opens.
const teamSceneRunId = 'oc-xru';

/// The finished, merged task of [TeamScene.loaded].
const teamSceneMergedRunId = 'ma-lqw';

enum TeamScene {
  /// A blocked batch that needs you, a working formula run, and a finished
  /// merged batch under Completed.
  loaded,

  /// Nothing run yet (or the host no longer lists it): the teaching empty
  /// state, and Start a run.
  empty,

  /// The host could not be reached at all.
  failed,

  /// The probe never answers: connecting, then after 8 s not answering.
  connecting,

  /// [loaded] plus nine more open tasks: a list long enough (more than
  /// eight) for search and filters.
  busy,

  /// The host answers that the team is not running yet: it is starting
  /// (the team waking up).
  starting,
}

class _SceneGateway extends FixtureOrchestrationGateway {
  _SceneGateway({
    required super.fixturePath,
    required this.scene,
    super.hostMode,
    super.url,
    this.city,
  });

  /// The city this scene's host reports, when not the recorded one.
  final String? city;

  @override
  OrchestrationHostIdentity? get host {
    final recorded = super.host;
    final name = city;
    if (recorded == null || name == null) return recorded;
    return OrchestrationHostIdentity(
      provider: recorded.provider,
      version: recorded.version,
      city: name,
      url: recorded.url,
      hostMode: recorded.hostMode,
    );
  }

  final TeamScene scene;

  DateTime _ago(Duration d) => teamSceneClock.subtract(d);

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async =>
      scene == TeamScene.empty
      ? const []
      : [
          OrchestrationRun(
            id: teamSceneRunId,
            title: 'Offline-first sessions',
            state: RunState.working,
            kind: RunKind.batch,
            stepCount: 5,
            completedSteps: 1,
            startedAt: _ago(const Duration(hours: 3, minutes: 12)),
            updatedAt: _ago(const Duration(minutes: 4)),
          ),
          OrchestrationRun(
            id: 'mol-upgrade',
            title: 'Upgrade the HTTP client and fix what breaks',
            state: RunState.working,
            kind: RunKind.formula,
            formula: 'mol-upgrade',
            stepCount: 4,
            completedSteps: 2,
            startedAt: _ago(const Duration(minutes: 48)),
            updatedAt: _ago(const Duration(minutes: 2)),
          ),
          OrchestrationRun(
            id: 'ma-lqw',
            title: 'Create hello.py that prints Hello from the AI Team',
            state: RunState.completed,
            kind: RunKind.batch,
            stepCount: 1,
            completedSteps: 1,
            merged: true,
            startedAt: _ago(const Duration(hours: 5, minutes: 20)),
            updatedAt: _ago(const Duration(hours: 5)),
            finishedAt: _ago(const Duration(hours: 5)),
          ),
          if (scene == TeamScene.busy)
            for (var i = 1; i <= 9; i++)
              OrchestrationRun(
                id: 'busy-$i',
                title: 'Follow-up task $i',
                state: i.isEven ? RunState.waiting : RunState.working,
                kind: RunKind.formula,
                formula: 'mol-follow-up',
                stepCount: 3,
                completedSteps: i % 3,
                startedAt: _ago(Duration(minutes: 20 + i)),
                updatedAt: _ago(Duration(minutes: 10 + i)),
              ),
        ];

  @override
  Future<List<WorkItem>> work({String? projectId}) async =>
      scene == TeamScene.empty
      ? const []
      : [
          WorkItem(
            id: 'w-storage',
            title: 'Storage layer',
            state: WorkState.completed,
            runId: teamSceneRunId,
            assignee: 'fox',
            updatedAt: _ago(const Duration(hours: 2)),
          ),
          WorkItem(
            id: 'w-sync',
            title: 'Sync engine',
            state: WorkState.working,
            runId: teamSceneRunId,
            assignee: 'fox',
            dependsOn: const ['w-storage'],
            updatedAt: _ago(const Duration(minutes: 5)),
          ),
          WorkItem(
            id: 'w-conflict',
            title: 'Conflict policy',
            state: WorkState.blocked,
            runId: teamSceneRunId,
            isBlocked: true,
            dependsOn: const ['w-sync'],
            updatedAt: _ago(const Duration(hours: 1)),
          ),
          WorkItem(
            id: 'w-schema',
            title: 'Schema for offline drafts',
            state: WorkState.needsInput,
            runId: teamSceneRunId,
            assignee: 'wolf',
            updatedAt: _ago(const Duration(minutes: 12)),
          ),
          WorkItem(
            id: 'w-tests',
            title: 'Tests for reconnect',
            state: WorkState.queued,
            runId: teamSceneRunId,
            dependsOn: const ['w-sync'],
            updatedAt: _ago(const Duration(hours: 3)),
          ),
          WorkItem(
            id: 'w-hello',
            title: 'Create hello.py that prints Hello from the AI Team',
            state: WorkState.completed,
            runId: 'ma-lqw',
            assignee: 'mole',
            updatedAt: _ago(const Duration(hours: 5)),
          ),
        ];

  @override
  Future<List<OrchestrationAgent>> agents() async => [
    OrchestrationAgent(
      id: 'gastown.mayor',
      name: 'mayor',
      state: AgentState.idle,
      sessionId: 'ma-1',
      pool: 'gastown.mayor',
      sessionStartedAt: _ago(const Duration(hours: 6)),
    ),
    if (scene != TeamScene.empty) ...[
      OrchestrationAgent(
        id: 'fox',
        name: 'fox',
        state: AgentState.working,
        sessionId: 'bl-5qc',
        pool: 'gastown.polecat',
        currentWorkId: 'w-sync',
        contextPercent: 63,
        lastActivity: _ago(const Duration(minutes: 1)),
        sessionStartedAt: _ago(const Duration(hours: 3)),
      ),
      OrchestrationAgent(
        id: 'wolf',
        name: 'wolf',
        state: AgentState.waiting,
        sessionId: 'bl-7wr',
        pool: 'gastown.polecat',
        currentWorkId: 'w-schema',
        contextPercent: 41,
        lastActivity: _ago(const Duration(minutes: 12)),
        sessionStartedAt: _ago(const Duration(hours: 1)),
      ),
    ],
  ];

  @override
  Future<List<OrchestrationGate>> gates() async => scene == TeamScene.empty
      ? const []
      : [
          OrchestrationGate(
            id: 'req-schema-1',
            kind: GateKind.choice,
            title: 'Keep drafts in SQLite or in plain files?',
            prompt:
                'Drafts must survive a restart. SQLite is safer; plain '
                'files are easier to inspect.',
            workId: 'w-schema',
            runId: teamSceneRunId,
            agentId: 'bl-7wr',
            choices: const ['SQLite', 'Plain files'],
            createdAt: _ago(const Duration(minutes: 12)),
          ),
        ];
}

Directory _fixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

/// A started controller for [scene]. Dispose it after the render.
///
/// [onPhone] hosts the team on the phone itself, as the in-app AI Team does
/// (BuiltinTeam.config: loopback 127.0.0.1:8472, city "phone"); otherwise
/// the team runs on a computer (the recorded PC city).
Future<OrchestrationController> teamSceneController(
  TeamScene scene, {
  bool onPhone = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final path = _fixtureRoot().path;
  final config = onPhone
      ? OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'http://127.0.0.1:8472',
          city: 'phone',
          hostMode: OrchestrationHostMode.phone,
          hostKind: OrchestrationHostKind.phone,
          enabledAt: DateTime.utc(2026, 9, 10),
        )
      : OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'http://dev-pc:7000',
          city: 'bright-lights',
          enabledAt: DateTime.utc(2026, 9, 10),
        );
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: 'golden',
      name: onPhone ? 'This phone' : 'Development PC',
      baseUrl: onPhone ? 'http://127.0.0.1:4097' : 'https://server.example',
      orchestration: config,
    ),
    config: config,
    store: OrchestrationStore(prefs),
    gatewayFactory: (_, _) => _SceneGateway(
      fixturePath: path,
      scene: scene,
      hostMode: onPhone
          ? OrchestrationHostMode.phone
          : OrchestrationHostMode.computer,
      url: config.url,
      city: onPhone ? config.city : null,
    ),
    probe: switch (scene) {
      TeamScene.failed => (_) async => const ProbeUnreachable(
        error: 'Connection refused (http://dev-pc:7000)',
      ),
      TeamScene.connecting => (_) => Completer<ProbeVerdict>().future,
      TeamScene.starting => (_) async => const ProbeCityNotRunning(
        city: 'bright-lights',
      ),
      // The fixture provider's own probe finds the recorded city.
      TeamScene.loaded || TeamScene.empty || TeamScene.busy => null,
    },
    now: () => teamSceneClock,
  );
  if (scene == TeamScene.connecting) {
    unawaited(controller.start());
  } else {
    await controller.start();
  }
  return controller;
}

/// Forgets which merged tasks celebrated and which questions waved, so
/// each picture is a fresh start (the app after a restart, with no memory).
void resetTeamMoments() {
  TeamCelebrations.forgetSession();
  TeamNeedsYouLabel.forgetSession();
}

/// One picture of the AI Team: the scene it needs and what to open.
enum TeamShot {
  homeLoaded(TeamScene.loaded, 'team_home_loaded'),
  homeEmpty(TeamScene.empty, 'team_home_empty'),
  homeError(TeamScene.failed, 'team_home_error'),
  homeNotAnswering(TeamScene.connecting, 'team_home_not_answering'),
  // The team host starting: the agents wake up (motion slice D).
  homeStarting(TeamScene.starting, 'team_home_starting'),
  // A task is its conversation (P3.5: the run page is retired). The file
  // names are kept: the design-standard map ties them to the screens.
  runOverview(TeamScene.loaded, 'team_run_overview'),
  // The conversation's Task details sheet: stages, steps, agents, numbers.
  runWork(TeamScene.loaded, 'team_run_work'),
  // A merged task's conversation, the first time: its celebration (slice D).
  runMerged(TeamScene.loaded, 'team_run_merged'),
  startRun(TeamScene.loaded, 'team_start_run'),
  agentOutput(TeamScene.loaded, 'team_agent_output'),
  // The in-app AI Team: the same team hosted on this phone.
  homeLoadedPhone(TeamScene.loaded, 'team_home_loaded_phone', onPhone: true),
  // The agents list the home's one agents row opens.
  agents(TeamScene.loaded, 'team_agents');

  const TeamShot(this.scene, this.fileName, {this.onPhone = false});

  final TeamScene scene;

  /// The team runs on this phone (the in-app AI Team), not a computer.
  final bool onPhone;

  /// The golden and capture file name.
  final String fileName;
}

/// Pumps [shot] at 412x915 under [boundary] and leaves it on screen.
/// Returns the controller; dispose it after the picture.
Future<OrchestrationController> pumpTeamShot(
  WidgetTester tester,
  TeamShot shot, {
  required bool light,
  required GlobalKey boundary,
  required ThemeData Function({bool light}) theme,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  resetTeamMoments();
  final controller = await teamSceneController(
    shot.scene,
    onPhone: shot.onPhone,
  );
  DateTime now() => teamSceneClock;
  final Widget home = switch (shot) {
    TeamShot.runOverview || TeamShot.runWork => TeamConversationScreen(
      team: controller,
      runId: teamSceneRunId,
      now: now,
    ),
    TeamShot.runMerged => TeamConversationScreen(
      team: controller,
      runId: teamSceneMergedRunId,
      now: now,
    ),
    // A worker is its conversation (P3.6): the watching page drawn from
    // the live output.
    TeamShot.agentOutput => TeamWatchLiveScreen(
      team: controller,
      agentId: 'fox',
    ),
    TeamShot.agents => TeamAgentsScreen(controller: controller, now: now),
    _ => TeamHomeScreen(controller: controller, now: now),
  };
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: theme(light: light),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: RepaintBoundary(key: boundary, child: child),
      ),
      home: home,
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 500));
  switch (shot) {
    case TeamShot.homeNotAnswering:
      // Past the 8 s rule (design standard §4).
      await tester.pump(const Duration(seconds: 9));
    case TeamShot.runWork:
      await tester.tap(find.byKey(const ValueKey('team-conversation-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('team-conversation-details')));
    case TeamShot.startRun:
      await tester.tap(find.byKey(const ValueKey('team-home-start-run')));
    case TeamShot.homeLoaded ||
        TeamShot.homeLoadedPhone ||
        TeamShot.homeEmpty ||
        TeamShot.homeError ||
        TeamShot.homeStarting ||
        TeamShot.runMerged ||
        TeamShot.runOverview ||
        TeamShot.agentOutput ||
        TeamShot.agents:
      break;
  }
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
  return controller;
}
