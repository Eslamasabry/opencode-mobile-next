package io.github.eslamasabry.oc.engine.analytics

import kotlinx.serialization.Serializable

public enum class NetworkKind { None, Unmetered, Metered }

/**
 * What the platform reports about the device right now. `:androidApp` maps ConnectivityManager (validated default
 * network, metered, `restrictBackgroundStatus`) and the process lifecycle into this; the engine never reads the
 * platform itself (ADR §7.2: engines have no androidMain).
 */
public data class DeviceConditions(
    val network: NetworkKind,
    val validated: Boolean,
    val foreground: Boolean,
    val dataSaver: Boolean,
) {
    public companion object {
        /** Until the platform says otherwise, nothing uploads. */
        public val Unknown: DeviceConditions =
            DeviceConditions(NetworkKind.None, validated = false, foreground = false, dataSaver = false)
    }
}

public enum class HoldReason {
    NoDestination, GateClosed, NothingQueued, NoNetwork, NetworkNotValidated, MobileDataInBackground,
    DataSaverInBackground, BackingOff,
}

public sealed interface UploadDecision {
    public data object Go : UploadDecision

    public data class Hold(val reason: HoldReason) : UploadDecision
}

/** Exponential backoff after a failed send: 1 min, doubling, capped at 6 h (plan 16 §5.6 rule 6). */
@Serializable
internal data class Backoff(val failures: Int = 0, val notBeforeMs: Long = 0) {
    fun failed(nowMs: Long): Backoff {
        val next = failures + 1
        return Backoff(next, nowMs + delayFor(next))
    }

    fun waiting(nowMs: Long): Boolean = failures > 0 && nowMs < notBeforeMs

    companion object {
        const val FIRST_MS: Long = 60_000L
        const val MAX_MS: Long = 6 * HOUR_MS

        fun delayFor(failures: Int): Long {
            var delay = FIRST_MS
            repeat((failures - 1).coerceIn(0, 30)) { delay = (delay * 2).coerceAtMost(MAX_MS) }
            return delay.coerceAtMost(MAX_MS)
        }
    }
}

/**
 * When the queue may be handed to a sink. A sink that keeps everything on the phone ([SinkKind.LocalDebug]) ignores
 * the network rules; a remote sink needs a validated network, and never uses mobile data or a Data Saver network
 * while the app is in the background.
 */
internal object UploadPolicy {
    fun decide(
        conditions: DeviceConditions,
        sink: SinkKind,
        gateOpen: Boolean,
        queued: Int,
        backoff: Backoff,
        nowMs: Long,
    ): UploadDecision {
        fun hold(reason: HoldReason) = UploadDecision.Hold(reason)
        return when {
            sink == SinkKind.None -> hold(HoldReason.NoDestination)
            !gateOpen -> hold(HoldReason.GateClosed)
            queued == 0 -> hold(HoldReason.NothingQueued)
            sink == SinkKind.LocalDebug -> UploadDecision.Go
            conditions.network == NetworkKind.None -> hold(HoldReason.NoNetwork)
            !conditions.validated -> hold(HoldReason.NetworkNotValidated)
            !conditions.foreground && conditions.network == NetworkKind.Metered -> hold(HoldReason.MobileDataInBackground)
            !conditions.foreground && conditions.dataSaver -> hold(HoldReason.DataSaverInBackground)
            backoff.waiting(nowMs) -> hold(HoldReason.BackingOff)
            else -> UploadDecision.Go
        }
    }
}
