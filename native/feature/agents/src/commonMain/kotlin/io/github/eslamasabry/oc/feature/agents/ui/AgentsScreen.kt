package io.github.eslamasabry.oc.feature.agents.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import io.github.eslamasabry.oc.ui.kit.frame.kitImePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputerKind
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsHubActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsHubControlsUi
import io.github.eslamasabry.oc.feature.agents.hub.AgentsIssue
import io.github.eslamasabry.oc.feature.agents.hub.AgentsProblem
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.ComputerCheck
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemoval
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemovalIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.feature.agents.hub.AgentsTab
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import io.github.eslamasabry.oc.ui.kit.state.KitActionCard
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerCheckFailedPlain
import io.github.eslamasabry.oc.ui.words.res.agentAccountsTitle
import io.github.eslamasabry.oc.ui.words.res.agentComputerCodex
import io.github.eslamasabry.oc.ui.words.res.agentComputerCodexNote
import io.github.eslamasabry.oc.ui.words.res.agentComputerPaseo
import io.github.eslamasabry.oc.ui.words.res.agentDraftTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelperMayHaveStopped
import io.github.eslamasabry.oc.ui.words.res.agentsHelperStartAgain
import io.github.eslamasabry.oc.ui.words.res.agentsHelperStopped
import io.github.eslamasabry.oc.ui.words.res.agentsHubAddComputer
import io.github.eslamasabry.oc.ui.words.res.agentsHubAddFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHubAdded
import io.github.eslamasabry.oc.ui.words.res.agentsHubAgentsFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHubCheck
import io.github.eslamasabry.oc.ui.words.res.agentsHubCheckAgents
import io.github.eslamasabry.oc.ui.words.res.agentsHubComputersEmpty
import io.github.eslamasabry.oc.ui.words.res.agentsHubDefaultName
import io.github.eslamasabry.oc.ui.words.res.agentsHubFolderHint
import io.github.eslamasabry.oc.ui.words.res.agentsHubInvalidAddress
import io.github.eslamasabry.oc.ui.words.res.agentsHubInvalidFolder
import io.github.eslamasabry.oc.ui.words.res.agentsHubInvalidPassword
import io.github.eslamasabry.oc.ui.words.res.agentsHubNotAnswering
import io.github.eslamasabry.oc.ui.words.res.agentsHubNotConnected
import io.github.eslamasabry.oc.ui.words.res.agentsHubOnComputers
import io.github.eslamasabry.oc.ui.words.res.agentsHubOnPhone
import io.github.eslamasabry.oc.ui.words.res.agentsHubPasswordNeeded
import io.github.eslamasabry.oc.ui.words.res.agentsHubSignInFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHubStartFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHubSwitchFailed
import io.github.eslamasabry.oc.ui.words.res.agentsPreviousVersion
import io.github.eslamasabry.oc.ui.words.res.agentsPreviousVersionBody
import io.github.eslamasabry.oc.ui.words.res.agentsPreviousVersionFailed
import io.github.eslamasabry.oc.ui.words.res.agentsPreviousVersionRestored
import io.github.eslamasabry.oc.ui.words.res.agentsResetSavedSignIns
import io.github.eslamasabry.oc.ui.words.res.agentsResetSavedSignInsBody
import io.github.eslamasabry.oc.ui.words.res.agentsResetSavedSignInsFailed
import io.github.eslamasabry.oc.ui.words.res.agentsRestartAfterReset
import io.github.eslamasabry.oc.ui.words.res.agentsRunOnBuiltIn
import io.github.eslamasabry.oc.ui.words.res.agentsSectionTitle
import io.github.eslamasabry.oc.ui.words.res.cardsFromAgentsSupporting
import io.github.eslamasabry.oc.ui.words.res.cardsFromAgentsTitle
import io.github.eslamasabry.oc.ui.words.res.codexProjectFolder
import io.github.eslamasabry.oc.ui.words.res.computerToolsOpen
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadBusy
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadRefresh
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadRemove
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadRemoveBody
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadRemoved
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnavailable
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnconfirmed
import io.github.eslamasabry.oc.ui.words.res.linuxAgentPayloadUnsafe
import io.github.eslamasabry.oc.ui.words.res.paseoAddressHint
import io.github.eslamasabry.oc.ui.words.res.paseoCheckNoneReady
import io.github.eslamasabry.oc.ui.words.res.paseoCheckReady
import io.github.eslamasabry.oc.ui.words.res.paseoPasswordHelp
import io.github.eslamasabry.oc.ui.words.res.paseoPasswordLabel
import io.github.eslamasabry.oc.ui.words.res.paseoSetupNotice
import io.github.eslamasabry.oc.ui.words.res.serversAddress
import io.github.eslamasabry.oc.ui.words.res.serversCancel
import io.github.eslamasabry.oc.ui.words.res.serversConnected
import io.github.eslamasabry.oc.ui.words.res.serversCreate
import io.github.eslamasabry.oc.ui.words.res.serversSave
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.ui.words.res.whatsNewShowAgents
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Agents (owner's pick C2, 2026-11-08): two tabs, "On this phone" and "On computers". The phone tab is
 * a bento grid of agent tiles (a large mark, the name, one state line, at most one small act; a tap opens the agent's
 * sheet) with one status line under it. The computers tab leads with "Add a computer" while none is connected, then
 * each computer's agents as tiles. Settings live in the top bar's ⋯; there is no Refresh row (the page reads on its
 * own, and a pull reads again) and no "Connect phone agents" button (they connect on their own). Kit parts only.
 */
@Composable
public fun AgentsScreen(
    state: AgentsUi,
    actions: AgentsActions,
    hub: AgentsHubControlsUi? = null,
    hubActions: AgentsHubActions? = null,
    showArrival: Boolean = false,
    dismissArrival: () -> Unit = {},
) {
    if (state.draftSource != null) {
        AgentDraftPanel(state, actions)
        return
    }
    if (state.accountTarget != null) {
        AgentAccountPanel(state, actions)
        return
    }
    val name = remember { TextFieldState() }
    val address = remember { TextFieldState() }
    val folder = remember { TextFieldState() }
    // Never rememberSaveable: a password must not enter saved instance state.
    val password = remember { TextFieldState() }
    LaunchedEffect(state.savedGeneration) {
        if (state.savedGeneration > 0) {
            password.edit { replace(0, length, "") }
            name.edit { replace(0, length, "") }
            address.edit { replace(0, length, "") }
            folder.edit { replace(0, length, "") }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(address.text.toString(), folder.text.toString(), password.text.toString())
        }
            .collect { actions.edited() }
    }
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    val fields = ComputerFields(name, address, folder, password)
    var menuOpen by remember { mutableStateOf(false) }
    val menu = agentsMenu(state, actions, hub, hubActions) { menuOpen = false }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.agentsSectionTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                more =
                    if (menu.isEmpty()) null
                    else { _ ->
                        val moreLabel = stringResource(Res.string.kitTopBarMore)
                        KitMenu(
                            expanded = menuOpen,
                            items = menu,
                            onDismissRequest = { menuOpen = false },
                            title = moreLabel,
                        ) {
                            KitIconButton(KitIcons.more, moreLabel, { menuOpen = true })
                        }
                    },
            )
        },
        width = KitScreenWidth.Reading,
        // No Refresh button: the page reads on its own, and a pull reads again.
        refresh = KitScreenRefresh(state.refreshing, actions::pullRefresh),
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize()
                .kitImePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            state.issue?.let { IssueNotice(it, gutter) }
            KitTip(stringResource(Res.string.whatsNewShowAgents), showArrival, dismissArrival) {
                KitSegmented(
                    listOf(
                        KitSegment(AgentsTab.Phone, stringResource(Res.string.agentsHubOnPhone), KitIcons.phone),
                        KitSegment(AgentsTab.Computers, stringResource(Res.string.agentsTabComputers), KitIcons.computer),
                    ),
                    state.tab,
                    stringResource(Res.string.agentsTabsLabel),
                    gutter,
                    onChange = actions::selectTab,
                )
            }
            when (state.tab) {
                AgentsTab.Phone -> PhoneTab(state, actions, hub, hubActions, enabled, gutter)
                AgentsTab.Computers -> ComputersTab(state, actions, enabled, gutter) { AddComputerForm(state, actions, enabled, gutter, fields) }
            }
        }
        PhoneAgentSheet(state, actions, enabled)
        KitSheet(
            visible = state.revertingAgent != null,
            onDismissRequest = actions::cancelPreviousVersion,
            title =
                state.revertingAgent?.let {
                    stringResource(Res.string.agentsPreviousVersion, it.name)
                } ?: stringResource(Res.string.agentsSectionTitle),
            icon = KitIcons.history,
            dismissible = !state.busy,
            primary =
                state.revertingAgent?.let { agent ->
                    KitAction(
                        stringResource(Res.string.agentsPreviousVersion, agent.name),
                        actions::confirmPreviousVersion,
                        destructive = true,
                        enabled = enabled,
                        working = state.busy,
                    )
                },
            secondary =
                KitAction(
                    stringResource(Res.string.serversCancel),
                    actions::cancelPreviousVersion,
                    enabled = enabled,
                ),
        ) {
            KitText(stringResource(Res.string.agentsPreviousVersionBody))
        }
        KitSheet(
            visible = state.resettingSignIns,
            onDismissRequest = actions::cancelSignInReset,
            title = stringResource(Res.string.agentsResetSavedSignIns),
            icon = KitIcons.login,
            dismissible = !state.busy,
            primary =
                KitAction(
                    stringResource(Res.string.agentsResetSavedSignIns),
                    actions::confirmSignInReset,
                    destructive = true,
                    enabled = enabled,
                    working = state.busy,
                ),
            secondary =
                KitAction(
                    stringResource(Res.string.serversCancel),
                    actions::cancelSignInReset,
                    enabled = enabled,
                ),
        ) {
            KitText(stringResource(Res.string.agentsResetSavedSignInsBody))
        }
        KitConfirm(
            state.signingOutAgent?.let { agent ->
                KitConfirmSpec(
                    title = stringResource(Res.string.agentsSignOutTitle, agent.name),
                    confirmLabel = stringResource(Res.string.agentsSignOutAction, agent.name),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.agentsSignOutBody, agent.name),
                    consequences = listOf(stringResource(Res.string.agentsSignOutKept, agent.name)),
                    icon = KitIcons.personRemove,
                )
            },
            onConfirm = actions::confirmSignOut,
            onCancel = actions::cancelSignOut,
            working = state.busy,
        )
        KitConfirm(
            state.removingAgent?.let { agent ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxAgentPayloadRemoveTitle, agent.name),
                    confirmLabel = stringResource(Res.string.linuxAgentPayloadRemove, agent.name),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.linuxAgentPayloadRemoveBody),
                )
            },
            onConfirm = actions::confirmRemoval,
            onCancel = actions::cancelRemoval,
            working = state.busy,
        )
    }
}

