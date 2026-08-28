import 'package:flutter/material.dart';

/// A one-shot staggered list entrance: items fade in and rise slightly, with
/// the stagger capped so long lists settle as fast as short ones.
///
/// Purely ticker-driven ([TweenAnimationBuilder]), so it is safe when a test
/// disposes the tree mid-flight, and it renders the final state immediately
/// when animations are disabled.
class EntranceReveal extends StatelessWidget {
  const EntranceReveal({super.key, required this.index, required this.child});

  /// Position in the list; delays are capped at [staggerCap] items.
  final int index;
  final Widget child;

  static const staggerCap = 8;
  static const _stepMs = 28;
  static const _revealMs = 190;

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    final delayMs = index.clamp(0, staggerCap) * _stepMs;
    final totalMs = delayMs + _revealMs;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: totalMs),
      builder: (context, t, child) {
        final elapsedMs = t * totalMs;
        final raw = ((elapsedMs - delayMs) / _revealMs).clamp(0.0, 1.0);
        final eased = Curves.easeOutCubic.transform(raw);
        return Opacity(
          opacity: eased,
          child: Transform.translate(
            offset: Offset(0, (1 - eased) * 8),
            child: child,
          ),
        );
      },
      child: child,
    );
  }
}
