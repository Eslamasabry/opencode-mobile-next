import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../kit_effects.dart';
import '../kit_motion.dart';

/// The app's few touches of feel (design standard §10), so a send and a
/// finish feel the same everywhere. Android plays them only when the
/// person's system "touch feedback" setting is on; a phone without the
/// effect ignores it.
///
/// - [send]: a light tick when the person sends something (a message, a
///   prompt) — the moment their words leave.
/// - [done]: a soft confirmation when something they waited for finishes
///   (a setup that is ready, a reply that finished, a task merged). It is
///   skipped under reduced motion, like the celebration it accompanies.
/// - [commit]: a firm tick when the person confirms a stop, a delete or a
///   discard.
/// - [longPress]: the platform's long-press tick when a long-press opens a
///   menu or starts a selection, as every Android long-click does (owner
///   rule 2026-10-26, kit-v2 §7.1: native Android feel). Nothing on other
///   platforms ([Feedback.forLongPress]).
/// - [select]: a light selection click when a switch flips, a segment is
///   chosen or a wheel moves onto the next value.
///
/// Never on scrolling, on a plain tap of a row or a button, or on a failure
/// the screen already shows: feedback that fires often stops meaning
/// anything.
abstract final class KitHaptics {
  /// Settings › Appearance › Vibration ([KitEffects.haptics]), mirrored
  /// here by the app so a call without a context obeys it too.
  static bool enabled = true;

  static bool _allowed(BuildContext? context) =>
      enabled && (context == null || KitEffects.read(context).haptics);

  /// A light tick: the person sent something.
  static void send([BuildContext? context]) {
    if (!_allowed(context)) return;
    unawaited(HapticFeedback.lightImpact());
  }

  /// A firm tick: the person confirmed an act that stops running work,
  /// deletes, or drops unsaved input (a `KitConfirmSheet` of kind stop,
  /// destructive or discard; docs/ux-system/kit-v2.md §2.13). Like [send],
  /// it obeys Settings › Vibration.
  static void commit([BuildContext? context]) {
    if (!_allowed(context)) return;
    unawaited(HapticFeedback.mediumImpact());
  }

  /// The platform's long-press feedback (Android's LONG_PRESS haptic): a
  /// long-press opened a menu or started a selection.
  static void longPress(BuildContext context) {
    if (!_allowed(context)) return;
    unawaited(Feedback.forLongPress(context));
  }

  /// A light selection click: a switch flipped, a segment or a wheel value
  /// was chosen.
  static void select([BuildContext? context]) {
    if (!_allowed(context)) return;
    unawaited(HapticFeedback.selectionClick());
  }

  /// A soft confirmation: something the person waited for finished.
  /// Android's CONFIRM effect (API 30+); nothing on older versions.
  static void done(BuildContext context) {
    if (!_allowed(context) || KitMotion.reduced(context)) return;
    unawaited(HapticFeedback.successNotification());
  }
}
