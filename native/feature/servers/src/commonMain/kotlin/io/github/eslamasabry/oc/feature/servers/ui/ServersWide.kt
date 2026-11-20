package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.lifecycle.PhoneHostSupport
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.lifecycle.PhoneServerCandidate
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceTile
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerReadyOpen
import io.github.eslamasabry.oc.ui.words.res.agentAccountTitle
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveAction
import io.github.eslamasabry.oc.ui.words.res.codexProjectFolder
import io.github.eslamasabry.oc.ui.words.res.connectionServerAddress
import io.github.eslamasabry.oc.ui.words.res.connectorCardRuns
import io.github.eslamasabry.oc.ui.words.res.e7SetupEditServer
import io.github.eslamasabry.oc.ui.words.res.monitorSession
import io.github.eslamasabry.oc.ui.words.res.otherServerWorking
import io.github.eslamasabry.oc.ui.words.res.otherServerWorking__eq1
import io.github.eslamasabry.oc.ui.words.res.rows40Move
import io.github.eslamasabry.oc.ui.words.res.rows40Waiting
import io.github.eslamasabry.oc.ui.words.res.serverRowConnected
import io.github.eslamasabry.oc.ui.words.res.serverRowDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.serverRowQueuedWaiting
import io.github.eslamasabry.oc.ui.words.res.serverRowQueuedWaiting__eq1
import io.github.eslamasabry.oc.ui.words.res.settingsV3ConnectTo
import io.github.eslamasabry.oc.ui.words.res.settingsV3NotConnected
import io.github.eslamasabry.oc.ui.words.res.settingsV3ServerSettings
import io.github.eslamasabry.oc.ui.words.res.upgradeReadyVersion
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** True when both the page's window class and its measured width are Expanded (>= 840 dp). */
internal fun isServersWide(windowClass: WindowClass, availableWidthDp: Float): Boolean =
    windowClass.isWide && availableWidthDp >= Layout.EXPANDED_FROM

/**
 * Shared Expanded (>= 840 dp) two-column frame for Servers pages:
 * places the page title (and optional end action) directly above two top-aligned, reading-capped
 * columns in one vertically centred group with no empty bands wider than a gutter.
 */
@Composable
internal fun ServersWideFrame(
    pageTitle: String,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
    subHeader: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = kitPageScrollState(),
    showTitle: Boolean = true,
) {
    val maxGroupWidth = (Layout.READING_WIDTH * 2 + Space.S6).dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(top = Space.S2.dp, bottom = Space.S6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = maxGroupWidth),
                    verticalArrangement = Arrangement.spacedBy(if (subHeader != null) Space.S4.dp else Space.S5.dp),
                ) {
                    if (showTitle || headerAction != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showTitle) {
                                KitText(
                                    pageTitle,
                                    Modifier.weight(1f).semantics { heading() },
                                    role = TypeRole.LargeTitle,
                                    kind = KitTextKind.Frame,
                                    pageTitle = true,
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            headerAction?.invoke()
                        }
                    }
                    subHeader?.let { banner ->
                        Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading, CapAlign.Start)) {
                            banner()
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.S6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start)) { start() }
                        Box(Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start)) { end() }
                    }
                }
            }
        }
    }
}

