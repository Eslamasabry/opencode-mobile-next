// TEAM-204: agent controls, run controls and Start a run.
//
// Controls are absent (not disabled) without the `control*` capabilities
// and present with `gascityFront`; Nudge is one tap with a receipt chip
// that follows the record (Sent → Confirmed / Unconfirmed); Stop, Restart
// and Cancel run are two-step (the first tap opens the confirmation, the
// second sends, backing out sends nothing); messaging the worker is its
// conversation's composer (slice-P3.6), which sends the text;
// Manual reassign is absent (the dispatcher owns it); Start a run sends the
// objective and the supervision line to the Mayor, shows "Planning…
// (Mayor)" on the home, resolves when a run carrying the objective
// appears, and a suspended Mayor opens "Team can't take tasks" with Wake
// the planner, without sending.
// TEAM-306: on a host that creates work (the phone's loopback) a suspended
// Mayor shows the direct task form instead, which creates one bead and
// slings it at the project's polecat pool; a refused create stays on the
// sheet and says why; a front host keeps the host-off copy.
// Layout: 320dp × 2.5x, LTR and RTL, for the agent controls and the sheet.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamWatchLiveScreen;
import 'package:shared_preferences/shared_preferences.dart';
import 'support/team_controls_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
    clock = DateTime.utc(2026, 9, 11, 9, 41);
    nextKey = 0;
  });

  group('gating', () {
    testWidgets('read-only host: no controls, no FAB, no run menu', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, _) = await boot(
        capabilities: OrchestrationCapabilities.gascityRead,
      );
      await pumpAgent(tester, controller);
      // Only the way to its work (its conversation) remains.
      expect(key('team-agent-control-message'), findsNothing);
      expect(key('team-agent-control-nudge'), findsNothing);
      expect(key('team-agent-control-pause'), findsNothing);
      expect(key('team-agent-open-conversation'), findsOneWidget);
      expect(key('team-agent-open-output'), findsNothing);
      expect(find.text('Open session'), findsNothing);

      await tester.pumpWidget(
        app(TeamHomeScreen(controller: controller, now: () => clock)),
      );
      await settle(tester);
      expect(key('team-home-start-run'), findsNothing);

      // The task's conversation (RunScreen retired, P3.5): its menu holds
      // Task details, and a read-only host adds no Stop task.
      await tester.pumpWidget(app(conversation(controller, 'oc-xru')));
      await settle(tester);
      await tester.tap(key('team-conversation-menu'));
      await tester.pumpAndSettle();
      expect(key('team-conversation-details'), findsOneWidget);
      expect(key('team-conversation-stop'), findsNothing);
    });

    testWidgets('front host: every control, never Open session', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, _) = await boot();
      await pumpAgent(tester, controller);
      await openMore(tester);
      // Messaging is the conversation's composer, not a control here.
      expect(key('team-agent-control-message'), findsNothing);
      expect(key('team-agent-open-conversation'), findsOneWidget);
      for (final id in ['nudge', 'pause', 'stop', 'restart']) {
        expect(key('team-agent-control-$id'), findsOneWidget, reason: id);
      }
      expect(key('team-agent-control-resume'), findsNothing);
      expect(find.text('Open session'), findsNothing);
      expect(find.text('Open worktree'), findsNothing);
      expect(key('team-agent-control-reassign'), findsNothing);
      expect(find.text('Reassign work…'), findsNothing);
    });

    testWidgets('only the granted controls exist', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, _) = await boot(
        capabilities: const OrchestrationCapabilities(
          runs: true,
          agents: true,
          agentOutput: true,
          controlMessage: true,
        ),
      );
      await pumpAgent(tester, controller);
      // Messaging lives in the conversation; the page offers it.
      expect(key('team-agent-open-conversation'), findsOneWidget);
      expect(key('team-agent-control-message'), findsNothing);
      expect(key('team-agent-control-nudge'), findsNothing);
      expect(key('team-agent-control-stop'), findsNothing);
      expect(key('team-agent-control-reassign'), findsNothing);
    });

    testWidgets('a stopped agent offers Resume and no Stop', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, _) = await boot(
        configure: (g) => g.agentList = [fox(state: AgentState.stopped)],
      );
      await pumpAgent(tester, controller);
      expect(key('team-agent-control-resume'), findsOneWidget);
      expect(key('team-agent-control-pause'), findsNothing);
      await openMore(tester);
      expect(key('team-agent-control-stop'), findsNothing);
      expect(key('team-agent-control-restart'), findsOneWidget);
    });
  });

  group('nudge', () {
    testWidgets('one tap sends the nudge and the chip follows the record', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        timeout: const Duration(seconds: 30),
      );
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-nudge'));
      await settle(tester);
      expect(gateway.calls, hasLength(1));
      expect(gateway.calls.single.verb, 'controlAgent');
      expect(gateway.calls.single.target, 'fox');
      expect(gateway.calls.single.arg, AgentControlAction.nudge);
      final record = controller.latestMutation(
        kind: MutationKind.controlAgent,
        targetId: 'fox',
      );
      expect(record?.status, MutationStatus.sent);
      expect(key('team-agent-receipt'), findsOneWidget);
      expect(find.textContaining('Nudge · Sending…'), findsOneWidget);

      gateway.push(
        const RequestResult(requestId: 'corr-key-1', ok: true, seq: 10),
      );
      await settle(tester);
      expect(find.textContaining('Nudge · Confirmed'), findsOneWidget);
      expect(gateway.calls, hasLength(1));
    });

    testWidgets('no result inside the window: Unconfirmed, never re-sent', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        timeout: const Duration(milliseconds: 100),
      );
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-nudge'));
      await settle(tester);
      expect(find.textContaining('Nudge · Sending…'), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 200));
      await settle(tester);
      expect(find.textContaining('Not confirmed yet'), findsOneWidget);
      expect(key('team-agent-receipt-retry'), findsOneWidget);
      expect(gateway.calls, hasLength(1));
    });

    testWidgets('the host refuses: Not accepted with the reason', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        configure: (g) =>
            g.answer = (call) async =>
                MutationReceipt.rejected(call.requestId, 'session is gone'),
      );
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-pause'));
      await settle(tester);
      expect(gateway.calls.single.arg, AgentControlAction.pause);
      // The one KitReceipt says the host's reason in its refusal words.
      expect(
        find.textContaining('Not accepted', findRichText: true),
        findsOneWidget,
      );
      expect(find.textContaining('session is gone'), findsOneWidget);
      expect(key('team-agent-pause-undo'), findsNothing);
    });
    testWidgets('an unconfirmed pause never claims the agent paused', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        configure: (g) => g.answer = (call) async => MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.pending,
          retryable: true,
        ),
      );
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-pause'));
      await settle(tester);
      expect(gateway.calls, hasLength(1));
      expect(gateway.calls.single.verb, 'controlAgent');
      expect(gateway.calls.single.target, 'fox');
      expect(gateway.calls.single.arg, AgentControlAction.pause);
      expect(
        controller
            .latestMutation(kind: MutationKind.controlAgent, targetId: 'fox')
            ?.status,
        MutationStatus.unconfirmed,
      );
      expect(find.textContaining('Not confirmed yet'), findsOneWidget);
      expect(key('team-agent-receipt-retry'), findsOneWidget);
      expect(key('team-agent-pause-undo'), findsNothing);
      expect(find.text('Paused fox'), findsNothing);
      await drain(tester);
      expect(
        gateway.calls,
        hasLength(1),
        reason: 'never automatically retried',
      );
    });
  });

  group('two-step', () {
    testWidgets('Stop: first tap confirms, second sends', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-stop'));
      await tester.pumpAndSettle();
      expect(key('team-agent-stop-confirm'), findsOneWidget);
      expect(find.text('Stop Worker · fox?'), findsOneWidget);
      expect(gateway.calls, isEmpty);
      await tester.tap(key('team-agent-stop-confirm-action'));
      await tester.pumpAndSettle();
      expect(gateway.calls.single.arg, AgentControlAction.stop);
      expect(find.textContaining('Stop · Sending…'), findsOneWidget);
      await drain(tester);
    });

    testWidgets('Stop: backing out sends nothing', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-stop'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Keep running'));
      await tester.pumpAndSettle();
      expect(key('team-agent-stop-confirm'), findsNothing);
      expect(gateway.calls, isEmpty);
      expect(key('team-agent-receipt'), findsNothing);
    });

    testWidgets('Restart: confirmation then the restart action', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await pumpAgent(tester, controller);
      await openMore(tester);
      await tester.tap(key('team-agent-control-restart'));
      await tester.pumpAndSettle();
      expect(find.text('Restart Worker · fox?'), findsOneWidget);
      expect(gateway.calls, isEmpty);
      await tester.tap(key('team-agent-restart-confirm-action'));
      await tester.pumpAndSettle();
      expect(gateway.calls.single.arg, AgentControlAction.restart);
      expect(find.textContaining('Restart · Sending…'), findsOneWidget);
      await drain(tester);
    });

    // RunScreen's Cancel run / Close batch is retired (P3.5): the task's
    // conversation owns Stop task, confirmed, with its receipt.
    testWidgets('Stop task: menu → confirmation → cancelRun', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await tester.pumpWidget(app(conversation(controller, 'oc-xru')));
      await settle(tester);
      await tester.tap(key('team-conversation-menu'));
      await tester.pumpAndSettle();
      await tester.tap(key('team-conversation-stop'));
      await tester.pumpAndSettle();
      expect(key('team-conversation-stop-confirm'), findsOneWidget);
      expect(gateway.calls, isEmpty);
      await tester.tap(key('team-conversation-stop-confirm-action'));
      await tester.pumpAndSettle();
      expect(gateway.calls.single.verb, 'cancelRun');
      expect(gateway.calls.single.target, 'oc-xru');
      expect(key('team-conversation-stop-receipt'), findsOneWidget);
      expect(find.textContaining('Stop task · Sending…'), findsOneWidget);
      await drain(tester);
    });

    testWidgets('Stop task: backing out sends nothing', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await tester.pumpWidget(app(conversation(controller, 'oc-xru')));
      await settle(tester);
      await tester.tap(key('team-conversation-menu'));
      await tester.pumpAndSettle();
      await tester.tap(key('team-conversation-stop'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Keep running'));
      await tester.pumpAndSettle();
      expect(gateway.calls, isEmpty);
      expect(key('team-conversation-stop-receipt'), findsNothing);
    });

    testWidgets('a batch offers Stop task; a finished task offers nothing', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, _) = await boot(
        configure: (g) => g.runList = [
          run(kind: RunKind.batch),
          run(id: 'oc-done', state: RunState.completed),
        ],
      );
      await tester.pumpWidget(app(conversation(controller, 'oc-xru')));
      await settle(tester);
      await tester.tap(key('team-conversation-menu'));
      await tester.pumpAndSettle();
      expect(key('team-conversation-stop'), findsOneWidget);
      await tester.tapAt(Offset.zero);
      await tester.pumpAndSettle();

      await tester.pumpWidget(app(conversation(controller, 'oc-done')));
      await settle(tester);
      // The menu holds Refresh and Task details only: nothing to stop.
      await tester.tap(key('team-conversation-menu'));
      await tester.pumpAndSettle();
      expect(key('team-conversation-details'), findsOneWidget);
      expect(key('team-conversation-stop'), findsNothing);
    });
  });

  group('message and reassign', () {
    testWidgets('Message: the conversation composer sends the text', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await tester.pumpWidget(
        app(TeamWatchLiveScreen(team: controller, agentId: 'fox')),
      );
      await settle(tester);
      // The composer addresses the worker; empty text cannot be sent.
      expect(key('chat-watching-composer'), findsOneWidget);
      expect(find.text('Message Worker…'), findsWidgets);
      await tester.tap(key('chat-watching-message-send'));
      await tester.pump();
      expect(gateway.calls, isEmpty);
      await tester.enterText(
        key('chat-watching-message-field'),
        'Use the offline queue for the tests',
      );
      await tester.pump();
      await tester.tap(key('chat-watching-message-send'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(gateway.calls.single.verb, 'message');
      expect(gateway.calls.single.target, 'fox');
      expect(gateway.calls.single.arg, 'Use the offline queue for the tests');
      // Taken: the field is cleared and the receipt is above the composer.
      expect(key('chat-watching-message-receipt'), findsOneWidget);
      expect(
        tester
            .widget<EditableText>(
              find.descendant(
                of: key('chat-watching-message-field'),
                matching: find.byType(EditableText),
              ),
            )
            .controller
            .text,
        isEmpty,
      );
      await tester.pumpWidget(const SizedBox());
      await drain(tester);
    });

    for (final readyWork in [true, false]) {
      testWidgets('manual reassign is absent with ready work: $readyWork', (
        tester,
      ) async {
        await size(tester, const Size(400, 900));
        final (controller, gateway) = await boot(
          configure: readyWork
              ? null
              : (g) => g.workList = const [
                  WorkItem(
                    id: 'w2',
                    title: 'Sync engine',
                    state: WorkState.working,
                  ),
                ],
        );
        await pumpAgent(tester, controller);
        await openMore(tester);
        // screen-team-1 removed manual reassignment: the dispatcher owns
        // routing. The menu must not offer a dead or hidden assign path.
        expect(key('team-agent-control-reassign'), findsNothing);
        expect(find.text('Reassign work…'), findsNothing);
        expect(key('team-agent-reassign-sheet'), findsNothing);
        expect(gateway.calls, isEmpty);
      });
    }
  });

  group('layout', () {
    for (final (direction, locale) in [
      (TextDirection.ltr, const Locale('en')),
      (TextDirection.rtl, const Locale('ar')),
    ]) {
      testWidgets(
        'agent controls and the start sheet at 320dp × 2.5x ${direction.name}',
        (tester) async {
          await size(tester, const Size(320, 640));
          final (controller, _) = await boot();
          await pumpAgent(
            tester,
            controller,
            locale: locale,
            direction: direction,
            scale: 2.5,
          );
          await openMore(tester);
          expect(key('team-agent-control-nudge'), findsOneWidget);
          expect(tester.takeException(), isNull);
          // Buttons are at least 48dp tall.
          final nudge = tester.getSize(key('team-agent-control-nudge'));
          expect(nudge.height, greaterThanOrEqualTo(48));
          await tester.tapAt(Offset.zero);
          await tester.pumpAndSettle();
          expect(key('team-agent-open-conversation'), findsOneWidget);
          expect(key('team-agent-control-message'), findsNothing);

          await tester.pumpWidget(
            app(
              TeamHomeScreen(controller: controller, now: () => clock),
              locale: locale,
              direction: direction,
              scale: 2.5,
            ),
          );
          await settle(tester);
          await tester.tap(key('team-home-start-run'));
          await tester.pumpAndSettle();
          expect(key('team-start-run-sheet'), findsOneWidget);
          await tester.scrollUntilVisible(
            key('team-start-run-send'),
            200,
            scrollable: find
                .descendant(
                  of: key('team-start-run-sheet'),
                  matching: find.byType(Scrollable),
                )
                .first,
          );
          await tester.pump();
          expect(key('team-start-run-send'), findsOneWidget);
          expect(tester.takeException(), isNull);
          if (direction == TextDirection.rtl) {
            await tapVisible(tester, key('team-start-run-technical'));
            await tester.pumpAndSettle();
            expect(
              tester
                  .widget<EditableText>(find.text('gastown.mayor'))
                  .textDirection,
              TextDirection.ltr,
            );
          }
        },
      );
    }
  });
}
