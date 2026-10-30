// Style recheck F5 and F7 (docs/audits/style-coherence-recheck-2026-10-21.md
// §4 N5, N8; R4): the parts that kept Solid's Material path ask
// KitSurfaceLook.isPlain, so Fine-tune reaches them under Solid; the menu's
// divider is the style's line; the scrim behind a sheet follows the
// effective style even under a nested Saver or high-contrast host; a diff's
// gap bar lies inside the one code frame.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

/// The app as main.dart hosts it: the effects and the Style's own theme
/// above the navigator; [inner] adds a second host under it (a capture
/// harness's Saver or high-contrast scope). Returns a context under the
/// navigator.
Future<BuildContext> _pump(
  WidgetTester tester, {
  required KitEffects effects,
  Widget Function(Widget navigator)? inner,
  Widget body = const SizedBox.expand(),
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  late BuildContext context;
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (_, navigator) => KitEffectsScope(
        effects: effects,
        child: kitStyleTheme(child: inner?.call(navigator!) ?? navigator!),
      ),
      home: Scaffold(
        body: Builder(
          builder: (inner) {
            context = inner;
            return body;
          },
        ),
      ),
    ),
  );
  return context;
}

Color _barrier(WidgetTester tester) => tester
    .widgetList<ModalBarrier>(find.byType(ModalBarrier))
    .map((b) => b.color)
    .whereType<Color>()
    .last;

const _solidTuned = KitEffects(
  fineTune: KitFineTune(corners: KitCorners.sharp),
);

