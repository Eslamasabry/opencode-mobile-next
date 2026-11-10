// Home-screen captures of the conversation widget (vision slice "Home-screen
// widget", 2026-10-20): the native card as Android draws it, on a 412x915 dp
// home screen, 2x pixels, real fonts, blurred shadows.
//
// The card itself is not drawn here. WidgetCardRenderTest applies the real
// RemoteViews layouts and binders under Robolectric's native graphics and
// writes one PNG per state and size (3x pixels). This test only places those
// PNGs on a home screen (the aurora wallpaper, the clock, a dock), so the
// picture is the launcher's, never a Flutter imitation of it.
//
//   WIDGET_RENDER_OUT=/tmp/wrender gradle -p android :app:testReleaseUnitTest \
//     --tests '*WidgetCardRenderTest*'
//   TZ=UTC flutter test --concurrency=1 --dart-define=WIDGET_RENDERS=/tmp/wrender \
//     --dart-define=VISION_OUT=<dir> tool/capture/vision_widget_test.dart

import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';

const _renders = String.fromEnvironment(
  'WIDGET_RENDERS',
  defaultValue: '/tmp/wrender',
);
const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/vision-assets/out/vision-widget',
);

/// The Robolectric renders are 3x (xxhdpi).
const _renderScale = 3.0;

enum _Scene {
  needsDark('needs-dark', ['needs-4x2']),
  needsLight('needs-light', ['needs-4x2-light'], light: true),
  working('working-dark', ['working-4x2']),
  idle('idle-dark', ['idle-4x2']),
  question('question-open-only-dark', ['question-4x2']),
  longCommand('long-command-dark', ['needs-long-4x2', 'needs-long-2x2']),
  offline('offline-dark', ['offline-4x2']),
  sizes('sizes-dark', ['needs-2x2', 'working-4x1', 'idle-2x1']),
  tall('tall-4x4-dark', ['needs-4x4']),
  small('small-320-text2-dark', [
    'needs-320-2x',
    'working-320-2x',
  ], size: Size(320, 693)),
  arabic('arabic-rtl-dark', ['needs-4x2-ar'], rtl: true);

  const _Scene(
    this.file,
    this.cards, {
    this.light = false,
    this.size = const Size(412, 915),
    this.rtl = false,
  });
  final String file;
  final List<String> cards;
  final bool light;
  final Size size;
  final bool rtl;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      final missing = [
        for (final card in scene.cards)
          if (!File('$_renders/$card.png').existsSync()) card,
      ];
      if (missing.isNotEmpty) {
        markTestSkipped('No native render for $missing in $_renders');
        return;
      }
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      final images = [
        for (final card in scene.cards)
          await decodeImageFile(tester, '$_renders/$card.png'),
      ];
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: scene.light),
            home: Directionality(
              textDirection: scene.rtl ? TextDirection.rtl : TextDirection.ltr,
              child: _HomeScreen(cards: images, light: scene.light),
            ),
          ),
        ),
      );
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}

/// A plain launcher home screen: the aurora wallpaper, the clock, the cards
/// stacked from the middle, and a dock. Launcher chrome only; the cards are
/// the native renders.
class _HomeScreen extends StatelessWidget {
  const _HomeScreen({required this.cards, required this.light});

  final List<ui.Image> cards;
  final bool light;

  @override
  Widget build(BuildContext context) {
    final ink = light ? const Color(0xFF111214) : const Color(0xFFF3F3F1);
    final soft = ink.withValues(alpha: 0.82);
    // The launcher's own type: the app's font, so nothing falls back to the
    // test font.
    final base = Theme.of(context).textTheme.bodyMedium!.copyWith(color: ink);
    return DefaultTextStyle(
      style: base,
      child: KitLiveBackground(
        ignoreSetting: true,
        strength: 1,
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _StatusBar(ink: ink),
                const SizedBox(height: 44),
                Padding(
                  padding: const EdgeInsetsDirectional.only(start: 12),
                  child: Text(
                    '10:00',
                    style: TextStyle(
                      color: ink,
                      fontSize: 76,
                      fontWeight: FontWeight.w300,
                      height: 1,
                      letterSpacing: -2,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsetsDirectional.only(start: 14, top: 8),
                  child: Text(
                    'Tuesday, October 20',
                    style: TextStyle(color: soft, fontSize: 17),
                  ),
                ),
                const Spacer(),
                Wrap(
                  spacing: 12,
                  runSpacing: 14,
                  children: [
                    for (final card in cards)
                      DecoratedBox(
                        // The launcher's own widget shadow.
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(24),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x40000000),
                              blurRadius: 18,
                              offset: Offset(0, 6),
                            ),
                          ],
                        ),
                        child: RawImage(
                          image: card,
                          scale: _renderScale,
                          filterQuality: FilterQuality.medium,
                        ),
                      ),
                  ],
                ),
                const Spacer(),
                _Dock(ink: ink, light: light),
                const SizedBox(height: 18),
                Center(
                  child: Container(
                    width: 120,
                    height: 4,
                    decoration: BoxDecoration(
                      color: ink.withValues(alpha: 0.7),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _StatusBar extends StatelessWidget {
  const _StatusBar({required this.ink});
  final Color ink;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(8, 10, 8, 0),
    child: Row(
      textDirection: TextDirection.ltr,
      children: [
        Text('10:00', style: TextStyle(color: ink, fontSize: 14)),
        const Spacer(),
        Icon(Icons.signal_cellular_alt, size: 16, color: ink),
        const SizedBox(width: 6),
        Icon(Icons.wifi, size: 16, color: ink),
        const SizedBox(width: 6),
        Icon(Icons.battery_full, size: 16, color: ink),
        const SizedBox(width: 2),
        Text('100%', style: TextStyle(color: ink, fontSize: 13)),
      ],
    ),
  );
}

class _Dock extends StatelessWidget {
  const _Dock({required this.ink, required this.light});
  final Color ink;
  final bool light;

  @override
  Widget build(BuildContext context) {
    final tile = light ? const Color(0xE6FFFFFF) : const Color(0xE61B1C20);
    Widget app(Widget glyph) => Container(
      width: 58,
      height: 58,
      decoration: BoxDecoration(
        color: tile,
        borderRadius: BorderRadius.circular(18),
      ),
      alignment: Alignment.center,
      child: glyph,
    );
    return Row(
      textDirection: TextDirection.ltr,
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        app(Icon(AppIconography.phone, color: ink, size: 26)),
        app(Icon(AppIconography.chat, color: ink, size: 26)),
        app(const AppBrandMark(size: 28)),
        app(Icon(AppIconography.camera, color: ink, size: 26)),
      ],
    );
  }
}
