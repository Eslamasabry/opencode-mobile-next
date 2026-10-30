part of 'kit_surface_look.dart';

// The painting half of the kit's surfaces: the face and shadows a
// [KitSurfaceDecoration] paints.

class _KitSurfacePainter extends BoxPainter {
  _KitSurfacePainter(this.d, super.onChanged);

  final KitSurfaceDecoration d;

  static bool _shows(Color c) => c.a > 0;

  @override
  void paint(Canvas canvas, Offset offset, ImageConfiguration configuration) {
    final size = configuration.size;
    if (size == null || size.isEmpty) return;
    final rtl = configuration.textDirection == TextDirection.rtl;
    final rect = offset & size;
    final rrect = d._rrect(rect);
    // The light comes from the top start.
    final toEnd = Offset(rtl ? -1 : 1, 1);

    final raised = 1 - d.inset.clamp(0.0, 1.0);
    final sigma = Shadow.convertRadiusToSigma(d.blur);
    final softShadows =
        raised > 0 &&
        d.distance > 0 &&
        (_shows(d.lightShadow) || _shows(d.darkShadow));
    final dropShadows =
        d.shadows.any((s) => _shows(s.color)) ||
        (d.hardOffset > 0 && _shows(d.hardShadow));
    if (softShadows || dropShadows) {
      // Shadows fall only outside the surface, so a clear pane is never
      // darkened by its own shadow.
      canvas.save();
      final reach = rect.inflate(64 + d.blur * 2 + d.distance * 2);
      canvas.clipPath(
        Path()
          ..fillType = PathFillType.evenOdd
          ..addRect(reach)
          ..addRRect(rrect),
      );
    }
    if (d.hardOffset > 0 && _shows(d.hardShadow)) {
      canvas.drawRRect(
        rrect.shift(toEnd * d.hardOffset),
        Paint()..color = d.hardShadow,
      );
    }
    for (final shadow in d.shadows) {
      if (!_shows(shadow.color)) continue;
      canvas.drawRRect(
        rrect.shift(shadow.offset).inflate(shadow.spreadRadius),
        shadow.toPaint(),
      );
    }
    if (softShadows) {
      for (final (color, sign) in [
        (d.lightShadow, -1.0),
        (d.darkShadow, 1.0),
      ]) {
        if (!_shows(color)) continue;
        final paint = Paint()
          ..color = color.withValues(alpha: color.a * raised)
          ..maskFilter = MaskFilter.blur(BlurStyle.normal, sigma);
        canvas.drawRRect(rrect.shift(toEnd * (d.distance * sign)), paint);
      }
    }
    if (softShadows || dropShadows) canvas.restore();

    // Hook H2: the face sinks towards the bottom end; shadows stay put.
    final face = d.faceShift == 0 ? rrect : rrect.shift(toEnd * d.faceShift);
    final faceRect = d.faceShift == 0 ? rect : face.outerRect;

    if (_shows(d.fill) || _shows(d.fillStart)) {
      final paint = Paint();
      if (d.fill == d.fillStart) {
        paint.color = d.fill;
      } else {
        final begin = rtl ? faceRect.topRight : faceRect.topLeft;
        final end = rtl ? faceRect.bottomLeft : faceRect.bottomRight;
        paint.shader = ui.Gradient.linear(
          begin,
          end,
          [d.fillStart, d.fill],
          [0, .7],
        );
      }
      canvas.drawRRect(face, paint);
    }

    final pressed = d.inset.clamp(0.0, 1.0);
    if (pressed > 0 && d.distance > 0) {
      canvas.save();
      canvas.clipRRect(face);
      final outer = faceRect.inflate(d.blur * 2 + d.distance * 2);
      // The dark one falls inside the top-start edge, the light one inside
      // the bottom-end edge: the surface is pressed into the ground.
      for (final (color, sign) in [
        (d.darkShadow, 1.0),
        (d.lightShadow, -1.0),
      ]) {
        if (!_shows(color)) continue;
        final hole = Path()
          ..fillType = PathFillType.evenOdd
          ..addRect(outer)
          ..addRRect(face.shift(toEnd * (d.distance * sign)));
        final paint = Paint()
          ..color = color.withValues(alpha: color.a * pressed)
          ..maskFilter = MaskFilter.blur(BlurStyle.normal, sigma);
        canvas.drawPath(hole, paint);
      }
      canvas.restore();
    }

    if (d.edgeWidth > 0 && (_shows(d.edgeStart) || _shows(d.edgeEnd))) {
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = d.edgeWidth;
      if (d.edgeStart == d.edgeEnd) {
        paint.color = d.edgeStart;
      } else {
        final begin = rtl ? faceRect.topRight : faceRect.topLeft;
        final end = rtl ? faceRect.bottomLeft : faceRect.bottomRight;
        paint.shader = ui.Gradient.linear(begin, end, [d.edgeStart, d.edgeEnd]);
      }
      canvas.drawRRect(face.deflate(d.edgeWidth / 2), paint);
    }
  }
}
