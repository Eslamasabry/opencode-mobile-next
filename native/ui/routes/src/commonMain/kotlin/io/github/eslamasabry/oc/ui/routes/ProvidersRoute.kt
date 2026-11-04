package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Provider sign-ins on the selected server. */
@Serializable public data class ProvidersRoute(val profile: ProfileId) : AppRoute
