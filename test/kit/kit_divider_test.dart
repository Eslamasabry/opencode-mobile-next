// KitDivider (docs/ux-system/kit-api/KitDivider.md): the one separator, a
// hairline exactly one physical pixel thick, snapped to the pixel grid so
// it is never smeared over two device-pixel rows, decorative (no semantics
// node), stateless, and inset to where a row's words start.
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_divider.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

/// Pumps [child] centred under a themed app, with the view's device pixel
/// ratio and text scale controlled directly (TEST-9, TEST-6 of this spec).
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double dpr = 3.0,
  bool light = true,
  double textScale = 1,
}) async {
  tester.view.devicePixelRatio = dpr;
  addTearDown(tester.view.reset);
  // A fresh tree every call: a previous pump's theme (light vs dark) would
  // otherwise cross-fade under MaterialApp's implicit AnimatedTheme, and
  // one pump() would land mid-transition instead of on the new theme.
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: light ? AppTheme.light() : AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: Scaffold(body: Center(child: child)),
    ),
  );
}

/// The hairline's own gap from the start edge of a [width]-wide container:
/// the left gap under LTR, the right gap under RTL (KitDivider.md: "under
/// RTL they are measured from the right").
Future<double> _startGap(
  WidgetTester tester, {
  required KitDividerInset inset,
  required TextDirection direction,
  double width = 200,
}) async {
  final box = GlobalKey();
  await _pump(
    tester,
    Directionality(
      textDirection: direction,
      child: SizedBox(
        key: box,
        width: width,
        child: KitDivider(inset: inset),
      ),
    ),
  );
  final container = tester.getRect(find.byKey(box));
  final hairline = tester.getRect(
    find.byKey(const ValueKey('kit-divider-line')),
  );
  return direction == TextDirection.ltr
      ? hairline.left - container.left
      : container.right - hairline.right;
}

/// The whole semantics tree as a screen reader gets it (every node's label,
/// flags, actions and rect), with the node ids left out, since those differ
/// between two pumps of the same tree.
String _semanticsTree(WidgetTester tester) => tester
    .binding
    .renderViews
    .single
    .owner!
    .semanticsOwner!
    .rootSemanticsNode!
    .toStringDeep(childOrder: DebugSemanticsDumpOrder.traversalOrder)
    .replaceAll(RegExp(r'SemanticsNode#\d+'), 'SemanticsNode');

/// The layout size of a fresh [KitDivider] (or [KitDivider.vertical]) at
/// [dpr], unconstrained on the axis its thickness lives on.
Future<Size> _layoutSize(
  WidgetTester tester, {
  required Axis axis,
  required double dpr,
}) async {
  final key = GlobalKey();
  final divider = axis == Axis.horizontal
      ? KitDivider(key: key)
      : KitDivider.vertical(key: key);
  await _pump(
    tester,
    axis == Axis.horizontal
        ? SizedBox(width: 200, child: divider)
        : SizedBox(height: 200, child: divider),
    dpr: dpr,
  );
  final box = key.currentContext!.findRenderObject()! as RenderBox;
  return box.size;
}

