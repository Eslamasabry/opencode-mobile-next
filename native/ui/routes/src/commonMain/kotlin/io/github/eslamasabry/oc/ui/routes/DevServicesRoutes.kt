package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * Owned by :feature:devservices (BUILD-RUNTIME-A). Development services of one project folder on one server: the
 * saved commands this app starts and controls, and the dev servers found running another way (Flutter
 * DevelopmentServicesScreen, reached from the project page).
 */
@Serializable
public sealed interface DevServicesRoute : AppRoute {
    @Serializable public data class Home(val profile: ProfileId, val directory: String) : DevServicesRoute
}
