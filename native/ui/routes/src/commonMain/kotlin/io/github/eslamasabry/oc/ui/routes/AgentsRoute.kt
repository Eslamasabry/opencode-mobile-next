package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Settings › Agents (plan 07 §5 G): agents on this phone and on the person's computers (Paseo). */
@Serializable
public sealed interface AgentsRoute : AppRoute {
    @Serializable
    public data class SignInTerminal(
        val owner: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        val agentId: String,
    ) : AgentsRoute

    @Serializable public data object Home : AgentsRoute
}
