package io.github.eslamasabry.oc.feature.chats.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The other servers' real requests, observed check-ins and unavailable reads share one list. */
@Composable
public fun MonitorInboxScreen(state: MonitorInboxUi, actions: MonitorInboxActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.monitorInboxTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = !state.facts.loaded || state.working,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            (state.failure ?: state.facts.failure)?.let { failure ->
                KitErrorNotice(
                    message = stringResource(Res.string.monitorOpenFailed),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refresh),
                )
            }
            if (
                state.facts.loaded &&
                    state.facts.rows.isEmpty() &&
                    state.facts.unreachable.isEmpty()
            )
                KitNotice(stringResource(Res.string.monitorInboxEmpty))
            for (row in state.facts.rows) {
                Column {
                    if (
                        row.kind in
                            setOf(
                                MonitoredRowKind.Permission,
                                MonitoredRowKind.Question,
                                MonitoredRowKind.Form,
                            )
                    )
                        KitNeedsYou(
                            reason =
                                if (row.kind == MonitoredRowKind.Permission)
                                    KitNeedsYouReason.Consent
                                else KitNeedsYouReason.Decision,
                            waiting = stringResource(Res.string.profileMonitorIfIgnored),
                        )
                    KitRow(
                        title =
                            row.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.monitorSession),
                        modifier = Modifier.testTag("monitor-row-${row.key}"),
                        supporting =
                            listOf(
                                    row.server,
                                    stringResource(
                                        when (row.kind) {
                                            MonitoredRowKind.CheckIn -> Res.string.monitorCheckInDue
                                            MonitoredRowKind.FailedRun ->
                                                Res.string.monitorInboxFailedRun
                                            else -> Res.string.monitorLastChecked
                                        }
                                    ),
                                    row.checked,
                                )
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                        supportingMaxLines = 3,
                        trailing = KitRowTrailing.Chevron,
                        enabled = !state.working,
                        disabledReason = stringResource(Res.string.monitorInboxOpening),
                        onClick = { actions.open(row.key) },
                    )
                }
            }
            for (server in state.facts.unreachable) KitNotice(
                title = stringResource(Res.string.monitorUnreachableTitle, server.name),
                message = stringResource(Res.string.monitorUnreachableDetail),
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.monitorUnreachableCheck, server.name),
                            actions::refresh,
                        )
                    ),
            )
            KitButton(
                stringResource(Res.string.connCheckAgain),
                actions::refresh,
                enabled =
                    if (state.working)
                        KitEnabled.Disabled(stringResource(Res.string.connCheckRunning))
                    else KitEnabled.Enabled,
            )
            KitRow(
                title = stringResource(Res.string.settingsV3Notifications),
                supporting = stringResource(Res.string.monitorInboxSettings),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::notificationSettings,
            )
        }
    }
    KitConfirm(
        spec =
            state.confirm?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.monitorSwitchToTitle, it.server),
                    body =
                        it.currentServer?.let { current ->
                            stringResource(Res.string.profileMonitorSwitchBody, current, it.server)
                        } ?: stringResource(Res.string.monitorInboxSwitchBody),
                    confirmLabel = stringResource(Res.string.monitorSwitchTo, it.server),
                )
            },
        onConfirm = actions::confirmSwitch,
        onCancel = actions::cancelSwitch,
    )
}
