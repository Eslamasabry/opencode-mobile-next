// Before/after captures of the chat while the agent works (vision slice,
// 2026-10-20): the real ChatScreen at 412x915 dp, real fonts, no server.
//
//   flutter test --concurrency=1 --dart-define=VISION_PHASE=before \
//     tool/capture/vision_chat_test.dart
//   flutter test --concurrency=1 tool/capture/vision_chat_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-chat/<phase>-<light|dark>.png
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _phase = String.fromEnvironment('VISION_PHASE', defaultValue: 'after');
const _dir = '/home/eslam/Storage2/launch-footage/vision-chat';

class _Api extends CaptureApi {
  // What OpenCode 2 advertises: it asks before acting and steers a send made
  // while a reply runs.
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

Part _tool(String id, String name, String status, Map<String, dynamic> in_) =>
    Part(
      id: id,
      messageID: 'msg_reply',
      callID: id,
      type: 'tool',
      toolName: name,
      toolState: ToolState.fromJson({
        'status': status,
        'title': name,
        'input': in_,
      }, toolName: name),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [false, true]) {
    testWidgets('working chat ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferencesStorePlatform.instance =
          InMemorySharedPreferencesStore.empty();
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
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
                text: 'Build me a small landing page for the bakery.',
              ),
            ],
          ),
          MessageWithParts(
            info: messageInfo('msg_reply', 'assistant', created: now - 15000),
            parts: [
              Part(
                id: 'part_words',
                messageID: 'msg_reply',
                type: 'text',
                text: 'I will plan it first, then write the page.',
              ),
              _tool('plan', 'todowrite', 'completed', {
                'todos': [
                  {'content': 'Plan the sections', 'status': 'completed'},
                  {'content': 'Write the page', 'status': 'in_progress'},
                  {'content': 'Add the styles', 'status': 'pending'},
                  {'content': 'Check it in a browser', 'status': 'pending'},
                ],
              }),
              _tool('write', 'write', 'running', {
                'filePath': '/project/index.html',
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
      await controller.setSessionAutoApproval(
        checkoutSessionID,
        const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
      );
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: checkoutSessionID),
          boundaryKey: key,
          controller: controller,
          light: light,
        ),
      );
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-${light ? 'light' : 'dark'}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      if (_phase != 'before') {
        // Where the model and Auto-approve went: the composer's +.
        await tester.tap(find.byKey(const Key('composer-tools-button')));
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
        await writePng(
          '$_dir/$_phase-plus-${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key, pixelRatio: 2),
        );
      }
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
    });
  }
}