@Composable
private fun IssueNotice(issue: AgentsProblem, modifier: Modifier) {
    if (issue.kind == AgentsIssue.Added) {
        KitNotice(stringResource(Res.string.agentsHubAdded), modifier)
        return
    }
    KitErrorNotice(
        issueWords(issue),
        modifier,
        failure = issue.failure,
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
    )
}

/** Plain words only; the daemon's own sentence stays under Details (B-AGENT-160). */
@Composable
private fun issueWords(issue: AgentsProblem): String =
    stringResource(
        when (issue.failure) {
            // The daemon asked for a password and none was given (`hello.rejected` "password_required"): say the
            // computer needs one, not that it refused one.
            is Failure.SignIn ->
                if ((issue.failure as Failure.SignIn).why == SignInWhy.PasswordMissing) Res.string.agentsHubPasswordNeeded
                else Res.string.agentsHubSignInFailed
            is Failure.ComputerDidNotAnswer,
            is Failure.Network,
            is Failure.TimedOut -> Res.string.agentsHubNotAnswering
            else ->
                when (issue.kind) {
                    AgentsIssue.CheckFailed -> Res.string.addServerCheckFailedPlain
                    AgentsIssue.AddFailed -> Res.string.agentsHubAddFailed
                    AgentsIssue.AgentsFailed -> Res.string.agentsHubAgentsFailed
                    AgentsIssue.StartFailed -> Res.string.agentsHubStartFailed
                    AgentsIssue.Added -> Res.string.agentsHubAdded
                }
        }
    )



