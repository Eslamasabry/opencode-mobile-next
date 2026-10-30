import 'package:flutter/widgets.dart';

import '../../theme_roles.dart';
import '../kit_effects.dart';
import '../kit_shape.dart' show KitSurfaceLevel;
import '../kit_surface_look.dart' show KitSurfaceDecoration, KitSurfaceRole;
import 'kit_style_brutal.dart';
import 'kit_style_paper.dart';
import 'kit_style_signal.dart';
import 'kit_style_soft.dart';
import 'kit_style_solid.dart';

/// The design schools behind Settings › Appearance › Style
/// (docs/design/design-schools/contract.md): one [KitStyleSpec] per
/// [KitSurfaceStyle], each a token object plus the one function that turns
/// a surface request into a decoration. [KitSurfaceLook] asks the spec;
/// kit parts ask [KitSurfaceLook]; screens never know a style exists.
///
/// Adding a style: append it to [KitSurfaceStyle], write its spec file in
/// this folder and add one line to [kitStyleSpec]. No part or screen
/// changes.
KitStyleSpec kitStyleSpec(KitSurfaceStyle style) =>
    debugKitStyleSpecs?.call(style) ?? _registered(style);

/// Tests only: a spec to use in place of a registered one (null keeps the
/// registered spec). Lets a hook's test drive it with a made-up style.
@visibleForTesting
KitStyleSpec? Function(KitSurfaceStyle style)? debugKitStyleSpecs;

KitStyleSpec _registered(KitSurfaceStyle style) => switch (style) {
  KitSurfaceStyle.solid => const KitStyleSolid(),
  KitSurfaceStyle.soft => const KitStyleSoft(),
  KitSurfaceStyle.brutal => const KitStyleBrutal(),
  KitSurfaceStyle.paper => const KitStylePaper(),
  KitSurfaceStyle.signal => const KitStyleSignal(),
};

/// What a style fixes for every part: its Fine-tune defaults and the
/// tokens the resolver and [KitTokens] read.
@immutable
class KitStyleTokens {
  const KitStyleTokens({
    required this.defaults,
    this.sharpRadius = 2,
    this.keepsUnderHighContrast = false,
    this.dependsOnDepth = false,
    this.type = const KitStyleType(),
    this.live = const KitLivePolicy(),
    this.glow = const KitStyleGlow(),
    this.flattensNested = false,
    this.keepsNestedSurfaces = false,
    this.dividerWidth,
    this.needsYou = const KitNeedsYouLook(),
    this.linkUnderline,
    this.fieldHalo,
    this.flatLists = false,
    this.track = false,
  });

  /// The Fine-tune knobs this style starts at (contract §2.3).
  final KitKnobs defaults;

  /// The radius Corners › Sharp gives a box in this style (Paper: 0).
  final double sharpRadius;

  /// Whether the style stays itself when the platform asks for high
  /// contrast (Brutal and Paper already are); otherwise it is Solid then.
  final bool keepsUnderHighContrast;

  /// Whether the style's shapes come from its shadows (Soft): Depth › Flat
  /// then turns Edges on, so no surface loses its boundary.
  final bool dependsOnDepth;

  /// The style's type tweaks (hook H1, applied by `KitText.styleOf`).
  final KitStyleType type;

  /// How the live background shows under this style (hook H7).
  final KitLivePolicy live;

  /// How the message box's running glow is drawn (hook H8).
  final KitStyleGlow glow;

  /// A surface inside a surface that casts casts nothing itself (hook H9):
  /// only the outer surface throws a shadow.
  final bool flattensNested;

  /// Whether a content surface inside a content surface paints its own
  /// face (Solid, as it always has). Every other style puts it on the outer
  /// one's face instead (the nesting rule, `KitSurfaceLook.decorate`), so a
  /// card never holds a pane inside a pane.
  final bool keepsNestedSurfaces;

  /// A divider's width in logical pixels (the `divider` role, read by
  /// `KitSurfaceLook.divider`); null keeps the part's own hairline.
  final double? dividerWidth;

  /// How a request card says "needs you": the tag, the tint, the ring and
  /// the start edge (contract F2; read by `kitRequestFrame` and the card's
  /// caption).
  final KitNeedsYouLook needsYou;

  /// The underline under a tertiary action's (a link's) words, as a
  /// multiple of the words' own stroke; 0 draws none. Null: as the
  /// `divider` role draws a line (Brutal's 2 dp ink, Paper's rule), none
  /// where it draws none. Read by `KitSurfaceLook.linkUnderline`.
  final double? linkUnderline;

  /// Whether a glowing field (the ask field, the start box) keeps its
  /// halo around the style's own face. Null: as [glow]'s halo. Solid at its
  /// defaults always paints the field's own look. Read by
  /// [fieldHaloShown].
  final bool? fieldHalo;

