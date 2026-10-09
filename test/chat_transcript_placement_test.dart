import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _TranscriptApi extends OpenCodeApi with CompleteMessageHistory {
  _TranscriptApi(this.transcript) : super(baseUrl: 'http://localhost');

  final List<MessageWithParts> transcript;

  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  required int created,
  Tokens? tokens,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    providerID: 'anthropic',
    modelID: 'claude',
    tokens: tokens,
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: parts,
);

Part _text(String id, String text) => Part(id: id, type: 'text', text: text);

Future<ConnectionController> _pump(
  WidgetTester tester,
  List<MessageWithParts> transcript, {
  bool busy = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = _TranscriptApi(transcript)
    ..status = StreamStatus.connected;
  if (busy) controller.busySessions.add('session-1');
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  // Working indicators can animate indefinitely, so use bounded frames.
  if (busy) {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  } else {
    await tester.pumpAndSettle();
  }
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a busy session writes its status under the newest turn; Stop '
      'is the composer\'s Send, and the only one', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'First question')], created: 1),
      _message('a1', 'assistant', [_text('a1-t', 'First answer')], created: 2),
      _message('u2', 'user', [_text('u2-t', 'Second question')], created: 3),
      _message('a2', 'assistant', [_text('a2-t', 'Second answer')], created: 4),
    ], busy: true);

    // 01B: the status is in the turn, under the reply, once.
    expect(find.byKey(const ValueKey('typing-indicator')), findsNothing);
    expect(find.byKey(const ValueKey('message-a2')), findsOneWidget);
    final line = find.byKey(const ValueKey('kit-turn-live-line'));
    expect(line, findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('message-a2')),
        matching: line,
      ),
      findsOneWidget,
    );
    // 11B: the composer's Send is Stop; there is no second one in the turn.
    final stop = find.byKey(const Key('chat-stop-button'));
    expect(stop, findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('message-a2')),
        matching: stop,
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: find.byKey(const Key('chat-composer-surface')),
        matching: stop,
      ),
      findsOneWidget,
    );
    expect(find.text('Stop reply'), findsNothing);
    // A screen reader reaches Stop as its own button.
    expect(find.bySemanticsLabel(RegExp('Stop the reply')), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('an idle session shows no working indicator', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Question')], created: 1),
      _message('a1', 'assistant', [_text('a1-t', 'Answer')], created: 2),
    ]);
    expect(find.byKey(const ValueKey('typing-indicator')), findsNothing);
    expect(find.byKey(const Key('chat-stop-button')), findsNothing);
    expect(find.bySemanticsLabel(RegExp('Stop the reply')), findsNothing);
    semantics.dispose();
  });

  testWidgets('step-finish, patch and snapshot parts leave no stray row', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Rename the file')], created: 1),
      _message('a1', 'assistant', [
        Part(id: 'a1-step', type: 'step-start'),
        Part(
          id: 'a1-edit',
          type: 'tool',
          callID: 'call-1',
          toolName: 'edit',
          toolState: ToolState.fromJson(const {
            'status': 'completed',
            'input': {'filePath': '/work/lib/main.dart'},
            'output': 'ok',
            'metadata': {'diff': ''},
          }, toolName: 'edit'),
        ),
      ], created: 2),
      // The bookkeeping tail of the turn: nothing a reader can act on.
      _message(
        'a2',
        'assistant',
        [
          Part(id: 'a2-patch', type: 'patch'),
          Part(id: 'a2-snapshot', type: 'snapshot'),
          Part(id: 'a2-finish', type: 'step-finish'),
        ],
        created: 3,
        tokens: Tokens(input: 10, output: 5),
      ),
    ]);

    expect(find.text('Edit'), findsOneWidget);
    // No "…" placeholder and no orphan actions row for the empty message.
    expect(find.text('…'), findsNothing);
    // The turn keeps exactly one actions affordance, in its footer: the
    // bookkeeping tail adds no second one and takes none away.
    expect(
      find.byWidgetPredicate((widget) {
        final key = widget.key;
        return key is ValueKey<String> &&
            key.value.startsWith('message-actions-') &&
            !key.value.startsWith('message-actions-disc-');
      }),
      findsOneWidget,
    );
  });

  Part tool(String id, String name) => Part(
    id: id,
    type: 'tool',
    callID: id,
    toolName: name,
    toolState: ToolState.fromJson(const {
      'status': 'completed',
      'input': {'filePath': '/work/lib/main.dart'},
      'output': 'ok',
    }, toolName: name),
  );

  testWidgets('a turn has one control, and a notice does not end the turn', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [
        _text('u1-t', 'Tidy the home screen'),
      ], created: 1),
      _message('a1', 'assistant', [
        _text('a1-t', 'Looking first.'),
      ], created: 2),
      // Filed under the user role by the server, but nobody typed it.
      _message('n1', 'user', [
        Part(id: 'v2-0', type: 'v2:notice', toolName: 'synthetic', text: 'x'),
      ], created: 3),
      _message('a2', 'assistant', [_text('a2-t', 'Done.')], created: 4),
      _message('u2', 'user', [_text('u2-t', 'Thanks')], created: 5),
      _message('a3', 'assistant', [_text('a3-t', 'Welcome.')], created: 6),
    ]);

    // No control under a step, under the prompt, or before the notice.
    expect(find.byKey(const ValueKey('message-actions-u1')), findsNothing);
    expect(find.byKey(const ValueKey('message-actions-a1')), findsNothing);
    // One per turn, under the step that ends it.
    expect(find.byKey(const ValueKey('message-actions-a2')), findsOneWidget);
    expect(find.byKey(const ValueKey('message-actions-a3')), findsOneWidget);
  });

  testWidgets('a one-line thought titles the tool run that follows it', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Patch it')], created: 1),
      _message('a1', 'assistant', [
        Part(id: 'a1-r', type: 'reasoning', text: '**Patching home shell**'),
        tool('a1-t1', 'read'),
        tool('a1-t2', 'edit'),
      ], created: 2),
    ]);

    // The run folds under the turn's one work line, which says what was
    // done; opened, the agent's own name for the step titles its first call
    // instead of a thinking block of its own.
    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(find.text('Read 1 file · edited 1 file'), findsOneWidget);
    expect(find.text('Tools'), findsNothing);
    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();
    expect(find.text('Patching home shell'), findsOneWidget);
    expect(find.byKey(const Key('assistant-reasoning-block')), findsNothing);
  });

  testWidgets('a prompt is an end-aligned bubble; the reply has no frame', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Hello there')], created: 1),
      _message('a1', 'assistant', [_text('a1-t', 'Hi.')], created: 2),
    ]);
    // VL §5 / Appendix A #47: the person's words sit in a bubble at the end
    // edge; the agent's prose starts at the start edge.
    expect(find.byKey(const ValueKey('user-prompt-u1')), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Hello there')).dx,
      greaterThan(tester.getTopLeft(find.text('Hi.')).dx + 16),
    );
    expect(
      tester.getTopRight(find.byKey(const ValueKey('user-prompt-u1'))).dx,
      equals(tester.getTopRight(find.text('Hi.')).dx),
    );
  });

  testWidgets(
    'a finished turn folds its work and passing words under one line',
    (tester) async {
      await _pump(tester, [
        _message('u1', 'user', [_text('u1-t', 'Fix the balance')], created: 1),
        _message('a1', 'assistant', [
          _text('a1-t', 'Looking into it.'),
          tool('t1', 'bash'),
        ], created: 2),
        // Later steps of the same stretch of work, stored as separate messages.
        _message('a2', 'assistant', [
          Part(id: 'r2', type: 'reasoning', text: '**Checking persistence**'),
          tool('t2', 'bash'),
        ], created: 3),
        _message('a3', 'assistant', [
          Part(id: 'r3', type: 'reasoning', text: '**Preparing the patch**'),
          tool('t3', 'read'),
          tool('t4', 'read'),
        ], created: 4),
        _message('a4', 'assistant', [_text('a4-t', 'Fixed.')], created: 5),
      ]);

      // A finished turn is the prompt, one line of work and the answer. What
      // the agent said on the way ("Looking into it.") is folded with the work.
      expect(find.text('Looking into it.'), findsNothing);
      expect(find.text('Fixed.'), findsOneWidget);
      expect(find.byKey(const Key('work-group')), findsOneWidget);
      expect(find.text('3 steps'), findsNothing);
      expect(find.text('Checking persistence'), findsNothing);

      // Discoverable: one tap shows every step, in order, by the agent's name.
      await tester.tap(find.byKey(const Key('work-group-header')));
      await tester.pumpAndSettle();
      expect(find.text('Looking into it.'), findsOneWidget);
      expect(find.text('Checking persistence'), findsOneWidget);
      expect(find.text('Preparing the patch'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Looking into it.')).dy,
        lessThan(tester.getTopLeft(find.text('Checking persistence')).dy),
      );
      expect(
        tester.getTopLeft(find.text('Checking persistence')).dy,
        lessThan(tester.getTopLeft(find.text('Preparing the patch')).dy),
      );
    },
  );

  testWidgets('a finished turn keeps the agent\'s explanation in view; only '
      'the work folds, under one line (gap 17)', (tester) async {
    const explanation =
        'The flakiness comes from CheckoutBloc: it reads the balance before '
        'the save completes.\n\nSo the test sometimes sees the old value.';
    await _pump(tester, [
      _message('u1', 'user', [
        _text('u1-t', 'Why is the test flaky?'),
      ], created: 1),
      _message('a1', 'assistant', [
        _text('a1-t', 'Looking into it.'),
        tool('t1', 'read'),
        tool('t2', 'read'),
      ], created: 2),
      _message('a2', 'assistant', [
        _text('a2-t', explanation),
        tool('t3', 'edit'),
      ], created: 3),
      _message('a3', 'assistant', [_text('a3-t', 'Fixed.')], created: 4),
    ]);

    // The explanation and the closing words stay; the passing words fold.
    expect(
      find.textContaining('The flakiness comes from CheckoutBloc'),
      findsOneWidget,
    );
    expect(find.text('Fixed.'), findsOneWidget);
    expect(find.text('Looking into it.'), findsNothing);
    // One work line for the whole turn, above the answer it led to.
    expect(find.byKey(const Key('work-group')), findsOneWidget);
    final work = tester.getTopLeft(find.byKey(const Key('work-group'))).dy;
    expect(
      work,
      lessThan(
        tester
            .getTopLeft(
              find.textContaining('The flakiness comes from CheckoutBloc'),
            )
            .dy,
      ),
    );
  });

  testWidgets('a notice filed mid-turn folds into the work, not between it', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Ship it')], created: 1),
      _message('a1', 'assistant', [tool('t1', 'bash')], created: 2),
      _message('n1', 'user', [
        Part(
          id: 'n1-p',
          messageID: 'n1',
          type: 'v2:notice',
          toolName: 'synthetic',
          filename: 'python3 release_discovery.py',
          text: 'done',
        ),
      ], created: 3),
      _message('a2', 'assistant', [tool('t2', 'read')], created: 4),
      _message('a3', 'assistant', [_text('a3-t', 'Published.')], created: 5),
    ]);

    // One line for the whole turn's work, the notice inside it.
    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(find.text('python3 release_discovery.py'), findsNothing);
    expect(find.text('Published.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();
    expect(find.text('python3 release_discovery.py'), findsOneWidget);
  });

  testWidgets('a background command finishing at the end of a turn is '
      'one line inside its work, not markup', (tester) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Run the tests')], created: 1),
      _message('a1', 'assistant', [tool('t1', 'bash')], created: 2),
      _message('a2', 'assistant', [tool('t2', 'read')], created: 3),
      _message('n1', 'user', [
        Part(
          id: 'n1-p',
          messageID: 'n1',
          type: 'v2:notice',
          toolName: 'synthetic',
          filename: '/tmp/opencode/flutter/bin/flutter test',
          text:
              '<shell id="sh_1" state="completed" '
              'command="/tmp/opencode/flutter/bin/flutter test">\n'
              'All tests passed!\n\nCommand exited with code 0.\n</shell>',
        ),
      ], created: 4),
    ]);

    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(find.byKey(const Key('background-shell-result')), findsNothing);
    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('background-shell-result')), findsOneWidget);
    expect(find.text('flutter test'), findsOneWidget);
    expect(find.textContaining('<shell'), findsNothing);
    // The output waits behind a tap of its own.
    expect(find.byKey(const Key('background-shell-output')), findsNothing);
  });

  testWidgets('a thought titles its step and explains itself inside it', (
    tester,
  ) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Go')], created: 1),
      _message('a1', 'assistant', [
        Part(
          id: 'r1',
          type: 'reasoning',
          text:
              // Short, like a real one: a centred line would sit mid-row.
              '**Rebuilding latest source**\n\nThe bundle is stale.',
        ),
        tool('t1', 'bash'),
      ], created: 2),
    ]);

    // No "Reasoning (tap to expand)" row of its own.
    expect(find.byKey(const Key('reasoning-toggle')), findsNothing);
    expect(find.text('Rebuilding latest source'), findsOneWidget);
    expect(find.byKey(const Key('step-note')), findsNothing);

    // Why, then what, once the step is opened.
    await tester.tap(find.text('Rebuilding latest source'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('step-note')), findsOneWidget);
    expect(find.textContaining('The bundle is stale'), findsOneWidget);
    // One transcript gutter: the opened note sits flush with its row's
    // leading edge, with no indent of its own.
    final row = find.ancestor(
      of: find.text('Rebuilding latest source'),
      matching: find.byType(KitToolRow),
    );
    expect(
      tester.getTopLeft(find.textContaining('The bundle is stale')).dx,
      equals(tester.getTopLeft(row).dx),
    );
  });

  testWidgets('a failure the agent got past does not open or redden the work', (
    tester,
  ) async {
    Part failed(String id) => Part(
      id: id,
      type: 'tool',
      callID: id,
      toolName: 'edit',
      toolState: ToolState.fromJson(const {
        'status': 'error',
        'input': {'filePath': '/work/lib/main.dart'},
        'error': 'patch verification failed',
      }, toolName: 'edit'),
    );
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Go')], created: 1),
      _message('a1', 'assistant', [
        failed('t1'),
        Part(id: 'r1', type: 'reasoning', text: '**Retrying the patch**'),
        tool('t2', 'edit'),
        _text('a1-t', 'Patched.'),
      ], created: 2),
    ]);
    expect(find.byKey(const Key('work-group')), findsOneWidget);
    expect(find.byKey(const Key('work-group-steps')), findsNothing);
    expect(find.text('patch verification failed'), findsNothing);
  });

  testWidgets('work that ends on a failure opens itself', (tester) async {
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Go')], created: 1),
      _message('a1', 'assistant', [
        tool('t1', 'read'),
        Part(id: 'r1', type: 'reasoning', text: '**Applying the patch**'),
        Part(
          id: 't2',
          type: 'tool',
          callID: 't2',
          toolName: 'edit',
          toolState: ToolState.fromJson(const {
            'status': 'error',
            'input': {'filePath': '/work/lib/main.dart'},
            'error': 'patch verification failed',
          }, toolName: 'edit'),
        ),
      ], created: 2),
    ]);
    expect(find.byKey(const Key('work-group-steps')), findsOneWidget);
  });

  testWidgets('long prose thinking stays a thinking block, not a step title', (
    tester,
  ) async {
    const thinking =
        'Okay, let me look at the router.\n\nThe credit products are sent to '
        'the wrong specialist, which suggests the intent table is matched '
        'before the product table. I should read the routing code first and '
        'then check how the seed profile marks an expired card.';
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Go')], created: 1),
      _message('a1', 'assistant', [
        Part(id: 'r1', type: 'reasoning', text: thinking),
        tool('t1', 'read'),
        _text('a1-t', 'Found it.'),
      ], created: 2),
    ]);
    // Thought and tool are two steps of one stretch of work.
    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('assistant-reasoning-block')), findsOneWidget);
    // The tool keeps its own name; the first sentence did not become a title.
    expect(find.text('Read'), findsOneWidget);
    expect(find.byKey(const Key('step-note')), findsNothing);
  });

  testWidgets('a running turn has no footer; it arrives when the turn ends', (
    tester,
  ) async {
    final controller = await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'First')], created: 1),
      _message('a1', 'assistant', [
        _text('a1-t', 'Done with that.'),
      ], created: 2),
      _message('u2', 'user', [_text('u2-t', 'Second')], created: 3),
      _message('a2', 'assistant', [_text('a2-t', 'Working on it')], created: 4),
    ], busy: true);

    // The finished turn keeps its one line; the running one has none yet.
    expect(find.byKey(const ValueKey('message-actions-a1')), findsOneWidget);
    expect(find.byKey(const ValueKey('message-actions-a2')), findsNothing);

    controller.busySessions.remove('session-1');
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('message-actions-a2')), findsOneWidget);
  });

  testWidgets('the turn footer copies the reply in one tap', (tester) async {
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
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Hi')], created: 1),
      _message('a1', 'assistant', [_text('a1-t', 'Part one.')], created: 2),
      _message('a2', 'assistant', [
        tool('t1', 'read'),
        _text('a2-t', 'Part two.'),
      ], created: 3),
    ]);
    final copy = find.byKey(const ValueKey('message-copy-a2'));
    expect(copy, findsOneWidget);
    expect(tester.getSize(copy).height, greaterThanOrEqualTo(44));
    await tester.tap(copy);
    await tester.pumpAndSettle();
    expect(copied, ['Part one.\n\nPart two.']);
  });

  testWidgets('only the newest failed compaction offers Compact again, and '
      'only while idle', (tester) async {
    MessageWithParts compaction(String id, String kind, int created) =>
        _message(id, 'user', [
          Part(id: 'v2-0', type: 'v2:compaction', toolName: kind, text: 'x'),
        ], created: created);
    final controller = await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Go')], created: 1),
      compaction('c1', 'failed', 2),
      _message('a1', 'assistant', [_text('a1-t', 'Carrying on.')], created: 3),
      compaction('c2', 'failed', 4),
    ]);
    // One button, on the newest failure.
    expect(find.text('Compact again'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('compaction-failed-c2')),
        matching: find.text('Compact again'),
      ),
      findsOneWidget,
    );

    // Not while something is running: it would race the turn.
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    expect(find.text('Compact again'), findsNothing);
  });

  testWidgets('a long step title never runs over its detail, however nested', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(340, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    Part read(String id) => Part(
      id: id,
      type: 'tool',
      callID: id,
      toolName: 'read',
      toolState: ToolState.fromJson(const {
        'status': 'completed',
        'input': {
          'filePath': '/work/lib/entry.dart',
          'offset': 126,
          'limit': 18,
        },
        'output': 'ok',
      }, toolName: 'read'),
    );
    await _pump(tester, [
      _message('u1', 'user', [_text('u1-t', 'Go')], created: 1),
      _message('a1', 'assistant', [
        Part(
          id: 'r1',
          type: 'reasoning',
          text: '**Checking AssistantEntry constructor arguments everywhere**',
        ),
        read('t1'),
        Part(
          id: 'r2',
          type: 'reasoning',
          text: '**Fixing widget constructors**',
        ),
        read('t2'),
        _text('a1-t', 'Done.'),
      ], created: 2),
    ]);
    // Inside the work line, where a row has the least room.
    await tester.tap(find.byKey(const Key('work-group-header')));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    final title = find.textContaining('Checking AssistantEntry');
    expect(title, findsOneWidget);
    final titleRight = tester.getRect(title).right;
    // Everything else on that row starts after the title ends.
    final row = find
        .ancestor(of: title, matching: find.byType(KitTappable))
        .first;
    for (final text
        in find.descendant(of: row, matching: find.byType(Text)).evaluate()) {
      if (identical(text.widget, tester.widget(title))) continue;
      final rect = tester.getRect(find.byWidget(text.widget));
      if (rect.width == 0) continue;
      // On the timeline the detail sits under the title; it must not share
      // the title's line and overlap it.
      final below = rect.top >= tester.getRect(title).bottom - 0.5;
      expect(
        below || rect.left >= titleRight,
        isTrue,
        reason: 'overlaps: ${(text.widget as Text).data}',
      );
    }
  });
}
