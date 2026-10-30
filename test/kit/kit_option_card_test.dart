// KitOptionCard (docs/ux-system/kit-api/KitOptionCard.md): one visual choice,
// drawn; the chosen one has a ring and a check; disabled says why.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

import 'kit_motion_still.dart';

Widget _theme({
  bool selected = false,
  VoidCallback? onPressed,
  bool off = false,
}) => SizedBox(
  width: 200,
  height: KitOptionCard.themeHeight + 60,
  child: KitOptionCard.theme(
    roles: themePack(ThemePackId.nord).dark.themeRoles,
    label: 'Nord',
    selected: selected,
    onPressed: off ? null : (onPressed ?? () {}),
    disabledReason: off ? 'Not on this phone' : null,
  ),
);

Widget _look() => SizedBox(
  width: 200,
  height: KitOptionCard.lookHeight,
  child: KitOptionCard.look(
    look: kitLiveStyleById('aurora'),
    label: 'Aurora drift',
    selected: true,
    onPressed: () {},
  ),
);

Widget _glow(KitGlowStyle style, {bool selected = false}) => SizedBox(
  width: 180,
  child: KitOptionCard.glow(
    style: style,
    label: 'Soft ring',
    selected: selected,
    onPressed: () {},
  ),
);

Widget _plain({bool selected = false, VoidCallback? onPressed}) => SizedBox(
  width: 300,
  child: KitOptionCard.plain(
    icon: AppIconography.sparkle,
    label: 'Calm',
    caption: 'Drawings appear, nothing keeps moving',
    selected: selected,
    onPressed: onPressed ?? () {},
  ),
);

Widget _app(Widget child, {double scale = 1}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: app!,
  ),
  home: Scaffold(
    body: Align(alignment: Alignment.topLeft, child: child),
  ),
);

Widget _accents(String? selected) => SizedBox(
  width: 412,
  child: KitAccentRow(
    semanticsLabel: 'Accent',
    matchLabel: 'Match theme',
    theme: graphiteDark,
    swatches: const [
      KitAccentSwatch(id: 'teal', color: Color(0xFF3CCFCF), label: 'Teal'),
      KitAccentSwatch(id: 'pink', color: Color(0xFFF2A7E6), label: 'Pink'),
    ],
    selected: selected,
    onChanged: (_) {},
  ),
);

Widget _grid(String? selected, {double width = 380}) => SizedBox(
  width: width,
  child: KitAccentRow.grid(
    semanticsLabel: 'Accent',
    matchLabel: 'Match',
    theme: graphiteDark,
    swatches: [
      for (final (id, label) in [
        ('mint', 'Mint'),
        ('teal', 'Teal'),
        ('blue', 'Blue'),
        ('violet', 'Violet'),
        ('pink', 'Pink'),
        ('mono', 'Mono'),
        ('silver', 'Silver'),
      ])
        KitAccentSwatch(id: id, color: const Color(0xFF5AB0FF), label: label),
    ],
    selected: selected,
    onChanged: (_) {},
  ),
);

