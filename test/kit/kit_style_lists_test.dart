// Slice S7 of the style cleanup (docs/audits/style-coherence-2026-10-21.md
// §6): lists, rows, tabs and dividers ask the style resolver. Solid keeps
// each part's own look; the other styles get what their spec answers for
// the part's role (divider, stateLayer, a chosen control).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_paper.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

Future<BuildContext> _pump(
  WidgetTester tester,
  KitSurfaceStyle style,
  Widget child,
) async {
  late BuildContext inner;
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: child!,
      ),
      home: KitEffectsScope(
        effects: KitEffects(surfaceStyle: style),
        child: kitStyleTheme(
          child: Scaffold(
            body: Builder(
              builder: (context) {
                inner = context;
                return child;
              },
            ),
          ),
        ),
      ),
    ),
  );
  return inner;
}

final _line = find.byKey(const ValueKey('kit-divider-line'));

/// Paper with a section rule: the `divider` role chosen answers in full
/// ink, apart from a row's rule.
class _RuledPaper extends KitStyleSpec {
  const _RuledPaper();

  static const paper = KitStylePaper();

  @override
  KitStyleTokens get tokens => paper.tokens;

  @override
  ThemeRoles adjustRoles(ThemeRoles roles) => paper.adjustRoles(roles);

  @override
  Decoration resolve(KitSurfaceRequest r) =>
      r.role == KitSurfaceRole.divider && r.selected
      ? KitSurfaceDecoration(fill: r.roles.text1, edgeWidth: 1)
      : paper.resolve(r);
}

void main() {
  group('KitDivider takes the divider role', () {
    testWidgets('Solid: the hairline; Brutal: an ink hairline; Soft: the '
        'space alone', (tester) async {
      final solid = await _pump(
        tester,
        KitSurfaceStyle.solid,
        const KitDivider(),
      );
      final hairline = KitTokens.hairlineWidth(solid);
      expect(tester.getSize(_line).height, hairline);
      expect(
        tester.renderObject(_line),
        paints..rect(color: KitTokens.of(solid).roles.hairline),
      );

      final brutal = await _pump(
        tester,
        KitSurfaceStyle.brutal,
        const KitDivider(),
      );
      // Ink at the kit's hairline weight (the owner found the 2 dp rule
      // under the Appearance preview too heavy, 2026-10-22).
      expect(tester.getSize(_line).height, KitTokens.hairlineWidth(brutal));
      expect(
        tester.renderObject(_line),
        paints
          ..rect(color: KitTokens.of(brutal).roles.text1.withValues(alpha: .9)),
      );

      final soft = await _pump(
        tester,
        KitSurfaceStyle.soft,
        const KitDivider(),
      );
      expect(tester.getSize(_line).height, hairline);
      expect(
        tester.renderObject(_line),
        paints
          ..rect(color: KitTokens.of(soft).roles.ground.withValues(alpha: 0)),
      );
    });

    testWidgets('the section rule takes no room unless a style answers it', (
      tester,
    ) async {
      for (final style in KitSurfaceStyle.values) {
        // Paper answers it (polish-paper): the rule above a section label.
        if (style == KitSurfaceStyle.paper) continue;
        await _pump(tester, style, const KitDivider.section());
        expect(_line, findsNothing, reason: style.name);
        expect(
          tester.getSize(find.byType(KitDivider)).height,
          0,
          reason: style.name,
        );
      }
      debugKitStyleSpecs = (style) =>
          style == KitSurfaceStyle.paper ? const _RuledPaper() : null;
      addTearDown(() => debugKitStyleSpecs = null);
      final paper = await _pump(
        tester,
        KitSurfaceStyle.paper,
        const KitDivider.section(),
      );
      expect(tester.getSize(_line).height, 1);
      expect(
        tester.renderObject(_line),
        paints..rect(color: KitTokens.of(paper).roles.text1),
      );
    });
  });

  group('KitTappable press is the state layer', () {
    Decoration? layerOf(WidgetTester tester) => tester
        .widgetList<AnimatedContainer>(
          find.descendant(
            of: find.byType(KitTappable),
            matching: find.byType(AnimatedContainer),
          ),
        )
        .first
        .decoration;

    for (final style in KitSurfaceStyle.values) {
      testWidgets('${style.name}: at rest none, held the spec\'s layer', (
        tester,
      ) async {
        final context = await _pump(
          tester,
          style,
          KitTappable(
            onTap: () {},
            child: const SizedBox(width: 120, height: 48),
          ),
        );
        final rest = layerOf(tester);
        expect(rest, isA<ShapeDecoration>());
        expect((rest! as ShapeDecoration).color!.a, 0);
        final gesture = await tester.startGesture(
          tester.getCenter(find.byType(KitTappable)),
        );
        await tester.pump();
        final held = layerOf(tester);
        final roles = KitTokens.of(context).roles;
        if (style == KitSurfaceStyle.solid) {
          expect(held, isA<ShapeDecoration>());
        } else {
          expect(held, isA<KitSurfaceDecoration>());
          if (style == KitSurfaceStyle.brutal) {
            expect((held! as KitSurfaceDecoration).fill, roles.surface3);
          }
          if (style == KitSurfaceStyle.paper) {
            // A step toward the ink (polish-paper), opaque, off the paper.
            final fill = (held! as KitSurfaceDecoration).fill;
            expect(fill.a, 1);
            expect(fill, isNot(roles.ground));
          }
          if (style == KitSurfaceStyle.soft) {
            expect((held! as KitSurfaceDecoration).inset, 1);
          }
        }
        await gesture.up();
        await tester.pump(const Duration(seconds: 1));
      });
    }
  });

  testWidgets('Paper sets an unboxed group\'s rows flush with its label; '
      'Solid too (flat lists); Soft keeps them inset in its panel', (
    tester,
  ) async {
    double gap() =>
        tester.getTopLeft(find.text('Appearance')).dx -
        tester.getTopLeft(find.text('General')).dx;
    final group = KitRowGroup(
      label: 'General',
      leadingIcons: false,
      children: [KitRow(title: 'Appearance', onTap: () {})],
    );
    await _pump(tester, KitSurfaceStyle.soft, group);
    expect(gap(), greaterThan(0));
    await _pump(tester, KitSurfaceStyle.paper, group);
    expect(gap(), 0);
    // Solid's lists are flat: rows on the page, words under the label.
    await _pump(tester, KitSurfaceStyle.solid, group);
    expect(gap(), 0);
  });

  testWidgets('the chosen segment is a chosen control in every style '
      '(Paper inverts it: one block of ink)', (tester) async {
    await _pump(
      tester,
      KitSurfaceStyle.paper,
      KitSegmented<int>(
        segments: const [
          KitSegment(value: 0, label: 'One'),
          KitSegment(value: 1, label: 'Two'),
        ],
        selected: 0,
        onChanged: (_) {},
        semanticsLabel: 'Count',
      ),
    );
    final roles = KitTokens.of(
      tester.element(find.byType(KitSegmented<int>)),
    ).roles;
    final fills = tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byType(KitSegmented<int>),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map((b) => b.decoration)
        .whereType<KitSurfaceDecoration>()
        .map((d) => d.fill);
    expect(fills, contains(KitStylePaper.invertedInk(roles)));
  });
}
