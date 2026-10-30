import 'package:flutter/widgets.dart';

/// How much the app moves, chosen in Settings › Appearance (design standard
/// §10). Lives in state (it is a stored preference); the design kit
/// re-exports it as `lib/ui/kit/kit_effects.dart`. The system's "remove animations" always wins over [full].
enum KitMotionLevel {
  /// Drawings draw themselves in, waiting scenes breathe, celebrations play.
  full,

  /// Drawings still draw themselves in; nothing loops while waiting.
  calm,

  /// Every drawing shows its finished frame; pages and parts change at once.
  off,
}

/// The app's own text size, chosen in Settings > Appearance > Text size. It
/// multiplies on top of the phone's font scale and applies at the app root.
enum KitTextSize {
  smallest(0.85),
  small(0.92),
  normal(1.0),
  large(1.15);

  const KitTextSize(this.factor);

  /// Multiplies the phone's own text scale.
  final double factor;
}

/// How the glowing border while a reply runs is drawn (Settings >
/// Appearance > Effects).
enum KitGlowStyle {
  /// The original: a bright highlight travels the box's own border while a
  /// glow breathes beneath it.
  classic,

  /// The soft ring sweep: a wide soft wash and a thin line, no head.
  softRing,
}

/// How strongly the live background shows (Settings > Appearance > Live
/// background > Intensity). Normal is the strength that ships.
enum KitLiveIntensity {
  subtle(0.7),
  normal(1.0),
  bold(1.6);

  const KitLiveIntensity(this.strength);

  /// Multiplies every look's strength.
  final double strength;
}

/// How the app's surfaces (cards, rows, sheets, the message box) are made
/// (Settings > Appearance > Style). Solid is the default and what Saver
/// always uses. Stored by name: a name no longer here (the retired Glass and
/// Crystal) reads as Solid.
enum KitSurfaceStyle {
  /// Matte panels with a hairline edge.
  solid,

  /// Soft UI: the ground's own colour, shaped by paired soft shadows.
  soft,

  /// Neo-brutalism: bold ink edges and hard offset shadows with no blur.
  brutal,

  /// Flat print, Swiss style: ink rules on paper, square, no shadows.
  paper,

  /// A railway signal box with a console's instruments (owner, 2026-10-30):
  /// cream paper and ink, one track down the start of lists and the
  /// transcript, signal lights as the only status colour, raised keys for
  /// actions. Night signal box in dark.
  signal,
}

/// Settings > Appearance > Style > Fine-tune: how round the corners are.
enum KitCorners { round, soft, sharp }

/// Fine-tune: whether content surfaces stand out with the style's depth.
enum KitDepth { flat, raised }

/// Fine-tune: how much room rows, panels and sections take.
enum KitDensity { comfortable, compact }

/// Fine-tune: whether surfaces draw the style's own edge (a hairline, an
/// ink border, a rule). Shown as On and Off.
enum KitEdges { hairline, none }

/// The four Fine-tune knobs, all decided: a style's defaults, or the
/// person's choices laid over them ([KitFineTune.over]).
@immutable
class KitKnobs {
  const KitKnobs({
    this.corners = KitCorners.round,
    this.depth = KitDepth.flat,
    this.density = KitDensity.comfortable,
    this.edges = KitEdges.hairline,
  });

  final KitCorners corners;
  final KitDepth depth;
  final KitDensity density;
  final KitEdges edges;

  KitKnobs copyWith({
    KitCorners? corners,
    KitDepth? depth,
    KitDensity? density,
    KitEdges? edges,
  }) => KitKnobs(
    corners: corners ?? this.corners,
    depth: depth ?? this.depth,
    density: density ?? this.density,
    edges: edges ?? this.edges,
  );

  @override
  bool operator ==(Object other) =>
      other is KitKnobs &&
      other.corners == corners &&
      other.depth == depth &&
      other.density == density &&
      other.edges == edges;

  @override
  int get hashCode => Object.hash(corners, depth, density, edges);

  @override
  String toString() =>
      'KitKnobs(${corners.name}, ${depth.name}, ${density.name}, ${edges.name})';
}

