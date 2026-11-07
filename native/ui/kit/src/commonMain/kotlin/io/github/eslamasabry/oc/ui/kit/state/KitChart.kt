package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentCardChartBar
import io.github.eslamasabry.oc.ui.words.res.agentCardChartHighest
import io.github.eslamasabry.oc.ui.words.res.agentCardChartLine
import io.github.eslamasabry.oc.ui.words.res.agentCardChartNone
import io.github.eslamasabry.oc.ui.words.res.agentCardChartPoints
import io.github.eslamasabry.oc.ui.words.res.agentCardChartSeries
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Which chart [KitChart] draws (Dart `KitChartKind`). */
public enum class KitChartKind { Bar, Line }

/** One named run of values, one per label (Dart `KitChartSeries`). */
@Immutable
public data class KitChartSeries(val name: String, val values: List<Double>)

/**
 * The words of a chart's text summary (Dart `KitChartWords`). [kitChartWords] gives the app's own (the agent-card
 * strings, which say the same as Flutter's English defaults).
 */
@Immutable
public class KitChartWords(
    public val barChart: String,
    public val lineChart: String,
    /** "3 series". */
    public val seriesCount: (Int) -> String,
    /** "12 points". */
    public val pointCount: (Int) -> String,
    /** "highest March: 42 ms"; `where` names the series (when there are several) and the label, `value` the unit. */
    public val highest: (where: String, value: String) -> String,
    /** Said when no value is finite. */
    public val noData: String,
)

/** The chart words from `ui/words`, for every count a chart can say (at most [KitChart.MAX_SERIES] and [KitChart.MAX_POINTS]). */
@Composable
public fun kitChartWords(): KitChartWords {
    val bar = stringResource(Res.string.agentCardChartBar)
    val line = stringResource(Res.string.agentCardChartLine)
    val none = stringResource(Res.string.agentCardChartNone)
    val series = List(KitChart.MAX_SERIES + 1) { pluralStringResource(Res.plurals.agentCardChartSeries, it, it) }
    val points = List(KitChart.MAX_POINTS + 1) { pluralStringResource(Res.plurals.agentCardChartPoints, it, it) }
    val where = "\u0001"
    val value = "\u0002"
    val highest = stringResource(Res.string.agentCardChartHighest, where, value)
    return remember(bar, line, none, series, points, highest) {
        KitChartWords(
            barChart = bar,
            lineChart = line,
            seriesCount = { series[it.coerceIn(0, KitChart.MAX_SERIES)] },
            pointCount = { points[it.coerceIn(0, KitChart.MAX_POINTS)] },
            highest = { w, v -> highest.replace(where, w).replace(value, v) },
            noData = none,
        )
    }
}

/** The pure rules of [KitChart] (Dart `KitChart.summaryOf`, `_plain`, `_pointsOf`). */
public object KitChart {
    public const val MAX_SERIES: Int = 3 // F: lib/ui/kit/kit_chart.dart:86
    public const val MAX_POINTS: Int = 30 // F: lib/ui/kit/kit_chart.dart:87

    /** The text read for the chart: kind, series, points and the highest value. */
    public fun summaryOf(
        kind: KitChartKind,
        labels: List<String>,
        series: List<KitChartSeries>,
        unit: String?,
        words: KitChartWords,
    ): String {
        val count = pointsOf(labels, series)
        val head =
            "${if (kind == KitChartKind.Bar) words.barChart else words.lineChart}, " +
                "${words.seriesCount(series.size)}, ${words.pointCount(count)}"
        var best: Double? = null
        var where = ""
        for (s in series) {
            var i = 0
            while (i < s.values.size && i < MAX_POINTS) {
                val v = s.values[i]
                if (v.isFinite() && (best == null || v > best)) {
                    best = v
                    val label = if (i < labels.size) labels[i] else "${i + 1}"
                    where = if (series.size > 1) "${s.name}, $label" else label
                }
                i++
            }
        }
        if (best == null) return "$head; ${words.noData}"
        val value = plain(best)
        val shown =
            when {
                unit.isNullOrEmpty() -> value
                unit == "%" -> "$value%"
                else -> "$value $unit"
            }
        return "$head; ${words.highest(where, shown)}"
    }

    /** A value as a person says it: whole numbers bare, else two decimals at most, no trailing zeros. */
    public fun plain(v: Double): String {
        if (v == roundAway(v)) return fixed(v, 0)
        var text = fixed(v, 2) // F: lib/ui/kit/kit_chart.dart:141
        while (text.endsWith('0')) text = text.dropLast(1)
        return text
    }

