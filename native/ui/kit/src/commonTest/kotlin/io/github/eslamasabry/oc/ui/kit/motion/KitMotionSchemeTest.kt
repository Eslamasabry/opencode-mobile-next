package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Motion lead 2026-11-07: one spring scheme for kit parts and the M3 parts the kit wraps. */
class KitMotionSchemeTest {
    private fun specs(level: MotionLevel, scale: Float = 1f) = KitMotionSpecs(KitMotionState(level, debugTimeScale = scale))

    private fun <T> spring(spec: Any?): SpringSpec<T> {
        assertIs<SpringSpec<*>>(spec)
        @Suppress("UNCHECKED_CAST")
        return spec as SpringSpec<T>
    }

    @Test
    fun `MOT-SCHEME spatial springs are fast, default and slow, all damped so nothing overshoots`() {
        val full = specs(MotionLevel.Full)
        val fast = spring<Float>(full.spatialFast<Float>())
        val default = spring<Float>(full.spatial<Float>())
        val slow = spring<Float>(full.spatialSlow<Float>())
        val placement = spring<Float>(full.placement<Float>())
        assertTrue(fast.stiffness > default.stiffness && default.stiffness > slow.stiffness)
        listOf(fast, default, slow, placement).forEach {
            assertEquals(MotionTokens.SPATIAL_ZETA, it.dampingRatio)
            assertTrue(it.dampingRatio >= 0.9f, "a spatial spring never visibly overshoots")
        }
    }

    @Test
    fun `MOT-SCHEME effects springs are critically damped`() {
        val full = specs(MotionLevel.Full)
        listOf(full.effectsFast<Float>(), full.effects<Float>(), full.effectsSlow<Float>()).forEach {
            assertEquals(1f, spring<Float>(it).dampingRatio)
        }
        assertEquals(MotionTokens.EFFECTS_FAST_K, spring<Float>(full.effectsFast<Float>()).stiffness)
    }

    @Test
    fun `MOT-SCHEME only a released gesture may overshoot, and only slightly`() {
        val release = spring<Float>(specs(MotionLevel.Full).release<Float>())
        assertTrue(release.dampingRatio < 1f && release.dampingRatio >= 0.8f)
    }

    @Test
    fun `MOT-SCHEME Calm keeps the springs (Calm decides what travels, not how fast)`() {
        val calm = specs(MotionLevel.Calm)
        assertIs<SpringSpec<Float>>(calm.spatial<Float>())
        assertIs<SpringSpec<Float>>(calm.effects<Float>())
    }

    @Test
    fun `MOT-SCHEME reduced motion snaps every spec, kit and M3 scheme alike`() {
        val off = specs(MotionLevel.Off)
        listOf(
            off.spatialFast<Float>(), off.spatial<Float>(), off.spatialSlow<Float>(), off.placement<Float>(),
            off.effectsFast<Float>(), off.effects<Float>(), off.effectsSlow<Float>(), off.release<Float>(),
            off.largeTravel<Float>(), off.enter<Float>(), off.quick<Float>(),
        ).forEach { assertIs<SnapSpec<Float>>(it) }
        val scheme = KitMotionScheme(KitMotionState(MotionLevel.Off))
        listOf(
            scheme.defaultSpatialSpec<Float>(), scheme.fastSpatialSpec<Float>(), scheme.slowSpatialSpec<Float>(),
            scheme.defaultEffectsSpec<Float>(), scheme.fastEffectsSpec<Float>(), scheme.slowEffectsSpec<Float>(),
        ).forEach { assertIs<SnapSpec<Float>>(it) }
    }

    @Test
    fun `MOT-SCHEME the M3 scheme is the kit's own springs`() {
        val full = specs(MotionLevel.Full)
        val scheme = KitMotionScheme(KitMotionState(MotionLevel.Full))
        assertEquals(spring<Float>(full.spatial<Float>()).stiffness, spring<Float>(scheme.defaultSpatialSpec<Float>()).stiffness)
        assertEquals(spring<Float>(full.spatialFast<Float>()).stiffness, spring<Float>(scheme.fastSpatialSpec<Float>()).stiffness)
        assertEquals(spring<Float>(full.spatialSlow<Float>()).stiffness, spring<Float>(scheme.slowSpatialSpec<Float>()).stiffness)
        assertEquals(spring<Float>(full.effects<Float>()).stiffness, spring<Float>(scheme.defaultEffectsSpec<Float>()).stiffness)
    }

    @Test
    fun `MOT-SCHEME the review slow-down stretches springs as it stretches tweens`() {
        val slowed = specs(MotionLevel.Full, scale = 2f)
        assertEquals(MotionTokens.SPATIAL_K / 4f, spring<Float>(slowed.spatial<Float>()).stiffness)
        assertEquals(MotionTokens.STANDARD * 2, (slowed.enter<Float>() as TweenSpec).durationMillis)
    }
}
