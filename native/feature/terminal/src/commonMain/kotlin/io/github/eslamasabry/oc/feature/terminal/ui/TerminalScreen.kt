package io.github.eslamasabry.oc.feature.terminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalLine
import io.github.eslamasabry.oc.feature.terminal.presentation.terminalKeyText
import io.github.eslamasabry.oc.feature.terminal.presentation.terminalLine
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBar
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBarController
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.TerminalName
import io.github.eslamasabry.oc.core.model.givenName
import io.github.eslamasabry.oc.core.model.terminalNameOf
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalDetailsUi
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A server's terminal (Flutter terminal_surface.dart): Copy in the bar and the rest in its menu, one status line only
 * while the shell is not connected, the readable transcript with its field, and the key bar under it.
 */
@Composable
public fun TerminalScreen(state: RemoteTerminalState, actions: TerminalActions, devServerPort: Int? = null) {
    val terminal = state.selected
    val canCreate = !state.loading && !state.working && state.failure == null
    var barMenuOpen by remember { mutableStateOf(false) }
    var rename by remember(terminal?.id) { mutableStateOf(false) }
    var remove by remember(terminal?.id) { mutableStateOf(false) }
    var resize by remember(terminal?.id) { mutableStateOf(false) }
    var details by remember(terminal?.id) { mutableStateOf<TerminalDetailsUi?>(null) }
    var removeEnded by remember { mutableStateOf(false) }
    val endedCount = state.terminals.count { !it.running && it.id != terminal?.id }
    val plainTitle = stringResource(Res.string.libraryTerminalTitle)
    // One rule for every place a terminal is named (core:model TerminalName): the person's own name, else its number
    // in the order the terminals were opened; the server's "Terminal <random id>" is never shown.
    val names =
        state.terminals.associate { process ->
            process.id to
                when (val name = terminalNameOf(state.terminals, process)) {
                    is TerminalName.Given -> name.text
                    is TerminalName.Plain ->
                        if (name.number <= 1) plainTitle
                        else stringResource(Res.string.e7SetupTerminalNumber, name.number)
                }
        }
    val title = terminal?.let { names[it.id] } ?: plainTitle
    val newLabel = stringResource(Res.string.remoteTerminalCreate)
    // No title is sent: the server's own default is hidden by the naming rule, so numbers never collide.
    val createNamed: () -> Unit = { actions.create() }
    val stopLabel =
        stringResource(
            if (terminal?.running == true) Res.string.terminalScreenStopConfirm
            else Res.string.terminalScreenRemoveConfirm
        )
    val keys = remember(terminal?.id) { TerminalKeyBarController() }
    val copy =
        rememberKitCopyAction(
            stringResource(Res.string.terminalScreenCopy),
            stringResource(Res.string.kitCopied),
            { state.transcript },
        )
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val canWrite = terminal != null && state.connected && !state.working
    val reconnect: () -> Unit = { terminal?.let { actions.select(it.id) } }
    val close: (() -> Unit) -> () -> Unit = { run ->
        {
            barMenuOpen = false
            run()
        }
    }
    val menu = buildList {
        if (canCreate) add(KitMenuItem(newLabel, close(createNamed), icon = KitIcons.add))
        // G7: Flutter's "Open preview" for the project's running dev server, on every server terminal.
        if (devServerPort != null)
            add(KitMenuItem(stringResource(Res.string.terminalOpenPreview), close(actions::openDevPreview), icon = KitIcons.browser))
        if (terminal == null && endedCount > 0 && !state.working)
            add(
                KitMenuItem(
                    pluralStringResource(Res.plurals.terminalScreenRemoveEnded, endedCount, endedCount),
                    close { removeEnded = true },
                    icon = KitIcons.delete,
                    destructive = true,
                )
            )
        add(KitMenuItem(stringResource(Res.string.terminalScreenUsePhone), close(actions::usePhone), icon = KitIcons.terminal))
        if (terminal != null) {
            add(KitMenuItem(
                stringResource(Res.string.remoteTerminalList),
                close(actions::showList),
                icon = KitIcons.terminal,
            ))
            add(
                KitMenuItem(
                    stringResource(Res.string.terminalScreenPaste, title),
                    close {
                        val pasted = clipboard.getText()?.text.orEmpty()
                        if (pasted.isNotEmpty()) actions.send(terminal.id, pasted)
                    },
                    icon = KitIcons.paste,
                    enabled =
                        if (canWrite) KitEnabled.Enabled
                        else KitEnabled.Disabled(stringResource(Res.string.e7SetupInputDisconnected)),
                )
            )
            add(
                KitMenuItem(
                    stringResource(Res.string.e7SetupReconnect),
                    close(reconnect),
                    icon = KitIcons.retry,
                    enabled =
                        if (state.working) KitEnabled.Disabled(stringResource(Res.string.terminalScreenConnecting))
                        else KitEnabled.Enabled,
                )
            )
            add(KitMenuItem(
                stringResource(Res.string.terminalScreenRename, title),
                close { rename = true },
                icon = KitIcons.edit,
            ))
            add(KitMenuItem(stringResource(Res.string.remoteTerminalSize), close { resize = true }))
            add(KitMenuItem(
                stringResource(Res.string.terminalScreenDetails),
                close { details = actions.details() },
                icon = KitIcons.info,
            ))
            add(
                KitMenuItem(
                    stringResource(
                        if (terminal.running) Res.string.terminalScreenStop else Res.string.terminalScreenRemove,
                        title,
                    ),
                    close { remove = true },
                    icon = if (terminal.running) KitIcons.stopCircle else KitIcons.delete,
                    destructive = true,
                )
            )
        }
    }
    KitScreen(
        status = terminalStatus(terminalLine(state), reconnect),
        topBar = {
            // Copy is the bar's one action; everything else waits in the menu, so nothing is cut off at phone width.
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                subtitle = devServerPort?.let { stringResource(Res.string.terminalDevServerRunning) },
                actions =
                    if (terminal == null) emptyList()
                    else listOf(KitLineAction(
                        copy.label,
                        { copy.onClick?.invoke() },
                        icon = copy.icon,
                        testTag = TerminalTags.COPY,
                    )),
                more = { _ ->
                    if (menu.isNotEmpty()) {
                        val moreLabel =
                            if (terminal != null) stringResource(Res.string.terminalScreenMenuLabel, title)
                            else stringResource(Res.string.kitTopBarMore)
                        KitMenu(
                            expanded = barMenuOpen,
                            items = menu,
                            onDismissRequest = { barMenuOpen = false },
                            title = moreLabel,
                        ) {
                            KitIconButton(
                                KitIcons.more,
                                moreLabel,
                                { barMenuOpen = true },
                                modifier = Modifier.testTag(TerminalTags.MENU),
                            )
                        }
                    }
                },
            )
        },
        width = KitScreenWidth.Full,
    ) {
        KitConfirm(
            if (!removeEnded) null
            else
                KitConfirmSpec(
                    title = pluralStringResource(Res.plurals.terminalScreenRemoveEndedTitle, endedCount, endedCount),
                    confirmLabel = pluralStringResource(Res.plurals.terminalScreenRemoveEnded, endedCount, endedCount),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.terminalScreenRemoveEndedBody),
                ),
            onConfirm = {
                removeEnded = false
                actions.removeEnded()
            },
            onCancel = { removeEnded = false },
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            // The frame keeps the page gutter (KitScreenEdges.Gutter): the notices, output and keys sit inside it.
            val inset = Modifier
            state.failure?.let {
                KitErrorNotice(
                    modifier = inset,
                    message = terminalFailureText(it),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry =
                        if (
                            it is Failure.Unavailable ||
                                (it is Failure.Refused && it.reason == RefusalReason.StaleScope)
                        )
                            null
                        else
                            KitLineAction(
                                stringResource(Res.string.kitTryAgain),
                                if (terminal != null) reconnect else actions::refresh,
                            ),
                )
            }
            if (terminal == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    when {
                        state.loading && state.terminals.isEmpty() -> item { KitSkeletonRows() }
                        state.failure != null && state.terminals.isEmpty() -> Unit
                        state.terminals.isEmpty() ->
                            item {
                                // Alone on the page: it takes the list's whole room, so it sits in the page's middle.
                                KitStateView(
                                    kind = KitStateKind.Empty,
                                    title = stringResource(Res.string.terminalScreenEmptyTitle),
                                    modifier = Modifier.fillParentMaxSize(),
                                    primary =
                                        KitAction(
                                            newLabel,
                                            createNamed,
                                            working = state.working,
                                        ),
                                )
                            }
                        else ->
                            items(state.terminals, key = { it.id }) { entry ->
                                KitRow(
                                    title = names[entry.id] ?: plainTitle,
                                    supporting = entry.directory,
                                    onClick = { actions.select(entry.id) },
                                )
                            }
                    }
                }
            } else {
                val inputOff = stringResource(Res.string.e7SetupInputDisconnected)
                KitTerminalView(
                    platformView = null,
                    title = title,
                    transcript = state.transcript,
                    accessibleMode = true,
                    onAccessibleModeChange = {},
                    // The phone keyboard's text takes a latched Ctrl or Alt, as Flutter's controller applies it.
                    onSend = { actions.send(terminal.id, keys.apply(it)) },
                    words =
                        KitTerminalWords(
                            stringResource(Res.string.libraryTerminalTitle),
                            stringResource(Res.string.remoteTerminalReadable),
                            stringResource(Res.string.remoteTerminalTranscript),
                            stringResource(Res.string.remoteTerminalNoOutput),
                            stringResource(Res.string.remoteTerminalInput),
                            stringResource(Res.string.remoteTerminalSend),
                            inputOff,
                        ),
                    inputEnabled = canWrite,
                    defaultKeyBar = false,
                    // A server terminal here is always the readable view: there is no live grid to switch to, so no
                    // disabled switch saying the shell is disconnected while it is live.
                    showAccessibleSwitch = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                TerminalKeyBar(
                    controller = keys,
                    enabled = canWrite,
                    disabledReason = inputOff,
                    interruptKeys = true,
                    onKey = { key, ctrl, alt ->
                        terminalKeyText(key, ctrl, alt)?.let { actions.send(terminal.id, it) }
                    },
                    modifier = Modifier.testTag(TerminalTags.KEYS),
                )
            }
            if (terminal != null) {
                // The field starts empty or with the person's own name, never a server id.
                KitInputDialog(
                    visible = rename,
                    title = stringResource(Res.string.terminalScreenRenameConfirm),
                    fieldLabel = stringResource(Res.string.terminalScreenNameLabel),
                    confirmLabel = stringResource(Res.string.terminalScreenRenameConfirm),
                    initial = terminal.givenName().orEmpty(),
                    operationKey = terminal.id,
                    onResult = {
                        rename = false
                        if (!it.isNullOrBlank()) actions.rename(terminal.id, it)
                    },
                )
                TerminalDetailsSheet(details, title) { details = null }
                ResizeTerminal(resize, onDismiss = { resize = false }) { rows, columns ->
                    resize = false
                    actions.resize(terminal.id, rows, columns)
                }
                KitConfirm(
                    if (!remove) null
                    else
                        KitConfirmSpec(
                            title =
                                stringResource(
                                    if (terminal.running) Res.string.terminalScreenStopTitle
                                    else Res.string.terminalScreenRemoveTitle,
                                    title,
                                ),
                            confirmLabel = stopLabel,
                            kind =
                                if (terminal.running) KitConfirmKind.Stop
                                else KitConfirmKind.Destructive,
                            body =
                                stringResource(
                                    if (terminal.running) Res.string.terminalScreenStopBody
                                    else Res.string.terminalScreenRemoveBody
                                ),
                        ),
                    onConfirm = {
                        remove = false
                        actions.remove(terminal.id)
                    },
                    onCancel = { remove = false },
                )
            }
        }
    }
}

