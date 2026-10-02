import 'dart:math' as math;

import 'package:flutter/material.dart';

/// The colour roles of the visual language (docs/design/visual-language-2026-09-26.md
/// §3): every colour a kit part or a screen paints comes from one of these,
/// never a literal. The type, shape, spacing and patterns are fixed; the
/// colours are a theme the person chooses (a theme pack, later a custom
/// theme), so each theme supplies the whole set.
///
/// The meaning of a role never changes between themes, only its hue:
/// [attention] always means "needs you", [danger] and [dangerFill] always
/// mean an act that destroys or stops something, [accent] is the primary
/// action and "working".
///
/// Carried on the app's [ThemeData] as an extension; read it with [of].
@immutable
class ThemeRoles extends ThemeExtension<ThemeRoles> {
  const ThemeRoles({
    required this.brightness,
    required this.ground,
    required this.surface1,
    required this.surface2,
    required this.surface3,
    required this.hairline,
    required this.text1,
    required this.text2,
    required this.text3,
    required this.accent,
    required this.onAccent,
    required this.attention,
    required this.attentionFill,
    required this.onAttentionFill,
    required this.attentionSurface,
    required this.attentionLine,
    required this.danger,
    required this.dangerFill,
    required this.onDangerFill,
    required this.success,
    required this.scrim,
    required this.codeKeyword,
    required this.codeString,
    required this.codeType,
    required this.elevationShadow,
  });

  final Brightness brightness;

  /// The screen's background.
  final Color ground;

  /// Grouped panels and cards.
  final Color surface1;

  /// Sheets, the composer, the person's own message bubble, the dock.
  final Color surface2;

  /// Secondary buttons, chips and icon tiles.
  final Color surface3;

  /// Separators and borders. Translucent: it reads the same on every
  /// surface. Never use it with `withValues(alpha:)`, which would replace
  /// its alpha.
  final Color hairline;

  /// Primary text.
  final Color text1;

  /// Secondary text and section labels.
  final Color text2;

  /// Meta and placeholders, and the words on a disabled button: at least
  /// 4.5:1 on [ground] and on every surface, [surface3] included (a
  /// disabled primary or secondary button is [text3] on [surface3]).
  final Color text3;

  /// Primary buttons, working marks and links.
  final Color accent;

  /// Text and icons on [accent].
  final Color onAccent;

  /// "Needs you": text and icons.
  final Color attention;

  /// A filled "needs you" mark (the pending badge) and the text on it.
  final Color attentionFill;
  final Color onAttentionFill;

  /// The needs-you card: its surface and its border (both translucent).
  final Color attentionSurface;
  final Color attentionLine;

  /// Destructive icons and text.
  final Color danger;

  /// The one destructive button, inside a confirmation, and its text.
  final Color dangerFill;
  final Color onDangerFill;

  /// Done, connected, a diff's added lines.
  final Color success;

  /// Behind sheets and dialogs.
  final Color scrim;

  /// Code: keywords, strings and types. Added and removed lines are
  /// [success] and [danger] on 10 % of themselves ([codeAddedSurface],
  /// [codeRemovedSurface]).
  final Color codeKeyword;
  final Color codeString;
  final Color codeType;

  /// The one tight shadow under a floating surface (the dock, the composer,
  /// the top controls): 30 % black in both brightnesses.
  final Color elevationShadow;

  Color get codeAdded => success;
  Color get codeRemoved => danger;
  Color get codeAddedSurface => success.withValues(alpha: .10);
  Color get codeRemovedSurface => danger.withValues(alpha: .10);

  bool get isDark => brightness == Brightness.dark;

  /// The roles in force: the theme's extension, or ones derived from its
  /// colour scheme (a bare `ThemeData` in a test).
  static ThemeRoles of(BuildContext context) => resolve(Theme.of(context));

  /// The box a widget that failed to build shows instead
  /// (`ErrorWidget.builder` in app_diagnostics.dart). It may be drawn above
  /// any theme, so it reads no roles: these two fixed colours are its ground
  /// and its words.
  static const bootErrorGround = Color(0xFF201A18);
  static const bootErrorText = Color(0xFFFFDCCB);

  static ThemeRoles resolve(ThemeData theme) =>
      theme.extension<ThemeRoles>() ??
      deriveRoles(
        accent: theme.colorScheme.primary,
        ground: theme.scaffoldBackgroundColor,
        brightness: theme.brightness,
        text: theme.colorScheme.onSurface,
      );

