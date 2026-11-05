package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPort
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProgramKind
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogBuffer
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitPageState
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitLogDropped
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxDevLog
import io.github.eslamasabry.oc.ui.words.res.linuxDevLogEmpty
import io.github.eslamasabry.oc.ui.words.res.linuxDevLogFailed
import io.github.eslamasabry.oc.ui.words.res.linuxDevLogLoading
import io.github.eslamasabry.oc.ui.words.res.linuxDevNoLog
import io.github.eslamasabry.oc.ui.words.res.linuxDevPorts
import io.github.eslamasabry.oc.ui.words.res.linuxDevProgram
import io.github.eslamasabry.oc.ui.words.res.linuxDevPrograms
import io.github.eslamasabry.oc.ui.words.res.linuxDevProgramsNote
import io.github.eslamasabry.oc.ui.words.res.linuxDevReadLog
import io.github.eslamasabry.oc.ui.words.res.linuxDevScanFailed
import io.github.eslamasabry.oc.ui.words.res.linuxDevStop
import io.github.eslamasabry.oc.ui.words.res.linuxDevStopBody
import io.github.eslamasabry.oc.ui.words.res.linuxDevStopTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortAll
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortLocal
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortNotPage
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortPreview
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortRow
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortUnknown
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortUsedBy
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortsEmpty
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortsEmptyBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortsNote
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortsTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemFailed
import io.github.eslamasabry.oc.ui.words.res.linuxPortActions
import io.github.eslamasabry.oc.ui.words.res.linuxPortOwnerResult
import io.github.eslamasabry.oc.ui.words.res.linuxPortOwnerStop
import io.github.eslamasabry.oc.ui.words.res.setupNativeRefreshing
import io.github.eslamasabry.oc.ui.words.res.workRefresh
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * LinuxRoute.Ports (Flutter LinuxPortsPage): one row per port this phone could open, who uses it
 * and how far it reaches; a dev server's row opens its preview in the browser (the OpenCode server
 * and Paseo are "Not a web page").
 */