/** Test handles for the parts a test cannot find by their words. */
public object TerminalTags {
    public const val COPY: String = "terminal-copy"
    public const val MENU: String = "terminal-surface-menu"
    public const val KEYS: String = "terminal-keys"
}

/** Flutter `_status`: the one line, only while the terminal is not connected. */
@Composable
private fun terminalStatus(line: TerminalLine, reconnect: () -> Unit): KitStatus? {
    val again = KitLineAction(stringResource(Res.string.e7SetupReconnect), reconnect)
    return when (line) {
        TerminalLine.None -> null
        TerminalLine.Connecting ->
            KitStatus(
                KitStatusKind.Connection,
                stringResource(Res.string.terminalScreenConnecting),
                id = "terminal-connection",
                tone = StatusTone.Progress,
                icon = KitIcons.sync,
            )
        is TerminalLine.Ended ->
            KitStatus(
                KitStatusKind.Connection,
                line.exitCode?.let { stringResource(Res.string.terminalScreenRowEnded, it.toString(), line.command) }
                    ?: stringResource(Res.string.terminalScreenRowEndedNoCode, line.command),
                id = "terminal-connection",
                icon = KitIcons.terminal,
            )
        TerminalLine.Closed ->
            KitStatus(
                KitStatusKind.Connection,
                stringResource(Res.string.e7SetupConnectionClosed),
                id = "terminal-connection",
                action = again,
                icon = KitIcons.cloudOff,
            )
    }
}

