import 'package:flutter/widgets.dart';

import 'kit_effects.dart';

/// One set of timings and curves for every movement in the app (design
/// standard §10), so a sheet, a check that draws itself and an illustration
/// all move alike.
///
/// Three switches decide how much moves:
///
/// - the person's system setting (remove animations) or Animations: Off in
///   Settings › Appearance ([KitEffects.motion]): nothing loops and every
///   illustration shows its finished drawing at once;
/// - Animations: Calm: drawings still draw themselves in, nothing loops;
/// - [loops]: ambient loops (a waiting scene that breathes) run in the app
///   and are off under `flutter test` (test/flutter_test_config.dart), so a
///   screen still settles for `pumpAndSettle`. Entrances are finite and run
///   in both.
abstract final class KitMotion {
  /// A control answering a touch: a chip, a toggle, a row's state.
  static const quick = Duration(milliseconds: 150);

  /// The lens lifting out of the bar while dragged, and settling back.
  static const lensLift = SpringDescription(
    mass: 1,
    stiffness: 260,
    damping: 20,
  );

  /// The most the composer's glowing border (and anything else that
  /// travels a ring) may turn: one lap in 6 s.
  static const double edgeLightMaxLapsPerSecond = 1 / 6;

  /// The composer's chosen glowing border: one lap in 8 s (never below 6 s).
  static const double activityGlowLapsPerSecond = 1 / 8;

  /// The ring around Stop while a reply runs: one lap in two seconds, half
  /// that in Calm. Never faster, whatever the work does.
  static const double stopRingLapsPerSecond = 1 / 2;
  static const double stopRingCalmLapsPerSecond = 1 / 4;

  /// The glowing border's strength easing in and out.
  static const Duration edgeLightHueFade = Duration(milliseconds: 450);

  static const Curve enter = Curves.easeOutCubic;
  static const Curve exit = Curves.easeInCubic;
  static const Curve emphasized = Curves.easeInOutCubicEmphasized;

  /// A drawing's part landing with a small overshoot: a mark popping in, a
  /// card settling on a lane (the KitScene arrivals, MOT-1).
  static const Curve land = Curves.easeOutBack;

  /// The soft in-and-out of a pulse (the working dot).
  static const Curve pulse = Curves.easeInOut;

  /// Constant speed inside a drawing: a wave, a route being traced.
  static const Curve steady = Curves.linear;

  /// Whether ambient loops may run at all; false under `flutter test`.
  static bool loops = true;

  /// The person asked for less motion: the system setting, or Animations:
  /// Off in Settings › Appearance.
  static bool reduced(BuildContext context) =>
      (MediaQuery.maybeDisableAnimationsOf(context) ?? false) ||
      KitEffects.of(context).motion == KitMotionLevel.off;

  /// Whether an ambient loop may run here (Animations: Full only).
  static bool loopsIn(BuildContext context) =>
      loops &&
      !reduced(context) &&
      KitEffects.of(context).motion == KitMotionLevel.full;
}
