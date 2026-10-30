// KitSurfaceLook: the one resolver behind Settings › Appearance › Style.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

double _contrast(Color a, Color b) {
  final la = a.computeLuminance();
  final lb = b.computeLuminance();
  final hi = la > lb ? la : lb;
  final lo = la > lb ? lb : la;
  return (hi + .05) / (lo + .05);
}

void main() {
  const solid = BoxDecoration(
    color: Color(0xFF141518),
    borderRadius: BorderRadius.all(Radius.circular(18)),
  );

  KitSurfaceDecoration resolve(
    ThemeRoles roles,
    KitSurfaceStyle style, {
    KitSurfaceRole role = KitSurfaceRole.content,
    bool selected = false,
    bool pressed = false,
    Color? color,
  }) =>
      KitSurfaceLook.resolve(
            roles: roles,
            style: style,
            role: role,
            solid: solid,
            selected: selected,
            pressed: pressed,
            color: color,
          )
          as KitSurfaceDecoration;

  group('solid', () {
    test('is the part\'s own decoration, untouched', () {
      for (final role in KitSurfaceRole.values) {
        expect(
          identical(
            KitSurfaceLook.resolve(
              roles: graphiteDark,
              style: KitSurfaceStyle.solid,
              role: role,
              solid: solid,
              selected: true,
            ),
            solid,
          ),
          isTrue,
        );
      }
    });

    testWidgets('high contrast makes every style solid', (tester) async {
      late Decoration seen;
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(highContrast: true),
          child: KitEffectsScope(
            effects: const KitEffects(surfaceStyle: KitSurfaceStyle.soft),
            child: Builder(
              builder: (context) {
                seen = KitSurfaceLook.decorate(context, solid: solid);
                return const SizedBox();
              },
            ),
          ),
        ),
      );
      expect(identical(seen, solid), isTrue);
    });

    testWidgets('reads the effects in force', (tester) async {
      late KitSurfaceStyle style;
      await tester.pumpWidget(
        KitEffectsScope(
          effects: const KitEffects(surfaceStyle: KitSurfaceStyle.soft),
          child: Builder(
            builder: (context) {
              style = KitSurfaceLook.styleOf(context);
              return const SizedBox();
            },
          ),
        ),
      );
      expect(style, KitSurfaceStyle.soft);
    });
  });

  group('soft', () {
    test('the ground\'s colour, raised at rest', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final d = resolve(roles, KitSurfaceStyle.soft);
        expect(d.inset, 0);
        expect(d.distance, greaterThan(0));
        expect(d.lightShadow.a, greaterThan(0));
        expect(d.darkShadow.a, greaterThan(0));
        // The face is the ground, at most lifted a few percent.
        expect(
          (d.fill.computeLuminance() - roles.ground.computeLuminance()).abs(),
          lessThan(.02),
        );
      }
    });

    test('selected and pressed sit pressed in; a track is a well', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        expect(resolve(roles, KitSurfaceStyle.soft, selected: true).inset, 1);
        expect(resolve(roles, KitSurfaceStyle.soft, pressed: true).inset, 1);
        expect(
          resolve(
            roles,
            KitSurfaceStyle.soft,
            role: KitSurfaceRole.track,
          ).inset,
          1,
        );
      }
    });

    test('dark depth reads: a lit rim and a lifted top start', () {
      final d = resolve(graphiteDark, KitSurfaceStyle.soft);
      expect(d.edgeStart.a, greaterThan(0));
      expect(
        d.fillStart.computeLuminance(),
        greaterThan(graphiteDark.ground.computeLuminance()),
      );
      expect(d.darkShadow.a, greaterThanOrEqualTo(.7));
    });

    for (final roles in [graphiteDark, graphiteLight]) {
      test('words keep AA in ${roles.brightness.name}', () {
        for (final selected in [false, true]) {
          final d = resolve(roles, KitSurfaceStyle.soft, selected: selected);
          for (final face in [d.fill, d.fillStart]) {
            for (final text in [roles.text1, roles.text2, roles.text3]) {
              expect(_contrast(text, face), greaterThanOrEqualTo(4.5));
            }
          }
        }
      });
    }
  });

  group('KitSurfaceDecoration', () {
    test('lerps with a plain decoration instead of snapping', () {
      final soft = resolve(graphiteDark, KitSurfaceStyle.soft);
      final half = Decoration.lerp(solid, soft, .5);
      expect(half, isA<KitSurfaceDecoration>());
      final mid = half! as KitSurfaceDecoration;
      expect(mid.fill, Color.lerp(solid.color, soft.fill, .5));
      expect(Decoration.lerp(solid, soft, 1), soft);
      final back = Decoration.lerp(soft, solid, .5)! as KitSurfaceDecoration;
      expect(back.fill, mid.fill);
    });

    test('reads a ShapeDecoration\'s shape and edge', () {
      final from = KitSurfaceDecoration.from(
        const ShapeDecoration(
          color: Color(0xFF26282D),
          shape: StadiumBorder(side: BorderSide(color: Color(0x12FFFFFF))),
        ),
      )!;
      expect(from.fill, const Color(0xFF26282D));
      expect(from.edgeStart, const Color(0x12FFFFFF));
      expect(from.borderRadius, BorderRadius.circular(9999));
    });
  });

  group('kitSurfaceBox', () {
    Widget host(KitSurfaceStyle style, {bool reduce = false}) => MediaQuery(
      data: MediaQueryData(disableAnimations: reduce),
      child: KitEffectsScope(
        effects: KitEffects(surfaceStyle: style),
        child: Theme(
          data: ThemeData(brightness: Brightness.dark),
          child: Builder(
            builder: (context) => Center(
              child: kitSurfaceBox(
                key: const ValueKey('box'),
                style: KitSurfaceLook.styleOf(context),
                decoration: KitSurfaceLook.decorate(context, solid: solid),
                child: const SizedBox(width: 40, height: 40),
              ),
            ),
          ),
        ),
      ),
    );

    Decoration painted(WidgetTester tester) => tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byKey(const ValueKey('box')),
            matching: find.byType(DecoratedBox),
          ),
        )
        .last
        .decoration;

    testWidgets('paints the solid decoration itself at rest', (tester) async {
      await tester.pumpWidget(host(KitSurfaceStyle.solid));
      expect(identical(painted(tester), solid), isTrue);
    });

    testWidgets('a change of style cross-fades over 200 ms', (tester) async {
      await tester.pumpWidget(host(KitSurfaceStyle.solid));
      await tester.pumpWidget(host(KitSurfaceStyle.soft));
      await tester.pump(const Duration(milliseconds: 100));
      final mid = painted(tester) as KitSurfaceDecoration;
      await tester.pump(const Duration(milliseconds: 120));
      expect(painted(tester), isA<KitSurfaceDecoration>());
      final end = painted(tester) as KitSurfaceDecoration;
      // The soft shadow fades in rather than snapping.
      expect(end.darkShadow.a, greaterThan(0));
      expect(mid.darkShadow.a, inExclusiveRange(0, end.darkShadow.a));
    });

    testWidgets('reduced motion changes at once', (tester) async {
      await tester.pumpWidget(host(KitSurfaceStyle.solid, reduce: true));
      await tester.pumpWidget(host(KitSurfaceStyle.soft, reduce: true));
      final d = painted(tester) as KitSurfaceDecoration;
      expect(d.distance, greaterThan(0));
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  // The live background after Paper (style recheck, fix group E): Paper
  // turns it off when chosen; leaving Paper gives it back, unless the
  // person had turned it off themselves.
  group('choose: the live background', () {
    KitEffects pick(KitEffects e, KitSurfaceStyle s) =>
        KitSurfaceLook.choose(e, s);

    test('Soft with live on, then Paper (off), then Soft (on again)', () {
      var e = const KitEffects(
        surfaceStyle: KitSurfaceStyle.soft,
        liveBackground: true,
      );
      e = pick(e, KitSurfaceStyle.paper);
      expect(e.liveBackground, isFalse);
      e = pick(e, KitSurfaceStyle.soft);
      expect(e.liveBackground, isTrue);
      expect(e.surfaceStyle, KitSurfaceStyle.soft);
      expect(e.liveHeldByStyle, isFalse);
    });

    test('live off (the default), then Paper, then Soft (still off)', () {
      var e = const KitEffects(surfaceStyle: KitSurfaceStyle.soft);
      expect(e.liveBackground, isFalse);
      e = pick(e, KitSurfaceStyle.paper);
      expect(e.liveBackground, isFalse);
      e = pick(e, KitSurfaceStyle.soft);
      expect(e.liveBackground, isFalse);
    });

    test('turned off by hand while on Paper stays off when leaving', () {
      var e = pick(
        const KitEffects(
          surfaceStyle: KitSurfaceStyle.soft,
          liveBackground: true,
        ),
        KitSurfaceStyle.paper,
      );
      // The person turns it on under Paper, then off again.
      e = e.copyWith(liveBackground: true).copyWith(liveBackground: false);
      e = pick(e, KitSurfaceStyle.soft);
      expect(e.liveBackground, isFalse);
    });

    test('on by hand under Paper stays on when leaving', () {
      var e = pick(const KitEffects(), KitSurfaceStyle.paper);
      e = e.copyWith(liveBackground: true);
      e = pick(e, KitSurfaceStyle.brutal);
      expect(e.liveBackground, isTrue);
    });

    test('choosing Paper again keeps the hold', () {
      var e = pick(
        const KitEffects(liveBackground: true),
        KitSurfaceStyle.paper,
      );
      e = pick(e, KitSurfaceStyle.paper);
      expect(e.liveBackground, isFalse);
      e = pick(e, KitSurfaceStyle.solid);
      expect(e.liveBackground, isTrue);
    });
  });
}
