// KitIcon (docs/ux-system/kit-api/KitIcon.md, wave 1, tier 1a): the one
// way to draw a glyph. KitIcon.status reads the kit's one status map,
// KitTokens.glyphFor / toneFor; docs/qa/revamp-kit-KitIcon-2026-09-26/
// README.md has the evidence.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/no_red.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_icon.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

Widget _host(
  Widget child, {
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
  bool highContrast = false,
}) => MaterialApp(
  theme: AppTheme.dark(),
  builder: (context, widget) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      textScaler: TextScaler.linear(textScale),
      highContrast: highContrast,
    ),
    child: widget!,
  ),
  home: Directionality(
    textDirection: direction,
    child: Scaffold(body: Center(child: child)),
  ),
);

void main() {
  kitMotionStillTests(
    'KitBrandMark',
    builds: {'tile': () => const KitBrandMark()},
  );

  kitMotionStillTests(
    'KitIcon',
    builds: {
      'small': () => const KitIcon(AppIconography.check),
      'status': () => const KitIcon.status(AppStatusTone.attention),
      'duotone': () => const KitIcon(AppIconography.workspaceSelected),
      'growsWithText': () =>
          const KitIcon(AppIconography.check, growsWithText: true),
    },
  );

  group('sizes (VL §7, C02: s/m/l = 20/22/24)', () {
    testWidgets(
      'fixed sizes render at exactly 20, 22 and 24 logical px at text scale '
      '1.0 and 2.0',
      (tester) async {
        for (final scale in [1.0, 2.0]) {
          await tester.pumpWidget(
            _host(
              const Column(
                children: [
                  KitIcon(AppIconography.check, size: KitIconSize.small),
                  KitIcon(AppIconography.check, size: KitIconSize.medium),
                  KitIcon(AppIconography.check, size: KitIconSize.large),
                ],
              ),
              textScale: scale,
            ),
          );
          final sizes = tester
              .widgetList<Icon>(find.byType(Icon))
              .map((i) => i.size)
              .toList();
          expect(sizes, [20, 22, 24], reason: 'at text scale $scale');
        }
      },
    );

    testWidgets(
      'growsWithText scales up to 1.5x and rounds to a whole physical pixel',
      (tester) async {
        for (final dpr in [2.625, 3.0]) {
          tester.view.devicePixelRatio = dpr;
          addTearDown(tester.view.reset);
          await tester.pumpWidget(
            _host(
              const KitIcon(
                AppIconography.check,
                size: KitIconSize.large,
                growsWithText: true,
              ),
              textScale: 2.0,
            ),
          );
          final size = tester.widget<Icon>(find.byType(Icon)).size!;
          // min(24 * 2, 24 * 1.5) = 36, then rounded to a whole physical
          // pixel at this DPR.
          final expected = (36 * dpr).roundToDouble() / dpr;
          expect(size, closeTo(expected, 1e-9), reason: 'at dpr $dpr');
          final physical = size * dpr;
          expect(
            physical,
            closeTo(physical.roundToDouble(), 1e-6),
            reason: 'size * dpr must be a whole physical pixel at dpr $dpr',
          );
        }
      },
    );
  });

  group('pixel alignment (VL §7: aligned to whole pixels)', () {
    testWidgets(
      'a KitIcon laid out at a fractional offset paints on whole physical '
      'pixels without moving its layout box',
      (tester) async {
        for (final (dpr, icon) in [
          (3.0, const KitIcon(AppIconography.check, size: KitIconSize.medium)),
          (
            2.625,
            const KitIcon(
              AppIconography.check,
              size: KitIconSize.small,
              growsWithText: true,
            ),
          ),
          (3.0, const KitBrandMark()),
        ]) {
          tester.view.devicePixelRatio = dpr;
          addTearDown(tester.view.reset);
          await tester.pumpWidget(
            _host(
              Align(
                alignment: AlignmentDirectional.topStart,
                child: Padding(
                  padding: const EdgeInsetsDirectional.only(
                    start: 10.3,
                    top: 7.45,
                  ),
                  child: icon,
                ),
              ),
              textScale: 1.3,
            ),
          );
          final layout = tester.getTopLeft(find.byWidget(icon));
          expect(layout.dx, closeTo(10.3, 1e-9), reason: 'layout unchanged');
          final painted = find
              .descendant(
                of: find.byWidget(icon),
                matching: find.byWidgetPredicate(
                  (w) => w is Icon || w is SvgPicture,
                ),
              )
              .first;
          final rect = tester.getRect(painted);
          for (final edge in [rect.left, rect.top, rect.right, rect.bottom]) {
            final physical = edge * dpr;
            expect(
              physical,
              closeTo(physical.roundToDouble(), 1e-6),
              reason: '$icon at dpr $dpr: $rect',
            );
          }
          expect(
            (rect.topLeft - layout).distance,
            lessThanOrEqualTo(1 / dpr),
            reason: 'the shift is under one physical pixel',
          );
        }
      },
    );
  });

  group('the app icon set (KitIcon.md "Replaces")', () {
    testWidgets(
      'the debug assert rejects Icons.add and passes AppIconography.add '
      'and AppIcons.copy',
      (tester) async {
        await tester.pumpWidget(_host(const KitIcon(Icons.add)));
        expect(tester.takeException(), isAssertionError);

        await tester.pumpWidget(_host(const KitIcon(AppIconography.add)));
        expect(tester.takeException(), isNull);

        await tester.pumpWidget(_host(const KitIcon(AppIcons.copy)));
        expect(tester.takeException(), isNull);
      },
    );
  });

  group('accessibility (A11Y-1)', () {
    testWidgets('decorative by default; semanticsLabel gives one image node', (
      tester,
    ) async {
      // Explicit dispose, not addTearDown: package:test runs tearDowns after
      // the flutter_test binding's own end-of-test invariant check, which
      // would still see the handle as active.
      final handle = tester.ensureSemantics();
      final host = find.byKey(const ValueKey('host'));
      // The host is a bare semantics container: its node is the one an
      // icon that adds nothing reports as its own nearest node.
      Widget hosted(Widget icon) => _host(
        Semantics(
          key: const ValueKey('host'),
          container: true,
          explicitChildNodes: true,
          child: icon,
        ),
      );

      // Unlabelled: the icon's nearest semantics node is its host's, so it
      // adds no node, and nothing under the host is an image or has a label.
      await tester.pumpWidget(hosted(const KitIcon(AppIconography.check)));
      final hostNode = tester.getSemantics(host);
      expect(tester.getSemantics(find.byType(KitIcon)), same(hostNode));
      expect(hostNode.childrenCount, 0);
      expect(_imageOrLabelledNodes(hostNode), isEmpty);

      // Labelled: exactly one node of its own, an image with that label.
      await tester.pumpWidget(
        hosted(
          const KitIcon(AppIconography.cloudOff, semanticsLabel: 'Offline'),
        ),
      );
      final labelledHost = tester.getSemantics(host);
      expect(labelledHost.childrenCount, 1);
      late final SemanticsNode labelled;
      labelledHost.visitChildren((child) {
        labelled = child;
        return false;
      });
      expect(labelled.label, 'Offline');
      expect(labelled.getSemanticsData().flagsCollection.isImage, isTrue);
      expect(_imageOrLabelledNodes(labelled), [labelled]);
      expect(find.bySemanticsLabel('Offline'), findsOneWidget);
      handle.dispose();
    });
  });

  group('KitIcon.status (README.md decision D12)', () {
    testWidgets(
      'maps each AppStatusTone to the tone the spec names; failure is not '
      'danger and attention is not the attention role',
      (tester) async {
        final theme = AppTheme.dark();
        final roles = ThemeRoles.resolve(theme);
        final expected = <AppStatusTone, Color>{
          AppStatusTone.neutral: roles.text2,
          AppStatusTone.progress: roles.accent,
          AppStatusTone.ok: roles.success,
          AppStatusTone.attention: roles.text1,
          AppStatusTone.failure: roles.text1,
        };
        for (final status in AppStatusTone.values) {
          await tester.pumpWidget(
            MaterialApp(
              theme: theme,
              home: Center(child: KitIcon.status(status)),
            ),
          );
          final color = tester.widget<Icon>(find.byType(Icon)).color;
          expect(color, expected[status], reason: '$status');
        }
        expect(expected[AppStatusTone.failure], isNotRed);
        expect(expected[AppStatusTone.attention], isNot(roles.attention));
      },
    );

    testWidgets(
      'each status draws its own glyph, the one KitTokens.glyphFor and '
      'KitStatusMark use, so no state is colour alone',
      (tester) async {
        final drawn = <AppStatusTone, IconData?>{};
        for (final status in AppStatusTone.values) {
          await tester.pumpWidget(_host(KitIcon.status(status)));
          drawn[status] = tester.widget<Icon>(find.byType(Icon)).icon;
          expect(drawn[status], KitTokens.glyphFor(status), reason: '$status');
        }
        expect(drawn.values.toSet(), hasLength(AppStatusTone.values.length));
        expect(drawn.values, isNot(contains(AppIconography.info)));

        // KitStatusMark's done and failed marks are the same glyphs.
        for (final (state, status) in [
          (KitMarkState.done, AppStatusTone.ok),
          (KitMarkState.failed, AppStatusTone.failure),
        ]) {
          await tester.pumpWidget(_host(KitStatusMark(state: state)));
          // The mark cross-fades between states; let the old one leave.
          await tester.pumpAndSettle();
          expect(
            tester.widget<Icon>(find.byType(Icon)).icon,
            KitTokens.glyphFor(status),
            reason: '$state',
          );
        }

        // A status line's own glyph still wins, in the status's tone.
        await tester.pumpWidget(
          _host(
            const KitIcon.status(
              AppStatusTone.ok,
              icon: AppIconography.cloudOff,
            ),
          ),
        );
        expect(
          tester.widget<Icon>(find.byType(Icon)).icon,
          AppIconography.cloudOff,
        );
      },
    );

    testWidgets('defaults to small and grows with text', (tester) async {
      await tester.pumpWidget(
        _host(const KitIcon.status(AppStatusTone.ok), textScale: 2.0),
      );
      final icon = tester.widget<Icon>(find.byType(Icon));
      expect(icon.size, greaterThan(20));
      expect(icon.size, lessThanOrEqualTo(20 * 1.5));
    });
  });

  group('RTL (LAY-8)', () {
    testWidgets(
      'AppIconography.back mirrors under RTL; AppIconography.check does not',
      (tester) async {
        await tester.pumpWidget(
          _host(
            const Column(
              children: [
                KitIcon(AppIconography.back),
                KitIcon(AppIconography.check),
              ],
            ),
            direction: TextDirection.rtl,
          ),
        );
        Finder iconFinder(IconData data) =>
            find.byWidgetPredicate((w) => w is Icon && w.icon == data);
        // The mirroring Transform (a horizontal flip) is inside Icon's own
        // build output, so it is a descendant of the Icon widget, not an
        // ancestor; Icon emits no Transform at all when it does not mirror.
        bool flipped(Finder of) => find
            .descendant(of: of, matching: find.byType(Transform))
            .evaluate()
            .map((e) => e.widget as Transform)
            .any((t) => t.transform.getColumn(0).x < 0);
        expect(flipped(iconFinder(AppIconography.back)), isTrue);
        expect(flipped(iconFinder(AppIconography.check)), isFalse);
      },
    );
  });

  group('duotone (LOOK-33: the dock/rail selected glyph only)', () {
    testWidgets('draws two layers, one under high contrast', (tester) async {
      await tester.pumpWidget(
        _host(const KitIcon(AppIconography.workspaceSelected)),
      );
      expect(find.byType(Icon), findsNWidgets(2));
      // The tree also carries unrelated Opacity widgets (Material's own
      // scaffolding), so the wash is found by its value, not by count.
      Finder wash() => find.byWidgetPredicate(
        (w) => w is Opacity && w.opacity == KitTokens.duotoneWash,
      );
      expect(wash(), findsOneWidget);

      await tester.pumpWidget(
        _host(
          const KitIcon(AppIconography.workspaceSelected),
          highContrast: true,
        ),
      );
      expect(find.byType(Icon), findsOneWidget);
      expect(wash(), findsNothing);
    });

    testWidgets('a non-duotone glyph draws one layer', (tester) async {
      await tester.pumpWidget(_host(const KitIcon(AppIconography.check)));
      expect(find.byType(Icon), findsOneWidget);
    });
  });

  group('tone colours (§7: no text or icon at partial opacity)', () {
    testWidgets(
      'the rendered glyph is opaque and unshadowed under a translucent, '
      'half-opacity, shadowed ambient IconTheme',
      (tester) async {
        final theme = AppTheme.dark();
        const ambient = IconThemeData(
          color: Color(0x61FF0000),
          opacity: .5,
          shadows: [Shadow(blurRadius: 4)],
          applyTextScaling: true,
        );
        final icons = <String, Widget>{
          'ambient': const KitIcon(AppIconography.check),
          for (final tone in KitTextTone.values)
            '$tone': KitIcon(AppIconography.check, tone: tone),
          for (final status in AppStatusTone.values)
            '$status': KitIcon.status(status),
          'duotone': const KitIcon(AppIconography.workspaceSelected),
        };
        for (final MapEntry(key: name, value: icon) in icons.entries) {
          await tester.pumpWidget(
            MaterialApp(
              theme: theme,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: const TextScaler.linear(2)),
                child: child!,
              ),
              home: Center(
                child: IconTheme(data: ambient, child: icon),
              ),
            ),
          );
          final glyphs = tester
              .widgetList<RichText>(
                find.descendant(
                  of: find.byType(KitIcon),
                  matching: find.byType(RichText),
                ),
              )
              .toList();
          expect(glyphs, isNotEmpty, reason: name);
          for (final glyph in glyphs) {
            final style = (glyph.text as TextSpan).style!;
            expect(style.color!.a, 1.0, reason: '$name: colour alpha 255');
            expect(style.shadows ?? const [], isEmpty, reason: name);
          }
          if (name == 'ambient') {
            expect(
              glyphs.single.text.style!.color,
              const Color(0xFFFF0000),
              reason: 'the ambient colour, made opaque',
            );
          }
          // The ambient applyTextScaling does not double a fixed size.
          if (!name.startsWith('AppStatusTone')) {
            expect(tester.getSize(find.byType(KitIcon)), const Size(24, 24));
          }
        }
      },
    );

    testWidgets('an explicit tone overrides the ambient icon colour', (
      tester,
    ) async {
      final theme = AppTheme.dark();
      final roles = ThemeRoles.resolve(theme);
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: Center(
            child: IconTheme(
              data: IconThemeData(color: roles.success),
              child: const KitIcon(
                AppIconography.check,
                tone: KitTextTone.accent,
              ),
            ),
          ),
        ),
      );
      expect(tester.widget<Icon>(find.byType(Icon)).color, roles.accent);
    });

    testWidgets('a null tone takes the ambient icon colour, else text1', (
      tester,
    ) async {
      final theme = AppTheme.dark();
      final roles = ThemeRoles.resolve(theme);
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: Center(
            child: IconTheme(
              data: IconThemeData(color: roles.success),
              child: const KitIcon(AppIconography.check),
            ),
          ),
        ),
      );
      expect(tester.widget<Icon>(find.byType(Icon)).color, roles.success);

      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: Center(child: const KitIcon(AppIconography.check)),
        ),
      );
      expect(tester.widget<Icon>(find.byType(Icon)).color, roles.text1);
    });
  });

  group(
    'AppGlyph and AppBrandMark (KIT-43: moved, re-exported, unchanged)',
    () {
      testWidgets(
        'AppGlyph still builds from app_iconography.dart with its old '
        'parameters',
        (tester) async {
          await tester.pumpWidget(
            _host(
              const AppGlyph(
                AppIconography.check,
                size: 18,
                color: Color(0xFF00FF00),
                semanticLabel: 'Done',
              ),
            ),
          );
          final icon = tester.widget<Icon>(find.byType(Icon));
          expect(icon.size, 18);
          expect(icon.color, const Color(0xFF00FF00));
          expect(find.bySemanticsLabel('Done'), findsOneWidget);
        },
      );

      testWidgets('AppBrandMark still builds from app_iconography.dart', (
        tester,
      ) async {
        await tester.pumpWidget(_host(const AppBrandMark(size: 40)));
        expect(find.byType(AppBrandMark), findsOneWidget);
      });
    },
  );

  group('KitBrandMark', () {
    testWidgets('renders at the tile size (KitTokens.iconTileSize, 28)', (
      tester,
    ) async {
      final theme = AppTheme.dark();
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: const Center(child: KitBrandMark()),
        ),
      );
      final picture = tester.widget<SvgPicture>(find.byType(SvgPicture));
      final tokens = KitTokens.fallback(theme);
      expect(tokens.iconTileSize, 28);
      expect(tokens.markSize, 44);
      expect(picture.width, tokens.iconTileSize);
    });

    testWidgets('renders at the mark size (KitTokens.markSize, 44)', (
      tester,
    ) async {
      final theme = AppTheme.dark();
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: const Center(child: KitBrandMark(size: KitBrandMarkSize.mark)),
        ),
      );
      final picture = tester.widget<SvgPicture>(find.byType(SvgPicture));
      expect(picture.width, KitTokens.fallback(theme).markSize);
    });

    testWidgets('is decorative by default; a label gives one image node', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        _host(const KitBrandMark(semanticsLabel: 'Open Portal')),
      );
      expect(find.bySemanticsLabel('Open Portal'), findsOneWidget);
      handle.dispose();
    });
  });
}

/// The nodes under (and including) [root] that are images or carry a label.
List<SemanticsNode> _imageOrLabelledNodes(SemanticsNode root) {
  final found = <SemanticsNode>[];
  bool visit(SemanticsNode node) {
    final data = node.getSemanticsData();
    if (data.flagsCollection.isImage || data.label.isNotEmpty) found.add(node);
    node.visitChildren(visit);
    return true;
  }

  visit(root);
  return found;
}