    /** The points drawn: the longest of the labels and every series, at most [MAX_POINTS]. */
    public fun pointsOf(labels: List<String>, series: List<KitChartSeries>): Int {
        var n = labels.size
        for (s in series) n = max(n, s.values.size)
        return min(n, MAX_POINTS)
    }
}

/** Dart `double.round()`: halves away from zero. */
internal fun roundAway(x: Double): Double = if (x < 0) -floor(-x + HALF) else floor(x + HALF)

/** Dart `toStringAsFixed` for the plain numbers a chart writes. */
internal fun fixed(v: Double, decimals: Int): String {
    val p = TEN.pow(decimals)
    val whole = floor(abs(v) * p + HALF).toLong()
    val digits = whole.toString().padStart(decimals + 1, '0')
    val sign = if (v < 0) "-" else ""
    return if (decimals == 0) sign + digits else sign + digits.dropLast(decimals) + "." + digits.takeLast(decimals)
}

private const val HALF = .5 // F: lib/ui/kit/kit_chart.dart:413
private const val TEN = 10.0 // F: lib/ui/kit/kit_chart.dart:293
private val LN10 = ln(TEN)

/** One scale for every axis label and every mark: it always holds zero (Dart `_Scale`). */
internal class ChartScale(val min: Double, val max: Double, val step: Double) {
    companion object {
        fun of(runs: List<List<Double>>): ChartScale {
            var lo = 0.0
            var hi = 0.0
            for (run in runs) for (v in run) {
                if (!v.isFinite()) continue
                lo = min(lo, v)
                hi = max(hi, v)
            }
            if (hi == lo) hi = lo + 1
            val raw = (hi - lo) / 4 // F: lib/ui/kit/kit_chart.dart:292
            val mag = TEN.pow(floor(ln(raw) / LN10))
            val norm = raw / mag
            val nice =
                when {
                    norm <= 1 -> 1.0
                    norm <= 2 -> 2.0 // F: lib/ui/kit/kit_chart.dart:298-299
                    norm <= 5 -> 5.0 // F: lib/ui/kit/kit_chart.dart:300-301
                    else -> 10.0 // F: lib/ui/kit/kit_chart.dart:302
                }
            val step = nice * mag
            return ChartScale(floor(lo / step) * step, ceil(hi / step) * step, step)
        }
    }

    val ticks: List<Double>
        get() {
            val out = ArrayList<Double>()
            var v = min
            while (v <= max + step / 2 && out.size < 12) { // F: lib/ui/kit/kit_chart.dart:309
                out += roundAway(v / step) * step
                v += step
            }
            return out
        }

    fun format(v: Double): String {
        val big = step >= 1000 && abs(v) >= 1000 // F: lib/ui/kit/kit_chart.dart:316
        val value = if (big) v / 1000 else v // F: lib/ui/kit/kit_chart.dart:317
        val unit = if (big) "k" else ""
        val decimals =
            if (step >= 1 || big) (if (big && step % 1000 != 0.0) 1 else 0) // F: lib/ui/kit/kit_chart.dart:320
            else ceil(-(ln(step) / LN10)).toInt().coerceIn(0, 6) // F: lib/ui/kit/kit_chart.dart:321
        var text = fixed(value, decimals)
        if (text.startsWith("-") && text.substring(1).toDouble() == 0.0) text = text.substring(1)
        return "$text$unit"
    }
}

