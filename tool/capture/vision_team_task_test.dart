// Captures of the AI Team task (vision slice, 2026-10-20,
// docs/qa/vision-team-task-2026-10-20/README.md): the real
// TeamConversationScreen over the census team world (the recorded Gas City
// fixture with a small believable team), the live aurora painted (still),
// real fonts and blurred shadows, no server. Phone 412x915 dp at 2x pixels,
// one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_team_task_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     --dart-define=VISION_ONLY=merge-dark tool/capture/vision_team_task_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-team-task/<scene>.png
//
// The header's Now line and the gate cards' question look need the chat
// library's side, docs/qa/vision-team-task-2026-10-20/chat-team-conversation.patch;
// the final renders were made with it applied.
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart'
    show TeamConversationScreen, TeamControllerScope;

import 'census/support/i1_team_core_world.dart';
import 'fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import 'audit/audit_env.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-team-task',
);

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('VISION_ONLY');

/// Where the task stands.
enum _Mode {
  /// Every step done, the checks passed: the merge gate waits on the person.
  merge,

  /// A worker is on a step; nothing waits on the person.
  working,

  /// A worker asks the person to choose (a team question).
  question,

  /// Merged into main.
  merged,

  /// The merge request waits for an approval first.
  approve,

  /// A step nobody has picked up for a while: the line under the header
  /// says why, with its ways out.
  stalled,
}

/// The same readiness with the merge request approved on the host.
MergeReadiness _approved(MergeReadiness r) => MergeReadiness(
  runId: r.runId,
  ready: r.ready,
  rig: r.rig,
  targetBranch: r.targetBranch,
  lines: r.lines,
  files: r.files,
  additions: r.additions,
  deletions: r.deletions,
  changes: r.changes,
  boundaries: r.boundaries,
  mergeRequest: MergeRequestInfo(
    id: r.mergeRequest!.id,
    title: r.mergeRequest!.title,
    approvedBy: 'you@laptop',
  ),
  branches: r.branches,
);

enum _Scene {
  mergeDark('merge-dark'),
  mergeLight('merge-light', light: true),
  workingDark('working-dark', mode: _Mode.working),
  workingLight('working-light', mode: _Mode.working, light: true),
  questionDark('question-dark', mode: _Mode.question),
  mergedDark('merged-dark', mode: _Mode.merged),
  approveDark('approve-first-dark', mode: _Mode.approve),
  stalledDark('taking-longer-dark', mode: _Mode.stalled),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.mode = _Mode.merge,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });

  final String file;
  final bool light;
  final _Mode mode;
  final Size size;
  final double scale;
  final Locale locale;
}

String _runFor(_Mode mode) => switch (mode) {
  _Mode.merge || _Mode.approve => teamReviewRunId,
  _Mode.merged => teamMergedRunId,
  _ => teamRunId,
};

/// The task waiting for its merge: both steps done, the second handed to
/// the project's refinery for review, the task itself still open.
OrchestrationRun _reviewRun() {
  final run = teamReviewRun();
  return OrchestrationRun(
    id: run.id,
    title: run.title,
    state: RunState.working,
    rawState: 'open',
    kind: run.kind,
    projectId: run.projectId,
    stepCount: run.stepCount,
    completedSteps: run.completedSteps,
    startedAt: run.startedAt,
    updatedAt: run.updatedAt,
    raw: run.raw,
  );
}

List<WorkItem> _reviewWork() => [
  for (final item in teamReviewWork())
    item.state == WorkState.review
        ? WorkItem(
            id: item.id,
            title: item.title,
            state: WorkState.review,
            runId: item.runId,
            assignee: 'shopfront/gastown.refinery',
            dependsOn: item.dependsOn,
            createdAt: item.createdAt,
            updatedAt: teamClock.subtract(const Duration(minutes: 6)),
            raw: {...item.raw, 'assignee': 'shopfront/gastown.refinery'},
          )
        : item,
];

