package io.github.eslamasabry.oc.engine.runtime.heat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port of test/thermal_guard_test.dart group ThermalPolicy (the rules only; the team guard is :engine:team, D6). */
class ThermalPolicyTest {
    private val t0 = Instant.parse("2026-09-26T14:00:00Z")
    private val none = ThermalReading(ThermalStatus.None)
    private val moderate = ThermalReading(ThermalStatus.Moderate)
    private val severe = ThermalReading(ThermalStatus.Severe)
    private val critical = ThermalReading(ThermalStatus.Critical)

    @Test
    fun `B-ANDR-083 pauses at SEVERE, stops at CRITICAL and above`() {
        val policy = ThermalPolicy()
        for (status in listOf(ThermalStatus.Unknown, ThermalStatus.None, ThermalStatus.Light, ThermalStatus.Moderate)) {
            assertEquals(ThermalAction.None, policy.decide(ThermalReading(status), t0, enabled = true), status.name)
        }
        assertEquals(ThermalAction.Pause, policy.decide(severe, t0, enabled = true))
        for (status in listOf(ThermalStatus.Critical, ThermalStatus.Emergency, ThermalStatus.Shutdown)) {
            assertEquals(ThermalAction.Stop, policy.decide(ThermalReading(status), t0, enabled = true), status.name)
        }
    }

    @Test
    fun `B-ANDR-083 an early forecast of 0_95 or more pauses before SEVERE`() {
        val policy = ThermalPolicy()
        assertEquals(ThermalAction.None, policy.decide(ThermalReading(ThermalStatus.Light, 0.94), t0, enabled = true))
        assertEquals(ThermalAction.Pause, policy.decide(ThermalReading(ThermalStatus.Light, 0.95), t0, enabled = true))
    }

    @Test
    fun `B-ANDR-083 a paused team escalates to a stop at CRITICAL`() {
        val policy = ThermalPolicy().apply { applied(ThermalAction.Pause) }
        assertEquals(ThermalAction.None, policy.decide(severe, t0, enabled = true))
        assertEquals(ThermalAction.Stop, policy.decide(critical, t0, enabled = true))
        policy.applied(ThermalAction.Stop)
        assertEquals(ThermalAction.None, policy.decide(critical, t0, enabled = true))
    }

    @Test
    fun `B-ANDR-084 resumes only after two cool minutes and heat restarts the wait`() {
        val policy = ThermalPolicy().apply { applied(ThermalAction.Pause) }
        fun at(s: Int) = t0 + s.seconds
        assertEquals(ThermalAction.None, policy.decide(moderate, at(0), enabled = true))
        assertEquals(ThermalAction.None, policy.decide(moderate, at(119), enabled = true))
        // Hot again for a moment: the two minutes start over.
        assertEquals(ThermalAction.None, policy.decide(severe, at(120), enabled = true))
        assertEquals(ThermalAction.None, policy.decide(none, at(150), enabled = true))
        assertEquals(ThermalAction.None, policy.decide(none, at(269), enabled = true))
        assertEquals(ThermalAction.Resume, policy.decide(none, at(270), enabled = true))
        assertEquals(t0 + 270.seconds, policy.resumeAt)
    }

    @Test
    fun `B-ANDR-085 turned off it starts nothing but still gives back what it holds`() {
        val off = ThermalPolicy()
        assertEquals(ThermalAction.None, off.decide(critical, t0, enabled = false))
        val holding = ThermalPolicy().apply { applied(ThermalAction.Pause) }
        assertEquals(ThermalAction.None, holding.decide(critical, t0, enabled = false))
        holding.decide(none, t0, enabled = false)
        assertEquals(ThermalAction.Resume, holding.decide(none, t0 + 2.minutes, enabled = false))
    }

    @Test
    fun `B-ANDR-129 hot means SEVERE or a forecast of 0_95, very hot means CRITICAL`() {
        assertEquals(true, ThermalPolicy.hot(severe))
        assertEquals(true, ThermalPolicy.hot(ThermalReading(ThermalStatus.Moderate, 0.97)))
        assertEquals(false, ThermalPolicy.hot(moderate))
        assertEquals(false, ThermalPolicy.veryHot(severe))
        assertEquals(true, ThermalPolicy.veryHot(critical))
    }
}
