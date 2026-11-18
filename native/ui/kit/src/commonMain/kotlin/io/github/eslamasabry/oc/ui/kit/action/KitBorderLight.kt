// The light's look is a table of art numbers (stroke widths, blurs, alphas, HCT tones and hue steps, gradient stops);
// naming each one would hide the table.
@file:Suppress("MagicNumber")

package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleGlow
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.lap
import io.github.eslamasabry.oc.ui.kit.theme.KitHct
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitSignals
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min

/**
 * A light that runs round a rounded kit button's own [shape] (owner 2026-11-17, after a web "AI glow button"; backlog
 * B-452): a comet of the colour pack's accent and two neighbours (one cooler, one warmer, never red) travels the
 * outline clockwise, one turn in [BORDER_LIGHT_TURN_MS], over a faint lit ring, with one small glint ahead of it. Up
 * to three layers from the style's own glow tokens: a wide soft halo under the button that breathes twice a turn, a
 * near glow and a sharp core line over its edge.
 *
 * One drawing modifier: install it after the button's size and before its face, edge and press clip. It adds no layout
 * node, hit area or semantics, so the button keeps its size, place and target; the light is drawn past the bounds, up
 * to about 24 dp at its faintest. The turn's place and the strength are read only while drawing: nothing recomposes
 * per frame.
 *
 * [on] is the host's reason for the light (the home's "+": a New conversation tap kept while its server opens). The
 * light fades in and out on the effects spring (instant under Motion off). Off and faded out, the modifier returns
 * before creating anything: no drawing, layer, clock or effect.
 *
 * Levels: Full travels; Calm (with the system's battery saver and a hot phone) and Off draw one still, the head at the
 * upper right with its tail across the top. Saver (the app's, or the system's power saving) paints the rim only, still,
 * with no blur. Light mode is not a dimmed dark mode: deep, saturated tones that read on a pale page, a stronger halo,
 * the white-hot head kept only where it lies on a [filled] face (false for Glass's clear one).
 *
 * Styles: Solid, Soft and Signal draw core, near glow and halo; Glass a thinner core with a wider near glow caught
 * inside the clear face; Brutal and Paper (no halo in their [StyleGlow]) one line, Paper's in muted ink.
 *
 * States: off, fading, on (travelling or still)
 * Text: none
 * Cap: the host button's own size; the light is drawn, never laid out
 * Motion: AmbientLoop (the turn, `kitBorderLight`) + Fade (its strength, `kitBorderLight.fade`); Calm and Off = the still
 * Replaces: none
 */
@Composable
public fun Modifier.kitBorderLight(shape: Shape, on: Boolean, filled: Boolean = true): Modifier {
    val target = if (on) 1f else 0f
    // The first composition shows the final state (a light already on when the screen opens is lit at once).
    val strength = remember { Animatable(target) }
    val lit by remember(strength) { derivedStateOf { strength.value > 0f } }
    if (!on && !lit) return this
    val fade = KitMotion.specs.effectsSlow<Float>()
    LaunchedEffect(target) { if (strength.value != target) strength.animateTo(target, fade) }

    val saver = LocalKitAppearance.current.saver || LocalKitSignals.current.powerSave
    val colors = LocalKitColors.current
    val style = LocalKitStyle.current
    val look = remember(style.style, style.spec.tokens.glow, saver) { borderLightLook(style.style, style.spec.tokens.glow, saver) }
    val palette = remember(colors.accent, colors.dark, look.muted, filled) { borderLightPalette(colors.accent, colors.dark, look.muted, filled) }
    val phase = borderLightPhase(saver)
    val travels = !saver && KitMotion.state.ambientLoops
    val haloLayer = rememberBlurLayer(look.halo)
    val nearLayer = rememberBlurLayer(look.near)
    val dark = colors.dark
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val ring = BorderRing(outline, size)
        val core = RingLayer(outline, size, look.core, palette.coreStops, blurred = false) { it.dp.toPx() }
        val near = look.near?.let { RingLayer(outline, size, it, palette.glowStops, blurred = nearLayer != null) { v -> v.dp.toPx() } }
        val halo = look.halo?.let { RingLayer(outline, size, it, palette.glowStops, blurred = haloLayer != null) { v -> v.dp.toPx() } }
        onDrawWithContent {
            val s = strength.value
            if (s <= 0f || ring.length <= 0f) {
                drawContent()
                return@onDrawWithContent
            }
            val u = phase()
            val angle = ring.headAngle(u)
            val breath = if (travels) haloBreath(u) else 1f
            halo?.let { drawRing(it, ring.center, angle, glowAlpha(s * breath, it.glow.alpha, if (dark) 1f else LIGHT_HALO_GAIN), haloLayer) }
            drawContent()
            near?.let { drawRing(it, ring.center, angle, glowAlpha(s, it.glow.alpha, if (dark) 1f else LIGHT_NEAR_GAIN), nearLayer) }
            drawRing(core, ring.center, angle, s * core.glow.alpha, null)
        }
    }
}

