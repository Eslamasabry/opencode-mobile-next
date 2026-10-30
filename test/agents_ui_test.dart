import 'support/fake_server_capabilities.dart';
import 'dart:async';
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show SystemChannels;
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_tools/agent_certification.dart';
import 'package:opencode_mobile/domain/mark_catalog.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sheet.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_model_sheet.dart'
    show agentDefaultModelName;
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/chats_fakes.dart';
import 'support/fake_local_terminal.dart';
import 'support/fake_sign_in_foreground.dart';

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

/// The agent sheet, opened the way the app does from the start screen's
/// "Set up" cards and from Settings.
Future<void> _openSheet(WidgetTester tester) async {
  unawaited(showAgentSheet(tester.element(find.byType(NewChatScreen))));
  await tester.pumpAndSettle();
}

Finder _name(String text) => find.text(KitBidi.auto(text));

void _expectClaudeSignIn(FakeLocalTerminalBackend terminal) {
  // Foreground admission is awaited before PTY launch, so the screen can lay
  // out first. Verify scope, command and valid dimensions rather than 24 x 80.
  final starts = terminal.calls.where((call) => call.startsWith('sign-in '));
  expect(starts, hasLength(1));
  expect(starts.single, matches(r'^sign-in local [1-9]\d* x [1-9]\d*$'));
  expect(terminal.programs.single, ['claude', 'auth', 'login', '--claudeai']);
}

