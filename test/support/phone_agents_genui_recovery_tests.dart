part of '../phone_agents_controller_test.dart';

class _RecoveryCardGateway extends _ListCardGateway {
  _RecoveryCardGateway(super.transport, super.folder, super.state) {
    cardHistory.addAll(history);
  }

  final cardHistory = <MessageWithParts>[];

  final channelStatuses = <void Function(StreamStatus)>[];
  final channelEvents = <void Function(EventEnvelope)>[];

  /// The real helper loads the agent to answer its first history read, and
  /// the agent sends session events while it loads; once loaded it is quiet.
  bool eventOnRead = false;
  final _loaded = <String>{};

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async {
    if (eventOnRead && _loaded.add(sessionID)) {
      for (final onEvent in List.of(channelEvents)) {
        onEvent(
          EventEnvelope(
            type: 'session.updated',
            properties: {
              'info': {'id': sessionID},
            },
          ),
        );
      }
    }
    return super.genUiHistoryPage(sessionID, cursor: cursor, limit: limit);
  }

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) {
    channelStatuses.add(onStatus);
    channelEvents.add(onEvent);
    return super.openEventChannel(
      onEvent: onEvent,
      onStatus: onStatus,
      onError: onError,
    );
  }

  void reconnectChannels() {
    for (final onStatus in List.of(channelStatuses)) {
      onStatus(StreamStatus.connecting);
      onStatus(StreamStatus.connected);
    }
  }
}

Future<_World> _recoveryWorld(
  WidgetTester tester, {
  Map<String, Object> prefs = const {},
  String status = 'idle',
  bool card = false,
  bool eventOnRead = false,
  bool awake = true,
}) async {
  final w = await _world(
    tester,
    genUiInstaller: _CardInstaller(),
    prefsExtra: prefs,
  );
  w.state.gatewayFactory = (transport, folder) {
    final gateway = _RecoveryCardGateway(transport, folder, w.state)
      ..eventOnRead = eventOnRead
      ..awake = awake;
    if (!card) gateway.history.clear();
    return gateway;
  };
  w.state.runtimes = {'claude': _ready('claude')};
  w.state.agents = [_agent('c1', _project, status: status)];
  await w.controller.rememberLastUsedProject(_project);
  await w.controller.refreshAgentRows();
  await w.controller.refreshChatFeed();
  await w.controller.setGenUiEnabled(true);
  await _drainRecovery(tester);
  return w;
}

Future<void> _drainRecovery(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump(const Duration(seconds: 3));
}

