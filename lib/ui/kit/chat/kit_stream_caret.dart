import 'dart:async';

import 'package:flutter/material.dart';

import '../kit_motion.dart';
import '../kit_tokens.dart';

/// The soft caret at the end of a reply that is still being written: a thin
/// bar in the accent after the last word. It breathes (fades down and back,
/// once a second) only where ambient loops may run ([KitMotion.loopsIn]);
/// elsewhere it is steady. It takes no width of its own, so the words do not
/// shift when it comes and goes.
///
/// States: none — a mark that follows motion settings.
class KitStreamCaret extends StatefulWidget {
  const KitStreamCaret({super.key});

  /// One breath of the caret.
  static const breath = Duration(milliseconds: 1100);

  @override
  State<KitStreamCaret> createState() => _KitStreamCaretState();
}

class _KitStreamCaretState extends State<KitStreamCaret>
    with SingleTickerProviderStateMixin {
  late final AnimationController _breath = AnimationController(
    vsync: this,
    duration: KitStreamCaret.breath,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final loops =
        KitMotion.loopsIn(context) && TickerMode.valuesOf(context).enabled;
    if (loops && !_breath.isAnimating) {
      unawaited(_breath.repeat(reverse: true));
    } else if (!loops) {
      _breath
        ..stop()
        ..value = 0;
    }
  }

  @override
  void dispose() {
    _breath.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final height = MediaQuery.textScalerOf(context).scale(16);
    // No width of its own: the words never shift for it.
    return ConstrainedBox(
      constraints: BoxConstraints.tightFor(width: 0, height: height),
      child: OverflowBox(
        alignment: AlignmentDirectional.centerStart,
        maxWidth: tokens.space2,
        child: Padding(
          padding: EdgeInsetsDirectional.only(start: tokens.space1 * .5),
          child: FadeTransition(
            opacity: Tween<double>(begin: 1, end: .25).animate(
              CurvedAnimation(parent: _breath, curve: KitMotion.stepEase),
            ),
            child: CustomPaint(
              size: Size(tokens.space1 * .5, height),
              painter: _CaretPainter(roles.accent),
            ),
          ),
        ),
      ),
    );
  }
}

/// The caret's bar: a thin rounded stroke of [color].
class _CaretPainter extends CustomPainter {
  const _CaretPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) => canvas.drawRRect(
    RRect.fromRectAndRadius(
      Offset.zero & size,
      Radius.circular(size.width / 2),
    ),
    Paint()..color = color,
  );

  @override
  bool shouldRepaint(_CaretPainter old) => old.color != color;
}
