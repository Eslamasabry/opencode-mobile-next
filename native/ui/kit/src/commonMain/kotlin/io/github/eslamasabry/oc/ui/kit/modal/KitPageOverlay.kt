package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowTitle

/**
 * A full-page local editor, with its own focus and modal host; the owning screen keeps its state.
 *
 * The overlay is a separate window, so nothing in it may join the page's shared-element transition: inside a page that
 * grew from a row, its KitTopBar title took the page's grow key and shared bounds across the two windows, and opening
 * the chat's prompt editor crashed the app (2026-11-08). The grow host and title are cleared here.
 */
@Composable
public fun KitPageOverlay(visible: Boolean, title: String, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    // The popup outlives `visible` by the exit, so the page fades out instead of vanishing: fade in on the enter pace,
    // out on QUICK; reduced motion snaps (the specs snap) and the popup goes in the same frame.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!visible && !shown.currentState) return
    val specs = KitMotion.specs
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = shown,
            enter = if (KitMotion.state.reduced) EnterTransition.None else fadeIn(specs.enter()),
            exit = if (KitMotion.state.reduced) ExitTransition.None else fadeOut(specs.exit()),
            label = "kit.pageOverlay",
        ) {
            CompositionLocalProvider(LocalKitGrowHost provides null, LocalKitGrowTitle provides null) {
                OverlayPage(title, content)
            }
        }
    }
}

@Composable
private fun OverlayPage(title: String, content: @Composable () -> Unit) {
    KitSurface(
        modifier = Modifier.fillMaxSize().testTag("kit-page-overlay").semantics { paneTitle = title },
        level = SurfaceLevel.Ground,
        shape = KitShapeId.Square,
        padding = KitSurfacePadding.None,
    ) {
        KitSheetHost { content() }
    }
}