/** The project/scope and optional terminal service can fail while chat remains connected. */
@Composable
private fun terminalFailureText(failure: Failure): String =
    stringResource(
        when {
            failure is Failure.Unavailable -> Res.string.remoteTerminalServiceUnavailable
            failure is Failure.Refused && failure.reason == RefusalReason.StaleScope ->
                Res.string.remoteTerminalProjectChanged
            failure is Failure.SignIn || failure is Failure.SecretUnavailable ->
                Res.string.remoteTerminalSignInRequired
            failure is Failure.Network ||
                failure is Failure.TimedOut ||
                failure is Failure.ComputerDidNotAnswer -> Res.string.remoteTerminalServiceNoAnswer
            else -> Res.string.remoteTerminalFailed
        }
    )

/**
 * Flutter `showKitTechnicalDetails` for one terminal: state, command, folder, process id, exit code, each masked by
 * the kit's redaction and copied together with "Copy details".
 */
@Composable
private fun TerminalDetailsSheet(details: TerminalDetailsUi?, name: String, onDismiss: () -> Unit) {
    KitSheet(
        visible = details != null,
        onDismissRequest = onDismiss,
        title = details?.let { stringResource(Res.string.terminalScreenDetailsTitle, name) } ?: stringResource(Res.string.terminalScreenDetails),
    ) {
        if (details != null)
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                notes = terminalDetailLines(details),
                initiallyExpanded = true,
            )
    }
}

