import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/fake_local_terminal.dart';
import 'support/chats_fakes.dart';

final _now = DateTime(2026, 10, 3, 12);

FakeChatsHost _host({
  FakePhoneAgentsSource? agents,
  List<ChatFeedItem> items = const [],
}) => FakeChatsHost(
  FakeChatFeedSource(
    items: items,
    projects: [project('alpha')],
    lastUsed: '/root/projects/alpha',
  ),
)..phoneAgents = agents;

Future<void> _newChat(
  WidgetTester tester,
  FakeChatsHost host, {
  LocalTerminalSessions? terminal,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    chatsApp(host, const NewChatScreen(), terminal: terminal),
  );
  await tester.pumpAndSettle();
}

Future<void> _openSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('chats-new-agent')));
  await tester.pumpAndSettle();
}

Finder _name(String text) => find.text(KitBidi.auto(text));

void main() {
  setUpAll(loadCaptureFonts);

  group('the agent chip', () {
    testWidgets('is hidden without phone agents', (tester) async {
      await _newChat(tester, _host());
      expect(find.byKey(const ValueKey('chats-new-agent')), findsNothing);
    });

    testWidgets('is hidden while phoneAgentsAvailable is false', (
      tester,
    ) async {
      await _newChat(
        tester,
        _host(agents: FakePhoneAgentsSource(available: false)),
      );
      expect(find.byKey(const ValueKey('chats-new-agent')), findsNothing);
    });

    testWidgets('shows OpenCode beside the project when available', (
      tester,
    ) async {
      await _newChat(tester, _host(agents: FakePhoneAgentsSource()));
      expect(_name('OpenCode'), findsOneWidget);
      expect(_name('alpha'), findsOneWidget);
    });

    testWidgets('send starts the conversation with the chosen agent', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
        selected: 'claude',
      );
      final host = _host(agents: agents);
      await _newChat(tester, host);
      expect(_name('Claude Code'), findsOneWidget);
      await tester.enterText(
        find.byKey(const ValueKey('chats-new-field')),
        'Review this',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await tester.pumpAndSettle();
      expect(host.startedAgents, ['claude']);
      expect(host.fake.started.single.prompt, 'Review this');
    });
  });

  group('the agent model chip', () {
    testWidgets('lists the chosen agent\'s own models, not OpenCode\'s', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
        selected: 'claude',
      );
      await _newChat(tester, _host(agents: agents));
      expect(find.text('Server default'), findsNothing);
      expect(find.text('Default model'), findsOneWidget);
      expect(find.textContaining('Ask Claude Code'), findsWidgets);
      await tester.tap(find.byKey(const ValueKey('chats-new-agent-model')));
      await tester.pumpAndSettle();
      expect(find.text('Choose a model'), findsOneWidget);
      expect(agents.calls, contains('models:claude'));
      await tester.tap(find.byKey(const ValueKey('agents-model-sonnet')));
      await tester.pumpAndSettle();
      expect(agents.chosenModel['claude'], 'sonnet');
      expect(find.text('Choose a model'), findsNothing);
      expect(find.text('Sonnet'), findsOneWidget);
    });

    testWidgets('OpenCode keeps the server model chip', (tester) async {
      await _newChat(tester, _host(agents: FakePhoneAgentsSource()));
      expect(find.text('Server default'), findsOneWidget);
      expect(find.byKey(const ValueKey('chats-new-agent-model')), findsNothing);
    });
  });

  group('the agent sheet', () {
    testWidgets('lists agents with one line of state each', (tester) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
      );
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      expect(find.text('Choose an agent'), findsOneWidget);
      expect(find.text('Ready'), findsOneWidget);
      // A second, quiet line says what reopening does.
      expect(
        find.text("Ready\nCan't reopen old conversations"),
        findsOneWidget,
      );
      expect(find.byType(BottomSheet), findsOneWidget);
    });

    testWidgets('choosing a ready agent selects it and closes', (tester) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
      );
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('select:claude'));
      expect(find.text('Choose an agent'), findsNothing);
      expect(_name('Claude Code'), findsOneWidget);
    });

    testWidgets('an agent that is not installed shows its state', (
      tester,
    ) async {
      await _newChat(tester, _host(agents: FakePhoneAgentsSource()));
      await _openSheet(tester);
      expect(find.textContaining('Not installed'), findsOneWidget);
    });

    testWidgets('install, cancel, then sign in happen in the same sheet', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource();
      final host = _host(agents: agents);
      final terminal = FakeLocalTerminalBackend();
      await _newChat(
        tester,
        host,
        terminal: LocalTerminalSessions(backend: terminal),
      );
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      // The setup step replaced the list; there is still one sheet.
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(find.textContaining('Set up'), findsOneWidget);
      expect(find.text('Choose an agent'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('install:claude'));
      expect(find.byKey(const ValueKey('agents-progress')), findsOneWidget);
      expect(
        find.text('Installing ${KitBidi.auto('Claude Code')}…'),
        findsOneWidget,
      );
      // Cancel stops only this job and offers Install again.
      await tester.tap(find.byKey(const ValueKey('agents-cancel-setup')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('cancel-install'));
      expect(find.byKey(const ValueKey('agents-install')), findsOneWidget);
      // Install again, and let it finish: the next step is sign-in.
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      await tester.pumpAndSettle();
      agents.change(() {
        agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.done,
        );
        agents.rows = [agentRowFor('claude', FakeAgentStage.signedOut)];
      });
      await tester.pumpAndSettle();
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(
        find.text('Sign in with ${KitBidi.auto('Claude Code')}'),
        findsWidgets,
      );
      // Signing in is Claude's own, on its terminal: nothing starts yet.
      expect(agents.calls, isNot(contains('sign-in:claude')));
      await tester.tap(find.byKey(const ValueKey('agents-sign-in-start')));
      await tester.pumpAndSettle();
      expect(terminal.calls, contains('sign-in local 24 x 80'));
      // Claude signs in and its sign-in ends: the sheet says so, then closes.
      agents.signedInAfterTerminal = true;
      terminal.exit(1, 0);
      await tester.pumpAndSettle();
      expect(find.text('Signed in'), findsOneWidget);
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('select:claude'));
      expect(find.text('Signed in'), findsNothing);
      expect(_name('Claude Code'), findsOneWidget);
      // No code ever went through the app.
      expect(agents.submitted, isEmpty);
    });

    testWidgets('a finished install that cannot move on reads rows once', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource();
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      await tester.pumpAndSettle();
      agents.calls.clear();
      agents.change(() {
        agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.done,
        );
      });
      await tester.pumpAndSettle();
      for (var i = 0; i < 5; i++) {
        agents.change(() {});
        await tester.pumpAndSettle();
      }
      expect(agents.calls.where((call) => call == 'refresh'), hasLength(1));
    });

    testWidgets('a failed phone check says which step and what to do', (
      tester,
    ) async {
      final agents =
          FakePhoneAgentsSource(
              rows: [agentRowFor('claude', FakeAgentStage.needsCheck)],
            )
            ..nextCheck = AgentPhoneCheckResult(
              agentId: 'claude',
              architecture: AgentArchitecture.arm64,
              passed: false,
              completed: const [
                AgentPhoneCheckStep.install,
                AgentPhoneCheckStep.version,
              ],
              failure: AgentHostFailure.daemon,
            );
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('check:claude'));
      expect(
        find.text("The agent connection didn't start.".trim()),
        findsNothing,
      );
      expect(
        find.textContaining("The agent connection didn't start."),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('agents-check-again')), findsOneWidget);
    });
  });

  group('the agent sheet as a shortcut to install', () {
    testWidgets('a not-installed row shows its size and an Install hint', (
      tester,
    ) async {
      await _newChat(tester, _host(agents: FakePhoneAgentsSource()));
      await _openSheet(tester);
      expect(find.textContaining('Not installed · '), findsOneWidget);
      expect(find.text('Install'), findsOneWidget);
    });

    testWidgets('tapping it runs install, phone check and sign-in in place', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource();
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      await tester.pumpAndSettle();
      // The install finished and the phone is not checked yet: the check
      // runs by itself, in the same sheet.
      agents.afterCheck = () =>
          agents.rows = [agentRowFor('claude', FakeAgentStage.signedOut)];
      agents.change(() {
        agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.done,
        );
        agents.rows = [agentRowFor('claude', FakeAgentStage.needsCheck)];
      });
      await tester.pumpAndSettle();
      expect(agents.calls, contains('check:claude'));
      expect(find.byType(BottomSheet), findsOneWidget);
      // Then sign-in, still the one sheet.
      expect(
        find.text('Sign in with ${KitBidi.auto('Claude Code')}'),
        findsWidgets,
      );
      expect(find.byType(BottomSheet), findsOneWidget);
    });

    testWidgets('a real blocker says exactly why, not "not available yet"', (
      tester,
    ) async {
      final claude = AgentCatalog.builtIn.byId('claude')!;
      final armOnly = AgentDescriptor(
        id: 'arm-only',
        name: 'Arm Only',
        iconKey: 'arm-only',
        route: AgentRoute.paseoNative,
        providerId: 'arm-only',
        signInMethod: AgentSignInMethod.none,
        recipe: AgentInstallRecipe(
          version: claude.recipe!.version,
          executable: claude.recipe!.executable,
          artifacts: {
            AgentArchitecture.arm64: claude.recipe!.artifacts.values.first,
          },
        ),
        limitation: 'Needs a check.',
        resumeReason: 'Needs a check.',
      );
      final agents = FakePhoneAgentsSource(
        rows: [
          buildAgentRow(
            descriptor: armOnly,
            architecture: AgentArchitecture.x64,
            serverCapabilities: ServerCapabilities.allV1,
          ),
        ],
      );
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      expect(find.text('Needs a 64-bit phone'), findsOneWidget);
      expect(find.text('Not available on this phone yet'), findsNothing);
      // Tapping says it again and starts nothing.
      await tester.tap(find.byKey(const ValueKey('agents-choice-arm-only')));
      await tester.pumpAndSettle();
      expect(agents.calls.where((c) => c.startsWith('install')), isEmpty);
      expect(find.text('Needs a 64-bit phone'), findsWidgets);
    });

    testWidgets('server types are not listed as agents', (tester) async {
      final agents = FakePhoneAgentsSource(
        rows: [
          for (final descriptor in AgentCatalog.builtIn.agents)
            buildAgentRow(
              descriptor: descriptor,
              architecture: AgentArchitecture.arm64,
              serverCapabilities: ServerCapabilities.allV1,
            ),
        ],
      );
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      expect(
        find.byKey(const ValueKey('agents-choice-opencode2')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('agents-choice-opencode1')),
        findsNothing,
      );
      expect(find.text(KitBidi.auto('OpenCode 2')), findsNothing);
      expect(find.text(KitBidi.auto('Claude Code')), findsOneWidget);
    });

    testWidgets('a failed step says it in words and Details show the text', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource()
        ..installError = const AgentHostException(AgentHostFailure.unavailable);
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('agents-error')), findsOneWidget);
      expect(find.text("This phone can't run this agent yet."), findsOneWidget);
      // The technical text is only under Details.
      expect(find.textContaining('AgentHostException'), findsNothing);
      final details = find.byKey(const ValueKey('agents-error-details'));
      expect(details, findsOneWidget);
      await tester.ensureVisible(details);
      await tester.tap(find.text('Details'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('AgentHostException(unavailable)'),
        findsOneWidget,
      );
    });
  });

  group('the sign-in terminal', () {
    FakePhoneAgentsSource signedOutClaude() => FakePhoneAgentsSource(
      rows: [agentRowFor('claude', FakeAgentStage.signedOut)],
    );

    Future<FakeLocalTerminalBackend> openTerminal(
      WidgetTester tester,
      FakeChatsHost host,
    ) async {
      final terminal = FakeLocalTerminalBackend();
      await _newChat(
        tester,
        host,
        terminal: LocalTerminalSessions(backend: terminal),
      );
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-in-start')));
      await tester.pumpAndSettle();
      return terminal;
    }

    testWidgets('runs Claude\'s own sign-in; its page opens through the '
        'safe opener and no code passes through the app', (tester) async {
      final agents = signedOutClaude();
      final host = _host(agents: agents);
      final terminal = await openTerminal(tester, host);
      expect(terminal.calls, contains('sign-in local 24 x 80'));
      expect(
        find.byKey(const ValueKey('agents-sign-in-terminal-view')),
        findsOneWidget,
      );
      expect(find.textContaining('Sign in there'), findsOneWidget);
      terminal.openUrl(
        1,
        'https://claude.com/cai/oauth/authorize?code=true&client_id=x',
      );
      await tester.pump();
      expect(host.links.single.host, 'claude.com');
      // Whatever is typed goes to Claude itself, not to the agent source.
      terminal.output(1, 'Paste code here if prompted > ');
      await tester.pump();
      expect(agents.calls.where((c) => c.startsWith('code:')), isEmpty);
      await tester.pump(const Duration(milliseconds: 200));
    });

    testWidgets('ending without signing in says so and starts again', (
      tester,
    ) async {
      final agents = signedOutClaude();
      final terminal = await openTerminal(tester, _host(agents: agents));
      terminal.exit(1, 1);
      await tester.pumpAndSettle();
      expect(agents.calls, contains('recheck:claude'));
      expect(
        find.byKey(const ValueKey('agents-sign-in-terminal-not-yet')),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('agents-sign-in-terminal-again')),
      );
      await tester.pumpAndSettle();
      expect(
        terminal.calls.where((c) => c.startsWith('sign-in local')),
        hasLength(2),
      );
      expect(terminal.calls, contains('remove 1'));
    });

    testWidgets('leaving the terminal ends Claude\'s sign-in', (tester) async {
      final agents = signedOutClaude();
      final terminal = await openTerminal(tester, _host(agents: agents));
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(terminal.calls, contains('remove 1'));
      // Back on the sheet, still offering to sign in.
      expect(
        find.byKey(const ValueKey('agents-sign-in-start')),
        findsOneWidget,
      );
    });
  });

  group('Conversations', () {
    Future<FakePhoneAgentsSource> pumpHome(
      WidgetTester tester, {
      required void Function(FakePhoneAgentsSource) script,
      List<ChatFeedItem> items = const [],
    }) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
      );
      script(agents);
      final host = _host(agents: agents, items: items);
      _lastHost = host;
      await withClock(Clock.fixed(_now), () async {
        await tester.pumpWidget(chatsApp(host, const ChatsHomeScreen()));
        await tester.pumpAndSettle();
      });
      return agents;
    }

    testWidgets('a plan limit is one quiet line with its reset time', (
      tester,
    ) async {
      await pumpHome(
        tester,
        script: (a) => a.lines = [
          PhoneAgentStatusLine(
            agentId: 'claude',
            agentName: 'Claude Code',
            kind: PhoneAgentStatusLineKind.limitReached,
            resetAt: DateTime(2026, 10, 3, 15),
          ),
        ],
      );
      expect(
        find.text('Claude Code plan limit reached · resets 3:00 PM'),
        findsOneWidget,
      );
    });

    testWidgets('a limit without a time says try again later', (tester) async {
      await pumpHome(
        tester,
        script: (a) => a.lines = const [
          PhoneAgentStatusLine(
            agentId: 'claude',
            agentName: 'Claude Code',
            kind: PhoneAgentStatusLineKind.limitReached,
          ),
        ],
      );
      expect(
        find.text('Claude Code plan limit reached · try again later'),
        findsOneWidget,
      );
    });

    testWidgets('Signed out opens the setup sheet at sign-in', (tester) async {
      final agents = await pumpHome(
        tester,
        script: (a) {
          a.rows = [agentRowFor('claude', FakeAgentStage.signedOut)];
          a.lines = const [
            PhoneAgentStatusLine(
              agentId: 'claude',
              agentName: 'Claude Code',
              kind: PhoneAgentStatusLineKind.signedOut,
            ),
          ];
        },
      );
      expect(find.text('Claude Code signed out'), findsOneWidget);
      await tester.tap(
        find.byKey(const ValueKey('agents-status-sign-in-claude')),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Sign in with ${KitBidi.auto('Claude Code')}'),
        findsWidgets,
      );
      expect(agents.calls, contains('recheck:claude'));
    });

    testWidgets('Stopped in the background resumes the host', (tester) async {
      final agents = await pumpHome(
        tester,
        script: (a) => a.lines = const [
          PhoneAgentStatusLine(
            agentId: 'claude',
            agentName: 'Claude Code',
            kind: PhoneAgentStatusLineKind.stopped,
          ),
        ],
      );
      expect(
        find.text('Claude Code stopped in the background'),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('agents-status-resume-claude')),
      );
      await tester.pumpAndSettle();
      expect(agents.calls, contains('resume'));
      expect(find.text('Claude Code stopped in the background'), findsNothing);
    });

    testWidgets('one restart offer closes the app', (tester) async {
      await pumpHome(tester, script: (a) => a.needRestart = true);
      expect(
        find.text('Close and reopen the app to finish clearing sign-ins.'),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('agents-restart-action')));
      expect(_lastHost!.closed, 1);
    });

    testWidgets('a row that cannot reopen asks before starting a new one', (
      tester,
    ) async {
      final item = chat(
        'old',
        'Review the diff',
        at: _now.subtract(const Duration(hours: 2)),
        agentId: 'claude',
        agentLabel: 'Claude Code',
      );
      final agents = await pumpHome(
        tester,
        items: [item],
        script: (a) => a.noticeFor['old'] = const AgentResumeNotice(
          canReopen: false,
          label: "Can't reopen old chats",
          note: 'Starts a new chat',
          requiresAcknowledgement: true,
        ),
      );
      expect(find.text("Can't reopen old conversations"), findsOneWidget);
      await tester.tap(_name('Review the diff'));
      await tester.pumpAndSettle();
      expect(find.text('Start a new conversation?'), findsOneWidget);
      expect(find.textContaining('Starts a new conversation.'), findsOneWidget);
      // Cancel changes nothing.
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(agents.calls.where((c) => c.startsWith('replace')), isEmpty);
      expect(_lastHost!.opened, isEmpty);
      // Start creates the replacement, acknowledged, and opens it.
      await tester.tap(_name('Review the diff'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-start-new')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('replace:old:true'));
      expect(_lastHost!.replaced, ['ses_replacement']);
      expect(_lastHost!.opened, isEmpty);
    });

    testWidgets('a row that can reopen opens straight away', (tester) async {
      final item = chat(
        'ok',
        'Explain the build',
        at: _now.subtract(const Duration(hours: 2)),
      );
      await pumpHome(tester, items: [item], script: (_) {});
      await tester.tap(_name('Explain the build'));
      await tester.pumpAndSettle();
      expect(_lastHost!.opened, ['ok']);
    });
  });

  group('Settings › This phone › Agents', () {
    Future<FakePhoneAgentsSource> pumpSection(
      WidgetTester tester,
      FakePhoneAgentsSource agents,
    ) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final host = _host(agents: agents);
      _lastHost = host;
      await tester.pumpWidget(
        chatsApp(host, ListView(children: const [AgentsSection()])),
      );
      await tester.pumpAndSettle();
      return agents;
    }

    testWidgets('draws nothing without phone agents', (tester) async {
      await pumpSection(tester, FakePhoneAgentsSource(available: false));
      expect(find.byKey(const ValueKey('agents-section')), findsNothing);
    });

    testWidgets('lists each agent with the act it needs', (tester) async {
      await pumpSection(tester, FakePhoneAgentsSource());
      expect(find.text('Agents'), findsOneWidget);
      expect(find.textContaining('Not installed'), findsOneWidget);
      expect(
        find.text('Install ${KitBidi.auto('Claude Code')}'),
        findsOneWidget,
      );
    });

    testWidgets('Check this phone shows each step in plain words', (
      tester,
    ) async {
      final agents =
          FakePhoneAgentsSource(
              rows: [agentRowFor('claude', FakeAgentStage.ready)],
            )
            ..nextCheck = AgentPhoneCheckResult(
              agentId: 'claude',
              architecture: AgentArchitecture.arm64,
              passed: false,
              completed: const [AgentPhoneCheckStep.install],
              failure: AgentHostFailure.version,
            );
      await pumpSection(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-check-phone')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('check:claude'));
      for (final step in ['Installed', 'Version', 'Connection', 'Ready']) {
        expect(find.text(step), findsOneWidget);
      }
      expect(
        find.textContaining(
          "The installed agent didn't pass its version check.",
        ),
        findsOneWidget,
      );
    });

    testWidgets('a passed check says the agent is ready', (tester) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
      );
      await pumpSection(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-check-phone')));
      await tester.pumpAndSettle();
      expect(
        find.text('${KitBidi.auto('Claude Code')} is ready on this phone.'),
        findsOneWidget,
      );
    });

    testWidgets('a fix action opens the sheet at its step', (tester) async {
      await pumpSection(tester, FakePhoneAgentsSource());
      await tester.tap(find.byKey(const ValueKey('agents-fix-claude')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Set up'), findsOneWidget);
    });
  });
}

FakeChatsHost? _lastHost;