@Composable
public fun LinuxPortsScreen(state: LinuxPortsUi, actions: LinuxPortsActions) {
    var programsOpen by remember { mutableStateOf(false) }
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxMgrPortsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.workRefresh),
                            actions::retry,
                            icon = KitIcons.retry,
                            enabled = !state.loading,
                            disabledReason = stringResource(Res.string.setupNativeRefreshing),
                        )
                    ),
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.ownerStopping || state.stoppingPid != null,
        loadingLabel = stringResource(Res.string.linuxMgrStopping),
    ) {
        if (!state.loading && state.ports.isEmpty() && state.problem != null) {
            KitPageState(
                kind = KitStateKind.Error,
                title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                body = state.problem.words(),
                icon = KitIcons.warning,
                primary = KitAction(stringResource(Res.string.commonRetry), actions::retry),
            )
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (state.loading && state.ports.isEmpty()) KitSkeletonRows()
                state.problem?.let { problem ->
                    KitErrorNotice(
                        title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                        message = problem.words(),
                        retry =
                            KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                    )
                }
                if (state.previewFailed) {
                    KitNotice(
                        message = stringResource(Res.string.linuxMgrProblemFailed),
                        tone = StatusTone.Failure,
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismiss,
                            ),
                    )
                }
                if (!state.loading && state.problem == null && state.ports.isEmpty()) {
                    KitInlineState(
                        title = stringResource(Res.string.linuxMgrPortsEmpty),
                        body = stringResource(Res.string.linuxMgrPortsEmptyBody),
                        icon = KitIcons.network,
                        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                    )
                }
                if (state.programsFailed) {
                    KitErrorNotice(
                        title = stringResource(Res.string.linuxDevScanFailed),
                        message = stringResource(Res.string.linuxMgrProblemFailed),
                        retry =
                            KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                    )
                }
                state.ownerStopResult?.let { result ->
                    KitNotice(
                        message =
                            stringResource(
                                Res.string.linuxPortOwnerResult,
                                result.ended,
                                result.forced,
                                result.remaining,
                                result.changed,
                            ),
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismiss,
                            ),
                    )
                }
                if (state.ports.isNotEmpty()) {
                    KitText(
                        stringResource(Res.string.linuxMgrPortsNote),
                        Modifier.padding(
                            horizontal = LocalKitTokens.current.gutter.dp,
                            vertical = Space.S1.dp,
                        ),
                        role = TypeRole.Caption,
                        tone = TextTone.Secondary,
                    )
                    KitRowGroup(leadingTiles = true) {
                        state.ports.forEach { PortRow(it, state.ownerStopping, actions) }
                    }
                }
                if (state.programs.isNotEmpty()) {
                    KitExpandRow(
                        title = stringResource(Res.string.linuxDevPrograms),
                        supporting = stringResource(Res.string.linuxDevProgramsNote),
                        expanded = programsOpen,
                        onExpandedChange = { programsOpen = it },
                        leading = KitIcons.code,
                    ) {
                        state.programs.forEach { server ->
                            val canRead = server.logPath != null
                            val ports =
                                stringResource(
                                    Res.string.linuxDevPorts,
                                    server.ports.joinToString(", "),
                                )
                            KitRow(
                                title =
                                    io.github.eslamasabry.oc.engine.runtime.procs.CommandNames
                                        .serverName(server.command)
                                        .takeIf { it.isNotBlank() }
                                        ?: stringResource(Res.string.linuxDevProgram, server.pid),
                                supporting =
                                    ports +
                                        " · " +
                                        stringResource(
                                            if (canRead) Res.string.linuxDevReadLog
                                            else Res.string.linuxDevNoLog
                                        ),
                                supportingMaxLines = 3,
                                onClick =
                                    if (canRead) ({ actions.readProgramLog(server.pid) }) else null,
                                trailing =
                                    if (
                                        server.pid > 1 &&
                                            server.command.isNotBlank() &&
                                            state.stoppingPid == null
                                    ) {
                                        KitRowTrailing.Action(
                                            KitLineAction(
                                                stringResource(Res.string.linuxDevStop),
                                                { actions.askStopProgram(server.pid) },
                                            )
                                        )
                                    } else {
                                        KitRowTrailing.None
                                    },
                            )
                        }
                    }
                }
            }
        }
        val selectedPort = state.ports.firstOrNull { it.port == state.selectedPort }
        KitSheet(
            visible = selectedPort != null,
            title =
                selectedPort?.let { stringResource(Res.string.linuxMgrPortRow, it.port) }
                    ?: stringResource(Res.string.linuxPortActions),
            onDismissRequest = actions::dismiss,
            primary =
                selectedPort
                    ?.takeIf { it.canPreview }
                    ?.let { port ->
                        KitAction(
                            stringResource(Res.string.linuxMgrPortPreview),
                            { actions.preview(port.port) },
                        )
                    },
            secondary =
                selectedPort?.let { port ->
                    KitAction(
                        stringResource(Res.string.linuxMgrPortCopy),
                        { actions.copyAddress(port.port) },
                    )
                },
            tertiary =
                selectedPort
                    ?.takeIf {
                        it.owner != null &&
                            it.kind !=
                                io.github.eslamasabry.oc.feature.linux.manager.LinuxProgramKind
                                    .OpenCodeServer
                    }
                    ?.let { port ->
                        listOf(
                            KitAction(
                                stringResource(Res.string.linuxPortOwnerStop, port.owner!!.name),
                                { actions.askStopOwner(port.port) },
                                destructive = true,
                                working = state.ownerStopping,
                                enabled =
                                    KitEnabled.of(
                                        !state.ownerStopping,
                                        stringResource(Res.string.linuxMgrStopping),
                                    ),
                            )
                        )
                    }
                    .orEmpty(),
        ) {}
        KitConfirm(
            spec =
                state.ownerStopAsk?.let { ask ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.linuxMgrStopTitle, ask.name),
                        confirmLabel = stringResource(Res.string.linuxMgrStopLabel, ask.name),
                        kind = KitConfirmKind.Stop,
                        body = stringResource(Res.string.linuxMgrStopBody),
                        consequences =
                            if (ask.identities.size > 1)
                                listOf(
                                    pluralStringResource(
                                        Res.plurals.linuxMgrStopHelpers,
                                        ask.identities.size - 1,
                                        ask.identities.size - 1,
                                    )
                                )
                            else emptyList(),
                    )
                },
            onConfirm = actions::confirmStopOwner,
            onCancel = actions::dismiss,
        )
        KitAlert(
            visible = state.logPid != null,
            title = stringResource(Res.string.linuxDevLog),
            body =
                when {
                    state.logLoading -> stringResource(Res.string.linuxDevLogLoading)
                    state.logFailed -> stringResource(Res.string.linuxDevLogFailed)
                    state.logText.isNullOrEmpty() -> stringResource(Res.string.linuxDevLogEmpty)
                    else -> boundedLog(state.logText)
                },
            onDismissRequest = actions::dismiss,
        )
        KitConfirm(
            spec =
                state.stopAsk?.let {
                    KitConfirmSpec(
                        title = stringResource(Res.string.linuxDevStopTitle),
                        confirmLabel = stringResource(Res.string.linuxDevStop),
                        body = stringResource(Res.string.linuxDevStopBody, it.pid),
                        kind = KitConfirmKind.Destructive,
                    )
                },
            onConfirm = actions::confirmStopProgram,
            onCancel = actions::dismiss,
        )
    }
}