/** Natural-height detail panel for the server chosen from the wide Servers list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WideServerDetailPanel(
    server: SavedServer,
    displayName: String,
    kindLabel: String,
    secretNote: String?,
    state: ServersUi,
    actions: ServersActions,
    enabled: KitEnabled,
    phoneHost: Boolean,
) {
    val facts = state.rows.facts[server.id]
    val phone =
        PhoneOwnServers.isPhoneOwnServer(
            PhoneServerCandidate(server.id.raw, server.address),
            if (phoneHost) PhoneHostSupport.Android else PhoneHostSupport.Elsewhere,
        )
    val waiting = if (server.connected) 0 else (facts?.waiting ?: 0)
    val running = facts?.running ?: 0
    val queued = facts?.queued ?: 0
    val statusText =
        when {
            server.connected -> stringResource(Res.string.serverRowConnected)
            waiting > 0 -> pluralStringResource(Res.plurals.rows40Waiting, waiting, waiting)
            running == 1 -> stringResource(Res.string.otherServerWorking__eq1)
            running > 0 -> pluralStringResource(Res.plurals.otherServerWorking, running, running)
            else -> stringResource(Res.string.settingsV3NotConnected)
        }
    val statusTone =
        when {
            server.connected -> TextTone.Accent
            waiting > 0 -> TextTone.Primary
            else -> TextTone.Secondary
        }
    val version = facts?.version?.takeIf(String::isNotBlank)?.let(::plainServerVersion)
    val runsValue = listOfNotNull(kindLabel, version).joinToString(" · ")
    val activityNotes =
        buildList {
            if (running == 1) add(stringResource(Res.string.otherServerWorking__eq1))
            else if (running > 0) add(pluralStringResource(Res.plurals.otherServerWorking, running, running))
            if (waiting > 0) add(pluralStringResource(Res.plurals.rows40Waiting, waiting, waiting))
            if (queued == 1) add(stringResource(Res.string.serverRowQueuedWaiting__eq1))
            else if (queued > 0) add(pluralStringResource(Res.plurals.serverRowQueuedWaiting, queued, queued))
            secretNote?.let(::add)
        }
    val destination =
        state.rows.moveDestination
            ?.takeIf { it != server.id }
            ?.let { id -> state.servers.firstOrNull { it.id == id } }

    KitSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitSurfaceTile(
                    icon = if (phone) KitIcons.phone else KitIcons.server,
                    tone = if (server.connected) TextTone.Accent else TextTone.Secondary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(displayName, role = TypeRole.Headline, maxLines = 2)
                    KitText(statusText, role = TypeRole.Label, tone = statusTone)
                }
            }
            KitDivider()
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
                        label = stringResource(Res.string.monitorSession),
                        value = activityNotes.joinToString(" · "),
                        mono = false,
                    )
                }
            }
            KitDivider()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                val primaryLabel =
                    when {
                        state.newConversation || server.connected ->
                            stringResource(Res.string.addServerReadyOpen, displayName)
                        else -> stringResource(Res.string.settingsV3ConnectTo, displayName)
                    }
                val primaryClick: () -> Unit = {
                    when {
                        state.newConversation -> actions.create(server.id)
                        server.connected -> actions.settings(server.id)
                        else -> actions.connect(server.id)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
                        label = stringResource(Res.string.e7SetupEditServer),
                        onClick = { actions.edit(server.id) },
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                        expand = false,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!server.connected && !state.newConversation) {
                        KitButton(
                            label = stringResource(Res.string.settingsV3ServerSettings),
                            onClick = { actions.settings(server.id) },
                            role = KitButtonRole.Secondary,
                            expand = false,
                        )
                    }
                    if (queued > 0 && destination != null) {
                        KitButton(
                            label = pluralStringResource(Res.plurals.rows40Move, queued, queued, destination.name),
                            onClick = { actions.moveQueued(server.id) },
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                            expand = false,
                        )
                    }
                    if (facts?.account == true) {
                        KitButton(
                            label = stringResource(Res.string.agentAccountTitle),
                            onClick = { actions.openAccount(server.id) },
                            role = KitButtonRole.Secondary,
                            expand = false,
                        )
                    }
                    KitButton(
                        label = stringResource(Res.string.agentsRemoveAction, displayName),
                        onClick = { actions.remove(server.id) },
                        role = KitButtonRole.Tertiary,
                        danger = true,
                        enabled = enabled,
                        expand = false,
                    )
                }
            }
        }
    }
}

/** Natural-height facts card for a connected server's Ready page on wide windows. */
@Composable
internal fun WideServerReadyDetailsPanel(
    readyName: String,
    host: String?,
    version: String?,
    runtimes: List<String>,
    directory: String,
) {
    val headerStatus =
        listOfNotNull(
            stringResource(Res.string.serverRowConnected),
            version?.takeIf(String::isNotBlank)?.let { stringResource(Res.string.upgradeReadyVersion, it) },
        ).joinToString(" · ")
    val rows =
        buildList<Triple<String, String, Boolean>> {
            host?.takeIf(String::isNotBlank)?.let {
                add(Triple(stringResource(Res.string.connectionServerAddress), it, true))
            }
            if (runtimes.isNotEmpty()) {
                add(Triple(stringResource(Res.string.connectorCardRuns), runtimes.joinToString(", "), false))
            }
            if (directory.isNotBlank()) {
                add(Triple(stringResource(Res.string.codexProjectFolder), directory, true))
            }
        }
    KitSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitSurfaceTile(icon = KitIcons.server, tone = TextTone.Accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(stringResource(Res.string.serverRowDetailsTitle, readyName), role = TypeRole.Headline)
                    KitText(headerStatus, role = TypeRole.Label, tone = TextTone.Accent)
                }
            }
            if (rows.isNotEmpty()) {
                KitDivider()
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                    rows.forEachIndexed { index, (label, value, mono) ->
                        if (index > 0) KitDivider()
                        WideFactRow(label = label, value = value, mono = mono)
                    }
                }
            }
        }
    }
}

/** Framed bay for the link illustration on wide server pages. */
@Composable
internal fun WideServerArtworkBay(content: @Composable () -> Unit) {
    KitSurface(
        modifier = Modifier.fillMaxWidth(),
        level = SurfaceLevel.Surface1,
        shape = KitShapeId.Panel,
        padding = KitSurfacePadding.Panel,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** One labelled fact row inside a wide detail panel, stacking cleanly at large text scales. */
@Composable
internal fun WideFactRow(label: String, value: String, mono: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        KitText(label, role = TypeRole.Label, tone = TextTone.Secondary)
        if (mono) {
            KitMonoText(value)
        } else {
            KitText(value, role = TypeRole.Body, tone = TextTone.Primary)
        }
    }
}
