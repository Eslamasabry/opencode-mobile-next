package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyphs
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.words.res.activeContextRowMenu
import io.github.eslamasabry.oc.ui.words.res.e7SetupAddServer
import io.github.eslamasabry.oc.ui.words.res.serversRemoveBody
import io.github.eslamasabry.oc.ui.words.res.serversRemoveServerKeeps
import io.github.eslamasabry.oc.ui.words.res.e7SetupRemoveServer
import io.github.eslamasabry.oc.ui.words.res.settingsV3NotConnected
import androidx.compose.foundation.layout.Column
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
import io.github.eslamasabry.oc.feature.servers.pairing.PairingText
import io.github.eslamasabry.oc.feature.servers.pairing.ShareLink
import io.github.eslamasabry.oc.feature.servers.pairing.pairingProblemText
import io.github.eslamasabry.oc.feature.servers.presentation.ServerFailureKind
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
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
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRowDetail
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsSectionTitle
import io.github.eslamasabry.oc.ui.words.res.addServerScan
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairingInstructions
import io.github.eslamasabry.oc.ui.words.res.previewQrPairingCodeLabel
import io.github.eslamasabry.oc.ui.words.res.previewQrLinkFilled
import io.github.eslamasabry.oc.ui.words.res.previewQrPairFilled
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
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
import io.github.eslamasabry.oc.ui.words.res.serversRemoveFailed
import io.github.eslamasabry.oc.ui.words.res.serversSave
import io.github.eslamasabry.oc.ui.words.res.serversSaveFailed
import io.github.eslamasabry.oc.ui.words.res.serversSaved
import io.github.eslamasabry.oc.ui.words.res.serversSignInFailed
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.serversUsername
import io.github.eslamasabry.oc.ui.words.res.serversWorking
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
    /** Opens This phone (the phone's own server and Linux); null where the platform has no built-in Linux. */
    onThisPhone: (() -> Unit)? = null,
) {
    // The unsaved draft survives a page on top (Scan code) and the form leaving composition: the plain fields are
    // saveable, the password is held in memory by the ViewModel only (never saved instance state).
    val name = rememberTextFieldState()
    val address = rememberTextFieldState()
    val username = rememberTextFieldState("opencode")
    val password = remember { TextFieldState(actions.heldPassword()) }
    LaunchedEffect(password) { snapshotFlow { password.text.toString() }.collect { actions.holdPassword(it) } }
    var removing by remember { mutableStateOf<ProfileId?>(null) }
    // The add form opens from the Add server button (or by itself when nothing is saved yet, or a code arrives).
    var adding by rememberSaveable { mutableStateOf(false) }
    val showForm = adding || state.servers.isEmpty()
    // PREVIEW-QR: a scanned or pasted pairing code (or share link) fills the form once; the password is taken, not kept.
    var filledFromLink by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var clearedGeneration by rememberSaveable { mutableIntStateOf(0) }
    // A pairing code carries a password, so it goes in a secret field (the kit's Paste lives there too).
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
        adding = true
    }
    LaunchedEffect(state.savedGeneration) {
        if (state.savedGeneration > clearedGeneration) {
            clearedGeneration = state.savedGeneration
            password.edit { replace(0, length, "") }
            adding = false
        }
    }
    LaunchedEffect(address) {
        // The first value is the field as it is (a restored draft keeps its consent); only a change is acted on.
        snapshotFlow { address.text.toString() }
            .drop(1)
            .collect { text ->
                // A pairing code or share link pasted into the address field is read, never saved as an address.
                if (PairingText.looksLikePayload(text) || ShareLink.origin(text) != null) {
                    address.edit { replace(0, length, "") }
                    actions.pastePairing(text)
                }
                actions.allowCleartext(false, address.text.toString())
            }
    }
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    val closeForm = adding && state.servers.isNotEmpty()
    KitBackGuard(enabled = closeForm) { adding = false }
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        when {
                            closeForm -> Res.string.e7SetupAddServer
                            state.newConversation -> Res.string.serversNewTitle
                            else -> Res.string.serversTitle
                        }
                    ),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = { if (closeForm) adding = false else actions.back() },
            )
        },
        bottom =
            if (showForm) null
            else ({
                KitButton(
                    stringResource(Res.string.e7SetupAddServer),
                    { adding = true },
                    Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
                    icon = KitGlyphs.Add,
                    enabled = enabled,
                )
            }),
        width = KitScreenWidth.Reading,
    ) {
        // Kit dialogs need the screen's sheet host, so the confirmation lives inside the frame.
        removing?.let { id ->
            val name = state.servers.firstOrNull { it.id == id }?.name
            KitConfirm(
                spec =
                    name?.let {
                        KitConfirmSpec(
                            title = stringResource(Res.string.e7SetupRemoveServer, it),
                            confirmLabel = stringResource(Res.string.serversRemove),
                            kind = KitConfirmKind.Destructive,
                            body = stringResource(Res.string.serversRemoveBody),
                            consequences = listOf(stringResource(Res.string.serversRemoveServerKeeps)),
                            cancelLabel = stringResource(Res.string.serversCancel),
                        )
                    },
                onConfirm = {
                    removing = null
                    actions.remove(id)
                },
                onCancel = { removing = null },
            )
        }
        // Rows bring their own gutter; everything else (notices, words, fields, buttons) takes the screen's.
        val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.issue?.let { issue -> Column(gutter) {
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
            } }
            onThisPhone?.takeIf { !closeForm }?.let {
                KitRow(
                    title = stringResource(Res.string.settingsV3ThisPhone),
                    leading = KitRowLeading.Glyph(KitGlyphs.Phone),
                    supporting = stringResource(Res.string.linuxMgrRowDetail),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = it,
                )
            }
            if (state.servers.isEmpty()) KitText(stringResource(Res.string.serversEmpty), gutter)
            // Flutter parity (servers_servers_loaded): one row per server — its glyph, name, status · address —
            // with the row's own act on tap and the rest in its ⋯ menu; removing asks first (KitConfirm).
            if (state.servers.isNotEmpty() && !closeForm) {
                KitRowGroup {
                    for (server in state.servers) {
                        ServerRow(
                            server = server,
                            newConversation = state.newConversation,
                            enabled = !state.busy,
                            actions = actions,
                            onRemove = { removing = server.id },
                        )
                    }
                }
                KitRow(
                    title = stringResource(Res.string.agentsSectionTitle),
                    leading = KitRowLeading.Glyph(KitGlyphs.Terminal),
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openAgents,
                )
            }
            if (!showForm) return@Column
            Column(gutter, verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
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
                KitNotice(stringResource(if (link) Res.string.previewQrLinkFilled else Res.string.previewQrPairFilled))
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
            }
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
                gutter,
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

// TODO(words): `serversSchedules` belongs in :ui:words (P12-SCHEDULES may not edit it); the l10n owner moves it.
@Composable
private fun schedulesLabel(): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "الطلبات المجدولة" else "Scheduled prompts"

/**
 * One saved server (Flutter `ServerRow`): tapping it does its obvious act — Connect when it is not connected, Start
 * conversation when it is — and its ⋯ menu names every other act on that server.
 */
@Composable
private fun ServerRow(
    server: SavedServer,
    newConversation: Boolean,
    enabled: Boolean,
    actions: ServersActions,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val status = stringResource(if (server.connected) Res.string.serversConnected else Res.string.settingsV3NotConnected)
    val connect = stringResource(Res.string.serversConnect)
    val create = stringResource(Res.string.serversCreate)
    val working = stringResource(Res.string.serversWorking)
    val items = buildList {
        add(KitMenuItem(create, { actions.create(server.id) }, icon = KitGlyphs.Chat))
        if (!server.connected) add(KitMenuItem(connect, { actions.connect(server.id) }, icon = KitGlyphs.Sync))
        if (server.connected) add(KitMenuItem(stringResource(Res.string.usageTitle), { actions.openUsage(server.id) }, icon = KitGlyphs.Usage))
        add(KitMenuItem(schedulesLabel(), { actions.openSchedules(server.id) }, icon = KitGlyphs.Clock))
        add(KitMenuItem(stringResource(Res.string.serversRemove), onRemove, destructive = true))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val opensChat = server.connected || newConversation
        KitRow(
            title = server.name,
            modifier = Modifier.weight(1f).testTag("server-row-${server.id.raw}"),
            supporting = "$status · ${server.address}",
            leading = KitRowLeading.Glyph(KitGlyphs.Server),
            enabled = enabled,
            disabledReason = if (enabled) null else working,
            onClickLabel = if (opensChat) create else connect,
            onClick = { if (opensChat) actions.create(server.id) else actions.connect(server.id) },
        )
        KitMenu(expanded = menu, items = items, onDismissRequest = { menu = false }, title = server.name) {
            KitIconButton(
                KitGlyphs.More,
                stringResource(Res.string.activeContextRowMenu, server.name),
                { menu = true },
                enabled = if (enabled) KitEnabled.Enabled else KitEnabled.Disabled(working),
            )
        }
    }
}
