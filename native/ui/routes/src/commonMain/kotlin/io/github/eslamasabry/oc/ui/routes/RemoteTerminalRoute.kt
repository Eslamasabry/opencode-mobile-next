package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** The selected server's authenticated, interactive terminal sessions. */
@Serializable public data class RemoteTerminalRoute(val profile: ProfileId) : AppRoute
