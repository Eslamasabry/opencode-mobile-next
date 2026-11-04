package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Server management is separate from app preferences. */
@Serializable
public sealed interface ServersRoute : AppRoute {
    @Serializable public data object Home : ServersRoute

    @Serializable public data class Setup(val profileId: ProfileId) : ServersRoute
}
