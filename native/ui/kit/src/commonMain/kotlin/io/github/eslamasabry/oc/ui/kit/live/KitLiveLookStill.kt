package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.res.Res
import io.github.eslamasabry.oc.ui.kit.res.aurora_dark
import io.github.eslamasabry.oc.ui.kit.res.aurora_light
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import org.jetbrains.compose.resources.imageResource
import kotlin.math.min
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

/**
 * Draws the page's soft lights ([AuroraImage.lightsFor]) over the plate, in [accent]'s hue, added to what is there, at
 * [alpha] (the plate's own). Fixed in page fractions: the aurora drifts under them (glass-in-the-kit 1b).
 */
internal fun DrawScope.drawAuroraLights(accent: io.github.eslamasabry.oc.ui.design.color.Argb, dark: Boolean, alpha: Float) {
    val lights = AuroraImage.lightsFor(dark)
    if (lights.isEmpty() || alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    val color = Color(AuroraImage.lightColorFor(accent).v)
    // The light's fall-off, sampled finely enough that the gradient's straight pieces do not show as rings.
    val stops = Array(LIGHT_STOPS + 1) { i ->
        val d = i.toFloat() / LIGHT_STOPS
        d to color.copy(alpha = AuroraImage.lightShare(d))
    }
    for (l in lights) {
        val center = androidx.compose.ui.geometry.Offset(size.width * l.x, size.height * l.y)
        val radius = size.width * l.radius
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(*stops, center = center, radius = radius),
            radius = radius,
            center = center,
            alpha = alpha,
            blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
        )
    }
}

/** How many pieces a light's radial fall-off is drawn in. */
private const val LIGHT_STOPS = 24

/**
 * The plate for this brightness, turned to the accent and tinted onto the ground; null for a look the app does not draw.
 * A [thumbnail] (a look's choice card) keeps the quiet plate's chroma ([AuroraImage.thumbnailChromaFor]).
 */
@Composable
internal fun rememberAuroraPlate(look: String?, thumbnail: Boolean = false): Pair<ImageBitmap, ColorFilter>? {
    if (look != AuroraImage.ID) return null
    val roles = LocalKitRoles.current
    val image = imageResource(if (roles.dark) Res.drawable.aurora_dark else Res.drawable.aurora_light)
    val filter =
        remember(roles.ground, roles.accent, roles.dark, thumbnail) {
            val chroma = if (thumbnail) AuroraImage.thumbnailChromaFor(roles.dark) else AuroraImage.chromaFor(roles.dark)
            ColorFilter.colorMatrix(ColorMatrix(AuroraImage.matrixFor(roles.ground, roles.accent, roles.dark, chroma)))
        }
    return if (image.width > 1 && image.height > 1) image to filter else null
}

/**
 * A sample of [look] (null: the plain ground) behind a tiny mock conversation, for a look's choice card (Dart
 * `_LookSample` and `liveMockChat`, `lib/ui/kit/kit_option_card.dart:755-783` and
 * `lib/ui/kit/live_background/live_mock_chat.dart` at GLASS_REF 1bbdc3f9c), drawn at the samples' strength 1.6
 * (`kit_option_card.dart:773`). A shader look runs live on the page's clock while the card is on screen and motion is
 * Full, and rests on its still frame when scrolled away; the painted aurora is a still frame. The message and the box
 * are drawn as the style in force draws them.
 *
 * States: still, live.
 */
@Composable
internal fun KitLiveLookStill(look: String?, modifier: Modifier = Modifier) {
    val ground = LocalKitColors.current.ground
    val plate = rememberAuroraPlate(look, thumbnail = true)
    val alpha = AuroraImage.alphaFor(SAMPLE_STRENGTH)
    val corner = (FlutterKitTokens.ICON_TILE_RADIUS + Space.S1).dp // FG: lib/ui/kit/kit_option_card.dart:766
    val shaderLook = LiveLooks.shader(look)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(ground)
            .drawBehind { plate?.let { (image, filter) -> drawAuroraPlate(image, filter, alpha, AuroraImage.driftAt(0.0)) } }
            .clearAndSetSemantics {}
    ) {
        if (shaderLook != null) LiveSample(shaderLook, Modifier.matchParentSize())
        MockChat(scale = .7f) // FG: lib/ui/kit/kit_option_card.dart:776
    }
}

