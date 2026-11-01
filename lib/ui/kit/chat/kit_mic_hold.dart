import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/widgets.dart';

import '../motion/kit_haptics.dart';
import 'kit_composer.dart' show KitComposerMicHold;

/// How a finger on the composer's mic is read: a quick touch is a tap, a
/// touch kept down is "hold to talk", and a held touch dragged far from where
/// it landed is "let go to cancel". Pure logic with no widgets, so the
/// three can be tested without a screen.
///
/// - Down, then up before [holdAfter]: [KitMicPressOutcome.tap]. (The mic's
///   own tap handler does the work; this only says it was a tap.)
/// - Down, then moved past [slop] before [holdAfter]: the finger is
///   dragging something else; nothing happens ([KitMicPressOutcome.none]).
/// - Down for [holdAfter]: [onHoldStart] fires, once.
/// - Held and then moved [cancelDistance] or more from the landing point:
///   [onArmedChanged] (true); coming back inside un-arms it (false).
/// - Up while held: [onHoldEnd] with `cancelled` true only when armed.
/// - The system takes the touch away while held: the hold is cancelled.
enum KitMicPressOutcome { tap, holdCommitted, holdCancelled, none }

enum KitMicPressPhase { idle, down, holding }

class KitMicPress {
  KitMicPress({
    this.holdAfter = const Duration(milliseconds: 350),
    this.slop = 18,
    this.cancelDistance = 80,
    this.onHoldStart,
    this.onHoldEnd,
    this.onArmedChanged,
  });

  final Duration holdAfter;

  /// Logical pixels a touch may drift before the hold is given up.
  final double slop;

  /// Logical pixels from the landing point at which letting go cancels.
  final double cancelDistance;

  VoidCallback? onHoldStart;

  /// The hold ended: `cancelled` when it was dragged away first.
  ValueChanged<bool>? onHoldEnd;

  /// Dragged far enough to cancel (true), or back inside (false).
  ValueChanged<bool>? onArmedChanged;

  KitMicPressPhase phase = KitMicPressPhase.idle;
  bool armed = false;
  Offset _origin = Offset.zero;
  Timer? _timer;

  bool get holding => phase == KitMicPressPhase.holding;

  void down(Offset position) {
    _timer?.cancel();
    _origin = position;
    armed = false;
    phase = KitMicPressPhase.down;
    _timer = Timer(holdAfter, () {
      _timer = null;
      if (phase != KitMicPressPhase.down) return;
      phase = KitMicPressPhase.holding;
      onHoldStart?.call();
    });
  }

  void move(Offset position) {
    final distance = (position - _origin).distance;
    switch (phase) {
      case KitMicPressPhase.idle:
        return;
      case KitMicPressPhase.down:
        if (distance > slop) {
          _timer?.cancel();
          _timer = null;
          phase = KitMicPressPhase.idle;
        }
      case KitMicPressPhase.holding:
        final now = distance >= cancelDistance;
        if (now != armed) {
          armed = now;
          onArmedChanged?.call(now);
        }
    }
  }

  KitMicPressOutcome up() {
    final was = phase;
    _timer?.cancel();
    _timer = null;
    phase = KitMicPressPhase.idle;
    switch (was) {
      case KitMicPressPhase.idle:
        return KitMicPressOutcome.none;
      case KitMicPressPhase.down:
        return KitMicPressOutcome.tap;
      case KitMicPressPhase.holding:
        final cancelled = armed;
        armed = false;
        onHoldEnd?.call(cancelled);
        return cancelled
            ? KitMicPressOutcome.holdCancelled
            : KitMicPressOutcome.holdCommitted;
    }
  }

  /// The system took the touch (a scroll claimed it, a call came in).
  KitMicPressOutcome cancel() {
    final was = phase;
    _timer?.cancel();
    _timer = null;
    phase = KitMicPressPhase.idle;
    if (was != KitMicPressPhase.holding) return KitMicPressOutcome.none;
    armed = false;
    onHoldEnd?.call(true);
    return KitMicPressOutcome.holdCancelled;
  }

  void dispose() {
    _timer?.cancel();
    _timer = null;
  }
}

/// Tells the mic's tap handler that the touch it is about to hear was a
/// hold, which already did its work.
class KitMicHoldGate {
  bool _held = false;

  /// True once per hold: the tap that follows a hold is not a tap.
  bool takeHeld() {
    final was = _held;
    _held = false;
    return was;
  }
}

/// Reads the finger on [child] (the mic) as tap or hold ([KitMicPress]).
/// A tap is left to the child's own handler; a hold calls [hold].
///
/// The touch is followed through the pointer router, not through this
/// widget: holding the mic turns the composer into voice mode, which
/// removes the mic from the tree while the finger is still down, and the
/// release must still be heard.
///
/// Feel: a tick when the hold starts (the recording begins), a tick on
/// entering and leaving the cancel zone, and a firm one when the finger
/// lifts to send.
class KitMicHoldDetector extends StatefulWidget {
  const KitMicHoldDetector({
    super.key,
    required this.child,
    required this.hold,
    required this.gate,
    this.holdAfter = const Duration(milliseconds: 350),
  });

  final Widget child;
  final KitComposerMicHold hold;
  final KitMicHoldGate gate;
  final Duration holdAfter;

  @override
  State<KitMicHoldDetector> createState() => _KitMicHoldDetectorState();
}

class _KitMicHoldDetectorState extends State<KitMicHoldDetector> {
  late final KitMicPress _press;
  int? _pointer;

  @override
  void initState() {
    super.initState();
    _press = KitMicPress(holdAfter: widget.holdAfter);
  }

  void _bind() {
    final hold = widget.hold;
    final gate = widget.gate;
    _press
      ..onHoldStart = () {
        gate._held = true;
        KitHaptics.commit();
        hold.onStart();
      }
      ..onArmedChanged = (armed) {
        KitHaptics.select();
        hold.onArmedChanged?.call(armed);
      }
      ..onHoldEnd = (cancelled) {
        if (!cancelled) KitHaptics.send();
        hold.onEnd(cancelled);
      };
  }

  void _down(PointerDownEvent event) {
    if (_pointer != null) return;
    if (event.kind == PointerDeviceKind.mouse &&
        event.buttons != kPrimaryMouseButton) {
      return;
    }
    _bind();
    widget.gate._held = false;
    _pointer = event.pointer;
    GestureBinding.instance.pointerRouter.addRoute(event.pointer, _route);
    _press.down(event.position);
  }

  void _route(PointerEvent event) {
    if (event is PointerMoveEvent) {
      _press.move(event.position);
    } else if (event is PointerUpEvent) {
      _finish(() => _press.up());
    } else if (event is PointerCancelEvent) {
      _finish(() => _press.cancel());
    }
  }

  void _finish(KitMicPressOutcome Function() end) {
    final pointer = _pointer;
    if (pointer != null) {
      GestureBinding.instance.pointerRouter.removeRoute(pointer, _route);
    }
    _pointer = null;
    end();
  }

  @override
  void dispose() {
    // A touch that has not become a hold yet is dropped with the widget; a
    // hold carries on until the finger lifts (the route is removed then).
    if (!_press.holding) {
      final pointer = _pointer;
      if (pointer != null) {
        GestureBinding.instance.pointerRouter.removeRoute(pointer, _route);
        _pointer = null;
      }
      _press.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      Listener(onPointerDown: _down, child: widget.child);
}
