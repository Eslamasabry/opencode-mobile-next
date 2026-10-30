// slice-P3.5 (Retire RunScreen) and the owner's team conversation reports
// on build 2055:
// - a task with no progress for 45 h says "No progress for 1 d 21 h" with
//   the next step (nudge, restart, report), and a long run reads in hours
//   and days, never "2,715 min";
// - the task's words are said once (the prompt): the lead says "it", and a
//   step that is the task itself is not listed again;
// - the agent strip sits in the header, above the transcript, never over
//   it; the composer's band is solid to the window's bottom edge;
// - Task details (the conversation's menu) holds every fact of the retired
//   run page; the transcript's steps open their Work sheet; Refresh is in
//   the menu;
// - the board's + is the one "Give the team a task" sheet, with Keep in
//   backlog.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_board_screen.dart';

import '../support/team_chat_fixture.dart';
import 'chat_3_support.dart';
import '../support/composer_tools.dart';

final _en = AppLocalizationsEn();

Finder _key(String name) => find.byKey(ValueKey(name));

/// 45 h before the pinned clock (19:55 on the 25th).
final _stalledAt = teamClock.subtract(const Duration(hours: 45));

final _task = OrchestrationRun(
  id: 'ma-convoy-1',
  title: 'Add a dark mode toggle',
  state: RunState.working,
  rawState: 'open',
  kind: RunKind.batch,
  projectId: 'my-app',
  stepCount: 1,
  completedSteps: 0,
  startedAt: _stalledAt.subtract(const Duration(minutes: 5)),
  updatedAt: _stalledAt,
  raw: const {'id': 'ma-convoy-1', 'issue_type': 'convoy'},
);

/// The task's one step, which is the task itself (Gas City names the
/// convoy's only bead as the task).
final _soleStep = WorkItem(
  id: 'ma-1',
  title: 'Add a dark mode toggle',
  state: WorkState.working,
  rawState: 'in_progress',
  runId: 'ma-convoy-1',
  projectId: 'my-app',
  assignee: 'my-app/gastown.furiosa',
  createdAt: _stalledAt.subtract(const Duration(minutes: 5)),
  updatedAt: _stalledAt,
  raw: const {
    'id': 'ma-1',
    'metadata': {'gc.routed_to': 'my-app/gastown.polecat'},
  },
);

/// furiosa's session runs, and has done nothing for 45 h.
final _quietFuriosa = OrchestrationAgent(
  id: 'my-app/gastown.furiosa',
  name: 'my-app/gastown.furiosa',
  state: AgentState.working,
  rawState: 'active',
  sessionId: 'ma-wisp-7',
  sessionName: 'my-app--gastown__polecat-ma-wisp-7',
  pool: 'my-app/gastown.polecat',
  provider: 'opencode',
  harness: 'OpenCode',
  currentWorkId: 'ma-1',
  workDir: teamPolecatDir,
  sessionStartedAt: _stalledAt,
  lastActivity: _stalledAt,
  sessionState: 'active',
  sessionRunning: true,
);