void main() {
  setUpAll(loadCaptureFonts);

  group('the agent chip', () {
    testWidgets('is hidden without phone agents', (tester) async {
      await _newChat(tester, _host());
      expect(find.byType(KitAgentChoice), findsNothing);
    });

    testWidgets('is hidden while phoneAgentsAvailable is false', (
      tester,
    ) async {
      await _newChat(
        tester,
        _host(agents: FakePhoneAgentsSource(available: false)),
      );
      expect(find.byType(KitAgentChoice), findsNothing);
    });

    testWidgets('shows OpenCode beside the project when available', (
      tester,
    ) async {
      await _newChat(
        tester,
        _host(agents: FakePhoneAgentsSource(rows: const [])),
      );
      // One agent never shows one lonely card: the quiet line instead.
      expect(find.byType(KitAgentChoice), findsNothing);
      expect(find.textContaining('OpenCode'), findsWidgets);
      expect(find.textContaining('alpha'), findsWidgets);
    });

    testWidgets('several agents are cards; the chosen one is selected and '
        'the button names it', (tester) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
        selected: 'claude',
      );
      await _newChat(tester, _host(agents: agents));
      expect(find.byType(KitAgentChoice), findsNWidgets(2));
      expect(find.text('Careful with big changes'), findsOneWidget);
      expect(find.text('Any model, free ones too'), findsOneWidget);
      expect(find.text('Start with Claude Code'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('chats-new-agent-opencode')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('select:opencode'));
      expect(find.text('Start with OpenCode'), findsOneWidget);
    });

    testWidgets('an agent not set up is a dimmed card that opens its setup', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.notInstalled)],
      );
      await _newChat(tester, _host(agents: agents));
      expect(find.byType(KitAgentChoice), findsNWidgets(2));
      expect(find.text('Set up'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('chats-new-agent-claude')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('agents-sheet')), findsOneWidget);
    });

    testWidgets('an agent that setting up would still not make a choice is '
        'left out', (tester) async {
      // Codex is not certified for chat on this version: installing it
      // would end at "not certified", so the start screen offers no card.
      final agents = FakePhoneAgentsSource(
        rows: [
          agentRowFor('claude', FakeAgentStage.ready),
          agentRowFor('codex', FakeAgentStage.notInstalled),
        ],
        selected: 'claude',
      );
      await _newChat(tester, _host(agents: agents));
      expect(find.byType(KitAgentChoice), findsNWidgets(2));
      expect(find.text('Set up'), findsNothing);
      expect(find.byKey(const ValueKey('chats-new-agent-codex')), findsNothing);
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
      expect(find.text('Start with Claude Code'), findsOneWidget);
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
      // The agent's own default, by its plain name.
      expect(_name('Opus'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('chats-new-model')));
      await tester.pumpAndSettle();
      expect(find.text('Choose a model'), findsOneWidget);
      expect(agents.calls, contains('models:claude'));
      await tester.tap(find.byKey(const ValueKey('agents-model-sonnet')));
      await tester.pumpAndSettle();
      expect(agents.chosenModel['claude'], 'sonnet');
      expect(find.text('Choose a model'), findsNothing);
      expect(_name('Sonnet'), findsOneWidget);
    });

    testWidgets('the row follows the chosen card\'s own default', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
        selected: 'claude',
      );
      await _newChat(tester, _host(agents: agents));
      Finder value(String text) => find.descendant(
        of: find.byKey(const ValueKey('chats-new-model')),
        matching: find.text(text),
      );
      expect(value(KitBidi.auto('Opus')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('chats-new-agent-opencode')));
      await tester.pumpAndSettle();
      expect(value('Server default'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('chats-new-agent-claude')));
      await tester.pumpAndSettle();
      expect(value(KitBidi.auto('Opus')), findsOneWidget);
    });

    testWidgets('says Default model only when the list names no model', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
        selected: 'claude',
      );
      agents.models['claude'] = const [
        AgentModelChoice(id: 'default', name: 'Default', isDefault: true),
        AgentModelChoice(id: 'sonnet', name: 'Sonnet'),
      ];
      await _newChat(tester, _host(agents: agents));
      expect(find.text('Default model'), findsOneWidget);
      expect(find.text('default'), findsNothing);
    });

    test('a default is named plainly, never by a raw id', () {
      String? name(String id, String label) => agentDefaultModelName([
        AgentModelChoice(id: id, name: label, isDefault: true),
        const AgentModelChoice(id: 'other', name: 'Other'),
      ]);
      expect(name('default', 'Default (Opus)'), 'Opus');
      expect(name('default', 'Default (recommended)'), isNull);
      expect(name('default', 'Default'), isNull);
      expect(name('claude-sonnet-5-5', 'Sonnet 5.5'), 'Sonnet 5.5');
      expect(
        name('claude-sonnet-5-5', 'claude-sonnet-5-5'),
        isNot('claude-sonnet-5-5'),
      );
      expect(agentDefaultModelName(const []), isNull);
    });

    testWidgets('OpenCode keeps the server model', (tester) async {
      await _newChat(tester, _host(agents: FakePhoneAgentsSource()));
      expect(find.text('Server default'), findsOneWidget);
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
      expect(find.text('Start with Claude Code'), findsOneWidget);
    });

    testWidgets('certified ready agents come first; the rest are dimmed and '
        'say why, with a way forward where there is one', (tester) async {
      final semantics = tester.ensureSemantics();
      final agents = FakePhoneAgentsSource(
        rows: [
          // Ready, but this version has no certification evidence.
          agentRowFor('gemini', FakeAgentStage.ready),
          agentRowFor('codex', FakeAgentStage.signedOut),
          // Ready and certified on the pinned version (the matrix).
          agentRowFor('claude', FakeAgentStage.ready),
          agentRowFor('goose', FakeAgentStage.notInstalled),
        ],
      );
      await _newChat(tester, _host(agents: agents));
      await _openSheet(tester);
      Finder row(String id) => find.byKey(ValueKey('agents-choice-$id'));
      double top(String id) => tester.getTopLeft(row(id)).dy;
      bool enabled(String id) => tester.widget<KitRow>(row(id)).enabled;

      expect(top('opencode'), lessThan(top('claude')));
      for (final other in ['gemini', 'codex', 'goose']) {
        expect(top('claude'), lessThan(top(other)), reason: other);
        expect(enabled(other), isFalse, reason: other);
      }
      expect(enabled('opencode'), isTrue);
      expect(enabled('claude'), isTrue);
      // One plain reason each.
      expect(find.text('Not certified on this version yet'), findsOneWidget);
      expect(find.text('Sign in needed'), findsOneWidget);
      expect(find.textContaining('Not installed'), findsOneWidget);
      // The way forward is a short chip; its target is in what it says to
      // screen readers. An uncertified version has none.
      Finder chipIn(String id, String act) =>
          find.descendant(of: row(id), matching: find.text(act));
      expect(chipIn('codex', 'Sign in'), findsOneWidget);
      expect(chipIn('goose', 'Install'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Sign in to ${KitBidi.auto('Codex')}'),
        findsOneWidget,
      );
      expect(
        find.descendant(of: row('gemini'), matching: find.byType(KitChip)),
        findsNothing,
      );

      // Tapping the uncertified agent chooses nothing and keeps the list.
      await tester.tap(row('gemini'));
      await tester.pumpAndSettle();
      expect(agents.calls.where((call) => call.startsWith('select:')), isEmpty);
      expect(find.text('Choose an agent'), findsOneWidget);
      // The signed-out one goes straight to its sign-in.
      await tester.tap(find.byKey(const ValueKey('agents-choice-fix-codex')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('agents-sign-in-words')),
        findsOneWidget,
      );
      semantics.dispose();
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
        terminal: LocalTerminalSessions(
          backend: terminal,
          signInForeground: FakeSignInForeground(),
        ),
      );
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      // The setup step replaced the list; there is still one sheet.
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('agents-sheet')),
          matching: find.textContaining('Set up'),
        ),
        findsOneWidget,
      );
      expect(find.text('Choose an agent'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      // The install bar is indeterminate until bytes arrive: it animates.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(agents.calls, contains('install:claude'));
      expect(find.byKey(const ValueKey('agents-progress')), findsOneWidget);
      expect(
        find.text('Installing ${KitBidi.auto('Claude Code')}…'),
        findsOneWidget,
      );
      // Measured bytes name the bar, as the phone check does.
      agents.change(
        () => agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.installing,
          downloadedBytes: 100000000,
          totalBytes: 252000000,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('100 MB', findRichText: true), findsOneWidget);
      expect(
        find.textContaining('of \u2066252 MB', findRichText: true),
        findsOneWidget,
      );
      // Cancel stops only this job and offers Install again.
      await tester.tap(find.byKey(const ValueKey('agents-cancel-setup')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('cancel-install'));
      expect(find.byKey(const ValueKey('agents-install')), findsOneWidget);
      // Install again, and let it finish: the next step is sign-in.
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      // The install bar is indeterminate until bytes arrive: it animates.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
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
      _expectClaudeSignIn(terminal);
      // Claude signs in and its sign-in ends: the sheet says so, then closes.
      agents.signedInAfterTerminal = true;
      terminal.exit(1, 0);
      await tester.pumpAndSettle();
      expect(find.text('Signed in'), findsOneWidget);
      // Still there after a second: long enough to read.
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Signed in'), findsOneWidget);
      expect(agents.calls, isNot(contains('select:claude')));
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('select:claude'));
      expect(find.text('Signed in'), findsNothing);
      expect(find.text('Start with Claude Code'), findsOneWidget);
      // No code ever went through the app.
      expect(agents.submitted, isEmpty);
    });

    // Re-check G1 (2026-10-17): the bar sat near 75% from the phase-based
    // overall fraction, then fell to ~13% when bytes appeared.
    testWidgets('install bar: indeterminate before bytes, never backwards, '
        'MB line from the first count', (tester) async {
      final agents = FakePhoneAgentsSource();
      final host = _host(agents: agents);
      await _newChat(tester, host);
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-install')));
      // The install bar is indeterminate until bytes arrive: it animates.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      double? bar() => tester
          .widget<LinearProgressIndicator>(
            find.byKey(const ValueKey('agents-progress')),
          )
          .value;
      // Phase fraction only, no bytes: indeterminate, not a fake 75%.
      agents.change(
        () => agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.installing,
          fraction: 0.75,
        ),
      );
      await tester.pump();
      expect(bar(), isNull);
      expect(find.textContaining('Downloading'), findsNothing);
      expect(
        find.textContaining('Installing ', findRichText: true),
        findsWidgets,
      );
      // The first byte count shows the MB line at once.
      agents.change(
        () => agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.installing,
          fraction: 0.75,
          downloadedBytes: 38000000,
          totalBytes: 253000000,
        ),
      );
      await tester.pump();
      expect(find.textContaining('38 MB', findRichText: true), findsOneWidget);
      final first = bar()!;
      expect(first, closeTo(38 / 253, 0.01));
      // A later, smaller reading (the next component) never lowers the bar.
      agents.change(
        () => agents.progress = const AgentSetupProgress(
          agentId: 'claude',
          phase: AgentSetupPhase.installing,
          downloadedBytes: 5000000,
          totalBytes: 253000000,
        ),
      );
      await tester.pump();
      expect(bar(), greaterThanOrEqualTo(first));
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
      // The install bar is indeterminate until bytes arrive: it animates.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
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
    testWidgets('a not-installed row shows its size and an Install chip', (
      tester,
    ) async {
      await _newChat(tester, _host(agents: FakePhoneAgentsSource()));
      await _openSheet(tester);
      expect(find.textContaining('Not installed · '), findsOneWidget);
      expect(
        find.byKey(const ValueKey('agents-choice-fix-claude')),
        findsOneWidget,
      );
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
      // The install bar is indeterminate until bytes arrive: it animates.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
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
            serverCapabilities: testServerCapabilities,
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
              serverCapabilities: testServerCapabilities,
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
      // The install bar is indeterminate until bytes arrive: it animates.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byKey(const ValueKey('agents-error')), findsOneWidget);
      expect(
        find.text(
          "This phone can't run this agent yet. Open Details to see why.",
        ),
        findsOneWidget,
      );
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
      // The type name is said once, not twice.
      expect(find.textContaining('AgentHostException:'), findsNothing);
      // The words name no action that is not on the sheet.
      expect(find.textContaining('Check again'), findsNothing);
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
        terminal: LocalTerminalSessions(
          backend: terminal,
          signInForeground: FakeSignInForeground(),
        ),
      );
      await _openSheet(tester);
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-in-start')));
      await tester.pumpAndSettle();
      return terminal;
    }

    testWidgets('with a screen reader, sign-in success is announced and the '
        'sheet waits for the person', (tester) async {
      tester.platformDispatcher.accessibilityFeaturesTestValue =
          const FakeAccessibilityFeatures(accessibleNavigation: true);
      addTearDown(
        tester.platformDispatcher.clearAccessibilityFeaturesTestValue,
      );
      final spoken = <String>[];
      tester.binding.defaultBinaryMessenger
          .setMockDecodedMessageHandler<dynamic>(SystemChannels.accessibility, (
            message,
          ) async {
            if (message is Map && message['type'] == 'announce') {
              spoken.add((message['data'] as Map)['message'] as String);
            }
            return null;
          });
      addTearDown(
        () => tester.binding.defaultBinaryMessenger
            .setMockDecodedMessageHandler<dynamic>(
              SystemChannels.accessibility,
              null,
            ),
      );
      final agents = signedOutClaude();
      final terminal = await openTerminal(tester, _host(agents: agents));
      agents.signedInAfterTerminal = true;
      terminal.exit(1, 0);
      await tester.pumpAndSettle();
      expect(spoken.single, startsWith('Signed in.'));
      // Nothing moves the person on; "Use Claude Code" is the way.
      await tester.pump(const Duration(seconds: 10));
      await tester.pumpAndSettle();
      expect(agents.calls, isNot(contains('select:claude')));
      expect(find.text('Signed in'), findsOneWidget);
      expect(find.byKey(const ValueKey('agents-sign-in-done')), findsOneWidget);
    });

    testWidgets('runs Claude\'s own sign-in; its page opens through the '
        'safe opener and no code passes through the app', (tester) async {
      final agents = signedOutClaude();
      final host = _host(agents: agents);
      final terminal = await openTerminal(tester, host);
      _expectClaudeSignIn(terminal);
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

    testWidgets('signed in, it still offers to sign in again', (tester) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
      );
      agents.signIn['claude'] = const AgentSignInState(
        phase: AgentSignInPhase.signedIn,
        method: AgentSignInMethod.browserOAuthHost,
        inspected: true,
      );
      final terminal = FakeLocalTerminalBackend();
      await _newChat(
        tester,
        _host(agents: agents),
        terminal: LocalTerminalSessions(
          backend: terminal,
          signInForeground: FakeSignInForeground(),
        ),
      );
      final context = tester.element(find.byType(NewChatScreen));
      unawaited(
        showAgentSheet(context, agentId: 'claude', step: AgentSheetStep.signIn),
      );
      await tester.pumpAndSettle();
      expect(find.text('Signed in'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('agents-sign-in-again')));
      await tester.pumpAndSettle();
      expect(terminal.programs.single, [
        'claude',
        'auth',
        'login',
        '--claudeai',
      ]);
      await tester.pump(const Duration(milliseconds: 200));
    });

    testWidgets('each agent signs in with its own login command', (
      tester,
    ) async {
      final claude = await openTerminal(
        tester,
        _host(agents: signedOutClaude()),
      );
      expect(claude.programs.single, ['claude', 'auth', 'login', '--claudeai']);
      await tester.pump(const Duration(milliseconds: 200));
    });

    testWidgets(
      'Codex runs its device sign-in, in words that are not Claude\'s',
      (tester) async {
        final agents = FakePhoneAgentsSource(
          rows: [agentRowFor('codex', FakeAgentStage.signedOut)],
        );
        final terminal = FakeLocalTerminalBackend();
        await _newChat(
          tester,
          _host(agents: agents),
          terminal: LocalTerminalSessions(
            backend: terminal,
            signInForeground: FakeSignInForeground(),
          ),
        );
        await _openSheet(tester);
        await tester.tap(find.byKey(const ValueKey('agents-choice-codex')));
        await tester.pumpAndSettle();
        expect(find.textContaining('Claude account'), findsNothing);
        await tester.tap(find.byKey(const ValueKey('agents-sign-in-start')));
        await tester.pumpAndSettle();
        expect(terminal.programs.single, ['codex', 'login', '--device-auth']);
        expect(find.textContaining('Claude account'), findsNothing);
        await tester.pump(const Duration(milliseconds: 200));
      },
    );

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

    testWidgets(
      'an agent New conversation does not offer asks for no sign-in',
      (tester) async {
        // Codex is installed but not certified for chat on this version:
        // signing in would lead nowhere, so Conversations stays quiet.
        expect(
          AgentCertificationMatrix.bundled.certifiedForChat('codex'),
          isFalse,
        );
        await pumpHome(
          tester,
          script: (a) {
            a.rows = [agentRowFor('codex', FakeAgentStage.signedOut)];
            a.lines = const [
              PhoneAgentStatusLine(
                agentId: 'codex',
                agentName: 'Codex',
                kind: PhoneAgentStatusLineKind.signedOut,
              ),
              PhoneAgentStatusLine(
                agentId: 'codex',
                agentName: 'Codex',
                kind: PhoneAgentStatusLineKind.limitReached,
              ),
            ];
          },
        );
        expect(find.text('Codex signed out'), findsNothing);
        expect(
          find.byKey(const ValueKey('agents-status-sign-in-codex')),
          findsNothing,
        );
        expect(find.textContaining('Codex plan limit'), findsNothing);
      },
    );

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

    testWidgets('a Resume that fails never says the phone cannot run the '
        'agent, and Details name the phone\'s code', (tester) async {
      final agents = await pumpHome(
        tester,
        script: (a) => a
          ..lines = const [
            PhoneAgentStatusLine(
              agentId: 'claude',
              agentName: 'Claude Code',
              kind: PhoneAgentStatusLineKind.stopped,
            ),
          ]
          ..resumeError = const AgentHostException(
            AgentHostFailure.unavailable,
            code: 'agent_host_exited',
          ),
      );
      await tester.tap(
        find.byKey(const ValueKey('agents-status-resume-claude')),
      );
      await tester.pumpAndSettle();
      expect(agents.calls, contains('resume'));
      expect(find.textContaining("can't run this agent"), findsNothing);
      expect(
        find.text(
          "${KitBidi.auto('Claude Code')} didn't start again. Tap Resume to "
          "try once more. If it still won't start, run its phone check.",
        ),
        findsOneWidget,
      );
      expect(find.textContaining('agent_host_exited'), findsNothing);
      await tester.tap(find.text('Details'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining(
          'AgentHostException(unavailable: agent_host_exited)',
        ),
        findsOneWidget,
      );
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
      // The state rides the row's meta line (project, time) now.
      expect(
        find.textContaining("Can't reopen old conversations"),
        findsOneWidget,
      );
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

    testWidgets('a ready agent opens its sign-in step, with Sign in again', (
      tester,
    ) async {
      final agents = FakePhoneAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.ready)],
      );
      agents.signIn['claude'] = const AgentSignInState(
        phase: AgentSignInPhase.signedIn,
        method: AgentSignInMethod.browserOAuthHost,
        inspected: true,
      );
      await pumpSection(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-row-claude')));
      await tester.pumpAndSettle();
      expect(find.text('Signed in'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('agents-sign-in-again')),
        findsOneWidget,
      );
    });

    testWidgets('draws nothing without phone agents', (tester) async {
      await pumpSection(tester, FakePhoneAgentsSource(available: false));
      expect(find.byKey(const ValueKey('agents-section')), findsNothing);
    });

    testWidgets('lists each agent with the act it needs', (tester) async {
      final semantics = tester.ensureSemantics();
      await pumpSection(tester, FakePhoneAgentsSource());
      expect(find.text('Agents'), findsOneWidget);
      expect(find.textContaining('Not installed'), findsOneWidget);
      // The row's title names the agent; the chip says only the act, and
      // screen readers still hear its target.
      expect(find.text('Install'), findsOneWidget);
      expect(
        find.bySemanticsLabel('Install ${KitBidi.auto('Claude Code')}'),
        findsOneWidget,
      );
      semantics.dispose();
    });

    testWidgets('each act is a short chip, all ending on the trailing edge', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await pumpSection(
        tester,
        FakePhoneAgentsSource(
          rows: [
            agentRowFor('claude', FakeAgentStage.ready),
            agentRowFor('codex', FakeAgentStage.notInstalled),
            agentRowFor('gemini', FakeAgentStage.notInstalled),
            agentRowFor('omp-acp', FakeAgentStage.notInstalled),
            agentRowFor('fx', FakeAgentStage.signedOut),
          ],
        ),
      );
      // No "Install Gemini CLI" or "Sign in to fx" of ragged widths.
      expect(find.text('Install'), findsNWidgets(3));
      expect(find.text('Sign in'), findsOneWidget);
      expect(find.textContaining('Install '), findsNothing);
      expect(find.textContaining('Sign in to'), findsNothing);
      for (final (id, act) in [
        ('codex', 'Install ${KitBidi.auto('Codex')}'),
        ('gemini', 'Install ${KitBidi.auto('Gemini CLI')}'),
        ('omp-acp', 'Install ${KitBidi.auto('Oh My Pi')}'),
        ('fx', 'Sign in to ${KitBidi.auto('fx')}'),
      ]) {
        expect(find.bySemanticsLabel(act), findsOneWidget, reason: id);
      }
      // A chip ends where the chevrons of the rows around it end, so the
      // trailing edge is one line down the list.
      final ends = {
        for (final id in ['codex', 'gemini', 'omp-acp', 'fx'])
          tester.getTopRight(find.byKey(ValueKey('agents-fix-$id'))).dx,
      };
      expect(ends, hasLength(1), reason: '$ends');
      // A tap still runs the act for its own agent.
      await tester.tap(find.byKey(const ValueKey('agents-fix-fx')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('agents-sign-in-words')),
        findsOneWidget,
      );
      semantics.dispose();
    });

    testWidgets('Kilo and Cline are installable rows with their own marks', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await pumpSection(
        tester,
        FakePhoneAgentsSource(
          rows: [
            agentRowFor('kilo', FakeAgentStage.notInstalled),
            agentRowFor('cline', FakeAgentStage.notInstalled),
          ],
        ),
      );
      expect(find.textContaining('Kilo'), findsWidgets);
      expect(find.textContaining('Cline'), findsWidgets);
      expect(find.text('Install'), findsNWidgets(2));
      expect(
        find.bySemanticsLabel('Install ${KitBidi.auto('Kilo')}'),
        findsOne,
      );
      expect(
        find.bySemanticsLabel('Install ${KitBidi.auto('Cline')}'),
        findsOne,
      );
      expect(agentMarkSlug('kilo'), 'kilocode');
      expect(agentMarkSlug('cline'), 'cline');
      semantics.dispose();
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
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('agents-sheet')),
          matching: find.textContaining('Set up'),
        ),
        findsOneWidget,
      );
    });
  });
}

FakeChatsHost? _lastHost;
