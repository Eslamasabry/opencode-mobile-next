package io.github.eslamasabry.oc.feature.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.monitor.presentation.MonitorActions
import io.github.eslamasabry.oc.feature.monitor.presentation.MonitorRowStatus
import io.github.eslamasabry.oc.feature.monitor.presentation.MonitorUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun MonitorScreen(state: MonitorUi, actions: MonitorActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.monitorNativeTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.monitorNativeBack),
                onExit = actions::back,
            )
        },
        loading = state.checking,
        loadingLabel = stringResource(Res.string.monitorNativeChecking),
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            item { KitGroupNote(stringResource(Res.string.monitorNativeForegroundOnly)) }
            item {
                KitButton(
                    stringResource(Res.string.monitorNativeCheck),
                    actions::check,
                    working = state.checking,
                )
            }
            if (state.failed) item { KitText(stringResource(Res.string.monitorNativeSaveFailed)) }
            if (state.rows.isEmpty())
                item { KitText(stringResource(Res.string.monitorNativeEmpty)) }
            for (row in state.rows) item(key = row.id.raw) {
                KitRowGroup(label = row.name) {
                    KitSwitchRow(
                        title = stringResource(Res.string.monitorNativeAllow),
                        checked = row.enabled,
                        supporting =
                            stringResource(
                                if (!row.supported) Res.string.monitorNativeUnsupported
                                else if (!row.usable) Res.string.monitorNativeReconnect
                                else Res.string.monitorNativeConsent
                            ),
                        risk = stringResource(Res.string.monitorNativeConsent),
                        enabled =
                            if (row.supported && row.usable) KitEnabled.Enabled
                            else
                                KitEnabled.Disabled(
                                    stringResource(
                                        if (row.supported) Res.string.monitorNativeReconnect
                                        else Res.string.monitorNativeUnsupported
                                    )
                                ),
                        onCheckedChange = { actions.setEnabled(row.id, it) },
                    )
                    KitText(
                        stringResource(
                            when (row.status) {
                                MonitorRowStatus.Off -> Res.string.monitorNativeOff
                                MonitorRowStatus.Waiting -> Res.string.monitorNativeWaiting
                                MonitorRowStatus.Checking -> Res.string.monitorNativeChecking
                                MonitorRowStatus.Current -> Res.string.monitorNativeCurrent
                                MonitorRowStatus.Partial -> Res.string.monitorNativePartial
                                MonitorRowStatus.Unknown -> Res.string.monitorNativeUnknown
                                MonitorRowStatus.Paused -> Res.string.monitorNativePaused
                            }
                        )
                    )
                    row.pending?.let {
                        KitText(stringResource(Res.string.monitorNativePending, it))
                    }
                    row.running?.let {
                        KitText(stringResource(Res.string.monitorNativeRunning, it))
                    }
                    for (session in row.sessions) KitRow(
                        title =
                            session.title.ifEmpty {
                                stringResource(Res.string.monitorNativeConversation)
                            },
                        supporting =
                            stringResource(
                                when {
                                    session.failed && session.fresh ->
                                        Res.string.monitorNativeFailed
                                    session.failed -> Res.string.monitorNativeEarlierFailure
                                    session.fresh -> Res.string.monitorNativeRequest
                                    else -> Res.string.monitorNativeEarlierRequest
                                }
                            ),
                        onClick = { actions.openSession(row.id, session.id) },
                    )
                }
            }
        }
    }
}
