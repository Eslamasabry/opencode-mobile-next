// The AI Team as a conversation (docs/design/team-conversation-2026-09-26.md)
// on the chat page, with the owner's phone timings of 2026-09-25: the task
// given at 19:49:03, furiosa's session created at 19:51:05, its first output
// about 19:55. Your prompt is the task; the lead's reply is written from the
// team's real events with their times; furiosa is a sub-agent card whose
// state comes from its session (Gas City's /agents said stopped while
// /sessions said running); the Now line says it is starting and that this
// can take minutes on a phone; the composer's words go through the team's
// message control, never into a worker's OpenCode session.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import 'support/team_chat_fixture.dart';

Finder _key(String value) => find.byKey(ValueKey(value));

/// The page's header (vision 2026-10-20: the Now line is its second line).
KitTopBar _bar(WidgetTester tester) =>
    tester.widget<KitTopBar>(find.byType(KitTopBar));

/// Every word drawn under [finder] (the finder itself included), as the
/// person reads it.
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

/// A time as the page shows it: the phone's own clock, 24-hour.
String _hhmm(DateTime at) {
  final local = at.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(local.hour)}:${two(local.minute)}';
}

/// ma-1 routed to the worker pool at 19:51:05 (its bead says so), not yet
/// claimed: furiosa's session runs but has written nothing yet.
final _routed = WorkItem(
  id: 'ma-1',
  title: 'Add the toggle to Settings',
  state: WorkState.working,
  rawState: 'open',
  runId: 'ma-convoy-1',
  projectId: 'my-app',
  createdAt: DateTime.utc(2026, 9, 25, 19, 49, 3),
  updatedAt: DateTime.utc(2026, 9, 25, 19, 51, 5),
  raw: const {
    'id': 'ma-1',
    'metadata': {'gc.routed_to': 'my-app/gastown.polecat'},
  },
);

