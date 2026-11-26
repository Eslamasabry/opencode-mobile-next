package io.github.eslamasabry.oc.ui.kit.content.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.abs

/** Visual state of [KitPaceBendChart]. */
public sealed interface KitPaceBendState {
    /** Too little use or elapsed time: even-pace line and now dot only, no forecast line. */
    public data object TooLittle : KitPaceBendState

    /** On course to last: dotted forecast from now to the reset edge with a landing label. */
    public data class Lasts(val atReset: Float, val landingLabel: String) : KitPaceBendState

    /** Ahead of pace: dotted forecast to the floor at [runsOutElapsed] plus hatched empty region. */
    public data class RunsOut(val runsOutElapsed: Float, val noneLeftLabel: String) : KitPaceBendState

    /** Already exhausted: hatched empty region from now to reset. */
    public data class Out(val noneLeftLabel: String) : KitPaceBendState
}

/** One axis marker along the bottom of [KitPaceBendChart]. */
public data class KitPaceAxisMark(
    val fraction: Float,
    val label: String? = null,
    val emphasized: Boolean = false,
    val isDivider: Boolean = false,
)

/** Localized edge and guide labels for [KitPaceBendChart]. */
public data class KitPaceBendLabels(
    val reset: String,
    val evenPace: String,
)

/** Input bundle for [KitPaceBendChart]. */
public data class KitPaceBendData(
    val elapsed: Float,
    val left: Float,
    val state: KitPaceBendState,
    val axisMarks: List<KitPaceAxisMark>,
    val labels: KitPaceBendLabels,
    val short: Boolean = false,
)

/**
 * Plain-language forecast sentence paired with an optional attention dot when ahead of pace (B-508).
 * Keeps the words in high-contrast primary ink while adding a non-text visual cue alongside the words.
 *
 * States: neutral (no dot) or attention (8 dp attention dot before the sentence)
 * Text: [text]
 * Cap: Fill
 * Motion: none
 * Hidden: none
 */
@Composable
public fun KitPaceSentence(
    text: String,
    attention: Boolean,
    modifier: Modifier = Modifier,
    short: Boolean = false,
) {
    val colors = LocalKitColors.current
    val role = if (short) TypeRole.Secondary else TypeRole.Body
    if (!attention) {
        KitText(text, modifier, role = role, tone = TextTone.Primary)
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(Modifier.padding(top = DOT_TOP_OFFSET_DP.dp).size(DOT_SIZE_DP.dp)) {
            drawCircle(colors.attention)
        }
        KitText(text, Modifier.weight(1f), role = role, tone = TextTone.Primary)
    }
}

/**
 * Option A pace bend chart for Usage › Limits (B-508, no-history form): draws the even-pace dashed
 * diagonal from 100% at window start to 0% at reset, the now dot, the dotted forecast line and its
 * landing point, and the hatched "None left" floor strip when projected to run out before reset.
 * Time runs with the reading direction (mirrored in RTL).
 *
 * States: [KitPaceBendState.TooLittle], [KitPaceBendState.Lasts], [KitPaceBendState.RunsOut], [KitPaceBendState.Out]
 * Text: axis ticks, Reset, Even pace, and landing/None left labels; full spoken summary in [semantics]
 * Cap: Fill
 * Motion: none (static forecast)
 * Hidden: none (every number is also written in words around the chart)
 */
