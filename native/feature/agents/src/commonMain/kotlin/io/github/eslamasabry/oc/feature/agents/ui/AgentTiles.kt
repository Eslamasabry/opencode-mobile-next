package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.persona.AgentPersona
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileAct
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileModel
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileState
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.cardOffersConversation
import io.github.eslamasabry.oc.feature.agents.hub.phoneChatOffered
import io.github.eslamasabry.oc.feature.agents.hub.phoneHelperDown
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.agentTile
import io.github.eslamasabry.oc.feature.agents.hub.phoneInstalledAgents
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTile
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTileAction
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTileTone
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTiles
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsChecking
import io.github.eslamasabry.oc.ui.words.res.agentsChipSignIn
import io.github.eslamasabry.oc.ui.words.res.agentsFailArchitecture
import io.github.eslamasabry.oc.ui.words.res.agentsHelperKeyRefused
import io.github.eslamasabry.oc.ui.words.res.agentsHelperKeyUnreadable
import io.github.eslamasabry.oc.ui.words.res.agentsHelperNoAnswer
import io.github.eslamasabry.oc.ui.words.res.agentsHelperNoLinux
import io.github.eslamasabry.oc.ui.words.res.agentsHelperNotConnected
import io.github.eslamasabry.oc.ui.words.res.agentsHelperNotStarted
import io.github.eslamasabry.oc.ui.words.res.agentsHelperUpdateAction
import io.github.eslamasabry.oc.ui.words.res.agentsHelperUpdateRequired
import io.github.eslamasabry.oc.ui.words.res.agentsInstallAction
import io.github.eslamasabry.oc.ui.words.res.agentsInstallHint
import io.github.eslamasabry.oc.ui.words.res.agentsNewChat
import io.github.eslamasabry.oc.ui.words.res.agentsNewChatWith
import io.github.eslamasabry.oc.ui.words.res.agentsPreviousVersion
import io.github.eslamasabry.oc.ui.words.res.agentsSetupSizeNote
import io.github.eslamasabry.oc.ui.words.res.agentsSignInAction
import io.github.eslamasabry.oc.ui.words.res.agentsSignInDone
import io.github.eslamasabry.oc.ui.words.res.agentsSignOutAction
import io.github.eslamasabry.oc.ui.words.res.agentsStateChecking
import io.github.eslamasabry.oc.ui.words.res.agentsStateLimit
import io.github.eslamasabry.oc.ui.words.res.agentsStateNotInstalled
import io.github.eslamasabry.oc.ui.words.res.agentsStateNotInstalledNoSize
import io.github.eslamasabry.oc.ui.words.res.agentsStatePhoneCheck
import io.github.eslamasabry.oc.ui.words.res.agentsStateReady
import io.github.eslamasabry.oc.ui.words.res.agentsStateSignInNeeded
import io.github.eslamasabry.oc.ui.words.res.agentsStateStopped
import io.github.eslamasabry.oc.ui.words.res.agentsStateUnavailable
import io.github.eslamasabry.oc.ui.words.res.agentsTileInstalling
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.linuxAgentInstallBackground
import io.github.eslamasabry.oc.ui.words.res.linuxAgentInstallCancel
import io.github.eslamasabry.oc.ui.words.res.linuxAgentInstallDone
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadRemove
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.engine.agents.check.PhoneCheckFailure
import io.github.eslamasabry.oc.ui.words.res.agentsChipCheck
import io.github.eslamasabry.oc.ui.words.res.agentsCheckAction
import io.github.eslamasabry.oc.ui.words.res.agentsChipResume
import io.github.eslamasabry.oc.ui.words.res.agentsResumeAction
import io.github.eslamasabry.oc.ui.words.res.agentsCheckRunning
import io.github.eslamasabry.oc.ui.words.res.agentsCheckTitle
import io.github.eslamasabry.oc.ui.words.res.agentsFailBusy
import io.github.eslamasabry.oc.ui.words.res.agentsFailInstall
import io.github.eslamasabry.oc.ui.words.res.agentsFailVersion
import io.github.eslamasabry.oc.ui.words.res.agentsFailDaemon
import io.github.eslamasabry.oc.ui.words.res.agentsFailTimeout
import io.github.eslamasabry.oc.ui.words.res.agentsFailHelperVersion
import io.github.eslamasabry.oc.ui.words.res.agentsFailNeedsSignIn
import io.github.eslamasabry.oc.ui.words.res.agentsFailUnavailable
import io.github.eslamasabry.oc.ui.words.res.agentsFailStorage
import io.github.eslamasabry.oc.ui.words.res.agentsFailStale
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's agents as a bento grid (Flutter `KitAgentGrid` look, the Agents page's own tiles): a large mark, the
 * name and one state line per tile, at most one small act, and the whole tile opens the agent's sheet. It stands on its
 * own so the parts of the page around it can change without touching it.
 */
