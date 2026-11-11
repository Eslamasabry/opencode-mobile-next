package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState

/**
 * A page whose body runs under a floating liquid bar (glass in the kit, step 4; owner 2026-11-11 on the home in Glass:
 * "Is this supposed to be glass?": outside the chat nothing ever passed under a glass pane, so the lens had nothing to
 * bend). Set on [KitScreen] next to its `topBar`. Opt-in: a screen without it lays out exactly as before.
 *
 * Where the lens is drawn (Glass, Android 13+, no battery saver, no reduced motion, no high contrast, a look with a lens,
 * a window that is not short) the bar is a liquid capsule pinned under the status bar, [pills] float as liquid pills
 * under it, and the body scrolls on under both: at rest its first part rests right below the pills (a spacer that
 * scrolls away first, as on a page with a header); once it scrolls, its content passes under the pills, the capsule and the
 * status bar, where it dissolves into a blur of itself. Everywhere else the page keeps today's layout: the pinned bar,
 * or with [scrollsElsewhere] the bar and pills as the first parts of the scrolling header.
 *
 * The pills are the one exception to the frame's "no new bar levels" (R9): they belong to the floating bar and exist
 * only with it.
 */
@Stable
public class KitFloatingBar(
    /**
     * Without the lens (or on a short window) the bar and [pills] are not pinned: they lie at the top of the page's
     * scrolling header, before its own header, and scroll away with the body (the Conversations home's own heading).
     * False keeps today's pinned bar there.
     */
    public val scrollsElsewhere: Boolean = false,
    /**
     * The feature's own recording of its body (the conversations list under its +): the capsule, the pills and the
     * status bar's edge bend and blur it, and the kit records nothing of its own. Null: the kit records the body.
     */
    public val source: KitFrostState? = null,
    /**
     * Small parts that float under the capsule, outside it (the home's server pill and filters). They are liquid pills
     * when [source] is given (a kit part reads it through `LocalKitLiquidChrome`); with the kit's own recording they keep
     * their own faces.
     */
    public val pills: (@Composable () -> Unit)? = null,
)

/**
 * A part brought into view on a floating page (a focused field, a part reached with the keyboard or TalkBack) never
 * rests under the bar. The page's scroller has already brought it into its own room, which runs on under the bar; when
 * what it asked to show still starts under the bar and the status bar ([chromePx] from the frame's top), the frame
 * brings its header (the spacer) back, all of it, so the page returns to its resting layout and the part lies below
 * the bar as it did before the page floated. Nothing is asked of the page's own scrollers, so a sideways one (a chip
 * row) is untouched.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class FloatingBarIntoView(
    private val header: KitScrollingHeaderState,
    private val chromePx: () -> Int,
    private val spec: () -> AnimationSpec<Float>?,
) : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect = localRect

    override suspend fun bringChildIntoView(localRect: () -> Rect?) {
        val rect = localRect() ?: return
        val back = floatingBringBack(rect.top, chromePx(), header.offset)
        if (back <= 0f) return
        val motion = spec()
        if (motion == null) {
            header.bringBack(back)
            return
        }
        var done = 0f
        animate(0f, back, animationSpec = motion) { value, _ ->
            header.bringBack(value - done)
            done = value
        }
    }
}

/**
 * How far a floating page's header comes back for a part whose top is [top] px below the frame's top, under chrome
 * [chromePx] tall, with the header [offset] px scrolled away (0 … −height): all of it while the part starts under the
 * chrome, nothing once it lies below.
 */
internal fun floatingBringBack(top: Float, chromePx: Int, offset: Float): Float =
    if (top >= chromePx || offset >= 0f) 0f else -offset
