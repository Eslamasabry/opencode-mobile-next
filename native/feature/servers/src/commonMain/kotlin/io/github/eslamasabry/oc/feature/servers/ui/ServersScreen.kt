package io.github.eslamasabry.oc.feature.servers.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
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
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
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
import io.github.eslamasabry.oc.ui.words.res.serversRemoveConfirm
import io.github.eslamasabry.oc.ui.words.res.serversRemoveFailed
import io.github.eslamasabry.oc.ui.words.res.serversSave
import io.github.eslamasabry.oc.ui.words.res.serversSaveFailed
import io.github.eslamasabry.oc.ui.words.res.serversSaved
import io.github.eslamasabry.oc.ui.words.res.serversSignInFailed
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.serversUsername
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import io.github.eslamasabry.oc.ui.words.res.usageTitle
import org.jetbrains.compose.resources.stringResource

/**
 * Every visual control is an existing kit part. Editable secrets stay in an unsaved secure text
 * field.
 */
@Composable
public fun ServersScreen(state: ServersUi, actions: ServersActions) {
    val name = remember { TextFieldState() }
    val address = remember { TextFieldState() }
    val username = remember { TextFieldState("opencode") }
    // Do not use rememberTextFieldState/rememberSaveable here: a password must never enter saved
    // instance state.
    val password = remember { TextFieldState() }
    var removing by remember { mutableStateOf<ProfileId?>(null) }
    LaunchedEffect(state.savedGeneration) {
        if (state.savedGeneration > 0) password.edit { replace(0, length, "") }
    }
    LaunchedEffect(address) {
        snapshotFlow { address.text.toString() }
            .collect { actions.allowCleartext(false, address.text.toString()) }
    }
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
            if (state.servers.isEmpty()) KitText(stringResource(Res.string.serversEmpty))
            else KitText(stringResource(Res.string.serversSaved))
            for (server in state.servers) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(server.name)
                    KitText(server.address)
                    if (server.connected) KitText(stringResource(Res.string.serversConnected))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                        KitButton(
                            stringResource(Res.string.serversCreate),
                            { actions.create(server.id) },
                            enabled = enabled,
                        )
                        KitButton(
                            stringResource(Res.string.serversConnect),
                            { actions.connect(server.id) },
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                        )
                        if (server.connected) {
                            KitButton(
                                stringResource(Res.string.usageTitle),
                                { actions.openUsage(server.id) },
                                role = KitButtonRole.Tertiary,
                            )
                        }
                        KitButton(
                            stringResource(Res.string.serversRemove),
                            { removing = server.id },
                            role = KitButtonRole.Tertiary,
                            destructive = true,
                            enabled = enabled,
                        )
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
            KitText(stringResource(Res.string.serversAdd))
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
