part of '../chat_live_events_test.dart';

// Diffs, foreground refresh, wake-time transports for send and stop, tool
// output previews, work lines, streamed deltas and transcript controls.

void _transportAndToolTests() {
  testWidgets('renders current OpenCode unified patches and server counts', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..diffs = [
        FileDiff.fromJson({
          'file': 'lib/main.dart',
          'patch': '@@ -1 +1 @@\n-old line\n+new line',
          'additions': 1,
          'deletions': 1,
          'status': 'modified',
        }),
      ];
    await _pumpChat(tester, api);

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
    await tester.pumpAndSettle();
    expect(find.text('+1 −1'), findsOneWidget);

    expect(find.text('old line', findRichText: true), findsOneWidget);
    expect(find.text('new line', findRichText: true), findsOneWidget);
    expect(find.byType(KitDiffView), findsOneWidget);
    expect(find.byKey(const Key('review-mode-split')), findsNothing);
    final diff = tester.widget<KitDiffView>(find.byType(KitDiffView));
    expect(diff.files.single.patch, '@@ -1 +1 @@\n-old line\n+new line');
  });

  testWidgets('stages a selected diff comment as a composer reference', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..diffs = [
        FileDiff.fromJson({
          'file': 'lib/client.dart',
          'patch': '@@ -8,2 +8,2 @@\n-old request\n+new request',
          'additions': 1,
          'deletions': 1,
          'status': 'modified',
        }),
      ];
    await _pumpChat(tester, api);

    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-changes')));
    await tester.pumpAndSettle();
    // KitDiffView paints compact numbers under an expanded gutter hit area.
    await tester.tapAt(
      tester.getCenter(find.byKey(const ValueKey('review-line-0-current-8'))),
    );
    await tester.pump();
    expect(find.byKey(const Key('review-selection-bar')), findsOneWidget);

    await tester.tap(find.text('Comment'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Keep the retry behavior explicit.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    // UX-103: review stays open so a pass can stage several findings, and
    // says how many are waiting on the prompt.
    expect(find.byKey(const Key('review-workspace')), findsOneWidget);
    expect(find.textContaining('1 on prompt'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    // Let the staging snack bar clear the composer before tapping Send.
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    // The finding is a removable chip, not pre-rendered composer text.
    expect(find.byKey(const Key('composer-reference-strip')), findsOneWidget);
    expect(find.textContaining('client.dart'), findsWidgets);
    final composer = tester.widget<TextField>(
      _textField(find.byKey(const Key('chat-composer-field'))),
    );
    expect(composer.controller?.text, isEmpty);

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();

    final sent = api.prompts.single.text;
    expect(sent, contains('`lib/client.dart`'));
    expect(sent, contains('new line 8'));
    expect(sent, contains('Keep the retry behavior explicit.'));
    expect(sent, contains('```diff\nnew request\n```'));
    expect(find.byKey(const Key('composer-reference-strip')), findsNothing);
  });

  testWidgets('foreground data refresh rehydrates messages missed while away', (
    tester,
  ) async {
    var text = 'Before background';
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'text-1',
            messageID: 'assistant-1',
            type: 'text',
            text: text,
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);
    expect(find.text('Before background'), findsOneWidget);

    text = 'Completed while backgrounded';
    controller.signalDataRefreshForTesting();
    await tester.pump();
    await tester.pump();

    expect(find.text('Completed while backgrounded'), findsOneWidget);
    expect(find.text('Before background'), findsNothing);
  });

  testWidgets('composer keeps focus when the Android keyboard opens', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);

    await _pumpChat(tester, _FakeOpenCodeApi());
    final fieldFinder = find.byKey(const Key('chat-composer-field'));
    await tester.tap(fieldFinder);
    await tester.pump();
    final before = tester.widget<TextField>(_textField(fieldFinder));
    expect(before.focusNode?.hasFocus, isTrue);

    tester.view.viewInsets = const FakeViewPadding(bottom: 400);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    final after = tester.widget<TextField>(_textField(fieldFinder));
    expect(after.focusNode, same(before.focusNode));
    expect(after.focusNode?.hasFocus, isTrue);
  });

  testWidgets('send waits for the wake-time replacement transport', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final retainedApi = _FakeOpenCodeApi();
    final replacementApi = _FakeOpenCodeApi();
    final readyApi = Completer<OpenCodeApi?>();
    final controller =
        _DelayedActionController(ProfileStore(prefs: prefs), readyApi)
          ..api = retainedApi
          // Connected: offline sends queue instead of hitting the transport, and
          // this test covers the wake path where the stream is already live but
          // the action transport is still being replaced.
          ..status = StreamStatus.connected;
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
    await tester.pump();

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'send after wake',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();

    expect(retainedApi.promptCalls, 0);
    expect(replacementApi.promptCalls, 0);
    expect(
      tester
          .widget<GestureDetector>(find.byKey(const Key('chat-send-button')))
          .onTap,
      isNull,
    );

    readyApi.complete(replacementApi);
    await tester.pumpAndSettle();

    expect(retainedApi.promptCalls, 0);
    expect(replacementApi.promptCalls, 1);
    expect(replacementApi.prompts.single.text, 'send after wake');
  });

  testWidgets('stop waits for the wake-time replacement transport', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final retainedApi = _FakeOpenCodeApi()..messagesHandler = _onePrompt;
    final replacementApi = _FakeOpenCodeApi();
    final readyApi = Completer<OpenCodeApi?>();
    final controller = _DelayedActionController(
      ProfileStore(prefs: prefs),
      readyApi,
    )..api = retainedApi;
    controller.busySessions.add('session-1');
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
    await tester.pump();

    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.byKey(const Key('chat-stop-button')));
    await tester.pump();

    expect(retainedApi.abortCalls, 0);
    expect(replacementApi.abortCalls, 0);

    readyApi.complete(replacementApi);
    // The session stays busy, so the composer's activity ring animates
    // forever: pump explicit frames instead of settling.
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(retainedApi.abortCalls, 0);
    expect(replacementApi.abortCalls, 1);
  });

  testWidgets('stop failure remains visible instead of being swallowed', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = _onePrompt
      ..abortError = ApiException('server refused to stop');
    final controller = await _pumpChat(tester, api);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();

    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.byKey(const Key('chat-stop-button')));
    // Still busy after the failed stop, so the composer keeps animating.
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(api.abortCalls, 1);
    // Said in words; the raw ApiException text is details only.
    expect(find.text('server refused to stop'), findsNothing);
    expect(
      find.text(
        "The server's answer didn't make sense to the app. Try again, or "
        'report the problem.',
      ),
      findsOneWidget,
    );
  });

  test(
    'session mutations wait for the wake-time replacement transport',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final retainedApi = _FakeOpenCodeApi();
      final replacementApi = _FakeOpenCodeApi();
      final readyApi = Completer<OpenCodeApi?>();
      final controller = _DelayedActionController(
        ProfileStore(prefs: prefs),
        readyApi,
      )..api = retainedApi;
      addTearDown(controller.dispose);

      final create = controller.createSession();
      final rename = controller.renameSession('session-1', 'Renamed');
      final delete = controller.deleteSession('session-2');
      await Future<void>.delayed(Duration.zero);

      expect(retainedApi.createCalls, 0);
      expect(retainedApi.renameCalls, isEmpty);
      expect(retainedApi.deleteCalls, isEmpty);

      readyApi.complete(replacementApi);
      final created = await create;
      await Future.wait([rename, delete]);

      expect(created.id, 'session-created');
      expect(replacementApi.createCalls, 1);
      expect(replacementApi.renameCalls, [(id: 'session-1', title: 'Renamed')]);
      expect(replacementApi.deleteCalls, ['session-2']);
    },
  );

  testWidgets('renders a generated image from a tool output filePath', (
    tester,
  ) async {
    const path = '/tmp/opencode/shots/captcha-r1.png';
    final api = _FakeOpenCodeApi()
      ..fileContents[path] = const FileContent(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
        type: 'binary',
        encoding: 'base64',
        mimeType: 'image/png',
      )
      ..messagesHandler = (_) async => [
        _message('assistant-image', 'assistant', [
          Part(
            id: 'tool-image',
            messageID: 'assistant-image',
            type: 'tool',
            toolName: 'browser screenshot',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'title': 'Capture CAPTCHA',
              'input': const <String, dynamic>{},
              'output': {'filePath': path},
            }),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(api.fileContentRequests, [path]);
    expect(find.byKey(const Key('tool-output-image')), findsOneWidget);
    expect(
      find.bySemanticsLabel('Preview generated image captcha-r1.png'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('tool-output-image')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-viewer-image')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('file-preview-attach')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsNothing);
    expect(find.bySemanticsLabel('Remove captcha-r1.png'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please inspect this CAPTCHA.',
    );
    await tester.pump();
    final send = find.byKey(const Key('chat-send-button'));
    expect(send.hitTestable(), findsOneWidget);
    await tester.tap(send);
    await tester.pumpAndSettle();

    expect(api.prompts.single.text, 'Please inspect this CAPTCHA.');
    expect(api.prompts.single.attachments.single.filename, 'captcha-r1.png');
    expect(api.prompts.single.attachments.single.mime, 'image/png');
    expect(
      api.prompts.single.attachments.single.url,
      startsWith('data:image/png;base64,'),
    );
  });

  testWidgets('tool output previews wait for the wake-time transport', (
    tester,
  ) async {
    const path = '/tmp/opencode/shots/after-wake.png';
    final retainedApi = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-after-wake', 'assistant', [
          Part(
            id: 'tool-after-wake',
            messageID: 'assistant-after-wake',
            type: 'tool',
            toolName: 'browser screenshot',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'title': 'Capture after wake',
              'input': const <String, dynamic>{},
              'output': {'filePath': path},
            }),
          ),
        ]),
      ];
    final replacementApi = _FakeOpenCodeApi()
      ..fileContents[path] = const FileContent(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
        type: 'binary',
        encoding: 'base64',
        mimeType: 'image/png',
      );
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final readyApi = Completer<OpenCodeApi?>();
    final controller = _DelayedActionController(
      ProfileStore(prefs: prefs),
      readyApi,
    )..api = retainedApi;

    await _pumpChat(tester, retainedApi, controller: controller);

    expect(retainedApi.fileContentRequests, isEmpty);
    expect(replacementApi.fileContentRequests, isEmpty);

    readyApi.complete(replacementApi);
    await tester.pumpAndSettle();

    expect(retainedApi.fileContentRequests, isEmpty);
    expect(replacementApi.fileContentRequests, [path]);
    expect(find.byKey(const Key('tool-output-image')), findsOneWidget);
  });

  testWidgets('opens non-image tool files in the shared preview', (
    tester,
  ) async {
    const path = '/tmp/opencode/report.md';
    final api = _FakeOpenCodeApi()
      ..fileContents[path] = const FileContent(
        '# Review me\n\n| File | Status |\n| --- | --- |\n| api.dart | Ready |',
        mimeType: 'text/markdown',
      )
      ..messagesHandler = (_) async => [
        _message('assistant-file', 'assistant', [
          Part(
            id: 'tool-file',
            messageID: 'assistant-file',
            type: 'tool',
            toolName: 'write',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'output': {'filePath': path},
            }),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(api.fileContentRequests, isEmpty);
    expect(find.byKey(const Key('tool-output-file')), findsOneWidget);
    await tester.tap(find.byKey(const Key('tool-output-file')));
    await tester.pumpAndSettle();

    expect(api.fileContentRequests, [path]);
    expect(find.byType(KitMarkdown), findsWidgets);
    expect(find.text('api.dart'), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-viewer-menu-source')));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('| File | Status |', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets(
    'generated CSV opens as a literal table without sending a prompt',
    (tester) async {
      const path = '/tmp/opencode/report.csv';
      final api = _FakeOpenCodeApi()
        ..fileContents[path] = const FileContent(
          'name,value\nentry,=SUM(A1)\n',
          mimeType: 'text/csv',
        )
        ..messagesHandler = (_) async => [
          _message('assistant-csv', 'assistant', [
            Part(
              id: 'tool-csv',
              messageID: 'assistant-csv',
              type: 'tool',
              toolName: 'write',
              toolState: ToolState.fromJson({
                'status': 'completed',
                'output': {'filePath': path},
              }),
            ),
          ]),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('tool-output-file')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-viewer-table')), findsOneWidget);
      expect(find.text(KitBidi.auto('name')), findsOneWidget);
      expect(find.text(KitBidi.auto('value')), findsOneWidget);
      expect(find.text(KitBidi.auto('=SUM(A1)')), findsOneWidget);
      expect(api.prompts, isEmpty);
    },
  );

  testWidgets('a finished turn gathers its tool chain into one work line; '
      'the words it said stay in view', (tester) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-tools', 'assistant', [
          Part(
            id: 'read-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'read',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'filePath': '/workspace/lib/main.dart'},
              'output': '<content>\n1: void main() {}\n</content>',
            }, toolName: 'read'),
          ),
          Part(
            id: 'grep-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'grep',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'pattern': 'main', 'path': '/workspace/lib'},
              'output': 'lib/main.dart:1:void main() {}',
              'metadata': {'matches': 1},
            }, toolName: 'grep'),
          ),
          Part(
            id: 'shell-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'command': 'flutter test'},
              'output': 'All tests passed.',
              'metadata': {'exit': 0},
            }, toolName: 'bash'),
          ),
          Part(
            id: 'text-boundary',
            messageID: 'assistant-tools',
            type: 'text',
            text: 'Tool chain finished.',
          ),
          Part(
            id: 'edit-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {
                'filePath': '/workspace/lib/main.dart',
                'oldString': 'old',
                'newString': 'new',
              },
              'output': 'done',
            }, toolName: 'edit'),
          ),
          Part(
            id: 'write-1',
            messageID: 'assistant-tools',
            type: 'tool',
            toolName: 'write',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'filePath': '/workspace/notes.md', 'content': '# Done'},
              'output': 'done',
            }, toolName: 'write'),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // The turn is over: its work is one line (the turn model, gap 17), and
    // the words stay below it.
    expect(find.byKey(const Key('work-group')), findsOneWidget);
    // What was done is the title; no generic "Tools" beside it.
    expect(find.text('Tools'), findsNothing);
    expect(find.textContaining('Read 1 file'), findsOneWidget);
    expect(find.textContaining('dited 2 files'), findsOneWidget);
    expect(find.text('Tool chain finished.'), findsOneWidget);
    expect(
      tester.getTopLeft(find.byKey(const Key('work-group'))).dy,
      lessThan(tester.getTopLeft(find.text('Tool chain finished.')).dy),
    );
    expect(find.text('Shell'), findsNothing);
    expect(find.text('Read'), findsNothing);
    expect(find.text('Search text'), findsNothing);
    expect(find.text('Edit'), findsNothing);

    final headers = find.byKey(const Key('work-group-header'));
    expect(tester.getSize(headers.first).height, greaterThanOrEqualTo(48));
    await tester.tap(headers.first);
    await tester.pumpAndSettle();

    expect(find.text('Read'), findsOneWidget);
    expect(find.text('Search text'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);

    // KitToolRow: a grouped row is a line on the ground surface, never a
    // nested card; at rest it paints no fill of its own.
    final rows = find.byKey(const Key('embedded-tool-row'));
    expect(rows, findsWidgets);
    for (final box in tester.widgetList<AnimatedContainer>(
      find.descendant(of: rows, matching: find.byType(AnimatedContainer)),
    )) {
      final decoration = box.decoration;
      if (decoration is ShapeDecoration) {
        expect(
          decoration.color?.a ?? 0,
          0,
          reason: 'Grouped tool rows must not render nested cards.',
        );
      }
    }
    expect(
      find.descendant(of: rows, matching: find.byType(Card)),
      findsNothing,
    );
  });

  testWidgets('renders grouped tool failures as flat inline results', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-errors', 'assistant', [
          Part(
            id: 'failed-edit',
            messageID: 'assistant-errors',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson(const {
              'status': 'error',
              'input': {'filePath': '/workspace/lib/main.dart'},
              'error': 'Tool execution aborted',
            }, toolName: 'edit'),
          ),
          Part(
            id: 'failed-shell',
            messageID: 'assistant-errors',
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState.fromJson(const {
              'status': 'error',
              'input': {'command': 'flutter test'},
              'error': 'Process exited before completion',
            }, toolName: 'bash'),
          ),
        ]),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(
      find.byKey(const Key('embedded-tool-error-output')),
      findsNWidgets(2),
    );
    expect(find.text('Tool execution aborted'), findsOneWidget);
    expect(find.text('Process exited before completion'), findsOneWidget);
    expect(find.byKey(const Key('standalone-tool-error-output')), findsNothing);

    // Each failure is the step's own capped output block (KitCodeBlock)
    // under its row, carrying the error text; the row already says Failed.
    final outputs = find.byKey(const Key('embedded-tool-error-output'));
    expect(
      find.descendant(of: outputs, matching: find.byType(KitCodeBlock)),
      findsNWidgets(2),
    );
    expect(
      find.descendant(
        of: outputs,
        matching: find.text('Process exited before completion'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('keeps a tool chain growing across assistant records', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-edit', 'assistant', [
          Part(
            id: 'edit-across-message',
            messageID: 'assistant-edit',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {
                'filePath': '/workspace/lib/main.dart',
                'oldString': 'old',
                'newString': 'new',
              },
              'output': 'done',
            }, toolName: 'edit'),
          ),
        ], created: 1),
        _message('assistant-shell', 'assistant', [
          Part(
            id: 'shell-across-message',
            messageID: 'assistant-shell',
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'command': 'flutter test'},
              'output': 'All tests passed.',
              'metadata': {'exit': 0},
            }, toolName: 'bash'),
          ),
        ], created: 2),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(find.text('Edited 1 file · ran 1 command'), findsOneWidget);
    expect(find.text('Edit'), findsNothing);
    expect(find.text('Shell'), findsNothing);

    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();

    expect(find.text('Edit'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);
  });

  testWidgets('shows model changes and aggregates usage once per turn', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(800, 1400));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-1', 'user', [
          Part(type: 'text', text: 'Inspect this'),
        ], created: 1),
        _message(
          'assistant-0',
          'assistant',
          [Part(type: 'text', text: 'Earlier turn')],
          created: 2,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 60, output: 15),
        ),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Continue'),
        ], created: 3),
        _message(
          'assistant-1',
          'assistant',
          [Part(type: 'text', text: 'First internal step')],
          created: 4,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 70, output: 30),
        ),
        _message(
          'assistant-2',
          'assistant',
          [Part(type: 'text', text: 'Second internal step')],
          created: 5,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 150, output: 50),
        ),
        _message(
          'assistant-3',
          'assistant',
          [Part(type: 'text', text: 'Model switched here')],
          created: 6,
          providerID: 'provider',
          modelID: 'model-b',
          tokens: Tokens(input: 40, output: 10),
        ),
      ];

    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // A model switch is named once, in the footer of the turn it happened
    // in; usage rides on the timestamps preference. Only the newest turn's
    // footer carries these words (older turns keep Copy and More).
    expect(
      find.textContaining('provider/model-a → provider/model-b'),
      findsOneWidget,
    );
    expect(find.textContaining('350 tok'), findsNothing);
    await controller.setTranscriptTimestampsVisible(true);
    await tester.pumpAndSettle();
    expect(find.textContaining('350 tok'), findsOneWidget);
    expect(find.textContaining('75 tok'), findsNothing);
    Finder usageSegment(String value) => find.byWidgetPredicate((widget) {
      if (widget is! Text || widget.data == null) return false;
      return widget.data!
          .split(RegExp(r'\s+·\s+'))
          .map((segment) => segment.trim())
          .contains(value);
    });
    expect(usageSegment('100 tok'), findsNothing);
    expect(usageSegment('200 tok'), findsNothing);
    expect(usageSegment('50 tok'), findsNothing);
  });

  testWidgets('applies split text, reasoning, and tool input deltas', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-1', 'assistant', [
          Part(id: 'text-1', messageID: 'assistant-1', type: 'text'),
          Part(id: 'reasoning-1', messageID: 'assistant-1', type: 'reasoning'),
          Part(
            id: 'tool-1',
            messageID: 'assistant-1',
            type: 'tool',
            toolName: 'search',
            toolState: ToolState(status: 'running'),
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);

    void delta(String partID, String field, String value) {
      controller.handleEventForTesting(
        _event('message.part.delta', {
          'sessionID': 'session-1',
          'messageID': 'assistant-1',
          'partID': partID,
          'field': field,
          'delta': value,
        }),
      );
    }

    delta('text-1', 'text', 'Hel');
    delta('text-1', 'text', 'lo');
    delta('reasoning-1', 'text', '**why ');
    delta('reasoning-1', 'text', 'this works**');
    delta('tool-1', 'input', '{"query":');
    delta('tool-1', 'input', '"chat"}');
    await _pumpEvent(tester);

    expect(find.text('Hello'), findsOneWidget);
    // A one-line thought right before a tool call is that call's title: the
    // step is one row, not a heading row and a tool row.
    expect(find.byKey(const Key('reasoning-toggle')), findsNothing);
    expect(find.text('why this works'), findsOneWidget);
    expect(find.text('**why this works**'), findsNothing);
    // An id the app has no words for reads as words ("search" → "Search").
    expect(find.textContaining('Search'), findsOneWidget);
    await tester.tap(find.text('why this works'));
    await _pumpEvent(tester);
    expect(
      jsonDecode(tester.widget<KitCodeBlock>(find.byType(KitCodeBlock)).text),
      {'query': 'chat'},
    );
    semantics.dispose();
  });

  testWidgets('merges consecutive reasoning and assistant text blocks', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-reasoning-1', 'assistant', [
          Part(
            id: 'reasoning-a',
            messageID: 'assistant-reasoning-1',
            type: 'reasoning',
            text: 'First reasoning fragment with enough detail to wrap.',
          ),
        ]),
        _message('assistant-reasoning-2', 'assistant', [
          Part(
            id: 'reasoning-b',
            messageID: 'assistant-reasoning-2',
            type: 'reasoning',
            text: 'Second reasoning fragment continues the same thought.',
          ),
        ], created: 2),
        _message('assistant-text-1', 'assistant', [
          Part(type: 'text', text: 'First answer paragraph.'),
        ], created: 3),
        _message('assistant-text-2', 'assistant', [
          Part(type: 'text', text: 'Second answer paragraph.'),
        ], created: 4),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('assistant-reasoning-block')), findsOneWidget);
    expect(find.byKey(const Key('assistant-text-block')), findsOneWidget);
    expect(find.byKey(const Key('reasoning-toggle')), findsOneWidget);
    await tester.tap(find.byKey(const Key('reasoning-toggle')));
    await tester.pumpAndSettle();
    // The fold is titled by the thought's first line, and opened it reads
    // whole: both fragments in the one block.
    expect(find.textContaining('First reasoning fragment'), findsWidgets);
    expect(find.textContaining('Second reasoning fragment'), findsOneWidget);
    expect(find.text('First answer paragraph.'), findsOneWidget);
    expect(find.text('Second answer paragraph.'), findsOneWidget);
  });

  testWidgets('transcript controls update old messages and persist state', (
    tester,
  ) async {
    const reasoning =
        'This is a deliberately long reasoning explanation that spans several lines on a phone and starts collapsed.';
    final created = DateTime.now().millisecondsSinceEpoch;
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('user-display', 'user', [
          Part(
            id: 'user-display-text',
            messageID: 'user-display',
            type: 'text',
            text: 'Explain the implementation',
          ),
        ], created: created),
        _message('assistant-display', 'assistant', [
          Part(
            id: 'assistant-display-reasoning',
            messageID: 'assistant-display',
            type: 'reasoning',
            text: reasoning,
          ),
          Part(
            id: 'assistant-display-text',
            messageID: 'assistant-display',
            type: 'text',
            text: 'Here is the implementation.',
          ),
        ], created: created + 1),
      ];

    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    expect(find.text(reasoning), findsNothing);
    expect(
      tester
          .widgetList<KitMessage>(find.byType(KitMessage))
          .singleWhere(
            (message) =>
                message.bubbleKey == const ValueKey('user-prompt-user-display'),
          )
          .time,
      isNull,
    );

    await _useComposerTool(tester, 'commands');
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'thinking',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('command-mobile-thinking')));
    await tester.pumpAndSettle();

    expect(controller.transcriptReasoningExpanded, isTrue);
    expect(find.text(reasoning), findsOneWidget);
    // The toggle offers Undo; the bar floats above every sheet until its
    // window ends.
    expect(find.text('Reasoning expanded in the transcript'), findsOneWidget);
    await tester.pump(KitUndo.window);
    await tester.pumpAndSettle();

    // The timestamps toggle is a command in the sheet (slice-P10.1).
    await _runSheetCommand(tester, 'timestamps');

    expect(controller.transcriptTimestampsVisible, isTrue);
    expect(
      tester
          .widgetList<KitMessage>(find.byType(KitMessage))
          .singleWhere(
            (message) =>
                message.bubbleKey == const ValueKey('user-prompt-user-display'),
          )
          .time,
      DateTime.fromMillisecondsSinceEpoch(created),
    );
    expect(
      find.byKey(const Key('message-meta-assistant-display')),
      findsOneWidget,
    );
    await tester.pump(KitUndo.window);
    await tester.pumpAndSettle();
    await _runSheetCommand(tester, 'thinking');

    expect(controller.transcriptReasoningExpanded, isFalse);
    expect(find.text(reasoning), findsNothing);
    await tester.pump(KitUndo.window);
    await tester.pumpAndSettle();
  });

  testWidgets('the transcript-wide reasoning toggle replaces per-part choices '
      'instead of stamping over them', (tester) async {
    const first =
        'A deliberately long first reasoning explanation that spans several lines on a phone and so starts collapsed.';
    const second =
        'A deliberately long second reasoning explanation that also spans several lines and starts collapsed too.';
    final created = DateTime.now().millisecondsSinceEpoch;
    final api = _FakeOpenCodeApi()
      ..messagesHandler = (_) async => [
        _message('assistant-one', 'assistant', [
          Part(
            id: 'reasoning-one',
            messageID: 'assistant-one',
            type: 'reasoning',
            text: first,
          ),
          Part(
            id: 'text-one',
            messageID: 'assistant-one',
            type: 'text',
            text: 'First answer.',
          ),
        ], created: created),
        // Two turns: within one, a finished turn folds its earlier thoughts.
        _message('user-two', 'user', [
          Part(
            id: 'user-two-text',
            messageID: 'user-two',
            type: 'text',
            text: 'And again',
          ),
        ], created: created),
        _message('assistant-two', 'assistant', [
          Part(
            id: 'reasoning-two',
            messageID: 'assistant-two',
            type: 'reasoning',
            text: second,
          ),
          Part(
            id: 'text-two',
            messageID: 'assistant-two',
            type: 'text',
            text: 'Second answer.',
          ),
        ], created: created + 1),
      ];

    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    expect(find.text(first), findsNothing);
    expect(find.text(second), findsNothing);

    // One per-part expansion, which the session store remembers. The
    // transcript renders reversed, so assert on the count rather than on
    // which of the two blocks the first toggle belongs to.
    await tester.tap(find.byKey(const Key('reasoning-toggle')).first);
    await tester.pumpAndSettle();
    expect(
      find.text(first).evaluate().length + find.text(second).evaluate().length,
      1,
    );

    Future<void> flipGlobal() async {
      await _runSheetCommand(tester, 'thinking');
      await tester.pump(KitUndo.window);
      await tester.pumpAndSettle();
    }

    // The transcript-wide default wins while it is being set...
    await flipGlobal();
    expect(controller.transcriptReasoningExpanded, isTrue);
    expect(find.text(first), findsOneWidget);
    expect(find.text(second), findsOneWidget);

    // ...and flipping it back collapses everything, including the block the
    // user had opened, rather than leaving the store stamped with values the
    // user never chose.
    await flipGlobal();
    expect(controller.transcriptReasoningExpanded, isFalse);
    expect(find.text(first), findsNothing);
    expect(find.text(second), findsNothing);

    // Per-part control still works after the round trip.
    await tester.tap(find.byKey(const Key('reasoning-toggle')).last);
    await tester.pumpAndSettle();
    expect(
      find.text(first).evaluate().length + find.text(second).evaluate().length,
      1,
    );
  });
}
