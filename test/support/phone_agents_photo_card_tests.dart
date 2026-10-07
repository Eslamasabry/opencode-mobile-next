part of '../phone_agents_controller_test.dart';

const _cardPhotoData =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';
const _cardPhoto = PromptAttachment(
  mime: 'image/png',
  filename: 'evidence.png',
  url: 'data:image/png;base64,$_cardPhotoData',
);

class _PhotoCardHistory {
  String? daemonID;
  final answers = <Map<String, dynamic>>[];

  List<MessageWithParts> messages(String sessionID) => [
    MessageWithParts(
      info: MessageInfo(
        id: 'photo-assistant',
        sessionID: sessionID,
        role: 'assistant',
      ),
      parts: [
        Part(
          id: 'photo-part',
          messageID: 'photo-assistant',
          type: 'tool',
          callID: 'photo-call',
          toolName: 'mcp__oc-ui__show',
          toolState: ToolState(
            status: 'completed',
            input: {
              'v': 1,
              'id': 'photo-evidence',
              'title': 'Show the result',
              'body': [],
              'ask': {'kind': 'photo', 'purpose': 'Show the result', 'max': 1},
            },
          ),
        ),
      ],
    ),
    for (final answer in answers)
      MessageWithParts(
        info: MessageInfo(
          id: answer['messageId'] as String,
          sessionID: sessionID,
          role: 'user',
        ),
        parts: [
          Part(type: 'text', text: answer['text'] as String),
          for (final image in answer['images'] as List)
            Part.fromJson({
              'type': 'file',
              'mime': (image as Map)['mimeType'],
              'url': 'data:${image['mimeType']};base64,${image['data']}',
            }),
        ],
      ),
  ];
}

/// Keep both prompt entry points real: only history/idle are synthetic.
class _PhotoCardGateway extends PaseoGateway {
  _PhotoCardGateway(PaseoTransport transport, String folder, this.history)
    : super(transport: transport, directory: folder);

  final _PhotoCardHistory history;

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      daemonSessionId(id) == history.daemonID ? history.messages(id) : [];

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async =>
      GenUiHistoryPage(items: await messages(sessionID), hasMore: false);

  @override
  Future<bool> genUiSessionIdle(String sessionID) async => true;
}

Future<
  ({
    _World world,
    ConnectionController backend,
    ChatFeedItem row,
    GenUiCard chatCard,
    GenUiCard listCard,
    FakePaseoSocket socket,
    _PhotoCardHistory history,
    String daemonID,
  })
>
_photoCardAliasSetup({bool failAfterDispatch = false}) async {
  final w = await _world(null, genUiInstaller: _CardInstaller());
  addTearDown(w.controller.dispose);
  final history = _PhotoCardHistory();
  w.state.gatewayFactory = (transport, folder) =>
      _PhotoCardGateway(transport, folder, history);
  w.state.runtimes = {'claude': _ready('claude')};
  final c = w.controller;
  await c.rememberLastUsedProject(_project);
  await c.refreshAgentRows();
  await c.refreshChatFeed();
  final daemonID = await c.startAgentChatIn(
    _project,
    agentId: 'claude',
    firstPrompt: 'Ask for a photo of the result.',
  );
  history.daemonID = daemonID;
  await c.setGenUiEnabled(true);
  await c.refreshChatFeed();
  // Feed history is intentionally queued behind a 350 ms debounce. These
  // are real-async tests; pumpEventQueue alone does not advance that timer.
  await Future<void>.delayed(const Duration(milliseconds: 400));
  await pumpEventQueue();
  final feedIndex = w.host.gateways.indexWhere((gateway) {
    return c.chatFeed().items.any(
      (row) =>
          row.sourceId == 'paseo:$_project' &&
          row.sessionID != daemonID &&
          gateway.daemonSessionId(row.sessionID) == daemonID,
    );
  });
  expect(feedIndex, greaterThanOrEqualTo(0));
  final feed = w.host.gateways[feedIndex];
  final row = c.chatFeed().items.singleWhere(
    (row) =>
        row.sourceId == 'paseo:$_project' &&
        feed.daemonSessionId(row.sessionID) == daemonID,
  );
  expect(row.sessionID, isNot(daemonID));
  final backend = c.backendForConversation(daemonID)!;
  await backend.loadSessionTail(daemonID);
  await pumpEventQueue();
  final chatCard = backend.waitingCardsForSession(daemonID).single;
  final listCard = c.waitingCardsForFeedItem(row).single;
  final socket = w.host.sockets[feedIndex];
  socket.handlers['send_agent_message_request'] = (request) {
    if (failAfterDispatch) {
      return ('rpc_error', {'error': 'Synthetic failure after dispatch'});
    }
    history.answers.add(Map<String, dynamic>.of(request));
    return ('send_agent_message_response', <String, dynamic>{});
  };
  return (
    world: w,
    backend: backend,
    row: row,
    chatCard: chatCard,
    listCard: listCard,
    socket: socket,
    history: history,
    daemonID: daemonID,
  );
}

