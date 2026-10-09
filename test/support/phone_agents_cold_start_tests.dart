part of '../phone_agents_controller_test.dart';

class _ColdStartHost extends _FakeHost implements PhoneAgentLivenessPort {
  _ColdStartHost(super.events, super.profileId, super.state);
  @override
  Future<bool?> helperRunning() async => state.helperRunning;
}

void _phoneColdStartTests() {
  const stopped = PhoneAgentRuntime(
    agentId: 'claude',
    installed: true,
    hostAvailable: false,
    architectureQualified: true,
    stoppedInBackground: true,
    signInPhase: AgentSignInPhase.signedIn,
  );

  testWidgets('cold start stays quiet until the helper answers at 14 seconds', (
    tester,
  ) async {
    final w = await _world(
      tester,
      livenessSupported: true,
      prefsExtra: {
        'oc.phoneAgentsUsed.local': true,
        'oc.agentFeed.local': jsonEncode([
          {
            'sourceId': 'paseo:$_project',
            'sessionID': 'saved1',
            'title': 'Saved chat',
            'directory': _project,
            'projectName': 'app',
            'isGit': false,
            'at': DateTime(2026, 10, 3).millisecondsSinceEpoch,
            'agentId': 'claude',
            'agentLabel': 'Claude Code',
            'canReopen': true,
          },
        ]),
      },
    );
    final c = w.controller;
    try {
      w.state.runtimes['claude'] = stopped;
      w.state.agents = [_agent('c1', _project)];
      w.state.failOpens = 1000;
      await c.rememberLastUsedProject(_project);
      await c.refreshChatFeed();
      await c.refreshAgentRows();
      await tester.pump();
      expect(w.events.log, contains('host.start'));
      await tester.pump(const Duration(seconds: 3));
      expect(
        c.agentStatusLines.where(
          (l) => l.kind == PhoneAgentStatusLineKind.stopped,
        ),
        isEmpty,
      );
      expect(c.chatFeed().complete, isTrue);
      await tester.pump(const Duration(seconds: 11));
      w.state.helperRunning = true;
      w.state.failOpens = 0;
      // No navigation or pull to refresh: the startup poll must reconcile.
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      expect(
        c.agentRows.firstWhere((r) => r.id == 'claude').status,
        isNot(PhoneAgentStatus.stoppedInBackground),
      );
      expect(
        c.agentStatusLines.where(
          (l) => l.kind == PhoneAgentStatusLineKind.stopped,
        ),
        isEmpty,
      );
      expect(c.chatFeed().items.map((i) => i.sessionID), contains('c1'));
      expect(c.chatFeed().complete, isTrue);
    } finally {
      c.dispose();
    }
  });

  testWidgets(
    'cold start helper already up overrides a stale stopped process row',
    (tester) async {
      final w = await _world(tester, livenessSupported: true);
      try {
        w.state.runtimes['claude'] = stopped;
        w.state.helperRunning = true;
        w.state.failOpens = 1000;
        await w.controller.rememberLastUsedProject(_project);
        await w.controller.refreshChatFeed();
        await w.controller.refreshAgentRows();
        await tester.pump();
        expect(w.controller.agentStatusLines, isEmpty);
        expect(
          w.controller.agentRows.firstWhere((r) => r.id == 'claude').status,
          isNot(PhoneAgentStatus.stoppedInBackground),
        );
        expect(w.events.log, isNot(contains('host.start')));
        expect(w.controller.chatFeed().complete, isTrue);
      } finally {
        w.controller.dispose();
      }
    },
  );

  testWidgets('cold start busy response preserves the automatic start window', (
    tester,
  ) async {
    final w = await _world(tester, livenessSupported: true);
    final firstStart = Completer<void>();
    var calls = 0;
    try {
      w.state.runtimes['claude'] = stopped;
      w.state.failOpens = 1000;
      w.state.startHandler = () async {
        if (++calls == 1) return firstStart.future;
        throw const AgentHostException(AgentHostFailure.busy);
      };
      await w.controller.refreshAgentRows();
      await tester.pump();
      expect(calls, 1);
      await expectLater(
        w.controller.resumeAgentHost(),
        throwsA(isA<AgentHostException>()),
      );
      firstStart.complete();
      await tester.pump();
      await tester.pump(const Duration(seconds: 3));
      expect(w.controller.agentStatusLines, isEmpty);
    } finally {
      if (!firstStart.isCompleted) firstStart.complete();
      w.controller.dispose();
    }
  });

  testWidgets('cold start timeout still exposes a genuinely stopped helper', (
    tester,
  ) async {
    final w = await _world(tester, livenessSupported: true);
    try {
      w.state.runtimes['claude'] = stopped;
      w.state.failOpens = 1000;
      await w.controller.refreshAgentRows();
      await tester.pump();
      await tester.pump(const Duration(seconds: 31));
      expect(
        w.controller.agentStatusLines.map((l) => l.kind),
        contains(PhoneAgentStatusLineKind.stopped),
      );
    } finally {
      w.controller.dispose();
    }
  });

  testWidgets('cold start Paseo metadata read survives its own status event', (
    tester,
  ) async {
    final w = await _world(tester);
    try {
      w.state.runtimes['claude'] = _ready('claude');
      w.state.agents = [_agent('c1', _project)];
      await w.controller.rememberLastUsedProject(_project);
      await w.controller.refreshAgentRows();
      await w.controller.refreshChatFeed();
      await tester.pump(const Duration(milliseconds: 100));
      final item = w.controller.chatFeed().items.firstWhere(
        (i) => i.sessionID == 'c1',
      );
      await w.controller.openChatFeedItem(item);
      final backend = w.controller.backendForConversation('c1')!;
      await backend.ensureSession('c1');
      await tester.pump();
      expect(backend.selectionForSession('c1').modelKnown, isTrue);
      expect(backend.modelForSession('c1')?.modelID, 'claude-model');
    } finally {
      w.controller.dispose();
    }
  });
}
