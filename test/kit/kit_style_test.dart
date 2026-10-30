// The design-schools baseline (docs/design/design-schools/contract.md): one
// spec per Style, the Fine-tune knobs and their storage, and the invariants
// every style keeps across every knob combination.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/app_preferences.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_brutal.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:shared_preferences/shared_preferences.dart';

double _contrast(Color a, Color b) {
  final la = a.computeLuminance();
  final lb = b.computeLuminance();
  final hi = la > lb ? la : lb;
  final lo = la > lb ? lb : la;
  return (hi + .05) / (lo + .05);
}

/// Every combination of the four knobs.
Iterable<KitKnobs> _allKnobs() sync* {
  for (final corners in KitCorners.values) {
    for (final depth in KitDepth.values) {
      for (final density in KitDensity.values) {
        for (final edges in KitEdges.values) {
          yield KitKnobs(
            corners: corners,
            depth: depth,
            density: density,
            edges: edges,
          );
        }
      }
    }
  }
}

const _solidPanel = BoxDecoration(
  color: Color(0xFF141518),
  borderRadius: BorderRadius.all(Radius.circular(18)),
);

Decoration _resolve(
  ThemeRoles roles,
  KitSurfaceStyle style, {
  KitSurfaceRole role = KitSurfaceRole.content,
  KitKnobs? knobs,
  bool selected = false,
  bool pressed = false,
}) => KitSurfaceLook.resolve(
  roles: roles,
  style: style,
  role: role,
  solid: _solidPanel,
  knobs: knobs,
  selected: selected,
  pressed: pressed,
);

/// A made-up style for the hooks' tests, standing in for Soft.
class _FakeSpec extends KitStyleSpec {
  const _FakeSpec();

  static const chosen = Color(0xFF00AA44);
  static const ground = Color(0xFF202020);

  @override
  KitStyleTokens get tokens => const KitStyleTokens(
    defaults: KitKnobs(),
    live: KitLivePolicy(maxStrength: .5),
    glow: KitStyleGlow(halo: false, lineWidth: 3),
    type: KitStyleType(bodyWeight: 500),
  );

  @override
  Decoration resolve(KitSurfaceRequest r) =>
      KitSurfaceDecoration(borderRadius: r.radius, fill: r.levelFill);

  @override
  Color? onColor(KitSurfaceRequest r) => r.selected ? chosen : null;

  @override
  ThemeRoles adjustRoles(ThemeRoles roles) => roles.copyWith(ground: ground);
}

Widget _host(KitSurfaceStyle style, Widget child, {TextDirection? dir}) =>
    MaterialApp(
      theme: ThemeData(extensions: [graphiteDark]),
      home: KitEffectsScope(
        effects: KitEffects(surfaceStyle: style),
        child: Directionality(
          textDirection: dir ?? TextDirection.ltr,
          child: Material(child: Center(child: child)),
        ),
      ),
    );

