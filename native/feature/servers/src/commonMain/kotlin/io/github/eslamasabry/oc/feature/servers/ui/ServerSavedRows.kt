package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Flutter _ServerRow: state before kind, addresses in Details, named actions in the visible More menu. */
@Composable
internal fun ServerSavedRows(state: ServersUi, actions: ServersActions, enabled: KitEnabled, phoneHost: Boolean = false) {
    var menuFor by remember { mutableStateOf<ProfileId?>(null) }
    var detailsFor by remember { mutableStateOf<ProfileId?>(null) }
    val ordered = state.servers.sortedWith(compareByDescending<io.github.eslamasabry.oc.engine.connection.SavedServer> {
        (state.rows.facts[it.id]?.waiting ?: 0) > 0
    }.thenByDescending { (state.rows.facts[it.id]?.running ?: 0) > 0 }
        .thenByDescending { state.rows.facts[it.id]?.needsSecret == true })
    KitRowGroup {
        ordered.forEachIndexed { index, server ->
            val facts = state.rows.facts[server.id]
            val phone = PhoneOwnServers.isPhoneOwnServer(PhoneServerCandidate(server.id.raw, server.address),
                if (phoneHost) PhoneHostSupport.Android else PhoneHostSupport.Elsewhere)
            val lines = buildList {
                if (server.connected) add(stringResource(Res.string.serverRowConnected))
                if (!server.connected && (facts?.waiting ?: 0) > 0) add(stringResource(Res.string.rows40Waiting, facts!!.waiting!!))
                if ((facts?.running ?: 0) > 0) add(stringResource(Res.string.rows40Working, facts!!.running!!))
                if (facts?.needsSecret == true) add(stringResource(Res.string.rows40Secret))
                if ((facts?.queued ?: 0) > 0) add(stringResource(Res.string.rows40Queued, facts!!.queued))
                if (!server.connected && (facts?.waiting ?: 0) == 0 && (facts?.running ?: 0) == 0) add(stringResource(Res.string.settingsV3NotConnected))
                add(stringResource(when (facts?.backend) {
                    BackendKind.Codex -> Res.string.addServerTypeCodex
                    BackendKind.Paseo -> Res.string.addServerTypePaseo
                    else -> Res.string.addServerTypeOpenCode
                }))
            }
            val destination = state.rows.moveDestination?.takeIf { it != server.id }?.let { id -> state.servers.firstOrNull { it.id == id } }
            val items = buildList {
                if (facts?.account == true) add(KitMenuItem(stringResource(Res.string.agentAccountTitle), { actions.openAccount(server.id) }))
                add(KitMenuItem(stringResource(Res.string.e7SetupConnect), { actions.connect(server.id) }, enabled = enabled))
                if ((facts?.queued ?: 0) > 0 && destination != null) add(KitMenuItem(stringResource(Res.string.rows40Move, facts!!.queued, destination.name), { actions.moveQueued(server.id) }, enabled = enabled))
                add(KitMenuItem(stringResource(Res.string.settingsV3ServerSettings), { actions.settings(server.id) }))
                add(KitMenuItem(stringResource(Res.string.e7SetupEdit), { actions.edit(server.id) }, enabled = enabled))
                add(KitMenuItem(stringResource(Res.string.kitDetails), { detailsFor = server.id }))
                add(KitMenuItem(stringResource(Res.string.capsuleRemove), { actions.remove(server.id) }, destructive = true, enabled = enabled))
            }
            if (index > 0) KitDivider(inset = KitDividerInset.Text)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    KitRow(title = server.name, supporting = lines.joinToString(" · "), supportingMaxLines = 2, // F: lib/ui/screens/servers/server_rows.dart:191-196
                        leading = KitRowLeading.Glyph(if (phone) KitIcons.phone else KitIcons.server, tile = false),
                        selected = server.connected, enabled = enabled.isEnabled,
                        disabledReason = if (enabled.isEnabled) null else stringResource(Res.string.serversWorking),
                        onClick = { if (state.newConversation) actions.create(server.id) else actions.settings(server.id) })
                }
                KitMenu(expanded = menuFor == server.id, items = items, onDismissRequest = { menuFor = null }, title = server.name,
                    anchor = { KitIconButton(KitIcons.more, server.name, { menuFor = server.id }, enabled = enabled) })
            }
        }
    }
    val details = state.servers.firstOrNull { it.id == detailsFor }
    KitSheet(visible = details != null, onDismissRequest = { detailsFor = null }, title = stringResource(Res.string.kitDetails)) {
        details?.let {
            KitText(stringResource(Res.string.connectionServerAddress), role = TypeRole.Label)
            KitMonoText(it.address)
            state.rows.facts[it.id]?.version?.takeIf(String::isNotBlank)?.let { version ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(stringResource(Res.string.serverRowDetailsVersion), role = TypeRole.Label)
                KitMonoText(version)
            }
            state.rows.facts[it.id]?.directory?.takeIf(String::isNotBlank)?.let { directory ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(stringResource(Res.string.codexProjectFolder), role = TypeRole.Label)
                KitMonoText(directory)
            }
        }
    }
}
