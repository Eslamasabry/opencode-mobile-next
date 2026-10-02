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

/// The person's choices for the app's effects, from Settings › Appearance:
/// glass, how much things move, celebrations and vibration. Read with
/// [KitEffects.of]; provided above the app by [KitEffectsScope].
@immutable
class KitEffects {
  const KitEffects({
    this.glass = true,
    this.motion = KitMotionLevel.full,
    this.celebrations = true,
    this.haptics = true,
    this.activityGlow = false,
  });

  /// Everything on: the default until the person changes it.
  static const defaults = KitEffects();

  /// Translucent glass on the dock, composer, bars and sheets (where the
  /// phone can draw it; see `lib/ui/kit/glass/`). Off: solid surfaces.
  final bool glass;

  final KitMotionLevel motion;

  /// One-time finished moments (setup ready, a task merged). Off: the
  /// finished drawing shows at once.
  final bool celebrations;

  /// The light tick on send and the confirmation on a finish.
  final bool haptics;

  /// A soft glowing ring around the message box while a reply runs, in
  /// addition to the living edge (Settings › Appearance › Effects). Off by
  /// default.
  final bool activityGlow;

  /// The choices in force here, or [defaults] above any scope (tests).
  static KitEffects of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<KitEffectsScope>()?.effects ??
      defaults;

  /// The same without subscribing to changes (event handlers).
  static KitEffects read(BuildContext context) =>
      context.getInheritedWidgetOfExactType<KitEffectsScope>()?.effects ??
      defaults;

  KitEffects copyWith({
    bool? glass,
    KitMotionLevel? motion,
    bool? celebrations,
    bool? haptics,
    bool? activityGlow,
  }) => KitEffects(
    glass: glass ?? this.glass,
    motion: motion ?? this.motion,
    celebrations: celebrations ?? this.celebrations,
    haptics: haptics ?? this.haptics,
    activityGlow: activityGlow ?? this.activityGlow,
  );

  @override
  bool operator ==(Object other) =>
      other is KitEffects &&
      other.glass == glass &&
      other.motion == motion &&
      other.celebrations == celebrations &&
      other.haptics == haptics &&
      other.activityGlow == activityGlow;

  @override
  int get hashCode =>
      Object.hash(glass, motion, celebrations, haptics, activityGlow);
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
