package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole

/*
 * What a screen's bottom block is (owner 2026-11-10). A chat's message box on Glass is one slab of glass: the body runs
 * sharp behind it and only the margin under it clears (KitScreen). Every other bottom block keeps the body's fade above
 * it. (A blurred copy of the body behind three separate glass pieces was tried first and dropped with them: it ended in
 * a hard line at the frame's bottom edge.)
 */

/**
 * What a screen's bottom block is. A block that is one pane of glass over the body (a chat's message box, owner
 * 2026-11-10: "a slab of glass without any fade below") reports [slab]: the frame then neither fades the body above
 * it, so the body scrolls sharp up to the glass and bends under its edge, as under the bar.
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
