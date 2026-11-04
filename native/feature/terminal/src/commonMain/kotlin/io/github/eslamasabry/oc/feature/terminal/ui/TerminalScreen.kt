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
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalActions
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * A visible project-terminal path; the existing kit provides the readable transcript and labeled
 * input.
 */
@Composable
public fun TerminalScreen(state: RemoteTerminalState, actions: TerminalActions) {
    val terminal = state.selected
    var rename by remember(terminal?.id) { mutableStateOf(false) }
    var remove by remember(terminal?.id) { mutableStateOf(false) }
    var resize by remember(terminal?.id) { mutableStateOf(false) }
    val title =
        terminal?.title?.takeIf { it.isNotBlank() }
            ?: stringResource(Res.string.libraryTerminalTitle)
    val newLabel = stringResource(Res.string.remoteTerminalCreate)
    val renameLabel = stringResource(Res.string.terminalScreenRenameConfirm)
    val sizeLabel = stringResource(Res.string.remoteTerminalSize)
    val listLabel = stringResource(Res.string.remoteTerminalList)
    val stopLabel =
        stringResource(
            if (terminal?.running == true) Res.string.terminalScreenStopConfirm
            else Res.string.terminalScreenRemoveConfirm
        )
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    buildList {
                        add(KitLineAction(newLabel, actions::create))
                        if (terminal != null) {
                            add(KitLineAction(listLabel, actions::showList))
                            add(KitLineAction(renameLabel, { rename = true }))
                            add(KitLineAction(sizeLabel, { resize = true }))
                            add(KitLineAction(stopLabel, { remove = true }))
                        }
                    },
            )
        },
        width = KitScreenWidth.Full,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            state.failure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.remoteTerminalFailed),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refresh),
                )
            }
            if (terminal == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    when {
                        state.loading -> item { KitSkeletonRows() }
                        state.terminals.isEmpty() ->
                            item {
                                KitStateView(
                                    kind = KitStateKind.Empty,
                                    title = stringResource(Res.string.remoteTerminalEmpty),
                                    primary =
                                        KitAction(
                                            newLabel,
                                            actions::create,
                                            working = state.working,
                                        ),
                                )
                            }
                        else ->
                            items(state.terminals, key = { it.id }) { entry ->
                                KitRow(
                                    title = entry.title.ifBlank { entry.command },
                                    supporting = entry.directory,
                                    onClick = { actions.select(entry.id) },
                                )
                            }
                    }
                }
            } else {
                if (!state.connected) {
                    KitNotice(message = stringResource(Res.string.remoteTerminalOffline))
                    KitButton(
                        stringResource(Res.string.remoteTerminalReconnect),
                        { actions.select(terminal.id) },
                        working = state.working,
                    )
                }
                KitTerminalView(
                    platformView = null,
                    title = title,
                    transcript = state.transcript,
                    accessibleMode = true,
                    onAccessibleModeChange = {},
                    onSend = { actions.send(terminal.id, it) },
                    words =
                        KitTerminalWords(
                            stringResource(Res.string.libraryTerminalTitle),
                            stringResource(Res.string.remoteTerminalReadable),
                            stringResource(Res.string.remoteTerminalTranscript),
                            stringResource(Res.string.remoteTerminalNoOutput),
                            stringResource(Res.string.remoteTerminalInput),
                            stringResource(Res.string.remoteTerminalSend),
                            stringResource(Res.string.remoteTerminalOffline),
                        ),
                    inputEnabled = state.connected && !state.working,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            if (terminal != null) {
                RenameTerminal(rename, terminal.title, onDismiss = { rename = false }) {
                    rename = false
                    actions.rename(terminal.id, it)
                }
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

@Composable
private fun RenameTerminal(
    visible: Boolean,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val name = rememberTextFieldState(current)
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.terminalScreenRenameConfirm),
        primary =
            KitAction(
                stringResource(Res.string.terminalScreenRenameConfirm),
                { if (name.text.isNotBlank()) onSave(name.text.toString()) },
            ),
    ) {
        KitField(name, stringResource(Res.string.terminalScreenNameLabel), maxLength = 200)
    }
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
