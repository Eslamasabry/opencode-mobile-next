import 'package:flutter/material.dart';

import '../state/profiles.dart' show ThemePackId;

export '../state/profiles.dart' show ThemePackId;

/// Holds the Material You pack once main harvests the system palette; null
/// when unavailable (below Android 12, desktop, tests).
final ValueNotifier<ThemePack?> harvestedDynamicPack = ValueNotifier(null);

/// Resolves the effective pack for a selection, falling back to OpenCode
/// when Material You was chosen but never harvested.
ThemePack effectiveThemePack(ThemePackId id) => id == ThemePackId.dynamic
    ? (harvestedDynamicPack.value ?? themePack(ThemePackId.opencode))
    : themePack(id);

/// One brightness variant of a pack: a fully-resolved scheme plus the three
/// app-level colors the component system needs beyond Material's scheme.
class ThemePalette {
  final ColorScheme scheme;
  final Color background;
  final Color navigation;
  final Color success;

  const ThemePalette({
    required this.scheme,
    required this.background,
    required this.navigation,
    required this.success,
  });
}

class ThemePack {
  final ThemePackId id;
  final String label;
  final String tagline;
  final ThemePalette dark;
  final ThemePalette light;

  const ThemePack({
    required this.id,
    required this.label,
    required this.tagline,
    required this.dark,
    required this.light,
  });

  ThemePalette palette(Brightness brightness) =>
      brightness == Brightness.dark ? dark : light;
}

/// Resolves a static pack. [ThemePackId.dynamic] has no static palettes —
/// callers must use [dynamicThemePack] with harvested schemes, or fall back
/// to [ThemePackId.opencode].
ThemePack themePack(ThemePackId id) => switch (id) {
  ThemePackId.opencode || ThemePackId.dynamic => _opencode,
  ThemePackId.catppuccin => _catppuccin,
  ThemePackId.gruvbox => _gruvbox,
  ThemePackId.solarized => _solarized,
};

const themePackLabels = {
  ThemePackId.opencode: 'OpenCode',
  ThemePackId.catppuccin: 'Catppuccin',
  ThemePackId.gruvbox: 'Gruvbox',
  ThemePackId.solarized: 'Solarized',
  ThemePackId.dynamic: 'Material You',
};

/// Builds the Material You pack from the device's harvested schemes. The
/// success color keeps the OpenCode semantics because dynamic palettes carry
/// no green with stable meaning.
ThemePack dynamicThemePack({
  required ColorScheme lightScheme,
  required ColorScheme darkScheme,
}) => ThemePack(
  id: ThemePackId.dynamic,
  label: themePackLabels[ThemePackId.dynamic]!,
  tagline: 'This phone’s Material You colors',
  dark: ThemePalette(
    scheme: darkScheme,
    background: darkScheme.surfaceContainerLowest,
    navigation: darkScheme.surfaceContainerLow,
    success: const Color(0xFF86D8A5),
  ),
  light: ThemePalette(
    scheme: lightScheme,
    background: lightScheme.surfaceContainerLowest,
    navigation: lightScheme.surfaceContainerLow,
    success: const Color(0xFF1E7A44),
  ),
);

// ---------------------------------------------------------------------------
// OpenCode — the app's own identity. These builders are the pre-pack scheme
// definitions moved verbatim; the default look must not drift.
// ---------------------------------------------------------------------------

