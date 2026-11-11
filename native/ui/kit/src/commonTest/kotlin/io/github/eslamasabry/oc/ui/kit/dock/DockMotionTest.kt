package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dock's springs against the liquid-glass motion rule: liquid levels, it does not ring. One overshoot along the
 * travel at most, the failure's shake two swings at most, and every spec snaps by itself under reduced motion.
 */
class DockMotionTest {
    private val all: List<Pair<String, (Boolean) -> AnimationSpec<Float>>> =
        listOf(
            "drop" to { r -> DockSprings.drop(r) },
            "fill" to { r -> DockSprings.fill(r) },
            "close" to { r -> DockSprings.close(r) },
            "follow" to { r -> DockSprings.follow(r) },
            "look" to { r -> DockSprings.look(r) },
            "timed" to { r -> DockSprings.timed(r, 400) },
            "arc" to { r -> DockSprings.arc(r) },
            "kick" to { r -> DockSprings.kick(r) },
            "pop" to { r -> DockSprings.pop(r) },
            "shake" to { r -> DockSprings.shake(r) },
        )

    @Test
    fun `every spec snaps by itself under reduced motion`() {
        for ((name, spec) in all) {
            assertTrue(spec(true) is SnapSpec<*>, "$name snaps under reduced motion")
            assertTrue(spec(false) !is SnapSpec<*>, "$name moves otherwise")
        }
    }

    private fun damping(spec: AnimationSpec<Float>): Float = (spec as SpringSpec<Float>).dampingRatio

    @Test
    fun `nothing rings - one overshoot at most, the shake two swings at most`() {
        // One small overshoot along the travel: the drop's 0.62 (about 8 %).
        assertEquals(0.62f, damping(DockSprings.drop(false)), 0.001f)
        // The ring's own accents overshoot once at most.
        for (spec in listOf(DockSprings.arc<Float>(false), DockSprings.kick(false), DockSprings.pop(false))) {
            assertTrue(damping(spec) >= 0.6f, "damping ${damping(spec)}")
        }
        // Calm fills and closes do not overshoot to the eye.
        assertTrue(damping(DockSprings.fill(false)) >= 0.9f)
        assertTrue(damping(DockSprings.close(false)) >= 1f)
        // The shake: each swing keeps exp(-pi z / sqrt(1 - z^2)) of the last; the third must be under 5 % of the first.
        val z = damping(DockSprings.shake(false)).toDouble()
        val keep = kotlin.math.exp(-kotlin.math.PI * z / kotlin.math.sqrt(1 - z * z))
        assertTrue(keep * keep < 0.05, "the shake's third swing keeps ${keep * keep} of the first")
    }
}