@Composable
public fun KitPaceBendChart(
    data: KitPaceBendData,
    semantics: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, MAX_FRAME_SCALE)
    val measurer = rememberTextMeasurer()

    val baseCaption = typography.style(TypeRole.Caption)
    val labelHeightDp = (baseCaption.lineHeight.value * fontScale).coerceAtLeast(MIN_LABEL_DP)
    val topDp = TOP_MARGIN_DP + labelHeightDp
    val plotDp = if (data.short) SHORT_PLOT_DP else FULL_PLOT_DP
    val extraBottomDp = if (data.state is KitPaceBendState.RunsOut) labelHeightDp + EXTRA_ROW_GAP_DP else 0f
    val totalHeightDp = topDp + plotDp + BASE_LABEL_GAP_DP + labelHeightDp + BOTTOM_PAD_DP + extraBottomDp

    val safeElapsed = if (data.elapsed.isFinite()) data.elapsed.coerceIn(0f, 1f) else 0f
    val safeLeft = if (data.left.isFinite()) data.left.coerceIn(0f, 1f) else 0f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(totalHeightDp.dp)
            .clearAndSetSemantics { contentDescription = semantics },
    ) {
        val padPx = EDGE_PAD_DP.dp.toPx()
        val rightPx = (size.width - padPx).coerceAtLeast(padPx + 1f)
        val geom = ChartGeom(padPx, rightPx, topDp.dp.toPx(), plotDp.dp.toPx(), rtl)
        val styles =
            ChartStyles(
                regular = baseCaption.copy(color = colors.text3),
                secondary = baseCaption.copy(color = colors.text2),
                reset = baseCaption.copy(color = colors.text2, fontWeight = FontWeight.Medium),
                today = baseCaption.copy(color = colors.text1, fontWeight = FontWeight.SemiBold),
                attention = baseCaption.copy(color = colors.attention, fontWeight = FontWeight.SemiBold),
            )
        val ctx = ChartDrawContext(geom, colors, measurer, styles)
        PaceBendPainter.drawFrameAndAxis(this, ctx, data.axisMarks, data.labels.reset)
        PaceBendPainter.drawEvenPace(this, ctx, data)
        PaceBendPainter.drawForecastAndDot(this, ctx, safeElapsed, safeLeft, data.state)
    }
}

private class ChartGeom(
    val x0: Float,
    val x1: Float,
    val top: Float,
    val plotH: Float,
    val rtl: Boolean,
) {
    val base: Float get() = top + plotH
    val xStart: Float get() = xOf(0f)
    val xReset: Float get() = xOf(1f)

    fun xOf(fraction: Float): Float = PaceBendRules.xForFraction(fraction, x0, x1, rtl)

    fun yOf(leftShare: Float): Float = PaceBendRules.yForLeft(leftShare, top, plotH)
}

private class ChartStyles(
    val regular: TextStyle,
    val secondary: TextStyle,
    val reset: TextStyle,
    val today: TextStyle,
    val attention: TextStyle,
)

private class ChartDrawContext(
    val geom: ChartGeom,
    val colors: KitColors,
    val measurer: TextMeasurer,
    val styles: ChartStyles,
)

private object PaceBendPainter {
    fun drawFrameAndAxis(
        scope: DrawScope,
        ctx: ChartDrawContext,
        axisMarks: List<KitPaceAxisMark>,
        resetLabel: String,
    ) = with(scope) {
        val geom = ctx.geom
        val lineStroke = AXIS_STROKE_DP.dp.toPx()
        drawLine(ctx.colors.text3, Offset(geom.x0, geom.base), Offset(geom.x1, geom.base), lineStroke)
        drawLine(
            ctx.colors.text3,
            Offset(geom.xReset, geom.top - RESET_OVERHANG_DP.dp.toPx()),
            Offset(geom.xReset, geom.base),
            lineStroke,
        )
        val resetLayout = ctx.measurer.measure(resetLabel, ctx.styles.reset)
        val resetX = if (geom.rtl) geom.x0 else (geom.x1 - resetLayout.size.width).coerceAtLeast(geom.x0)
        val resetY = (geom.top - RESET_LABEL_GAP_DP.dp.toPx() - resetLayout.size.height).coerceAtLeast(0f)
        drawText(resetLayout, topLeft = Offset(resetX, resetY))

        val tickLen = TICK_LENGTH_DP.dp.toPx()
        for (mark in axisMarks) {
            if (mark.isDivider) {
                val tx = geom.xOf(mark.fraction)
                drawLine(ctx.colors.text3, Offset(tx, geom.base), Offset(tx, geom.base + tickLen), lineStroke)
            }
        }
        val candidates =
            axisMarks
                .filter { !it.isDivider && !it.label.isNullOrBlank() }
                .map { mark ->
                    val style = if (mark.emphasized) ctx.styles.today else ctx.styles.regular
                    val layout = ctx.measurer.measure(mark.label.orEmpty(), style)
                    PaceLabelCandidate(geom.xOf(mark.fraction), layout.size.width.toFloat(), mark.emphasized, layout)
                }
        val placed = PaceBendRules.placeLabels(candidates, geom.x0, geom.x1, LABEL_GAP_DP.dp.toPx())
        val labelY = geom.base + AXIS_LABEL_GAP_DP.dp.toPx()
        for (item in placed) {
            drawText(item.payload, topLeft = Offset(item.left, labelY))
        }
    }

