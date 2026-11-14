package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.ceil

/*
 * E20-S30 (owner 2026-11-07: "Big gap not required … why not translucent for nice looks?"): the chat's bottom chrome
 * (the composer's strip, the composer's face and the "Jump to latest" pill) is a translucent veil over the transcript,
 * which scrolls under it, instead of an opaque band. It reuses the kit's frost source ([KitFrostState], recorded by
 * [kitFrostSource]) and the platform blur ([io.github.eslamasabry.oc.ui.kit.platform.KitPlatform.frostEffect], API 31+)
 * rather than a new library: the transcript is already recorded once per frame for the Glass composer, so the veil
 * costs one more blurred draw of that recording and no new dependency.
 */

/** How a veil draws (see [ChromeVeilPolicy.veil]). */
public enum class VeilLook {
    /** The content behind, blurred, under the tint at [ChromeVeil.tintAlpha]. */
    Blur,

    /** No blur available or wanted: the tint alone at [ChromeVeilPolicy.FALLBACK_TINT] over the sharp content. */
    Tint,

    /** High contrast: the tint, opaque. Nothing behind shows through. */
    Opaque,
}

/** One veil: how it draws, the blur's sigma (dp) and the tint's alpha. [faceAlpha] is the alpha a face on it keeps. */
@Immutable
public data class ChromeVeil(val look: VeilLook, val sigmaDp: Float, val tintAlpha: Float) {
    /** A surface lying on this veil (the composer's face) is this translucent; opaque when the veil is. */
    public val faceAlpha: Float get() = if (look == VeilLook.Opaque) 1f else tintAlpha
}

/** Pure rules for [ChromeVeil] (unit-tested; no Compose). */
public object ChromeVeilPolicy {
    /** The tint over the blur: the surface colour at 76 % (story: 70–80 %). */
    public const val BLUR_TINT: Float = 0.76f

    /** Below API 31 (or the frost strength Off, or Saver): a plain 92 % tint over the sharp content, no blur. */
    public const val FALLBACK_TINT: Float = 0.92f

    /** The blur's sigma in dp: about a 22 dp blur radius (story: 20–24 dp; radius ≈ sigma / 0.577). */
    public const val SIGMA_DP: Float = 12.5f

    /** The blur is sampled at this fraction of the screen's resolution: a quarter of the pixels, the same look. */
    public const val SAMPLE_SCALE: Float = 0.5f

    /**
     * The veil for the person's settings. [blurAvailable] is whether the platform has a backdrop blur (Android API 31+).
     * High contrast holds it opaque; Saver and frost strength Off keep the translucent tint without the GPU blur.
     */
    public fun veil(highContrast: Boolean, saver: Boolean, strength: FrostStrength, blurAvailable: Boolean): ChromeVeil =
        when {
            highContrast -> ChromeVeil(VeilLook.Opaque, 0f, 1f)
            saver || strength == FrostStrength.Off || !blurAvailable -> ChromeVeil(VeilLook.Tint, 0f, FALLBACK_TINT)
            else -> ChromeVeil(VeilLook.Blur, SIGMA_DP, BLUR_TINT)
        }
}

/**
 * The veil a part lies on (provided by `KitScreen` around its bottom chrome while it veils), so the composer's face
 * turns translucent with it. Null: the part keeps its opaque face.
 */
public val LocalKitChromeVeil: ProvidableCompositionLocal<ChromeVeil?> = compositionLocalOf { null }

/** The veil for this screen now (settings, platform blur). */
@Composable
public fun rememberKitChromeVeil(): ChromeVeil {
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val signals by platform.signals.collectAsState()
    val density = LocalDensity.current.density
    val blurs = remember(platform, density) { platform.frostEffect(FrostSpec(1f, 1f, 0f), density) != null }
    return remember(signals.highContrast, appearance.saver, appearance.frost, blurs) {
        ChromeVeilPolicy.veil(signals.highContrast, appearance.saver, appearance.frost, blurs)
    }
}

/**
 * Draws [veil] behind this part, inside [shape]: what [source] recorded behind it, blurred at half resolution, then
 * [tint] at the veil's alpha, then an optional [topEdge] hairline. Under [VeilLook.Tint] / [VeilLook.Opaque] it is the
 * tint alone. Content is drawn on top as usual.
 */
@Composable
public fun Modifier.kitBackdropVeil(
    source: KitFrostState,
    veil: ChromeVeil,
    tint: Color,
    shape: Shape = RectangleShape,
    topEdge: Color? = null,
): Modifier {
    val platform = LocalKitPlatform.current
    val density = LocalDensity.current.density
    val ground = LocalKitColors.current.ground
    // Sampled at half resolution: the effect's radius is in the small layer's pixels, so it is made at half density.
    val effect =
        remember(veil, density, platform) {
            if (veil.look != VeilLook.Blur) null
            else platform.frostEffect(FrostSpec(veil.sigmaDp, 1f, 0f), density * ChromeVeilPolicy.SAMPLE_SCALE)
        }
    val blur = rememberGraphicsLayer()
    val origin = remember { arrayOf(Offset.Zero) }
    return this.onGloballyPositioned { origin[0] = it.positionInRoot() }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            if (effect == null) {
                val alpha = if (veil.look == VeilLook.Opaque) 1f else veil.tintAlpha
                drawOutline(outline, tint.copy(alpha = tint.alpha * alpha))
            } else {
                clipPath(Path().apply { addOutline(outline) }) {
                    // The recording has no ground of its own (the screen paints it): lay the opaque ground first, so
                    // only the blur shows, never the sharp words under this part.
                    drawRect(ground.copy(alpha = 1f))
                    val s = ChromeVeilPolicy.SAMPLE_SCALE
                    val small = IntSize(ceil(size.width * s).toInt().coerceAtLeast(1), ceil(size.height * s).toInt().coerceAtLeast(1))
                    val shift = source.sourceOrigin - origin[0]
                    blur.renderEffect = effect
                    blur.kitRecord(Density(density * s, fontScale), layoutDirection, small) {
                        scale(s, s, pivot = Offset.Zero) { translate(shift.x, shift.y) { kitDrawLayer(source.layer) } }
                    }
                    scale(1f / s, 1f / s, pivot = Offset.Zero) { kitDrawLayer(blur) }
                    drawRect(tint.copy(alpha = tint.alpha * veil.tintAlpha))
                }
            }
            topEdge?.let { drawLine(it, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) }
        }
}
