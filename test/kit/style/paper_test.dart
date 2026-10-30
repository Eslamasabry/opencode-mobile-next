// Paper (flat print, Swiss style): its tokens, its newsprint roles and how
// it resolves each surface, under every knob it answers to.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_paper.dart';
import 'package:opencode_mobile/ui/theme_roles.dart';

double _contrast(Color a, Color b) {
  final la = a.computeLuminance();
  final lb = b.computeLuminance();
  final hi = la > lb ? la : lb;
  final lo = la > lb ? lb : la;
  return (hi + .05) / (lo + .05);
}

const _solid = BoxDecoration(
  color: Color(0xFF808080),
  borderRadius: BorderRadius.all(Radius.circular(18)),
);

KitSurfaceDecoration _paper(
  ThemeRoles roles, {
  KitSurfaceRole role = KitSurfaceRole.content,
  KitSurfaceLevel level = KitSurfaceLevel.surface1,
  KitKnobs? knobs,
  Color? color,
  bool circle = false,
  bool selected = false,
  bool pressed = false,
}) =>
    KitSurfaceLook.resolve(
          roles: roles,
          style: KitSurfaceStyle.paper,
          role: role,
          solid: _solid,
          level: level,
          knobs: knobs,
          color: color,
          circle: circle,
          selected: selected,
          pressed: pressed,
        )
        as KitSurfaceDecoration;

