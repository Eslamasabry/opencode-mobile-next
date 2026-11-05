package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
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
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.workRefresh),
                            actions::refresh,
                            icon = KitIcons.retry,
                            enabled = !state.loading && !state.working,
                            disabledReason = stringResource(Res.string.keptSvcWorking),
                        )
                    ),
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.working,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.loading && state.services.isEmpty()) KitSkeletonRows()
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
            if (!state.loading)
                KitRowGroup(leadingTiles = true) {
                    KitRow(
                        stringResource(Res.string.keptSvcAdd),
                        leading = KitRowLeading.Glyph(KitIcons.addCircle),
                        supporting = stringResource(Res.string.linuxSvcAddDetail),
                        supportingMaxLines = 2,
                        trailing = KitRowTrailing.Chevron,
                        enabled = !state.working,
                        disabledReason = stringResource(Res.string.keptSvcWorking),
                        onClick = actions::add,
                    )
                }
            KitRowGroup(leadingTiles = true) {
                if (state.services.isEmpty() && !state.loading)
                    KitText(stringResource(Res.string.linuxSvcEmpty))
                state.services
                    .sortedWith(
                        compareBy<KeptService> { it.urgency }
                            .thenBy { it.definition.title.lowercase() }
                    )
                    .forEach { service ->
                        KitRow(
                            title = service.definition.title,
                            leading = KitRowLeading.Glyph(serviceGlyph(service)),
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
                                                if (service.showsStop) KeptAct.Stop
                                                else KeptAct.Start,
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
            if (state.services.isNotEmpty()) {
                KitText(
                    stringResource(Res.string.linuxSvcKeepsRunning),
                    Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                    role = TypeRole.Caption,
                )
                KitText(
                    stringResource(Res.string.linuxSvcScreenOff),
                    Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                    role = TypeRole.Caption,
                )
            }
        }
        val selected = state.services.firstOrNull { it.definition.name == state.selected }
        KitSheet(
            visible = selected != null,
            title = selected?.definition?.title ?: stringResource(Res.string.keptSvcTitle),
            height = KitSheetHeight.Full,
            onDismissRequest = actions::back,
            dismissible = !state.working,
            loading = state.working,
        ) {
            ServiceFailure(state)
            selected?.let { service ->
                val name = service.definition.name
                val port = service.definition.port
                val disabled = stringResource(Res.string.keptSvcWorking)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
                ) {
                    KitRowGroup(leadingTiles = true) {
                        KitRow(
                            serviceStatus(service, state.nowMs),
                            leading = KitRowLeading.Glyph(serviceGlyph(service)),
                            titleMaxLines = 3,
                            supporting =
                                if (
                                    service.restarts > 0 &&
                                        service.lastExitAt != null &&
                                        service.phase == KeptPhase.Running
                                )
                                    stringResource(Res.string.keptSvcRestarts, service.restarts)
                                else null,
                            supportingMaxLines = 3,
                        )
                    }
                    KitRowGroup(leadingTiles = true) {
                        if (service.phase == KeptPhase.Running && port != null)
                            KitRow(
                                stringResource(
                                    Res.string.keptSvcOpenNamed,
                                    service.definition.title,
                                ),
                                leading = KitRowLeading.Glyph(KitIcons.externalLink),
                                trailing = KitRowTrailing.Chevron,
                                supporting =
                                    stringResource(Res.string.linuxSvcOpenDetail, port.toString()),
                                supportingMaxLines = 2,
                                enabled = !state.working,
                                disabledReason = disabled,
                                onClick = { actions.preview(port) },
                            )
                        KitRow(
                            stringResource(
                                if (service.showsStop) Res.string.keptSvcStopNamed
                                else Res.string.keptSvcStartNamed,
                                service.definition.title,
                            ),
                            leading =
                                KitRowLeading.Glyph(
                                    if (service.showsStop) KitIcons.stopCircle else KitIcons.play
                                ),
                            supporting =
                                stringResource(
                                    if (service.showsStop) Res.string.linuxSvcStopDetail
                                    else Res.string.linuxSvcStartDetail
                                ),
                            supportingMaxLines = 2,
                            enabled = !state.working,
                            disabledReason = disabled,
                            onClick = {
                                actions.act(
                                    name,
                                    if (service.showsStop) KeptAct.Stop else KeptAct.Start,
                                )
                            },
                        )
                        if (
                            service.phase in
                                setOf(KeptPhase.Running, KeptPhase.Starting, KeptPhase.Backoff)
                        )
                            KitRow(
                                stringResource(
                                    Res.string.keptSvcRestartNamed,
                                    service.definition.title,
                                ),
                                leading = KitRowLeading.Glyph(KitIcons.restart),
                                enabled = !state.working,
                                disabledReason = disabled,
                                onClick = { actions.act(name, KeptAct.Restart) },
                            )
                        KitRow(
                            stringResource(Res.string.keptSvcRemoveNamed, service.definition.title),
                            leading = KitRowLeading.Glyph(KitIcons.removeCircle),
                            enabled = !state.working,
                            disabledReason = disabled,
                            onClick = { actions.act(name, KeptAct.Remove) },
                        )
                    }
                    KitText(stringResource(Res.string.linuxSvcLogTitle), role = TypeRole.Headline)
                    // AndroidKeptServices.log redacts before state.log is published. Every log
                    // operation uses this same text.
                    val lines = state.log.lines().map { logWords(it) }
                    KitLogPanel(
                        redactedText = lines.joinToString("\n"),
                        live =
                            service.phase in
                                setOf(KeptPhase.Running, KeptPhase.Starting, KeptPhase.Backoff),
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                    )
                }
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
                        consequenceItems =
                            listOf(
                                KitConsequence(
                                    stringResource(Res.string.keptSvcRemoveGoes),
                                    KitConsequenceMark.Lost,
                                ),
                                KitConsequence(
                                    stringResource(Res.string.keptSvcRemoveStays),
                                    KitConsequenceMark.Kept,
                                ),
                            ),
                    )
                },
            onConfirm = actions::confirm,
            onCancel = actions::dismiss,
        )
    }
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
        height = KitSheetHeight.Full,
        onDismissRequest = actions::back,
        dismissible = !state.working,
        loading = state.working,
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
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
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
                helper = stringResource(Res.string.linuxSvcFieldCommandHelper),
                kind = KitFieldKind.Mono,
                maxLength = 4096,
                enabled = enabled,
                error =
                    if (state.invalid == KeptField.Command)
                        stringResource(Res.string.keptSvcBadCommand)
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
            ServiceFailure(state)
        }
    }
}

