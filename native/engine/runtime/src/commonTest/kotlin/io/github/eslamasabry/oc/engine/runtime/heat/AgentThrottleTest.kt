package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Port of test/heat_tier_test.dart group AgentThrottle. */
class AgentThrottleTest {
    private fun r(status: ThermalStatus, headroom: Double? = null) = ThermalReading(status, headroom)

    private class Rig {
        val clock = TestClock()
        val calls = mutableListOf<AgentPriorityLevel>()
        var battery = BatteryReading(level = 80, charging = true)
        var full = false
        var saver = false
        var hidden = false
        var answer: Outcome<Int> = 4.ok()
        val throttle =
            AgentThrottle(
                fullSpeed = { full },
                setPriority = { level ->
                    calls += level
                    answer
                },
                battery = { battery },
                powerSave = { saver },
                hidden = { hidden },
                clock = clock,
            )
    }

    @Test
    fun `B-ANDR-130 lowers priority on a rising forecast and restores only after two calm minutes`() = runTest {
        val rig = Rig()
        val t = rig.throttle
        t.observe(r(ThermalStatus.None, 0.8))
        assertEquals(listOf(AgentPriorityLevel.Slow), rig.calls)
        assertEquals(HeatTier.Slow, t.tier.value)
        assertEquals(10, AgentPriorityLevel.Slow.nice)

        // A cooler reading alone is not enough: calm has to last.
        rig.clock.advance(30.seconds)
        t.observe(r(ThermalStatus.None, 0.3))
        assertEquals(HeatTier.Slow, t.tier.value)
        assertEquals(listOf(AgentPriorityLevel.Slow), rig.calls)

        // Still too early, a warm reading restarts the wait.
        rig.clock.advance(60.seconds)
        t.observe(r(ThermalStatus.None, 0.8))
        rig.clock.advance(90.seconds)
        t.observe(r(ThermalStatus.None, 0.3))
        assertEquals(HeatTier.Slow, t.tier.value, "no flapping")

        rig.clock.advance(HeatTierPolicy.calmFor + 1.seconds)
        t.observe(r(ThermalStatus.None, 0.3))
        assertEquals(HeatTier.Normal, t.tier.value)
        assertEquals(AgentPriorityLevel.Normal, rig.calls.last())
    }

    @Test
    fun `B-ANDR-130 battery saver slows the agents`() = runTest {
        val rig = Rig()
        rig.saver = true
        rig.throttle.observe(r(ThermalStatus.None, 0.1))
        assertEquals(HeatTier.Slow, rig.throttle.tier.value)
    }

    @Test
    fun `B-ANDR-130 goes up at once, to hold`() = runTest {
        val rig = Rig()
        rig.throttle.observe(r(ThermalStatus.None, 0.8))
        rig.throttle.observe(r(ThermalStatus.Moderate, 0.95))
        assertEquals(HeatTier.Hold, rig.throttle.tier.value)
        assertEquals(listOf(AgentPriorityLevel.Slow, AgentPriorityLevel.Hold), rig.calls)
        assertEquals(15, AgentPriorityLevel.Hold.nice)
    }

    @Test
    fun `B-ANDR-130 going out of sight slows and coming back restores after calm`() = runTest {
        val rig = Rig()
        val t = rig.throttle
        t.observe(r(ThermalStatus.None, 0.2))
        assertEquals(emptyList(), rig.calls)
        rig.hidden = true
        t.evaluate()
        assertEquals(HeatTier.Slow, t.tier.value)
        rig.hidden = false
        t.evaluate() // calm starts now
        rig.clock.advance(HeatTierPolicy.calmFor + 1.seconds)
        t.evaluate()
        assertEquals(HeatTier.Normal, t.tier.value)
        assertEquals(listOf(AgentPriorityLevel.Slow, AgentPriorityLevel.Normal), rig.calls)
    }

    @Test
    fun `B-ANDR-130 Always full speed restores at once and stays normal`() = runTest {
        val rig = Rig()
        rig.throttle.observe(r(ThermalStatus.None, 0.8))
        rig.full = true
        rig.throttle.evaluate()
        assertEquals(HeatTier.Normal, rig.throttle.tier.value)
        assertEquals(AgentPriorityLevel.Normal, rig.calls.last())
    }

    @Test
    fun `B-ANDR-130 a failed lowering is tried again and is applied again every 5 minutes, not on every reading`() =
        runTest {
            val rig = Rig()
            val t = rig.throttle
            rig.answer = Failure.Device().failed()
            t.observe(r(ThermalStatus.None, 0.8))
            rig.answer = 0.ok() // no helper was running yet
            t.observe(r(ThermalStatus.None, 0.8))
            assertEquals(2, rig.calls.size)
            // Applied, but set again after five minutes for helpers started since.
            rig.clock.advance(AgentThrottle.reapplyEvery + 1.seconds)
            t.observe(r(ThermalStatus.None, 0.8))
            assertEquals(3, rig.calls.size)
            t.observe(r(ThermalStatus.None, 0.8))
            assertEquals(3, rig.calls.size, "not on every reading")
        }
}
