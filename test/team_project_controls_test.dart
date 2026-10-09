// Controls on the project pages that send a command to the team's engine and
// had no test of their own: Mark as read (digest), Accept (milestone), Ignore
// (a finding), and saving the project's settings. Fakes only.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_conversation.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_editors.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit/kit_harness.dart';

final _en = lookupAppLocalizations(const Locale('en'));

class _Gateway implements OrchestrationProjectGateway {
  _Gateway({this.finished = false});
  final bool finished;
  final commands = <TeamProjectCommand>[];

  @override
  Future<TeamWorkspace> teamWorkspace() async => TeamWorkspace(
    servers: const [TeamServer(id: 'pc', name: 'Home PC', laneCap: 8)],
    roles: const [TeamProjectRole(id: 'frontend', name: 'Frontend')],
    projects: [
      TeamProject(
        id: 'site',
        name: 'Launch site',
        revision: 4,
        status: 'running',
        settings: const TeamProjectSettings(
          mode: 'parallel',
          maxLanes: 3,
          budget: TeamBudget(chosen: true, unlimited: true),
        ),
        repos: const [TeamRepo(id: 'r', name: 'web', serverId: 'pc')],
        specDraft: const TeamSpec(
          goal: 'Make our site accessible',
          milestones: [TeamMilestone(id: 'm', title: 'Accessible pages')],
        ),
        phases: const [
          TeamPhase(
            id: 'ph',
            milestoneId: 'm',
            title: 'Build pages',
            accepted: true,
          ),
        ],
        timeline: const [
          TeamTimelineEvent(
            id: 'e1',
            kind: 'event',
            text: 'Pricing page merged',
            at: '2026-10-09T08:00:00Z',
          ),
        ],
        tasks: [
          const TeamTask(
            id: 't',
            title: 'Pricing page',
            phaseId: 'ph',
            roleId: 'frontend',
            repoId: 'r',
            serverId: 'pc',
            status: 'merged',
          ),
          TeamTask(
            id: 'review',
            title: 'Contact form',
            phaseId: 'ph',
            repoId: 'r',
            status: finished ? 'merged' : 'review',
            findings: const [
              TeamFinding(id: 'f1', text: 'Label missing on the email field'),
            ],
          ),
        ],
      ),
    ],
  );
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand c) async {
    commands.add(c);
    return const TeamCommandResult(accepted: true);
  }

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

Future<_Gateway> _open(
  WidgetTester tester,
  Widget Function(TeamProjectController) page, {
  bool finished = false,
}) async {
  final gateway = _Gateway(finished: finished);
  final c = TeamProjectController(gateway);
  await c.load();
  addTearDown(c.dispose);
  final context = await pumpKitHost(tester);
  pushKitPage<void>(context, (_) => page(c));
  await _settle(tester);
  return gateway;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('Mark as read sends acknowledgeDigest for the project', (
    tester,
  ) async {
    final gateway = await _open(
      tester,
      (c) => TeamProjectOverview(controller: c, projectId: 'site'),
    );
    final read = find.text(_en.teamProjectDigestRead);
    await tester.ensureVisible(read);
    await tester.tap(read);
    await _settle(tester);
    expect(gateway.commands.single.action, TeamProjectAction.acknowledgeDigest);
    expect(gateway.commands.single.projectId, 'site');
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Accept on a finished milestone sends acceptMilestone', (
    tester,
  ) async {
    final gateway = await _open(
      tester,
      (c) => TeamProjectOverview(controller: c, projectId: 'site'),
      finished: true,
    );
    final accept = find.text(_en.teamProjectAccept);
    await tester.ensureVisible(accept.first);
    await tester.tap(accept.first);
    await _settle(tester);
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.acceptMilestone);
    expect(command.targetId, 'm');
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Accept waits while a task of the milestone is unmerged', (
    tester,
  ) async {
    final gateway = await _open(
      tester,
      (c) => TeamProjectOverview(controller: c, projectId: 'site'),
    );
    expect(find.text(_en.teamProjectAccept), findsNothing);
    expect(gateway.commands, isEmpty);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Ignore a finding asks for a reason and sends ignoreFinding', (
    tester,
  ) async {
    final gateway = await _open(
      tester,
      (c) => TeamProjectConversation(
        controller: c,
        projectId: 'site',
        taskId: 'review',
      ),
    );
    final card = tester.widget<KitFindingsCard>(find.byType(KitFindingsCard));
    card.findings.first.onChanged!(true);
    await tester.pump();
    final ignore = find.text(_en.teamProjectTaskIgnore);
    await tester.ensureVisible(ignore.first);
    await tester.tap(ignore.first);
    await _settle(tester);
    await tester.enterText(
      find.byType(TextField).last,
      'Fixed by the redesign',
    );
    await tester.tap(find.text(_en.teamProjectTaskIgnore).last);
    await _settle(tester);
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.ignoreFinding);
    expect(command.findingIds, ['f1']);
    expect(command.text, 'Fixed by the redesign');
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Save changes on the project settings sends updateSettings', (
    tester,
  ) async {
    final gateway = _Gateway();
    final c = TeamProjectController(gateway);
    await c.load();
    addTearDown(c.dispose);
    final context = await pumpKitHost(tester);
    unawaited(openTeamProjectSettings(context, c, 'site'));
    await _settle(tester);
    final save = find.text(_en.teamProjectEditorSave);
    await tester.ensureVisible(save.first);
    await tester.tap(save.first);
    await _settle(tester);
    expect(gateway.commands.single.action, TeamProjectAction.updateSettings);
    expect(gateway.commands.single.projectId, 'site');
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Ask for a change sends the draft, then requestSpecChange', (
    tester,
  ) async {
    final gateway = _Gateway();
    final c = TeamProjectController(gateway);
    await c.load();
    addTearDown(c.dispose);
    final context = await pumpKitHost(tester);
    unawaited(openTeamSpecEditor(context, c, 'site'));
    await _settle(tester);
    await tester.enterText(
      find.descendant(
        of: find.byKey(const ValueKey('changeRequest')),
        matching: find.byType(TextField),
      ),
      'Add a pricing FAQ to the first milestone',
    );
    final ask = find.text(_en.teamProjectEditorAskChange);
    await tester.ensureVisible(ask);
    await tester.tap(ask);
    await _settle(tester);
    expect(
      [for (final c in gateway.commands) c.action],
      [TeamProjectAction.saveSpecDraft, TeamProjectAction.requestSpecChange],
    );
    expect(
      gateway.commands.last.text,
      'Add a pricing FAQ to the first milestone',
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Save changes on a role sends saveRole', (tester) async {
    final gateway = _Gateway();
    final c = TeamProjectController(gateway);
    await c.load();
    addTearDown(c.dispose);
    final context = await pumpKitHost(tester);
    unawaited(openTeamRoles(context, c));
    await _settle(tester);
    await tester.tap(find.text('Frontend'));
    await _settle(tester);
    final save = find.text(_en.teamProjectEditorSave);
    await tester.ensureVisible(save.first);
    await tester.tap(save.first);
    await _settle(tester);
    expect(gateway.commands.single.action, TeamProjectAction.saveRole);
    expect(gateway.commands.single.role?.id, 'frontend');
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
