package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

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
