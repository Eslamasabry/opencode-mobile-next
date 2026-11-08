// Golden renders of the phone Conversations top: the title row with plain
// search and Settings icons, then one row with the connection pill, a
// divider and the filters. "before" draws the old layout (the shell bar's
// pill and ringed buttons, then the title, then the chips) for comparison.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/chats_home_header_golden_test.dart
import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/app_theme.dart' show AppStatusTone;
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/chats_fakes.dart';

final _now = DateTime(2026, 10, 3, 12);

ChatsHomeControls _controls(
  String server, {
  String status = 'Connected',
  AppStatusTone tone = AppStatusTone.ok,
}) => ChatsHomeControls(
  server: server,
  status: status,
  tone: tone,
  onServer: () {},
  onSearch: () {},
  onSettings: () {},
);

FakeChatFeedSource _source() => FakeChatFeedSource(
  items: [
    chat(
      'b',
      'Add dark mode',
      status: ChatStatus.running,
      at: _now.subtract(const Duration(minutes: 9)),
    ),
    chat(
      'c',
      'Explain the build',
      status: ChatStatus.running,
      at: _now.subtract(const Duration(hours: 1)),
    ),
    chat(
      'd',
      'Rename the settings page',
      at: _now.subtract(const Duration(days: 3)),
    ),
  ],
  projects: [project('alpha', chats: 3)],
  lastUsed: '/root/projects/alpha',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  final scenes = <String, ({ChatsHomeControls? controls, Locale locale})>{
    'before': (controls: null, locale: const Locale('en')),
    'after': (controls: _controls('This phone'), locale: const Locale('en')),
    'after_long': (
      controls: _controls('Eslam’s workstation at the office (secure):4443'),
      locale: const Locale('en'),
    ),
    'after_offline': (
      controls: _controls(
        'This phone',
        status: 'Offline',
        tone: AppStatusTone.failure,
      ),
      locale: const Locale('en'),
    ),
    'after_rtl': (
      controls: _controls('هذا الهاتف'),
      locale: const Locale('ar'),
    ),
  };

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(:key, :value) in scenes.entries) {
      testWidgets('header $key · $mode', (tester) async {
        tester.view.physicalSize = const Size(412, 915);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        final boundary = GlobalKey();
        final host = FakeChatsHost(_source());
        final Widget home = value.controls == null
            ? KitScreen(
                topBar: KitTopBar.shell(
                  controls: KitShellControls(
                    server: 'This phone',
                    serverStatus: 'Connected',
                    serverTone: AppStatusTone.ok,
                    onServer: () {},
                    onSearch: () {},
                    onSettings: () {},
                  ),
                ),
                body: const ChatsHomeScreen(),
              )
            : KitScreen(
                page: true,
                body: ChatsHomeScreen(controls: value.controls),
              );
        await withClock(Clock.fixed(_now), () async {
          await tester.pumpWidget(
            RepaintBoundary(
              key: boundary,
              child: chatsApp(host, home, light: light, locale: value.locale),
            ),
          );
          await tester.pump(const Duration(milliseconds: 400));
          await tester.pumpAndSettle();
        });
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('chats_home_header_${key}_$mode.png'),
        );
        await tester.pumpWidget(const SizedBox.shrink());
        debugDefaultTargetPlatformOverride = null;
      });
    }
  }
}