/**
 * A bar or line chart of up to [KitChart.MAX_SERIES] series of up to [KitChart.MAX_POINTS] points, drawn with a canvas
 * in the theme's roles: accent, codeType and codeKeyword, never amber (Dart `KitChart`, lib/ui/kit/kit_chart.dart).
 * Every axis label comes from one scale (zero is always in it), a legend names the series when there are several, and
 * [unit] sits above the plot. All-zero data draws the empty scale, negatives hang below the zero line, a single point
 * is a dot or one bar. The chart is one picture to a screen reader ([KitChart.summaryOf]). The drawing grows once when
 * it first appears; under reduced motion it shows its finished frame at once.
 *
 * States: none (an empty chart draws its axes and says "no data")
 * Cap: Reading
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitChart(
    kind: KitChartKind,
    labels: List<String>,
    series: List<KitChartSeries>,
    modifier: Modifier = Modifier,
    unit: String? = null,
    words: KitChartWords = kitChartWords(),
    plotHeight: Int = 160, // F: lib/ui/kit/kit_chart.dart:83
) {
    kitAssert(series.size <= KitChart.MAX_SERIES && KitChart.pointsOf(labels, series) <= KitChart.MAX_POINTS) {
        "KitChart draws at most ${KitChart.MAX_SERIES} series of ${KitChart.MAX_POINTS} points (agent-card contract)"
    }
    val colors = LocalKitColors.current
    val shown = series.take(KitChart.MAX_SERIES)
    val inks = listOf(colors.accent, colors.codeType, colors.codeKeyword)
    val axisStyle = LocalKitTypography.current.style(TypeRole.Caption).copy(color = colors.tone(TextTone.Secondary))
    val density = LocalDensity.current
    val scaled = with(density) { axisStyle.fontSize.toDp().value }
    val labelRoom = scaled * 1.4f + 8 // F: lib/ui/kit/kit_chart.dart:176
    val summary = KitChart.summaryOf(kind, labels, shown, unit, words)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, specs.enter()) }
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().clearAndSetSemantics {
            contentDescription = summary
            role = Role.Image
        },
    ) {
        if (!unit.isNullOrEmpty()) {
            KitText(unit, Modifier.padding(bottom = Space.S1.dp), role = TypeRole.Caption, tone = TextTone.Secondary)
        }
        Canvas(Modifier.fillMaxWidth().height((plotHeight + labelRoom).dp)) {
            drawChart(
                ChartPaint(kind, labels, shown, inks, colors.hairline, colors.text3, axisStyle, measurer, rtl, labelRoom.dp.toPx(), (plotHeight + labelRoom).dp.toPx()),
                progress.value,
            )
        }
        if (shown.size > 1) {
            FlowRow(
                Modifier.padding(top = Space.S2.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                shown.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ink = inks[i]
                        Canvas(Modifier.size(LEGEND_DOT.dp)) { drawCircle(ink) }
                        Spacer(Modifier.width(Space.S2.dp))
                        KitText(s.name, Modifier.weight(1f, fill = false), role = TypeRole.Caption, tone = TextTone.Secondary)
                    }
                }
            }
        }
    }
}

private const val LEGEND_DOT = 10 // F: lib/ui/kit/kit_chart.dart:240

/** What the painter reads (Dart `_ChartPainter` fields). */
private class ChartPaint(
    val kind: KitChartKind,
    val labels: List<String>,
    val series: List<KitChartSeries>,
    val inks: List<Color>,
    val grid: Color,
    val zero: Color,
    val axisStyle: TextStyle,
    val measurer: TextMeasurer,
    val rtl: Boolean,
    val labelRoom: Float,
    /** The box's exact height (the canvas itself is a whole number of pixels tall). */
    val height: Float,
)

/** A laid-out label and its width as Flutter's TextPainter reports it (the intrinsic width, capped). */
private class Label(val layout: TextLayoutResult, val width: Float) {
    val height: Float get() = layout.size.height.toFloat()
}

