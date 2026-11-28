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
import androidx.compose.ui.text.TextLayoutResult
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
    val plotDp =
        when {
            !data.short -> FULL_PLOT_DP
            data.state is KitPaceBendState.Lasts -> shortPlotDp(labelHeightDp)
            else -> SHORT_PLOT_DP
        }
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
        val baseCtx = ChartDrawContext(geom, colors, measurer, styles)
        val ctx = baseCtx.withGuides(PaceBendPainter.measureGuides(this, baseCtx, data, safeElapsed, safeLeft))
        PaceBendPainter.drawFrameAndAxis(this, ctx, data.axisMarks, data.labels.reset)
        PaceBendPainter.drawEvenPace(this, ctx)
        PaceBendPainter.drawForecastAndDot(this, ctx, safeElapsed, safeLeft, data.state)
    }
}

/**
 * A short chart that carries a landing label is tall enough for the label to clear the now dot above it or
 * under the even-pace diagonal: twice the room it needs on either side of the dot, and a gap to the base line.
 */
private fun shortPlotDp(labelHeightDp: Float): Float =
    maxOf(SHORT_PLOT_DP, 2f * (labelHeightDp + NOW_RING_DP + LABEL_GAP_DP) + LABEL_GAP_DP)

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
    val guides: MeasuredGuideLabels = MeasuredGuideLabels(null, null, PaceGuidePlacement(null, null)),
) {
    fun withGuides(guides: MeasuredGuideLabels): ChartDrawContext =
        ChartDrawContext(geom, colors, measurer, styles, guides)
}

private class MeasuredGuideLabels(
    val landingLayout: TextLayoutResult?,
    val evenPaceLayout: TextLayoutResult?,
    val placement: PaceGuidePlacement,
)

private object PaceBendPainter {
    fun measureGuides(
        scope: DrawScope,
        ctx: ChartDrawContext,
        data: KitPaceBendData,
        elapsed: Float,
        left: Float,
    ): MeasuredGuideLabels =
        with(scope) {
            val lasts = data.state as? KitPaceBendState.Lasts
            val landingLayout =
                lasts?.landingLabel?.takeIf { it.isNotBlank() }?.let {
                    ctx.measurer.measure(it, ctx.styles.secondary)
                }
            val evenText = data.labels.evenPace
            val showEven = !data.short && data.state !is KitPaceBendState.Out && evenText.isNotBlank()
            val evenLayout = if (showEven) ctx.measurer.measure(evenText, ctx.styles.regular) else null
            val usedAbove = data.state is KitPaceBendState.Lasts || data.state is KitPaceBendState.TooLittle
            val geom = ctx.geom
            val placement =
                PaceBendRules.placeGuideLabels(
                    PaceGuideLayoutInput(
                        x0 = geom.x0,
                        x1 = geom.x1,
                        top = geom.top,
                        plotHeight = geom.plotH,
                        rtl = geom.rtl,
                        elapsed = elapsed,
                        left = left,
                        atReset = lasts?.atReset?.coerceIn(0f, 1f),
                        usedAbove = usedAbove,
                        landingSize =
                            landingLayout?.let { PaceLabelSize(it.size.width.toFloat(), it.size.height.toFloat()) },
                        evenPaceSize =
                            evenLayout?.let { PaceLabelSize(it.size.width.toFloat(), it.size.height.toFloat()) },
                        pad = LANDING_LABEL_PAD_DP.dp.toPx(),
                        minGap = LABEL_GAP_DP.dp.toPx(),
                        nowDotRadius = NOW_RING_DP.dp.toPx(),
                    ),
                )
            MeasuredGuideLabels(landingLayout, evenLayout, placement)
        }
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

