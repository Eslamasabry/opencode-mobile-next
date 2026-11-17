package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * True inside a [KitScreen] whose chrome floats (a chat: the header capsule on top, the message box at the bottom, the
 * transcript running behind both with nothing painted but each part's own fill). Read by the list that ends itself above
 * the bottom block (it then ends nowhere: no band, no mask) and by the jump control (a round button, not a pill).
 */
public val LocalKitScreenFloating: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** True inside a [KitHeaderCapsule]: the bar's title takes one line (its subtitle or scope line sits under it). */
public val LocalKitHeaderCapsule: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** The margin above and below the capsule, dp; the frame starts the transcript's clip this far under the status area. */
internal const val HEADER_CAPSULE_MARGIN_DP: Int = 4

/** The message box stands this far in from the page gutter (its own pane inset); the capsule matches it, dp. */
internal const val HEADER_CAPSULE_SIDE_EXTRA_DP: Int = 4

/**
 * The floating header of a chat (owner 2026-11-17, "one capsule will look cleaner"): one capsule across the width, on
 * its own opaque fill, with the message box's side margins and corner, holding the bar's back arrow, title and overflow.
 * No bar, plate, line, gradient or fade lies behind it; the transcript runs behind it and shows only above, below and in
 * the side margins, never between parts. It mirrors the message box: one box at the bottom, one at the top.
 *
 * Tag `kit-header-capsule`. Cap: Fill. Motion: none.
 */
@Composable
public fun KitHeaderCapsule(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    // The face is the style's own (a floating pane at the message box's corner), never painted by hand here.
    val look =
        io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook(
            io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput(
                role = io.github.eslamasabry.oc.ui.design.style.SurfaceRole.Floating,
                shape = io.github.eslamasabry.oc.ui.design.tokens.KitShapeId.Panel,
                level = io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Surface3,
                ownFill = colors.surface3,
                radius = tokens.composerRadius.dp,
            )
        )
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.gutter.dp + HEADER_CAPSULE_SIDE_EXTRA_DP.dp, vertical = HEADER_CAPSULE_MARGIN_DP.dp)
            .then(look.modifier)
            .border(1.dp, colors.hairline, look.shape)
            .testTag("kit-header-capsule")
    ) {
        CompositionLocalProvider(LocalKitHeaderCapsule provides true, content = content)
    }
}
