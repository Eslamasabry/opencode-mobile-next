package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerCheckFailedPlain
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
import io.github.eslamasabry.oc.ui.words.res.serversName
import io.github.eslamasabry.oc.ui.words.res.serversSave
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Agents (Flutter `AgentsScreen` + the Paseo pairing editor). Kit parts only. The phone
 * part is never an empty page: without a phone agent host it says where agents run in one line.
 * Every computer row has its visible actions (Start conversation, Check agents); adding one is a
 * visible button, never a hidden gesture.
 */
@Composable
public fun AgentsScreen(state: AgentsUi, actions: AgentsActions) {
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

            KitSectionLabel(stringResource(Res.string.agentsHubOnPhone))
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
            AgentInstallPanel(state, actions, gutter, enabled)
            KitButton(
                stringResource(Res.string.linuxAgentPayloadRefresh),
                actions::refreshPhone,
                gutter,
                role = KitButtonRole.Secondary,
                enabled = enabled,
            )
            if (!state.phone.available) {
                KitRow(
                    title = stringResource(Res.string.agentsRunOnBuiltIn),
                    supporting = stringResource(Res.string.agentsStateUnavailable),
                    supportingMaxLines = 3,
                )
            } else {
                for (agent in state.phone.agents) {
                    KitRow(title = agent.name, supporting = phoneStatusWords(agent.status))
                    if (agent.installable) {
                        KitButton(
                            agentInstallLabel(agent.name),
                            { actions.installPhone(agent.id) },
                            gutter,
                            enabled = enabled,
                        )
                    }
                    if (agent.removable) {
                        KitButton(
                            stringResource(Res.string.linuxAgentPayloadRemove, agent.name),
                            { actions.requestRemoval(agent.id) },
                            gutter,
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                        )
                    }
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
                    KitNotice(stringResource(Res.string.paseoSetupNotice))
                    val invalid = state.check as? ComputerCheck.Invalid
                    KitField(
                        name,
                        stringResource(Res.string.serversName),
                        placeholder = stringResource(Res.string.agentsHubDefaultName),
                        enabled = enabled,
                    )
                    KitField(
                        address,
                        stringResource(Res.string.serversAddress),
                        kind = KitFieldKind.Url,
                        placeholder = stringResource(Res.string.paseoAddressHint),
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
                        stringResource(Res.string.paseoPasswordLabel),
                        kind = KitFieldKind.Secret,
                        helper = stringResource(Res.string.paseoPasswordHelp),
                        error =
                            if (invalid?.password == true)
                                stringResource(Res.string.agentsHubInvalidPassword)
                            else null,
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
    }
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
    )
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
private fun phoneStatusWords(status: PhoneAgentStatus): String =
    stringResource(
        when (status) {
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
