package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

@Serializable
public data class McpCatalogRoute(
    val profile: ProfileId,
    val directory: String? = null,
    val workspace: String? = null,
) : AppRoute

@Serializable
public data class SetupReviewRoute(
    val profile: ProfileId,
    val directory: String? = null,
    val workspace: String? = null,
) : AppRoute