  /// These roles with another [accent] (a theme pack's, or the person's
  /// own): [onAccent] is re-derived and the accent moved only as far as it
  /// takes to read on the ground.
  ThemeRoles withAccent(Color accent) {
    final a = _guardAccent(accent, [ground, surface1], brightness);
    return copyWith(accent: a, onAccent: onColor(a));
  }

  @override
  ThemeRoles copyWith({
    Color? ground,
    Color? surface1,
    Color? surface2,
    Color? surface3,
    Color? hairline,
    Color? text1,
    Color? text2,
    Color? text3,
    Color? accent,
    Color? onAccent,
    Color? attention,
    Color? attentionFill,
    Color? onAttentionFill,
    Color? attentionSurface,
    Color? attentionLine,
    Color? danger,
    Color? dangerFill,
    Color? onDangerFill,
    Color? success,
    Color? scrim,
    Color? codeKeyword,
    Color? codeString,
    Color? codeType,
    Color? elevationShadow,
  }) => ThemeRoles(
    brightness: brightness,
    ground: ground ?? this.ground,
    surface1: surface1 ?? this.surface1,
    surface2: surface2 ?? this.surface2,
    surface3: surface3 ?? this.surface3,
    hairline: hairline ?? this.hairline,
    text1: text1 ?? this.text1,
    text2: text2 ?? this.text2,
    text3: text3 ?? this.text3,
    accent: accent ?? this.accent,
    onAccent: onAccent ?? this.onAccent,
    attention: attention ?? this.attention,
    attentionFill: attentionFill ?? this.attentionFill,
    onAttentionFill: onAttentionFill ?? this.onAttentionFill,
    attentionSurface: attentionSurface ?? this.attentionSurface,
    attentionLine: attentionLine ?? this.attentionLine,
    danger: danger ?? this.danger,
    dangerFill: dangerFill ?? this.dangerFill,
    onDangerFill: onDangerFill ?? this.onDangerFill,
    success: success ?? this.success,
    scrim: scrim ?? this.scrim,
    codeKeyword: codeKeyword ?? this.codeKeyword,
    codeString: codeString ?? this.codeString,
    codeType: codeType ?? this.codeType,
    elevationShadow: elevationShadow ?? this.elevationShadow,
  );

  @override
  ThemeRoles lerp(covariant ThemeRoles? other, double t) {
    if (other == null) return this;
    Color c(Color a, Color b) => Color.lerp(a, b, t)!;
    return ThemeRoles(
      brightness: t < .5 ? brightness : other.brightness,
      ground: c(ground, other.ground),
      surface1: c(surface1, other.surface1),
      surface2: c(surface2, other.surface2),
      surface3: c(surface3, other.surface3),
      hairline: c(hairline, other.hairline),
      text1: c(text1, other.text1),
      text2: c(text2, other.text2),
      text3: c(text3, other.text3),
      accent: c(accent, other.accent),
      onAccent: c(onAccent, other.onAccent),
      attention: c(attention, other.attention),
      attentionFill: c(attentionFill, other.attentionFill),
      onAttentionFill: c(onAttentionFill, other.onAttentionFill),
      attentionSurface: c(attentionSurface, other.attentionSurface),
      attentionLine: c(attentionLine, other.attentionLine),
      danger: c(danger, other.danger),
      dangerFill: c(dangerFill, other.dangerFill),
      onDangerFill: c(onDangerFill, other.onDangerFill),
      success: c(success, other.success),
      scrim: c(scrim, other.scrim),
      codeKeyword: c(codeKeyword, other.codeKeyword),
      codeString: c(codeString, other.codeString),
      codeType: c(codeType, other.codeType),
      elevationShadow: c(elevationShadow, other.elevationShadow),
    );
  }
}

// ---------------------------------------------------------------------------
// Graphite: the default theme (the spec's §3 palette).
// ---------------------------------------------------------------------------