/** One turn of the light round the border, ms: unhurried (on the 56 dp "+" its head moves about 30 dp a second). */
internal const val BORDER_LIGHT_TURN_MS: Int = 6000

/** Where the still and every new turn put the head: the outline's upper right ([BorderRing] starts there). */
internal const val BORDER_LIGHT_STILL_PHASE: Float = 0f

/**
 * How much stronger the glows are drawn on a light page, where a soft glow vanishes. The halo, on the page beside the
 * button, takes most of it; the near glow, over the deep face's edge, little (judge 2026-11-18: with both boosted the
 * near glow saturated at the head and read as a blotch).
 */
private const val LIGHT_HALO_GAIN: Float = 1.35f
private const val LIGHT_NEAR_GAIN: Float = 1.1f

/** No glow layer is drawn opaque: a saturated, flat-topped glow reads as a smudge, not light. */
private const val MAX_GLOW_ALPHA: Float = 0.9f

private fun glowAlpha(strength: Float, alpha: Float, gain: Float): Float = (strength * alpha * gain).coerceAtMost(MAX_GLOW_ALPHA)

/** Halo strength over a turn: 0.82..1, two breaths per turn; 1 at the turn's start and end, so it closes. */
internal fun haloBreath(phase: Float): Float = 0.91f + 0.09f * cos(4f * PI.toFloat() * phase)

/**
 * The turn's place, 0 until 1, read only in the draw phase. A loop runs only at Full, never under Saver: every other
 * level holds the still. The loop is the kit's linear lap (debug time scale included) and stops with the window.
 */
@Composable
private fun borderLightPhase(saver: Boolean): () -> Float {
    if (saver || !KitMotion.state.ambientLoops) return { BORDER_LIGHT_STILL_PHASE }
    val turn = rememberInfiniteTransition(label = "border light")
    val phase = turn.animateFloat(0f, 1f, KitMotion.specs.lap(BORDER_LIGHT_TURN_MS), label = "distance round the button")
    return { phase.value }
}

/**
 * A graphics layer that blurs [glow] where the platform blurs layers (Android 12+, desktop); null where it does not
 * (the layer then draws its stepped stand-in, [ringStrokes]) and for a layer without blur. Created only while lit.
 */
@Composable
private fun rememberBlurLayer(glow: BorderGlow?): GraphicsLayer? {
    if (glow == null || glow.blurDp <= 0f) return null
    val density = LocalDensity.current
    val effect = remember(density, glow.blurDp) { with(density) { glow.blurDp.dp.toPx() }.let { BlurEffect(it, it, TileMode.Decal) } }
    if (!effect.isSupported()) return null
    val layer = rememberGraphicsLayer()
    return remember(layer, effect) { layer.apply { renderEffect = effect } }
}

// ---- look -------------------------------------------------------------------------------------------------------

/** One layer of the light: a stroke of [widthDp] on a centre line [outDp] outside the edge, blurred by [blurDp]. */
@Immutable
internal data class BorderGlow(val widthDp: Float, val blurDp: Float, val alpha: Float, val outDp: Float = 0f) {
    /** Room the layer keeps past the button on every side, so neither stroke nor blur is cut. */
    val roomDp: Float get() = ceil(outDp + widthDp / 2f + blurDp * 2.5f + 1f)
}

