package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * Slice-0 skeleton of the live gateway (plan 03 §10.4): two facets, enough for the P1 graph. The full facet lists
 * are frozen per slice in contracts/<slice>.md from lib/domain/server_gateway/interfaces.dart.
 */
public interface HealthGateway {
    public suspend fun health(): Outcome<Unit>
}

public interface SessionGateway {
    public suspend fun sessions(): Outcome<ImmutableList<Session>>

    public suspend fun session(id: SessionId): Outcome<Session>
}

public interface ServerGateway : HealthGateway, SessionGateway {
    /** Computed from the probed server version, not fixed per backend. */
    public val capabilities: StateFlow<ServerCapabilities>

    public suspend fun close()
}

/** Every method has a default returning Unavailable, so a backend overrides only what it supports. */
public interface ServerOperationsGateway {
    public suspend fun forkSession(id: SessionId): Outcome<SessionId> = unavailable("sessionFork")
}

public fun unavailable(feature: String): Outcome<Nothing> = Outcome.Failed(Failure.Unavailable(FeatureId(feature)))

/** Backends plug in here (plan 03 S3): a Set multibinding, one factory per BackendKind. */
public interface GatewayFactory {
    public val backend: BackendKind

    public suspend fun connect(profile: ServerProfile): Outcome<GatewayPair>
}

public data class GatewayPair(val live: ServerGateway, val operations: ServerOperationsGateway)
