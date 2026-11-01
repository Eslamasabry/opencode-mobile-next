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

/** What the classifier needs; null fields mean "not answered". */
public data class NetworkFacts(
    val hasManager: Boolean,
    val hasActiveNetwork: Boolean,
    val internet: Boolean,
    val validated: Boolean,
    val cellular: Boolean,
    val vpn: Boolean,
    val activeMetered: Boolean,
)

/**
 * Network class for download consent (plan 10 §7.6, B-ANDR-101): missing or failed probes never allow free data.
 * Cellular is always Metered (even on an unmetered plan); a VPN is Unknown.
 */
public object SetupNetworkRules {
    public fun classify(facts: NetworkFacts?): SetupNetwork {
        if (facts == null || !facts.hasManager) return SetupNetwork.Unknown
        if (!facts.hasActiveNetwork || !facts.internet) return SetupNetwork.Offline
        if (!facts.validated) return SetupNetwork.Unknown
        if (facts.cellular) return SetupNetwork.Metered
        if (facts.vpn) return SetupNetwork.Unknown
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
            awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }.distinctUntilChanged()

    private fun facts(): NetworkFacts? =
        try {
            val cm = manager ?: return NetworkFacts(false, false, false, false, false, false, false)
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
            )
        } catch (_: RuntimeException) {
            null
        }
}