/// The default theme, dark. The spec's values, except where a value missed
/// its contrast floor (noted inline).
const graphiteDark = ThemeRoles(
  brightness: Brightness.dark,
  ground: Color(0xFF0B0C0E),
  surface1: Color(0xFF141518),
  surface2: Color(0xFF1B1C20),
  surface3: Color(0xFF26282D),
  hairline: Color(0x12FFFFFF), // rgba(255,255,255,.07)
  text1: Color(0xFFF3F3F1),
  text2: Color(0xFFA3A5AB),
  // Spec #8A8D94 reads at 4.44:1 on surface3, where a disabled button puts
  // it; one step lighter reaches 4.5:1 (4.55:1).
  text3: Color(0xFF8C8F96),
  accent: Color(0xFF3DDC8A),
  onAccent: Color(0xFF03140B),
  attention: Color(0xFFFFB547),
  attentionFill: Color(0xFFFFB547),
  onAttentionFill: Color(0xFF1A1000),
  attentionSurface: Color(0x17FFB547), // .09
  attentionLine: Color(0x4DFFB547), // .30
  danger: Color(0xFFFF7A7A),
  // Spec #E5484D carries white text at 3.9:1; this is the nearest red with
  // white at 4.5:1.
  dangerFill: Color(0xFFD93B40),
  onDangerFill: Color(0xFFFFFFFF),
  success: Color(0xFF3DDC8A),
  scrim: Color(0x94000000), // .58
  codeKeyword: Color(0xFFC7A6FF),
  codeString: Color(0xFFFFB88A),
  codeType: Color(0xFF7FD1FF),
  elevationShadow: Color(0x4D000000), // black .30
);

/// The default theme, light.
const graphiteLight = ThemeRoles(
  brightness: Brightness.light,
  ground: Color(0xFFF3F3F1),
  surface1: Color(0xFFFFFFFF),
  surface2: Color(0xFFFFFFFF),
  surface3: Color(0xFFE9E9E6),
  hairline: Color(0x14000000), // rgba(0,0,0,.08)
  text1: Color(0xFF111214),
  text2: Color(0xFF50535A),
  text3: Color(0xFF62656C),
  // Spec #0B8A4A reads at 4.0:1 on the ground and carries white at 4.4:1;
  // one step deeper reaches 4.5:1 for both (links are text).
  accent: Color(0xFF087F43),
  onAccent: Color(0xFFFFFFFF),
  attention: Color(0xFF9A5700),
  attentionFill: Color(0xFFFFB547),
  onAttentionFill: Color(0xFF1A1000),
  attentionSurface: Color(0x1FFFA600), // rgba(255,166,0,.12)
  attentionLine: Color(0x4DBE6E00), // rgba(190,110,0,.30)
  danger: Color(0xFFC62828),
  dangerFill: Color(0xFFD93B40),
  onDangerFill: Color(0xFFFFFFFF),
  success: Color(0xFF087F43),
  scrim: Color(0x94000000),
  codeKeyword: Color(0xFF6D4AFF),
  codeString: Color(0xFFB4480B),
  codeType: Color(0xFF0B6BA8),
  elevationShadow: Color(0x4D000000), // black .30 in both brightnesses
);

/// The accents the canvas offers for Graphite, dark and light (§3, theme
/// packs): green (the default), blue, teal, violet. For a later accent
/// picker: `graphiteDark.withAccent(graphiteAccents[1].dark)`.
///
/// Teal replaced orange (owner decision B15): amber means only "needs
/// you", so no accent may sit near it (LOOK-39: at least 30° of hue and a
/// CIEDE2000 ΔE of 20 from attention and danger; [accentKeepsMeaning]). Teal is hue 180–182°,
/// carries its on-colour at 5.6:1 or more and reads on the ground and
/// surface1 at 5:1 or more in both brightnesses (LOOK-8;
/// test/theme_roles_test.dart).
const graphiteAccents = <({String name, Color dark, Color light})>[
  (name: 'green', dark: Color(0xFF3DDC8A), light: Color(0xFF087F43)),
  (name: 'blue', dark: Color(0xFF5AB0FF), light: Color(0xFF1F6FEB)),
  (name: 'teal', dark: Color(0xFF3CCFCF), light: Color(0xFF0D7377)),
  (name: 'violet', dark: Color(0xFFC7A6FF), light: Color(0xFF6D4AFF)),
];

// ---------------------------------------------------------------------------
// LOOK-39: an accent keeps its meaning. The one check every accent passes
// (a Graphite accent, a pack's, the custom theme's picker in KitSwatch.accent,
// test/theme_roles_test.dart): amber means only "needs you" and red only
// "destroys or stops", so no accent may sit near either.
// ---------------------------------------------------------------------------

