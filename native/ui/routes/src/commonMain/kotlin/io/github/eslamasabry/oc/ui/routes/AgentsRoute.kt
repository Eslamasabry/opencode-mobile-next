package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Settings › Agents (plan 07 §5 G): agents on this phone and on the person's computers (Paseo). */
@Serializable
public sealed interface AgentsRoute : AppRoute {
    @Serializable public data object Home : AgentsRoute

    @Serializable
    public data class SignInTerminal(val owner: ProfileId, val agentId: String) : AgentsRoute
}
