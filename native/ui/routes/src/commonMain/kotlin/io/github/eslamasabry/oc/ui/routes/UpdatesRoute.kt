package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Settings › This app › Updates: the app's own update (owned by :feature:updates). */
@Serializable
public sealed interface UpdatesRoute : AppRoute {
    @Serializable public data object Home : UpdatesRoute
}
