package io.github.eslamasabry.oc.engine.runtime.heat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/heat_tier_test.dart group HeatTierPolicy and the reading parsers of lib/platform/thermal.dart. */
class HeatTierPolicyTest {
    private fun r(status: ThermalStatus, headroom: Double? = null) = ThermalReading(status, headroom)

    @Test
    fun `B-ANDR-128 a cool phone with a good battery runs at full speed`() {
        assertEquals(
            HeatTier.Normal,
            HeatTierPolicy.decide(r(ThermalStatus.None, 0.3), batteryLevel = 80, charging = false),
        )
    }

    @Test
    fun `B-ANDR-128 a rising forecast slows the agents before any pause`() {
        val tier = HeatTierPolicy.decide(r(ThermalStatus.None, 0.75))
        assertEquals(HeatTier.Slow, tier)
        assertTrue(tier.slowsAgents)
        assertFalse(tier.holdsNewWork)
    }

    @Test
    fun `B-ANDR-128 a light status slows the agents`() {
        assertEquals(HeatTier.Slow, HeatTierPolicy.decide(r(ThermalStatus.Light)))
    }

    @Test
    fun `B-ANDR-128 a near-severe forecast or moderate status also holds new work`() {
        assertEquals(HeatTier.Hold, HeatTierPolicy.decide(r(ThermalStatus.None, 0.92)))
        assertEquals(HeatTier.Hold, HeatTierPolicy.decide(r(ThermalStatus.Moderate)))
        assertTrue(HeatTier.Hold.holdsNewWork)
    }

    @Test
    fun `B-ANDR-128 low battery counts only when unplugged, and an unknown charging state is not guessed`() {
        assertEquals(HeatTier.Slow, HeatTierPolicy.decide(r(ThermalStatus.None), batteryLevel = 18, charging = false))
        assertEquals(HeatTier.Normal, HeatTierPolicy.decide(r(ThermalStatus.None), batteryLevel = 18, charging = true))
        assertEquals(HeatTier.Hold, HeatTierPolicy.decide(r(ThermalStatus.None), batteryLevel = 8, charging = false))
        assertEquals(HeatTier.Normal, HeatTierPolicy.decide(r(ThermalStatus.None), batteryLevel = 8))
    }

    @Test
    fun `B-ANDR-128 battery saver and the app out of sight slow the tools`() {
        assertEquals(HeatTier.Slow, HeatTierPolicy.decide(r(ThermalStatus.None), powerSave = true))
        assertEquals(HeatTier.Slow, HeatTierPolicy.decide(r(ThermalStatus.None), hidden = true))
    }

    @Test
    fun `B-ANDR-128 Always full speed turns every step off`() {
        assertEquals(
            HeatTier.Normal,
            HeatTierPolicy.decide(
                r(ThermalStatus.Moderate, 0.95),
                batteryLevel = 5,
                charging = false,
                hidden = true,
                fullSpeed = true,
                powerSave = true,
            ),
        )
    }

    @Test
    fun `B-ANDR-093 native thermal words map to statuses and a missing forecast is null`() {
        assertEquals(ThermalStatus.Severe, ThermalStatus.parse("severe"))
        assertEquals(ThermalStatus.Shutdown, ThermalStatus.parse("shutdown"))
        assertEquals(ThermalStatus.Unknown, ThermalStatus.parse("toasty"))
        assertEquals(ThermalStatus.Unknown, ThermalStatus.parse(null))
        assertNull(ThermalReading.of(ThermalStatus.None, Double.NaN).headroom)
        assertNull(ThermalReading.of(ThermalStatus.None, -1.0).headroom)
        assertEquals(0.5, ThermalReading.of(ThermalStatus.None, 0.5).headroom)
        assertTrue(ThermalStatus.Critical.atLeast(ThermalStatus.Severe))
        assertFalse(ThermalStatus.Unknown.atLeast(ThermalStatus.None))
    }

    @Test
    fun `B-LINUX-281 battery readings from the kernel files`() {
        assertEquals(BatteryReading(level = 17, charging = false), BatteryReading.fromSysfs("17\n", "Discharging\n"))
        assertEquals(BatteryReading(level = 100, charging = true), BatteryReading.fromSysfs("100", "Full"))
        assertEquals(BatteryReading(level = 40, charging = false), BatteryReading.fromSysfs("40", "Not charging"))
        assertEquals(BatteryReading.Unknown, BatteryReading.fromSysfs(null, null))
        assertEquals(BatteryReading(level = null, charging = null), BatteryReading.fromSysfs("101", "weird"))
        assertEquals(BatteryReading(level = null, charging = true), BatteryReading.of(level = -1, charging = true))
    }
}
