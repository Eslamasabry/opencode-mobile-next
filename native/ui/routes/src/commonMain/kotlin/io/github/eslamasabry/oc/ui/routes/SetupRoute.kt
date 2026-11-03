package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * "Set up OpenCode on this phone" (BUILD-RUNTIME-B; Flutter phone_setup start + progress screens). Owned by
 * :feature:setup. One page: the start page while no job exists, the live job once one does. Shown only with builtinLinux.
 */
@Serializable
public sealed interface SetupRoute : AppRoute {
    @Serializable public data object Phone : SetupRoute
}
