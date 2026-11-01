package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.eslamasabry.oc.ui.design.tokens.Alphas

/** How far [kitDim] dims (Dart `KitDimLevel`). */
public enum class KitDimLevel {
    /** Last-known content while a refresh is pending (0.60); the host pairs it with a stale word. */
    Stale,

    /** An image or drawing for an unavailable choice (0.38). */
    Disabled,
}

/** The alpha [kitDim] draws at. */
public fun kitDimAlpha(dimmed: Boolean, level: KitDimLevel): Float =
    when {
        !dimmed -> 1f
        level == KitDimLevel.Stale -> Alphas.STALE
        else -> Alphas.DISABLED
    }

/**
 * Dims an image, a drawing or a mark (Dart `KitDim`, `Opacity`'s job). Never on text: text at rest is opaque, and stale
 * or disabled text uses a tone instead (LOOK-14; Konsist bans `kitDim` on `KitText` call sites). Changes no semantics:
 * the host says why something is dimmed.
 *
 * Motion: alpha tween [pace] (QUICK); Calm = same; Off = instant (class Fade)
 * Replaces: KitDim (lib/ui/kit/motion/kit_motion_parts.dart)
 */
@Composable
public fun Modifier.kitDim(dimmed: Boolean = true, level: KitDimLevel = KitDimLevel.Disabled, pace: KitPace = KitPace.Quick): Modifier {
    val target = kitDimAlpha(dimmed, level)
    if (KitMotion.state.reduced) return this.graphicsLayer { alpha = target }
    val shown by animateFloatAsState(target, KitMotion.specs.pace(pace), label = "kit.dim")
    return this.graphicsLayer { alpha = shown }
}