void main() {
  group('the scrim behind a sheet (N8)', () {
    final pack = ThemeRoles.resolve(AppTheme.dark()).scrim;

    for (final style in KitSurfaceStyle.values) {
      testWidgets('${style.name} keeps the theme\'s scrim', (tester) async {
        final context = await _pump(
          tester,
          effects: KitEffects(surfaceStyle: style),
        );
        unawaited(
          showKitSheet<void>(
            context,
            title: 'Sheet',
            body: (_) => const Text('x'),
          ),
        );
        await tester.pumpAndSettle();
        expect(_barrier(tester), pack);
      });
    }
  });

  group('Solid with Fine-tune leaves the Material path (R4)', () {
    testWidgets('a sheet: the route turns clear, the style paints it', (
      tester,
    ) async {
      Future<Color?> sheetFill(KitEffects effects) async {
        final context = await _pump(tester, effects: effects);
        unawaited(
          showKitSheet<void>(
            context,
            title: 'Sheet',
            body: (_) => const Text('x'),
          ),
        );
        await tester.pumpAndSettle();
        final fill = tester.widget<BottomSheet>(find.byType(BottomSheet));
        Navigator.of(context).pop();
        await tester.pumpAndSettle();
        return fill.backgroundColor;
      }

      expect(await sheetFill(KitEffects.defaults), isNot(Colors.transparent));
      expect(await sheetFill(_solidTuned), Colors.transparent);
    });

    testWidgets('an alert: the panel Material turns clear', (tester) async {
      final context = await _pump(tester, effects: _solidTuned);
      unawaited(showKitAlert(context, title: 'Alert', body: 'Body'));
      await tester.pumpAndSettle();
      final panel = tester.widget<Material>(
        find
            .ancestor(of: find.text('Alert'), matching: find.byType(Material))
            .last,
      );
      expect(panel.color, Colors.transparent);
    });

    testWidgets('a menu: the plain hairline gives way to the style', (
      tester,
    ) async {
      Future<Decoration> menuEdge(KitEffects effects) async {
        final context = await _pump(tester, effects: effects);
        unawaited(
          showKitMenu(
            context,
            items: [KitMenuItem(label: 'Archive', onSelected: () {})],
          ),
        );
        await tester.pumpAndSettle();
        final box = tester.widget<DecoratedBox>(
          find.byKey(const ValueKey('kit-menu')),
        );
        Navigator.of(context).pop();
        await tester.pumpAndSettle();
        return box.decoration;
      }

      expect(
        (await menuEdge(KitEffects.defaults) as BoxDecoration).border,
        isNotNull,
      );
      expect((await menuEdge(_solidTuned) as BoxDecoration).border, isNull);
    });
  });

  group('Solid with Fine-tune: the floating parts (R4)', () {
    testWidgets('a floating panel leaves the plain Material', (tester) async {
      final surface2 = ThemeRoles.resolve(AppTheme.dark()).surface2;
      Future<int> plain(KitEffects effects) async {
        await _pump(
          tester,
          effects: effects,
          body: const KitFloatingPanel(onDismiss: null, child: Text('panel')),
        );
        await tester.pumpAndSettle();
        return tester
            .widgetList<Material>(
              find.ancestor(
                of: find.text('panel'),
                matching: find.byType(Material),
              ),
            )
            .where((m) => m.color == surface2)
            .length;
      }

      expect(await plain(KitEffects.defaults), 1);
      expect(await plain(_solidTuned), 0);
    });

    testWidgets('a jump pill drops the plain hairline', (tester) async {
      Future<BorderSide> edge(KitEffects effects) async {
        await _pump(
          tester,
          effects: effects,
          body: Stack(
            children: [
              KitJumpPill(label: 'Latest', onPressed: () {}, visible: true),
            ],
          ),
        );
        await tester.pumpAndSettle();
        final box = tester
            .widgetList<DecoratedBox>(
              find.ancestor(
                of: find.text('Latest'),
                matching: find.byType(DecoratedBox),
              ),
            )
            .firstWhere(
              (b) =>
                  b.position == DecorationPosition.foreground &&
                  b.decoration is ShapeDecoration,
            );
        return ((box.decoration as ShapeDecoration).shape as OutlinedBorder)
            .side;
      }

      expect((await edge(KitEffects.defaults)).style, BorderStyle.solid);
      expect(await edge(_solidTuned), BorderSide.none);
    });

    testWidgets('a seen fact drops the plain hairline', (tester) async {
      final hairline = ThemeRoles.resolve(AppTheme.dark()).hairline;
      Future<void> fact(KitEffects effects) async {
        await _pump(
          tester,
          effects: effects,
          body: KitFactRow(
            text: 'Prefers tabs',
            provenance: KitProvenance.seen,
            observedAt: DateTime.utc(2026, 10, 9),
            source: 'a shared message',
          ),
        );
        await tester.pumpAndSettle();
      }

      // The provenance border: the one stroked rounded rectangle.
      Future<Color> border(KitEffects effects) async {
        await fact(effects);
        final strokes = <Color>[];
        expect(
          find.byType(KitFactRow),
          paints..everything((method, args) {
            if (method == #drawRRect &&
                (args[1] as Paint).style == PaintingStyle.stroke) {
              strokes.add((args[1] as Paint).color);
            }
            return true;
          }),
        );
        return strokes.single;
      }

      expect(
        (await border(KitEffects.defaults)).toARGB32(),
        hairline.toARGB32(),
      );
      expect((await border(_solidTuned)).a, 0);
    });
  });

  testWidgets('the menu divider is the style\'s line (F7)', (tester) async {
    Future<Color> line(KitSurfaceStyle style) async {
      final context = await _pump(
        tester,
        effects: KitEffects(surfaceStyle: style),
      );
      unawaited(
        showKitMenu(
          context,
          items: [
            KitMenuItem(label: 'Archive', onSelected: () {}, group: 'a'),
            KitMenuItem(label: 'Rename', onSelected: () {}, group: 'b'),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final fill = tester.widget<ColoredBox>(
        find.descendant(
          of: find.byKey(const ValueKey('kit-menu-divider')),
          matching: find.byType(ColoredBox),
        ),
      );
      Navigator.of(context).pop();
      await tester.pumpAndSettle();
      return fill.color;
    }

    final hairline = ThemeRoles.resolve(AppTheme.dark()).hairline;
    expect(await line(KitSurfaceStyle.solid), hairline);
    // Soft parts groups by space alone.
    expect((await line(KitSurfaceStyle.soft)).a, 0);
  });

  group('the gap bar lies in the one code frame (F5, N5)', () {
    KitDiffFile sixty() {
      final before = [for (var i = 1; i <= 60; i++) 'line $i'];
      final after = [...before]..[30] = 'changed line 31';
      return KitDiffFile.fromTexts(
        'notes.txt',
        before: before.join('\n'),
        after: after.join('\n'),
      );
    }

    Future<Rect> gapBar(WidgetTester tester, KitSurfaceStyle style) async {
      await _pump(
        tester,
        effects: KitEffects(surfaceStyle: style),
        body: KitDiffView(files: [sixty()]),
      );
      await tester.pumpAndSettle();
      return tester.getRect(find.byKey(const ValueKey('kit-diff-gap-0')));
    }

    testWidgets('Solid: the bar runs the frame\'s full width, as before', (
      tester,
    ) async {
      final bar = await gapBar(tester, KitSurfaceStyle.solid);
      expect(bar.left, 0);
      expect(bar.right, 412);
    });

    for (final style in [KitSurfaceStyle.brutal, KitSurfaceStyle.paper]) {
      testWidgets('${style.name}: the bar sits inside the frame\'s edge', (
        tester,
      ) async {
        final bar = await gapBar(tester, style);
        expect(bar.left, greaterThan(0));
        expect(bar.right, lessThan(412));
        final words = tester.getRect(find.text('… 27 lines above'));
        expect(words.top, greaterThan(bar.top));
      });
    }
  });
}
