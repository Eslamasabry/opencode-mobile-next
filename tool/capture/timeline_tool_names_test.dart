// Before/after captures for the tool label slice (2026-10-08): the real
// ChatScreen's timeline sheet at 412x915 dp, dark theme, real fonts, no
// server calls. A step that only called tools is named by them.
//
//   flutter test --concurrency=1 --dart-define=TOOL_NAMES_CAPTURE=before \
//     tool/capture/timeline_tool_names_test.dart   # on the old code
//   flutter test --concurrency=1 tool/capture/timeline_tool_names_test.dart
//
// Output: docs/qa/timeline-tool-names-2026-10-08/<before|after>-<scene>.png
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _prefix = String.fromEnvironment(
  'TOOL_NAMES_CAPTURE',
  defaultValue: 'after',
);
const _dir = 'docs/qa/timeline-tool-names-2026-10-08';

class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Future<void> _settle(WidgetTester tester, [int frames = 8]) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Part _tool(String id, String name, Map<String, dynamic> input) => Part(
  id: id,
  messageID: 'msg_tools',
  callID: id,
  type: 'tool',
  toolName: name,
  toolState: ToolState.fromJson({
    'status': 'completed',
    'title': name,
    'input': input,
    'output': 'ok',
  }, toolName: name),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('timeline', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferencesStorePlatform.instance =
        InMemorySharedPreferencesStore.empty();
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = _Api()
      ..busy = {}
      ..messagesHandler = (_) async => [
        MessageWithParts(
          info: messageInfo('msg_user', 'user', created: now - 60000),
          parts: [
            Part(
              id: 'p1',
              messageID: 'msg_user',
              type: 'text',
              text: 'The checkout test is flaky. Find out why and fix it.',
            ),
          ],
        ),
        MessageWithParts(
          info: messageInfo(
            'msg_tools',
            'assistant',
            created: now - 50000,
            completed: now - 40000,
          ),
          parts: [
            _tool('t1', 'task_notification', {
              'status': 'completed',
              'summary': 'Release build',
            }),
            _tool('t2', 'oc-ui_show', {'id': 'fix', 'title': 'Choose a fix'}),
            _tool('t3', 'render_mermaid_diagram', {'source': 'graph TD'}),
          ],
        ),
        MessageWithParts(
          info: messageInfo(
            'msg_reply',
            'assistant',
            created: now - 30000,
            completed: now - 20000,
          ),
          parts: [
            Part(
              id: 'p2',
              messageID: 'msg_reply',
              type: 'text',
              text: 'Pick a fix on the card above and I will apply it.',
            ),
          ],
        ),
      ];
    final controller = await captureController(prefs: prefs, api: api);
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
    await tester.tap(find.byTooltip('Conversation menu'));
    await _settle(tester);
    await tester.ensureVisible(
      find.byKey(const ValueKey('session-menu-timeline')),
    );
    await _settle(tester, 2);
    await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/$_prefix-timeline.png',
      await capturePng(tester, key, pixelRatio: 1),
    );
    await tester.enterText(
      find.byKey(const Key('timeline-search')),
      'Choose a fix',
    );
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/$_prefix-timeline-search.png',
      await capturePng(tester, key, pixelRatio: 1),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  });
}
