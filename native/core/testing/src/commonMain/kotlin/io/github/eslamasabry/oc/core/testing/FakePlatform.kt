package io.github.eslamasabry.oc.core.testing

import io.github.eslamasabry.oc.core.domain.platform.NetworkGate
import io.github.eslamasabry.oc.core.domain.platform.NetworkStatus
import io.github.eslamasabry.oc.core.model.NetworkChange
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** A network the test switches (plan 04 §14). */
public class FakeNetworkStatus(initial: NetworkState = Online) : NetworkStatus {
    override val state: MutableStateFlow<NetworkState> = MutableStateFlow(initial)

    public fun goOffline() {
        state.value = state.value.copy(online = NetworkState.Online.No)
    }

    public fun goOnline(defaultNetworkId: Long? = state.value.defaultNetworkId) {
        state.value = state.value.copy(online = NetworkState.Online.Yes, defaultNetworkId = defaultNetworkId)
    }

    public companion object {
        public val Online: NetworkState = NetworkState(NetworkState.Online.Yes, NetworkState.Transport.Wifi, false, 1L)
    }
}

/** The plan 04 §6.4 gate rule over a [FakeNetworkStatus]: loopback never waits; others wait while offline. */
public class FakeNetworkGate(private val status: FakeNetworkStatus) : NetworkGate {
    override val changes: MutableSharedFlow<NetworkChange> = MutableSharedFlow(extraBufferCapacity = 16)

    override suspend fun awaitUsable(hostClass: HostClass) {
        if (hostClass == HostClass.Loopback) return
        status.state.first { it.online != NetworkState.Online.No }
    }
}

