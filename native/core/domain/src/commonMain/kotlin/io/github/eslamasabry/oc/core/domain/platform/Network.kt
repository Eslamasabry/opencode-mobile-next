package io.github.eslamasabry.oc.core.domain.platform

import io.github.eslamasabry.oc.core.model.NetworkChange
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.security.HostClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Network facts (plan 04 §6.4). In :core:domain, not :core:net, so engines and :android:platform reach it without
 * forbidden edges (audit 04 BLOCKER 3). Android: :android:platform ports NetworkMonitor.kt (B-ANDR-037).
 */
public interface NetworkStatus {
    public val state: StateFlow<NetworkState>
}

/** Consulted before every non-loopback attempt so no try is spent offline (04 N4-D8). Implemented in :core:net. */
public interface NetworkGate {
    /** Returns at once for Loopback; suspends while `online == No`. */
    public suspend fun awaitUsable(hostClass: HostClass)

    public val changes: Flow<NetworkChange>
}

/** Desktop (and iOS until NWPathMonitor): always Unknown, never gates. */
public object UnknownNetworkStatus : NetworkStatus {
    override val state: StateFlow<NetworkState> = MutableStateFlow(NetworkState.Unknown).asStateFlow()
}

/** The gate for a platform with no network signal: never waits. */
public object OpenNetworkGate : NetworkGate {
    override suspend fun awaitUsable(hostClass: HostClass) {}

    override val changes: Flow<NetworkChange> = emptyFlow()
}