/// The person's Fine-tune choices: null means "the style's default". A new
/// style starts from [none] ([KitEffects.withSurfaceStyle]).
@immutable
class KitFineTune {
  const KitFineTune({this.corners, this.depth, this.density, this.edges});

  /// Every knob at the style's default.
  static const none = KitFineTune();

  final KitCorners? corners;
  final KitDepth? depth;
  final KitDensity? density;
  final KitEdges? edges;

  bool get isNone =>
      corners == null && depth == null && density == null && edges == null;

  /// These choices over a style's [defaults].
  KitKnobs over(KitKnobs defaults) => KitKnobs(
    corners: corners ?? defaults.corners,
    depth: depth ?? defaults.depth,
    density: density ?? defaults.density,
    edges: edges ?? defaults.edges,
  );

  @override
  bool operator ==(Object other) =>
      other is KitFineTune &&
      other.corners == corners &&
      other.depth == depth &&
      other.density == density &&
      other.edges == edges;

  @override
  int get hashCode => Object.hash(corners, depth, density, edges);
}

/// How many hues the glowing border uses.
enum KitGlowColours {
  /// The theme's main colour, with a lighter shade of it as the highlight.
  one,

  /// The theme's main colour and its partner colour.
  two,
}

/// How fast the glowing border travels. Normal is the app's own pace; the
/// ring never turns faster than the design kit allows.
enum KitGlowSpeed { slow, normal, fast }

/// The person's choices for the app's effects, from Settings › Appearance:
/// how much things move, celebrations and vibration. Read with
/// [KitEffects.of]; provided above the app by [KitEffectsScope].
@immutable
class KitEffects {
  const KitEffects({
    this.motion = KitMotionLevel.full,
    this.celebrations = true,
    this.haptics = true,
    this.activityGlow = true,
    this.glowStyle = KitGlowStyle.classic,
    this.glowColours = KitGlowColours.one,
    this.glowSpeed = KitGlowSpeed.normal,
    this.liveBackground = false,
    this.liveHeldByStyle = false,
    this.liveStyle = 'aurora',
    this.liveIntensity = KitLiveIntensity.normal,
    this.surfaceStyle = KitSurfaceStyle.solid,
    this.fineTune = KitFineTune.none,
    this.saver = false,
  });

  /// The defaults until the person changes them: motion, celebrations,
  /// vibration and the reply glow on; the page its plain colour.
  static const defaults = KitEffects();

  final KitMotionLevel motion;

  /// One-time finished moments (setup ready, a task merged). Off: the
  /// finished drawing shows at once.
  final bool celebrations;

  /// The light tick on send and the confirmation on a finish.
  final bool haptics;

  /// A glowing border around the message box while a reply runs
  /// (Settings › Appearance › Effects). On by default.
  final bool activityGlow;

  /// How that border is drawn, in how many colours, and how fast it moves.
  final KitGlowStyle glowStyle;
  final KitGlowColours glowColours;
  final KitGlowSpeed glowSpeed;

  /// A calm, slowly drifting look on the ground of every page (Settings ›
  /// Appearance › Background). Off by default: the page is its plain
  /// colour until the person picks a look.
  final bool liveBackground;

  /// The live background is off only because the chosen style turned it
  /// off when it was picked ([KitLivePolicy.offWhenChosen]: Paper), not by
  /// the person: choosing another style turns it back on. Any other change
  /// to [liveBackground] (the person's switch) clears it.
  final bool liveHeldByStyle;

  /// Which look it has (a `KitLiveStyle.id`; an unknown id falls back to the
  /// default).
  final String liveStyle;

  /// How strongly the look shows.
  final KitLiveIntensity liveIntensity;

  /// How surfaces are made: the Style (docs/design/design-schools/).
  final KitSurfaceStyle surfaceStyle;

  /// The person's Fine-tune choices over [surfaceStyle]'s defaults.
  final KitFineTune fineTune;

