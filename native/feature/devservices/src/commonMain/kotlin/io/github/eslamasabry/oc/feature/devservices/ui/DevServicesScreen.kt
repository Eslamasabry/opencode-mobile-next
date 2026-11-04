package io.github.eslamasabry.oc.feature.devservices.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStatus
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesActions
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesAsk
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesProblem
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesReceipt
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesUi
import io.github.eslamasabry.oc.feature.devservices.presentation.EditorUi
import io.github.eslamasabry.oc.feature.devservices.presentation.FoundRowUi
import io.github.eslamasabry.oc.feature.devservices.presentation.NameError
import io.github.eslamasabry.oc.feature.devservices.presentation.ServiceRowUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemFailed
import io.github.eslamasabry.oc.ui.words.res.servicesAdd
import io.github.eslamasabry.oc.ui.words.res.servicesAlsoPhoneFailed
import io.github.eslamasabry.oc.ui.words.res.servicesAlsoPhoneHint
import io.github.eslamasabry.oc.ui.words.res.servicesAlsoRunning
import io.github.eslamasabry.oc.ui.words.res.servicesAlsoServerHint
import io.github.eslamasabry.oc.ui.words.res.servicesCommand
import io.github.eslamasabry.oc.ui.words.res.servicesCommandHint
import io.github.eslamasabry.oc.ui.words.res.servicesCommandRequired
import io.github.eslamasabry.oc.ui.words.res.servicesDuplicateName
import io.github.eslamasabry.oc.ui.words.res.servicesEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.servicesExit
import io.github.eslamasabry.oc.ui.words.res.servicesForget
import io.github.eslamasabry.oc.ui.words.res.servicesForgetHint
import io.github.eslamasabry.oc.ui.words.res.servicesForgetTitle
import io.github.eslamasabry.oc.ui.words.res.servicesIntro
import io.github.eslamasabry.oc.ui.words.res.servicesLogEmpty
import io.github.eslamasabry.oc.ui.words.res.servicesLogFailed
import io.github.eslamasabry.oc.ui.words.res.servicesLogs
import io.github.eslamasabry.oc.ui.words.res.servicesName
import io.github.eslamasabry.oc.ui.words.res.servicesNameRequired
import io.github.eslamasabry.oc.ui.words.res.servicesNoPortYet
import io.github.eslamasabry.oc.ui.words.res.servicesNotStarted
import io.github.eslamasabry.oc.ui.words.res.servicesOffline
import io.github.eslamasabry.oc.ui.words.res.servicesPreviewPort
import io.github.eslamasabry.oc.ui.words.res.servicesProjectFolder
import io.github.eslamasabry.oc.ui.words.res.servicesRemove
import io.github.eslamasabry.oc.ui.words.res.servicesRemoveRunningHint
import io.github.eslamasabry.oc.ui.words.res.servicesRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.servicesRemoved
import io.github.eslamasabry.oc.ui.words.res.servicesRestart
import io.github.eslamasabry.oc.ui.words.res.servicesRestartConfirm
import io.github.eslamasabry.oc.ui.words.res.servicesRestartHint
import io.github.eslamasabry.oc.ui.words.res.servicesRestartTitle
import io.github.eslamasabry.oc.ui.words.res.servicesRunning
import io.github.eslamasabry.oc.ui.words.res.servicesSave
import io.github.eslamasabry.oc.ui.words.res.servicesSaveCommand
import io.github.eslamasabry.oc.ui.words.res.servicesScopeChanged
import io.github.eslamasabry.oc.ui.words.res.servicesStart
import io.github.eslamasabry.oc.ui.words.res.servicesStarted
import io.github.eslamasabry.oc.ui.words.res.servicesStatusHint
import io.github.eslamasabry.oc.ui.words.res.servicesStop
import io.github.eslamasabry.oc.ui.words.res.servicesStopConfirm
import io.github.eslamasabry.oc.ui.words.res.servicesStopFoundHint
import io.github.eslamasabry.oc.ui.words.res.servicesStopFoundTitle
import io.github.eslamasabry.oc.ui.words.res.servicesStopHint
import io.github.eslamasabry.oc.ui.words.res.servicesStopTitle
import io.github.eslamasabry.oc.ui.words.res.servicesStopped
import io.github.eslamasabry.oc.ui.words.res.servicesTitle
import io.github.eslamasabry.oc.ui.words.res.servicesUnavailable
import io.github.eslamasabry.oc.ui.words.res.servicesUnknown
import io.github.eslamasabry.oc.ui.words.res.servicesUnknownHint
import io.github.eslamasabry.oc.ui.words.res.servicesUrl
import io.github.eslamasabry.oc.ui.words.res.servicesUrlHint
import io.github.eslamasabry.oc.ui.words.res.servicesUrlInvalid
import io.github.eslamasabry.oc.ui.words.res.servicesVisit
import io.github.eslamasabry.oc.ui.words.res.servicesWorking
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import io.github.eslamasabry.oc.ui.words.res.workOutput
import io.github.eslamasabry.oc.ui.words.res.workMoreOutput
import io.github.eslamasabry.oc.ui.words.res.workTrimmed
import io.github.eslamasabry.oc.ui.words.res.workUnavailable
import io.github.eslamasabry.oc.ui.words.res.workRestarted
import io.github.eslamasabry.oc.ui.words.res.shellOutputReadFailed
import io.github.eslamasabry.oc.ui.words.res.workRefresh
import org.jetbrains.compose.resources.stringResource

