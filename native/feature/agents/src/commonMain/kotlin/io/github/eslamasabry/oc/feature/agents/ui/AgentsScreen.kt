package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
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
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
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
import io.github.eslamasabry.oc.ui.words.res.agentsConnectPhoneHelper
import io.github.eslamasabry.oc.ui.words.res.agentsConnectPhoneHelperFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHelperMayHaveStopped
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
import io.github.eslamasabry.oc.ui.words.res.agentsHubSignInFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHubStartFailed
import io.github.eslamasabry.oc.ui.words.res.agentsHubSwitchFailed
import io.github.eslamasabry.oc.ui.words.res.agentsPhoneHelperConnected
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
import io.github.eslamasabry.oc.ui.words.res.agentsStateChecking
import io.github.eslamasabry.oc.ui.words.res.agentsStateLimit
import io.github.eslamasabry.oc.ui.words.res.agentsStateNotInstalledNoSize
import io.github.eslamasabry.oc.ui.words.res.agentsStatePhoneCheck
import io.github.eslamasabry.oc.ui.words.res.agentsStateReady
import io.github.eslamasabry.oc.ui.words.res.agentsStateSignInNeeded
import io.github.eslamasabry.oc.ui.words.res.agentsStateStopped
import io.github.eslamasabry.oc.ui.words.res.agentsStateUnavailable
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
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Agents (Flutter `AgentsScreen` + the Paseo pairing editor). Kit parts only. The phone
 * catalog stays visible before phone setup and while a computer is selected. Payload sizes and
 * actions come from the runtime owner; installation never implies readiness or sign-in.
 * Every computer row has its visible actions (Start conversation, Check agents); adding one is a
 * visible button, never a hidden gesture.
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
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.agentsSectionTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            state.issue?.let { IssueNotice(it, gutter) }

            KitTip(stringResource(Res.string.whatsNewShowAgents), showArrival, dismissArrival) {
                KitSectionLabel(stringResource(Res.string.agentsHubOnPhone))
            }
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
                )
            }
            if (state.resetFailed) {
                KitErrorNotice(stringResource(Res.string.agentsResetSavedSignInsFailed), gutter)
            }
            state.removal?.let { removal ->
                when (removal) {
                    is PhoneAgentRemoval.Removed ->
                        KitNotice(
                            stringResource(Res.string.linuxAgentPayloadRemoved, removal.freedBytes),
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
            if (state.helperConnectFailed) {
                KitErrorNotice(stringResource(Res.string.agentsConnectPhoneHelperFailed), gutter)
            }
            if (state.helperConnected) {
                KitNotice(stringResource(Res.string.agentsPhoneHelperConnected), gutter)
            }
            if (state.helperOffered && state.phone.available && !state.phone.needsRestart) {
                KitButton(
                    stringResource(Res.string.agentsConnectPhoneHelper),
                    actions::connectPhoneHelper,
                    gutter,
                    enabled = enabled,
                )
            }
            // Phone payloads belong to the runtime owner, even while a computer is selected.
            val phoneAvailable = state.phone.available
            AgentInstallPanel(state, actions, gutter, enabled)
            if (
                !phoneAvailable && hub?.connection?.builtInAvailable == true && hubActions != null
            ) {
                KitButton(
                    stringResource(Res.string.agentsSwitchToBuiltIn),
                    hubActions::switchToBuiltIn,
                    gutter,
                    enabled =
                        if (hub.working) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
                        else enabled,
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
            if (state.phone.agents.isNotEmpty()) PhoneAgentRows(state, actions, enabled)

            KitButton(
                stringResource(Res.string.linuxAgentPayloadRefresh),
                actions::refreshPhone,
                gutter,
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )

            if (phoneAvailable && hub?.connection?.usingBuiltIn != false && hubActions != null) {
                KitRow(
                    title = stringResource(Res.string.cardsFromAgentsTitle),
                    supporting = stringResource(Res.string.cardsFromAgentsSupporting),
                    supportingMaxLines = 3,
                    trailing = KitRowTrailing.Chevron,
                    onClick = hubActions::openCardsSettings,
                )
            }

            var morePhoneOptions by remember { mutableStateOf(false) }
            if (phoneAvailable)
                KitExpandRow(
                    stringResource(Res.string.editor40More),
                    morePhoneOptions,
                    onExpandedChange = { morePhoneOptions = it },
                ) {
                    KitButton(
                        stringResource(Res.string.agentsResetSavedSignIns),
                        actions::requestSignInReset,
                        role = KitButtonRole.Tertiary,
                        enabled = enabled,
                    )
                }

            if (state.draftSources.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.agentDraftTitle))
                for (source in state.draftSources) {
                    KitRow(title = source.name, onClick = { actions.openDrafts(source.id) })
                }
            }

            if (state.accounts.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.agentAccountsTitle))
                for (account in state.accounts) {
                    KitRow(title = account.name, onClick = { actions.openAccount(account.id) })
                }
            }

            KitSectionLabel(stringResource(Res.string.agentsHubOnComputers))
            if (state.computers.isEmpty() && !state.adding) {
                KitText(stringResource(Res.string.agentsHubComputersEmpty), gutter)
            }
            for (computer in state.computers) {
                val ready = state.ready[computer.id]
                val readyLine =
                    when {
                        ready == null -> null
                        ready.isEmpty() -> stringResource(Res.string.paseoCheckNoneReady)
                        else -> stringResource(Res.string.paseoCheckReady, ready.joinToString(", "))
                    }
                val link =
                    if (computer.connected) stringResource(Res.string.serversConnected)
                    else stringResource(Res.string.agentsHubNotConnected)
                KitRow(
                    title = computer.name,
                    server = computer.folder.ifEmpty { computer.address },
                    supporting = readyLine ?: link,
                    supportingMaxLines = 3,
                )
                FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitButton(
                        stringResource(Res.string.serversCreate),
                        { actions.newConversation(computer.id) },
                        enabled = enabled,
                    )
                    KitButton(
                        stringResource(Res.string.agentsHubCheckAgents),
                        { actions.readAgents(computer.id) },
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                    )
                    KitButton(
                        stringResource(Res.string.computerToolsOpen),
                        { actions.openComputer(computer.id) },
                        role = KitButtonRole.Secondary,
                    )
                }
            }

            if (!state.adding) {
                KitButton(
                    stringResource(Res.string.agentsHubAddComputer),
                    actions::startAdding,
                    gutter,
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                )
            } else {
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
        }
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
                    io.github.eslamasabry.oc.ui.kit.KitAction(
                        stringResource(Res.string.agentsPreviousVersion, agent.name),
                        actions::confirmPreviousVersion,
                        destructive = true,
                        enabled = enabled,
                        working = state.busy,
                    )
                },
            secondary =
                io.github.eslamasabry.oc.ui.kit.KitAction(
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
                io.github.eslamasabry.oc.ui.kit.KitAction(
                    stringResource(Res.string.agentsResetSavedSignIns),
                    actions::confirmSignInReset,
                    destructive = true,
                    enabled = enabled,
                    working = state.busy,
                ),
            secondary =
                io.github.eslamasabry.oc.ui.kit.KitAction(
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
            is Failure.SignIn -> Res.string.agentsHubSignInFailed
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

@Composable
private fun phoneStatusWords(
    agent: io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
): String {
    if (agent.status == PhoneAgentStatus.NotInstalled && !agent.supportedInstallation)
        return stringResource(Res.string.agentsFailArchitecture)
    if (agent.status == PhoneAgentStatus.NotInstalled && agent.downloadBytes != null)
        return stringResource(
            Res.string.agentsStateNotInstalled,
            agentPayloadSize(agent.downloadBytes),
        )
    return stringResource(
        when (agent.status) {
            PhoneAgentStatus.Ready -> Res.string.agentsStateReady
            PhoneAgentStatus.NotInstalled -> Res.string.agentsStateNotInstalledNoSize
            PhoneAgentStatus.SignInNeeded -> Res.string.agentsStateSignInNeeded
            PhoneAgentStatus.Checking -> Res.string.agentsStateChecking
            PhoneAgentStatus.PhoneCheck -> Res.string.agentsStatePhoneCheck
            PhoneAgentStatus.Stopped -> Res.string.agentsStateStopped
            PhoneAgentStatus.Limit -> Res.string.agentsStateLimit
            PhoneAgentStatus.Unavailable -> Res.string.agentsStateUnavailable
        }
    )
}

/** No agent sheet is fabricated: only actions actually present in AgentsActions are offered. */
@Composable
private fun PhoneAgentRows(state: AgentsUi, actions: AgentsActions, enabled: KitEnabled) {
    if (state.phone.agents.isEmpty()) {
        KitText(
            stringResource(Res.string.agentsChecking),
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        for (agent in state.phone.agents) {
            KitSurfacePanel(
                Modifier.padding(
                    horizontal = LocalKitTokens.current.gutter.dp,
                    vertical = Space.S1.dp,
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    val menu = buildList {
                        if (agent.signOutAvailable)
                            add(
                                KitMenuItem(
                                    stringResource(
                                        Res.string.agentsSignOutAction,
                                        agent.name,
                                    ),
                                    { actions.requestSignOut(agent.id) },
                                    enabled = enabled,
                                )
                            )
                        if (agent.previousVersionAvailable)
                            add(
                                KitMenuItem(
                                    stringResource(
                                        Res.string.agentsPreviousVersion,
                                        agent.name,
                                    ),
                                    { actions.requestPreviousVersion(agent.id) },
                                    enabled = enabled,
                                )
                            )
                        if (agent.removable)
                            add(
                                KitMenuItem(
                                    stringResource(
                                        Res.string.linuxAgentPayloadRemove,
                                        agent.name,
                                    ),
                                    { actions.requestRemoval(agent.id) },
                                    destructive = true,
                                    enabled = enabled,
                                )
                            )
                    }
                    KitRow(
                        title = agent.name,
                        supporting = phoneStatusWords(agent),
                        supportingMaxLines = 3,
                        leading = KitRowLeading.Logo(agent.id, agent.name, agent = true),
                        trailing =
                            if (menu.isEmpty()) KitRowTrailing.None
                            else KitRowTrailing.Menu(agent.name, menu, title = agent.name),
                    )
                    when {
                        agent.status == PhoneAgentStatus.NotInstalled &&
                            agent.supportedInstallation ->
                            KitButton(
                                agentInstallLabel(agent.name),
                                { actions.requestInstall(agent.id) },
                                enabled = enabled,
                            )
                        agent.signInAvailable ->
                            KitButton(
                                stringResource(Res.string.agentsSignInAction, agent.name),
                                { actions.signInPhone(agent.id) },
                                enabled = enabled,
                            )
                    }
                }
            }
        }
        if (
            state.phone.agents.any {
                it.status != PhoneAgentStatus.NotInstalled &&
                    it.status != PhoneAgentStatus.Unavailable
            }
        ) {
            KitRow(
                title = stringResource(Res.string.agentsCheckTitle),
                leading = KitRowLeading.Glyph(KitIcons.phone),
                trailing = KitRowTrailing.Chevron,
                enabled = enabled.isEnabled,
                disabledReason =
                    if (enabled.isEnabled) null else stringResource(Res.string.agentsCheckTitle),
                onClick = actions::checkPhoneAgents,
            )
        }
    }
}
