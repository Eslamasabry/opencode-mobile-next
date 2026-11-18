package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp

/*
 * The scroll edges of a page whose body runs under its chrome (owner 2026-11-09: "a rough line just below Jump to
 * latest", "the same on top, that dark gradient below the notifications bar", "an opaque slab under the composer").
 * The chrome no longer lays a band of the page's ground over the body (a fade, a veil, a slab): bands of ground ended
 * in a hard line wherever the page's ground is a live picture, and two of them met in a step. Instead the body's own
 * content fades out at its edges, so what lies behind the page (its ground, its live picture) shows through unchanged
 * and nothing draws an edge. Recordings made inside the body (the liquid panes', the veils') are not faded: glass over
 * the content still sees it.
 */

/** Points along a smooth step, so a fade has no knee at either end (a linear ramp reads as a faint line there). */
private const val EDGE_STOPS = 9

/** Smoothstep: 0 at 0, 1 at 1, flat at both ends. */
internal fun edgeEase(f: Float): Float {
    val t = f.coerceIn(0f, 1f)
    return t * t * (SMOOTH_A - SMOOTH_B * t)
}

private const val SMOOTH_A = 3f
private const val SMOOTH_B = 2f

/**
 * Evenly spaced colours for a gradient that clears content (alpha = how much is cleared) from [from] at its start to
 * [to] at its end.
 */
private fun clearing(from: Float, to: Float, strength: Float, color: Color = Color.Black): List<Color> =
    List(EDGE_STOPS) { i ->
        val f = i / (EDGE_STOPS - 1f)
        color.copy(alpha = ((from + (to - from) * edgeEase(f)) * strength).coerceIn(0f, 1f))
    }

/**
 * A fade's edge on a whole pixel. A gradient that clears content (DstOut) through a rectangle whose edge lies inside a
 * pixel is drawn anti-aliased, which needs the renderer to mix coverage into the blend: that leaves a hairline of
 * half-cleared content at the edge, and it is what made the Android emulator's renderer crash on every chat
 * (2026-11-09 upgrade proof, "bad color buffer handle"; found by bisect 2026-11-10). On whole pixels the same fade is a
 * plain blend everywhere.
 */
internal fun wholePx(y: Float): Float = kotlin.math.round(y)

/**
 * Fades this content out at its scroll edges. Top: cleared above [clearAbove] px, fading in over [topReach] below it.
 * Bottom: whole down to [fadeFromBottom] px above the bottom edge, fading out over [bottomReach] from there, cleared
 * below. A null edge is left alone; [strength] (0–1, read while drawing) eases the top edge in and out, [bottomStrength] the bottom one.
 *
 * How it clears (c2-performance, 2026-11-15): over a live picture (or any ground the frame cannot name) the content is
 * drawn in its own offscreen layer and cleared by its alpha, so the picture behind shows through unchanged. That layer is
 * the costliest thing a phone's GPU does per frame: every frame the content moves it fills a second buffer the size of
 * the page and blends it back. Over a flat [ground] (the default look: no live background) the same fade is the ground
 * itself laid over the content with the clearing's alphas, which gives the same pixels (content cleared by a over a
 * flat ground G shows G·a + content·(1−a), exactly the ground drawn at a over it) with no layer at all. The content is
 * clipped to the box as the layer clipped it.
 */
internal fun Modifier.kitScrollEdges(
    clearAbove: (() -> Float)?,
    topReach: Dp,
    fadeFromBottom: (() -> Float)?,
    bottomReach: Dp,
    strength: () -> Float = { 1f },
    /** The top fade's length in px, read while drawing; overrides [topReach] (the status edge's dissolve). */
    topReachPx: (() -> Float)? = null,
    /** The bottom fade's length in px, read while drawing; overrides [bottomReach]. */
    bottomReachPx: (() -> Float)? = null,
    /** How much of the bottom fade shows, 0-1, read while drawing (0 leaves the bottom whole). */
    bottomStrength: () -> Float = { 1f },
    /** The bottom px left alone below the fade (a pinned foot inside the faded box), read while drawing. */
    keepBottomPx: () -> Float = { 0f },
    /**
     * The opaque flat ground right behind this box, when the frame knows it ([LocalKitFlatGround]); unspecified draws the
     * clearing through an offscreen layer.
     */
    ground: Color = Color.Unspecified,
): Modifier {
    if (clearAbove == null && fadeFromBottom == null) return this
    val flat = ground.isSpecified && ground.alpha >= 1f
    val draw =
        Modifier.drawWithCache {
            // The two fades' brushes are kept between frames and built again only when their own numbers change:
            // this runs on every frame the transcript scrolls or a reply streams, and each frame built two gradients
            // and their colour lists (jank pass 2026-11-10).
            val top = EdgeBrush()
            val bottom = EdgeBrush()
            val ink = if (flat) ground else Color.Black
            val mode = if (flat) BlendMode.SrcOver else BlendMode.DstOut
            onDrawWithContent {
                if (flat) clipRect { this@onDrawWithContent.drawContent() } else drawContent()
                val w = size.width
                if (clearAbove != null) {
                    val k = strength().coerceIn(0f, 1f)
                    if (k > 0f) {
                        val edge = clearAbove().coerceIn(0f, size.height)
                        val t = wholePx(edge)
                        val r = wholePx(edge + (topReachPx?.invoke() ?: topReach.toPx())) - t
                        if (t > 0f) {
                            drawRect(ink.copy(alpha = k), Offset.Zero, Size(w, t), blendMode = mode)
                        }
                        if (r > 0f) drawRect(top.of(1f, 0f, k, t, t + r, ink), Offset(0f, t), Size(w, r), blendMode = mode)
                    }
                }
                val kb = if (fadeFromBottom != null) bottomStrength().coerceIn(0f, 1f) else 0f
                if (fadeFromBottom != null && kb > 0f) {
                    val from = (size.height - fadeFromBottom()).coerceIn(0f, size.height)
                    val y0 = wholePx(from)
                    val r = wholePx(from + (bottomReachPx?.invoke() ?: bottomReach.toPx())) - y0
                    if (r > 0f) drawRect(bottom.of(0f, 1f, kb, y0, y0 + r, ink), Offset(0f, y0), Size(w, r), blendMode = mode)
                    val below = y0 + r
                    val kept = wholePx((size.height - keepBottomPx()).coerceAtMost(size.height))
                    if (below < kept)
                        drawRect(ink.copy(alpha = kb), Offset(0f, below), Size(w, kept - below), blendMode = mode)
                }
            }
        }
    return if (flat) this.then(draw) else graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.then(draw)
}

/** One edge's clearing gradient, kept until its strength or its span changes. */
internal class EdgeBrush {
    private var k = Float.NaN
    private var start = Float.NaN
    private var end = Float.NaN
    private var ink = Color.Unspecified
    private var brush: Brush? = null

    fun of(from: Float, to: Float, strength: Float, startY: Float, endY: Float, color: Color = Color.Black): Brush {
        val kept = brush
        if (kept != null && k == strength && start == startY && end == endY && ink == color) return kept
        k = strength
        start = startY
        end = endY
        ink = color
        return Brush.verticalGradient(clearing(from, to, strength, color), startY = startY, endY = endY).also { brush = it }
    }
}