@Composable
internal fun PhoneAgentTiles(state: AgentsUi, actions: AgentsActions, modifier: Modifier = Modifier) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    if (state.phone.agents.isEmpty()) {
        KitText(stringResource(Res.string.agentsChecking), modifier.then(gutter))
        return
    }
    val tiles = state.phone.agents.map { tileOf(it, state) }
    // A ready agent starts a conversation once the phone's agents are connected (never beside "did not start").
    val chat = phoneChatOffered(state)
    KitAgentTiles(
        tiles.map { tile -> @Composable { AgentTile(tile, actions, chat, state.checkFailedAgent == tile.agent.id) } },
        modifier.then(gutter),
    )
}

/** A computer's ready agents as tiles, each with New chat on that computer. */
@Composable
internal fun ComputerAgentTiles(id: ProfileId, agents: List<String>, actions: AgentsActions, modifier: Modifier = Modifier) {
    KitAgentTiles(
        agents.map { name ->
            @Composable {
                KitAgentTile(
                    name = name,
                    state = stringResource(Res.string.agentsStateReady),
                    markId = AgentPersona.of(name).markSlug,
                    onClick = { actions.newConversation(id) },
                    ready = true,
                    action =
                        KitAgentTileAction(
                            stringResource(Res.string.agentsNewChat),
                            stringResource(Res.string.agentsNewChatWith, name),
                            icon = KitIcons.chat,
                        ) {
                            actions.newConversation(id)
                        },
                )
            }
        },
        modifier,
    )
}

/** The tile for [agent] on this page: a check or a restart that runs now reads "Checking…" on that tile. */
private fun tileOf(agent: PhoneAgentRow, state: AgentsUi): AgentTileModel =
    agentTile(
        agent,
        state.phone.installation,
        checking = state.checkingAgent == agent.id,
        resuming = state.helperConnecting,
        helperDown = phoneHelperDown(state),
    )

@Composable
private fun AgentTile(tile: AgentTileModel, actions: AgentsActions, chat: Boolean, checkFailed: Boolean) {
    val agent = tile.agent
    KitAgentTile(
        name = agent.name,
        state = tileWords(tile),
        markId = agent.id,
        onClick = { actions.openAgent(agent.id) },
        tone =
            when (tile.state) {
                AgentTileState.SignInNeeded,
                AgentTileState.PhoneCheck -> KitAgentTileTone.NeedsYou
                AgentTileState.CannotRun,
                AgentTileState.Unavailable -> KitAgentTileTone.Dimmed
                else -> KitAgentTileTone.Calm
            },
        progress = if (tile.state == AgentTileState.Installing) tileProgress(tile) else null,
        ready = tile.state == AgentTileState.Ready,
        action =
            when (tile.act) {
                AgentTileAct.Install ->
                    KitAgentTileAction(
                        stringResource(Res.string.agentsInstallHint),
                        stringResource(Res.string.agentsInstallAction, agent.name),
                        icon = KitIcons.download,
                    ) {
                        actions.requestInstall(agent.id)
                    }
                AgentTileAct.SignIn ->
                    KitAgentTileAction(
                        stringResource(Res.string.agentsChipSignIn),
                        stringResource(Res.string.agentsSignInAction, agent.name),
                        icon = KitIcons.login,
                        prominent = true,
                    ) {
                        actions.signInPhone(agent.id)
                    }
                AgentTileAct.Check ->
                    KitAgentTileAction(
                        stringResource(if (checkFailed) Res.string.kitTryAgain else Res.string.agentsChipCheck),
                        stringResource(Res.string.agentsCheckAction, agent.name),
                        icon = KitIcons.check,
                        prominent = true,
                    ) {
                        actions.checkPhoneAgent(agent.id)
                    }
                AgentTileAct.Resume ->
                    KitAgentTileAction(
                        stringResource(Res.string.agentsChipResume),
                        stringResource(Res.string.agentsResumeAction, agent.name),
                        icon = KitIcons.play,
                        prominent = true,
                    ) {
                        actions.resumePhoneAgent(agent.id)
                    }
                null ->
                    if (cardOffersConversation(tile, chat))
                        KitAgentTileAction(
                            stringResource(Res.string.agentsNewChat),
                            stringResource(Res.string.agentsNewChatWith, agent.name),
                            icon = KitIcons.chat,
                        ) {
                            actions.newPhoneConversation(agent.id)
                        }
                    else null
            },
    )
}

