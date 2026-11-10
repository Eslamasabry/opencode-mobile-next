// TEAM-111: the Agents fleet, the Agent detail and the live output page
// (slice-P3.6: the worker's watching conversation drawn from the team's
// live output, [TeamWatchLiveScreen]).
//
// Fleet rows carry the six status words with their glyphs, sort per 02-ux
// §5.1 and show "current work · ctx N% · age"; a run's Agents tab shows
// the same rows scoped to that run. The Agent detail renders the header
// (state, context number with its 75% / 90% tones, "Recycling soon" from
// 90%, session age), the sections of §5.2, the step log parsed from the
// real recorded polecat transcript into collapsed groups, and the "Live
// output" row. The output page is LTR mono, follows by default, stops on a
// drag up, resumes from the Follow switch and reports a session the host
// no longer serves while keeping the cached text. Layout: 320dp × 2.5x,
// LTR and RTL, English and Arabic for both pages.

import 'dart:async';
import 'dart:io';

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamAgentTranscript, TeamConversationScreen, TeamWatchLiveScreen;
import 'package:opencode_mobile/ui/widgets/team_vocabulary.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart' show ToolCard;
import 'package:shared_preferences/shared_preferences.dart';

Directory _findFixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError(
    'tool/qa/gascity_fixture not found from ${Directory.current}',
  );
}

