// Reading a long chat's history scrolls without building the
// earlier-messages pill again on every frame (scroll-perf, 2026-10-24).
//
// The pill listened to the transcript's item positions, which change on
// every frame of a scroll, so it was built again on each of them: under
// Crystal its lens with it, and its layer was laid out again and the page
// around the transcript recorded again, the floating composer's glass
// included. On the emulator a scroll through history under Crystal missed
// more than half its frames (about 38 ms a frame). Now the pill listens to
// its count alone.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

const _reply =
    'The look is unchanged: same hues, same pulses, same halos. It just '
    'stops paying the glow tax sixty times a second.\n\n'
    '- **Particles batched:** cached round sprites instead of arc paths.\n'
    '- **Pixel ratio capped at 2:** about a third fewer fragments.\n'
    '- Brick position math no longer recomputes its constants.';

/// Twenty turns: forty messages, more than the thirty after which the
/// pill counts the messages above the screen.
List<MessageWithParts> _history() {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    for (var t = 0; t < 20; t++) ...[
      MessageWithParts(
        info: messageInfo(
          'msg_u$t',
          'user',
          created: now - (20 - t) * 60000,
          completed: now - (20 - t) * 60000,
        ),
        parts: [
          Part(
            id: 'p_u$t',
            messageID: 'msg_u$t',
            type: 'text',
            text: 'Make the game smoother on my phone, step $t.',
          ),
        ],
      ),
      MessageWithParts(
        info: messageInfo(
          'msg_a$t',
          'assistant',
          created: now - (20 - t) * 60000 + 1000,
          completed: now - (20 - t) * 60000 + 9000,
        ),
        parts: [
          Part(id: 'p_a$t', messageID: 'msg_a$t', type: 'text', text: _reply),
        ],
      ),
    ],
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('scrolling through history builds the earlier-messages pill '
      'again only when its count changes', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final api = CaptureApi()
      ..busy = {}
      ..messagesHandler = (_) async => _history();
    final controller = await captureController(prefs: prefs, api: api);
    controller.busySessions = {};
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
    final transcript = find
        .descendant(
          of: find.byKey(const ValueKey('transcript-repaint-boundary')),
          matching: find.byType(Scrollable),
        )
        .first;

    // Into the history: the list is reversed, so a drag down reads older
    // turns; both pills come up.
    await tester.drag(transcript, const Offset(0, 1500));
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    final earlier = find.byKey(const ValueKey('earlier-messages-pill'));
    expect(earlier, findsOneWidget);
    expect(find.textContaining('earlier messages'), findsOneWidget);

    // Every build of the top pill, with the words it was built with.
    final built = <String>[];
    debugOnRebuildDirtyWidget = (element, _) {
      final widget = element.widget;
      if (widget is KitJumpPill && widget.edge == KitJumpEdge.top) {
        built.add(widget.label);
      }
    };
    final gesture = await tester.startGesture(tester.getCenter(transcript));
    try {
      for (var i = 0; i < 30; i++) {
        await gesture.moveBy(Offset(0, i < 20 ? 20 : -20));
        await tester.pump(const Duration(milliseconds: 16));
      }
    } finally {
      debugOnRebuildDirtyWidget = null;
      await gesture.up();
    }
    await tester.pump(const Duration(seconds: 1));

    var changes = 0;
    for (var i = 1; i < built.length; i++) {
      if (built[i] != built[i - 1]) changes++;
    }
    expect(
      built.length,
      lessThanOrEqualTo(changes + 1),
      reason:
          'the pill was built ${built.length} times in 30 frames of a '
          'scroll for $changes changes of its words: $built',
    );
  });
}