/** How many log lines a row shows: the tail is already bounded by the engine (B-LINUX-064). */
private const val LOG_LINES = 60

/**
 * DevServicesRoute.Home (Flutter DevelopmentServicesScreen): the project folder, the saved services with Start or Stop
 * and their other acts, then "Also running" — dev servers found on the phone or among the server's shells.
 */
@Composable
public fun DevServicesScreen(state: DevServicesUi, actions: DevServicesActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.servicesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (!state.available) {
                KitNotice(message = stringResource(Res.string.servicesOffline), tone = StatusTone.Attention)
                return@Column
            }
            KitText(stringResource(Res.string.servicesIntro), role = TypeRole.Secondary, tone = TextTone.Secondary)
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.servicesProjectFolder),
                    supporting = state.directory,
                    supportingMaxLines = 3,
                )
            }
            if (!state.supported) KitNotice(message = stringResource(Res.string.servicesUnavailable), liveRegion = false)
            Notices(state, actions)
            KitButton(
                stringResource(Res.string.servicesAdd),
                onClick = actions::add,
                modifier = Modifier.fillMaxWidth(),
                role = KitButtonRole.Secondary,
                enabled = if (state.readable && !state.busy) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.servicesWorking)),
            )
            if (state.services.isEmpty() && !state.loading) {
                KitNotice(
                    title = stringResource(Res.string.servicesEmptyTitle),
                    message = stringResource(Res.string.servicesCommandHint),
                    liveRegion = false,
                )
            }
            state.services.forEach { ServiceBlock(it, state, actions) }
            Found(state, actions)
        }
    }
    Editor(state.editor, actions)
    Output(state, actions)
    KitConfirm(state.ask?.let { spec(it) }, onConfirm = actions::confirm, onCancel = actions::cancel)
}

@Composable
private fun Output(state: DevServicesUi, actions: DevServicesActions) {
    val output = state.output
    KitSheet(
        visible = output != null,
        onDismissRequest = actions::closeOutput,
        title = stringResource(Res.string.workOutput),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary = KitAction(
            stringResource(if (output?.hasMore == true) Res.string.workMoreOutput else Res.string.workRefresh),
            actions::refreshOutput.takeIf { output?.refreshing == false },
        ),
    ) {
        if (output != null) {
            output.shell?.command?.let { KitText(it, role = TypeRole.Mono) }
            if (!output.available) KitNotice(message = stringResource(if (output.serverRestarted) Res.string.workRestarted else Res.string.workUnavailable))
            if (output.trimmed) KitNotice(message = stringResource(Res.string.workTrimmed), liveRegion = false)
            output.failure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.shellOutputReadFailed),
                    failure = it,
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::refreshOutput),
                )
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                KitText(output.displayText.ifBlank { stringResource(Res.string.servicesLogEmpty) }, role = TypeRole.Mono)
            }
        }
    }
}

@Composable
private fun Notices(state: DevServicesUi, actions: DevServicesActions) {
    state.problem?.let { problem ->
        KitErrorNotice(
            message = problemWords(problem),
            failure = state.failure,
            retry = KitLineAction(stringResource(Res.string.commonRetry), actions::refresh),
        )
    }
    state.receipt?.let { receipt ->
        KitNotice(
            message =
                when (receipt) {
                    is DevServicesReceipt.Started -> stringResource(Res.string.servicesStarted, receipt.name)
                    is DevServicesReceipt.Removed -> stringResource(Res.string.servicesRemoved, receipt.name)
                },
            tone = StatusTone.Ok,
            dismiss = KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss),
        )
    }
}

