part of '../chat_live_events_test.dart';

// The command launcher and slash commands, the menu, /files, move, the
// prompt editor, the timeline, forks, inline suggestions and pills.

void _commandAndTimelineTests() {
  testWidgets('command launcher maps diff to the native session viewer', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..diffs = [
        FileDiff(file: '/workspace/lib/chat.dart', additions: 7, deletions: 2),
      ]
      ..todoItems = [
        Todo(content: 'Flatten groups', status: 'completed'),
        Todo(content: 'Verify review', status: 'pending'),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    // Changes live in the conversation menu's Go to (slice-P10.2).
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.text('\u2066chat.dart\u2069'), findsOneWidget);
    expect(find.text('+7 −2'), findsOneWidget);
  });

  testWidgets('command launcher maps context to the native usage surface', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-context', 'user', [
          Part(type: 'text', text: 'Inspect context'),
        ], created: 1),
        _message(
          'assistant-context',
          'assistant',
          [Part(type: 'text', text: 'Context ready')],
          created: 2,
          providerID: 'openai',
          modelID: 'gpt-context',
          tokens: Tokens(input: 700, output: 40, cacheRead: 260),
          cost: .031,
        ),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    // Details (the conversation's context page) is the menu's Go to.
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-details')));
    await tester.pumpAndSettle();

    expect(find.byType(SessionContextScreen), findsOneWidget);
    expect(
      find.text('1,000 tokens · limit unavailable'),
      findsOneWidget,
      reason: tester
          .widgetList<Text>(
            find.descendant(
              of: find.byType(SessionContextScreen),
              matching: find.byType(Text),
            ),
          )
          .map((w) => w.data ?? w.textSpan?.toPlainText())
          .join(' | '),
    );
  });

  testWidgets('debug command opens native app diagnostics', (tester) async {
    await _pumpChat(tester, _FakeOpenCodeApi());
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'debug',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-debug')));
    await tester.pumpAndSettle();

    expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
    expect(find.text('Report a problem'), findsOneWidget);
    expect(
      find.text(
        'Say what went wrong. You see the whole report before anything leaves this phone.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('health command opens native project health', (tester) async {
    final repository = _DestinationRepository();
    await _pumpChat(tester, _FakeOpenCodeApi(), repository: repository);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'health',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-health')));
    await tester.pumpAndSettle();

    expect(find.byType(ProjectHealthScreen), findsOneWidget);
    expect(find.text('feature/mobile'), findsOneWidget);
  });

  testWidgets('sessions command opens the all-project native finder', (
    tester,
  ) async {
    await _pumpChat(tester, _FakeOpenCodeApi());
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'sessions',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-sessions')));
    await tester.pumpAndSettle();

    expect(find.byType(GlobalSessionsScreen), findsOneWidget);
    expect(find.text('All conversations'), findsOneWidget);
  });

  // Navigation commands land on the same screens as the Settings hub rows,
  // so a typed alias and the hub never disagree about where a thing lives.
  for (final entry in <String, (Type, bool Function(Widget))>{
    'status': (ServerSettingsScreen, (_) => true),
    'connect': (
      IntegrationsScreen,
      (widget) =>
          (widget as IntegrationsScreen).mode == IntegrationsMode.providers,
    ),
    'mcps': (
      IntegrationsScreen,
      (widget) => (widget as IntegrationsScreen).mode == IntegrationsMode.mcp,
    ),
    'tools': (
      CapabilitiesScreen,
      (widget) => (widget as CapabilitiesScreen).initialTab == 1,
    ),
  }.entries) {
    testWidgets('${entry.key} command opens its Settings hub destination', (
      tester,
    ) async {
      await _pumpChat(
        tester,
        _FakeOpenCodeApi(),
        repository: _DestinationRepository(),
      );
      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        entry.key,
      );
      await tester.pump();
      await tester.tap(find.byKey(Key('command-mobile-${entry.key}')));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));

      final destination = find.byType(entry.value.$1);
      expect(destination, findsOneWidget);
      expect(entry.value.$2(tester.widget(destination)), isTrue);
      // Never the whole hub under a misleading name.
      expect(find.byType(SettingsScreen), findsNothing);
      if (entry.key == 'tools') {
        expect(find.byType(ToolsScreen), findsOneWidget);
      }
    });
  }

  testWidgets('themes command opens Settings › Appearance', (tester) async {
    final controller = await _pumpChat(tester, _FakeOpenCodeApi());
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'themes',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-themes')));
    await tester.pumpAndSettle();

    expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
    // Light or dark is chosen inline and applies at once (the separate
    // light-or-dark sheet was removed by slice-P3.1).
    final light = find.byKey(const ValueKey('appearance-mode-light'));
    await tester.ensureVisible(light);
    await tester.pumpAndSettle();
    await tester.tap(light);
    await tester.pumpAndSettle();

    expect(controller.appearance.value, AppAppearance.light);
  });

  testWidgets('the menu\'s Tasks lands on the plan in the transcript, open', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-plan', 'assistant', [
          Part(
            id: 'tool-plan',
            messageID: 'assistant-plan',
            type: 'tool',
            toolName: 'todowrite',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'input': {
                'todos': [
                  {
                    'content': 'Verify production release',
                    'status': 'pending',
                    'priority': 'high',
                  },
                ],
              },
              'output': '',
            }),
          ),
          Part(
            id: 'text-plan',
            messageID: 'assistant-plan',
            type: 'text',
            text: 'Planned the release check.',
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    // The plan's step shows closed until asked for.
    expect(find.text('Verify production release'), findsNothing);

    // The plan is a command in the sheet now (slice-P10.1).
    await _runSheetCommand(tester, 'plan');

    // No sheet: the transcript's own checklist, opened in place.
    expect(find.byKey(const Key('timeline-sheet')), findsNothing);
    expect(find.text('Verify production release'), findsOneWidget);
    expect(find.text('Pending'), findsWidgets);
  });

  testWidgets('no plan in the transcript, no Tasks entry in the menu', (
    tester,
  ) async {
    await _pumpChat(tester, _FakeOpenCodeApi());
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();

    expect(find.text('Tasks'), findsNothing);
  });

  testWidgets('launcher combines mobile actions with server commands', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _FakeProductRepository(const [
      CommandInfo(
        name: 'review',
        description: 'Review current changes',
        subtask: false,
      ),
      CommandInfo(
        name: 'init',
        description: 'Create project guidance',
        subtask: false,
      ),
    ]);

    await _pumpChat(tester, api, repository: repository);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'models',
    );
    await tester.pump();
    expect(find.text('/models'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'open',
    );
    await tester.pump();
    expect(find.text('/files'), findsOneWidget);
    expect(find.text('/editor'), findsNothing);
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'review',
    );
    await tester.pump();
    expect(find.textContaining('/review', findRichText: true), findsOneWidget);
    await tester.tap(find.byKey(const Key('command-server-review')));
    await tester.pumpAndSettle();
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, '/review ');
  });

  testWidgets('/files attaches a project artifact back to the chat', (
    tester,
  ) async {
    const path = 'docs/review.md';
    final api = _FakeOpenCodeApi()
      ..projectFiles = [FileNode(name: 'review.md', path: path, isDir: false)]
      ..fileContents[path] = const FileContent(
        '# Review\n\nPlease check this file.',
        mimeType: 'text/markdown',
      );

    await _pumpChat(tester, api);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'open',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-files')));
    await tester.pumpAndSettle();

    expect(find.text('review.md'), findsOneWidget);
    await tester.tap(find.text('review.md'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('project-file-attach')), findsOneWidget);
    expect(find.byKey(const Key('project-file-download')), findsOneWidget);

    await tester.tap(find.byKey(const Key('project-file-attach')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'review.md attached. Return to the conversation to add your comment.',
      ),
      findsOneWidget,
    );

    // Attaching closes the viewer and returns to the file tree.
    expect(find.byKey(const ValueKey('files-viewer')), findsNothing);
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel('Remove review.md'), findsOneWidget);
  });

  testWidgets('move, warp, and org commands preserve their server semantics', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _DestinationRepository();
    final controller = await _controller(api, savedProfile: true);
    controller
      ..repository = repository
      ..directory = '/work/acme'
      ..workspace = 'workspace-1'
      ..sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'Mobile work',
        projectID: 'project-1',
        workspaceID: 'workspace-1',
        directory: '/work/acme',
      );
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

    Future<void> openCommand(String command) async {
      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        command,
      );
      await tester.pump();
      await tester.tap(find.byKey(Key('command-mobile-$command')));
      await tester.pumpAndSettle();
    }

    await openCommand('move');
    expect(find.byKey(const Key('move-session-sheet')), findsOneWidget);
    expect(find.text('Current'), findsOneWidget);
    await tester.tap(find.byKey(const Key('move-destination-/work/acme-copy')));
    await tester.pumpAndSettle();
    expect(find.textContaining('1 changed file is present.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('session-destination-confirm')));
    await tester.pumpAndSettle();
    expect(repository.movedDirectory, '/work/acme-copy');
    expect(repository.movedChanges, isTrue);
    expect(repository.reminderDirectory, '/work/acme-copy');
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await openCommand('warp');
    expect(find.byKey(const Key('warp-session-sheet')), findsOneWidget);
    expect(find.text('Offline cloud'), findsOneWidget);
    await tester.tap(find.byKey(const Key('warp-destination-workspace-2')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const Key('session-destination-without-changes')),
    );
    await tester.pumpAndSettle();
    expect(repository.warpedWorkspaceID, 'workspace-2');
    expect(repository.copiedChanges, isFalse);
    expect(repository.reminderDirectory, '/remote/review');
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await openCommand('org');
    expect(find.byKey(const Key('console-organization-sheet')), findsOneWidget);
    expect(find.text('Current org'), findsOneWidget);
    await tester.tap(find.byKey(const Key('console-org-account-1-org-next')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('console-org-confirm')));
    await tester.pumpAndSettle();
    expect(repository.switchedOrganization?.orgID, 'org-next');
  });

  testWidgets('move fails closed when working changes cannot be inspected', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi();
    final repository = _DestinationRepository(healthError: true);
    final controller = await _controller(api, savedProfile: true);
    controller
      ..repository = repository
      ..directory = '/work/acme'
      ..sessionsById['session-1'] = Session(
        id: 'session-1',
        projectID: 'project-1',
        directory: '/work/acme',
      );
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
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'move',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-move')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('move-destination-/work/acme-copy')));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('could not inspect working changes'),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('session-destination-without-changes')),
      findsNothing,
    );
    await tester.tap(find.byKey(const Key('session-destination-confirm')));
    await tester.pumpAndSettle();
    expect(repository.movedChanges, isFalse);
  });

  testWidgets('prompt editor preserves selection, attachments, and cancel', (
    tester,
  ) async {
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
    final composerFinder = find.byKey(const Key('chat-composer-field'));
    final composer = tester
        .widget<TextField>(_textField(composerFinder))
        .controller!;
    composer.value = const TextEditingValue(
      text: 'Original prompt draft',
      selection: TextSelection(baseOffset: 2, extentOffset: 10),
    );
    await tester.pump();

    expect(find.byKey(const Key('prompt-editor-button')), findsOneWidget);
    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'editor',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-editor')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);
    final editor = tester
        .widget<TextField>(
          _textField(find.byKey(const Key('prompt-editor-field'))),
        )
        .controller!;
    expect(editor.text, 'Original prompt draft');
    expect(
      editor.selection,
      const TextSelection(baseOffset: 2, extentOffset: 10),
    );
    editor.selection = const TextSelection.collapsed(offset: 4);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsNothing);
    expect(
      composer.selection,
      const TextSelection(baseOffset: 2, extentOffset: 10),
    );

    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    final discardEditor = tester
        .widget<TextField>(
          _textField(find.byKey(const Key('prompt-editor-field'))),
        )
        .controller!;
    discardEditor.value = const TextEditingValue(
      text: 'Discarded edit',
      selection: TextSelection.collapsed(offset: 5),
    );
    await tester.tap(find.bySemanticsLabel('Remove notes.txt'));
    await tester.pump();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Discard prompt changes?'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-screen')), findsOneWidget);

    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Discard changes'));
    await tester.pumpAndSettle();
    expect(composer.text, 'Original prompt draft');
    expect(find.bySemanticsLabel('Remove notes.txt'), findsOneWidget);

    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();
    final savedEditor = tester
        .widget<TextField>(
          _textField(find.byKey(const Key('prompt-editor-field'))),
        )
        .controller!;
    savedEditor.value = const TextEditingValue(
      text: 'Final edited prompt',
      selection: TextSelection.collapsed(offset: 7),
    );
    await tester.tap(find.bySemanticsLabel('Remove notes.txt'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('prompt-editor-done')));
    await tester.pumpAndSettle();

    expect(composer.text, 'Final edited prompt');
    expect(composer.selection, const TextSelection.collapsed(offset: 7));
    expect(find.bySemanticsLabel('Remove notes.txt'), findsNothing);
    expect(api.promptCalls, 0);
  });

  testWidgets('timeline finds a stable anchor with reduced motion', (
    tester,
  ) async {
    final messages = <MessageWithParts>[];
    for (var index = 0; index < 30; index += 1) {
      messages.add(
        _message('user-$index', 'user', [
          Part(
            id: 'part-$index',
            messageID: 'user-$index',
            type: 'text',
            text: index == 0 ? 'oldest anchor prompt' : 'prompt $index',
          ),
        ], created: index + 1),
      );
    }
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;

    await _pumpChat(tester, api, reduceMotion: true);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    // The sheet scrolls at 320dp with 2x text; the chip stays reachable.
    await tester.ensureVisible(
      find.byKey(const ValueKey('session-menu-timeline')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('timeline-search')),
      'oldest anchor',
    );
    await tester.pump();
    expect(find.byKey(const Key('timeline-row-user-0')), findsOneWidget);

    await tester.tap(find.byKey(const Key('timeline-row-user-0')));
    await tester.pumpAndSettle();

    // A match in a prompt's own words is highlighted in place: no excerpt
    // repeating the prompt above it (review board: find bar).
    expect(
      find.descendant(
        of: find.byType(KitMarkdown),
        matching: find.text('oldest anchor prompt'),
      ),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('transcript-match-user-0/0/0')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('user-prompt-user-0')).hitTestable(),
      findsOneWidget,
    );
    // The found turn carries the find band (painted around it, so nothing
    // moves), with no fade under reduced motion; its neighbour has none.
    Finder turnOf(String id) => find.ancestor(
      of: find.byKey(ValueKey('user-prompt-$id')),
      matching: find.byType(KitTurn),
    );
    bool banded(String id) => tester
        .widgetList<CustomPaint>(
          find.descendant(of: turnOf(id), matching: find.byType(CustomPaint)),
        )
        .any((paint) => paint.painter != null);
    expect(turnOf('user-0'), findsOneWidget);
    expect(banded('user-0'), isTrue);
    final band = tester.widget<TweenAnimationBuilder<double>>(
      find
          .descendant(
            of: turnOf('user-0'),
            matching: find.byType(TweenAnimationBuilder<double>),
          )
          .first,
    );
    expect(band.duration, Duration.zero);
    if (find
        .byKey(const ValueKey('user-prompt-user-1'))
        .evaluate()
        .isNotEmpty) {
      expect(banded('user-1'), isFalse);
    }
  });

  testWidgets('fork from prompt restores text and file in the new composer', (
    tester,
  ) async {
    final prompt = _message('user-restore', 'user', [
      Part(
        id: 'text-restore',
        messageID: 'user-restore',
        type: 'text',
        text: 'Review this design',
      ),
      Part(
        id: 'file-restore',
        messageID: 'user-restore',
        type: 'file',
        filename: 'design.png',
        mime: 'image/png',
        url: 'data:image/png;base64,iVBORw0KGgo=',
      ),
    ]);
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [prompt];
    final repository = _FakeProductRepository(const []);

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('timeline-fork-user-restore')));
    await tester.pumpAndSettle();

    expect(repository.forkCalls, 1);
    expect(repository.forkMessageID, 'user-restore');
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, 'Review this design');
    expect(find.bySemanticsLabel('Remove design.png'), findsOneWidget);
  });

  testWidgets('session repository actions wait for the wake-time replacement', (
    tester,
  ) async {
    final prompt = _message('user-after-wake', 'user', [
      Part(
        id: 'text-after-wake',
        messageID: 'user-after-wake',
        type: 'text',
        text: 'Fork after wake',
      ),
    ]);
    final api = _FakeOpenCodeApi()..messagesHandler = (_) async => [prompt];
    final retainedRepository = _FakeProductRepository(const []);
    final replacementRepository = _FakeProductRepository(const []);
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final readyRepository = Completer<ProductRepository?>();
    final controller = _DelayedRepositoryController(
      ProfileStore(prefs: prefs),
      readyRepository,
    )..api = api;

    await _pumpChat(
      tester,
      api,
      repository: retainedRepository,
      controller: controller,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('timeline-fork-user-after-wake')));
    await tester.pump();

    expect(retainedRepository.forkCalls, 0);
    expect(replacementRepository.forkCalls, 0);

    readyRepository.complete(replacementRepository);
    await tester.pumpAndSettle();

    expect(retainedRepository.forkCalls, 0);
    expect(replacementRepository.forkCalls, 1);
    expect(replacementRepository.forkMessageID, 'user-after-wake');
  });

  testWidgets('/fork lists prompts and forks the selected message point', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-fork-command', 'user', [
          Part(
            id: 'fork-command-text',
            messageID: 'user-fork-command',
            type: 'text',
            text: 'Try another implementation',
          ),
        ]),
        _message('assistant-fork-command', 'assistant', [
          Part(
            id: 'assistant-command-text',
            messageID: 'assistant-fork-command',
            type: 'text',
            text: 'Current implementation',
          ),
        ]),
      ];
    final repository = _FakeProductRepository(const []);

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    // "/fork" forks the whole conversation and lands in the copy, like the
    // menu's Fork (slice-P10.2: fork lands in one place).
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/fork',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    expect(repository.forkCalls, 1);
    expect(repository.forkMessageID, isNull);
    expect(
      tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
      'forked-session',
    );
    expect(find.text('Fork from prompt'), findsNothing);
  });

  testWidgets('a prompt picked in the timeline forks from that point', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-fork-command', 'user', [
          Part(
            id: 'fork-command-text',
            messageID: 'user-fork-command',
            type: 'text',
            text: 'Try another implementation',
          ),
        ]),
      ];
    final repository = _FakeProductRepository(const []);
    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();
    final fork = find.byKey(const ValueKey('timeline-fork-user-fork-command'));
    await tester.ensureVisible(fork);
    await tester.pumpAndSettle();
    await tester.tap(fork);
    await tester.pumpAndSettle();

    expect(repository.forkMessageID, 'user-fork-command');
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, 'Try another implementation');
  });

  testWidgets('timeline remains usable at 320dp with 2x text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-narrow', 'user', [
          Part(
            id: 'part-narrow',
            messageID: 'user-narrow',
            type: 'text',
            text: 'A long prompt that must remain reachable on a narrow phone',
          ),
        ]),
      ];
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
    await tester.pumpAndSettle();

    // Typed, the command runs as the sheet's row would.
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/timestamps',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    expect(controller.transcriptTimestampsVisible, isTrue);
    expect(tester.takeException(), isNull);
    await _dismissSheetIfOpen(tester);

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey('session-menu-timeline')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('timeline-search')), findsOneWidget);
    expect(find.byKey(const Key('timeline-row-user-narrow')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'project reference screen adds an upstream directory prompt part',
    (tester) async {
      final api = _FakeOpenCodeApi();
      final repository = _FakeProductRepository(
        const [],
        references: const [
          ReferenceInfo(
            name: 'docs',
            path: '/workspace/../shared-docs',
            description: 'Shared engineering documentation',
          ),
        ],
      );

      await _pumpChat(tester, api, repository: repository);
      await _useComposerTool(tester, 'commands');
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        'references',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('command-mobile-references')));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Shared engineering documentation'),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const Key('reference-docs')));
      await tester.pumpAndSettle();

      final composer = tester.widget<TextField>(
        _textField(find.byKey(const Key('chat-composer-field'))),
      );
      expect(composer.controller?.text, '@docs');
      expect(find.bySemanticsLabel('Folder, @docs'), findsOneWidget);
      expect(find.bySemanticsLabel('Remove @docs'), findsOneWidget);
      expect(find.bySemanticsLabel('Preview attachment docs'), findsNothing);

      await tester.tap(find.byTooltip('Send'));
      await tester.pumpAndSettle();

      expect(api.prompts.single.text, '@docs');
      expect(api.prompts.single.attachments.single.toJson(), {
        'type': 'file',
        'mime': 'application/x-directory',
        'filename': 'docs',
        'url': 'file:///shared-docs',
      });
    },
  );

  testWidgets('typing slash opens filtered inline command suggestions', (
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

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/rev',
    );
    await tester.pump();

    expect(find.byKey(const Key('inline-command-suggestions')), findsOneWidget);
    expect(find.byKey(const Key('inline-command-review')), findsOneWidget);
    // A server command says what it does under its slash word too.
    expect(
      find.descendant(
        of: find.byKey(const Key('inline-command-review')),
        matching: find.text('Review current changes'),
      ),
      findsOneWidget,
    );
    expect(find.text('/models'), findsNothing);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/too',
    );
    await tester.pump();
    expect(find.byKey(const Key('inline-command-tools')), findsOneWidget);
  });

  testWidgets('inline command suggestion rows meet 44dp targets', (
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

    await _pumpChat(tester, api, repository: repository);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      '/rev',
    );
    await tester.pump();

    final row = find.byKey(const Key('inline-command-review'));
    expect(row, findsOneWidget);
    expect(tester.getSize(row).height, greaterThanOrEqualTo(44));
  });

  testWidgets(
    'nested code scrolling does not change follow latest and new events respect reading position',
    (tester) async {
      final messages = <MessageWithParts>[
        for (var index = 0; index < 35; index++)
          _message('user-$index', 'user', [
            Part(
              id: 'part-$index',
              messageID: 'user-$index',
              type: 'text',
              text: 'prompt $index',
            ),
          ], created: index + 1),
        _message('code-reply', 'assistant', [
          Part(
            id: 'code-part',
            messageID: 'code-reply',
            type: 'text',
            text: '```text\n${'wide ' * 150}\n```',
          ),
        ], created: 40),
      ];
      final api = _FakeOpenCodeApi()..messagesHandler = (_) async => messages;
      final controller = await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      final horizontal = find.descendant(
        of: find.byType(KitCodeBlock),
        matching: find.byWidgetPredicate(
          (w) =>
              w is SingleChildScrollView &&
              w.scrollDirection == Axis.horizontal,
        ),
      );
      expect(horizontal, findsOneWidget);
      await tester.drag(horizontal, const Offset(-650, 0));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('jump-to-latest')).hitTestable(),
        findsNothing,
      );
      await tester.drag(
        find.byType(ScrollablePositionedList),
        const Offset(0, 900),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('jump-to-latest')).hitTestable(),
        findsOneWidget,
      );
      controller.handleEventForTesting(
        _event('message.updated', {
          'info': {
            'id': 'new-stream',
            'sessionID': 'session-1',
            'role': 'assistant',
            'time': {'created': 100},
          },
        }),
      );
      controller.handleEventForTesting(
        _event('message.part.updated', {
          'sessionID': 'session-1',
          'part': _partJson(
            id: 'new-part',
            messageID: 'new-stream',
            type: 'text',
            text: 'Later streamed reply',
          ),
        }),
      );
      await _pumpEvent(tester);
      expect(
        find.text('Later streamed reply', findRichText: true),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('jump-to-latest')));
      // The unfinished assistant deliberately keeps its streaming indicator
      // active. Advance the navigation animation without waiting for idle.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pump(const Duration(milliseconds: 500));
      expect(
        find.text('Later streamed reply', findRichText: true),
        findsOneWidget,
      );
    },
  );

  testWidgets('floating transcript pills meet tap-target minimums', (
    tester,
  ) async {
    final messages = <MessageWithParts>[
      for (var index = 0; index < 35; index += 1)
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
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // Both pills gate on being scrolled away from the latest message.
    await tester.drag(
      find.text('prompt 34'),
      const Offset(0, 600),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();

    final earlier = find.byKey(const ValueKey('earlier-messages-pill'));
    expect(earlier, findsOneWidget);
    expect(tester.getSize(earlier).height, greaterThanOrEqualTo(44));

    final jump = find.byKey(const ValueKey('jump-to-latest'));
    expect(jump, findsOneWidget);
    final jumpSize = tester.getSize(jump);
    expect(jumpSize.width, greaterThanOrEqualTo(48));
    expect(jumpSize.height, greaterThanOrEqualTo(48));
  });
}
