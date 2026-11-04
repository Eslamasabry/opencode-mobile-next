package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.lifecycle.PhoneHostSupport
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.lifecycle.PhoneServerCandidate
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.feature.servers.pairing.ShareLink
import io.github.eslamasabry.oc.feature.servers.pairing.pairingProblemText
import io.github.eslamasabry.oc.feature.servers.presentation.ServerFailureKind
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
import io.github.eslamasabry.oc.feature.servers.presentation.ServerMonitorStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.presentation.serverFailureKind
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
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerScan
import io.github.eslamasabry.oc.ui.words.res.agentToolsTitle
import io.github.eslamasabry.oc.ui.words.res.agentsSectionTitle
import io.github.eslamasabry.oc.ui.words.res.codexNativeTitle
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairingInstructions
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxHostSetupTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRowDetail
import io.github.eslamasabry.oc.ui.words.res.mcpNativeTitle
import io.github.eslamasabry.oc.ui.words.res.monitorNativeTitle
import io.github.eslamasabry.oc.ui.words.res.monitorServerCheckInMinutes
import io.github.eslamasabry.oc.ui.words.res.monitorServerCheckInOff
import io.github.eslamasabry.oc.ui.words.res.monitorServerCurrent
import io.github.eslamasabry.oc.ui.words.res.monitorOptIn
import io.github.eslamasabry.oc.ui.words.res.monitorOptInDetail
import io.github.eslamasabry.oc.ui.words.res.monitorServerPaused
import io.github.eslamasabry.oc.ui.words.res.monitorServerUnavailable
import io.github.eslamasabry.oc.ui.words.res.monitorServerWaiting
import io.github.eslamasabry.oc.ui.words.res.monitorServerWifi
import io.github.eslamasabry.oc.ui.words.res.monitorServerWifiRequired
import io.github.eslamasabry.oc.ui.words.res.previewQrLinkFilled
import io.github.eslamasabry.oc.ui.words.res.previewQrPairFilled
import io.github.eslamasabry.oc.ui.words.res.previewQrPairingCodeLabel
import io.github.eslamasabry.oc.ui.words.res.serverFailureCertificate
import io.github.eslamasabry.oc.ui.words.res.serverFailureIncompatible
import io.github.eslamasabry.oc.ui.words.res.serverFailureNetwork
import io.github.eslamasabry.oc.ui.words.res.serverFailureOther
import io.github.eslamasabry.oc.ui.words.res.serverFailureRejected
import io.github.eslamasabry.oc.ui.words.res.serverFailureSecret
import io.github.eslamasabry.oc.ui.words.res.serverFailureServer
import io.github.eslamasabry.oc.ui.words.res.serverFailureStorage
import io.github.eslamasabry.oc.ui.words.res.serverFailureTimeout
import io.github.eslamasabry.oc.ui.words.res.serverFailureUnreadableReply
import io.github.eslamasabry.oc.ui.words.res.serversAdd
import io.github.eslamasabry.oc.ui.words.res.serversAddress
import io.github.eslamasabry.oc.ui.words.res.serversCancel
import io.github.eslamasabry.oc.ui.words.res.serversCleartext
import io.github.eslamasabry.oc.ui.words.res.serversCleartextHelp
import io.github.eslamasabry.oc.ui.words.res.serversCleartextRequired
import io.github.eslamasabry.oc.ui.words.res.serversConnect
import io.github.eslamasabry.oc.ui.words.res.serversConnectFailed
import io.github.eslamasabry.oc.ui.words.res.serversConnected
import io.github.eslamasabry.oc.ui.words.res.serversCreate
import io.github.eslamasabry.oc.ui.words.res.serversCreateFailed
import io.github.eslamasabry.oc.ui.words.res.serversEmpty
import io.github.eslamasabry.oc.ui.words.res.serversInvalidAddress
import io.github.eslamasabry.oc.ui.words.res.serversName
import io.github.eslamasabry.oc.ui.words.res.serversNewTitle
import io.github.eslamasabry.oc.ui.words.res.serversPassword
import io.github.eslamasabry.oc.ui.words.res.serversRemove
import io.github.eslamasabry.oc.ui.words.res.serversRemoveConfirm
import io.github.eslamasabry.oc.ui.words.res.serversRemoveFailed
import io.github.eslamasabry.oc.ui.words.res.serversSave
import io.github.eslamasabry.oc.ui.words.res.serversSaveFailed
import io.github.eslamasabry.oc.ui.words.res.serversSaved
import io.github.eslamasabry.oc.ui.words.res.serversSignInFailed
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.serversUsername
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.setupNativeTitle
import io.github.eslamasabry.oc.ui.words.res.usageTabPlans
import io.github.eslamasabry.oc.ui.words.res.usageTitle
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/**
 * Every visual control is an existing kit part. Editable secrets stay in an unsaved secure text
 * field.
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
) {
    // The unsaved draft survives a page on top (Scan code) and the form leaving composition: the
    // plain fields are
    // saveable, the password is held in memory by the ViewModel only (never saved instance state).
    val name = rememberTextFieldState()
    val address = rememberTextFieldState()
    val username = rememberTextFieldState("opencode")
    val password = remember { TextFieldState(actions.heldPassword()) }
    LaunchedEffect(password) {
        snapshotFlow { password.text.toString() }.collect { actions.holdPassword(it) }
    }
    var removing by remember { mutableStateOf<ProfileId?>(null) }
    var hostSetup by rememberSaveable { mutableStateOf(false) }
    // PREVIEW-QR: a scanned or pasted pairing code (or share link) fills the form once; the
    // password is taken, not kept.
    var filledFromLink by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var clearedGeneration by rememberSaveable { mutableIntStateOf(0) }
    // A pairing code carries a password, so it goes in a secret field (the kit's Paste lives there
    // too).
    val code = remember { TextFieldState() }
    LaunchedEffect(code) {
        snapshotFlow { code.text.toString() }
            .collect { text ->
                if (PairingText.looksLikePayload(text) || ShareLink.origin(text) != null) {
                    code.edit { replace(0, length, "") }
                    actions.pastePairing(text)
                }
            }
    }
    LaunchedEffect(state.pairingArrival) {
        val fill = actions.takePairing() ?: return@LaunchedEffect
        address.edit { replace(0, length, fill.address) }
        fill.username?.let { value -> username.edit { replace(0, length, value) } }
        fill.takePassword()?.let { value -> password.edit { replace(0, length, value) } }
        filledFromLink = fill.fromLink
    }
    LaunchedEffect(state.savedGeneration) {
        if (state.savedGeneration > clearedGeneration) {
            clearedGeneration = state.savedGeneration
            password.edit { replace(0, length, "") }
        }
    }
    LaunchedEffect(address) {
        // The first value is the field as it is (a restored draft keeps its consent); only a change
        // is acted on.
        snapshotFlow { address.text.toString() }
            .drop(1)
            .collect { text ->
                // A pairing code or share link pasted into the address field is read, never saved
                // as an address.
                if (PairingText.looksLikePayload(text) || ShareLink.origin(text) != null) {
                    address.edit { replace(0, length, "") }
                    actions.pastePairing(text)
                }
                actions.allowCleartext(false, address.text.toString())
            }
    }
    HostSetupSheet(hostSetup) { hostSetup = false }
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (state.newConversation) Res.string.serversNewTitle
                        else Res.string.serversTitle
                    ),
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
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.issue?.let { issue ->
                val words = issueWords(issue, state.failure)
                if (issue == ServerIssue.Connected) {
                    KitNotice(words)
                } else {
                    KitErrorNotice(
                        words,
                        failure = state.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
                }
            }
            onThisPhone?.let {
                KitRow(
                    title = stringResource(Res.string.settingsV3ThisPhone),
                    supporting = stringResource(Res.string.linuxMgrRowDetail),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = it,
                )
            }
            KitRow(
                title = stringResource(Res.string.linuxHostSetupTitle),
                trailing = KitRowTrailing.Chevron,
                onClick = { hostSetup = true },
            )
            if (state.servers.isEmpty()) KitText(stringResource(Res.string.serversEmpty))
            else KitText(stringResource(Res.string.serversSaved))
            for (server in state.servers) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(server.name)
                    KitText(server.address)
                    // B-NAV-040: only the phone's own servers are marked as this phone; a forwarded
                    // loopback is a server.
                    val support =
                        if (onThisPhone != null) PhoneHostSupport.Android
                        else PhoneHostSupport.Elsewhere
                    if (
                        PhoneOwnServers.isPhoneOwnServer(
                            PhoneServerCandidate(server.id.raw, server.address),
                            support,
                        )
                    ) {
                        KitText(stringResource(Res.string.settingsV3ThisPhone))
                    }
                    if (server.connected) KitText(stringResource(Res.string.serversConnected))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                        KitButton(
                            stringResource(Res.string.serversCreate),
                            { actions.create(server.id) },
                            enabled = enabled,
                        )
                        // QA wave 4 B15: a connected server says Connected; Connect is offered only
                        // when it is not.
                        if (!server.connected) {
                            KitButton(
                                stringResource(Res.string.serversConnect),
                                { actions.connect(server.id) },
                                role = KitButtonRole.Secondary,
                                enabled = enabled,
                            )
                        }
                        if (server.connected) {
                            KitButton(
                                stringResource(Res.string.usageTitle),
                                { actions.openUsage(server.id) },
                                role = KitButtonRole.Tertiary,
                            )
                        }
                        KitButton(
                            schedulesLabel(),
                            { actions.openSchedules(server.id) },
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                        )
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
                            stringResource(Res.string.serversRemove),
                            { removing = server.id },
                            role = KitButtonRole.Tertiary,
                            destructive = true,
                            enabled = enabled,
                        )
                    }
                    state.monitoring[server.id]?.let { monitor ->
                        if (monitor.available) {
                            KitSwitchRow(
                                title = stringResource(Res.string.monitorOptIn),
                                supporting = stringResource(Res.string.monitorOptInDetail),
                                checked = monitor.enabled,
                                enabled = enabled,
                                onCheckedChange = { actions.monitor(server.id, it) },
                            )
                            if (monitor.enabled) {
                                KitText(
                                    stringResource(
                                        when (monitor.status) {
                                            ServerMonitorStatus.Current ->
                                                Res.string.monitorServerCurrent
                                            ServerMonitorStatus.Unavailable ->
                                                Res.string.monitorServerUnavailable
                                            ServerMonitorStatus.WifiRequired ->
                                                Res.string.monitorServerWifiRequired
                                            ServerMonitorStatus.Paused ->
                                                Res.string.monitorServerPaused
                                            ServerMonitorStatus.Waiting ->
                                                Res.string.monitorServerWaiting
                                        }
                                    )
                                )
                                KitSwitchRow(
                                    title = stringResource(Res.string.monitorServerWifi),
                                    checked = monitor.wifiOnly,
                                    enabled = enabled,
                                    onCheckedChange = { actions.monitorWifi(server.id, it) },
                                )
                                KitButton(
                                    stringResource(
                                        if (monitor.checkInMinutes == null)
                                            Res.string.monitorServerCheckInOff
                                        else Res.string.monitorServerCheckInMinutes,
                                        monitor.checkInMinutes ?: 30,
                                    ),
                                    {
                                        actions.monitorCheckIn(
                                            server.id,
                                            when (monitor.checkInMinutes) {
                                                null -> 15
                                                15 -> 30
                                                30 -> 60
                                                60 -> 120
                                                else -> null
                                            },
                                        )
                                    },
                                    role = KitButtonRole.Secondary,
                                    enabled = enabled,
                                )
                            }
                        } else KitText(stringResource(Res.string.monitorServerUnavailable))
                    }
                    if (removing == server.id) {
                        KitNotice(stringResource(Res.string.serversRemoveConfirm, server.name))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                            KitButton(
                                stringResource(Res.string.serversRemove),
                                {
                                    removing = null
                                    actions.remove(server.id)
                                },
                                destructive = true,
                                enabled = enabled,
                            )
                            KitButton(
                                stringResource(Res.string.serversCancel),
                                { removing = null },
                                role = KitButtonRole.Tertiary,
                            )
                        }
                    }
                }
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
            KitText(stringResource(Res.string.serversAdd))
            KitButton(
                stringResource(Res.string.addServerScan),
                actions::scanPairing,
                role = KitButtonRole.Secondary,
                enabled = enabled,
            )
            KitField(
                code,
                stringResource(Res.string.previewQrPairingCodeLabel),
                kind = KitFieldKind.Secret,
                helper = stringResource(Res.string.e7SetupPairingInstructions),
                enabled = enabled,
                onSubmit = {
                    val text = code.text.toString()
                    code.edit { replace(0, length, "") }
                    actions.pastePairing(text)
                },
            )
            state.pairingProblem?.let { KitErrorNotice(pairingProblemText(it)) }
            filledFromLink?.let { link ->
                KitNotice(
                    stringResource(
                        if (link) Res.string.previewQrLinkFilled else Res.string.previewQrPairFilled
                    )
                )
            }
            KitField(name, stringResource(Res.string.serversName), enabled = enabled)
            KitField(
                address,
                stringResource(Res.string.serversAddress),
                kind = KitFieldKind.Url,
                placeholder = "https://example.com:4096",
                enabled = enabled,
            )
            KitField(username, stringResource(Res.string.serversUsername), enabled = enabled)
            KitField(
                password,
                stringResource(Res.string.serversPassword),
                kind = KitFieldKind.Secret,
                enabled = enabled,
            )
            KitSwitchRow(
                title = stringResource(Res.string.serversCleartext),
                supporting = stringResource(Res.string.serversCleartextHelp),
                checked = state.cleartextAllowed,
                enabled = enabled,
                onCheckedChange = { actions.allowCleartext(it, address.text.toString()) },
            )
            KitButton(
                stringResource(
                    if (state.newConversation) Res.string.serversCreate else Res.string.serversSave
                ),
                {
                    actions.save(
                        name.text.toString(),
                        address.text.toString(),
                        username.text.toString(),
                        password.text.toString(),
                    )
                },
                enabled = enabled,
                working = state.busy,
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

// TODO(words): `serversSchedules` belongs in :ui:words (P12-SCHEDULES may not edit it); the l10n
// owner moves it.
@Composable
private fun schedulesLabel(): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "الطلبات المجدولة"
    else "Scheduled prompts"