void _photoCardAliasTests() {
  _livePhotoReceiptTests();
  test(
    'photo card on a new Claude chat sends bytes through the real gateway',
    () async {
      final x = await _photoCardAliasSetup();
      final c = x.world.controller;
      final creates = x.socket.of('create_agent_request').length;
      final resumes = List<String>.of(x.world.state.resumed);
      final sending = x.backend.answerGenUi(
        x.chatCard,
        const GenUiPhotoAnswer(1),
        attachments: [_cardPhoto],
      );
      await c.delayedAnswers.flush();
      await sending;
      final sent = x.socket.of('send_agent_message_request').single;
      expect(sent['agentId'], x.daemonID);
      expect(sent['images'], [
        {'data': _cardPhotoData, 'mimeType': 'image/png'},
      ]);
      expect(sent['messageId'], isNotEmpty);
      expect(sent['text'], contains('[oc-ui answer photo-evidence]'));
      expect(x.socket.of('create_agent_request'), hasLength(creates));
      expect(x.world.state.resumed, resumes);
      expect(x.backend.genUiStateForCard(x.chatCard), GenUiCardState.answered);
      expect(c.genUiStateForCard(x.listCard), GenUiCardState.answered);
    },
  );

  test('photo card chat and list aliases share identity and Undo', () async {
    final x = await _photoCardAliasSetup();
    final c = x.world.controller;
    expect(x.chatCard.identity, x.listCard.identity);
    final held = x.backend.answerGenUi(
      x.chatCard,
      const GenUiPhotoAnswer(1),
      attachments: [_cardPhoto],
    );
    expect(c.genUiDeliveryFor(x.listCard), GenUiDeliveryState.held);
    c.undoGenUiAnswer(x.listCard);
    await held;
    await c.delayedAnswers.flush();
    expect(x.socket.of('send_agent_message_request'), isEmpty);
    expect(c.genUiDeliveryFor(x.listCard), GenUiDeliveryState.idle);
  });

  test(
    'photo card unconfirmed send cannot retry through the list alias',
    () async {
      final x = await _photoCardAliasSetup(failAfterDispatch: true);
      final c = x.world.controller;
      final sending = x.backend.answerGenUi(
        x.chatCard,
        const GenUiPhotoAnswer(1),
        attachments: [_cardPhoto],
      );
      final failed = expectLater(sending, throwsA(isA<ProductException>()));
      await c.delayedAnswers.flush();
      await failed;
      expect(x.socket.of('send_agent_message_request'), hasLength(1));
      expect(
        c.genUiDeliveryFor(x.listCard),
        GenUiDeliveryState.deliveryUnknown,
      );
      final retry = c.answerGenUi(
        x.listCard,
        const GenUiPhotoAnswer(1),
        attachments: [_cardPhoto],
      );
      final refused = expectLater(retry, throwsA(isA<ProductException>()));
      await c.delayedAnswers.flush();
      await refused;
      expect(x.socket.of('send_agent_message_request'), hasLength(1));
    },
  );

  test(
    'photo card keeps a legacy local-ID dispatch marker fail closed',
    () async {
      final x = await _photoCardAliasSetup();
      final c = x.world.controller;
      final key = 'oc.genui.journal.${x.chatCard.scope.profileID}';
      final saved = jsonDecode(c.store.prefs.getString(key)!) as Map;
      final entries = (saved['entries'] as List).cast<Map>();
      final reference = entries.firstWhere(
        (entry) =>
            entry['message'] == x.chatCard.messageID &&
            entry['call'] == x.chatCard.callID,
      );
      final legacy = {
        ...reference,
        'session': x.row.sessionID,
        'dispatch': 'legacy-photo-dispatch',
      };
      await c.store.prefs.setString(
        key,
        jsonEncode({
          'v': 1,
          'entries': [
            for (final entry in entries)
              if (entry['session'] != x.row.sessionID ||
                  entry['message'] != x.chatCard.messageID ||
                  entry['call'] != x.chatCard.callID)
                entry,
            legacy,
          ],
        }),
      );
      final answer = x.backend.answerGenUi(
        x.chatCard,
        const GenUiPhotoAnswer(1),
        attachments: [_cardPhoto],
      );
      final refused = expectLater(answer, throwsA(isA<ProductException>()));
      await c.delayedAnswers.flush();
      await refused;
      expect(x.socket.of('send_agent_message_request'), isEmpty);
      final after = jsonDecode(c.store.prefs.getString(key)!) as Map;
      expect(
        (after['entries'] as List).where(
          (entry) =>
              (entry as Map)['session'] == x.row.sessionID &&
              entry['dispatch'] == 'legacy-photo-dispatch',
        ),
        hasLength(1),
      );
    },
  );

  test(
    'photo card archive clears both chat and list session aliases',
    () async {
      final x = await _photoCardAliasSetup();
      final c = x.world.controller;
      expect(x.backend.waitingCardsForSession(x.daemonID), hasLength(1));
      expect(c.waitingCardsForFeedItem(x.row), hasLength(1));

      x.world.state.agents.removeWhere((agent) => agent['id'] == x.daemonID);
      x.socket.push('agent_archived', {'agentId': x.daemonID});
      await pumpEventQueue();

      expect(x.backend.waitingCardsForSession(x.daemonID), isEmpty);
      expect(c.waitingCardsForFeedItem(x.row), isEmpty);
      final answer = x.backend.answerGenUi(
        x.chatCard,
        const GenUiPhotoAnswer(1),
        attachments: [_cardPhoto],
      );
      final refused = expectLater(answer, throwsA(isA<ProductException>()));
      await c.delayedAnswers.flush();
      await refused;
      expect(x.socket.of('send_agent_message_request'), isEmpty);
    },
  );

  test(
    'photo card explicit delete clears both aliases without an event',
    () async {
      final x = await _photoCardAliasSetup();
      final c = x.world.controller;
      final index = x.world.host.gateways.indexWhere(
        (gateway) => identical(gateway, x.backend.api),
      );
      expect(index, greaterThanOrEqualTo(0));
      final socket = x.world.host.sockets[index];
      socket.handlers['archive_agent_request'] = (request) {
        expect(request['agentId'], x.daemonID);
        x.world.state.agents.removeWhere((agent) => agent['id'] == x.daemonID);
        return ('archive_agent_response', <String, dynamic>{});
      };

      await x.backend.deleteSession(x.daemonID);
      await pumpEventQueue();

      expect(socket.of('archive_agent_request'), hasLength(1));
      expect(x.backend.waitingCardsForSession(x.daemonID), isEmpty);
      expect(c.waitingCardsForFeedItem(x.row), isEmpty);
      expect(
        x.world.host.sockets.expand((s) => s.of('send_agent_message_request')),
        isEmpty,
      );
    },
  );
}