/** The light's layers for one style: [core] always, [near] and [halo] where the style glows. */
@Immutable
internal data class BorderLightLook(val core: BorderGlow, val near: BorderGlow?, val halo: BorderGlow?, val muted: Boolean)

/** Per style, from its own glow tokens. Saver paints the rim only: the core line, no glow, no blur. */
internal fun borderLightLook(style: Style, glow: StyleGlow, saver: Boolean): BorderLightLook {
    val look =
        when {
            !glow.halo -> BorderLightLook(BorderGlow((glow.lineWidthDp ?: 2f) + 0.5f, 0f, 1f), null, null, muted = style == Style.Paper)
            style == Style.Glass ->
                BorderLightLook(
                    core = BorderGlow(1.4f, 0f, 1f),
                    near = BorderGlow(6f, 4f, 0.75f, outDp = -1f),
                    halo = BorderGlow(8f, 9f, 0.5f, outDp = 2f),
                    muted = false,
                )
            else ->
                BorderLightLook(
                    core = BorderGlow(2f, 0f, 1f),
                    near = BorderGlow(4f, 3f, 0.8f, outDp = 0.5f),
                    halo = BorderGlow(8f, 9f, 0.6f, outDp = 2.5f),
                    muted = false,
                )
        }
    return if (saver) look.copy(near = null, halo = null) else look
}

// ---- colours ----------------------------------------------------------------------------------------------------

/**
 * The light's colours, from the pack's accent in HCT: [lead] is the accent's own hue, [cool] and [warm] its neighbours
 * either side, [head] the white-hot tip, [base] the faint ring. Dark: high, luminous tones. Light: deep, saturated tones
 * (a pale glow vanishes on a pale page), the tip white only on a filled button.
 */
@Immutable
internal data class BorderLightPalette(val head: Color, val lead: Color, val cool: Color, val warm: Color, val base: Color, val baseAlpha: Float, val dark: Boolean) {
    /** The core line: the tip, a crisp front, the faint ring, one glint ahead, then the tail warm → cool → lead. */
    val coreStops: Array<Pair<Float, Color>> = stops(head, baseAlpha, glint = 0.45f, warmA = 0.7f, coolA = 0.95f, leadA = 1f, front = 0.02f)

    /**
     * The glows: the same comet, the ring nearly dark so only the comet glows. On a light page the tip is the lead
     * colour, a little under full, and it leaves the head over a soft shoulder instead of a 2 % cliff, so the blurred
     * glow spreads along the tail instead of piling up into a blotch at the head (judge 2026-11-18).
     */
    val glowStops: Array<Pair<Float, Color>> =
        if (dark) stops(head, 0.05f, glint = 0.3f, warmA = 0.5f, coolA = 0.85f, leadA = 1f, front = 0.02f)
        else stops(lead.copy(alpha = 0.8f), 0.05f, glint = 0.3f, warmA = 0.5f, coolA = 0.75f, leadA = 0.82f, front = 0.07f)

    private fun stops(tip: Color, ring: Float, glint: Float, warmA: Float, coolA: Float, leadA: Float, front: Float): Array<Pair<Float, Color>> {
        val faint = base.copy(alpha = ring)
        // 0 and 1 are the head (the gradient closes on it). Travel is clockwise: just past 0 lies ahead of the head,
        // just before 1 behind it, where the tail runs.
        return arrayOf(
            0f to tip,
            front to faint,
            0.24f to faint,
            0.31f to cool.copy(alpha = glint),
            0.38f to faint,
            0.52f to faint,
            0.68f to warm.copy(alpha = warmA),
            0.85f to cool.copy(alpha = coolA),
            0.95f to lead.copy(alpha = leadA),
            1f to tip,
        )
    }
}