/// A worker on its step and nothing else in flight: the schema step waits
/// its turn instead of asking.
List<WorkItem> _workingWork(List<WorkItem> work) => [
  for (final item in work)
    item.id == 'w-schema'
        ? WorkItem(
            id: item.id,
            title: item.title,
            state: WorkState.queued,
            projectId: item.projectId,
            runId: item.runId,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
          )
        : item,
];

List<OrchestrationAgent> _workingAgents(List<OrchestrationAgent> agents) => [
  for (final agent in agents)
    if (agent.id != 'wolf') agent,
];

void _configure(CensusTeamGateway g, _Mode mode) {
  switch (mode) {
    case _Mode.merge:
      g
        ..runList = [...g.runList, _reviewRun()]
        ..workList = [...g.workList, ..._reviewWork()]
        ..gateList = []
        ..readiness[teamReviewRunId] = _approved(teamReadiness());
    case _Mode.approve:
      g
        ..runList = [...g.runList, _reviewRun()]
        ..workList = [...g.workList, ..._reviewWork()]
        ..gateList = []
        ..readiness[teamReviewRunId] = teamReadiness();
    case _Mode.working:
      g
        ..gateList = []
        ..workList = _workingWork(g.workList)
        ..agentList = _workingAgents(g.agentList);
    case _Mode.question:
      g.gateList = [teamChoiceGate()];
    case _Mode.merged || _Mode.stalled:
      g.gateList = [];
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    // Arabic falls back to Noto, as on a device.
    final arabic = FontLoader('Noto Sans Arabic');
    for (final face in ['Regular', 'Bold']) {
      final bytes = File(
        'test/fixtures/fonts/NotoSansArabic-$face.ttf',
      ).readAsBytesSync();
      arabic.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await arabic.load();
    final sans = FontLoader('sans-serif');
    for (final face in ['Regular', 'Medium', 'Bold']) {
      final bytes = File(
        'tool/capture/fonts/Roboto-$face.ttf',
      ).readAsBytesSync();
      sans.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await sans.load();
  });
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    if (_only.isNotEmpty && !_only.split(',').contains(scene.file)) continue;
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      tester.view.padding = const FakeViewPadding(top: 64, bottom: 48);
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      // The team's clock is the phone's: the cards count from host times
      // with the real clock.
      teamClock = DateTime.now().toUtc();
      final (team, _) = await teamController(
        configure: (g) => _configure(g, scene.mode),
      );
      final key = GlobalKey();
      final theme = captureTheme(light: scene.light);
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: scene.locale.languageCode == 'en'
                ? theme
                : AppTheme.forLocale(theme, scene.locale),
            locale: scene.locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                disableAnimations: false,
                textScaler: TextScaler.linear(scene.scale),
              ),
              child: auditWrap(context, child!),
            ),
            home: TeamControllerScope(
              team: team,
              child: TeamConversationScreen(
                team: team,
                runId: _runFor(scene.mode),
                now: teamNow,
              ),
            ),
          ),
        ),
      );
      for (var i = 0; i < 20; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      if (scene.mode == _Mode.merge ||
          scene.mode == _Mode.approve ||
          scene.mode == _Mode.question) {
        // Scrolled to where the question waits, as a person reads it.
        final list = tester.state<ScrollableState>(
          find
              .descendant(
                of: find.byKey(const ValueKey('team-conversation-list')),
                matching: find.byType(Scrollable),
              )
              .first,
        );
        list.position.jumpTo(list.position.maxScrollExtent);
        if (scene.scale > 1) {
          // At large text the question leads the screen.
          final card = find.byKey(
            ValueKey(
              scene.mode == _Mode.question
                  ? 'team-conversation-gate-req-schema-1'
                  : 'team-merge-section',
            ),
          );
          await tester.pump();
          Scrollable.ensureVisible(tester.element(card));
        }
        for (var i = 0; i < 12; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      team.dispose();
      await tester.pump(const Duration(minutes: 2));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
