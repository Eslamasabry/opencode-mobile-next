// Soft (neumorphism, raised and pressed): its tokens and how it resolves
// the surfaces the polish pass tuned, in every theme the app ships.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_soft.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

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

KitSurfaceDecoration _soft(
  ThemeRoles roles, {
  KitSurfaceRole role = KitSurfaceRole.content,
  KitSurfaceLevel level = KitSurfaceLevel.surface1,
  KitKnobs? knobs,
  Color? color,
  bool selected = false,
  bool pressed = false,
  bool disabled = false,
}) =>
    KitSurfaceLook.resolve(
          roles: roles,
          style: KitSurfaceStyle.soft,
          role: role,
          solid: _solid,
          level: level,
          knobs: knobs,
          color: color,
          selected: selected,
          pressed: pressed,
          disabled: disabled,
        )
        as KitSurfaceDecoration;

/// [d]'s face as seen over the opaque ground.
Color _face(ThemeRoles roles, KitSurfaceDecoration d) =>
    Color.alphaBlend(d.fill, roles.ground.withValues(alpha: 1));

void main() {
  const spec = KitStyleSoft();
  // Every theme the app ships (but the device's own colours), each under
  // Soft's own roles.
  final themes = <String, KitTokens>{
    for (final b in Brightness.values)
      'graphite ${b.name}': KitTokens.fallback(
        b == Brightness.dark ? AppTheme.dark() : AppTheme.light(),
      ),
    for (final id in ThemePackId.values)
      if (id != ThemePackId.dynamic && id != ThemePackId.opencode)
        for (final b in Brightness.values)
          '${id.name} ${b.name}': KitTokens.fallback(
            b == Brightness.dark
                ? AppTheme.dark(themePack(id))
                : AppTheme.light(themePack(id)),
          ),
  };
  ThemeRoles rolesOf(KitTokens t) => spec.adjustRoles(t.roles);

  group('tokens', () {
    test('raised, no edges; the calm needs-you card; the ask field glows', () {
      final t = spec.tokens;
      expect(t.defaults.depth, KitDepth.raised);
      expect(t.defaults.edges, KitEdges.none);
      expect(t.dependsOnDepth, isTrue);
      expect(t.live.maxStrength, .7);
      final look = t.needsYou.decide(halo: t.glow.halo);
      expect(look.tint, isFalse, reason: 'no amber fill (contract F2)');
      expect(look.ring, isFalse);
      expect(look.line, isFalse);
      expect(look.startEdge, isTrue);
      expect(look.pill, isFalse);
      expect(t.fieldHaloShown, isTrue);
    });
  });

  group('chosen controls (guide F5, gate G5)', () {
    test('an accent thumb or tab stands raised out of its pressed-in '
        'track, its words at AA', () {
      for (final MapEntry(key: name, value: t) in themes.entries) {
        final roles = rolesOf(t);
        final d = _soft(
          roles,
          role: KitSurfaceRole.control,
          color: roles.accent,
          selected: true,
        );
        final track = _soft(roles, role: KitSurfaceRole.track);
        expect(d.inset, 0, reason: name);
        expect(track.inset, 1, reason: name);
        expect(d.distance, greaterThan(0), reason: name);
        expect(d.fill, roles.accent, reason: '$name: no muddy well');
        expect(
          _contrast(roles.onAccent, _face(roles, d)),
          greaterThanOrEqualTo(4.5),
          reason: name,
        );
        final held = _soft(
          roles,
          role: KitSurfaceRole.control,
          color: roles.accent,
          selected: true,
          pressed: true,
        );
        expect(held.inset, 1, reason: '$name: it still sinks while held');
      }
    });

    test('a face with its own colour (a status tag) takes no white wash in '
        'dark, so its words keep their contrast', () {
      for (final MapEntry(key: name, value: t) in themes.entries) {
        final roles = rolesOf(t);
        if (!roles.isDark) continue;
        final tint = roles.success.withValues(alpha: .16);
        final d = _soft(roles, role: KitSurfaceRole.control, color: tint);
        expect(d.fill, tint, reason: name);
      }
    });

    test('a chosen control without a fill is pressed in with accent words '
        'at AA', () {
      for (final MapEntry(key: name, value: t) in themes.entries) {
        final roles = rolesOf(t);
        final r = KitSurfaceRequest(
          roles: roles,
          role: KitSurfaceRole.control,
          solid: _solid,
          knobs: spec.tokens.defaults,
          level: KitSurfaceLevel.surface3,
          selected: true,
        );
        final d = _soft(
          roles,
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          selected: true,
        );
        expect(d.inset, 1, reason: name);
        final words = spec.onColor(r)!;
        expect(
          _contrast(words, _face(roles, d)),
          greaterThanOrEqualTo(4.5),
          reason: name,
        );
        expect(
          spec.onColor(
            KitSurfaceRequest(
              roles: roles,
              role: KitSurfaceRole.control,
              solid: _solid,
              knobs: spec.tokens.defaults,
              level: KitSurfaceLevel.surface3,
            ),
          ),
          isNull,
          reason: '$name: an unchosen control keeps its own words',
        );
      }
    });

    test('a control that cannot be used is flat: no shadows, no inset', () {
      for (final roles in [graphiteLight, graphiteDark]) {
        for (final color in [null, roles.accent]) {
          final d = _soft(
            roles,
            role: KitSurfaceRole.control,
            color: color,
            disabled: true,
          );
          expect(d.casts, isFalse);
          expect(d.inset, 0);
          expect(d.distance, 0);
        }
      }
    });
  });

  group('the prompt bubble (S9-4)', () {
    test('your prompt has its own tint, raised, words at AA', () {
      for (final MapEntry(key: name, value: t) in themes.entries) {
        final roles = rolesOf(t);
        final prompt = _soft(roles, level: KitSurfaceLevel.surface2);
        final panel = _soft(roles);
        expect(prompt.fill, isNot(panel.fill), reason: name);
        expect(prompt.inset, 0, reason: name);
        expect(prompt.distance, greaterThan(0), reason: name);
        expect(
          _contrast(roles.text1, _face(roles, prompt)),
          greaterThanOrEqualTo(4.5),
          reason: name,
        );
        expect(
          _contrast(roles.text2, _face(roles, prompt)),
          greaterThanOrEqualTo(4.5),
          reason: name,
        );
      }
    });
  });

  group('the code well', () {
    test('every code colour reads at AA on the well, in every theme', () {
      final fails = <String>[];
      for (final MapEntry(key: name, value: t) in themes.entries) {
        final roles = rolesOf(t);
        final d = _soft(
          roles,
          role: KitSurfaceRole.code,
          // KitTokens.detailsSurface under Soft's own roles.
          color: roles.isDark ? roles.surface1 : roles.ground,
        );
        expect(d.inset, 1, reason: '$name: still a well');
        final well = _face(roles, d);
        for (final (what, ink) in [
          ('text1', roles.text1),
          ('text2', roles.text2),
          ('keyword', roles.codeKeyword),
          ('string', roles.codeString),
          ('type', roles.codeType),
          ('added', roles.codeAdded),
        ]) {
          final c = _contrast(Color.alphaBlend(ink, well), well);
          if (c < 4.5) fails.add('$name $what ${c.toStringAsFixed(2)}');
        }
      }
      expect(fails, isEmpty, reason: fails.join('\n'));
    });
  });
}