internal fun borderLightPalette(accent: Color, dark: Boolean, muted: Boolean, filled: Boolean): BorderLightPalette {
    val a = KitHct.of(accent)
    val hue = calmHue(a.hue)
    val chroma = a.chroma * if (muted) MUTED_CHROMA else 1.0
    val cool = neighbourHue(hue, COOL_STEP)
    val warm = neighbourHue(hue, -WARM_STEP)
    fun tone(h: Double, chromaScale: Double, t: Double) = KitHct(h, chroma * chromaScale, t).toColor()
    return if (dark)
        BorderLightPalette(
            head = KitHct(hue, min(chroma, 16.0), 97.0).toColor(),
            lead = tone(hue, 1.1, 86.0),
            cool = tone(cool, 1.1, 80.0),
            warm = tone(warm, 1.0, 90.0),
            base = tone(hue, 0.9, 72.0),
            baseAlpha = 0.22f,
            dark = true,
        )
    else {
        val lead = tone(hue, 1.3, 58.0)
        BorderLightPalette(
            head = if (filled) KitHct(hue, min(chroma, 8.0), 99.0).toColor() else lead,
            lead = lead,
            cool = tone(cool, 1.3, 50.0),
            warm = tone(warm, 1.3, 64.0),
            base = tone(hue, 1.0, 52.0),
            baseAlpha = 0.3f,
            dark = false,
        )
    }
}

/** The neighbours' distance from the accent's hue, degrees (HCT): close enough to read as one family. */
private const val COOL_STEP: Double = 38.0
private const val WARM_STEP: Double = 32.0

/** Paper's light is ink on paper: a little over half the colour. */
private const val MUTED_CHROMA: Double = 0.55

/** HCT hues that read as red (crimson to vermilion). The light is an invitation, never an alert: it never takes them. */
private const val RED_FROM: Double = 355.0
private const val RED_MID: Double = 22.0
private const val RED_TO: Double = 50.0

private fun wrapHue(h: Double): Double = ((h % 360.0) + 360.0) % 360.0

private fun isRed(h: Double): Boolean = wrapHue(h).let { it >= RED_FROM || it < RED_TO }

/** [h] moved out of the red band to its nearer edge (a rust accent lights in amber-orange, a crimson one in rose). */
internal fun calmHue(h: Double): Double {
    val x = wrapHue(h)
    return when {
        !isRed(x) -> x
        x >= RED_FROM || x < RED_MID -> RED_FROM - 5.0
        else -> RED_TO
    }
}

/** The neighbour [step] degrees from [hue]; where that is red, the same step the other way (both on the calm side). */
private fun neighbourHue(hue: Double, step: Double): Double {
    val there = wrapHue(hue + step)
    if (!isRed(there)) return there
    val other = wrapHue(hue - step)
    return if (!isRed(other)) other else calmHue(there)
}

// ---- geometry ---------------------------------------------------------------------------------------------------

/**
 * The button's real outline, measured by LENGTH (so the light keeps its pace round the corners, and is right on
 * Signal's rounded rectangle as on the disc). u = 0 is the outline's upper right, wherever the platform starts its
 * rounded outline, and u grows clockwise; u = 1 is u = 0, so a turn closes without a jump.
 */
internal class BorderRing(outline: Outline, size: Size) {
    val center: Offset = Offset(size.width / 2f, size.height / 2f)
    private val measure = PathMeasure().apply { setPath(outlinePath(outline, size, 0f), true) }
    val length: Float = measure.length

    // The upper-right quarter: the sampled point furthest right and up.
    private val start: Float =
        if (length <= 0f) 0f
        else (0 until START_SAMPLES).maxBy { step ->
            val p = measure.getPosition(length * step / START_SAMPLES)
            p.x - START_RISE * p.y
        } * length / START_SAMPLES

    /**
     * The canvas turn, degrees, that puts the head of a sweep gradient (its stop 0, on the positive x axis) on the
     * outline at [u]: the ray from the centre at that angle meets the outline exactly there.
     */
    fun headAngle(u: Float): Float {
        val wrapped = u - floor(u)
        val p = measure.getPosition((start + wrapped * length) % length) - center
        return (atan2(p.y, p.x) * DEGREES).toFloat()
    }

    private companion object {
        const val START_SAMPLES = 48
        const val START_RISE = 0.42f
        const val DEGREES = 180.0 / PI
    }
}

/**
 * [outline] grown by [outPx] on every side (shrunk when negative), clockwise. A rounded or plain rectangle grows
 * exactly (its corners by the same amount); any other outline is scaled about its centre.
 */
