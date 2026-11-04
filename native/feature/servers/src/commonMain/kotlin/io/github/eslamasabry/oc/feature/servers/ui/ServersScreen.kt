package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
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
    onThisPhone: (() -> Unit)? = null,
) {
    var hostSetup by remember { mutableStateOf(false) }
    HostSetupSheet(hostSetup) { hostSetup = false }
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
                if (issue == ServerIssue.Connected) KitNotice(issueWords(issue, state.failure))
                else
                    KitErrorNotice(
                        issueWords(issue, state.failure),
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
            if (state.servers.isEmpty()) KitNotice(stringResource(Res.string.serversEmpty))
            ServerSavedRows(state, actions, enabled, phoneHost = onThisPhone != null)
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
