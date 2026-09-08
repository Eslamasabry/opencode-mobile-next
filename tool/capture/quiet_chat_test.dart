// Real chat widgets with deterministic local content; no server or model calls.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import '../../test/support/setup_capture_preferences.dart';

import 'fixtures.dart';

class _QuietChatApi extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    testWidgets('quiet chat ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final prefs = await setupCapturePreferences();
      final api = _QuietChatApi()..busy = {};
      api.messagesHandler = (_) async => [
        MessageWithParts(
          info: MessageInfo(
            id: 'quiet-assistant',
            sessionID: checkoutSessionID,
            role: 'assistant',
          ),
          parts: [
            Part(
              type: 'text',
              text:
                  'The checkout is ready to review.\n\n'
                  'The basket restores after reopening, and the payment '
                  'form keeps your place if you go back.\n\n'
                  'Run locally with:\n\n```bash\nnpm run dev\n```\n\n'
                  'Try adding two items, then reopening the app.',
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
          light: light,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byTooltip('Copy code'), findsOneWidget);
      expect(tester.takeException(), isNull);
      final mode = light ? 'light' : 'dark';
      await writePng(
        'docs/qa/quiet-chat-2026-09-09/$mode-idle.png',
        await capturePng(tester, key),
      );
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Keep the basket after reopening.\nShow a useful empty state.\nCheck keyboard navigation.',
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/quiet-chat-2026-09-09/$mode-draft.png',
        await capturePng(tester, key),
      );
      await tester.pumpWidget(const SizedBox());
    });
  }
}
