package io.github.eslamasabry.oc.engine.diagnostics.exit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeepAliveAdviceTest {
    @Test
    fun `B-ANDR-007 manufacturer and brand map to a maker`() {
        assertEquals(PhoneMaker.Nubia, PhoneMaker.of("nubia", "RedMagic"))
        assertEquals(PhoneMaker.Nubia, PhoneMaker.of("ZTE"))
        assertEquals(PhoneMaker.Xiaomi, PhoneMaker.of("Xiaomi", "Redmi"))
        assertEquals(PhoneMaker.Xiaomi, PhoneMaker.of("Xiaomi", "POCO"))
        assertEquals(PhoneMaker.Oppo, PhoneMaker.of("OnePlus"))
        assertEquals(PhoneMaker.Oppo, PhoneMaker.of("realme"))
        assertEquals(PhoneMaker.Oppo, PhoneMaker.of("OPPO"))
        assertEquals(PhoneMaker.Vivo, PhoneMaker.of("vivo"))
        assertEquals(PhoneMaker.Huawei, PhoneMaker.of("HUAWEI"))
        assertEquals(PhoneMaker.Huawei, PhoneMaker.of("HONOR"))
        assertEquals(PhoneMaker.Samsung, PhoneMaker.of("samsung"))
        assertEquals(PhoneMaker.Other, PhoneMaker.of("Google", "google"))
        assertEquals(PhoneMaker.Other, PhoneMaker.of(""))
    }

    @Test
    fun `B-ANDR-007 battery first everywhere and a lock where swiping force-stops`() {
        for (maker in PhoneMaker.entries) {
            val steps = keepAliveSteps(maker)
            assertEquals(KeepAliveStepKind.Battery, steps.first().kind)
            assertEquals(KeepAliveSetting.Battery, steps.first().setting)
            assertEquals(maker.closesOnSwipe, steps.any { it.kind == KeepAliveStepKind.LockInRecents }, maker.name)
            // A step done by hand has no screen to open.
            assertTrue(steps.filter { it.kind == KeepAliveStepKind.LockInRecents }.all { it.setting == null })
        }
        // On Nubia the lock matters most after the battery switch.
        assertEquals(KeepAliveStepKind.LockInRecents, keepAliveSteps(PhoneMaker.Nubia)[1].kind)
        assertTrue(keepAliveSteps(PhoneMaker.Other).none { it.kind == KeepAliveStepKind.Autostart })
    }

    @Test
    fun `B-ANDR-007 each maker gets its own words`() {
        fun wording(maker: PhoneMaker, kind: KeepAliveStepKind) = keepAliveSteps(maker).first { it.kind == kind }.wording(maker)
        assertEquals(KeepAliveWording.LockNubia, wording(PhoneMaker.Nubia, KeepAliveStepKind.LockInRecents))
        assertEquals(KeepAliveWording.LockSamsung, wording(PhoneMaker.Samsung, KeepAliveStepKind.LockInRecents))
        assertEquals(KeepAliveWording.LockOther, wording(PhoneMaker.Xiaomi, KeepAliveStepKind.LockInRecents))
        assertEquals(KeepAliveWording.BackgroundXiaomi, wording(PhoneMaker.Xiaomi, KeepAliveStepKind.Background))
        assertEquals(KeepAliveWording.BackgroundOppo, wording(PhoneMaker.Oppo, KeepAliveStepKind.Background))
        assertEquals(KeepAliveWording.BackgroundVivo, wording(PhoneMaker.Vivo, KeepAliveStepKind.Background))
        assertEquals(KeepAliveWording.BackgroundSamsung, wording(PhoneMaker.Samsung, KeepAliveStepKind.Background))
        assertEquals(KeepAliveWording.BackgroundOther, wording(PhoneMaker.Other, KeepAliveStepKind.Background))
        assertEquals(KeepAliveWording.AutostartHuawei, wording(PhoneMaker.Huawei, KeepAliveStepKind.Autostart))
        assertEquals(KeepAliveWording.AutostartOther, wording(PhoneMaker.Xiaomi, KeepAliveStepKind.Autostart))
        val battery = keepAliveSteps(PhoneMaker.Other).first()
        assertEquals(KeepAliveWording.BatteryDetail, battery.wording(PhoneMaker.Other))
        assertEquals(KeepAliveWording.BatteryDone, battery.wording(PhoneMaker.Other, batteryAllowed = true))
    }
}
