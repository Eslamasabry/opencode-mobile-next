package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import kotlin.math.abs

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
 * How far a scroller in a floating page scrolls to bring a part into view (a focused field, a part reached with the
 * keyboard): as Compose's default, but over the room below the floating bar, so the part never rests under the bar.
 * [obscured] is how many px at the scroller's top lie under the bar and the status bar right now (0 at rest, where the
 * spacer holds the body below them).
 */
@OptIn(ExperimentalFoundationApi::class)
internal class FloatingBarBringIntoView(private val obscured: () -> Int) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = obscured().toFloat().coerceIn(0f, containerSize)
        return bringIntoViewDistance(offset - top, size, containerSize - top)
    }
}

/**
 * Compose's default bring-into-view rule for a part [leading] px from the top of [room] px of visible room: no scroll
 * when it fits (or is bigger than the room and covers it), else the smaller of the moves that align its leading or its
 * trailing edge with the room's.
 */
internal fun bringIntoViewDistance(leading: Float, size: Float, room: Float): Float {
    val trailing = leading + size
    return when {
        leading >= 0f && trailing <= room -> 0f
        leading < 0f && trailing > room -> 0f
        abs(leading) < abs(trailing - room) -> leading
        else -> trailing - room
    }
}