private fun tileProgress(tile: AgentTileModel): KitProgress =
    tile.fraction?.let { KitProgress.Known(it.toFloat()) } ?: KitProgress.Waiting()

/** The tile's one line (Flutter `agentRowLine`). */
@Composable
internal fun tileWords(tile: AgentTileModel): String {
    val agent = tile.agent
    return when (tile.state) {
        AgentTileState.Installing -> stringResource(Res.string.agentsTileInstalling)
        AgentTileState.NotInstalled ->
            agent.downloadBytes?.let { stringResource(Res.string.agentsStateNotInstalled, agentPayloadSize(it)) }
                ?: stringResource(Res.string.agentsStateNotInstalledNoSize)
        AgentTileState.CannotRun -> stringResource(Res.string.agentsFailArchitecture)
        AgentTileState.Ready -> stringResource(Res.string.agentsStateReady)
        AgentTileState.SignInNeeded -> stringResource(Res.string.agentsStateSignInNeeded)
        AgentTileState.Checking -> stringResource(Res.string.agentsStateChecking)
        AgentTileState.PhoneCheck -> stringResource(Res.string.agentsStatePhoneCheck)
        AgentTileState.Stopped -> stringResource(Res.string.agentsStateStopped)
        AgentTileState.Limit -> stringResource(Res.string.agentsStateLimit)
        AgentTileState.Unavailable -> stringResource(Res.string.agentsStateUnavailable)
    }
}

/**
 * The one quiet line after a real failed connection, with the reason in plain words and a single Try again. Nothing
 * while no agent is installed (nothing was tried), while connecting, or once connected.
 */
@Composable
internal fun PhoneAgentConnectLine(state: AgentsUi, actions: AgentsActions, modifier: Modifier = Modifier) {
    if (!state.helperConnectFailed || state.helperConnecting) return
    if (phoneInstalledAgents(state.phone).isEmpty() || state.phone.needsRestart) return
    // A helper that is missing or too old cannot start by trying again: its way forward is the update.
    val needsUpdate = (state.helperConnectFailure as? Failure.Runtime)?.problem == RuntimeProblem.HelperUpdateRequired
    KitStatusLine(
        connectFailureWords(state.helperConnectFailure),
        modifier,
        tone = StatusTone.Attention,
        action =
            if (needsUpdate)
                // The update is a page step: while another runs it reads as waiting for it, never a dropped press.
                KitLineAction(
                    stringResource(Res.string.agentsHelperUpdateAction),
                    actions::updatePhoneHelper,
                    enabled = !state.busy,
                    disabledReason = if (state.busy) stringResource(Res.string.serversWorking) else null,
                )
            else KitLineAction(stringResource(Res.string.kitTryAgain), actions::connectPhoneHelper),
        quiet = true,
    )
}

/** The real reason a connection failed, in plain words; the technical text never shows. */
@Composable
private fun connectFailureWords(failure: Failure?): String =
    stringResource(
        when (failure) {
            is Failure.Network,
            is Failure.TimedOut,
            is Failure.ComputerDidNotAnswer -> Res.string.agentsHelperNoAnswer
            is Failure.SignIn -> Res.string.agentsHelperKeyRefused
            is Failure.SecretUnavailable,
            is Failure.StorageRefused -> Res.string.agentsHelperKeyUnreadable
            is Failure.Runtime ->
                when (failure.problem) {
                    RuntimeProblem.NotInstalled -> Res.string.agentsHelperNoLinux
                    RuntimeProblem.HelperUpdateRequired -> Res.string.agentsHelperUpdateRequired
                    else -> Res.string.agentsHelperNotStarted
                }
            else -> Res.string.agentsHelperNotConnected
        }
    )

/**
 * The agent's sheet: where it stands, and every act it has (install, sign in, cancel an install, sign out, use the
 * earlier version, remove). An install that finished but still needs sign-in and the phone check says so here, not as
 * a notice floating over the page.
 */
