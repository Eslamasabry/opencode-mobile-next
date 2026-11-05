package io.github.eslamasabry.oc.feature.devservices.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitCommandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateLayout
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val LOG_HEIGHT_SHARE = .6f // F: lib/ui/screens/development_services_screen.dart:726

/**
 * DevServicesRoute.Home (Flutter DevelopmentServicesScreen): the project folder, the saved services
 * with Start or Stop and their other acts, then "Also running" — dev servers found on the phone or
 * among the server's shells.
 */
@Composable
public fun DevServicesScreen(state: DevServicesUi, actions: DevServicesActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    var moreOpen by remember { mutableStateOf(false) }
    var copyRequest by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var copyRevision by remember { mutableStateOf(0L) }
    val mask = rememberKitMask()
    val copy: (String) -> Unit = {
        copyRevision += 1
        copyRequest = copyRevision to mask(it)
    }
    KitClipboardEffect(copyRequest?.first, copyRequest?.second) { copyRequest = null }
    val canAdd = state.available && state.readable && !state.busy
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.servicesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    if (state.available && state.services.isNotEmpty())
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.servicesAdd),
                                actions::add,
                                enabled = canAdd,
                                disabledReason = stringResource(Res.string.servicesWorking),
                                icon = KitIcons.add,
                                testTag = "development-services-add",
                            )
                        )
                    else emptyList(),
                more =
                    if (state.available) {
                        { _ ->
                            KitMenu(
                                moreOpen,
                                listOf(
                                    KitMenuItem(
                                        stringResource(Res.string.servicesRefresh),
                                        actions::refresh,
                                        icon = KitIcons.retry,
                                        enabled =
                                            if (state.busy)
                                                KitEnabled.Disabled(
                                                    stringResource(Res.string.servicesWorking)
                                                )
                                            else KitEnabled.Enabled,
                                    )
                                ),
                                { moreOpen = false },
                            ) {
                                KitIconButton(
                                    KitIcons.more,
                                    stringResource(Res.string.kitMore),
                                    { moreOpen = true },
                                )
                            }
                        }
                    } else null,
            )
        },
        width = KitScreenWidth.List,
        loading = state.loading || state.busy,
        loadingLabel = stringResource(Res.string.servicesWorking),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S4.dp)
        ) {
            if (!state.available) {
                KitInlineState(
                    stringResource(Res.string.servicesScopeChanged),
                    KitIcons.info,
                    layout = KitInlineStateLayout.List,
                )
                return@Column
            }
            if (!state.supported) {
                KitNotice(
                    message = stringResource(Res.string.servicesUnavailable),
                    liveRegion = false,
                )
                Spacer(Modifier.height(Space.S3.dp))
            }
            Notices(state, actions)
            if (state.services.isEmpty() && !state.loading) {
                KitInlineState(
                    stringResource(Res.string.servicesEmptyTitle),
                    KitIcons.processor,
                    layout = KitInlineStateLayout.List,
                    body = stringResource(Res.string.servicesIntro),
                    primary =
                        KitAction(
                            stringResource(Res.string.servicesAdd),
                            actions::add,
                            icon = KitIcons.add,
                            enabled =
                                if (canAdd) KitEnabled.Enabled
                                else
                                    KitEnabled.Disabled(stringResource(Res.string.servicesWorking)),
                        ),
                )
            } else if (state.services.isNotEmpty()) {
                KitRowGroup(leadingTiles = true) {
                    state.services.forEach { ServiceBlock(it, state, actions, copy) }
                }
                Spacer(Modifier.height(Space.S2.dp))
                KitText(
                    stringResource(Res.string.servicesStatusHint),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
            Found(state, actions, copy)
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            val redactor = LocalKitRedactor.current
            KitDetailsFold(
                stringResource(Res.string.kitDetails),
                stringResource(Res.string.kitCopyDetails),
                values =
                    listOfNotNull(
                        KitTechnicalValue(
                            stringResource(Res.string.servicesProjectFolder),
                            redactor.details(state.directory),
                        ),
                        state.workspace?.let {
                            KitTechnicalValue(
                                stringResource(Res.string.servicesWorkspace),
                                redactor.details(it),
                            )
                        },
                    ),
            )
        }
        KitSheet(
            visible = state.log != null,
            onDismissRequest = actions::closeLogs,
            title =
                state.log?.let { "${it.title} · ${stringResource(Res.string.servicesLogs)}" }
                    ?: stringResource(Res.string.servicesLogs),
            height = KitSheetHeight.Full,
            primary =
                state.log?.let { log ->
                    val saved =
                        state.services.firstOrNull { !log.found && it.id == log.id && it.canStop }
                    val found =
                        state.found.firstOrNull { log.found && it.id == log.id && it.canStop }
                    if (saved != null || found != null)
                        KitAction(
                            stringResource(Res.string.servicesStop),
                            {
                                actions.closeLogs()
                                if (log.found) actions.askStopFound(log.id)
                                else actions.askStop(log.id)
                            },
                            icon = KitIcons.stop,
                        )
                    else null
                },
            secondary =
                state.log
                    ?.takeIf { log ->
                        !log.found && state.services.any { it.id == log.id && it.canStop }
                    }
                    ?.let { log ->
                        KitAction(
                            stringResource(Res.string.servicesRestart),
                            {
                                actions.closeLogs()
                                actions.askRestart(log.id)
                            },
                            icon = KitIcons.restart,
                        )
                    },
            closeLabel = stringResource(Res.string.kitSheetClose),
        ) {
            state.log?.let { log ->
                if (log.failed) KitNotice(stringResource(Res.string.servicesLogFailed))
                KitLogPanel(
                    log.text,
                    rememberKitMask(),
                    actions::refreshLogs,
                    Modifier.fillMaxWidth()
                        .height((LocalKitWindow.current.heightDp * LOG_HEIGHT_SHARE).dp),
                )
            }
        }
        Editor(state.editor, state, actions)
        Output(state, actions)
        KitConfirm(
            state.ask?.let { spec(it) },
            onConfirm = actions::confirm,
            onCancel = actions::cancel,
        )
    }
}

