package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.Failure

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

/** Network facts (plan 04 §6.4). Values here; the NetworkStatus service interface is in :core:domain. */
public data class NetworkState(
    val online: Online,
    val transport: Transport,
    val metered: Boolean?,
    /** Changes when the default network changes (Wi-Fi ↔ mobile, Tailscale VPN up/down). */
    val defaultNetworkId: Long?,
) {
    /** Yes = INTERNET ∧ VALIDATED; No = no default network. */
    public enum class Online { Yes, No, Unknown }

    /** VPN (e.g. Tailscale) or wifi+cellular → Unknown. */
    public enum class Transport { Wifi, Mobile, Other, Unknown }

    public companion object {
        /** Desktop and "not known yet": never gates. */
        public val Unknown: NetworkState = NetworkState(Online.Unknown, Transport.Unknown, null, null)
    }
}

public enum class NetworkChange { DefaultChanged, Lost, Regained }
