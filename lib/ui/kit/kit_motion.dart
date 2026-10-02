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

  /// The composer's edge light flashing once (a send, a stop).
  static const composerFlash = Duration(milliseconds: 520);

  /// The composer opening or closing under Animations: Full.
  static const composerOpen = Duration(milliseconds: 380);

  /// The composer opening or closing under Animations: Calm.
  static const composerOpenCalm = Duration(milliseconds: 150);

  /// The curve of the composer opening or closing.
  static const Curve composerOpenCurve = Curves.easeInOutCubic;

  /// The edge light settling after a send.
  static const Curve sealFade = Curves.easeInOut;

  /// The edge light's tail fading in along its length.
  static const Curve tailFadeIn = Curves.easeOut;

  /// A part appearing or changing size: a notice, a section unfolding.
  static const standard = Duration(milliseconds: 250);

  /// An illustration drawing itself in. Long enough to be seen, short
  /// enough that nobody waits for it.
  static const entrance = Duration(milliseconds: 900);

  /// A one-time moment worth marking: setup finished, a task merged.
  static const celebration = Duration(milliseconds: 1400);

  /// One breath of an ambient loop on a waiting screen.
  static const breath = Duration(seconds: 4);

  /// A wait turns into an explanation after this (KitSince.md, KitField.md,
  /// KitStateView.md; MOT-1, kit-v2 G9 "escalate after 8 s").
  static const escalateAfter = Duration(seconds: 8);

  /// The least time a press stays visible, so a quick tap (finger down and
  /// up between two frames) still shows its pressed fill (KitPressTracker;
  /// Android's pressed-state duration is 64 ms, this seam rounds up so the
  /// fill registers). A state, not a movement: it holds under reduced
  /// motion too, where the fill still appears and clears instantly.
  static const pressHold = Duration(milliseconds: 100);

  /// How long an undo stays offered (KitReceipt.md, KitUndo.md: 8 s).
  static const undoWindow = Duration(seconds: 8);

  /// How long a copy control shows its check (KitIconButton.md,
  /// KitAction.md). No spec states a value; 2 s is this seam's choice.
  static const copiedHold = Duration(seconds: 2);

  /// A log panel's default poll interval (KitLogPanel.md).
  static const logPoll = Duration(seconds: 2);

  /// Typing counts as settled after this: the search debounce and the
  /// result-count announcement (KitSearchField.md, about 300 ms).
  static const typingSettle = Duration(milliseconds: 300);

  // The navigation's tab lens moves on springs, not on a duration.
  // Stiffness and damping per unit mass, in logical pixels and seconds; a
  // spring is never used under [reduced], where every state is instant.

  /// The tab lens's leading edge after a tap: it leads, so the lens
  /// stretches towards the new tab.
  static const lensLead = SpringDescription(
    mass: 1,
    stiffness: 560,
    damping: 32,
  );

  /// The tab lens's trailing edge after a tap: it follows, softer.
  static const lensTrail = SpringDescription(
    mass: 1,
    stiffness: 210,
    damping: 23,
  );

  /// The lens's leading edge while a finger drags it along the bar.
  static const lensDragLead = SpringDescription(
    mass: 1,
    stiffness: 700,
    damping: 36,
  );

  /// The lens's trailing edge while a finger drags it along the bar.
  static const lensDragTrail = SpringDescription(
    mass: 1,
    stiffness: 260,
    damping: 26,
  );

  /// The lens lifting out of the bar while dragged, and settling back.
  static const lensLift = SpringDescription(
    mass: 1,
    stiffness: 260,
    damping: 20,
  );

  /// The composer's edge light (the running reply's status): calm by rule.
  /// It never travels faster than [edgeLightMaxLapsPerSecond] (one lap in
  /// 6 s, reached only by a burst of words), rests at
  /// [edgeLightThinkingLapsPerSecond] while the model thinks, and every
  /// change of speed eases over [edgeLightSpeedEase] and of hue over
  /// [edgeLightHueFade]. Where a status touches the border it fades out and
  /// back in over [edgeLightFadeSpan] dp instead of stopping.
  static const double edgeLightMaxLapsPerSecond = 1 / 6;

  /// The composer's chosen glowing border: one lap in 8 s (never below 6 s).
  static const double activityGlowLapsPerSecond = 1 / 8;
  static const double edgeLightThinkingLapsPerSecond = 1 / 10;
  static const Duration edgeLightSpeedEase = Duration(milliseconds: 800);
  static const Duration edgeLightHueFade = Duration(milliseconds: 450);
  static const double edgeLightFadeSpan = 20;

  static const Curve enter = Curves.easeOutCubic;
  static const Curve exit = Curves.easeInCubic;
  static const Curve emphasized = Curves.easeInOutCubicEmphasized;

  /// A drawing's part landing with a small overshoot: a mark popping in, a
  /// card settling on a lane (the KitScene arrivals, MOT-1).
  static const Curve land = Curves.easeOutBack;

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
