package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.lifecycle.PhoneHostSupport
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.lifecycle.PhoneServerCandidate
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowFacts
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.kitServerSupporting
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentAccountTitle
import io.github.eslamasabry.oc.ui.words.res.capsuleRemove
import io.github.eslamasabry.oc.ui.words.res.codexProjectFolder
import io.github.eslamasabry.oc.ui.words.res.connectionServerAddress
import io.github.eslamasabry.oc.ui.words.res.e7SetupConnect
import io.github.eslamasabry.oc.ui.words.res.e7SetupEdit
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.rows40Move
import io.github.eslamasabry.oc.ui.words.res.serverRowDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.serverRowDetailsVersion
import io.github.eslamasabry.oc.ui.words.res.settingsV3ServerSettings
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** True when the saved server is this phone's own Linux (the row then hears it from the screen reader). */
internal fun phoneOwnServer(server: SavedServer, phoneHost: Boolean): Boolean =
    PhoneOwnServers.isPhoneOwnServer(
        PhoneServerCandidate(server.id.raw, server.address),
        if (phoneHost) PhoneHostSupport.Android else PhoneHostSupport.Elsewhere,
    )

/** The plain parts a saved-server list is handed with: whether the phone hosts one, and which row is chosen. */
internal class ServerSavedRowSelection(
    val phoneHost: Boolean = false,
    val selectedId: ProfileId? = null,
)

/** The row's supporting line: the kind (when kinds differ), waiting/running counts and the secret note. */
@Composable
internal fun serverSupportLine(showKind: Boolean, kind: String, server: SavedServer, facts: ServerRowFacts?): String {
    val waiting = if (server.connected) 0 else (facts?.waiting ?: 0)
    return kitServerSupporting(
        kind = if (showKind) kind else "",
        connected = server.connected,
        waiting = waiting,
        running = facts?.running ?: 0,
        secret = serverRowSecretNote(facts),
        queued = facts?.queued ?: 0,
    )
}

/** The row's visible More menu: account, connect, move queued, settings, edit, details and remove. */
@Composable
internal fun serverMenuItems(
    state: ServersUi,
    server: SavedServer,
    enabled: KitEnabled,
    actions: ServersActions,
    onOpenDetails: () -> Unit,
): List<KitMenuItem> {
    val facts = state.rows.facts[server.id]
    val destination =
        state.rows.moveDestination
            ?.takeIf { it != server.id }
            ?.let { id -> state.servers.firstOrNull { it.id == id } }
    return buildList {
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
                    pluralStringResource(Res.plurals.rows40Move, facts!!.queued, facts.queued, destination.name),
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
        add(KitMenuItem(stringResource(Res.string.kitDetails), onOpenDetails))
        add(
            KitMenuItem(
                stringResource(Res.string.capsuleRemove),
                { actions.remove(server.id) },
                destructive = true,
                enabled = enabled,
            )
        )
    }
}

/** The Details sheet of a saved server row: its address, version and project folder. */
@Composable
internal fun ServerDetailsSheet(
    state: ServersUi,
    names: Map<ProfileId, String>,
    detailsFor: ProfileId?,
    onClose: () -> Unit,
) {
    val details = state.servers.firstOrNull { it.id == detailsFor }
    KitSheet(
        visible = details != null,
        onDismissRequest = onClose,
        title =
            details?.let { stringResource(Res.string.serverRowDetailsTitle, names[it.id] ?: it.name) }
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