final ThemePack _opencode = ThemePack(
  id: ThemePackId.opencode,
  label: themePackLabels[ThemePackId.opencode]!,
  tagline: 'Terminal green, the default',
  dark: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFF83CDAA),
          brightness: Brightness.dark,
        ).copyWith(
          primary: const Color(0xFF83CDAA),
          onPrimary: const Color(0xFF052117),
          primaryContainer: const Color(0xFF183B2D),
          onPrimaryContainer: const Color(0xFFB6EBD2),
          secondary: const Color(0xFFA8CBB9),
          onSecondary: const Color(0xFF10231A),
          secondaryContainer: const Color(0xFF253A30),
          onSecondaryContainer: const Color(0xFFD7E9DE),
          surface: const Color(0xFF151A17),
          onSurface: const Color(0xFFE3E8E4),
          onSurfaceVariant: const Color(0xFFBCC5BF),
          outline: const Color(0xFF7F8A83),
          outlineVariant: const Color(0xFF3B443F),
          error: const Color(0xFFFFB4AB),
          onError: const Color(0xFF690005),
          errorContainer: const Color(0xFF4B1518),
          onErrorContainer: const Color(0xFFFFDAD6),
          surfaceContainerLowest: const Color(0xFF0C0F0D),
          surfaceContainerLow: const Color(0xFF171C19),
          surfaceContainer: const Color(0xFF1B211D),
          surfaceContainerHigh: const Color(0xFF222824),
          surfaceContainerHighest: const Color(0xFF29302B),
        ),
    background: const Color(0xFF101310),
    navigation: const Color(0xFF131714),
    success: const Color(0xFF86D8A5),
  ),
  light: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFF176B4B),
          brightness: Brightness.light,
        ).copyWith(
          primary: const Color(0xFF176B4B),
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFFD0F2DF),
          onPrimaryContainer: const Color(0xFF083923),
          secondary: const Color(0xFF4F6A5D),
          onSecondary: Colors.white,
          secondaryContainer: const Color(0xFFD7E8DE),
          onSecondaryContainer: const Color(0xFF243A30),
          surface: const Color(0xFFFFFFFF),
          onSurface: const Color(0xFF172019),
          onSurfaceVariant: const Color(0xFF46534B),
          outline: const Color(0xFF68776E),
          outlineVariant: const Color(0xFFC5D0C8),
          error: const Color(0xFFBA1A1A),
          onError: Colors.white,
          errorContainer: const Color(0xFFFFDAD6),
          onErrorContainer: const Color(0xFF410002),
          surfaceContainerLowest: Colors.white,
          surfaceContainerLow: const Color(0xFFF0F5F1),
          surfaceContainer: const Color(0xFFEAF0EB),
          surfaceContainerHigh: const Color(0xFFE3EAE5),
          surfaceContainerHighest: const Color(0xFFDCE4DE),
        ),
    background: const Color(0xFFF6F9F6),
    navigation: const Color(0xFFF0F5F1),
    success: const Color(0xFF1E7A44),
  ),
);

// ---------------------------------------------------------------------------
// Catppuccin — Mocha (dark) and Latte (light), mauve-led.
// ---------------------------------------------------------------------------

