package io.github.eslamasabry.oc.feature.integrations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorRow
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorState
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsActions
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsPhase
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.intaBrowseDetail
import io.github.eslamasabry.oc.ui.words.res.intaBrowseTitle
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsEmpty
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsEmptyBody
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsFailed
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsHelp
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsLoading
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsNoServer
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsNoServerBody
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsOpenServers
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsRefresh
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsSection
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsTitle
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsTryAgain
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsUnsupported
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.intaDismiss
import io.github.eslamasabry.oc.ui.words.res.intaStateBusy
import io.github.eslamasabry.oc.ui.words.res.intaStateConnected
import io.github.eslamasabry.oc.ui.words.res.intaStateFailed
import io.github.eslamasabry.oc.ui.words.res.intaStateNeedsSignIn
import io.github.eslamasabry.oc.ui.words.res.intaStateOff
import io.github.eslamasabry.oc.ui.words.res.intaStateStarting
import io.github.eslamasabry.oc.ui.words.res.intaStateUnknown
import io.github.eslamasabry.oc.ui.words.res.intaToggleFailedBody
import io.github.eslamasabry.oc.ui.words.res.intaTurnOffFailed
import io.github.eslamasabry.oc.ui.words.res.intaTurnOnFailed
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

/** The Connectors page: one switch row per MCP server, the state in plain words under its name. Kit parts only. */
@Composable
public fun ConnectorsScreen(state: ConnectorsUi, actions: ConnectorsActions) {
    val gutter = LocalKitTokens.current.gutter
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.intaConnectorsTitle),
                subtitle = state.serverName,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    if (state.server == null) emptyList()
                    else
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.intaConnectorsRefresh),
                                actions::refresh,
                                enabled = !state.refreshing,
                                disabledReason = stringResource(Res.string.intaConnectorsLoading),
                            )
                        ),
            )
        },
        loading = state.refreshing,
        loadingLabel = stringResource(Res.string.intaConnectorsLoading),
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            state.problem?.let { problem ->
                item(key = "problem") {
                    KitErrorNotice(
                        stringResource(Res.string.intaToggleFailedBody),
                        Modifier.padding(horizontal = gutter.dp),
                        title =
                            stringResource(
                                if (problem.turningOn) Res.string.intaTurnOnFailed else Res.string.intaTurnOffFailed,
                                problem.name,
                            ),
                        failure = problem.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        retry = KitLineAction(stringResource(Res.string.intaDismiss), actions::dismissProblem),
                    )
                }
            }
            when (state.phase) {
                ConnectorsPhase.NoServer ->
                    item(key = "no-server") {
                        KitStateView(
                            kind = KitStateKind.Missing,
                            title = stringResource(Res.string.intaConnectorsNoServer),
                            body = stringResource(Res.string.intaConnectorsNoServerBody),
                            primary = KitAction(stringResource(Res.string.intaConnectorsOpenServers), actions::openServers),
                        )
                    }
                ConnectorsPhase.Loading -> item(key = "loading") { KitSkeletonRows() }
                ConnectorsPhase.Unsupported ->
                    item(key = "unsupported") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.intaConnectorsUnsupported),
                            body = stringResource(Res.string.intaConnectorsUnsupportedBody),
                        )
                    }
                ConnectorsPhase.Empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.intaConnectorsEmpty),
                            body = stringResource(Res.string.intaConnectorsEmptyBody),
                        )
                    }
                ConnectorsPhase.Failed ->
                    item(key = "failed") {
                        KitErrorNotice(
                            stringResource(Res.string.intaConnectorsFailed),
                            Modifier.padding(horizontal = gutter.dp),
                            failure = state.failure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            retry = KitLineAction(stringResource(Res.string.intaConnectorsTryAgain), actions::refresh),
                        )
                    }
                ConnectorsPhase.Ready -> Unit
            }
            if (state.catalogueOffered) {
                item(key = "catalogue") {
                    KitRowGroup {
                        KitRow(
                            title = stringResource(Res.string.intaBrowseTitle),
                            supporting = stringResource(Res.string.intaBrowseDetail),
                            trailing = KitRowTrailing.Chevron,
                            supportingMaxLines = 2,
                            onClick = actions::openCatalogue,
                        )
                    }
                }
            }
            if (state.rows.isNotEmpty()) {
                item(key = "rows") {
                    KitRowGroup(label = stringResource(Res.string.intaConnectorsSection)) {
                        state.rows.forEach { row -> ConnectorSwitch(row, actions) }
                    }
                }
                item(key = "help") {
                    KitText(
                        stringResource(Res.string.intaConnectorsHelp),
                        role = KitTextRole.Supporting,
                        modifier = Modifier.padding(horizontal = gutter.dp, vertical = Space.S3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectorSwitch(row: ConnectorRow, actions: ConnectorsActions) {
    val busy = stringResource(Res.string.intaStateBusy)
    KitSwitchRow(
        title = row.name,
        checked = row.state.on,
        supporting = if (row.busy) busy else stateWords(row.state),
        enabled = if (row.busy) KitEnabled.Disabled(busy) else KitEnabled.Enabled,
        onCheckedChange = { on -> actions.toggle(row.name, on) },
    )
}

@Composable
private fun stateWords(state: ConnectorState): String =
    stringResource(
        when (state) {
            ConnectorState.Connected -> Res.string.intaStateConnected
            ConnectorState.Starting -> Res.string.intaStateStarting
            ConnectorState.Off -> Res.string.intaStateOff
            ConnectorState.NeedsSignIn -> Res.string.intaStateNeedsSignIn
            ConnectorState.Failed -> Res.string.intaStateFailed
            ConnectorState.Unknown -> Res.string.intaStateUnknown
        }
    )
