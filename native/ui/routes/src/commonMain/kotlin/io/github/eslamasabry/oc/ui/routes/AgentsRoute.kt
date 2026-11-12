package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Settings › Agents (plan 07 §5 G): agents on this phone and on the person's computers (Paseo). */
@Serializable
public sealed interface AgentsRoute : AppRoute {
    @Serializable public data object Home : AgentsRoute

    /**
     * One phone agent's own sheet (install, sign in, check, resume, use). [forNewChat]: opened from New conversation's
     * "Set up" row, so it steps back there by itself once the agent is ready (F: `agent_sheet.dart` returns after sign-in).
     */
    @Serializable
    public data class Agent(val agentId: String, val forNewChat: Boolean = false) : AgentsRoute

    @Serializable
    public data class SignInTerminal(val owner: ProfileId, val agentId: String) : AgentsRoute
}
