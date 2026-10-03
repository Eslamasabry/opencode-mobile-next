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

/// How the glowing border while a reply runs is drawn (Settings >
/// Appearance > Effects).
enum KitGlowStyle {
  /// The original: a bright highlight travels the box's own border while a
  /// glow breathes beneath it.
  classic,

  /// The soft ring sweep: a wide soft wash and a thin line, no head.
  softRing,
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
  });

  /// Everything on: the default until the person changes it.
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
  }) => KitEffects(
    motion: motion ?? this.motion,
    celebrations: celebrations ?? this.celebrations,
    haptics: haptics ?? this.haptics,
    activityGlow: activityGlow ?? this.activityGlow,
    glowStyle: glowStyle ?? this.glowStyle,
    glowColours: glowColours ?? this.glowColours,
    glowSpeed: glowSpeed ?? this.glowSpeed,
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
      other.glowSpeed == glowSpeed;

  @override
  int get hashCode => Object.hash(
    motion,
    celebrations,
    haptics,
    activityGlow,
    glowStyle,
    glowColours,
    glowSpeed,
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
