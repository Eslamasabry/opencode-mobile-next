package io.github.eslamasabry.oc.core.net.gate

import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Yes = a validated default network; No = no default network at all; Unknown = the platform cannot say. */
public enum class Online { Yes, No, Unknown }

public enum class Transport { Wifi, Mobile, Other, Unknown }

/** Plan 04 §6.4. Android binds it from `:android:platform` (default-network callback); desktop never gates. */
public data class NetworkState(
    val online: Online,
    val transport: Transport = Transport.Unknown,
    val metered: Boolean? = null,
    /** Changes when the default network changes (Wi-Fi ↔ mobile, a VPN such as Tailscale up or down). */
    val defaultNetworkId: Long? = null,
)

public interface NetworkStatus {
    public val state: StateFlow<NetworkState>
}

/** Desktop (and tests that do not care): always Unknown, so nothing is ever gated. */
public object UnknownNetworkStatus : NetworkStatus {
    override val state: StateFlow<NetworkState> = MutableStateFlow(NetworkState(Online.Unknown)).asStateFlow()
}

/**
 * N4-D8: a non-loopback host waits while there is no default network, without spending retries; the phone's own
 * server (loopback) is never gated, because a "no network" reading is advice only for it.
 */
public class NetworkGate(private val status: NetworkStatus) {
    public fun isUsable(host: HostClass): Boolean = host == HostClass.Loopback || status.state.value.online != Online.No

    /** Returns at once for loopback; suspends while the platform reports no network. */
    public suspend fun awaitUsable(host: HostClass) {
        if (host == HostClass.Loopback) return
        status.state.first { it.online != Online.No }
    }

    /** The default network id as it is now, to compare with later. */
    public fun currentNetworkId(): Long? = status.state.value.defaultNetworkId

    /** Suspends until the default network differs from [from] while online (a new network to dial on). */
    public suspend fun awaitNetworkChangeFrom(from: Long?) {
        status.state.first { it.online != Online.No && it.defaultNetworkId != from }
    }

    /** Emits when the platform loses the network (online becomes No). */
    public val losses: Flow<Unit> =
        status.state.map { it.online == Online.No }.distinctUntilChanged().drop(1).map { }
}
