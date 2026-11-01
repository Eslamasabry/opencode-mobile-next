package io.github.eslamasabry.oc.android.platform

import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import io.github.eslamasabry.oc.core.model.NetworkState.Transport
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plain JVM: the default-network rules without Android types. */
class NetworkTrackerTest {
    private data class Net(val id: Long)

    private val wifi = NetworkCaps(wifi = true, cellular = false, vpn = false, internet = true, validated = true, notMetered = true)
    private val mobile = NetworkCaps(wifi = false, cellular = true, vpn = false, internet = true, validated = true, notMetered = false)
    private val tracker = DefaultNetworkTracker<Net> { it.id }

    @Test
    fun `B-ANDR-037 no network, unvalidated Wi-Fi and unknown are three different readings`() {
        assertEquals(NetworkState.Unknown, tracker.state.value, "unknown before anything is known")
        tracker.seed(null, null, stillDefault = true)
        assertEquals(NetworkState(Online.No, Transport.Unknown, metered = null, defaultNetworkId = null), tracker.state.value)
        tracker.seed(Net(7), wifi.copy(validated = false), stillDefault = true)
        assertEquals(NetworkState(Online.Unknown, Transport.Wifi, metered = false, defaultNetworkId = 7), tracker.state.value,
            "connected but unvalidated is never No, so a LAN server is not gated")
    }

    @Test
    fun `B-ANDR-037 typed mobile and metered state`() {
        assertEquals(NetworkState(Online.Yes, Transport.Mobile, metered = true, defaultNetworkId = 3), networkReading(mobile, 3))
        assertEquals(NetworkState(Online.Yes, Transport.Wifi, metered = false, defaultNetworkId = 3), networkReading(wifi, 3))
        assertEquals(Transport.Unknown, networkReading(wifi.copy(vpn = true), 3).transport, "Tailscale reads Unknown")
        assertEquals(Transport.Unknown, networkReading(wifi.copy(cellular = true), 3).transport)
        assertEquals(Transport.Other, networkReading(wifi.copy(wifi = false), 3).transport)
        assertEquals(Online.Unknown, networkReading(wifi.copy(internet = false), 3).online)
    }

    @Test
    fun `B-ANDR-037 a snapshot never combines capabilities with a different default network`() {
        tracker.seed(Net(1), wifi, stillDefault = false)
        assertEquals(NetworkState.Unknown, tracker.state.value)
        tracker.seed(Net(1), null, stillDefault = true)
        assertEquals(NetworkState(Online.Unknown, Transport.Unknown, metered = null, defaultNetworkId = 1), tracker.state.value)
    }

    @Test
    fun `B-ANDR-037 newer callbacks beat the seed and a late lost for the old default is ignored`() {
        tracker.seed(Net(1), wifi, stillDefault = true)
        tracker.onAvailable(Net(2))
        assertEquals(NetworkState(Online.Unknown, Transport.Unknown, metered = null, defaultNetworkId = 2), tracker.state.value)
        tracker.onCapabilitiesChanged(Net(1), wifi.copy(validated = false))
        assertEquals(2, tracker.state.value.defaultNetworkId, "capabilities of the old default are ignored")
        tracker.onCapabilitiesChanged(Net(2), mobile)
        tracker.onLost(Net(1))
        assertEquals(NetworkState(Online.Yes, Transport.Mobile, metered = true, defaultNetworkId = 2), tracker.state.value)
        tracker.onLost(Net(2))
        assertEquals(Online.No, tracker.state.value.online)
    }

    @Test
    fun `B-ANDR-037 repeats are harmless and reset returns to unknown`() {
        tracker.onAvailable(Net(4))
        tracker.onCapabilitiesChanged(Net(4), wifi)
        val first = tracker.state.value
        tracker.onCapabilitiesChanged(Net(4), wifi)
        assertEquals(first, tracker.state.value)
        tracker.onLost(Net(4))
        tracker.onLost(Net(4))
        assertEquals(Online.No, tracker.state.value.online)
        tracker.reset()
        tracker.reset()
        assertEquals(NetworkState.Unknown, tracker.state.value)
        tracker.onCapabilitiesChanged(Net(4), wifi)
        assertEquals(NetworkState.Unknown, tracker.state.value, "after reset nothing is current until onAvailable")
    }
}