/** The look's shader at thumbnail size, live only while it is on screen. */
@Composable
private fun LiveSample(look: LiveLook, modifier: Modifier) {
    val clock = LocalLiveClock.current ?: rememberLiveFrameClock()
    var onScreen by remember { mutableStateOf(false) }
    val animates = LocalKitMotion.current.ambientLoops && onScreen
    if (animates) {
        DisposableEffect(clock) {
            clock.acquire()
            onDispose { clock.release() }
        }
    }
    val time = rememberLiveTime(clock, animates)
    LiveShaderLayer(
        look,
        SAMPLE_STRENGTH,
        time,
        modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            onScreen = coordinates.isAttached && bounds.width > 0f && bounds.height > 0f
        },
    )
}

private const val SAMPLE_STRENGTH: Float = 1.6f // FG: lib/ui/kit/kit_option_card.dart:773

/** Dart `liveMockChat` (`live_mock_chat.dart:15-89`): a message, some lines, the message box. */
@Composable
private fun MockChat(scale: Float) {
    val colors = LocalKitColors.current
    val gap = (Space.S2 * scale).dp // FG: lib/ui/kit/live_background/live_mock_chat.dart:18
    Column(Modifier.fillMaxSize().padding((Space.S3 * scale).dp)) { // FG: lib/ui/kit/live_background/live_mock_chat.dart:55
        // A control, not content: the tile is itself a content surface (live_mock_chat.dart:61-68).
        val message =
            kitSurfaceLook(
                KitSurfaceInput(
                    SurfaceRole.Control,
                    KitShapeId.Button,
                    SurfaceLevel.Surface2,
                    ownFill = colors.surface2,
                    radius = (FlutterKitTokens.BUTTON_RADIUS * scale).dp,
                )
            )
        Box(Modifier.align(Alignment.End).then(message.modifier).padding((Space.S2 * scale).dp)) { // FG: lib/ui/kit/live_background/live_mock_chat.dart:66
            MockBar(70f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:67
        }
        Spacer(Modifier.height(gap * 1.5f)) // FG: lib/ui/kit/live_background/live_mock_chat.dart:70
        MockBar(90f, 5f, scale, colors.text3) // FG: lib/ui/kit/live_background/live_mock_chat.dart:71
        Spacer(Modifier.height(gap))
        MockBar(130f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:73
        Spacer(Modifier.height(gap))
        MockBar(110f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:75
        Spacer(Modifier.height(gap))
        MockBar(80f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:77
        Spacer(Modifier.weight(1f))
        val box =
            kitSurfaceLook(
                KitSurfaceInput(
                    SurfaceRole.Floating,
                    KitShapeId.Pill,
                    SurfaceLevel.Surface2,
                    ownFill = colors.surface2,
                    radius = (FlutterKitTokens.COMPOSER_RADIUS * scale).dp,
                )
            )
        Box(
            Modifier.fillMaxWidth()
                .height((Space.S6 * scale).dp) // FG: lib/ui/kit/live_background/live_mock_chat.dart:82
                .then(box.modifier)
                .padding(horizontal = (Space.S3 * scale).dp), // FG: lib/ui/kit/live_background/live_mock_chat.dart:83
            contentAlignment = Alignment.CenterStart,
        ) {
            MockBar(60f, 5f, scale, colors.text3) // FG: lib/ui/kit/live_background/live_mock_chat.dart:85
        }
    }
}

/** A line of words: a pill (`live_mock_chat.dart:20-28`). */
@Composable
private fun MockBar(w: Float, h: Float, scale: Float, color: Color) {
    val radius: Dp = min(h * scale / 2f, 999f).dp // FG: lib/ui/kit/live_background/live_mock_chat.dart:26
    Box(Modifier.width((w * scale).dp).height((h * scale).dp).background(color, RoundedCornerShape(radius)))
}
