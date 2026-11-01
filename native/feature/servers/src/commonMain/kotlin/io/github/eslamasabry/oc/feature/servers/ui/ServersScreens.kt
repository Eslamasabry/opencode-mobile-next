package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.EditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.EditorMode
import io.github.eslamasabry.oc.feature.servers.presentation.EditorUi
import io.github.eslamasabry.oc.feature.servers.presentation.EditorVerdict
import io.github.eslamasabry.oc.feature.servers.presentation.EntryKind
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.SecretPresence
import io.github.eslamasabry.oc.feature.servers.presentation.ServerPageActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerPageUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSwitcherActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSwitcherUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.presentation.TransportSecurity
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerCleartextConfirmed
import io.github.eslamasabry.oc.ui.words.res.addServerManual
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.codexConnectionVerified
import io.github.eslamasabry.oc.ui.words.res.connectionDisplayName
import io.github.eslamasabry.oc.ui.words.res.connectionServerAddress
import io.github.eslamasabry.oc.ui.words.res.e7SetupAddServer
import io.github.eslamasabry.oc.ui.words.res.e7SetupEditServer
import io.github.eslamasabry.oc.ui.words.res.e7SetupPaired
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairingInstructions
import io.github.eslamasabry.oc.ui.words.res.e7SetupPastePairing
import io.github.eslamasabry.oc.ui.words.res.e7SetupServerPassword
import io.github.eslamasabry.oc.ui.words.res.e7SetupTestConnection
import io.github.eslamasabry.oc.ui.words.res.e7SetupUsername
import io.github.eslamasabry.oc.ui.words.res.externalAgentsDetailConnection
import io.github.eslamasabry.oc.ui.words.res.externalAgentsRemoveNamed
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitFieldReplace
import io.github.eslamasabry.oc.ui.words.res.kitFieldSaved
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.serverDisconnectFrom
import io.github.eslamasabry.oc.ui.words.res.serverRowDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.serverSwitcherAddServer
import io.github.eslamasabry.oc.ui.words.res.serverSwitcherTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateFailed
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateInspect
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificatePinned
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpUnconfirmed
import io.github.eslamasabry.oc.ui.words.res.serversRemoveBody
import io.github.eslamasabry.oc.ui.words.res.settingsV3ConnectTo
import io.github.eslamasabry.oc.ui.words.res.settingsV3Servers
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import org.jetbrains.compose.resources.stringResource

/*
 * The servers screens arrange kit parts only (rule 2, Konsist K1). Rows that open pages are visible rows, never only
 * in a menu (07 §10.6); every action names its server (rule 15).
 */

/** Servers (07 §5 E01): the welcome when nothing is saved, else the saved list with the phone's rows first. */
@Composable
public fun ServersScreen(state: ServersUi, actions: ServersActions) {
    KitScreen(title = stringResource(Res.string.settingsV3Servers)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            if (state.welcome) KitText(stringResource(Res.string.e7SetupPairingInstructions), KitTextRole.Body)
            state.rows.forEach { row -> ServerRow(row, state.connecting == row.id, actions) }
            KitButton(label = stringResource(Res.string.e7SetupAddServer), onClick = actions::addServer)
        }
        val forgetting = state.forgetting
        ForgetSheet(forgetting?.name, onConfirm = actions::confirmForget, onCancel = actions::cancelForget)
    }
}

@Composable
private fun ServerRow(row: ServerRowUi, connecting: Boolean, actions: ServersActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        val status = if (connecting) ServerStatus.Connecting.words() else row.status.words()
        KitRow(title = row.name, supporting = row.address + " · " + status, onClick = { actions.openServer(row.id) })
        val failure = row.failure
        if (failure != null) {
            FailureVerdict(failure) { actions.fix(row.id, it) }
        } else if (!row.active && row.status != ServerStatus.Connecting && !connecting) {
            KitRow(
                title = stringResource(Res.string.settingsV3ConnectTo, row.name),
                supporting = null,
                onClick = { actions.connect(row.id) },
            )
        }
    }
}

