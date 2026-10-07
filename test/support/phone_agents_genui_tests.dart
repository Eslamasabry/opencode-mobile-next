part of '../phone_agents_controller_test.dart';

class _ListCardGateway extends PaseoGateway {
  _ListCardGateway(PaseoTransport transport, String folder, this.state)
    : super(transport: transport, directory: folder);

  final _HostState state;
  int listReads = 0;
  Completer<void>? pauseList;
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    listReads++;
    final page = await super.sessionPage(cursor: cursor, limit: limit);
    await pauseList?.future;
    return page;
  }

  final history = <MessageWithParts>[
    MessageWithParts(
      info: MessageInfo(id: 'assistant', sessionID: 'c1', role: 'assistant'),
      parts: [
        Part(
          id: 'card-part',
          type: 'tool',
          messageID: 'assistant',
          callID: 'card-call',
          toolName: 'mcp__oc-ui__show',
          toolState: ToolState(
            status: 'completed',
            input: {
              'v': 1,
              'id': 'continue',
              'title': 'Continue?',
              'body': [],
              'ask': {'kind': 'confirm'},
            },
          ),
        ),
      ],
    ),
  ];

  @override
  Future<bool> genUiSessionIdle(String sessionID) async => true;

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async => GenUiHistoryPage(
    items: sessionID == 'c1' ? List.of(history) : [],
    hasMore: false,
  );

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    beforeSend?.call();
    history.add(
      MessageWithParts(
        info: MessageInfo(id: messageID, sessionID: sessionID, role: 'user'),
        parts: [
          Part(id: 'answer', type: 'text', messageID: messageID, text: text),
        ],
      ),
    );
    state.agents = [
      for (final a in state.agents)
        if (a['id'] == sessionID)
          {...a, 'updatedAt': '2026-10-07T10:00:00Z', 'status': 'running'}
        else
          a,
    ];
  }
}

void _genUiFeedRefreshTests() {
  testWidgets(
    'list card answer refreshes activity and completion without opening chat',
    (tester) async {
      final w = await _world(tester, genUiInstaller: _CardInstaller());
      final c = w.controller;
      w.state.gatewayFactory = (transport, folder) =>
          _ListCardGateway(transport, folder, w.state);
      w.state.runtimes = {'claude': _ready('claude')};
      w.state.agents = [
        {..._agent('c1', _project), 'updatedAt': '2026-10-07T08:00:00Z'},
        {..._agent('newer', _project), 'updatedAt': '2026-10-07T09:00:00Z'},
      ];
      await c.rememberLastUsedProject(_project);
      await c.refreshAgentRows();
      await c.refreshChatFeed();
      await c.setGenUiEnabled(true);
      await tester.pump(const Duration(milliseconds: 400));
      ChatFeedItem row() =>
          c.chatFeed().items.firstWhere((r) => r.sessionID == 'c1');
      expect(c.backendForConversation('c1'), isNull);
      final gateway = w.host.gateways.first as _ListCardGateway;
      final beforeUndo = gateway.listReads;
      final card = c.waitingCardsForFeedItem(row()).single;
      final undone = c.answerGenUi(card, const GenUiConfirmAnswer(true));
      c.undoGenUiAnswer(card);
      await undone;
      await tester.pump(const Duration(seconds: 3));
      expect(
        row().lastActivity.toUtc(),
        DateTime.parse('2026-10-07T08:00:00Z'),
      );

      expect(gateway.listReads, beforeUndo);

      final beforeAnswer = gateway.listReads;
      final answer = c.answerGenUi(card, const GenUiConfirmAnswer(true));
      await c.delayedAnswers.flush();
      await answer;
      await tester.pump(const Duration(seconds: 3));
      expect(
        row().lastActivity.toUtc(),
        DateTime.parse('2026-10-07T10:00:00Z'),
      );
      expect(row().status, ChatStatus.running);
      expect(gateway.listReads, beforeAnswer + 1);
      expect(c.chatFeed().items.first.sessionID, 'c1');

      final beforeCompletion = gateway.listReads;
      final socket = w.host.sockets.first;
      socket.push('agent_stream', {
        'agentId': 'c1',
        'event': {'type': 'turn_started'},
      });
      await tester.pump();
      w.state.agents = [
        for (final a in w.state.agents)
          if (a['id'] == 'c1')
            {...a, 'updatedAt': '2026-10-07T10:02:00Z', 'status': 'idle'}
          else
            a,
      ];
      socket.push('agent_stream', {
        'agentId': 'c1',
        'event': {'type': 'turn_completed'},
      });
      await tester.pump();
      await tester.pump(const Duration(seconds: 3));
      expect(
        row().lastActivity.toUtc(),
        DateTime.parse('2026-10-07T10:02:00Z'),
      );
      expect(row().status, ChatStatus.idle);
      expect(gateway.listReads, beforeCompletion + 1);
      expect(c.backendForConversation('c1'), isNull);

      final beforeTokens = gateway.listReads;
      for (var i = 0; i < 20; i++) {
        c.handleEventForTesting(
          EventEnvelope(
            type: 'message.part.delta',
            properties: {'sessionID': 'c1', 'delta': 'token'},
          ),
        );
      }
      await tester.pump(const Duration(seconds: 3));
      expect(gateway.listReads, beforeTokens);

      // A completion during a preexisting read must not join its old snapshot.
      final pause = Completer<void>();
      gateway.pauseList = pause;
      final inFlight = c.refreshChatFeed();
      await tester.pump();
      w.state.agents = [
        for (final a in w.state.agents)
          if (a['id'] == 'c1')
            {...a, 'updatedAt': '2026-10-07T10:03:00Z'}
          else
            a,
      ];
      socket.push('agent_stream', {
        'agentId': 'c1',
        'event': {'type': 'turn_completed'},
      });
      await tester.pump();
      await tester.pump(const Duration(seconds: 3));
      gateway.pauseList = null;
      pause.complete();
      await inFlight;
      await tester.pump();
      await tester.pump(const Duration(seconds: 3));
      expect(
        row().lastActivity.toUtc(),
        DateTime.parse('2026-10-07T10:03:00Z'),
      );
      c.dispose();
      await tester.pump();
    },
  );
}
