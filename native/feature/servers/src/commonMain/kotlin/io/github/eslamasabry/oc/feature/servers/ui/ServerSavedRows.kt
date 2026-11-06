package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.lifecycle.PhoneHostSupport
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.lifecycle.PhoneServerCandidate
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Flutter _ServerRow: state before kind, addresses in Details, named actions in the visible More
 * menu.
 */
@Composable
internal fun ServerSavedRows(
    state: ServersUi,
    actions: ServersActions,
    enabled: KitEnabled,
    phoneHost: Boolean = false,
) {
    var detailsFor by remember { mutableStateOf<ProfileId?>(null) }
    val ordered = savedServersByUrgency(state)
    KitRowGroup(leadingTiles = true) {
        ordered.forEach { server ->
            val facts = state.rows.facts[server.id]
            val phone =
                PhoneOwnServers.isPhoneOwnServer(
                    PhoneServerCandidate(server.id.raw, server.address),
                    if (phoneHost) PhoneHostSupport.Android else PhoneHostSupport.Elsewhere,
                )
            val waiting = if (server.connected) 0 else (facts?.waiting ?: 0)
            val kind =
                stringResource(
                    when (facts?.backend) {
                        BackendKind.Codex -> Res.string.addServerTypeCodex
                        BackendKind.Paseo -> Res.string.addServerTypePaseo
                        BackendKind.OpenCode2 -> Res.string.setupRuntimeTwo
                        null -> Res.string.firstRunAgentOpenCode
                    }
                )
            val secret =
                if (facts?.needsSecret == true)
                    stringResource(
                        if (facts.backend == BackendKind.Codex || facts.backend == BackendKind.Paseo)
                            Res.string.e7SetupTokenRequired
                        else Res.string.e7SetupPasswordRequired
                    )
                else null
            val supporting =
                kitServerSupporting(
                    kind = kind,
                    connected = server.connected,
                    waiting = waiting,
                    running = facts?.running ?: 0,
                    secret = secret,
                    queued = facts?.queued ?: 0,
                )
            val destination =
                state.rows.moveDestination
                    ?.takeIf { it != server.id }
                    ?.let { id -> state.servers.firstOrNull { it.id == id } }
            val items = buildList {
                if (facts?.account == true)
                    add(
                        KitMenuItem(
                            stringResource(Res.string.agentAccountTitle),
                            { actions.openAccount(server.id) },
                        )
                    )
                add(
                    KitMenuItem(
                        stringResource(Res.string.e7SetupConnect),
                        { actions.connect(server.id) },
                        enabled = enabled,
                    )
                )
                if ((facts?.queued ?: 0) > 0 && destination != null)
                    add(
                        KitMenuItem(
                            stringResource(Res.string.rows40Move, facts!!.queued, destination.name),
                            { actions.moveQueued(server.id) },
                            enabled = enabled,
                        )
                    )
                add(
                    KitMenuItem(
                        stringResource(Res.string.settingsV3ServerSettings),
                        { actions.settings(server.id) },
                    )
                )
                add(
                    KitMenuItem(
                        stringResource(Res.string.e7SetupEdit),
                        { actions.edit(server.id) },
                        enabled = enabled,
                    )
                )
                add(KitMenuItem(stringResource(Res.string.kitDetails), { detailsFor = server.id }))
                add(
                    KitMenuItem(
                        stringResource(Res.string.capsuleRemove),
                        { actions.remove(server.id) },
                        destructive = true,
                        enabled = enabled,
                    )
                )
            }
            KitRow(
                title = server.name,
                supporting = supporting,
                supportingMaxLines =
                    2, // F: lib/ui/screens/servers/server_rows.dart:191-196
                leading =
                    if (waiting <= 0)
                        KitRowLeading.Glyph(
                            if (phone) KitIcons.phone else KitIcons.server,
                            tile = false,
                        )
                    else null,
                state = if (waiting > 0) KitRowState(null, KitRowStateTone.NeedsYou) else null,
                selected = server.connected,
                enabled = enabled.isEnabled,
                disabledReason =
                    if (enabled.isEnabled) null
                    else stringResource(Res.string.serversWorking),
                onClick = {
                    if (state.newConversation) actions.create(server.id)
                    else actions.settings(server.id)
                },
                trailing = KitRowTrailing.Menu(server.name, items, title = server.name),
            )
        }
    }
    val details = state.servers.firstOrNull { it.id == detailsFor }
    KitSheet(
        visible = details != null,
        onDismissRequest = { detailsFor = null },
        title =
            details?.let { stringResource(Res.string.serverRowDetailsTitle, it.name) }
                ?: stringResource(Res.string.kitDetails),
    ) {
        details?.let {
            KitText(stringResource(Res.string.connectionServerAddress), role = TypeRole.Label)
            KitMonoText(it.address)
            state.rows.facts[it.id]?.version?.takeIf(String::isNotBlank)?.let { version ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(stringResource(Res.string.serverRowDetailsVersion), role = TypeRole.Label)
                KitMonoText(plainServerVersion(version))
            }
            state.rows.facts[it.id]?.directory?.takeIf(String::isNotBlank)?.let { directory ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(stringResource(Res.string.codexProjectFolder), role = TypeRole.Label)
                KitMonoText(directory)
            }
        }
    }
}

/**
 * Flutter servers_actions.dart:575-597: preserve list order within each urgency, including idle
 * credentials.
 */
internal fun savedServersByUrgency(
    state: ServersUi
): List<io.github.eslamasabry.oc.engine.connection.SavedServer> =
    state.servers.sortedBy { server ->
        val facts = state.rows.facts[server.id]
        when {
            (facts?.waiting ?: 0) > 0 -> 0
            (facts?.running ?: 0) > 0 -> 1
            else -> 2 // F: lib/ui/screens/servers/servers_actions.dart:589
        }
    }

/** Flutter strips the daemon's explanatory label, keeping a reported prerelease/build suffix. */
internal fun plainServerVersion(raw: String): String =
    Regex("""\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""").find(raw)?.value ?: raw.trim()
