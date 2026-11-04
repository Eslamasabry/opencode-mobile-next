package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

@Serializable
public sealed interface ManageSpaceRoute : AppRoute {
    @Serializable public data object Home : ManageSpaceRoute
}
