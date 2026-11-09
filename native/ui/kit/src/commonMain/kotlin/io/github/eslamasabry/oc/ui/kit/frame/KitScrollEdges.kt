package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
private fun clearing(from: Float, to: Float, strength: Float): List<Color> =
    List(EDGE_STOPS) { i ->
        val f = i / (EDGE_STOPS - 1f)
        Color.Black.copy(alpha = ((from + (to - from) * edgeEase(f)) * strength).coerceIn(0f, 1f))
    }

/**
 * Fades this content out at its scroll edges. Top: cleared above [clearAbove] px, fading in over [topReach] below it.
 * Bottom: whole down to [fadeFromBottom] px above the bottom edge, fading out over [bottomReach] from there, cleared
 * below. A null edge is left alone; [strength] (0–1, read while drawing) eases the top edge in and out. The content
 * is drawn in its own layer so the fade clears it alone.
 */
internal fun Modifier.kitScrollEdges(
    clearAbove: (() -> Float)?,
    topReach: Dp,
    fadeFromBottom: (() -> Float)?,
    bottomReach: Dp,
    strength: () -> Float = { 1f },
    /** The top fade's length in px, read while drawing; overrides [topReach] (the status edge's dissolve). */
    topReachPx: (() -> Float)? = null,
): Modifier {
    if (clearAbove == null && fadeFromBottom == null) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = size.width
            if (clearAbove != null) {
                val k = strength().coerceIn(0f, 1f)
                if (k > 0f) {
                    val t = clearAbove().coerceIn(0f, size.height)
                    val r = topReachPx?.invoke() ?: topReach.toPx()
                    if (t > 0f) {
                        drawRect(Color.Black.copy(alpha = k), Offset.Zero, Size(w, t), blendMode = BlendMode.DstOut)
                    }
                    if (r > 0f)
                        drawRect(
                            Brush.verticalGradient(clearing(1f, 0f, k), startY = t, endY = t + r),
                            Offset(0f, t),
                            Size(w, r),
                            blendMode = BlendMode.DstOut,
                        )
                }
            }
            if (fadeFromBottom != null) {
                val y0 = (size.height - fadeFromBottom()).coerceIn(0f, size.height)
                val r = bottomReach.toPx()
                if (r > 0f)
                    drawRect(
                        Brush.verticalGradient(clearing(0f, 1f, 1f), startY = y0, endY = y0 + r),
                        Offset(0f, y0),
                        Size(w, r),
                        blendMode = BlendMode.DstOut,
                    )
                val below = y0 + r
                if (below < size.height)
                    drawRect(Color.Black, Offset(0f, below), Size(w, size.height - below), blendMode = BlendMode.DstOut)
            }
        }
}
