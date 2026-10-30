// slice-P5.1, the team's Now line: one line says what the team is doing for
// this task now, for how long, what comes next and how long that usually
// takes; after 8 s without the next stage it says why and unfolds the Why
// in place (no sheet), replacing the dispatch cycle strip and its How
// sheet. A task being planned is a row of the team page's one list (the
// planning card is gone) and its conversation carries the planning state.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/team_planning.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/team_now_line_view.dart';

import '../support/team_chat_fixture.dart';

Finder _key(String name) => find.byKey(ValueKey(name));

String _words(WidgetTester tester, Finder finder) => tester
    .widgetList<RichText>(
      find.descendant(
        of: finder,
        matching: find.byType(RichText),
        matchRoot: true,
      ),
    )
    .map((text) => text.text.toPlainText())
    .join(' ');

/// Engine words that never show above Details (acceptance).
const _engineWords = [
  'Mayor',
  'mayor',
  'bead',
  'polecat',
  'refinery',
  'sling',
  'host',
  'Routed',
  'Claimed',
];

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: true),
    child: child!,
  ),
  home: Scaffold(body: child),
);

MutationRecord _planningRecord({required DateTime sentAt}) => MutationRecord(
  key: 'plan-1',
  request: MutationRequest.message(
    teamPlannerAgentId,
    composeTeamPlanningMessage(
      objective: 'Add a dark mode toggle',
      supervision: TeamSupervision.balanced,
    ),
  ),
  createdAt: sentAt,
  status: MutationStatus.confirmed,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('the line', () {
    testWidgets('says why after 8 s with no new event, and the Why unfolds '
        'in place', (tester) async {
      var now = teamClock;
      final refreshed = <int>[];
      final input = TeamNowInput(
        activityKey: 'p:run-1',
        activity: TeamNowActivity.waitingForWorker,
        next: TeamNowNext.worker,
        reason: TeamNowReason.noWorkerReported,
      );
      await tester.pumpWidget(
        _app(
          TeamNowLineView(
            keyPrefix: 't',
            input: input,
            clock: () => now,
            wayOut: (action) => action == TeamNowAction.refresh
                ? KitAction(
                    key: const ValueKey('t-refresh'),
                    label: 'Refresh',
                    onPressed: () => refreshed.add(1),
                  )
                : null,
          ),
        ),
      );
      expect(_words(tester, _key('t-now-text')), 'Waiting for a worker');
      expect(_words(tester, _key('t-now-next')), 'Next: a worker starts');
      expect(_key('t-now-reason'), findsNothing);
      expect(_key('t-now-why'), findsNothing);

      // Eight seconds pass and nothing arrives from the team.
      now = now.add(const Duration(seconds: 8));
      await tester.pump(const Duration(seconds: 8));
      await tester.pumpAndSettle();
      expect(
        _words(tester, _key('t-now-reason')),
        'No worker has been reported yet.',
      );
      await tester.tap(_key('t-now-why'));
      await tester.pumpAndSettle();
      // In place: no sheet, no page over it.
      expect(find.byType(BottomSheet), findsNothing);
      expect(_key('t-now-why-fold'), findsOneWidget);
      await tester.tap(_key('t-refresh'));
      expect(refreshed, [1]);
      // Why folds away again.
      await tester.tap(_key('t-now-why'));
      await tester.pumpAndSettle();
      expect(_key('t-now-why-fold'), findsNothing);
    });

    testWidgets('the usual time is said while it holds, never as a deadline', (
      tester,
    ) async {
      var now = teamClock;
      final since = teamClock.subtract(const Duration(minutes: 2));
      Widget line() => _app(
        TeamNowLineView(
          keyPrefix: 't',
          input: TeamNowInput(
            activityKey: 'p:run-1',
            activity: TeamNowActivity.startingWorker,
            next: TeamNowNext.work,
            reason: TeamNowReason.workerStarting,
            since: since,
            typicalUpperBound: teamWorkerStartUsual,
          ),
          clock: () => now,
          wayOut: (_) => null,
        ),
      );
      await tester.pumpWidget(line());
      expect(_words(tester, _key('t-now-text')), 'Starting a worker · 2 min');
      expect(
        _words(tester, _key('t-now-next')),
        'Next: the worker begins the task · usually within 5 min',
      );
      now = now.add(const Duration(minutes: 4));
      await tester.pumpWidget(line());
      await tester.pumpAndSettle();
      expect(_words(tester, _key('t-now-text')), 'Starting a worker · 6 min');
      expect(
        _words(tester, _key('t-now-next')),
        'Next: the worker begins the task',
      );
    });

    testWidgets('a finished task: one word, no Why, nothing next', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          TeamNowLineView(
            keyPrefix: 't',
            input: const TeamNowInput(
              activityKey: 'p:run-1',
              activity: TeamNowActivity.completed,
              next: TeamNowNext.none,
              reason: null,
            ),
            clock: () => teamClock,
            wayOut: (_) => null,
          ),
        ),
      );
      expect(_words(tester, _key('t-now-text')), 'Finished');
      expect(_key('t-now-why'), findsNothing);
      expect(find.textContaining('Next:'), findsNothing);
    });
  });

  group('the conversation', () {
    Future<OrchestrationController> pumpPending(
      WidgetTester tester, {
      required MutationRecord record,
    }) async {
      phoneViewport(tester);
      final (team, _) = await bootTeam(runs: const [], work: const []);
      final connection = await teamConnection(
        api: TeamChatApi(const {}),
        repository: TeamChatRepository(const []),
      );
      await tester.pumpWidget(
        teamChatApp(
          connection,
          TeamControllerScope(
            team: team,
            child: TeamConversationScreen(
              team: team,
              pending: TeamPendingTask(
                title: 'Add a dark mode toggle',
                sentAt: record.createdAt,
                record: record,
              ),
              now: () => teamClock,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return team;
    }

    testWidgets('31 min without a plan: a reason and ways out, never "Still '
        'planning" alone, no engine words above Details', (tester) async {
      final team = await pumpPending(
        tester,
        record: _planningRecord(
          sentAt: teamClock.subtract(const Duration(minutes: 31)),
        ),
      );
      final block = _key('team-conversation-now-block');
      expect(
        _words(tester, _key('team-conversation-now-text')),
        'Waiting for a plan · 31 min',
      );
      expect(
        _words(tester, _key('team-conversation-now-reason')),
        'No plan has been reported yet. The reason is unknown.',
      );
      expect(find.textContaining('Still planning'), findsNothing);
      await tester.tap(_key('team-conversation-now-why'));
      await tester.pumpAndSettle();
      expect(_key('team-conversation-now-watch'), findsOneWidget);
      expect(_key('team-conversation-now-refresh'), findsOneWidget);
      expect(_key('team-conversation-now-dismiss'), findsOneWidget);
      final said = _words(tester, block);
      for (final word in _engineWords) {
        expect(said, isNot(contains(word)), reason: word);
      }
      expect(team.isPlanningDismissed('plan-1'), isFalse);
    });

    testWidgets('a request the app could not confirm says so, with the '
        'planner a tap away', (tester) async {
      await pumpPending(
        tester,
        record: MutationRecord(
          key: 'plan-2',
          request: _planningRecord(sentAt: teamClock).request,
          createdAt: teamClock.subtract(const Duration(minutes: 2)),
          status: MutationStatus.unconfirmed,
        ),
      );
      expect(
        _words(tester, _key('team-conversation-now-text')),
        'Request not confirmed',
      );
      expect(
        _words(tester, _key('team-conversation-now-reason')),
        "We can't confirm the request arrived. Check before sending it again.",
      );
    });

    testWidgets('a moving task: the stage, not the worker line again', (
      tester,
    ) async {
      phoneViewport(tester);
      final (team, _) = await bootTeam();
      final connection = await teamConnection(
        api: TeamChatApi(const {}),
        repository: TeamChatRepository(const []),
      );
      await tester.pumpWidget(
        teamChatApp(
          connection,
          TeamControllerScope(
            team: team,
            child: TeamConversationScreen(
              team: team,
              runId: 'ma-convoy-1',
              now: () => teamClock,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      // The Now line is the header's second line (vision 2026-10-20): the
      // stage, the worker named the way its own line names it, never the
      // host's generated name.
      final said = tester.widget<KitTopBar>(find.byType(KitTopBar)).subtitle!;
      expect(said, isNot(contains('furiosa')));
      expect(said, allOf(startsWith('Now: '), contains('starting')));
      // The retired strip and its How sheet are gone for good.
      expect(_key('team-cycle-strip'), findsNothing);
      expect(find.text('How the host dispatches'), findsNothing);
    });
  });

  group('the team page', () {
    testWidgets('a task being planned is a row of the one list, no card, '
        'and opens its conversation', (tester) async {
      tester.view.physicalSize = const Size(412, 1400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final (team, _) = await bootTeam(
        runs: const [],
        work: const [],
        agents: [
          teamFuriosa(),
          const OrchestrationAgent(
            id: teamPlannerAgentId,
            name: teamPlannerAgentId,
            state: AgentState.idle,
          ),
        ],
      );
      final record = await team.messageAgent(
        teamPlannerAgentId,
        composeTeamPlanningMessage(
          objective: 'Add a dark mode toggle',
          supervision: TeamSupervision.balanced,
        ),
      );
      final connection = await teamConnection(
        api: TeamChatApi(const {}),
        repository: TeamChatRepository(const []),
      );
      await tester.pumpWidget(
        teamChatApp(
          connection,
          TeamHomeScreen(controller: team, now: () => teamClock),
        ),
      );
      await tester.pumpAndSettle();
      final row = _key('team-home-planning-${record.key}');
      expect(row, findsOneWidget);
      expect(
        find.descendant(of: _key('team-home-tasks'), matching: row),
        findsOneWidget,
      );
      expect(
        _words(tester, _key('team-home-planning-${record.key}-state')),
        'Waiting for a plan',
      );
      // No card, no drawing of its own, no empty state beside it.
      expect(find.byType(KitPanel), findsNothing);
      expect(_key('team-home-runs-empty'), findsNothing);
      expect(_key('team-home-runs-empty-filtered'), findsNothing);
      await tester.tap(row);
      await tester.pumpAndSettle();
      expect(find.byType(TeamConversationScreen), findsOneWidget);
      expect(
        tester.widget<KitTopBar>(find.byType(KitTopBar)).subtitle,
        startsWith('Now: planning the steps'),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      // The sent message's receipt timer runs out.
      await tester.pump(const Duration(seconds: 61));
    });
  });
}
