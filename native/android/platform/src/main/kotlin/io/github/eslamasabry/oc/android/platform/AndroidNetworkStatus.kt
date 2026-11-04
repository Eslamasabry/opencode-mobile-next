package io.github.eslamasabry.oc.android.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import io.github.eslamasabry.oc.core.model.NetworkState.Transport
import io.github.eslamasabry.oc.core.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Port of the Flutter app's NetworkMonitor.kt at cdfd713b (plan 04 §6.4, plan 11 §1.1 row 42,
// B-ANDR-037) without the
// channel glue. App-default connectivity only: no probes, no network names or identifiers beyond
// the opaque default
// network handle, nothing persisted, nothing logged.

/**
 * The capability bits the reading needs, copied out of [NetworkCapabilities] so the rules run on a
 * plain JVM.
 */
public data class NetworkCaps(
    val wifi: Boolean,
    val cellular: Boolean,
    val vpn: Boolean,
    val internet: Boolean,
    val validated: Boolean,
    val notMetered: Boolean,
)

/**
 * The typed reading for a connected default network. Online is Yes only for INTERNET ∧ VALIDATED; a
 * connected network that is not validated (captive Wi-Fi, a hotspot with no uplink) is Unknown,
 * never No, so it never gates a LAN or Tailnet host (No means "no default network", plan 04 §6.4).
 * VPN (Tailscale) and Wi-Fi+cellular read as Unknown.
 */
public fun networkReading(caps: NetworkCaps, defaultNetworkId: Long?): NetworkState {
    val transport =
        when {
            caps.vpn -> Transport.Unknown
            caps.wifi && caps.cellular -> Transport.Unknown
            caps.wifi -> Transport.Wifi
            caps.cellular -> Transport.Mobile
            else -> Transport.Other
        }
    val online = if (caps.internet && caps.validated) Online.Yes else Online.Unknown
    return NetworkState(
        online,
        transport,
        metered = !caps.notMetered,
        defaultNetworkId = defaultNetworkId,
    )
}

/** No default network at all. */
public val NoNetwork: NetworkState =
    NetworkState(Online.No, Transport.Unknown, metered = null, defaultNetworkId = null)

/**
 * The default-network state machine, generic over the platform's network handle [N] so JVM tests
 * drive it. Not thread safe: the Android binding calls it on the main looper only (registration,
 * seed and every callback).
 *
 * Rules kept from the Flutter monitor:
 * - callback arguments are authoritative; capabilities are never queried from inside a callback;
 * - capabilities for a network that is not the current default are ignored;
 * - a late `onLost` for the previous default never erases a replacement already announced by
 *   `onAvailable`;
 * - after [reset] (stop, registration failure) the state is Unknown, which never gates.
 */
public class DefaultNetworkTracker<N : Any>(private val idOf: (N) -> Long?) {
    private val mutable = MutableStateFlow(NetworkState.Unknown)
    public val state: StateFlow<NetworkState> = mutable.asStateFlow()
    private var current: N? = null

    /**
     * The snapshot taken right after registering. [stillDefault] is false when the default network
     * changed between reading it and reading its capabilities: never combine capabilities with a
     * different default network.
     */
    public fun seed(active: N?, caps: NetworkCaps?, stillDefault: Boolean) {
        current = active
        mutable.value =
            when {
                active == null -> NoNetwork
                !stillDefault -> NetworkState.Unknown
                caps == null -> connectedUnknown(active)
                else -> networkReading(caps, idOf(active))
            }
    }

    public fun onAvailable(network: N) {
        current = network
        mutable.value = connectedUnknown(network)
    }

    public fun onCapabilitiesChanged(network: N, caps: NetworkCaps) {
        if (network == current) mutable.value = networkReading(caps, idOf(network))
    }

    public fun onLost(network: N) {
        if (network != current) return
        current = null
        mutable.value = NoNetwork
    }

    public fun reset() {
        current = null
        mutable.value = NetworkState.Unknown
    }

    private fun connectedUnknown(network: N) =
        NetworkState(
            Online.Unknown,
            Transport.Unknown,
            metered = null,
            defaultNetworkId = idOf(network),
        )
}

/**
 * The Android [NetworkStatus] (plan 04 §6.4): one default-network callback, delivered on the main
 * looper. Unknown until [start]; the app graph starts it once per process. [start] and [close] may
 * be called from any thread and repeat safely; registration and every callback run on the main
 * looper, so the seed always precedes the first callback.
 */
public class AndroidNetworkStatus(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : NetworkStatus, AutoCloseable {
    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val tracker = DefaultNetworkTracker<Network> { it.networkHandle }
    private var callback: ConnectivityManager.NetworkCallback? = null // main looper only

    override val state: StateFlow<NetworkState>
        get() = tracker.state

    public fun start() {
        handler.post { startOnMain() }
    }

    override fun close() {
        handler.post { stopOnMain() }
    }

    private fun startOnMain() {
        if (callback != null) return
        val manager = connectivity ?: return
        val listener =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (callback === this) tracker.onAvailable(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    if (callback === this)
                        tracker.onCapabilitiesChanged(network, capsOf(networkCapabilities))
                }

                override fun onLost(network: Network) {
                    if (callback === this) tracker.onLost(network)
                }
            }
        callback = listener
        try {
            manager.registerDefaultNetworkCallback(listener, handler)
            val active = manager.activeNetwork
            val caps = active?.let { manager.getNetworkCapabilities(it) }
            tracker.seed(
                active,
                caps?.let(::capsOf),
                stillDefault = manager.activeNetwork == active,
            )
        } catch (_: Exception) {
            // Missing permission or a platform failure: Unknown never gates (B-ANDR-037 "returns
            // unknown").
            stopOnMain()
        }
    }

    private fun stopOnMain() {
        val listener = callback
        callback = null
        tracker.reset()
        if (listener != null) {
            try {
                connectivity?.unregisterNetworkCallback(listener)
            } catch (_: Exception) {
                // Idempotent, including after a failed registration.
            }
        }
    }

    private fun capsOf(caps: NetworkCapabilities) =
        NetworkCaps(
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
}