final _queued = WorkItem(
  id: 'ma-2',
  title: 'Remember the choice',
  state: WorkState.queued,
  rawState: 'open',
  runId: 'ma-convoy-1',
  projectId: 'my-app',
  createdAt: DateTime.utc(2026, 9, 25, 19, 49, 3),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(OrchestrationController, TeamChatGateway, TeamChatApi)> pump(
    WidgetTester tester, {
    List<WorkItem>? work,
    List<OrchestrationGate> gates = const [],
    List<OrchestrationRun>? runs,
    Future<void> Function(BuildContext, OrchestrationAgent)? onOpenAgent,
    String? runId = 'ma-convoy-1',
    TeamPendingTask? pending,
    List<OrchestrationAgent>? agents,
  }) async {
    phoneViewport(tester);
    final (team, gateway) = await bootTeam(
      runs: runs,
      work: work ?? [_routed, _queued],
      gates: gates,
      agents: agents,
    );
    final api = TeamChatApi({
      'ses_furiosa': [
        teamChatMessage('m1', 'assistant', 'Claimed ma-1.', minute: 55),
      ],
    });
    final connection = await teamConnection(
      api: api,
      repository: TeamChatRepository([
        teamSession(
          'ses_furiosa',
          teamPolecatDir,
          updated: DateTime.utc(2026, 9, 25, 19, 54),
        ),
      ]),
    );
    await tester.pumpWidget(
      teamChatApp(
        connection,
        TeamControllerScope(
          team: team,
          child: TeamConversationScreen(
            team: team,
            runId: runId,
            pending: pending,
            now: () => teamClock,
            onOpenAgent: onOpenAgent,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return (team, gateway, api);
  }

  testWidgets('the task reads as a conversation from the team\'s real data', (
    tester,
  ) async {
    await pump(tester);

    // Your prompt is the task.
    expect(
      find.descendant(
        of: _key('team-conversation-prompt'),
        matching: find.text('Add a dark mode toggle'),
      ),
      findsOneWidget,
    );
    // The lead's lines, each from a real event with its time.
    final lead = _key('team-conversation-lead');
    expect(
      find.descendant(
        of: lead,
        matching: find.textContaining(
          'Planned 2 steps · ${_hhmm(DateTime.utc(2026, 9, 25, 19, 49))}',
          findRichText: true,
        ),
      ),
      findsWidgets,
    );
    expect(
      find.descendant(
        of: lead,
        matching: find.textContaining(
          'Sent “Add the toggle to Settings” to the workers · '
          '${_hhmm(DateTime.utc(2026, 9, 25, 19, 51))}',
          findRichText: true,
        ),
      ),
      findsWidgets,
    );
    // furiosa: the session runs, whatever /agents said.
    final agentLine = _key('team-conversation-agent-my-app/gastown.furiosa');
    expect(_words(tester, agentLine), contains('Worker · Running'));
    // The one Now line (slice-P5.1), folded into the header's second line
    // (vision 2026-10-20): what happens, for how long, what comes next. A
    // calm stage draws no line of its own under the header.
    expect(_bar(tester).subtitle, 'Now: Worker is starting · 3 min');
    expect(_bar(tester).subtitleTail, 'Next: the worker begins');
    expect(_key('team-conversation-now-calm'), findsOneWidget);
    // One live status: the Now line. No chip row, no second spinner, and
    // no generated agent name in the page's copy.
    expect(find.byType(KitAgentStrip), findsNothing);
    expect(_key('team-conversation-family-lead'), findsNothing);
    expect(find.textContaining('furiosa'), findsNothing);
    // The steps, with their marks, open under the one work line (two steps).
    expect(_key('team-conversation-steps-fold'), findsOneWidget);
    expect(_key('team-conversation-step-ma-1'), findsOneWidget);
    expect(_key('team-conversation-step-ma-2'), findsOneWidget);
    // Who the message goes to, before typing.
    expect(find.text('Your message goes to Worker'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Message the team goes through the team\'s control, never into '
      'the worker\'s OpenCode session', (tester) async {
    final (_, gateway, api) = await pump(tester);
    await tester.enterText(
      _key('team-conversation-field'),
      'Put the toggle under Appearance.',
    );
    await tester.pump();
    await tester.tap(_key('team-conversation-send'));
    await tester.pumpAndSettle();

    expect(gateway.messages, [
      ('my-app/gastown.furiosa', 'Put the toggle under Appearance.'),
    ]);
    expect(api.prompts, isEmpty);
    // The person's words stay in the conversation with their receipt.
    expect(
      find.textContaining(
        'Put the toggle under Appearance.',
        findRichText: true,
      ),
      findsWidgets,
    );
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget.key is ValueKey<String> &&
            (widget.key! as ValueKey<String>).value.startsWith(
              'team-conversation-sent-',
            ),
      ),
      findsOneWidget,
    );
    // The send's confirmation window runs out (no host event here).
    await tester.pump(const Duration(seconds: 61));
  });

  testWidgets('the worker card opens its conversation in watching mode', (
    tester,
  ) async {
    await pump(tester);
    final line = _key('team-conversation-agent-my-app/gastown.furiosa');
    await tester.ensureVisible(line);
    await tester.pumpAndSettle();
    await tester.tap(line);
    await tester.pumpAndSettle();

    expect(find.byType(ChatScreen), findsOneWidget);
    // Who, and its state from its session (P3.6).
    expect(find.text('Watching Worker · Working'), findsOneWidget);
    expect(find.text('Claimed ma-1.'), findsOneWidget);
    // Messaging it is this conversation's composer.
    expect(_key('chat-watching-message-field'), findsOneWidget);
    expect(find.text('Message Worker…'), findsWidgets);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(_key('team-conversation'), findsOneWidget);
  });

  testWidgets('a question from the team is the request card, answered here', (
    tester,
  ) async {
    final (_, gateway, _) = await pump(
      tester,
      gates: [
        OrchestrationGate(
          id: 'ma-gate-1',
          kind: GateKind.choice,
          title: 'Which default?',
          prompt: 'Should the toggle follow the system setting?',
          workId: 'ma-1',
          runId: 'ma-convoy-1',
          choices: const ['Follow the system', 'Always dark'],
          createdAt: DateTime.utc(2026, 9, 25, 19, 54),
        ),
      ],
    );
    // The question is its card's: the header keeps its host words under
    // the one needs-you marker, and no Now line repeats the wait.
    expect(_key('team-conversation-now'), findsNothing);
    expect(_bar(tester).needsYou, 1);
    // The header counts it with the one needs-you marker.
    expect(
      find.textContaining('Needs you · AI Team', findRichText: true),
      findsOneWidget,
    );
    // Concept B (20 Oct 2026): the page opens on the waiting question, and
    // the composer dims to "Waiting for you" (its answer is on the card).
    final card = tester.getRect(_key('team-conversation-gate-ma-gate-1'));
    final list = tester.getRect(_key('team-conversation-list'));
    expect(card.top, greaterThanOrEqualTo(list.top));
    expect(card.top, lessThan(list.bottom));
    expect(
      find.descendant(
        of: _key('team-conversation-composer'),
        matching: find.text('Waiting for you'),
      ),
      findsOneWidget,
    );
    expect(find.text('Message the team…'), findsNothing);
    await tester.ensureVisible(find.text('Always dark'));
    await tester.pumpAndSettle();
    // One tap sends the option (slice-P4.1c: the one request card); no
    // separate Send.
    await tester.tap(find.text('Always dark'));
    for (var i = 0; i < 5; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(_key('team-conversation-gate-ma-gate-1-send'), findsNothing);
    expect(gateway.messages, isEmpty);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget.key ==
            const ValueKey('team-conversation-gate-ma-gate-1-receipt'),
      ),
      findsOneWidget,
    );
    await tester.pump(const Duration(seconds: 61));
  });

  testWidgets('many steps fold under one line', (tester) async {
    await pump(
      tester,
      work: [
        _routed,
        _queued,
        for (var i = 3; i <= 5; i++)
          WorkItem(
            id: 'ma-$i',
            title: 'Step $i',
            state: i == 5 ? WorkState.completed : WorkState.queued,
            runId: 'ma-convoy-1',
          ),
      ],
    );
    expect(find.text('5 steps · 1 done'), findsOneWidget);
    expect(_key('team-conversation-step-ma-4'), findsNothing);
    await tester.ensureVisible(_key('team-conversation-steps-fold'));
    await tester.pumpAndSettle();
    await tester.tap(_key('team-conversation-steps-fold'));
    await tester.pumpAndSettle();
    expect(_key('team-conversation-step-ma-4'), findsOneWidget);
  });

  testWidgets('a task just given binds to its run once the team lists it', (
    tester,
  ) async {
    final (team, gateway, _) = await pump(
      tester,
      runId: null,
      runs: const [],
      work: const [],
      pending: TeamPendingTask(
        title: 'Add a dark mode toggle',
        sentAt: DateTime.utc(2026, 9, 25, 19, 49, 3),
        workId: 'ma-1',
      ),
    );
    expect(
      _words(tester, _key('team-conversation-now-text')),
      'Waiting for a worker · 5 min',
    );
    expect(
      find.textContaining(
        'Sent to the team · ${_hhmm(DateTime.utc(2026, 9, 25, 19, 49))}',
        findRichText: true,
      ),
      findsWidgets,
    );

    gateway
      ..runsOverride = [teamTask]
      ..workOverride = [_routed, _queued];
    await team.refresh();
    await tester.pumpAndSettle();
    expect(
      find.textContaining(
        'Planned 2 steps · ${_hhmm(DateTime.utc(2026, 9, 25, 19, 49))}',
        findRichText: true,
      ),
      findsWidgets,
    );
    expect(
      _words(tester, _key('team-conversation-agent-my-app/gastown.furiosa')),
      contains('Worker'),
    );
  });

  // The chat's rule (the turn model in chat/message_view.dart): lines share
  // the prose's edge, with no frame or fill, and work folds under one line.
  group('drawn the chat\'s one way', () {
    testWidgets('the lead reads as a plain reply: no fill, no bullets', (
      tester,
    ) async {
      await pump(tester);
      final lead = _key('team-conversation-lead');
      final blocks = find.descendant(
        of: lead,
        matching: _key('team-conversation-lead-reply'),
      );
      expect(blocks, findsOneWidget);
      // No fill behind the prose: a reply carries no frame or tint.
      final fills = find.descendant(
        of: blocks,
        matching: find.byWidgetPredicate((widget) {
          final decoration = switch (widget) {
            DecoratedBox(:final decoration) => decoration,
            Container(:final decoration) => decoration,
            _ => null,
          };
          final color = decoration is BoxDecoration ? decoration.color : null;
          return color != null && color.a > 0;
        }),
      );
      expect(
        fills,
        findsNothing,
        reason: 'the lead is a finished reply, drawn with no fill',
      );
      // Quiet, small update lines (concept B): the secondary caption in its
      // muted tone, the time to the minute, never the seconds.
      final lines = tester.widgetList<KitText>(
        find.descendant(of: blocks, matching: find.byType(KitText)),
      );
      expect(lines, isNotEmpty);
      for (final line in lines) {
        expect(line.role, KitTextRole.secondary);
        expect(line.tone, KitTextTone.secondary);
      }
      expect(
        _words(tester, blocks),
        isNot(matches(RegExp(r'\d{1,2}:\d{2}:\d{2}'))),
      );
      // Three lines: none folded.
      expect(_key('team-conversation-lead-earlier'), findsNothing);
      expect(
        find.descendant(
          of: lead,
          matching: find.textContaining('•', findRichText: true),
        ),
        findsNothing,
      );
    });

    testWidgets('many lead lines fold the earlier ones under one line', (
      tester,
    ) async {
      await pump(
        tester,
        work: [
          _routed,
          _queued,
          WorkItem(
            id: 'ma-3',
            title: 'Drop the old flag',
            state: WorkState.cancelled,
            runId: 'ma-convoy-1',
            updatedAt: DateTime.utc(2026, 9, 25, 19, 53),
          ),
        ],
        gates: [
          OrchestrationGate(
            id: 'ma-gate-1',
            kind: GateKind.choice,
            title: 'Which default?',
            workId: 'ma-1',
            runId: 'ma-convoy-1',
            choices: const ['Follow the system', 'Always dark'],
            createdAt: DateTime.utc(2026, 9, 25, 19, 54),
          ),
        ],
      );
      final planned = find.textContaining(
        'Planned 3 steps',
        findRichText: true,
      );
      expect(_key('team-conversation-lead-earlier'), findsOneWidget);
      expect(find.text('3 earlier updates'), findsOneWidget);
      expect(planned, findsNothing);
      // The newest stay in view.
      expect(
        find.textContaining('Needs you', findRichText: true),
        findsWidgets,
      );
      await tester.tap(_key('team-conversation-lead-earlier'));
      await tester.pumpAndSettle();
      expect(planned, findsWidgets);
      await tester.pump(const Duration(seconds: 61));
    });

    testWidgets('a worker is a sub-agent line, not a boxed card', (
      tester,
    ) async {
      OrchestrationAgent? opened;
      await pump(tester, onOpenAgent: (_, agent) async => opened = agent);
      final line = _key('team-conversation-agent-my-app/gastown.furiosa');
      expect(line, findsOneWidget);
      expect(
        find.ancestor(of: line, matching: find.byType(KitPanel)),
        findsNothing,
      );
      expect(
        find.descendant(of: line, matching: find.byType(KitPanel)),
        findsNothing,
      );
      // One line: who, what it works on, its state and time.
      expect(_words(tester, line), contains('Worker · Running'));
      expect(
        find.descendant(
          of: line,
          matching: find.text('Add the toggle to Settings'),
        ),
        findsOneWidget,
      );
      // The sub-agent line: who and its state on the first line, what it
      // works on under it (KitToolRow.agent), no frame around them.
      expect(
        tester
            .getRect(
              find.descendant(
                of: line,
                matching: find.text('Add the toggle to Settings'),
              ),
            )
            .top,
        greaterThanOrEqualTo(
          tester
              .getRect(
                find.descendant(
                  of: line,
                  matching: find.textContaining('Worker'),
                ),
              )
              .bottom,
        ),
      );
      await tester.ensureVisible(line);
      await tester.pumpAndSettle();
      await tester.tap(line);
      await tester.pumpAndSettle();
      expect(opened?.id, 'my-app/gastown.furiosa');
    });

    testWidgets('the header never says Paused while the worker runs', (
      tester,
    ) async {
      // The agents list says furiosa is suspended; its session runs.
      final base = teamFuriosa();
      await pump(
        tester,
        agents: [
          OrchestrationAgent(
            id: base.id,
            name: base.name,
            state: AgentState.stopped,
            rawState: 'suspended',
            suspended: true,
            sessionId: base.sessionId,
            sessionName: base.sessionName,
            pool: base.pool,
            currentWorkId: base.currentWorkId,
            workDir: base.workDir,
            sessionStartedAt: base.sessionStartedAt,
            sessionState: 'active',
            sessionRunning: true,
          ),
        ],
      );
      // The header says what the worker does now, never "Paused".
      expect(_bar(tester).subtitle, startsWith('Now: '));
      expect(find.textContaining('Paused'), findsNothing);
    });
  });

  // Map record team-conversation (proposal fix): the states and actions it
  // listed as missing.
  group('what the map asked for', () {
    testWidgets('a task the team no longer lists says so, with its page', (
      tester,
    ) async {
      await pump(tester, runs: const [], work: const []);
      expect(_key('team-conversation-gone'), findsOneWidget);
      expect(find.text('Task no longer listed'), findsOneWidget);
      expect(_key('team-conversation-gone-team-page'), findsOneWidget);
      // No composer for a task that is gone.
      expect(_key('team-conversation-field'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a task the planner refused says so with the reason', (
      tester,
    ) async {
      final sent = DateTime.utc(2026, 9, 25, 19, 49, 3);
      await pump(
        tester,
        runId: null,
        runs: const [],
        work: const [],
        pending: TeamPendingTask(
          title: 'Add a dark mode toggle',
          sentAt: sent,
          record: MutationRecord(
            key: 'plan-1',
            request: MutationRequest.message(
              'my-app/planner',
              'Add a dark mode toggle',
            ),
            createdAt: sent,
            status: MutationStatus.rejected,
            receipt: const MutationReceipt(
              id: 'r-1',
              status: MutationReceiptStatus.rejected,
              message: 'The planner is off',
            ),
          ),
        ),
      );
      expect(_key('team-conversation-refused'), findsOneWidget);
      expect(find.text('Task not taken'), findsOneWidget);
      expect(
        find.textContaining('The planner is off', findRichText: true),
        findsWidgets,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('a task waiting long for the team says why and unfolds its '
        'ways out in place', (tester) async {
      await pump(
        tester,
        runId: null,
        runs: const [],
        work: const [],
        pending: TeamPendingTask(
          title: 'Add a dark mode toggle',
          sentAt: DateTime.utc(2026, 9, 25, 19, 40),
        ),
      );
      expect(
        _words(tester, _key('team-conversation-now-text')),
        'Waiting for a worker · 15 min',
      );
      expect(
        _words(tester, _key('team-conversation-now-reason')),
        'No worker has been reported yet.',
      );
      await tester.tap(_key('team-conversation-now-why'));
      await tester.pumpAndSettle();
      expect(_key('team-conversation-now-why-fold'), findsOneWidget);
      expect(_key('team-conversation-now-refresh'), findsOneWidget);
    });

    testWidgets('the draft is kept when the person leaves and comes back', (
      tester,
    ) async {
      final (team, _, _) = await pump(tester);
      await tester.enterText(_key('team-conversation-field'), 'Half a thought');
      await tester.pump();
      // Leave the page and open the same task again.
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        teamChatApp(
          await teamConnection(
            api: TeamChatApi(const {}),
            repository: TeamChatRepository(const []),
          ),
          TeamConversationScreen(
            team: team,
            runId: 'ma-convoy-1',
            now: () => teamClock,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<EditableText>(
              find.descendant(
                of: _key('team-conversation-field'),
                matching: find.byType(EditableText),
                matchRoot: true,
              ),
            )
            .controller
            .text,
        'Half a thought',
      );
      // Leave nothing behind for the next test.
      await tester.enterText(_key('team-conversation-field'), '');
      await tester.pump();
    });

    testWidgets('Stop task is on the task\'s own menu and asks first', (
      tester,
    ) async {
      final (_, gateway, _) = await pump(tester);
      await tester.tap(_key('team-conversation-menu'));
      await tester.pumpAndSettle();
      await tester.tap(_key('team-conversation-stop'));
      await tester.pumpAndSettle();
      expect(_key('team-conversation-stop-confirm'), findsOneWidget);
      expect(find.textContaining('Add a dark mode toggle'), findsWidgets);
      expect(gateway.messages, isEmpty);
    });
  });
}
