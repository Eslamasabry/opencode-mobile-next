package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.AgentRecoverySnapshot

/** Optional cheap recovery facts from the gateway's existing cache and live turn state. */
public interface CachedAgentRecoveryGateway {
    /**
     * Reads an immutable snapshot synchronously. Never performs network I/O, loads or wakes an
     * agent, or changes transport state. Null means unknown or closed, so no coverage is inferred.
     */
    public fun cachedAgentRecovery(session: SessionId): AgentRecoverySnapshot?
}
