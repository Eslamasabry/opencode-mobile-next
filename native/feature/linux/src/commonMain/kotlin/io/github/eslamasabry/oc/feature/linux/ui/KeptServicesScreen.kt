package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptAct
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptField
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptPhase
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptService
import io.github.eslamasabry.oc.feature.linux.presentation.KeptServicesActions
import io.github.eslamasabry.oc.feature.linux.presentation.KeptServicesUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTextBody
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Device-wide supervised services. Project commands keep their distinct Services(profile, folder)
 * path.
 */
@Composable
public fun KeptServicesScreen(state: KeptServicesUi, actions: KeptServicesActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.keptSvcTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.failure?.let {
                KitErrorNotice(
                    title = stringResource(Res.string.keptSvcFailed),
                    message = stringResource(Res.string.keptSvcFailedBody),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::refresh),
                )
            }
            KitButton(stringResource(Res.string.keptSvcAdd), actions::add, working = state.working)
            KitButton(
                stringResource(Res.string.commonRetry),
                actions::refresh,
                role = KitButtonRole.Tertiary,
                working = state.working,
            )
            KitRowGroup(note = stringResource(Res.string.keptSvcKeepsRunning)) {
                if (state.services.isEmpty() && !state.loading)
                    KitText(stringResource(Res.string.keptSvcEmpty))
                state.services.forEach { service ->
                    KitRow(
                        title = service.definition.title,
                        supporting = serviceWords(service, state.nowMs),
                        supportingMaxLines = 3,
                        trailing =
                            KitRowTrailing.Action(
                                KitLineAction(
                                    stringResource(
                                        if (service.showsStop) Res.string.keptSvcStopNamed
                                        else Res.string.keptSvcStartNamed,
                                        service.definition.title,
                                    ),
                                    {
                                        actions.act(
                                            service.definition.name,
                                            if (service.showsStop) KeptAct.Stop else KeptAct.Start,
                                        )
                                    },
                                    enabled = !state.working,
                                    disabledReason = stringResource(Res.string.keptSvcWorking),
                                )
                            ),
                        onClick = { actions.select(service.definition.name) },
                    )
                }
            }
            KitNotice(message = stringResource(Res.string.keptSvcScreenOff), liveRegion = false)
        }
    }
    val selected = state.services.firstOrNull { it.definition.name == state.selected }
    KitSheet(
        visible = selected != null,
        title = selected?.definition?.title ?: stringResource(Res.string.keptSvcTitle),
        onDismissRequest = actions::back,
        primary =
            selected?.let { service ->
                KitAction(
                    stringResource(
                        if (service.showsStop) Res.string.keptSvcStopNamed
                        else Res.string.keptSvcStartNamed,
                        service.definition.title,
                    ),
                    {
                        actions.act(
                            service.definition.name,
                            if (service.showsStop) KeptAct.Stop else KeptAct.Start,
                        )
                    },
                    working = state.working,
                )
            },
        secondary =
            selected
                ?.takeIf {
                    it.wanted &&
                        it.phase in
                            setOf(
                                KeptPhase.Running,
                                KeptPhase.Starting,
                                KeptPhase.Backoff,
                                KeptPhase.PortBusy,
                            )
                }
                ?.let { service ->
                    KitAction(
                        stringResource(Res.string.keptSvcRestartNamed, service.definition.title),
                        { actions.act(service.definition.name, KeptAct.Restart) },
                        working = state.working,
                    )
                },
        tertiary =
            buildList {
                selected?.let { service ->
                    if (service.phase == KeptPhase.Running)
                        service.definition.port?.let { port ->
                            add(
                                KitAction(
                                    stringResource(
                                        Res.string.keptSvcOpenNamed,
                                        service.definition.title,
                                    ),
                                    { actions.preview(port) },
                                )
                            )
                        }
                    add(KitAction(stringResource(Res.string.keptSvcCopyLog), actions::copyLog))
                    add(
                        KitAction(
                            stringResource(Res.string.keptSvcRemoveNamed, service.definition.title),
                            { actions.act(service.definition.name, KeptAct.Remove) },
                            destructive = true,
                            working = state.working,
                        )
                    )
                }
            },
    ) {
        selected?.let { service ->
            KitText(serviceWords(service, state.nowMs))
            if (
                service.restarts > 0 &&
                    service.lastExitAt != null &&
                    service.phase == KeptPhase.Running
            )
                KitText(stringResource(Res.string.keptSvcRestarts, service.restarts))
            KitText(stringResource(Res.string.keptSvcLogTitle))
            KitSearchField(
                label = stringResource(Res.string.keptSvcSearchLog),
                onSearch = actions::searchLog,
            )
            val lines =
                state.log
                    .lineSequence()
                    .filter {
                        state.logQuery.isBlank() || it.contains(state.logQuery, ignoreCase = true)
                    }
                    .toList()
                    .map { logWords(it) }
            if (state.log.isBlank()) KitText(stringResource(Res.string.keptSvcLogEmpty))
            else KitViewerTextBody(lines.joinToString("\n"), Modifier.fillMaxWidth().height(360.dp))
        }
    }
    AddKeptService(state, actions)
    val removed = state.services.firstOrNull { it.definition.name == state.ask?.name }
    KitConfirm(
        spec =
            removed?.let { service ->
                KitConfirmSpec(
                    stringResource(Res.string.keptSvcRemoveQuestion, service.definition.title),
                    stringResource(Res.string.keptSvcRemoveNamed, service.definition.title),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.keptSvcRemoveBody),
                    consequences =
                        listOf(
                            stringResource(Res.string.keptSvcRemoveGoes),
                            stringResource(Res.string.keptSvcRemoveStays),
                        ),
                )
            },
        onConfirm = actions::confirm,
        onCancel = actions::dismiss,
    )
}

