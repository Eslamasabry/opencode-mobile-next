// The Appearance bugs the owner found on the phone on 2026-10-22 (Midnight,
// blue accent), one test each, kit level. The chat ones are in
// owner_phone_bugs_chat_2026_10_22_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_brutal.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

Widget _app(
  Widget child, {
  KitSurfaceStyle style = KitSurfaceStyle.solid,
  bool light = true,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: light
      ? AppTheme.light(themePack(ThemePackId.midnight))
      : AppTheme.dark(themePack(ThemePackId.midnight)),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, inner) => KitEffectsScope(
    effects: KitSurfaceLook.choose(KitEffects.defaults, style),
    child: kitStyleTheme(child: inner!),
  ),
  home: Scaffold(
    body: Align(alignment: Alignment.topLeft, child: child),
  ),
);

/// Every decoration painted under [of].
Iterable<Decoration> _decorations(WidgetTester tester, Finder of) => tester
    .widgetList<DecoratedBox>(
      find.descendant(of: of, matching: find.byType(DecoratedBox)),
    )
    .map((box) => box.decoration);

bool _visible(Color c) => c.a > 0;

/// Whether [d] draws an edge in [color] at least [width] wide.
bool _edged(Decoration d, Color color, double width) => switch (d) {
  KitSurfaceDecoration(:final edgeStart, :final edgeEnd, :final edgeWidth) =>
    edgeWidth >= width &&
        edgeStart.withValues(alpha: 1) == color.withValues(alpha: 1) &&
        edgeEnd.withValues(alpha: 1) == color.withValues(alpha: 1),
  BoxDecoration(:final Border border) =>
    border.top.width >= width &&
        border.top.color.withValues(alpha: 1) == color.withValues(alpha: 1),
  _ => false,
};

bool _casts(Decoration d) =>
    d is KitSurfaceDecoration && d.hardOffset > 0 && _visible(d.hardShadow);

/// The Style picker's cards, three to a row as on Appearance.
Widget _styleRow(KitSurfaceStyle chosen) => SizedBox(
  width: 412,
  child: Wrap(
    children: [
      for (final style in KitSurfaceStyle.values)
        SizedBox(
          width: 412 / 3,
          child: KitOptionCard.surface(
            key: ValueKey('card-${style.name}'),
            style: style,
            label: style.name,
            selected: style == chosen,
            onPressed: () {},
          ),
        ),
    ],
  ),
);