  /// [fieldHalo] decided.
  bool get fieldHaloShown => fieldHalo ?? glow.halo;

  /// Whether lists sit straight on the page, as in the phone's own
  /// Settings (Solid, owner 2026-10-26: "flat lists with dividers, fewer
  /// boxes"): a row group, a choice list and the big choices have no
  /// panel, fill or edge of their own; rows run the page's width, parted
  /// by inset hairlines, groups by space and their label; a row's icon
  /// stands without a tile. Read by `KitSurfaceLook.flatLists`.
  final bool flatLists;

  /// Whether lists and the transcript run on one track down their start
  /// edge, with signal lights as the only status colour (Signal): a feed
  /// row's state is a lamp on the track, a step's node a signal, the
  /// running time a segment timer, a request a printed slip. Read by
  /// `KitTrack.on`; every part keeps its own look elsewhere.
  final bool track;
}

/// How a request card that needs the person is told apart (contract F2,
/// the needs-you look). Every null follows today's rule, which reads the
/// glow ([KitStyleGlow.halo]): with a halo, the full card is tinted and
/// ringed; without one, it is calm with the start edge alone. The question
/// look is always calm. Read through [KitNeedsYouLook.decide].
@immutable
class KitNeedsYouLook {
  const KitNeedsYouLook({
    this.tag = KitNeedsYouTag.caption,
    this.tint,
    this.ring,
    this.startEdge,
  });

  /// How the words "Needs you" are set: an amber caption (today), or an
  /// amber pill in the style's own control look (Brutal: with an ink
  /// edge). Either way only while the request waits.
  final KitNeedsYouTag tag;

  /// The attention wash on the full card's face. Null: as the glow's halo.
  final bool? tint;

  /// The attention line around the card (the question look too) and, on
  /// the full card, the faint 4 dp ring outside it. Null: as the glow's
  /// halo.
  final bool? ring;

  /// The 4 dp attention edge at the card's start. Null: wherever the card
  /// is not ringed (the question look; the full card without a ring).
  final bool? startEdge;

  /// The look decided for a card under a style whose glow has [halo]: its
  /// tint, its attention [line] over a style's own face, the [ring]
  /// outside, the start edge and the pill. The [question] look is never
  /// tinted or ringed (it keeps the line and the start edge).
  ({bool tint, bool line, bool ring, bool startEdge, bool pill}) decide({
    required bool halo,
    bool question = false,
  }) {
    final line = this.ring ?? halo;
    final ring = !question && line;
    return (
      tint: !question && (tint ?? halo),
      line: line,
      ring: ring,
      startEdge: startEdge ?? !ring,
      pill: tag == KitNeedsYouTag.pill,
    );
  }
}

/// How the words "Needs you" are set on a request card.
enum KitNeedsYouTag {
  /// The attention caption (amber words), as every style had it.
  caption,

  /// A pill in the attention fill with its own words, drawn as the style's
  /// control (`control` with the fill as its colour).
  pill,
}

/// How the live background shows under a style (contract §3.11). Null
/// keeps the person's Intensity; 0 hides the look (the ground stays).
@immutable
class KitLivePolicy {
  const KitLivePolicy({this.maxStrength, this.offWhenChosen = false});

  /// Choosing this style turns the live background off (owner Q4: "print
  /// does not move"); it stays the person's switch, so they can turn it
  /// back on ([KitStyles.choose]).
  final bool offWhenChosen;

  /// The strongest the look may be, as a [KitLiveIntensity.strength]
  /// multiplier (Subtle is 0.7).
  final double? maxStrength;

  /// [strength] under this policy.
  double cap(double strength) {
    final max = maxStrength;
    return max == null || strength <= max ? strength : max;
  }
}

/// How the running glow around the message box is drawn (contract §3.10).
@immutable
class KitStyleGlow {
  const KitStyleGlow({this.halo = true, this.lineWidth});

  /// The soft wash or breathing halo under the line. Off: the line alone
  /// (Brutal's hard ring, Paper's rule).
  final bool halo;

  /// The line's width in logical pixels; null keeps the glow's own.
  final double? lineWidth;
}

/// A style's type tweaks over the kit's type scale (contract §3.6). A null
/// keeps the role's own value. Read by the school agents' `KitText` hook;
/// the baseline does not apply them yet.
@immutable
class KitStyleType {
  const KitStyleType({
    this.displayWeight,
    this.displayTracking,
    this.largeTitleSize,
    this.largeTitleWeight,
    this.titleSize,
    this.bodyWeight,
    this.monoLabels = false,
    this.engravedLabels = false,
    this.firmWeights = false,
  });

  final double? displayWeight;
  final double? displayTracking;
  final double? largeTitleSize;

  /// The large title's own weight, over [displayWeight] (Signal's page
  /// title is set heavier than its headlines).
  final double? largeTitleWeight;