@Composable
internal fun PhoneAgentSheet(state: AgentsUi, actions: AgentsActions, enabled: KitEnabled) {
    val agent = state.sheetAgent?.let { id -> state.phone.agents.firstOrNull { it.id == id } }
    val tile = agent?.let { tileOf(it, state) }
    val working = KitEnabled.Disabled(stringResource(Res.string.serversWorking))
    val can = if (state.busy) working else enabled
    KitSheet(
        visible = tile != null,
        onDismissRequest = actions::closeAgent,
        title = agent?.name ?: stringResource(Res.string.agentsStateReady),
        subtitle = tile?.let { tileWords(it) },
        primary =
            when (tile?.act) {
                AgentTileAct.Install ->
                    KitAction(
                        stringResource(Res.string.agentsInstallAction, tile.agent.name),
                        { actions.requestInstall(tile.agent.id) },
                        enabled = can,
                        working = state.busy,
                    )
                AgentTileAct.SignIn ->
                    KitAction(
                        stringResource(Res.string.agentsSignInAction, tile.agent.name),
                        { actions.signInPhone(tile.agent.id) },
                        enabled = can,
                        working = state.busy,
                    )
                AgentTileAct.Check ->
                    KitAction(
                        stringResource(Res.string.agentsCheckAction, tile.agent.name),
                        { actions.checkPhoneAgent(tile.agent.id) },
                        // One phone check at a time: another agent's running check holds this one.
                        enabled = if (state.checkingAgent != null && state.checkingAgent != tile.agent.id) working else can,
                        working = state.busy || state.checkingAgent != null,
                    )
                AgentTileAct.Resume ->
                    KitAction(
                        stringResource(Res.string.agentsResumeAction, tile.agent.name),
                        { actions.resumePhoneAgent(tile.agent.id) },
                        enabled = can,
                    )
                null ->
                    when (tile?.state) {
                        AgentTileState.Installing ->
                            KitAction(stringResource(Res.string.linuxAgentInstallCancel), actions::cancelPhoneInstall, enabled = can)
                        // Flutter's "Use {agent}": choose it and open New conversation with it selected.
                        AgentTileState.Ready ->
                            KitAction(
                                stringResource(Res.string.agentsSignInDone, tile.agent.name),
                                { actions.newPhoneConversation(tile.agent.id) },
                                enabled = can,
                            )
                        else -> null
                    }
            },
        tertiary =
            buildList {
                val a = agent ?: return@buildList
                if (a.signOutAvailable)
                    add(KitAction(stringResource(Res.string.agentsSignOutAction, a.name), { actions.requestSignOut(a.id) }, enabled = can))
                if (a.previousVersionAvailable)
                    add(
                        KitAction(
                            stringResource(Res.string.agentsPreviousVersion, a.name),
                            { actions.requestPreviousVersion(a.id) },
                            enabled = can,
                        )
                    )
                if (a.removable)
                    add(
                        KitAction(
                            stringResource(Res.string.linuxAgentPayloadRemove, a.name),
                            { actions.requestRemoval(a.id) },
                            enabled = can,
                            destructive = true,
                        )
                    )
            },
    ) {
        if (tile == null) return@KitSheet
        when {
            tile.state == AgentTileState.Installing -> {
                KitProgressView(tileProgress(tile))
                KitText(stringResource(Res.string.linuxAgentInstallBackground))
            }
            state.checkingAgent == tile.agent.id -> {
                KitProgressView(KitProgress.Waiting())
                KitText(stringResource(Res.string.agentsCheckRunning, tile.agent.name))
            }
            tile.state == AgentTileState.PhoneCheck && state.checkFailedAgent == tile.agent.id ->
                state.checkFailure?.let { KitText(stringResource(checkFailureWords(it))) }
            tile.justInstalled -> KitText(stringResource(Res.string.linuxAgentInstallDone, tile.agent.name))
            tile.state == AgentTileState.PhoneCheck ->
                KitText(stringResource(Res.string.agentsCheckTitle))
            tile.state == AgentTileState.NotInstalled ->
                tile.agent.downloadBytes?.let { KitText(stringResource(Res.string.agentsSetupSizeNote, agentPayloadSize(it))) }
            else -> Unit
        }
    }
}

/** Why a phone check did not pass, in plain words (Flutter `AgentHostFailure` sentences). */
internal fun checkFailureWords(failure: PhoneCheckFailure): org.jetbrains.compose.resources.StringResource =
    when (failure) {
        PhoneCheckFailure.Busy -> Res.string.agentsFailBusy
        PhoneCheckFailure.WrongArchitecture -> Res.string.agentsFailArchitecture
        PhoneCheckFailure.Install -> Res.string.agentsFailInstall
        PhoneCheckFailure.Version -> Res.string.agentsFailVersion
        PhoneCheckFailure.Daemon -> Res.string.agentsFailDaemon
        PhoneCheckFailure.Timeout -> Res.string.agentsFailTimeout
        PhoneCheckFailure.HelperVersion -> Res.string.agentsFailHelperVersion
        PhoneCheckFailure.NeedsSignIn -> Res.string.agentsFailNeedsSignIn
        PhoneCheckFailure.Unavailable -> Res.string.agentsFailUnavailable
        PhoneCheckFailure.Storage -> Res.string.agentsFailStorage
        PhoneCheckFailure.Stale -> Res.string.agentsFailStale
    }
