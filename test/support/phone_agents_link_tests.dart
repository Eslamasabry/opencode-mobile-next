part of '../phone_agents_controller_test.dart';

/// A helper that answers its own health check (`GET /api/health`).
class _HealthHost extends _FakeHost implements PhoneAgentDaemonHealthPort {
  _HealthHost(super.events, super.profileId, super.state);
  @override
  Future<bool> daemonAnswers() async => state.daemonAnswers;
}

void _phoneAgentLinkTests() {
  /// The agent chat [c1], open on its own connection, mid-turn.
  Future<(_World, ConnectionController)> openRunningChat(
    WidgetTester tester, {
    required bool Function() holdLists,
  }) async {
    final w = await _world(tester, healthSupported: true);
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('c1', _project, status: 'running')];
    w.state.freshPrivateSockets = true;
    // While [holdLists] the helper leaves agent-list reads unanswered (the
    // app's request times out); every other request is answered.
    w.state.configureSocket = (socket) {
      final list = socket.handlers['fetch_agents_request']!;
      socket.handlers['fetch_agents_request'] = (request) =>
          holdLists() ? null : list(request);
    };
    final c = w.controller;
    await c.rememberLastUsedProject(_project);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final row = c.chatFeed().items.firstWhere(
      (r) => r.sourceId == 'paseo:$_project',
    );
    await c.openChatFeedItem(row);
    final backend = c.backendForConversation('c1')!;
    await tester.pump(const Duration(milliseconds: 100));
    expect(backend.isConnected, isTrue);
    expect(backend.busySessions, contains('c1'));
    return (w, backend);
  }

  /// What the connection line says about [backend] right now.
  Future<String?> bannerWords(
    WidgetTester tester,
    ConnectionController backend,
  ) async {
    String? words;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            words = connectionKitStatus(context, backend)?.message;
            return const SizedBox();
          },
        ),
      ),
    );
    return words;
  }

  void dropEveryLink(_World w) {
    for (final host in w.hosts) {
      for (final socket in host.sockets) {
        unawaited(socket.close());
      }
    }
  }

  testWidgets('a tool call silent for minutes while the helper answers never '
      'reads as not answering, and the link comes back without a tap', (
    tester,
  ) async {
    var holding = false;
    final (w, backend) = await openRunningChat(
      tester,
      holdLists: () => holding,
    );
    final c = w.controller;
    try {
      // A long command (`sleep 590`) runs: no turn events for minutes, and
      // the helper leaves list reads unanswered until the turn ends. The
      // link drops once; the helper's health check keeps answering.
      holding = true;
      dropEveryLink(w);
      final seen = <String>[];
      for (var second = 0; second < 300; second += 5) {
        await tester.pump(const Duration(seconds: 5));
        final status = backend.connectionStatus;
        final words = await bannerWords(tester, backend);
        if (status.visible) seen.add('$second s: ${status.phase} "$words"');
        expect(
          status.visible && status.phase == ConnectionStatusPhase.notAnswering,
          isFalse,
          reason: seen.join('\n'),
        );
        expect(words ?? '', isNot(contains("isn't answering")));
        if (second >= 20) {
          // The helper answered the link's hello: the link is up.
          expect(backend.isConnected, isTrue, reason: 'at $second s');
          expect(backend.api, isNotNull);
        }
      }
      // The turn ends: the helper says so on the live link, answers list
      // reads again, and the chat goes on.
      holding = false;
      w.state.agents = [_agent('c1', _project)];
      for (final socket in [
        for (final host in w.hosts) ...host.sockets,
        ...w.state.reconnectSockets,
      ]) {
        socket.push('agent_stream', {
          'agentId': 'c1',
          'event': {'type': 'turn_completed'},
          'timestamp': _stamp,
        });
      }
      for (var i = 0; i < 20 && backend.busySessions.contains('c1'); i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      expect(backend.isConnected, isTrue);
      expect(backend.busySessions, isNot(contains('c1')));
      expect(backend.connectionStatus.visible, isFalse);
    } finally {
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      await tester.pump(const Duration(seconds: 3));
    }
  });

  testWidgets('a helper that answers its health check but takes no new link '
      'for a minute reads as reconnecting, then reconnects without a tap', (
    tester,
  ) async {
    final (w, backend) = await openRunningChat(tester, holdLists: () => false);
    final c = w.controller;
    try {
      w.state.refuseSockets = true;
      w.state.failOpens = 1000;
      dropEveryLink(w);
      for (var second = 0; second < 60; second += 5) {
        await tester.pump(const Duration(seconds: 5));
        final status = backend.connectionStatus;
        expect(
          status.visible && status.phase == ConnectionStatusPhase.notAnswering,
          isFalse,
          reason: 'at $second s',
        );
        if (status.visible) {
          expect(status.waiting, isTrue, reason: 'at $second s');
          expect(await bannerWords(tester, backend), 'Reconnecting…');
        }
      }
      w.state.refuseSockets = false;
      w.state.failOpens = 0;
      for (var i = 0; i < 10 && !backend.isConnected; i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      expect(backend.isConnected, isTrue);
      expect(backend.connectionStatus.visible, isFalse);
    } finally {
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      await tester.pump(const Duration(seconds: 3));
    }
  });

  testWidgets('a helper too loaded to answer its health check while a command '
      'runs reads as reconnecting, for as long as a command may run', (
    tester,
  ) async {
    final (w, backend) = await openRunningChat(tester, holdLists: () => false);
    final c = w.controller;
    try {
      // An APK build on this phone: the agent's Bash call runs, and the
      // phone is too busy for the helper to answer its health check in
      // time.
      backend.handleEventForTesting(
        EventEnvelope(
          type: 'message.part.updated',
          properties: {
            'sessionID': 'c1',
            'part': {
              'id': 'prt_build',
              'type': 'tool',
              'sessionID': 'c1',
              'tool': 'bash',
              'state': {'status': 'running'},
            },
          },
        ),
      );
      expect(backend.hasRunningTool, isTrue);
      backend.agentCommandHoldOff = const Duration(minutes: 3);
      w.state.daemonAnswers = false;
      w.state.refuseSockets = true;
      w.state.failOpens = 1000;
      dropEveryLink(w);
      for (var second = 0; second < 170; second += 10) {
        await tester.pump(const Duration(seconds: 10));
        expect(
          backend.connectionStatus.phase,
          isNot(ConnectionStatusPhase.notAnswering),
          reason: 'at $second s',
        );
      }
      expect(
        await bannerWords(tester, backend),
        isNot(contains("isn't answering")),
      );
      // Past the longest a command may run: a helper that still says
      // nothing is named.
      for (
        var second = 0;
        second < 300 &&
            backend.connectionStatus.phase !=
                ConnectionStatusPhase.notAnswering;
        second += 10
      ) {
        await tester.pump(const Duration(seconds: 10));
      }
      expect(
        backend.connectionStatus.phase,
        ConnectionStatusPhase.notAnswering,
      );
      // The build ends and the helper answers again: no tap needed.
      w.state.daemonAnswers = true;
      w.state.refuseSockets = false;
      w.state.failOpens = 0;
      for (var i = 0; i < 12 && !backend.isConnected; i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      expect(backend.isConnected, isTrue);
    } finally {
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      await tester.pump(const Duration(seconds: 3));
    }
  });

  testWidgets('a helper that really stops answering is named, offers Restart, '
      'and is reconnected by itself once it answers again', (tester) async {
    final (w, backend) = await openRunningChat(tester, holdLists: () => false);
    final c = w.controller;
    try {
      w.state.daemonAnswers = false;
      w.state.refuseSockets = true;
      w.state.failOpens = 1000;
      dropEveryLink(w);
      await tester.pump(const Duration(seconds: 20));
      expect(
        backend.connectionStatus.phase,
        ConnectionStatusPhase.notAnswering,
      );
      expect(backend.recoverAgentBackend, isNotNull);
      expect(
        await bannerWords(tester, backend),
        "Claude Code on this phone isn't answering",
      );
      // The helper is back: no tap needed.
      w.state.daemonAnswers = true;
      w.state.refuseSockets = false;
      w.state.failOpens = 0;
      for (var i = 0; i < 12 && !backend.isConnected; i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      expect(backend.isConnected, isTrue);
      expect(backend.connectionStatus.visible, isFalse);
    } finally {
      await tester.pumpWidget(const SizedBox());
      c.dispose();
      await tester.pump(const Duration(seconds: 3));
    }
  });
}
