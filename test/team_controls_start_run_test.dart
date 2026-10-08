// TEAM-204 / TEAM-306: Start a run. It sends the objective and the
// supervision line to the Mayor, shows "Planning… (Mayor)" on the home,
// resolves when a run carrying the objective appears, and a suspended Mayor
// opens "Team can't take tasks" with Wake the planner, without sending.
// On a host that creates work (the phone's loopback) a suspended Mayor shows
// the direct task form instead, which creates one bead and slings it at the
// project's polecat pool; a refused create stays on the sheet and says why;
// a front host keeps the host-off copy. The agent and run controls are in
// team_controls_test.dart.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/team_dispatch.dart';
import 'package:opencode_mobile/state/team_planning.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen, TeamWatchLiveScreen;
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

  group('start a run', () {
    Future<void> pumpHome(
      WidgetTester tester,
      OrchestrationController controller, {
      Locale locale = const Locale('en'),
      TextDirection? direction,
      double scale = 1,
    }) async {
      await tester.pumpWidget(
        app(
          TeamHomeScreen(controller: controller, now: () => clock),
          locale: locale,
          direction: direction,
          scale: scale,
        ),
      );
      await settle(tester);
    }

    testWidgets('an empty Runs list teaches and offers Start a run', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        configure: (g) => g.runList = const [],
      );
      await pumpHome(tester, controller);
      expect(key('team-home-runs-empty'), findsOneWidget);
      expect(find.text('No recent tasks'), findsOneWidget);
      expect(
        find.text(
          'Say what you need, and the team splits it into steps and shows '
          'its progress here.',
        ),
        findsOneWidget,
      );
      expect(find.text('Start tasks on the computer for now.'), findsNothing);
      // Design standard §2: one primary per screen. The empty state teaches;
      // Start a run is the button pinned below the list, not a second one
      // inside the empty state.
      expect(
        find.descendant(
          of: key('team-home-runs-empty'),
          matching: find.text('Give the team a task'),
        ),
        findsNothing,
      );
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      expect(key('team-start-run-sheet'), findsOneWidget);
      expect(gateway.calls, isEmpty);
    });

    testWidgets(
      'sends objective + supervision to the Mayor, shows Planning, resolves',
      (tester) async {
        await size(tester, const Size(400, 900));
        final (controller, gateway) = await boot(
          timeout: const Duration(seconds: 30),
        );
        await pumpHome(tester, controller);
        expect(key('team-home-start-run'), findsOneWidget);
        await tester.tap(key('team-home-start-run'));
        await tester.pumpAndSettle();
        expect(key('team-start-run-sheet'), findsOneWidget);
        expect(find.text('Send to the Mayor'), findsOneWidget);
        expect(find.text('gastown.mayor'), findsNothing);
        await tapVisible(tester, key('team-start-run-technical'));
        await tester.pumpAndSettle();
        expect(find.text('gastown.mayor'), findsOneWidget);
        expect(find.text('Boundaries'), findsNothing);

        // Empty objective: validation, nothing sent.
        await tapVisible(tester, key('team-start-run-send'));
        await tester.pumpAndSettle();
        expect(find.text('Write an objective first.'), findsOneWidget);
        expect(gateway.calls, isEmpty);

        await tester.enterText(
          key('team-start-run-objective'),
          'Ship offline-first sessions with conflict resolution',
        );
        await tapVisible(tester, key('team-start-run-supervision-autonomous'));
        await tester.pump();
        await tapVisible(tester, key('team-start-run-send'));
        await tester.pumpAndSettle();

        expect(gateway.calls, hasLength(1));
        final call = gateway.calls.single;
        expect(call.verb, 'message');
        expect(call.target, 'gastown.mayor');
        final text = call.arg! as String;
        expect(text, startsWith(teamPlanningMarker));
        expect(
          text,
          contains(
            'Objective: Ship offline-first sessions with conflict resolution',
          ),
        );
        expect(text, contains('Supervision: Autonomous'));
        expect(text, contains('inside the host boundaries'));
        expect(text, isNot(contains('Project:')));

        // The task's conversation opens (P0.3); back on the home, the task
        // is a row of the one list until the planner lists it
        // (slice-P5.1: no planning card), and the row opens the task's
        // conversation, whose Why leads to the planner.
        expect(key('team-start-run-sheet'), findsNothing);
        expect(find.byType(TeamConversationScreen), findsOneWidget);
        await tester.pageBack();
        await tester.pumpAndSettle();
        expect(key('team-home-planning-key-1'), findsOneWidget);
        expect(
          find.descendant(
            of: key('team-home-planning-key-1'),
            matching: find.textContaining(
              'Waiting for a plan',
              findRichText: true,
            ),
          ),
          findsWidgets,
        );
        expect(
          find.text('Ship offline-first sessions with conflict resolution'),
          findsOneWidget,
        );
        await tester.tap(key('team-home-planning-key-1'));
        await tester.pumpAndSettle();
        expect(find.byType(TeamConversationScreen), findsOneWidget);
        // Eight seconds with no plan: the line says why, and its Why
        // unfolds the ways out in place. The page reads the pinned clock,
        // so the clock moves with the pump.
        clock = clock.add(const Duration(seconds: 9));
        await tester.pump(const Duration(seconds: 9));
        await tester.pumpAndSettle();
        await tester.tap(key('team-conversation-now-why'));
        await tester.pumpAndSettle();
        await tester.tap(key('team-conversation-now-watch'));
        await tester.pumpAndSettle();
        // The planner's conversation, from the team's live output.
        expect(find.byType(TeamWatchLiveScreen), findsOneWidget);
        await tester.pageBack();
        await tester.pumpAndSettle();
        await tester.pageBack();
        await tester.pumpAndSettle();

        // A run carrying the objective appears: the card resolves.
        gateway.runList = [
          run(),
          run(
            id: 'oc-new',
            title: 'Ship offline-first sessions with conflict resolution',
            state: RunState.planning,
            startedAt: clock,
          ),
        ];
        await controller.refresh();
        await settle(tester);
        expect(key('team-home-planning-key-1'), findsNothing);
        expect(
          find.text('Ship offline-first sessions with conflict resolution'),
          findsOneWidget,
        );
        await drain(tester);
      },
    );

    testWidgets('a chosen project is named in the message', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot();
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      await tester.enterText(key('team-start-run-objective'), 'Add dark mode');
      await tapVisible(tester, key('team-start-run-project'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('ocproof').last);
      await tester.pumpAndSettle();
      await tapVisible(tester, key('team-start-run-send'));
      await tester.pumpAndSettle();
      final text = gateway.calls.single.arg! as String;
      expect(text, contains('Project: ocproof'));
      // The server's level from What runs by itself: High until chosen
      // (P6.1, personas-verticals.md §3).
      expect(text, contains('Supervision: High'));
      await drain(tester);
    });

    testWidgets('a suspended Mayor: the team can\'t take tasks, nothing sent', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        configure: (g) => g.agentList = [fox(), mayor(suspended: true)],
      );
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      expect(key('team-start-run-planner-off'), findsOneWidget);
      // slice-close-team: plain title and words, and a way on.
      expect(find.text("Team can't take tasks"), findsOneWidget);
      expect(find.text('The planner is switched off'), findsOneWidget);
      expect(key('team-start-run-wake'), findsOneWidget);
      expect(key('team-start-run-host-guide'), findsOneWidget);
      expect(key('team-start-run-send'), findsNothing);
      expect(key('team-start-run-objective'), findsNothing);
      // A front host has no create route: no direct form (TEAM-306).
      expect(controller.capabilities.controlCreateWork, isFalse);
      expect(key('team-start-run-direct'), findsNothing);
      expect(key('team-start-run-direct-send'), findsNothing);
      expect(gateway.calls, isEmpty);
    });

    MutationReceipt created(Call call) => MutationReceipt(
      id: call.requestId,
      status: MutationReceiptStatus.accepted,
      raw: {'id': 'fx-new-1', 'status': 'open', 'title': call.target},
      upstreamStatus: 201,
    );

    MutationReceipt slung(Call call) => MutationReceipt(
      id: call.requestId,
      status: MutationReceiptStatus.accepted,
      correlationId: 'corr-${call.requestId}',
      upstreamStatus: 202,
    );

    testWidgets(
      'TEAM-306: a suspended Mayor on the phone host shows the direct task '
      'form; Send creates the work and slings it at the project pool',
      (tester) async {
        await size(tester, const Size(400, 900));
        final (controller, gateway) = await boot(
          capabilities: OrchestrationCapabilities.gascityLoopback,
          configure: (g) {
            g.agentList = [fox(), mayor(suspended: true)];
            g.answer = (call) async =>
                call.verb == 'createWork' ? created(call) : slung(call);
          },
        );
        await pumpHome(tester, controller);
        await tester.tap(key('team-home-start-run'));
        await tester.pumpAndSettle();
        expect(key('team-start-run-direct'), findsOneWidget);
        expect(key('team-start-run-planner-off'), findsNothing);
        expect(key('team-start-run-objective'), findsNothing);
        expect(
          find.text(
            'The planner is off on this host. Give one task straight to '
            "the project's agent.",
          ),
          findsOneWidget,
        );
        expect(key('team-start-run-direct-project'), findsOneWidget);
        expect(find.text('ocproof'), findsOneWidget);
        expect(find.text('Send to an agent'), findsOneWidget);
        expect(key('team-start-run-host-guide'), findsOneWidget);

        // Empty title: validation, nothing sent.
        await tester.tap(key('team-start-run-direct-send'));
        await tester.pumpAndSettle();
        expect(find.text('Write a task first.'), findsOneWidget);
        expect(gateway.calls, isEmpty);

        await tester.enterText(
          key('team-start-run-direct-title'),
          'Add a docstring to add() in calc.py',
        );
        await tester.enterText(
          key('team-start-run-direct-details'),
          'One line, nothing else.',
        );
        await tester.tap(key('team-start-run-direct-send'));
        await tester.pumpAndSettle();

        expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);
        expect(gateway.calls[0].target, 'Add a docstring to add() in calc.py');
        expect(gateway.calls[0].arg, 'ocproof');
        expect(gateway.calls[1].target, 'fx-new-1');
        expect(gateway.calls[1].arg, 'ocproof/gastown.polecat');

        // The sheet closed on the task's own conversation (P0.3).
        expect(key('team-start-run-sheet'), findsNothing);
        expect(find.byType(TeamConversationScreen), findsOneWidget);
        expect(
          find.descendant(
            of: key('team-conversation-title'),
            matching: find.text('Add a docstring to add() in calc.py'),
          ),
          findsOneWidget,
        );
        final record = controller.latestMutation(
          kind: MutationKind.createWork,
        )!;
        expect(record.status, MutationStatus.confirmed);
        expect(record.receipt?.createdId, 'fx-new-1');
        expect(record.request.text, 'One line, nothing else.');
        expect(record.request.projectId, 'ocproof');
        expect(
          controller.latestMutation(kind: MutationKind.assign)?.targetId,
          'fx-new-1',
        );
        await drain(tester);
      },
    );

    testWidgets('TEAM-306: a refused direct task stays on the sheet and says '
        'why; nothing is slung', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        capabilities: OrchestrationCapabilities.gascityLoopback,
        configure: (g) {
          g.agentList = [fox(), mayor(suspended: true)];
          g.answer = (call) async =>
              MutationReceipt.rejected(call.requestId, 'rig ocproof unknown');
        },
      );
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      await tester.enterText(key('team-start-run-direct-title'), 'Add tests');
      await tester.tap(key('team-start-run-direct-send'));
      await tester.pumpAndSettle();
      expect(gateway.calls.map((c) => c.verb), ['createWork']);
      expect(key('team-start-run-sheet'), findsOneWidget);
      expect(key('team-start-run-direct-error'), findsOneWidget);
      // Plain words; the host's own words only under Technical details.
      expect(
        find.text('The task wasn’t made. Change it and send it again.'),
        findsOneWidget,
      );
      expect(find.textContaining('rig ocproof unknown'), findsNothing);
      await tapVisible(tester, key('team-start-run-direct-error-details'));
      await tester.pumpAndSettle();
      expect(find.textContaining('rig ocproof unknown'), findsOneWidget);
      expect(
        controller.latestMutation(kind: MutationKind.createWork)?.status,
        MutationStatus.rejected,
      );
      // Nothing was made, so nothing is said on the page behind.
      expect(TeamDispatchAttempts.of(controller).latest, isNull);
    });

    group('P6.3 dispatch stages', () {
      Future<void> openDirect(
        WidgetTester tester,
        OrchestrationController controller,
        String title,
      ) async {
        await pumpHome(tester, controller);
        await tester.tap(key('team-home-start-run'));
        await tester.pumpAndSettle();
        expect(key('team-start-run-direct'), findsOneWidget);
        await tester.enterText(key('team-start-run-direct-title'), title);
        await tester.pump();
      }

      String textOf(WidgetTester tester, String value) {
        final text = tester.widget<Text>(
          find
              .descendant(
                of: key(value),
                matching: find.byType(Text),
                matchRoot: true,
              )
              .first,
        );
        return text.data ?? text.textSpan?.toPlainText() ?? '';
      }

      String stage(WidgetTester tester) =>
          textOf(tester, 'team-start-run-direct-stage-text');

      String homeLine(WidgetTester tester) =>
          textOf(tester, 'team-home-dispatch-text');

      OrchestrationAgent worker({bool running = true}) => OrchestrationAgent(
        id: 'ocproof/polecat-1',
        name: 'polecat-1',
        state: AgentState.working,
        rawState: 'active',
        pool: 'gastown.polecat',
        sessionId: 'bl-new',
        sessionRunning: running,
        currentWorkId: 'fx-new-1',
      );

      testWidgets(
        'the sheet says each stage once the host confirmed it; the home '
        'status line carries it on; one request per step; timing probe',
        (tester) async {
          await size(tester, const Size(412, 915));
          final createGate = Completer<void>();
          final assignGate = Completer<void>();
          final (controller, gateway) = await boot(
            capabilities: OrchestrationCapabilities.gascityLoopback,
            configure: (g) {
              g.agentList = [fox(), mayor(suspended: true)];
              g.answer = (call) async {
                if (call.verb == 'createWork') {
                  await createGate.future;
                  return created(call);
                }
                await assignGate.future;
                return slung(call);
              };
            },
          );
          await openDirect(tester, controller, 'Add a docstring');
          await tester.tap(key('team-start-run-direct-send'));
          await tester.pump();
          expect(stage(tester), 'Creating your task…');
          expect(gateway.calls.map((c) => c.verb), ['createWork']);

          // Timing probe: create receipt -> first visible next stage.
          final watch = Stopwatch()..start();
          createGate.complete();
          await tester.pump();
          watch.stop();
          expect(stage(tester), 'Task created · sending it to the team…');
          debugPrint(
            'P6.3 timing probe: create receipt -> "Task created" visible in '
            '1 frame, ${watch.elapsedMicroseconds} µs test time',
          );
          expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);
          expect(gateway.calls[1].target, 'fx-new-1');

          assignGate.complete();
          await tester.pumpAndSettle();
          // The task's conversation opened; back on the page, the line.
          expect(find.byType(TeamConversationScreen), findsOneWidget);
          Navigator.of(
            tester.element(find.byType(TeamConversationScreen)),
          ).pop();
          await tester.pumpAndSettle();
          expect(key('team-home-dispatch-awaitingWorker'), findsOneWidget);
          expect(
            homeLine(tester),
            'Task sent to the team · waiting for a worker',
          );
          expect(
            find.text('Next: a worker starts · usually within 5 min'),
            findsOneWidget,
          );

          // Only a running session on this exact task says more.
          gateway.agentList = [fox(), mayor(suspended: true), worker()];
          await controller.refresh();
          await settle(tester);
          expect(key('team-home-dispatch-workerObserved'), findsOneWidget);
          expect(homeLine(tester), 'A worker started your task');

          // Reopening the page sends nothing again.
          await tester.pumpWidget(const SizedBox());
          await pumpHome(tester, controller);
          expect(key('team-home-dispatch-workerObserved'), findsOneWidget);
          expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);
          await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')));
          await settle(tester);
          expect(key('team-home-dispatch-workerObserved'), findsNothing);
          expect(gateway.calls.length, 2);
          await drain(tester);
        },
      );

      testWidgets(
        'the task\'s own conversation Now line follows the same attempt: '
        'nothing claimed before the host answers, then the real stage',
        (tester) async {
          await size(tester, const Size(412, 915));
          final assignGate = Completer<void>();
          final (controller, gateway) = await boot(
            capabilities: OrchestrationCapabilities.gascityLoopback,
            timeout: const Duration(seconds: 5),
            configure: (g) {
              g.agentList = [fox(), mayor(suspended: true)];
              g.answer = (call) async {
                if (call.verb == 'createWork') return created(call);
                await assignGate.future;
                return slung(call);
              };
            },
          );
          String nowText() => textOf(tester, 'team-conversation-now-text');
          await openDirect(tester, controller, 'Add a docstring');
          await tester.tap(key('team-start-run-direct-send'));
          await tester.pump();
          await tester.pump();
          assignGate.complete();
          await tester.pumpAndSettle();
          expect(find.byType(TeamConversationScreen), findsOneWidget);
          expect(nowText(), contains('Waiting for a worker'));

          // Only a running session on this exact task says more.
          gateway.agentList = [fox(), mayor(suspended: true), worker()];
          await controller.refresh();
          await settle(tester);
          expect(nowText(), contains('Starting a worker'));
          expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);
          await drain(tester);
        },
      );

      testWidgets(
        'an assignment the host never confirms reads "couldn\'t confirm", '
        'not sent',
        (tester) async {
          await size(tester, const Size(412, 915));
          final (controller, gateway) = await boot(
            capabilities: OrchestrationCapabilities.gascityLoopback,
            timeout: const Duration(seconds: 5),
            configure: (g) {
              g.agentList = [fox(), mayor(suspended: true)];
              g.answer = (call) async =>
                  call.verb == 'createWork' ? created(call) : slung(call);
            },
          );
          await openDirect(tester, controller, 'Add a docstring');
          await tester.tap(key('team-start-run-direct-send'));
          await tester.pumpAndSettle();
          Navigator.of(
            tester.element(find.byType(TeamConversationScreen)),
          ).pop();
          await tester.pumpAndSettle();
          expect(key('team-home-dispatch-awaitingWorker'), findsOneWidget);
          await tester.pump(const Duration(seconds: 6));
          await settle(tester);
          expect(key('team-home-dispatch-dispatchUnconfirmed'), findsOneWidget);
          expect(
            homeLine(tester),
            'Task created · couldn’t confirm it reached the team',
          );
          expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);
          await drain(tester);
        },
      );

      testWidgets(
        'a refused assignment keeps the task; the host\'s words only under '
        'Technical details, redacted',
        (tester) async {
          await size(tester, const Size(412, 915));
          const secret = 'sk-ant-api03-abcdefghijklmnopqrstuvwxyz0123456789';
          final (controller, gateway) = await boot(
            capabilities: OrchestrationCapabilities.gascityLoopback,
            configure: (g) {
              g.agentList = [fox(), mayor(suspended: true)];
              g.answer = (call) async => call.verb == 'createWork'
                  ? created(call)
                  : MutationReceipt.rejected(
                      call.requestId,
                      'pool suspended (key $secret)',
                    );
            },
          );
          await openDirect(tester, controller, 'Add a docstring');
          await tester.tap(key('team-start-run-direct-send'));
          await tester.pumpAndSettle();
          // No conversation opens on a task nobody took.
          expect(find.byType(TeamConversationScreen), findsNothing);
          expect(key('team-start-run-sheet'), findsNothing);
          expect(key('team-home-dispatch-assignRefused'), findsOneWidget);
          expect(
            homeLine(tester),
            'Task created, but it could not be sent to the team',
          );
          expect(
            find.text('The task stays on the board, given to no one.'),
            findsOneWidget,
          );
          expect(find.textContaining('pool suspended'), findsNothing);
          expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);

          await tester.tap(find.byKey(const ValueKey('kit-status-more')));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Technical details').last);
          await tester.pumpAndSettle();
          expect(key('team-home-dispatch-details-sheet'), findsOneWidget);
          expect(find.textContaining('fx-new-1'), findsWidgets);
          expect(find.textContaining('pool suspended'), findsOneWidget);
          expect(find.textContaining(secret), findsNothing);
          expect(gateway.calls.length, 2);
        },
      );

      testWidgets(
        'a create with no task ID opens nothing, keeps the words and sends '
        'nothing more',
        (tester) async {
          await size(tester, const Size(412, 915));
          final (controller, gateway) = await boot(
            capabilities: OrchestrationCapabilities.gascityLoopback,
            timeout: const Duration(seconds: 5),
            configure: (g) {
              g.agentList = [fox(), mayor(suspended: true)];
              g.answer = (call) async => MutationReceipt(
                id: call.requestId,
                status: MutationReceiptStatus.accepted,
                upstreamStatus: 202,
              );
            },
          );
          await openDirect(tester, controller, 'Add a docstring');
          await tester.tap(key('team-start-run-direct-send'));
          await tester.pumpAndSettle();
          expect(find.byType(TeamConversationScreen), findsNothing);
          expect(key('team-start-run-sheet'), findsNothing);
          expect(key('team-home-dispatch-createUnconfirmed'), findsOneWidget);
          expect(
            homeLine(tester),
            'Couldn’t confirm whether the task was created',
          );
          expect(gateway.calls.map((c) => c.verb), ['createWork']);
          // The words wait for a deliberate new send.
          await tester.tap(key('team-home-start-run'));
          await tester.pumpAndSettle();
          expect(find.text('Add a docstring'), findsOneWidget);
          expect(gateway.calls.length, 1);
          await drain(tester);
        },
      );
    });

    testWidgets('TEAM-306: no project listed: the host-off copy, even on '
        'the phone host', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        capabilities: OrchestrationCapabilities.gascityLoopback,
        configure: (g) => g
          ..agentList = [fox(), mayor(suspended: true)]
          ..projectList = const [],
      );
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      expect(key('team-start-run-planner-off'), findsOneWidget);
      expect(key('team-start-run-direct'), findsNothing);
      expect(gateway.calls, isEmpty);
    });

    testWidgets('no Mayor listed: the missing copy', (tester) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        configure: (g) => g.agentList = [fox()],
      );
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      expect(key('team-start-run-planner-missing'), findsOneWidget);
      expect(gateway.calls, isEmpty);
    });

    testWidgets('a Mayor without a session is woken before the message', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, gateway) = await boot(
        configure: (g) => g.agentList = [fox(), mayor(session: false)],
      );
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      await tester.enterText(key('team-start-run-objective'), 'Add dark mode');
      await tapVisible(tester, key('team-start-run-send'));
      await tester.pumpAndSettle();
      expect(gateway.calls.map((c) => c.verb), ['controlAgent', 'message']);
      expect(gateway.calls.first.arg, AgentControlAction.start);
      expect(gateway.calls.first.target, 'gastown.mayor');
      await drain(tester);
    });

    testWidgets('after 31 minutes: a reason and a way out, never "Still '
        'planning" alone (slice-P5.1)', (tester) async {
      // Tall enough for the whole start sheet and its Send.
      await size(tester, const Size(400, 1400));
      final (controller, _) = await boot(timeout: const Duration(seconds: 30));
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      await tester.enterText(key('team-start-run-objective'), 'Add dark mode');
      await tapVisible(tester, key('team-start-run-send'));
      await tester.pumpAndSettle();
      expect(find.byType(TeamConversationScreen), findsOneWidget);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(key('team-home-planning-key-1'), findsOneWidget);

      clock = clock.add(const Duration(minutes: 31));
      await pumpHome(tester, controller);
      expect(
        find.descendant(
          of: key('team-home-planning-key-1'),
          matching: find.textContaining(
            'Waiting for a plan · 31 min',
            findRichText: true,
          ),
        ),
        findsWidgets,
      );
      await tester.tap(key('team-home-planning-key-1'));
      await tester.pumpAndSettle();
      // Already 31 minutes old: the reason shows at once, with no
      // engine word and no "Still planning".
      expect(
        find.textContaining(
          'No plan has been reported yet',
          findRichText: true,
        ),
        findsWidgets,
      );
      expect(find.textContaining('Still planning'), findsNothing);
      await tester.tap(key('team-conversation-now-why'));
      await tester.pumpAndSettle();
      await tester.tap(key('team-conversation-now-dismiss'));
      await tester.pumpAndSettle();
      expect(find.byType(TeamConversationScreen), findsNothing);
      expect(key('team-home-planning-key-1'), findsNothing);
      expect(controller.isPlanningDismissed('key-1'), isTrue);
      await drain(tester);
    });

    testWidgets('a refused objective stays on the sheet and says why', (
      tester,
    ) async {
      await size(tester, const Size(400, 900));
      final (controller, _) = await boot(
        configure: (g) => g.answer = (call) async =>
            MutationReceipt.rejected(call.requestId, 'identity not allowed'),
      );
      await pumpHome(tester, controller);
      await tester.tap(key('team-home-start-run'));
      await tester.pumpAndSettle();
      await tester.enterText(key('team-start-run-objective'), 'Add dark mode');
      await tapVisible(tester, key('team-start-run-send'));
      await tester.pumpAndSettle();
      expect(key('team-start-run-sheet'), findsOneWidget);
      expect(
        find.descendant(
          of: key('team-start-run-refused'),
          matching: find.text(
            'The host refused the objective: identity not allowed',
          ),
        ),
        findsOneWidget,
      );
    });

    test('the planning message round-trips through its record', () {
      final text = composeTeamPlanningMessage(
        objective: '  Add dark mode  ',
        supervision: TeamSupervision.high,
        projectName: 'ocproof',
      );
      final record = MutationRecord(
        key: 'k',
        request: MutationRequest.message('gastown.mayor', text),
        createdAt: clock,
        status: MutationStatus.confirmed,
      );
      final parsed = TeamPlanningRequest.parse(record);
      expect(parsed?.objective, 'Add dark mode');
      expect(parsed?.supervision, TeamSupervision.high);
      expect(parsed?.projectName, 'ocproof');
      expect(
        TeamPlanningRequest.parse(
          MutationRecord(
            key: 'k2',
            request: MutationRequest.message('fox', 'please continue'),
            createdAt: clock,
            status: MutationStatus.sent,
          ),
        ),
        isNull,
      );
      // A run that started well before the request is not its run.
      expect(
        teamPlanningRunMatches(
          run(
            title: 'Add dark mode',
            startedAt: clock.subtract(const Duration(hours: 2)),
          ),
          record,
          'Add dark mode',
        ),
        isFalse,
      );
      expect(
        teamPlanningRunMatches(
          run(title: 'ADD DARK MODE to the app', startedAt: clock),
          record,
          'Add dark mode',
        ),
        isTrue,
      );
      expect(
        teamPlanningRunMatches(
          OrchestrationRun(
            id: 'r',
            title: 'unrelated',
            state: RunState.planning,
            raw: const {
              'metadata': {'request_id': 'k'},
            },
          ),
          record,
          'Add dark mode',
        ),
        isTrue,
      );
    });
  });
}
