package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
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
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
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
import kotlinx.coroutines.flow.drop

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
    /**
     * The page is the bottom of the stack (Dart `isRoot`, servers_state.dart:83): the whole app before a server is in
     * use. It then carries the product mark and name, and Report a problem behind ⋯, instead of Back and "Servers".
     */
    root: Boolean = false,
) {
    var hostSetup by rememberSaveable { mutableStateOf(initialHostSetup) }
    var tailscaleSetup by rememberSaveable { mutableStateOf(initialTailscaleSetup) }
    var phoneChoice by rememberSaveable { mutableStateOf(onThisPhone != null) }
    var arrivalOpen by remember { mutableStateOf(false) }
    val arrivalName = rememberTextFieldState()
    val arrivalAddress = rememberTextFieldState()
    val arrivalUser = rememberTextFieldState("opencode")
    val arrivalPassword = remember { TextFieldState() }
    LaunchedEffect(state.pairingArrival) {
        val fill = actions.takePairing() ?: return@LaunchedEffect
        arrivalAddress.edit { replace(0, length, fill.address) }
        fill.username?.let { user -> arrivalUser.edit { replace(0, length, user) } }
        fill.takePassword()?.let { secret -> arrivalPassword.edit { replace(0, length, secret) } }
        arrivalOpen = true
    }
    LaunchedEffect(arrivalAddress) {
        snapshotFlow { arrivalAddress.text.toString() }.drop(1).collect { actions.allowCleartext(false, it) }
    }
    LaunchedEffect(state.savedGeneration) {
        if (state.savedGeneration > 0) {
            arrivalPassword.edit { replace(0, length, "") }
            arrivalOpen = false
        }
    }
    val enabled = KitEnabled.of(!state.busy, stringResource(Res.string.serversWorking))
    val empty = state.servers.isEmpty()
    KitScreen(
        topBar = {
            if (root)
                ServersBrandBar(
                    name = stringResource(Res.string.openCodeConnectionLabel),
                    moreLabel = stringResource(Res.string.kitTopBarMore),
                    menu = listOf(
                        KitMenuItem(stringResource(Res.string.e7LibraryReportABug), actions::reportProblem, KitIcons.bug),
                    ),
                )
            else
                KitTopBar(
                    title = stringResource(if (state.newConversation) Res.string.serversNewTitle else Res.string.serversTitle),
                    exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = actions::back,
                )
        },
        loading = state.busy,
        loadingLabel = stringResource(Res.string.serversWorking),
        width = if (empty) KitScreenWidth.Reading else KitScreenWidth.List,
        bottom = {
            val primary = KitAction(
                label = stringResource(if (empty && phoneChoice) Res.string.onboardingTermuxSetup
                    else if (empty) Res.string.welcomeComputerConnect else Res.string.e7SetupAddServer),
                onClick = if (empty && phoneChoice && onThisPhone != null) onThisPhone else ({ actions.edit(null) }),
                icon = if (empty) null else KitIcons.add, enabled = enabled,
            )
            if (empty) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KitButton(primary, fullWidth = true, hero = true) }
            else KitActionBlock(primary = primary)
        },
        edges = KitScreenEdges.Bleed,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            state.issue?.takeIf { it != ServerIssue.Connected }?.let { issue ->
                // s23-oc1move: a computer still on OpenCode 1 gets its guided update, not a dead end.
                val guide = state.openCode1?.let { id ->
                    io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction(
                        stringResource(Res.string.openCode1UnsupportedTitle), { actions.openUpdateGuide(id) },
                    )
                }
                KitErrorNotice(issueWords(issue, state.failure),
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S1.dp),
                    failure = state.failure, detailsLabel = stringResource(Res.string.kitDetails), copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = guide)
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
        TailscaleSetupSheet(tailscaleSetup, tailscale, { tailscaleSetup = false }) { reviewed ->
            arrivalAddress.edit { replace(0, length, reviewed) }
            actions.allowCleartext(false, reviewed)
            arrivalOpen = true
        }
        KitSheet(visible = arrivalOpen, onDismissRequest = {
            if (!state.busy) {
                arrivalPassword.edit { replace(0, length, "") }
                arrivalOpen = false
            }
        }, title = stringResource(Res.string.serversAdd), dismissible = !state.busy,
            primary = io.github.eslamasabry.oc.ui.kit.KitAction(stringResource(Res.string.serversSave), {
                actions.save(arrivalName.text.toString(), arrivalAddress.text.toString(), arrivalUser.text.toString(), arrivalPassword.text.toString())
            }, enabled = enabled, working = state.busy),
        ) {
            // The existing arrivals contract supplies a fill, not an editor navigation target. Keep that memory-only
            // handoff usable without putting a password in a route key or recreating a pairing payload.
            KitField(arrivalName, stringResource(Res.string.serversName), enabled = enabled)
            KitField(arrivalAddress, stringResource(Res.string.serversAddress), kind = KitFieldKind.Url, enabled = enabled)
            KitField(arrivalUser, stringResource(Res.string.serversUsername), enabled = enabled)
            KitField(arrivalPassword, stringResource(Res.string.serversPassword), kind = KitFieldKind.Secret, enabled = enabled)
            KitSwitchRow(title = stringResource(Res.string.serversCleartext), supporting = stringResource(Res.string.serversCleartextHelp),
                checked = state.cleartextAllowed, enabled = enabled,
                onCheckedChange = { actions.allowCleartext(it, arrivalAddress.text.toString()) })
            state.issue?.let { KitErrorNotice(issueWords(it, state.failure), failure = state.failure,
                detailsLabel = stringResource(Res.string.kitDetails), copyLabel = stringResource(Res.string.kitCopyDetails)) }
        }
        ConnectionCheckSheet(state.connectionCheck, actions)
    }
}

@Composable
private fun issueWords(issue: ServerIssue, failure: Failure?): String =
    stringResource(
        if (issue == ServerIssue.HttpsRequired) Res.string.serverHttpsRequired
        else when (serverFailureKind(failure)) {
            ServerFailureKind.SignIn -> Res.string.serversSignInFailed
            ServerFailureKind.CleartextConsent -> Res.string.serversCleartextRequired
            ServerFailureKind.InvalidAddress -> Res.string.serversInvalidAddress
            ServerFailureKind.Network -> Res.string.serverFailureNetwork
            ServerFailureKind.Timeout -> Res.string.serverFailureTimeout
            ServerFailureKind.Certificate -> Res.string.serverFailureCertificate
            ServerFailureKind.Incompatible ->
                if (serverRunsOpenCode1(failure)) Res.string.serverFailureOpenCode1
                else Res.string.serverFailureIncompatible
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
                        ServerIssue.HttpsRequired -> Res.string.serverHttpsRequired
                    }
        }
    )

@Composable private fun schedulesLabel(): String = stringResource(Res.string.stateServerSchedules)