  /// The page title's size in the top bar (the headline role there only;
  /// a card's headline keeps the role's own).
  final double? titleSize;
  final double? bodyWeight;

  /// Section labels and captions in the mono face (sentence case). The
  /// label role elsewhere (a segment's or a tab's words, a field's label)
  /// stays in the sans face.
  final bool monoLabels;

  /// Section labels engraved: small capitals, widely tracked, in the
  /// label's weight (Signal). The words are set in capitals by the
  /// section label itself (`KitSectionLabel`); a screen reader still reads
  /// them as written. Arabic has no capitals and keeps zero tracking.
  final bool engravedLabels;

  /// The firmer weights from before the 2026-10-30 unslop pass (titles 650,
  /// headline, label, caption and button 600, secondary 500): every Style
  /// with a look of its own keeps them; Solid's text is lighter.
  final bool firmWeights;

  /// Whether these tweaks change nothing.
  bool get isNone =>
      displayWeight == null &&
      displayTracking == null &&
      largeTitleSize == null &&
      largeTitleWeight == null &&
      titleSize == null &&
      bodyWeight == null &&
      !monoLabels &&
      !engravedLabels &&
      !firmWeights;
}

/// One surface to resolve: the part's own [solid] look and where it sits.
@immutable
class KitSurfaceRequest {
  const KitSurfaceRequest({
    required this.roles,
    required this.role,
    required this.solid,
    required this.knobs,
    this.level = KitSurfaceLevel.surface1,
    this.radius = BorderRadius.zero,
    this.circle = false,
    this.color,
    this.selected = false,
    this.pressed = false,
    this.hovered = false,
    this.disabled = false,
    this.withinGroup = false,
    this.edgeWidth = 1,
    this.action = false,
  });

  final ThemeRoles roles;
  final KitSurfaceRole role;

  /// The part's own decoration: what Solid paints, unchanged.
  final Decoration solid;

  /// The Fine-tune knobs in force.
  final KitKnobs knobs;
  final KitSurfaceLevel level;
  final BorderRadius radius;
  final bool circle;

  /// A part's own fill (an accent button, a selected chip), kept as it is.
  final Color? color;
  final bool selected;
  final bool pressed;

  /// A fine pointer rests on it (a field's hover). A spec that ignores it
  /// keeps the resting look, as every style but Solid had it.
  final bool hovered;

  /// The control cannot be used now (a disabled button). A spec that
  /// ignores it draws the control as when it can (today's look); a style
  /// whose resting control casts may want it flat, so it never reads as a
  /// secondary.
  final bool disabled;

  /// A `divider` between rows of one group (inset to the words), not
  /// between groups or sections (edge to edge). A spec that ignores it
  /// draws both alike, as every style had it.
  final bool withinGroup;

  /// The part's hairline, in logical pixels.
  final double edgeWidth;

  /// The control is an action the person presses (a button, Send), not a
  /// mark or a choice (a status tag, a chosen segment). A spec that ignores
  /// it draws both alike, as every style had it; Signal makes an action
  /// with a face of its own a raised key.
  final bool action;

  /// The surface step's colour.
  Color get levelFill => switch (level) {
    KitSurfaceLevel.ground => roles.ground,
    KitSurfaceLevel.surface1 => roles.surface1,
    KitSurfaceLevel.surface2 => roles.surface2,
    KitSurfaceLevel.surface3 => roles.surface3,
  };
}

/// A design school: its tokens and its resolver.
abstract class KitStyleSpec {
  const KitStyleSpec();

  KitStyleTokens get tokens;

  /// The decoration for [request]. Solid returns `request.solid` itself at
  /// its defaults, so a solid screen is exactly what it was.
  ///
  /// A [KitSurfaceDecoration] may also carry `faceShift` (hook H2: the face
  /// and its content sink into the shadow while pressed).
  Decoration resolve(KitSurfaceRequest request);

  /// The words' colour on a surface (hook H3): a chosen chip, segment or
  /// tab, a button. Null keeps the part's own colour.
  Color? onColor(KitSurfaceRequest request) => null;

  /// The press or hover highlight on a control with a fill of its own
  /// (`stateLayer` asked with `color`: the accent primary), apart from the
  /// one on the style's own face, so a style's opaque highlight never greys
  /// the accent. Null keeps the part's own layer (`request.solid`), as
  /// every style had it.
  Decoration? resolveFilledLayer(KitSurfaceRequest request) => null;

  /// The theme's roles under this style (hook H6): only `ground`,
  /// `surface1`–`surface3`, `hairline`, `scrim` and `elevationShadow` may
  /// change (contract §3.3); the accent guard re-runs after. Identity keeps
  /// the theme as it is.
  ThemeRoles adjustRoles(ThemeRoles roles) => roles;
}

