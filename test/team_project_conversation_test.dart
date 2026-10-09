import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_conversation.dart';

import 'kit/kit_harness.dart';

class _Gateway implements OrchestrationProjectGateway {
  _Gateway({this.requests = const [], this.promotionReady = false});
  final bool promotionReady;
  final List<TeamRequest> requests;
  final commands = <TeamProjectCommand>[];
  bool rejectMessage = false;
  @override
  Future<TeamWorkspace> teamWorkspace() async => TeamWorkspace(
    projects: [
      TeamProject(
        id: 'p',
        name: 'Project',
        revision: 7,
        status: 'running',
        requests: requests,
        planApproved: promotionReady,
        mergeQueue: promotionReady
            ? const [
                TeamMergeItem(
                  id: 'merge',
                  taskId: 'task',
                  repoId: 'repo',
                  status: 'merged',
                  checksPassed: true,
                ),
              ]
            : const [],
        repos: [
          TeamRepo(
            id: 'repo',
            name: 'App',
            mainCommit: 'main-reviewed',
            devCommit: 'dev-reviewed',
          ),
        ],
        tasks: [
          TeamTask(
            id: 'task',
            title: 'Keep drafts after restart',
            status: promotionReady ? 'merged' : 'review',
            repoId: 'repo',
            findings: promotionReady
                ? const []
                : [
                    TeamFinding(id: 'f1', text: 'First finding'),
                    TeamFinding(id: 'f2', text: 'Second finding'),
                  ],
          ),
        ],
      ),
    ],
  );
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand command) async {
    commands.add(command);
    return TeamCommandResult(
      accepted:
          !(rejectMessage && command.action == TeamProjectAction.messageTask),
      code: 'saveFailed',
    );
  }

  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

Future<_Gateway> _open(WidgetTester tester, {_Gateway? source}) async {
  final gateway = source ?? _Gateway();
  final controller = TeamProjectController(gateway);
  await controller.load();
  addTearDown(controller.dispose);
  final context = await pumpKitHost(tester);
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => TeamProjectConversation(
        controller: controller,
        projectId: 'p',
        taskId: 'task',
      ),
    ),
  );
  await tester.pumpAndSettle();
  return gateway;
}

void main() {
  testWidgets('failed requests offer restart rather than a question answer', (
    tester,
  ) async {
    final gateway = await _open(
      tester,
      source: _Gateway(
        requests: const [
          TeamRequest(
            id: 'recover',
            kind: 'failed',
            taskId: 'task',
            title: 'Build stopped',
          ),
        ],
      ),
    );
    expect(find.text('Send answer'), findsNothing);
    final action = find.text('Start task again');
    await tester.ensureVisible(action);
    await tester.tap(action);
    await tester.pump();
    expect(gateway.commands.single.action, TeamProjectAction.restartTask);
    expect(gateway.commands.single.targetId, 'task');
  });

  testWidgets('Stop task asks first and names the task', (tester) async {
    final gateway = await _open(
      tester,
      source: _Gateway(
        requests: const [
          TeamRequest(
            id: 'recover',
            kind: 'failed',
            taskId: 'task',
            title: 'Build stopped',
          ),
        ],
      ),
    );
    final stop = find.text('Stop task');
    await tester.ensureVisible(stop);
    await tester.tap(stop);
    await tester.pumpAndSettle();
    expect(
      find.textContaining('“Keep drafts after restart” stops'),
      findsOneWidget,
    );
    expect(gateway.commands, isEmpty);
    await tester.tap(find.text('Stop task').last);
    await tester.pumpAndSettle();
    expect(gateway.commands.single.action, TeamProjectAction.stopTask);
  });

  testWidgets('failed message keeps the complete composer draft', (
    tester,
  ) async {
    final gateway = await _open(tester);
    gateway.rejectMessage = true;
    final composer = tester.widget<KitComposer>(find.byType(KitComposer));
    composer.controller.text = 'Keep this unsent instruction';
    composer.onSend();
    await tester.pumpAndSettle();
    expect(gateway.commands.single.text, 'Keep this unsent instruction');
    expect(composer.controller.text, 'Keep this unsent instruction');
    expect(
      find.text(
        'The change was not saved. Refresh and try again; your message is still here.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('fix command includes only the selected finding', (tester) async {
    final gateway = await _open(tester);
    final card = tester.widget<KitFindingsCard>(find.byType(KitFindingsCard));
    card.findings.first.onChanged!(true);
    await tester.pump();
    final fix = find.text('Fix selected');
    await tester.ensureVisible(fix);
    await tester.tap(fix);
    await tester.pumpAndSettle();
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.fixFindings);
    expect(command.targetId, 'task');
    expect(command.findingIds, ['f1']);
    expect(command.expectedRevision, 7);
  });

  testWidgets('promotion needs confirmation and pins both reviewed commits', (
    tester,
  ) async {
    final gateway = await _open(tester, source: _Gateway(promotionReady: true));
    await tester.tap(
      find.widgetWithText(KitButton, 'Promote dev to main').last,
    );
    await tester.pumpAndSettle();
    expect(gateway.commands, isEmpty);
    expect(find.text('App: main-reviewed → dev-reviewed'), findsWidgets);
    await tester.tap(
      find.widgetWithText(KitButton, 'Promote dev to main').last,
    );
    await tester.pumpAndSettle();
    final command = gateway.commands.single;
    expect(command.action, TeamProjectAction.promote);
    expect(command.confirmed, isTrue);
    expect(command.expectedDevCommit, 'dev-reviewed');
    expect(command.expectedMainCommit, 'main-reviewed');
    expect(command.expectedRevision, 7);
  });
}