@Composable
private fun PortRow(port: LinuxPort, stopping: Boolean, actions: LinuxPortsActions) {
    var menuOpen by remember(port.port) { mutableStateOf(false) }
    val owner =
        when {
            port.kind == LinuxProgramKind.OpenCodeServer ->
                stringResource(
                    Res.string.linuxMgrPortUsedBy,
                    stringResource(Res.string.linuxMgrKindServer),
                )
            port.owner != null ->
                stringResource(Res.string.linuxMgrPortUsedBy, "⁨${port.owner.name}⁩")
            else -> stringResource(Res.string.linuxMgrPortUnknown)
        }
    val reach =
        when (port.allInterfaces) {
            true -> stringResource(Res.string.linuxMgrPortAll)
            false -> stringResource(Res.string.linuxMgrPortLocal)
            null -> null
        }
    val supporting =
        listOfNotNull(
                owner,
                if (!port.canPreview) stringResource(Res.string.linuxMgrPortNotPage) else reach,
            )
            .joinToString(" · ")
    val enabled = KitEnabled.of(!stopping, stringResource(Res.string.linuxMgrStopping))
    val items = buildList {
        if (port.canPreview)
            add(
                KitMenuItem(
                    stringResource(Res.string.linuxMgrPortPreviewNamed, port.port),
                    { actions.preview(port.port) },
                )
            )
        add(
            KitMenuItem(
                stringResource(Res.string.linuxMgrPortCopy),
                { actions.copyAddress(port.port) },
            )
        )
        if (port.owner != null && port.kind != LinuxProgramKind.OpenCodeServer)
            add(
                KitMenuItem(
                    stringResource(Res.string.linuxMgrStopLabel, port.owner.name),
                    { actions.askStopOwner(port.port) },
                    destructive = true,
                    enabled = enabled,
                )
            )
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            KitRow(
                title = stringResource(Res.string.linuxMgrPortRow, port.port),
                leading =
                    KitRowLeading.Glyph(
                        when (port.kind) {
                            LinuxProgramKind.OpenCodeServer,
                            LinuxProgramKind.Paseo -> KitIcons.server
                            LinuxProgramKind.Agent -> KitIcons.agent
                            LinuxProgramKind.DevServer -> KitIcons.code
                            else -> KitIcons.network
                        }
                    ),
                supporting = supporting,
                supportingMaxLines = 3, // F: lib/ui/screens/linux_manager/linux_ports_page.dart:253
                onClick = if (port.canPreview) ({ actions.preview(port.port) }) else null,
                trailing =
                    if (port.canPreview)
                        KitRowTrailing.Value(
                            stringResource(Res.string.linuxMgrPortPreview),
                            chevron = true,
                        )
                    else KitRowTrailing.None,
            )
        }
        KitMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            items = items,
            title = stringResource(Res.string.linuxMgrPortRow, port.port),
            anchor = {
                KitIconButton(
                    KitIcons.more,
                    stringResource(Res.string.linuxPortActions),
                    { menuOpen = true },
                )
            },
        )
    }
}

private const val LOG_LINES = 200

/**
 * The log as the kit's bounded line buffer shows it (B-KIT-021): the newest lines, with the dropped
 * count said in words.
 */
@Composable
private fun boundedLog(text: String): String {
    val buffer = remember(text) { KitLogBuffer(LOG_LINES).also { it.replaceText(text) } }
    val kept = buffer.lines.joinToString("\n") { it.text }
    if (buffer.dropped == 0) return kept
    return pluralStringResource(Res.plurals.kitLogDropped, buffer.dropped, buffer.dropped) +
        "\n" +
        kept
}