void main() {
  kitMotionStillTests(
    'KitDivider',
    builds: {
      'horizontal': () => const KitDivider(),
      'horizontal gutter inset': () =>
          const KitDivider(inset: KitDividerInset.gutter),
      'vertical': () => const KitDivider.vertical(),
    },
  );

  group('layout extent is exactly one physical pixel', () {
    for (final dpr in [1.0, 2.625, 3.0]) {
      testWidgets('horizontal height at dpr $dpr', (tester) async {
        final size = await _layoutSize(tester, axis: Axis.horizontal, dpr: dpr);
        expect(size.height, closeTo(1 / dpr, 1e-9));
        expect(size.width, 200);
      });

      testWidgets('vertical width at dpr $dpr', (tester) async {
        final size = await _layoutSize(tester, axis: Axis.vertical, dpr: dpr);
        expect(size.width, closeTo(1 / dpr, 1e-9));
        expect(size.height, 200);
      });
    }

    testWidgets('thickness is unaffected by 2.0 text', (tester) async {
      final key = GlobalKey();
      await _pump(
        tester,
        SizedBox(width: 200, child: KitDivider(key: key)),
        dpr: 3,
        textScale: 2,
      );
      final box = key.currentContext!.findRenderObject()! as RenderBox;
      expect(box.size.height, closeTo(1 / 3, 1e-9));
    });

    testWidgets('both axes are exactly KitTokens.hairlineWidth thick', (
      tester,
    ) async {
      final horizontal = GlobalKey();
      final vertical = GlobalKey();
      late double token;
      await _pump(
        tester,
        Builder(
          builder: (context) {
            token = KitTokens.hairlineWidth(context);
            return Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(width: 200, child: KitDivider(key: horizontal)),
                SizedBox(height: 40, child: KitDivider.vertical(key: vertical)),
              ],
            );
          },
        ),
        dpr: 2.625,
      );
      Size sizeOf(GlobalKey key) =>
          (key.currentContext!.findRenderObject()! as RenderBox).size;
      expect(sizeOf(horizontal).height, token);
      expect(sizeOf(vertical).width, token);
    });
  });

  testWidgets(
    'a fractional offset snaps to exactly one device-pixel row (DPR 2.625)',
    (tester) async {
      const dpr = 2.625;
      final boundary = GlobalKey();
      await _pump(
        tester,
        RepaintBoundary(
          key: boundary,
          child: ColoredBox(
            color: Colors.white,
            child: SizedBox(
              width: 40,
              height: 40,
              // The divider's top sits at device row 27.5: unsnapped, its
              // one-device-pixel-tall rect would straddle rows 27 and 28
              // half and half, so this is the fractional offset most
              // likely to catch a paint that is not pixel-grid snapped
              // (KitDivider.md's own example, y = 10.3, is too close to a
              // whole row to tell the two implementations apart).
              child: Column(
                children: const [
                  SizedBox(height: 27.5 / dpr), // 10.476190…
                  KitDivider(),
                ],
              ),
            ),
          ),
        ),
        dpr: dpr,
      );
      final hairline = ThemeRoles.resolve(AppTheme.light()).hairline;
      final expected = Color.alphaBlend(hairline, Colors.white);

      final render = tester.renderObject<RenderRepaintBoundary>(
        find.byKey(boundary),
      );
      final image = (await tester.runAsync(
        () => render.toImage(pixelRatio: dpr),
      ))!;
      final bytes = (await tester.runAsync(
        () => image.toByteData(format: ui.ImageByteFormat.rawRgba),
      ))!;
      final x = image.width ~/ 2;
      const tol = 3;
      bool isHairlineRow(int y) {
        final i = (y * image.width + x) * 4;
        return (bytes.getUint8(i) - expected.r * 255).abs() <= tol &&
            (bytes.getUint8(i + 1) - expected.g * 255).abs() <= tol &&
            (bytes.getUint8(i + 2) - expected.b * 255).abs() <= tol;
      }

      // 27.5 device pixels rounds up (Dart's round-half-away-from-zero) to
      // device row 28.
      const snappedRow = 28;
      expect(
        isHairlineRow(snappedRow),
        isTrue,
        reason: 'device row $snappedRow should be the one painted row',
      );
      expect(
        isHairlineRow(snappedRow - 1),
        isFalse,
        reason:
            'device row ${snappedRow - 1} must stay background, not half-painted',
      );
      expect(
        isHairlineRow(snappedRow + 1),
        isFalse,
        reason:
            'device row ${snappedRow + 1} must stay background, not half-painted',
      );
      image.dispose();
    },
  );

  testWidgets('insets: none 0, gutter 16, text 56 from the start edge', (
    tester,
  ) async {
    for (final direction in TextDirection.values) {
      expect(
        await _startGap(
          tester,
          inset: KitDividerInset.none,
          direction: direction,
        ),
        closeTo(0, 0.001),
        reason: '$direction none',
      );
      expect(
        await _startGap(
          tester,
          inset: KitDividerInset.gutter,
          direction: direction,
        ),
        closeTo(16, 0.001),
        reason: '$direction gutter',
      );
      expect(
        await _startGap(
          tester,
          inset: KitDividerInset.text,
          direction: direction,
        ),
        closeTo(56, 0.001),
        reason: '$direction text',
      );
    }
  });

  testWidgets('colour is exactly ThemeRoles.hairline, alpha unchanged', (
    tester,
  ) async {
    const dpr = 3.0;
    for (final light in [true, false]) {
      final boundary = GlobalKey();
      final background = light ? Colors.white : Colors.black;
      await _pump(
        tester,
        RepaintBoundary(
          key: boundary,
          child: ColoredBox(
            color: background,
            child: const SizedBox(width: 20, child: KitDivider()),
          ),
        ),
        dpr: dpr,
        light: light,
      );
      final hairline = ThemeRoles.resolve(
        light ? AppTheme.light() : AppTheme.dark(),
      ).hairline;
      final expected = Color.alphaBlend(hairline, background);

      final render = tester.renderObject<RenderRepaintBoundary>(
        find.byKey(boundary),
      );
      final image = (await tester.runAsync(
        () => render.toImage(pixelRatio: dpr),
      ))!;
      final bytes = (await tester.runAsync(
        () => image.toByteData(format: ui.ImageByteFormat.rawRgba),
      ))!;
      final x = image.width ~/ 2;
      int channel(int c) => bytes.getUint8(x * 4 + c);
      expect(
        channel(0),
        closeTo(expected.r * 255, 2),
        reason: 'red, light=$light',
      );
      expect(
        channel(1),
        closeTo(expected.g * 255, 2),
        reason: 'green, light=$light',
      );
      expect(
        channel(2),
        closeTo(expected.b * 255, 2),
        reason: 'blue, light=$light',
      );
      image.dispose();
    }
  });

  testWidgets('has no semantics node (decorative, never announced)', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    // Two rows with a separator between them, and the same two rows with an
    // empty box of the same size in the separator's place: a screen reader
    // must meet exactly the same nodes, in the same places, in both.
    Future<String> treeWith(Widget between) async {
      await _pump(
        tester,
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [const Text('Above'), between, const Text('Below')],
        ),
      );
      return _semanticsTree(tester);
    }

    for (final (name, divider) in [
      ('horizontal', const SizedBox(width: 200, child: KitDivider())),
      (
        'text inset',
        const SizedBox(
          width: 200,
          child: KitDivider(inset: KitDividerInset.text),
        ),
      ),
      ('vertical', const SizedBox(height: 40, child: KitDivider.vertical())),
    ]) {
      final withDivider = await treeWith(divider);
      final dividerSize = tester.getSize(find.byType(KitDivider));
      final withoutDivider = await treeWith(
        SizedBox.fromSize(size: dividerSize),
      );
      expect(
        withDivider,
        withoutDivider,
        reason: 'a $name KitDivider must add nothing to the semantics tree',
      );
      // The probe itself reads the tree: both rows are there.
      expect(withDivider, contains('"Above"'));
      expect(withDivider, contains('"Below"'));
    }
    // Disposed here, not via addTearDown: Flutter's end-of-test check for a
    // dangling SemanticsHandle runs before addTearDown callbacks do.
    semantics.dispose();
  });
}
