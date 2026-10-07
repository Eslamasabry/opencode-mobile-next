import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_motion.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Which chart [KitChart] draws.
enum KitChartKind { bar, line }

/// One named run of values, one per label.
@immutable
class KitChartSeries {
  const KitChartSeries({required this.name, required this.values});

  final String name;
  final List<double> values;
}

/// The words of a chart's text summary, from the caller's ARB. The defaults
/// are English so a test or gallery needs none.
@immutable
class KitChartWords {
  const KitChartWords({
    this.barChart = 'Bar chart',
    this.lineChart = 'Line chart',
    this.seriesCount = _series,
    this.pointCount = _points,
    this.highest = _highest,
    this.noData = 'no data',
  });

  final String barChart;
  final String lineChart;

  /// "3 series".
  final String Function(int count) seriesCount;

  /// "12 points".
  final String Function(int count) pointCount;

  /// "highest March: 42 ms"; [where] names the series (when there are
  /// several) and the label, [value] carries the unit.
  final String Function(String where, String value) highest;

  /// Said when no value is finite.
  final String noData;

  static String _series(int n) => '$n series';
  static String _points(int n) => n == 1 ? '1 point' : '$n points';
  static String _highest(String where, String value) =>
      'highest $where: $value';
}

/// A bar or line chart of up to [maxSeries] series of up to [maxPoints]
/// points (docs/design/genui-contract-2026-10-07.md), drawn with a canvas in
/// the theme's roles: accent, success and a neutral, never amber (amber means
/// "needs you"). Every axis label comes from one scale (zero is always in
/// it, so a bar's length stays honest), a legend names the series when
/// there are several, and [unit] sits above the plot. All-zero data draws
/// the empty scale, negatives hang below the zero line, a single point is a
/// dot or one bar.
///
/// The chart is one picture to a screen reader: [KitChart.summaryOf] says
/// "Bar chart, 3 series, 12 points; highest Mar: 42 ms" (in the caller's
/// [words]). The drawing grows once when it first appears, unless the
/// person asked for stillness ([KitMotion.reduced]), when it shows its
/// finished frame at once.
///
/// States: none — it draws the numbers it is given (an empty chart draws
/// its axes and says "no data").
class KitChart extends StatelessWidget {
  const KitChart({
    super.key,
    required this.kind,
    required this.labels,
    required this.series,
    this.unit,
    this.words = const KitChartWords(),
    this.plotHeight = 160,
  });

  static const int maxSeries = 3;
  static const int maxPoints = 30;

  final KitChartKind kind;

  /// The x axis: one label per point ("Mon", "Jan").
  final List<String> labels;
  final List<KitChartSeries> series;

  /// What the numbers count: "ms", "files", "%".
  final String? unit;
  final KitChartWords words;

  /// The plot's height before text scaling; the axis labels add theirs.
  final double plotHeight;

  /// The text read for the chart: kind, series, points and the highest value.
  static String summaryOf({
    required KitChartKind kind,
    required List<String> labels,
    required List<KitChartSeries> series,
    String? unit,
    KitChartWords words = const KitChartWords(),
  }) {
    final count = _pointsOf(labels, series);
    final head =
        '${kind == KitChartKind.bar ? words.barChart : words.lineChart}, '
        '${words.seriesCount(series.length)}, ${words.pointCount(count)}';
    double? best;
    String where = '';
    for (final s in series) {
      for (var i = 0; i < s.values.length && i < maxPoints; i++) {
        final v = s.values[i];
        if (!v.isFinite) continue;
        if (best == null || v > best) {
          best = v;
          final label = i < labels.length ? labels[i] : '${i + 1}';
          where = series.length > 1 ? '${s.name}, $label' : label;
        }
      }
    }
    if (best == null) return '$head; ${words.noData}';
    final value = _plain(best);
    final shown = unit == null || unit.isEmpty
        ? value
        : unit == '%'
        ? '$value%'
        : '$value $unit';
    return '$head; ${words.highest(where, shown)}';
  }

