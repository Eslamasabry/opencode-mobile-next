package io.github.eslamasabry.oc.core.domain.capabilities

import kotlin.test.Test
import kotlin.test.assertEquals

/** Device-class classification tests (B-CAP-300..302). */
class DeviceClassTest {
    @Test
    fun `B-CAP-300 smallest width 600 dp or more without fold hardware is a tablet and under 600 dp is a phone`() {
        for (sw in listOf(0, 320, 360, 412, 599)) {
            assertEquals(DeviceClass.Phone, PlatformCapabilities.resolveDeviceClass(sw, hasFold = false), "sw=${sw}dp")
        }
        for (sw in listOf(600, 720, 800, 840, 1024, 1280)) {
            assertEquals(DeviceClass.Tablet, PlatformCapabilities.resolveDeviceClass(sw, hasFold = false), "sw=${sw}dp")
        }
    }

    @Test
    fun `B-CAP-301 fold hardware keeps a device classified as a phone even at tablet widths`() {
        for (sw in listOf(360, 600, 720, 840, 1280)) {
            assertEquals(DeviceClass.Phone, PlatformCapabilities.resolveDeviceClass(sw, hasFold = true), "sw=${sw}dp")
        }
    }

    @Test
    fun `B-CAP-302 desktop and non-Android platforms default to Phone deviceClass`() {
        for (family in PlatformFamily.entries) {
            assertEquals(DeviceClass.Phone, PlatformCapabilities.forPlatform(family).deviceClass, "$family")
        }
        assertEquals(DeviceClass.Tablet, PlatformCapabilities.android(deviceClass = DeviceClass.Tablet).deviceClass)
    }
}