    fun drawEvenPace(scope: DrawScope, ctx: ChartDrawContext, data: KitPaceBendData) = with(scope) {
        val geom = ctx.geom
        val dash = DASH_LEN_DP.dp.toPx()
        drawLine(
            color = ctx.colors.text3,
            start = Offset(geom.xStart, geom.yOf(1f)),
            end = Offset(geom.xReset, geom.yOf(0f)),
            strokeWidth = EVEN_STROKE_DP.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
        val labelText = data.labels.evenPace
        if (!data.short && data.state !is KitPaceBendState.Out && labelText.isNotBlank()) {
            val usedAbove = data.state is KitPaceBendState.Lasts || data.state is KitPaceBendState.TooLittle
            val frac = if (usedAbove) EVEN_LABEL_BELOW_F else EVEN_LABEL_ABOVE_F
            val layout = ctx.measurer.measure(labelText, ctx.styles.regular)
            val ex = geom.xOf(frac)
            val ey = geom.yOf(1f - frac)
            val rawX = if (usedAbove xor geom.rtl) ex - layout.size.width else ex
            val x = rawX.coerceIn(geom.x0, (geom.x1 - layout.size.width).coerceAtLeast(geom.x0))
            val pad = EVEN_LABEL_PAD_DP.dp.toPx()
            val rawY = if (usedAbove) ey + pad else ey - layout.size.height - pad
            val y = rawY.coerceIn(geom.top, (geom.base - layout.size.height).coerceAtLeast(geom.top))
            drawText(layout, topLeft = Offset(x, y))
        }
    }

    fun drawForecastAndDot(
        scope: DrawScope,
        ctx: ChartDrawContext,
        elapsed: Float,
        left: Float,
        state: KitPaceBendState,
    ) = with(scope) {
        val geom = ctx.geom
        val isAttention = state is KitPaceBendState.RunsOut || state is KitPaceBendState.Out
        val accentColor = if (isAttention) ctx.colors.attention else ctx.colors.accent
        val nowPt = Offset(geom.xOf(elapsed), geom.yOf(left))
        when (state) {
            is KitPaceBendState.TooLittle -> Unit
            is KitPaceBendState.Lasts -> drawLasts(this, ctx, nowPt, state, accentColor)
            is KitPaceBendState.RunsOut -> drawRunsOut(this, ctx, nowPt, elapsed, state)
            is KitPaceBendState.Out -> drawOut(this, ctx, nowPt, state)
        }
        drawCircle(ctx.colors.ground, NOW_RING_DP.dp.toPx(), nowPt)
        drawCircle(accentColor, NOW_DOT_DP.dp.toPx(), nowPt)
    }

    private fun drawLasts(
        scope: DrawScope,
        ctx: ChartDrawContext,
        nowPt: Offset,
        state: KitPaceBendState.Lasts,
        color: Color,
    ) = with(scope) {
        val geom = ctx.geom
        val landPt = Offset(geom.xReset, geom.yOf(state.atReset.coerceIn(0f, 1f)))
        drawDotted(this, nowPt, landPt, color)
        drawLanding(this, landPt, ctx.colors.ground, color)
        if (state.landingLabel.isNotBlank()) {
            val layout = ctx.measurer.measure(state.landingLabel, ctx.styles.secondary)
            val pad = LANDING_LABEL_PAD_DP.dp.toPx()
            val x =
                if (geom.rtl) {
                    (landPt.x + pad).coerceAtMost((geom.x1 - layout.size.width).coerceAtLeast(geom.x0))
                } else {
                    (landPt.x - pad - layout.size.width).coerceAtLeast(geom.x0)
                }
            val below = nowPt.y <= landPt.y + 1f
            val rawY = if (below) landPt.y + pad else landPt.y - layout.size.height - pad
            val y = rawY.coerceIn(geom.top, (geom.base - layout.size.height).coerceAtLeast(geom.top))
            drawText(layout, topLeft = Offset(x, y))
        }
    }

    private fun drawRunsOut(
        scope: DrawScope,
        ctx: ChartDrawContext,
        nowPt: Offset,
        elapsed: Float,
        state: KitPaceBendState.RunsOut,
    ) = with(scope) {
        val geom = ctx.geom
        val color = ctx.colors.attention
        val landPt = Offset(geom.xOf(state.runsOutElapsed.coerceIn(elapsed, 1f)), geom.base)
        drawHatched(this, ctx, landPt.x, geom.xReset)
        drawDotted(this, nowPt, landPt, color)
        drawLanding(this, landPt, ctx.colors.ground, color)
        if (state.noneLeftLabel.isNotBlank()) {
            val layout = ctx.measurer.measure(state.noneLeftLabel, ctx.styles.attention)
            val center = (landPt.x + geom.xReset) / 2f
            val maxX = (geom.x1 - layout.size.width).coerceAtLeast(geom.x0)
            val x = (center - layout.size.width / 2f).coerceIn(geom.x0, maxX)
            val y = geom.base + AXIS_LABEL_GAP_DP.dp.toPx() + layout.size.height + NONE_LEFT_ROW_GAP_DP.dp.toPx()
            drawText(layout, topLeft = Offset(x, y))
        }
    }

    private fun drawOut(scope: DrawScope, ctx: ChartDrawContext, nowPt: Offset, state: KitPaceBendState.Out) =
        with(scope) {
            val geom = ctx.geom
            drawHatched(this, ctx, nowPt.x, geom.xReset)
            if (state.noneLeftLabel.isNotBlank()) {
                val layout = ctx.measurer.measure(state.noneLeftLabel, ctx.styles.secondary)
                if (abs(geom.xReset - nowPt.x) >= layout.size.width * MIN_OUT_SPAN_RATIO) {
                    val center = (nowPt.x + geom.xReset) / 2f
                    val maxX = (geom.x1 - layout.size.width).coerceAtLeast(geom.x0)
                    val x = (center - layout.size.width / 2f).coerceIn(geom.x0, maxX)
                    val y =
                        (geom.base - HATCH_HALF_DP.dp.toPx() - layout.size.height - OUT_LABEL_PAD_DP.dp.toPx())
                            .coerceAtLeast(geom.top)
                    drawText(layout, topLeft = Offset(x, y))
                }
            }
        }

    private fun drawDotted(scope: DrawScope, start: Offset, end: Offset, color: Color) = with(scope) {
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = FORECAST_STROKE_DP.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DOT_ON_PX, DOT_GAP_DP.dp.toPx())),
        )
    }

    private fun drawLanding(scope: DrawScope, center: Offset, fill: Color, stroke: Color) = with(scope) {
        val r = LANDING_RADIUS_DP.dp.toPx()
        drawCircle(fill, r, center)
        drawCircle(stroke, r, center, style = Stroke(LANDING_STROKE_DP.dp.toPx()))
    }

    private fun drawHatched(scope: DrawScope, ctx: ChartDrawContext, edgeA: Float, edgeB: Float) = with(scope) {
        val leftX = minOf(edgeA, edgeB)
        val rightX = maxOf(edgeA, edgeB)
        if (rightX - leftX >= 1f) {
            val halfH = HATCH_HALF_DP.dp.toPx()
            val stripTop = ctx.geom.base - halfH
            val stripBottom = ctx.geom.base + halfH
            val step = HATCH_STEP_DP.dp.toPx()
            val stroke = HATCH_STROKE_DP.dp.toPx()
            clipRect(leftX, stripTop, rightX, stripBottom) {
                drawRect(ctx.colors.attentionSurface, Offset(leftX, stripTop), Size(rightX - leftX, halfH * 2f))
                var x = leftX - halfH * 2f
                while (x < rightX + halfH * 2f) {
                    drawLine(ctx.colors.attention, Offset(x, stripBottom), Offset(x + halfH * 2f, stripTop), stroke)
                    x += step
                }
            }
        }
    }
}

