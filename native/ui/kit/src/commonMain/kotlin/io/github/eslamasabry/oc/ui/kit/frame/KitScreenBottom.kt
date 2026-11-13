package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole

/*
 * What a screen's bottom block is. A chat's message box on Glass is one slab of glass (owner 2026-11-10: "a slab of glass
 * without any fade below"): it sits in from the sides as the bar does and the jump pill joins it. Since 2026-11-14 (the
 * owner's "Wtfff" screenshot: words bent under the pill and smeared along the box's top edge) no body runs under any
 * bottom block: the body fades out above the block, and above a control floating over it, in every style, and the
 * block's glass sees the body cleared the same way (KitScreen).
 */

/**
 * What a screen's bottom block is. A block that is one pane of glass over the body (a chat's message box) reports
 * [slab]; the jump pill then joins it while nothing but the folding phrase row stands above it ([stacked]).
 */
@Stable
public class KitScreenBottom internal constructor() {
    internal var slab: Boolean by mutableStateOf(false)

    /**
     * The top edge, in root px, of the block's liquid pane that carries words to read or type (the message box); NaN
     * while there is none. What the block's glass sees of the body fades out below it ([KitWordsFade]).
     */
    internal var wordsTop: Float by mutableFloatStateOf(Float.NaN)

    /** How far that pane's lens rim reaches in from its top edge, px ([LiquidEdge.rimPx] for the pane's size). */
    internal var wordsRim: Float by mutableFloatStateOf(0f)

    /** The top edge, in root px, of the whole bottom block (its padding included); NaN until it is placed. */
    internal var blockTop: Float by mutableFloatStateOf(Float.NaN)

    /** The room, px, the block keeps above its words pane when nothing else sits there (its padding and the box's own gap). */
    internal var restGap: Float by mutableFloatStateOf(0f)

    /**
     * Whether something sits above the words pane inside the block (a request card and its label, Find, a notice): the
     * pane's top lies more than the block's resting gap below the block's top, not counting the phrase row that folds
     * away while the pill shows ([foldPx]). The jump pill, which would join the box's top, then stays above the block,
     * in the zone the body has faded out of.
     */
    internal val stacked: Boolean by derivedStateOf { !wordsTop.isNaN() && !blockTop.isNaN() && wordsTop - blockTop - foldPx > restGap }

    /**
     * The height, px, of the parts above the words pane that fold away while the body is away from its newest end (the
     * phrase row, [kitScreenFoldsWhileAway]). They do not make the block [stacked]: when the jump pill shows they are
     * gone, so the pill joins the box (owner 2026-11-14: "Why did you unlink jump to latest from the composer").
     */
    internal var foldPx: Float by mutableFloatStateOf(0f)

    /** Whether the body is away from its newest end ([KitReportAwayFromLatest]); read in composition. */
    internal var away: () -> Boolean by mutableStateOf({ false })

    /**
     * The top edge, in root px, of a control that floats over the body just above the block (the jump pill's face);
     * NaN while none shows. The body is gone above it too ([kitScreenFloat]).
     */
    internal var floatTop: Float by mutableFloatStateOf(Float.NaN)

    /**
     * What the glass of the block and of a control floating over it sees of the body: the screen's recording, cleared
     * where the body itself is cleared (KitScreen), so no glass bends words the page no longer shows. Null outside a
     * page that records its body.
     */
    internal var glass: KitFrostState? by mutableStateOf(null)
}

/**
 * Where a control floating under a screen's top chrome ends (the chat's "N earlier messages"): the body fades in below it
 * as it does below the bar, so the control never stands on a line of text ([kitScreenFloat]).
 */
@Stable
internal class KitScreenTopFloat {
    /** The control's bottom edge, root px; NaN while none shows. */
    var bottom: Float by mutableFloatStateOf(Float.NaN)
}

internal val LocalKitScreenTopFloat = staticCompositionLocalOf<KitScreenTopFloat?> { null }

/**
 * Marks this node as a control that floats over the screen's body at its [edge]: under the top chrome ([KitJumpEdge.Top])
 * or just above the bottom block ([KitJumpEdge.Bottom]). The frame then ends its body, by its soft fade, before the
 * control, so no line of text runs under it and its glass sees none. Outside a screen frame: nothing.
 */
@Composable
public fun Modifier.kitScreenFloat(edge: KitJumpEdge): Modifier {
    val top = LocalKitScreenTopFloat.current.takeIf { edge == KitJumpEdge.Top }
    val bottom = LocalKitScreenBottom.current.takeIf { edge == KitJumpEdge.Bottom }
    if (top == null && bottom == null) return this
    DisposableEffect(top, bottom) {
        onDispose {
            top?.bottom = Float.NaN
            bottom?.floatTop = Float.NaN
        }
    }
    return onGloballyPositioned {
        val y = it.positionInRoot().y
        if (top != null) top.bottom = y + it.size.height
        if (bottom != null) bottom.floatTop = y
    }
}

/**
 * Tells the screen frame whether its body is away from its newest end (a transcript scrolled up into its history). The
 * phrase row folds away meanwhile ([kitScreenAwayFromLatest]). Outside such a frame: nothing.
 */
@Composable
public fun KitReportAwayFromLatest(away: () -> Boolean) {
    val bottom = LocalKitScreenBottom.current ?: return
    val latest = androidx.compose.runtime.rememberUpdatedState(away)
    DisposableEffect(bottom) {
        bottom.away = { latest.value() }
        onDispose { bottom.away = { false } }
    }
}