/// Wire fixtures only: sends, timeline mapping, idle checks and event forwarding
/// all use the real PaseoGateway. History deliberately never contains the reply.
class _LivePhotoWire {
  int historyReads = 0;
  final sends = <Map<String, dynamic>>[];

  void configure(FakePaseoSocket socket) {
    socket.handlers['fetch_agent_timeline_request'] = (request) {
      historyReads++;
      return (
        'fetch_agent_timeline_response',
        {
          'agentId': request['agentId'],
          'epoch': 'live-photo-epoch',
          'hasOlder': false,
          'hasNewer': false,
          'startCursor': {'epoch': 'live-photo-epoch', 'seq': 3},
          'endCursor': {'epoch': 'live-photo-epoch', 'seq': 3},
          'entries': [
            {
              'provider': 'claude',
              'seqStart': 3,
              'seqEnd': 3,
              'timestamp': _stamp,
              'item': {
                'type': 'tool_call',
                'callId': 'live-photo-call',
                'name': 'mcp__oc-ui__show',
                'status': 'completed',
                'detail': {
                  'type': 'unknown',
                  'input': {
                    'v': 1,
                    'id': 'photo-evidence',
                    'title': 'Show the result',
                    'body': [],
                    'ask': {
                      'kind': 'photo',
                      'purpose': 'Show the result',
                      'max': 1,
                    },
                  },
                },
              },
            },
          ],
        },
      );
    };
    socket.handlers['send_agent_message_request'] = (request) {
      sends.add(Map<String, dynamic>.of(request));
      return ('send_agent_message_response', {'accepted': true});
    };
    socket.handlers['agent.provider_subagents.list.request'] = (_) =>
        ('agent.provider_subagents.list.response', {'subagents': []});
  }
}