/// The least hue distance, in degrees, an accent keeps from [ThemeRoles.attention]
/// and [ThemeRoles.danger] (LOOK-39).
const double accentMinHueDistance = 30;

/// The least CIEDE2000 colour difference an accent keeps from
/// [ThemeRoles.attention] and [ThemeRoles.danger] (LOOK-39).
const double accentMinDeltaE = 20;

/// Whether [accent] keeps its meaning in [roles] (LOOK-39): at least
/// [accentMinHueDistance] of hue and a CIEDE2000 ΔE of [accentMinDeltaE]
/// from both [ThemeRoles.attention] and [ThemeRoles.danger], so it never
/// reads as "needs you" or as a destructive act.
bool accentKeepsMeaning(Color accent, ThemeRoles roles) {
  for (final other in [roles.attention, roles.danger]) {
    if (hueDistance(accent, other) < accentMinHueDistance) return false;
    if (deltaE2000(accent, other) < accentMinDeltaE) return false;
  }
  return true;
}

/// The distance between two colours' hues on the colour wheel, 0–180°.
double hueDistance(Color a, Color b) {
  final d = (HSVColor.fromColor(a).hue - HSVColor.fromColor(b).hue).abs();
  return d > 180 ? 360 - d : d;
}

/// CIEDE2000 colour difference of two opaque sRGB colours.
double deltaE2000(Color a, Color b) => deltaE2000Lab(_lab(a), _lab(b));

/// CIE L*a*b* (D65) of an opaque sRGB colour.
List<double> _lab(Color c) {
  double lin(double v) =>
      v <= 0.04045 ? v / 12.92 : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
  final r = lin(c.r), g = lin(c.g), b = lin(c.b);
  final x = (r * .4124564 + g * .3575761 + b * .1804375) / .95047;
  final y = r * .2126729 + g * .7151522 + b * .0721750;
  final z = (r * .0193339 + g * .1191920 + b * .9503041) / 1.08883;
  double f(double t) =>
      t > 0.008856 ? math.pow(t, 1 / 3).toDouble() : 7.787 * t + 16 / 116;
  final fx = f(x), fy = f(y), fz = f(z);
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}

/// CIEDE2000 of two L*a*b* colours (Sharma, Wu and Dalal 2005); exposed
/// so a test can hold it to the paper's reference pairs.
@visibleForTesting
double deltaE2000Lab(List<double> lab1, List<double> lab2) {
  double rad(double deg) => deg * math.pi / 180;
  double deg(double rad) => rad * 180 / math.pi;
  final [l1, a1, b1] = lab1;
  final [l2, a2, b2] = lab2;
  final cBar =
      (math.sqrt(a1 * a1 + b1 * b1) + math.sqrt(a2 * a2 + b2 * b2)) / 2;
  final cBar7 = math.pow(cBar, 7);
  final g = .5 * (1 - math.sqrt(cBar7 / (cBar7 + math.pow(25, 7))));
  final a1p = (1 + g) * a1, a2p = (1 + g) * a2;
  final c1p = math.sqrt(a1p * a1p + b1 * b1);
  final c2p = math.sqrt(a2p * a2p + b2 * b2);
  final h1p = (deg(math.atan2(b1, a1p)) + 360) % 360;
  final h2p = (deg(math.atan2(b2, a2p)) + 360) % 360;
  final dLp = l2 - l1;
  final dCp = c2p - c1p;
  var dh = h2p - h1p;
  if (c1p * c2p == 0) {
    dh = 0;
  } else if (dh > 180) {
    dh -= 360;
  } else if (dh < -180) {
    dh += 360;
  }
  final dHp = 2 * math.sqrt(c1p * c2p) * math.sin(rad(dh / 2));
  final lBarp = (l1 + l2) / 2;
  final cBarp = (c1p + c2p) / 2;
  double hBarp;
  if (c1p * c2p == 0) {
    hBarp = h1p + h2p;
  } else if ((h1p - h2p).abs() <= 180) {
    hBarp = (h1p + h2p) / 2;
  } else if (h1p + h2p < 360) {
    hBarp = (h1p + h2p + 360) / 2;
  } else {
    hBarp = (h1p + h2p - 360) / 2;
  }
  final t =
      1 -
      .17 * math.cos(rad(hBarp - 30)) +
      .24 * math.cos(rad(2 * hBarp)) +
      .32 * math.cos(rad(3 * hBarp + 6)) -
      .20 * math.cos(rad(4 * hBarp - 63));
  final dTheta = 30 * math.exp(-math.pow((hBarp - 275) / 25, 2));
  final cBarp7 = math.pow(cBarp, 7);
  final rc = 2 * math.sqrt(cBarp7 / (cBarp7 + math.pow(25, 7)));
  final sl =
      1 +
      .015 * math.pow(lBarp - 50, 2) / math.sqrt(20 + math.pow(lBarp - 50, 2));
  final sc = 1 + .045 * cBarp;
  final sh = 1 + .015 * cBarp * t;
  final rt = -math.sin(rad(2 * dTheta)) * rc;
  return math.sqrt(
    math.pow(dLp / sl, 2) +
        math.pow(dCp / sc, 2) +
        math.pow(dHp / sh, 2) +
        rt * (dCp / sc) * (dHp / sh),
  );
}

