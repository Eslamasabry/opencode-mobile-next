package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.lerp

/**
 * The theme change (P-029): dark to light, a new pack or accent fades over [MS] instead of jumping, and every colour
 * moves on the one clock, so a part's own colour easing does not add its tail on top (it measured 270 ms).
 */
public object ThemeFade {
    /** Flutter's theme change (`kThemeAnimationDuration`). */
    public const val MS: Int = 200

    /** The colours [t] of the way from [from] to [to] (0 … 1); dark-or-light follows the nearer end. */
    public fun lerp(from: KitColors, to: KitColors, t: Float): KitColors =
        when {
            t <= 0f -> from
            t >= 1f -> to
            else -> mix(from, to, t)
        }

    private fun mix(from: KitColors, to: KitColors, t: Float): KitColors {
        fun c(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color) = lerp(a, b, t)
        return KitColors(
            dark = if (t < 0.5f) from.dark else to.dark,
            ground = c(from.ground, to.ground),
            surface1 = c(from.surface1, to.surface1),
            surface2 = c(from.surface2, to.surface2),
            surface3 = c(from.surface3, to.surface3),
            hairline = c(from.hairline, to.hairline),
            text1 = c(from.text1, to.text1),
            text2 = c(from.text2, to.text2),
            text3 = c(from.text3, to.text3),
            accent = c(from.accent, to.accent),
            onAccent = c(from.onAccent, to.onAccent),
            partner = c(from.partner, to.partner),
            onPartner = c(from.onPartner, to.onPartner),
            attention = c(from.attention, to.attention),
            attentionFill = c(from.attentionFill, to.attentionFill),
            onAttentionFill = c(from.onAttentionFill, to.onAttentionFill),
            attentionSurface = c(from.attentionSurface, to.attentionSurface),
            attentionLine = c(from.attentionLine, to.attentionLine),
            success = c(from.success, to.success),
            scrim = c(from.scrim, to.scrim),
            codeKeyword = c(from.codeKeyword, to.codeKeyword),
            codeString = c(from.codeString, to.codeString),
            codeType = c(from.codeType, to.codeType),
            elevationShadow = c(from.elevationShadow, to.elevationShadow),
        )
    }
}

/** The colours shown now and whether a theme fade is running. */
internal class KitThemeFadeFrame(val colors: KitColors, val fading: Boolean)

/**
 * Follows [target] through a [ThemeFade.MS] linear fade; at once when [fades] is false (motion Off, still frames). A
 * change during a fade starts from the colours on screen, never jumping back.
 */
@Composable
internal fun rememberKitThemeFade(target: KitColors, fades: Boolean): KitThemeFadeFrame {
    val from = remember { mutableStateOf(target) }
    val to = remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, fades) {
        if (target == to.value) return@LaunchedEffect
        from.value = ThemeFade.lerp(from.value, to.value, progress.value)
        to.value = target
        if (!fades) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        // P-116: the first fading frame recomposes every part in the new colours' path (it measured ~95 ms of a
        // 200 ms fade left on the emulator). The fade's clock starts once that frame is under way, so all of MS is
        // seen as motion instead of being spent on one long first frame.
        withFrameNanos {}
        progress.animateTo(1f, tween(ThemeFade.MS, easing = LinearEasing))
    }
    // Motion Off and still frames show the new colours on the same frame.
    if (!fades) return KitThemeFadeFrame(target, fading = false)
    val t = progress.value
    // Until the effect has started the fade, keep the colours on screen (one frame), not the new ones.
    return if (t >= 1f) KitThemeFadeFrame(to.value, fading = false)
    else KitThemeFadeFrame(ThemeFade.lerp(from.value, to.value, t), fading = true)
}
