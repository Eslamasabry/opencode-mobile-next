// While the agent works the chat shows the conversation, one live line in the
// turn and the composer (vision slice, 2026-10-20): the live line names the
// step in hand and, when the agent wrote a todo list, "Step 2 of 4"; no
// folded tool line sits beside it; the composer says "Steer while it works…"
// and no chips row stands over it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

class _Api extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(permissionRequests: true, promptSteering: true);

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Part _tool(String id, String name, String status, Map<String, dynamic> input) =>
    Part(
      id: id,
      messageID: 'msg_reply',
      callID: id,
      type: 'tool',
      toolName: name,
      toolState: ToolState.fromJson({
        'status': status,
        'title': name,
        'input': input,
      }, toolName: name),
    );

Future<void> _open(WidgetTester tester, {required bool todos}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final now = DateTime.now().millisecondsSinceEpoch;
  final api = _Api()
    ..messagesHandler = (_) async => [
      MessageWithParts(
        info: messageInfo('msg_user', 'user', created: now - 20000),
        parts: [
          Part(
            id: 'part_prompt',
            messageID: 'msg_user',
            type: 'text',
            text: 'Build me a landing page.',
          ),
        ],
      ),
      MessageWithParts(
        info: messageInfo('msg_reply', 'assistant', created: now - 15000),
        parts: [
          if (todos)
            _tool('plan', 'todowrite', 'completed', {
              'todos': [
                {'content': 'Plan', 'status': 'completed'},
                {'content': 'Write the page', 'status': 'in_progress'},
                {'content': 'Style it', 'status': 'pending'},
                {'content': 'Check it', 'status': 'pending'},
              ],
            }),
          // Agents other than OpenCode say `file_path`.
          _tool('write', 'write', 'running', {
            'file_path': '/project/index.html',
          }),
        ],
      ),
    ];
  final controller = CaptureController(
    SeededProfileStore(
      prefs: prefs,
      seeded: [
        ServerProfile(
          id: 'laptop',
          name: 'Laptop',
          baseUrl: 'http://192.168.1.20:4096',
        ),
      ],
    ),
  );
  controller
    ..api = api
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = Set.of(api.busy);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('one live line names the step and the plan; no tool line', (
    tester,
  ) async {
    await _open(tester, todos: true);
    final live = find.byKey(const ValueKey('kit-turn-live-line'));
    expect(live, findsOneWidget);
    // The file name shows even though this agent says `file_path`.
    expect(
      find.descendant(
        of: live,
        // The file name is isolated left to right (KitBidi).
        matching: find.textContaining(RegExp(r'^Writing \W?index\.html\W? · ')),
      ),
      findsOneWidget,
    );
    expect(find.text('Step 2 of 4'), findsOneWidget);
    // No second line for the same step.
    expect(find.byKey(const Key('work-group')), findsNothing);
    expect(find.textContaining('index.html'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('no todo list, no step line', (tester) async {
    await _open(tester, todos: false);
    expect(find.byKey(const ValueKey('kit-turn-live-line')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-turn-live-step')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the composer says to steer, and the chips row carries Stop', (
    tester,
  ) async {
    await _open(tester, todos: true);
    expect(find.text('Steer while it works…'), findsOneWidget);
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
    // One row: the model chip and Stop share it, Stop at the trailing end.
    final model = tester.getRect(
      find.byKey(const Key('composer-model-context')),
    );
    final stop = tester.getRect(find.byKey(const Key('chat-stop-button')));
    expect(stop.center.dy, moreOrLessEquals(model.center.dy, epsilon: 8));
    expect(stop.left, greaterThanOrEqualTo(model.right));
  });
}
