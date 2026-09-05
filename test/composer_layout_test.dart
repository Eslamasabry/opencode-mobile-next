import 'support/complete_message_history.dart';
import 'dart:convert';
import 'dart:io';

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
import 'package:shared_preferences/shared_preferences.dart';

/// Audit UX-P0-03: the composer used to ring the prompt field with five
/// equal-weight controls, so the field was the least stable element on a
/// narrow phone at a large text scale. These tests pin the new anatomy —
/// one leading tools button, a secondary model chip, and Send — and the
/// share of the composer the field must keep.
class _FakeApi extends OpenCodeApi with CompleteMessageHistory {
  _FakeApi() : super(baseUrl: 'http://localhost');
  List<MessageWithParts> transcript = [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<Session> session(String id) async => Session(id: id);
}

class _BackgroundRepository extends ProductRepository {
  BackgroundWorkSupport support = BackgroundWorkSupport.subagents;
  BackgroundWorkResult result = BackgroundWorkResult.promoted;
  int capabilityReads = 0;
  int promotions = 0;
  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async {
    capabilityReads++;
    return support;
  }

  @override
  Future<BackgroundWorkResult> backgroundSession(String sessionID) async {
    promotions++;
    return result;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

MessageWithParts _prompt(String id, String text) => MessageWithParts(
  info: MessageInfo(id: id, sessionID: 'session-1', role: 'user'),
  parts: [Part(type: 'text', text: text)],
);

MessageWithParts _task() => MessageWithParts(
  info: MessageInfo(
    id: 'assistant-1',
    sessionID: 'session-1',
    role: 'assistant',
  ),
  parts: [
    Part(
      id: 'task-1',
      messageID: 'assistant-1',
      type: 'tool',
      toolName: 'task',
      toolState: ToolState.fromJson({
        'status': 'running',
        'input': {'description': 'Review UI'},
        'metadata': {'sessionId': 'child-1'},
      }),
    ),
  ],
);

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return ConnectionController(store)
    ..api = _FakeApi()
    ..status = StreamStatus.connected;
}

Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController conn, {
  required Size size,
  double textScale = 2.5,
  List<PromptAttachment> attachments = const [],
  bool settle = true,
  double keyboardInset = 0,
}) async {
  await tester.binding.setSurfaceSize(size);
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        // setSurfaceSize resizes the render surface but leaves the view's
        // reported size at the 800x600 default, and the composer picks its
        // layout from MediaQuery, so both have to say the same thing.
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            size: size,
            textScaler: TextScaler.linear(textScale),
            viewInsets: EdgeInsets.only(bottom: keyboardInset),
          ),
          child: child!,
        ),
        home: ChatScreen(
          sessionID: 'session-1',
          initialAttachments: attachments,
        ),
      ),
    ),
  );
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
  }
}

/// The prompt field's width as a fraction of the composer surface it sits in.
double _fieldShare(WidgetTester tester) {
  final field = tester.getSize(find.byKey(const Key('chat-composer-field')));
  final surface = tester.getSize(
    find.byKey(const Key('chat-composer-surface')),
  );
  return field.width / surface.width;
}

