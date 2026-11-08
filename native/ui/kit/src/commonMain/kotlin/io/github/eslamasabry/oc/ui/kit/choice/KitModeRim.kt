package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.motion.ThemeRimKind
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// The rim light of KitModeSegmented's chosen block (theme-switch-premium §1, §3): a PathMeasure stroke along the
// block's outline, no shader, so it draws the same on every Android version and on desktop.

/** The block's outline, measured once per size, and the scratch path a rim frame draws into. */
private class RimGeometry(val path: Path, val stroke: Float, height: Float) {
    private val measure = PathMeasure().apply { setPath(path, false) }
    val length: Float = measure.length
    private val piece = Path()

    /** Where a run starts: the middle of the edge facing each way of travel (leftward, rightward). */
    val starts: FloatArray =
        FloatArray(2) { side ->
            val dir = if (side == 0) -1f else 1f
            (0 until ModeLook.RIM_START_SAMPLES).map { length * it / ModeLook.RIM_START_SAMPLES }
                .maxByOrNull { d -> measure.getPosition(d).let { p -> p.x * dir - abs(p.y - height / 2f) } } ?: 0f
        }

    /** The outline from [a] to [b] (distances along it, wrapping round). */
    fun piece(a: Float, b: Float): Path {
        piece.reset()
        if (b - a >= length) {
            piece.addPath(path)
            return piece
        }
        val s = ((a % length) + length) % length
        val e = s + (b - a)
        if (e <= length) {
            measure.getSegment(s, e, piece, true)
        } else {
            measure.getSegment(s, length, piece, true)
            measure.getSegment(0f, e - length, piece, true)
        }
        return piece
    }
}

/** Where a rim frame is: the head's distance round the edge, the glow's fade in and the settle after the run. */
private class RimFrame(ms: Float, val geometry: RimGeometry, travel: Float) {
    val head: Float
    val settle: Float
    val fadeIn: Float = (ms / ModeLook.RIM_FADE_IN_MS).coerceIn(0f, 1f)
    private val dir = if (travel >= 0f) 1f else -1f
    private val start = geometry.starts[if (dir > 0f) 1 else 0]

    init {
        val t = (ms / ThemeSwitchTimings.RIM_TRAVEL_MS).coerceIn(0f, 1f)
        head = (1f - (1f - t) * (1f - t)) * geometry.length // ease out
        val settleMs = (ThemeSwitchTimings.RIM_MS - ThemeSwitchTimings.RIM_TRAVEL_MS).toFloat()
        settle = ((ms - ThemeSwitchTimings.RIM_TRAVEL_MS) / settleMs).coerceIn(0f, 1f)
    }

    /** The edge between [a] and [b] behind the start, going round in the travel's turning sense. */
    fun span(a: Float, b: Float): Path =
        if (dir > 0f) geometry.piece(start + a, start + b) else geometry.piece(start - b, start - a)
}

/** The rim light (theme-switch-premium §1, §3): a PathMeasure stroke along the block's outline, drawn per style. */
internal fun Modifier.modeRim(
    motion: ModeSwitchMotion,
    kind: ThemeRimKind,
    ink: Color,
    shape: Shape,
    density: Density,
): Modifier =
    drawWithCache {
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, density)) }
        val geometry = RimGeometry(outline, ModeLook.RIM_STROKE_DP.dp.toPx(), size.height)
        onDrawWithContent {
            drawContent()
            val ms = motion.rim.value
            if (!motion.plan.rim || ms >= ThemeSwitchTimings.RIM_MS || geometry.length <= 0f) return@onDrawWithContent
            val frame = RimFrame(ms, geometry, motion.travel)
            when (kind) {
                ThemeRimKind.Glow -> drawGlow(frame, ink, inner = false)
                ThemeRimKind.InnerGlow -> clipPath(outline) { drawGlow(frame, ink, inner = true) }
                ThemeRimKind.HardSweep -> drawSweep(frame, ink, ModeLook.HARD_SWEEP_WIDTH, StrokeCap.Butt)
                ThemeRimKind.InkSweep -> drawSweep(frame, ink, ModeLook.INK_SWEEP_WIDTH, StrokeCap.Round)
            }
        }
    }

/** A head of light with a fading tail and a halo (or, [inner], a soft glow inside the edge), then a settling glow. */
private fun DrawScope.drawGlow(frame: RimFrame, ink: Color, inner: Boolean) {
    val stroke = frame.geometry.stroke
    val tail = frame.geometry.length * ModeLook.RIM_TAIL
    val pieces = ModeLook.RIM_TAIL_PIECES
    val glow = frame.fadeIn * (1f - frame.settle)
    fun pass(p: Path, a: Float, width: Float) =
        drawPath(p, ink.copy(alpha = ink.alpha * a), style = Stroke(stroke * width, cap = StrokeCap.Round))
    for (k in 0 until pieces) {
        val near = frame.head - tail * k / pieces
        if (near <= 0f) continue
        val p = frame.span(maxOf(0f, frame.head - tail * (k + 1) / pieces), near)
        val fall = 1f - k / pieces.toFloat()
        val a = glow * fall * fall
        if (inner) {
            pass(p, a * ModeLook.INNER_ALPHA, ModeLook.INNER_WIDTH)
            pass(p, a * ModeLook.INNER_MID_ALPHA, ModeLook.INNER_MID_WIDTH)
        } else {
            pass(p, a * ModeLook.HALO_ALPHA, ModeLook.HALO_WIDTH)
            pass(p, a * ModeLook.GLOW_ALPHA, ModeLook.GLOW_WIDTH)
            pass(p, a, 1f)
        }
    }
    // The settle: the whole edge glows softly and fades.
    val rest = frame.fadeIn * sin(PI.toFloat() * frame.settle) * ModeLook.SETTLE_ALPHA
    if (rest > 0f) {
        val width = stroke * if (inner) ModeLook.SETTLE_INNER_WIDTH else ModeLook.SETTLE_GLOW_WIDTH
        drawPath(frame.geometry.path, ink.copy(alpha = ink.alpha * rest), style = Stroke(width))
    }
}

/** No glow: the outline is traced from the start to the head, held whole, then lifts off. */
private fun DrawScope.drawSweep(frame: RimFrame, ink: Color, width: Float, cap: StrokeCap) {
    val alpha = 1f - frame.settle
    if (alpha <= 0f || frame.head <= 0f) return
    val line = Stroke(frame.geometry.stroke * width, cap = cap)
    drawPath(frame.span(0f, frame.head), ink.copy(alpha = ink.alpha * alpha), style = line)
}
