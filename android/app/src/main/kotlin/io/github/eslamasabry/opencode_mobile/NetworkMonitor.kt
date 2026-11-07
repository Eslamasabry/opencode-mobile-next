package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/** App-default connectivity only. No probes, identifiers, credentials or persistence. */
class NetworkMonitor(context: Context, messenger: BinaryMessenger) : EventChannel.StreamHandler {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val methods = MethodChannel(messenger, "oc/network")
    private val events = EventChannel(messenger, "oc/network/events")
    private var sink: EventChannel.EventSink? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var defaultNetwork: Network? = null
    private var disposed = false

    init {
        methods.setMethodCallHandler { call, result ->
            when (call.method) {
                "current" -> result.success(snapshot())
                else -> result.notImplemented()
            }
        }
        events.setStreamHandler(this)
    }

    override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink) {
        stopListening()
        sink = eventSink
        val manager = connectivity
        if (disposed || manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            eventSink.success(unknown())
            return
        }
        val listener = object : ConnectivityManager.NetworkCallback() {
            // Callback arguments are authoritative: never synchronously query
            // capabilities from inside callbacks (Android documents that race).
            override fun onAvailable(network: Network) = deliver {
                defaultNetwork = network
                sink?.success(unknown(connected = true))
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = deliver {
                // Capabilities alone in callbacks.
                if (network == defaultNetwork) sink?.success(reading(caps, activeMetered = false))
            }

            override fun onLost(network: Network) = deliver {
                // A late lost callback for the previous default must not erase
                // a replacement already announced by onAvailable.
                if (network == defaultNetwork) {
                    defaultNetwork = null
                    sink?.success(disconnected())
                }
            }

            private fun deliver(action: () -> Unit) {
                handler.post {
                    if (!disposed && callback === this) action()
                }
            }
        }
        callback = listener
        try {
            manager.registerDefaultNetworkCallback(listener)
            // Seed the initial snapshot on the main thread. Callback delivery
            // is queued after this, so subsequent capabilities always win.
            defaultNetwork = manager.activeNetwork
            eventSink.success(snapshot())
        } catch (_: Exception) {
            stopListening()
            eventSink.success(unknown())
        }
    }

    override fun onCancel(arguments: Any?) {
        stopListening()
    }

    private fun stopListening() {
        val listener = callback
        callback = null
        sink = null
        defaultNetwork = null
        if (listener != null) {
            try {
                connectivity?.unregisterNetworkCallback(listener)
            } catch (_: Exception) {
                // Cancellation is idempotent, including registration failure.
            }
        }
    }

    private fun snapshot(): Map<String, Any?> {
        if (disposed || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return unknown()
        val manager = connectivity ?: return unknown()
        return try {
            val network = manager.activeNetwork ?: return disconnected()
            val caps = manager.getNetworkCapabilities(network)
            // Do not combine capabilities with a different default network.
            if (manager.activeNetwork != network) unknown()
            else if (caps == null) unknown(connected = true)
            else reading(caps, activeMetered = manager.isActiveNetworkMetered)
        } catch (_: Exception) {
            unknown()
        }
    }

    private fun reading(caps: NetworkCapabilities, activeMetered: Boolean): Map<String, Any?> {
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
                if (VpnMetering.classify(
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                        activeMetered,
                    ) == VpnMetering.UNMETERED) "other" else "unknown"
            wifi && mobile -> "unknown"
            wifi -> "wifi"
            mobile -> "mobile"
            else -> "other"
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return mapOf(
            "status" to if (validated) "online" else "offline",
            "transport" to transport,
            "metered" to !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            "connected" to true,
        )
    }

    private fun unknown(connected: Boolean? = null): Map<String, Any?> = mapOf(
        "status" to "unknown", "transport" to "unknown", "metered" to null,
        "connected" to connected,
    )

    private fun disconnected(): Map<String, Any?> = mapOf(
        "status" to "offline", "transport" to "unknown", "metered" to null,
        "connected" to false,
    )

    fun dispose() {
        disposed = true
        stopListening()
        methods.setMethodCallHandler(null)
        events.setStreamHandler(null)
    }
}
