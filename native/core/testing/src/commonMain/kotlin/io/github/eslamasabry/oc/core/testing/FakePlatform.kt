package io.github.eslamasabry.oc.core.testing

import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A network the test switches (plan 04 §14). The gate rule itself is `:core:net`'s `NetworkGate`, which tests build
 * over this status (`:core:testing` may not see `:core:net`), so there is no second fake gate to drift from it.
 */
public class FakeNetworkStatus(initial: NetworkState = Online) : NetworkStatus {
    override val state: MutableStateFlow<NetworkState> = MutableStateFlow(initial)

    public fun goOffline() {
        state.value = state.value.copy(online = NetworkState.Online.No)
    }

    public fun goOnline(defaultNetworkId: Long? = state.value.defaultNetworkId) {
        state.value = state.value.copy(online = NetworkState.Online.Yes, defaultNetworkId = defaultNetworkId)
    }

    public companion object {
        public val Online: NetworkState =
            NetworkState(
                online = NetworkState.Online.Yes,
                transport = NetworkState.Transport.Wifi,
                onWifi = true,
                metered = false,
                defaultNetworkId = 1L,
            )
    }
}
