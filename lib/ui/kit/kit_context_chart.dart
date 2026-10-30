import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../l10n/app_localizations.dart';
import '../theme_roles.dart';
import 'kit_context_ring.dart';
import 'kit_max_width.dart';
import 'kit_tokens.dart';

/// One assistant reply in a [KitContextChart]: the share of the window the
/// conversation used after it.
@immutable
class KitContextBar {
  const KitContextBar({required this.fraction, this.compactedBefore = false});

  /// 0..1 and over (the bar is clamped to the chart's top).
  final double fraction;

  /// The conversation was compacted between the reply before and this one,
  /// so the bar is lower than its neighbour on purpose.
  final bool compactedBefore;
}

/// How full the context window was after each reply: one bar per reply,
/// coloured by level (the newest in the level's strong colour), a dashed
/// line where compacting is suggested, and a thin mark where the
/// conversation was compacted (docs/ux-system/kit-api/KitContextChart.md).
/// At most [maxBars] bars show, the newest ones.
///
/// States: none — it draws the bars it is given; fewer than two bars draw
/// nothing (one reply has no history to chart).
class KitContextChart extends StatelessWidget {
  const KitContextChart({
    super.key,
    required this.bars,
    this.threshold = 0.8,
    this.chartKey,
  });

  /// The most bars drawn.
  static const maxBars = 40;

  /// Oldest first.
  final List<KitContextBar> bars;

  /// Where the dashed line sits, as a share of the window.
  final double threshold;

  /// A test hook on the painted surface.
  final Key? chartKey;

  /// The bars that are drawn: the newest [maxBars].
  List<KitContextBar> get shown =>
      bars.length <= maxBars ? bars : bars.sublist(bars.length - maxBars);

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final drawn = shown;
    if (drawn.length < 2) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final locale = Localizations.localeOf(context).toString();
    final format = NumberFormat.decimalPattern(locale);
    final newest = KitContextLevel.percentOf(drawn.last.fraction);
    final marker = l10n.kitModelContext(
      format.format(KitContextLevel.percentOf(threshold)),
    );
    final labelStyle = Theme.of(context).textTheme.labelSmall!.copyWith(
      color: roles.text2,
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    return Semantics(
      container: true,
      label: l10n.kitContextChartSemantics(
        format.format(drawn.length),
        format.format(newest),
      ),
      excludeSemantics: true,
      child: SizedBox(
        height: tokens.minTarget * 2.4,
        width: double.infinity,
        child: CustomPaint(
          key: chartKey,
          painter: KitContextChartPainter(
            bars: drawn,
            threshold: threshold,
            roles: roles,
            marker: marker,
            markerStyle: labelStyle,
            direction: Directionality.of(context),
            textScaler: MediaQuery.textScalerOf(context),
            gap: tokens.space1,
            cornerRadius: tokens.iconTileRadius,
          ),
        ),
      ),
    );
  }
}

/// Draws the bars of a [KitContextChart]. Public so a test can count what
/// was drawn.
class KitContextChartPainter extends CustomPainter {
  const KitContextChartPainter({
    required this.bars,
    required this.threshold,
    required this.roles,
    required this.marker,
    required this.markerStyle,
    required this.direction,
    required this.textScaler,
    required this.gap,
    required this.cornerRadius,
  });

  final List<KitContextBar> bars;
  final double threshold;
  final ThemeRoles roles;
  final String marker;
  final TextStyle markerStyle;
  final TextDirection direction;
  final TextScaler textScaler;
  final double gap;
  final double cornerRadius;

  /// The colour of a bar: the level's, calm bars quiet unless newest.
  Color colorOf(int index) {
    final level = KitContextLevel.of(bars[index].fraction);
    if (index == bars.length - 1) return level.colorIn(roles);
    return level == KitContextLevel.normal ? roles.text3 : level.colorIn(roles);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final text = TextPainter(
      text: TextSpan(text: marker, style: markerStyle),
      textDirection: direction,
      textScaler: textScaler,
      maxLines: 1,
    )..layout();
    final rtl = direction == TextDirection.rtl;
    // The marker sits at the end edge, above the dashed line, so it never
    // covers a bar's top.
    final top = text.height + 2;
    final plot = Rect.fromLTWH(0, top, size.width, size.height - top);
    double yOf(double fraction) =>
        plot.bottom - plot.height * fraction.clamp(0.0, 1.0);
    final slot = plot.width / bars.length;
    final barWidth = (slot - gap).clamp(2.0, 18.0);

    canvas.drawLine(
      Offset(0, plot.bottom),
      Offset(size.width, plot.bottom),
      Paint()
        ..color = roles.surface3
        ..strokeWidth = 1,
    );

    for (var i = 0; i < bars.length; i++) {
      final index = rtl ? bars.length - 1 - i : i;
      final x = index * slot + (slot - barWidth) / 2;
      final bar = bars[i];
      final y = yOf(bar.fraction);
      if (bar.compactedBefore) {
        final edge = x - (slot - barWidth) / 2;
        canvas.drawLine(
          Offset(edge, plot.top),
          Offset(edge, plot.bottom),
          Paint()
            ..color = roles.text3
            ..strokeWidth = 1,
        );
      }
      canvas.drawRRect(
        RRect.fromRectAndCorners(
          Rect.fromLTRB(x, y, x + barWidth, plot.bottom),
          topLeft: Radius.circular(math.min(cornerRadius, barWidth / 2)),
          topRight: Radius.circular(math.min(cornerRadius, barWidth / 2)),
        ),
        Paint()..color = colorOf(i),
      );
    }

    final lineY = yOf(threshold);
    final dash = Paint()
      ..color = roles.attention
      ..strokeWidth = 1.2;
    for (double x = 0; x < size.width; x += 8) {
      canvas.drawLine(
        Offset(x, lineY),
        Offset((x + 4).clamp(0, size.width), lineY),
        dash,
      );
    }
    text.paint(
      canvas,
      Offset(rtl ? 0 : size.width - text.width, lineY - text.height - 1),
    );
    text.dispose();
  }

  @override
  bool shouldRepaint(KitContextChartPainter old) =>
      old.bars != bars ||
      old.threshold != threshold ||
      old.roles != roles ||
      old.marker != marker ||
      old.direction != direction;
}