    fun drawEvenPace(scope: DrawScope, ctx: ChartDrawContext) = with(scope) {
        val geom = ctx.geom
        val dash = DASH_LEN_DP.dp.toPx()
        drawLine(
            color = ctx.colors.text3,
            start = Offset(geom.xStart, geom.yOf(1f)),
            end = Offset(geom.xReset, geom.yOf(0f)),
            strokeWidth = EVEN_STROKE_DP.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        )
        val layout = ctx.guides.evenPaceLayout
        val rect = ctx.guides.placement.evenPace
        if (layout != null && rect != null) {
            drawText(layout, topLeft = Offset(rect.left, rect.top))
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
        val layout = ctx.guides.landingLayout
        val rect = ctx.guides.placement.landing
        if (layout != null && rect != null) {
            drawText(layout, topLeft = Offset(rect.left, rect.top))
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

/** Axis-aligned rectangle inside [KitPaceBendChart]'s plot area. */
public data class PaceLabelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Horizontal center of this label box. */
    public val centerX: Float get() = (left + right) / 2f

    /** True when this box overlaps [other] (within [gap] on both axes). */
    public fun overlaps(other: PaceLabelRect, gap: Float = 0f): Boolean {
        val horiz = left < other.right + gap && other.left < right + gap
        val vert = top < other.bottom + gap && other.top < bottom + gap
        return horiz && vert
    }
}

/** Width and height of a measured guide label. */
public data class PaceLabelSize(
    val width: Float,
    val height: Float,
)

/** Geometry and label sizes needed to place the landing and even-pace guide labels. */
public data class PaceGuideLayoutInput(
    val x0: Float,
    val x1: Float,
    val top: Float,
    val plotHeight: Float,
    val rtl: Boolean,
    val elapsed: Float,
    val left: Float,
    val atReset: Float?,
    val usedAbove: Boolean,
    val landingSize: PaceLabelSize?,
    val evenPaceSize: PaceLabelSize?,
    val pad: Float,
    val minGap: Float,
    /** Radius of the now dot's ring, in the same units as the rest; 0 when there is no dot the labels must avoid. */
    val nowDotRadius: Float = 0f,
)

/** Placed guide labels inside the plot; either slot is null when absent or dropped for space. */
public data class PaceGuidePlacement(
    val landing: PaceLabelRect?,
    val evenPace: PaceLabelRect?,
)

/** Geometry and label size needed to place the 'None left' label in the [KitPaceBendState.Out] state. */
public data class PaceNoneLeftInput(
    val x0: Float,
    val x1: Float,
    val top: Float,
    val plotHeight: Float,
    val canvasHeight: Float,
    val rtl: Boolean,
    val elapsed: Float,
    val labelSize: PaceLabelSize,
    val axisRowHeight: Float,
    val axisLabelGap: Float,
    val rowGap: Float,
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
     * Places the optional landing and even-pace guide labels inside `[top, top + plotHeight]`.
     * The landing label has priority: beside its landing point, on the side of it away from the even-pace
     * diagonal, and never over the now dot. The even-pace label is kept only when it fits without colliding.
     */
    public fun placeGuideLabels(input: PaceGuideLayoutInput): PaceGuidePlacement {
        val base = input.top + input.plotHeight
        val landing = placeLandingLabel(input, base)
        val evenPace = placeEvenPaceLabel(input, base, landing)
        return PaceGuidePlacement(landing = landing, evenPace = evenPace)
    }

    private fun placeLandingLabel(input: PaceGuideLayoutInput, base: Float): PaceLabelRect? {
        val atReset = input.atReset
        val size = input.landingSize?.takeIf { it.width > 0f && it.height > 0f }
        if (atReset == null || size == null) return null
        return LandingLabel(input, base, atReset, size).place()
    }

    private fun placeEvenPaceLabel(
        input: PaceGuideLayoutInput,
        base: Float,
        landing: PaceLabelRect?,
    ): PaceLabelRect? {
        val plotW = input.x1 - input.x0
        val size =
            input.evenPaceSize
                ?.takeIf { it.width > 0f && it.height > 0f }
                ?.takeIf { it.width <= plotW && it.height <= input.plotHeight }
                ?: return null
        val frac = if (input.usedAbove) EVEN_LABEL_BELOW_F else EVEN_LABEL_ABOVE_F
        val ex = xForFraction(frac, input.x0, input.x1, input.rtl)
        val ey = yForLeft(1f - frac, input.top, input.plotHeight)
        val maxLeft = (input.x1 - size.width).coerceAtLeast(input.x0)
        val rawLeft = if (input.usedAbove xor input.rtl) ex - size.width else ex
        val left = rawLeft.coerceIn(input.x0, maxLeft)
        val maxTop = (base - size.height).coerceAtLeast(input.top)
        val rawTop = if (input.usedAbove) ey + input.pad else ey - size.height - input.pad
        val topY = rawTop.coerceIn(input.top, maxTop)
        val rect = PaceLabelRect(left = left, top = topY, right = left + size.width, bottom = topY + size.height)
        val evenAtMid = evenPaceY(rect.centerX, input)
        val clearsLine = if (input.usedAbove) rect.top > evenAtMid else rect.bottom < evenAtMid
        val clearsLanding = landing == null || !rect.overlaps(landing, input.minGap)
        return if (clearsLine && clearsLanding) rect else null
    }

    private fun fractionForX(x: Float, input: PaceGuideLayoutInput): Float {
        val span = (input.x1 - input.x0).coerceAtLeast(1f)
        return (if (input.rtl) (input.x1 - x) / span else (x - input.x0) / span).coerceIn(0f, 1f)
    }

    private fun evenPaceY(x: Float, input: PaceGuideLayoutInput): Float =
        yForLeft(1f - fractionForX(x, input), input.top, input.plotHeight)

    /** Places the 'None left' label for an exhausted ([KitPaceBendState.Out]) window. */
    public fun placeNoneLeftLabel(input: PaceNoneLeftInput): PaceLabelRect? {
        val size = input.labelSize
        if (size.width <= 0f || size.height <= 0f) return null
        val base = input.top + input.plotHeight
        val xNow = xForFraction(input.elapsed, input.x0, input.x1, input.rtl)
        val xReset = xForFraction(1f, input.x0, input.x1, input.rtl)
        val center = (xNow + xReset) / 2f
        val maxX = (input.x1 - size.width).coerceAtLeast(input.x0)
        val left = (center - size.width / 2f).coerceIn(input.x0, maxX)
        val y = (base - HATCH_HALF_DP - size.height - OUT_LABEL_PAD_DP).coerceAtLeast(input.top)
        return PaceLabelRect(left = left, top = y, right = left + size.width, bottom = y + size.height)
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

/**
 * Where the landing label goes (B-508). It starts beside the landing point; when that box would cover the now
 * dot, the first of three moves that stays inside the plot, clear of the dot and on one side of the even-pace
 * diagonal wins, and the label keeps its place when none does. Positions are distances from the window's start
 * edge, so every move mirrors in RTL.
 */
private class LandingLabel(
    private val input: PaceGuideLayoutInput,
    private val base: Float,
    private val atReset: Float,
    private val size: PaceLabelSize,
) {
    private val span = input.x1 - input.x0
    private val startX = if (input.rtl) input.x1 else input.x0
    private val dir = if (input.rtl) -1f else 1f
    private val landY = PaceBendRules.yForLeft(atReset, input.top, input.plotHeight)
    private val gap = input.minGap + LABEL_NUDGE
    private val dot =
        input.nowDotRadius.takeIf { it > 0f }?.let { radius ->
            val cx = PaceBendRules.xForFraction(input.elapsed, input.x0, input.x1, input.rtl)
            val cy = PaceBendRules.yForLeft(input.left, input.top, input.plotHeight)
            PaceLabelRect(cx - radius, cy - radius, cx + radius, cy + radius)
        }

    /** The label's place on this chart. */
    fun place(): PaceLabelRect {
        val anchored = atLanding()
        val box = dot
        if (box == null || !anchored.overlaps(box, input.minGap)) return anchored
        val moves = listOfNotNull(towardReset(anchored, box), aboveDot(anchored, box), underLine(box))
        return moves.firstOrNull { isClear(it, box) } ?: anchored
    }

    /** Beside the landing point, on the side of it away from the diagonal, as high as the forecast line allows. */
    private fun atLanding(): PaceLabelRect {
        val xReset = PaceBendRules.xForFraction(1f, input.x0, input.x1, input.rtl)
        val maxLeft = (input.x1 - size.width).coerceAtLeast(input.x0)
        val rawLeft = if (input.rtl) xReset + input.pad else xReset - input.pad - size.width
        val left = rawLeft.coerceIn(input.x0, maxLeft)
        val right = left + size.width
        val nowY = PaceBendRules.yForLeft(input.left, input.top, input.plotHeight)
        val elapsed = input.elapsed.coerceIn(0f, 1f)
        val nearEdgeX = if (input.rtl) right else left
        val nearFrac = fraction(nearEdgeX).coerceIn(elapsed, 1f)
        val remain = 1f - elapsed
        val t = if (remain > FRAC_EPSILON) ((nearFrac - elapsed) / remain).coerceIn(0f, 1f) else 1f
        val ceilingY = minOf(landY, nowY + t * (landY - nowY), lineY((left + right) / 2f))
        val maxTop = (base - size.height).coerceAtLeast(input.top)
        val topY = (ceilingY - input.pad - size.height).coerceIn(input.top, maxTop)
        return PaceLabelRect(left = left, top = topY, right = right, bottom = minOf(topY + size.height, base))
    }

    /** Along its own row toward the landing point, past the dot. */
    private fun towardReset(anchored: PaceLabelRect, box: PaceLabelRect): PaceLabelRect? {
        val boxReset = if (input.rtl) input.x1 - box.left else box.right - input.x0
        val dFar = boxReset + gap
        val limit = if (anchored.bottom <= landY - input.pad) span else span - input.pad
        val dNear = minOf(dFar + size.width, limit)
        if (dNear - size.width < dFar) return null
        return rect(dNear - size.width, dNear, anchored.top)
    }

    /** Above the dot's row, when the plot has that much headroom. */
    private fun aboveDot(anchored: PaceLabelRect, box: PaceLabelRect): PaceLabelRect? {
        val bottom = box.top - gap
        val top = bottom - size.height
        if (top < input.top) return null
        return PaceLabelRect(anchored.left, top, anchored.right, bottom)
    }

    /** Under the even-pace diagonal, as near to the reset as the diagonal and the dot leave room for. */
    private fun underLine(box: PaceLabelRect): PaceLabelRect? {
        val maxTop = (base - size.height).coerceAtLeast(input.top)
        val dCeiling = (maxTop - gap - input.top) * span / input.plotHeight.coerceAtLeast(1f)
        val boxStart = if (input.rtl) input.x1 - box.right else box.left - input.x0
        val boxReset = if (input.rtl) input.x1 - box.left else box.right - input.x0
        val dNear = maxOf(minOf(dCeiling, boxStart - gap), size.width)
        if (dNear > span) return null
        val xNear = startX + dir * dNear
        val underDot = dNear > boxStart - gap && dNear - size.width < boxReset + gap
        val floor = if (underDot) maxOf(lineY(xNear), box.bottom) else lineY(xNear)
        val top = (floor + gap).coerceIn(input.top, maxTop)
        return rect(dNear - size.width, dNear, top)
    }

    /** Inside the plot, off the dot, and on one side of the even-pace diagonal. */
    private fun isClear(candidate: PaceLabelRect, box: PaceLabelRect): Boolean {
        val insideX = candidate.left >= input.x0 && candidate.right <= input.x1
        val insideY = candidate.top >= input.top && candidate.bottom <= base
        val lineA = lineY(candidate.left)
        val lineB = lineY(candidate.right)
        val offLine = candidate.bottom < minOf(lineA, lineB) || candidate.top > maxOf(lineA, lineB)
        val settled = insideX && insideY && offLine
        return settled && !candidate.overlaps(box, input.minGap)
    }

    private fun rect(dFar: Float, dNear: Float, top: Float): PaceLabelRect {
        val a = (startX + dir * dFar).coerceIn(input.x0, input.x1)
        val b = (startX + dir * dNear).coerceIn(input.x0, input.x1)
        return PaceLabelRect(minOf(a, b), top, maxOf(a, b), minOf(top + size.height, base))
    }

    /** The window fraction at [x]: 0 at the start edge, 1 at the reset. */
    private fun fraction(x: Float): Float =
        ((if (input.rtl) input.x1 - x else x - input.x0) / span.coerceAtLeast(1f)).coerceIn(0f, 1f)

    /** The even-pace diagonal's y at [x]. */
    private fun lineY(x: Float): Float = PaceBendRules.yForLeft(1f - fraction(x), input.top, input.plotHeight)
}

/** A hair more than a gap, so "a gap apart" never reads as touching once floats are rounded. */
private const val LABEL_NUDGE = 0.01f

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
private const val FRAC_EPSILON = 0.001f
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
/** The now dot's ring, in dp; the chart's rules keep guide labels clear of it. */
internal const val NOW_RING_DP = 6.5f
private const val NOW_DOT_DP = 4.5f
private const val DOT_SIZE_DP = 8f
private const val DOT_TOP_OFFSET_DP = 7f
