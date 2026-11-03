package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Owned by :feature:integrations (unit BUILD-INTEGRATIONS-A). */
@Serializable
public sealed interface IntegrationsRoute : AppRoute {
    /** The connectors (MCP servers) one server has at its current location; null = the active server. */
    @Serializable public data class Connectors(val profile: ProfileId? = null) : IntegrationsRoute

    /** The public MCP registry as switches for one server (consent first); null = the active server. */
    @Serializable public data class Catalogue(val profile: ProfileId? = null) : IntegrationsRoute
}
