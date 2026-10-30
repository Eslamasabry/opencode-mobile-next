// KitLiveBackground: the calm drifting colour on every page's ground.
// It must stop moving when the person turned it off, asked for less motion,
// or the app is not in front; it brightens while a reply runs; and it never
// costs text its contrast.
import 'dart:typed_data';
import 'dart:ui' show PictureRecorder;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'kit_motion_still.dart';

Widget _app({
  KitEffects effects = const KitEffects(liveBackground: true),
  bool reduce = false,
  Brightness brightness = Brightness.dark,
}) => MaterialApp(
  theme: brightness == Brightness.dark ? AppTheme.dark() : AppTheme.light(),
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduce),
    child: KitEffectsScope(
      effects: effects,
      child: const KitLiveBackground(child: SizedBox.expand()),
    ),
  ),
);

bool _ticking(WidgetTester tester) =>
    (tester.state(find.byType(KitLiveBackground)) as dynamic).ticking as bool;

void main() {
  // The halo is measured on real letters.
  setUpAll(loadKitGalleryFonts);
  kitMotionStillTests(
    'KitLiveBackground',
    builds: {
      'default': () => const KitLiveBackground(child: Text('Page')),
      'reply running': () => KitLiveBackground(
        replyRunning: ValueNotifier(true),
        child: const Text('Page'),
      ),
    },
    changes: {
      'the look changes': KitMotionChange(
        build: () => KitLiveBackground(
          style: kitLiveStyleById('aurora'),
          child: const Text('Page'),
        ),
        act: (tester, stage) => stage.rebuild(
          KitLiveBackground(
            style: kitLiveStyleById('mesh'),
            child: const Text('Page'),
          ),
        ),
        shows: 'Page',
      ),
    },
  );

  setUp(() => KitMotion.loops = true);
  tearDown(() {
    KitMotion.loops = false;
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.running.value = 0;
  });

  testWidgets('the page is its plain colour by default', (tester) async {
    await tester.pumpWidget(_app(effects: KitEffects.defaults));
    expect(KitEffects.defaults.liveBackground, isFalse);
    expect(_ticking(tester), isFalse);
    expect(
      find.descendant(
        of: find.byType(KitLiveBackground),
        matching: find.byType(CustomPaint),
      ),
      findsNothing,
    );
  });

  testWidgets('moves once a look is on', (tester) async {
    await tester.pumpWidget(_app());
    expect(_ticking(tester), isTrue);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('the page above keeps its state when the look finishes loading', (
    tester,
  ) async {
    var inits = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        home: KitEffectsScope(
          effects: const KitEffects(liveBackground: true),
          child: KitLiveBackground(child: _Probe(onInit: () => inits++)),
        ),
      ),
    );
    expect(inits, 1);
    // The look's warm-up finishes later; the page is not rebuilt from scratch.
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(
      find.descendant(
        of: find.byType(KitLiveBackground),
        matching: find.byType(CustomPaint),
      ),
      findsWidgets,
      reason: 'the look is painted by now',
    );
    expect(inits, 1);
  });

  testWidgets('Live background off: no ticker, no fields', (tester) async {
    await tester.pumpWidget(
      _app(effects: const KitEffects(liveBackground: false)),
    );
    expect(_ticking(tester), isFalse);
    expect(
      find.descendant(
        of: find.byType(KitLiveBackground),
        matching: find.byType(CustomPaint),
      ),
      findsNothing,
    );
  });

  testWidgets('reduced motion: no ticker', (tester) async {
    await tester.pumpWidget(_app(reduce: true));
    expect(_ticking(tester), isFalse);
  });

  testWidgets('Animations: Calm: no ticker', (tester) async {
    await tester.pumpWidget(
      _app(
        effects: const KitEffects(
          liveBackground: true,
          motion: KitMotionLevel.calm,
        ),
      ),
    );
    expect(_ticking(tester), isFalse);
  });

  testWidgets('app paused stops the ticker, resumed starts it', (tester) async {
    await tester.pumpWidget(_app());
    expect(_ticking(tester), isTrue);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    expect(_ticking(tester), isFalse);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(_ticking(tester), isTrue);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('under flutter test (loops off) only the flat ground paints', (
    tester,
  ) async {
    KitMotion.loops = false;
    await tester.pumpWidget(_app());
    expect(_ticking(tester), isFalse);
    expect(
      find.descendant(
        of: find.byType(KitLiveBackground),
        matching: find.byType(CustomPaint),
      ),
      findsNothing,
    );
  });

  testWidgets('a running reply brightens the fields and settling dims them', (
    tester,
  ) async {
    await tester.pumpWidget(_app());
    final lift = _liftOf(tester);
    expect(lift.value, 0);
    KitLiveBackground.running.value = 1;
    await tester.pump();
    await tester.pump(KitLiveBackground.brighten);
    expect(lift.value, 1);
    KitLiveBackground.running.value = 0;
    await tester.pump();
    await tester.pump(KitLiveBackground.brighten);
    expect(lift.value, 0);
    await tester.pumpWidget(const SizedBox());
  });

  test('the default look is aurora, an unknown id falls back to it', () {
    expect(kitLiveDefaultStyleId, 'aurora');
    expect(kitLiveStyleById(null).id, 'aurora');
    expect(kitLiveStyleById('nope').id, 'aurora');
    expect(kitLiveStyles.map((s) => s.id).toSet().length, kitLiveStyles.length);
  });

  testWidgets('every look keeps text readable at its strongest', (
    tester,
  ) async {
    final fails = <String>[];
    for (final b in Brightness.values) {
      final theme = b == Brightness.dark ? AppTheme.dark() : AppTheme.light();
      final tokens = KitTokens.fallback(theme);
      final roles = tokens.roles;
      final palette = KitLiveBackground.paletteOf(tokens);
      for (final style in kitLiveStyles) {
        await tester.runAsync(style.warmUp);
        final memory = KitLiveMemory();
        // A look that asks for a halo has text2 and text3 measured through
        // it, over its brightest pixel (below); text1 is read bare.
        final halo = style.wantsTextHalo(palette);
        var brightest = roles.ground;
        // The lightest and darkest pixel over a few moments of the loop,
        // idle and with a reply running.
        for (final running in [0.0, 1.0]) {
          for (final t in [1.0, 9.0, 21.0, 40.0]) {
            final recorder = PictureRecorder();
            final canvas = Canvas(recorder);
            const size = Size(120, 260);
            canvas.drawRect(Offset.zero & size, Paint()..color = roles.ground);
            style.paint(
              canvas,
              size,
              KitLiveFrame(
                t: t,
                running: running,
                strength: style.ceilingFactor,
                palette: palette,
                memory: memory,
              ),
            );
            final pic = recorder.endRecording();
            final bytes = await tester.runAsync(() async {
              final img = await pic.toImage(120, 260);
              final data = await img.toByteData();
              img.dispose();
              return data!;
            });
            if (halo) brightest = _brightest(bytes!, brightest);
            for (final text in [
              roles.text1,
              if (!halo) roles.text2,
              if (!halo) roles.text3,
            ]) {
              final before = contrastRatio(text, roles.ground);
              var worst = 100.0;
              // Text is read against the look averaged over a few pixels:
              // one thin dot or line under a letter does not change how the
              // letter reads, so the grid is checked in 4x4 blocks.
              for (var by = 0; by < 260; by += 4) {
                for (var bx = 0; bx < 120; bx += 4) {
                  var r = 0, g = 0, bl = 0;
                  for (var y = by; y < by + 4; y++) {
                    for (var x = bx; x < bx + 4; x++) {
                      final i = (y * 120 + x) * 4;
                      r += bytes!.getUint8(i);
                      g += bytes.getUint8(i + 1);
                      bl += bytes.getUint8(i + 2);
                    }
                  }
                  final px = Color.fromARGB(255, r ~/ 16, g ~/ 16, bl ~/ 16);
                  final c = contrastRatio(text, px);
                  if (c < worst) worst = c;
                }
              }
              if (worst < (before >= 4.5 ? 4.5 : before - .2)) {
                fails.add(
                  '${style.id} ${b.name} t=$t run=$running text '
                  '${before.toStringAsFixed(2)} -> ${worst.toStringAsFixed(2)}',
                );
              }
            }
          }
        }
        if (halo) {
          for (final text in [roles.text2, roles.text3]) {
            final before = contrastRatio(text, roles.ground);
            final worst = await _haloWorst(tester, theme, brightest, text);
            if (worst < (before >= 4.5 ? 4.5 : before - .2)) {
              fails.add(
                '${style.id} ${b.name} halo over $brightest '
                '${before.toStringAsFixed(2)} -> ${worst.toStringAsFixed(2)}',
              );
            }
          }
        }
      }
    }
    expect(fails, isEmpty);
  });

  // The aurora is measured against each theme's own text colours (it is
  // the default look, behind every page), so it is held to the same floor
  // in every theme pack and at every Intensity, not only the default theme:
  // text1 on the look itself, text2 and text3 through their halo over the
  // brightest pixel where the look asks for one (dark themes).
  testWidgets(
    'the aurora keeps text readable in every theme, every Intensity',
    (tester) async {
      final style = kitLiveStyleById('aurora');
      final fails = <String>[];
      for (final id in ThemePackId.values) {
        if (id == ThemePackId.dynamic) continue;
        for (final b in Brightness.values) {
          final pack = themePack(id);
          final theme = b == Brightness.dark
              ? AppTheme.dark(pack)
              : AppTheme.light(pack);
          final tokens = KitTokens.fallback(theme);
          final roles = tokens.roles;
          final palette = KitLiveBackground.paletteOf(tokens);
          final halo = style.wantsTextHalo(palette);
          for (final intensity in KitLiveIntensity.values) {
            final memory = KitLiveMemory();
            var brightest = roles.ground;
            for (final running in [0.0, 1.0]) {
              for (final t in [1.0, 9.0, 21.0, 40.0]) {
                final bytes = await _paintLook(
                  tester,
                  style,
                  roles.ground,
                  KitLiveFrame(
                    t: t,
                    running: running,
                    strength: intensity.strength,
                    palette: palette,
                    memory: memory,
                  ),
                );
                if (halo) brightest = _brightest(bytes, brightest);
                for (final text in [
                  roles.text1,
                  if (!halo) roles.text2,
                  if (!halo) roles.text3,
                ]) {
                  final before = contrastRatio(text, roles.ground);
                  final worst = _worstContrast(bytes, text);
                  if (worst < (before >= 4.5 ? 4.5 : before - .2)) {
                    fails.add(
                      '${id.name} ${b.name} ${intensity.name} t=$t '
                      'run=$running ${before.toStringAsFixed(2)} -> '
                      '${worst.toStringAsFixed(2)}',
                    );
                  }
                }
              }
            }
            if (halo) {
              for (final text in [roles.text2, roles.text3]) {
                final before = contrastRatio(text, roles.ground);
                final worst = await _haloWorst(tester, theme, brightest, text);
                if (worst < (before >= 4.5 ? 4.5 : before - .2)) {
                  fails.add(
                    '${id.name} ${b.name} ${intensity.name} halo '
                    '${before.toStringAsFixed(2)} -> '
                    '${worst.toStringAsFixed(2)}',
                  );
                }
              }
            }
          }
        }
      }
      expect(fails, isEmpty);
    },
  );
}

const _w = 120, _h = 260;

/// One frame of [style] over [ground], as RGBA bytes.
Future<ByteData> _paintLook(
  WidgetTester tester,
  KitLiveStyle style,
  Color ground,
  KitLiveFrame frame,
) async {
  final recorder = PictureRecorder();
  final canvas = Canvas(recorder);
  final size = Size(_w.toDouble(), _h.toDouble());
  canvas.drawRect(Offset.zero & size, Paint()..color = ground);
  style.paint(canvas, size, frame);
  final pic = recorder.endRecording();
  return (await tester.runAsync(() async {
    final img = await pic.toImage(_w, _h);
    final data = await img.toByteData();
    img.dispose();
    return data!;
  }))!;
}

/// The lowest contrast of [text] against the look, read in 4x4 blocks as
/// the test above does.
double _worstContrast(ByteData bytes, Color text) {
  var worst = 100.0;
  for (var by = 0; by < _h; by += 4) {
    for (var bx = 0; bx < _w; bx += 4) {
      var r = 0, g = 0, bl = 0;
      for (var y = by; y < by + 4; y++) {
        for (var x = bx; x < bx + 4; x++) {
          final i = (y * _w + x) * 4;
          r += bytes.getUint8(i);
          g += bytes.getUint8(i + 1);
          bl += bytes.getUint8(i + 2);
        }
      }
      final c = contrastRatio(
        text,
        Color.fromARGB(255, r ~/ 16, g ~/ 16, bl ~/ 16),
      );
      if (c < worst) worst = c;
    }
  }
  return worst;
}

/// The brighter (in luminance) of [so] and the brightest spot in [bytes]:
/// 2x2 pixels averaged, so a lone pixel where two triangles of a mesh meet
/// does not stand in for the look.
Color _brightest(ByteData bytes, Color so) {
  var best = so;
  var lum = so.computeLuminance();
  int at(int x, int y, int c) => bytes.getUint8((y * _w + x) * 4 + c);
  for (var y = 0; y < _h - 1; y++) {
    for (var x = 0; x < _w - 1; x++) {
      int mean(int c) =>
          (at(x, y, c) +
              at(x + 1, y, c) +
              at(x, y + 1, c) +
              at(x + 1, y + 1, c)) ~/
          4;
      final c = Color.fromARGB(255, mean(0), mean(1), mean(2));
      final l = c.computeLuminance();
      if (l > lum) {
        lum = l;
        best = c;
      }
    }
  }
  return best;
}

/// The lowest contrast of [text] in the kit's small roles (caption and
/// secondary), drawn with the halo ([KitTextHalo]) on a flat [fill], read
/// against what sits right around its letters: the pixels within one
/// logical pixel of a stroke, averaged per letter-sized cell (8x8 logical
/// pixels; only cells that hold a real piece of a letter's edge). Drawn at
/// 2x, as a phone does.
Future<double> _haloWorst(
  WidgetTester tester,
  ThemeData theme,
  Color fill,
  Color text,
) async {
  final ground = KitTokens.fallback(theme).roles.ground;
  const w = 608, h = 144;
  Future<ByteData> draw(List<Shadow>? shadows) async {
    final recorder = PictureRecorder();
    final canvas = Canvas(recorder)..scale(2);
    canvas.drawRect(Rect.fromLTWH(0, 0, w / 2, h / 2), Paint()..color = fill);
    var y = 6.0;
    for (final role in [KitTextRole.caption, KitTextRole.secondary]) {
      final painter = TextPainter(
        text: TextSpan(
          text: 'Today · shopfront 2h ago · Yesterday 36',
          style: KitText.styleFor(role).copyWith(
            fontFamily: theme.textTheme.bodyLarge?.fontFamily,
            color: text,
            shadows: shadows,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: w / 2 - 12);
      painter.paint(canvas, Offset(6, y));
      y += painter.height + 8;
      painter.dispose();
    }
    final pic = recorder.endRecording();
    return (await tester.runAsync(() async {
      final img = await pic.toImage(w, h);
      final data = await img.toByteData();
      img.dispose();
      return data!;
    }))!;
  }

  final bare = await draw(null);
  final haloed = await draw(KitTextHalo.shadowsFor(ground));
  final fr = (fill.r * 255).round(), fg = (fill.g * 255).round();
  final fb = (fill.b * 255).round();
  final glyph = List<bool>.generate(w * h, (i) {
    final d =
        (bare.getUint8(i * 4) - fr).abs() +
        (bare.getUint8(i * 4 + 1) - fg).abs() +
        (bare.getUint8(i * 4 + 2) - fb).abs();
    return d > 12;
  });
  bool nearGlyph(int x, int y) {
    for (var dy = -2; dy <= 2; dy++) {
      for (var dx = -2; dx <= 2; dx++) {
        final yy = y + dy, xx = x + dx;
        if (yy < 0 || xx < 0 || yy >= h || xx >= w) continue;
        if (glyph[yy * w + xx]) return true;
      }
    }
    return false;
  }

  var worst = 100.0;
  const cell = 16;
  for (var by = 0; by < h; by += cell) {
    for (var bx = 0; bx < w; bx += cell) {
      var r = 0, g = 0, b = 0, n = 0;
      for (var y = by; y < by + cell; y++) {
        for (var x = bx; x < bx + cell; x++) {
          final i = y * w + x;
          if (glyph[i] || !nearGlyph(x, y)) continue;
          r += haloed.getUint8(i * 4);
          g += haloed.getUint8(i * 4 + 1);
          b += haloed.getUint8(i * 4 + 2);
          n++;
        }
      }
      if (n < cell * cell ~/ 4) continue;
      final c = contrastRatio(
        text,
        Color.fromARGB(255, r ~/ n, g ~/ n, b ~/ n),
      );
      if (c < worst) worst = c;
    }
  }
  return worst;
}

_Lift _liftOf(WidgetTester tester) => _Lift(tester);

class _Lift {
  _Lift(this.tester);
  final WidgetTester tester;
  double get value =>
      (tester.state(find.byType(KitLiveBackground)) as dynamic).lift as double;
}

class _Probe extends StatefulWidget {
  const _Probe({required this.onInit});
  final VoidCallback onInit;

  @override
  State<_Probe> createState() => _ProbeState();
}

class _ProbeState extends State<_Probe> {
  @override
  void initState() {
    super.initState();
    widget.onInit();
  }

  @override
  Widget build(BuildContext context) => const SizedBox.expand();
}