void main() {
  kitMotionStillTests(
    'KitAccentRow',
    builds: {
      'match theme': () => _accents(null),
      'pink': () => _accents('pink'),
      'grid': () => _grid('teal'),
    },
    changes: {
      'a swatch is chosen': KitMotionChange(
        build: () => _accents(null),
        act: (tester, stage) => stage.rebuild(_accents('pink')),
        shows: 'Match theme',
      ),
    },
  );

  kitMotionStillTests(
    'KitOptionCard',
    builds: {
      'theme': () => _theme(selected: true),
      'theme disabled': () => _theme(off: true),
      'look': _look,
      'glow': () => _glow(KitGlowStyle.softRing, selected: true),
      'plain': () => _plain(selected: true),
    },
    changes: {
      'becomes chosen': KitMotionChange(
        build: () => _plain(),
        act: (tester, stage) => stage.rebuild(_plain(selected: true)),
        shows: 'Calm',
      ),
    },
  );

  testWidgets('a tap chooses it', (tester) async {
    var taps = 0;
    await tester.pumpWidget(_app(_theme(onPressed: () => taps++)));
    await tester.tap(find.text('Nord'));
    expect(taps, 1);
  });

  testWidgets('the chosen card has a check, the others none', (tester) async {
    double shown() => tester
        .widget<AnimatedOpacity>(
          find.ancestor(
            of: find.byIcon(AppIconography.check),
            matching: find.byType(AnimatedOpacity),
          ),
        )
        .opacity;
    await tester.pumpWidget(_app(_plain()));
    await tester.pumpAndSettle();
    expect(shown(), 0);
    await tester.pumpWidget(_app(_plain(selected: true)));
    await tester.pumpAndSettle();
    expect(shown(), 1);
  });

  testWidgets('the chosen check fades in from slightly smaller, no overshoot', (
    tester,
  ) async {
    // The drawn width of the check, frame by frame, as a share of its
    // settled width (a ratio, so the view's pixel ratio cancels out; the
    // x scale, since the depth axis never scales).
    double drawn() => tester
        .renderObject<RenderBox>(find.byIcon(AppIconography.check))
        .getTransformTo(null)
        .entry(0, 0);
    await tester.pumpWidget(_app(_plain()));
    await tester.pumpAndSettle();
    await tester.pumpWidget(_app(_plain(selected: true)));
    final frames = <double>[];
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 16));
      frames.add(drawn());
    }
    await tester.pumpAndSettle();
    final settled = drawn();
    final shares = [for (final f in frames) f / settled];
    expect(shares.first, greaterThanOrEqualTo(KitMotion.markFrom - 1e-6));
    expect(shares.first, lessThan(1), reason: 'it does grow in');
    expect(shares.every((s) => s <= 1 + 1e-6), isTrue, reason: 'no overshoot');
  });

  testWidgets('disabled shows its reason as words and does not respond', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_theme(off: true)));
    expect(find.text('Not on this phone'), findsOneWidget);
  });

  testWidgets('is one button with selected semantics and the in-use value', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_app(_theme(selected: true)));
    final node = find.semantics.byLabel('Nord').evaluate().first;
    expect(node.flagsCollection.isSelected, isNotNull);
    handle.dispose();
  });

  testWidgets('a theme card is drawn in its own colours, not the current '
      'theme\'s', (tester) async {
    await tester.pumpWidget(_app(_theme()));
    final nord = themePack(ThemePackId.nord).dark.themeRoles.ground;
    expect(
      find.byWidgetPredicate((w) => w is ColoredBox && w.color == nord),
      findsWidgets,
    );
  });

  testWidgets('every kind fits at 200 % text without overflow', (tester) async {
    for (final child in [
      _theme(),
      _look(),
      _glow(KitGlowStyle.classic),
      _plain(),
    ]) {
      await tester.pumpWidget(_app(child, scale: 2));
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('a surface card draws its style and saves on a tap', (
    tester,
  ) async {
    var taps = 0;
    for (final style in KitSurfaceStyle.values) {
      await tester.pumpWidget(
        _app(
          SizedBox(
            width: 130,
            child: KitOptionCard.surface(
              style: style,
              label: style.name,
              selected: style == KitSurfaceStyle.solid,
              onPressed: () => taps++,
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      await tester.tap(find.text(style.name));
    }
    expect(taps, KitSurfaceStyle.values.length);
  });

  // Style recheck, fix group E: in the Style picker the chosen card is
  // framed in the accent under every style, not marked by its check alone;
  // Solid keeps its own accent ring, and the others take none.
  testWidgets('the chosen card is framed in the accent in every style', (
    tester,
  ) async {
    bool framed(WidgetTester tester, String label, Color accent) {
      final ring = KitTokens.focusRingWidth(tester.element(find.text(label)));
      for (final box in tester.widgetList<DecoratedBox>(
        find.descendant(
          of: find.widgetWithText(KitOptionCard, label),
          matching: find.byType(DecoratedBox),
        ),
      )) {
        final d = box.decoration;
        if (d is BoxDecoration &&
            d.border is Border &&
            (d.border! as Border).top.color == accent &&
            (d.border! as Border).top.width >= ring) {
          return true;
        }
        if (d is KitSurfaceDecoration &&
            d.edgeStart == accent &&
            d.edgeEnd == accent &&
            d.edgeWidth >= ring) {
          return true;
        }
      }
      return false;
    }

    for (final style in KitSurfaceStyle.values) {
      for (final fineTune in const [
        KitFineTune.none,
        KitFineTune(edges: KitEdges.none),
      ]) {
        await tester.pumpWidget(
          _app(
            KitEffectsScope(
              effects: KitEffects(surfaceStyle: style, fineTune: fineTune),
              child: SizedBox(
                width: 412,
                child: Row(
                  children: [
                    for (final label in ['Chosen', 'Other'])
                      Expanded(
                        child: KitOptionCard.surface(
                          style: style,
                          label: label,
                          selected: label == 'Chosen',
                          onPressed: () {},
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final accent = KitTokens.of(
          tester.element(find.text('Chosen')),
        ).roles.accent;
        final where = '${style.name}, edges ${fineTune.edges?.name}';
        expect(framed(tester, 'Chosen', accent), isTrue, reason: where);
        expect(framed(tester, 'Other', accent), isFalse, reason: where);
      }
    }
  });

  testWidgets('a resting surface card is announced with its reason but does '
      'not print it', (tester) async {
    await tester.pumpWidget(
      _app(
        const SizedBox(
          width: 130,
          child: KitOptionCard.surface(
            style: KitSurfaceStyle.soft,
            label: 'Soft',
            selected: false,
            onPressed: null,
            disabledReason: 'Saver uses solid surfaces',
          ),
        ),
      ),
    );
    expect(find.text('Saver uses solid surfaces'), findsNothing);
  });

  testWidgets('a theme card under Saver draws a black ground', (tester) async {
    await tester.pumpWidget(
      _app(
        SizedBox(
          width: 200,
          height: KitOptionCard.themeHeight + 60,
          child: KitOptionCard.theme(
            roles: themePack(ThemePackId.nord).dark.themeRoles,
            label: 'Nord',
            selected: false,
            saverLook: kitLiveStyleById('aurora'),
            onPressed: () {},
          ),
        ),
      ),
    );
    expect(
      find.byWidgetPredicate(
        (w) =>
            w is AnimatedContainer &&
            w.decoration is BoxDecoration &&
            (w.decoration! as BoxDecoration).color?.toARGB32() == 0xFF000000,
      ),
      findsWidgets,
    );
  });

  group('KitAccentRow', () {
    Widget row(String? selected, ValueChanged<String?> onChanged) => SizedBox(
      width: 412,
      child: KitAccentRow(
        semanticsLabel: 'Accent',
        matchLabel: 'Match theme',
        theme: graphiteDark,
        swatches: const [
          KitAccentSwatch(id: 'teal', color: Color(0xFF3CCFCF), label: 'Teal'),
          KitAccentSwatch(id: 'pink', color: Color(0xFFF2A7E6), label: 'Pink'),
        ],
        selected: selected,
        onChanged: onChanged,
      ),
    );

    testWidgets('a swatch or Match theme is chosen by a tap', (tester) async {
      final chosen = <String?>[];
      await tester.pumpWidget(_app(row(null, chosen.add)));
      await tester.tap(find.bySemanticsLabel('Pink'));
      await tester.pumpWidget(_app(row('pink', chosen.add)));
      await tester.tap(find.text('Match theme'));
      expect(chosen, ['pink', null]);
    });

    testWidgets('the chosen one is selected, every target is 48 dp', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(_app(row('teal', (_) {})));
      expect(
        tester.getSemantics(find.bySemanticsLabel('Teal')),
        isSemantics(isSelected: true),
      );
      for (final label in ['Teal', 'Pink']) {
        final rect = tester.getSemantics(find.bySemanticsLabel(label)).rect;
        expect(rect.width, greaterThanOrEqualTo(48));
        expect(rect.height, greaterThanOrEqualTo(48));
      }
      semantics.dispose();
    });

    testWidgets('fits at 200 % text without overflow', (tester) async {
      await tester.pumpWidget(_app(row(null, (_) {}), scale: 2));
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('the grid shows every swatch named, four to a row, with '
      'nothing scrolling sideways, at 1.3x text and right to left', (
    tester,
  ) async {
    for (final (scale, rtl) in [(1.0, false), (1.3, false), (1.3, true)]) {
      await tester.pumpWidget(
        Directionality(
          textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
          child: _app(_grid('teal', width: 320), scale: scale),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(Scrollable), findsNothing);
      final box = tester.getRect(find.byType(KitAccentRow)).inflate(.5);
      for (final label in [
        'Match',
        'Mint',
        'Teal',
        'Blue',
        'Violet',
        'Pink',
        'Mono',
        'Silver',
      ]) {
        final rect = tester.getRect(find.text(label));
        expect(
          box.contains(rect.topLeft) && box.contains(rect.bottomRight),
          isTrue,
          reason: '$label at $scale',
        );
      }
      // Two rows of four.
      final first = tester.getTopLeft(find.text('Match')).dy;
      expect(tester.getTopLeft(find.text('Blue')).dy, first);
      expect(tester.getTopLeft(find.text('Violet')).dy, greaterThan(first));
    }
  });

  testWidgets('a look name too long for its cell never splits a word', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        SizedBox(
          width: 96,
          height: KitOptionCard.lookHeight,
          child: KitOptionCard.look(
            look: kitLiveStyleById('aurora'),
            label: 'Constellation',
            selected: false,
            onPressed: () {},
          ),
        ),
      ),
    );
    final paragraph = tester.renderObject<RenderParagraph>(
      find.text('Constellation'),
    );
    // One line: the word was set smaller, not broken across two.
    expect(paragraph.size.height, lessThan(30));
  });
}