double _editingShare(WidgetTester tester) {
  final editable = find.descendant(
    of: find.byKey(const Key('chat-composer-field')),
    matching: find.byType(EditableText),
  );
  return tester.getSize(editable).width /
      tester.getSize(find.byKey(const Key('chat-composer-surface'))).width;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('typing saves the latest draft before leaving the chat', (
    tester,
  ) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn, size: const Size(390, 844), textScale: 1);
    final field = find.byKey(const Key('chat-composer-field'));
    await tester.enterText(field, 'First');
    await tester.pump(const Duration(milliseconds: 300));
    await tester.enterText(field, 'Latest draft');
    await tester.pump(const Duration(milliseconds: 700));
    expect(conn.sessionDraft('session-1'), 'Latest draft');
    expect(
      conn.store.prefs.getString('oc.sessionDrafts'),
      contains('Latest draft'),
    );
  });

  testWidgets(
    'typing updates Send and slash suggestions without a server event',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await _pumpChat(tester, conn, size: const Size(390, 844), textScale: 1);
      final field = find.byKey(const Key('chat-composer-field'));
      final send = find.byKey(const Key('chat-send-button'));
      expect(tester.widget<IconButton>(send).onPressed, isNull);
      await tester.enterText(field, 'Test draft');
      await tester.pump();
      expect(tester.widget<IconButton>(send).onPressed, isNotNull);
      await tester.enterText(field, '   ');
      await tester.pump();
      expect(tester.widget<IconButton>(send).onPressed, isNull);
      await tester.enterText(field, '/model');
      await tester.pump();
      expect(
        find.byKey(const Key('inline-command-suggestions')),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'local image attachments show a thumbnail with independent removal',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      final bytes = File('assets/branding/app-icon-256.png').readAsBytesSync();
      await _pumpChat(
        tester,
        conn,
        size: const Size(390, 844),
        textScale: 1,
        attachments: [
          PromptAttachment(
            filename: 'screenshot.png',
            mime: 'image/png',
            url: 'data:image/png;base64,${base64Encode(bytes)}',
          ),
        ],
      );
      expect(find.byKey(const Key('attachment-thumbnail')), findsOneWidget);
      await tester.tap(find.byTooltip('Remove attachment screenshot.png'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('attachment-thumbnail')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'clearing text can be undone without losing newer typing or attachments',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      await _pumpChat(
        tester,
        conn,
        size: const Size(390, 844),
        textScale: 1,
        attachments: [
          PromptAttachment(
            filename: 'notes.txt',
            mime: 'text/plain',
            url: 'data:text/plain;base64,SGVsbG8=',
          ),
        ],
      );
      final field = find.byKey(const Key('chat-composer-field'));
      await tester.enterText(field, 'Original draft');
      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-clear')));
      await tester.pumpAndSettle();
      expect(tester.widget<TextField>(field).controller!.text, isEmpty);
      expect(find.text('notes.txt'), findsOneWidget);
      await tester.enterText(field, 'New typing');
      await tester.tap(find.text('Undo'));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(field).controller!.text,
        'Original draft\n\nNew typing',
      );
      expect(find.text('notes.txt'), findsOneWidget);
    },
  );

  testWidgets(
    'prompt reuse searches deduplicated history and preserves the draft',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      (conn.api as _FakeApi).transcript = [
        _prompt('m1', 'Review the UI'),
        _prompt('m2', 'Fix tests'),
        _prompt('m3', 'Review the UI'),
      ];
      await _pumpChat(tester, conn, size: const Size(390, 844), textScale: 1);
      final field = find.byKey(const Key('chat-composer-field'));
      await tester.enterText(field, 'Keep this');
      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-history')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('reuse-prompt-2')), findsNothing);
      await tester.enterText(
        find.byKey(const Key('prompt-history-search')),
        'review',
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('reuse-prompt-1')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('reuse-prompt-0')));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(field).controller!.text,
        'Keep this\n\nReview the UI',
      );
      expect(tester.widget<TextField>(field).focusNode!.hasFocus, isTrue);
    },
  );

  testWidgets('finishing a run preserves the editor state and selection', (
    tester,
  ) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn, size: const Size(390, 844), textScale: 1);
    final field = find.byKey(const Key('chat-composer-field'));
    await tester.enterText(field, 'Still writing');
    final editor = find.descendant(
      of: field,
      matching: find.byType(EditableText),
    );
    final state = tester.state<EditableTextState>(editor);
    final control = tester.widget<TextField>(field).controller!;
    control.selection = const TextSelection(baseOffset: 2, extentOffset: 5);
    conn.busySessions.add('session-1');
    conn.notifyListeners();
    await tester.pump();
    conn.busySessions.clear();
    conn.notifyListeners();
    await tester.pumpAndSettle();
    expect(tester.state<EditableTextState>(editor), same(state));
    expect(
      control.selection,
      const TextSelection(baseOffset: 2, extentOffset: 5),
    );
    expect(control.text, 'Still writing');
    expect(tester.widget<TextField>(field).focusNode!.hasFocus, isTrue);
  });

  testWidgets(
    'prompt history stays usable with large text and the keyboard open',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      (conn.api as _FakeApi).transcript = [_prompt('m1', 'Review the UI')];
      await _pumpChat(
        tester,
        conn,
        size: const Size(360, 760),
        textScale: 2.5,
        keyboardInset: 300,
      );
      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await tester.pumpAndSettle();
      final history = find.byKey(const Key('composer-tool-history'));
      await tester.ensureVisible(history);
      await tester.tap(history);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('reuse-prompt-0')),
        160,
        scrollable: find.descendant(
          of: find.byKey(const Key('prompt-history-sheet')),
          matching: find.byType(Scrollable),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('reuse-prompt-0')));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .controller!
            .text,
        'Review the UI',
      );
    },
  );

  testWidgets('background capability is cached and refreshed after reconnect', (
    tester,
  ) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    final repository = _BackgroundRepository();
    conn.repository = repository;
    (conn.api as _FakeApi).transcript = [_task()];
    await _pumpChat(
      tester,
      conn,
      size: const Size(360, 760),
      textScale: 1,
      settle: false,
    );
    conn.busySessions.add('session-1');
    conn.notifyListeners();
    await tester.pump();
    expect(find.text('Background subagents'), findsOneWidget);
    expect(repository.capabilityReads, 1);
    expect(
      tester.getSize(find.byKey(const Key('background-running-work'))).height,
      greaterThanOrEqualTo(48),
    );
    repository.support = BackgroundWorkSupport.unavailable;
    conn.dataRefreshRevision++;
    conn.notifyListeners();
    await tester.pump();
    await tester.pump();
    expect(repository.capabilityReads, 2);
    expect(find.byKey(const Key('background-running-work')), findsNothing);
    conn.busySessions.clear();
    conn.notifyListeners();
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'touch background action handles a no-op without claiming success',
    (tester) async {
      final conn = await _controller();
      addTearDown(conn.dispose);
      final repository = _BackgroundRepository()
        ..result = BackgroundWorkResult.unchanged;
      conn.repository = repository;
      (conn.api as _FakeApi).transcript = [_task()];
      await _pumpChat(
        tester,
        conn,
        size: const Size(360, 760),
        textScale: 1,
        settle: false,
      );
      conn.busySessions.add('session-1');
      conn.notifyListeners();
      await tester.pump();
      await tester.tap(find.byKey(const Key('background-running-work')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(repository.promotions, 1);
      expect(
        find.text('No foreground subagents to background.'),
        findsOneWidget,
      );
      expect(
        find.text('Subagents are continuing in the background.'),
        findsNothing,
      );
    },
  );

  testWidgets('the prompt field stays dominant on a 360dp phone at 2.5x text', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller, size: const Size(360, 760));

    // Only the leading tools button, the model context chip, and Send
    // surround the field; Attach and Voice moved into the tools sheet.
    expect(find.byKey(const Key('composer-tools-button')), findsOneWidget);
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);
    expect(find.byKey(const Key('chat-send-button')), findsOneWidget);
    expect(find.byIcon(Icons.attach_file_rounded), findsNothing);
    expect(find.byIcon(Icons.mic_none_rounded), findsNothing);

    expect(_fieldShare(tester), greaterThanOrEqualTo(0.9));
    // Measure the actual typing area, not the decoration that used to
    // include an expand button and make the field look wider than it was.
    expect(_editingShare(tester), greaterThanOrEqualTo(0.85));
    expect(tester.takeException(), isNull);
  });

  testWidgets('the compact composer keeps a full-width typing area', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    // Short windows reduce the line budget, preserving the same layout.
    await _pumpChat(tester, controller, size: const Size(360, 420));

    expect(find.byKey(const Key('composer-tools-button')), findsOneWidget);
    expect(find.byIcon(Icons.attach_file_rounded), findsNothing);
    expect(find.byIcon(Icons.mic_none_rounded), findsNothing);
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);

    expect(_fieldShare(tester), greaterThanOrEqualTo(0.9));
    expect(_editingShare(tester), greaterThanOrEqualTo(0.85));
    expect(tester.takeException(), isNull);
  });

  testWidgets('keyboard resizing preserves the editor and draft selection', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(
      tester,
      controller,
      size: const Size(360, 760),
      textScale: 1,
    );
    final fieldFinder = find.byKey(const Key('chat-composer-field'));
    final editorFinder = find.descendant(
      of: fieldFinder,
      matching: find.byType(EditableText),
    );
    await tester.tap(fieldFinder);
    await tester.enterText(fieldFinder, 'Review this draft');
    final field = tester.widget<TextField>(fieldFinder);
    field.controller!.selection = const TextSelection.collapsed(offset: 7);
    await tester.pump();
    final editor = tester.state<EditableTextState>(editorFinder);

    tester.view.viewInsets = const FakeViewPadding(bottom: 320);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(tester.state<EditableTextState>(editorFinder), same(editor));
    expect(field.focusNode!.hasFocus, isTrue);
    expect(tester.testTextInput.hasAnyClients, isTrue);
    expect(field.controller!.text, 'Review this draft');
    expect(field.controller!.selection.baseOffset, 7);
    expect(_editingShare(tester), greaterThanOrEqualTo(0.85));
    expect(
      tester.getTopLeft(find.byKey(const Key('chat-send-button'))).dy,
      greaterThanOrEqualTo(tester.getBottomLeft(fieldFinder).dy),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('every collapsed tool stays reachable from the tools sheet', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(
      tester,
      controller,
      size: const Size(360, 760),
      textScale: 1,
    );

    await tester.tap(find.byKey(const Key('composer-tools-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
    final tools = [
      const Key('composer-tool-commands'),
      const Key('composer-tool-attach'),
      const Key('composer-tool-voice'),
    ];
    for (final tool in tools) {
      final finder = find.byKey(tool);
      expect(finder, findsOneWidget);
      // Every entry keeps the Android minimum target it had as a button.
      expect(tester.getSize(finder).height, greaterThanOrEqualTo(48));
      final node = tester.getSemantics(finder);
      expect(node.label, isNotEmpty);
    }

    await tester.tap(find.byKey(const Key('composer-tool-commands')));
    await tester.pumpAndSettle();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('command-launcher-search')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'the tools sheet closes Voice while a run is active; Attach stays '
    'open for the queued send',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await _pumpChat(
        tester,
        controller,
        size: const Size(360, 760),
        textScale: 1,
      );
      controller.busySessions.add('session-1');
      controller.notifyListeners();
      // The busy composer animates its activity ring forever, so this pumps
      // explicit frames instead of settling.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      // A send made mid-turn is queued (v1) or steered/queued (v2), and can
      // carry attachments like any other send.
      expect(
        tester
            .widget<ListTile>(find.byKey(const Key('composer-tool-attach')))
            .enabled,
        isTrue,
      );
      expect(
        tester
            .widget<ListTile>(find.byKey(const Key('composer-tool-voice')))
            .enabled,
        isFalse,
      );
      // Commands stay available: they do not depend on the run finishing.
      expect(
        tester
            .widget<ListTile>(find.byKey(const Key('composer-tool-commands')))
            .enabled,
        isTrue,
      );
    },
  );

  testWidgets('a busy run lights the composer surface instead of a transcript '
      'row', (tester) async {
    final semantics = tester.ensureSemantics();
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pumpChat(
      tester,
      controller,
      size: const Size(360, 760),
      textScale: 1,
    );
    expect(find.byKey(const ValueKey('composer-activity')), findsNothing);
    expect(find.bySemanticsLabel('Assistant is working'), findsNothing);

    controller.busySessions.add('session-1');
    controller.notifyListeners();
    // The activity ring animates forever, so pump explicit frames.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.byKey(const ValueKey('composer-activity')), findsOneWidget);
    expect(find.byKey(const ValueKey('typing-indicator')), findsNothing);
    expect(find.bySemanticsLabel('Assistant is working'), findsOneWidget);
    // The ring is an overlay on the surface, not a new surface: the prompt
    // field keeps its place and the surface still bounds the ring.
    final surface = tester.getRect(
      find.byKey(const Key('chat-composer-surface')),
    );
    final ring = tester.getRect(
      find.byKey(const ValueKey('composer-activity')),
    );
    expect(ring, surface);
    expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);

    controller.busySessions.remove('session-1');
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('composer-activity')), findsNothing);
    expect(find.bySemanticsLabel('Assistant is working'), findsNothing);
    semantics.dispose();
  });

  testWidgets('reduced motion keeps a still activity ring while busy', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final controller = await _controller();
    addTearDown(controller.dispose);
    controller.busySessions.add('session-1');
    await tester.binding.setSurfaceSize(const Size(360, 760));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(size: const Size(360, 760), disableAnimations: true),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 'session-1'),
        ),
      ),
    );
    // With animations disabled the ring is static, so the tree settles.
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('composer-activity')), findsOneWidget);
    expect(find.bySemanticsLabel('Assistant is working'), findsOneWidget);
    semantics.dispose();
  });
}
