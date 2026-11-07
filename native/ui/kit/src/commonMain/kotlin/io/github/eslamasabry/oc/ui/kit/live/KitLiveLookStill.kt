package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.res.Res
import io.github.eslamasabry.oc.ui.kit.res.aurora_dark
import io.github.eslamasabry.oc.ui.kit.res.aurora_light
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

/** Draws the aurora [image] over the whole scope at [drift], through [filter], at [alpha] (`kit_live_aurora_image.dart:282-314`). */
internal fun DrawScope.drawAuroraPlate(image: ImageBitmap, filter: ColorFilter, alpha: Float, drift: AuroraImage.Drift) {
    val src = AuroraImage.sourceRect(image.width.toFloat(), image.height.toFloat(), size.width, size.height, drift)
    drawImage(
        image,
        srcOffset = IntOffset(src[0].roundToInt(), src[1].roundToInt()),
        srcSize = IntSize(src[2].roundToInt().coerceAtLeast(1), src[3].roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        colorFilter = filter,
        filterQuality = FilterQuality.Medium, // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:303
    )
}

/** The plate for this brightness, turned to the accent and tinted onto the ground; null for a look the app does not draw. */
@Composable
internal fun rememberAuroraPlate(look: String?): Pair<ImageBitmap, ColorFilter>? {
    if (look != AuroraImage.ID) return null
    val roles = LocalKitRoles.current
    val image = imageResource(if (roles.dark) Res.drawable.aurora_dark else Res.drawable.aurora_light)
    val filter =
        remember(roles.ground, roles.accent, roles.dark) {
            ColorFilter.colorMatrix(ColorMatrix(AuroraImage.matrixFor(roles.ground, roles.accent, roles.dark)))
        }
    return if (image.width > 1 && image.height > 1) image to filter else null
}

/**
 * One still frame of [look] (null: the plain ground) behind a tiny mock conversation, for a look's choice card (Dart
 * `_LookSample` and `liveMockChat`, `lib/ui/kit/kit_option_card.dart:755-783` and
 * `lib/ui/kit/live_background/live_mock_chat.dart` at GLASS_REF 1bbdc3f9c). Still, so a grid of samples costs no
 * battery; drawn at the samples' strength 1.6 (`kit_option_card.dart:773`). The message and the box are drawn as the
 * style in force draws them.
 *
 * States: none.
 */
@Composable
internal fun KitLiveLookStill(look: String?, modifier: Modifier = Modifier) {
    val ground = LocalKitColors.current.ground
    val plate = rememberAuroraPlate(look)
    // Under tests and screenshots a drawn look's sample keeps the plain ground, as Flutter's goldens do.
    val drawn = if (plate == null && !LocalKitMotion.current.stillFrame) kitLiveStyleOrNull(look) else null
    val alpha = AuroraImage.alphaFor(SAMPLE_STRENGTH.toFloat())
    val corner = (FlutterKitTokens.ICON_TILE_RADIUS + Space.S1).dp // FG: lib/ui/kit/kit_option_card.dart:766
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(ground)
            .drawBehind { plate?.let { (image, filter) -> drawAuroraPlate(image, filter, alpha, AuroraImage.driftAt(0.0)) } }
            .clearAndSetSemantics {}
    ) {
        // A drawn look: one still frame at the samples' strength (kit_option_card.dart:754-759, `still: true`).
        KitLiveLayer(drawn, SAMPLE_STRENGTH, moves = false, running = false, modifier = Modifier.matchParentSize())
        LiveMockChat(scale = .7f) // F: lib/ui/kit/kit_option_card.dart:759
    }
}

/** The strength a look's sample is drawn at. */
private const val SAMPLE_STRENGTH: Double = 1.6 // F: lib/ui/kit/kit_option_card.dart:756