/// Every test reads the pinned clock (KitSince ticks from `clock`).
void _test(String name, Future<void> Function(WidgetTester tester) body) =>
    testWidgets(
      name,
      (tester) => withClock(Clock.fixed(teamClock), () => body(tester)),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<(OrchestrationController, TeamChatGateway)> pump(
    WidgetTester tester, {
    List<OrchestrationRun>? runs,
    List<WorkItem>? work,
    List<OrchestrationAgent>? agents,
    OrchestrationCapabilities capabilities =
        OrchestrationCapabilities.gascityLoopback,
  }) async {
    phoneViewport(tester);
    final (team, gateway) = await bootTeam(
      runs: runs,
      work: work,
      agents: agents,
      capabilities: capabilities,
    );
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
    return (team, gateway);
  }

  Future<(OrchestrationController, TeamChatGateway)> pumpStalled(
    WidgetTester tester, {
    OrchestrationCapabilities capabilities =
        OrchestrationCapabilities.gascityLoopback,
  }) => pump(
    tester,
    runs: [_task],
    work: [_soleStep],
    agents: [_quietFuriosa],
    capabilities: capabilities,
  );

  /// The transcript scrolled to its end, clear of the composer.
  Future<void> toEnd(WidgetTester tester) async {
    await tester.drag(_key('team-conversation-list'), const Offset(0, -2000));
    await tester.pumpAndSettle();
  }

  String nowLine(WidgetTester tester) => tester
      .widget<RichText>(
        find
            .descendant(
              of: _key('team-conversation-now-text'),
              matching: find.byType(RichText),
            )
            .first,
      )
      .text
      .toPlainText();

  group('a task with no progress (owner report, 45 h)', () {
    _test('the Now line says so plainly, in days and hours', (tester) async {
      await pumpStalled(tester);
      expect(nowLine(tester), 'No progress for 1 d 21 h');
      expect(find.textContaining('check the agent'), findsNothing);
      // Never thousands of minutes anywhere on the page.
      expect(find.textContaining(RegExp(r'\d,\d{3} min')), findsNothing);
      expect(find.textContaining('2700 min'), findsNothing);
    });

    _test('the worker line names its role and state, not its internal id', (
      tester,
    ) async {
      await pumpStalled(tester);
      final row = _key('team-conversation-agent-my-app/gastown.furiosa');
      expect(
        find.descendant(of: row, matching: find.textContaining('Worker')),
        findsWidgets,
      );
      expect(
        find.descendant(of: row, matching: find.textContaining('Running')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: row, matching: find.textContaining('furiosa')),
        findsNothing,
      );
      // The time is said once, on the Now line, never thousands of minutes.
      expect(find.textContaining('Running for'), findsNothing);
    });

    _test('the notice offers the next step: nudge, restart, report', (
      tester,
    ) async {
      final (_, gateway) = await pumpStalled(tester);
      expect(_key('team-conversation-no-progress'), findsOneWidget);
      expect(
        find.textContaining("Worker hasn't moved this task since"),
        findsOneWidget,
      );
      expect(find.text('Nudge Worker'), findsOneWidget);
      expect(find.text('Restart Worker'), findsOneWidget);
      expect(find.text(_en.teamChatNoProgressReport), findsOneWidget);

      await toEnd(tester);
      await tester.tap(_key('team-conversation-no-progress-nudge'));
      await tester.pumpAndSettle();
      expect(gateway.controls, [
        ('my-app/gastown.furiosa', AgentControlAction.nudge),
      ]);
      expect(_key('team-conversation-no-progress-receipt'), findsOneWidget);
      await tester.pump(const Duration(seconds: 61));
    });

    _test('restart asks first; backing out sends nothing', (tester) async {
      final (_, gateway) = await pumpStalled(tester);
      await toEnd(tester);
      await tester.tap(_key('team-conversation-no-progress-restart'));
      await tester.pumpAndSettle();
      expect(_key('team-conversation-restart-confirm'), findsOneWidget);
      await tester.tapAt(const Offset(200, 40));
      await tester.pumpAndSettle();
      expect(gateway.controls, isEmpty);

      await toEnd(tester);
      await tester.tap(_key('team-conversation-no-progress-restart'));
      await tester.pumpAndSettle();
      await tester.tap(_key('team-conversation-restart-confirm-action'));
      await tester.pumpAndSettle();
      expect(gateway.controls, [
        ('my-app/gastown.furiosa', AgentControlAction.restart),
      ]);
      await tester.pump(const Duration(seconds: 61));
    });

    _test('a host without agent controls offers the report only', (
      tester,
    ) async {
      await pumpStalled(
        tester,
        capabilities: const OrchestrationCapabilities(
          projects: true,
          runs: true,
          runSteps: true,
          workGraph: true,
          agents: true,
          agentOutput: true,
          gatesInteractions: true,
          gatesBeads: true,
          eventStream: true,
          controlRespond: true,
          controlMessage: true,
          phoneHost: true,
        ),
      );
      expect(find.text('Nudge Worker'), findsNothing);
      expect(find.text('Restart Worker'), findsNothing);
      expect(find.text(_en.teamChatNoProgressReport), findsOneWidget);
    });

    _test('a task that moves says nothing of the kind', (tester) async {
      await pump(tester);
      expect(_key('team-conversation-no-progress'), findsNothing);
      // Calm: the Now line is the header's (vision 2026-10-20); no line of
      // its own under it.
      expect(_key('team-conversation-now'), findsNothing);
      expect(
        tester.widget<KitTopBar>(find.byType(KitTopBar)).subtitle,
        isNot(contains('No progress')),
      );
    });
  });

  group('the task is said once (owner rule: nothing shown twice)', () {
    _test('the lead refers back to the prompt; the step and worker '
        'rows do not repeat it', (tester) async {
      await pumpStalled(tester);
      // The prompt says it.
      expect(
        find.descendant(
          of: _key('team-conversation-prompt'),
          matching: find.textContaining('Add a dark mode toggle'),
        ),
        findsOneWidget,
      );
      // The lead's reply refers back to it.
      final lead = _key('team-conversation-lead');
      expect(
        find.descendant(
          of: lead,
          matching: find.textContaining('Add a dark mode toggle'),
        ),
        findsNothing,
      );
      expect(
        find.descendant(
          of: lead,
          matching: find.textContaining('Worker started'),
        ),
        findsOneWidget,
      );
      // The one step is the task: no steps fold, and the worker's line
      // does not carry the task's words again.
      expect(_key('team-conversation-steps'), findsNothing);
      expect(
        find.descendant(
          of: _key('team-conversation-agent-my-app/gastown.furiosa'),
          matching: find.textContaining('Add a dark mode toggle'),
        ),
        findsNothing,
      );
      // In the transcript the words show once: the prompt.
      expect(
        find.descendant(
          of: _key('team-conversation-list'),
          matching: find.textContaining('Add a dark mode toggle'),
        ),
        findsOneWidget,
      );
    });

    _test('a step that is not the task keeps its own words', (tester) async {
      await pump(tester);
      expect(_key('team-conversation-steps'), findsOneWidget);
      expect(
        find.descendant(
          of: _key('team-conversation-steps'),
          matching: find.text('Remember the choice'),
        ),
        findsOneWidget,
      );
    });
  });

  group('nothing covers the transcript', () {
    _test('no chip row sits between the header and the prompt', (tester) async {
      await pump(tester);
      expect(_key('team-conversation-family'), findsNothing);
      final bar = tester.getRect(find.byType(KitTopBar));
      final list = tester.getRect(_key('team-conversation-list'));
      expect(bar.bottom, lessThanOrEqualTo(list.top));
      final prompt = tester.getRect(_key('team-conversation-prompt'));
      expect(prompt.top, greaterThanOrEqualTo(list.top));
    });

    _test('the composer band is solid to the bottom edge', (tester) async {
      await pump(tester);
      final band = tester.getRect(_key('kit-composer-band'));
      expect(band.bottom, 915);
      expect(band.left, 0);
      expect(band.right, 412);
      final composer = tester.getRect(_key('team-conversation-composer'));
      expect(band.top, lessThanOrEqualTo(composer.top));
    });
  });

  group('RunScreen retired (P3.5)', () {
    _test('Task details is a sheet with every former run fact', (tester) async {
      await pump(tester);
      await tester.tap(_key('team-conversation-menu'));
      await tester.pumpAndSettle();
      await tester.tap(_key('team-conversation-details'));
      await tester.pumpAndSettle();

      expect(_key('team-task-details'), findsOneWidget);
      expect(_key('team-run'), findsNothing);
      // Where it stands: state word, steps done, for how long.
      expect(_key('team-task-details-state'), findsOneWidget);
      expect(
        find.descendant(
          of: _key('team-task-details-progress'),
          matching: find.text('0 of 2 steps done'),
        ),
        findsOneWidget,
      );
      expect(_key('team-task-details-elapsed'), findsOneWidget);
      // The stage line and the steps (as the graph's rows); the agents are
      // the conversation's strip and lines, not repeated here.
      expect(_key('team-task-details-stage'), findsOneWidget);
      expect(_key('team-task-details-step-ma-1'), findsOneWidget);
      expect(_key('team-task-details-step-ma-2'), findsOneWidget);
      expect(_key('team-task-details-agents'), findsNothing);
      // What the team spent today, and the one technical fold with the ids.
      expect(_key('team-task-details-usage'), findsOneWidget);
      await tester.ensureVisible(_key('team-task-details-technical'));
      await tester.tap(
        find.descendant(
          of: _key('team-task-details-technical'),
          matching: find.text(_en.teamUiTechnicalDetails),
        ),
      );
      await tester.pumpAndSettle();
      expect(_key('team-task-details-id'), findsOneWidget);

      // A step opens its Work sheet.
      await tester.ensureVisible(_key('team-task-details-step-ma-2'));
      await tester.tap(_key('team-task-details-step-ma-2'));
      await tester.pumpAndSettle();
      expect(_key('team-work-sheet'), findsOneWidget);
    });

    _test('a step in the transcript opens its Work sheet', (tester) async {
      await pump(tester);
      await tester.ensureVisible(_key('team-conversation-step-ma-2'));
      await tester.pumpAndSettle();
      await tester.tap(_key('team-conversation-step-ma-2'));
      await tester.pumpAndSettle();
      expect(_key('team-work-sheet'), findsOneWidget);
      expect(
        find.descendant(
          of: _key('team-work-sheet'),
          matching: find.text('Remember the choice'),
        ),
        findsWidgets,
      );
    });

    _test('Refresh is in the conversation menu', (tester) async {
      await pump(tester);
      await tester.tap(_key('team-conversation-menu'));
      await tester.pumpAndSettle();
      expect(_key('team-conversation-refresh'), findsOneWidget);
      await tester.tap(_key('team-conversation-refresh'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });
  });

  group("the board's + is the one Give the team a task sheet", () {
    Future<(OrchestrationController, TeamChatGateway)> pumpBoard(
      WidgetTester tester,
    ) async {
      phoneViewport(tester);
      final (team, gateway) = await bootTeam(
        capabilities: OrchestrationCapabilities.gascityLoopback,
        agents: [
          teamFuriosa(),
          // The planner is off on the phone: the direct form.
          const OrchestrationAgent(
            id: 'gastown.mayor',
            name: 'gastown.mayor',
            state: AgentState.stopped,
            suspended: true,
          ),
        ],
      );
      final connection = await teamConnection(
        api: TeamChatApi(const {}),
        repository: TeamChatRepository(const []),
      );
      await tester.pumpWidget(
        teamChatApp(
          connection,
          TeamControllerScope(
            team: team,
            child: TeamBoardScreen(
              controller: team,
              projectId: 'my-app',
              now: () => teamClock,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return (team, gateway);
    }

    _test('Keep in backlog makes the task, given to no one', (tester) async {
      final (_, gateway) = await pumpBoard(tester);
      final add = find.byWidgetPredicate(
        (w) =>
            w.key == const ValueKey('team-board-add') ||
            w.key == const ValueKey('team-board-empty-add'),
      );
      expect(add, findsWidgets);
      await tester.tap(add.first);
      await tester.pumpAndSettle();
      expect(_key('team-start-run-sheet'), findsOneWidget);
      expect(find.text(_en.teamUiStartRunTitle), findsWidgets);
      expect(_key('team-board-add-sheet'), findsNothing);
      await tester.enterText(
        _key('team-start-run-direct-title'),
        'Add a changelog',
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(_key('team-start-run-backlog'));
      // The sheet's body scrolls the button clear of the screen edge; let
      // that layout land before the tap point is read.
      await tester.pumpAndSettle();
      await tester.tap(_key('team-start-run-backlog'));
      await tester.pumpAndSettle();
      expect(gateway.created.single.$1, 'Add a changelog');
      // No conversation opened; the board stays.
      expect(_key('team-start-run-sheet'), findsNothing);
      expect(find.byType(TeamConversationScreen), findsNothing);
      expect(_key('team-board'), findsOneWidget);
      await tester.pump(const Duration(seconds: 61));
    });
  });

  // P6.6a's call sites in the chat (docs/qa/slice-P6.6a-2026-09-27): the
  // model the app picked by itself is said once per server, where it is
  // used, with the way to change it.
  group('the default model is said once where it is used (P6.6a)', () {
    CatalogModel model(String provider, String id, String name) => CatalogModel(
      id: id,
      providerID: provider,
      name: name,
      enabled: true,
      status: 'active',
      contextLimit: 200000,
      outputLimit: 8000,
      reasoning: true,
      attachments: true,
      tools: true,
      variants: const [],
    );

    testWidgets('once per server, with Choose another model', (tester) async {
      chat3MockSecureStorage();
      final conn = await chat3Controller();
      addTearDown(conn.dispose);
      conn
        ..catalog = CatalogSnapshot(
          providers: const [],
          models: [
            model('anthropic', 'sonnet', 'Claude Sonnet 4'),
            model('openai', 'gpt', 'GPT-6'),
          ],
          agents: const [],
        )
        ..selectedModel = ModelRef(providerID: 'anthropic', modelID: 'sonnet');
      await pumpChat3(tester, conn);
      await tester.pumpAndSettle();
      expect(_key('chat-default-model-notice'), findsOneWidget);
      expect(
        find.textContaining("this server's default model"),
        findsOneWidget,
      );
      expect(find.text(_en.defaultModelChange), findsOneWidget);
      await tester.tap(_key('chat-default-model-dismiss'));
      await tester.pumpAndSettle();
      expect(_key('chat-default-model-notice'), findsNothing);

      // Back in the conversation later: said already on this server.
      await tester.pumpWidget(const SizedBox());
      await pumpChat3(tester, conn);
      await tester.pumpAndSettle();
      expect(_key('chat-default-model-notice'), findsNothing);
    });
  });

  testWidgets('the default-model notice folds away by itself after a few '
      'seconds (owner, 2026-10-27)', (tester) async {
    chat3MockSecureStorage();
    final conn = await chat3Controller();
    addTearDown(conn.dispose);
    conn
      ..catalog = CatalogSnapshot(
        providers: const [],
        models: [
          CatalogModel(
            id: 'sonnet',
            providerID: 'anthropic',
            name: 'Claude Sonnet 4',
            enabled: true,
            status: 'active',
            contextLimit: 200000,
            outputLimit: 8000,
            reasoning: true,
            attachments: true,
            tools: true,
            variants: const [],
          ),
        ],
        agents: const [],
      )
      ..selectedModel = ModelRef(providerID: 'anthropic', modelID: 'sonnet');
    await pumpChat3(tester, conn);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(_key('chat-default-model-notice'), findsOneWidget);
    await tester.pump(const Duration(seconds: 7));
    await tester.pumpAndSettle();
    expect(_key('chat-default-model-notice'), findsNothing);
  });

  group('the default-model notice agrees with the composer chip (E1)', () {
    CatalogModel model(String id, String name) => CatalogModel(
      id: id,
      providerID: 'free',
      name: name,
      enabled: true,
      status: 'active',
      contextLimit: 200000,
      outputLimit: 8000,
      reasoning: true,
      attachments: true,
      tools: true,
      variants: const [],
    );

    testWidgets('choosing another model for this chat retires the notice', (
      tester,
    ) async {
      chat3MockSecureStorage();
      final conn = await chat3Controller();
      addTearDown(conn.dispose);
      conn
        ..catalog = CatalogSnapshot(
          providers: const [],
          models: [
            model('nemotron', 'Nemotron 3.5 Super VL Free'),
            model('catjam', 'Cat Jam Free'),
          ],
          agents: const [],
        )
        ..selectedModel = ModelRef(providerID: 'free', modelID: 'nemotron');
      await pumpChat3(tester, conn);
      await tester.pumpAndSettle();
      expect(find.textContaining('Nemotron 3.5 Super VL Free'), findsWidgets);
      expect(_key('chat-default-model-notice'), findsOneWidget);

      await conn.selectModelForSession(
        'session-1',
        ModelRef(providerID: 'free', modelID: 'catjam'),
      );
      await tester.pumpAndSettle();
      // The model is a row of the composer's + sheet, not a chip.
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byKey(const Key('composer-setting-model')),
          matching: find.textContaining('Cat Jam Free'),
        ),
        findsOneWidget,
      );
      await tester.tapAt(const Offset(5, 5));
      await tester.pumpAndSettle();
      expect(_key('chat-default-model-notice'), findsNothing);
      expect(
        find.textContaining('Nemotron 3.5 Super VL Free, this server'),
        findsNothing,
      );
    });
  });

  // G12 (SEC-13): Copy transcript keeps the person's own prompts verbatim
  // and masks everything else, so a key a tool printed never reaches the
  // clipboard.
  group('Copy transcript masks what is not the person\'s own (G12)', () {
    testWidgets('tool output and replies are masked; the prompt stays', (
      tester,
    ) async {
      chat3MockSecureStorage();
      final copied = <String>[];
      final messenger = tester.binding.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') {
          copied.add((call.arguments as Map)['text'] as String);
        }
        return null;
      });
      addTearDown(
        () => messenger.setMockMethodCallHandler(SystemChannels.platform, null),
      );
      const secret = 'sk-ant-api03-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';
      final api = Chat3Api()
        ..transcript = [
          chat3Prompt('m1', 'Rename the env var MY_FLAG to FEATURE_FLAG'),
          MessageWithParts(
            info: MessageInfo(
              id: 'm2',
              sessionID: 'session-1',
              role: 'assistant',
              time: MsgTime(completed: 1),
            ),
            parts: [
              Part(
                id: 'p1',
                messageID: 'm2',
                type: 'tool',
                callID: 'c1',
                toolName: 'bash',
                toolState: ToolState(
                  status: 'completed',
                  title: 'env',
                  input: const {'command': 'env'},
                  output: 'ANTHROPIC_API_KEY=$secret\nPATH=/usr/bin',
                ),
              ),
              Part(
                id: 'p2',
                messageID: 'm2',
                type: 'text',
                text: 'Your key is Authorization: Bearer $secret',
              ),
            ],
          ),
        ];
      final conn = await chat3Controller(api: api);
      addTearDown(conn.dispose);
      await pumpChat3(tester, conn);
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-commands')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        'copy',
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text(_en.chatUiCopyTranscript).last);
      await tester.pumpAndSettle();

      expect(copied, hasLength(1));
      final text = copied.single;
      expect(text, isNot(contains(secret)));
      expect(text, contains('Rename the env var MY_FLAG to FEATURE_FLAG'));
      expect(text, contains('PATH=/usr/bin'));
    });
  });
}
