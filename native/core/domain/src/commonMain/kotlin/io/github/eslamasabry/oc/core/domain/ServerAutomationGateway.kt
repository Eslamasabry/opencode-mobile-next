package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerAutomation
import kotlinx.collections.immutable.ImmutableList

/**
 * Inspect, pause, resume and delete server-side automations (B-605). Gated on
 * `ServerCapabilities.serverAutomations`; defaults answer `Failure.Unavailable("serverAutomations")`.
 */
public interface ServerAutomationGateway {
    public suspend fun listAutomations(
        sessionId: SessionId? = null,
        directory: String? = null,
    ): Outcome<ImmutableList<ServerAutomation>> = unavailable("serverAutomations")

    public suspend fun automation(id: String): Outcome<ServerAutomation> =
        unavailable("serverAutomations")

    public suspend fun pauseAutomation(id: String): Outcome<ServerAutomation> =
        unavailable("serverAutomations")

    public suspend fun resumeAutomation(id: String): Outcome<ServerAutomation> =
        unavailable("serverAutomations")

    public suspend fun deleteAutomation(id: String): Outcome<Unit> =
        unavailable("serverAutomations")
}
