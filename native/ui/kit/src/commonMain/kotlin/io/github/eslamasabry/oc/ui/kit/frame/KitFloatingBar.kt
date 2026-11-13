package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_INSET_X
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState

/**
 * A page whose body runs under a floating liquid bar (glass in the kit, step 4; owner 2026-11-11 on the home in Glass:
 * "Is this supposed to be glass?": outside the chat nothing ever passed under a glass pane, so the lens had nothing to
 * bend). Set on [KitScreen] next to its `topBar`. Opt-in: a screen without it lays out exactly as before.
 *
 * Where the lens is drawn (Glass, Android 13+, no battery saver, no reduced motion, no high contrast, a look with a lens,
 * a window that is not short) the bar and its second row ([below]) lie on ONE liquid pane pinned under the status bar,
 * one body of glass with one bottom edge, and the body scrolls on under it: at rest its first part rests right below
 * the pane (a spacer that scrolls away first, as on a page with a header); once it scrolls, its content passes under
 * the pane and the status bar, and nothing of it shows above the pane's bottom edge except through the glass.
 * Everywhere else the page keeps today's layout: the pinned bar, or with [scrollsElsewhere] the bar and [below] as the
 * first parts of the scrolling header.
 *
 * [below] is the one exception to the frame's "no new bar levels" (R9): it belongs to the floating bar and exists only
 * with it. Its controls sit on the pane: they take no frost of their own and ask for no lens.
 */
@Stable
public class KitFloatingBar(
    /**
     * Without the lens (or on a short window) the bar and [below] are not pinned: they lie at the top of the page's
     * scrolling header, before its own header, and scroll away with the body (the Conversations home's own heading).
     * False keeps today's pinned bar there.
     */
    public val scrollsElsewhere: Boolean = false,
    /**
     * The feature's own recording of its body (the conversations list under its +): the pane and the status bar's edge
     * bend and blur it, and the kit records nothing of its own. Null: the kit records the body.
     */
    public val source: KitFrostState? = null,
    /**
     * A second row inside the bar's pane, under the bar (the home's server pill, divider and filters): controls on the
     * glass, not panes of their own.
     */
    public val below: (@Composable () -> Unit)? = null,
    /**
     * Where the bar does not float (no lens: Solid, Soft, Brutal, Paper, Glass held or on a short window) and is not
     * [scrollsElsewhere], [below] stays pinned directly under the bar on the page margin instead of scrolling away as the
     * scrolling header's first part (a page's tab row). False keeps the home's behaviour: its chips row scrolls away.
     * Where the bar floats the row is in its pane either way.
     */
    public val belowPinned: Boolean = false,
)

/**
 * The floating pane's second row ([KitFloatingBar.below]) and the sideways lines on it (owner's first phone look,
 * 2026-11-11: the last chip was sliced by the pane's rounded corner, a chip scrolled to the start was cut hard, and the
 * pane's bottom edge sat 3 dp under the chips). Nothing here applies where the bar does not float.
 */
public object FloatingPaneRow {
    /**
     * Room under the second row inside the pane: as much as the page heading keeps above its title row (the page's
     * space2 and the heading's own), so the pane's top and bottom edges sit equally far from the rows they hold (that
     * room less the pane's own inset, [io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_INSET_Y], at both ends).
     */
    public const val ROOM_BELOW_DP: Int = Space.S2 + Space.S2

    /** A sideways line's clip edge, in from the bar's side: the pane's own side, so nothing of it draws beside the pane. */
    public const val EDGE_DP: Int = LIQUID_BAR_INSET_X

    /** How far a sideways line fades out at an edge with more of it past that edge (the kit's scroll fade, kitScrollFade). */
    public const val FADE_DP: Int = Space.S4

    /**
     * Where a sideways line's content rests inside its clip edge: the page gutter less the pane's inset, so its first and
     * last chips rest on the title's gutter, clear of the pane's rounded corners, and a chip leaving the pane thins out
     * inside it rather than meeting its outline.
     */
    public fun contentInsetDp(gutter: Int): Int = (gutter - LIQUID_BAR_INSET_X).coerceAtLeast(0)
}

/** True inside the floating pane's second row (where the lens is drawn): sideways lines there follow [FloatingPaneRow]. */
public val LocalKitOnFloatingPane: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

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
