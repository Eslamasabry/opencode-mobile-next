package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.lifecycle.PhoneHostSupport
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.lifecycle.PhoneServerCandidate
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServerSavedRows(
    state: ServersUi,
    actions: ServersActions,
    enabled: KitEnabled,
    phoneHost: Boolean = false,
) {
    var details by remember { mutableStateOf<Set<ProfileId>>(emptySet()) }
    for (server in state.servers) {
        val facts = state.rows.facts[server.id]
        val lines = buildList {
            // B-NAV-040: only the phone's own servers are marked as this phone; a forwarded
            // loopback is a server.
            if (
                PhoneOwnServers.isPhoneOwnServer(
                    PhoneServerCandidate(server.id.raw, server.address),
                    if (phoneHost) PhoneHostSupport.Android else PhoneHostSupport.Elsewhere,
                )
            )
                add(stringResource(Res.string.settingsV3ThisPhone))
            if (server.connected) add(stringResource(Res.string.serversConnected))
            if ((facts?.waiting ?: 0) > 0)
                add(stringResource(Res.string.rows40Waiting, facts!!.waiting!!))
            if ((facts?.running ?: 0) > 0)
                add(stringResource(Res.string.rows40Working, facts!!.running!!))
            if (facts?.needsSecret == true) add(stringResource(Res.string.rows40Secret))
            if ((facts?.queued ?: 0) > 0)
                add(stringResource(Res.string.rows40Queued, facts!!.queued))
            add(
                stringResource(
                    when (facts?.backend) {
                        BackendKind.Codex -> Res.string.addServerTypeCodex
                        BackendKind.Paseo -> Res.string.addServerTypePaseo
                        else -> Res.string.addServerTypeOpenCode
                    }
                )
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitRow(
                title = server.name,
                supporting = lines.joinToString(" · "),
                supportingMaxLines = 3,
                trailing = KitRowTrailing.Chevron,
                selected = server.connected,
                onClick = {
                    if (state.newConversation) actions.create(server.id)
                    else actions.settings(server.id)
                },
            )
            if (facts?.account == true)
                KitButton(
                    stringResource(Res.string.account40Title),
                    { actions.openAccount(server.id) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                if (!server.connected)
                    KitButton(
                        stringResource(Res.string.serversConnect),
                        { actions.connect(server.id) },
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                    )
                KitButton(
                    stringResource(Res.string.serversCreate),
                    { actions.create(server.id) },
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                )
                KitButton(
                    stringResource(Res.string.editor40Edit),
                    { actions.edit(server.id) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                )
                KitButton(
                    stringResource(Res.string.serverSettingsTitle),
                    { actions.settings(server.id) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                )
                if (server.connected)
                    KitButton(
                        stringResource(Res.string.usageTitle),
                        { actions.openUsage(server.id) },
                        role = KitButtonRole.Tertiary,
                        enabled = enabled,
                    )
                KitButton(
                    stringResource(Res.string.rows40Schedules),
                    { actions.openSchedules(server.id) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                )
            }
            val destination =
                state.rows.moveDestination
                    ?.takeIf { it != server.id }
                    ?.let { id -> state.servers.firstOrNull { it.id == id } }
            if ((facts?.queued ?: 0) > 0 && destination != null)
                KitButton(
                    stringResource(Res.string.rows40Move, facts!!.queued, destination.name),
                    { actions.moveQueued(server.id) },
                    role = KitButtonRole.Tertiary,
                    enabled = enabled,
                )
            // ANDR-2: explicit per-server opt-in to the one background monitor (off by default).
            state.monitoring[server.id]?.let { monitor ->
                if (monitor.available) {
                    KitSwitchRow(
                        title = stringResource(Res.string.monitorOptIn),
                        supporting = stringResource(Res.string.monitorOptInDetail),
                        checked = monitor.enabled,
                        enabled = enabled,
                        onCheckedChange = { actions.monitor(server.id, it) },
                    )
                    if (monitor.enabled)
                        KitText(
                            stringResource(
                                when (monitor.status) {
                                    ServerMonitorStatus.Current -> Res.string.monitorServerCurrent
                                    ServerMonitorStatus.Unavailable ->
                                        Res.string.monitorServerUnavailable
                                    ServerMonitorStatus.WifiRequired ->
                                        Res.string.monitorServerWifiRequired
                                    ServerMonitorStatus.Paused -> Res.string.monitorServerPaused
                                    ServerMonitorStatus.Waiting -> Res.string.monitorServerWaiting
                                }
                            )
                        )
                } else KitText(stringResource(Res.string.monitorServerUnavailable))
            }
            KitExpandRow(
                stringResource(Res.string.kitDetails),
                server.id in details,
                onExpandedChange = { open ->
                    details = if (open) details + server.id else details - server.id
                },
            ) {
                KitText(server.address)
                facts?.version?.takeIf { it.isNotBlank() }?.let { KitText(it) }
                facts?.directory?.takeIf { it.isNotBlank() }?.let { KitText(it) }
            }
            KitButton(
                stringResource(Res.string.serversRemove),
                { actions.remove(server.id) },
                role = KitButtonRole.Tertiary,
                destructive = true,
                enabled = enabled,
            )
        }
    }
}
