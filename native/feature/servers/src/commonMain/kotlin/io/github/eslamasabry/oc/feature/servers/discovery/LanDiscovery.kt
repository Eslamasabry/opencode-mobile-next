package io.github.eslamasabry.oc.feature.servers.discovery

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** What the person decided when the system asked for local-network access. */
public enum class LanAccess {
    Granted,

    /** Refused this time; asking again is honest. */
    Denied,

    /** Refused for good: only the system's app settings can change it. */
    Blocked,
}

/** A prepared "ask for access" step; [ask] answers through the callback given to [LanDiscovery.rememberAccess]. */
public fun interface LanAccessRequest {
    public fun ask()
}

/**
 * Finding OpenCode servers on the phone's own network. Common code owns the screen and the result
 * merging; the platform owns sockets, the network-service API and the permission.
 *
 * Contract: only addresses inside the phone's own subnet are contacted, no credential is ever sent,
 * and [scan] ends by itself within a few seconds (the flow completes) or when its collector stops.
 */
public interface LanDiscovery {
    /** False where there is no local network to look at (desktop, tests). */
    public val supported: Boolean

    /**
     * Remembers a request for local-network access for this composition. [onResult] runs with the
     * system's answer; when nothing needs asking it is called with [LanAccess.Granted] at once.
     */
    @Composable public fun rememberAccess(onResult: (LanAccess) -> Unit): LanAccessRequest

    /** Opens the app's system settings so a blocked permission can be changed. */
    public fun openAccessSettings()

    /**
     * Emits each computer as it is found (advertised and scanned ones are merged by the caller).
     * [extraPorts] are ports the person already used.
     */
    public fun scan(extraPorts: Set<Int>): Flow<LanFinding>
}

/** The default: nothing to look at on this platform. */
@ContributesBinding(AppScope::class)
@Inject
public class NoLanDiscovery : LanDiscovery {
    override val supported: Boolean = false

    @Composable
    override fun rememberAccess(onResult: (LanAccess) -> Unit): LanAccessRequest =
        LanAccessRequest { onResult(LanAccess.Denied) }

    override fun openAccessSettings(): Unit = Unit

    override fun scan(extraPorts: Set<Int>): Flow<LanFinding> = emptyFlow()
}