void main() {
  group('the style set', () {
    test('five styles: Glass and Crystal retired, Clay dropped, Signal '
        'added (2026-10-30)', () {
      expect(KitSurfaceStyle.values.map((s) => s.name), [
        'solid',
        'soft',
        'brutal',
        'paper',
        'signal',
      ]);
    });

    test('each style has its contract defaults', () {
      KitKnobs d(KitSurfaceStyle s) => kitStyleSpec(s).tokens.defaults;
      expect(d(KitSurfaceStyle.solid), const KitKnobs());
      expect(
        d(KitSurfaceStyle.soft),
        const KitKnobs(depth: KitDepth.raised, edges: KitEdges.none),
      );
      expect(
        d(KitSurfaceStyle.brutal),
        const KitKnobs(corners: KitCorners.soft, depth: KitDepth.raised),
      );
      expect(
        d(KitSurfaceStyle.paper),
        const KitKnobs(corners: KitCorners.sharp),
      );
      expect(kitStyleSpec(KitSurfaceStyle.paper).tokens.sharpRadius, 0);
      expect(
        d(KitSurfaceStyle.signal),
        const KitKnobs(corners: KitCorners.sharp),
      );
      expect(kitStyleSpec(KitSurfaceStyle.signal).tokens.sharpRadius, 4);
    });
  });

  group('Solid stays what it was', () {
    test('at its defaults every role is the part\'s own decoration', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        for (final role in KitSurfaceRole.values) {
          expect(
            identical(
              _resolve(
                roles,
                KitSurfaceStyle.solid,
                role: role,
                selected: true,
              ),
              _solidPanel,
            ),
            isTrue,
          );
        }
      }
    });

    test('tokens are the theme\'s own object at Round and Comfortable', () {
      final base = KitTokens.fromRoles(graphiteDark, ThemeData().textTheme);
      expect(identical(base.styled(const KitKnobs()), base), isTrue);
      expect(
        identical(
          base.styled(
            const KitKnobs(depth: KitDepth.raised, edges: KitEdges.none),
          ),
          base,
        ),
        isTrue,
      );
    });

    testWidgets('decorate and KitTokens.of change nothing by default', (
      tester,
    ) async {
      late Decoration decorated;
      late KitTokens tokens;
      late KitTokens themeTokens;
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData(extensions: [graphiteDark]),
          home: KitEffectsScope(
            effects: KitEffects.defaults,
            child: Builder(
              builder: (context) {
                decorated = KitSurfaceLook.decorate(
                  context,
                  solid: _solidPanel,
                );
                tokens = KitTokens.of(context);
                final theme = Theme.of(context);
                themeTokens =
                    theme.extension<KitTokens>() ?? KitTokens.fallback(theme);
                return const SizedBox();
              },
            ),
          ),
        ),
      );
      expect(identical(decorated, _solidPanel), isTrue);
      expect(tokens.panelCornerRadius, themeTokens.panelCornerRadius);
      expect(tokens.rowHeight, themeTokens.rowHeight);
    });

    test('Solid honours its knobs once they move', () {
      final raised =
          _resolve(
                graphiteDark,
                KitSurfaceStyle.solid,
                knobs: const KitKnobs(depth: KitDepth.raised),
              )
              as BoxDecoration;
      expect(raised.boxShadow, hasLength(1));
      final bordered = _solidPanel.copyWith(
        border: Border.all(color: graphiteDark.hairline),
      );
      final stripped =
          KitSurfaceLook.resolve(
                roles: graphiteDark,
                style: KitSurfaceStyle.solid,
                role: KitSurfaceRole.content,
                solid: bordered,
                knobs: const KitKnobs(edges: KitEdges.none),
              )
              as BoxDecoration;
      expect(stripped.border, isNull);
    });
  });

  group('knobs', () {
    test('null choices take the style\'s defaults', () {
      expect(
        KitStyles.knobsFor(style: KitSurfaceStyle.brutal),
        const KitKnobs(corners: KitCorners.soft, depth: KitDepth.raised),
      );
      expect(
        KitStyles.knobsFor(
          style: KitSurfaceStyle.brutal,
          fineTune: const KitFineTune(corners: KitCorners.sharp),
        ).corners,
        KitCorners.sharp,
      );
    });

    test('Soft without depth keeps an edge', () {
      final knobs = KitStyles.knobsFor(
        style: KitSurfaceStyle.soft,
        fineTune: const KitFineTune(depth: KitDepth.flat),
      );
      expect(knobs.edges, KitEdges.hairline);
    });

    test('high contrast: Soft goes Solid; Brutal, Paper and Signal stay', () {
      for (final style in KitSurfaceStyle.values) {
        final effective = KitStyles.effectiveStyle(style, highContrast: true);
        final keeps =
            style == KitSurfaceStyle.brutal ||
            style == KitSurfaceStyle.paper ||
            style == KitSurfaceStyle.signal;
        expect(effective, keeps ? style : KitSurfaceStyle.solid);
        final knobs = KitStyles.knobsFor(style: style, highContrast: true);
        expect(knobs.depth, KitDepth.flat);
        expect(knobs.edges, KitEdges.hairline);
      }
    });

    test('changing the style resets Fine-tune; Saver resets it too', () {
      const tuned = KitEffects(
        surfaceStyle: KitSurfaceStyle.brutal,
        fineTune: KitFineTune(corners: KitCorners.round),
      );
      expect(
        tuned.withSurfaceStyle(KitSurfaceStyle.paper).fineTune.isNone,
        isTrue,
      );
      expect(tuned.underSaver.fineTune.isNone, isTrue);
      expect(tuned.underSaver.surfaceStyle, KitSurfaceStyle.solid);
    });

    test('corners: Soft takes about 60 %, Sharp the style\'s sharp radius', () {
      expect(KitStyles.corner(18, KitCorners.round), 18);
      expect(KitStyles.corner(18, KitCorners.soft), 11);
      expect(KitStyles.corner(18, KitCorners.sharp), 2);
      expect(KitStyles.corner(18, KitCorners.sharp, sharp: 0), 0);
      expect(KitStyles.corner(9999, KitCorners.soft), 9999);
    });

    test('compact density never takes a row below the touch target', () {
      final base = KitTokens.fromRoles(graphiteLight, ThemeData().textTheme);
      final compact = base.styled(const KitKnobs(density: KitDensity.compact));
      expect(compact.rowHeight, 48);
      expect(compact.rowHeightTwoLine, 56);
      expect(compact.sectionGap, 16);
      expect(compact.buttonHeight, greaterThanOrEqualTo(compact.minTarget));
      final sharp = base.styled(
        const KitKnobs(corners: KitCorners.sharp),
        sharpRadius: 0,
      );
      expect(sharp.panelCornerRadius, 0);
      expect(sharp.cardRadius, 0);
      expect(
        identical(
          base.styled(const KitKnobs(density: KitDensity.compact)),
          compact,
        ),
        isTrue,
      );
    });
  });

  group('invariants for every style × brightness × knobs', () {
    for (final style in KitSurfaceStyle.values) {
      for (final roles in [graphiteDark, graphiteLight]) {
        final mode = roles.isDark ? 'dark' : 'light';
        test('${style.name} $mode', () {
          for (final knobs in _allKnobs()) {
            for (final role in KitSurfaceRole.values) {
              for (final down in [false, true]) {
                final d = _resolve(
                  roles,
                  style,
                  role: role,
                  knobs: knobs,
                  selected: down,
                );
                if (d is! KitSurfaceDecoration) continue;
                final why = '${style.name} $mode ${role.name} $knobs $down';
                // Shadows only on uniform corners (the engine's fast path).
                if (d.hardOffset > 0 ||
                    d.shadows.isNotEmpty ||
                    d.distance > 0) {
                  final r = d.borderRadius;
                  expect(
                    r.topLeft == r.topRight &&
                        r.topLeft == r.bottomLeft &&
                        r.topLeft == r.bottomRight,
                    isTrue,
                    reason: why,
                  );
                }
                // Words keep AA on opaque styles' fills (a line and a
                // switch's track carry none).
                if ((style == KitSurfaceStyle.brutal ||
                        style == KitSurfaceStyle.paper) &&
                    role != KitSurfaceRole.divider &&
                    role != KitSurfaceRole.toggle) {
                  final seen = Color.alphaBlend(d.fill, roles.ground);
                  // Paper's one exception (its inverted selection): where
                  // its spec gives the words their own colour (hook H3,
                  // read through KitSurfaceLook.wordsOn), those words are
                  // the ones that must keep AA on the fill.
                  final words = style == KitSurfaceStyle.paper
                      ? kitStyleSpec(style).onColor(
                          KitSurfaceRequest(
                            roles: roles,
                            role: role,
                            solid: _solidPanel,
                            knobs: knobs,
                            selected: down,
                          ),
                        )
                      : null;
                  for (final ink
                      in words != null
                          ? [words]
                          : [roles.text1, roles.text2, roles.text3]) {
                    expect(
                      _contrast(ink, seen),
                      greaterThanOrEqualTo(4.5),
                      reason: why,
                    );
                  }
                }
                // Nothing but the needs-you parts paints amber.
                expect(d.fill, isNot(roles.attention), reason: why);
                expect(d.hardShadow, isNot(roles.attention), reason: why);
              }
            }
          }
        });
      }
    }
  });

  group('Brutal and Paper placeholders', () {
    test('Brutal: a 2 dp ink edge and a hard shadow that sits down when '
        'chosen or flat', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final rest =
            _resolve(roles, KitSurfaceStyle.brutal) as KitSurfaceDecoration;
        expect(rest.edgeWidth, KitStyleBrutal.edge);
        expect(rest.edgeStart, roles.text1);
        expect(rest.hardOffset, 4);
        expect(
          _contrast(rest.edgeStart, roles.ground),
          greaterThanOrEqualTo(3),
        );
        final chosen =
            _resolve(roles, KitSurfaceStyle.brutal, selected: true)
                as KitSurfaceDecoration;
        expect(chosen.hardOffset, 0);
        final flat =
            _resolve(
                  roles,
                  KitSurfaceStyle.brutal,
                  knobs: const KitKnobs(depth: KitDepth.flat),
                )
                as KitSurfaceDecoration;
        expect(flat.hardOffset, 0);
        final rimless =
            _resolve(
                  roles,
                  KitSurfaceStyle.brutal,
                  role: KitSurfaceRole.control,
                  knobs: const KitKnobs(edges: KitEdges.none),
                )
                as KitSurfaceDecoration;
        expect(rimless.edgeWidth, KitStyleBrutal.edge, reason: 'fields stay');
      }
    });

    test('Paper: no shadows, ink boxes on what you act on', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        for (final role in KitSurfaceRole.values) {
          final d =
              _resolve(roles, KitSurfaceStyle.paper, role: role)
                  as KitSurfaceDecoration;
          expect(d.shadows, isEmpty);
          expect(d.hardOffset, 0);
          expect(d.distance, 0);
          if (const {
            KitSurfaceRole.control,
            KitSurfaceRole.track,
            KitSurfaceRole.floating,
            KitSurfaceRole.sheet,
            KitSurfaceRole.field,
            KitSurfaceRole.toggle,
          }.contains(role)) {
            expect(
              _contrast(Color.alphaBlend(d.edgeStart, d.fill), d.fill),
              greaterThanOrEqualTo(3),
              reason: role.name,
            );
          }
        }
      }
    });

    testWidgets('the hard shadow falls towards the end, in both directions', (
      tester,
    ) async {
      final d =
          _resolve(graphiteLight, KitSurfaceStyle.brutal)
              as KitSurfaceDecoration;
      Future<double> shadowLeft(TextDirection direction) async {
        final key = GlobalKey();
        await tester.pumpWidget(
          Directionality(
            textDirection: direction,
            child: Center(
              child: DecoratedBox(
                key: key,
                decoration: d,
                child: const SizedBox(width: 100, height: 40),
              ),
            ),
          ),
        );
        double? left;
        expect(
          tester.renderObject(find.byKey(key)),
          paints..something((method, args) {
            if (method != #drawRRect) return false;
            final paint = args[1] as Paint;
            if (paint.color.toARGB32() != d.hardShadow.toARGB32()) {
              return false;
            }
            left = (args[0] as RRect).left;
            return true;
          }),
        );
        // The painter's canvas is the box's own: its left edge is 0.
        return left!;
      }

      expect(await shadowLeft(TextDirection.ltr), d.hardOffset);
      expect(await shadowLeft(TextDirection.rtl), -d.hardOffset);
    });
  });

  group('storage', () {
    test('Fine-tune round-trips and a new style clears it', () async {
      SharedPreferences.setMockInitialValues({
        'oc.effectsSurfaceStyle': 'brutal',
        'oc.effectsCorners': 'sharp',
        'oc.effectsDensity': 'compact',
        'oc.effectsEdges': 'unknown',
      });
      final prefs = await SharedPreferences.getInstance();
      final store = AppPreferencesStore.forPreferences(prefs);
      final effects = store.value.effects;
      expect(effects.surfaceStyle, KitSurfaceStyle.brutal);
      expect(effects.fineTune.corners, KitCorners.sharp);
      expect(effects.fineTune.density, KitDensity.compact);
      expect(effects.fineTune.depth, isNull);
      expect(effects.fineTune.edges, isNull, reason: 'unknown is default');

      await store.setEffects(
        effects.copyWith(
          fineTune: const KitFineTune(
            corners: KitCorners.sharp,
            depth: KitDepth.flat,
          ),
        ),
      );
      expect(prefs.getString('oc.effectsDepth'), 'flat');
      expect(prefs.containsKey('oc.effectsDensity'), isFalse);

      // Even a caller that forgets withSurfaceStyle starts the new style at
      // its own defaults.
      await store.setEffects(
        store.value.effects.copyWith(surfaceStyle: KitSurfaceStyle.paper),
      );
      expect(prefs.getString('oc.effectsSurfaceStyle'), 'paper');
      for (final key in [
        'oc.effectsCorners',
        'oc.effectsDepth',
        'oc.effectsDensity',
        'oc.effectsEdges',
      ]) {
        expect(prefs.containsKey(key), isFalse, reason: key);
      }
      expect(store.value.effects.fineTune.isNone, isTrue);
    });
  });

  group('hooks (each a no-op for Solid)', () {
    setUp(
      () =>
          debugKitStyleSpecs = (style) =>
              style == KitSurfaceStyle.soft ? const _FakeSpec() : null,
    );
    tearDown(() => debugKitStyleSpecs = null);

    testWidgets('H1: type tweaks reach KitText; Solid keeps its weights', (
      tester,
    ) async {
      late TextStyle styled;
      late TextStyle plain;
      await tester.pumpWidget(
        _host(
          KitSurfaceStyle.soft,
          Builder(
            builder: (context) {
              styled = KitText.styleOf(context, KitTextRole.body);
              return const SizedBox();
            },
          ),
        ),
      );
      await tester.pumpWidget(
        _host(
          KitSurfaceStyle.solid,
          Builder(
            builder: (context) {
              plain = KitText.styleOf(context, KitTextRole.body);
              return const SizedBox();
            },
          ),
        ),
      );
      expect(plain.fontWeight, FontWeight.w400);
      expect(styled.fontWeight, FontWeight.w500);
      final brutal = kitStyleSpec(KitSurfaceStyle.brutal).tokens.type;
      final title = KitText.applyType(
        KitTextRole.largeTitle,
        KitText.styleFor(KitTextRole.largeTitle),
        brutal,
      );
      expect(title.fontWeight, const FontWeight(800));
      final label = KitText.applyType(
        KitTextRole.label,
        KitText.styleFor(KitTextRole.label),
        brutal,
      );
      expect(label.fontFamily, 'AppMono');
      final arabic = KitText.applyType(
        KitTextRole.label,
        KitText.styleFor(KitTextRole.label),
        brutal,
        arabic: true,
      );
      expect(arabic.fontFamily, isNot('AppMono'));
      final base = KitTokens.fromRoles(graphiteDark, ThemeData().textTheme);
      final tokens = base.styled(const KitKnobs(), type: brutal);
      expect(tokens.cardTitle.fontWeight, const FontWeight(800));
    });

    testWidgets('H1 monoLabels: section labels and captions, never segments', (
      tester,
    ) async {
      for (final style in [KitSurfaceStyle.brutal, KitSurfaceStyle.paper]) {
        await tester.pumpWidget(
          _host(
            style,
            Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const KitSectionLabel('Connection'),
                const KitText(
                  'Needs you · 40 s ago',
                  role: KitTextRole.caption,
                ),
                KitSegmented<int>(
                  segments: const [
                    KitSegment(value: 0, label: 'Light'),
                    KitSegment(value: 1, label: 'Dark'),
                  ],
                  selected: 0,
                  onChanged: (_) {},
                  semanticsLabel: 'Theme',
                ),
              ],
            ),
          ),
        );
        String? family(String words) {
          final text = tester.widget<Text>(find.text(words));
          return text.style?.fontFamily;
        }

        expect(family('Connection'), 'AppMono', reason: '$style section label');
        expect(family('Needs you · 40 s ago'), 'AppMono', reason: '$style');
        expect(family('Light'), isNot('AppMono'), reason: '$style segment');
        expect(family('Dark'), isNot('AppMono'), reason: '$style segment');
      }
    });

    testWidgets('H2: a pressed face sinks towards the end, hit box fixed', (
      tester,
    ) async {
      Future<Offset> shiftIn(TextDirection dir) async {
        await tester.pumpWidget(
          _host(
            KitSurfaceStyle.brutal,
            kitSurfaceBox(
              decoration: const KitSurfaceDecoration(
                fill: Color(0xFFFFFFFF),
                faceShift: 4,
              ),
              child: const SizedBox(
                key: ValueKey('face'),
                width: 40,
                height: 20,
              ),
            ),
            dir: dir,
          ),
        );
        final transform = tester.widget<Transform>(
          find
              .ancestor(
                of: find.byKey(const ValueKey('face')),
                matching: find.byType(Transform),
              )
              .first,
        );
        expect(transform.transformHitTests, isFalse);
        final t = transform.transform.getTranslation();
        return Offset(t.x, t.y);
      }

      expect(await shiftIn(TextDirection.ltr), const Offset(4, 4));
      expect(await shiftIn(TextDirection.rtl), const Offset(-4, 4));
      final pressed =
          _resolve(graphiteLight, KitSurfaceStyle.brutal, pressed: true)
              as KitSurfaceDecoration;
      expect(
        pressed.faceShift,
        KitStyleBrutal.offsetOf(KitSurfaceRole.content),
      );
      expect(pressed.hardOffset, 0);
    });

    testWidgets('H3: a style may colour the words on a chosen surface', (
      tester,
    ) async {
      final seen = <KitSurfaceStyle, (Color, Color)>{};
      for (final style in [KitSurfaceStyle.solid, KitSurfaceStyle.soft]) {
        await tester.pumpWidget(
          _host(
            style,
            Builder(
              builder: (context) {
                seen[style] = (
                  KitSurfaceLook.wordsOn(
                    context,
                    fallback: graphiteDark.onAccent,
                    selected: true,
                  ),
                  KitSurfaceLook.wordsOn(context, fallback: graphiteDark.text1),
                );
                return const SizedBox();
              },
            ),
          ),
        );
      }
      expect(seen[KitSurfaceStyle.solid], (
        graphiteDark.onAccent,
        graphiteDark.text1,
      ));
      expect(seen[KitSurfaceStyle.soft], (
        _FakeSpec.chosen,
        graphiteDark.text1,
      ));
    });

    test('H4: pills follow Corners › Sharp; stadiums otherwise', () {
      final base = KitTokens.fromRoles(graphiteDark, ThemeData().textTheme);
      expect(base.shapeOf(KitShape.pill), isA<StadiumBorder>());
      expect(
        base.styled(const KitKnobs(corners: KitCorners.soft)).pillRadius,
        double.infinity,
      );
      final sharp = base.styled(
        const KitKnobs(corners: KitCorners.sharp),
        sharpRadius: 0,
      );
      expect(sharp.pillRadius, 0);
      expect(sharp.shapeOf(KitShape.pill), isA<RoundedRectangleBorder>());
      expect(sharp.pillBorder(side: const BorderSide(width: 2)).side.width, 2);
    });

    test('H5: the static shape tokens follow Corners and Density', () {
      final base = KitTokens.fromRoles(graphiteDark, ThemeData().textTheme);
      expect(base.fieldCorner, KitTokens.fieldRadius);
      expect(base.popoverCorner, KitTokens.popoverRadius);
      expect(base.bubbleCorner, KitTokens.bubbleRadius);
      expect(base.pillHeight, KitTokens.chipHeight);
      final soft = base.styled(const KitKnobs(corners: KitCorners.soft));
      expect(soft.fieldCorner, 8);
      expect(soft.bubbleCorner, 12);
      expect(soft.bubbleTailCorner, 4);
      final compact = base.styled(const KitKnobs(density: KitDensity.compact));
      expect(compact.pillHeight, 28);
      expect(compact.panelInner, 12);
    });

    test('H6: a style may move the ground; Solid keeps the theme itself', () {
      final theme = ThemeData(
        extensions: [
          graphiteDark,
          KitTokens.fromRoles(graphiteDark, ThemeData().textTheme),
        ],
      );
      expect(
        identical(kitStyleRestyled(theme, KitSurfaceStyle.solid), theme),
        isTrue,
      );
      final moved = kitStyleRestyled(theme, KitSurfaceStyle.soft);
      expect(moved.extension<ThemeRoles>()!.ground, _FakeSpec.ground);
      expect(moved.extension<KitTokens>()!.roles.ground, _FakeSpec.ground);
      expect(moved.scaffoldBackgroundColor, _FakeSpec.ground);
      expect(
        identical(kitStyleRestyled(theme, KitSurfaceStyle.soft), moved),
        isTrue,
        reason: 'memoised',
      );
    });

    test('H7: live background policy and choosing Paper', () {
      expect(const KitLivePolicy().cap(1.6), 1.6);
      expect(const KitLivePolicy(maxStrength: .7).cap(1.6), .7);
      expect(const KitLivePolicy(maxStrength: .7).cap(.5), .5);
      const effects = KitEffects(liveBackground: true);
      expect(
        KitStyles.choose(effects, KitSurfaceStyle.paper).liveBackground,
        isFalse,
      );
      expect(
        KitStyles.choose(effects, KitSurfaceStyle.brutal).liveBackground,
        isTrue,
      );
      expect(
        KitStyles.choose(
          const KitEffects(
            surfaceStyle: KitSurfaceStyle.paper,
            liveBackground: true,
          ),
          KitSurfaceStyle.paper,
        ).liveBackground,
        isTrue,
        reason: 'choosing the same style again changes nothing',
      );
    });

    test('H8: Brutal draws a hard ring, Paper a rule, Solid its own glow', () {
      KitStyleGlow g(KitSurfaceStyle s) => kitStyleSpec(s).tokens.glow;
      expect(g(KitSurfaceStyle.solid).halo, isTrue);
      expect(g(KitSurfaceStyle.solid).lineWidth, isNull);
      expect(g(KitSurfaceStyle.brutal).halo, isFalse);
      expect(g(KitSurfaceStyle.brutal).lineWidth, 2);
      expect(g(KitSurfaceStyle.paper).lineWidth, 1);
    });

    testWidgets('H9: only the outer surface casts under Brutal', (
      tester,
    ) async {
      final cast =
          _resolve(graphiteLight, KitSurfaceStyle.brutal)
              as KitSurfaceDecoration;
      expect(cast.casts, isTrue);
      await tester.pumpWidget(
        _host(
          KitSurfaceStyle.brutal,
          kitSurfaceBox(
            style: KitSurfaceStyle.brutal,
            decoration: cast,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: kitSurfaceBox(
                style: KitSurfaceStyle.brutal,
                decoration: cast,
                child: const SizedBox(width: 40, height: 20),
              ),
            ),
          ),
        ),
      );
      final painted = tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .map((b) => b.decoration)
          .whereType<KitSurfaceDecoration>()
          .toList();
      expect(painted, hasLength(2));
      expect(painted.first.casts, isTrue);
      expect(painted.last.casts, isFalse);
      expect(painted.last.edgeWidth, KitStyleBrutal.edge);
    });
  });

  testWidgets('H12: the calm needs-you card keeps its own shadow; only its '
      'attention edge is clipped (Solid clips the whole card, as before)', (
    tester,
  ) async {
    Future<(bool cardClipped, bool edgeClipped)> frameIn(
      KitSurfaceStyle style,
    ) async {
      await tester.pumpWidget(
        _host(
          style,
          SizedBox(
            width: 300,
            child: Builder(
              builder: (context) => kitRequestFrame(
                context,
                key: const ValueKey('card'),
                attention: true,
                edge: true,
                child: const SizedBox(height: 80),
              ),
            ),
          ),
        ),
      );
      final card = find.byKey(const ValueKey('card'));
      final edge = find.byWidgetPredicate(
        (w) => w is ColoredBox && w.color == graphiteDark.attention,
      );
      bool clipped(Finder f) => find
          .ancestor(of: f, matching: find.byType(ClipRRect))
          .evaluate()
          .isNotEmpty;
      return (clipped(card), clipped(edge));
    }

    expect(await frameIn(KitSurfaceStyle.solid), (true, true));
    var casting = 0;
    for (final style in [KitSurfaceStyle.soft, KitSurfaceStyle.brutal]) {
      final (card, edge) = await frameIn(style);
      final face = tester
          .widget<DecoratedBox>(find.byKey(const ValueKey('card')))
          .decoration;
      if (face is KitSurfaceDecoration && face.casts) {
        casting++;
        expect(card, isFalse, reason: style.name);
        // A style without a start edge (Brutal's pill) has none to clip.
        final edged = find
            .byWidgetPredicate(
              (w) => w is ColoredBox && w.color == graphiteDark.attention,
            )
            .evaluate()
            .isNotEmpty;
        if (edged) expect(edge, isTrue, reason: style.name);
      }
    }
    expect(casting, greaterThanOrEqualTo(2), reason: 'Soft and Brutal cast');
  });

  group('S0: the parts\' own roles and the shared rules', () {
    // Every theme the app ships (but the device's own colours), each under
    // a style's own ground (hook H6), so a school's later tuning stays
    // inside these rules.
    final themes = <String, ThemeRoles>{
      for (final b in Brightness.values)
        'graphite ${b.name}': KitTokens.fallback(
          b == Brightness.dark ? AppTheme.dark() : AppTheme.light(),
        ).roles,
      for (final id in ThemePackId.values)
        if (id != ThemePackId.dynamic && id != ThemePackId.opencode)
          for (final b in Brightness.values)
            '${id.name} ${b.name}': KitTokens.fallback(
              b == Brightness.dark
                  ? AppTheme.dark(themePack(id))
                  : AppTheme.light(themePack(id)),
            ).roles,
    };
    final styled = [
      for (final style in KitSurfaceStyle.values)
        if (style != KitSurfaceStyle.solid) style,
    ];

    test('the page is its fill alone and never casts, in every style, '
        'brightness and knob', () {
      for (final style in KitSurfaceStyle.values) {
        for (final base in [graphiteDark, graphiteLight]) {
          final roles = kitStyleSpec(style).adjustRoles(base);
          for (final knobs in _allKnobs()) {
            final d = KitSurfaceLook.resolve(
              roles: roles,
              style: style,
              role: KitSurfaceRole.ground,
              solid: ShapeDecoration(
                color: roles.ground,
                shape: const RoundedRectangleBorder(),
              ),
              level: KitSurfaceLevel.ground,
              knobs: knobs,
            );
            final why = '${style.name} ${roles.brightness.name} $knobs';
            if (d is! KitSurfaceDecoration) {
              expect(style, KitSurfaceStyle.solid, reason: why);
              expect(
                (d as ShapeDecoration).shadows ?? const [],
                isEmpty,
                reason: why,
              );
              continue;
            }
            expect(d.casts, isFalse, reason: why);
            expect(d.edgeWidth, 0, reason: why);
            expect(d.hosts, isFalse, reason: why);
            expect(d.fill, roles.ground, reason: why);
          }
        }
      }
    });

    test('a field\'s boundary, and an off switch\'s, reads at 3:1 at rest '
        'in every style but Solid, every theme and every knob', () {
      final fails = <String>[];
      for (final style in styled) {
        for (final MapEntry(key: name, value: base) in themes.entries) {
          final roles = kitStyleSpec(style).adjustRoles(base);
          for (final knobs in _allKnobs()) {
            for (final role in [KitSurfaceRole.field, KitSurfaceRole.toggle]) {
              final d =
                  KitSurfaceLook.resolve(
                        roles: roles,
                        style: style,
                        role: role,
                        solid: _solidPanel,
                        knobs: knobs,
                      )
                      as KitSurfaceDecoration;
              for (final behind in [
                roles.ground,
                roles.surface1,
                roles.surface2,
              ]) {
                final under = Color.alphaBlend(
                  behind,
                  roles.ground.withValues(alpha: 1),
                );
                final face = Color.alphaBlend(d.fill, under);
                for (final edge in [d.edgeStart, d.edgeEnd]) {
                  final seen = Color.alphaBlend(edge, face);
                  final outside = _contrast(seen, under);
                  final inside = _contrast(seen, face);
                  if (d.edgeWidth <= 0 || outside < 3 || inside < 3) {
                    fails.add(
                      '${style.name} $name ${role.name} $knobs: '
                      '${d.edgeWidth} dp, ${outside.toStringAsFixed(2)} out, '
                      '${inside.toStringAsFixed(2)} in',
                    );
                  }
                }
              }
            }
          }
        }
      }
      expect(fails, isEmpty, reason: fails.take(20).join('\n'));
    });

    test('code stays opaque in every style, brightness and knob', () {
      for (final style in KitSurfaceStyle.values) {
        for (final roles in [graphiteDark, graphiteLight]) {
          for (final knobs in _allKnobs()) {
            final d = KitSurfaceLook.resolve(
              roles: roles,
              style: style,
              role: KitSurfaceRole.code,
              solid: _solidPanel,
              color: roles.surface1.withValues(alpha: .4),
              knobs: knobs,
            );
            if (d is! KitSurfaceDecoration) continue;
            final why = '${style.name} ${roles.brightness.name} $knobs';
            expect(d.fill.a, 1, reason: why);
            expect(d.fillStart.a, 1, reason: why);
          }
        }
      }
    });

    test('a highlight is never a part\'s own fill and never casts', () {
      for (final style in styled) {
        for (final roles in [graphiteDark, graphiteLight]) {
          for (final pressed in [false, true]) {
            final d =
                _resolve(
                      roles,
                      style,
                      role: KitSurfaceRole.stateLayer,
                      pressed: pressed,
                    )
                    as KitSurfaceDecoration;
            final why = '${style.name} ${roles.brightness.name} $pressed';
            expect(d.casts, isFalse, reason: why);
            expect(d.fill.a, greaterThan(0), reason: why);
            expect(d.fill, isNot(roles.attention), reason: why);
          }
        }
      }
    });

    testWidgets('dividers: Brutal ink at the hairline, Paper a light rule, '
        'Soft none, Solid the part\'s hairline', (tester) async {
      final seen = <KitSurfaceStyle, ({Color color, double width})>{};
      for (final style in KitSurfaceStyle.values) {
        await tester.pumpWidget(
          _host(
            style,
            Builder(
              builder: (context) {
                seen[style] = KitSurfaceLook.divider(
                  context,
                  color: graphiteDark.hairline,
                  width: .5,
                );
                return const SizedBox();
              },
            ),
          ),
        );
      }
      final ink = graphiteDark.text1;
      expect(seen[KitSurfaceStyle.solid], (
        color: graphiteDark.hairline,
        width: .5,
      ));
      expect(seen[KitSurfaceStyle.soft]!.color.a, 0);
      // Owner, 2026-10-22: a 2 dp rule read as a heavy black bar.
      expect(seen[KitSurfaceStyle.brutal], (
        color: ink.withValues(alpha: .9),
        width: .5,
      ));
      expect(seen[KitSurfaceStyle.paper], (
        color: ink.withValues(alpha: .2),
        width: .5,
      ));
    });

    /// The [KitSurfaceDecoration]s painted, outermost first.
    List<KitSurfaceDecoration> painted(WidgetTester tester) => tester
        .widgetList<DecoratedBox>(find.byType(DecoratedBox))
        .map((b) => b.decoration)
        .whereType<KitSurfaceDecoration>()
        .toList();

    testWidgets('a page hosted on the ground keeps its surfaces\' shadows '
        '(Brutal Conversations, audit V1)', (tester) async {
      await tester.pumpWidget(
        _host(
          KitSurfaceStyle.brutal,
          const KitSurface(
            level: KitSurfaceLevel.ground,
            shape: KitShape.square,
            padding: KitSurfacePadding.none,
            clip: false,
            child: Padding(
              padding: EdgeInsets.all(24),
              child: KitSurface.panel(child: SizedBox(width: 80, height: 40)),
            ),
          ),
        ),
      );
      final [page, panel] = painted(tester);
      expect(page.casts, isFalse);
      expect(page.edgeWidth, 0);
      expect(panel.casts, isTrue, reason: 'the panel keeps its hard shadow');
      expect(panel.hardOffset, KitStyleBrutal.offsetOf(KitSurfaceRole.content));
    });

    testWidgets('a panel in a panel sits on the outer face in every style '
        'but Solid', (tester) async {
      for (final style in KitSurfaceStyle.values) {
        await tester.pumpWidget(
          _host(
            style,
            const KitSurface.panel(
              child: KitSurface.panel(
                child: SizedBox(key: ValueKey('inner'), width: 80, height: 40),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final surfaces = painted(tester);
        if (style == KitSurfaceStyle.solid) {
          expect(surfaces, isEmpty, reason: 'Solid paints through Material');
          final inner = tester
              .widgetList<Material>(
                find.ancestor(
                  of: find.byKey(const ValueKey('inner')),
                  matching: find.byType(Material),
                ),
              )
              .first;
          expect(inner.color, graphiteDark.surface1);
          continue;
        }
        final [outer, inner] = surfaces;
        // The outer panel is exactly what a lone panel is (Paper sets even
        // that on the page with no fill), and hosts what is in it.
        await tester.pumpWidget(
          _host(
            style,
            const KitSurface.panel(child: SizedBox(width: 80, height: 40)),
          ),
        );
        await tester.pumpAndSettle();
        expect(outer, painted(tester).single, reason: style.name);
        expect(outer.hosts, isTrue, reason: style.name);
        expect(inner.fill.a, 0, reason: style.name);
        expect(inner.fillStart.a, 0, reason: style.name);
        expect(inner.edgeWidth, 0, reason: style.name);
        expect(inner.casts, isFalse, reason: style.name);
      }
    });

    testWidgets('the question card\'s answers sit on the card (audit V3); '
        'a chosen nested surface keeps its face', (tester) async {
      for (final style in styled) {
        late Decoration chosen;
        await tester.pumpWidget(
          _host(
            style,
            SizedBox(
              width: 300,
              child: Builder(
                builder: (context) => kitRequestFrame(
                  context,
                  key: const ValueKey('card'),
                  attention: true,
                  edge: true,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const KitSurface.panel(
                        child: SizedBox(key: ValueKey('answers'), height: 40),
                      ),
                      Builder(
                        builder: (context) {
                          chosen = KitSurfaceLook.decorate(
                            context,
                            solid: _solidPanel,
                            selected: true,
                          );
                          return const SizedBox();
                        },
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        // The answers' own surface: the nearest styled box around them,
        // inside the card.
        final answers = tester
            .widgetList<DecoratedBox>(
              find.ancestor(
                of: find.byKey(const ValueKey('answers')),
                matching: find.byType(DecoratedBox),
              ),
            )
            .map((b) => b.decoration)
            .whereType<KitSurfaceDecoration>()
            .first;
        final card = tester
            .widget<DecoratedBox>(find.byKey(const ValueKey('card')))
            .decoration;
        expect(identical(answers, card), isFalse, reason: style.name);
        expect(answers.fill.a, 0, reason: style.name);
        expect(answers.edgeWidth, 0, reason: style.name);
        expect(chosen, isA<KitSurfaceDecoration>());
        final face = chosen as KitSurfaceDecoration;
        expect(face.casts, isFalse, reason: style.name);
        expect(face.fill.a, greaterThan(0), reason: style.name);
      }
    });

    testWidgets('Fine-tune reaches a solid surface; at its defaults Solid '
        'still paints through Material', (tester) async {
      Future<void> pump(KitFineTune fineTune) => tester.pumpWidget(
        MaterialApp(
          theme: ThemeData(extensions: [graphiteDark]),
          home: KitEffectsScope(
            effects: KitEffects(fineTune: fineTune),
            child: const Material(
              child: Center(
                child: KitSurface.panel(
                  child: SizedBox(key: ValueKey('in'), width: 80, height: 40),
                ),
              ),
            ),
          ),
        ),
      );
      Material material() => tester
          .widgetList<Material>(
            find.ancestor(
              of: find.byKey(const ValueKey('in')),
              matching: find.byType(Material),
            ),
          )
          .first;
      List<Decoration> boxes() => tester
          .widgetList<DecoratedBox>(
            find.ancestor(
              of: find.byKey(const ValueKey('in')),
              matching: find.byType(DecoratedBox),
            ),
          )
          .map((b) => b.decoration)
          .toList();

      await pump(KitFineTune.none);
      expect(material().color, graphiteDark.surface1);
      expect(
        boxes().every(
          (d) => d is BoxDecoration && d.color == null && d.boxShadow == null,
        ),
        isTrue,
      );

      await pump(const KitFineTune(depth: KitDepth.raised));
      await tester.pumpAndSettle();
      expect(material().color!.a, 0);
      final raised = boxes().whereType<ShapeDecoration>().single;
      expect(raised.color, graphiteDark.surface1);
      expect(raised.shadows, hasLength(1));
    });
  });
}