void _pushLivePhotoEvent(
  _World world,
  String daemonID,
  int seq,
  Map<String, dynamic> event,
) {
  for (final socket in world.host.sockets) {
    socket.push('agent_stream', {
      'agentId': daemonID,
      'epoch': 'live-photo-epoch',
      'seq': seq,
      'timestamp': _stamp,
      'event': event,
    });
  }
}

Future<
  ({
    _World world,
    _LivePhotoWire wire,
    ConnectionController backend,
    GenUiCard chatCard,
    GenUiCard listCard,
    String daemonID,
  })
>
_livePhotoSetup() async {
  final w = await _world(null, genUiInstaller: _CardInstaller());
  addTearDown(w.controller.dispose);
  final wire = _LivePhotoWire();
  w.state.freshPrivateSockets = true;
  w.state.configureSocket = wire.configure;
  w.state.runtimes = {'claude': _ready('claude')};
  final c = w.controller;
  await c.rememberLastUsedProject(_project);
  await c.refreshAgentRows();
  await c.refreshChatFeed();
  final id = await c.startAgentChatIn(
    _project,
    agentId: 'claude',
    firstPrompt: 'Ask for a photo.',
  );
  _pushLivePhotoEvent(w, id, 1, {'type': 'turn_started', 'provider': 'claude'});
  _pushLivePhotoEvent(w, id, 2, {
    'type': 'turn_completed',
    'provider': 'claude',
  });
  await pumpEventQueue();
  await c.setGenUiEnabled(true);
  await c.refreshChatFeed();
  await Future<void>.delayed(const Duration(milliseconds: 400));
  await pumpEventQueue();
  final backend = c.backendForConversation(id)!;
  await backend.loadSessionTail(id);
  await pumpEventQueue();
  final row = c.chatFeed().items.singleWhere(
    (r) =>
        r.sourceId == 'paseo:$_project' &&
        w.host.gateways.any((g) => g.daemonSessionId(r.sessionID) == id),
  );
  expect(row.sessionID, isNot(id));
  expect(w.host.gateways.every((g) => g.runtimeType == PaseoGateway), isTrue);
  return (
    world: w,
    wire: wire,
    backend: backend,
    chatCard: backend.waitingCardsForSession(id).single,
    listCard: c.waitingCardsForFeedItem(row).single,
    daemonID: id,
  );
}

