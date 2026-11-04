package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** A server terminal in the project explicitly opened by the person. */
@Serializable
public sealed interface TerminalRoute : AppRoute {
    @Serializable
    public data class Home(val profile: ProfileId, val directory: String) : TerminalRoute
}
