package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * "Set up OpenCode on this phone" (BUILD-RUNTIME-B; Flutter phone_setup start + progress screens). Owned by
 * :feature:setup. One page: the start page while no job exists, the live job once one does. Shown only with builtinLinux.
 */
@Serializable
public sealed interface SetupRoute : AppRoute {
    @Serializable public data object Phone : SetupRoute

    /** STATE: the setup assistant for one saved server. */
    @Serializable public data class Home(val profile: ProfileId) : SetupRoute
}