/** The add-a-computer form's fields, kept by the screen so a save can clear them. */
private class ComputerFields(
    val name: TextFieldState,
    val address: TextFieldState,
    val folder: TextFieldState,
    val password: TextFieldState,
)

/** The page's settings, in the top bar's ⋯ (owner 2026-11-08): never rows under the agents. */
@Composable
private fun agentsMenu(
    state: AgentsUi,
    actions: AgentsActions,
    hub: AgentsHubControlsUi?,
    hubActions: AgentsHubActions?,
    close: () -> Unit,
): List<KitMenuItem> {
    val phoneAvailable = state.phone.available
    val items = mutableListOf<KitMenuItem>()
    fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onSelect: () -> Unit) {
        items += KitMenuItem(label, { close(); onSelect() }, icon = icon)
    }
    if (phoneAvailable && hub?.connection?.usingBuiltIn != false && hubActions != null)
        item(stringResource(Res.string.cardsFromAgentsTitle), KitIcons.category, hubActions::openCardsSettings)
    val sources = state.draftSources
    for (source in sources) {
        item(
            if (sources.size == 1) stringResource(Res.string.agentsConversationsStored)
            else stringResource(Res.string.agentsConversationsStoredOn, source.name),
            KitIcons.database,
        ) {
            actions.openDrafts(source.id)
        }
    }
    for (account in state.accounts)
        item(stringResource(Res.string.agentsHostAccount, account.name), KitIcons.account) { actions.openAccount(account.id) }
    if (phoneAvailable)
        item(stringResource(Res.string.agentsResetSavedSignIns), KitIcons.login, actions::requestSignInReset)
    return items
}