  /// A value as a person says it: whole numbers bare, else two decimals at
  /// most, no trailing zeros.
  static String _plain(double v) {
    if (v == v.roundToDouble()) return v.toStringAsFixed(0);
    var text = v.toStringAsFixed(2);
    while (text.endsWith('0')) {
      text = text.substring(0, text.length - 1);
    }
    return text;
  }

  static int _pointsOf(List<String> labels, List<KitChartSeries> series) {
    var n = labels.length;
    for (final s in series) {
      n = math.max(n, s.values.length);
    }
    return math.min(n, maxPoints);
  }

  @override
  Widget build(BuildContext context) {
    assert(
      series.length <= maxSeries && _pointsOf(labels, series) <= maxPoints,
      'KitChart draws at most $maxSeries series of $maxPoints points '
      '(agent-card contract)',
    );
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final shown = series.take(maxSeries).toList();
    final colors = _colorsOf(roles);
    final scaler = MediaQuery.textScalerOf(context);
    final axisStyle = KitText.styleOf(
      context,
      KitTextRole.caption,
      tone: KitTextTone.secondary,
    );
    final labelRoom = scaler.scale(axisStyle.fontSize ?? 12) * 1.4 + 8;
    final summary = summaryOf(
      kind: kind,
      labels: labels,
      series: shown,
      unit: unit,
      words: words,
    );
    final unitText = unit;
    return Semantics(
      container: true,
      label: summary,
      image: true,
      excludeSemantics: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (unitText != null && unitText.isNotEmpty)
            Padding(
              padding: EdgeInsetsDirectional.only(bottom: tokens.space1),
              child: KitText(
                unitText,
                role: KitTextRole.caption,
                tone: KitTextTone.secondary,
              ),
            ),
          TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: 1),
            duration: KitMotion.reduced(context)
                ? Duration.zero
                : KitMotion.standard,
            curve: KitMotion.enter,
            builder: (context, t, _) => SizedBox(
              height: plotHeight + labelRoom,
              child: CustomPaint(
                painter: _ChartPainter(
                  kind: kind,
                  labels: labels,
                  series: shown,
                  colors: colors,
                  grid: roles.hairline,
                  zero: roles.text3,
                  axisStyle: axisStyle,
                  scaler: scaler,
                  rtl: Directionality.of(context) == TextDirection.rtl,
                  labelRoom: labelRoom,
                  progress: t,
                ),
              ),
            ),
          ),
          if (shown.length > 1)
            Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space2),
              child: Wrap(
                spacing: tokens.space4,
                runSpacing: tokens.space1,
                children: [
                  for (var i = 0; i < shown.length; i++)
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox.square(
                          dimension: 10,
                          child: DecoratedBox(
                            decoration: ShapeDecoration(
                              color: colors[i],
                              shape: const CircleBorder(),
                            ),
                          ),
                        ),
                        SizedBox(width: tokens.space2),
                        Flexible(
                          child: KitText(
                            shown[i].name,
                            role: KitTextRole.caption,
                            tone: KitTextTone.secondary,
                          ),
                        ),
                      ],
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  static List<Color> _colorsOf(ThemeRoles roles) => [
    roles.accent,
    roles.codeType,
    roles.codeKeyword,
  ];
}

/// One scale for every axis label and every mark: it always holds zero.
class _Scale {
  const _Scale(this.min, this.max, this.step);

  final double min;
  final double max;
  final double step;

  factory _Scale.of(List<List<double>> runs, [List<double> extra = const []]) {
    var lo = 0.0;
    var hi = 0.0;
    for (final run in [...runs, extra]) {
      for (final v in run) {
        if (!v.isFinite) continue;
        lo = math.min(lo, v);
        hi = math.max(hi, v);
      }
    }
    if (hi == lo) hi = lo + 1;
    final raw = (hi - lo) / 4;
    final mag = math.pow(10, (math.log(raw) / math.ln10).floor()).toDouble();
    final norm = raw / mag;
    final step =
        (norm <= 1
            ? 1
            : norm <= 2
            ? 2
            : norm <= 5
            ? 5
            : 10) *
        mag;
    return _Scale((lo / step).floor() * step, (hi / step).ceil() * step, step);
  }

