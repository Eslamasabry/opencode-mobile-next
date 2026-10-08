// Before/after captures for three chat transcript fixes (2026-10-08): the
// real ChatScreen at 412x915 dp, dark theme, real fonts, no server calls.
//
//   flutter test --concurrency=1 --dart-define=CHAT_TURN_CAPTURE=before \
//     tool/capture/chat_turn_fixes_test.dart   # on the old code
//   flutter test --concurrency=1 tool/capture/chat_turn_fixes_test.dart
//   (--dart-define=CHAT_TURN_ONLY=<scene> renders one scene: stopped,
//   card-tool, stalled, work)
//
// Output: docs/qa/chat-turn-fixes-2026-10-08/<before|after>-<scene>.png
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _prefix = String.fromEnvironment(
  'CHAT_TURN_CAPTURE',
  defaultValue: 'after',
);
const _only = String.fromEnvironment('CHAT_TURN_ONLY');
const _size = Size(412, 915);

bool _wanted(String scene) => _only.isEmpty || _only == scene;
const _dir = 'docs/qa/chat-turn-fixes-2026-10-08';

class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

/// The capture connection with the stall watchdog's finding set by hand.
class _Controller extends CaptureController {
  _Controller(super.store);

  TurnStallDiagnosis? stall;

  @override
  TurnStallDiagnosis? turnStallFor(String sessionId) => stall;

  /// The server's word that [sessionId] went idle, as its status event
  /// would leave it, without the test-only event entry point.
  void settle(String sessionId) {
    busySessions.remove(sessionId);
    notifyListeners();
  }

  /// Something changed that the chat reads on its next build.
  void touch() => notifyListeners();
}

Future<void> _settle(WidgetTester tester, [int frames = 8]) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<(_Controller, GlobalKey)> _chat(WidgetTester tester, _Api api) async {
  tester.view.physicalSize = _size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final prefs = await SharedPreferences.getInstance();
  await prefs.clear();
  final controller = _Controller(
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
  final key = GlobalKey();
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: key,
      controller: controller,
    ),
  );
  await _settle(tester);
  return (controller, key);
}

Future<void> _shot(WidgetTester tester, GlobalKey key, String scene) async {
  expect(tester.takeException(), isNull);
  await writePng(
    '$_dir/$_prefix-$scene.png',
    await capturePng(tester, key, pixelRatio: 1),
  );
}

Future<void> _unmount(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 1));
}

List<MessageWithParts> _turn(int now, List<Part> reply) => [
  MessageWithParts(
    info: messageInfo('msg_user', 'user', created: now - 60000),
    parts: [
      Part(
        id: 'part_prompt',
        messageID: 'msg_user',
        type: 'text',
        text: 'The checkout test is flaky. Find out why and fix it.',
      ),
    ],
  ),
  MessageWithParts(
    info: messageInfo(
      'msg_reply',
      'assistant',
      created: now - 50000,
      completed: now - 1000,
    ),
    parts: reply,
  ),
];

Part _words(String text) =>
    Part(id: 'part_words', messageID: 'msg_reply', type: 'text', text: text);

Part _tool(String name, String status, Map<String, dynamic> input) => Part(
  id: 'part_tool',
  messageID: 'msg_reply',
  callID: 'call_tool',
  type: 'tool',
  toolName: name,
  toolState: ToolState.fromJson({
    'status': status,
    'title': name,
    'input': input,
  }, toolName: name),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('stopped turn', (tester) async {
    if (!_wanted('stopped')) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = _Api()
      ..messagesHandler = (_) async => _turn(now, [
        _words('Looking at the checkout test and its fixtures now.'),
        _tool('read', 'completed', {
          'filePath': 'test/checkout_bloc_test.dart',
        }),
      ]);
    final (controller, key) = await _chat(tester, api);
    await tester.tap(find.byKey(const Key('chat-stop-button')));
    await _settle(tester, 2);
    controller.settle(checkoutSessionID);
    await _settle(tester);
    await _shot(tester, key, 'stopped');
    await _unmount(tester);
  });

  testWidgets('agent card tool', (tester) async {
    if (!_wanted('card-tool')) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = _Api()
      ..messagesHandler = (_) async => _turn(now, [
        _words('Two fixes would work. Let me ask which you prefer.'),
        _tool('oc-ui_show', 'running', {'id': 'fix', 'title': 'Choose a fix'}),
      ]);
    final (_, key) = await _chat(tester, api);
    await _shot(tester, key, 'card-tool');
    await _unmount(tester);
  });

  testWidgets('stalled turn', (tester) async {
    if (!_wanted('stalled')) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = _Api()
      ..messagesHandler = (_) async =>
          _turn(now, [_words('Running the checkout tests again.')]);
    final (controller, key) = await _chat(tester, api);
    controller.stall = const TurnStallDiagnosis(
      kind: TurnStallKind.modelSlow,
      evidence: TurnStallEvidence(
        transportConnected: true,
        endpointReachable: true,
      ),
      silentFor: Duration(seconds: 52),
    );
    controller.touch();
    await _settle(tester);
    await _shot(tester, key, 'stalled');
    await _unmount(tester);
  });

  // The owner's Claude Code turn (2026-10-08): a long running stretch of
  // work with a background-task notice in it, folded and then opened.
  testWidgets('running work, folded and opened', (tester) async {
    if (!_wanted('work')) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    Part tool(
      String id,
      String name,
      String status,
      Map<String, dynamic> in_,
    ) => Part(
      id: id,
      messageID: 'msg_reply',
      callID: id,
      type: 'tool',
      toolName: name,
      toolState: ToolState.fromJson({
        'status': status,
        'input': in_,
      }, toolName: name),
    );
    final api = _Api()
      ..messagesHandler = (_) async => _turn(now, [
        _words('Everything passes. Final full run and build:'),
        tool('t1', 'bash', 'completed', {
          'command': 'cd /root/projects/shopfront; python3 tool/check.py',
        }),
        tool('t2', 'task_notification', 'completed', {
          'status': 'completed',
          'summary': 'Background build finished',
        }),
        tool('t3', 'bash', 'running', {
          'command': 'cd /root/projects/shopfront; tool/build.sh --release',
        }),
      ]);
    final (_, key) = await _chat(tester, api);
    await _shot(tester, key, 'work-collapsed');
    await tester.tap(find.byKey(const Key('work-group-header')));
    await _settle(tester);
    await _shot(tester, key, 'work-expanded');
    await _unmount(tester);
  });
}