/** Candidate label for collision-free axis layout. */
public data class PaceLabelCandidate<T>(
    val anchorX: Float,
    val width: Float,
    val priority: Boolean,
    val payload: T,
)

/** A label that fit on the axis without touching any previously placed label. */
public data class PlacedPaceLabel<T>(
    val left: Float,
    val right: Float,
    val payload: T,
)

/** Pure layout and coordinate rules behind [KitPaceBendChart], testable without Compose. */
public object PaceBendRules {
    /** Maps window fraction `0..1` to horizontal coordinate, mirrored in RTL. */
    public fun xForFraction(fraction: Float, x0: Float, x1: Float, rtl: Boolean): Float {
        val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return if (rtl) x1 - f * (x1 - x0) else x0 + f * (x1 - x0)
    }

    /** Maps remaining share `0..1` to vertical coordinate (`1` at top, `0` at base). */
    public fun yForLeft(leftShare: Float, top: Float, plotHeight: Float): Float {
        val l = if (leftShare.isFinite()) leftShare.coerceIn(0f, 1f) else 0f
        return top + (1f - l) * plotHeight
    }

    /**
     * Places axis labels in priority order, clamping every label inside `[x0, x1]` and dropping any
     * candidate that would come within [minGap] of an already-placed label.
     */
    public fun <T> placeLabels(
        candidates: List<PaceLabelCandidate<T>>,
        x0: Float,
        x1: Float,
        minGap: Float,
    ): List<PlacedPaceLabel<T>> {
        val placed = mutableListOf<PlacedPaceLabel<T>>()
        val ordered = candidates.sortedByDescending { it.priority }
        for (c in ordered) {
            val maxLeft = (x1 - c.width).coerceAtLeast(x0)
            val left = (c.anchorX - c.width / 2f).coerceIn(x0, maxLeft)
            val right = left + c.width
            val collides = placed.any { left < it.right + minGap && it.left < right + minGap }
            if (!collides) {
                placed += PlacedPaceLabel(left, right, c.payload)
            }
        }
        return placed
    }
}

