part of '../chat_live_events_test.dart';

// Composer tools and agents, part and message removal, hydration races,
// optimistic bubbles, errors, attachments, starters and message menus.

void _composerAndErrorTests() {
  testWidgets(
    'searchable sheets keep drag handles and dismiss the keyboard on drag',
    (tester) async {
      final messages = <MessageWithParts>[
        for (var index = 0; index < 8; index += 1)
          _message('user-$index', 'user', [
            Part(
              id: 'part-$index',
              messageID: 'user-$index',
              type: 'text',
              text: 'prompt $index',
            ),
          ], created: index + 1),
      ];
      final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;
      // The product theme supplies the default drag handle under test.
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,

            theme: AppTheme.dark(),
            home: const ChatScreen(sessionID: 'session-1'),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Conversation menu'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
      await tester.pumpAndSettle();

      // The sheet no longer opts out of the theme drag handle.
      expect(
        tester.widget<BottomSheet>(find.byType(BottomSheet)).showDragHandle,
        isNot(false),
      );
      // The kit sheet frame's one scroll carries the rows.
      final timelineList = tester.widget<CustomScrollView>(
        find.descendant(
          of: find.byKey(const ValueKey('timeline-sheet')),
          matching: find.byType(CustomScrollView),
        ),
      );
      expect(
        timelineList.keyboardDismissBehavior,
        ScrollViewKeyboardDismissBehavior.onDrag,
      );

      // Focus the search, then drag the results: the keyboard focus releases.
      await tester.tap(find.byKey(const Key('timeline-search')));
      await tester.pump();
      final editable = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const Key('timeline-search')),
          matching: find.byType(EditableText),
        ),
      );
      expect(editable.focusNode.hasFocus, isTrue);
      // Dragging the rows scrolls the sheet's body, which releases the
      // keyboard focus.
      await tester.drag(
        find.byKey(const ValueKey('timeline-row-user-7')),
        const Offset(0, -300),
        warnIfMissed: false,
      );
      await tester.pumpAndSettle();
      await tester.drag(
        find.byKey(const ValueKey('timeline-row-user-7')),
        const Offset(0, -120),
        warnIfMissed: false,
      );
      await tester.pumpAndSettle();
      expect(editable.focusNode.hasFocus, isFalse);

      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();

      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      expect(
        tester.widget<BottomSheet>(find.byType(BottomSheet)).showDragHandle,
        isNot(false),
      );
      // Focus the launcher's search, then drag its list: the keyboard
      // focus releases here too.
      await tester.tap(find.byKey(const Key('command-launcher-search')));
      await tester.pump();
      final launcherSearch = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const Key('command-launcher-search')),
          matching: find.byType(EditableText),
        ),
      );
      expect(launcherSearch.focusNode.hasFocus, isTrue);
      // The list is taller than the sheet: drag from its visible top.
      await tester.dragFrom(
        tester.getTopLeft(find.byKey(const Key('command-launcher-list'))) +
            const Offset(24, 24),
        const Offset(0, -120),
      );
      await tester.pumpAndSettle();
      expect(launcherSearch.focusNode.hasFocus, isFalse);
    },
  );

  testWidgets('composer tools delegates only to visible server subagents', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _StaticCatalogController(ProfileStore(prefs: prefs))
      ..api = _FakeOpenCodeApi()
      ..status = StreamStatus.connected
      ..catalog = const CatalogSnapshot(
        providers: [],
        models: [],
        agents: [
          CatalogAgent(id: 'build', mode: 'primary', hidden: false),
          CatalogAgent(
            id: 'explore',
            mode: 'subagent',
            hidden: false,
            description: 'Find code and explain how it works',
          ),
          CatalogAgent(id: 'internal', mode: 'subagent', hidden: true),
        ],
      );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tools-sheet')), findsNothing);
    expect(find.byKey(const Key('command-launcher-sheet')), findsOneWidget);
    expect(find.text('Commands and agents'), findsOneWidget);
    expect(tester.takeException(), isNull);
    // At 200% text the sheet header scrolls with the body. Reveal the
    // delegate tab before choosing it on this narrow phone viewport.
    final sheetScroll = find
        .descendant(
          of: find.byKey(const Key('command-launcher-sheet')),
          matching: find.byType(Scrollable),
        )
        .first;
    await tester.scrollUntilVisible(
      find.byKey(const Key('composer-tools-agents-tab')),
      100,
      scrollable: sheetScroll,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('composer-tools-agents-tab')));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const Key('composer-agent-explore')),
      100,
      scrollable: sheetScroll,
    );
    await tester.pumpAndSettle();
    expect(find.text('@${KitBidi.auto('explore')}'), findsOneWidget);
    expect(
      find.byKey(const Key('composer-agent-build'), skipOffstage: false),
      findsNothing,
    );
    expect(
      find.byKey(const Key('composer-agent-internal'), skipOffstage: false),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
    await tester.tap(find.byKey(const Key('composer-agent-explore')));
    await tester.pumpAndSettle();

    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, '@explore ');
    expect(tester.takeException(), isNull);
  });

  testWidgets('typed agent autocomplete sends exact OpenCode agent parts', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _StaticCatalogController(ProfileStore(prefs: prefs))
      ..api = api
      ..status = StreamStatus.connected
      ..catalog = const CatalogSnapshot(
        providers: [],
        models: [],
        agents: [
          CatalogAgent(id: 'build', mode: 'primary', hidden: false),
          CatalogAgent(id: 'general', mode: 'subagent', hidden: false),
        ],
      );
    await _pumpChat(tester, api, controller: controller);
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please ask @gen',
    );
    await tester.pump();
    expect(find.byKey(const Key('inline-agent-general')), findsOneWidget);
    await tester.tap(find.byKey(const Key('inline-agent-general')));
    await tester.pump();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please ask @general to inspect this.',
    );
    await tester.tap(find.byTooltip('Send'));
    await tester.pumpAndSettle();

    expect(api.prompts.single.text, 'Please ask @general to inspect this.');
    expect(api.prompts.single.agentMentions, hasLength(1));
    final mention = api.prompts.single.agentMentions.single;
    expect(mention.name, 'general');
    expect(mention.value, '@general');
    expect(mention.start, 11);
    expect(mention.end, 19);
  });

  testWidgets('removes individual parts and complete messages', (tester) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'part-1',
            messageID: 'assistant-1',
            type: 'text',
            text: 'remove this part',
          ),
          Part(
            id: 'part-2',
            messageID: 'assistant-1',
            type: 'text',
            text: 'keep until message removal',
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);

    controller.handleEventForTesting(
      _event('message.part.removed', {
        'sessionID': 'session-1',
        'messageID': 'assistant-1',
        'partID': 'part-1',
      }),
    );
    await _pumpEvent(tester);
    expect(find.text('remove this part'), findsNothing);
    expect(find.text('keep until message removal'), findsOneWidget);

    controller.handleEventForTesting(
      _event('message.removed', {
        'sessionID': 'session-1',
        'messageID': 'assistant-1',
      }),
    );
    await _pumpEvent(tester);
    expect(find.text('keep until message removal'), findsNothing);
  });

  testWidgets('newer deltas survive an older hydration response', (
    tester,
  ) async {
    final hydration = Completer<List<MessageWithParts>>();
    final api = _FakeOpenCodeApi()..messagesHandler = (_) => hydration.future;
    final controller = await _controller(api);
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

    controller.handleEventForTesting(
      _event('message.part.delta', {
        'sessionID': 'session-1',
        'messageID': 'assistant-1',
        'partID': 'part-1',
        'field': 'text',
        'delta': ' fresh',
      }),
    );
    hydration.complete([
      _message('assistant-1', 'assistant', [
        Part(
          id: 'part-1',
          messageID: 'assistant-1',
          type: 'text',
          text: 'stale',
        ),
      ]),
    ]);
    await tester.pumpAndSettle();

    expect(find.text('stale fresh'), findsOneWidget);
    expect(find.text('stale'), findsNothing);
  });

  testWidgets(
    'a prompt with photos is not shown twice when the server re-homes them',
    (tester) async {
      final prompt = Completer<void>();
      final api = _FakeOpenCodeApi()..promptCompleter = prompt;
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,

            home: ChatScreen(
              sessionID: 'session-1',
              initialAttachments: [
                PromptAttachment(
                  mime: 'image/jpeg',
                  filename: '1000101752.jpg',
                  url: 'data:image/jpeg;base64,/9j/4AAQ',
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'match this UI',
      );
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
      await tester.pump();
      expect(find.text('match this UI'), findsOneWidget);

      // The server's copy: same text and file name, but the file now lives at
      // the server's own location and the type is spelled differently.
      for (final part in [
        _partJson(
          id: 'part-1',
          messageID: 'user-1',
          type: 'text',
          text: 'match this UI',
        ),
        {
          ..._partJson(
            id: 'file-0',
            messageID: 'user-1',
            type: 'file',
            text: '',
          ),
          'filename': '1000101752.jpg',
          'mime': 'image/jpg',
          'url': 'file:///srv/opencode/attachments/ab12/1000101752.jpg',
        },
      ]) {
        controller.handleEventForTesting(
          _event('message.part.updated', {
            'sessionID': 'session-1',
            'part': part,
          }),
        );
      }
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'user-1',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': DateTime.now().millisecondsSinceEpoch},
          },
        }),
      );
      await tester.pump();
      prompt.complete();
      await tester.pumpAndSettle();

      expect(find.text('match this UI'), findsOneWidget);
      // The photo shows once, as its thumbnail (FC4).
      expect(find.bySemanticsLabel('Preview 1000101752.jpg'), findsOneWidget);
    },
  );

  testWidgets('canonical user event replaces the optimistic bubble', (
    tester,
  ) async {
    final prompt = Completer<void>();
    final api = _FakeOpenCodeApi()..promptCompleter = prompt;
    final controller = await _pumpChat(tester, api);

    await tester.enterText(find.byType(TextField), 'hello server');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pump();
    expect(find.text('hello server'), findsOneWidget);

    controller.handleEventForTesting(
      _event('message.part.updated', {
        'sessionID': 'session-1',
        'part': _partJson(
          id: 'part-1',
          messageID: 'user-1',
          type: 'text',
          text: 'hello server',
        ),
      }),
    );
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'user-1',
          'sessionID': 'session-1',
          'role': 'user',
          'time': {'created': DateTime.now().millisecondsSinceEpoch},
        },
      }),
    );
    await tester.pump();

    expect(find.text('hello server'), findsOneWidget);
    prompt.complete();
    await tester.pump();
  });

  testWidgets(
    'out-of-order canonical users reconcile by prompt instead of timestamp',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final controller = await _pumpChat(tester, api);

      await tester.enterText(find.byType(TextField), 'first prompt');
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'second prompt');
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();

      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'part-second',
            messageID: 'user-second',
            type: 'text',
            text: 'second prompt',
          ),
        }),
      );
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'user-second',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': DateTime.now().millisecondsSinceEpoch},
          },
        }),
      );
      await _pumpEvent(tester);

      expect(find.text('first prompt'), findsOneWidget);
      expect(find.text('second prompt'), findsOneWidget);

      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'user-first',
            'sessionID': 'session-1',
            'role': 'user',
            'time': {'created': DateTime.now().millisecondsSinceEpoch - 1000},
          },
        }),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'part-first',
            messageID: 'user-first',
            type: 'text',
            text: 'first prompt',
          ),
        }),
      );
      await _pumpEvent(tester);

      expect(find.text('first prompt'), findsOneWidget);
      expect(find.text('second prompt'), findsOneWidget);
    },
  );

  testWidgets(
    'failed send removes optimism, restores input, and blocks repeats',
    (tester) async {
      final prompt = Completer<void>();
      final api = _FakeOpenCodeApi()..promptCompleter = prompt;
      await _pumpChat(tester, api);

      await tester.enterText(find.byType(TextField), 'try once');
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
      await tester.tap(find.byTooltip('Send'));
      await tester.pump();
      expect(api.promptCalls, 1);
      expect(find.text('try once'), findsOneWidget);

      prompt.completeError(StateError('network failed'));
      await tester.pumpAndSettle();

      expect(find.text('try once'), findsOneWidget);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller?.text,
        'try once',
      );
      expect(
        find.text(
          "That didn't work. Details show what happened. Try again, or report the problem.",
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets('session errors stop thinking and remain visible in chat', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);

    await tester.enterText(find.byType(TextField), 'hello');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await _pumpEvent(tester);

    controller.handleEventForTesting(
      _event('session.status', {
        'sessionID': 'session-1',
        'status': {'type': 'busy'},
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);

    controller.handleEventForTesting(
      _event('session.error', {
        'sessionID': 'session-1',
        'error': {
          'name': 'ProviderError',
          'data': {'message': 'Sign in to the selected model provider.'},
        },
      }),
    );
    await _pumpEvent(tester);
    // The edge status leaves with a short exit animation.
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('chat-stop-button')), findsNothing);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    expect(find.text('The agent stopped because of an error.'), findsOneWidget);
  });

  testWidgets('an error a reply carries is not repeated in a banner', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'assistant-1',
          'sessionID': 'session-1',
          'role': 'assistant',
          'time': {'created': 1, 'completed': 2},
          'error': {
            'name': 'ProviderError',
            'data': {'message': 'WebSocket inbound queue overflow'},
          },
        },
      }),
    );
    await _pumpEvent(tester);

    expect(
      find.text('This request was too large to send to the model.'),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsNothing);
  });

  testWidgets('a dropped connection is said in plain words, once, and the '
      'banner leaves when the turn moves on', (tester) async {
    const raw =
        'ECONNRESET: The socket connection was closed unexpectedly. For more '
        'information, pass `verbose: true` in the second argument to fetch()';
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.handleEventForTesting(
      _event('session.error', {
        'sessionID': 'session-1',
        'error': {
          'name': 'UnknownError',
          'data': {'message': raw},
        },
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    expect(find.text('The connection to the model dropped.'), findsOneWidget);
    expect(find.byKey(const ValueKey('prompt-error-hint')), findsOneWidget);
    expect(find.textContaining('ECONNRESET'), findsNothing);
    expect(find.textContaining('fetch()'), findsNothing);
    // The server's exact words are one tap away.
    // The status line unfolds first (design standard §10).
    await tester.pump(KitMotion.standard);
    await tester.tap(find.byKey(const ValueKey('prompt-error-details')));
    await tester.pumpAndSettle();
    expect(find.textContaining('ECONNRESET'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();

    // The reply carries the same problem (worded slightly differently by the
    // server): one place says it, not two.
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'assistant-1',
          'sessionID': 'session-1',
          'role': 'assistant',
          'time': {'created': 1, 'completed': 2},
          'error': {
            'name': 'UnknownError',
            'data': {
              'message': 'The socket connection was closed unexpectedly.',
            },
          },
        },
      }),
    );
    await _pumpEvent(tester);
    // The status line folds away (design standard §10), leaving the reply.
    await tester.pump(KitMotion.standard);
    await tester.pump(KitMotion.standard);
    expect(find.text('The connection to the model dropped.'), findsOneWidget);

    // The agent carries on: the error becomes a quiet line of the turn and
    // no banner claims something is still wrong.
    controller.handleEventForTesting(
      _event('message.updated', {
        'info': {
          'id': 'assistant-2',
          'sessionID': 'session-1',
          'role': 'assistant',
          'time': {'created': 3},
        },
      }),
    );
    controller.handleEventForTesting(
      _event('message.part.updated', {
        'sessionID': 'session-1',
        'part': _partJson(
          id: 'p2',
          messageID: 'assistant-2',
          type: 'text',
          text: 'Retrying the build.',
        ),
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsNothing);
    expect(find.text('The agent carried on after this.'), findsOneWidget);
  });

  testWidgets('a model-not-found session error shows one line and Choose model', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.handleEventForTesting(
      _event('session.error', {
        'sessionID': 'session-1',
        'error': {
          'name': 'ProviderModelNotFoundError',
          'data': {
            'message':
                'ProviderModelNotFoundError: Model not found: openai/gpt-5.6. '
                'Did you mean: gpt-5.6, gpt-5.6-pro?\n'
                '    at <anonymous> (/\$bunfs/root/chunk.js:439:1)\n'
                '    at SessionPrompt.getModel (/\$bunfs/root/chunk.js:1096:2)',
          },
        },
      }),
    );
    await _pumpEvent(tester);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    // Words on the line; the server's text only under Details.
    expect(find.text("The server doesn't have this model."), findsOneWidget);
    expect(find.textContaining('Did you mean'), findsNothing);
    expect(find.textContaining('at <anonymous>'), findsNothing);
    expect(
      find.byKey(const ValueKey('prompt-error-choose-model')),
      findsOneWidget,
    );
    // Choose model is the line's action; Details is under its More menu.
    // The status line unfolds first (design standard §10).
    await tester.pump(KitMotion.standard);
    await tester.tap(find.byKey(const ValueKey('kit-status-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('prompt-error-details')));
    await tester.pumpAndSettle();
    expect(find.textContaining('at <anonymous>'), findsOneWidget);
    expect(find.textContaining('Did you mean'), findsOneWidget);
  });

  testWidgets('renders attachment-only and mixed user prompts accessibly', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-1', 'user', [
          Part(type: 'file', filename: 'report.pdf'),
        ]),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Review this image'),
          Part(
            type: 'file',
            mime: 'image/png',
            filename: 'diagram.png',
            url:
                'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
          ),
        ], created: 2),
      ];
    await _pumpChat(tester, api);

    expect(find.text('report.pdf'), findsOneWidget);
    expect(find.bySemanticsLabel('Preview report.pdf'), findsOneWidget);
    expect(find.text('Review this image'), findsOneWidget);
    // A photo shows as its thumbnail, not its name (FC4).
    expect(find.text('diagram.png'), findsNothing);
    expect(find.bySemanticsLabel('Preview diagram.png'), findsOneWidget);

    final diagram = find.bySemanticsLabel('Preview diagram.png');
    await tester.ensureVisible(diagram);
    await tester.pumpAndSettle();
    await tester.tap(diagram);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-viewer-image')), findsOneWidget);
    expect(find.byType(KitZoom), findsOneWidget);
    expect(find.byTooltip('Zoom in'), findsOneWidget);
  });

  testWidgets('retry preserves mixed and attachment-only file parts', (
    tester,
  ) async {
    const textUrl = 'data:text/plain;base64,bm90ZXM=';
    const imageUrl = 'data:image/png;base64,iVBORw0KGgo=';
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-files', 'user', [
          Part(
            type: 'file',
            mime: 'text/plain',
            filename: 'notes.txt',
            url: textUrl,
          ),
        ]),
        _message('user-mixed', 'user', [
          Part(type: 'text', text: 'Review this'),
          Part(
            type: 'file',
            mime: 'image/png',
            filename: 'diagram.png',
            url: imageUrl,
          ),
        ], created: 2),
      ];
    final controller = await _pumpChat(tester, api);
    controller.selectedVariant = 'fast';

    await _runSheetCommand(tester, 'retry');

    expect(api.prompts.single.text, 'Review this');
    expect(api.prompts.single.variant, 'fast');
    expect(api.prompts.single.attachments.single.toJson(), {
      'type': 'file',
      'mime': 'image/png',
      'filename': 'diagram.png',
      'url': imageUrl,
    });

    controller.handleEventForTesting(
      _event('message.removed', {
        'sessionID': 'session-1',
        'messageID': 'user-mixed',
      }),
    );
    await _pumpEvent(tester);
    await _runSheetCommand(tester, 'retry');

    expect(api.prompts.last.text, isEmpty);
    expect(api.prompts.last.attachments.single.toJson(), {
      'type': 'file',
      'mime': 'text/plain',
      'filename': 'notes.txt',
      'url': textUrl,
    });
  });

  testWidgets('typed server command passes arguments and selected model', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _FakeProductRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
    ]);
    final controller = await _pumpChat(tester, api, repository: repository);
    controller.selectedModel = ModelRef(
      providerID: 'anthropic',
      modelID: 'claude-sonnet',
    );
    controller.selectedVariant = 'high';
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/review --staged',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pumpAndSettle();

    expect(api.slashCommandName, 'review');
    expect(api.slashArguments, '--staged');
    expect(api.slashModel?.providerID, 'anthropic');
    expect(api.slashModel?.modelID, 'claude-sonnet');
    expect(api.slashVariant, 'high');
  });

  testWidgets('attachment count limit is enforced before opening the picker', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          home: ChatScreen(
            sessionID: 'session-1',
            initialAttachments: List.generate(
              5,
              (index) => PromptAttachment(
                mime: 'text/plain',
                filename: 'file-$index.txt',
                url: 'data:text/plain;base64,WA==',
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _useComposerTool(tester, 'attach');
    await tester.pumpAndSettle();

    expect(find.textContaining('attach up to 5 files'), findsOneWidget);
  });

  testWidgets('attachment remove action is accessible and at least 48dp', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          home: ChatScreen(
            sessionID: 'session-1',
            initialAttachments: [
              PromptAttachment(
                mime: 'text/plain',
                filename: 'notes.txt',
                url: 'data:text/plain;base64,bm90ZXM=',
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final remove = find.bySemanticsLabel('Remove notes.txt');
    expect(remove, findsOneWidget);
    final size = tester.getSize(remove);
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
    expect(find.bySemanticsLabel('Remove notes.txt'), findsOneWidget);

    final preview = find.bySemanticsLabel('Preview notes.txt');
    expect(
      tester.getSemantics(preview),
      matchesSemantics(
        label: 'Preview notes.txt',
        isButton: true,
        hasTapAction: true,
      ),
    );
    await tester.tap(preview);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byType(KitCodeBlock), findsWidgets);
    expect(find.text('notes'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();

    await tester.tap(remove);
    await tester.pumpAndSettle();
    expect(remove, findsNothing);
    semantics.dispose();
  });

  testWidgets('empty transcript starters fill the composer', (tester) async {
    final api = _FakeOpenCodeApi();
    // No directory is selected, so the project- and git-dependent starters
    // give way to ones that work in the server's default folder.
    await _pumpChat(tester, api);
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text(KitBidi.auto('Server folder')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-start-tip')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-starter-What changed recently?')),
      findsNothing,
    );

    await tester.tap(
      find.byKey(const ValueKey("chat-starter-List what's in this folder")),
    );
    await tester.pump();

    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      "List what's in this folder",
    );
    expect(api.promptCalls, 0);
  });

  testWidgets('empty transcript starters are seeded from the active project', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..projectFiles = [
        FileNode(name: 'pubspec.yaml', path: 'pubspec.yaml', isDir: false),
      ];
    final controller = await _controller(api);
    controller.directory = '/work/oc_app';
    await _pumpChat(tester, api, controller: controller);
    await tester.pump(const Duration(milliseconds: 300));

    // The project is named once, by the header chip.
    expect(find.text(KitBidi.auto('oc_app')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-start-name')), findsNothing);
    expect(find.text('1 item'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-starter-Explain this project')),
      findsOneWidget,
    );
    // No Git history is known here, so nothing asks what changed.
    expect(
      find.byKey(const ValueKey('chat-starter-What changed recently?')),
      findsNothing,
    );
  });

  testWidgets('long-pressing a user message offers copy and fork actions', (
    tester,
  ) async {
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedText = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-1', 'user', [
          Part(type: 'text', text: 'Fix the login bug'),
        ]),
      ];
    await _pumpChat(tester, api);

    // The prompt bubble's own menu (KitMessage.prompt).
    await tester.longPress(find.text('Fix the login bug'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('message-menu-copy')), findsOneWidget);
    expect(find.byKey(const ValueKey('message-menu-fork')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('message-menu-copy')));
    await tester.pumpAndSettle();
    expect(copiedText, 'Fix the login bug');
    // The kit's copy feedback (a tick and an announcement), not a snackbar.
    expect(find.byType(SnackBar), findsNothing);
  });

  testWidgets('deleting a message confirms, calls the server, and prunes it', (
    tester,
  ) async {
    final serverMessages = <MessageWithParts>[
      _message('user-1', 'user', [Part(type: 'text', text: 'first prompt')]),
      _message('user-2', 'user', [
        Part(type: 'text', text: 'second prompt'),
      ], created: 2),
    ];
    final repository = _MessageDeleteRepository(serverMessages);
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => List.of(serverMessages);
    await _pumpChat(tester, api, repository: repository);

    await tester.longPress(find.text('first prompt'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('message-menu-delete')));
    await tester.pumpAndSettle();

    expect(find.text('Delete this message?'), findsOneWidget);
    expect(find.textContaining('File changes it made'), findsOneWidget);
    await tester.tap(find.text('Delete message'));
    await tester.pumpAndSettle();

    expect(repository.deleted, [('session-1', 'user-1')]);
    expect(find.text('first prompt'), findsNothing);
    expect(find.text('second prompt'), findsOneWidget);
  });

  testWidgets('a failed message delete keeps the transcript intact', (
    tester,
  ) async {
    final serverMessages = <MessageWithParts>[
      _message('user-1', 'user', [Part(type: 'text', text: 'only prompt')]),
    ];
    final repository = _MessageDeleteRepository(serverMessages)
      ..failure = const ProductException('Message deletion is unavailable');
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => List.of(serverMessages);
    await _pumpChat(tester, api, repository: repository);

    await tester.longPress(find.text('only prompt'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('message-menu-delete')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete message'));
    await tester.pumpAndSettle();

    expect(repository.deleted, isEmpty);
    expect(find.text('only prompt'), findsOneWidget);
    expect(
      find.textContaining('Message deletion is unavailable'),
      findsOneWidget,
    );
  });

  for (final percent in [25, 75]) {
    testWidgets(
      'context usage at $percent percent is disclosed at the right level',
      (tester) async {
        final api = _FakeOpenCodeApi()
          ..messagesHandler = (_) async => [
            _message(
              'assistant-1',
              'assistant',
              [Part(type: 'text', text: 'done')],
              providerID: 'p',
              modelID: 'm',
              tokens: Tokens(input: percent * 1000, output: 0),
            ),
          ];
        final controller = await _controller(api);
        controller.catalog = const CatalogSnapshot(
          providers: [
            CatalogProvider(id: 'p', name: 'Provider', enabled: true),
          ],
          models: [
            CatalogModel(
              id: 'm',
              providerID: 'p',
              name: 'Model',
              enabled: true,
              status: 'active',
              contextLimit: 100000,
              outputLimit: 8192,
              reasoning: false,
              attachments: false,
              tools: false,
              variants: [],
            ),
          ],
          agents: [],
        );
        await _pumpChat(tester, api, controller: controller);
        await tester.pumpAndSettle();
        // The kit composer discloses high usage on the model chip; the
        // separate meter was retired in chat-3.
        final contextPercent = find.byKey(
          const Key('composer-context-percent'),
        );
        expect(contextPercent, percent >= 70 ? findsOneWidget : findsNothing);
        if (percent >= 70) {
          expect(find.text('· $percent %'), findsOneWidget);
          expect(
            find.bySemanticsLabel(RegExp('Context.*$percent')),
            findsOneWidget,
          );
        }
        // Routine usage is still reachable, with the exact value, through
        // the same Context usage destination used for warnings.
        // The conversation menu's Details (slice-P10.2).
        await tester.tap(find.byKey(const ValueKey('session-actions-button')));
        await tester.pumpAndSettle();
        final details = find.byKey(const ValueKey('session-menu-details'));
        expect(details.hitTestable(), findsOneWidget);
        await tester.tap(details);
        await tester.pumpAndSettle();
        expect(find.byType(SessionContextScreen), findsOneWidget);
        expect(
          find.text(
            percent < 50 ? '$percent% used · plenty left' : '$percent% used',
          ),
          findsOneWidget,
        );
        expect(find.text('$percent,000 of 100,000 tokens'), findsOneWidget);
        expect(
          find.bySemanticsLabel(
            'Model, $percent percent, $percent,000 of 100,000 tokens',
          ),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('composer hides the context percentage without a known limit', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    await _pumpChat(tester, api);

    expect(find.byKey(const Key('composer-context-percent')), findsNothing);
  });

  testWidgets('Ctrl+Enter sends the drafted prompt', (tester) async {
    final api = _FakeOpenCodeApi();
    await _pumpChat(tester, api);

    await tester.enterText(find.byKey(const Key('chat-composer-field')), 'go');
    await tester.pump();

    await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
    await _pumpEvent(tester);

    expect(api.promptCalls, 1);
    expect(api.prompts.single.text, 'go');
  });

  testWidgets('session sheets fit a 320dp phone at 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,

          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    // The menu scrolls at 320dp with 2x text; every entry stays reachable.
    for (final key in [
      'session-menu-find',
      'session-menu-details',
      'session-menu-rename',
    ]) {
      await tester.ensureVisible(find.byKey(ValueKey(key)));
      await tester.pumpAndSettle();
      expect(find.byKey(ValueKey(key)).hitTestable(), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });
}