@Composable
private fun serviceStatus(service: KeptService, nowMs: Long): String =
    when (service.phase) {
        KeptPhase.Off ->
            stringResource(if (service.paused) Res.string.linuxSvcPaused else Res.string.keptSvcOff)
        KeptPhase.Starting -> stringResource(Res.string.linuxSvcStarting)
        KeptPhase.Running ->
            service.since?.let {
                stringResource(
                    Res.string.linuxSvcRunningFor,
                    kitDurationWords((nowMs - it.toEpochMilliseconds()).milliseconds),
                )
            } ?: stringResource(Res.string.linuxSvcRunning)
        KeptPhase.Stopping -> stringResource(Res.string.linuxSvcStopping)
        KeptPhase.Backoff ->
            stringResource(
                Res.string.linuxSvcRetrying,
                service.lastExit ?: 1,
                ceil((service.nextTry?.toEpochMilliseconds()?.minus(nowMs) ?: 0) / 1000.0)
                    .toInt()
                    .coerceIn(
                        0,
                        3600,
                    ), // F: lib/ui/screens/linux_manager/linux_services_common.dart:257-261
            )
        KeptPhase.PortBusy ->
            stringResource(
                Res.string.linuxSvcPortBusy,
                service.definition.port?.toString().orEmpty(),
            )
        KeptPhase.Failed -> stringResource(Res.string.keptSvcGaveUp, service.restarts)
        KeptPhase.Exited -> stringResource(Res.string.linuxSvcExited, service.lastExit ?: 0)
    }

@Composable
private fun serviceWords(service: KeptService, nowMs: Long): String {
    val status = serviceStatus(service, nowMs)
    val port = service.definition.port
    val showsPort =
        port != null &&
            !service.paused &&
            service.phase in setOf(KeptPhase.Running, KeptPhase.Off, KeptPhase.Exited)
    return if (showsPort) "$status · ${stringResource(Res.string.linuxSvcPort, port.toString())}"
    else status
}

@Composable
private fun ServiceFailure(state: KeptServicesUi) {
    state.failure?.let {
        KitErrorNotice(
            title = stringResource(Res.string.keptSvcFailed),
            message = stringResource(Res.string.keptSvcFailedBody),
            failure = it,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
        )
    }
}

@Composable
private fun logWords(line: String): String {
    if (!line.startsWith("::svc ")) return line
    val words = line.removePrefix("::svc ").trim().split(Regex("\\s+"))
    val number = words.getOrNull(1)?.toIntOrNull() ?: 0
    return when (words.firstOrNull()) {
        "start" -> stringResource(Res.string.linuxSvcLogStarted)
        "exit" ->
            stringResource(
                if (number == 0) Res.string.linuxSvcLogEnded else Res.string.linuxSvcLogCrashed,
                number,
            )
        "backoff" -> stringResource(Res.string.linuxSvcLogRetry, number)
        "gave-up" -> stringResource(Res.string.linuxSvcLogGaveUp)
        "port-busy" -> stringResource(Res.string.linuxSvcLogPortBusy, number.toString())
        "stop" -> stringResource(Res.string.keptSvcOff)
        else -> line
    }
}

private fun serviceGlyph(service: KeptService) =
    when {
        service.paused -> KitIcons.pause
        service.phase == KeptPhase.Failed || service.phase == KeptPhase.PortBusy -> KitIcons.error
        service.phase == KeptPhase.Backoff -> KitIcons.restart
        service.phase == KeptPhase.Running || service.phase == KeptPhase.Starting ->
            KitIcons.checkCircle
        else -> KitIcons.terminal
    }
