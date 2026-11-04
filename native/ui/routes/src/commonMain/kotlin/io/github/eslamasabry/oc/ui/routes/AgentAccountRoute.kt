package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** The account at one connected official agent runtime; credentials stay on that runtime. */
@Serializable public data class AgentAccountRoute(val profile: ProfileId) : AppRoute
