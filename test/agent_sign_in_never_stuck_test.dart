// Issue #95: an agent's sign-in could sit on "Checking sign-in…" for good
// when its status read failed or never answered. Every read now ends in a
// plain state: the sign-in step offers Sign in, and the sign-in terminal says
// the agent isn't signed in yet and offers to start again.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sign_in_terminal.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/chats_fakes.dart';
import 'support/fake_local_terminal.dart';
import 'support/fake_sign_in_foreground.dart';

/// A phone whose status read for the agent fails every time.
class _UnreadableAgents extends FakeAccountAgentsSource {
  _UnreadableAgents({super.rows});

  @override
  Future<void> recheckAgentSignIn(String agentId) async {
    calls.add('recheck:$agentId');
    throw StateError('the agent host went away');
  }

  @override
  Future<bool> confirmAgentSignIn(String agentId) async {
    calls.add('confirm:$agentId');
    throw StateError('the agent host went away');
  }
}

void main() {
  setUpAll(loadCaptureFonts);

  void phone(WidgetTester tester) {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  testWidgets('a sign-in step whose status read fails offers Sign in', (
    tester,
  ) async {
    phone(tester);
    final agents = _UnreadableAgents(
      rows: [agentRowFor('codex', FakeAgentStage.signedOut)],
    );
    final host = FakeChatsHost(
      FakeChatFeedSource(items: const [], projects: [project('alpha')]),
    )..phoneAgents = agents;
    await tester.pumpWidget(
      chatsApp(host, ListView(children: const [AgentsSection()])),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('agents-fix-codex')));
    await tester.pumpAndSettle();

    expect(agents.calls, contains('recheck:codex'));
    expect(find.text('Checking sign-in…'), findsNothing);
    expect(find.byKey(const ValueKey('agents-sign-in-start')), findsOneWidget);
  });

  testWidgets('a sign-in terminal whose check fails offers to start again', (
    tester,
  ) async {
    phone(tester);
    final backend = FakeLocalTerminalBackend();
    final host = FakeChatsHost(
      FakeChatFeedSource(items: const [], projects: [project('alpha')]),
    );
    await tester.pumpWidget(
      chatsApp(
        host,
        AgentSignInTerminalScreen(
          agents: _UnreadableAgents(
            rows: [agentRowFor('codex', FakeAgentStage.signedOut)],
          ),
          agentId: 'codex',
          agentName: 'Codex',
          sessions: LocalTerminalSessions(
            backend: backend,
            signInForeground: FakeSignInForeground(),
          ),
          openPage: (context, url) async {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 200));

    backend.exit(1, 0);
    await tester.pumpAndSettle();

    expect(find.text('Checking sign-in…'), findsNothing);
    expect(
      find.byKey(const ValueKey('agents-sign-in-terminal-again')),
      findsOneWidget,
    );
  });
}
