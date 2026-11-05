package io.github.eslamasabry.oc.feature.projects.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalBarKey
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBar
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBarController
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The callable server's readable terminal; no platform grid is fabricated when the remote renderer
 * is absent.
 */
@Composable
public fun TerminalScreen(state: TerminalUi, actions: TerminalActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    var menu by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.terminal40Title)
    val unavailable = stringResource(Res.string.terminal40InputOff)
    val selected = state.processes.firstOrNull { it.id == state.selected }
    val connected = state.phase == TerminalPhase.Connected
    val canAct =
        if (state.working) KitEnabled.Disabled(stringResource(Res.string.terminal40Working))
        else KitEnabled.Enabled
    val keys = remember { TerminalKeyBarController() }
    KitScreen(
        loading = state.phase == TerminalPhase.Opening || state.phase == TerminalPhase.Connecting,
        topBar = {
            KitTopBar(
                title = selected?.title ?: title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    if (selected == null) emptyList()
                    else
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.terminal40Copy),
                                actions::copy,
                                icon = KitIcons.copy,
                            )
                        ),
                more = { _ ->
                    KitMenu(
                        expanded = menu,
                        items =
                            buildList {
                                add(
                                    KitMenuItem(
                                        stringResource(Res.string.terminal40New),
                                        {
                                            menu = false
                                            actions.create()
                                        },
                                        enabled = canAct,
                                    )
                                )
                                if (selected != null) {
                                    if (state.grid != null)
                                        add(
                                            KitMenuItem(
                                                stringResource(Res.string.terminal40Select),
                                                {
                                                    menu = false
                                                    actions.selectText()
                                                },
                                                checked = state.selecting,
                                            )
                                        )
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.terminal40Copy),
                                            {
                                                menu = false
                                                actions.copy()
                                            },
                                        )
                                    )
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.terminal40Paste),
                                            {
                                                menu = false
                                                actions.paste()
                                            },
                                            enabled =
                                                if (connected) canAct
                                                else KitEnabled.Disabled(unavailable),
                                        )
                                    )
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.terminal40Reconnect),
                                            {
                                                menu = false
                                                actions.reconnect()
                                            },
                                        )
                                    )
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.terminal40Rename),
                                            {
                                                menu = false
                                                actions.askRename()
                                            },
                                            enabled = canAct,
                                        )
                                    )
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.kitDetails),
                                            {
                                                menu = false
                                                actions.details()
                                            },
                                        )
                                    )
                                    add(
                                        KitMenuItem(
                                            stringResource(
                                                if (selected.running) Res.string.terminal40Stop
                                                else Res.string.terminal40Remove
                                            ),
                                            {
                                                menu = false
                                                actions.askRemove()
                                            },
                                            destructive = true,
                                            enabled = canAct,
                                        )
                                    )
                                }
                                if (state.processes.any { !it.running })
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.terminal40RemoveEnded),
                                            {
                                                menu = false
                                                actions.askRemoveEnded()
                                            },
                                            destructive = true,
                                            enabled = canAct,
                                        )
                                    )
                            },
                        onDismissRequest = { menu = false },
                        anchor = {
                            KitIconButton(
                                KitIcons.more,
                                stringResource(Res.string.terminal40Actions),
                                { menu = true },
                            )
                        },
                    )
                },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            val unavailableState =
                state.phase == TerminalPhase.Unavailable || state.phase == TerminalPhase.Stale
            if (unavailableState) {
                KitNotice(
                    stringResource(
                        if (state.phase == TerminalPhase.Stale) Res.string.terminal40Stale
                        else Res.string.terminal40Unavailable
                    )
                )
                return@Column
            }
            state.failure?.let { failure ->
                KitErrorNotice(
                    message = stringResource(Res.string.terminal40Failed),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refresh),
                )
            }
            if (state.clipboardFailed)
                KitNotice(stringResource(Res.string.terminal40ClipboardFailed))
            if (state.processes.size in 2..8) {
                KitTabStrip(
                    tabs =
                        state.processes.map {
                            KitTab(
                                if (it.running) it.title
                                else stringResource(Res.string.terminal40EndedTab, it.title)
                            )
                        },
                    selected =
                        state.processes.indexOfFirst { it.id == state.selected }.coerceAtLeast(0),
                    onSelected = { actions.select(state.processes[it].id) },
                    semanticsLabel = stringResource(Res.string.terminal40Sessions),
                )
            } else if (state.processes.size > 8) {
                KitMenu(
                    expanded = sessions,
                    items =
                        state.processes.map { process ->
                            KitMenuItem(
                                process.title,
                                {
                                    sessions = false
                                    actions.select(process.id)
                                },
                                checked = process.id == state.selected,
                            )
                        },
                    onDismissRequest = { sessions = false },
                    anchor = {
                        KitButton(
                            stringResource(Res.string.terminal40Sessions),
                            { sessions = true },
                            role = KitButtonRole.Tertiary,
                        )
                    },
                )
            }
            if (selected == null) {
                if (state.phase != TerminalPhase.Opening)
                    KitNotice(stringResource(Res.string.terminal40Empty))
            } else {
                if (!connected)
                    KitNotice(
                        stringResource(
                            when (state.phase) {
                                TerminalPhase.Connecting -> Res.string.terminal40Connecting
                                TerminalPhase.Paused -> Res.string.terminal40Paused
                                else -> Res.string.terminal40Closed
                            }
                        )
                    )
                state.details?.let { detail ->
                    KitDetailsFold(
                        label = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        initiallyExpanded = true,
                        values =
                            listOf(
                                KitTechnicalValue(
                                    stringResource(Res.string.terminal40CommandLabel),
                                    detail.command,
                                ),
                                KitTechnicalValue(
                                    stringResource(Res.string.terminal40FolderLabel),
                                    detail.directory,
                                ),
                                KitTechnicalValue(
                                    stringResource(Res.string.terminal40PidLabel),
                                    detail.pid,
                                ),
                                KitTechnicalValue(
                                    stringResource(Res.string.terminal40ExitLabel),
                                    detail.exit,
                                ),
                            ),
                    )
                }
                KitTip(
                    message = stringResource(Res.string.terminal40Tip),
                    visible = state.teaching,
                    onDismissed = actions::dismissTip,
                    modifier = Modifier.weight(1f),
                    anchor = {
                        KitTerminalView(
                            platformView = state.grid?.native,
                            title = selected.title,
                            accessibleMode = state.accessibleMode,
                            onAccessibleModeChange = actions::accessibleMode,
                            transcript = state.transcript,
                            onSend = { actions.send(keys.apply(it)) },
                            defaultKeyBar = false,
                            showAccessibleSwitch = false,
                            inputEnabled = connected,
                            words =
                                KitTerminalWords(
                                    terminal = title,
                                    accessibleMode = stringResource(Res.string.terminal40Readable),
                                    transcript = stringResource(Res.string.terminal40Transcript),
                                    noOutput = stringResource(Res.string.terminal40NoOutput),
                                    sendToTerminal = stringResource(Res.string.terminal40Command),
                                    send = stringResource(Res.string.terminal40Send),
                                    inputUnavailable = unavailable,
                                ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
                TerminalKeyBar(
                    controller = keys,
                    enabled = connected,
                    disabledReason = unavailable,
                    interruptKeys = true,
                    onKey = { key, ctrl, alt ->
                        if (ctrl) actions.key("ctrl")
                        if (alt) actions.key("alt")
                        actions.key(terminalActionKey(key))
                    },
                )
            }
        }
        KitInputDialog(
            visible = state.rename != null,
            title = stringResource(Res.string.terminal40Rename),
            fieldLabel = stringResource(Res.string.terminal40Name),
            confirmLabel = stringResource(Res.string.terminal40Rename),
            initial = state.processes.firstOrNull { it.id == state.rename }?.title.orEmpty(),
            validate = { if (it.isBlank()) unavailable else null },
            onResult = actions::rename,
        )
        KitConfirm(
            spec =
                if (state.removing.isEmpty()) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.terminal40RemoveQuestion),
                        confirmLabel = stringResource(Res.string.terminal40Remove),
                        kind = KitConfirmKind.Destructive,
                        body =
                            stringResource(
                                Res.string.terminal40RemoveBody,
                                state.removing.joinToString(", ") { it.title },
                            ),
                    ),
            onConfirm = actions::remove,
            onCancel = actions::dismiss,
        )
    }
}

/** The view model's existing named-key path retains the platform cursor-key mode. */
internal fun terminalActionKey(key: KitTerminalBarKey): String =
    when (key) {
        KitTerminalBarKey.Esc -> "escape"
        KitTerminalBarKey.Tab -> "tab"
        KitTerminalBarKey.Interrupt -> "interrupt"
        KitTerminalBarKey.EndOfInput -> "eof"
        KitTerminalBarKey.PageUp -> "pageUp"
        KitTerminalBarKey.PageDown -> "pageDown"
        else -> key.text ?: key.name.replaceFirstChar { it.lowercase() }
    }
