// E2E 2026-09-30 fixes: B-10 (plan card), B-11 (merge and promote flows),
// B-16 (timeline noise, lane default), B-17 (gap under a flat card).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/project_fixture_gateway.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_conversation.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';

import 'kit/kit_harness.dart';

class _Memory implements TeamProjectPersistence {
  String? value;
  @override
  Future<String?> read() async => value;
  @override
  Future<void> write(String next) async => value = next;
  @override
  Future<void> delete() async => value = null;
}

/// A gateway that applies merge and promote the way the engine would, so the
/// receipt rows can be seen after the command.
class _Gateway implements OrchestrationProjectGateway {
  _Gateway({required this.project, this.servers = const []});
  TeamProject project;
  final List<TeamServer> servers;
  final commands = <TeamProjectCommand>[];
  @override
  Future<TeamWorkspace> teamWorkspace() async =>
      TeamWorkspace(servers: servers, projects: [project]);
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand command) async {
    commands.add(command);
    if (command.action == TeamProjectAction.processMergeQueue) {
      project = project.copyWith(
        mergeQueue: [
          for (final m in project.mergeQueue)
            m.copyWith(status: 'merged', checksPassed: true),
        ],
        tasks: [for (final t in project.tasks) t.copyWith(status: 'merged')],
        repos: [
          for (final r in project.repos) r.copyWith(devCommit: 'dev2222222'),
        ],
        receipts: [
          const TeamProjectReceipt(
            id: 'r1',
            kind: 'merge',
            repoId: 'repo',
            before: 'dev1111111',
            after: 'dev2222222',
          ),
        ],
      );
    }
    return const TeamCommandResult(accepted: true);
  }

  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

TeamProject _mergeable() => const TeamProject(
  id: 'p',
  name: 'Project',
  status: 'running',
  planApproved: true,
  specDraft: TeamSpec(
    goal: 'Ship it',
    milestones: [TeamMilestone(id: 'm', title: 'First')],
  ),
  phases: [TeamPhase(id: 'ph', milestoneId: 'm', title: 'Build')],
  repos: [
    TeamRepo(
      id: 'repo',
      name: 'App',
      mainCommit: 'main0000000',
      devCommit: 'dev1111111',
      checkCommand: 'make test',
    ),
  ],
  tasks: [
    TeamTask(
      id: 'task',
      title: 'Keep drafts',
      phaseId: 'ph',
      repoId: 'repo',
      serverId: 'pc',
      status: 'done',
    ),
  ],
  mergeQueue: [TeamMergeItem(id: 'merge', taskId: 'task', repoId: 'repo')],
);