@Composable
private fun Output(state: DevServicesUi, actions: DevServicesActions) {
    val output = state.output
    KitSheet(
        visible = output != null,
        onDismissRequest = actions::closeOutput,
        title = stringResource(Res.string.workOutput),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary =
            KitAction(
                stringResource(
                    if (output?.hasMore == true) Res.string.workMoreOutput
                    else Res.string.workRefresh
                ),
                actions::refreshOutput.takeIf { output?.refreshing == false },
            ),
    ) {
        if (output != null) {
            output.shell?.command?.let { KitText(it, role = TypeRole.Mono) }
            if (!output.available)
                KitNotice(
                    message =
                        stringResource(
                            if (output.serverRestarted) Res.string.workRestarted
                            else Res.string.workUnavailable
                        )
                )
            if (output.trimmed)
                KitNotice(message = stringResource(Res.string.workTrimmed), liveRegion = false)
            output.failure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.shellOutputReadFailed),
                    failure = it,
                    retry =
                        KitLineAction(
                            stringResource(Res.string.commonRetry),
                            actions::refreshOutput,
                        ),
                )
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                KitText(
                    output.displayText.ifBlank { stringResource(Res.string.servicesLogEmpty) },
                    role = TypeRole.Mono,
                )
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
                    is DevServicesReceipt.Started ->
                        stringResource(Res.string.servicesStarted, receipt.name)
                    is DevServicesReceipt.Removed ->
                        stringResource(Res.string.servicesRemoved, receipt.name)
                },
            tone = StatusTone.Ok,
            dismiss =
                KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss),
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
        DevServicesProblem.NotSaved,
        DevServicesProblem.Failed -> stringResource(Res.string.linuxMgrProblemFailed)
    }

@Composable
private fun statusWords(row: ServiceRowUi): String =
    when (row.status) {
        DevelopmentServiceStatus.NotStarted -> stringResource(Res.string.servicesNotStarted)
        DevelopmentServiceStatus.Running -> stringResource(Res.string.servicesRunning)
        DevelopmentServiceStatus.Stopped ->
            listOfNotNull(
                    stringResource(Res.string.servicesStopped),
                    row.exitCode?.let { stringResource(Res.string.servicesExit, it) },
                )
                .joinToString(" · ")
        DevelopmentServiceStatus.Unknown -> stringResource(Res.string.servicesUnknown)
    }