final ThemePack _catppuccin = ThemePack(
  id: ThemePackId.catppuccin,
  label: themePackLabels[ThemePackId.catppuccin]!,
  tagline: 'Mocha and Latte, mauve-led',
  dark: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFFCBA6F7),
          brightness: Brightness.dark,
        ).copyWith(
          primary: const Color(0xFFCBA6F7),
          onPrimary: const Color(0xFF241839),
          primaryContainer: const Color(0xFF453A5F),
          onPrimaryContainer: const Color(0xFFE9DCFC),
          secondary: const Color(0xFFB4BEFE),
          onSecondary: const Color(0xFF191D3B),
          secondaryContainer: const Color(0xFF363A5C),
          onSecondaryContainer: const Color(0xFFDEE2FF),
          surface: const Color(0xFF1E1E2E),
          onSurface: const Color(0xFFCDD6F4),
          onSurfaceVariant: const Color(0xFFA6ADC8),
          outline: const Color(0xFF7F849C),
          outlineVariant: const Color(0xFF45475A),
          error: const Color(0xFFF38BA8),
          onError: const Color(0xFF3A101E),
          errorContainer: const Color(0xFF5C2536),
          onErrorContainer: const Color(0xFFF9C6D3),
          surfaceContainerLowest: const Color(0xFF11111B),
          surfaceContainerLow: const Color(0xFF252537),
          surfaceContainer: const Color(0xFF2A2A40),
          surfaceContainerHigh: const Color(0xFF313244),
          surfaceContainerHighest: const Color(0xFF45475A),
        ),
    background: const Color(0xFF181825),
    navigation: const Color(0xFF1B1B2B),
    success: const Color(0xFFA6E3A1),
  ),
  light: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFF8839EF),
          brightness: Brightness.light,
        ).copyWith(
          primary: const Color(0xFF8839EF),
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFFE4D3FC),
          onPrimaryContainer: const Color(0xFF3A1465),
          secondary: const Color(0xFF565FA8),
          onSecondary: Colors.white,
          secondaryContainer: const Color(0xFFDFE2FF),
          onSecondaryContainer: const Color(0xFF2A2F5E),
          surface: const Color(0xFFFDFDFE),
          onSurface: const Color(0xFF4C4F69),
          onSurfaceVariant: const Color(0xFF6C6F85),
          outline: const Color(0xFF8C8FA1),
          outlineVariant: const Color(0xFFCCD0DA),
          error: const Color(0xFFD20F39),
          onError: Colors.white,
          errorContainer: const Color(0xFFF8D7DE),
          onErrorContainer: const Color(0xFF7C0A22),
          surfaceContainerLowest: Colors.white,
          surfaceContainerLow: const Color(0xFFE6E9EF),
          surfaceContainer: const Color(0xFFDCE0E8),
          surfaceContainerHigh: const Color(0xFFCCD0DA),
          surfaceContainerHighest: const Color(0xFFBCC0CC),
        ),
    background: const Color(0xFFEFF1F5),
    navigation: const Color(0xFFE6E9EF),
    success: const Color(0xFF40A02B),
  ),
);

// ---------------------------------------------------------------------------
// Gruvbox — warm retro, orange-led.
// ---------------------------------------------------------------------------

final ThemePack _gruvbox = ThemePack(
  id: ThemePackId.gruvbox,
  label: themePackLabels[ThemePackId.gruvbox]!,
  tagline: 'Warm retro, orange-led',
  dark: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFFFE8019),
          brightness: Brightness.dark,
        ).copyWith(
          primary: const Color(0xFFFE8019),
          onPrimary: const Color(0xFF351A02),
          primaryContainer: const Color(0xFF5A3210),
          onPrimaryContainer: const Color(0xFFFFD8B0),
          secondary: const Color(0xFFFABD2F),
          onSecondary: const Color(0xFF352A05),
          secondaryContainer: const Color(0xFF5C4A16),
          onSecondaryContainer: const Color(0xFFFCE8B0),
          surface: const Color(0xFF282828),
          onSurface: const Color(0xFFEBDBB2),
          onSurfaceVariant: const Color(0xFFBDAE93),
          outline: const Color(0xFF928374),
          outlineVariant: const Color(0xFF504945),
          error: const Color(0xFFFB4934),
          onError: const Color(0xFF3A0A05),
          errorContainer: const Color(0xFF5C1A14),
          onErrorContainer: const Color(0xFFFDC5B8),
          surfaceContainerLowest: const Color(0xFF171919),
          surfaceContainerLow: const Color(0xFF2C2A28),
          surfaceContainer: const Color(0xFF32302C),
          surfaceContainerHigh: const Color(0xFF3C3836),
          surfaceContainerHighest: const Color(0xFF504945),
        ),
    background: const Color(0xFF1D2021),
    navigation: const Color(0xFF232120),
    success: const Color(0xFFB8BB26),
  ),
  light: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFFAF3A03),
          brightness: Brightness.light,
        ).copyWith(
          primary: const Color(0xFFAF3A03),
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFFF6D3AE),
          onPrimaryContainer: const Color(0xFF5A1E00),
          secondary: const Color(0xFFB57614),
          onSecondary: Colors.white,
          secondaryContainer: const Color(0xFFF3E0B2),
          onSecondaryContainer: const Color(0xFF4A3305),
          surface: const Color(0xFFFFFBEB),
          onSurface: const Color(0xFF3C3836),
          onSurfaceVariant: const Color(0xFF504945),
          outline: const Color(0xFF7C6F64),
          outlineVariant: const Color(0xFFD5C4A1),
          error: const Color(0xFF9D0006),
          onError: Colors.white,
          errorContainer: const Color(0xFFF5C6BC),
          onErrorContainer: const Color(0xFF5C0004),
          surfaceContainerLowest: const Color(0xFFFFFEF5),
          surfaceContainerLow: const Color(0xFFF2E5BC),
          surfaceContainer: const Color(0xFFEBDBB2),
          surfaceContainerHigh: const Color(0xFFD5C4A1),
          surfaceContainerHighest: const Color(0xFFBDAE93),
        ),
    background: const Color(0xFFFBF1C7),
    navigation: const Color(0xFFF2E5BC),
    success: const Color(0xFF79740E),
  ),
);