private const val MAX_FRAME_SCALE = 1.3f
private const val MIN_LABEL_DP = 16f
private const val TOP_MARGIN_DP = 8f
private const val SHORT_PLOT_DP = 56f
private const val FULL_PLOT_DP = 104f
private const val EXTRA_ROW_GAP_DP = 6f
private const val BASE_LABEL_GAP_DP = 10f
private const val BOTTOM_PAD_DP = 4f
private const val EDGE_PAD_DP = 5f
private const val AXIS_STROKE_DP = 1f
private const val RESET_OVERHANG_DP = 4f
private const val RESET_LABEL_GAP_DP = 5f
private const val TICK_LENGTH_DP = 4f
private const val LABEL_GAP_DP = 6f
private const val AXIS_LABEL_GAP_DP = 6f
private const val DASH_LEN_DP = 4f
private const val EVEN_STROKE_DP = 1.5f
private const val EVEN_LABEL_BELOW_F = 0.50f
private const val EVEN_LABEL_ABOVE_F = 0.28f
private const val EVEN_LABEL_PAD_DP = 8f
private const val FORECAST_STROKE_DP = 2.5f
private const val DOT_ON_PX = 0.1f
private const val DOT_GAP_DP = 6f
private const val LANDING_RADIUS_DP = 4.5f
private const val LANDING_STROKE_DP = 2f
private const val LANDING_LABEL_PAD_DP = 8f
private const val HATCH_HALF_DP = 3f
private const val HATCH_STEP_DP = 5f
private const val HATCH_STROKE_DP = 2f
private const val NONE_LEFT_ROW_GAP_DP = 4f
private const val OUT_LABEL_PAD_DP = 6f
private const val MIN_OUT_SPAN_RATIO = 0.9f
private const val NOW_RING_DP = 6.5f
private const val NOW_DOT_DP = 4.5f
private const val DOT_SIZE_DP = 8f
private const val DOT_TOP_OFFSET_DP = 7f
