// Actual chat review journey over local fixtures; no real authorization.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _ReviewApi extends CaptureApi {
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
    testWidgets('review and reject preserves draft ${light ? 'light' : 'dark'}', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final api = _ReviewApi()..busy = {};
      api.messagesHandler = (_) async => [
        MessageWithParts(
          info: MessageInfo(
            id: 'review-answer',
            sessionID: checkoutSessionID,
            role: 'assistant',
          ),
          parts: [
            Part(
              type: 'text',
              text:
                  'I can update the welcome message. Review the proposed change before deciding.',
            ),
          ],
        ),
      ];
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: api,
      );
      addTearDown(controller.dispose);
      controller.permissions = {
        'review-edit': PermissionRequest(
          id: 'review-edit',
          sessionID: checkoutSessionID,
          permission: 'edit',
          patterns: const ['welcome.txt'],
          metadata: const {
            'filePath': 'welcome.txt',
            'diff':
                '--- welcome.txt\n+++ welcome.txt\n@@ -1 +1 @@\n-Hello\n+Welcome aboard!',
          },
          always: const ['*.txt'],
        ),
      };
      final boundary = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: checkoutSessionID),
          boundaryKey: boundary,
          controller: controller,
          light: light,
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Keep the greeting concise.',
      );
      await tester.pumpAndSettle();
      final mode = light ? 'light' : 'dark';
      Future<void> shot(String state) async {
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/page-reviews/chat/$mode-$state.png',
          await capturePng(tester, boundary),
        );
      }

      expect(find.text('Allow once'), findsNothing);
      await shot('ready');
      await tester.tap(find.byKey(const Key('permission-card-review')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('permission-diff-preview')), findsOneWidget);
      await shot('review');
      await tester.tap(find.byKey(const Key('permission-reject')));
      await tester.pumpAndSettle();
      if (find
          .byKey(const Key('permission-reject-send'))
          .evaluate()
          .isNotEmpty) {
        await tester.tap(find.byKey(const Key('permission-reject-send')));
        await tester.pumpAndSettle();
      }
      expect(controller.answered.single.reply, 'reject');
      expect(controller.permissions, isEmpty);
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .controller!
            .text,
        'Keep the greeting concise.',
      );
      await shot('returned');
      await tester.pumpWidget(const SizedBox());
    });
  }
}
