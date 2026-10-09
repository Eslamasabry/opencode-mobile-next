// E2E 2026-09-30 B-12, B-14, B-15: the board opens where the work is, the
// overview's page rows are not under "Cost", the servers page agrees with
// the project's lane limit, and a queued merge never says it waits for
// dependencies. Fakes only.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit/kit_harness.dart';

final _en = lookupAppLocalizations(const Locale('en'));

class _Gateway implements OrchestrationProjectGateway {
  @override
  Future<TeamWorkspace> teamWorkspace() async => const TeamWorkspace(
    servers: [TeamServer(id: 'pc', name: 'Home PC', laneCap: 8)],
    roles: [TeamProjectRole(id: 'frontend', name: 'Frontend')],
    projects: [
      TeamProject(
        id: 'site',
        name: 'Launch site',
        status: 'running',
        settings: TeamProjectSettings(mode: 'parallel', maxLanes: 3),
        repos: [TeamRepo(id: 'r', name: 'web', serverId: 'pc')],
        specDraft: TeamSpec(
          goal: 'Make our site accessible',
          milestones: [TeamMilestone(id: 'm', title: 'Accessible pages')],
        ),
        phases: [TeamPhase(id: 'ph', milestoneId: 'm', title: 'Build pages')],
        tasks: [
          TeamTask(
            id: 't',
            title: 'Pricing page',
            phaseId: 'ph',
            roleId: 'frontend',
            repoId: 'r',
            serverId: 'pc',
            status: 'done',
          ),
        ],
        mergeQueue: [
          TeamMergeItem(
            id: 'merge-t',
            taskId: 't',
            repoId: 'r',
            checksPassed: true,
          ),
        ],
      ),
    ],
  );
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand c) async =>
      const TeamCommandResult(accepted: true);
  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<TeamProjectController> _open(
  WidgetTester tester,
  Widget Function(TeamProjectController) page,
) async {
  final c = TeamProjectController(_Gateway());
  await c.load();
  addTearDown(c.dispose);
  final context = await pumpKitHost(tester);
  pushKitPage<void>(context, (_) => page(c));
  await _settle(tester);
  return c;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('B-12: the board opens on the column that has the task, with '
      'the header on the gutter', (tester) async {
    await _open(
      tester,
      (c) => TeamProjectBoard(controller: c, projectId: 'site'),
    );
    // The only task is Done: its card is on screen without a tap.
    expect(find.text('Pricing page'), findsOneWidget);
    final segmented = tester.getRect(find.byType(KitSegmented<bool>));
    expect(segmented.left, greaterThan(0));
    expect(segmented.right, lessThan(tester.view.physicalSize.width));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('B-14: Board, Timeline, Servers, Settings are not under the '
      'Cost heading', (tester) async {
    await _open(
      tester,
      (c) => TeamProjectOverview(controller: c, projectId: 'site'),
    );
    await tester.scrollUntilVisible(
      find.text(_en.teamProjectSettings),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    double y(String text) => tester.getTopLeft(find.text(text).first).dy;
    // Every page row sits above the Cost heading.
    expect(y(_en.teamProjectSettings), lessThan(y(_en.teamProjectCost)));
    expect(y(_en.teamProjectPages), lessThan(y(_en.teamProjectBoard)));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Stop project asks first and names the project', (tester) async {
    await _open(
      tester,
      (c) => TeamProjectOverview(controller: c, projectId: 'site'),
    );
    await tester.tap(find.byTooltip(_en.teamProjectMenu));
    await _settle(tester);
    await tester.tap(find.text(_en.teamProjectStop));
    await _settle(tester);
    expect(find.textContaining('“Launch site” stops'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('B-15: servers show the project lane limit; a queued merge '
      'says it is ready, not waiting for dependencies', (tester) async {
    final c = await _open(
      tester,
      (c) => TeamProjectServers(controller: c, projectId: 'site'),
    );
    expect(find.text(_en.teamProjectLaneCount(0, 3)), findsOneWidget);
    expect(find.text(_en.teamProjectLaneCount(0, 8)), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());

    final context = await pumpKitHost(tester);
    pushKitPage<void>(
      context,
      (_) => TeamProjectOverview(controller: c, projectId: 'site'),
    );
    await _settle(tester);
    expect(find.text(_en.teamProjectMergeReady), findsOneWidget);
    expect(find.text(_en.teamProjectWaiting), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
