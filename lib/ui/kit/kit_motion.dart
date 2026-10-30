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

  /// A part appearing or changing size: a notice, a section unfolding.
  static const standard = Duration(milliseconds: 250);

  /// A page that grew out of a row closing back into it: shorter than its
  /// opening (Material's container transform returns in 300 ms).
  static const rowReturn = Duration(milliseconds: 300);

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

  /// A streaming reply's box growing to its next height (KitMarkdown): a
  /// critically damped spring (no overshoot) that settles on a new line in
  /// about 210 ms, and carries its speed when another line lands mid-way.
  static const replyGrow = SpringDescription(
    mass: 1,
    stiffness: 700,
    damping: 53,
  );

  /// The most the composer's glowing border (and anything else that
  /// travels a ring) may turn: one lap in 6 s.
  static const double edgeLightMaxLapsPerSecond = 1 / 6;

  /// The composer's chosen glowing border: one lap in 8 s (never below 6 s).
  static const double activityGlowLapsPerSecond = 1 / 8;

  /// The classic glowing border (the original composer ring): one lap in
  /// 3.6 s at Normal, the pace it always had. It is faster than
  /// [edgeLightMaxLapsPerSecond] on purpose: that cap belongs to the soft
  /// ring and the living edge, which have no bright head. The classic ring's
  /// Slow is half this and Fast twice it, and nothing may turn faster than
  /// [glowMaxLapsPerSecond] (one lap in 1.8 s; its breath stays under
  /// 1.2 Hz, well clear of flicker rates).
  static const double classicGlowLapsPerSecond = 1 / 3.6;
  static const double glowMaxLapsPerSecond = 1 / 1.8;

  /// The ring around Stop while a reply runs and nothing else moves: one lap
  /// in two seconds. Never faster, whatever the work does.
  static const double stopRingLapsPerSecond = 1 / 2;

  /// The glowing border's strength easing in and out.
  static const Duration edgeLightHueFade = Duration(milliseconds: 450);

  /// A running step's light: one lap, the tick's stay, the glyph's spring back
  /// and the name's sweep (KitStepTimeline.md).
  static const Duration stepLap = Duration(milliseconds: 2000);
  static const Duration stepLapCalm = Duration(milliseconds: 4000);
  static const Duration stepBreathe = Duration(milliseconds: 1600);
  static const Duration stepFlash = Duration(milliseconds: 300);
  static const Duration stepFade = Duration(milliseconds: 200);
  static const Duration stepTick = Duration(milliseconds: 600);
  static const Duration stepSpring = Duration(milliseconds: 260);
  static const Duration stepSweep = Duration(milliseconds: 2200);

  /// The context ring (KitContextRing.md): the arc and its number moving to
  /// a new value, the colour crossing to the next level, the one halo when
  /// 70 % or 95 % is crossed upward, the arc draining after a compaction,
  /// and one lap of the short arc while compacting.
  static const Duration contextValue = Duration(milliseconds: 600);
  static const Duration contextColor = Duration(milliseconds: 320);
  static const Duration contextHalo = Duration(milliseconds: 700);
  static const Duration contextDrain = Duration(milliseconds: 900);
  static const Duration contextSpin = Duration(milliseconds: 1200);

  /// How long the terminal's "Copied to clipboard" note stays up.
  static const Duration terminalCopiedNote = Duration(seconds: 2);

  static const Curve stepEase = Curves.easeInOutQuad;
  static const Curve stepOut = Curves.easeOutQuad;

  /// Something arriving, or any change whose progress runs forward in time
  /// (0 → 1), including a part leaving that way: starts fast, settles.
  static const Curve enter = Curves.easeOutCubic;

  /// A part leaving, for a reverse slot only (`reverseCurve:`,
  /// `switchOutCurve:`), where the value runs from 1 to 0: there this
  /// curve leaves at once and settles. Never `.flipped` (that holds, then
  /// accelerates away), and never `.transform(t)` on forward progress
  /// (use [enter]).
  static const Curve exit = Curves.easeInCubic;
  static const Curve emphasized = Curves.easeInOutCubicEmphasized;

  /// A page that grew out of a row going back into it (KitPageOrigin): the
  /// opening's path mirrored, so it starts moving at once and lands softly
  /// in the row. For a reverse slot (`reverseCurve`), where the value runs
  /// from 1 to 0.
  static const Curve rowReturnPath = FlippedCurve(
    Curves.easeInOutCubicEmphasized,
  );

  /// A drawing's part landing with a small overshoot (KitScene arrivals
  /// only, MOT-1). Not for marks or controls: they never bounce.
  static const Curve land = Curves.easeOutBack;

  /// How small a mark (a chosen card's check, a guide's dot, a done tick)
  /// is as it appears or leaves: never from nothing (it fades in from
  /// here, on [enter]).
  static const double markFrom = .9;

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

  /// Whether a small change may travel as well as fade (Animations: Full
  /// only): a count rolling to its next number, a menu dropping a few dp from
  /// what opened it. Under Calm the same change only cross-fades, and under
  /// Off (or the system's remove animations) it is instant.
  static bool travels(BuildContext context) =>
      !reduced(context) && KitEffects.of(context).motion == KitMotionLevel.full;

  /// Whether an ambient loop may run here (Animations: Full only).
  static bool loopsIn(BuildContext context) =>
      loops &&
      !reduced(context) &&
      KitEffects.of(context).motion == KitMotionLevel.full;
}
