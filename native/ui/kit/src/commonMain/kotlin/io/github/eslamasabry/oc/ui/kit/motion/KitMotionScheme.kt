package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * The kit's motion scheme handed to `MaterialTheme(motionScheme = …)` by `KitTheme` (plan 08 §2.7, §3.4 mechanism 2),
 * so the M3 parts the kit wraps (sheet, switch, pull-to-refresh, menus) move on the same springs as kit parts and snap
 * under Off. Every spec comes from [KitMotionSpecs]: one scheme for the whole app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
public class KitMotionScheme(private val specs: KitMotionSpecs) : MotionScheme {
    public constructor(state: KitMotionState) : this(KitMotionSpecs(state))

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = specs.spatial()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = specs.spatialFast()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = specs.spatialSlow()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = specs.effects()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = specs.effectsFast()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = specs.effectsSlow()
}
