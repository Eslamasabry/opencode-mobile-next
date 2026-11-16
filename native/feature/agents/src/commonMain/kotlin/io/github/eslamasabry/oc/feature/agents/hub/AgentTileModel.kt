package io.github.eslamasabry.oc.feature.agents.hub

/** Where an agent's tile stands; the words live in the screen. */
public enum class AgentTileState {
    Ready,
    /** Installed; set it up the first time it is used ([PhoneAgentStatus.SetUpAtFirstUse]). */
    SetUp,
    SignInNeeded,
    NotInstalled,
    Installing,
    /** The last install of this agent failed or was cut short: the reason and Try again stay until either is used. */
    InstallFailed,
    CannotRun,
    Checking,
    PhoneCheck,
    Stopped,
    Limit,
    Unavailable,
    /**
     * Ready, but the helper every phone agent runs on did not start (the page's start failed and none runs now): the
     * agent itself is fine, the phone's agents are not running. Its act starts the helper again.
     */
    HelperDown,
}

/** The tile's one small act, only when it matters. */
public enum class AgentTileAct {
    Install,
    SignIn,
    /** Open the agent's own set-up in the sign-in terminal ([AgentTileState.SetUp]). */
    SetUp,
    /** Run the phone check for this agent (Flutter's "Check" chip). */
    Check,
    /**
     * signin-truth: the sign-in could not be confirmed (its check gave no answer): check it again now ("Check Claude Code
     * again"), never a sign-in on a guess.
     */
    CheckSignIn,
    /** Start the stopped agent helper again (Flutter's "Resume" chip). */
    Resume,
    /** Install again after a failed or interrupted install: opens the install review, as Install does. */
    Retry,
    /** Start the phone's agent helper again after it did not start ([AgentTileState.HelperDown]). */
    StartAgain,
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
    /** This agent's install while it runs (its step and bytes) or after it failed (its reason); else null. */
    val install: PhoneAgentInstallation? = null,
    /** The running install has shown nothing new for a while: the tile says it is still working. */
    val quiet: Boolean = false,
)

/**
 * The one status of a phone agent that every place shows (issue 147, second report: Antigravity read Ready in New
 * conversation's agent list while the same page asked to set it up). The Agents page's tile ([agentTile]), New
 * conversation's "Who works on it" ([phoneAgentChoosable], [phoneAgentListedWords]) and its set-up rows all read it.
 */
public enum class PhoneAgentShown {
    NotInstalled,
    Installing,
    /** Installed; its own set-up comes first (never seen set up, or its phone check is due). */
    SetUp,
    /** Installed and seen set up; no sign-in can be read ahead, so it reads "set it up the first time you use it". */
    SetUpAtFirstUse,
    SignInNeeded,
    /** Its state is being read. */
    Checking,
    /** The helper is stopped: it can be chosen, and a start starts the helper first. */
    Starting,
    Ready,
    /** Ready, but the phone's agent helper did not start. */
    HelperDown,
    Unavailable,
}

/** [agent]'s one status ([PhoneAgentShown]) given the running [installation] and whether the helper did not start. */
public fun phoneAgentShown(
    agent: PhoneAgentRow,
    installation: PhoneAgentInstallation? = null,
    helperDown: Boolean = false,
): PhoneAgentShown {
    if (installation?.id == agent.id && installation.phase == PhoneAgentInstallPhase.Installing)
        return PhoneAgentShown.Installing
    return when (agent.status) {
        PhoneAgentStatus.NotInstalled -> PhoneAgentShown.NotInstalled
        PhoneAgentStatus.SetUpAtFirstUse ->
            when {
                agent.needsSetUp -> PhoneAgentShown.SetUp
                helperDown -> PhoneAgentShown.HelperDown
                else -> PhoneAgentShown.SetUpAtFirstUse
            }
        PhoneAgentStatus.SignInNeeded -> PhoneAgentShown.SignInNeeded
        PhoneAgentStatus.Ready -> if (helperDown) PhoneAgentShown.HelperDown else PhoneAgentShown.Ready
        PhoneAgentStatus.Checking -> PhoneAgentShown.Checking
        PhoneAgentStatus.Stopped -> PhoneAgentShown.Starting
        PhoneAgentStatus.PhoneCheck -> PhoneAgentShown.SetUp
        PhoneAgentStatus.Limit,
        PhoneAgentStatus.Unavailable -> PhoneAgentShown.Unavailable
    }
}

/** Whether [shown] can start a conversation (a start of a [PhoneAgentShown.Starting] one starts the helper itself). */
public fun phoneAgentChoosable(shown: PhoneAgentShown): Boolean =
    shown == PhoneAgentShown.Ready || shown == PhoneAgentShown.SetUpAtFirstUse || shown == PhoneAgentShown.Starting

/**
 * The tile for [agent] given the phone's current [installation] (Flutter `agentRowLine` and its one fix chip).
 * [checking] is this agent's phone check running and [resuming] is the helper being started again: both read as
 * "Checking…" and offer no act until they end. [helperDown] is the page's failed start of the agent helper.
 * [quiet] is a running install that has shown nothing new for a while. A failed or cut-short install of an agent that
 * is still not installed reads as that failure, with Try again, never as a plain "Not installed".
 */
