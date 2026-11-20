package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import io.github.eslamasabry.oc.ui.design.style.Style
import kotlin.math.floor

/** The family's own drawing box: every mark's pen weight is set in units of a box this many units wide. */
internal const val WAIT_BOX: Float = 20f

private const val ROUND_STROKE = 1.6f
private const val HARD_STROKE = 1.9f
private const val FINE_STROKE = 1.35f

/** Brutal's loop is a flip-book: this many drawings, each held, none in between. */
private const val HARD_STEPS = 12

/** Keeps a phase that is a whole step (7/12 as a float) on its own step. */
private const val STEP_SLACK = 0.001f

/** The handle length, as a share of the radius, that makes a cubic read as a quarter circle. */
private const val ROUNDNESS = 0.5523f

/**
 * How a style holds the pen. Solid, Glass, Soft and Signal draw with a round nib and eased motion. Brutal cuts every
 * end square, draws heavier (its ink edge) and moves in [steps] held drawings with no overshoot. Paper cuts square
 * too, with a finer rule, and keeps the eased motion.
 */
internal data class WaitHand(val stroke: Float, val square: Boolean, val steps: Int)

internal fun waitHand(style: Style): WaitHand =
    when (style) {
        Style.Brutal -> WaitHand(HARD_STROKE, square = true, steps = HARD_STEPS)
        Style.Paper -> WaitHand(FINE_STROKE, square = true, steps = 0)
        Style.Solid, Style.Glass, Style.Soft, Style.Signal -> WaitHand(ROUND_STROKE, square = false, steps = 0)
    }

/**
 * The colours a mark may use, each with one meaning across the family (see `KitWaitMode`):
 * [ink] the thing itself, [warm] heat and light, [live] what the wait is making or moving, [call] the person's turn.
 * In Ink mode [warm] and [live] are the ink; [call] is the amber "needs you" in both modes. [callEdge], when there is
 * one, is a fine edge of the deeper amber round the dot, so the bright amber holds on a light page.
 */
internal data class WaitColours(
    val ink: Color,
    val warm: Color,
    val live: Color,
    val call: Color,
    val callEdge: Color? = null,
)

/**
 * The pen the marks are drawn with, in units of a drawing grid (units across the box): Fable's marks are drawn on 20
 * units, Astra's on 24. The weight of a line is the same on screen whatever the grid. Everything is drawn in [paint],
 * which a mark sets before each part (the dispatcher starts every mark in the ink). Made once per size, colours and
 * style (`drawWithCache`), so a frame allocates nothing: the one [Path] is rewound and reused, the strokes are kept.
 */
internal class WaitPen(private val boxPx: Float, val colours: WaitColours, private val hand: WaitHand) {
    /** One unit of the current grid, in pixels. */
    var u: Float = boxPx / WAIT_BOX
        private set
    private var grid: Float = WAIT_BOX
    private val cap = if (hand.square) StrokeCap.Butt else StrokeCap.Round
    private val join = if (hand.square) StrokeJoin.Miter else StrokeJoin.Round
    private val outline = Stroke(hand.stroke * boxPx / WAIT_BOX, cap = cap, join = join)

    /** The same line weight for a shape drawn in grid units ([unitShape]); made again only when the grid changes. */
    private var unitOutline = Stroke(hand.stroke, cap = cap, join = join)
    private val path = Path().apply { fillType = PathFillType.EvenOdd }

    /** The colour the next parts are drawn in. */
    var paint: Color = colours.ink

    val ink: Color get() = colours.ink

    /** The stroke's width in units of the current grid. */
    val stroke: Float get() = hand.stroke * grid / WAIT_BOX

    val square: Boolean get() = hand.square

    /** Brutal's flip-book: this many held drawings per loop, 0 for a smooth hand. */
    val steps: Int get() = hand.steps

    /** Draws what follows on a grid of [units] across the box, in the ink. */
    fun onGrid(units: Float) {
        if (units != grid) unitOutline = Stroke(hand.stroke * units / WAIT_BOX, cap = cap, join = join)
        grid = units
        u = boxPx / units
        paint = colours.ink
    }

    fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = stroke) {
        drawLine(paint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), width * u, cap)
    }

    fun DrawScope.dot(cx: Float, cy: Float, radius: Float) {
        drawCircle(paint, radius * u, Offset(cx * u, cy * u))
    }

    /** A standing column: round ends for a round hand, cut square otherwise. */
    fun DrawScope.column(left: Float, top: Float, right: Float, bottom: Float) {
        val r = if (hand.square) 0f else (right - left) / 2f * u
        val size = Size((right - left) * u, (bottom - top) * u)
        drawRoundRect(paint, Offset(left * u, top * u), size, CornerRadius(r, r))
    }

    /** Starts a new shape at ([x], [y]); the calls below carry it on, [strokeShape] or [fillShape] draws it. */
    fun begin(x: Float, y: Float) {
        path.rewind()
        path.moveTo(x * u, y * u)
    }

    fun lineTo(x: Float, y: Float) {
        path.lineTo(x * u, y * u)
    }

    /** Carries the shape on from ([left], [base]) over a round arch as high as [top] down to ([right], [base]). */
    fun arch(left: Float, top: Float, right: Float, base: Float) {
        val middle = (left + right) / 2f
        val reach = (right - left) / 2f * ROUNDNESS
        val rise = (base - top) * ROUNDNESS
        path.cubicTo(left * u, (base - rise) * u, (middle - reach) * u, top * u, middle * u, top * u)
        path.cubicTo((middle + reach) * u, top * u, right * u, (base - rise) * u, right * u, base * u)
    }

    fun DrawScope.strokeShape() {
        drawPath(path, paint, style = outline)
    }

    fun DrawScope.fillShape() {
        path.close()
        drawPath(path, paint)
    }

    /** A fixed shape drawn in grid units ([WaitShape]): filled, or stroked with the pen's line. */
    fun DrawScope.unitShape(shape: Path, fill: Boolean = true) {
        scale(u, u, Offset.Zero) {
            if (fill) drawPath(shape, paint) else drawPath(shape, paint, style = unitOutline)
        }
    }
}

/** Where the loop is: wrapped into 0..1 (so 1 is 0) and, for a stepping hand, held on its step. */
internal fun WaitPen.place(phase: Float): Float {
    val p = turn(phase)
    return if (steps > 0) floor(p * steps + STEP_SLACK) / steps else p
}

/** An arrival with weight: it passes its mark a little and settles. A square hand arrives without it. */
internal fun WaitPen.settle(x: Float): Float = if (square) easeOut(x) else overshoot(x)

/** A fixed shape from its path data, in grid units, parsed once. [cut] makes overlapping pieces cut through. */
internal fun waitShape(data: String, cut: Boolean = false): Path =
    PathParser().parsePathString(data).toPath().apply { if (cut) fillType = PathFillType.EvenOdd }