/** "On this phone": the phone's notices, its agent tiles and one status line with Check again. */
@Composable
private fun PhoneTab(
    state: AgentsUi,
    actions: AgentsActions,
    hub: AgentsHubControlsUi?,
    hubActions: AgentsHubActions?,
    enabled: KitEnabled,
    gutter: Modifier,
) {
    if (state.phone.needsRestart) {
        KitNotice(stringResource(Res.string.agentsRestartAfterReset), gutter)
    }
    if (state.phone.helperStoppedUnexpectedly) {
        KitNotice(
            stringResource(
                if (state.phone.helperMayHaveBeenStoppedByAndroid)
                    Res.string.agentsHelperMayHaveStopped
                else Res.string.agentsHelperStopped
            ),
            gutter,
            // The way forward sits on the notice itself: the same start the page makes on its own.
            actions =
                listOf(
                    KitLineAction(
                        stringResource(Res.string.agentsHelperStartAgain),
                        actions::connectPhoneHelper,
                        testTag = "agents-helper-start-again",
                    )
                ),
        )
    }
    if (state.resetFailed) {
        KitErrorNotice(stringResource(Res.string.agentsResetSavedSignInsFailed), gutter)
    }
    state.removal?.let { removal ->
        when (removal) {
            is PhoneAgentRemoval.Removed ->
                KitNotice(
                    pluralStringResource(
                        Res.plurals.linuxAgentPayloadRemoved,
                        removal.freedBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        removal.freedBytes,
                    ),
                    gutter,
                )
            is PhoneAgentRemoval.Refused ->
                KitErrorNotice(
                    stringResource(
                        when (removal.issue) {
                            PhoneAgentRemovalIssue.Busy -> Res.string.linuxAgentPayloadBusy
                            PhoneAgentRemovalIssue.Unsafe ->
                                Res.string.linuxAgentPayloadUnsafe
                            PhoneAgentRemovalIssue.Unconfirmed ->
                                Res.string.linuxAgentPayloadUnconfirmed
                            PhoneAgentRemovalIssue.Unavailable ->
                                Res.string.linuxAgentPayloadUnavailable
                        }
                    ),
                    gutter,
                )
        }
    }
    hub?.failure?.let { failure ->
        KitErrorNotice(
            stringResource(Res.string.agentsHubSwitchFailed),
            gutter,
            failure = failure,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
        )
    }
    state.signOutFailed?.let { name ->
        KitErrorNotice(stringResource(Res.string.agentsSignOutFailed, name), gutter)
    }
    if (state.previousVersionFailed)
        KitErrorNotice(stringResource(Res.string.agentsPreviousVersionFailed), gutter)
    if (state.previousVersionRestored)
        KitNotice(stringResource(Res.string.agentsPreviousVersionRestored), gutter)
    // Phone agents connect on their own; only a real failure says so, once, with Try again.
    PhoneAgentConnectLine(state, actions, gutter)
    // Phone payloads belong to the runtime owner, even while a computer is selected.
    val phoneAvailable = state.phone.available
    AgentInstallPanel(state, actions, gutter, enabled)
    if (!phoneAvailable && hub?.connection?.builtInAvailable == true && hubActions != null) {
        KitButton(
            stringResource(Res.string.agentsSwitchToBuiltIn),
            hubActions::switchToBuiltIn,
            gutter,
            enabled = if (hub.working) KitEnabled.Disabled(stringResource(Res.string.serversWorking)) else enabled,
            working = hub.working,
        )
    }
    if (!phoneAvailable) {
        KitRow(
            title = stringResource(Res.string.agentsHubOnPhone),
            supporting = stringResource(Res.string.agentsRunOnBuiltIn),
            supportingMaxLines = 3,
            leading = KitRowLeading.Glyph(KitIcons.phone),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openPhone,
        )
    }
    if (state.phone.agents.isNotEmpty()) PhoneAgentTiles(state, actions)
    PhoneStatusLine(state, actions, enabled)
}

