package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPort
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
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
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/**
 * LinuxRoute.Ports (Flutter LinuxPortsPage): one row per port this phone could open, who uses it
 * and how far it reaches; a dev server's row opens its preview in the browser (the OpenCode server
 * and Paseo are "Not a web page").
 */
@Composable
public fun LinuxPortsScreen(state: LinuxPortsUi, actions: LinuxPortsActions) {
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
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.problem?.let { problem ->
                KitErrorNotice(
                    title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                    message = problem.words(),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
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
                KitNotice(
                    title = stringResource(Res.string.linuxMgrPortsEmpty),
                    message = stringResource(Res.string.linuxMgrPortsEmptyBody),
                    liveRegion = false,
                )
            }
            if (state.programsFailed) {
                KitErrorNotice(
                    title = stringResource(Res.string.linuxDevScanFailed),
                    message = stringResource(Res.string.linuxMgrProblemFailed),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            if (state.programs.isNotEmpty()) {
                KitRowGroup(
                    label = stringResource(Res.string.linuxDevPrograms),
                    note = stringResource(Res.string.linuxDevProgramsNote),
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
                                server.command.takeIf { it.isNotBlank() }?.take(100)
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
            if (state.ports.isNotEmpty()) {
                KitRowGroup(note = stringResource(Res.string.linuxMgrPortsNote)) {
                    state.ports.forEach { PortRow(it, actions) }
                }
            }
        }
    }
    KitAlert(
        visible = state.logPid != null,
        title = stringResource(Res.string.linuxDevLog),
        body =
            when {
                state.logLoading -> stringResource(Res.string.linuxDevLogLoading)
                state.logFailed -> stringResource(Res.string.linuxDevLogFailed)
                state.logText.isNullOrEmpty() -> stringResource(Res.string.linuxDevLogEmpty)
                else -> state.logText
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

@Composable
private fun PortRow(port: LinuxPort, actions: LinuxPortsActions) {
    val owner =
        port.owner
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { stringResource(Res.string.linuxMgrPortUsedBy, "⁦$it⁩") }
            ?: port.kind?.let { it.words() }
            ?: stringResource(Res.string.linuxMgrPortUnknown)
    val reach =
        when (port.allInterfaces) {
            true -> stringResource(Res.string.linuxMgrPortAll)
            false -> stringResource(Res.string.linuxMgrPortLocal)
            null -> null
        }
    val notPage = if (port.canPreview) null else stringResource(Res.string.linuxMgrPortNotPage)
    KitRow(
        title = stringResource(Res.string.linuxMgrPortRow, port.port),
        supporting = listOfNotNull(owner, reach, notPage).joinToString(" · "),
        supportingMaxLines = 3,
        trailing =
            if (port.canPreview) {
                KitRowTrailing.Action(
                    KitLineAction(
                        stringResource(Res.string.linuxMgrPortPreview),
                        { actions.preview(port.port) },
                    )
                )
            } else {
                KitRowTrailing.None
            },
    )
}
