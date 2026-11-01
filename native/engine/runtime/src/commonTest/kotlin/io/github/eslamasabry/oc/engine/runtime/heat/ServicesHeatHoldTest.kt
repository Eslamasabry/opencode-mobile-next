package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Port of test/linux_services_test.dart group heat (the hold controller; the file itself is the binding's). */
class ServicesHeatHoldTest {
    private val critical = ThermalReading(ThermalStatus.Critical)
    private val severe = ThermalReading(ThermalStatus.Severe)
    private val cool = ThermalReading(ThermalStatus.Light)

    @Test
    fun `B-LINUX-261 critical holds the services, severe alone does not, and cool for the wait gives them back`() =
        runTest {
            val clock = TestClock()
            val holds = mutableListOf<Boolean>()
            var enabled = true
            val heat =
                ServicesHeatHold(
                    readHold = { false },
                    setHold = { holds += it; Unit.ok() },
                    enabled = { enabled },
                    clock = clock,
                )
            heat.observe(severe)
            assertEquals(emptyList(), holds, "severe pauses the team, not services")
            heat.observe(critical)
            assertEquals(listOf(true), holds)
            heat.observe(cool)
            clock.advance(1.minutes)
            heat.observe(severe) // hot again: the wait starts over
            clock.advance(1.minutes)
            heat.observe(cool)
            clock.advance(1.minutes + 59.seconds)
            heat.observe(cool)
            assertEquals(listOf(true), holds)
            clock.advance(1.seconds)
            heat.observe(cool)
            assertEquals(listOf(true, false), holds)
            enabled = false
            heat.observe(critical)
            assertEquals(listOf(true, false), holds, "off: nothing new is held")
        }

    @Test
    fun `B-LINUX-261 a hold left by an earlier process is given back once cool even with the guard off`() = runTest {
        val clock = TestClock()
        val holds = mutableListOf<Boolean>()
        val heat = ServicesHeatHold(readHold = { true }, setHold = { holds += it; Unit.ok() }, enabled = { false }, clock = clock)
        heat.observe(cool)
        clock.advance(2.minutes)
        heat.observe(cool)
        assertEquals(listOf(false), holds)
        assertEquals(false, heat.held)
    }
}