/** "This phone is ready · arm64 · Android 15" with Check again, under the tiles (it replaces "Check this phone"). */
@Composable
private fun PhoneStatusLine(state: AgentsUi, actions: AgentsActions, enabled: KitEnabled) {
    if (!state.phone.available || state.phone.needsRestart) return
    val device = state.phone.device
    KitStatusLine(
        if (device == null) stringResource(Res.string.agentsPhoneReady)
        else stringResource(Res.string.agentsPhoneReadyDetails, device.processor, device.android),
        tone = StatusTone.Ok,
        action =
            KitLineAction(
                stringResource(Res.string.agentsCheckAgain),
                actions::checkPhoneAgents,
                enabled = enabled.isEnabled,
                disabledReason = (enabled as? KitEnabled.Disabled)?.reason,
            ),
        quiet = true,
    )
}

/**
 * "On computers": with no computer connected, the one call to add one comes first; then each computer's agents as
 * tiles under its name, and the add form or a quiet Add a computer at the end.
 */
@Composable
private fun ComputersTab(
    state: AgentsUi,
    actions: AgentsActions,
    enabled: KitEnabled,
    gutter: Modifier,
    form: @Composable () -> Unit,
) {
    // The phone's own helper is not a computer.
    val shown = state.computers.filter { it.id != state.phone.conversations }
    if (shown.none { it.connected } && !state.adding) {
        KitActionCard(
            KitIcons.computer,
            stringResource(Res.string.agentsAddComputerLine),
            KitAction(stringResource(Res.string.agentsHubAddComputer), actions::startAdding, enabled = enabled),
            gutter,
        )
    }
    if (state.adding) form()
    for (computer in shown) {
        KitRow(
            title = computer.name,
            server = computer.folder.ifEmpty { computer.address },
            supporting =
                stringResource(if (computer.connected) Res.string.serversConnected else Res.string.agentsHubNotConnected),
            leading = KitRowLeading.Glyph(KitIcons.computer),
            // A named way to the computer's page (update its agents, import conversations), never a bare chevron.
            trailing =
                KitRowTrailing.Action(
                    KitLineAction(stringResource(Res.string.computerToolsOpen), { actions.openComputer(computer.id) })
                ),
        )
        val ready = state.ready[computer.id]
        when {
            computer.id in state.unreadComputers ->
                KitText(stringResource(Res.string.agentsComputerReadFailed), gutter, role = TypeRole.Secondary, tone = TextTone.Secondary)
            ready == null ->
                KitText(stringResource(Res.string.agentsComputerLooking), gutter, role = TypeRole.Secondary, tone = TextTone.Secondary)
            ready.isEmpty() ->
                KitText(stringResource(Res.string.paseoCheckNoneReady), gutter, role = TypeRole.Secondary, tone = TextTone.Secondary)
            else -> ComputerAgentTiles(computer.id, ready, actions, gutter)
        }
    }
    if (shown.any { it.connected } && !state.adding) {
        KitButton(
            stringResource(Res.string.agentsHubAddComputer),
            actions::startAdding,
            gutter,
            role = KitButtonRole.Secondary,
            icon = KitIcons.add,
            enabled = enabled,
        )
    }
}