  /// [surfaceStyle] changed to [style], with Fine-tune back at that style's
  /// defaults: changing the style resets the knobs.
  KitEffects withSurfaceStyle(KitSurfaceStyle style) =>
      copyWith(surfaceStyle: style, fineTune: KitFineTune.none);

  /// Saver is in force (Settings > Appearance > Light or dark > Saver): the
  /// app is on a true black ground, and [underSaver] already turned motion,
  /// the live background and non-solid surfaces off. Never stored; the
  /// glowing border reads it to draw itself as a still hairline.
  final bool saver;

  /// These choices as Saver applies them: motion off, no live background,
  /// solid surfaces, celebrations off. The person's stored choices are not
  /// touched; this is what [KitEffectsScope] provides while Saver is on.
  KitEffects get underSaver => copyWith(
    motion: KitMotionLevel.off,
    celebrations: false,
    liveBackground: false,
    surfaceStyle: KitSurfaceStyle.solid,
    fineTune: KitFineTune.none,
    saver: true,
  );

  /// The choices in force here, or [defaults] above any scope (tests).
  static KitEffects of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<KitEffectsScope>()?.effects ??
      defaults;

  /// The same without subscribing to changes (event handlers).
  static KitEffects read(BuildContext context) =>
      context.getInheritedWidgetOfExactType<KitEffectsScope>()?.effects ??
      defaults;

  KitEffects copyWith({
    KitMotionLevel? motion,
    bool? celebrations,
    bool? haptics,
    bool? activityGlow,
    KitGlowStyle? glowStyle,
    KitGlowColours? glowColours,
    KitGlowSpeed? glowSpeed,
    bool? liveBackground,
    bool? liveHeldByStyle,
    String? liveStyle,
    KitLiveIntensity? liveIntensity,
    KitSurfaceStyle? surfaceStyle,
    KitFineTune? fineTune,
    bool? saver,
  }) => KitEffects(
    motion: motion ?? this.motion,
    celebrations: celebrations ?? this.celebrations,
    haptics: haptics ?? this.haptics,
    activityGlow: activityGlow ?? this.activityGlow,
    glowStyle: glowStyle ?? this.glowStyle,
    glowColours: glowColours ?? this.glowColours,
    glowSpeed: glowSpeed ?? this.glowSpeed,
    liveBackground: liveBackground ?? this.liveBackground,
    liveHeldByStyle:
        liveHeldByStyle ??
        (liveBackground == null || liveBackground == this.liveBackground
            ? this.liveHeldByStyle
            : false),
    liveStyle: liveStyle ?? this.liveStyle,
    liveIntensity: liveIntensity ?? this.liveIntensity,
    surfaceStyle: surfaceStyle ?? this.surfaceStyle,
    fineTune: fineTune ?? this.fineTune,
    saver: saver ?? this.saver,
  );

  @override
  bool operator ==(Object other) =>
      other is KitEffects &&
      other.motion == motion &&
      other.celebrations == celebrations &&
      other.haptics == haptics &&
      other.activityGlow == activityGlow &&
      other.glowStyle == glowStyle &&
      other.glowColours == glowColours &&
      other.glowSpeed == glowSpeed &&
      other.liveBackground == liveBackground &&
      other.liveHeldByStyle == liveHeldByStyle &&
      other.liveStyle == liveStyle &&
      other.liveIntensity == liveIntensity &&
      other.surfaceStyle == surfaceStyle &&
      other.fineTune == fineTune &&
      other.saver == saver;

  @override
  int get hashCode => Object.hash(
    motion,
    celebrations,
    haptics,
    activityGlow,
    glowStyle,
    glowColours,
    glowSpeed,
    liveBackground,
    liveHeldByStyle,
    liveStyle,
    liveIntensity,
    surfaceStyle,
    fineTune,
    saver,
  );
}

/// Provides the person's [KitEffects] to everything below it (placed once,
/// above the app's navigator).
class KitEffectsScope extends InheritedWidget {
  const KitEffectsScope({
    super.key,
    required this.effects,
    required super.child,
  });

  final KitEffects effects;

  @override
  bool updateShouldNotify(KitEffectsScope old) => old.effects != effects;
}
