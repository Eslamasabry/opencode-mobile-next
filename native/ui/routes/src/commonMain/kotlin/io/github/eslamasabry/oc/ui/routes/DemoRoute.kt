package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** The isolated, offline production-chat demonstration (plan 07 C39). */
@Serializable
public data object DemoRoute : AppRoute {
    override val motion: RouteMotion
        get() = RouteMotion.SharedAxisX
}