public fun agentTile(
    agent: PhoneAgentRow,
    installation: PhoneAgentInstallation?,
    checking: Boolean = false,
    resuming: Boolean = false,
    helperDown: Boolean = false,
    quiet: Boolean = false,
): AgentTileModel {
    val mine = installation?.takeIf { it.id == agent.id }
    if (mine?.phase == PhoneAgentInstallPhase.Installing)
        return AgentTileModel(
            agent,
            AgentTileState.Installing,
            fraction = mine.fraction?.coerceIn(0.0, 1.0),
            install = mine,
            quiet = quiet,
        )
    val ended = mine?.phase == PhoneAgentInstallPhase.Failed || mine?.phase == PhoneAgentInstallPhase.Interrupted
    if (ended && agent.status == PhoneAgentStatus.NotInstalled && agent.supportedInstallation)
        return AgentTileModel(agent, AgentTileState.InstallFailed, act = AgentTileAct.Retry, install = mine)
    val justInstalled = mine?.phase == PhoneAgentInstallPhase.Done && agent.status != PhoneAgentStatus.Ready
    // The one status every place shows ([phoneAgentShown]); the tile adds its act and its own detail words.
    return when (phoneAgentShown(agent, installation, helperDown)) {
        PhoneAgentShown.Installing -> AgentTileModel(agent, AgentTileState.Installing, install = mine, quiet = quiet)
        PhoneAgentShown.NotInstalled ->
            if (agent.supportedInstallation) AgentTileModel(agent, AgentTileState.NotInstalled, act = AgentTileAct.Install)
            else AgentTileModel(agent, AgentTileState.CannotRun)
        PhoneAgentShown.SetUp ->
            if (agent.status == PhoneAgentStatus.PhoneCheck)
                if (checking) AgentTileModel(agent, AgentTileState.Checking, justInstalled = justInstalled)
                else AgentTileModel(agent, AgentTileState.PhoneCheck, act = AgentTileAct.Check, justInstalled = justInstalled)
            else
                AgentTileModel(
                    agent,
                    AgentTileState.SetUp,
                    act = if (agent.signInAvailable) AgentTileAct.SetUp else null,
                    justInstalled = justInstalled,
                )
        PhoneAgentShown.SetUpAtFirstUse ->
            AgentTileModel(
                agent,
                AgentTileState.SetUp,
                act = if (agent.signInAvailable) AgentTileAct.SetUp else null,
                justInstalled = justInstalled,
            )
        PhoneAgentShown.SignInNeeded ->
            AgentTileModel(
                agent,
                AgentTileState.SignInNeeded,
                act =
                    when {
                        // Not confirmed: nothing is known about the account, so the way forward is the check itself.
                        agent.signInCheckFailed -> AgentTileAct.CheckSignIn
                        agent.signInAvailable -> AgentTileAct.SignIn
                        else -> null
                    },
                justInstalled = justInstalled,
            )
        // The helper every phone agent runs on did not start: no agent can work, so none reads Ready. It says that, not
        // "Not available on this phone yet" (the agent is installed and fine), with Try again on the tile itself.
        PhoneAgentShown.HelperDown -> AgentTileModel(agent, AgentTileState.HelperDown, act = AgentTileAct.StartAgain)
        PhoneAgentShown.Ready -> AgentTileModel(agent, AgentTileState.Ready)
        PhoneAgentShown.Checking -> AgentTileModel(agent, AgentTileState.Checking, justInstalled = justInstalled)
        PhoneAgentShown.Starting ->
            if (resuming) AgentTileModel(agent, AgentTileState.Checking)
            else AgentTileModel(agent, AgentTileState.Stopped, act = AgentTileAct.Resume)
        PhoneAgentShown.Unavailable ->
            if (agent.status == PhoneAgentStatus.Limit) AgentTileModel(agent, AgentTileState.Limit)
            else AgentTileModel(agent, AgentTileState.Unavailable)
    }
}

/** The page's own start of the agent helper failed and no start runs now: the tiles cannot say Ready. */
public fun phoneHelperDown(state: AgentsUi): Boolean = state.helperConnectFailed && !state.helperConnecting

/**
 * Whether a Ready tile offers New conversation: the phone's agents are connected (by this page or another way) and
 * the page is not showing that the helper did not start. Before, a saved but unconnected helper was enough, so the
 * tiles offered conversations right under "the agent helper did not start".
 */
public fun phoneChatOffered(state: AgentsUi): Boolean {
    val helper = state.phone.conversations ?: return false
    if (phoneHelperDown(state)) return false
    return state.helperConnected || state.computers.any { it.id == helper && it.connected }
}

/**
 * The one rule for whether a phone agent can start a conversation now. The Agents page's card offers New conversation
 * by it ([cardOffersConversation]) and New conversation's "Who works on it" lists by it (the app's phone offer), so
 * the two can never disagree. An agent set up at first use starts one only once the app has seen it set up
 * ([PhoneAgentRow.needsSetUp] false); before that its set-up leads (the card's Set up, New conversation's set-up row).
 */
public fun phoneAgentStartsConversation(row: PhoneAgentRow): Boolean =
    phoneAgentShown(row).let { it == PhoneAgentShown.Ready || it == PhoneAgentShown.SetUpAtFirstUse }

/** Whether [tile]'s card offers New conversation, given [chat] ([phoneChatOffered] for the page). */
public fun cardOffersConversation(tile: AgentTileModel, chat: Boolean): Boolean =
    chat &&
        (tile.state == AgentTileState.Ready || tile.state == AgentTileState.SetUp) &&
        phoneAgentStartsConversation(tile.agent)
