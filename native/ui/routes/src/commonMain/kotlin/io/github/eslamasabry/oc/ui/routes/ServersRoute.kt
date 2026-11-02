package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Server management is separate from app preferences. */
@Serializable
public sealed interface ServersRoute : AppRoute {
    @Serializable public data object Home : ServersRoute
}
