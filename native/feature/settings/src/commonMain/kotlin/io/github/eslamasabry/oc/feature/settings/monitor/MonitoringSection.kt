package io.github.eslamasabry.oc.feature.settings.monitor

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ColumnScope.MonitoringSection(state: MonitoringUi, actions: MonitoringActions) {
    if (state.servers.isEmpty()) return
    KitGroupNote(stringResource(Res.string.andrMonitorExplanation))
    if (state.saveFailed) KitNotice(stringResource(Res.string.andrMonitorSaveFailed))
    state.servers.forEach { server ->
        KitRowGroup(label = server.name) {
            KitSwitchRow(
                title = stringResource(Res.string.andrMonitorEnabled),
                checked = server.enabled,
                enabled =
                    KitEnabled.of(!state.busy, stringResource(Res.string.andrMonitorChecking)),
                supporting =
                    stringResource(
                        when (server.state) {
                            MonitoringState.Off -> Res.string.andrMonitorOff
                            MonitoringState.Waiting -> Res.string.andrMonitorWaiting
                            MonitoringState.Checking -> Res.string.andrMonitorChecking
                            MonitoringState.Current -> Res.string.andrMonitorCurrent
                            MonitoringState.Unavailable -> Res.string.andrMonitorUnavailable
                            MonitoringState.WifiRequired -> Res.string.andrMonitorWifi
                            MonitoringState.Paused -> Res.string.andrMonitorPaused
                        }
                    ),
                onCheckedChange = { actions.enabled(server.id, it) },
            )
            if (server.enabled) {
                KitSwitchRow(
                    title = stringResource(Res.string.andrMonitorCheckIn),
                    checked = server.checkInMinutes != null,
                    enabled =
                        KitEnabled.of(!state.busy, stringResource(Res.string.andrMonitorChecking)),
                    supporting = stringResource(Res.string.andrMonitorCheckInDetail),
                    onCheckedChange = { actions.checkIn(server.id, if (it) 30 else null) },
                )
            }
        }
        if (server.enabled && server.checkInMinutes != null) {
            KitChoiceList(
                choices =
                    listOf(15, 30, 60, 120).map {
                        KitChoice(
                            it,
                            stringResource(Res.string.andrMonitorMinutes, Numbers.format(it)),
                        )
                    },
                selected = server.checkInMinutes,
                semanticsLabel = stringResource(Res.string.andrMonitorCheckIn),
                onSelect = { actions.checkIn(server.id, it) },
            )
        }
    }
}
