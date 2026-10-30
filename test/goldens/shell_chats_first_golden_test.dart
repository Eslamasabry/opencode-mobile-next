// Golden renders of the chats-first shell: the Files page (the large title,
// the project under it, the project's files; the tools in the bar) and the
// same page opened from the conversation's project chip. 412x915, dark and light,
// with the app's real fonts and no server.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/shell_chats_first_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart'
    show openProjectFiles;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../support/work_tab_fixture.dart';

/// One page of history.
class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

List<MessageWithParts> _turn() {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo(
        'msg_user',
        'user',
        created: now - 95 * 1000,
        completed: now - 95 * 1000,
      ),
      parts: [
        Part(
          id: 'part_user',
          messageID: 'msg_user',
          type: 'text',
          text: userPrompt,
        ),
      ],
    ),
    MessageWithParts(
      info: messageInfo(
        'msg_assistant',
        'assistant',
        created: now - 80 * 1000,
        completed: now - 4 * 1000,
      ),
      parts: [textPart('part_intro', answerIntro)],
    ),
  ];
}

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('shell · Files page · $mode', (tester) async {
      _mockSecureStorage(tester);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await workController();
      final boundary = GlobalKey();
      try {
        await tester.pumpWidget(
          captureApp(
            home: const HomeScreen(),
            boundaryKey: boundary,
            controller: controller,
            light: light,
          ),
        );
        await tester.pump();
        // A phone has no Files tab: the project's Files are a page.
        unawaited(
          openProjectFiles(tester.element(find.byType(HomeScreen)), controller),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 600));
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('shell_files_page_$mode.png'),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        controller.dispose();
        await tester.pump();
      }
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));

    testWidgets('chat · project chip opens the Files page · $mode', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final api = _Api()
        ..busy = {}
        ..messagesHandler = (_) async => _turn();
      final controller = await captureController(prefs: prefs, api: api);
      final boundary = GlobalKey();
      try {
        await tester.pumpWidget(
          captureApp(
            home: ChatScreen(sessionID: checkoutSessionID),
            boundaryKey: boundary,
            controller: controller,
            light: light,
          ),
        );
        for (var i = 0; i < 8; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
        await tester.tap(find.byKey(const ValueKey('chat-project-chip')));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('chat_project_files_page_$mode.png'),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(seconds: 10));
        controller.dispose();
      }
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
  }
}
