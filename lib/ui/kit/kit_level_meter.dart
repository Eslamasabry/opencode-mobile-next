import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';

import 'kit_max_width.dart';
import 'kit_tokens.dart';

/// The microphone's input level as bars. Decorative: excluded from
/// semantics; its host says "Listening" in words.
///
/// States: none — decorative bars; listening, quiet and paused are the
/// levels its host names in words, not KIT-12 states.
///
/// docs/ux-system/kit-api/KitLevelMeter.md.
class KitLevelMeter extends StatelessWidget {
  /// The host rebuilds it with each new level (today's voice sheet).
  const KitLevelMeter({
    super.key,
    required double this.level,
    this.active = true,
    this.meterKey,
  }) : listenable = null;

  /// Repaints on each new level without rebuilding the host (the composer
  /// field at ~30 updates a second; PERF-4).
  const KitLevelMeter.listen({
    super.key,
    required ValueListenable<double> this.listenable,
    this.active = true,
    this.meterKey,
  }) : level = null;

  /// The microphone's input level, 0..1 (clamped; NaN reads as 0). Null when
  /// built with [KitLevelMeter.listen].
  final double? level;

  /// Repaints this part alone on each new value, without rebuilding the
  /// host. Null when built with the default constructor.
  final ValueListenable<double>? listenable;

  /// False while the mic is not listening (paused, transcribing): every bar
  /// sits at rest, whatever [level] or [listenable] says.
  final bool active;

  /// A test hook on the painted surface: finds the [RenderRepaintBoundary]
  /// that wraps the bars, to scan its painted pixels.
  final Key? meterKey;

  /// How many bars a level lights: bar i (0-based) is lit when
  /// `level >= (i + 1) / 12`, as today, so a normal voice lights about half
  /// and a shout lights all nine at 0.75.
  static int litFor(double level) {
    final v = level.isNaN ? 0.0 : level.clamp(0.0, 1.0);
    var lit = 0;
    for (var i = 0; i < KitTokens.meterBars; i++) {
      if (v >= (i + 1) / _litSteps) lit++;
    }
    return lit;
  }

  /// The lighting formula's denominator: the frozen spec's own number (not
  /// a design token), fixed independently of [KitTokens.meterBars] so a
  /// normal voice still lights about half at nine bars.
  static const _litSteps = 12;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final size = _levelMeterSize(tokens);
    final dpr = MediaQuery.devicePixelRatioOf(context);
    final direction = Directionality.of(context);
    final litColor = tokens.roles.accent;
    final unlitColor = tokens.roles.surface3;

    Widget paintAt(double value) => CustomPaint(
      size: size,
      painter: _LevelMeterPainter(
        level: value,
        active: active,
        litColor: litColor,
        unlitColor: unlitColor,
        gap: tokens.space1,
        devicePixelRatio: dpr,
        textDirection: direction,
      ),
    );

    final meter = listenable != null
        ? ValueListenableBuilder<double>(
            valueListenable: listenable!,
            builder: (context, value, _) => paintAt(value),
          )
        : paintAt(level ?? 0);

    return ExcludeSemantics(
      child: RepaintBoundary(
        key: meterKey,
        child: SizedBox.fromSize(size: size, child: meter),
      ),
    );
  }
}

/// The meter's fixed footprint (KitLevelMeter.md "Tokens"): [KitTokens
/// .meterBars] bars at [KitTokens.meterBarWidth] plus a gap of [KitTokens
/// .space1] between each, by the tallest bar, [KitTokens.meterBarMax].
Size _levelMeterSize(KitTokens tokens) {
  const bars = KitTokens.meterBars;
  final width = bars * KitTokens.meterBarWidth + (bars - 1) * tokens.space1;
  return Size(width, KitTokens.meterBarMax);
}

/// Paints the bars (MOT-5: paint only, no layout, its own [RepaintBoundary]).
/// Bars light from the start edge: index 0 first, mirrored in RTL (LAY-8).
/// Every edge, width and height snaps to whole physical pixels (LOOK-21
/// pre-wave seam: [KitTokens.hairlineWidth] is not used here).
class _LevelMeterPainter extends CustomPainter {
  const _LevelMeterPainter({
    required this.level,
    required this.active,
    required this.litColor,
    required this.unlitColor,
    required this.gap,
    required this.devicePixelRatio,
    required this.textDirection,
  });

  final double level;
  final bool active;
  final Color litColor;
  final Color unlitColor;
  final double gap;
  final double devicePixelRatio;
  final TextDirection textDirection;

  double _snap(double value) => devicePixelRatio > 0
      ? (value * devicePixelRatio).round() / devicePixelRatio
      : value;

  @override
  void paint(Canvas canvas, Size size) {
    const bars = KitTokens.meterBars;
    const center = (bars - 1) / 2;
    final litCount = active ? KitLevelMeter.litFor(level) : 0;
    final width = _snap(KitTokens.meterBarWidth);
    final step = width + _snap(gap);
    final paint = Paint()..style = PaintingStyle.fill;
    for (var i = 0; i < bars; i++) {
      final rawHeight =
          KitTokens.meterBarMin +
          (KitTokens.meterBarMax - KitTokens.meterBarMin) *
              (i - center).abs() /
              center;
      final height = _snap(rawHeight);
      final visualIndex = textDirection == TextDirection.rtl ? bars - 1 - i : i;
      final x = _snap(visualIndex * step);
      final y = _snap((size.height - height) / 2);
      paint.color = i < litCount ? litColor : unlitColor;
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, y, width, height),
          Radius.circular(width / 2),
        ),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _LevelMeterPainter oldDelegate) =>
      level != oldDelegate.level ||
      active != oldDelegate.active ||
      litColor != oldDelegate.litColor ||
      unlitColor != oldDelegate.unlitColor ||
      gap != oldDelegate.gap ||
      devicePixelRatio != oldDelegate.devicePixelRatio ||
      textDirection != oldDelegate.textDirection;
}
