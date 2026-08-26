import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeOpenCodeApi extends OpenCodeApi {
  _FakeOpenCodeApi() : super(baseUrl: 'http://localhost');

  Future<List<MessageWithParts>> Function(String id)? messagesHandler;
  Completer<void>? promptCompleter;
  int promptCalls = 0;
  final List<
    ({
      String text,
      ModelRef? model,
      String? variant,
      List<PromptAttachment> attachments,
    })
  >
  prompts = [];
  String? slashCommandName;
  String? slashArguments;
  ModelRef? slashModel;
  String? slashVariant;
  bool failRename = false;
  bool failDelete = false;
  List<FileDiff> diffs = const [];
  final Map<String, FileContent> fileContents = {};
  final List<String> fileContentRequests = [];

  @override
  Future<List<MessageWithParts>> messages(String id) =>
      messagesHandler?.call(id) ?? Future.value([]);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => diffs;

  @override
  Future<FileContent> fileContent(String path) async {
    fileContentRequests.add(path);
    final content = fileContents[path];
    if (content == null) throw StateError('missing fixture: $path');
    return content;
  }

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
  }) {
    promptCalls += 1;
    prompts.add((
      text: text,
      model: model,
      variant: variant,
      attachments: attachments,
    ));
    return promptCompleter?.future ?? Future.value();
  }

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    slashCommandName = command;
    slashArguments = args;
    slashModel = model;
    slashVariant = variant;
  }

  @override
  Future<void> renameSession(String id, String title) async {
    if (failRename) throw StateError('rename failed');
  }

  @override
  Future<void> deleteSession(String id) async {
    if (failDelete) throw StateError('delete failed');
  }
}

Future<ConnectionController> _controller(_FakeOpenCodeApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))..api = api;
}

class _DelayedActionController extends ConnectionController {
  _DelayedActionController(super.store, this.readyApi);

  final Completer<OpenCodeApi?> readyApi;

  @override
  Future<OpenCodeApi?> prepareActionTransport() => readyApi.future;
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  int created = 1,
  String? providerID,
  String? modelID,
  Tokens? tokens,
  double cost = 0,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    providerID: providerID,
    modelID: modelID,
    tokens: tokens,
    cost: cost,
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: parts,
);

EventEnvelope _event(String type, Map<String, dynamic> properties) =>
    EventEnvelope(type: type, properties: properties);

