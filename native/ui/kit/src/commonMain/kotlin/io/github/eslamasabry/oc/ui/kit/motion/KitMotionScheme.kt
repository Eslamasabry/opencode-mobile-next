package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens

/**
 * The standard M3 numbers, snapping under Off (plan 08 §2.7, §3.4 mechanism 2), so the M3 parts the kit wraps (sheet,
 * switch, pull-to-refresh state) obey the in-app Off too. `KitTheme` (a part-A file) passes it to
 * `MaterialTheme(motionScheme = …)`; until its owner wires it, [KitRefresh] and the S2-KITB parts take their specs from
 * `KitMotionSpecs` directly and do not depend on it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
public class KitMotionScheme(private val off: Boolean) : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        if (off) snap() else spring(MotionTokens.SPATIAL_ZETA, MotionTokens.SPATIAL_K)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        if (off) snap() else spring(MotionTokens.SPATIAL_ZETA, MotionTokens.SPATIAL_FAST_K)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        if (off) snap() else spring(MotionTokens.SPATIAL_ZETA, MotionTokens.SPATIAL_SLOW_K)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(1f, MotionTokens.EFFECTS_K)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(1f, MotionTokens.EFFECTS_FAST_K)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(1f, MotionTokens.EFFECTS_SLOW_K)
}
