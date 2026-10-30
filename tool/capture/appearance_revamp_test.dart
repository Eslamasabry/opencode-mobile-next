// Review frames for the Appearance studio, final design (2026-10-21): dark,
// Saver, light, the Accent row, Surfaces, 320 dp at 2x text, Arabic, tablet
// and the other tabs. One full screen per image, opened as the app opens it
// (with Back) and in the theme main.dart builds from the choices, written to
// APPEARANCE_SHOTS_DIR (default build/appearance-review).
// The live look is painted (a frozen frame).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../../test/support/settings_scenes.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

final _out =
    Platform.environment['APPEARANCE_SHOTS_DIR'] ?? 'build/appearance-review';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    // Arabic falls back to Noto, as on a device; AppTheme.forLocale names
    // the platform sans (Roboto) for it.
    Future<void> family(String name, List<String> paths) async {
      final loader = FontLoader(name);
      for (final path in paths) {
        final bytes = File(path).readAsBytesSync();
        loader.addFont(Future.value(ByteData.sublistView(bytes)));
      }
      await loader.load();
    }

    await family('Noto Sans Arabic', [
      for (final face in ['Regular', 'Bold'])
        'test/fixtures/fonts/NotoSansArabic-$face.ttf',
    ]);
    await family('sans-serif', [
      for (final face in ['Regular', 'Medium', 'Bold'])
        'tool/capture/fonts/Roboto-$face.ttf',
    ]);
  });
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitLiveBackground.frozenSeconds = 8;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  /// Live parts keep a ticker running, so settle by time, not by quiet.
  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 12; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  Future<void> shoot(
    WidgetTester tester,
    String name, {
    required bool light,
    String tab = 'theme',
    Size size = const Size(412, 915),
    double textScale = 1,
    Locale? locale,
    String? scrollTo,
    void Function(dynamic controller)? configure,
  }) async {
    tester.platformDispatcher.textScaleFactorTestValue = textScale;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    if (locale != null) {
      tester.platformDispatcher.localesTestValue = [locale];
      addTearDown(tester.platformDispatcher.clearLocalesTestValue);
    }
    final boundary = GlobalKey();
    final done = await mountSettingsScene(
      tester,
      SettingsScene.appearance,
      light: light,
      boundary: boundary,
      size: size,
      configure: configure,
      followAppearance: true,
      pushed: true,
    );
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await settle(tester);
    if (tab != 'theme') {
      await tester.tap(find.byKey(ValueKey('appearance-tab-$tab')));
      await settle(tester);
    }
    if (scrollTo != null) {
      await tester.ensureVisible(find.byKey(ValueKey(scrollTo)));
      await settle(tester);
    }
    await writePng(
      '$_out/$name-${light ? 'light' : 'dark'}.png',
      await capturePng(tester, boundary, pixelRatio: 2),
    );
    await done();
  }

  void saver(dynamic controller) =>
      controller.appearance.value = AppAppearance.saver;

  testWidgets('1 dark main', (tester) async {
    await shoot(tester, '1-main', light: false);
  });
  testWidgets('2 saver', (tester) async {
    await shoot(tester, '2-saver', light: false, configure: saver);
  });
  testWidgets('3 light', (tester) async {
    await shoot(tester, '3-main', light: true);
  });
  testWidgets('4 accent row, pink chosen', (tester) async {
    await shoot(
      tester,
      '4-accent',
      light: false,
      scrollTo: 'appearance-accent',
      configure: (controller) => controller.appearanceAccent.value = 'pink',
    );
  });
  testWidgets('5 surfaces', (tester) async {
    await shoot(
      tester,
      '5-surfaces',
      light: false,
      scrollTo: 'appearance-surfaces',
    );
  });
  testWidgets('5 surfaces under saver', (tester) async {
    await shoot(
      tester,
      '5-surfaces-saver',
      light: false,
      scrollTo: 'appearance-surfaces',
      configure: saver,
    );
  });
  testWidgets('5 surfaces light', (tester) async {
    await shoot(
      tester,
      '5-surfaces',
      light: true,
      scrollTo: 'appearance-surfaces',
    );
  });
  testWidgets('6 320 dp at 2x text', (tester) async {
    await shoot(
      tester,
      '6-320-text2',
      light: false,
      size: const Size(320, 700),
      textScale: 2,
    );
  });
  testWidgets('7 arabic', (tester) async {
    await shoot(tester, '7-arabic', light: false, locale: const Locale('ar'));
  });
  testWidgets('8 tablet', (tester) async {
    await shoot(tester, '8-tablet', light: false, size: const Size(1180, 820));
  });
  testWidgets('9 catppuccin with the circuit look', (tester) async {
    await shoot(
      tester,
      '9-catppuccin-circuit',
      light: false,
      configure: (controller) {
        // Stored too, so the store's next publish keeps it.
        controller.themePack.value = ThemePackId.catppuccin;
        controller.setThemePack(ThemePackId.catppuccin);
        controller.setEffects(const KitEffects(liveStyle: 'circuit'));
      },
    );
  });
  for (final tab in ['text', 'background', 'glow', 'motion']) {
    testWidgets('tab $tab', (tester) async {
      await shoot(tester, '0-$tab', light: false, tab: tab);
    });
  }

  // The real chat screen with the aurora look on: what the preview promises.
  testWidgets('chat with the aurora background', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await captureController(
      prefs: await setupCapturePreferences(),
      api: CaptureApi()..busy = {},
    );
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: boundary,
        controller: controller,
      ),
    );
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await settle(tester);
    await writePng(
      '$_out/4-real-chat-aurora-dark.png',
      await capturePng(tester, boundary, pixelRatio: 2),
    );
  });

  // A short real conversation (two bubbles and a tool row) over the aurora.
  testWidgets('chat conversation over the aurora', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = CaptureApi()
      ..busy = {}
      ..messagesHandler = (_) async => [
        MessageWithParts(
          info: messageInfo('msg_user', 'user', created: now - 60000),
          parts: [
            Part(
              id: 'part_prompt',
              messageID: 'msg_user',
              type: 'text',
              text: 'The checkout test is flaky. Find out why and fix it.',
            ),
          ],
        ),
        MessageWithParts(
          info: messageInfo(
            'msg_reply',
            'assistant',
            created: now - 50000,
            completed: now - 1000,
          ),
          parts: [
            Part(
              id: 'part_tool',
              messageID: 'msg_reply',
              callID: 'call_tool',
              type: 'tool',
              toolName: 'bash',
              toolState: ToolState.fromJson({
                'status': 'completed',
                'input': {'command': 'python3 tool/check.py'},
              }, toolName: 'bash'),
            ),
            Part(
              id: 'part_words',
              messageID: 'msg_reply',
              type: 'text',
              text:
                  'It was a race in the cart total: two updates landed out '
                  'of order. I made the update atomic and the test now '
                  'passes ten runs in a row.',
            ),
          ],
        ),
      ];
    final controller = await captureController(
      prefs: await setupCapturePreferences(),
      api: api,
    );
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: boundary,
        controller: controller,
      ),
    );
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await settle(tester);
    await writePng(
      '$_out/6-chat-conversation-aurora-dark.png',
      await capturePng(tester, boundary, pixelRatio: 2),
    );
  });
}