@Composable
private fun AddKeptService(state: KeptServicesUi, actions: KeptServicesActions) {
    if (!state.adding) return
    val name = rememberTextFieldState()
    val command = rememberTextFieldState()
    val directory = rememberTextFieldState("/root")
    val port = rememberTextFieldState()
    var restart by remember { mutableStateOf(true) }
    val enabled =
        if (state.working) KitEnabled.Disabled(stringResource(Res.string.keptSvcWorking))
        else KitEnabled.Enabled
    KitSheet(
        visible = true,
        title = stringResource(Res.string.keptSvcAdd),
        onDismissRequest = actions::back,
        primary =
            KitAction(
                stringResource(Res.string.keptSvcSaveStart),
                {
                    actions.save(
                        name.text.toString(),
                        command.text.toString(),
                        directory.text.toString(),
                        port.text.toString(),
                        restart,
                    )
                },
                enabled = enabled,
                working = state.working,
            ),
    ) {
        KitField(
            name,
            stringResource(Res.string.keptSvcName),
            kind = KitFieldKind.Mono,
            maxLength = 24,
            helper = stringResource(Res.string.keptSvcNameHint),
            enabled = enabled,
            error =
                if (state.invalid == KeptField.Name) stringResource(Res.string.keptSvcBadName)
                else null,
        )
        KitField(
            command,
            stringResource(Res.string.keptSvcCommand),
            kind = KitFieldKind.Mono,
            maxLength = 4096,
            enabled = enabled,
            error =
                if (state.invalid == KeptField.Command) stringResource(Res.string.keptSvcBadCommand)
                else null,
        )
        KitField(
            directory,
            stringResource(Res.string.keptSvcDirectory),
            kind = KitFieldKind.Path,
            enabled = enabled,
            error =
                if (state.invalid == KeptField.Directory)
                    stringResource(Res.string.keptSvcBadDirectory)
                else null,
        )
        KitField(
            port,
            stringResource(Res.string.keptSvcPort),
            kind = KitFieldKind.Number,
            maxLength = 5,
            enabled = enabled,
            helper = stringResource(Res.string.keptSvcPortHint),
            error =
                if (state.invalid == KeptField.Port) stringResource(Res.string.keptSvcBadPort)
                else null,
        )
        KitSwitchRow(
            stringResource(Res.string.keptSvcRestart),
            restart,
            supporting = stringResource(Res.string.keptSvcRestartBody),
            enabled = enabled,
            onCheckedChange = { restart = it },
        )
        if (state.invalid == KeptField.Limit) KitText(stringResource(Res.string.keptSvcLimit))
        if (state.failure != null) KitText(stringResource(Res.string.keptSvcFailedBody))
    }
}

@Composable
private fun serviceWords(service: KeptService, nowMs: Long): String {
    val status =
        when (service.phase) {
            KeptPhase.Off ->
                stringResource(
                    if (service.paused) Res.string.keptSvcPaused else Res.string.keptSvcOff
                )
            KeptPhase.Starting -> stringResource(Res.string.keptSvcStarting)
            KeptPhase.Running ->
                service.since?.let {
                    stringResource(
                        Res.string.keptSvcRunningFor,
                        ((nowMs - it.toEpochMilliseconds()).coerceAtLeast(0) / 60000).toInt(),
                    )
                } ?: stringResource(Res.string.keptSvcRunning)
            KeptPhase.Stopping -> stringResource(Res.string.keptSvcStopping)
            KeptPhase.Backoff ->
                stringResource(
                    Res.string.keptSvcRetrying,
                    service.lastExit ?: 1,
                    ((service.nextTry?.toEpochMilliseconds()?.minus(nowMs) ?: 0).coerceAtLeast(0) /
                            1000)
                        .coerceAtMost(3600)
                        .toInt(),
                )
            KeptPhase.PortBusy ->
                stringResource(Res.string.keptSvcPortBusy, service.definition.port ?: 0)
            KeptPhase.Failed -> stringResource(Res.string.keptSvcGaveUp, service.restarts)
            KeptPhase.Exited -> stringResource(Res.string.keptSvcExited, service.lastExit ?: 0)
        }
    val port = service.definition.port
    return if (port != null) "$status · ${stringResource(Res.string.keptSvcPortNumber, port)}"
    else status
}

@Composable
private fun logWords(line: String): String {
    if (!line.startsWith("::svc ")) return line
    val words = line.removePrefix("::svc ").trim().split(' ')
    val number = words.getOrNull(1)?.toIntOrNull() ?: 0
    return when (words.firstOrNull()) {
        "start" -> stringResource(Res.string.keptSvcLogStarted)
        "exit" -> stringResource(Res.string.keptSvcLogExit, number)
        "backoff" -> stringResource(Res.string.keptSvcLogRetry, number)
        "gave-up" -> stringResource(Res.string.keptSvcLogGaveUp)
        "port-busy" -> stringResource(Res.string.keptSvcPortBusy, number)
        "stop" -> stringResource(Res.string.keptSvcOff)
        else -> line
    }
}