@Composable
private fun AddComputerForm(state: AgentsUi, actions: AgentsActions, enabled: KitEnabled, gutter: Modifier, fields: ComputerFields) {
    val address = fields.address
    val folder = fields.folder
    val password = fields.password
    val name = fields.name
    Column(gutter, verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            for (kind in AgentComputerKind.entries) {
                KitButton(
                    stringResource(
                        if (kind == AgentComputerKind.Paseo)
                            Res.string.agentComputerPaseo
                        else Res.string.agentComputerCodex
                    ),
                    { actions.selectComputerKind(kind) },
                    role =
                        if (kind == state.computerKind) KitButtonRole.Primary
                        else KitButtonRole.Secondary,
                    enabled = enabled,
                )
            }
        }
        KitNotice(
            stringResource(
                if (state.computerKind == AgentComputerKind.Paseo)
                    Res.string.paseoSetupNotice
                else Res.string.agentComputerCodexNote
            )
        )
        val invalid = state.check as? ComputerCheck.Invalid
        KitField(
            address,
            stringResource(Res.string.serversAddress),
            kind = KitFieldKind.Url,
            placeholder =
                stringResource(
                    if (state.computerKind == AgentComputerKind.Codex)
                        Res.string.codexAddressHint
                    else Res.string.paseoAddressHint
                ),
            error =
                if (invalid?.address == true)
                    stringResource(Res.string.agentsHubInvalidAddress)
                else null,
            enabled = enabled,
        )
        KitField(
            folder,
            stringResource(Res.string.codexProjectFolder),
            kind = KitFieldKind.Path,
            placeholder = stringResource(Res.string.agentsHubFolderHint),
            error =
                if (invalid?.folder == true)
                    stringResource(Res.string.agentsHubInvalidFolder)
                else null,
            enabled = enabled,
        )
        KitField(
            password,
            stringResource(
                if (state.computerKind == AgentComputerKind.Codex)
                    Res.string.codexTokenLabel
                else Res.string.paseoPasswordLabel
            ),
            kind = KitFieldKind.Secret,
            helper =
                stringResource(
                    if (state.computerKind == AgentComputerKind.Codex)
                        Res.string.codexTokenStorageHelp
                    else Res.string.paseoPasswordHelp
                ),
            error =
                if (invalid?.password == true)
                    stringResource(Res.string.agentsHubInvalidPassword)
                else null,
            enabled = enabled,
        )
        KitField(
            name,
            stringResource(Res.string.connectionDisplayName),
            placeholder = stringResource(Res.string.agentsHubDefaultName),
            enabled = enabled,
        )
        (state.check as? ComputerCheck.Ready)?.let { ready ->
            KitNotice(
                if (ready.agents.isEmpty())
                    stringResource(Res.string.paseoCheckNoneReady)
                else
                    stringResource(
                        Res.string.paseoCheckReady,
                        ready.agents.joinToString(", "),
                    )
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitButton(
                stringResource(Res.string.agentsHubCheck),
                {
                    actions.check(
                        address.text.toString(),
                        password.text.toString(),
                        folder.text.toString(),
                    )
                },
                role = KitButtonRole.Secondary,
                enabled = enabled,
                working = state.busy,
            )
            KitButton(
                stringResource(Res.string.serversSave),
                {
                    actions.add(
                        name.text.toString(),
                        address.text.toString(),
                        password.text.toString(),
                        folder.text.toString(),
                    )
                },
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.serversCancel),
                actions::cancelAdding,
                role = KitButtonRole.Tertiary,
            )
        }
    }
}
