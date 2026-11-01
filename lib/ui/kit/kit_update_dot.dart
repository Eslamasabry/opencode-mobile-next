import 'package:flutter/material.dart';

import 'kit_motion.dart';
import 'kit_tokens.dart';

/// A small dot on the top-end corner of [child] while an app update waits
/// (the Settings gear, the Settings rail destination). It fades in over
/// 150 ms (nothing under reduced motion) and says nothing itself: the host
/// carries "Settings, update available" as its own label, so the dot is
/// excluded from semantics and from taps.
class KitUpdateDot extends StatelessWidget {
  const KitUpdateDot({super.key, required this.show, required this.child});

  final bool show;
  final Widget child;

  static const fade = Duration(milliseconds: 150);
  static const double size = 10;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Stack(
      clipBehavior: Clip.none,
      children: [
        child,
        PositionedDirectional(
          top: 0,
          end: 0,
          child: IgnorePointer(
            child: ExcludeSemantics(
              child: AnimatedOpacity(
                key: const ValueKey('kit-update-dot'),
                opacity: show ? 1 : 0,
                duration: KitMotion.reduced(context) ? Duration.zero : fade,
                child: CustomPaint(
                  size: const Size.square(size),
                  painter: _DotPainter(tokens.roles.attentionFill),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Amber means "something waits", in every style, so the dot is painted
/// as a plain circle rather than asked of the style resolver.
class _DotPainter extends CustomPainter {
  const _DotPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) => canvas.drawCircle(
    size.center(Offset.zero),
    size.shortestSide / 2,
    Paint()..color = color,
  );

  @override
  bool shouldRepaint(_DotPainter old) => old.color != color;
}