@Composable
private fun ServiceBlock(
    row: ServiceRowUi,
    state: DevServicesUi,
    actions: DevServicesActions,
    copy: (String) -> Unit,
) {
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    val hold =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.servicesWorking))
        else KitEnabled.Enabled
    val mask = rememberKitMask()
    val logsAvailable = state.supported && row.status != DevelopmentServiceStatus.NotStarted
    val menu =
        listOfNotNull(
            if (row.canStart)
                KitMenuItem(
                    stringResource(Res.string.servicesStart),
                    { actions.start(row.id) },
                    KitIcons.play,
                    enabled = hold,
                )
            else null,
            if (row.canStop)
                KitMenuItem(
                    stringResource(Res.string.servicesStop),
                    { actions.askStop(row.id) },
                    KitIcons.stop,
                    enabled = hold,
                )
            else null,
            if (row.canStop)
                KitMenuItem(
                    stringResource(Res.string.servicesRestart),
                    { actions.askRestart(row.id) },
                    KitIcons.restart,
                    enabled = hold,
                )
            else null,
            if (logsAvailable)
                KitMenuItem(
                    stringResource(Res.string.servicesLogs),
                    { actions.logs(row.id) },
                    KitIcons.text,
                    enabled = hold,
                )
            else null,
            if (row.url.isNotBlank())
                KitMenuItem(
                    stringResource(Res.string.servicesVisit),
                    { actions.visit(row.id) },
                    KitIcons.externalLink,
                )
            else null,
            KitMenuItem(
                stringResource(Res.string.servicesCopy),
                { copy(row.command) },
                KitIcons.copy,
            ),
            if (row.status == DevelopmentServiceStatus.Unknown)
                KitMenuItem(
                    stringResource(Res.string.servicesForget),
                    { actions.askForget(row.id) },
                    KitIcons.info,
                    enabled = hold,
                )
            else null,
            KitMenuItem(
                stringResource(Res.string.servicesRemove),
                { actions.askRemove(row.id) },
                KitIcons.delete,
                destructive = true,
                enabled = hold,
            ),
        )
    val ports =
        row.ports
            .takeIf { row.status == DevelopmentServiceStatus.Running && it.isNotEmpty() }
            ?.let {
                pluralStringResource(
                    Res.plurals.servicesPorts,
                    it.size,
                    it.size,
                    it.joinToString(", "),
                )
            }
    KitCommandRow(
        row.name,
        listOfNotNull(statusWords(row), ports, mask(row.command)).joinToString(" · "),
        KitIcons.processor,
        { if (logsAvailable) actions.logs(row.id) else menuOpen = true },
        Modifier.testTag("development-service-${row.id}"),
        current = row.status == DevelopmentServiceStatus.Running,
        below =
            if (row.status == DevelopmentServiceStatus.Unknown || row.url.isNotBlank()) {
                {
                    KitText(
                        if (row.status == DevelopmentServiceStatus.Unknown)
                            stringResource(Res.string.servicesUnknownHint)
                        else mask(row.url),
                        role =
                            if (row.status == DevelopmentServiceStatus.Unknown) TypeRole.Secondary
                            else TypeRole.Mono,
                        tone = TextTone.Secondary,
                        maxLines =
                            if (row.status == DevelopmentServiceStatus.Unknown) Int.MAX_VALUE
                            else 1,
                    )
                }
            } else null,
        trailing = {
            Row {
                if (row.canStop)
                    KitIconButton(
                        KitIcons.stop,
                        stringResource(Res.string.servicesStopNamed, row.name),
                        { actions.askStop(row.id) },
                        enabled = hold,
                    )
                else if (row.canStart)
                    KitIconButton(
                        KitIcons.play,
                        stringResource(Res.string.servicesStartNamed, row.name),
                        { actions.start(row.id) },
                        enabled = hold,
                    )
                KitMenu(menuOpen, menu, { menuOpen = false }) {
                    KitIconButton(
                        KitIcons.more,
                        "${stringResource(Res.string.kitMore)} · ${row.name}",
                        { menuOpen = true },
                    )
                }
            }
        },
    )
}

