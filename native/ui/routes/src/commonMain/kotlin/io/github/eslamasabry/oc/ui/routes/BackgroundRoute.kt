package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Settings › Notifications › Background: the live background connection (owned by :feature:background). */
@Serializable
public sealed interface BackgroundRoute : AppRoute {
    @Serializable public data object Home : BackgroundRoute
}
