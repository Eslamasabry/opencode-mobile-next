package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Credentials already resolved for one connection (plan 03 §10.7, plan 04 §4.8). Built by
 * `:engine:connection` through the matching ProfileSource, which reads SecretStore and registers
 * every value with the Redactor BEFORE handing it over. The transport never reads SecretStore; a
 * changed password reaches it by reconnecting with new credentials.
 *
 * [username]: the Basic user (OpenCode 2) when the profile has one. [secret]: the password, token
 * or device token; null = none stored. `toString()` never prints the secret.
 */
public class ConnectionCredentials(public val username: String?, public val secret: String?) {
    override fun equals(other: Any?): Boolean =
        other is ConnectionCredentials && other.username == username && other.secret == secret

    override fun hashCode(): Int = 31 * (username?.hashCode() ?: 0) + (secret?.hashCode() ?: 0)

    override fun toString(): String =
        "ConnectionCredentials(username=${username != null}, secret=${secret != null})"

    public companion object {
        /** Nothing stored (a loopback server without a password, a demo graph, slice-0 graphs). */
        public val None: ConnectionCredentials = ConnectionCredentials(null, null)
    }
}

/** What to probe: an address the person typed or a saved profile (plan 04 §7.7). */
public data class ProbeTarget(
    val origin: ServerOrigin,
    /**
     * Null until the origin is trusted: a probe never sends a credential to an untrusted origin
     * (B-SEC-124).
     */
    val credentials: ConnectionCredentials?,
)

/** What a probe proved about the server. */
public data class ProbeResult(
    val backend: BackendKind,
    val serverVersion: String?,
    /** The base this server version supports, before runtime latches. */
    val capabilities: ServerCapabilities,
)

/**
 * Everything a factory needs to open a gateway pair (plan 04 §11.1). Fields the plan adds with
 * their owning units — `permit: TransportPermit` (S1-SECNET), `serverRunsTools`, `options` (the
 * connection engine) — are appended here, with defaults, when those types exist.
 */
public data class GatewaySpec(
    val profile: ServerProfile,
    val credentials: ConnectionCredentials,
    /**
     * The graph's scope: every coroutine the pair starts is a child of it; closing it stops them.
     */
    val scope: ConnectionCoroutineScope,
    /** The restored location (plan 04 §12.2 step 4). */
    val location: ServerLocation = ServerLocation.Default,
    /** The probe of this connect, when one ran; lets the gateway skip a second version read. */
    val probe: ProbeResult? = null,
    /**
     * N4-D21 (plan 04 §12.9): whether the gateway's event stream (and sockets) may reconnect on
     * their own. The protocol passes it to `EventStreamLoop`; false keeps a live stream, schedules
     * no try after a drop and lets only a person's Try again dial (B-PROTO-146; review w1-core-net
     * MAJOR 7). Default: always allowed.
     */
    val reconnectAllowed: StateFlow<Boolean> = AlwaysAllowed,
    /** Bounded read clients (the profile monitor) need no event subscription. */
    val observeEvents: Boolean = true,
)

private val AlwaysAllowed: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

/**
 * Backends plug in here (plan 03 S3): a Metro Set multibinding, one factory per [BackendKind] (plan
 * 04 §11.1).
 */
public interface GatewayFactory {
    public val backend: BackendKind

    /**
     * Reads health/version only; never sends a credential to an origin that is not trusted yet
     * (B-SEC-124).
     */
    public suspend fun probe(target: ProbeTarget): Outcome<ProbeResult>

    /**
     * OpenCode 2: no network I/O until first use. Paseo/Codex: opens the socket and runs
     * hello/initialize, so this can fail with a typed Failure. Each protocol builds its own
     * transport from the spec; nothing above `:protocol:*` sees one.
     */
    public suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair>
}

public data class GatewayPair(val live: ServerGateway, val operations: ServerOperationsGateway)
