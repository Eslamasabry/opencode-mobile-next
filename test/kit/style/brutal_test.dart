// Brutal (docs/design/design-schools/schools/brutal.md): its tokens, its
// cream paper ground, the hard shadow per role and knob, the chosen and
// pressed states, the right-to-left mirror and the fade to and from Solid.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_brutal.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

const _spec = KitStyleBrutal();
const _panel = BorderRadius.all(Radius.circular(12));
const _sheetTop = BorderRadius.vertical(top: Radius.circular(20));

KitSurfaceDecoration _look(
  ThemeRoles roles, {
  KitSurfaceRole role = KitSurfaceRole.content,
  KitSurfaceLevel level = KitSurfaceLevel.surface1,
  KitKnobs knobs = const KitKnobs(
    corners: KitCorners.soft,
    depth: KitDepth.raised,
  ),
  BorderRadius radius = _panel,
  Color? color,
  bool selected = false,
  bool pressed = false,
  bool disabled = false,
  bool withinGroup = false,
}) =>
    _spec.resolve(
          KitSurfaceRequest(
            roles: roles,
            role: role,
            solid: BoxDecoration(color: roles.surface1, borderRadius: radius),
            knobs: knobs,
            level: level,
            radius: radius,
            color: color,
            selected: selected,
            pressed: pressed,
            disabled: disabled,
            withinGroup: withinGroup,
          ),
        )
        as KitSurfaceDecoration;

/// Every static theme pack's roles, both brightnesses, and Graphite.
Iterable<(String, ThemeRoles)> _everyTheme() sync* {
  yield ('graphite/dark', graphiteDark);
  yield ('graphite/light', graphiteLight);
  for (final id in ThemePackId.values.where(
    (id) => id != ThemePackId.dynamic,
  )) {
    for (final brightness in Brightness.values) {
      yield (
        '${id.name}/${brightness.name}',
        themePack(id).palette(brightness).themeRoles,
      );
    }
  }
}

