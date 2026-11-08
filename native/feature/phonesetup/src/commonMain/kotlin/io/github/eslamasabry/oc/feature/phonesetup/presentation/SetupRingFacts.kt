package io.github.eslamasabry.oc.feature.phonesetup.presentation

import kotlin.math.roundToInt

/** The network the setup downloads over, as Android names its transport (Flutter `NetworkTransport`). */
public enum class SetupTransport { Wifi, Mobile, Other, Unknown }

/**
 * What the progress ring says beside the time left (Flutter phone_setup_progress_screen.dart `_costLine` and the
 * safe-to-leave line): the network in use, and whether the app may post the "ready" notification. The app binds it
 * over ConnectivityManager and the notification permission; without a binding the ring says neither.
 */
public interface SetupRingFacts {
    /** Read on each progress change; never blocks the ring (an unanswered read is [SetupTransport.Unknown]). */
    public suspend fun transport(): SetupTransport

    /** The person allowed notifications, so the ring can promise "We'll let you know when it's ready". */
    public fun canNotify(): Boolean
}

public class NoSetupRingFacts : SetupRingFacts {
    override suspend fun transport(): SetupTransport = SetupTransport.Unknown

    override fun canNotify(): Boolean = false
}

/** The cost line's parts, in Flutter's order: the time left (when known), then the network (Wi-Fi or mobile data). */
public sealed interface RingCostPart {
    public data object UnderMinute : RingCostPart

    public data class Minutes(val minutes: Int) : RingCostPart

    public data object Wifi : RingCostPart

    public data object MobileData : RingCostPart
}

public object SetupRingCost {
    /**
     * Flutter `_costLine`: the time left ("Less than a minute left" only on the last step), then the network; the
     * network alone says nothing while nothing downloads. Empty: no line.
     */
    public fun parts(etaSeconds: Int?, lastStep: Boolean, downloading: Boolean, transport: SetupTransport): List<RingCostPart> {
        val parts = mutableListOf<RingCostPart>()
        if (etaSeconds != null)
            parts += if (etaSeconds < 60 && lastStep) RingCostPart.UnderMinute else RingCostPart.Minutes(maxOf(1, (etaSeconds / 60.0).roundToInt()))
        if (parts.isEmpty() && !downloading) return emptyList()
        when (transport) {
            SetupTransport.Wifi -> parts += RingCostPart.Wifi
            SetupTransport.Mobile -> parts += RingCostPart.MobileData
            SetupTransport.Other,
            SetupTransport.Unknown -> Unit
        }
        return parts
    }
}