@Composable
private fun ForgetSheet(name: String?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val last = remember { mutableStateOf(name) }
    if (name != null) last.value = name
    val shown = last.value.orEmpty()
    KitSheet(
        visible = name != null,
        onDismissRequest = onCancel,
        title = stringResource(Res.string.agentsRemoveTitle, shown),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        KitText(stringResource(Res.string.serversRemoveBody), KitTextRole.Body)
        KitButton(label = stringResource(Res.string.externalAgentsRemoveNamed, shown), onClick = onConfirm)
        KitRow(title = stringResource(Res.string.kitConfirmCancel), supporting = null, onClick = onCancel)
    }
}

/** The editor (07 §5 E02): pairing code or typed address, the test verdict inline, consent and pin review sheets. */
@Composable
public fun EditorScreen(state: EditorUi, actions: EditorActions) {
    val field = LocalServersField.current
    val title =
        when (state.mode) {
            EditorMode.Add -> stringResource(Res.string.e7SetupAddServer)
            else -> state.serverName ?: stringResource(Res.string.e7SetupEditServer)
        }
    KitScreen(title = title, backLabel = stringResource(Res.string.kitTopBarBack), onBack = actions::back) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            when (state.entry) {
                EntryKind.PairingCode -> {
                    KitText(stringResource(Res.string.e7SetupPairingInstructions), KitTextRole.Supporting)
                    field(
                        ServersFieldSpec(
                            ServersFieldKind.Code, stringResource(Res.string.e7SetupPastePairing), state.code,
                            actions::setCode, state.codeProblem?.words(), focus = false,
                        ),
                    )
                    KitRow(title = stringResource(Res.string.addServerManual), supporting = null, onClick = { actions.useEntry(EntryKind.Address) })
                }
                EntryKind.Address -> {
                    field(
                        ServersFieldSpec(
                            ServersFieldKind.Url, stringResource(Res.string.connectionServerAddress), state.address,
                            actions::setAddress, state.addressProblem?.words(), focus = false,
                        ),
                    )
                    field(ServersFieldSpec(ServersFieldKind.Text, stringResource(Res.string.connectionDisplayName), state.name, actions::setName, null, false))
                    field(ServersFieldSpec(ServersFieldKind.Text, stringResource(Res.string.e7SetupUsername), state.username, actions::setUsername, null, false))
                    if (state.password == SecretPresence.Saved) {
                        KitRow(
                            title = stringResource(Res.string.e7SetupServerPassword),
                            supporting = stringResource(Res.string.kitFieldSaved) + " · " + stringResource(Res.string.kitFieldReplace),
                            onClick = actions::replacePassword,
                        )
                    } else {
                        field(
                            ServersFieldSpec(
                                ServersFieldKind.Secret, stringResource(Res.string.e7SetupServerPassword), "",
                                actions::setPassword, null, state.focusPassword,
                            ),
                        )
                    }
                    if (state.cleartextAllowed) KitText(stringResource(Res.string.addServerCleartextConfirmed), KitTextRole.Supporting)
                    if (state.pinned) KitText(stringResource(Res.string.serverTrustCertificatePinned), KitTextRole.Supporting)
                    if (state.mode == EditorMode.Add) {
                        KitRow(title = stringResource(Res.string.e7SetupPastePairing), supporting = null, onClick = { actions.useEntry(EntryKind.PairingCode) })
                    }
                }
            }
            if (state.checking) KitText(checkingWords(), KitTextRole.Supporting)
            if (state.certificateUnreadable) KitText(stringResource(Res.string.serverTrustCertificateFailed), KitTextRole.Supporting)
            when (val v = state.verdict) {
                is EditorVerdict.Answered ->
                    KitText(
                        if (state.entry == EntryKind.PairingCode) stringResource(Res.string.e7SetupPaired, v.origin.toString())
                        else stringResource(Res.string.codexConnectionVerified),
                        KitTextRole.Body,
                    )
                is EditorVerdict.Failed ->
                    FailureVerdict(v.failure) { action ->
                        when (action) {
                            FailureAction.ReviewCertificate -> actions.reviewCertificate()
                            FailureAction.UpdatePassword,
                            FailureAction.UpdateToken -> actions.replacePassword()
                            else -> actions.test()
                        }
                    }
                is EditorVerdict.NoAddressAnswered ->
                    v.outcomes.forEach { KitRow(title = it.display, supporting = it.verdict.words(), onClick = actions::test) }
                null -> Unit
            }
            if (state.entry == EntryKind.Address) {
                KitRow(title = stringResource(Res.string.e7SetupTestConnection), supporting = null, onClick = actions::test)
            }
            val host = (state.verdict as? EditorVerdict.Answered)?.origin?.host ?: state.address.ifBlank { null }
            KitButton(
                label = host?.let { stringResource(Res.string.settingsV3ConnectTo, it) } ?: stringResource(Res.string.e7SetupTestConnection),
                onClick = actions::saveAndConnect,
                enabled = !state.checking,
            )
        }
        CleartextConsentSheet(state.consent, onAllow = actions::allowHttp, onDecline = actions::declineHttp)
        PinReviewSheet(state.pinReview, onTrust = actions::trustCertificate, onCancel = actions::cancelCertificateReview)
    }
}

