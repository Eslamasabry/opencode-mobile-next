// Settings › Agents: a phone agent row says "Signed in" only after the
// agent's own sign-in status check confirmed it (docs/design/BA1-contract.md),
// shows the account name the check gave as one plain line, and the agent's
// sheet signs it out after asking. Behaviour only; the source is a fake that
// plays the controller's part (test/support/agents_fakes.dart). Account names
// are fixtures.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/chats_fakes.dart';
import 'support/fake_local_terminal.dart';

const _account = 'Example account';
const _signedIn = AgentAuthProbeResult(state: AgentAuthProbeState.signedIn);
const _signedOut = AgentAuthProbeResult(state: AgentAuthProbeState.signedOut);

AgentAuthProbeResult _named(String name) => AgentAuthProbeResult(
  state: AgentAuthProbeState.signedIn,
  accountDisplayName: name,
);

String _n(String name) => KitBidi.auto(name);

FakeAccountAgentsSource _claude({
  AgentAuthProbeResult result = _signedIn,
  bool canSignOut = true,
}) {
  final agents = FakeAccountAgentsSource(
    rows: [agentRowFor('claude', FakeAgentStage.ready)],
  );
  if (canSignOut) agents.signOutCapable.add('claude');
  agents.check('claude', result);
  return agents;
}

