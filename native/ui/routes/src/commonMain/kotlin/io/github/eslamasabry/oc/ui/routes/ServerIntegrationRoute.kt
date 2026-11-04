package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

@Serializable
public enum class ServerIntegrationPage {
    Providers,
    Tools,
    Commands,
    Skills,
    References,
    Mcp,
    Permissions,
    Setup,
    Host,
    Plugins,
}

/**
 * Integration doors always name the saved server; switching the active server cannot retarget them.
 */
@Serializable
public data class ServerIntegrationRoute(val profile: ProfileId, val page: ServerIntegrationPage) :
    AppRoute

@Serializable
public data class ServerProviderAuthRoute(val profile: ProfileId, val provider: String) : AppRoute
