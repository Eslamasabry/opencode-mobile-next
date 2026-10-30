import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Where a trailing glyph's ink ends (impeccable layout pass, 2026-10-28:
/// "the visible glyph of every trailing control ends on the rail").
///
/// A glyph's ink stands in from its square by a margin that differs per
/// glyph: Phosphor's caret by 28 % of its size, its dots and cross by 19 %,
/// its copy by 12.5 %, its check by 9 %. Placed by their boxes, a chevron,
/// a ⋯ and a check at the end of one list end on three different lines.
/// [endInset] is that margin, so a part can place a trailing glyph by its
/// ink: [endPad] is the room to leave after the glyph's box for its ink to
/// end [rail] from the edge.
///
/// Measured from the bundled font (assets/fonts/phosphor/Phosphor.ttf, the
/// glyph's bounds against its 1024-unit advance). A glyph not listed is
/// placed by its box (an inset of 0), as before.
abstract final class KitOptical {
  /// The regular Phosphor family the kit's trailing glyphs come from.
  static const _family = 'AppPhosphorRegular';

  /// codePoint -> (ink start, ink end) as fractions of the glyph's size, in
  /// the glyph's own left-to-right drawing.
  static const Map<int, (double, double)> _ink = {
    0xe13a: (.34375, .28125), // chevronRight (caret-right)
    0xe136: (.15625, .15625), // chevronDown (caret-down)
    0xe13c: (.15625, .15625), // chevronUp (caret-up)
    0xe1fe: (.1875, .1875), // more (dots-three)
    0xe4f6: (.1875, .1875), // close (x)
    0xe182: (.125, .09375), // check
    0xe1cc: (.125, .125), // copy
  };

  /// How far [icon]'s ink stands in from the end of its [size] square, in
  /// the reading direction of [context]. A glyph that mirrors in a
  /// right-to-left language (`matchTextDirection`) keeps its end margin.
  static double endInset(BuildContext context, IconData icon, double size) {
    if (icon.fontFamily != _family) return 0;
    final ink = _ink[icon.codePoint];
    if (ink == null) return 0;
    final rtl = Directionality.maybeOf(context) == TextDirection.rtl;
    return size * (rtl && !icon.matchTextDirection ? ink.$1 : ink.$2);
  }

  /// The room after [icon]'s [size] box so that its ink ends [rail] from the
  /// same edge. Never negative.
  static double endPad(
    BuildContext context,
    IconData icon,
    double size,
    double rail,
  ) => math.max(0, rail - endInset(context, icon, size));
}
