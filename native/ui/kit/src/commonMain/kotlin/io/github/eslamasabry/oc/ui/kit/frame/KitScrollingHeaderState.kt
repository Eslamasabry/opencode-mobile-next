package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * How a screen's header scrolls with its body (P-006), pure so it is tested without Compose. The header is the body's
 * first part, as in Flutter, where home's title and chips scroll with the list: scrolling up takes the header away
 * before the body moves; scrolling down brings it back only once the body is at its top. [offset] runs from 0 (all of
 * the header shown) to −[height] (all of it gone under the bar).
 */
@Stable
public class KitScrollingHeaderState {
    /** The header's measured height in px; written by the layout pass. */
    public var height: Int = 0
        internal set

    /** How far the header has scrolled away, in px (0 … −[height]). */
    public var offset: Float by mutableFloatStateOf(0f)
        private set

    /** An upward scroll [dy] (< 0) first takes the header away; returns the part it used. */
    public fun takeAway(dy: Float): Float {
        if (dy >= 0f) return 0f
        val next = (offset + dy).coerceIn(-height.toFloat(), 0f)
        val used = next - offset
        offset = next
        return used
    }

    /** A downward scroll [dy] (> 0) the body left over at its top brings the header back; returns the part used. */
    public fun bringBack(dy: Float): Float {
        if (dy <= 0f) return 0f
        val next = (offset + dy).coerceIn(-height.toFloat(), 0f)
        val used = next - offset
        offset = next
        return used
    }

    /** How much of the header shows, for a header [measured] px tall (it may have changed since the last scroll). */
    public fun shown(measured: Int): Int =
        (measured + offset.coerceIn(-measured.toFloat(), 0f)).toInt().coerceIn(0, measured)

    /**
     * How strongly the cut under the bar fades, 0 … 1: nothing while the whole header shows (nothing is cut yet), in
     * full once [reachPx] of it has gone (the kit's 12 dp chrome fade), so the fade never pops in.
     */
    public fun fade(reachPx: Float): Float =
        if (reachPx <= 0f || offset >= 0f) 0f else (-offset / reachPx).coerceIn(0f, 1f)

    internal val connection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                Offset(0f, takeAway(available.y))

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                Offset(0f, bringBack(available.y))
        }
}

/**
 * The screen's header and body as one scrolling column (P-006): the header slides up under the bar as the body scrolls
 * and comes back at the body's top; the body takes the room the header gives up. The cut under the bar fades over the
 * kit's 12 dp chrome fade once anything is under it, never a hard line through a row. Direct manipulation only, so
 * there is no motion to reduce.
 */
@Composable
internal fun KitScrollingHeaderFrame(
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: KitScrollingHeaderState = remember { KitScrollingHeaderState() },
    rest: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        content = {
            Column(Modifier.fillMaxWidth().testTag("kit-screen-header"), content = header)
            Column(Modifier.fillMaxWidth(), content = rest)
        },
        modifier =
            modifier
                .clipToBounds()
                .nestedScroll(state.connection)
                .graphicsLayer {
                    compositingStrategy =
                        if (state.fade(HEADER_FADE_DP.dp.toPx()) > 0f) CompositingStrategy.Offscreen
                        else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    val reach = HEADER_FADE_DP.dp.toPx()
                    val t = state.fade(reach)
                    if (t > 0f) {
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 1f - t),
                                1f to Color.Black,
                                startY = 0f,
                                endY = reach,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, reach),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
    ) { measurables, constraints ->
        val head = measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        state.height = head.height
        val shown = state.shown(head.height)
        val bounded = constraints.hasBoundedHeight
        val restHeight = if (bounded) (constraints.maxHeight - shown).coerceAtLeast(0) else Constraints.Infinity
        val body =
            measurables[1].measure(
                constraints.copy(minHeight = if (bounded) restHeight else 0, maxHeight = restHeight)
            )
        val height = if (bounded) constraints.maxHeight else shown + body.height
        layout(constraints.maxWidth, height) {
            head.placeRelative(0, shown - head.height)
            body.placeRelative(0, shown)
        }
    }
}
