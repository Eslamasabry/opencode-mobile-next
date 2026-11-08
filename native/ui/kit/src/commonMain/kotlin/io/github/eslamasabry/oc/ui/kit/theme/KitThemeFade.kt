package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material3.ColorScheme
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The theme change (P-029, M-019): dark to light, a new pack or accent fades over [MS] instead of jumping. The old
 * screen's picture fades out over the new theme ([rememberKitThemeFade]); [lerp] is the colour mix kept for parts
 * that blend two themes.
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

/**
 * M-019: the picture of the screen as it was, laid over the new theme and fading out. The live screen switches to the
 * new colours in one frame underneath it, so every word stays fully readable through the change (a colour lerp left
 * text near the ground's colour at the midpoint) and the parts' own moves (the segmented indicator) still run.
 */
@Stable
internal class KitThemeCover(internal val layer: GraphicsLayer) {
    /** The old screen while it fades out; null at rest. */
    internal var shot: ImageBitmap? by mutableStateOf(null)

    /** The old screen's opacity over the new one: 1 → 0 over [ThemeFade.MS]. */
    internal val alpha: Animatable<Float, AnimationVector1D> = Animatable(0f)

    /** True while the old screen still covers part of the new one. */
    internal val fading: Boolean get() = shot != null
}

/** The colours shown now and the cover that fades the old screen out. */
internal class KitThemeFadeFrame(val colors: KitColors, val cover: KitThemeCover)

/**
 * Follows [target]: the screen as it is now is captured, the new colours apply at once underneath, and the capture
 * fades out over [ThemeFade.MS] (linear, Flutter `AnimatedTheme`). At once when [fades] is false (motion Off, still
 * frames). A change during a fade captures what is on screen then (the half-faded cover included), never jumping back.
 */
@Composable
internal fun rememberKitThemeFade(target: KitColors, fades: Boolean): KitThemeFadeFrame {
    val layer = rememberGraphicsLayer()
    val cover = remember(layer) { KitThemeCover(layer) }
    val shown = remember { mutableStateOf(target) }
    LaunchedEffect(target, fades) {
        if (!fades) {
            cover.shot = null
            shown.value = target
            return@LaunchedEffect
        }
        if (target == shown.value) return@LaunchedEffect
        // Nothing drawn yet (the first frame): nothing to fade from.
        if (layer.size.width == 0 || layer.size.height == 0) {
            shown.value = target
            return@LaunchedEffect
        }
        // The layer still holds the last frame drawn, in the old colours.
        val shot = layer.toImageBitmap()
        cover.alpha.snapTo(1f)
        cover.shot = shot
        shown.value = target
        // P-116: the first frame in the new colours recomposes the parts that read them (it measured ~95 ms on the
        // emulator when every part recomposed); the fade's clock starts once that frame is under way, so all of MS is
        // seen as motion. The gap to the next frame is that frame's cost, kept for KitThemeMetrics.
        val first = withFrameNanos { it }
        coroutineScope {
            launch { KitThemeMetrics.record((withFrameNanos { it } - first) / 1_000_000f) }
            cover.alpha.animateTo(0f, tween(ThemeFade.MS, easing = LinearEasing))
        }
        cover.shot = null
    }
    // Motion Off and still frames show the new colours on the same frame.
    return KitThemeFadeFrame(if (fades) shown.value else target, cover)
}

/**
 * Draws the theme's content through [cover]'s layer (so the last frame can be captured) and the fading old screen
 * over it. The cover is drawn inside the layer, so a capture taken mid-fade holds what was really on screen.
 */
internal fun Modifier.kitThemeCover(cover: KitThemeCover): Modifier = drawWithContent {
    val content = this
    cover.layer.record {
        content.drawContent()
        val shot = cover.shot
        if (shot != null) drawImage(shot, alpha = cover.alpha.value.coerceIn(0f, 1f))
    }
    drawLayer(cover.layer)
}

/**
 * The theme-change frame meter (theme-switch-premium Step 0): after each faded theme change it holds how long the frame
 * that first drew the new colours took (the gap between that frame and the next, so a 16 ms frame on a 60 Hz screen is
 * on time). Debug builds and benchmarks read [lastChangeFrameMs] or set [listener]; the theme never reads it.
 */
public object KitThemeMetrics {
    /** The last theme change's first-frame time in ms; null before the first faded change. */
    public var lastChangeFrameMs: Float? = null
        private set

    /** Called with each change's first-frame time (ms). Set by a debug screen or a benchmark; null by default. */
    public var listener: ((Float) -> Unit)? = null

    internal fun record(ms: Float) {
        lastChangeFrameMs = ms
        listener?.invoke(ms)
    }
}

/**
 * Material's colour scheme, held back after a change until a theme switch's motion has finished
 * ([ThemeSwitchTimings.IDLE_MS] after the tap, so [ThemeSwitchTimings.IDLE_MS] − [ThemeSwitchTimings.COMMIT_MS] after
 * the commit). Material keeps it in a static local, so its swap recomposes every part under the theme; held back, that
 * frame lands when nothing moves. The kit's own colours are not held back. A newer change restarts the wait (the latest
 * wins). At once when [defers] is false (motion Off, still frames) and on the first frame.
 */
@Composable
internal fun rememberSettledScheme(scheme: ColorScheme, defers: Boolean): ColorScheme {
    val held = remember { mutableStateOf(scheme) }
    LaunchedEffect(scheme, defers) {
        if (defers && held.value !== scheme) delay(SCHEME_SETTLE_MS)
        held.value = scheme
    }
    return if (defers) held.value else scheme
}

/** How long Material's scheme is held back after a theme change. */
internal const val SCHEME_SETTLE_MS: Long = ThemeSwitchTimings.IDLE_MS - ThemeSwitchTimings.COMMIT_MS
