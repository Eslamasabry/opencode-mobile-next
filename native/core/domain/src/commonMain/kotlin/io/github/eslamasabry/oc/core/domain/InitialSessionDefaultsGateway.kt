package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection

/** A new empty conversation can carry local defaults into its first real turn. */
public interface InitialSessionDefaultsGateway {
    public suspend fun createSessionWithDefaults(defaults: SessionSelection): Outcome<Session>
}
