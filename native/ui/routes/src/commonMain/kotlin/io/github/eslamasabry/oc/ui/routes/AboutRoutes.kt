package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Settings › Help › Tips and Settings › Help › About › Open source notices (owned by :feature:about, P12). */
@Serializable
public sealed interface AboutRoute : AppRoute {
    @Serializable public data object Tips : AboutRoute

    @Serializable public data object Notices : AboutRoute
}