  List<double> get ticks {
    final out = <double>[];
    for (var v = min; v <= max + step / 2 && out.length < 12; v += step) {
      out.add((v / step).round() * step);
    }
    return out;
  }

  String format(double v) {
    final big = step >= 1000 && v.abs() >= 1000;
    final value = big ? v / 1000 : v;
    final unit = big ? 'k' : '';
    final decimals = step >= 1 || big
        ? (big && step % 1000 != 0 ? 1 : 0)
        : (-(math.log(step) / math.ln10)).ceil().clamp(0, 6);
    var text = value.toStringAsFixed(decimals);
    if (text.startsWith('-') && double.parse(text) == 0) {
      text = text.substring(1);
    }
    return '$text$unit';
  }
}

class _ChartPainter extends CustomPainter {
  _ChartPainter({
    required this.kind,
    required this.labels,
    required this.series,
    required this.colors,
    required this.grid,
    required this.zero,
    required this.axisStyle,
    required this.scaler,
    required this.rtl,
    required this.labelRoom,
    required this.progress,
  });

  final KitChartKind kind;
  final List<String> labels;
  final List<KitChartSeries> series;
  final List<Color> colors;
  final Color grid;
  final Color zero;
  final TextStyle axisStyle;
  final TextScaler scaler;
  final bool rtl;
  final double labelRoom;
  final double progress;

