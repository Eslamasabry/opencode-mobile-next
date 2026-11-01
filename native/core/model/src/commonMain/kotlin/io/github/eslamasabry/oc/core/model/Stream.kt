package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.Failure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status of one live event stream (plan 04 §6.3). Lives in :core:model, not :core:net, so :core:domain can expose
 * it (audit 04 BLOCKER 3). The UI never sees it directly: :engine:connection turns it into the quiet LinkStatus.
 *
 * `nextAttemptAtMs` is a real schedule: set when the delay is chosen, kept while that try is in flight.
 */
public sealed interface StreamStatus {
    public data object Connecting : StreamStatus

    public data object Connected : StreamStatus

    public data class Reconnecting(val attempt: Int, val nextAttemptAtMs: Long?) : StreamStatus

    /** The label after 6 failures; the loop still retries. */
    public data class Disconnected(val attempt: Int, val nextAttemptAtMs: Long?) : StreamStatus

    /** No usable network for a non-loopback host; no attempt is spent. */
    public data object WaitingForNetwork : StreamStatus

    /** The app is in the background and nothing is watching (LinkDemand = BackgroundIdle). */
    public data object Suspended : StreamStatus

    /** The loop gave up for a reason retrying cannot fix: sign-in needed, incompatible server. */
    public data class Stopped(val failure: Failure) : StreamStatus
}

/**
 * Network facts (plan 04 §6.4). The one network reading in the app: :core:net (the gate), :protocol:*, engines and
 * :android:platform all see :core:model. INTEGRATE-W1 moved [NetworkStatus] here from :core:domain, because :core:net
 * may not depend on :core:domain (ADR §6.1) and plan 04 §6.4 homes it in :core:model.
 */
public data class NetworkState(
    val online: Online,
    val transport: Transport = Transport.Unknown,
    /** The active network carries Wi-Fi, VPN included (Tailscale over Wi-Fi = true); Flutter's monitorNetworkPolicy. */
    val onWifi: Boolean? = null,
    val metered: Boolean? = null,
    /** Changes when the default network changes (Wi-Fi ↔ mobile, Tailscale VPN up/down). */
    val defaultNetworkId: Long? = null,
) {
    /** Yes = INTERNET ∧ VALIDATED; No = no default network. */
    public enum class Online { Yes, No, Unknown }

    /** VPN (e.g. Tailscale) or wifi+cellular → Unknown. */
    public enum class Transport { Wifi, Mobile, Other, Unknown }

    public companion object {
        /** Desktop and "not known yet": never gates. */
        public val Unknown: NetworkState = NetworkState(Online.Unknown)
    }
}

public enum class NetworkChange { DefaultChanged, Lost, Regained }

/** Plan 04 §6.4. Android: :android:platform ports NetworkMonitor.kt (B-ANDR-037); desktop binds [UnknownNetworkStatus]. */
public interface NetworkStatus {
    public val state: StateFlow<NetworkState>
}

/** Desktop (and iOS until NWPathMonitor): always Unknown, never gates. */
public object UnknownNetworkStatus : NetworkStatus {
    override val state: StateFlow<NetworkState> = MutableStateFlow(NetworkState.Unknown).asStateFlow()
}
