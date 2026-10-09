// TEAM-203: answering decisions and closing gates from Activity, with
// receipts, plus the four notification kinds. The Gate sheet runs over a
// scripted gateway (every control on, no network): a choice routes to
// exactly the gate's request id, free text keeps the `text` action, a
// confirmation's deny and a destructive approve are two-step in red, a
// gate bead marks done, a failed run retries (re-sling) or cancels (two-
// step). Receipts: sent (action off), confirmed (row leaves, sheet closes
// after a beat), unconfirmed (+Retry, a new key, the old record marked
// retriedBy), rejected (host message + Try again). Controls are absent
// without capabilities. Notifications post once per gate with ids only,
// respect the background toggle, and a tap opens the exact sheet without
// sending. Layout at 320dp × 2.5x, LTR and RTL, English and Arabic.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_control.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamWatchLiveScreen;
import 'package:shared_preferences/shared_preferences.dart';

import 'support/team_gate_answer_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    store = OrchestrationStore(prefs);
    clock = DateTime.utc(2026, 9, 11, 12, 30);
    nextKey = 0;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  // ---------------------------------------------------------------------
  // Choice
  // ---------------------------------------------------------------------

  group('choice', () {
    testWidgets('Send routes the option to exactly the gate\'s request id', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(choiceGate()),
      );
      await pumpSheet(tester, team, 'req-1');
      expect(
        find.byKey(const ValueKey('team-gate-answer-on-host')),
        findsNothing,
      );
      // One choice acts on tap (KIT-25): no separate Send.
      expect(send, findsNothing);

      await tester.tap(find.byKey(const ValueKey('team-gate-option-1')));
      await tester.pump();

      // Exactly one send, to the gate's own id, with the chosen option.
      expect(gateway.calls, hasLength(1));
      final call = gateway.calls.single;
      expect(call.verb, 'respond');
      expect(call.target, 'req-1');
      expect((call.arg as GateResponse).choice, 'Filesystem');
      expect(call.requestId, 'key-1');
      final record = team.mutationFor('req-1')!;
      expect(record.key, 'key-1');
      expect(record.request.targetId, 'req-1');
      expect(record.request.kind, MutationKind.respond);
      expect(record.status, MutationStatus.sent);

      // The receipt shows inline while sent.
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('a confirmed answer says Answered and closes the sheet', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(choiceGate()),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);

      gateway.push(const GateChanged(gateId: 'req-1', resolved: true));
      await tester.pump();
      await tester.pump();
      expect(team.mutationFor('req-1')!.status, MutationStatus.confirmed);
      expect(receiptOf('confirmed'), findsOneWidget);
      expect(sheet, findsOneWidget);
      await tester.pump(gateSheetAnsweredBeat);
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);
      expect(gateway.calls, hasLength(1));
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Free text
  // ---------------------------------------------------------------------

  group('free text', () {
    testWidgets('the composer sends the text under action "text"', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(textGate()),
      );
      await pumpSheet(tester, team, 'req-text');
      final composer = find.byKey(const ValueKey('team-gate-composer'));
      expect(composer, findsOneWidget);
      expect(enabled(tester, send), isFalse);
      await tester.enterText(composer, '  dev, the fix is there  ');
      await tester.pump();
      expect(enabled(tester, send), isTrue);
      await tester.tap(send);
      await tester.pump();
      final call = gateway.calls.single;
      expect(call.target, 'req-text');
      final response = call.arg as GateResponse;
      expect(response.text, 'dev, the fix is there');
      expect(response.choice, isNull);
      expect(respondAction(response), 'text');
      expect(
        team.mutationFor('req-text')!.request.text,
        'dev, the fix is there',
      );
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  // ---------------------------------------------------------------------
  // Confirmation
  // ---------------------------------------------------------------------

  group('confirmation', () {
    testWidgets('Approve on a safe prompt is one tap; so is Deny', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(confirmGate()),
      );
      await pumpSheet(tester, team, 'req-safe');
      final approve = find.byKey(const ValueKey('team-gate-approve'));
      final deny = find.byKey(const ValueKey('team-gate-deny'));
      final error = AppTheme.dark().colorScheme.error;
      // The one red fill is dangerFill (visual language §5).
      final fill = AppTheme.rolesOf(AppTheme.dark()).dangerFill;
      // The safe approve is the plain primary; deny takes the error tone.
      // Design standard §2: approve is the kit's primary, deny its
      // secondary (tonal, error-coloured), no longer an outlined button.
      final approveStyle = tester
          .widget<FilledButton>(
            find.descendant(of: approve, matching: find.byType(FilledButton)),
          )
          .style!;
      expect(approveStyle.backgroundColor?.resolve({}), isNot(fill));
      final denyStyle = tester
          .widget<FilledButton>(
            find.descendant(of: deny, matching: find.byType(FilledButton)),
          )
          .style!;
      // Denying is not destructive: the neutral tone, no red.
      expect(denyStyle.foregroundColor?.resolve({}), isNot(error));

      // Deny sends at once, as on the card (slice-P4.1c): saying no loses
      // nothing, so no second step.
      await tester.tap(deny);
      await tester.pumpAndSettle();
      expect(confirmSheet, findsNothing);
      expect(gateway.calls, hasLength(1));
      expect(gateway.calls.single.target, 'req-safe');
      expect((gateway.calls.single.arg as GateResponse).confirmed, isFalse);
      expect(respondAction(gateway.calls.single.arg as GateResponse), 'deny');
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('a destructive approve is two-step in the error tone', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(confirmGate(destructive: true)),
      );
      await pumpSheet(tester, team, 'req-destroy');
      final approve = find.byKey(const ValueKey('team-gate-approve'));
      // The one red fill is dangerFill (visual language §5).
      final fill = AppTheme.rolesOf(AppTheme.dark()).dangerFill;
      expect(
        tester
            .widget<FilledButton>(
              find.descendant(of: approve, matching: find.byType(FilledButton)),
            )
            .style
            ?.backgroundColor
            ?.resolve({}),
        fill,
      );
      expect(
        find.byKey(const ValueKey('team-gate-destructive')),
        findsOneWidget,
      );

      await tester.tap(approve);
      await tester.pumpAndSettle();
      expect(confirmSheet, findsOneWidget);
      expect(find.text('Approve this destructive action?'), findsOneWidget);
      expect(gateway.calls, isEmpty);
      await tester.tap(confirmYes);
      await tester.pumpAndSettle();
      expect(gateway.calls, hasLength(1));
      expect((gateway.calls.single.arg as GateResponse).confirmed, isTrue);
      expect(respondAction(gateway.calls.single.arg as GateResponse), 'allow');
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  // ---------------------------------------------------------------------
  // Gate bead and run failed
  // ---------------------------------------------------------------------

  group('gate bead', () {
    testWidgets('Mark done answers the bead gate', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(beadGate()),
      );
      await pumpSheet(tester, team, 'bead:w-gate');
      expect(
        find.text('Close this on the host. The phone can only watch for now.'),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('team-gate-mark-done')));
      await tester.pump();
      expect(gateway.calls.single.verb, 'respond');
      expect(gateway.calls.single.target, 'bead:w-gate');
      expect((gateway.calls.single.arg as GateResponse).confirmed, isTrue);
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  group('run failed', () {
    testWidgets('a failure a retry cannot fix offers no retry; the title '
        'names the task once', (tester) async {
      final (team, _) = await boot(configure: runShape);
      await pumpSheet(tester, team, 'run:oc-loy');
      // "tests failed": something needs changing first, so no Send again.
      expect(find.byKey(const ValueKey('team-gate-run-retry')), findsNothing);
      expect(find.byKey(const ValueKey('team-gate-recoverable')), findsNothing);
      expect(find.text('What went wrong'), findsOneWidget);
      // The sheet is titled after the task; no second heading says it.
      expect(find.text('Add subtract() to calc.py stopped'), findsOneWidget);
      expect(find.byKey(const ValueKey('team-gate-title')), findsNothing);
      // Every action names what it acts on.
      expect(find.text("Open Wolf's page"), findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('Retry re-slings the stuck work to its agent', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => runShape(g, prompt: 'connection reset'),
      );
      await pumpSheet(tester, team, 'run:oc-loy');
      // The button names what it sends and to whom; no note repeats it.
      expect(
        find.text('Send Write tests for calc.py to Wolf again'),
        findsOneWidget,
      );
      expect(
        find.text('Sends Write tests for calc.py to Wolf again.'),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('team-gate-run-retry')));
      await tester.pump();
      final call = gateway.calls.single;
      expect(call.verb, 'assign');
      expect(call.target, 'w-tests');
      expect(call.arg, 'a-wolf');
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('Cancel work is two-step in red and cancels the run', (
      tester,
    ) async {
      final (team, gateway) = await boot(configure: runShape);
      await pumpSheet(tester, team, 'run:oc-loy');
      // Design standard §2: at most two tertiary actions show (the agent's
      // page, Watch the agent); Cancel work is a rare destructive path and sits
      // under More, still two-step. No Close repeats the sheet's X.
      expect(find.byKey(const ValueKey('team-gate-close')), findsNothing);
      final cancel = find.byKey(const ValueKey('team-gate-run-cancel'));
      expect(cancel, findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      await tester.tap(cancel);
      await tester.pumpAndSettle();
      expect(confirmSheet, findsOneWidget);
      expect(find.text('Stop this work?'), findsOneWidget);
      // The confirm names the task it stops.
      expect(
        find.textContaining('“Add subtract() to calc.py” stops'),
        findsOneWidget,
      );
      expect(gateway.calls, isEmpty);
      await tester.tap(confirmYes);
      await tester.pumpAndSettle();
      expect(gateway.calls.single.verb, 'cancelRun');
      expect(gateway.calls.single.target, 'oc-loy');
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('Watch the agent and Restart or reassign open the agent\'s '
        'conversation and page', (tester) async {
      final (team, gateway) = await boot(configure: runShape);
      await pumpSheet(tester, team, 'run:oc-loy');
      // Watch the agent is the second tertiary (design standard §2: two
      // shown); here its conversation is the live output.
      await tester.tap(find.byKey(const ValueKey('team-gate-run-logs')));
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);
      final logs = tester.widget<TeamWatchLiveScreen>(
        find.byType(TeamWatchLiveScreen),
      );
      expect(logs.agentId, 'a-wolf');
      await tester.pageBack();
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('open')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('team-gate-run-agent')));
      await tester.pumpAndSettle();
      expect(
        tester.widget<AgentScreen>(find.byType(AgentScreen)).agentId,
        'a-wolf',
      );
      // Navigation only: nothing was sent.
      expect(gateway.calls, isEmpty);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Receipts
  // ---------------------------------------------------------------------

  group('receipts', () {
    testWidgets('unconfirmed: the copy, Retry under a new key, old marked', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g
          ..gateList.add(choiceGate())
          ..answer = (call) async => MutationReceipt(
            id: call.requestId,
            status: MutationReceiptStatus.pending,
            message: 'connection reset',
          ),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(receiptOf('unconfirmed'), findsOneWidget);
      expect(retry, findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(gateway.calls, hasLength(1));

      // Nothing re-sends on its own.
      await tester.pump(const Duration(minutes: 2));
      expect(gateway.calls, hasLength(1));

      await tester.tap(retry);
      await tester.pump();
      await tester.pump();
      expect(gateway.calls, hasLength(2));
      expect(gateway.calls[0].requestId, 'key-1');
      expect(gateway.calls[1].requestId, 'key-2');
      expect(gateway.calls[1].target, 'req-1');
      expect((gateway.calls[1].arg as GateResponse).choice, 'SQLite');
      final old = team.mutation('key-1')!;
      expect(old.retriedBy, 'key-2');
      expect(old.canRetry, isFalse);
      final next = team.mutation('key-2')!;
      expect(next.retryOf, 'key-1');
      expect(next.status, MutationStatus.unconfirmed);
      expect(team.mutationFor('req-1')!.key, 'key-2');
      // The sheet follows the retry.
      expect(retry, findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('rejected: the host\'s message and Try again', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => g
          ..gateList.add(choiceGate())
          ..answer = (call) async => MutationReceipt.rejected(
            call.requestId,
            'no pending interaction',
          ),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(receiptOf('rejected'), findsOneWidget);
      expect(find.textContaining('no pending interaction'), findsWidgets);
      // A refused answer was not stored: no Try again; the options stay
      // answerable, and a tap sends the new answer.
      expect(retry, findsNothing);
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(gateway.calls, hasLength(2));
      expect(tester.takeException(), isNull);
    });

    testWidgets('a sent answer with no result becomes unconfirmed', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(choiceGate()),
        mutationTimeout: const Duration(seconds: 5),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);
      await tester.pump(const Duration(seconds: 6));
      expect(team.mutationFor('req-1')!.status, MutationStatus.unconfirmed);
      expect(receiptOf('unconfirmed'), findsOneWidget);
      expect(gateway.calls, hasLength(1));
      expect(tester.takeException(), isNull);
    });
  });
}