void _genUiRecoveryCoverageTests() {
  for (final trigger in ['feed refresh', 'setup', 'reconnect']) {
    testWidgets('covered idle Paseo history is skipped on $trigger', (
      tester,
    ) async {
      final w = await _recoveryWorld(tester);
      final c = w.controller;
      final gateway = w.host.gateways.first as _RecoveryCardGateway;
      final coveredReads = gateway.historyReads;
      expect(coveredReads, greaterThan(0));
      switch (trigger) {
        case 'feed refresh':
          await c.refreshChatFeed();
        case 'setup':
          await c.setGenUiEnabled(true);
        case 'reconnect':
          gateway.reconnectChannels();
      }
      await _drainRecovery(tester);
      expect(gateway.historyReads, coveredReads);
      expect(c.waitingCardsForFeedItem(c.chatFeed().items.single), isEmpty);
      await _finish(tester, c);
    });
  }

  for (final trigger in ['feed refresh', 'setup', 'reconnect']) {
    testWidgets('a sleeping Paseo agent is never read on $trigger', (
      tester,
    ) async {
      // Reading history wakes a stored agent: a provider process starts and
      // its updatedAt jumps to now (incident 2026-10-27).
      final w = await _recoveryWorld(tester, awake: false);
      final c = w.controller;
      final gateway = w.host.gateways.first as _RecoveryCardGateway;
      switch (trigger) {
        case 'feed refresh':
          await c.refreshChatFeed();
        case 'setup':
          await c.setGenUiEnabled(true);
        case 'reconnect':
          gateway.reconnectChannels();
      }
      await _drainRecovery(tester);
      expect(gateway.historyReads, 0);
      await _finish(tester, c);
    });
  }

  testWidgets('loading an idle agent for its history still covers it', (
    tester,
  ) async {
    final w = await _recoveryWorld(tester, eventOnRead: true);
    final c = w.controller;
    final gateway = w.host.gateways.first as _RecoveryCardGateway;
    expect(gateway.historyReads, greaterThan(0));
    expect(c.store.prefs.containsKey('oc.genui.covered.local'), isTrue);
    final coveredReads = gateway.historyReads;
    await c.refreshChatFeed();
    await _drainRecovery(tester);
    expect(gateway.historyReads, coveredReads);
    expect(c.chatFeed().complete, isTrue);
    await _finish(tester, c);
  });

  testWidgets('a chat whose history holds a card is never covered', (
    tester,
  ) async {
    // Events from loading the agent arrive during the read, as on a phone.
    final w = await _recoveryWorld(tester, card: true, eventOnRead: true);
    final c = w.controller;
    final gateway = w.host.gateways.first as _RecoveryCardGateway;
    expect(c.waitingCardsForFeedItem(c.chatFeed().items.single), hasLength(1));
    expect(c.store.prefs.containsKey('oc.genui.covered.local'), isFalse);
    final reads = gateway.historyReads;
    await c.refreshChatFeed();
    await _drainRecovery(tester);
    expect(gateway.historyReads, greaterThan(reads));
    expect(c.waitingCardsForFeedItem(c.chatFeed().items.single), hasLength(1));
    await _finish(tester, c);
  });

  testWidgets('new prompt in an idle Paseo agent recovers its waiting card', (
    tester,
  ) async {
    final w = await _recoveryWorld(tester);
    final c = w.controller;
    final gateway = w.host.gateways.first as _RecoveryCardGateway;
    final coveredReads = gateway.historyReads;
    gateway.history.addAll(gateway.cardHistory);
    w.state.agents = [
      {...w.state.agents.single, 'lastUserMessageAt': '2026-10-26T10:00:00Z'},
    ];
    await c.refreshChatFeed();
    await _drainRecovery(tester);
    expect(gateway.historyReads, greaterThan(coveredReads));
    expect(c.waitingCardsForFeedItem(c.chatFeed().items.single), hasLength(1));
    await _finish(tester, c);
  });

  testWidgets('running Paseo history is recovered despite a covered prompt', (
    tester,
  ) async {
    final w = await _recoveryWorld(tester);
    final c = w.controller;
    final gateway = w.host.gateways.first as _RecoveryCardGateway;
    final coveredReads = gateway.historyReads;
    w.state.agents = [_agent('c1', _project, status: 'running')];
    await c.refreshChatFeed();
    await _drainRecovery(tester);
    expect(gateway.historyReads, greaterThan(coveredReads));
    final runningReads = gateway.historyReads;
    await c.refreshChatFeed();
    await _drainRecovery(tester);
    expect(gateway.historyReads, greaterThan(runningReads));
    await _finish(tester, c);
  });

  testWidgets('idle Paseo coverage survives controller restart', (
    tester,
  ) async {
    final first = await _recoveryWorld(tester);
    final prefs = first.controller.store.prefs;
    final saved = {
      'oc.genui.covered.local': prefs.getString('oc.genui.covered.local')!,
    };
    await _finish(tester, first.controller);
    final second = await _recoveryWorld(tester, prefs: saved);
    final gateway = second.host.gateways
        .whereType<_RecoveryCardGateway>()
        .single;
    expect(gateway.historyReads, 0);
    await second.controller.refreshChatFeed();
    await _drainRecovery(tester);
    expect(gateway.historyReads, 0);
    await _finish(tester, second.controller);
  });

  testWidgets('profile deletion sweeps persisted Paseo card coverage', (
    tester,
  ) async {
    final w = await _recoveryWorld(tester);
    final c = w.controller;
    expect(c.store.prefs.containsKey('oc.genui.covered.local'), isTrue);
    // Deletion itself needs real secure storage; its sweep is what finds the
    // key ('deletion closes auth, setup, host and feeds before ProfileStore').
    expect(
      c.store.profileScopedPreferenceKeys('local'),
      contains('oc.genui.covered.local'),
    );
    await _finish(tester, c);
  });

  testWidgets('journaled waiting card survives controller restart', (
    tester,
  ) async {
    final first = await _recoveryWorld(tester, card: true);
    expect(
      first.controller.waitingCardsForFeedItem(
        first.controller.chatFeed().items.single,
      ),
      hasLength(1),
    );
    final prefs = first.controller.store.prefs;
    // Only the card journal and coverage carry over, so the second world
    // builds its own fakes before any agent feed starts.
    final saved = {
      for (final key in ['oc.genui.journal.local', 'oc.genui.covered.local'])
        key: ?prefs.getString(key),
    };
    expect(saved, contains('oc.genui.journal.local'));
    await _finish(tester, first.controller);
    final second = await _recoveryWorld(tester, prefs: saved, card: true);
    final gateway = second.host.gateways
        .whereType<_RecoveryCardGateway>()
        .single;
    expect(gateway.historyReads, greaterThan(0));
    expect(
      second.controller.waitingCardsForFeedItem(
        second.controller.chatFeed().items.single,
      ),
      hasLength(1),
    );
    await _finish(tester, second.controller);
  });
}

Future<void> _finish(WidgetTester tester, ConnectionController c) async {
  c.dispose();
  await tester.pump();
}