Future<void> _pump(
  WidgetTester tester,
  FakePhoneAgentsSource agents, {
  LocalTerminalSessions? terminal,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final host = FakeChatsHost(
    FakeChatFeedSource(
      items: const [],
      projects: [project('alpha')],
      lastUsed: '/root/projects/alpha',
    ),
  )..phoneAgents = agents;
  await tester.pumpWidget(
    chatsApp(
      host,
      ListView(children: const [AgentsSection()]),
      terminal: terminal,
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _openClaudeSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('agents-row-claude')));
  await tester.pumpAndSettle();
}

Finder get _signOut => find.byKey(const ValueKey('agents-sign-out'));

void main() {
  setUpAll(loadCaptureFonts);

  group('the Settings row says Signed in only after the status check', () {
    testWidgets('a confirmed sign-in reads Signed in, not Ready', (
      tester,
    ) async {
      await _pump(tester, _claude());
      expect(
        find.text("Signed in\nCan't reopen old conversations"),
        findsOneWidget,
      );
      expect(find.textContaining('Ready'), findsNothing);
    });

    testWidgets('the account name is one plain line', (tester) async {
      final agents = _claude(result: _named(_account));
      // This agent can reopen old conversations: the account is the only line.
      agents.rows = [agentRowFor('claude', FakeAgentStage.readyVerifiedResume)];
      await _pump(tester, agents);
      expect(find.text('Signed in as ${_n(_account)}'), findsOneWidget);
      expect(find.textContaining('Ready'), findsNothing);
    });

    testWidgets('the account name sits above the quiet reopen line', (
      tester,
    ) async {
      await _pump(tester, _claude(result: _named(_account)));
      expect(
        find.text(
          "Signed in as ${_n(_account)}\nCan't reopen old conversations",
        ),
        findsOneWidget,
      );
    });

    testWidgets('a name with line breaks stays on one line', (tester) async {
      final agents = _claude(result: _named('Example\n account'));
      agents.rows = [agentRowFor('claude', FakeAgentStage.readyVerifiedResume)];
      await _pump(tester, agents);
      expect(
        find.text('Signed in as ${_n('Example account')}'),
        findsOneWidget,
      );
    });

    testWidgets('a very long name wraps without overflowing the row', (
      tester,
    ) async {
      await _pump(tester, _claude(result: _named('a' * 150)));
      expect(tester.takeException(), isNull);
      expect(find.textContaining('Signed in as'), findsOneWidget);
    });

    testWidgets('an install and a passed phone check do not make a row '
        'Signed in', (tester) async {
      final agents = FakeAccountAgentsSource(
        rows: [agentRowFor('claude', FakeAgentStage.notInstalled)],
      );
      await _pump(tester, agents);
      expect(find.text('Install'), findsOneWidget);
      // The install finished and the phone check passed: the row is ready,
      // but no status check has answered.
      agents.rows = [agentRowFor('claude', FakeAgentStage.ready)];
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-check-phone')));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('check:claude'));
      expect(
        find.text("Ready\nCan't reopen old conversations"),
        findsOneWidget,
      );
      expect(find.textContaining('Signed in'), findsNothing);
    });

    testWidgets('signed out reads Sign in needed with a Sign in chip', (
      tester,
    ) async {
      await _pump(tester, _claude(result: _signedOut));
      expect(find.text('Sign in needed'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('agents-row-claude')),
          matching: find.text('Sign in'),
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Signed in'), findsNothing);
    });

    for (final reason in [
      AgentAuthProbeError.probeUnsupported,
      AgentAuthProbeError.timedOut,
      AgentAuthProbeError.invalidResponse,
      AgentAuthProbeError.hostUnavailable,
    ]) {
      testWidgets('a check that ends in ${reason.name} keeps the existing '
          'wording and claims nothing', (tester) async {
        await _pump(
          tester,
          _claude(result: AgentAuthProbeResult.failed(reason)),
        );
        expect(find.text('Sign in needed'), findsOneWidget);
        expect(find.textContaining('Signed'), findsNothing);
        expect(find.textContaining('Ready'), findsNothing);
      });
    }

    testWidgets('other agents keep their wording beside a signed-in one', (
      tester,
    ) async {
      final agents = FakeAccountAgentsSource(
        rows: [
          agentRowFor('claude', FakeAgentStage.ready),
          agentRowFor('codex', FakeAgentStage.notInstalled),
          agentRowFor('gemini', FakeAgentStage.notInstalled),
        ],
      )..check('claude', _named(_account));
      await _pump(tester, agents);
      expect(find.textContaining('Signed in as'), findsOneWidget);
      expect(find.textContaining('Not installed'), findsNWidgets(2));
    });

    testWidgets('a source without status checks keeps Ready', (tester) async {
      await _pump(
        tester,
        FakePhoneAgentsSource(
          rows: [agentRowFor('claude', FakeAgentStage.ready)],
        ),
      );
      expect(
        find.text("Ready\nCan't reopen old conversations"),
        findsOneWidget,
      );
      expect(find.textContaining('Signed'), findsNothing);
    });
  });

  group('Sign out in the agent sheet', () {
    testWidgets('a confirmed sign-in offers Sign out of the agent', (
      tester,
    ) async {
      await _pump(tester, _claude());
      await _openClaudeSheet(tester);
      expect(find.text('Signed in'), findsWidgets);
      expect(find.text('Sign out of ${_n('Claude Code')}'), findsOneWidget);
    });

    testWidgets('it asks first: names the agent, says conversations stay, '
        'and Cancel changes nothing', (tester) async {
      final agents = _claude();
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      expect(find.text('Sign out of ${_n('Claude Code')}?'), findsOneWidget);
      expect(
        find.textContaining(
          '${_n('Claude Code')} can\'t start new conversations until you '
          'sign in again.',
        ),
        findsOneWidget,
      );
      expect(
        find.text('Your conversations with ${_n('Claude Code')} stay.'),
        findsOneWidget,
      );
      expect(agents.calls.where((c) => c.startsWith('sign-out')), isEmpty);
      await tester.tap(find.byKey(const ValueKey('kit-confirm-cancel')));
      await tester.pumpAndSettle();
      expect(agents.calls.where((c) => c.startsWith('sign-out')), isEmpty);
      expect(
        agents.agentAccount('claude')?.state,
        AgentAuthProbeState.signedIn,
      );
      expect(_signOut, findsOneWidget);
    });

    testWidgets('confirming runs the agent logout; the sheet then offers '
        'Sign in', (tester) async {
      final agents = _claude(result: _named(_account));
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pumpAndSettle();
      expect(agents.calls.where((c) => c.startsWith('sign-out')), [
        'sign-out:claude',
      ]);
      expect(
        agents.agentAccount('claude')?.state,
        AgentAuthProbeState.signedOut,
      );
      expect(_signOut, findsNothing);
      expect(
        find.byKey(const ValueKey('agents-sign-in-start')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('agents-error')), findsNothing);
    });

    testWidgets('the row behind turns to Sign in after the sign-out', (
      tester,
    ) async {
      final agents = _claude(result: _named(_account));
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pumpAndSettle();
      expect(find.text('Sign in needed'), findsOneWidget);
      expect(find.textContaining('Signed in as'), findsNothing);
    });

    testWidgets('while it runs the other actions wait and the layout holds', (
      tester,
    ) async {
      final agents = _claude()..signOutGate = Completer<void>();
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(agents.calls, contains('sign-out:claude'));
      expect(
        tester
            .widget<KitButton>(
              find.byKey(const ValueKey('agents-sign-in-again')),
            )
            .onPressed,
        isNull,
      );
      expect(find.text('Signed in'), findsWidgets);
      agents.signOutGate!.complete();
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('agents-sign-in-start')),
        findsOneWidget,
      );
    });

    testWidgets('an unconfirmed sign-out says so in plain words and reads '
        'the real state again', (tester) async {
      final agents = _claude()..logoutUnconfirmed = true;
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'Couldn\'t confirm that ${_n('Claude Code')} signed out. Try again.',
        ),
        findsOneWidget,
      );
      // The controller's own sentence is under Details, never copy.
      expect(find.textContaining('could not be confirmed'), findsNothing);
      expect(agents.calls, contains('recheck:claude'));
      // Still signed in, so Sign out is still there to try again.
      expect(_signOut, findsOneWidget);
      expect(find.byKey(const ValueKey('agents-sign-in-start')), findsNothing);
    });

    testWidgets('a failed sign-out never flashes the signed-out layout while '
        'the real state is read again', (tester) async {
      final agents = _claude()
        ..logoutUnconfirmed = true
        ..recheckGate = Completer<void>();
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      // The unconfirmed answer is in, the re-read is pending.
      expect(agents.agentAccount('claude')?.state, AgentAuthProbeState.error);
      expect(find.byKey(const ValueKey('agents-sign-in-start')), findsNothing);
      expect(find.text('Signed in'), findsWidgets);
      agents.recheckGate!.complete();
      await tester.pumpAndSettle();
      expect(_signOut, findsOneWidget);
      expect(find.byKey(const ValueKey('agents-error')), findsOneWidget);
    });

    testWidgets('a retry after a failure signs out', (tester) async {
      final agents = _claude()..logoutUnconfirmed = true;
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pumpAndSettle();
      agents.logoutUnconfirmed = false;
      await tester.tap(_signOut);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('agents-sign-in-start')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('agents-error')), findsNothing);
    });

    testWidgets('an agent whose logout is not qualified has no Sign out', (
      tester,
    ) async {
      await _pump(tester, _claude(canSignOut: false));
      await _openClaudeSheet(tester);
      expect(find.text('Signed in'), findsWidgets);
      expect(_signOut, findsNothing);
    });

    testWidgets('a source without status checks has no Sign out', (
      tester,
    ) async {
      final agents =
          FakePhoneAgentsSource(
              rows: [agentRowFor('claude', FakeAgentStage.ready)],
            )
            ..signIn['claude'] = const AgentSignInState(
              phase: AgentSignInPhase.signedIn,
              method: AgentSignInMethod.browserOAuthHost,
              inspected: true,
            );
      await _pump(tester, agents);
      await _openClaudeSheet(tester);
      expect(find.text('Signed in'), findsWidgets);
      expect(_signOut, findsNothing);
    });

    testWidgets('a signed-out agent has no Sign out', (tester) async {
      final agents = _claude(result: _signedOut);
      await _pump(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-fix-claude')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('agents-sign-in-start')),
        findsOneWidget,
      );
      expect(_signOut, findsNothing);
    });
  });

  group('a finished sign-in terminal', () {
    Future<FakeLocalTerminalBackend> openTerminal(
      WidgetTester tester,
      FakeAccountAgentsSource agents,
    ) async {
      final terminal = FakeLocalTerminalBackend();
      await _pump(
        tester,
        agents,
        terminal: LocalTerminalSessions(backend: terminal),
      );
      await tester.tap(find.byKey(const ValueKey('agents-fix-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-sign-in-start')));
      await tester.pumpAndSettle();
      return terminal;
    }

    testWidgets('a clean exit is not a sign-in until the status check says '
        'so', (tester) async {
      final agents = _claude(result: _signedOut);
      final terminal = await openTerminal(tester, agents);
      terminal.exit(1, 0);
      await tester.pumpAndSettle();
      expect(agents.calls, contains('confirm:claude'));
      expect(
        find.byKey(const ValueKey('agents-sign-in-terminal-not-yet')),
        findsOneWidget,
      );
      expect(find.textContaining('Signed in'), findsNothing);
    });

    testWidgets('it ends once the status check says signed in', (tester) async {
      final agents = _claude(result: _signedOut);
      final terminal = await openTerminal(tester, agents);
      // The agent now answers signed in; the app has not read it yet.
      agents.truth['claude'] = _named(_account);
      terminal.exit(1, 0);
      await tester.pumpAndSettle();
      expect(agents.calls, contains('confirm:claude'));
      expect(
        find.byKey(const ValueKey('agents-sign-in-terminal-view')),
        findsNothing,
      );
      expect(agents.agentAccount('claude')?.accountDisplayName, _account);
      // Signed in shows for a moment, then the agent is chosen.
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(agents.calls, contains('select:claude'));
    });
  });
}
