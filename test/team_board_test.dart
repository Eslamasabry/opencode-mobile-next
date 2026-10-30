// The AI Team's board (docs/design/team-board-2026-09-26.md): the host's
// bookkeeping never shows; tasks group into Backlog · Ready · Working ·
// Review · Done from their status, who has them and their session, with
// counts; a card opens its conversation; the person's moves ask before
// anything destructive and never happen by a gesture alone; a refused move
// goes back with the host's words; a host without writes is read-only and
// says so; empty, loading and error each have their state.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/team_board.dart';
import 'package:opencode_mobile/ui/kit/kit_board_lane.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';

import 'support/team_board_fixture.dart';

List<String> _ids(TeamBoard board, TeamBoardColumn column) => [
  for (final card in board.cards(column)) card.id,
];

Future<(OrchestrationController, BoardData)> _pump(
  WidgetTester tester,
  BoardScene scene, {
  ValueChanged<TeamBoardCard>? onOpenCard,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final (controller, gateway) = await boardController(scene);
  _controller = controller;
  await tester.pumpWidget(boardApp(controller, onOpenCard: onOpenCard));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
  return (controller, gateway);
}

OrchestrationController? _controller;

/// Unmounts the board and disposes its controller so their timers stop
/// before the test ends.
Future<void> _unmount(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump();
  _controller?.dispose();
  _controller = null;
  await tester.pump();
}

Finder _card(String id) => find.byKey(ValueKey('team-board-card-$id'));

Future<void> _showColumn(WidgetTester tester, TeamBoardColumn column) async {
  final tab = find.byKey(ValueKey('team-board-tab-${column.name}'));
  await tester.ensureVisible(tab);
  await tester.pumpAndSettle();
  await tester.tap(tab);
  await tester.pumpAndSettle();
}

void main() {
  group('grouping', () {
    late OrchestrationSnapshot snapshot;

    setUp(() async {
      final (controller, _) = await boardController(BoardScene.loaded);
      snapshot = controller.snapshot;
      controller.dispose();
    });

    test('the host\'s bookkeeping is never on the board', () {
      final board = buildTeamBoard(
        snapshot,
        projectId: boardProject,
        now: boardClock,
      );
      final shown = {
        for (final column in TeamBoardColumn.values)
          for (final card in board.cards(column)) card.item.title,
      };
      for (final title in boardBookkeepingTitles) {
        expect(shown, isNot(contains(title)), reason: title);
      }
      expect(board.total, 16);
    });

    test('columns come from status, who has it and its session', () {
      final board = buildTeamBoard(
        snapshot,
        projectId: boardProject,
        now: boardClock,
      );
      // Not given to anyone: Backlog; the blocked one stays there, flagged.
      expect(
        _ids(board, TeamBoardColumn.backlog),
        unorderedEquals(['bd-dark', 'bd-crash', 'bd-epic', 'bd-tests']),
      );
      // Given to the workers, nobody on it yet: Ready.
      expect(
        _ids(board, TeamBoardColumn.ready),
        unorderedEquals(['bd-rename', 'bd-conflict']),
      );
      // A session / an agent on it, waiting on the person, or failed.
      expect(
        _ids(board, TeamBoardColumn.working),
        unorderedEquals(['bd-sync', 'bd-schema', 'bd-http', 'bd-cold']),
      );
      expect(
        _ids(board, TeamBoardColumn.review),
        unorderedEquals(['bd-voice', 'bd-pull']),
      );
      // Finished in the last 7 days (not the ten-day-old one).
      expect(_ids(board, TeamBoardColumn.done), [
        'bd-hello',
        'bd-storage',
        'bd-model',
        'bd-flaky',
      ]);
      // What needs the person first, then the most urgent.
      expect(_ids(board, TeamBoardColumn.working).take(2), [
        'bd-schema',
        'bd-cold',
      ]);
      expect(board.needsYou(TeamBoardColumn.working), isTrue);

      final epic = board.find('bd-epic')!;
      expect((epic.epicDone, epic.epicTotal), (2, 5));
      expect(board.find('bd-sync')!.epic?.id, 'bd-epic');
      expect(board.find('bd-tests')!.blockers.single.id, 'bd-sync');
      expect(board.find('bd-http')!.priority, WorkPriority.urgent);
    });

    test('an open bead with a live session is Working even while it says '
        'open; one handed to the reviewer is Review', () {
      const open = WorkItem(
        id: 'x',
        title: 'x',
        state: WorkState.queued,
        sessionId: 's-1',
      );
      expect(teamBoardColumnOf(open), TeamBoardColumn.working);
      const routed = WorkItem(
        id: 'y',
        title: 'y',
        state: WorkState.queued,
        raw: {
          'metadata': {'gc.routed_to': 'oc_app/gastown.polecat'},
        },
      );
      expect(teamBoardColumnOf(routed), TeamBoardColumn.ready);
      expect(
        teamBoardColumnOf(routed, agentOnIt: true),
        TeamBoardColumn.working,
      );
      const handed = WorkItem(
        id: 'z',
        title: 'z',
        state: WorkState.queued,
        raw: {'assignee': 'oc_app/gastown.refinery'},
      );
      expect(teamBoardColumnOf(handed), TeamBoardColumn.review);
    });

    test('another project\'s task is only on its own board', () {
      final mine = buildTeamBoard(
        snapshot,
        projectId: boardProject,
        now: boardClock,
      );
      final theirs = buildTeamBoard(
        snapshot,
        projectId: boardOtherProject,
        now: boardClock,
      );
      expect(mine.find('site-hero'), isNull);
      expect(theirs.find('site-hero'), isNotNull);
    });
  });

  group('board screen', () {
    testWidgets('opens on Working with counts; bookkeeping never shows', (
      tester,
    ) async {
      await _pump(tester, BoardScene.loaded);
      expect(find.text('Board'), findsOneWidget);
      // oc_app has open work, so it is chosen; the switcher names it.
      expect(find.text('oc_app'), findsOneWidget);
      for (final (column, count) in [
        (TeamBoardColumn.backlog, 4),
        (TeamBoardColumn.ready, 2),
        (TeamBoardColumn.working, 4),
        (TeamBoardColumn.review, 2),
        (TeamBoardColumn.done, 4),
      ]) {
        expect(
          find.descendant(
            of: find.byKey(ValueKey('team-board-tab-${column.name}')),
            matching: find.text('$count'),
          ),
          findsOneWidget,
          reason: column.name,
        );
      }
      // The Working tab says something there needs the person.
      final lanes = tester.widget<KitBoardLanes>(find.byType(KitBoardLanes));
      expect([for (final c in lanes.columns) c.needsYou], [0, 0, 1, 0, 0]);
      expect(_card('bd-schema'), findsOneWidget);
      expect(find.text('Needs you'), findsWidgets);
      for (final title in boardBookkeepingTitles) {
        expect(find.text(title), findsNothing, reason: title);
      }
      await _unmount(tester);
    });

    testWidgets('tapping a card opens its conversation', (tester) async {
      TeamBoardCard? opened;
      await _pump(tester, BoardScene.loaded, onOpenCard: (c) => opened = c);
      await tester.tap(find.text('Sync engine for offline drafts'));
      await tester.pumpAndSettle();
      expect(opened?.id, 'bd-sync');
      expect(opened?.runId, 'oc-xru');
      await _unmount(tester);
    });

    testWidgets('the team\'s cards have no moves; a long press says why', (
      tester,
    ) async {
      final (_, gateway) = await _pump(tester, BoardScene.loaded);
      expect(
        find.byKey(const ValueKey('team-board-card-more-bd-sync')),
        findsNothing,
      );
      await tester.longPress(find.text('Sync engine for offline drafts'));
      await tester.pumpAndSettle();
      expect(find.textContaining('The team moves this task'), findsOneWidget);
      expect(find.byKey(const ValueKey('team-board-move-open')), findsOne);
      expect((gateway as BoardGateway).edits, isEmpty);
      expect(gateway.controlCalls, isEmpty);
      await _unmount(tester);
    });

    testWidgets('Start now gives a Backlog task to the workers and it moves '
        'to Ready at once', (tester) async {
      final (_, gateway) = await _pump(tester, BoardScene.loaded);
      await _showColumn(tester, TeamBoardColumn.backlog);
      await tester.tap(
        find.byKey(const ValueKey('team-board-card-more-bd-crash')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('team-board-move-startNow')));
      await tester.pumpAndSettle();
      final assign = gateway.controlCalls.single;
      expect(assign.verb, 'assign');
      expect(assign.target, 'bd-crash');
      expect(assign.arg, boardPool);
      // Shown in Ready, saying it is on its way, until the host shows it.
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('team-board-tab-ready')),
          matching: find.text('3'),
        ),
        findsOneWidget,
      );
      await _showColumn(tester, TeamBoardColumn.ready);
      expect(
        find.textContaining('Moving to Ready', findRichText: true),
        findsWidgets,
      );
      await _unmount(tester);
    });

    testWidgets('Cancel asks first; "Keep it" sends nothing', (tester) async {
      final (_, data) = await _pump(tester, BoardScene.loaded);
      final gateway = data as BoardGateway;
      await _showColumn(tester, TeamBoardColumn.ready);
      Future<void> openCancel() async {
        await tester.tap(
          find.byKey(const ValueKey('team-board-card-more-bd-rename')),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('team-board-move-cancel')));
        await tester.pumpAndSettle();
      }

      await openCancel();
      expect(find.byKey(const ValueKey('team-board-cancel-sheet')), findsOne);
      await tester.tap(find.text('Keep it'));
      await tester.pumpAndSettle();
      expect(gateway.edits, isEmpty);

      await openCancel();
      await tester.tap(find.byKey(const ValueKey('team-board-cancel-confirm')));
      await tester.pumpAndSettle();
      expect(gateway.edits.single.verb, 'cancel');
      expect(gateway.edits.single.workId, 'bd-rename');
      await _unmount(tester);
    });

    testWidgets('priority is a choice, sent as the host\'s number', (
      tester,
    ) async {
      final (_, data) = await _pump(tester, BoardScene.loaded);
      final gateway = data as BoardGateway;
      await _showColumn(tester, TeamBoardColumn.backlog);
      await tester.tap(
        find.byKey(const ValueKey('team-board-card-more-bd-dark')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('team-board-move-priority')));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('team-board-priority-urgent')),
      );
      await tester.pumpAndSettle();
      expect(gateway.edits.single.verb, 'priority');
      expect(gateway.edits.single.arg, WorkPriority.urgent);
      expect(WorkPriority.urgent.value, 0);
      await _unmount(tester);
    });

    testWidgets('a refused move goes back and says why', (tester) async {
      final (_, data) = await _pump(tester, BoardScene.loaded);
      final gateway = data as BoardGateway..refuse = 'bead bd-rename is locked';
      await _showColumn(tester, TeamBoardColumn.ready);
      await tester.tap(
        find.byKey(const ValueKey('team-board-card-more-bd-rename')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('team-board-move-backToBacklog')),
      );
      await tester.pumpAndSettle();
      expect(gateway.edits.single.verb, 'unassign');
      expect(find.byKey(const ValueKey('team-board-failure')), findsOne);
      // Plain words and a way forward; the host's text is under Details.
      expect(find.textContaining('bead bd-rename is locked'), findsNothing);
      expect(find.text('Try again'), findsOneWidget);
      await tester.tap(
        find.byKey(const ValueKey('team-board-failure-details')),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('bead bd-rename is locked'), findsWidgets);
      // Try again really asks the host again.
      await tester.tap(find.byKey(const ValueKey('team-board-failure-retry')));
      await tester.pumpAndSettle();
      expect(gateway.edits, hasLength(2));
      expect(gateway.edits.last.verb, 'unassign');
      // Still in Ready, not moving.
      expect(_card('bd-rename'), findsOneWidget);
      expect(find.textContaining('Moving to'), findsNothing);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('team-board-tab-ready')),
          matching: find.text('2'),
        ),
        findsOneWidget,
      );
      await _unmount(tester);
    });

    testWidgets('a host without writes is read-only and says so', (
      tester,
    ) async {
      await _pump(tester, BoardScene.readOnly);
      expect(find.byKey(const ValueKey('team-board-read-only')), findsOne);
      expect(find.byKey(const ValueKey('team-board-add')), findsNothing);
      await _showColumn(tester, TeamBoardColumn.backlog);
      expect(
        find.byKey(const ValueKey('team-board-card-more-bd-crash')),
        findsNothing,
      );
      await tester.longPress(find.text('Crash when a photo is over 20 MB'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-board-move-note')), findsOne);
      expect(
        find.byKey(const ValueKey('team-board-move-startNow')),
        findsNothing,
      );
      await _unmount(tester);
    });

    testWidgets('empty: the team at an empty board, and Add to backlog', (
      tester,
    ) async {
      await _pump(tester, BoardScene.empty);
      expect(find.text('No tasks yet'), findsOneWidget);
      expect(find.byKey(const ValueKey('team-board-empty-add')), findsOne);
      expect(find.byKey(const ValueKey('team-board-tabs')), findsNothing);
      await _unmount(tester);
    });

    testWidgets('loading: skeleton cards, then the honest 8 s state', (
      tester,
    ) async {
      await _pump(tester, BoardScene.connecting);
      expect(find.byKey(const ValueKey('team-board-loading')), findsOne);
      await tester.pump(const Duration(seconds: 9));
      expect(find.byKey(const ValueKey('team-board-not-answering')), findsOne);
      await _unmount(tester);
    });

    testWidgets('error: the host cannot be reached, with Retry', (
      tester,
    ) async {
      await _pump(tester, BoardScene.failed);
      expect(find.byKey(const ValueKey('team-board-error')), findsOne);
      expect(find.byKey(const ValueKey('team-board-retry')), findsOne);
      await _unmount(tester);
    });

    testWidgets('Keep in backlog (the one Give the team a task sheet) '
        'creates a task nobody is given', (tester) async {
      final (_, gateway) = await _pump(tester, BoardScene.loaded);
      await tester.tap(find.byKey(const ValueKey('team-board-add')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-start-run-sheet')), findsOne);
      // The planner's objective, or the direct task's title when the
      // planner is off: either way the task's words.
      final field = find.byKey(const ValueKey('team-start-run-objective'));
      await tester.enterText(
        field.evaluate().isNotEmpty
            ? field
            : find.byKey(const ValueKey('team-start-run-direct-title')),
        'Export a conversation as Markdown',
      );
      await tester.pump();
      // The sheet is one scrolling form: Keep in backlog sits below the
      // fold on a phone, so scroll the form up to it.
      await tester.drag(
        field.evaluate().isNotEmpty
            ? field
            : find.byKey(const ValueKey('team-start-run-direct-title')),
        const Offset(0, -400),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('team-start-run-backlog')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-start-run-sheet')), findsNothing);
      final call = gateway.controlCalls.single;
      expect(call.verb, 'createWork');
      expect(call.target, 'Export a conversation as Markdown');
      expect(call.arg, boardProject);
      await _unmount(tester);
    });
  });

  testWidgets('the AI Team page opens the board from its header only', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final (controller, _) = await boardController(BoardScene.loaded);
    _controller = controller;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: TeamHomeScreen(controller: controller, now: () => boardClock),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    // On a phone the top bar shows one action: since P3.4 (Technical
    // details moved to the page's "how it runs" row) it is the board.
    await tester.tap(find.byKey(const ValueKey('team-home-board')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('team-board')), findsOneWidget);
    tester.state<NavigatorState>(find.byType(Navigator)).pop();
    await tester.pumpAndSettle();
    // One entry point (owner rule 2026-09-27): no "View board" row repeats
    // the top bar's Board.
    expect(find.byKey(const ValueKey('team-home-board-row')), findsNothing);
    await _unmount(tester);
  });
}
