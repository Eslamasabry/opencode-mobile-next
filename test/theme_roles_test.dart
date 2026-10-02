// The colour roles of the visual language (docs/design/visual-language-2026-09-26.md
// §3): Graphite is the default theme, every pack supplies the whole role
// set, and deriveRoles (the seam for a custom theme) keeps the contrast
// floors for any accent and ground.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

/// Every floor a role set promises, as failure lines.
List<String> _floors(String tag, ThemeRoles r) {
  final failures = <String>[];
  void floor(String what, Color fg, Color bg, double min) {
    final ratio = contrastRatio(fg, Color.alphaBlend(bg, r.ground));
    if (ratio < min) {
      failures.add('$tag $what ${ratio.toStringAsFixed(2)} < $min');
    }
  }

  final surfaces = {
    'ground': r.ground,
    'surface1': r.surface1,
    'surface2': r.surface2,
    'surface3': r.surface3,
  };
  for (final MapEntry(:key, :value) in surfaces.entries) {
    floor('text1 on $key', r.text1, value, 7);
    floor('text2 on $key', r.text2, value, 4.5);
    // text3 on surface3 is a disabled primary or secondary button.
    floor('text3 on $key', r.text3, value, 4.5);
  }
  floor('accent on ground', r.accent, r.ground, 4.5);
  floor('accent on surface1', r.accent, r.surface1, 4.5);
  floor('onAccent on accent', r.onAccent, r.accent, 4.5);
  floor('attention on ground', r.attention, r.ground, 4.5);
  floor('attention on surface1', r.attention, r.surface1, 4.5);
  floor(
    'attention on its card',
    r.attention,
    Color.alphaBlend(r.attentionSurface, r.surface1),
    4.5,
  );
  floor(
    'text1 on the needs-you card',
    r.text1,
    Color.alphaBlend(r.attentionSurface, r.surface1),
    7,
  );
  floor('badge text', r.onAttentionFill, r.attentionFill, 4.5);
  floor('danger on ground', r.danger, r.ground, 4.5);
  floor('danger on surface2', r.danger, r.surface2, 4.5);
  floor('onDangerFill on dangerFill', r.onDangerFill, r.dangerFill, 4.5);
  floor('success on ground', r.success, r.ground, 4.5);
  for (final code in [r.codeKeyword, r.codeString, r.codeType]) {
    floor('code on surface1', code, r.surface1, 4.5);
  }
  // Opaque text roles (§7: never text at partial opacity).
  for (final text in [r.text1, r.text2, r.text3, r.accent, r.attention]) {
    if (text.a != 1) failures.add('$tag a text role is translucent');
  }
  return failures;
}

/// LOOK-39 for [accent] against a role set's attention and danger.
List<String> _apart(String tag, Color accent, ThemeRoles r) => [
  for (final (name, other) in [
    ('attention', r.attention),
    ('danger', r.danger),
  ]) ...[
    if (hueDistance(accent, other) < accentMinHueDistance)
      '$tag hue ${hueDistance(accent, other).toStringAsFixed(0)}° '
          'from $name < 30°',
    if (deltaE2000(accent, other) < accentMinDeltaE)
      '$tag ΔE2000 ${deltaE2000(accent, other).toStringAsFixed(1)} '
          'from $name < 20',
  ],
];

