package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerProfile

/** Resolve and register secrets immediately before opening a profile's transport. */
public fun interface ConnectionCredentialSource {
    public suspend fun resolve(profile: ServerProfile): Outcome<ConnectionCredentials>
}
