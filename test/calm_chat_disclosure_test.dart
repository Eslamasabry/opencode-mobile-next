import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitTopBar;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';
import 'support/composer_tools.dart';

class _CalmApi extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Future<void> _pump(
  WidgetTester tester, {
  List<MessageWithParts> transcript = const [],
}) async {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  final api = _CalmApi()
    ..busy = {}
    ..messagesHandler = (_) async => transcript;
  final controller = await captureController(prefs: prefs, api: api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

void main() {
  testWidgets(
    'idle chat keeps task details available without empty task chrome',
    (tester) async {
      await _pump(tester);
      expect(find.byKey(const Key('running-work-indicator')), findsNothing);
      expect(find.byKey(const Key('prompt-editor-button')), findsNothing);
      // Idle, the model chip sits over the composer (chat fidelity, 20 Oct
      // 2026); it is a real choice, not empty chrome.
      expect(find.byKey(const Key('composer-model-context')), findsOneWidget);
      // The title is the bar's (KitTopBar): a header naming the route.
      expect(
        find.descendant(
          of: find.byType(KitTopBar),
          matching: find.byKey(const Key('chat-title')),
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('session-actions-button')));
      await tester.pumpAndSettle();
      // Task details are the conversation menu's Subagents (slice-P10.2).
      await tester.tap(find.byKey(const ValueKey('session-menu-subagents')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('session-menu-find')), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('tools reveal secondary inputs and prompt operations on demand', (
    tester,
  ) async {
    await _pump(tester);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      // Past one line: the full-screen editor's corner shows.
      'Keep my draft\nand this line',
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('prompt-editor-button')), findsOneWidget);
    // "+" opens its tray in place; File is there, the rest behind More.
    await openComposerTray(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tool-attach')), findsOneWidget);
    await tester.tap(find.byKey(const Key('composer-tray-more')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tool-commands')), findsOneWidget);
    // Voice conversation waits under Advanced (the Context capsule is gone,
    // slice-P3.3).
    expect(find.byKey(const Key('composer-tool-conversation')), findsNothing);
    expect(find.byKey(const Key('composer-tool-clear')), findsNothing);
    final advanced = find.byKey(const Key('composer-tools-advanced'));
    await tester.ensureVisible(advanced);
    await tester.pumpAndSettle();
    await tester.tap(advanced);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tool-conversation')), findsOneWidget);
    // Tap the disclosure header, not the expanded tile's children.
    await tester.ensureVisible(find.text('More tools'));
    await tester.tap(find.text('More tools'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
    final prompts = find.byKey(const Key('composer-tools-prompts'));
    await tester.ensureVisible(prompts);
    await tester.tap(prompts);
    await tester.pumpAndSettle();
    final clear = find.byKey(const Key('composer-tool-clear'));
    await tester.ensureVisible(clear);
    // The sheet gained a project-file row; lay out the scrolled sheet first.
    await tester.pumpAndSettle();
    expect(clear.hitTestable(), findsOneWidget);
    expect(
      tester
          .widget<EditableText>(
            find.descendant(
              of: find.byKey(const Key('chat-composer-field')),
              matching: find.byType(EditableText),
            ),
          )
          .controller
          .text,
      // Past one line: the full-screen editor's corner shows.
      'Keep my draft\nand this line',
    );
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  for (final status in ['running', 'error']) {
    testWidgets(
      '$status tool group keeps progress clear and reveals actionable failures',
      (tester) async {
        await _pump(
          tester,
          transcript: [
            MessageWithParts(
              info: MessageInfo(
                id: 'tools',
                sessionID: checkoutSessionID,
                role: 'assistant',
              ),
              parts: [
                for (final (id, state) in [
                  ('read', 'completed'),
                  ('test', status),
                ])
                  Part(
                    id: id,
                    callID: id,
                    type: 'tool',
                    toolName: 'bash',
                    toolState: ToolState.fromJson({
                      'status': state,
                      'input': {
                        'command': id == 'read' ? 'cat basket.ts' : 'npm test',
                      },
                      if (state == 'error') 'error': 'Checkout test failed',
                      if (state == 'completed') 'output': 'Basket source',
                    }, toolName: 'bash'),
                  ),
              ],
            ),
          ],
        );
        expect(find.byKey(const Key('work-group-header')), findsOneWidget);
        expect(
          find.byKey(const Key('embedded-tool-row')),
          status == 'error' ? findsWidgets : findsNothing,
        );
        if (status == 'running') {
          await tester.tap(find.byKey(const Key('work-group-header')));
          await tester.pump();
          expect(find.byKey(const Key('embedded-tool-row')), findsWidgets);
        }
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}
