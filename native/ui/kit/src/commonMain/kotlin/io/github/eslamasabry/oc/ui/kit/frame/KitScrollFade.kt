package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space

/** Which edges of a sideways line fade, in reading order. */
public data class KitFadeEdges(val start: Boolean, val end: Boolean)

/** Dart `KitScrollFade` / `KitFilterChips` fade rules as pure functions. */
public object ScrollFadeRules {
    /** An edge fades only when more of the line lies past it (Flutter: extent past the edge > 0.5 px). */
    public fun edges(value: Int, max: Int): KitFadeEdges = KitFadeEdges(start = value > 0, end = value < max)

    /** Reading order to physical sides: (left, right). The scroll's start is the right in Arabic. */
    public fun leftRight(edges: KitFadeEdges, rtl: Boolean): Pair<Boolean, Boolean> =
        if (rtl) edges.end to edges.start else edges.start to edges.end

    /**
     * The mask's six stops over [widthPx]: the outer third of each fade fully clear, so no letter stands half visible
     * at the very edge; a fade never takes more than half the line.
     */
    public fun stops(widthPx: Float, fadePx: Float): List<Float> {
        val e = if (widthPx <= 0f) 0f else (fadePx / widthPx).coerceIn(0f, 0.5f)
        return listOf(0f, e / 3f, e, 1f - e, 1f - e / 3f, 1f)
    }

    /** `space6 + space4` (40 dp): enough for a word to fade rather than read as cut. */
    public const val DEFAULT_WIDTH_DP: Int = Space.S6 + Space.S4
}

/**
 * Fades the edges of a sideways-scrolling line where there is more past them (Dart `KitScrollFade`; plan 06 §7.3): a
 * chip running past an edge melts away over [width] instead of ending in a hard cut mid-word ("Auto Ac"). An edge with
 * nothing beyond it stays sharp; a line that fits fades nowhere. The fade follows the scroll, read in draw.
 *
 * Apply it to the clipping box around a `horizontalScroll(state)`. Always masked, so the scroller keeps its place.
 *
 * States: none (a mask over a line)
 * Text: none
 * Cap: none
 * Motion: follows the scroll (class Follow)
 * Replaces: KitScrollFade (lib/ui/kit/kit_scroll_fade.dart)
 */
@Composable
public fun Modifier.kitScrollFade(state: ScrollState, width: Dp = ScrollFadeRules.DEFAULT_WIDTH_DP.dp): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edges = ScrollFadeRules.edges(state.value, state.maxValue)
            val (left, right) = ScrollFadeRules.leftRight(edges, rtl)
            if (!left && !right) return@drawWithContent
            val s = ScrollFadeRules.stops(size.width, width.toPx())
            val l = if (left) Color.Transparent else Color.Black
            val r = if (right) Color.Transparent else Color.Black
            val brush =
                Brush.horizontalGradient(
                    s[0] to l, s[1] to l, s[2] to Color.Black, s[3] to Color.Black, s[4] to r, s[5] to r,
                )
            drawRect(brush, blendMode = BlendMode.DstIn)
        }
}
