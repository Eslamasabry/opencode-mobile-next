package io.github.eslamasabry.oc.ui.kit.frame

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
import io.github.eslamasabry.oc.ui.kit.foundation.kitDecorated
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
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

/** The capsule's side margin, dp: where the message box and the transcript's bubbles stand (measured on the real chat). */
internal const val HEADER_CAPSULE_SIDE_DP: Int = 14

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
    val style = io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle.current.style
    // EXACTLY the message box's material (owner 2026-11-17, one fill family): the same surface step, the same corner,
    // the same soft shadow in Solid and the same hairline, so top and bottom read as one design in both themes.
    val look =
        io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook(
            io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput(
                role = io.github.eslamasabry.oc.ui.design.style.SurfaceRole.Floating,
                shape = io.github.eslamasabry.oc.ui.design.tokens.KitShapeId.Panel,
                level = io.github.eslamasabry.oc.ui.kit.foundation.kitFloatingLevel(colors),
                ownFill = io.github.eslamasabry.oc.ui.kit.foundation.kitFloatingFill(colors),
                radius = tokens.composerRadius.dp,
            )
        )
    val solid = style == io.github.eslamasabry.oc.ui.design.style.Style.Solid
    val shadow =
        if (!solid) Modifier
        else
            Modifier.kitDecorated(
                        io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration(
                            radii = io.github.eslamasabry.oc.ui.design.style.Radii.all(tokens.composerRadius.toFloat()),
                            shadows =
                                listOf(
                                    io.github.eslamasabry.oc.ui.design.style.Shadow(
                                        dxDp = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.FLOATING_SHADOWS_DX.toFloat(),
                                        dyDp = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.FLOATING_SHADOWS_DY.toFloat(),
                                        blurDp = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.FLOATING_SHADOWS_BLUR.toFloat(),
                                        spreadDp = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.FLOATING_SHADOWS_SPREAD.toFloat(),
                                        color =
                                            io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles.current.elevationShadow
                                                .withAlpha(io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.FLOATING_SHADOWS_ALPHA),
                                    )
                                ),
                        )
                    )
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_CAPSULE_SIDE_DP.dp, vertical = HEADER_CAPSULE_MARGIN_DP.dp)
            .then(shadow)
            .then(if (solid) Modifier.kitHairlineBorder(io.github.eslamasabry.oc.ui.kit.foundation.kitHairline(), colors.hairline, look.shape) else Modifier)
            .then(look.modifier)
            .testTag("kit-header-capsule")
    ) {
        CompositionLocalProvider(LocalKitHeaderCapsule provides true, content = content)
    }
}
