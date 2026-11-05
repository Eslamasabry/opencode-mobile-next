package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Flutter servers_state.dart: a single ranked list, its visible row menus, and one pinned Add server. */
@Composable
public fun ServersScreen(
    state: ServersUi,
    actions: ServersActions,
    onThisPhone: (() -> Unit)? = null,
    onInspectSetup: ((ProfileId) -> Unit)? = null,
    onTerminal: ((ProfileId) -> Unit)? = null,
    tailscale: TailscaleSetup? = null,
    initialHostSetup: Boolean = false,
    initialTailscaleSetup: Boolean = false,
) {
    var hostSetup by rememberSaveable { mutableStateOf(initialHostSetup) }
    var tailscaleSetup by rememberSaveable { mutableStateOf(initialTailscaleSetup) }
    var phoneChoice by rememberSaveable { mutableStateOf(onThisPhone != null) }
    val enabled = KitEnabled.of(!state.busy, stringResource(Res.string.serversWorking))
    val empty = state.servers.isEmpty()
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(if (state.newConversation) Res.string.serversNewTitle else Res.string.serversTitle),
                exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = actions::back,
            )
        },
        loading = state.busy,
        loadingLabel = stringResource(Res.string.serversWorking),
        width = if (empty) KitScreenWidth.Reading else KitScreenWidth.List,
        bottom = {
            KitButton(
                label = stringResource(if (empty && phoneChoice) Res.string.onboardingTermuxSetup
                    else if (empty) Res.string.welcomeComputerConnect else Res.string.e7SetupAddServer),
                onClick = if (empty && phoneChoice && onThisPhone != null) onThisPhone else ({ actions.edit(null) }),
                icon = if (empty) null else KitIcons.add,
                enabled = enabled, fullWidth = true,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            state.issue?.takeIf { it != ServerIssue.Connected }?.let { issue ->
                KitErrorNotice(issueWords(issue, state.failure),
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S1.dp),
                    failure = state.failure, detailsLabel = stringResource(Res.string.kitDetails), copyLabel = stringResource(Res.string.kitCopyDetails))
            }
            if (empty) {
                Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp).padding(top = (Space.S6 + Space.S6).dp)) {
                    KitText(stringResource(Res.string.firstRunWhereQuestion), role = TypeRole.LargeTitle)
                    Spacer(Modifier.height((Space.S6 + Space.S2).dp))
                    KitChoiceCards {
                        if (onThisPhone != null) KitChoiceCard(
                            title = stringResource(Res.string.onboardingTermuxSetup),
                            selected = phoneChoice, onSelect = { if (!state.busy) phoneChoice = true },
                            icon = KitIcons.phone,
                        )
                        KitChoiceCard(
                            title = stringResource(Res.string.firstRunOnComputer), supporting = stringResource(Res.string.welcomeComputerLine),
                            selected = !phoneChoice, onSelect = { if (!state.busy) phoneChoice = false }, icon = KitIcons.computer,
                        )
                    }
                    Spacer(Modifier.height(Space.S2.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                        KitButton(stringResource(Res.string.firstRunJustShowMe), actions::tryOffline, role = KitButtonRole.Tertiary, enabled = enabled)
                        if (onThisPhone != null) KitButton(stringResource(Res.string.welcomeMoreWays), onThisPhone, role = KitButtonRole.Tertiary, enabled = enabled)
                    }
                }
            } else {
                Spacer(Modifier.height(Space.S2.dp))
                ServerSavedRows(state, actions, enabled, phoneHost = onThisPhone != null)
            }
        }
        HostSetupSheet(hostSetup) { hostSetup = false }
        TailscaleSetupSheet(tailscaleSetup, tailscale, { tailscaleSetup = false }) { actions.edit(null) }
        ImportedSignInsSheet(state.importedSignIns, actions)
        ConnectionCheckSheet(state.connectionCheck, actions)
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

@Composable private fun schedulesLabel(): String = stringResource(Res.string.stateServerSchedules)