void _livePhotoReceiptTests() {
  for (final correlation in ['messageId', 'clientMessageId']) {
    test(
      'live photo receipt settles while running using $correlation',
      () async {
        final x = await _livePhotoSetup();
        final c = x.world.controller;
        final sending = x.backend.answerGenUi(
          x.chatCard,
          const GenUiPhotoAnswer(1),
          attachments: [_cardPhoto],
        );
        await c.delayedAnswers.flush();
        await sending;
        expect(x.wire.sends, hasLength(1));
        final request = x.wire.sends.single;
        expect(request['agentId'], x.daemonID);
        // An accepted RPC alone cannot settle a card.
        expect(c.genUiStateForCard(x.listCard), isNot(GenUiCardState.answered));
        expect(c.genUiAnswerSummary(x.listCard), isNull);
        expect(
          c.genUiDeliveryFor(x.listCard),
          GenUiDeliveryState.deliveryUnknown,
        );

        for (final agent in x.world.state.agents) {
          if (agent['id'] == x.daemonID) agent['status'] = 'running';
        }
        _pushLivePhotoEvent(x.world, x.daemonID, 4, {
          'type': 'turn_started',
          'provider': 'claude',
        });
        _pushLivePhotoEvent(x.world, x.daemonID, 5, {
          'type': 'timeline',
          'provider': 'claude',
          'item': {
            'type': 'user_message',
            'text': request['text'],
            if (correlation == 'messageId') 'messageId': request['messageId'],
            if (correlation == 'clientMessageId') ...{
              'messageId': 'server-photo-receipt',
              'clientMessageId': request['messageId'],
            },
          },
        });
        await pumpEventQueue();
        expect(x.backend.busySessions, contains(x.daemonID));
        expect(
          x.backend.genUiStateForCard(x.chatCard),
          GenUiCardState.answered,
        );
        expect(c.genUiStateForCard(x.listCard), GenUiCardState.answered);
        expect(c.genUiAnswerSummary(x.listCard), isNotEmpty);

        final before = x.wire.historyReads;
        _pushLivePhotoEvent(x.world, x.daemonID, 6, {
          'type': 'timeline',
          'provider': 'claude',
          'item': {
            'type': 'assistant_message',
            'messageId': 'running-response',
            'text': 'Inspecting the photo',
          },
        });
        await Future<void>.delayed(const Duration(milliseconds: 450));
        await pumpEventQueue();
        expect(x.wire.historyReads, greaterThan(before));
        expect(x.backend.busySessions, contains(x.daemonID));
        expect(
          x.backend.genUiStateForCard(x.chatCard),
          GenUiCardState.answered,
        );
        expect(c.genUiStateForCard(x.listCard), GenUiCardState.answered);
        expect(x.wire.sends, hasLength(1));
      },
    );
  }

  for (final mismatch in [
    'correlation',
    'card',
    'call',
    'assistant',
    'missing ID',
  ]) {
    test(
      'live photo receipt rejects $mismatch without authoritative history',
      () async {
        final x = await _livePhotoSetup();
        final c = x.world.controller;
        final sending = x.backend.answerGenUi(
          x.chatCard,
          const GenUiPhotoAnswer(1),
          attachments: [_cardPhoto],
        );
        await c.delayedAnswers.flush();
        await sending;
        final request = x.wire.sends.single;
        var text = request['text'] as String;
        if (mismatch == 'card') {
          text = text.replaceAll('photo-evidence', 'another-card');
        }
        if (mismatch == 'call') {
          text = text.replaceAll('live-photo-call', 'another-call');
        }
        _pushLivePhotoEvent(x.world, x.daemonID, 4, {
          'type': 'turn_started',
          'provider': 'claude',
        });
        _pushLivePhotoEvent(x.world, x.daemonID, 5, {
          'type': 'timeline',
          'provider': 'claude',
          'item': {
            'type': mismatch == 'assistant'
                ? 'assistant_message'
                : 'user_message',
            'text': text,
            if (mismatch != 'missing ID') 'messageId': request['messageId'],
            if (mismatch == 'correlation')
              'clientMessageId': 'different-dispatch',
          },
        });
        await pumpEventQueue();
        await Future<void>.delayed(const Duration(milliseconds: 450));
        await pumpEventQueue();
        expect(
          x.backend.genUiStateForCard(x.chatCard),
          isNot(GenUiCardState.answered),
        );
        expect(c.genUiStateForCard(x.listCard), isNot(GenUiCardState.answered));
        expect(c.genUiAnswerSummary(x.listCard), isNull);
        expect(x.wire.sends, hasLength(1));
      },
    );
  }
}
