package io.github.eslamasabry.opencode_mobile

/**
 * How a VPN is classified for download consent. Android derives a VPN's
 * metered state from the network underneath it, so a VPN that reports
 * NOT_METERED while the active network is not metered is a free link; a VPN
 * on a metered link is metered; anything else cannot be told.
 */
internal object VpnMetering {
    const val UNMETERED = "unmetered"
    const val METERED = "metered"
    const val UNKNOWN = "unknown"

    fun classify(notMeteredCapability: Boolean, activeNetworkMetered: Boolean): String = when {
        activeNetworkMetered -> METERED
        notMeteredCapability -> UNMETERED
        else -> UNKNOWN
    }
}
