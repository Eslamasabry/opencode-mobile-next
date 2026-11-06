package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccessRequest
import io.github.eslamasabry.oc.feature.servers.discovery.LanDiscovery
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.discovery.LanFindings
import io.github.eslamasabry.oc.feature.servers.discovery.LanSource
import io.github.eslamasabry.oc.feature.servers.discovery.LanSubnet
import io.github.eslamasabry.oc.feature.servers.discovery.NoLanDiscovery
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2GatewayFactory
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Find on my network" for Android.
 *
 * - OpenCode 1 advertises `_http._tcp` through NsdManager; a service whose name or host contains
 *   "opencode", or that answers the OpenCode probe, is kept.
 * - OpenCode 2 does not advertise, so the phone's own Wi-Fi /24 is swept on ports 4096 and 4097
 *   (plus a port the person already used): a 300 ms connect, then the protocol's own health/version
 *   probe. A 401 counts as found and is tagged "needs password".
 *
 * Never leaves the phone's subnet, never sends a credential (the probe carries none), and the
 * whole search ends by itself after [TOTAL_CAP_MS].
 */
@ContributesBinding(AppScope::class, replaces = [NoLanDiscovery::class])
@Inject
class AndroidLanDiscovery(
    private val application: Application,
    private val oc2: Oc2GatewayFactory,
    private val dispatchers: AppDispatchers,
) : LanDiscovery {
    override val supported: Boolean = true

    @Composable
    override fun rememberAccess(onResult: (LanAccess) -> Unit): LanAccessRequest {
        val context = LocalContext.current
        val activity = remember(context) { context.lanActivity() }
        val latest by rememberUpdatedState(onResult)
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                latest(
                    when {
                        ok -> LanAccess.Granted
                        activity?.shouldShowRequestPermissionRationale(LOCAL_NETWORK) == true -> LanAccess.Denied
                        else -> LanAccess.Blocked
                    }
                )
            }
        return remember(context, launcher) {
            LanAccessRequest {
                if (!needsPermission() || context.checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
                    latest(LanAccess.Granted)
                else launcher.launch(LOCAL_NETWORK)
            }
        }
    }

    override fun openAccessSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", application.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            application.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // The screen keeps its manual-entry way forward.
        }
    }

    override fun scan(extraPorts: Set<Int>): Flow<LanFinding> =
        channelFlow {
            withTimeoutOrNull(TOTAL_CAP_MS) {
                coroutineScope {
                    launch { advertised(this@channelFlow) }
                    launch { sweep(this@channelFlow, extraPorts) }
                }
            }
        }
            .flowOn(dispatchers.default)

    // ---- sweep: the phone's own /24 (or /22) -----------------------------------------------------

    private data class Wifi(val address: String, val prefix: Int)

    private fun wifi(): Wifi? {
        val manager = application.getSystemService(ConnectivityManager::class.java) ?: return null
        val active = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(active) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        // A VPN over Wi-Fi (Tailscale) is the default network and carries the Wi-Fi transport, but its own address is
        // a /32 on the tunnel: the subnet to sweep is the Wi-Fi network's underneath it.
        val network =
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) active
            else
                @Suppress("DEPRECATION") // allNetworks: the one call that lists the VPN's underlying Wi-Fi on API 26+
                manager.allNetworks.firstOrNull { candidate ->
                    manager.getNetworkCapabilities(candidate)?.let {
                        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    } == true
                } ?: return null
        val link = manager.getLinkProperties(network) ?: return null
        val own = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return Wifi(own.address.hostAddress ?: return null, own.prefixLength)
    }

    private suspend fun sweep(out: ProducerScope<LanFinding>, extraPorts: Set<Int>) {
        val net = wifi() ?: return
        val hosts = LanSubnet.hosts(net.address, net.prefix) ?: return
        val ports = LanSubnet.ports(extraPorts)
        val gate = Semaphore(CONCURRENT_PROBES)
        coroutineScope {
            for (host in hosts) for (port in ports) {
                launch {
                    gate.withPermit {
                        // Defence in depth: nothing outside the phone's own subnet is contacted.
                        if (!LanSubnet.contains(net.address, net.prefix, host)) return@withPermit
                        probe(host, port, source = LanSource.Scanned, keepUnknown = false)?.let { out.send(it) }
                    }
                }
            }
        }
    }

    /** One address: a quick TCP connect, then the protocol probe without credentials. */
    private suspend fun probe(host: String, port: Int, source: LanSource, keepUnknown: Boolean, name: String? = null): LanFinding? {
        if (!open(host, port)) return if (keepUnknown) LanFinding(host, port, name, source = source) else null
        val origin = (AddressRules.normalize("http://$host:$port") as? Outcome.Ok)?.value ?: return null
        if (!AddressRules.isPrivateNetwork(origin.host)) return null
        // The consent stamp lets this one disposable, credential-less probe use plain HTTP on the
        // local network; it is not stored and does not trust the origin for a later connection.
        val result =
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                oc2.probe(ProbeTarget(origin, ConnectionCredentials.None, ProfileTrust(null, 0L)))
            }
        return when (result) {
            is Outcome.Ok -> LanFinding(host, port, name, version = result.value.serverVersion, source = source)
            is Outcome.Failed ->
                when {
                    result.failure is Failure.SignIn ||
                        (result.failure as? Failure.Server)?.status.let { it == 401 || it == 403 } ->
                        LanFinding(host, port, name, needsPassword = true, source = source)
                    keepUnknown -> LanFinding(host, port, name, source = source)
                    else -> null
                }
            null -> if (keepUnknown) LanFinding(host, port, name, source = source) else null
        }
    }

    private suspend fun open(host: String, port: Int): Boolean =
        withContext(dispatchers.io) {
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS); true }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }

    // ---- advertised: OpenCode 1 --mdns, `_http._tcp` ----------------------------------------------

    @Suppress("DEPRECATION")
    private suspend fun advertised(out: ProducerScope<LanFinding>) {
        val manager = application.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val discovery =
            object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) { found.trySend(info) }
                override fun onServiceLost(info: NsdServiceInfo) = Unit
                override fun onDiscoveryStarted(type: String) = Unit
                override fun onDiscoveryStopped(type: String) = Unit
                override fun onStartDiscoveryFailed(type: String, code: Int) { found.close() }
                override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            }
        try {
            manager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (_: Exception) {
            return
        }
        try {
            // Resolving is one at a time (the platform refuses overlapping resolves).
            for (service in found) {
                val resolved = resolve(manager, service) ?: continue
                val address = resolved.host as? Inet4Address ?: continue
                val ip = address.hostAddress ?: continue
                val named = LanFindings.looksLikeOpenCode(resolved.serviceName)
                val finding =
                    probe(ip, resolved.port, LanSource.Advertised, keepUnknown = named, name = LanFindings.nameFrom(resolved.serviceName))
                if (finding != null) out.send(finding)
            }
        } finally {
            try {
                manager.stopServiceDiscovery(discovery)
            } catch (_: Exception) {
            }
            found.close()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(manager: NsdManager, service: NsdServiceInfo): NsdServiceInfo? =
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                try {
                    manager.resolveService(
                        service,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                                if (cont.isActive) cont.resumeWith(Result.success(null))
                            }
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                if (cont.isActive) cont.resumeWith(Result.success(info))
                            }
                        },
                    )
                } catch (_: Exception) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
            }
        }

    private companion object {
        /** Android 16 only opts apps in; the permission is enforced from Android 17 (API 37). */
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val TOTAL_CAP_MS = 6_000L
        const val CONNECT_TIMEOUT_MS = 300
        const val PROBE_TIMEOUT_MS = 1_500L
        const val RESOLVE_TIMEOUT_MS = 2_000L
        const val CONCURRENT_PROBES = 32

        fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= 37
    }
}

private tailrec fun Context.lanActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.lanActivity()
        else -> null
    }
