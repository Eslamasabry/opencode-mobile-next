// The newest line of a working chat (its live line) stays fully above what
// floats over the transcript, also when a chip comes up over the composer in
// mid-turn (owner phone 2026-10-29: "Running a command" sat half under the
// Fast chip until new words arrived).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

class _InboxApi extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(forms: true, inbox: true, promptSteering: true);
}

List<MessageWithParts> _history() {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    for (var t = 0; t < 12; t++) ...[
      MessageWithParts(
        info: messageInfo(
          'msg_u$t',
          'user',
          created: now - 900000 + t * 1000,
          completed: now - 900000 + t * 1000,
        ),
        parts: [
          Part(
            id: 'p_u$t',
            messageID: 'msg_u$t',
            type: 'text',
            text: 'Step $t please.',
          ),
        ],
      ),
      MessageWithParts(
        info: messageInfo(
          'msg_a$t',
          'assistant',
          created: now - 899000 + t * 1000,
          completed: now - 898000 + t * 1000,
        ),
        parts: [
          Part(
            id: 'p_a$t',
            messageID: 'msg_a$t',
            type: 'text',
            text: 'Done with step $t. ' * 12,
          ),
        ],
      ),
    ],
    MessageWithParts(
      info: messageInfo(
        'msg_u99',
        'user',
        created: now - 120000,
        completed: now - 120000,
      ),
      parts: [
        Part(
          id: 'p_u99',
          messageID: 'msg_u99',
          type: 'text',
          text: 'Run the long one.',
        ),
      ],
    ),
    MessageWithParts(
      info: messageInfo('msg_a99', 'assistant', created: now - 110000),
      parts: [
        Part(
          id: 'p_a99',
          messageID: 'msg_a99',
          type: 'tool',
          callID: 'c99',
          toolName: 'bash',
          toolState: ToolState(
            status: 'running',
            input: {'command': 'sleep 150'},
          ),
        ),
      ],
    ),
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a chip that appears mid-turn leaves the live line clear', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final api = _InboxApi()..messagesHandler = (_) async => _history();
    final controller = await captureController(prefs: prefs, api: api);
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
      controller.dispose();
    });
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: GlobalKey(),
        controller: controller,
      ),
    );
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    // The list was positioned at an earlier row (a jump to a message, a
    // restored history page) and then read down to its end.
    tester
        .widget<ScrollablePositionedList>(find.byType(ScrollablePositionedList))
        .itemScrollController!
        .jumpTo(index: 5, alignment: .3);
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    await tester.drag(
      find.byType(ScrollablePositionedList),
      const Offset(0, -4000),
    );
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    double bandTop() =>
        tester.getRect(find.byKey(const ValueKey('kit-composer-band'))).top;
    final live = find.textContaining('Running');
    expect(live, findsWidgets);
    final before = tester.getRect(live.last).bottom;
    expect(before, lessThanOrEqualTo(bandTop()), reason: 'no chip yet');

    controller.handleEventForTesting(
      captureEvent('session.inbox.enqueued', {
        'sessionID': checkoutSessionID,
        'inboxID': 'ctx1',
        'item': {
          'type': 'synthetic',
          'payload': {'text': 'Background task finished'},
          'delivery': 'queue',
        },
      }),
    );
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    expect(find.byKey(const Key('pending-context-chip')), findsOneWidget);
    final after = tester.getRect(find.textContaining('Running').last).bottom;
    expect(after, lessThanOrEqualTo(bandTop()), reason: 'chip is up');
  });
}