// ---------------------------------------------------------------------------
// Deriving a whole role set from a few colours: every non-default theme,
// and the seam for a custom theme (the person picks an accent and a ground).
// ---------------------------------------------------------------------------

/// WCAG contrast ratio of two opaque colours.
double contrastRatio(Color a, Color b) {
  final la = a.computeLuminance();
  final lb = b.computeLuminance();
  final hi = la > lb ? la : lb;
  final lo = la > lb ? lb : la;
  return (hi + .05) / (lo + .05);
}

/// Text that reads on [fill]: near-black ink or white, whichever measures
/// better.
Color onColor(Color fill) {
  const ink = Color(0xFF111214);
  return contrastRatio(ink, fill) >= contrastRatio(Colors.white, fill)
      ? ink
      : Colors.white;
}

Color _mix(Color a, Color b, double t) => Color.lerp(a, b, t)!;

/// [color] moved toward [toward] only as far as it takes to reach [min]
/// against every one of [grounds]; kept as is when it already reads.
Color readableOn(
  Color color,
  List<Color> grounds,
  double min, {
  required Color toward,
}) {
  var result = color;
  for (var i = 0; i <= 50; i++) {
    result = _mix(color, toward, i / 50);
    if (grounds.every((g) => contrastRatio(result, g) >= min)) break;
  }
  return result;
}

/// The accent reads as text on the ground (4.5:1, links are text) and
/// carries its own on-colour at 4.5:1.
Color _guardAccent(Color accent, List<Color> grounds, Brightness brightness) {
  final far = brightness == Brightness.dark ? Colors.white : Colors.black;
  var result = readableOn(accent, grounds, 4.5, toward: far);
  // A mid-tone accent carries neither ink nor white at 4.5:1: deepen it
  // (light) or brighten it (dark) until one of them does.
  for (var i = 0; i < 40; i++) {
    if (contrastRatio(onColor(result), result) >= 4.5) break;
    result = _mix(result, far, .04);
  }
  return result;
}

