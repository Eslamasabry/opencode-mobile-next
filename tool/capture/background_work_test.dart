// Render of the owner's chat (2026-10-27) with the "Still running in the
// background" line under the finished turn: a phone screen (412x915, dark)
// with the app's real fonts.
//
//   flutter test --concurrency=1 tool/capture/background_work_test.dart
//
// Output: build/capture/background-running/chat-background-work-dark.png
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../test/chat_background_work_test.dart' show pumpOwnerChat;
import 'fixtures.dart';

void main() {
  setUpAll(loadCaptureFonts);

  testWidgets('chat with work still running in the background · dark', (
    tester,
  ) async {
    final boundary = GlobalKey();
    final startedAt = clock
        .now()
        .subtract(const Duration(minutes: 2, seconds: 30))
        .millisecondsSinceEpoch;
    await pumpOwnerChat(tester, startedAt: startedAt, boundaryKey: boundary);
    await tester.pump(const Duration(milliseconds: 600));
    await writePng(
      'build/capture/background-running/chat-background-work-dark.png',
      await capturePng(tester, boundary),
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
