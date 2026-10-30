// Before/after captures of the Work tab's restart notice (quiet-notices
// slice, 2026-10-20): the real Conversations screen at 412x915 dp, dark.
//
//   flutter test --concurrency=1 --dart-define=NOTE_PHASE=before \
//     tool/capture/quiet_notices_test.dart
//   flutter test --concurrency=1 tool/capture/quiet_notices_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/quiet-notices/<phase>-dark.png
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';

import '../../test/support/chats_fakes.dart';
import 'fixtures.dart';

const _phase = String.fromEnvironment('NOTE_PHASE', defaultValue: 'after');
const _dir = '/home/eslam/Storage2/launch-footage/quiet-notices';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  testWidgets('restart notice $_phase', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final now = DateTime(2026, 10, 3, 17);
    final source = FakeChatFeedSource(
      items: [
        chat(
          'a',
          'Fix the login bug',
          status: ChatStatus.needsYou,
          at: now.subtract(const Duration(minutes: 2)),
        ),
        chat(
          'b',
          'Add dark mode',
          status: ChatStatus.running,
          at: now.subtract(const Duration(minutes: 9)),
        ),
        chat(
          'c',
          'Explain the build',
          at: now.subtract(const Duration(hours: 3)),
        ),
      ],
      projects: [project('alpha', chats: 3, needs: 1, kind: 'dart')],
    );
    final before = _phase == 'before';
    final status = KitStatus(
      kind: KitStatusKind.appStopped,
      id: 'app-stopped',
      icon: AppIconography.restart,
      message: before
          ? "OpenCode Mobile was closed at 4:54 PM. Your phone's OpenCode "
                'stopped with it and is running again.'
          : 'Restarted after the app closed',
      action: KitAction(label: 'Keep it running', onPressed: () {}),
      onDismiss: () {},
      quiet: !before,
    );
    final key = GlobalKey();
    await withClock(Clock.fixed(now), () async {
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: chatsApp(
            FakeChatsHost(source),
            AppConditionsScope(
              conditions: [status],
              child: const ChatsHomeScreen(),
            ),
          ),
        ),
      );
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/$_phase-dark.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
    });
  });
}
