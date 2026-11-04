package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The list has one add door. Every new profile goes through the checked editor, including New chat.
 */
@Composable
public fun ServersScreen(
    state: ServersUi,
    actions: ServersActions,
    /**
     * Opens This phone (the phone's own server and Linux); null where the platform has no built-in
     * Linux.
     */
    onThisPhone: (() -> Unit)? = null,
    onInspectSetup: ((ProfileId) -> Unit)? = null,
    onTerminal: ((ProfileId) -> Unit)? = null,
    tailscale: TailscaleSetup? = null,
    initialHostSetup: Boolean = false,
    initialTailscaleSetup: Boolean = false,
) {
    var hostSetup by rememberSaveable { mutableStateOf(initialHostSetup) }
    var tailscaleSetup by rememberSaveable { mutableStateOf(initialTailscaleSetup) }
    var confirmingDisconnect by rememberSaveable { mutableStateOf<String?>(null) }
    HostSetupSheet(hostSetup) { hostSetup = false }
    ImportedSignInsSheet(state.importedSignIns, actions)
    // The reviewed address goes to the checked editor (the one add door), never into saved state.
    TailscaleSetupSheet(tailscaleSetup, tailscale, { tailscaleSetup = false }) { reviewed ->
        tailscaleSetup = false
        actions.addServerAt(reviewed)
    }
    ConnectionCheckSheet(state.connectionCheck, actions)
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(
                    if (state.newConversation) Res.string.serversNewTitle
                    else Res.string.serversTitle
                ),
                centred = state.newConversation,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        bottom = {
            KitButton(
                stringResource(Res.string.serversAdd),
                { actions.edit(null) },
                enabled = enabled,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.issue?.let { issue ->
                val known =
                    state.addressProblem
                        ?: state.failure?.details?.text?.let {
                            io.github.eslamasabry.oc.engine.runtime.messages.SetupMessages.plain(it)
                        }
                val words = known?.let { setupMessageWords(it) } ?: issueWords(issue, state.failure)
                if (issue == ServerIssue.Connected) KitNotice(words)
                else
                    KitErrorNotice(
                        words,
                        failure = state.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
            }
            onThisPhone?.let {
                KitRow(
                    stringResource(Res.string.settingsV3ThisPhone),
                    supporting = stringResource(Res.string.linuxMgrRowDetail),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = it,
                )
            }
            KitRow(
                stringResource(Res.string.linuxHostSetupTitle),
                trailing = KitRowTrailing.Chevron,
                onClick = { hostSetup = true },
            )
            KitRow(
                stringResource(Res.string.tailscaleTitle),
                trailing = KitRowTrailing.Chevron,
                onClick = { tailscaleSetup = true },
            )
            KitButton(
                stringResource(Res.string.stateDemoTryOffline),
                actions::tryOffline,
                role = KitButtonRole.Tertiary,
            )
            if (state.servers.isEmpty()) KitNotice(stringResource(Res.string.serversEmpty))
            // Flutter confirmDisconnectServer: the person confirms before live updates stop.
            val confirming =
                state.servers.firstOrNull { it.connected && it.id.raw == confirmingDisconnect }
            KitConfirm(
                spec =
                    confirming?.let {
                        KitConfirmSpec(
                            title = stringResource(Res.string.e7SettingsDisconnectTitle, it.name),
                            confirmLabel = stringResource(Res.string.serverDisconnectFrom, it.name),
                            body =
                                stringResource(
                                    if (it.address.startsWith("http://127.0.0.1:"))
                                        Res.string.safetyDisconnectBodyPhone
                                    else Res.string.safetyDisconnectBody
                                ),
                        )
                    },
                onConfirm = {
                    confirmingDisconnect = null
                    confirming?.let { (actions as? ServerDisconnectActions)?.disconnect(it.id) }
                },
                onCancel = { confirmingDisconnect = null },
            )
            ServerSavedRows(state, actions, enabled, phoneHost = onThisPhone != null)
            for (server in state.servers) {
                ServerMoreActions(
                    server,
                    state,
                    actions,
                    enabled,
                    onTerminal = onTerminal,
                    onInspectSetup = onInspectSetup,
                    onDisconnect = { confirmingDisconnect = server.id.raw },
                )
            }
            KitButton(
                stringResource(Res.string.agentsSectionTitle),
                actions::openAgents,
                role = KitButtonRole.Secondary,
            )
            KitButton(
                stringResource(Res.string.usageTabPlans),
                actions::openPlans,
                role = KitButtonRole.Secondary,
            )
            KitButton(
                stringResource(Res.string.codexNativeTitle),
                actions::openCodexProbe,
                role = KitButtonRole.Secondary,
            )
            ComputerCommands()
        }
    }
}

/**
 * The per-server doors beyond the saved row (connection check, imported sign-ins, disconnect,
 * terminal, setup pages, connectors, agent tools, monitor, read sharing), one expandable row per
 * server so the list stays scannable.
 */
@Composable
private fun ServerMoreActions(
    server: SavedServer,
    state: ServersUi,
    actions: ServersActions,
    enabled: KitEnabled,
    onTerminal: ((ProfileId) -> Unit)?,
    onInspectSetup: ((ProfileId) -> Unit)?,
    onDisconnect: () -> Unit,
) {
    var open by rememberSaveable(server.id.raw) { mutableStateOf(false) }
    KitExpandRow(server.name, open, onExpandedChange = { open = it }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            if (state.connectionCheckAvailable) {
                KitButton(
                    stringResource(Res.string.connectionCheckAction),
                    { actions.checkConnection(server.id) },
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                )
            }
            KitButton(
                stringResource(Res.string.secImportedSignInsTitle),
                { actions.reviewImportedSignIns(server.id) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
            val disconnect = actions as? ServerDisconnectActions
            if (server.connected && disconnect?.disconnectSupported == true) {
                KitButton(
                    stringResource(Res.string.serverDisconnectFrom, server.name),
                    onDisconnect,
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                )
            }
            if (server.connected) {
                onTerminal?.let { terminal ->
                    KitButton(
                        stringResource(Res.string.serversRemoteTerminal),
                        { terminal(server.id) },
                        role = KitButtonRole.Secondary,
                    )
                }
                onInspectSetup?.let { inspect ->
                    KitButton(
                        stringResource(Res.string.serversInspectSetup),
                        { inspect(server.id) },
                        role = KitButtonRole.Secondary,
                    )
                }
                KitButton(
                    stringResource(Res.string.secSetupTitle),
                    { actions.openSetup(server.id) },
                    role = KitButtonRole.Tertiary,
                )
            }
            KitButton(
                stringResource(Res.string.mcpNativeTitle),
                { actions.openConnectorCatalog(server.id) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.setupNativeTitle),
                { actions.openSetupReview(server.id) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.agentToolsTitle),
                { actions.openAgentTools(server.id) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.monitorNativeTitle),
                { actions.openMonitor(server.id) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.stateSetupTitle),
                { actions.openStateSetup(server.id) },
                role = KitButtonRole.Tertiary,
            )
        }
        state.readSharing[server.id]
            ?.takeIf { it.available }
            ?.let { sharing ->
                KitSwitchRow(
                    title = stringResource(Res.string.stateShareViewedTitle),
                    supporting = stringResource(Res.string.stateShareViewedDetail),
                    checked = sharing.enabled,
                    enabled =
                        if (sharing.saving)
                            KitEnabled.Disabled(stringResource(Res.string.stateShareViewedSaving))
                        else enabled,
                    onCheckedChange = { actions.setReadSharing(server.id, it) },
                )
            }
    }
}

@Composable
private fun issueWords(issue: ServerIssue, failure: Failure?): String =
    stringResource(
        when (serverFailureKind(failure)) {
            ServerFailureKind.SignIn -> Res.string.serversSignInFailed
            ServerFailureKind.CleartextConsent -> Res.string.serversCleartextRequired
            ServerFailureKind.InvalidAddress -> Res.string.serversInvalidAddress
            ServerFailureKind.Network -> Res.string.serverFailureNetwork
            ServerFailureKind.Timeout -> Res.string.serverFailureTimeout
            ServerFailureKind.Certificate -> Res.string.serverFailureCertificate
            ServerFailureKind.Incompatible -> Res.string.serverFailureIncompatible
            ServerFailureKind.UnreadableReply -> Res.string.serverFailureUnreadableReply
            ServerFailureKind.Server -> Res.string.serverFailureServer
            ServerFailureKind.Rejected -> Res.string.serverFailureRejected
            ServerFailureKind.Storage -> Res.string.serverFailureStorage
            ServerFailureKind.Secret -> Res.string.serverFailureSecret
            ServerFailureKind.Other ->
                if (failure != null && issue != ServerIssue.Connected) Res.string.serverFailureOther
                else
                    when (issue) {
                        ServerIssue.InputRequired -> Res.string.serversInvalidAddress
                        ServerIssue.SaveFailed -> Res.string.serversSaveFailed
                        ServerIssue.ConnectFailed -> Res.string.serversConnectFailed
                        ServerIssue.CreateFailed -> Res.string.serversCreateFailed
                        ServerIssue.RemoveFailed -> Res.string.serversRemoveFailed
                        ServerIssue.Connected -> Res.string.serversConnected
                    }
        }
    )