// ---------------------------------------------------------------------------
// Solarized — precise dual palette, blue-led.
// ---------------------------------------------------------------------------

final ThemePack _solarized = ThemePack(
  id: ThemePackId.solarized,
  label: themePackLabels[ThemePackId.solarized]!,
  tagline: 'The classic dual palette, blue-led',
  dark: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFF268BD2),
          brightness: Brightness.dark,
        ).copyWith(
          primary: const Color(0xFF268BD2),
          onPrimary: const Color(0xFF00212A),
          primaryContainer: const Color(0xFF0E4C74),
          onPrimaryContainer: const Color(0xFFBCDFF5),
          secondary: const Color(0xFF2AA198),
          onSecondary: const Color(0xFF00211F),
          secondaryContainer: const Color(0xFF0E4B47),
          onSecondaryContainer: const Color(0xFFC0EAE6),
          surface: const Color(0xFF002B36),
          onSurface: const Color(0xFF93A1A1),
          onSurfaceVariant: const Color(0xFF839496),
          outline: const Color(0xFF586E75),
          outlineVariant: const Color(0xFF0E4250),
          error: const Color(0xFFE4615F),
          onError: const Color(0xFF2D0505),
          errorContainer: const Color(0xFF5C1716),
          onErrorContainer: const Color(0xFFF7C9C8),
          surfaceContainerLowest: const Color(0xFF001B22),
          surfaceContainerLow: const Color(0xFF073642),
          surfaceContainer: const Color(0xFF0A3D4A),
          surfaceContainerHigh: const Color(0xFF104A5A),
          surfaceContainerHighest: const Color(0xFF1A5468),
        ),
    background: const Color(0xFF00212A),
    navigation: const Color(0xFF01313E),
    success: const Color(0xFF859900),
  ),
  light: ThemePalette(
    scheme:
        ColorScheme.fromSeed(
          seedColor: const Color(0xFF1E6FA8),
          brightness: Brightness.light,
        ).copyWith(
          primary: const Color(0xFF1E6FA8),
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFFCCE4F5),
          onPrimaryContainer: const Color(0xFF0A3A5C),
          secondary: const Color(0xFF1F7A72),
          onSecondary: Colors.white,
          secondaryContainer: const Color(0xFFCDEAE7),
          onSecondaryContainer: const Color(0xFF093F3A),
          surface: const Color(0xFFFFFDF6),
          onSurface: const Color(0xFF586E75),
          onSurfaceVariant: const Color(0xFF657B83),
          outline: const Color(0xFF93A1A1),
          outlineVariant: const Color(0xFFD5CDB4),
          error: const Color(0xFFDC322F),
          onError: Colors.white,
          errorContainer: const Color(0xFFF6CFCC),
          onErrorContainer: const Color(0xFF6E100E),
          surfaceContainerLowest: const Color(0xFFFFFEF9),
          surfaceContainerLow: const Color(0xFFEEE8D5),
          surfaceContainer: const Color(0xFFE6DFC8),
          surfaceContainerHigh: const Color(0xFFDDD6BE),
          surfaceContainerHighest: const Color(0xFFD0C8AC),
        ),
    background: const Color(0xFFFDF6E3),
    navigation: const Color(0xFFEEE8D5),
    success: const Color(0xFF6C7E00),
  ),
);
