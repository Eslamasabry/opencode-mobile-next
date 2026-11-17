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
    public fun classify(facts: NetworkFacts?): SetupNetwork {
        if (facts == null || !facts.hasManager) return SetupNetwork.Unknown
        if (!facts.hasActiveNetwork || !facts.internet) return SetupNetwork.Offline
        if (facts.vpn) {
            // Decide by the network that really carries the traffic. Several: all of them must be free.
            val carriers = facts.underlying.orEmpty().filter { it.internet }
            if (carriers.isNotEmpty()) {
                if (!facts.validated && carriers.none { it.validated }) return SetupNetwork.Unknown
                return if (carriers.all { it.notMetered && !it.cellular }) SetupNetwork.Unmetered else SetupNetwork.Metered
            }
        }
        if (!facts.validated) return SetupNetwork.Unknown
        if (facts.cellular) return SetupNetwork.Metered
        if (facts.vpn) {
            return when {
                facts.activeMetered -> SetupNetwork.Metered
                facts.notMetered -> SetupNetwork.Unmetered
                else -> SetupNetwork.Unknown
            }
        }
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
            NetworkFacts(
                hasManager = true,
                hasActiveNetwork = network != null && caps != null,
                internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
                vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                activeMetered = cm.isActiveNetworkMetered,
                notMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
                underlying = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) underlyingOf(cm, caps) else null,
            )
        } catch (_: RuntimeException) {
            null
        }

    /**
     * The networks a VPN can be riding on: connected non-VPN networks whose transports the VPN's own record carries
     * (Android copies its underlying networks' transports onto it). Their own NOT_METERED answers decide; the name of
     * the transport only picks which networks to read. Empty or unreadable: null (the VPN's own record decides).
     */
    @Suppress("DEPRECATION")
    private fun underlyingOf(cm: ConnectivityManager, vpn: NetworkCapabilities): List<UnderlyingNetworkFacts>? {
        val kinds =
            intArrayOf(
                NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.TRANSPORT_CELLULAR,
                NetworkCapabilities.TRANSPORT_ETHERNET,
                NetworkCapabilities.TRANSPORT_BLUETOOTH,
            )
        val found =
            cm.allNetworks.mapNotNull { network ->
                val c = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                val own = kinds.filter { c.hasTransport(it) }
                if (own.isEmpty() || own.any { !vpn.hasTransport(it) }) return@mapNotNull null
                UnderlyingNetworkFacts(
                    internet = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    cellular = c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    notMetered = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                )
            }
        return found.ifEmpty { null }
    }
}