void main() {
  test('Graphite is the default and keeps the spec palette', () {
    final dark = AppTheme.dark();
    final roles = dark.extension<ThemeRoles>()!;
    expect(roles.ground, const Color(0xFF0B0C0E));
    expect(roles.surface1, const Color(0xFF141518));
    expect(roles.surface2, const Color(0xFF1B1C20));
    expect(roles.surface3, const Color(0xFF26282D));
    expect(roles.text1, const Color(0xFFF3F3F1));
    expect(roles.accent, const Color(0xFF3DDC8A));
    expect(roles.attention, const Color(0xFFFFB547));
    expect(dark.scaffoldBackgroundColor, roles.ground);
    expect(dark.colorScheme.primary, roles.accent);
    expect(dark.colorScheme.onPrimary, roles.onAccent);
    expect(dark.colorScheme.surfaceContainerLow, roles.surface1);
    expect(dark.colorScheme.surfaceContainer, roles.surface2);
    expect(dark.colorScheme.surfaceContainerHighest, roles.surface3);
    expect(dark.colorScheme.error, roles.danger);
    expect(themePackLabels[ThemePackId.opencode], 'Graphite');

    final light = AppTheme.light().extension<ThemeRoles>()!;
    expect(light.ground, const Color(0xFFF3F3F1));
    expect(light.surface1, Colors.white);
    expect(light.text1, const Color(0xFF111214));
  });

  test('Graphite meets every contrast floor in both modes', () {
    expect([
      ..._floors('dark', graphiteDark),
      ..._floors('light', graphiteLight),
    ], isEmpty);
  });

  test('every theme pack supplies the whole role set, readable', () {
    final failures = <String>[];
    for (final id in ThemePackId.values.where(
      (id) => id != ThemePackId.dynamic,
    )) {
      for (final brightness in Brightness.values) {
        final roles = themePack(id).palette(brightness).themeRoles;
        expect(roles.brightness, brightness, reason: id.name);
        failures.addAll(_floors('${id.name}/${brightness.name}', roles));
      }
    }
    expect(failures, isEmpty, reason: failures.join('\n'));
  });

  test('a custom accent and ground derive a readable role set', () {
    // The seam for a custom theme: the person picks an accent and a ground.
    const accents = [
      Color(0xFF3DDC8A),
      Color(0xFF5AB0FF),
      Color(0xFFFF8A4C),
      Color(0xFFC7A6FF),
      Color(0xFFFFEB3B), // too light for a light theme
      Color(0xFF1A237E), // too dark for a dark theme
      Color(0xFF808080), // mid grey: carries neither ink nor white
      Color(0xFFE91E63),
    ];
    const darkGrounds = [
      Color(0xFF000000),
      Color(0xFF0B0C0E),
      Color(0xFF1E1E2E),
      Color(0xFF002B36),
      Color(0xFF2D2A2E),
    ];
    const lightGrounds = [
      Color(0xFFFFFFFF),
      Color(0xFFF3F3F1),
      Color(0xFFFDF6E3),
      Color(0xFFE1E2E7),
      Color(0xFFEFF1F5),
    ];
    final failures = <String>[];
    for (final accent in accents) {
      for (final (brightness, grounds) in [
        (Brightness.dark, darkGrounds),
        (Brightness.light, lightGrounds),
      ]) {
        for (final ground in grounds) {
          final roles = deriveRoles(
            accent: accent,
            ground: ground,
            brightness: brightness,
          );
          failures.addAll(
            _floors(
              '${accent.toARGB32().toRadixString(16)} on '
              '${ground.toARGB32().toRadixString(16)}',
              roles,
            ),
          );
        }
      }
    }
    expect(failures, isEmpty, reason: failures.join('\n'));
  });

  test('withAccent swaps only the accent and keeps it readable', () {
    for (final option in graphiteAccents) {
      final dark = graphiteDark.withAccent(option.dark);
      final light = graphiteLight.withAccent(option.light);
      expect(dark.surface1, graphiteDark.surface1);
      expect(dark.attention, graphiteDark.attention);
      expect(light.danger, graphiteLight.danger);
      expect(_floors('dark ${option.name}', dark), isEmpty);
      expect(_floors('light ${option.name}', light), isEmpty);
    }
  });

  test('packs change colours only: shape, space and type are fixed', () {
    final reference = KitTokens.fromRoles(
      graphiteDark,
      AppTheme.dark().textTheme,
    );
    for (final id in ThemePackId.values.where(
      (id) => id != ThemePackId.dynamic,
    )) {
      final theme = AppTheme.dark(themePack(id));
      final tokens = theme.extension<KitTokens>()!;
      expect(tokens.panelCornerRadius, reference.panelCornerRadius);
      expect(tokens.cardRadius, reference.cardRadius);
      expect(tokens.sheetRadius, reference.sheetRadius);
      expect(tokens.rowHeight, reference.rowHeight);
      expect(tokens.navHeight, reference.navHeight);
      expect(tokens.rowTitle.fontSize, reference.rowTitle.fontSize);
      expect(theme.textTheme.bodyLarge?.fontFamily, AppTheme.sansFamily);
      expect(theme.colorScheme.primary, tokens.roles.accent);
    }
  });

  test('type sizes are whole pixels and the faces are Geist', () {
    for (final role in KitTextRole.values) {
      final size = KitText.styleFor(role).fontSize!;
      expect(size, size.roundToDouble(), reason: role.name);
    }
    expect(KitText.styleFor(KitTextRole.mono).fontFamily, AppTheme.monoFamily);
    final text = AppTheme.dark().textTheme;
    for (final style in [
      text.headlineLarge,
      text.titleLarge,
      text.titleMedium,
      text.bodyLarge,
      text.bodyMedium,
      text.bodySmall,
      text.labelLarge,
      text.labelMedium,
      text.labelSmall,
    ]) {
      expect(style?.fontFamily, AppTheme.sansFamily);
      expect(style?.fontSize, style!.fontSize!.roundToDouble());
      expect(style.color?.a, 1);
    }
  });

  test('the ΔE2000 used for LOOK-39 matches the published reference', () {
    // Sharma, Wu and Dalal (2005), table 1: pairs 1, 7 and 17.
    expect(
      deltaE2000Lab([50, 2.6772, -79.7751], [50, 0, -82.7485]),
      closeTo(2.0425, 1e-4),
    );
    expect(deltaE2000Lab([50, 0, 0], [50, -1, 2]), closeTo(2.3669, 1e-4));
    expect(deltaE2000Lab([50, 2.5, 0], [73, 25, -18]), closeTo(27.1492, 1e-4));
    expect(deltaE2000(Colors.black, Colors.white), closeTo(100, .01));
    expect(hueDistance(const Color(0xFFFF0000), const Color(0xFF00FFFF)), 180);
    expect(hueDistance(const Color(0xFFFF0000), const Color(0xFFFF00FF)), 60);
  });

  test(
    'Graphite offers green, blue, teal and violet; teal replaced orange',
    () {
      // Owner decision B15: amber means only "needs you", so no accent is
      // orange.
      expect(graphiteAccents.map((option) => option.name), [
        'green',
        'blue',
        'teal',
        'violet',
      ]);
      final teal = graphiteAccents.firstWhere(
        (option) => option.name == 'teal',
      );
      expect(teal.dark, const Color(0xFF3CCFCF));
      expect(teal.light, const Color(0xFF0D7377));
      // Teal passes LOOK-8 as chosen: the contrast guard leaves it alone.
      expect(graphiteDark.withAccent(teal.dark).accent, teal.dark);
      expect(graphiteLight.withAccent(teal.light).accent, teal.light);
      for (final (roles, accent) in [
        (graphiteDark, teal.dark),
        (graphiteLight, teal.light),
      ]) {
        final tag = 'teal/${roles.brightness.name}';
        expect(
          contrastRatio(onColor(accent), accent),
          greaterThanOrEqualTo(4.5),
          reason: '$tag onAccent on accent',
        );
        expect(
          contrastRatio(accent, roles.ground),
          greaterThanOrEqualTo(4.5),
          reason: '$tag accent on ground',
        );
        expect(
          contrastRatio(accent, roles.surface1),
          greaterThanOrEqualTo(4.5),
          reason: '$tag accent on surface1',
        );
      }
    },
  );

  test(
    'every Graphite accent keeps clear of attention and danger (LOOK-39)',
    () {
      final failures = <String>[];
      for (final option in graphiteAccents) {
        for (final (roles, raw) in [
          (graphiteDark, option.dark),
          (graphiteLight, option.light),
        ]) {
          final tag = '${option.name}/${roles.brightness.name}';
          failures
            ..addAll(_apart(tag, raw, roles))
            ..addAll(
              _apart('$tag guarded', roles.withAccent(raw).accent, roles),
            );
        }
      }
      expect(failures, isEmpty, reason: failures.join('\n'));
      // The check bites: the orange that teal replaced fails it.
      expect(
        _apart('orange', const Color(0xFFFF8A4C), graphiteDark),
        isNotEmpty,
      );
      expect(
        _apart('orange', const Color(0xFFC2410C), graphiteLight),
        isNotEmpty,
      );
    },
  );

  test('a disabled button reads in every pack: text3 on surface3 (R9)', () {
    // KitButton's disabled primary and secondary paint text3 on surface3.
    // Graphite dark's spec #8A8D94 measured 4.44:1 there.
    expect(
      contrastRatio(graphiteDark.text3, graphiteDark.surface3),
      greaterThanOrEqualTo(4.5),
    );
    expect(graphiteDark.text3, const Color(0xFF8C8F96));
    final failures = <String>[];
    void check(String tag, ThemeRoles r) {
      final ratio = contrastRatio(r.text3, r.surface3);
      if (ratio < 4.5) {
        failures.add('$tag text3 on surface3 ${ratio.toStringAsFixed(2)}');
      }
    }

    for (final id in ThemePackId.values.where(
      (id) => id != ThemePackId.dynamic,
    )) {
      for (final brightness in Brightness.values) {
        check(
          '${id.name}/${brightness.name}',
          themePack(id).palette(brightness).themeRoles,
        );
      }
    }
    // A custom theme too: the seam derives it with the same floor.
    for (final (brightness, ground) in [
      (Brightness.dark, const Color(0xFF1E1E2E)),
      (Brightness.light, const Color(0xFFFDF6E3)),
    ]) {
      check(
        'custom/${brightness.name}',
        deriveRoles(
          accent: const Color(0xFF5AB0FF),
          ground: ground,
          brightness: brightness,
        ),
      );
    }
    expect(failures, isEmpty, reason: failures.join('\n'));
  });

  test('accentKeepsMeaning is the one LOOK-39 check (R9)', () {
    expect(accentMinHueDistance, 30);
    expect(accentMinDeltaE, 20);
    // Every offered Graphite accent keeps its meaning, raw and guarded.
    for (final option in graphiteAccents) {
      for (final (roles, raw) in [
        (graphiteDark, option.dark),
        (graphiteLight, option.light),
      ]) {
        final tag = '${option.name}/${roles.brightness.name}';
        expect(accentKeepsMeaning(raw, roles), isTrue, reason: tag);
        expect(
          accentKeepsMeaning(roles.withAccent(raw).accent, roles),
          isTrue,
          reason: '$tag guarded',
        );
      }
    }
    // Attention, danger and the orange teal replaced all lose it.
    for (final roles in [graphiteDark, graphiteLight]) {
      expect(accentKeepsMeaning(roles.attention, roles), isFalse);
      expect(accentKeepsMeaning(roles.danger, roles), isFalse);
    }
    expect(accentKeepsMeaning(const Color(0xFFFF8A4C), graphiteDark), isFalse);
    expect(accentKeepsMeaning(const Color(0xFFC2410C), graphiteLight), isFalse);
    // It says the same as the line-by-line reasons the tests print.
    for (var hue = 0; hue < 360; hue += 15) {
      final colour = HSVColor.fromAHSV(1, hue.toDouble(), .7, .9).toColor();
      for (final roles in [graphiteDark, graphiteLight]) {
        expect(
          accentKeepsMeaning(colour, roles),
          _apart('hue $hue', colour, roles).isEmpty,
          reason: 'hue $hue/${roles.brightness.name}',
        );
      }
    }
  });

  test('a floating surface has its own shadow role in every theme', () {
    // The one shadow is 30 % black in both brightnesses.
    const shadow = Color(0x4D000000);
    expect(graphiteDark.elevationShadow, shadow);
    expect(graphiteLight.elevationShadow, shadow);
    // Derived: every pack and a custom theme carry it.
    for (final brightness in Brightness.values) {
      final custom = deriveRoles(
        accent: const Color(0xFFE91E63),
        ground: brightness == Brightness.dark
            ? const Color(0xFF1E1E2E)
            : const Color(0xFFFDF6E3),
        brightness: brightness,
      );
      final packs = [
        for (final id in ThemePackId.values.where(
          (id) => id != ThemePackId.dynamic,
        ))
          themePack(id).palette(brightness).themeRoles,
      ];
      for (final roles in [custom, ...packs]) {
        expect(roles.elevationShadow, shadow);
      }
    }
    // It survives copyWith and lerp.
    expect(
      graphiteDark.copyWith().elevationShadow,
      graphiteDark.elevationShadow,
    );
    final tokens = KitTokens.fromRoles(
      graphiteLight,
      AppTheme.light().textTheme,
    );
    expect(tokens.surfaceShadows, hasLength(1));
    expect(tokens.surfaceShadows.single.color, shadow);
    expect(tokens.surfaceShadows.single.blurRadius, 16);
    expect(tokens.surfaceShadows.single.offset, const Offset(0, 6));
  });

  test('every Material type slot is one role, with no other size', () {
    // LOOK-12, LOOK-17: no 57/45/36/28/20/15 anywhere in the type scale.
    bool same(TextStyle a, TextStyle b) =>
        a.fontSize == b.fontSize &&
        a.height == b.height &&
        a.fontWeight == b.fontWeight &&
        a.letterSpacing == b.letterSpacing;
    final roleSizes = {
      for (final role in KitTextRole.values) KitText.styleFor(role).fontSize,
    };
    expect(roleSizes, {32, 24, 17, 16, 14, 13, 12});
    for (final theme in [AppTheme.dark(), AppTheme.light()]) {
      final text = theme.textTheme;
      final slots = {
        'displayLarge': text.displayLarge,
        'displayMedium': text.displayMedium,
        'displaySmall': text.displaySmall,
        'headlineLarge': text.headlineLarge,
        'headlineMedium': text.headlineMedium,
        'headlineSmall': text.headlineSmall,
        'titleLarge': text.titleLarge,
        'titleMedium': text.titleMedium,
        'titleSmall': text.titleSmall,
        'bodyLarge': text.bodyLarge,
        'bodyMedium': text.bodyMedium,
        'bodySmall': text.bodySmall,
        'labelLarge': text.labelLarge,
        'labelMedium': text.labelMedium,
        'labelSmall': text.labelSmall,
      };
      for (final MapEntry(:key, :value) in slots.entries) {
        final roles = KitTextRole.values.where(
          (role) =>
              role != KitTextRole.mono && same(value!, KitText.styleFor(role)),
        );
        expect(roles, isNotEmpty, reason: '$key is no role: $value');
      }
      // The top bar's title is the headline role.
      expect(
        same(
          theme.appBarTheme.titleTextStyle!,
          KitText.styleFor(KitTextRole.headline),
        ),
        isTrue,
      );
    }
    // The button role is 16/20 (LOOK-12), not the old 15.
    expect(KitText.styleFor(KitTextRole.button).fontSize, 16);
  });

  test('a row value is the secondary role and a typed name is mono 13', () {
    final tokens = AppTheme.dark().extension<KitTokens>()!;
    final secondary = KitText.styleFor(KitTextRole.secondary);
    expect(tokens.rowValue.fontSize, secondary.fontSize);
    expect(tokens.rowValue.height, secondary.height);
    expect(tokens.rowValue.fontWeight, secondary.fontWeight);
    expect(tokens.rowValue.color, graphiteDark.text3);
    final mono = KitText.styleFor(KitTextRole.mono);
    expect(tokens.typedName.fontFamily, AppTheme.monoFamily);
    expect(tokens.typedName.fontSize, 13);
    expect(tokens.typedName.height, mono.height);
  });
}