private fun DrawScope.text(p: ChartPaint, text: String, maxWidth: Float = Float.POSITIVE_INFINITY): Label {
    val bound = if (maxWidth.isFinite()) Constraints(maxWidth = max(0, floor(maxWidth).toInt())) else Constraints()
    val layout =
        p.measurer.measure(
            text,
            p.axisStyle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = bound,
            layoutDirection = if (p.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            density = this,
        )
    return Label(layout, min(layout.multiParagraph.intrinsics.maxIntrinsicWidth, maxWidth))
}

private fun DrawScope.drawChart(p: ChartPaint, progress: Float) {
    val count = KitChart.pointsOf(p.labels, p.series)
    val scale = ChartScale.of(p.series.map { it.values })
    val ticks = scale.ticks
    val tickText = ticks.map { text(p, scale.format(it)) }
    val gap = 8.dp.toPx() // F: lib/ui/kit/kit_chart.dart:378
    var axisWidth = 0f
    for (t in tickText) axisWidth = max(axisWidth, t.width)
    axisWidth += gap
    val plot =
        Rect(
            if (p.rtl) 0f else axisWidth,
            4.dp.toPx(), // F: lib/ui/kit/kit_chart.dart:381
            if (p.rtl) size.width - axisWidth else size.width,
            p.height - p.labelRoom,
        )
    if (plot.width <= 0 || plot.height <= 0) return
    val yOf = { v: Double -> (plot.bottom - (v - scale.min) / (scale.max - scale.min) * plot.height).toFloat() }

    // Grid, zero line and the y labels from the one scale.
    val stroke = 1.dp.toPx()
    for (i in ticks.indices) {
        val y = yOf(ticks[i])
        val isZero = abs(ticks[i]) < scale.step / 1000 // F: lib/ui/kit/kit_chart.dart:395
        drawLine(if (isZero) p.zero else p.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = stroke)
        val label = tickText[i]
        val x = if (p.rtl) plot.right + gap else axisWidth - gap - label.width
        drawText(label.layout, topLeft = Offset(x, y - label.height / 2)) // F: lib/ui/kit/kit_chart.dart:407
    }
    if (count == 0) return

    val slot = plot.width / count
    val centerOf = { i: Int -> if (p.rtl) plot.right - (i + HALF.toFloat()) * slot else plot.left + (i + HALF.toFloat()) * slot }

    // The x labels: every nth, so none touch.
    var widest = 0f
    for (i in 0 until count) {
        val t = if (i < p.labels.size) p.labels[i] else "${i + 1}"
        widest = max(widest, text(p, t, plot.width).width)
    }
    val fit = max(1, floor(plot.width / (widest + gap)).toInt())
    val stride = ceil(count.toDouble() / fit).toInt()
    val labelTop = plot.bottom + 4.dp.toPx() // F: lib/ui/kit/kit_chart.dart:429
    var i = 0
    while (i < count) {
        val t = if (i < p.labels.size) p.labels[i] else "${i + 1}"
        val label = text(p, t, slot * stride)
        drawText(label.layout, topLeft = Offset(centerOf(i) - label.width / 2, labelTop)) // F: lib/ui/kit/kit_chart.dart:556
        i += stride
    }

    if (p.kind == KitChartKind.Bar) bars(p, count, slot, centerOf, yOf, progress) else lines(p, plot, count, centerOf, yOf, progress)
}

private fun DrawScope.bars(
    p: ChartPaint,
    count: Int,
    slot: Float,
    centerOf: (Int) -> Float,
    yOf: (Double) -> Float,
    progress: Float,
) {
    val inner = slot * .72f // F: lib/ui/kit/kit_chart.dart:448
    val each = max(2.dp.toPx(), inner / p.series.size) // F: lib/ui/kit/kit_chart.dart:449
    val zeroY = yOf(0.0)
    val inset = HALF.toFloat().dp.toPx()
    val r = CornerRadius(FlutterKitTokens.PROGRESS_BAR_RADIUS.dp.toPx())
    for (s in p.series.indices) {
        val values = p.series[s].values
        for (i in 0 until min(count, values.size)) {
            val v = values[i]
            if (!v.isFinite() || v == 0.0) continue
            val x = centerOf(i) - each * p.series.size / 2 + each * s // F: lib/ui/kit/kit_chart.dart:456
            val end = zeroY + (yOf(v) - zeroY) * progress
            val rect = Rect(x + inset, min(zeroY, end), x + each - inset, max(zeroY, end))
            val up = if (v > 0) r else CornerRadius.Zero
            val down = if (v < 0) r else CornerRadius.Zero
            drawPath(Path().apply { addRoundRect(RoundRect(rect, up, up, down, down)) }, p.inks[s])
        }
    }
}

private fun DrawScope.lines(
    p: ChartPaint,
    plot: Rect,
    count: Int,
    centerOf: (Int) -> Float,
    yOf: (Double) -> Float,
    progress: Float,
) {
    // The line draws in from its start edge.
    val reach = plot.width * progress
    val pad = 4.dp.toPx() // F: lib/ui/kit/kit_chart.dart:498-499
    val far = 1e5f // F: lib/ui/kit/kit_chart.dart:498-499
    val (l, r) = if (p.rtl) (plot.right - reach - pad) to (plot.right + pad) else (plot.left - pad) to (plot.left + reach + pad)
    clipRect(l, 0f, r, far) {
        for (s in p.series.indices) {
            val values = p.series[s].values
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) // F: lib/ui/kit/kit_chart.dart:506
            val path = Path()
            var open = false
            val dots = ArrayList<Offset>()
            var i = 0
            while (i < count && i < values.size) {
                val v = values[i]
                if (!v.isFinite()) {
                    open = false
                    i++
                    continue
                }
                val pt = Offset(centerOf(i), yOf(v))
                dots += pt
                if (open) path.lineTo(pt.x, pt.y) else {
                    path.moveTo(pt.x, pt.y)
                    open = true
                }
                i++
            }
            drawPath(path, p.inks[s], style = stroke)
            // Few points read as points; a long line stays a line.
            if (dots.size <= 12) for (d in dots) drawCircle(p.inks[s], radius = 3.5f.dp.toPx(), center = d) // F: lib/ui/kit/kit_chart.dart:530,532
        }
    }
}