@Composable
private fun Found(state: DevServicesUi, actions: DevServicesActions, copy: (String) -> Unit) {
    if (state.found.isNotEmpty()) {
        Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
        KitSectionLabel(stringResource(Res.string.servicesAlsoRunning))
        KitRowGroup(leadingTiles = true) {
            state.found.forEach { FoundRow(it, state, actions, copy) }
        }
    }
    if (state.found.isNotEmpty() || state.foundFailed || (!state.scansPhone && state.supported)) {
        Spacer(Modifier.height(Space.S2.dp))
        KitText(
            stringResource(
                when {
                    !state.scansPhone -> Res.string.servicesAlsoServerHint
                    state.foundFailed -> Res.string.servicesAlsoPhoneFailed
                    else -> Res.string.servicesAlsoPhoneHint
                }
            ),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
    }
}

@Composable
private fun FoundRow(
    row: FoundRowUi,
    state: DevServicesUi,
    actions: DevServicesActions,
    copy: (String) -> Unit,
) {
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    val mask = rememberKitMask()
    val hold =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.servicesWorking))
        else KitEnabled.Enabled
    val ports =
        if (row.ports.isEmpty()) stringResource(Res.string.servicesNoPortYet)
        else
            pluralStringResource(
                Res.plurals.servicesPorts,
                row.ports.size,
                row.ports.size,
                row.ports.joinToString(", "),
            )
    val menu =
        listOfNotNull(
            if (row.hasLogs)
                KitMenuItem(
                    stringResource(Res.string.servicesLogs),
                    { actions.foundLogs(row.id) },
                    KitIcons.text,
                    enabled = hold,
                )
            else null,
            if (row.canStop)
                KitMenuItem(
                    stringResource(Res.string.servicesStop),
                    { actions.askStopFound(row.id) },
                    KitIcons.stop,
                    enabled = hold,
                )
            else null,
            if (
                row.canSave &&
                    state.readable &&
                    mask(row.command) == row.command &&
                    state.services.none { it.command.trim() == row.command.trim() }
            )
                KitMenuItem(
                    stringResource(Res.string.servicesSaveCommand),
                    { actions.saveFound(row.id) },
                    KitIcons.save,
                    enabled = hold,
                )
            else null,
            if (row.command.isNotBlank())
                KitMenuItem(
                    stringResource(Res.string.servicesCopy),
                    { copy(row.command) },
                    KitIcons.copy,
                )
            else null,
        )
    KitCommandRow(
        mask(row.name),
        "${stringResource(Res.string.servicesRunning)} · $ports",
        KitIcons.processor,
        { if (row.hasLogs) actions.foundLogs(row.id) else menuOpen = true },
        Modifier.testTag("development-found-${row.id}"),
        current = true,
        below =
            if (row.command.isNotBlank()) {
                { KitText(mask(row.command), role = TypeRole.Mono, maxLines = 1) }
            } else null,
        trailing = {
            Row {
                if (row.canStop)
                    KitIconButton(
                        KitIcons.stop,
                        stringResource(Res.string.servicesStopNamed, mask(row.name)),
                        { actions.askStopFound(row.id) },
                        enabled = hold,
                    )
                if (menu.isNotEmpty())
                    KitMenu(menuOpen, menu, { menuOpen = false }) {
                        KitIconButton(
                            KitIcons.more,
                            "${stringResource(Res.string.kitMore)} · ${mask(row.name)}",
                            { menuOpen = true },
                        )
                    }
            }
        },
    )
}

@Composable
private fun Editor(editor: EditorUi?, state: DevServicesUi, actions: DevServicesActions) {
    val open = editor != null
    // A fresh form each time it opens, filled from what opened it (a found server's command, or
    // empty).
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
        subtitle = state.directory,
        icon = KitIcons.processor,
        loading = state.busy,
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary =
            KitAction(
                stringResource(Res.string.servicesSave),
                save,
                enabled =
                    if (state.busy) KitEnabled.Disabled(stringResource(Res.string.servicesWorking))
                    else KitEnabled.Enabled,
            ),
        dirty = name.text.isNotEmpty() || command.text.isNotEmpty(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
            KitField(
                name,
                label = stringResource(Res.string.servicesName),
                error =
                    when (editor?.nameError) {
                        NameError.Required -> stringResource(Res.string.servicesNameRequired)
                        NameError.Duplicate -> stringResource(Res.string.servicesDuplicateName)
                        null -> null
                    },
                maxLength = 80, // F: lib/ui/screens/development_services_screen.dart:1153
            )
            KitField(
                command,
                label = stringResource(Res.string.servicesCommand),
                kind = KitFieldKind.Mono,
                maxLength = 4096, // F: lib/ui/screens/development_services_screen.dart:1165
                helper = stringResource(Res.string.servicesCommandHint),
                error =
                    if (editor?.commandMissing == true)
                        stringResource(Res.string.servicesCommandRequired)
                    else null,
            )
            KitField(
                url,
                label = stringResource(Res.string.servicesUrl),
                kind = KitFieldKind.Url,
                maxLength = 2048, // F: lib/ui/screens/development_services_screen.dart:1177
                helper = stringResource(Res.string.servicesUrlHint),
                error =
                    if (editor?.urlInvalid == true) stringResource(Res.string.servicesUrlInvalid)
                    else null,
                onSubmit = save,
            )
        }
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
                body =
                    if (ask.running) stringResource(Res.string.servicesRemoveRunningHint) else null,
            )
        is DevServicesAsk.StopFound ->
            KitConfirmSpec(
                title = stringResource(Res.string.servicesStopFoundTitle, ask.name),
                confirmLabel = stringResource(Res.string.servicesStop),
                kind = KitConfirmKind.Stop,
                body = stringResource(Res.string.servicesStopFoundHint),
            )
    }
