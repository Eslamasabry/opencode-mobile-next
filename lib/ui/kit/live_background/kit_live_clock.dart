import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';

/// The live background's one shared clock: a slow tick for every page that
/// shows a moving look, and a rest when nobody touches the app.
///
/// Why a slow tick: every look drifts slowly (less than a pixel a second),
/// so a frame on every vsync bought nothing a person can see and cost the
/// most of any part of the app. Measured on the owner's phone (2026-10-23,
/// Conversations untouched): 60 frames a second and 37-79 % CPU with the
/// look on, none and 0.3 % with it off.
///
/// So the look moves [framesPerSecond] times a second (a look may ask for
/// fewer: `KitLiveStyle.framesPerSecond`), every page on the same tick,
/// and nothing is drawn between ticks. After [idleAfter] with no touch,
/// scroll or key it rests: no tick, no frame. The next touch wakes it
/// where it stopped ([seconds] never jumps), so it never appears to skip.
///
/// The clock runs only while at least one moving look is shown
/// ([attach]); a hidden page, the app in the background, Live background
/// off, reduced motion, Calm and Saver all leave it detached, so it
/// arms no timer and listens to no input then.
class KitLiveClock {
  KitLiveClock._();

  /// The one clock every page shares.
  static final KitLiveClock instance = KitLiveClock._();

  /// Ticks a second while a look moves.
  static const int framesPerSecond = 12;

  /// The time between two ticks.
  static const Duration interval = Duration(
    microseconds: Duration.microsecondsPerSecond ~/ framesPerSecond,
  );

  /// How long without a touch, scroll or key before the look rests.
  static Duration idleAfter = const Duration(seconds: 30);

  /// Seconds the looks have moved, advanced only on a tick: it holds still
  /// while the clock rests or nobody is attached, and carries on from
  /// there.
  final ValueNotifier<double> seconds = ValueNotifier<double>(0);

  /// Ticks so far ([seconds] in ticks): a look slower than
  /// [framesPerSecond] repaints on every few of them.
  int get ticks => _ticks;

  /// False while the look rests (no input for [idleAfter]).
  final ValueNotifier<bool> awake = ValueNotifier<bool>(true);

  int _users = 0;
  int _ticks = 0;
  int _quiet = 0;
  int _lastTimerTick = 0;
  Timer? _timer;

  /// Moving looks shown right now (tests).
  @visibleForTesting
  int get users => _users;

  /// Whether a tick is due (tests: the clock arms a timer only while it
  /// moves).
  @visibleForTesting
  bool get running => _timer != null;

  /// A moving look starts showing: the first one starts the clock and
  /// listens for input.
  void attach() {
    if (_users++ > 0) return;
    GestureBinding.instance.pointerRouter.addGlobalRoute(_pointer);
    HardwareKeyboard.instance.addHandler(_key);
    _wake();
  }

  /// A moving look stops showing: the last one stops the clock.
  void detach() {
    assert(_users > 0, 'detach without attach');
    if (--_users > 0) return;
    GestureBinding.instance.pointerRouter.removeGlobalRoute(_pointer);
    HardwareKeyboard.instance.removeHandler(_key);
    _stop();
    awake.value = true;
  }

  void _pointer(PointerEvent event) => input();

  bool _key(KeyEvent event) {
    input();
    return false;
  }

  /// A touch, a scroll or a key: the look moves again if it rested.
  void input() {
    _quiet = 0;
    if (!awake.value && _users > 0) _wake();
  }

  void _wake() {
    _quiet = 0;
    awake.value = true;
    if (_timer != null) return;
    _lastTimerTick = 0;
    _timer = Timer.periodic(interval, _tick);
  }

  void _stop() {
    _timer?.cancel();
    _timer = null;
  }

  void _tick(Timer timer) {
    // A late timer reports the ticks it missed; a long stall (a busy
    // isolate) moves at most a few ticks' worth, so nothing leaps.
    final missed = math.max(1, timer.tick - _lastTimerTick);
    _lastTimerTick = timer.tick;
    final step = math.min(missed, 3);
    _ticks += step;
    seconds.value += step * interval.inMicroseconds / 1e6;
    _quiet += missed;
    if (_quiet * interval.inMicroseconds >= idleAfter.inMicroseconds) {
      _stop();
      awake.value = false;
    }
  }

  /// Back to a fresh clock (tests).
  @visibleForTesting
  void debugReset() {
    if (_users > 0) {
      GestureBinding.instance.pointerRouter.removeGlobalRoute(_pointer);
      HardwareKeyboard.instance.removeHandler(_key);
    }
    _stop();
    _users = 0;
    _ticks = 0;
    _quiet = 0;
    seconds.value = 0;
    awake.value = true;
    idleAfter = const Duration(seconds: 30);
  }
}
