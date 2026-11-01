package io.github.eslamasabry.oc.core.net.gate

import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * N4-D8: a non-loopback host waits while there is no default network, without spending retries; the phone's own
 * server (loopback) is never gated, because a "no network" reading is advice only for it.
 */
public class NetworkGate(private val status: NetworkStatus) {
    public fun isUsable(host: HostClass): Boolean = host == HostClass.Loopback || status.state.value.online != NetworkState.Online.No

    /** Returns at once for loopback; suspends while the platform reports no network. */
    public suspend fun awaitUsable(host: HostClass) {
        if (host == HostClass.Loopback) return
        status.state.first { it.online != NetworkState.Online.No }
    }

    /** The default network id as it is now, to compare with later. */
    public fun currentNetworkId(): Long? = status.state.value.defaultNetworkId

    /** Suspends until the default network differs from [from] while online (a new network to dial on). */
    public suspend fun awaitNetworkChangeFrom(from: Long?) {
        status.state.first { it.online != NetworkState.Online.No && it.defaultNetworkId != from }
    }

    /** Emits when the platform loses the network (online becomes No). */
    public val losses: Flow<Unit> =
        status.state.map { it.online == NetworkState.Online.No }.distinctUntilChanged().drop(1).map { }
}
