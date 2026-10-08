part of '../chat_live_events_test.dart';

// History, paging, revert, sub-agent and sibling navigation, leaving an
// empty session, offline and rehydrate.

void _historyAndNavigationTests() {
  MessageWithParts historyMessage(String id, [String? text]) => _message(
    id,
    'user',
    [Part(id: 'part-$id', messageID: id, type: 'text', text: text ?? id)],
  );

  testWidgets(
    'UXCHAT uncached child Open rejects a project change during fetch',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final parent = Session(id: 'session-1', title: 'Parent');
      final child = Session(
        id: 'child',
        parentID: parent.id,
        title: 'Child task',
      );
      final pending = Completer<Session>();
      final repo = _RelationsProductRepository(parent, [child])
        ..detailsHandler = (id) =>
            id == child.id ? pending.future : Future.value(parent);
      final conn = await _controller(api, savedProfile: true)
        ..sessionsById = {parent.id: parent};
      await _pumpChat(
        tester,
        api,
        repository: repo,
        controller: conn,
        reduceMotion: true,
      );
      expect(find.byKey(const Key('running-work-indicator')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('session-actions-button')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-subagents')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('work-agent-child')), findsOneWidget);
      await tester.tap(find.byKey(const Key('work-agent-child')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      conn.directory = '/other-project';
      conn.locationRevision++;
      conn.notifyListeners();
      pending.complete(child);
      await tester.pumpAndSettle();
      expect(
        tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
        parent.id,
      );
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('UXCHAT child Open rejects a superseded location selection', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final parent = Session(id: 'session-1', title: 'Parent');
    final child = Session(
      id: 'child',
      parentID: parent.id,
      title: 'Child task',
      directory: '/child-project',
    );
    final saved = await _controller(api, savedProfile: true);
    final conn = _DelayedLocationController(saved.store)
      ..api = api
      ..status = StreamStatus.connected
      ..sessionsById = {parent.id: parent, child.id: child};
    saved.dispose();
    await _pumpChat(
      tester,
      api,
      repository: _RelationsProductRepository(parent, [child]),
      controller: conn,
      reduceMotion: true,
    );
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Keep my draft',
    );
    await tester.tap(find.byKey(const ValueKey('session-actions-button')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-subagents')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('work-agent-child')));
    await tester.pumpAndSettle();
    expect(conn.selectionStarted, isTrue);
    conn.directory = '/competing-project';
    conn.locationRevision++;
    conn.notifyListeners();
    conn.selection.complete();
    await tester.pumpAndSettle();
    expect(
      tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
      parent.id,
    );
    expect(find.text('Keep my draft'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'UXCHAT every parent inbox delivery loads canonical server result without sending',
    (tester) async {
      final api = _FakeOpenCodeApi();
      var reads = 0;
      var delivered = 0;
      api.messagesHandler = (_) async {
        reads++;
        return [
          for (var index = 1; index <= delivered; index++)
            _message('inbox-$index', 'assistant', [
              Part(
                id: 'notice-$index',
                messageID: 'inbox-$index',
                type: 'v2:notice',
                toolName: 'synthetic',
                filename: 'Background result $index',
                text:
                    '<subagent sessionID="child" state="completed">\nServer result $index\n</subagent>',
              ),
            ], created: index),
        ];
      };
      final conn = await _pumpChat(tester, api, reduceMotion: true);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'My unsent draft',
      );
      final before = reads;
      conn.handleEventForTesting(
        _event('session.inbox.delivered', {
          'sessionID': 'other-parent',
          'inboxID': 'unrelated',
        }),
      );
      await tester.pumpAndSettle();
      expect(reads, before);
      for (var index = 1; index <= 2; index++) {
        delivered = index;
        final previousReads = reads;
        conn.handleEventForTesting(
          _event('session.inbox.delivered', {
            'sessionID': 'session-1',
            'inboxID': 'inbox-$index',
          }),
        );
        await tester.pump(const Duration(milliseconds: 200));
        await tester.pumpAndSettle();
        expect(reads, greaterThan(previousReads));
        expect(find.textContaining('Background result $index'), findsOneWidget);
      }
      expect(find.text('My unsent draft'), findsOneWidget);
      expect(api.promptCalls, 0);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'UXCHAT server-returned child result appears in parent without a client prompt',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final conn = await _pumpChat(tester, api, reduceMotion: true);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'My unsent draft',
      );
      api.messagesHandler = (_) async => [
        _message('server-result', 'assistant', [
          Part(
            id: 'server-result-text',
            messageID: 'server-result',
            type: 'text',
            text: 'The delegated keyboard review is ready.',
          ),
        ], created: 2),
      ];
      conn.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'server-result',
            'sessionID': 'session-1',
            'role': 'assistant',
            'time': {'created': 2},
          },
        }),
      );
      conn.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': {
            'id': 'server-result-text',
            'messageID': 'server-result',
            'sessionID': 'session-1',
            'type': 'text',
            'text': 'The delegated keyboard review is ready.',
          },
        }),
      );
      await _pumpEvent(tester);
      await tester.pumpAndSettle();
      expect(
        find.text('The delegated keyboard review is ready.'),
        findsOneWidget,
      );
      expect(find.text('My unsent draft'), findsOneWidget);
      expect(api.promptCalls, 0);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('UXCHAT v2 subagent tools are summarized as delegated tasks', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('delegation', 'assistant', [
          for (final id in ['a', 'b'])
            Part(
              id: id,
              messageID: 'delegation',
              type: 'tool',
              toolName: 'subagent',
              toolState: ToolState.fromJson({
                'status': 'completed',
                'input': {'description': 'Review $id'},
                'output': 'Reviewed $id',
              }, toolName: 'subagent'),
            ),
        ]),
      ];
    await _pumpChat(tester, api, reduceMotion: true);
    await tester.pumpAndSettle();
    expect(find.textContaining('Delegated 2 tasks'), findsOneWidget);
    expect(find.textContaining('other call'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'UXCHAT single foreground Stop preserves draft and targets this session',
    (tester) async {
      final api = _FakeOpenCodeApi()..messagesHandler = _onePrompt;
      final conn = await _pumpChat(tester, api, reduceMotion: true);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Keep my draft',
      );
      conn.busySessions.add('session-1');
      conn.notifyListeners();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 250));
      // Send became Stop; the status is written in the turn.
      expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
      await tester.tap(find.byKey(const Key('chat-stop-button')));
      await tester.pump();
      expect(api.abortCalls, 1);
      expect(find.text('Keep my draft'), findsOneWidget);
      expect(api.promptCalls, 0);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'staged revert skips hidden pages when reopening older boundary',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..sessionResult = Session(
          id: 'session-1',
          reverted: true,
          stagedRevert: SessionRevert(messageID: 'msg_02'),
        )
        ..pageHandler = (cursor) async => switch (cursor) {
          null => ServerPage(
            items: [historyMessage('msg_05')],
            nextCursor: 'hidden',
          ),
          'hidden' => const ServerPage(items: [], nextCursor: 'boundary'),
          _ => ServerPage(
            items: [historyMessage('msg_01'), historyMessage('msg_02')],
          ),
        };
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = api.sessionResult!;
      await _pumpChat(
        tester,
        api,
        controller: controller,
        repository: _RevertProductRepository(),
      );
      await tester.pumpAndSettle();
      expect(api.pageCursors, containsAllInOrder([null, 'hidden', 'boundary']));
      expect(find.byKey(const ValueKey('message-msg_01')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
      expect(find.byKey(const ValueKey('message-msg_05')), findsNothing);
      expect(find.text('Revert staged'), findsOneWidget);
    },
  );

  testWidgets(
    'staged revert commit never reveals removed cached messages on refresh failure',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..sessionResult = Session(
          id: 'session-1',
          reverted: true,
          stagedRevert: SessionRevert(messageID: 'msg_02'),
        )
        ..pageHandler = (_) async => ServerPage(
          items: [
            historyMessage('msg_01'),
            historyMessage('msg_02'),
            historyMessage('msg_03'),
          ],
        );
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = api.sessionResult!;
      await _pumpChat(
        tester,
        api,
        controller: controller,
        repository: _RevertProductRepository(),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
      api.sessionResult = Session(id: 'session-1');
      api.pageHandler = (_) async => throw StateError('history unavailable');
      controller.handleEventForTesting(
        _event('session.revert.committed', {
          'sessionID': 'session-1',
          'to': 'msg_02',
        }),
      );
      await _pumpEvent(tester);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-msg_01')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
      expect(find.byKey(const ValueKey('message-msg_03')), findsNothing);
      expect(find.text('Revert staged'), findsNothing);
    },
  );

  testWidgets(
    'staged revert keeps a composer draft until explicitly resolved',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..sessionResult = Session(
          id: 'session-1',
          reverted: true,
          stagedRevert: SessionRevert(messageID: 'msg_02'),
        )
        ..pageHandler = (_) async => ServerPage(
          items: [historyMessage('msg_01'), historyMessage('msg_02')],
        );
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = api.sessionResult!;
      await _pumpChat(
        tester,
        api,
        controller: controller,
        repository: _RevertProductRepository(),
      );
      await tester.pumpAndSettle();
      final field = find.byKey(const Key('chat-composer-field'));
      await tester.enterText(field, 'My next change');
      await tester.pump();
      expect(
        tester
            .widget<GestureDetector>(find.byKey(const Key('chat-send-button')))
            .onTap,
        isNull,
      );
      expect(api.promptCalls, 0);
      expect(
        tester.widget<TextField>(_textField(field)).controller!.text,
        'My next change',
      );
      expect(find.text('Revert staged'), findsOneWidget);
      api.sessionResult = Session(id: 'session-1');
      controller.handleEventForTesting(
        _event('session.revert.cleared', {'sessionID': 'session-1'}),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
      expect(api.prompts.single.text, 'My next change');
    },
  );

  testWidgets('staged revert clear restores the hidden conversation', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..sessionResult = Session(
        id: 'session-1',
        reverted: true,
        stagedRevert: SessionRevert(messageID: 'msg_02'),
      )
      ..pageHandler = (_) async => ServerPage(
        items: [historyMessage('msg_01'), historyMessage('msg_02')],
      );
    final controller = await _controller(api);
    controller.sessionsById['session-1'] = api.sessionResult!;
    await _pumpChat(
      tester,
      api,
      controller: controller,
      repository: _RevertProductRepository(),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('message-msg_02')), findsNothing);
    api.sessionResult = Session(id: 'session-1');
    controller.handleEventForTesting(
      _event('session.revert.cleared', {'sessionID': 'session-1'}),
    );
    await _pumpEvent(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('message-msg_02')), findsOneWidget);
    expect(find.text('Revert staged'), findsNothing);
  });

  testWidgets(
    'paged history preserves newest rows through older and duplicate pages',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => switch (cursor) {
          null => ServerPage(
            items: [historyMessage('recent'), historyMessage('newest')],
            nextCursor: 'first',
          ),
          'first' => ServerPage(
            items: [historyMessage('older'), historyMessage('recent')],
            nextCursor: 'empty',
          ),
          'empty' => const ServerPage(items: [], nextCursor: 'last'),
          _ => ServerPage(items: [historyMessage('oldest')]),
        };
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      final older = find.byKey(const ValueKey('chat-load-older'));
      for (var i = 0; i < 3; i++) {
        await tester.ensureVisible(older);
        await tester.tap(older);
        await tester.pumpAndSettle();
      }
      expect(api.pageCursors, [null, 'first', 'empty', 'last']);
      expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-recent')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-oldest')), findsOneWidget);
      expect(older, findsNothing);
    },
  );

  testWidgets('paged history retry keeps the cursor and transcript', (
    tester,
  ) async {
    var fail = true;
    final api = _FakeOpenCodeApi()
      ..pageHandler = (cursor) async {
        if (cursor == null) {
          return ServerPage(
            items: [historyMessage('newest')],
            nextCursor: 'retry',
          );
        }
        if (fail) throw ApiException('Older request failed');
        return ServerPage(items: [historyMessage('older')]);
      };
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('chat-load-older')));
    await tester.pumpAndSettle();
    // Said in words; the raw ApiException text is details only.
    expect(find.text('Older request failed'), findsNothing);
    expect(
      find.text(
        "The server's answer didn't make sense to the app. Try again, or "
        'report the problem.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
    fail = false;
    await tester.tap(find.byKey(const ValueKey('chat-load-older')));
    await tester.pumpAndSettle();
    expect(api.pageCursors, [null, 'retry', 'retry']);
    expect(find.byKey(const ValueKey('message-older')), findsOneWidget);
  });

  testWidgets(
    'paged history does not resurrect a message removed during loading',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(items: [historyMessage('newest')], nextCursor: 'older')
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pump();
      controller.handleEventForTesting(
        _event('message.removed', {
          'sessionID': 'session-1',
          'messageID': 'deleted',
        }),
      );
      await _pumpEvent(tester);
      pending.complete(
        ServerPage(items: [historyMessage('deleted'), historyMessage('older')]),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-deleted')), findsNothing);
      expect(find.byKey(const ValueKey('message-older')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
    },
  );

  testWidgets('paged history preserves deltas newer than an older snapshot', (
    tester,
  ) async {
    final pending = Completer<ServerPage<MessageWithParts>>();
    final api = _FakeOpenCodeApi()
      ..pageHandler = (cursor) async => cursor == null
          ? ServerPage(
              items: [historyMessage('newest', 'latest')],
              nextCursor: 'older',
            )
          : pending.future;
    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('chat-load-older')));
    await tester.pump();
    controller.handleEventForTesting(
      _event('message.part.delta', {
        'sessionID': 'session-1',
        'messageID': 'newest',
        'partID': 'part-newest',
        'field': 'text',
        'delta': ' live',
      }),
    );
    await _pumpEvent(tester);
    pending.complete(
      ServerPage(
        items: [historyMessage('older'), historyMessage('newest', 'stale')],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('latest live'), findsOneWidget);
    expect(find.text('stale'), findsNothing);
  });

  testWidgets(
    'paged history anchors the reader while older rows and live messages arrive',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [
                  // Enough one-line rows that reaching the oldest leaves the
                  // reader well over the 480px "away from latest" mark.
                  for (var i = 0; i < 40; i++) historyMessage('recent-$i'),
                ],
                nextCursor: 'older',
              )
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      final listFinder = find.byType(ScrollablePositionedList);
      for (
        var i = 0;
        i < 8 &&
            find
                .byKey(const ValueKey('chat-load-older'))
                .hitTestable()
                .evaluate()
                .isEmpty;
        i++
      ) {
        await tester.drag(listFinder, const Offset(0, 450));
        await tester.pumpAndSettle();
      }
      final anchor = find.byKey(const ValueKey('message-recent-0'));
      final before = tester.getTopLeft(anchor);
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pump();
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'live-newest',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': 100},
          },
        }),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'live-text',
            messageID: 'live-newest',
            type: 'text',
            text: 'Live newest',
          ),
        }),
      );
      await _pumpEvent(tester);
      pending.complete(
        ServerPage(
          items: [for (var i = 0; i < 20; i++) historyMessage('older-$i')],
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(anchor).dy, closeTo(before.dy, 1));
      expect(find.text('Live newest'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('jump-to-latest')));
      await tester.pumpAndSettle();
      expect(find.text('Live newest'), findsOneWidget);
    },
  );

  testWidgets(
    'paged history retains older prefix on overlapping head refresh',
    (tester) async {
      var refreshed = false;
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [
                  historyMessage('recent'),
                  historyMessage(refreshed ? 'newest' : 'previous'),
                ],
                nextCursor: 'older',
              )
            : ServerPage(items: [historyMessage('oldest')]);
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pumpAndSettle();
      refreshed = true;
      controller.handleEventForTesting(
        _event('session.shell.changed', {'sessionID': 'session-1'}),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('message-oldest')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-newest')), findsOneWidget);
      expect(find.byKey(const ValueKey('message-previous')), findsNothing);
      expect(find.byKey(const ValueKey('chat-load-older')), findsNothing);
    },
  );

  testWidgets(
    'paged history reconnect invalidates pending older page and updates open timeline',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      var refreshed = false;
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(
                items: [
                  historyMessage(refreshed ? 'new-window' : 'old-window'),
                ],
                nextCursor: 'older',
              )
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Conversation menu'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('timeline-search')),
        'window',
      );
      await tester.tap(find.byKey(const ValueKey('timeline-load-older')));
      await tester.pump();
      refreshed = true;
      controller.signalDataRefreshForTesting();
      await tester.pumpAndSettle();
      pending.complete(ServerPage(items: [historyMessage('stale-window')]));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('timeline-row-new-window')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('timeline-row-old-window')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('timeline-row-stale-window')),
        findsNothing,
      );
      expect(
        tester
            .widget<TextField>(
              _textField(find.byKey(const ValueKey('timeline-search'))),
            )
            .controller!
            .text,
        'window',
      );
    },
  );

  testWidgets(
    'unloaded part edits wait for history and retain updates newer than its page',
    (tester) async {
      final pending = Completer<ServerPage<MessageWithParts>>();
      final api = _FakeOpenCodeApi()
        ..pageHandler = (cursor) async => cursor == null
            ? ServerPage(items: [historyMessage('newest')], nextCursor: 'older')
            : pending.future;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('chat-load-older')));
      await tester.pump();
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'part-old',
            messageID: 'old',
            type: 'text',
            text: 'Edited old prompt',
          ),
        }),
      );
      await _pumpEvent(tester);
      expect(find.byKey(const ValueKey('message-old')), findsNothing);
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'old',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': 0},
          },
        }),
      );
      await _pumpEvent(tester);
      expect(find.byKey(const ValueKey('message-old')), findsNothing);
      controller.handleEventForTesting(
        _event('message.part.delta', {
          'sessionID': 'session-1',
          'messageID': 'old',
          'partID': 'late-part',
          'field': 'text',
          'delta': ' plus live delta',
        }),
      );
      await _pumpEvent(tester);
      pending.complete(
        ServerPage(items: [historyMessage('old', 'stale prompt')]),
      );
      await tester.pumpAndSettle();
      expect(find.text('Edited old prompt'), findsOneWidget);
      expect(find.text('stale prompt'), findsNothing);
      expect(
        tester.getTopLeft(find.text('Edited old prompt')).dy,
        lessThan(tester.getTopLeft(find.text('newest')).dy),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'late-part',
            messageID: 'old',
            type: 'text',
            text: 'Late base',
          ),
        }),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Late base plus live delta'), findsOneWidget);
    },
  );

  testWidgets(
    'provisional session with an empty page and cursor is not discarded',
    (tester) async {
      final api = _FakeOpenCodeApi()
        ..pageHandler = (_) async =>
            const ServerPage(items: [], nextCursor: 'more');
      await _pumpProvisionalChat(tester, api);
      await tester.pumpAndSettle();
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(api.deleteCalls, isEmpty);
    },
  );

  test('message errors surface nested data.message', () {
    final info = MessageInfo.fromJson({
      'id': 'assistant-1',
      'sessionID': 'session-1',
      'role': 'assistant',
      'error': {
        'name': 'ProviderError',
        'data': {'message': 'The selected model is unavailable'},
      },
    });

    expect(info.errorText, 'The selected model is unavailable');
  });

  testWidgets('back discards only the exact verified empty mobile session', (
    tester,
  ) async {
    final requested = <String>[];
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (id) async {
        requested.add(id);
        return [];
      };
    await _pumpProvisionalChat(tester, api);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(requested, everyElement('session-1'));
    expect(requested.length, greaterThanOrEqualTo(2));
    expect(api.deleteCalls, ['session-1']);
    expect(find.byKey(const ValueKey('provisional-chat-host')), findsOneWidget);
  });

  testWidgets('back preserves a newly created session after server messages', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-committed', 'user', [
          Part(type: 'text', text: 'Keep this session'),
        ]),
      ];
    await _pumpProvisionalChat(tester, api);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(api.deleteCalls, isEmpty);
    expect(find.byKey(const ValueKey('provisional-chat-host')), findsOneWidget);
  });

  testWidgets('failed empty-session verification keeps server state', (
    tester,
  ) async {
    var calls = 0;
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async {
        calls += 1;
        if (calls > 1) throw StateError('transport moved');
        return [];
      };
    await _pumpProvisionalChat(tester, api);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(api.deleteCalls, isEmpty);
    // Kept quietly: an empty conversation is harmless and the list shows
    // it, so nothing interrupts the way out.
    expect(find.byType(SnackBar), findsNothing);
    expect(find.byKey(const ValueKey('product-error-alert')), findsNothing);
  });

  testWidgets('leaving with a typed draft keeps it silently', (tester) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [];
    final controller = await _pumpProvisionalChat(tester, api, textScale: 2);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Keep this draft',
    );

    await tester.pageBack();
    await tester.pumpAndSettle();

    // No confirmation: the text persists as a per-session draft, and the
    // provisional session survives so the draft has a home to return to.
    expect(
      find.byKey(const ValueKey('discard-chat-draft-dialog')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('provisional-chat-host')), findsOneWidget);
    expect(api.deleteCalls, isEmpty);
    expect(controller.sessionDraft('session-1'), 'Keep this draft');
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'leaving an empty conversation keeps its pending photo destination',
    (tester) async {
      final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [];
      final controller = await _pumpProvisionalChat(tester, api);
      await controller.store.prefs.setString(
        PromptPhotoStore.key,
        jsonEncode(
          const PendingPromptPhoto(
            id: 'pending',
            profileID: '',
            sessionID: 'session-1',
          ).toJson(),
        ),
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(api.deleteCalls, isEmpty);
      expect(controller.promptPhotos.pending!.sessionID, 'session-1');
      expect(
        find.byKey(const ValueKey('provisional-chat-host')),
        findsOneWidget,
      );
    },
  );

  testWidgets('mobile new command stays when an attachment cannot persist', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [];
    await _pumpProvisionalChat(
      tester,
      api,
      attachments: const [
        PromptAttachment(
          mime: 'text/plain',
          filename: 'notes.txt',
          url: 'content://temporary/notes.txt',
        ),
      ],
    );

    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('command-mobile-new')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('draft-save-error')), findsOneWidget);

    expect(api.createCalls, 0);
    expect(api.deleteCalls, isEmpty);
    expect(find.textContaining('notes.txt'), findsWidgets);
  });

  testWidgets('a running sibling agent appears in the switch strip', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 720));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final parent = Session(
      id: 'parent',
      title: 'Parent work',
      directory: '/work/acme',
      time: SessionTime(created: 1),
    );
    final child = Session(
      id: 'session-1',
      title: 'Explore mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 2),
    );
    final sibling = Session(
      id: 'session-2',
      title: 'Review mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 3),
    );
    final controller = await _controller(api)
      ..directory = '/work/acme'
      ..repository = _RelationsProductRepository(parent, [child, sibling])
      ..sessionsById = {parent.id: parent, child.id: child, sibling.id: sibling}
      ..busySessions = {'session-2'};
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final work = find.byKey(const ValueKey('running-work-indicator'));
    expect(work, findsOneWidget);
    await tester.tap(work);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const ValueKey('work-agent-session-2')), findsOneWidget);
    expect(find.byKey(const ValueKey('work-agent-session-1')), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(tester.takeException(), isNull);
  });

  testWidgets('child chat exposes parent and sibling navigation', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final parent = Session(
      id: 'parent',
      title: 'Parent work',
      directory: '/work/acme',
      time: SessionTime(created: 1),
    );
    final child = Session(
      id: 'session-1',
      title: 'Explore mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 2),
    );
    final sibling = Session(
      id: 'session-2',
      title: 'Review mobile flow',
      parentID: parent.id,
      directory: '/work/acme',
      time: SessionTime(created: 3),
    );
    final controller = await _controller(api, savedProfile: true)
      ..directory = '/work/acme'
      ..repository = _RelationsProductRepository(parent, [child, sibling])
      ..sessionsById = {
        parent.id: parent,
        child.id: child,
        sibling.id: sibling,
      };
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Delegated conversation · 1 of 2'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('subagent-parent-session')),
      findsOneWidget,
    );
    // The chat's one status line (design standard §5): the siblings are
    // under its More menu.
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('subagent-session-list')));
    await tester.pumpAndSettle();

    expect(find.text('Subagents'), findsOneWidget);
    expect(find.text('Review mobile flow'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('offline chat keeps its transcript and shows recovery actions', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'part-1',
            type: 'text',
            text: 'Retained response',
            messageID: 'assistant-1',
          ),
        ]),
      ];
    // App-wide status belongs to an actual saved profile, like production.
    final controller = await _controller(api, savedProfile: true);
    await _pumpChat(tester, api, controller: controller);
    expect(find.text('Retained response'), findsOneWidget);

    controller
      ..status = StreamStatus.disconnected
      ..lastError = 'Endpoint is unavailable'
      ..signalDataRefreshForTesting();
    await tester.pump();
    await tester.pump();

    expect(find.text('Retained response'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('connection-status-banner')),
      findsOneWidget,
    );
    // The Work tab's words (design standard §5): the last attempt failed,
    // so the line says so at once.
    expect(find.text("Synthetic isn't answering"), findsOneWidget);
    expect(find.text('Reconnect to Synthetic'), findsOneWidget);
    // The raw error and the secondary action live behind Details, in the
    // status line's menu (design standard §5: one action per line).
    // The status line unfolds first (design standard §10).
    await tester.pump(KitMotion.standard);
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    // The raw error waits folded under the sheet's own Details.
    expect(find.textContaining('Endpoint is unavailable'), findsNothing);
    final fold = find.descendant(
      of: find.byKey(const ValueKey('connection-banner-details-sheet')),
      matching: find.byKey(const ValueKey('kit-details-toggle')),
    );
    await tester.ensureVisible(fold);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(fold);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.textContaining('Endpoint is unavailable'), findsOneWidget);
    expect(find.text('Switch server'), findsOneWidget);
  });

  testWidgets('rehydrate never flashes a skeleton over existing messages', (
    tester,
  ) async {
    var loads = 0;
    Completer<List<MessageWithParts>>? pending;
    List<MessageWithParts> transcript() => [
      _message('assistant-1', 'assistant', [
        Part(
          id: 'part-1',
          type: 'text',
          text: 'Retained response',
          messageID: 'assistant-1',
        ),
      ]),
    ];
    final api = _FakeOpenCodeApi();
    api.messagesHandler = (_) {
      loads += 1;
      if (loads == 1) return Future.value(transcript());
      pending = Completer<List<MessageWithParts>>();
      return pending!.future;
    };

    final controller = await _pumpChat(tester, api);
    expect(find.text('Retained response'), findsOneWidget);
    expect(find.byType(KitSkeletonTranscript), findsNothing);

    controller.signalDataRefreshForTesting();
    await tester.pump();
    await tester.pump();

    // The reload is still in flight: the transcript stays rendered with no
    // skeleton and no full-screen error.
    expect(loads, 2);
    expect(find.text('Retained response'), findsOneWidget);
    expect(find.byType(KitSkeletonTranscript), findsNothing);
    expect(find.byType(KitStateView), findsNothing);

    // Even a failed refresh keeps the transcript instead of a dead end.
    pending!.completeError(StateError('stream reset during rehydrate'));
    await tester.pumpAndSettle();
    expect(find.text('Retained response'), findsOneWidget);
    expect(find.byType(KitSkeletonTranscript), findsNothing);
    expect(find.byType(KitStateView), findsNothing);
  });
}