@Composable
private fun problemWords(problem: DevServicesProblem): String =
    when (problem) {
        DevServicesProblem.ScopeChanged -> stringResource(Res.string.servicesScopeChanged)
        DevServicesProblem.Unavailable -> stringResource(Res.string.servicesUnavailable)
        DevServicesProblem.Offline -> stringResource(Res.string.servicesOffline)
        DevServicesProblem.LogFailed -> stringResource(Res.string.servicesLogFailed)
        DevServicesProblem.NotSaved, DevServicesProblem.Failed -> stringResource(Res.string.linuxMgrProblemFailed)
    }

@Composable
private fun statusWords(row: ServiceRowUi): String =
    when (row.status) {
        DevelopmentServiceStatus.NotStarted -> stringResource(Res.string.servicesNotStarted)
        DevelopmentServiceStatus.Running -> stringResource(Res.string.servicesRunning)
        DevelopmentServiceStatus.Stopped ->
            row.exitCode?.let { stringResource(Res.string.servicesExit, it) } ?: stringResource(Res.string.servicesStopped)
        DevelopmentServiceStatus.Unknown -> stringResource(Res.string.servicesUnknown)
    }

@Composable
private fun ServiceBlock(row: ServiceRowUi, state: DevServicesUi, actions: DevServicesActions) {
    val idle = !state.busy
    val ports = row.ports.joinToString(", ").takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.servicesPreviewPort, it) }
    KitRowGroup(
        note =
            when (row.status) {
                DevelopmentServiceStatus.Running -> stringResource(Res.string.servicesStatusHint)
                DevelopmentServiceStatus.Unknown -> stringResource(Res.string.servicesUnknownHint)
                else -> null
            },
    ) {
        KitRow(
            title = row.name,
            supporting = listOfNotNull(statusWords(row), row.command, ports).joinToString(" · "),
            supportingMaxLines = 3,
            trailing =
                when {
                    row.canStop -> KitRowTrailing.Action(KitLineAction(stringResource(Res.string.servicesStop), { actions.askStop(row.id) }, enabled = idle))
                    state.supported && row.status != DevelopmentServiceStatus.Unknown ->
                        KitRowTrailing.Action(
                            KitLineAction(stringResource(Res.string.servicesStart), { actions.start(row.id) }, enabled = idle && row.canStart),
                        )
                    else -> KitRowTrailing.None
                },
        )
    }
    KitActionBlock(
        tertiary =
            listOfNotNull(
                KitAction(stringResource(Res.string.servicesLogs), { actions.logs(row.id) }.takeIf { idle && state.supported && row.status != DevelopmentServiceStatus.NotStarted }),
                KitAction(stringResource(Res.string.servicesRestart), { actions.askRestart(row.id) }.takeIf { idle }).takeIf { row.canStop },
                KitAction(stringResource(Res.string.servicesVisit), { actions.visit(row.id) }).takeIf { row.url.isNotBlank() },
                KitAction(stringResource(Res.string.servicesForget), { actions.askForget(row.id) }.takeIf { idle })
                    .takeIf { row.status == DevelopmentServiceStatus.Unknown },
                KitAction(stringResource(Res.string.servicesRemove), { actions.askRemove(row.id) }.takeIf { idle }),
            ),
    )
    row.log?.let { LogText(it) }
}

@Composable
private fun LogText(log: String) {
    val text = log.lines().takeLast(LOG_LINES).joinToString("\n").ifBlank { null }
    KitText(
        text ?: stringResource(Res.string.servicesLogEmpty),
        Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp),
        role = if (text == null) TypeRole.Secondary else TypeRole.Mono,
        tone = TextTone.Secondary,
    )
}

@Composable
private fun Found(state: DevServicesUi, actions: DevServicesActions) {
    if (state.found.isEmpty() && !state.foundFailed) return
    KitSectionLabel(stringResource(Res.string.servicesAlsoRunning))
    if (state.foundFailed) KitNotice(message = stringResource(Res.string.servicesAlsoPhoneFailed), tone = StatusTone.Attention)
    KitRowGroup(note = stringResource(if (state.scansPhone) Res.string.servicesAlsoPhoneHint else Res.string.servicesAlsoServerHint)) {
        state.found.forEach { FoundRow(it, actions) }
    }
    state.found.forEach { row ->
        if (row.hasLogs || row.canSave) {
            KitActionBlock(
                tertiary =
                    listOfNotNull(
                        KitAction("${stringResource(Res.string.servicesLogs)} · ${row.name}", { actions.foundLogs(row.id) }).takeIf { row.hasLogs },
                        KitAction("${stringResource(Res.string.servicesSaveCommand)} · ${row.name}", { actions.saveFound(row.id) }).takeIf { row.canSave },
                    ),
            )
        }
        row.log?.let { LogText(it) }
    }
}