/** One server's page with its connection details (07 §5 E04). */
@Composable
public fun ServerPageScreen(state: ServerPageUi, actions: ServerPageActions) {
    KitScreen(title = state.name, backLabel = stringResource(Res.string.kitTopBarBack), onBack = actions::back) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitText(stringResource(Res.string.externalAgentsDetailConnection), KitTextRole.Supporting)
            KitText(state.address + " · " + state.status.words(), KitTextRole.Body)
            when (state.security) {
                TransportSecurity.ThisDevice -> KitText(stringResource(Res.string.settingsV3ThisPhone), KitTextRole.Supporting)
                TransportSecurity.HttpsPinned -> {
                    KitText(stringResource(Res.string.serverTrustCertificatePinned), KitTextRole.Supporting)
                    state.pinFingerprint?.let { KitText(it, KitTextRole.Supporting) }
                }
                TransportSecurity.HttpAllowed -> KitText(stringResource(Res.string.addServerCleartextConfirmed), KitTextRole.Supporting)
                TransportSecurity.HttpNotAllowed -> KitText(stringResource(Res.string.serverTrustHttpUnconfirmed), KitTextRole.Supporting)
                TransportSecurity.Https -> Unit
            }
            state.failure?.let { FailureVerdict(it, actions::fix) }
            if (state.certificateUnreadable) KitText(stringResource(Res.string.serverTrustCertificateFailed), KitTextRole.Supporting)
            if (state.active) {
                KitButton(label = stringResource(Res.string.serverDisconnectFrom, state.name), onClick = actions::disconnect)
            } else if (state.failure == null) {
                KitButton(label = stringResource(Res.string.settingsV3ConnectTo, state.name), onClick = actions::connect, enabled = !state.busy)
            }
            KitRow(title = stringResource(Res.string.e7SetupEditServer), supporting = null, onClick = actions::edit)
            if (state.security == TransportSecurity.Https || state.security == TransportSecurity.HttpsPinned) {
                KitRow(title = stringResource(Res.string.serverTrustCertificateInspect), supporting = null, onClick = actions::reviewCertificate)
            }
            KitRow(title = stringResource(Res.string.externalAgentsRemoveNamed, state.name), supporting = null, onClick = actions::forget)
        }
        ForgetSheet(if (state.forgetting) state.name else null, onConfirm = actions::confirmForget, onCancel = actions::cancelForget)
        PinReviewSheet(state.pinReview, onTrust = actions::trustCertificate, onCancel = actions::cancelCertificateReview)
    }
}

/**
 * The switcher's body (07 §2.3 `SheetKey.ServerSwitcher`): the shell's sheet host draws the frame once `SheetKey`
 * exists; until then [ServerSwitcherSheet] wraps it in a `KitSheet`.
 */
@Composable
public fun ServerSwitcherContent(state: ServerSwitcherUi, actions: ServerSwitcherActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        state.rows.forEach { row ->
            val where = if (row.port != null) row.host + ":" + row.port else row.host
            KitRow(title = row.name, supporting = where + " · " + row.status.words(), onClick = { actions.choose(row.id) })
            if (row.current) {
                KitRow(title = stringResource(Res.string.serverRowDetailsTitle, row.name), supporting = null, onClick = { actions.manage(row.id) })
            }
        }
        KitButton(label = stringResource(Res.string.serverSwitcherAddServer), onClick = actions::addServer)
    }
}

@Composable
public fun ServerSwitcherSheet(visible: Boolean, state: ServerSwitcherUi, actions: ServerSwitcherActions, onDismiss: () -> Unit) {
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.serverSwitcherTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        ServerSwitcherContent(state, actions)
    }
}
