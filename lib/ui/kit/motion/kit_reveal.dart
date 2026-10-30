import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../kit_motion.dart';

/// A part that comes and goes (design standard §10): a notice, a status
/// line, a folded section's rows. When [child] turns non-null it unfolds
/// downwards and fades in over [KitMotion.standard]; when it turns null it
/// folds away and fades out over [KitMotion.quick] (leaving is quicker
/// than arriving). A change to a child that stays is shown in place,
/// without motion.
///
/// The first build shows what is there at once: a screen opening with a
/// notice already present does not animate it (the page transition already
/// moves the whole screen). With reduced motion every change is instant.
///
/// While it leaves, the old child ignores touches and screen readers skip
/// it, so nothing acts on a part that is going away.
///
/// Cost: the fold is a clip and a height change of one small part, laid out
/// once per frame for 250 ms; the child keeps its constraints, so it is not
/// laid out again. Use it for parts, not for whole screens.
class KitReveal extends StatefulWidget {
  const KitReveal({super.key, required this.child, this.fade = true});

  /// What to show; null folds the part away.
  final Widget? child;

  /// Fade as well as fold (off for a part whose own entrance fades).
  final bool fade;

  /// Whether [context] sits inside a [KitReveal] that animates its arrival,
  /// so a part inside (a notice) does not play its own entrance on top.
  static bool revealsAround(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitRevealScope>() != null;

  @override
  State<KitReveal> createState() => _KitRevealState();
}

class _KitRevealState extends State<KitReveal>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final CurvedAnimation _curve = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
    reverseCurve: KitMotion.exit,
  );
  late Widget? _shown = widget.child;

  @override
  void initState() {
    super.initState();
    // Starts as the first build shows it: no motion until something changes.
    _controller = AnimationController(
      vsync: this,
      duration: KitMotion.standard,
      reverseDuration: KitMotion.quick,
      value: widget.child == null ? 0 : 1,
    )..addStatusListener(_onStatus);
  }

  bool get _leaving => _controller.status == AnimationStatus.reverse;

  void _onStatus(AnimationStatus status) {
    if (status == AnimationStatus.dismissed && widget.child == null) {
      setState(() => _shown = null);
    }
  }

  @override
  void didUpdateWidget(KitReveal old) {
    super.didUpdateWidget(old);
    final child = widget.child;
    final reduced = KitMotion.reduced(context);
    if (child != null) {
      _shown = child;
      if (reduced) {
        _controller.value = 1;
      } else if (old.child == null || _leaving) {
        _controller.forward();
      }
    } else if (old.child != null) {
      if (reduced) {
        _controller.value = 0;
        _shown = null;
      } else {
        _controller.reverse();
      }
    }
  }

  @override
  void dispose() {
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final shown = _shown;
    if (shown == null) return const SizedBox.shrink();
    final leaving = widget.child == null;
    Widget part = _KitRevealScope(child: shown);
    if (widget.fade) part = FadeTransition(opacity: _curve, child: part);
    return IgnorePointer(
      ignoring: leaving,
      child: ExcludeSemantics(
        excluding: leaving,
        // A SizeTransition clips always, so a settled part lost its shadow,
        // outline and glow at its box edge (an opened command box, owner
        // 2026-10-29). The clip stays only while the part is folding.
        child: AnimatedBuilder(
          animation: _curve,
          child: part,
          builder: (context, child) => ClipRect(
            clipBehavior: _curve.value >= 1 ? Clip.none : Clip.hardEdge,
            child: Align(
              alignment: AlignmentDirectional.topStart,
              heightFactor: math.max(0.0, _curve.value),
              child: child,
            ),
          ),
        ),
      ),
    );
  }
}

class _KitRevealScope extends InheritedWidget {
  const _KitRevealScope({required super.child});

  @override
  bool updateShouldNotify(_KitRevealScope old) => false;
}

/// A part arriving (design standard §10): it fades in and rises a few dp
/// into place over [KitMotion.standard] (the rise only under Animations:
/// Full; under Calm it only fades), once when it is built and again
/// whenever [trigger] changes (a state that became a different state).
///
/// Paint and transform only: nothing is laid out again, and the part is
/// hit-testable and read out from the first frame. Inside a [KitReveal] it
/// does not play (the reveal already brings it in). With reduced motion it
/// shows at once.
class KitEntrance extends StatefulWidget {
  const KitEntrance({
    super.key,
    required this.child,
    this.trigger,
    this.rise = 6,
    this.onMount = true,
  });

  final Widget child;

  /// Replays the entrance when it changes; compared with `==`.
  final Object? trigger;

  /// How far the part rises into place, in dp.
  final double rise;

  /// Play when first built. False plays only on a [trigger] change.
  final bool onMount;

  @override
  State<KitEntrance> createState() => _KitEntranceState();
}

class _KitEntranceState extends State<KitEntrance>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    value: 1,
  );
  late final CurvedAnimation _curve = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
  );
  bool _started = false;

  bool _still(BuildContext context) =>
      KitMotion.reduced(context) || KitReveal.revealsAround(context);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_still(context)) {
      _controller.value = 1;
    } else if (!_started && widget.onMount) {
      _controller.forward(from: 0);
    }
    _started = true;
  }

  @override
  void didUpdateWidget(KitEntrance old) {
    super.didUpdateWidget(old);
    if (old.trigger != widget.trigger && !_still(context)) {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Calm: the part fades in where it lands (KitMotion.travels).
    final rise = KitMotion.travels(context) ? widget.rise : 0.0;
    return FadeTransition(
      opacity: _curve,
      child: AnimatedBuilder(
        animation: _curve,
        builder: (context, child) => Transform.translate(
          offset: Offset(0, rise * (1 - _curve.value)),
          child: child,
        ),
        child: widget.child,
      ),
    );
  }
}