void main() {
  const spec = KitStylePaper();
  final themes = [graphiteLight, graphiteDark];

  group('tokens', () {
    test('Sharp at 0, flat, ruled; keeps itself under high contrast', () {
      final t = spec.tokens;
      expect(t.defaults, const KitKnobs(corners: KitCorners.sharp));
      expect(t.defaults.depth, KitDepth.flat);
      expect(t.defaults.edges, KitEdges.hairline);
      expect(t.sharpRadius, 0);
      expect(t.keepsUnderHighContrast, isTrue);
      expect(t.live.offWhenChosen, isTrue);
      expect(t.glow.halo, isFalse);
      expect(t.glow.lineWidth, 1);
      expect(t.type.largeTitleSize, 34);
      expect(t.type.displayWeight, 750);
      expect(t.type.monoLabels, isTrue);
      expect(
        KitStyles.effectiveStyle(KitSurfaceStyle.paper, highContrast: true),
        KitSurfaceStyle.paper,
      );
    });
  });

  group('newsprint roles', () {
    test('only the ground, surface1, surface3 and the hairline move', () {
      for (final roles in themes) {
        final paper = spec.adjustRoles(roles);
        expect(paper.surface1, paper.ground, reason: 'panels are not filled');
        expect(paper.surface2, roles.surface2);
        expect(paper.text1, roles.text1);
        expect(paper.accent, roles.accent);
        expect(paper.attention, roles.attention);
        expect(paper.scrim, roles.scrim);
        expect(paper.hairline, roles.text1.withValues(alpha: .2));
      }
    });

    test('light paper is warm and a shade under white; dark is near black', () {
      final light = spec.adjustRoles(graphiteLight).ground;
      expect(light.r, greaterThan(light.b), reason: 'warm');
      expect(light.computeLuminance(), lessThan(1));
      expect(light.computeLuminance(), greaterThan(.85));
      final dark = spec.adjustRoles(graphiteDark).ground;
      expect(dark.computeLuminance(), lessThan(.01));
    });

    test('every word reads on the paper and on the pressed step (AA)', () {
      for (final roles in themes) {
        final paper = spec.adjustRoles(roles);
        for (final fill in [paper.ground, paper.surface2, paper.surface3]) {
          for (final ink in [paper.text1, paper.text2, paper.text3]) {
            expect(_contrast(ink, fill), greaterThanOrEqualTo(4.5));
          }
        }
        // The divider is a rule, not a boundary that must carry 3:1, but
        // it must still show.
        final rule = Color.alphaBlend(paper.hairline, paper.ground);
        expect(_contrast(rule, paper.ground), greaterThan(1.3));
      }
    });
  });

  group('resolve at the defaults', () {
    test('no shadow of any kind on any role, chosen or not', () {
      for (final roles in themes) {
        for (final role in KitSurfaceRole.values) {
          for (final down in [false, true]) {
            final d = _paper(roles, role: role, selected: down, pressed: down);
            expect(d.shadows, isEmpty);
            expect(d.hardOffset, 0);
            expect(d.distance, 0);
            expect(d.faceShift, 0);
          }
        }
      }
    });

    test('a panel is set on the page: no fill, no box', () {
      for (final roles in themes) {
        final d = _paper(roles);
        expect(d.fill.a, 0);
        expect(d.edgeWidth, 0);
      }
    });

    test('a raised step on the page (the prompt) is a box on the paper', () {
      for (final roles in themes) {
        final d = _paper(roles, level: KitSurfaceLevel.surface2);
        expect(d.fill, roles.ground);
        expect(d.edgeWidth, 1);
        expect(
          _contrast(Color.alphaBlend(d.edgeStart, d.fill), d.fill),
          greaterThanOrEqualTo(3),
        );
      }
    });

    test('controls, tracks and floating pieces are ink boxes (3:1)', () {
      for (final roles in themes) {
        for (final role in [
          KitSurfaceRole.control,
          KitSurfaceRole.track,
          KitSurfaceRole.floating,
          KitSurfaceRole.sheet,
        ]) {
          final d = _paper(roles, role: role);
          expect(d.edgeWidth, 1, reason: role.name);
          expect(
            d.edgeStart,
            roles.text1.withValues(alpha: roles.isDark ? .6 : .9),
          );
          expect(d.fill.a, 1, reason: '${role.name} is opaque');
          expect(
            _contrast(Color.alphaBlend(d.edgeStart, d.fill), d.fill),
            greaterThanOrEqualTo(3),
            reason: role.name,
          );
        }
      }
    });

    test('the accent keeps its own fill and needs no box', () {
      for (final roles in themes) {
        final d = _paper(
          roles,
          role: KitSurfaceRole.control,
          color: roles.accent,
        );
        expect(d.fill, roles.accent);
        expect(d.edgeWidth, 0);
      }
    });

    test('square corners; circles stay circles', () {
      final d = _paper(graphiteLight, role: KitSurfaceRole.control);
      expect(d.borderRadius, BorderRadius.zero);
      final send = _paper(
        graphiteLight,
        role: KitSurfaceRole.control,
        circle: true,
      );
      expect(send.circle, isTrue);
    });
  });

  group('chosen and pressed', () {
    test('a chosen control inverts: an ink fill, paper words at AA', () {
      for (final roles in themes) {
        for (final color in [null, roles.accent]) {
          final d = _paper(
            roles,
            role: KitSurfaceRole.control,
            color: color,
            selected: true,
          );
          final ink = roles.text1.withValues(alpha: 1);
          expect(d.fill, ink);
          expect(d.edgeStart, ink, reason: 'one block, no ring');
          final words = spec.onColor(
            KitSurfaceRequest(
              roles: roles,
              role: KitSurfaceRole.control,
              solid: _solid,
              knobs: spec.tokens.defaults,
              color: color,
              selected: true,
            ),
          );
          expect(words, roles.ground.withValues(alpha: 1));
          expect(_contrast(words!, d.fill), greaterThanOrEqualTo(4.5));
        }
      }
    });

    test('a pressed row takes the pressed step, still without a box', () {
      for (final roles in themes) {
        final d = _paper(roles, pressed: true);
        expect(d.fill, roles.surface3);
        expect(d.edgeWidth, 0);
      }
    });

    test('a chosen card is boxed, so the choice shows without colour', () {
      final d = _paper(graphiteLight, selected: true);
      expect(d.edgeWidth, KitStylePaper.chosenRule);
    });

    test('only a chosen control inverts its words', () {
      KitSurfaceRequest r({
        KitSurfaceRole role = KitSurfaceRole.control,
        Color? color,
        bool selected = false,
      }) => KitSurfaceRequest(
        roles: graphiteLight,
        role: role,
        solid: _solid,
        knobs: spec.tokens.defaults,
        color: color,
        selected: selected,
      );
      expect(spec.onColor(r(color: graphiteLight.accent)), isNull);
      expect(spec.onColor(r()), isNull);
      expect(
        spec.onColor(r(role: KitSurfaceRole.content, selected: true)),
        isNull,
      );
      expect(
        spec.onColor(r(role: KitSurfaceRole.field, selected: true)),
        isNull,
      );
    });

    test('hover and press differ visibly; press keeps every word at AA', () {
      for (final roles in themes) {
        final paper = spec.adjustRoles(roles);
        final hover = Color.alphaBlend(
          _paper(paper, role: KitSurfaceRole.stateLayer).fill,
          paper.ground,
        );
        final held = Color.alphaBlend(
          _paper(paper, role: KitSurfaceRole.stateLayer, pressed: true).fill,
          paper.ground,
        );
        expect(_contrast(held, paper.ground), greaterThan(1.15));
        // The held step sits at least twice as far from the paper as the
        // hover.
        expect(
          _contrast(held, paper.ground) - 1,
          greaterThan((_contrast(hover, paper.ground) - 1) * 2),
        );
        for (final ink in [paper.text1, paper.text2, paper.text3]) {
          expect(_contrast(ink, held), greaterThanOrEqualTo(4.5));
        }
      }
    });
  });

  group('rules, needs you and the switch', () {
    test('the section rule is ink, apart from the row divider', () {
      for (final roles in themes) {
        final row = _paper(roles, role: KitSurfaceRole.divider);
        final section = _paper(
          roles,
          role: KitSurfaceRole.divider,
          selected: true,
        );
        expect(
          row.fill,
          roles.text1.withValues(alpha: KitStylePaper.ruleAlpha),
        );
        expect(section.fill, roles.text1.withValues(alpha: .9));
        expect(section.edgeWidth, 1);
        final off = _paper(
          roles,
          role: KitSurfaceRole.divider,
          selected: true,
          knobs: const KitKnobs(
            corners: KitCorners.sharp,
            edges: KitEdges.none,
          ),
        );
        expect(off.fill.a, 0, reason: 'Edges › Off: space only');
      }
    });

    test('code is a printed listing in the ink box', () {
      for (final roles in themes) {
        final d = _paper(roles, role: KitSurfaceRole.code);
        expect(d.edgeWidth, 1);
        expect(
          d.edgeStart,
          roles.text1.withValues(alpha: KitStylePaper.boxAlpha(roles)),
        );
        expect(d.fill.a, 1);
      }
    });

    test('a field is a ruled box on the page under it', () {
      for (final roles in themes) {
        for (final focused in [false, true]) {
          final d = _paper(
            roles,
            role: KitSurfaceRole.field,
            selected: focused,
          );
          expect(d.fill.a, 0);
          expect(d.edgeWidth, focused ? KitStylePaper.chosenRule : 1);
          expect(
            _contrast(
              Color.alphaBlend(d.edgeStart, roles.ground),
              roles.ground,
            ),
            greaterThanOrEqualTo(3),
          );
        }
      }
    });

    test('needs you: no tint, no ring, the start rule alone', () {
      final look = spec.tokens.needsYou.decide(halo: spec.tokens.glow.halo);
      expect(look.tint, isFalse);
      expect(look.ring, isFalse);
      expect(look.line, isFalse);
      expect(look.startEdge, isTrue);
      expect(look.pill, isFalse);
    });

    test('the switch thumb and the mint main action are square; stop and the '
        'quiet discs stay round', () {
      for (final roles in themes) {
        final thumb = _paper(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface2,
          color: roles.text2,
          circle: true,
        );
        expect(thumb.circle, isFalse);
        expect(thumb.borderRadius, BorderRadius.zero);
        final track = _paper(roles, role: KitSurfaceRole.toggle);
        expect(track.circle, isFalse);
        expect(track.borderRadius, BorderRadius.zero);
        // The composer's mic or send (paper.md F5: the primary is a
        // square of accent), keeping its mint.
        final send = _paper(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          color: roles.accent,
          circle: true,
        );
        expect(send.circle, isFalse);
        expect(send.borderRadius, BorderRadius.zero);
        expect(send.fill, roles.accent);
        final stop = _paper(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          color: roles.text1,
          circle: true,
        );
        expect(stop.circle, isTrue, reason: 'stop turns in a round ring');
        final quiet = _paper(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          circle: true,
        );
        expect(quiet.circle, isTrue);
        final roundSend = _paper(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          color: roles.accent,
          circle: true,
          knobs: const KitKnobs(),
        );
        expect(roundSend.circle, isTrue, reason: 'Corners › Round keeps it');
        final round = _paper(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface2,
          circle: true,
          knobs: const KitKnobs(),
        );
        expect(round.circle, isTrue, reason: 'Corners › Round keeps it');
      }
    });
  });

  group('knobs', () {
    test('Edges › Off: sheets lose the box; controls and the message box '
        'keep theirs', () {
      const off = KitKnobs(corners: KitCorners.sharp, edges: KitEdges.none);
      for (final roles in themes) {
        expect(
          _paper(roles, role: KitSurfaceRole.sheet, knobs: off).edgeWidth,
          0,
        );
        final prompt = _paper(
          roles,
          level: KitSurfaceLevel.surface2,
          knobs: off,
        );
        expect(prompt.edgeWidth, 0);
        expect(
          prompt.fill,
          roles.surface3,
          reason: 'your prompt still stands apart from the reply',
        );
        for (final role in [
          KitSurfaceRole.control,
          KitSurfaceRole.track,
          KitSurfaceRole.floating,
        ]) {
          expect(_paper(roles, role: role, knobs: off).edgeWidth, 1);
        }
      }
    });

    test('Depth › Raised: only the floating layer takes the one drop', () {
      const raised = KitKnobs(
        corners: KitCorners.sharp,
        depth: KitDepth.raised,
      );
      for (final role in KitSurfaceRole.values) {
        final d = _paper(graphiteLight, role: role, knobs: raised);
        expect(
          d.shadows,
          role == KitSurfaceRole.floating ? hasLength(1) : isEmpty,
          reason: role.name,
        );
      }
    });

    test('Corners › Round and Soft keep the part\'s own corners', () {
      for (final corners in [KitCorners.round, KitCorners.soft]) {
        final d = _paper(
          graphiteLight,
          role: KitSurfaceRole.control,
          knobs: KitKnobs(corners: corners),
        );
        expect(d.borderRadius, BorderRadius.circular(18));
      }
    });

    test('high contrast keeps Paper with edges on and depth flat', () {
      final knobs = KitStyles.knobsFor(
        style: KitSurfaceStyle.paper,
        fineTune: const KitFineTune(
          depth: KitDepth.raised,
          edges: KitEdges.none,
        ),
        highContrast: true,
      );
      expect(knobs.depth, KitDepth.flat);
      expect(knobs.edges, KitEdges.hairline);
    });
  });

  group('direction and motion', () {
    test('nothing directional: the same rule at the start and the end', () {
      for (final role in KitSurfaceRole.values) {
        final d = _paper(graphiteDark, role: role, selected: true);
        expect(d.edgeStart, d.edgeEnd);
        expect(d.fillStart, d.fill);
        expect(d.hardOffset, 0);
      }
    });

    testWidgets('paints the same in left-to-right and right-to-left', (
      tester,
    ) async {
      final d = _paper(graphiteLight, role: KitSurfaceRole.control);
      Future<List<int>> shot(TextDirection direction) async {
        final key = GlobalKey();
        await tester.pumpWidget(
          Directionality(
            textDirection: direction,
            child: Center(
              child: RepaintBoundary(
                key: key,
                child: SizedBox(
                  width: 40,
                  height: 20,
                  child: DecoratedBox(decoration: d),
                ),
              ),
            ),
          ),
        );
        final boundary =
            key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
        late List<int> bytes;
        await tester.runAsync(() async {
          final image = await boundary.toImage();
          final data = await image.toByteData();
          bytes = data!.buffer.asUint8List();
          image.dispose();
        });
        return bytes;
      }

      expect(await shot(TextDirection.rtl), await shot(TextDirection.ltr));
    });

    test('lerps to and from Solid', () {
      for (final roles in themes) {
        final paper = _paper(roles, role: KitSurfaceRole.control);
        final solid = KitSurfaceLook.resolve(
          roles: roles,
          style: KitSurfaceStyle.solid,
          role: KitSurfaceRole.control,
          solid: _solid,
        );
        expect(solid, _solid, reason: 'Solid is the part\'s own look');
        final there = Decoration.lerp(solid, paper, 1);
        expect(there, paper);
        final back = Decoration.lerp(paper, solid, 1)!;
        expect(KitSurfaceDecoration.radiusOf(back), _solid.borderRadius);
        final half = Decoration.lerp(solid, paper, .5);
        expect(half, isA<KitSurfaceDecoration>());
        final mid = half! as KitSurfaceDecoration;
        expect(mid.borderRadius, BorderRadius.circular(9));
      }
    });
  });
}
