package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile

/**
 * The transport credential seam (audit 03 BLOCKER 3, third item): protocol and net code read the password through
 * this interface at request time; the SecretStore adapter is bound above both modules. Every value read is
 * registered with the Redactor by the adapter before it is returned (ADR §9.7).
 */
public fun interface CredentialReader {
    /** The stored password for the profile, or null when none is stored. Storage failures are Failure.Storage. */
    public suspend fun password(): Outcome<String?>
}

/** What to probe: an address the person typed or a saved profile (plan 04 §7.7). */
public data class ProbeTarget(val origin: ServerOrigin, val username: String?, val credentials: CredentialReader)

/** What a probe proved about the server. */
public data class ProbeResult(
    val backend: BackendKind,
    val serverVersion: String?,
    /** The base this server version supports, before runtime latches. */
    val capabilities: ServerCapabilities,
)

/** Everything a factory needs to open a gateway pair. */
public data class GatewaySpec(
    val profile: ServerProfile,
    val credentials: CredentialReader,
    val location: ServerLocation = ServerLocation.Default,
    /** The probe of this connect, when one ran; lets the gateway skip a second version read. */
    val probe: ProbeResult? = null,
)

/** Backends plug in here (plan 03 S3): a Metro Set multibinding, one factory per [BackendKind]. */
public interface GatewayFactory {
    public val kind: BackendKind

    /** Reads health/version only; never sends a credential to an origin that is not trusted yet (B-SEC-124). */
    public suspend fun probe(target: ProbeTarget): Outcome<ProbeResult>

    /** No I/O until first use. Every coroutine the pair starts is a child of [scope]; closing the scope stops them. */
    public fun open(spec: GatewaySpec, scope: ConnectionCoroutineScope): GatewayPair
}

public data class GatewayPair(val live: ServerGateway, val operations: ServerOperationsGateway)