  TextPainter _text(String text, {double? maxWidth}) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: axisStyle),
      textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
      textScaler: scaler,
      maxLines: 1,
      ellipsis: '…',
    )..layout(maxWidth: maxWidth ?? double.infinity);
    return painter;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final count = KitChart._pointsOf(labels, series);
    final scale = _Scale.of([for (final s in series) s.values]);
    final ticks = scale.ticks;
    final tickText = [for (final v in ticks) _text(scale.format(v))];
    var axisWidth = 0.0;
    for (final t in tickText) {
      axisWidth = math.max(axisWidth, t.width);
    }
    axisWidth += 8;
    final plot = Rect.fromLTRB(
      rtl ? 0 : axisWidth,
      4,
      rtl ? size.width - axisWidth : size.width,
      size.height - labelRoom,
    );
    if (plot.width <= 0 || plot.height <= 0) return;
    double yOf(double v) =>
        plot.bottom - (v - scale.min) / (scale.max - scale.min) * plot.height;

    // Grid, zero line and the y labels from the one scale.
    final gridPaint = Paint()
      ..color = grid
      ..strokeWidth = 1;
    for (var i = 0; i < ticks.length; i++) {
      final y = yOf(ticks[i]);
      final isZero = ticks[i].abs() < scale.step / 1000;
      canvas.drawLine(
        Offset(plot.left, y),
        Offset(plot.right, y),
        isZero
            ? (Paint()
                ..color = zero
                ..strokeWidth = 1)
            : gridPaint,
      );
      final label = tickText[i];
      final x = rtl ? plot.right + 8 : axisWidth - 8 - label.width;
      label.paint(canvas, Offset(x, y - label.height / 2));
    }
    if (count == 0) return;

    final slot = plot.width / count;
    double centerOf(int i) =>
        rtl ? plot.right - (i + .5) * slot : plot.left + (i + .5) * slot;

    // The x labels: every nth, so none touch.
    var widest = 0.0;
    final measured = <int, TextPainter>{};
    for (var i = 0; i < count; i++) {
      final text = i < labels.length ? labels[i] : '${i + 1}';
      final painter = _text(text, maxWidth: plot.width);
      measured[i] = painter;
      widest = math.max(widest, painter.width);
    }
    final fit = math.max(1, (plot.width / (widest + 8)).floor());
    final stride = (count / fit).ceil();
    for (var i = 0; i < count; i += stride) {
      final text = i < labels.length ? labels[i] : '${i + 1}';
      final painter = _text(text, maxWidth: slot * stride);
      canvas.drawText(painter, centerOf(i), plot.bottom + 4);
    }

    if (kind == KitChartKind.bar) {
      _bars(canvas, plot, scale, count, slot, centerOf, yOf);
    } else {
      _lines(canvas, plot, count, centerOf, yOf);
    }
  }

  void _bars(
    Canvas canvas,
    Rect plot,
    _Scale scale,
    int count,
    double slot,
    double Function(int) centerOf,
    double Function(double) yOf,
  ) {
    final inner = slot * .72;
    final each = math.max(2.0, inner / series.length);
    final zeroY = yOf(0);
    for (var s = 0; s < series.length; s++) {
      final paint = Paint()..color = colors[s];
      for (var i = 0; i < count && i < series[s].values.length; i++) {
        final v = series[s].values[i];
        if (!v.isFinite || v == 0) continue;
        final x = centerOf(i) - each * series.length / 2 + each * s;
        final end = zeroY + (yOf(v) - zeroY) * progress;
        final rect = Rect.fromLTRB(
          x + .5,
          math.min(zeroY, end),
          x + each - .5,
          math.max(zeroY, end),
        );
        canvas.drawRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: v > 0
                ? const Radius.circular(KitTokens.progressBarRadius)
                : Radius.zero,
            topRight: v > 0
                ? const Radius.circular(KitTokens.progressBarRadius)
                : Radius.zero,
            bottomLeft: v < 0
                ? const Radius.circular(KitTokens.progressBarRadius)
                : Radius.zero,
            bottomRight: v < 0
                ? const Radius.circular(KitTokens.progressBarRadius)
                : Radius.zero,
          ),
          paint,
        );
      }
    }
  }

  void _lines(
    Canvas canvas,
    Rect plot,
    int count,
    double Function(int) centerOf,
    double Function(double) yOf,
  ) {
    canvas.save();
    // The line draws in from its start edge.
    final reach = plot.width * progress;
    canvas.clipRect(
      rtl
          ? Rect.fromLTRB(plot.right - reach - 4, 0, plot.right + 4, 1e5)
          : Rect.fromLTRB(plot.left - 4, 0, plot.left + reach + 4, 1e5),
    );
    for (var s = 0; s < series.length; s++) {
      final values = series[s].values;
      final stroke = Paint()
        ..color = colors[s]
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..strokeJoin = StrokeJoin.round
        ..strokeCap = StrokeCap.round;
      final dot = Paint()..color = colors[s];
      final path = Path();
      var open = false;
      final dots = <Offset>[];
      for (var i = 0; i < count && i < values.length; i++) {
        final v = values[i];
        if (!v.isFinite) {
          open = false;
          continue;
        }
        final p = Offset(centerOf(i), yOf(v));
        dots.add(p);
        if (open) {
          path.lineTo(p.dx, p.dy);
        } else {
          path.moveTo(p.dx, p.dy);
          open = true;
        }
      }
      canvas.drawPath(path, stroke);
      // Few points read as points; a long line stays a line.
      if (dots.length <= 12) {
        for (final p in dots) {
          canvas.drawCircle(p, 3.5, dot);
        }
      }
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_ChartPainter old) =>
      old.progress != progress ||
      old.kind != kind ||
      old.rtl != rtl ||
      old.labelRoom != labelRoom ||
      old.axisStyle != axisStyle ||
      old.scaler != scaler ||
      old.grid != grid ||
      old.labels != labels ||
      old.series != series ||
      old.colors.first != colors.first;
}

extension on Canvas {
  /// Draws [painter] centred on [x].
  void drawText(TextPainter painter, double x, double y) =>
      painter.paint(this, Offset(x - painter.width / 2, y));
}
