package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowFacts
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceTile
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The plain facts a wide detail panel is handed about the chosen server, built at the call site. */
internal class WideServerDetailParts(
    val server: SavedServer,
    val displayName: String,
    val kindLabel: String,
    val secretNote: String?,
    val phoneHost: Boolean,
)

/** The status word under the chosen server's name: connected, waiting, working or not connected. */
@Composable
internal fun wideServerStatusText(connected: Boolean, waiting: Int, running: Int): String =
    when {
        connected -> stringResource(Res.string.serverRowConnected)
        waiting > 0 -> pluralStringResource(Res.plurals.rows40Waiting, waiting, waiting)
        running == 1 -> stringResource(Res.string.otherServerWorking__eq1)
        running > 0 -> pluralStringResource(Res.plurals.otherServerWorking, running, running)
        else -> stringResource(Res.string.settingsV3NotConnected)
    }

/** The tone of the status word: accent when connected, primary when a row needs the person. */
@Composable
internal fun wideServerStatusTone(connected: Boolean, waiting: Int): TextTone =
    when {
        connected -> TextTone.Accent
        waiting > 0 -> TextTone.Primary
        else -> TextTone.Secondary
    }

/** The notes under the facts card's Activity row: working, waiting and queued counts, and the secret note. */
@Composable
internal fun wideServerActivityNotes(running: Int, waiting: Int, queued: Int, secretNote: String?): List<String> =
    buildList {
        if (running == 1) add(stringResource(Res.string.otherServerWorking__eq1))
        else if (running > 0) add(pluralStringResource(Res.plurals.otherServerWorking, running, running))
        if (waiting > 0) add(pluralStringResource(Res.plurals.rows40Waiting, waiting, waiting))
        if (queued == 1) add(stringResource(Res.string.serverRowQueuedWaiting__eq1))
        else if (queued > 0) add(pluralStringResource(Res.plurals.serverRowQueuedWaiting, queued, queued))
        secretNote?.let(::add)
    }

/** The panel's head: the server's glyph tile, its name and its status word. */
@Composable
internal fun WideServerHeader(
    parts: WideServerDetailParts,
    phone: Boolean,
    statusText: String,
    statusTone: TextTone,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitSurfaceTile(
            icon = if (phone) KitIcons.phone else KitIcons.server,
            tone = if (parts.server.connected) TextTone.Accent else TextTone.Secondary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitText(parts.displayName, role = TypeRole.Headline, maxLines = 2)
            KitText(statusText, role = TypeRole.Label, tone = statusTone)
        }
    }
}

/** The panel's facts: address, runs (kind and version), project folder and the activity notes. */
@Composable
internal fun WideServerFacts(
    server: SavedServer,
    runsValue: String,
    facts: ServerRowFacts?,
    activityNotes: List<String>,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        WideFactRow(
            label = stringResource(Res.string.connectionServerAddress),
            value = server.address,
            mono = true,
        )
        KitDivider()
        WideFactRow(
            label = stringResource(Res.string.connectorCardRuns),
            value = runsValue,
            mono = false,
        )
        facts?.directory?.takeIf(String::isNotBlank)?.let { dir ->
            KitDivider()
            WideFactRow(
                label = stringResource(Res.string.codexProjectFolder),
                value = dir,
                mono = true,
            )
        }
        if (activityNotes.isNotEmpty()) {
            KitDivider()
            WideFactRow(
                label = stringResource(Res.string.chatsHomeTitle),
                value = activityNotes.joinToString(" · "),
                mono = false,
            )
        }
    }
}

/** The panel's primary row: Open/Connect to (wide, primary) and Edit named after the server. */
@Composable
internal fun WidePrimaryActions(
    parts: WideServerDetailParts,
    state: ServersUi,
    actions: ServersActions,
    enabled: KitEnabled,
) {
    val primaryLabel =
        when {
            state.newConversation || parts.server.connected ->
                stringResource(Res.string.addServerReadyOpen, parts.displayName)
            else -> stringResource(Res.string.settingsV3ConnectTo, parts.displayName)
        }
    val primaryClick: () -> Unit = {
        when {
            state.newConversation -> actions.create(parts.server.id)
            parts.server.connected -> actions.settings(parts.server.id)
            else -> actions.connect(parts.server.id)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.kitMaxWidth(CapRole.Control, CapAlign.Start)) {
                KitButton(
                    label = primaryLabel,
                    onClick = primaryClick,
                    role = KitButtonRole.Primary,
                    enabled = enabled,
                    fullWidth = true,
                )
            }
        }
        KitButton(
            label = stringResource(Res.string.serversEditNamed, parts.displayName),
            onClick = { actions.edit(parts.server.id) },
            role = KitButtonRole.Secondary,
            enabled = enabled,
            expand = false,
        )
    }
}

/** The panel's further actions: server settings, move queued, account and remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WideMoreActions(
    parts: WideServerDetailParts,
    state: ServersUi,
    facts: ServerRowFacts?,
    actions: ServersActions,
    enabled: KitEnabled,
) {
    val queued = facts?.queued ?: 0
    val destination =
        state.rows.moveDestination
            ?.takeIf { it != parts.server.id }
            ?.let { id -> state.servers.firstOrNull { it.id == id } }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (!parts.server.connected && !state.newConversation) {
            KitButton(
                label = stringResource(Res.string.settingsV3ServerSettings),
                onClick = { actions.settings(parts.server.id) },
                role = KitButtonRole.Secondary,
                expand = false,
            )
        }
        if (queued > 0 && destination != null) {
            KitButton(
                label = pluralStringResource(Res.plurals.rows40Move, queued, queued, destination.name),
                onClick = { actions.moveQueued(parts.server.id) },
                role = KitButtonRole.Secondary,
                enabled = enabled,
                expand = false,
            )
        }
        if (facts?.account == true) {
            KitButton(
                label = stringResource(Res.string.agentAccountTitle),
                onClick = { actions.openAccount(parts.server.id) },
                role = KitButtonRole.Secondary,
                expand = false,
            )
        }
        KitButton(
            label = stringResource(Res.string.agentsRemoveAction, parts.displayName),
            onClick = { actions.remove(parts.server.id) },
            role = KitButtonRole.Tertiary,
            danger = true,
            enabled = enabled,
            expand = false,
        )
    }
}