@Composable
private fun FoundRow(row: FoundRowUi, actions: DevServicesActions) {
    val ports = row.ports.joinToString(", ").takeIf { it.isNotEmpty() }
    KitRow(
        title = row.name,
        supporting = listOf(ports?.let { stringResource(Res.string.servicesPreviewPort, it) } ?: stringResource(Res.string.servicesNoPortYet), row.command).joinToString(" · "),
        supportingMaxLines = 2,
        trailing =
            if (row.canStop) {
                KitRowTrailing.Action(KitLineAction(stringResource(Res.string.servicesStop), { actions.askStopFound(row.id) }))
            } else {
                KitRowTrailing.None
            },
    )
}

@Composable
private fun Editor(editor: EditorUi?, actions: DevServicesActions) {
    val open = editor != null
    // A fresh form each time it opens, filled from what opened it (a found server's command, or empty).
    val name = remember(open) { TextFieldState(editor?.name.orEmpty()) }
    val command = remember(open) { TextFieldState(editor?.command.orEmpty()) }
    val url = remember(open) { TextFieldState(editor?.url.orEmpty()) }
    val save = {
        actions.edit(name.text.toString(), command.text.toString(), url.text.toString())
        actions.save()
    }
    KitSheet(
        visible = open,
        onDismissRequest = actions::closeEditor,
        title = stringResource(Res.string.servicesAdd),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary = KitAction(stringResource(Res.string.servicesSave), save),
        dirty = name.text.isNotEmpty() || command.text.isNotEmpty(),
    ) {
        KitField(
            name,
            label = stringResource(Res.string.servicesName),
            error =
                when (editor?.nameError) {
                    NameError.Required -> stringResource(Res.string.servicesNameRequired)
                    NameError.Duplicate -> stringResource(Res.string.servicesDuplicateName)
                    null -> null
                },
            maxLength = 80,
        )
        KitField(
            command,
            label = stringResource(Res.string.servicesCommand),
            kind = KitFieldKind.Mono,
            helper = stringResource(Res.string.servicesCommandHint),
            error = if (editor?.commandMissing == true) stringResource(Res.string.servicesCommandRequired) else null,
        )
        KitField(
            url,
            label = stringResource(Res.string.servicesUrl),
            kind = KitFieldKind.Url,
            helper = stringResource(Res.string.servicesUrlHint),
            error = if (editor?.urlInvalid == true) stringResource(Res.string.servicesUrlInvalid) else null,
            onSubmit = save,
        )
    }
}

@Composable
private fun spec(ask: DevServicesAsk): KitConfirmSpec =
    when (ask) {
        is DevServicesAsk.Stop ->
            KitConfirmSpec(
                title = stringResource(Res.string.servicesStopTitle, ask.name),
                confirmLabel = stringResource(Res.string.servicesStopConfirm),
                kind = KitConfirmKind.Stop,
                body = stringResource(Res.string.servicesStopHint),
            )
        is DevServicesAsk.Restart ->
            KitConfirmSpec(
                title = stringResource(Res.string.servicesRestartTitle, ask.name),
                confirmLabel = stringResource(Res.string.servicesRestartConfirm),
                kind = KitConfirmKind.Stop,
                body = stringResource(Res.string.servicesRestartHint),
            )
        is DevServicesAsk.Forget ->
            KitConfirmSpec(
                title = stringResource(Res.string.servicesForgetTitle, ask.name),
                confirmLabel = stringResource(Res.string.servicesForget),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.servicesForgetHint),
            )
        is DevServicesAsk.Remove ->
            KitConfirmSpec(
                title = stringResource(Res.string.servicesRemoveTitle, ask.name),
                confirmLabel = stringResource(Res.string.servicesRemove),
                kind = KitConfirmKind.Destructive,
                body = if (ask.running) stringResource(Res.string.servicesRemoveRunningHint) else null,
            )
        is DevServicesAsk.StopFound ->
            KitConfirmSpec(
                title = stringResource(Res.string.servicesStopFoundTitle, ask.name),
                confirmLabel = stringResource(Res.string.servicesStop),
                kind = KitConfirmKind.Stop,
                body = stringResource(Res.string.servicesStopFoundHint),
            )
    }