Map<String, dynamic> _partJson({
  required String id,
  required String messageID,
  required String type,
  String text = '',
  String? filename,
  String? tool,
  Map<String, dynamic>? state,
}) => {
  'id': id,
  'sessionID': 'session-1',
  'messageID': messageID,
  'type': type,
  'text': text,
  'filename': ?filename,
  'tool': ?tool,
  'state': ?state,
};

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _FakeOpenCodeApi api,
) async {
  final controller = await _controller(api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
  return controller;
}

Future<void> _pumpEvent(WidgetTester tester) async {
  await tester.pump();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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

    await tester.tap(find.byTooltip('Changes'));
    await tester.pumpAndSettle();
    expect(find.text('+1'), findsOneWidget);
    expect(find.text('-1'), findsOneWidget);

    await tester.tap(find.text('main.dart'));
    await tester.pumpAndSettle();
    expect(find.text('@@ -1 +1 @@'), findsOneWidget);
    expect(find.text('-old line'), findsOneWidget);
    expect(find.text('+new line'), findsOneWidget);
    expect(find.byTooltip('Copy patch'), findsOneWidget);
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

  testWidgets('send waits for the wake-time replacement transport', (
    tester,
  ) async {
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

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
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
          .widget<IconButton>(find.byKey(const Key('chat-send-button')))
          .onPressed,
      isNull,
    );

    readyApi.complete(replacementApi);
    await tester.pumpAndSettle();

    expect(retainedApi.promptCalls, 0);
    expect(replacementApi.promptCalls, 1);
    expect(replacementApi.prompts.single.text, 'send after wake');
  });

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
    expect(find.text('captcha-r1.png'), findsOneWidget);

    await tester.tap(find.byKey(const Key('tool-output-image')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-image')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);

    await tester.tap(find.byKey(const Key('file-preview-attach')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsNothing);
    expect(
      find.bySemanticsLabel('Remove attachment captcha-r1.png'),
      findsOneWidget,
    );

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Please inspect this CAPTCHA.',
    );
    await tester.pump();
    final sendButton = tester.widget<IconButton>(
      find.byKey(const Key('chat-send-button')),
    );
    expect(sendButton.onPressed, isNotNull);
    sendButton.onPressed!();
    await tester.pumpAndSettle();

    expect(api.prompts.single.text, 'Please inspect this CAPTCHA.');
    expect(api.prompts.single.attachments.single.filename, 'captcha-r1.png');
    expect(api.prompts.single.attachments.single.mime, 'image/png');
    expect(
      api.prompts.single.attachments.single.url,
      startsWith('data:image/png;base64,'),
    );
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
    expect(find.byKey(const Key('file-preview-text')), findsOneWidget);
    expect(find.byType(DataTable), findsOneWidget);
    expect(find.text('api.dart'), findsOneWidget);
    expect(find.byKey(const Key('file-preview-download')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-attach')), findsOneWidget);

    await tester.tap(find.byKey(const Key('file-preview-raw-mode')));
    await tester.pump();
    expect(find.byType(DataTable), findsNothing);
    expect(find.textContaining('| File | Status |'), findsOneWidget);
  });

  testWidgets('groups a tool chain until assistant text appears', (
    tester,
  ) async {
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

    expect(find.byKey(const Key('tool-call-group')), findsNWidgets(2));
    expect(find.text('Tools'), findsNWidgets(2));
    expect(find.text('3 calls · read · search · shell'), findsOneWidget);
    expect(find.text('2 calls · edit · write'), findsOneWidget);
    expect(find.text('Tool chain finished.'), findsOneWidget);
    expect(find.text('Shell'), findsNothing);
    expect(find.text('Read'), findsNothing);
    expect(find.text('Search text'), findsNothing);
    expect(find.text('Edit'), findsNothing);

    final headers = find.byKey(const Key('tool-call-group-header'));
    expect(tester.getSize(headers.first).height, greaterThanOrEqualTo(48));
    await tester.tap(headers.first);
    await tester.pumpAndSettle();

    expect(find.text('Read'), findsOneWidget);
    expect(find.text('Search text'), findsOneWidget);
    expect(find.text('Shell'), findsOneWidget);
    expect(find.text('Edit'), findsNothing);
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

    expect(find.byKey(const Key('tool-call-group')), findsOneWidget);
    expect(find.text('2 calls · edit · shell'), findsOneWidget);
    expect(find.text('Edit'), findsNothing);
    expect(find.text('Shell'), findsNothing);

    await tester.tap(find.byKey(const Key('tool-call-group-header')));
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
          'assistant-1',
          'assistant',
          [Part(type: 'text', text: 'First internal step')],
          created: 2,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 70, output: 30),
        ),
        _message(
          'assistant-2',
          'assistant',
          [Part(type: 'text', text: 'Second internal step')],
          created: 3,
          providerID: 'provider',
          modelID: 'model-a',
          tokens: Tokens(input: 150, output: 50),
        ),
        _message(
          'assistant-3',
          'assistant',
          [Part(type: 'text', text: 'Model switched here')],
          created: 4,
          providerID: 'provider',
          modelID: 'model-b',
          tokens: Tokens(input: 40, output: 10),
        ),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Continue'),
        ], created: 5),
        _message(
          'assistant-4',
          'assistant',
          [Part(type: 'text', text: 'Same model, next turn')],
          created: 6,
          providerID: 'provider',
          modelID: 'model-b',
          tokens: Tokens(input: 60, output: 15),
        ),
      ];

    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    expect(find.textContaining('provider/model-a'), findsOneWidget);
    expect(find.textContaining('provider/model-b'), findsOneWidget);
    expect(find.textContaining('350 tok'), findsOneWidget);
    expect(find.textContaining('75 tok'), findsOneWidget);
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
    delta('reasoning-1', 'text', 'why ');
    delta('reasoning-1', 'text', 'this works');
    delta('tool-1', 'input', '{"query":');
    delta('tool-1', 'input', '"chat"}');
    await _pumpEvent(tester);

    expect(find.text('Hello'), findsOneWidget);
    final reasoningToggle = find.byKey(const Key('reasoning-toggle'));
    expect(tester.getSize(reasoningToggle).height, greaterThanOrEqualTo(48));
    expect(find.bySemanticsLabel('Expand reasoning details'), findsOneWidget);
    await tester.tap(reasoningToggle);
    await _pumpEvent(tester);
    expect(find.text('why this works'), findsOneWidget);
    expect(find.bySemanticsLabel('Collapse reasoning details'), findsOneWidget);
    final reasoningTextNode = tester.getSemantics(find.text('why this works'));
    for (
      var ancestor = reasoningTextNode.parent;
      ancestor != null;
      ancestor = ancestor.parent
    ) {
      expect(
        ancestor.flagsCollection.isButton,
        isFalse,
        reason:
            'Expanded reasoning text must remain readable outside the button',
      );
    }
    await tester.tap(find.text('search'));
    await _pumpEvent(tester);
    expect(find.textContaining('"query": "chat"'), findsOneWidget);
    semantics.dispose();
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
        child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
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
      await tester.tap(find.byTooltip('Send'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'second prompt');
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
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
      expect(find.textContaining('Send failed:'), findsOneWidget);
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
    expect(find.text('thinking…'), findsOneWidget);

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

    expect(find.text('thinking…'), findsNothing);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    expect(
      find.text('Sign in to the selected model provider.'),
      findsOneWidget,
    );
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
    expect(find.text('PDF · prompt attachment'), findsOneWidget);
    expect(find.text('Review this image'), findsOneWidget);
    expect(find.text('diagram.png'), findsOneWidget);
    expect(find.text('PNG · prompt attachment'), findsOneWidget);
    expect(
      find.bySemanticsLabel('Preview attachment report.pdf'),
      findsOneWidget,
    );
    expect(
      find.bySemanticsLabel('Preview attachment diagram.png'),
      findsOneWidget,
    );

    final diagram = find.text('diagram.png');
    await tester.ensureVisible(diagram);
    await tester.pumpAndSettle();
    await tester.tap(diagram);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-image')), findsOneWidget);
    expect(find.text('Pinch to zoom'), findsOneWidget);
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

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry last prompt'));
    await tester.pumpAndSettle();

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
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry last prompt'));
    await tester.pumpAndSettle();

    expect(api.prompts.last.text, isEmpty);
    expect(api.prompts.last.attachments.single.toJson(), {
      'type': 'file',
      'mime': 'text/plain',
      'filename': 'notes.txt',
      'url': textUrl,
    });
  });

  testWidgets('manual slash command passes the selected model', (tester) async {
    final api = _FakeOpenCodeApi();
    final controller = await _pumpChat(tester, api);
    controller.selectedModel = ModelRef(
      providerID: 'anthropic',
      modelID: 'claude-sonnet',
    );
    controller.selectedVariant = 'high';

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Run command'));
    await tester.pumpAndSettle();
    final commandField = find.byWidgetPredicate(
      (widget) =>
          widget is TextField && widget.decoration?.labelText == 'Command',
    );
    final argumentsField = find.byWidgetPredicate(
      (widget) =>
          widget is TextField && widget.decoration?.labelText == 'Arguments',
    );
    await tester.enterText(commandField, '/review');
    await tester.enterText(argumentsField, '--staged');
    await tester.tap(find.widgetWithText(FilledButton, 'Run'));
    await tester.pumpAndSettle();

    expect(api.slashCommandName, 'review');
    expect(api.slashArguments, '--staged');
    expect(api.slashModel?.providerID, 'anthropic');
    expect(api.slashModel?.modelID, 'claude-sonnet');
    expect(api.slashVariant, 'high');
  });

  testWidgets('session rename and delete failures preserve the session', (
    tester,
  ) async {
    final api = _FakeOpenCodeApi()
      ..failRename = true
      ..failDelete = true;
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    controller.sessionsById = {
      'session-1': Session(
        id: 'session-1',
        title: 'Original title',
        time: SessionTime(created: 1, updated: 1),
      ),
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: SessionsTab(controller: controller)),
      ),
    );

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not delete chat:'), findsOneWidget);
    expect(controller.sessionsById, contains('session-1'));
    expect(find.text('Original title'), findsOneWidget);
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rename'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Changed title');
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not rename chat:'), findsOneWidget);
    expect(controller.sessionsById['session-1']?.title, 'Original title');
    expect(find.text('Original title'), findsOneWidget);
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

    await tester.tap(find.byTooltip('Attach file'));
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

    final remove = find.byTooltip('Remove attachment notes.txt');
    expect(remove, findsOneWidget);
    final size = tester.getSize(remove);
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
    expect(
      find.bySemanticsLabel('Remove attachment notes.txt'),
      findsOneWidget,
    );

    final preview = find.bySemanticsLabel('Preview attachment notes.txt');
    expect(
      tester.getSemantics(preview),
      matchesSemantics(
        label: 'Preview attachment notes.txt',
        isButton: true,
        hasTapAction: true,
      ),
    );
    await tester.tap(preview);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('file-preview-sheet')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-text')), findsOneWidget);
    expect(find.text('notes'), findsOneWidget);
    await tester.tap(find.byTooltip('Close preview'));
    await tester.pumpAndSettle();

    await tester.tap(remove);
    await tester.pumpAndSettle();
    expect(remove, findsNothing);
    semantics.dispose();
  });
}