/// Reading the style and the knobs in force.
abstract final class KitStyles {
  /// The style in force here: [KitEffects.surfaceStyle], made Solid when
  /// the platform asks for high contrast unless the style keeps itself.
  static KitSurfaceStyle styleOf(BuildContext context) {
    final style = KitEffects.of(context).surfaceStyle;
    final highContrast = MediaQuery.maybeHighContrastOf(context) ?? false;
    return effectiveStyle(style, highContrast: highContrast);
  }

  /// [styleOf] without a context.
  static KitSurfaceStyle effectiveStyle(
    KitSurfaceStyle style, {
    required bool highContrast,
  }) {
    if (highContrast && !kitStyleSpec(style).tokens.keepsUnderHighContrast) {
      return KitSurfaceStyle.solid;
    }
    return style;
  }

  /// [effects] with [style] chosen: Fine-tune back at the style's
  /// defaults, and the live background off when the style starts without
  /// it ([KitLivePolicy.offWhenChosen]). What the Style picker writes.
  static KitEffects choose(KitEffects effects, KitSurfaceStyle style) {
    final next = effects.withSurfaceStyle(style);
    if (style == effects.surfaceStyle) return next;
    return kitStyleSpec(style).tokens.live.offWhenChosen
        ? next.copyWith(liveBackground: false)
        : next;
  }

  /// The Fine-tune knobs in force here.
  static KitKnobs knobsOf(BuildContext context) {
    final effects = KitEffects.of(context);
    return knobsFor(
      style: effects.surfaceStyle,
      fineTune: effects.fineTune,
      highContrast: MediaQuery.maybeHighContrastOf(context) ?? false,
    );
  }

  /// [knobsOf] without a context (contract §2.4): the person's choices over
  /// the effective style's defaults; high contrast turns edges on and depth
  /// off; a style shaped by its shadows keeps its edges when flat.
  static KitKnobs knobsFor({
    required KitSurfaceStyle style,
    KitFineTune fineTune = KitFineTune.none,
    bool highContrast = false,
  }) {
    final effective = effectiveStyle(style, highContrast: highContrast);
    final tokens = kitStyleSpec(effective).tokens;
    var knobs = fineTune.over(tokens.defaults);
    if (highContrast) {
      knobs = knobs.copyWith(depth: KitDepth.flat, edges: KitEdges.hairline);
    }
    if (tokens.dependsOnDepth && knobs.depth == KitDepth.flat) {
      knobs = knobs.copyWith(edges: KitEdges.hairline);
    }
    return knobs;
  }

  /// [radius] under Corners [corners]: Round keeps it, Soft takes about
  /// 60 %, Sharp takes [sharp]. Stadiums (chips, pills) stay stadiums under
  /// Round and Soft; circles are never passed here.
  static double corner(double radius, KitCorners corners, {double sharp = 2}) {
    if (radius <= 0) return radius;
    return switch (corners) {
      KitCorners.round => radius,
      KitCorners.soft => radius >= 999 ? radius : (radius * .6).roundToDouble(),
      KitCorners.sharp => radius < sharp ? radius : sharp,
    };
  }
}

/// The theme's own near-white: the light on a surface (text1 in dark,
/// surface2 in light), fully opaque.
Color kitStyleLight(ThemeRoles roles) =>
    (roles.isDark ? roles.text1 : roles.surface2).withValues(alpha: 1);

/// The theme's shadow colour, fully opaque: the shade on a surface.
Color kitStyleShade(ThemeRoles roles) =>
    roles.elevationShadow.withValues(alpha: 1);

/// The page (the `ground` role) in any style: the fill alone, no edge and
/// no shadow. The resolver keeps it so whatever a spec returns.
KitSurfaceDecoration kitStyleGround(KitSurfaceRequest r) =>
    KitSurfaceDecoration(
      borderRadius: r.radius,
      circle: r.circle,
      fill: r.color ?? r.levelFill,
    );

/// A boundary that reads at 3:1 or more at rest (WCAG 1.4.11) against the
/// ground, every surface step and the theme's near-white: a field's or an
/// off switch's edge in a style whose own edges are softer. Opaque; the
/// quietest grey on the way from `text3` to `text1` that reaches it.
/// Memoised per role set.
Color kitStyleBoundary(ThemeRoles roles) => _boundaries[roles] ??= () {
  final grounds = [
    roles.ground,
    roles.surface1,
    roles.surface2,
    roles.surface3,
    kitStyleLight(roles),
  ].map((c) => Color.alphaBlend(c, roles.ground.withValues(alpha: 1))).toList();
  final quiet = Color.alphaBlend(
    roles.text3.withValues(alpha: .5),
    grounds.first,
  );
  return readableOn(
    quiet,
    grounds,
    3.1,
    toward: roles.text1.withValues(alpha: 1),
  );
}();

final _boundaries = Expando<Color>('kitStyleBoundary');
