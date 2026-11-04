package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * One exact form in its captured connection location; global elicitation never becomes a synthetic
 * chat.
 */
@Serializable
public data class ConnectionFormRoute(
    val profile: ProfileId,
    val formId: String,
    val sessionId: String = "global",
    val directory: String? = null,
    val workspace: String? = null,
) : AppRoute {
    override val motion: RouteMotion
        get() = RouteMotion.SlideUp
}