private fun outlinePath(outline: Outline, size: Size, outPx: Float): Path =
    Path().apply {
        when (outline) {
            is Outline.Rounded -> addRoundRect(outline.roundRect.grow(outPx), Path.Direction.Clockwise)
            is Outline.Rectangle -> addRect(outline.rect.inflate(outPx), Path.Direction.Clockwise)
            is Outline.Generic -> {
                addPath(outline.path)
                if (outPx != 0f && size.width > 0f && size.height > 0f) {
                    translate(Offset(-size.width / 2f, -size.height / 2f))
                    transform(Matrix().apply { scale((size.width + 2f * outPx) / size.width, (size.height + 2f * outPx) / size.height) })
                    translate(Offset(size.width / 2f, size.height / 2f))
                }
            }
        }
    }

private fun RoundRect.grow(by: Float): RoundRect {
    fun CornerRadius.grow() = CornerRadius((x + by).coerceAtLeast(0f), (y + by).coerceAtLeast(0f))
    return RoundRect(
        left - by, top - by, right + by, bottom + by,
        topLeftCornerRadius.grow(), topRightCornerRadius.grow(), bottomRightCornerRadius.grow(), bottomLeftCornerRadius.grow(),
    )
}

/**
 * One layer's drawing, cached per size: its outline centred on the origin, a scratch path and matrix, the brush and
 * its strokes. Per frame it only turns the scratch path: no allocation.
 */
private class RingLayer(outline: Outline, size: Size, val glow: BorderGlow, stops: Array<Pair<Float, Color>>, blurred: Boolean, px: (Float) -> Float) {
    private val centred = outlinePath(outline, size, px(glow.outDp)).apply { translate(Offset(-size.width / 2f, -size.height / 2f)) }
    val turned = Path()
    private val matrix = Matrix()
    val brush: Brush = Brush.sweepGradient(*stops, center = Offset.Zero)
    val strokes: List<Pair<Stroke, Float>> = ringStrokes(px(glow.widthDp), if (glow.blurDp > 0f && !blurred) px(glow.blurDp) else 0f)
    val roomPx: Float = px(glow.roomDp)

    /** The outline turned back by [degrees]: drawn under a canvas turned by [degrees], it stays exactly in place. */
    fun turnBack(degrees: Float) {
        matrix.reset()
        // Matrix.rotateZ is the same clockwise turn as DrawScope.rotate (x' = c·x − s·y, y' = s·x + c·y).
        matrix.rotateZ(-degrees)
        turned.rewind()
        turned.addPath(centred)
        turned.transform(matrix)
    }
}

/**
 * The strokes of one layer: one of [width], or, where the platform cannot blur, a stepped glow of [blur]: five strokes
 * from the widest and faintest to the line's own width, their overlap brightest at the centre.
 */
private fun ringStrokes(width: Float, blur: Float): List<Pair<Stroke, Float>> =
    if (blur <= 0f) listOf(Stroke(width) to 1f)
    else (0 until STEPS).map { i ->
        val reach = 1f - i.toFloat() / STEPS
        Stroke(width + 2f * blur * reach) to STEP_ALPHA
    }

private const val STEPS = 5
private const val STEP_ALPHA = 0.24f

/**
 * Draws [layer] with its brush's head turned to [angle] round [center]. The brush turns with the canvas; the path is
 * turned back first, so the outline itself never moves. A blurred layer is recorded into [blur] with room on every side
 * and drawn back in place.
 */
private fun DrawScope.drawRing(layer: RingLayer, center: Offset, angle: Float, alpha: Float, blur: GraphicsLayer?) {
    if (alpha <= 0f) return
    layer.turnBack(angle)
    val stroke: DrawScope.() -> Unit = {
        rotate(angle, pivot = Offset.Zero) {
            layer.strokes.forEach { (s, a) -> drawPath(layer.turned, layer.brush, alpha = (alpha * a).coerceAtMost(1f), style = s) }
        }
    }
    if (blur == null) {
        translate(center.x, center.y, stroke)
        return
    }
    val pad = ceil(layer.roomPx)
    kitRecord(blur, IntSize(ceil(size.width + 2f * pad).toInt(), ceil(size.height + 2f * pad).toInt())) {
        translate(pad + center.x, pad + center.y, stroke)
    }
    translate(-pad, -pad) { kitDrawLayer(blur) }
}
