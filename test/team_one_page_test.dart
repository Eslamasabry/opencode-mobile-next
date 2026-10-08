// P0.3 "A team task opens one page from every door" and P0.4 "Stop the task
// from its conversation" (docs/ux-system/programmes.json): a team task is a
// conversation (docs/design/team-conversation-2026-09-26.md, option A), so
// the team-home row and team-home's "Give the team a task" land on it, the
// team's page opened from a conversation comes back to that same task
// rather than stacking a second copy, and the conversation's menu stops the
// task through the confirm sheet with a receipt in the conversation.
//
// The notification door is covered in team_gate_rows_notifications_test.dart,
// the Work row in team_discover_test.dart and the board card in
// team_board_test.dart.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';

import 'support/team_chat_fixture.dart';

Finder _key(String value) => find.byKey(ValueKey(value));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(OrchestrationController, TeamChatGateway)> pumpPage(
    WidgetTester tester,
    Widget Function(OrchestrationController team) page, {
    OrchestrationCapabilities capabilities =
        OrchestrationCapabilities.gascityLoopback,
    List<OrchestrationRun>? runs,
  }) async {
    phoneViewport(tester);
    final (team, gateway) = await bootTeam(
      capabilities: capabilities,
      runs: runs,
    );
    final connection = await teamConnection(
      api: TeamChatApi(const {}),
      repository: TeamChatRepository(const []),
    );
    await tester.pumpWidget(teamChatApp(connection, page(team)));
    await tester.pumpAndSettle();
    return (team, gateway);
  }

  /// Lets the team's own timers (a sent control's confirm window, a
  /// refetch after an event) run out before the test ends.
  Future<void> drain(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(minutes: 10));
  }

  Widget conversation(OrchestrationController team) => TeamControllerScope(
    team: team,
    child: TeamConversationScreen(
      team: team,
      runId: teamTask.id,
      now: () => teamClock,
    ),
  );

  group('P0.3: every door lands on the conversation', () {
    testWidgets('a team-home task row opens its conversation, not RunScreen', (
      tester,
    ) async {
      await pumpPage(
        tester,
        (team) => TeamHomeScreen(controller: team, now: () => teamClock),
      );
      await tester.tap(find.text(teamTask.title));
      await tester.pumpAndSettle();
      expect(find.byType(TeamConversationScreen), findsOneWidget);
      expect(
        tester
            .widget<TeamConversationScreen>(find.byType(TeamConversationScreen))
            .runId,
        teamTask.id,
      );
      expect(find.byKey(const ValueKey('team-run')), findsNothing);
    });

    testWidgets('team-home opened from a conversation comes back to the '
        'same task instead of stacking a second copy', (tester) async {
      await pumpPage(tester, conversation);
      await tester.tap(_key('team-conversation-team-page'));
      await tester.pumpAndSettle();
      expect(find.byType(TeamHomeScreen), findsOneWidget);

      await tester.tap(find.text(teamTask.title));
      await tester.pumpAndSettle();
      expect(find.byType(TeamHomeScreen), findsNothing);
      expect(find.byKey(const ValueKey('team-run')), findsNothing);
      // One conversation in the whole stack, offstage routes included.
      expect(
        find.byType(TeamConversationScreen, skipOffstage: false),
        findsOneWidget,
      );
    });

    testWidgets('"Give the team a task" on team-home lands on the new '
        'task\'s conversation', (tester) async {
      final (_, gateway) = await pumpPage(
        tester,
        (team) => TeamHomeScreen(controller: team, now: () => teamClock),
      );
      await tester.tap(_key('team-home-start-run'));
      await tester.pumpAndSettle();
      // The fixture team has no planner: the direct task form.
      expect(_key('team-start-run-direct'), findsOneWidget);
      await tester.enterText(
        _key('team-start-run-direct-title'),
        'Add a docstring to add()',
      );
      await tester.tap(_key('team-start-run-direct-send'));
      await tester.pumpAndSettle();

      expect(
        gateway.inner.controlCalls.map((call) => call.verb),
        contains('createWork'),
      );
      expect(_key('team-start-run-sheet'), findsNothing);
      expect(find.byType(TeamConversationScreen), findsOneWidget);
      expect(
        find.descendant(
          of: _key('team-conversation-title'),
          matching: find.text('Add a docstring to add()'),
        ),
        findsOneWidget,
      );
      // Back on the team's page, not a second page of the task.
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.byType(TeamHomeScreen), findsOneWidget);
      await drain(tester);
    });
  });

  group('P0.4: Stop task from the conversation', () {
    Future<void> openMenu(WidgetTester tester) async {
      await tester.tap(_key('team-conversation-menu'));
      await tester.pumpAndSettle();
    }

    testWidgets('Stop asks first, cancels the task and shows the receipt', (
      tester,
    ) async {
      final (team, gateway) = await pumpPage(tester, conversation);
      await openMenu(tester);
      expect(_key('team-conversation-details'), findsOneWidget);
      await tester.tap(_key('team-conversation-stop'));
      await tester.pumpAndSettle();

      // The confirm sheet names the task and what happens to its workers.
      expect(_key('team-conversation-stop-confirm'), findsOneWidget);
      expect(find.text('Stop this task?'), findsOneWidget);
      expect(find.textContaining('“${teamTask.title}”'), findsOneWidget);
      expect(find.textContaining('workers still running stop'), findsOneWidget);
      expect(find.text('Keep running'), findsOneWidget);

      // Keep running sends nothing.
      await tester.tap(find.text('Keep running'));
      await tester.pumpAndSettle();
      expect(gateway.inner.controlCalls, isEmpty);
      expect(_key('team-conversation-stop-receipt'), findsNothing);

      await openMenu(tester);
      await tester.tap(_key('team-conversation-stop'));
      await tester.pumpAndSettle();
      await tester.tap(_key('team-conversation-stop-confirm-action'));
      await tester.pumpAndSettle();

      final call = gateway.inner.controlCalls.single;
      expect(call.verb, 'cancelRun');
      expect(call.target, teamTask.id);
      expect(_key('team-conversation-stop-receipt'), findsOneWidget);
      expect(find.textContaining('Stop task · Sending…'), findsOneWidget);

      // The host says the task was cancelled: Confirmed.
      gateway.stream.add(
        RunChanged(runId: teamTask.id, state: RunState.cancelled),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Stop task · Confirmed'), findsOneWidget);
      expect(
        team.latestMutation(kind: MutationKind.cancelRun)?.status,
        MutationStatus.confirmed,
      );
      await drain(tester);
    });

    testWidgets('a refusal reads as the host\'s own words', (tester) async {
      final (_, gateway) = await pumpPage(tester, conversation);
      gateway.cancelRunAnswer = (_, requestId) async =>
          MutationReceipt.rejected(requestId, 'convoy is already closed');
      await openMenu(tester);
      await tester.tap(_key('team-conversation-stop'));
      await tester.pumpAndSettle();
      await tester.tap(_key('team-conversation-stop-confirm-action'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Not accepted'), findsOneWidget);
      expect(find.textContaining('convoy is already closed'), findsOneWidget);
    });

    testWidgets('no Stop on a finished task', (tester) async {
      await pumpPage(
        tester,
        conversation,
        runs: [
          OrchestrationRun(
            id: teamTask.id,
            title: teamTask.title,
            state: RunState.completed,
            kind: RunKind.batch,
            startedAt: teamTask.startedAt,
          ),
        ],
      );
      await openMenu(tester);
      expect(_key('team-conversation-details'), findsOneWidget);
      expect(_key('team-conversation-stop'), findsNothing);
    });

    testWidgets('no Stop where the host takes no cancel control', (
      tester,
    ) async {
      await pumpPage(
        tester,
        conversation,
        capabilities: OrchestrationCapabilities.gascityRead,
      );
      await openMenu(tester);
      expect(_key('team-conversation-details'), findsOneWidget);
      expect(_key('team-conversation-stop'), findsNothing);
    });
  });
}
