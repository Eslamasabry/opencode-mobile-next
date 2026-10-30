// KitSurface (docs/ux-system/kit-api/KitSurface.md): the one solid box —
// fill from the surface steps, a token shape, token padding and an optional
// hairline edge — and its retired forwarder KitPanel (KIT-43).
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_panel.dart';
import 'package:opencode_mobile/ui/kit/kit_surface.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

Widget _host(
  Widget child, {
  bool light = false,
  TextDirection direction = TextDirection.ltr,
}) => MaterialApp(
  theme: light ? AppTheme.light() : AppTheme.dark(),
  home: Directionality(
    textDirection: direction,
    child: Scaffold(body: Center(child: child)),
  ),
);

/// The [Material] a kit part of type [T] paints through — never an
/// ancestor's own (the [Scaffold]'s).
Material _materialOf<T extends Widget>(WidgetTester tester) =>
    tester.widget<Material>(
      find
          .descendant(of: find.byType(T), matching: find.byType(Material))
          .first,
    );

void main() {
  kitMotionStillTests(
    'KitSurface',
    builds: {'default': () => const KitSurface(child: KitText('A panel'))},
  );

  group('levels', () {
    for (final level in KitSurfaceLevel.values) {
      testWidgets(
        '$level paints exactly fillOf($level), no shadow or elevation',
        (tester) async {
          await tester.pumpWidget(
            _host(
              KitSurface(
                level: level,
                child: const SizedBox(width: 40, height: 40),
              ),
            ),
          );
          final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
          final material = _materialOf<KitSurface>(tester);
          expect(material.color, tokens.fillOf(level));
          expect(material.elevation, 0);
          expect(
            find.descendant(
              of: find.byType(KitSurface),
              matching: find.byWidgetPredicate(
                (w) =>
                    w is DecoratedBox &&
                    ((w.decoration as BoxDecoration?)?.boxShadow?.isNotEmpty ??
                        false),
              ),
            ),
            findsNothing,
          );
        },
      );
    }
  });

  testWidgets('shape: panel clips a full-bleed child to the 18 dp radius', (
    tester,
  ) async {
    final boundary = GlobalKey();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          backgroundColor: Colors.black,
          body: Center(
            child: RepaintBoundary(
              key: boundary,
              child: SizedBox(
                width: 120,
                height: 120,
                child: ColoredBox(
                  color: Colors.black,
                  child: KitSurface(
                    shape: KitShape.panel,
                    padding: KitSurfacePadding.none,
                    child: Container(color: Colors.white),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final dpr = tester.view.devicePixelRatio;
    final render = tester.renderObject<RenderRepaintBoundary>(
      find.byKey(boundary),
    );
    final image = (await tester.runAsync(
      () => render.toImage(pixelRatio: dpr),
    ))!;
    final bytes = (await tester.runAsync(
      () => image.toByteData(format: ui.ImageByteFormat.rawRgba),
    ))!;
    int redAt(int x, int y) => bytes.getUint8((y * image.width + x) * 4);
    // The corner is clipped away: it shows the black ground, not the white
    // full-bleed child.
    expect(redAt(0, 0), lessThan(50));
    // The middle is inside the rounded rect: the white child shows through.
    expect(redAt(image.width ~/ 2, image.height ~/ 2), greaterThan(200));
    image.dispose();
  });

  group('shapes', () {
    for (final shape in KitShape.values) {
      testWidgets('shape: $shape resolves to KitTokens.shapeOf($shape)', (
        tester,
      ) async {
        await tester.pumpWidget(
          _host(
            KitSurface(
              shape: shape,
              child: const SizedBox(width: 20, height: 20),
            ),
          ),
        );
        final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
        expect(_materialOf<KitSurface>(tester).shape, tokens.shapeOf(shape));
      });
    }
  });

  testWidgets(
    'outlined paints a hairline edge at devicePixelRatio 2.625 and 3.0, never doubled',
    (tester) async {
      for (final dpr in [2.625, 3.0]) {
        tester.view.devicePixelRatio = dpr;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          _host(
            KitSurface(
              outlined: true,
              child: const SizedBox(width: 40, height: 40),
            ),
          ),
        );
        final context = tester.element(find.byType(KitSurface));
        final expectedWidth = KitTokens.hairlineWidth(context);
        final shape = _materialOf<KitSurface>(tester).shape;
        expect(shape, isA<OutlinedBorder>());
        expect(
          (shape! as OutlinedBorder).side.width,
          closeTo(expectedWidth, 1e-9),
        );
        expect(
          find.descendant(
            of: find.byType(KitSurface),
            matching: find.byType(Material),
          ),
          findsOneWidget,
          reason: 'no doubled line: exactly one Material paints the edge',
        );
      }
    },
  );

  testWidgets('clip: true (the default) clips to the shape; false does not', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(const KitSurface(child: SizedBox(width: 20, height: 20))),
    );
    expect(_materialOf<KitSurface>(tester).clipBehavior, Clip.antiAlias);

    await tester.pumpWidget(
      _host(
        KitSurface(clip: false, child: const SizedBox(width: 20, height: 20)),
      ),
    );
    expect(_materialOf<KitSurface>(tester).clipBehavior, Clip.none);
  });

  testWidgets(
    'KitSurface.panel renders a 20 dp icon in text2 and a headline title '
    'that wraps, never ellipsises',
    (tester) async {
      const title =
          'A title long enough to need two lines at double text scale';
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: Scaffold(
            body: SingleChildScrollView(
              child: SizedBox(
                width: 200,
                child: KitSurface.panel(
                  title: title,
                  icon: AppIconography.star,
                  child: const SizedBox(height: 10),
                ),
              ),
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
      final icon = tester.widget<Icon>(find.byIcon(AppIconography.star));
      expect(icon.size, tokens.smallIconSize);
      expect(icon.color, tokens.roles.text2);
      final text = tester.widget<Text>(find.text(title));
      expect(text.maxLines, isNull, reason: 'no line cap: it wraps');
      expect(text.overflow, isNull, reason: 'no override: never ellipsises');
    },
  );

  testWidgets('KitSurface.inset is surface1 in dark and ground in light', (
    tester,
  ) async {
    final seen = <bool, Color>{};
    for (final light in [false, true]) {
      // Not const: a canonicalized const instance identical to the previous
      // pump's would make Element.updateChild's `child.widget == newWidget`
      // fast path skip rebuilding it, so it would keep the first theme.
      await tester.pumpWidget(
        _host(
          KitSurface.inset(child: const SizedBox(width: 10, height: 10)),
          light: light,
        ),
      );
      // MaterialApp animates a theme change through AnimatedTheme; without
      // settling, Theme.of still reads the previous pump's theme mid-tween.
      await tester.pumpAndSettle();
      final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
      final material = _materialOf<KitSurface>(tester);
      // KitTokens.insetSurface already says "surface1 in dark, ground in
      // light" (kit_tokens.dart); this proves KitSurface.inset paints it.
      expect(material.color, tokens.insetSurface);
      seen[light] = material.color!;
    }
    expect(seen[false], isNot(seen[true]), reason: 'dark and light differ');
  });

  testWidgets(
    'KitSurface.tile is 30x30 surface3 with 9 dp corners and a 20 dp glyph',
    (tester) async {
      await tester.pumpWidget(
        _host(const KitSurface.tile(AppIconography.star)),
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
      expect(
        tester.getSize(find.byType(KitSurface)),
        Size(tokens.iconTileSize, tokens.iconTileSize),
      );
      final material = _materialOf<KitSurface>(tester);
      expect(material.color, tokens.roles.surface3);
      expect(material.shape, tokens.shapeOf(KitShape.tile));
      final icon = tester.widget<Icon>(find.byIcon(AppIconography.star));
      expect(icon.size, tokens.smallIconSize);
    },
  );

  testWidgets(
    'KitSurface.tile: with semanticsLabel it has one image node; without, none',
    (tester) async {
      // The semantics TREE, not the widget tree: `Icon` itself always wraps
      // in a `Semantics(label: null)` widget (icon.dart), so the widget
      // count is not the right proof; the merged node is. Neither instance
      // is const: reusing one canonical instance across pumps would let
      // Element.updateChild's `child.widget == newWidget` fast path skip
      // the rebuild.
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _host(KitSurface.tile(AppIconography.star, semanticsLabel: 'Terminal')),
      );
      final labeled = tester.getSemantics(find.bySemanticsLabel('Terminal'));
      expect(labeled.flagsCollection.isImage, isTrue);

      await tester.pumpWidget(_host(KitSurface.tile(AppIconography.star)));
      expect(find.bySemanticsLabel('Terminal'), findsNothing);
      final unlabeled = tester.getSemantics(find.byType(KitSurface));
      expect(unlabeled.flagsCollection.isImage, isFalse);
      expect(unlabeled.label, isEmpty);
      semantics.dispose();
    },
  );

  testWidgets(
    'KitSurface.tile: a tone other than the text1 default paints that tone',
    (tester) async {
      await tester.pumpWidget(
        _host(
          const KitSurface.tile(
            AppIconography.star,
            tone: KitTextTone.attention,
          ),
        ),
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
      final icon = tester.widget<Icon>(find.byIcon(AppIconography.star));
      expect(
        icon.color,
        KitText.toneColor(tokens.roles, KitTextTone.attention),
      );
    },
  );

  group('KitPanel', () {
    testWidgets('its old signature builds', (tester) async {
      await tester.pumpWidget(
        _host(
          KitPanel(
            key: const ValueKey('panel'),
            tone: AppStatusTone.neutral,
            icon: AppIconography.star,
            title: 'Title',
            titleKey: const ValueKey('title'),
            padding: const EdgeInsets.all(8),
            onTap: () {},
            child: const SizedBox(width: 10, height: 10),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byKey(const ValueKey('panel')), findsOneWidget);
    });

    testWidgets(
      'tone: neutral, default padding, no onTap equals KitSurface.panel',
      (tester) async {
        await tester.pumpWidget(
          _host(
            KitPanel(
              icon: AppIconography.star,
              title: 'Same look',
              child: const SizedBox(width: 10, height: 10),
            ),
          ),
        );
        final surface = tester.widget<KitSurface>(find.byType(KitSurface));
        expect(surface.level, KitSurfaceLevel.surface1);
        expect(surface.shape, KitShape.panel);
        final tokens = KitTokens.of(tester.element(find.byType(KitSurface)));
        final icon = tester.widget<Icon>(find.byIcon(AppIconography.star));
        expect(icon.size, tokens.smallIconSize);
        expect(find.text('Same look'), findsOneWidget);
      },
    );

    testWidgets('tone: attention keeps attentionSurface and attentionLine', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          KitPanel(
            tone: AppStatusTone.attention,
            child: const SizedBox(width: 10, height: 10),
          ),
        ),
      );
      final theme = Theme.of(tester.element(find.byType(KitPanel)));
      final roles = AppTheme.rolesOf(theme);
      final material = _materialOf<KitPanel>(tester);
      expect(
        material.color,
        Color.alphaBlend(roles.attentionSurface, roles.surface1),
      );
      final shape = material.shape;
      expect(shape, isA<RoundedRectangleBorder>());
      expect(
        (shape! as RoundedRectangleBorder).side.color,
        roles.attentionLine,
      );
      expect(
        find.descendant(
          of: find.byType(KitPanel),
          matching: find.byType(KitSurface),
        ),
        findsNothing,
        reason:
            'the retired tone path does not go through KitSurface (no tonal level, KIT-42)',
      );
    });

    testWidgets('onTap still fires', (tester) async {
      var tapped = false;
      await tester.pumpWidget(
        _host(
          KitPanel(
            onTap: () => tapped = true,
            child: const SizedBox(width: 40, height: 40),
          ),
        ),
      );
      await tester.tap(find.byType(KitPanel));
      expect(tapped, isTrue);
    });

    for (final tone in [AppStatusTone.neutral, AppStatusTone.attention]) {
      testWidgets(
        'tone: ${tone.name}, a tap inside the padding band still fires onTap',
        (tester) async {
          var tapped = 0;
          await tester.pumpWidget(
            _host(
              KitPanel(
                tone: tone,
                padding: const EdgeInsets.all(16),
                onTap: () => tapped++,
                child: const SizedBox(width: 120, height: 40),
              ),
            ),
          );
          final topLeft = tester.getTopLeft(find.byType(KitPanel));
          // Left band, and top band (inside the 16 dp padding, outside the
          // child), away from the rounded corner.
          await tester.tapAt(topLeft + const Offset(4, 20));
          await tester.tapAt(topLeft + const Offset(40, 4));
          expect(tapped, 2);
        },
      );
    }

    testWidgets(
      'a tappable neutral panel inks the whole panel, padding included',
      (tester) async {
        await tester.pumpWidget(
          _host(
            KitPanel(
              padding: const EdgeInsetsDirectional.fromSTEB(8, 8, 4, 10),
              onTap: () {},
              child: const SizedBox(width: 120, height: 40),
            ),
          ),
        );
        final ink = find.descendant(
          of: find.byType(KitPanel),
          matching: find.byType(InkWell),
        );
        expect(ink, findsOneWidget);
        expect(tester.getRect(ink), tester.getRect(find.byType(KitPanel)));
      },
    );

    testWidgets(
      'a custom-padding panel draws the same header as KitSurface.panel',
      (tester) async {
        await tester.pumpWidget(
          _host(
            KitPanel(
              icon: AppIconography.terminal,
              title: 'Header',
              padding: const EdgeInsets.all(8),
              child: const SizedBox(width: 10, height: 10),
            ),
          ),
        );
        final tokens = KitTokens.of(tester.element(find.byType(KitPanel)));
        final icon = tester.widget<Icon>(find.byIcon(AppIconography.terminal));
        expect(icon.size, tokens.smallIconSize);
        expect(icon.color, tokens.roles.text2);
        final title = tester.widget<KitText>(
          find.ancestor(
            of: find.text('Header'),
            matching: find.byType(KitText),
          ),
        );
        expect(title.role, KitTextRole.headline);
      },
    );

    testWidgets('tone: attention draws its border as a hairline', (
      tester,
    ) async {
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        _host(
          KitPanel(
            tone: AppStatusTone.attention,
            child: const SizedBox(width: 10, height: 10),
          ),
        ),
      );
      final shape = _materialOf<KitPanel>(tester).shape!;
      expect((shape as RoundedRectangleBorder).side.width, 1 / 3);
    });

    testWidgets(
      'a custom padding still gets the panel fill and shape from KitSurface',
      (tester) async {
        await tester.pumpWidget(
          _host(
            KitPanel(
              padding: const EdgeInsetsDirectional.fromSTEB(8, 8, 4, 10),
              child: const SizedBox(width: 10, height: 10),
            ),
          ),
        );
        final tokens = KitTokens.of(tester.element(find.byType(KitPanel)));
        final material = _materialOf<KitPanel>(tester);
        expect(material.color, tokens.roles.surface1);
        expect(material.shape, tokens.shapeOf(KitShape.panel));
        expect(find.byType(KitSurface), findsOneWidget);
      },
    );
  });

  testWidgets(
    'a descendant needing a Material ancestor builds without an error',
    (tester) async {
      await tester.pumpWidget(_host(const KitSurface(child: TextField())));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('under RTL the panel header icon sits at the right', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        SizedBox(
          width: 220,
          child: KitSurface.panel(
            title: 'Title',
            icon: AppIconography.star,
            child: const SizedBox(height: 4),
          ),
        ),
        direction: TextDirection.rtl,
      ),
    );
    final iconLeft = tester.getTopLeft(find.byIcon(AppIconography.star)).dx;
    final titleLeft = tester.getTopLeft(find.text('Title')).dx;
    expect(iconLeft, greaterThan(titleLeft));
  });

  testWidgets(
    'a static surface creates no ticker (reduced motion has nothing to reduce)',
    (tester) async {
      await tester.pumpWidget(
        _host(
          KitSurface.panel(
            title: 'T',
            icon: AppIconography.star,
            child: const SizedBox(height: 4),
          ),
        ),
      );
      await tester.pump();
      expect(SchedulerBinding.instance.transientCallbackCount, 0);
    },
  );

  group('G6 overflow matrix (no images)', () {
    // LAY-4's overflow widths and scales (test/text_scale_overflow_test.dart
    // §"G6"), pumped directly here rather than through the shared
    // test/kit/kit_overflow_scenes.dart registry: that registry is read from
    // lib/ui/kit/kit.dart's exports, and KitSurface is not exported there
    // yet (R06, the integrator's step), so a scene naming it now would be a
    // stale entry until that export lands.
    const sizes = [
      Size(320, 640),
      Size(360, 740),
      Size(412, 915),
      Size(600, 960),
      Size(800, 1280),
      Size(840, 1180),
      Size(1280, 800),
      Size(1600, 1000),
      Size(915, 412),
    ];
    const scales = [1.0, 1.3, 2.0];

    Widget scene() => SingleChildScrollView(
      padding: const EdgeInsets.all(8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitSurface.panel(
            title:
                'A title long enough to test wrapping under every width '
                'and text scale in the matrix',
            icon: AppIconography.star,
            child: const SizedBox(height: 4),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              KitSurface.tile(
                AppIconography.terminal,
                tone: KitTextTone.accent,
              ),
              const SizedBox(width: 8),
              KitSurface.tile(
                AppIconography.warning,
                tone: KitTextTone.primary,
              ),
            ],
          ),
          const SizedBox(height: 12),
          KitSurface.inset(child: const SizedBox(height: 20)),
          const SizedBox(height: 12),
          KitSurface(outlined: true, child: const SizedBox(height: 20)),
        ],
      ),
    );

    for (final size in sizes) {
      for (final scale in scales) {
        final label =
            '${size.width.toInt()}x${size.height.toInt()} at ${scale}x';
        testWidgets('$label: no overflow', (tester) async {
          tester.view.physicalSize = size;
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.reset);
          await tester.pumpWidget(
            MaterialApp(
              theme: AppTheme.dark(),
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scale)),
                child: child!,
              ),
              home: Scaffold(body: scene()),
            ),
          );
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
        });
      }
    }
  });

  // Flat polish (sweep 3730, 45-report): Report a problem's summary was
  // still a white rounded card in Solid, where every list sits on the page.
  testWidgets('flatInLists: on the page in Solid, its words on the box\'s '
      'own edge; a padded box otherwise', (tester) async {
    await tester.pumpWidget(
      _host(
        const SizedBox(
          width: 300,
          child: Column(
            children: [
              KitSurface(
                key: ValueKey('flat'),
                outlined: true,
                flatInLists: true,
                child: Text('Android closed the app'),
              ),
              KitSurface(
                key: ValueKey('card'),
                outlined: true,
                child: Text('A card'),
              ),
            ],
          ),
        ),
      ),
    );
    final tokens = KitTokens.of(tester.element(find.text('A card')));
    double inset(String key, String text) =>
        tester.getTopLeft(find.text(text)).dx -
        tester.getTopLeft(find.byKey(ValueKey(key))).dx;
    expect(inset('flat', 'Android closed the app'), 0);
    expect(inset('card', 'A card'), tokens.space4);
  });
}
