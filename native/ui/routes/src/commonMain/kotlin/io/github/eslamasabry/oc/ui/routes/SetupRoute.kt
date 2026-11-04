package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

@Serializable
public sealed interface SetupRoute : AppRoute {
    @Serializable public data class Home(val profile: ProfileId) : SetupRoute
}