void main() {
  group('tokens', () {
    test('defaults: Soft corners, raised, comfortable, the ink edge on', () {
      final tokens = _spec.tokens;
      expect(
        tokens.defaults,
        const KitKnobs(
          corners: KitCorners.soft,
          depth: KitDepth.raised,
          density: KitDensity.comfortable,
          edges: KitEdges.hairline,
        ),
      );
      expect(tokens.keepsUnderHighContrast, isTrue);
      expect(tokens.flattensNested, isTrue);
      expect(tokens.live.cap(1), .7);
      expect(tokens.glow.halo, isFalse);
      expect(tokens.glow.lineWidth, 2);
      expect(tokens.type.displayWeight, 800);
      expect(tokens.type.monoLabels, isTrue);
      expect(kitStyleSpec(KitSurfaceStyle.brutal), isA<KitStyleBrutal>());
    });
  });

  group('the ground (hook H6)', () {
    test('light: cream paper ground, warm paper surfaces, words unchanged', () {
      final roles = _spec.adjustRoles(graphiteLight);
      final ground = roles.ground;
      // Warm: red over blue, unlike Graphite's near-neutral ground.
      expect((ground.r - ground.b) * 255, greaterThan(10));
      expect(ground.computeLuminance(), lessThan(1));
      expect(roles.surface1, roles.surface2);
      expect(
        roles.surface1.computeLuminance(),
        greaterThan(ground.computeLuminance()),
      );
      expect(roles.text1, graphiteLight.text1);
      expect(roles.text3, graphiteLight.text3);
      expect(roles.accent, graphiteLight.accent);
      expect(roles.attention, graphiteLight.attention);
    });

    test('dark: the near-black ground stays; cards take the second step', () {
      final roles = _spec.adjustRoles(graphiteDark);
      expect(roles.ground, graphiteDark.ground);
      expect(roles.surface1, graphiteDark.surface2);
      expect(roles.text3, graphiteDark.text3);
    });

    test('every theme keeps its words at AA on every adjusted step', () {
      for (final (name, base) in _everyTheme()) {
        final roles = _spec.adjustRoles(base).withAccent(base.accent);
        for (final fill in [
          roles.ground,
          roles.surface1,
          roles.surface2,
          roles.surface3,
        ]) {
          for (final ink in [
            roles.text1,
            roles.text2,
            roles.text3,
            roles.attention,
          ]) {
            // A theme already under AA on its own steps is not made worse.
            final before = [
              base.ground,
              base.surface1,
              base.surface2,
              base.surface3,
            ].map((f) => contrastRatio(ink, f)).reduce((a, b) => a < b ? a : b);
            expect(
              contrastRatio(ink, fill),
              greaterThanOrEqualTo(before < 4.5 ? before - .05 : 4.5),
              reason: name,
            );
          }
        }
        // The accent guard re-ran: accent words still read on the ground.
        expect(
          contrastRatio(roles.accent, roles.ground),
          greaterThanOrEqualTo(
            contrastRatio(base.accent, base.ground) < 4.5 ? 0 : 4.4,
          ),
          reason: name,
        );
      }
    });
  });

  group('surfaces', () {
    test('the page at the ground step is the paper itself: no edge, no '
        'shadow, so it never flattens what sits on it', () {
      final solid = BoxDecoration(color: graphiteLight.ground);
      final d = _spec.resolve(
        KitSurfaceRequest(
          roles: graphiteLight,
          role: KitSurfaceRole.content,
          solid: solid,
          knobs: _spec.tokens.defaults,
          level: KitSurfaceLevel.ground,
        ),
      );
      expect(d, same(solid));
    });

    test('the ink edge and the hard shadow, per role', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        // The panes; a part's own roles (the page, a line, a highlight)
        // are no ink box and never cast (style S0, kit_style_test.dart).
        for (final role in KitSurfaceRole.panes) {
          final d = _look(roles, role: role);
          expect(d.edgeWidth, KitStyleBrutal.edge, reason: role.name);
          expect(d.edgeStart, roles.text1, reason: role.name);
          expect(
            d.hardOffset,
            KitStyleBrutal.offsetOf(role),
            reason: role.name,
          );
          expect(d.shadows, isEmpty);
          expect(d.blur, 0);
        }
        expect(_look(roles).hardOffset, 4);
        expect(_look(roles, role: KitSurfaceRole.control).hardOffset, 3);
        expect(_look(roles, role: KitSurfaceRole.floating).hardOffset, 6);
        expect(_look(roles, role: KitSurfaceRole.track).hardOffset, 0);
      }
    });

    test('a part\'s own roles: fields, switches and code keep the ink box; '
        'the page, a line and a highlight are no box; none casts', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        for (final role in KitSurfaceRole.values) {
          if (KitSurfaceRole.panes.contains(role)) continue;
          final d = _look(roles, role: role);
          final boxed =
              role == KitSurfaceRole.field ||
              role == KitSurfaceRole.toggle ||
              role == KitSurfaceRole.code;
          expect(
            d.edgeWidth,
            boxed ? KitStyleBrutal.edge : 0,
            reason: role.name,
          );
          if (boxed) expect(d.edgeStart, roles.text1, reason: role.name);
          expect(d.casts, isFalse, reason: role.name);
          expect(KitStyleBrutal.offsetOf(role), 0, reason: role.name);
        }
      }
    });

    test('the shadow: ink in light, an ink-grey in dark that still shows', () {
      expect(_look(graphiteLight).hardShadow, graphiteLight.text1);
      final dark = _look(graphiteDark).hardShadow;
      expect(dark.a, 1);
      expect(dark, isNot(graphiteDark.text3));
      expect(contrastRatio(dark, graphiteDark.ground), greaterThan(2.5));
      expect(
        contrastRatio(dark, graphiteDark.ground),
        lessThan(contrastRatio(graphiteDark.text3, graphiteDark.ground)),
      );
    });

    test('a bottom sheet sits on the scrim; a dialog or menu casts', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final sheet = _look(
          roles,
          role: KitSurfaceRole.sheet,
          radius: _sheetTop,
        );
        expect(sheet.hardOffset, 0);
        expect(sheet.edgeWidth, KitStyleBrutal.edge);
        expect(_look(roles, role: KitSurfaceRole.sheet).hardOffset, 4);
      }
    });

    test('controls rest on paper; chosen they sit down on the tile step', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final rest = _look(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
        );
        expect(rest.fill, roles.surface2);
        final chosen = _look(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          selected: true,
        );
        expect(chosen.fill, roles.surface3);
        expect(chosen.hardOffset, 0);
        expect(chosen.faceShift, 0);
        expect(chosen.edgeWidth, KitStyleBrutal.edge);
        // A part's own fill is kept; the accent is the main action's face.
        final primary = _look(
          roles,
          role: KitSurfaceRole.control,
          color: roles.accent,
        );
        expect(primary.fill, KitStyleBrutal.mintOf(roles));
        expect(
          _look(
            roles,
            role: KitSurfaceRole.control,
            color: roles.attentionFill,
          ).fill,
          roles.attentionFill,
        );
        expect(primary.hardOffset, 3);
      }
    });

    test('pressed: the face sinks into its shadow (hook H2)', () {
      for (final role in [
        KitSurfaceRole.content,
        KitSurfaceRole.control,
        KitSurfaceRole.floating,
      ]) {
        final d = _look(graphiteLight, role: role, pressed: true);
        expect(d.faceShift, KitStyleBrutal.offsetOf(role), reason: role.name);
        expect(d.hardOffset, 0, reason: role.name);
      }
      final primary = _look(
        graphiteLight,
        role: KitSurfaceRole.control,
        color: graphiteLight.accent,
        pressed: true,
      );
      expect(primary.fill, KitStyleBrutal.mintOf(graphiteLight));
      expect(primary.faceShift, 3);
    });
  });

  group('polish', () {
    test('dark: a chosen card or tile trades its ink edge for the accent, '
        'at 3:1 on its fill and on the ground', () {
      for (final (name, raw) in _everyTheme()) {
        if (!raw.isDark) continue;
        final roles = _spec.adjustRoles(raw);
        for (final role in [KitSurfaceRole.content, KitSurfaceRole.control]) {
          final rest = _look(
            roles,
            role: role,
            level: KitSurfaceLevel.surface2,
          );
          expect(rest.edgeStart, roles.text1, reason: '$name $role');
          final chosen = _look(
            roles,
            role: role,
            level: KitSurfaceLevel.surface2,
            selected: true,
          );
          expect(chosen.edgeStart, roles.accent, reason: '$name $role');
          expect(chosen.edgeEnd, roles.accent, reason: '$name $role');
          expect(chosen.edgeWidth, KitStyleBrutal.edge, reason: '$name $role');
          for (final under in [chosen.fill, roles.ground]) {
            expect(
              contrastRatio(roles.accent, under),
              greaterThanOrEqualTo(3),
              reason: '$name $role',
            );
          }
          // The ring is the state: Edges › Off keeps it on content too.
          final off = _look(
            roles,
            role: role,
            level: KitSurfaceLevel.surface2,
            knobs: const KitKnobs(
              corners: KitCorners.soft,
              depth: KitDepth.raised,
              edges: KitEdges.none,
            ),
            selected: true,
          );
          expect(off.edgeStart, roles.accent, reason: '$name $role');
          expect(off.edgeWidth, KitStyleBrutal.edge, reason: '$name $role');
        }
        // A part's own fill (a chosen segment) shows the choice itself.
        final segment = _look(
          roles,
          role: KitSurfaceRole.control,
          color: roles.accent,
          selected: true,
        );
        expect(segment.edgeStart, roles.text1, reason: name);
      }
      // Light keeps the ink edge: the ink shadow it gives up says it.
      final light = _look(
        _spec.adjustRoles(graphiteLight),
        role: KitSurfaceRole.control,
        selected: true,
      );
      expect(light.edgeStart, graphiteLight.text1);
    });

    test('needs you: an amber pill, no wash, ring or start edge', () {
      const look = KitStyleBrutal();
      final decided = look.tokens.needsYou.decide(halo: false);
      expect(decided.pill, isTrue);
      expect(decided.tint, isFalse);
      expect(decided.ring, isFalse);
      expect(decided.line, isFalse);
      expect(decided.startEdge, isFalse);
      final question = look.tokens.needsYou.decide(halo: false, question: true);
      expect(question.pill, isTrue);
      expect(question.startEdge, isFalse);
      // The pill is the style's control in amber, in the ink box.
      for (final roles in [graphiteDark, graphiteLight]) {
        final pill = _look(
          roles,
          role: KitSurfaceRole.control,
          color: roles.attentionFill,
        );
        expect(pill.fill, roles.attentionFill);
        expect(pill.edgeStart, roles.text1);
        expect(pill.edgeWidth, KitStyleBrutal.edge);
      }
    });

    test('dividers: ink between groups, space within one', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final between = _look(roles, role: KitSurfaceRole.divider);
        expect(between.fill, roles.text1.withValues(alpha: .9));
        final within = _look(
          roles,
          role: KitSurfaceRole.divider,
          withinGroup: true,
        );
        expect(within.fill.a, 0);
      }
    });

    test('a focused field rings its ink box in 3 dp of accent', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final rest = _look(roles, role: KitSurfaceRole.field);
        expect(rest.shadows, isEmpty);
        final focused = _look(
          roles,
          role: KitSurfaceRole.field,
          selected: true,
        );
        expect(focused.edgeStart, roles.text1);
        expect(focused.edgeWidth, KitStyleBrutal.edge);
        expect(focused.shadows, hasLength(1));
        final ring = focused.shadows.single;
        expect(ring.color, roles.accent);
        expect(ring.spreadRadius, KitStyleBrutal.focusRing);
        expect(ring.blurRadius, 0);
        expect(ring.offset, Offset.zero);
        expect(focused.hardOffset, 0);
      }
    });

    test('the main action: bright mint with ink words in light, the '
        'dark accent as it is', () {
      final mint = KitStyleBrutal.mintOf(graphiteLight);
      expect(mint, isNot(graphiteLight.accent));
      expect(contrastRatio(graphiteLight.text1, mint), greaterThanOrEqualTo(7));
      // Near the guide's #3DDC8A: the accent's own hue, lifted.
      final hue = HSLColor.fromColor(graphiteLight.accent).hue;
      expect(HSLColor.fromColor(mint).hue, closeTo(hue, 2));
      expect(HSLColor.fromColor(mint).lightness, closeTo(.55, .05));
      KitSurfaceRequest words(ThemeRoles roles, Color? color) =>
          KitSurfaceRequest(
            roles: roles,
            role: KitSurfaceRole.control,
            solid: const BoxDecoration(),
            knobs: _spec.tokens.defaults,
            color: color,
          );
      expect(
        _spec.onColor(words(graphiteLight, graphiteLight.accent)),
        graphiteLight.text1,
      );
      expect(_spec.onColor(words(graphiteLight, null)), isNull);
      // A chosen segment or tab keeps the theme's accent and its words.
      final chosen = _look(
        graphiteLight,
        role: KitSurfaceRole.control,
        color: graphiteLight.accent,
        selected: true,
      );
      expect(chosen.fill, graphiteLight.accent);
      expect(
        _spec.onColor(
          KitSurfaceRequest(
            roles: graphiteLight,
            role: KitSurfaceRole.control,
            solid: const BoxDecoration(),
            knobs: _spec.tokens.defaults,
            color: graphiteLight.accent,
            selected: true,
          ),
        ),
        isNull,
      );
      expect(KitStyleBrutal.mintOf(graphiteDark), graphiteDark.accent);
      expect(_spec.onColor(words(graphiteDark, graphiteDark.accent)), isNull);
    });

    test('every theme: the words on the main action keep AA', () {
      for (final (name, raw) in _everyTheme()) {
        final roles = _spec.adjustRoles(raw);
        final face = KitStyleBrutal.mintOf(roles);
        final words =
            _spec.onColor(
              KitSurfaceRequest(
                roles: roles,
                role: KitSurfaceRole.control,
                solid: const BoxDecoration(),
                knobs: _spec.tokens.defaults,
                color: roles.accent,
              ),
            ) ??
            roles.onAccent;
        expect(
          contrastRatio(words, face),
          greaterThanOrEqualTo(4.5),
          reason: name,
        );
      }
    });

    test('a disabled control lies flat in a muted edge, never a secondary', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        for (final pressed in [false, true]) {
          final off = _look(
            roles,
            role: KitSurfaceRole.control,
            level: KitSurfaceLevel.surface3,
            disabled: true,
            pressed: pressed,
          );
          expect(off.fill, roles.surface3);
          expect(off.hardOffset, 0);
          expect(off.faceShift, 0);
          expect(off.edgeStart, roles.text3);
          expect(contrastRatio(roles.text3, off.fill), greaterThan(4.5));
        }
        // A part's own quiet fill (the waiting hero) is kept, flat.
        final quiet = roles.accent.withValues(alpha: .14);
        final hero = _look(
          roles,
          role: KitSurfaceRole.control,
          color: quiet,
          disabled: true,
        );
        expect(hero.fill, quiet);
        expect(hero.hardOffset, 0);
      }
    });

    test('a held primary keeps its colour: no layer raised, a faint ink '
        'flat', () {
      KitSurfaceDecoration layer(KitDepth depth) =>
          _spec.resolveFilledLayer(
                KitSurfaceRequest(
                  roles: graphiteLight,
                  role: KitSurfaceRole.stateLayer,
                  solid: const BoxDecoration(),
                  knobs: KitKnobs(corners: KitCorners.soft, depth: depth),
                  color: graphiteLight.accent,
                  pressed: true,
                ),
              )
              as KitSurfaceDecoration;
      expect(layer(KitDepth.raised).fill.a, 0);
      final flat = layer(KitDepth.flat).fill;
      expect(flat.a, closeTo(.12, .01));
      expect(
        contrastRatio(
          graphiteLight.text1,
          Color.alphaBlend(flat, KitStyleBrutal.mintOf(graphiteLight)),
        ),
        greaterThanOrEqualTo(4.5),
      );
    });
  });

  group('knobs', () {
    test('Depth › Flat: no shadow, no sinking; the press shows as a fill', () {
      const flat = KitKnobs(corners: KitCorners.soft, depth: KitDepth.flat);
      for (final role in KitSurfaceRole.panes) {
        final rest = _look(graphiteLight, role: role, knobs: flat);
        expect(rest.hardOffset, 0, reason: role.name);
        expect(rest.edgeWidth, KitStyleBrutal.edge, reason: role.name);
        final pressed = _look(
          graphiteLight,
          role: role,
          knobs: flat,
          pressed: true,
        );
        expect(pressed.faceShift, 0, reason: role.name);
        expect(pressed.fill, graphiteLight.surface3, reason: role.name);
      }
    });

    test('Edges › Off: content keeps only its shadow; controls and tracks '
        'keep the ink edge', () {
      const off = KitKnobs(
        corners: KitCorners.soft,
        depth: KitDepth.raised,
        edges: KitEdges.none,
      );
      final panel = _look(graphiteLight, knobs: off);
      expect(panel.edgeWidth, 0);
      expect(panel.hardOffset, 4);
      for (final role in [KitSurfaceRole.control, KitSurfaceRole.track]) {
        final d = _look(graphiteLight, role: role, knobs: off);
        expect(d.edgeWidth, KitStyleBrutal.edge, reason: role.name);
        expect(
          contrastRatio(d.edgeStart, d.fill),
          greaterThanOrEqualTo(3),
          reason: role.name,
        );
      }
    });

    test('Density › Compact: every shadow a step tighter', () {
      const compact = KitKnobs(
        corners: KitCorners.soft,
        depth: KitDepth.raised,
        density: KitDensity.compact,
      );
      for (final role in KitSurfaceRole.values) {
        final d = _look(graphiteLight, role: role, knobs: compact);
        expect(d.hardOffset, KitStyleBrutal.compactOffsetOf(role));
        expect(
          d.hardOffset,
          lessThanOrEqualTo(KitStyleBrutal.offsetOf(role)),
          reason: role.name,
        );
      }
      expect(_look(graphiteLight, knobs: compact).hardOffset, 3);
    });

    test('high contrast keeps Brutal, flat with its edges', () {
      expect(
        KitStyles.effectiveStyle(KitSurfaceStyle.brutal, highContrast: true),
        KitSurfaceStyle.brutal,
      );
      final knobs = KitStyles.knobsFor(
        style: KitSurfaceStyle.brutal,
        highContrast: true,
      );
      expect(knobs.depth, KitDepth.flat);
      expect(knobs.edges, KitEdges.hairline);
      final d = _look(graphiteLight, knobs: knobs);
      expect(d.hardOffset, 0);
      expect(
        contrastRatio(d.edgeStart, graphiteLight.ground),
        greaterThanOrEqualTo(3),
      );
    });
  });

  group('direction', () {
    testWidgets('the shadow and the pressed face fall towards the end', (
      tester,
    ) async {
      Future<(double, Offset)> paintIn(TextDirection direction) async {
        final rest = _look(graphiteLight);
        final pressed = _look(graphiteLight, pressed: true);
        final key = GlobalKey();
        await tester.pumpWidget(
          Directionality(
            textDirection: direction,
            child: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  DecoratedBox(
                    key: key,
                    decoration: rest,
                    child: const SizedBox(width: 100, height: 40),
                  ),
                  kitSurfaceBox(
                    style: KitSurfaceStyle.brutal,
                    decoration: pressed,
                    child: const SizedBox(
                      key: ValueKey('face'),
                      width: 100,
                      height: 40,
                    ),
                  ),
                ],
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
            if (paint.color.toARGB32() != rest.hardShadow.toARGB32()) {
              return false;
            }
            left = (args[0] as RRect).left;
            return true;
          }),
        );
        final transform = tester.widget<Transform>(
          find
              .ancestor(
                of: find.byKey(const ValueKey('face')),
                matching: find.byType(Transform),
              )
              .first,
        );
        final t = transform.transform.getTranslation();
        return (left!, Offset(t.x, t.y));
      }

      final ltr = await paintIn(TextDirection.ltr);
      expect(ltr.$1, 4);
      expect(ltr.$2, const Offset(4, 4));
      final rtl = await paintIn(TextDirection.rtl);
      expect(rtl.$1, -4);
      expect(rtl.$2, const Offset(-4, 4));
    });
  });

  group('fades', () {
    test('lerp to and from Solid lands on each end', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        final brutal = _look(roles);
        final solid = BoxDecoration(
          color: roles.surface1,
          borderRadius: _panel,
        );
        final there = Decoration.lerp(brutal, solid, 1);
        final back = Decoration.lerp(solid, brutal, 1);
        final start = Decoration.lerp(brutal, solid, 0);
        expect(back, brutal);
        expect(start, brutal);
        expect(there, isNotNull);
        if (there is KitSurfaceDecoration) {
          expect(there.hardOffset == 0 || there.hardShadow.a == 0, isTrue);
          expect(there.edgeWidth == 0 || there.edgeStart.a == 0, isTrue);
          expect(there.fill, roles.surface1);
        } else {
          expect(there, solid);
        }
        // Halfway the shadow is still hard: no blur appears in between.
        final half = Decoration.lerp(brutal, solid, .5);
        if (half is KitSurfaceDecoration) {
          expect(half.blur, 0);
          expect(half.shadows, isEmpty);
        }
      }
    });

    test('rest to pressed fades the shadow out as the face sinks in', () {
      final rest = _look(graphiteLight);
      final pressed = _look(graphiteLight, pressed: true);
      final half = Decoration.lerp(rest, pressed, .5) as KitSurfaceDecoration;
      expect(half.faceShift, closeTo(2, .001));
      expect(Decoration.lerp(rest, pressed, 1), pressed);
      expect(Decoration.lerp(rest, pressed, 0), rest);
    });
  });
}
