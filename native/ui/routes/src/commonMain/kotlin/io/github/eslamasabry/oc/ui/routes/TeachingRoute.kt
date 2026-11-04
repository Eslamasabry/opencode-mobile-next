package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Help › Tips, including replay and Show tips again. */
@Serializable
public sealed interface TeachingRoute : AppRoute {
    @Serializable public data object Tips : TeachingRoute
}