/// Every role from an [accent], a [ground] and a [brightness], with the
/// contrast floors built in: text roles reach 4.5:1 (primary text 7:1) on
/// the ground and on every surface they sit on, the accent reads as a link
/// and carries its on-colour, attention and danger read as text.
///
/// [text], [attention], [danger] and [success] take a theme's own hues
/// where it has them (a palette's documented foreground, red and green);
/// they are moved only as far as their floor needs.
ThemeRoles deriveRoles({
  required Color accent,
  required Color ground,
  required Brightness brightness,
  Color? text,
  Color? attention,
  Color? danger,
  Color? success,
}) {
  final dark = brightness == Brightness.dark;
  final far = dark ? Colors.white : Colors.black;
  final base = text ?? (dark ? graphiteDark.text1 : graphiteLight.text1);
  final Color surface1;
  final Color surface2;
  final Color surface3;
  if (dark) {
    surface1 = _mix(ground, base, .035);
    surface2 = _mix(ground, base, .065);
    surface3 = _mix(ground, base, .11);
  } else {
    surface1 = _mix(ground, Colors.white, .8);
    surface2 = surface1;
    surface3 = _mix(ground, base, .05);
  }
  final surfaces = [ground, surface1, surface2, surface3];
  final defaults = dark ? graphiteDark : graphiteLight;
  // The needs-you card is attention at 9–12 % over a panel: its words must
  // still read there.
  final attentionCard = Color.alphaBlend(defaults.attentionSurface, surface1);
  final text1 = readableOn(base, [...surfaces, attentionCard], 7, toward: far);
  final text2 = readableOn(
    _mix(text1, ground, dark ? .36 : .30),
    surfaces,
    4.5,
    toward: far,
  );
  // Disabled buttons put text3 on surface3, so it reads there too.
  final text3 = readableOn(
    _mix(text1, ground, dark ? .46 : .40),
    surfaces,
    4.5,
    toward: far,
  );
  final a = _guardAccent(accent, [ground, surface1], brightness);
  final attentionHue = attention ?? defaults.attention;
  final att = readableOn(
    attentionHue,
    [ground, surface1, attentionCard],
    4.5,
    toward: far,
  );
  final dangerHue = danger ?? defaults.danger;
  final dng = readableOn(
    dangerHue,
    [ground, surface1, surface2],
    4.5,
    toward: far,
  );
  final fill = readableOn(
    defaults.dangerFill,
    [Colors.white],
    4.5,
    toward: Colors.black,
  );
  final ok = readableOn(
    success ?? defaults.success,
    [ground, surface1],
    4.5,
    toward: far,
  );
  Color code(Color hue) =>
      readableOn(hue, [surface1, surface2], 4.5, toward: far);
  return ThemeRoles(
    brightness: brightness,
    ground: ground,
    surface1: surface1,
    surface2: surface2,
    surface3: surface3,
    hairline: defaults.hairline,
    text1: text1,
    text2: text2,
    text3: text3,
    accent: a,
    onAccent: onColor(a),
    attention: att,
    attentionFill: defaults.attentionFill,
    onAttentionFill: defaults.onAttentionFill,
    attentionSurface: defaults.attentionSurface,
    attentionLine: defaults.attentionLine,
    danger: dng,
    dangerFill: fill,
    onDangerFill: Colors.white,
    success: ok,
    scrim: defaults.scrim,
    codeKeyword: code(defaults.codeKeyword),
    codeString: code(defaults.codeString),
    codeType: code(defaults.codeType),
    elevationShadow: defaults.elevationShadow,
  );
}

/// A Material [ColorScheme] that says the same as [roles], so a stock
/// Material widget no screen restyled (a date picker, a menu) still looks
/// like the rest. Hues the roles do not name (Material's secondary and
/// tertiary families) are mapped onto the nearest role: secondary to the
/// neutral steps, tertiary to attention.
ColorScheme schemeFromRoles(ThemeRoles roles, {ColorScheme? base}) {
  final r = roles;
  final seed =
      base ??
      ColorScheme.fromSeed(seedColor: r.accent, brightness: r.brightness);
  final flat = Color.alphaBlend(r.hairline, r.surface1);
  return seed.copyWith(
    brightness: r.brightness,
    primary: r.accent,
    onPrimary: r.onAccent,
    primaryContainer: Color.alphaBlend(
      r.accent.withValues(alpha: r.isDark ? .16 : .14),
      r.surface1,
    ),
    onPrimaryContainer: r.text1,
    primaryFixed: r.accent,
    onPrimaryFixed: r.onAccent,
    secondary: r.text2,
    onSecondary: r.ground,
    secondaryContainer: r.surface3,
    onSecondaryContainer: r.text1,
    tertiary: r.attention,
    onTertiary: r.onAttentionFill,
    tertiaryContainer: Color.alphaBlend(r.attentionSurface, r.surface1),
    onTertiaryContainer: r.text1,
    error: r.danger,
    onError: r.isDark ? r.ground : Colors.white,
    errorContainer: Color.alphaBlend(
      r.danger.withValues(alpha: .14),
      r.surface1,
    ),
    onErrorContainer: r.text1,
    surface: r.ground,
    onSurface: r.text1,
    onSurfaceVariant: r.text2,
    surfaceDim: r.ground,
    surfaceBright: r.surface3,
    surfaceContainerLowest: r.ground,
    surfaceContainerLow: r.surface1,
    surfaceContainer: r.surface2,
    surfaceContainerHigh: r.isDark
        ? _mix(r.surface2, r.surface3, .5)
        : r.surface2,
    surfaceContainerHighest: r.surface3,
    outline: r.text3,
    outlineVariant: flat,
    inverseSurface: r.text1,
    onInverseSurface: r.ground,
    inversePrimary: r.accent,
    shadow: Colors.black,
    scrim: Colors.black,
    surfaceTint: Colors.transparent,
  );
}
