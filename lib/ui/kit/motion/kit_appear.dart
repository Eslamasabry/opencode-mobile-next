import 'package:flutter/material.dart';

import '../kit_motion.dart';

/// Something that appears after the screen was already shown (a row read a
/// moment later), arriving once: it fades in and rises a few dp on
/// [KitMotion.standard] with [KitMotion.enter], so it is noticed without a
/// jump. Shown at once under reduced motion. Wrap only what is new: a
/// rebuilt [KitAppear] does not play again.
class KitAppear extends StatefulWidget {
  const KitAppear({super.key, required this.child});

  final Widget child;

  /// How far it rises, in dp.
  static const double rise = 8;

  @override
  State<KitAppear> createState() => _KitAppearState();
}

class _KitAppearState extends State<KitAppear>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
  );
  late final Animation<double> _curve = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
  );
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (KitMotion.reduced(context)) {
      _controller.value = 1;
    } else {
      _controller.forward();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _curve,
    child: widget.child,
    builder: (context, child) => Opacity(
      opacity: _curve.value,
      child: Transform.translate(
        offset: Offset(0, (1 - _curve.value) * KitAppear.rise),
        child: child,
      ),
    ),
  );
}
