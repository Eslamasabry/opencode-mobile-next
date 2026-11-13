package io.github.eslamasabry.oc.feature.settings.monitor

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
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
    if (state.servers.isEmpty()) {
        // The empty words follow the real list this section draws, not a second copy of it.
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        KitRowGroup(leadingTiles = true) {
            KitRow(
                title = stringResource(Res.string.notifyNoServersTitle),
                leading = KitRowLeading.Glyph(KitIcons.server),
                supporting = stringResource(Res.string.notifyNoServersDetail),
                supportingMaxLines = Int.MAX_VALUE,
            )
        }
        return
    }
    KitGroupNote(stringResource(Res.string.andrMonitorExplanation))
    if (state.saveFailed) KitNotice(stringResource(Res.string.andrMonitorSaveFailed))
    state.servers.forEach { server ->
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
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
