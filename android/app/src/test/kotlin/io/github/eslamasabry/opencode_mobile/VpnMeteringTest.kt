package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnMeteringTest {
    @Test
    fun vpnOverUnmeteredWifiIsUnmetered() {
        assertEquals("unmetered", VpnMetering.classify(notMeteredCapability = true, activeNetworkMetered = false))
    }

    @Test
    fun vpnOverMeteredLinkIsMeteredEvenIfCapabilityIsSet() {
        assertEquals("metered", VpnMetering.classify(notMeteredCapability = false, activeNetworkMetered = true))
        assertEquals("metered", VpnMetering.classify(notMeteredCapability = true, activeNetworkMetered = true))
    }

    @Test
    fun vpnWithNoProofStaysUnknown() {
        assertEquals("unknown", VpnMetering.classify(notMeteredCapability = false, activeNetworkMetered = false))
    }
}
