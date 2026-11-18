package io.github.eslamasabry.oc.android.voice.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.models.SetupNetworkSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** One network a VPN runs over, as Android describes it (Android 12 and later name them on the VPN's own record). */
public data class UnderlyingNetworkFacts(
    val internet: Boolean,
    val validated: Boolean,
    val cellular: Boolean,
    /** NET_CAPABILITY_NOT_METERED of this network itself. */
    val notMetered: Boolean,
)

/** The kinds of link a network can be, as the VPN's own record names them. */
public enum class LinkKind { Wifi, Cellular, Ethernet, Bluetooth }

/** One connected non-VPN network as Android describes it. */
public data class ConnectedNetworkFacts(
    val links: Set<LinkKind>,
    val internet: Boolean,
    val validated: Boolean,
    val notMetered: Boolean,
)

/** What the classifier needs; null fields mean "not answered". */
public data class NetworkFacts(
    val hasManager: Boolean,
    val hasActiveNetwork: Boolean,
    val internet: Boolean,
    val validated: Boolean,
    val cellular: Boolean,
    val vpn: Boolean,
    val activeMetered: Boolean,
    /** The active network's NET_CAPABILITY_NOT_METERED; for a VPN, Android derives it from the underlying networks. */
    val notMetered: Boolean = !activeMetered,
    /**
     * For a VPN: the networks it really runs over (null when Android does not say). The traffic is paid for by those,
     * so they decide, never the VPN's own transport or validation.
     */
    val underlying: List<UnderlyingNetworkFacts>? = null,
)

/**
 * Network class for download consent (plan 10 §7.6, B-ANDR-101): missing or failed probes never allow free data.
 * Cellular is always Metered (even on an unmetered plan). A VPN is read through what Android says about it: since
 * API 29 a VPN network carries its underlying networks' transports and is NOT_METERED only when they all are, and
 * `isActiveNetworkMetered` follows the same rule. So Tailscale over home Wi-Fi is Unmetered, a VPN over mobile data is
 * Metered, and a VPN whose two answers disagree is Unknown (never free data).
 */
public object SetupNetworkRules {
    /**
     * The networks a VPN really runs over, from every connected non-VPN network: those whose links the VPN's own
     * record carries (a VPN over Wi-Fi carries Wi-Fi, so mobile data that merely stays connected beside it is not
     * read; a VPN pinned to mobile data carries cellular, so the Wi-Fi beside it is not read). Null when none fits.
     */
    public fun carriersOf(
        vpnLinks: Set<LinkKind>,
        connected: List<ConnectedNetworkFacts>,
    ): List<UnderlyingNetworkFacts>? =
        connected
            .filter { it.links.isNotEmpty() && vpnLinks.containsAll(it.links) }
            .map { UnderlyingNetworkFacts(it.internet, it.validated, LinkKind.Cellular in it.links, it.notMetered) }
            .ifEmpty { null }

    /**
     * First the VPN's own answer, when it is validated: Android derives its metered-ness from the networks it runs
     * over (Android 10 and later). Only when that is unvalidated or contradicts itself do the networks it runs over
     * speak (carriers), each by its own NOT_METERED; none known stays "ask".
     */
    private fun classifyVpn(facts: NetworkFacts): SetupNetwork = vpnOwnAnswer(facts) ?: fromCarriers(facts)

    private fun vpnOwnAnswer(facts: NetworkFacts): SetupNetwork? =
        when {
            !facts.validated -> null
            facts.cellular || facts.activeMetered -> SetupNetwork.Metered
            facts.notMetered -> SetupNetwork.Unmetered
            else -> null
        }

    private fun fromCarriers(facts: NetworkFacts): SetupNetwork {
        val carriers = facts.underlying.orEmpty().filter { it.internet }
        return when {
            // Android names no network the VPN runs over, yet says the VPN is not metered, has no cellular link and is
            // not on a metered connection: that is the Wi-Fi-or-wired case, free data (never when it says otherwise).
            carriers.isEmpty() ->
                if (!facts.cellular && !facts.activeMetered && facts.notMetered) {
                    SetupNetwork.Unmetered
                } else {
                    SetupNetwork.Unknown
                }
            !facts.validated && carriers.none { it.validated } -> SetupNetwork.Unknown
            carriers.all { it.notMetered && !it.cellular } -> SetupNetwork.Unmetered
            else -> SetupNetwork.Metered
        }
    }

    public fun classify(facts: NetworkFacts?): SetupNetwork {
        if (facts == null || !facts.hasManager) return SetupNetwork.Unknown
        if (!facts.hasActiveNetwork || !facts.internet) return SetupNetwork.Offline
        if (facts.vpn) return classifyVpn(facts)
        if (!facts.validated) return SetupNetwork.Unknown
        if (facts.cellular) return SetupNetwork.Metered
        if (facts.activeMetered) return SetupNetwork.Metered
        return SetupNetwork.Unmetered
    }
}

public class AndroidSetupNetwork(private val context: Context) : SetupNetworkSource {
    private val manager: ConnectivityManager? get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override suspend fun current(): SetupNetwork = withTimeoutOrNull(5.seconds) { SetupNetworkRules.classify(facts()) } ?: SetupNetwork.Unknown

    override val changes: Flow<SetupNetwork> =
        callbackFlow {
            val cm = manager
            if (cm == null) {
                trySend(SetupNetwork.Unknown)
                awaitClose {}
                return@callbackFlow
            }
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        trySend(SetupNetworkRules.classify(facts()))
                    }

                    override fun onLost(network: Network) {
                        trySend(SetupNetworkRules.classify(facts()))
                    }
                }
            try {
                cm.registerDefaultNetworkCallback(callback)
            } catch (_: RuntimeException) {
                trySend(SetupNetwork.Unknown)
            }
            awaitClose {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: IllegalArgumentException) {
                    // never registered
                }
            }
        }.distinctUntilChanged()

    private fun facts(): NetworkFacts? =
        try {
            val cm = manager ?: return NetworkFacts(false, false, false, false, false, false, true, notMetered = false)
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            NetworkFacts(
                hasManager = true,
                hasActiveNetwork = network != null && caps != null,
                internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
                vpn = vpn,
                activeMetered = cm.isActiveNetworkMetered,
                notMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
                underlying = if (caps != null && vpn) underlyingOf(cm, caps) else null,
            )
        } catch (_: RuntimeException) {
            null
        }

    @Suppress("DEPRECATION")
    private fun underlyingOf(cm: ConnectivityManager, vpn: NetworkCapabilities): List<UnderlyingNetworkFacts>? {
        fun links(c: NetworkCapabilities) =
            buildSet {
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(LinkKind.Wifi)
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(LinkKind.Cellular)
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(LinkKind.Ethernet)
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add(LinkKind.Bluetooth)
            }
        val connected =
            cm.allNetworks.mapNotNull { network ->
                val c = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                ConnectedNetworkFacts(
                    links = links(c),
                    internet = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    notMetered = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                )
            }
        return SetupNetworkRules.carriersOf(links(vpn), connected)
    }
}