/** "Label: value" lines, the state first (the server's own word for the program, not the connection's). */
@Composable
internal fun terminalDetailLines(details: TerminalDetailsUi): List<String> {
    fun line(label: String, value: String) = "$label: ${KitRedact.text(value)}"
    return listOfNotNull(
        line(
            stringResource(Res.string.terminalScreenDetailState),
            stringResource(if (details.running) Res.string.terminalStateRunning else Res.string.terminalStateEnded),
        ),
        line(stringResource(Res.string.terminalScreenDetailCommand), details.command),
        details.folder?.let { line(stringResource(Res.string.terminalScreenDetailFolder), it) },
        line(stringResource(Res.string.terminalScreenDetailPid), details.pid.toString()),
        details.exitCode?.let { line(stringResource(Res.string.terminalScreenDetailExit), it.toString()) },
    )
}

@Composable
private fun ResizeTerminal(visible: Boolean, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val rows = rememberTextFieldState("24")
    val columns = rememberTextFieldState("80")
    var invalid by remember { mutableStateOf(false) }
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.remoteTerminalSize),
        primary =
            KitAction(
                stringResource(Res.string.remoteTerminalApplySize),
                {
                    val rowCount = rows.text.toString().toIntOrNull()
                    val columnCount = columns.text.toString().toIntOrNull()
                    if (
                        rowCount != null &&
                            columnCount != null &&
                            rowCount in 1..1000 &&
                            columnCount in 1..1000
                    ) {
                        invalid = false
                        onSave(rowCount, columnCount)
                    } else invalid = true
                },
            ),
    ) {
        KitField(rows, stringResource(Res.string.remoteTerminalRows), maxLength = 4)
        KitField(columns, stringResource(Res.string.remoteTerminalColumns), maxLength = 4)
        if (invalid) KitNotice(message = stringResource(Res.string.remoteTerminalSizeRange))
    }
}
