package io.github.eslamasabry.oc.feature.agents.hub

/** Where an agent's tile stands; the words live in the screen. */
public enum class AgentTileState {
    Ready,
    SignInNeeded,
    NotInstalled,
    Installing,
    CannotRun,
    Checking,
    PhoneCheck,
    Stopped,
    Limit,
    Unavailable,
}

/** The tile's one small act, only when it matters. */
public enum class AgentTileAct {
    Install,
    SignIn,
    /** Run the phone check for this agent (Flutter's "Check" chip). */
    Check,
    /** Start the stopped agent helper again (Flutter's "Resume" chip). */
    Resume,
}

/** One phone agent as the bento grid draws it: its state, the install's measured share, and at most one act. */
public data class AgentTileModel(
    val agent: PhoneAgentRow,
    val state: AgentTileState,
    /** 0..1 from measured bytes while installing; null before a total is known. */
    val fraction: Double? = null,
    val act: AgentTileAct? = null,
    /** Installed in this visit but not yet usable: the sheet says sign-in and the phone check are still needed. */
    val justInstalled: Boolean = false,
)

/**
 * The tile for [agent] given the phone's current [installation] (Flutter `agentRowLine` and its one fix chip).
 * [checking] is this agent's phone check running and [resuming] is the helper being started again: both read as
 * "Checking…" and offer no act until they end.
 */
public fun agentTile(
    agent: PhoneAgentRow,
    installation: PhoneAgentInstallation?,
    checking: Boolean = false,
    resuming: Boolean = false,
): AgentTileModel {
    val mine = installation?.takeIf { it.id == agent.id }
    if (mine?.phase == PhoneAgentInstallPhase.Installing)
        return AgentTileModel(agent, AgentTileState.Installing, fraction = mine.fraction?.coerceIn(0.0, 1.0))
    val justInstalled = mine?.phase == PhoneAgentInstallPhase.Done && agent.status != PhoneAgentStatus.Ready
    return when (agent.status) {
        PhoneAgentStatus.NotInstalled ->
            if (agent.supportedInstallation) AgentTileModel(agent, AgentTileState.NotInstalled, act = AgentTileAct.Install)
            else AgentTileModel(agent, AgentTileState.CannotRun)
        PhoneAgentStatus.SignInNeeded ->
            AgentTileModel(
                agent,
                AgentTileState.SignInNeeded,
                act = if (agent.signInAvailable) AgentTileAct.SignIn else null,
                justInstalled = justInstalled,
            )
        PhoneAgentStatus.Ready -> AgentTileModel(agent, AgentTileState.Ready)
        PhoneAgentStatus.Checking -> AgentTileModel(agent, AgentTileState.Checking, justInstalled = justInstalled)
        PhoneAgentStatus.PhoneCheck ->
            if (checking) AgentTileModel(agent, AgentTileState.Checking, justInstalled = justInstalled)
            else AgentTileModel(agent, AgentTileState.PhoneCheck, act = AgentTileAct.Check, justInstalled = justInstalled)
        PhoneAgentStatus.Stopped ->
            if (resuming) AgentTileModel(agent, AgentTileState.Checking)
            else AgentTileModel(agent, AgentTileState.Stopped, act = AgentTileAct.Resume)
        PhoneAgentStatus.Limit -> AgentTileModel(agent, AgentTileState.Limit)
        PhoneAgentStatus.Unavailable -> AgentTileModel(agent, AgentTileState.Unavailable)
    }
}
