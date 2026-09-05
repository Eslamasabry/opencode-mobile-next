import 'support/complete_message_history.dart';
// Tests for the chat-transcript lens fixes (C1–C14, T1):
// streaming delta batching, expansion survival across list recycling,
// assistant long-press actions, and earlier-messages pill gating.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _TranscriptApi extends OpenCodeApi with CompleteMessageHistory {
  _TranscriptApi() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> Function()? messagesBuilder;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      messagesBuilder?.call() ?? [];

  @override
  Future<Session> session(String id) async => Session(id: id);
}

class _TranscriptRepository implements ProductRepository {
  final deletedMessageIDs = <String>[];

  @override
  Future<void> deleteMessage({
    required String sessionID,
    required String messageID,
  }) async {
    deletedMessageIDs.add(messageID);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  int created = 1,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: parts,
);

Future<ConnectionController> _controller(_TranscriptApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
}

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _TranscriptApi api,
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

void _delta(
  ConnectionController controller,
  String messageID,
  String partID,
  String value,
) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'message.part.delta',
      properties: {
        'sessionID': 'session-1',
        'messageID': messageID,
        'partID': partID,
        'field': 'text',
        'delta': value,
      },
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a burst of streaming deltas rebuilds the transcript once', (
    tester,
  ) async {
    final api = _TranscriptApi()
      ..messagesBuilder = () => [
        _message('assistant-0', 'assistant', [
          Part(
            id: 'settled-text',
            messageID: 'assistant-0',
            type: 'text',
            text: 'A **settled** earlier answer.',
          ),
        ]),
        _message('assistant-1', 'assistant', [
          Part(
            id: 'live-text',
            messageID: 'assistant-1',
            type: 'text',
            text: 'Streaming:',
          ),
        ], created: 2),
      ];
    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    final flushesBefore = debugChatStreamFlushes;
    final parsesBefore = MarkdownText.debugParseCount;
    for (var i = 0; i < 20; i++) {
      _delta(controller, 'assistant-1', 'live-text', ' tok$i');
    }
    await tester.pump();
    await tester.pump();

    // The whole burst coalesced into one setState, and only the streaming
    // message re-parsed its markdown — the settled message stayed cached.
    expect(find.textContaining('tok0', findRichText: true), findsOneWidget);
    expect(find.textContaining('tok19', findRichText: true), findsOneWidget);
    expect(debugChatStreamFlushes - flushesBefore, 1);
    expect(MarkdownText.debugParseCount - parsesBefore, lessThanOrEqualTo(2));

    // Deltas arriving inside the throttle window flush together when the
    // ~50ms window closes.
    _delta(controller, 'assistant-1', 'live-text', ' tail');
    await tester.pump();
    expect(find.textContaining('tail', findRichText: true), findsNothing);
    await tester.pump(const Duration(milliseconds: 60));
    expect(find.textContaining('tail', findRichText: true), findsOneWidget);
    expect(debugChatStreamFlushes - flushesBefore, 2);

    // Drain the trailing window so no timer outlives the test.
    await tester.pump(const Duration(milliseconds: 60));
  });

  testWidgets('tool card expansion survives list recycling via the store', (
    tester,
  ) async {
    final store = <String, bool>{};
    final state = ToolState.fromJson(const {
      'status': 'completed',
      'input': {'command': 'flutter test'},
      'output': 'All tests passed.',
    }, toolName: 'bash');

    Widget card() => MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: ToolCard(
            toolName: 'bash',
            state: state,
            expansionStore: store,
            expansionKey: 'tool:call-1',
          ),
        ),
      ),
    );

    await tester.pumpWidget(card());
    expect(find.textContaining('All tests passed.'), findsNothing);
    await tester.tap(find.text('Shell'));
    await tester.pump();
    expect(find.textContaining('All tests passed.'), findsOneWidget);
    expect(store['tool:call-1'], isTrue);

    // Simulate list recycling: the item State is destroyed and rebuilt.
    await tester.pumpWidget(const MaterialApp(home: SizedBox.shrink()));
    await tester.pumpWidget(card());
    expect(find.textContaining('All tests passed.'), findsOneWidget);

    // And an explicit collapse survives the same way.
    await tester.tap(find.text('Shell'));
    await tester.pump();
    await tester.pumpWidget(const MaterialApp(home: SizedBox.shrink()));
    await tester.pumpWidget(card());
    expect(find.textContaining('All tests passed.'), findsNothing);
  });

  testWidgets('collapsing a running tool group is not undone by new tools', (
    tester,
  ) async {
    final api = _TranscriptApi()
      ..messagesBuilder = () => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'tool-1',
            messageID: 'assistant-1',
            type: 'tool',
            callID: 'call-1',
            toolName: 'read',
            toolState: ToolState.fromJson(const {
              'status': 'completed',
              'input': {'filePath': '/a.txt'},
              'output': 'done',
            }, toolName: 'read'),
          ),
          Part(
            id: 'tool-2',
            messageID: 'assistant-1',
            type: 'tool',
            callID: 'call-2',
            toolName: 'grep',
            toolState: ToolState.fromJson(const {
              'status': 'running',
              'input': {'pattern': 'x'},
            }, toolName: 'grep'),
          ),
        ]),
      ];
    final controller = await _pumpChat(tester, api);
    // A running spinner animates indefinitely, so settle with finite pumps.
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 400));

    // Auto-opened while running.
    expect(find.byKey(const Key('embedded-tool-row')), findsWidgets);

    await tester.tap(find.byKey(const Key('tool-call-group-header')));
    await tester.pump();
    expect(find.byKey(const Key('embedded-tool-row')), findsNothing);

    // A new tool joining the running group must not force it back open.
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'message.part.updated',
        properties: {
          'sessionID': 'session-1',
          'part': {
            'id': 'tool-3',
            'sessionID': 'session-1',
            'messageID': 'assistant-1',
            'type': 'tool',
            'callID': 'call-3',
            'tool': 'list',
            'state': {'status': 'running'},
          },
        },
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(find.byKey(const Key('embedded-tool-row')), findsNothing);
  });

  testWidgets('assistant prose is selectable; the ⋯ button opens the actions '
      'menu', (tester) async {
    final api = _TranscriptApi()
      ..messagesBuilder = () => [
        _message('assistant-1', 'assistant', [
          Part(
            id: 'answer',
            messageID: 'assistant-1',
            type: 'text',
            text: 'The answer paragraph.',
          ),
        ]),
      ];
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // Prose gives up the long-press so a reader can select and copy a phrase
    // on a phone; the actions moved to the always-visible ⋯ affordance.
    expect(
      find.descendant(
        of: find.byKey(const Key('assistant-text-block')),
        matching: find.byType(SelectableText),
      ),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('message-actions-assistant-1')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('message-action-copy')), findsOneWidget);
    expect(find.byKey(const ValueKey('message-action-fork')), findsNothing);
    expect(find.byKey(const ValueKey('message-action-delete')), findsOneWidget);
  });

  testWidgets('the meta-row affordance opens the same actions menu', (
    tester,
  ) async {
    final api = _TranscriptApi()
      ..messagesBuilder = () => [
        _message('user-1', 'user', [
          Part(
            id: 'prompt',
            messageID: 'user-1',
            type: 'text',
            text: 'Fix the login bug',
          ),
        ]),
      ];
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    final affordance = find.byKey(const ValueKey('message-actions-user-1'));
    expect(affordance, findsOneWidget);
    await tester.tap(affordance);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('message-action-copy')), findsOneWidget);
    expect(find.byKey(const ValueKey('message-action-fork')), findsOneWidget);
  });

  testWidgets('copy complete reply includes its fragments but no other turn', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(600, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final copied = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied.add((call.arguments as Map)['text'] as String);
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
    final api = _TranscriptApi()
      ..messagesBuilder = () => [
        _message('user-1', 'user', [Part(type: 'text', text: 'First prompt')]),
        _message('assistant-1', 'assistant', [
          Part(type: 'text', text: 'First paragraph.'),
          Part(type: 'text', text: 'Internal note', synthetic: true),
          Part(type: 'reasoning', text: 'Private reasoning'),
        ], created: 2),
        _message('assistant-2', 'assistant', [
          Part(type: 'text', text: 'Second paragraph.'),
        ], created: 3),
        _message('user-2', 'user', [
          Part(type: 'text', text: 'Next prompt'),
        ], created: 4),
        _message('assistant-3', 'assistant', [
          Part(type: 'text', text: 'Different reply.'),
        ], created: 5),
      ];
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('message-actions-assistant-1')));
    await tester.pumpAndSettle();
    expect(find.text('Copy complete reply'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('message-action-copy')));
    await tester.pumpAndSettle();

    expect(copied, ['First paragraph.\n\nSecond paragraph.']);
  });

  testWidgets(
    'complete-reply copy leaves delete scoped to the chosen message',
    (tester) async {
      final repository = _TranscriptRepository();
      final messages = [
        _message('assistant-1', 'assistant', [
          Part(type: 'text', text: 'First paragraph.'),
        ]),
        _message('assistant-2', 'assistant', [
          Part(type: 'text', text: 'Second paragraph.'),
        ], created: 2),
      ];
      final api = _TranscriptApi()
        ..messagesBuilder = () => messages
            .where(
              (message) =>
                  !repository.deletedMessageIDs.contains(message.info.id),
            )
            .toList();
      final controller = await _pumpChat(tester, api);
      controller.repository = repository;
      await tester.pumpAndSettle();

      await tester.tap(
        find.byKey(const ValueKey('message-actions-assistant-1')),
      );
      await tester.pumpAndSettle();
      expect(find.text('Copy complete reply'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('message-action-delete')));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Delete message'));
      await tester.pumpAndSettle();

      expect(repository.deletedMessageIDs, ['assistant-1']);
      expect(find.textContaining('First paragraph.'), findsNothing);
      expect(find.textContaining('Second paragraph.'), findsOneWidget);
    },
  );

  testWidgets(
    'an unfinished assistant reply does not promise a complete copy',
    (tester) async {
      final api = _TranscriptApi()
        ..messagesBuilder = () => [
          _message('assistant-1', 'assistant', [
            Part(type: 'text', text: 'First paragraph.'),
          ]),
          MessageWithParts(
            info: MessageInfo(
              id: 'assistant-2',
              sessionID: 'session-1',
              role: 'assistant',
              time: MsgTime(created: 2),
            ),
            parts: [Part(type: 'text', text: 'Still writing')],
          ),
        ];
      final controller = await _pumpChat(tester, api);
      controller.busySessions = {'session-1'};
      controller.notifyListeners();
      await tester.pump(const Duration(milliseconds: 400));

      await tester.tap(
        find.byKey(const ValueKey('message-actions-assistant-1')),
      );
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.text('Copy reply so far'), findsOneWidget);
      expect(find.text('Copy complete reply'), findsNothing);
    },
  );

  testWidgets(
    'tool groups report skipped steps without a false running state',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final api = _TranscriptApi()
        ..messagesBuilder = () => [
          _message('assistant-1', 'assistant', [
            Part(
              id: 'read-1',
              type: 'tool',
              toolName: 'read',
              toolState: ToolState(
                status: 'completed',
                input: const {'filePath': '/work/a.dart'},
                output: 'Read contents',
              ),
            ),
            Part(
              id: 'grep-1',
              type: 'tool',
              toolName: 'grep',
              toolState: ToolState(
                status: 'running',
                input: const {'pattern': 'unused'},
                executed: false,
              ),
            ),
          ]),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();

      expect(find.text('Read 1 file, 1 step was not run'), findsOneWidget);
      expect(find.text('Exploring'), findsNothing);
      expect(find.text('Explored'), findsNothing);
      expect(find.byKey(const Key('embedded-tool-row')), findsNothing);
      final group = find.byKey(const Key('tool-call-group'));
      expect(
        find.descendant(
          of: group,
          matching: find.byType(CircularProgressIndicator),
        ),
        findsNothing,
      );
      expect(
        tester
            .getSemantics(find.byKey(const Key('tool-call-group-header')))
            .label,
        contains('includes steps not run'),
      );
      semantics.dispose();
    },
  );

  testWidgets('earlier-messages pill gates on scroll and excludes visible '
      'messages; new turns defer while reading history', (tester) async {
    final api = _TranscriptApi()
      ..messagesBuilder = () => [
        for (var index = 0; index < 40; index += 1)
          _message('user-$index', 'user', [
            Part(
              id: 'part-$index',
              messageID: 'user-$index',
              type: 'text',
              text: 'prompt number $index',
            ),
          ], created: index + 1),
      ];
    final controller = await _pumpChat(tester, api);
    await tester.pumpAndSettle();

    // At the live end there is no pill, even with 40 messages.
    expect(find.byKey(const ValueKey('earlier-messages-pill')), findsNothing);

    await tester.drag(
      find.byType(ScrollablePositionedList),
      const Offset(0, 900),
    );
    await tester.pump();
    await tester.pump();

    final pill = find.byKey(const ValueKey('earlier-messages-pill'));
    expect(pill, findsOneWidget);
    // The count excludes messages currently on screen.
    expect(find.text('40 earlier messages'), findsNothing);
    expect(find.textContaining('earlier messages'), findsOneWidget);

    // C14: a turn completing while scrolled up does not shift the viewport —
    // the new message stays deferred until jump-to-latest.
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'message.part.updated',
        properties: {
          'sessionID': 'session-1',
          'part': {
            'id': 'fresh',
            'sessionID': 'session-1',
            'messageID': 'assistant-new',
            'type': 'text',
            'text': 'brand new reply',
          },
        },
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(
      find.textContaining('brand new reply', findRichText: true),
      findsNothing,
    );

    await tester.tap(find.byKey(const ValueKey('jump-to-latest')));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('brand new reply', findRichText: true),
      findsOneWidget,
    );
  });
}
