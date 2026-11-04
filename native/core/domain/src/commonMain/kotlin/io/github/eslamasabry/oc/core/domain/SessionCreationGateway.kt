package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation

/**
 * Optional only for backends that accept a client-selected session id. No create is retried here.
 */
public interface SessionCreationGateway {
    public fun newSessionCreationId(): SessionId

    public suspend fun createSessionWithId(
        id: SessionId,
        location: ServerLocation,
    ): Outcome<SessionId>

    /** An exact read; absence remains unconfirmed and never authorizes another create. */
    public suspend fun lookupCreatedSession(id: SessionId): Outcome<Boolean>
}