Future<void> _openOverview(WidgetTester tester, _Gateway gateway) async {
  final c = TeamProjectController(gateway);
  await c.load();
  addTearDown(c.dispose);
  final context = await pumpKitHost(tester);
  pushKitPage<void>(
    context,
    (_) => Scaffold(
      body: TeamProjectOverview(controller: c, projectId: 'p'),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'B-11: merging asks first, names the effect, then leaves a receipt',
    (tester) async {
      final gateway = _Gateway(project: _mergeable());
      await _openOverview(tester, gateway);
      final merge = find.widgetWithText(
        KitButton,
        'Merge checked work into dev',
      );
      await tester.ensureVisible(merge);
      await tester.tap(merge);
      await tester.pumpAndSettle();
      expect(gateway.commands, isEmpty, reason: 'nothing runs before the yes');
      expect(
        find.text('1 checked task from App will be merged into dev.'),
        findsOneWidget,
      );
      expect(find.text('Main is not touched.'), findsOneWidget);
      await tester.tap(
        find.widgetWithText(KitButton, 'Merge checked work into dev').last,
      );
      await tester.pumpAndSettle();
      final command = gateway.commands.single;
      expect(command.action, TeamProjectAction.processMergeQueue);
      expect(command.confirmed, isTrue);
      expect(command.targetId, 'repo');
      expect(find.text('Merged into dev · App'), findsOneWidget);
      expect(find.text('dev1111 → dev2222'), findsOneWidget);
    },
  );

  testWidgets('B-11: Promote dev to main is reachable from the overview', (
    tester,
  ) async {
    final gateway = _Gateway(
      project: _mergeable().copyWith(
        tasks: [_mergeable().tasks.single.copyWith(status: 'merged')],
        mergeQueue: [
          const TeamMergeItem(
            id: 'merge',
            taskId: 'task',
            repoId: 'repo',
            status: 'merged',
            checksPassed: true,
          ),
        ],
      ),
    );
    await _openOverview(tester, gateway);
    final promote = find.widgetWithText(KitButton, 'Promote dev to main');
    expect(promote, findsOneWidget);
    await tester.ensureVisible(promote);
    await tester.tap(promote);
    await tester.pumpAndSettle();
    expect(gateway.commands, isEmpty);
    expect(find.text('App: main0000000 → dev1111111'), findsWidgets);
    await tester.tap(
      find.widgetWithText(KitButton, 'Promote dev to main').last,
    );
    await tester.pumpAndSettle();
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.promote);
    expect(command.confirmed, isTrue);
    expect(command.expectedMainCommit, 'main0000000');
  });

  testWidgets('B-10: the plan card can wait, and each task picks its server', (
    tester,
  ) async {
    final gateway = _Gateway(
      project: _mergeable().copyWith(
        status: 'plan',
        planApproved: false,
        mergeQueue: const [],
        tasks: [_mergeable().tasks.single.copyWith(status: 'queued')],
      ),
      servers: const [
        TeamServer(id: 'pc', name: 'Home PC'),
        TeamServer(id: 'phone', name: 'This phone', phone: true),
      ],
    );
    final c = TeamProjectController(gateway);
    await c.load();
    addTearDown(c.dispose);
    final context = await pumpKitHost(tester);
    pushKitPage<void>(
      context,
      (_) => TeamProjectConversation(
        controller: c,
        projectId: 'p',
        taskId: 'task',
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Ask to change'), findsOneWidget);
    expect(find.text('Not yet'), findsOneWidget);
    await tester.tap(
      find.descendant(
        of: find.byType(KitPlanCard),
        matching: find.text('Home PC'),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('This phone').last);
    await tester.pumpAndSettle();
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.moveTask);
    expect(command.targetId, 'task');
    expect(command.serverId, 'phone');
  });

  test(
    'B-16: a work tick writes no generic row, and Parallel starts above one',
    () async {
      final gateway = ProjectFixtureGateway(persistence: _Memory());
      addTearDown(gateway.close);
      for (var i = 0; i < 6; i++) {
        await gateway.advance();
      }
      final w = await gateway.teamWorkspace();
      for (final p in w.projects) {
        expect(
          p.timeline.where((e) => e.text == 'Simulated work advanced'),
          isEmpty,
        );
      }
      expect(w.defaultSettings?.maxLanes, greaterThan(1));
    },
  );

  testWidgets('B-16: back-to-back identical timeline rows fold into one', (
    tester,
  ) async {
    const row = TeamTimelineEvent(
      kind: 'merge',
      text: 'Something happened',
      at: '2026-09-30T10:00:00Z',
    );
    final gateway = _Gateway(
      project: _mergeable().copyWith(
        timeline: [
          for (var i = 0; i < 4; i++)
            TeamTimelineEvent(
              id: 'e$i',
              kind: row.kind,
              text: row.text,
              at: row.at,
            ),
        ],
      ),
    );
    final c = TeamProjectController(gateway);
    await c.load();
    addTearDown(c.dispose);
    final context = await pumpKitHost(tester);
    pushKitPage<void>(
      context,
      (_) => TeamProjectTimeline(controller: c, projectId: 'p'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Something happened · 4 times'), findsOneWidget);
    expect(find.text('Something happened'), findsNothing);
  });

  testWidgets('B-17: a flat card leaves a gap before what follows', (
    tester,
  ) async {
    final context = await pumpKitHost(tester);
    pushKitPage<void>(
      context,
      (_) => Scaffold(
        body: Column(
          children: [
            KitMergeQueue(
              title: 'Queue',
              status: 'dev',
              actions: [KitAction(label: 'Merge now', onPressed: () {})],
            ),
            const Text('Next heading'),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();
    final button = tester.getBottomLeft(
      find.widgetWithText(KitButton, 'Merge now'),
    );
    final next = tester.getTopLeft(find.text('Next heading'));
    expect(next.dy - button.dy, greaterThanOrEqualTo(12));
  });
}