/** Whether this screen's body is away from its newest end ([KitReportAwayFromLatest]); false outside a frame. */
@Composable
public fun kitScreenAwayFromLatest(): Boolean = LocalKitScreenBottom.current?.away?.invoke() == true

/**
 * The recording a control floating over this screen's body near its bottom block reads (the jump pill): cleared where
 * the body is ([KitScreenBottom.glass]); null outside a page that records its body.
 */
@Composable
public fun kitScreenBottomGlass(): KitFrostState? = LocalKitScreenBottom.current?.glass

/**
 * Reports this node's height as a part of the bottom block that folds away while the body is away from its newest end
 * (the phrase row), so the block does not count as stacked for it ([KitScreenBottom.foldPx]).
 */
@Composable
internal fun Modifier.kitScreenFoldsWhileAway(): Modifier {
    val bottom = LocalKitScreenBottom.current ?: return this
    DisposableEffect(bottom) { onDispose { bottom.foldPx = 0f } }
    return onSizeChanged { bottom.foldPx = it.height.toFloat() }
}

internal val LocalKitScreenBottom = staticCompositionLocalOf<KitScreenBottom?> { null }

/** Tells the screen frame that its bottom block is one pane of glass over the body. Outside such a frame: nothing. */
@Composable
public fun KitReportBottomSlab(slab: Boolean) {
    val bottom = LocalKitScreenBottom.current ?: return
    DisposableEffect(bottom, slab) {
        bottom.slab = slab
        onDispose { bottom.slab = false }
    }
}

/** Whether this screen's bottom block is one pane of glass over the body ([KitReportBottomSlab]); false outside one. */
@Composable
public fun kitScreenBottomIsSlab(): Boolean = LocalKitScreenBottom.current?.slab == true

/** Whether something stands above the message box inside this screen's bottom block ([KitScreenBottom.stacked]); false outside one. */
@Composable
internal fun kitScreenBottomIsStacked(): Boolean = LocalKitScreenBottom.current?.stacked == true

/**
 * Marks this box as the bottom block's liquid pane of [role] that carries words to read or type (the message box), so
 * the body's content the block's glass sees fades out as it passes under it: whole at the pane's top edge, gone by the
 * inner end of its lens rim (KitWordsFade.kt). Use it only while the pane is liquid. Outside a screen frame: nothing.
 */
@Composable
public fun Modifier.kitScreenWordsPane(role: LiquidRole): Modifier {
    val bottom = LocalKitScreenBottom.current ?: return this
    val density = LocalDensity.current.density
    // The rim's reach depends on the role and the pane's size only, not on the theme or the look.
    val recipe = LiquidEdge.recipe(role, dark = true)
    DisposableEffect(bottom) {
        onDispose {
            bottom.wordsTop = Float.NaN
            bottom.wordsRim = 0f
        }
    }
    return onGloballyPositioned { c ->
        bottom.wordsTop = c.positionInRoot().y
        bottom.wordsRim = LiquidEdge.rimPx(minOf(c.size.width, c.size.height).toFloat(), density, recipe)
    }
}

/**
 * Where a page that records its body ends its scrolling content (root px, read while drawing): cleared above [topClear]
 * (while [topOn] says so) and gone below [bottomGone], each with the frame's soft fade. A scrolling list applies it to
 * itself ([kitScreenListEdges]), so the controls floating beside it (the jump pill, "N earlier messages"), which are not
 * part of the list, stay whole while the words under them go.
 */
internal class KitScreenZones(
    val topClear: () -> Float,
    val topOn: () -> Float,
    val bottomGone: () -> Float,
) {
    /** How many lists end themselves here; while one does, the frame leaves the body's bottom to them. */
    var claims: Int by androidx.compose.runtime.mutableIntStateOf(0)
}

internal val LocalKitScreenZones = staticCompositionLocalOf<KitScreenZones?> { null }

/**
 * Ends this scrolling content where its page's floating controls begin (2026-11-14, the owner's "Wtfff" screenshot):
 * it fades out over 16 dp above the bottom block or the jump pill floating over it and is gone below, and fades in over
 * 12 dp below a control floating under the bar. Apply it to the list itself, not to a box that also holds those
 * controls: they must not be cleared with it. Outside a page that records its body: nothing.
 */
@Composable
public fun Modifier.kitScreenListEdges(): Modifier {
    val zones = LocalKitScreenZones.current ?: return this
    DisposableEffect(zones) {
        zones.claims++
        onDispose { zones.claims-- }
    }
    val at = androidx.compose.runtime.remember { floatArrayOf(Float.NaN, 0f) }
    val bottomFade = with(LocalDensity.current) { COMPOSER_FADE_DP.dp.toPx() }
    return onGloballyPositioned {
        at[0] = it.positionInRoot().y
        at[1] = it.size.height.toFloat()
    }.kitScrollEdges(
        clearAbove = { zones.topClear().let { if (it.isNaN() || at[0].isNaN()) 0f else it - at[0] } },
        topReach = HEADER_FADE_DP.dp,
        strength = zones.topOn,
        fadeFromBottom = {
            val gone = zones.bottomGone()
            if (gone.isNaN() || at[0].isNaN()) 0f else at[0] + at[1] - gone + bottomFade
        },
        bottomReach = COMPOSER_FADE_DP.dp,
        bottomStrength = { if (zones.bottomGone().isNaN() || at[0].isNaN()) 0f else 1f },
    )
}