/// The fixture with the runs, work, agents and gates a scenario needs, an
/// owned event stream and the fixture's session output.
class _Gateway
    implements OrchestrationGateway, OrchestrationAgentOutputGateway {
  _Gateway(this.inner);

  final FixtureOrchestrationGateway inner;
  final stream = StreamController<OrchestrationEvent>.broadcast();
  List<OrchestrationRun>? runsOverride;
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;
  List<OrchestrationGate>? gatesOverride;
  final outputRequests = <String>[];

  @override
  OrchestrationCapabilities get capabilities =>
      OrchestrationCapabilities.gascityRead;
  @override
  OrchestrationHostIdentity? get host => inner.host;
  @override
  bool get isClosed => inner.isClosed;
  @override
  Future<void> close() async {
    await stream.close();
    await inner.close();
  }

  @override
  Future<List<OrchestrationProject>> projects() => inner.projects();
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async =>
      runsOverride ?? await inner.runs(projectId: projectId);
  @override
  Future<OrchestrationRun?> run(String id) => inner.run(id);
  @override
  Future<List<WorkItem>> work({String? projectId}) async =>
      workOverride ?? await inner.work(projectId: projectId);
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) =>
      inner.readyWork(projectId: projectId);
  @override
  Future<WorkItem?> workItem(String id) => inner.workItem(id);
  @override
  Future<List<OrchestrationAgent>> agents() async =>
      agentsOverride ?? await inner.agents();
  @override
  Future<OrchestrationAgent?> agent(String id) => inner.agent(id);
  @override
  Future<List<OrchestrationGate>> gates() async =>
      gatesOverride ?? await inner.gates();
  @override
  Future<OrchestrationUsage?> usage() => inner.usage();
  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) =>
      inner.activity(afterSeq: afterSeq, limit: limit);
  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;
  @override
  Stream<AgentOutputEvent> agentOutput(String sessionId) {
    outputRequests.add(sessionId);
    return inner.agentOutput(sessionId);
  }

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => inner.respond(gateId, response, requestId: requestId);
  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => inner.message(agentId, text, requestId: requestId);
  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => inner.controlAgent(agentId, action, requestId: requestId);
  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => inner.cancelRun(runId, requestId: requestId);
  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => inner.assign(workId, agentId: agentId, requestId: requestId);

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => inner.createWork(
    title: title,
    description: description,
    projectId: projectId,
    requestId: requestId,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late String fixturePath;
  late OrchestrationStore store;
  late DateTime clock;

  setUp(() async {
    fixturePath = _findFixtureRoot().path;
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
    clock = DateTime.utc(2026, 9, 11, 9, 41);
  });

  Future<(OrchestrationController, _Gateway)> boot({
    void Function(_Gateway gateway)? configure,
  }) async {
    final gateway = _Gateway(
      FixtureOrchestrationGateway(fixturePath: fixturePath),
    );
    configure?.call(gateway);
    final config = OrchestrationConfig(
      provider: OrchestrationProvider.fixture,
      url: fixturePath,
      city: 'bright-lights',
      enabledAt: DateTime.utc(2026, 9, 10),
    );
    final controller = OrchestrationController(
      profile: ServerProfile(
        id: 'srv-1',
        name: 'Development PC',
        baseUrl: 'https://server.example:4096',
        orchestration: config,
      ),
      config: config,
      store: store,
      gatewayFactory: (_, _) => gateway,
      now: () => clock,
    );
    addTearDown(controller.dispose);
    await controller.start();
    return (controller, gateway);
  }

  /// The polecat whose session the fixture recorded (`bl-5qc`), working
  /// the sync engine of run `oc-xru` with a full runtime.
  OrchestrationAgent fox({int? context = 63, AgentState? state}) =>
      OrchestrationAgent(
        id: 'fox',
        name: 'fox',
        state: state ?? AgentState.working,
        rawState: 'active',
        sessionId: 'bl-5qc',
        sessionName: 'ocproof--polecat--fox',
        pool: 'gastown.polecat',
        provider: 'opencode',
        model: 'openai/gpt-x',
        harness: 'OpenCode',
        currentWorkId: 'w2',
        lastActivity: clock.subtract(const Duration(minutes: 12)),
        contextPercent: context,
        workDir: '/home/eslam/city/.gc/worktrees/ocproof/polecats/fox',
        branch: 'polecat/oc-cq6',
        sessionStartedAt: clock.subtract(const Duration(hours: 3, minutes: 14)),
        raw: const {
          'id': 'bl-5qc',
          'template': 'ocproof/gastown.polecat',
          'metadata': {'rig': 'ocproof', 'branch': 'polecat/oc-cq6'},
        },
      );

  void runShape(_Gateway gateway, {List<OrchestrationAgent>? agents}) {
    gateway
      ..runsOverride = [
        OrchestrationRun(
          id: 'oc-xru',
          title: 'Offline-first sessions',
          state: RunState.working,
          rawState: 'open',
          kind: RunKind.batch,
          stepCount: 3,
          completedSteps: 1,
          startedAt: clock.subtract(const Duration(hours: 3)),
          updatedAt: clock,
          raw: const {'id': 'oc-xru', 'issue_type': 'convoy'},
        ),
      ]
      ..workOverride = const [
        WorkItem(
          id: 'w1',
          title: 'Storage layer',
          state: WorkState.completed,
          runId: 'oc-xru',
        ),
        WorkItem(
          id: 'w2',
          title: 'Sync engine',
          state: WorkState.working,
          runId: 'oc-xru',
        ),
        WorkItem(
          id: 'w3',
          title: 'Conflict policy',
          state: WorkState.blocked,
          runId: 'oc-xru',
          isBlocked: true,
        ),
        WorkItem(id: 'w9', title: 'Elsewhere', state: WorkState.working),
      ]
      ..gatesOverride = const []
      ..agentsOverride =
          agents ??
          [
            fox(),
            const OrchestrationAgent(
              id: 'wolf',
              name: 'wolf',
              state: AgentState.waiting,
              sessionId: 's-wolf',
              currentWorkId: 'w3',
            ),
            const OrchestrationAgent(
              id: 'bear',
              name: 'bear',
              state: AgentState.working,
              sessionId: 's-bear',
              currentWorkId: 'w9',
            ),
            const OrchestrationAgent(
              id: 'owl',
              name: 'owl',
              state: AgentState.idle,
              pack: 'gastown',
            ),
          ];
  }

  Widget app(
    Widget home, {
    Locale locale = const Locale('en'),
    TextDirection? direction,
    double scale = 1,
  }) => MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
      child: direction == null
          ? child!
          : Directionality(textDirection: direction, child: child!),
    ),
    home: home,
  );

  Future<void> size(WidgetTester tester, Size size) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  Future<void> pumpAgent(
    WidgetTester tester,
    OrchestrationController controller,
    String agentId, {
    Locale locale = const Locale('en'),
    TextDirection? direction,
    double scale = 1,
    Size viewport = const Size(800, 2000),
  }) async {
    await size(tester, viewport);
    await tester.pumpWidget(
      app(
        AgentScreen(controller: controller, agentId: agentId, now: () => clock),
        locale: locale,
        direction: direction,
        scale: scale,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  }

  Future<void> pumpOutput(
    WidgetTester tester,
    OrchestrationController controller,
    String agentId, {
    Locale locale = const Locale('en'),
    TextDirection? direction,
    double scale = 1,
    Size viewport = const Size(800, 600),
  }) async {
    await size(tester, viewport);
    await tester.pumpWidget(
      app(
        TeamWatchLiveScreen(team: controller, agentId: agentId),
        locale: locale,
        direction: direction,
        scale: scale,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  }

  Finder key(String value) => find.byKey(ValueKey(value));

  // The list's own scrollable (the chat's parts inside it scroll wide
  // output sideways in scrollables of their own).
  ScrollPosition outputPosition(WidgetTester tester) => tester
      .state<ScrollableState>(
        find
            .descendant(
              of: key('chat-watching-live-list'),
              matching: find.byType(Scrollable),
            )
            .first,
      )
      .position;

  group('fleet', () {
    // P3.5: the run page's Agents tab is retired; the task's conversation
    // names the agents on it (its strip and worker lines).
    Widget conversation(OrchestrationController controller) =>
        TeamConversationScreen(
          team: controller,
          runId: 'oc-xru',
          now: () => clock,
        );

    testWidgets('the task lists only the agents on it', (tester) async {
      final (controller, _) = await boot(configure: runShape);
      await size(tester, const Size(800, 1600));
      await tester.pumpWidget(app(conversation(controller)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(key('team-conversation-family'), findsNothing);
      expect(key('team-conversation-agent-fox'), findsOneWidget);
      expect(key('team-conversation-agent-wolf'), findsOneWidget);
      // Bear works w9, which is not on this task; owl has no work.
      expect(key('team-conversation-agent-bear'), findsNothing);
      expect(key('team-conversation-agent-owl'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a task without agents shows no worker row', (tester) async {
      final (controller, _) = await boot(
        configure: (g) => runShape(g, agents: const []),
      );
      await size(tester, const Size(800, 1600));
      await tester.pumpWidget(app(conversation(controller)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(key('team-conversation'), findsOneWidget);
      expect(key('team-conversation-family'), findsNothing);
      expect(key('team-conversation-agent-fox'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  group('detail', () {
    testWidgets('header: state, context number, session age; sections', (
      tester,
    ) async {
      final (controller, gateway) = await boot(configure: runShape);
      await pumpAgent(tester, controller, 'fox');
      // Named by role and short name; the engine's session id is under
      // Technical details, and the subtitle is the task it works on.
      expect(key('team-agent-title'), findsOneWidget);
      expect(
        find.descendant(
          of: key('team-agent-title'),
          matching: find.textContaining(RegExp(r' · fox$')),
        ),
        findsOneWidget,
      );
      expect(find.text('Agent · session bl-5qc'), findsNothing);
      expect(key('team-agent-state'), findsOneWidget);
      expect(
        find.descendant(
          of: key('team-agent-header'),
          matching: find.text('Working'),
        ),
        findsOneWidget,
      );
      // The state row's line: how full its context is and the session age.
      expect(
        find.text('Context 63% used · Session 3 h 14 min'),
        findsOneWidget,
      );
      expect(key('team-agent-recycling'), findsNothing);
      // The output stream was opened for the agent's session: its newest
      // step and when it was last active are one line of the status.
      expect(gateway.outputRequests, ['bl-5qc']);
      final activity = key('team-agent-activity-line');
      expect(
        find.descendant(
          of: activity,
          matching: find.textContaining(RegExp(r'^Last step: ')),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(of: activity, matching: find.text('active 12 min ago')),
        findsOneWidget,
      );

      // A short status page: no sections, and no second renderer of the
      // agent's work (its conversation, or Live output, shows that).
      for (final section in [
        'identity',
        'runtime',
        'work',
        'activity',
        'output',
      ]) {
        expect(key('team-agent-$section'), findsNothing, reason: section);
      }
      expect(key('team-agent-step-group-header'), findsNothing);
      expect(find.text('Ran 5 commands'), findsNothing);
      expect(find.textContaining('Branch setup: metadata says'), findsNothing);
      // The one pinned action is its conversation; with no OpenCode server
      // to look in here, the reason (the team's live output) is beside it.
      expect(
        tester.widget<KitButton>(key('team-agent-open-conversation')).role,
        KitButtonRole.primary,
      );
      expect(key('team-agent-open-output'), findsNothing);
      expect(key('team-agent-menu-output'), findsNothing);
      // Messaging moved to the conversation's composer.
      expect(key('team-agent-control-message'), findsNothing);
      expect(key('team-agent-conversation-miss'), findsOneWidget);
      // This read-only host allows no controls, and the page says where
      // they are.
      expect(key('team-agent-controls-elsewhere'), findsOneWidget);
      expect(key('team-agent-control-nudge'), findsNothing);

      // What the host reports is folded under Technical details.
      expect(find.text('openai/gpt-x'), findsNothing);
      await tester.tap(key('team-agent-technical'));
      await tester.pumpAndSettle();
      expect(find.text('openai/gpt-x'), findsOneWidget);
      expect(find.text('OpenCode'), findsOneWidget);
      final workDir = find.text(
        '/home/eslam/city/.gc/worktrees/ocproof/polecats/fox',
      );
      expect(workDir, findsOneWidget);
      expect(
        tester.widget<EditableText>(workDir).textDirection,
        TextDirection.ltr,
      );
      expect(
        tester.widget<EditableText>(workDir).style.fontFamily,
        AppTheme.monoFamily,
      );
      expect(find.text('polecat/oc-cq6'), findsWidgets);
      // The task and what holds it up are the subtitle, said once; no task
      // row repeats it.
      expect(key('team-agent-work-chip'), findsNothing);
      expect(
        find.text('On “Sync engine” · nothing blocking it'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('the context number changes tone at 75% and 90%', (
      tester,
    ) async {
      expect(teamContextHighPercent, 75);
      expect(teamContextRecyclePercent, 90);
      Future<void> at(int percent) async {
        final (controller, _) = await boot(
          configure: (g) => runShape(g, agents: [fox(context: percent)]),
        );
        await pumpAgent(tester, controller, 'fox');
      }

      // The number is said in words on the state row; only the recycle
      // point adds a notice (the tone lives in that notice, not the word).
      for (final percent in [74, 75, 89]) {
        await at(percent);
        expect(
          find.textContaining('Context $percent% used'),
          findsOneWidget,
          reason: '$percent%',
        );
        expect(key('team-agent-recycling'), findsNothing);
      }

      await at(90);
      expect(find.textContaining('Context 90% used'), findsOneWidget);
      expect(key('team-agent-recycling'), findsOneWidget);
      expect(find.text('Recycling soon · context nearly full'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('no context reported: no number, no recycling line', (
      tester,
    ) async {
      final (controller, _) = await boot(
        configure: (g) => runShape(g, agents: [fox(context: null)]),
      );
      await pumpAgent(tester, controller, 'fox');
      expect(key('team-agent-context'), findsNothing);
      expect(key('team-agent-recycling'), findsNothing);
      // Not reported is left out, not listed.
      expect(find.text('Not reported'), findsNothing);
      expect(key('team-agent-context-row'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'Live output draws the recorded transcript with the chat\'s parts',
      (tester) async {
        final (controller, _) = await boot(configure: runShape);
        await pumpOutput(tester, controller, 'fox');
        // The whole polecat recording replayed: 80 markers, eight groups
        // of tool calls with the agent's prose between them.
        final tail = controller.agentOutput('fox');
        expect(tail.text, contains('[tool: bash]'));
        expect(tail.received, isTrue);
        final blocks = parseAgentTranscript(tail.text);
        final groups = blocks.whereType<AgentStepGroup>().toList();
        expect(groups.length, greaterThanOrEqualTo(5));
        expect(groups.first.steps.length, 5);
        expect(groups.first.steps.first.command, startsWith('ls /home/eslam'));
        expect(groups.first.steps.first.kind, AgentStepKind.command);
        expect(groups.first.steps.first.output, contains('On branch master'));
        expect(
          blocks.whereType<AgentProse>().first.text,
          contains('Branch setup: metadata says work_dir'),
        );
        expect(
          groups.any((g) => g.steps.any((s) => s.kind == AgentStepKind.read)),
          isTrue,
          reason: 'the read tool call is classified as a read',
        );

        // The chat's own parts, not a second renderer: the agent's words
        // are the reply's prose blocks, each run of calls one of the chat's
        // folded tool lines ("Ran 5 commands"), no frame, and no raw
        // `[tool: …]` markers or one monospace dump of the transcript.
        final transcript = key('chat-watching-live-text');
        expect(tester.widget(transcript), isA<TeamAgentTranscript>());
        Finder inside(Finder matching) =>
            find.descendant(of: transcript, matching: matching);
        expect(
          inside(find.byKey(const Key('assistant-text-block'))),
          findsWidgets,
        );
        final lines = inside(find.byKey(const Key('work-group-header')));
        expect(lines, findsWidgets);
        expect(
          inside(find.textContaining(RegExp(r'[Rr]an \d+ commands?'))),
          findsWidgets,
        );
        expect(inside(find.byKey(const Key('work-group-steps'))), findsNothing);
        expect(inside(find.textContaining('[tool:')), findsNothing);
        expect(key('team-agent-step-group-header'), findsNothing);

        // Opened, the calls are the chat's tool rows: the command in LTR
        // mono.
        await tester.ensureVisible(lines.first);
        await tester.pumpAndSettle();
        await tester.tap(lines.first);
        await tester.pumpAndSettle();
        expect(
          inside(find.byKey(const Key('work-group-steps'))),
          findsOneWidget,
        );
        expect(inside(find.byType(ToolCard)), findsWidgets);
        final command = inside(
          // The tool row shortens a long command in the middle.
          find.textContaining('ls /home/eslam/Storage/C'),
        );
        expect(command, findsWidgets);
        final text = tester.widget<Text>(command.first);
        expect(text.style?.fontFamily, AppTheme.monoFamily);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('Live output is one tap away and reports a gone session', (
      tester,
    ) async {
      final (controller, gateway) = await boot(configure: runShape);
      await pumpAgent(tester, controller, 'fox');
      gateway.inner.endOutput(
        'bl-5qc',
        reason: 'session bl-5qc has no live output',
      );
      await tester.pumpAndSettle();
      final tail = controller.agentOutput('fox');
      expect(tail.ended, isTrue);
      expect(tail.text, isNotEmpty, reason: 'the cached text stays');

      await tester.tap(key('team-agent-open-conversation'));
      await tester.pumpAndSettle();
      expect(key('chat-watching-live'), findsOneWidget);
      // Why the live output and not its conversation.
      expect(key('chat-watching-live-note'), findsOneWidget);
      expect(
        find.text('Session ended · output no longer on the host'),
        findsOneWidget,
      );
      expect(key('chat-watching-live-text'), findsOneWidget);
      // The title is the task it works on.
      expect(
        find.descendant(
          of: key('chat-watching-live-title'),
          matching: find.text('Sync engine'),
        ),
        findsOneWidget,
      );
      // Opened from its own page: Back returns there, no way round again.
      expect(key('chat-watching-details'), findsNothing);
      // Nothing more can arrive: no Follow switch, and no jump pill.
      expect(key('chat-watching-live-jump').hitTestable(), findsNothing);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(key('team-agent'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('an agent without a session has no live output', (
      tester,
    ) async {
      final (controller, gateway) = await boot(configure: runShape);
      await pumpAgent(tester, controller, 'owl');
      expect(gateway.outputRequests, isEmpty);
      expect(
        find.descendant(
          of: key('team-agent-title'),
          matching: find.textContaining(RegExp(r'^Agent')),
        ),
        findsOneWidget,
      );
      // Nothing done yet and no activity: no status line for it.
      expect(key('team-agent-activity-line'), findsNothing);
      await tester.tap(key('team-agent-open-conversation'));
      await tester.pumpAndSettle();
      expect(
        find.text('Live output is not available for this agent'),
        findsOneWidget,
      );
      // Nothing said yet: the watching page's own empty state, and no
      // task to name, so the title says what the page is.
      expect(key('chat-watching-empty'), findsOneWidget);
      expect(
        find.descendant(
          of: key('chat-watching-live-title'),
          matching: find.text('Live output'),
        ),
        findsOneWidget,
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.tap(key('team-agent-technical'));
      await tester.pumpAndSettle();
      expect(key('team-agent-no-work'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a blocked item and its gate show under the header', (
      tester,
    ) async {
      final (controller, _) = await boot(
        configure: (g) {
          runShape(g);
          g.gatesOverride = [
            OrchestrationGate(
              id: 'g1',
              kind: GateKind.choice,
              title: 'Which persistence strategy?',
              workId: 'w3',
              agentId: 's-wolf',
              choices: const ['SQLite', 'Filesystem'],
              createdAt: clock,
            ),
          ];
        },
      );
      await pumpAgent(tester, controller, 'wolf');
      expect(key('team-agent-gate'), findsOneWidget);
      expect(find.text('Which persistence strategy?'), findsOneWidget);
      // What the wait costs, named after the agent.
      expect(find.textContaining('waits until you answer'), findsOneWidget);
      // What holds its task up ends the subtitle.
      expect(find.textContaining(' · blocked'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('an agent the host no longer lists', (tester) async {
      final (controller, _) = await boot(configure: runShape);
      await pumpAgent(tester, controller, 'gone');
      expect(key('team-agent-missing'), findsOneWidget);
      expect(find.text('This agent is no longer on the host'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Technical details expand with the raw fields', (tester) async {
      final (controller, _) = await boot(configure: runShape);
      await pumpAgent(tester, controller, 'fox');
      await tester.tap(key('team-agent-technical'));
      await tester.pumpAndSettle();
      expect(find.text('metadata.rig'), findsOneWidget);
      expect(find.text('ocproof/gastown.polecat'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('output page', () {
    testWidgets('draws with the chat\'s parts and follows by default', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        configure: (g) {
          runShape(g);
          g.inner.outputReplay = 6;
        },
      );
      await pumpOutput(
        tester,
        controller,
        'fox',
        locale: const Locale('ar'),
        direction: TextDirection.rtl,
        // Short enough to scroll: the page no longer spends a row on a
        // Follow switch.
        viewport: const Size(360, 420),
      );
      final text = key('chat-watching-live-text');
      expect(text, findsOneWidget);
      // The chat's reply, in the reader's direction (its commands stay LTR
      // mono inside the chat's tool rows), never a raw mono dump.
      expect(tester.widget(text), isA<TeamAgentTranscript>());
      expect(Directionality.of(tester.element(text)), TextDirection.rtl);
      expect(find.textContaining('[tool:'), findsNothing);
      // The status line names who is watched (its role, not the generated
      // name) once words arrived.
      expect(
        find.descendant(
          of: key('chat-watching-banner'),
          matching: find.textContaining(
            lookupAppLocalizations(const Locale('ar')).teamUiAgentRoleWorker,
          ),
        ),
        findsOneWidget,
      );
      // Built from kit parts: no Follow switch (following is the default,
      // a drag up stops it and the pill resumes it).
      expect(find.byType(SwitchListTile), findsNothing);

      var position = outputPosition(tester);
      expect(position.maxScrollExtent, greaterThan(0));
      expect(position.pixels, position.maxScrollExtent);

      final before = position.maxScrollExtent;
      gateway.inner.emitOutput('bl-5qc', 4);
      await tester.pumpAndSettle();
      position = outputPosition(tester);
      expect(position.maxScrollExtent, greaterThan(before));
      expect(position.pixels, position.maxScrollExtent);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a drag up stops following; the pill resumes and jumps', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        configure: (g) {
          runShape(g);
          g.inner.outputReplay = 24;
        },
      );
      await pumpOutput(tester, controller, 'fox');
      final jump = key('chat-watching-live-jump').hitTestable();
      expect(jump, findsNothing);

      await tester.drag(key('chat-watching-live-list'), const Offset(0, 400));
      await tester.pumpAndSettle();
      var position = outputPosition(tester);
      expect(position.pixels, lessThan(position.maxScrollExtent - 24));
      expect(jump, findsOneWidget);

      final held = position.pixels;
      gateway.inner.emitOutput('bl-5qc', 4);
      await tester.pumpAndSettle();
      position = outputPosition(tester);
      expect(position.pixels, held, reason: 'new text does not move it');
      expect(position.pixels, lessThan(position.maxScrollExtent));

      await tester.tap(jump);
      await tester.pumpAndSettle();
      position = outputPosition(tester);
      expect(position.pixels, position.maxScrollExtent);
      expect(jump, findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('the tail is kept by the controller across pages', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        configure: (g) {
          runShape(g);
          g.inner.outputReplay = 2;
        },
      );
      await pumpOutput(tester, controller, 'fox');
      final first = controller.agentOutput('fox').text;
      expect(first, isNotEmpty);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
      expect(controller.agentOutput('fox').watching, isFalse);
      expect(controller.agentOutput('fox').text, first);
      // Watching again reopens the stream (one request per open).
      await pumpOutput(tester, controller, 'fox');
      expect(gateway.outputRequests.length, 2);
      expect(controller.agentOutput('fox').watching, isTrue);
      expect(tester.takeException(), isNull);
    });
  });

  group('320dp 2.5x', () {
    for (final (direction, locale) in [
      (TextDirection.ltr, const Locale('en')),
      (TextDirection.rtl, const Locale('ar')),
      (TextDirection.ltr, const Locale('ar')),
      (TextDirection.rtl, const Locale('en')),
    ]) {
      final label = '${direction.name} ${locale.languageCode}';

      Future<void> reveal(
        WidgetTester tester,
        String list,
        Finder target,
      ) async {
        // Scrolled by position, not by drag: at 2.5x a drag can land on
        // selectable notice text and select it instead of scrolling.
        final position = tester
            .state<ScrollableState>(
              find
                  .descendant(of: key(list), matching: find.byType(Scrollable))
                  .first,
            )
            .position;
        // The kit list builds lazily. A return to an earlier section must
        // scroll back before looking for its now-unmounted row.
        if (target.evaluate().isEmpty) {
          position.jumpTo(0);
          await tester.pump();
        }
        while (target.evaluate().isEmpty &&
            position.pixels < position.maxScrollExtent) {
          position.jumpTo(
            math.min(position.pixels + 120, position.maxScrollExtent),
          );
          await tester.pump();
        }
        await tester.ensureVisible(target);
        await tester.pumpAndSettle();
        expect(target, findsOneWidget);
        final rect = tester.getRect(target);
        expect(rect.left, greaterThanOrEqualTo(0));
        expect(rect.right, lessThanOrEqualTo(320));
      }

      testWidgets('$label: the agent detail fits', (tester) async {
        final (controller, _) = await boot(
          configure: (g) => runShape(g, agents: [fox(context: 92)]),
        );
        await pumpAgent(
          tester,
          controller,
          'fox',
          locale: locale,
          direction: direction,
          scale: 2.5,
          viewport: const Size(320, 740),
        );
        expect(tester.getSize(key('team-agent')).width, 320);
        expect(key('team-agent-recycling'), findsOneWidget);
        // The primary sits pinned under the list.
        expect(key('team-agent-open-conversation'), findsOneWidget);
        for (final part in [
          'header',
          'activity-line',
          'controls-elsewhere',
          'technical',
        ]) {
          await reveal(tester, 'team-agent-list', key('team-agent-$part'));
        }
        await tester.tap(key('team-agent-technical'));
        await tester.pumpAndSettle();
        await reveal(tester, 'team-agent-list', key('team-agent-header'));
        expect(tester.takeException(), isNull);
      });

      testWidgets('$label: the output page fits', (tester) async {
        final (controller, gateway) = await boot(
          configure: (g) {
            runShape(g);
            g.inner.outputReplay = 3;
          },
        );
        await pumpOutput(
          tester,
          controller,
          'fox',
          locale: locale,
          direction: direction,
          scale: 2.5,
          viewport: const Size(320, 740),
        );
        expect(tester.getSize(key('chat-watching-live')).width, 320);
        expect(key('chat-watching-banner'), findsOneWidget);
        // The composer stays in the window at this size (it scrolls within
        // its room): its field is in reach, nothing overflows.
        expect(key('chat-watching-composer'), findsOneWidget);
        final field = tester.getRect(key('chat-watching-message-field'));
        expect(field.bottom, lessThanOrEqualTo(740));
        // The transcript is in the list, which the large text leaves
        // little room; no Follow switch.
        expect(
          find.byKey(
            const ValueKey('chat-watching-live-text'),
            skipOffstage: false,
          ),
          findsOneWidget,
        );
        expect(key('chat-watching-live-follow'), findsNothing);
        gateway.inner.endOutput('bl-5qc');
        await tester.pumpAndSettle();
        expect(key('chat-watching-live-jump').hitTestable(), findsNothing);
        expect(tester.takeException(), isNull);
      });
    }
  });
}