void main() {
  // Bug 1: in Paper the unchosen style cards lost their frames.
  testWidgets('Paper: every style card keeps a ruled frame', (tester) async {
    await tester.pumpWidget(
      _app(_styleRow(KitSurfaceStyle.paper), style: KitSurfaceStyle.paper),
    );
    await tester.pumpAndSettle();
    final ink = KitTokens.of(
      tester.element(find.text('paper')),
    ).roles.text1.withValues(alpha: 1);
    for (final style in KitSurfaceStyle.values) {
      // The card's own frame, not a piece of its sample.
      final card = find.byKey(ValueKey('card-${style.name}'));
      final sample = _decorations(
        tester,
        find.byKey(ValueKey('kit-style-sample-${style.name}')),
      ).toSet();
      final own = _decorations(tester, card).where((d) => !sample.contains(d));
      expect(own.any((d) => _edged(d, ink, 1)), isTrue, reason: style.name);
    }
  });

  // Bug 2: the samples were blank pills, alike in light. Each is now a tiny
  // scene in its own style: a card, a chip and the main action's button.
  testWidgets('each style sample is a scene in its own style', (tester) async {
    for (final chosen in [KitSurfaceStyle.solid, KitSurfaceStyle.paper]) {
      await tester.pumpWidget(_app(_styleRow(chosen), style: chosen));
      await tester.pumpAndSettle();
      final scenes = <KitSurfaceStyle, List<Decoration>>{};
      for (final style in KitSurfaceStyle.values) {
        final sample = find.descendant(
          of: find.byKey(ValueKey('card-${style.name}')),
          matching: find.byKey(ValueKey('kit-style-sample-${style.name}')),
        );
        expect(sample, findsOneWidget, reason: style.name);
        scenes[style] = _decorations(tester, sample).toList();
        // The main action is in the accent (Brutal's bright face of it).
        final roles = ThemeRoles.resolve(
          kitStyleRestyled(
            Theme.of(tester.element(find.text(style.name))),
            style,
          ),
        );
        final action = style == KitSurfaceStyle.brutal
            ? KitStyleBrutal.mintOf(roles)
            : roles.accent;
        expect(
          scenes[style]!.any(
            (d) =>
                (d is KitSurfaceDecoration && d.fill == action) ||
                (d is BoxDecoration && d.color == action),
          ),
          isTrue,
          reason: '${style.name} under ${chosen.name}: its button',
        );
      }
      // Brutal's sample casts its hard shadow even when Brutal is not
      // chosen; Paper's never does.
      expect(scenes[KitSurfaceStyle.brutal]!.any(_casts), isTrue);
      expect(scenes[KitSurfaceStyle.paper]!.any(_casts), isFalse);
      // Solid and Soft read apart.
      final light = [
        KitSurfaceStyle.solid,
        KitSurfaceStyle.soft,
      ].map((s) => scenes[s]!.toSet()).toList();
      expect(light[0].difference(light[1]), isNotEmpty);
    }
  });

  // Bug 3: Brutal's theme cards and the Match theme tile had no ink edge.
  testWidgets('Brutal: theme cards and Match theme wear the ink edge', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        SizedBox(
          width: 412,
          child: Column(
            children: [
              SizedBox(
                width: 160,
                height: KitOptionCard.themeHeight,
                child: KitOptionCard.theme(
                  key: const ValueKey('theme-card'),
                  roles: themePack(ThemePackId.forest).light.themeRoles,
                  label: 'Forest',
                  selected: false,
                  onPressed: () {},
                ),
              ),
              KitAccentRow(
                semanticsLabel: 'Accent',
                matchLabel: 'Match theme',
                theme: themePack(ThemePackId.midnight).light.themeRoles,
                swatches: const [
                  KitAccentSwatch(
                    id: 'teal',
                    color: Color(0xFF0D7377),
                    label: 'Teal',
                  ),
                ],
                selected: 'teal',
                onChanged: (_) {},
              ),
            ],
          ),
        ),
        style: KitSurfaceStyle.brutal,
      ),
    );
    await tester.pumpAndSettle();
    final ink = KitTokens.of(
      tester.element(find.text('Forest')),
    ).roles.text1.withValues(alpha: 1);
    expect(
      _decorations(
        tester,
        find.byKey(const ValueKey('theme-card')),
      ).any((d) => _edged(d, ink, KitStyleBrutal.edge)),
      isTrue,
      reason: 'theme card',
    );
    final match = [
      ..._decorations(
        tester,
        find.byKey(const ValueKey('appearance-accent-match')),
      ),
      ...tester
          .widgetList<Container>(
            find.descendant(
              of: find.byKey(const ValueKey('appearance-accent-match')),
              matching: find.byType(Container),
            ),
          )
          .map((c) => c.decoration)
          .whereType<Decoration>(),
    ];
    expect(
      match.any((d) => _edged(d, ink, KitStyleBrutal.edge)),
      isTrue,
      reason: 'Match theme',
    );
  });

  // Bug 3: the tiny step tiles each cast a hard shadow, which was busy.
  testWidgets('Brutal: a step tile casts no hard shadow', (tester) async {
    await tester.pumpWidget(
      _app(
        SizedBox(
          width: 360,
          child: KitStepTimeline(
            key: const ValueKey('timeline'),
            label: 'Read 3 files',
            icon: AppIconography.fileText,
            expanded: true,
            onPressed: () {},
            steps: [
              Builder(
                builder: (context) => KitStepTimeline.node(
                  context,
                  icon: AppIconography.fileText,
                  state: KitStepNodeState.done,
                  child: const SizedBox(
                    height: 32,
                    child: KitText('Read settings.dart'),
                  ),
                ),
              ),
            ],
          ),
        ),
        style: KitSurfaceStyle.brutal,
      ),
    );
    await tester.pumpAndSettle();
    final drawn = _decorations(tester, find.byKey(const ValueKey('timeline')));
    expect(drawn.whereType<KitSurfaceDecoration>(), isNotEmpty);
    expect(drawn.where(_casts), isEmpty);
  });

  // Bug 3: the full-width rule under the Appearance preview was a heavy
  // 2 dp black bar.
  testWidgets('Brutal: an edge-to-edge rule has the hairline weight', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const SizedBox(width: 300, child: KitDivider()),
        style: KitSurfaceStyle.brutal,
      ),
    );
    final line = find.byKey(const ValueKey('kit-divider-line'));
    expect(
      tester.getSize(line).height,
      KitTokens.hairlineWidth(tester.element(line)),
    );
  });

  // Bug 4: under the sticky tab row the panel was cut mid-line.
  testWidgets('a scrolled panel fades under the tab row, never cuts', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        SizedBox(
          width: 360,
          height: 400,
          child: KitTabSwitcher(
            index: 0,
            children: [
              ListView(
                children: [
                  for (var i = 0; i < 40; i++)
                    SizedBox(height: 40, child: KitText('Line $i')),
                ],
              ),
              const SizedBox(),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    // At rest the top is as it was: crisp, no mask.
    expect(tester.layers.whereType<ShaderMaskLayer>(), isEmpty);
    await tester.drag(find.text('Line 3'), const Offset(0, -100));
    await tester.pumpAndSettle();
    expect(tester.layers.whereType<ShaderMaskLayer>(), hasLength(1));
    // Back at the start, crisp again.
    await tester.drag(find.text('Line 3'), const Offset(0, 300));
    await tester.pumpAndSettle();
    expect(tester.layers.whereType<ShaderMaskLayer>(), isEmpty);
  });
}
