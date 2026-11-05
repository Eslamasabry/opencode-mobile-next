package io.github.eslamasabry.oc.feature.terminal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalNativeActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.content.terminal.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTab
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTabs
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

private val LOCAL_TERMINAL_COMPACT = 420.dp // F: lib/ui/screens/local_terminal_screen.dart:333

/** Flutter's local phone shell: optional sessions, one terminal/key surface, actions in the bar. */
@Composable
public fun LocalTerminalScreen(state: LocalTerminalUi, actions: LocalTerminalActions) {
    val nativeActions = actions as? LocalTerminalNativeActions
    val selected = state.shells.firstOrNull { it.id == state.selected }
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardVisible = keyboard > 0
    var menu by remember { mutableStateOf(false) }
    val held =
        if (state.working) KitEnabled.Disabled(stringResource(Res.string.localTerminalStarting))
        else KitEnabled.Enabled
    val keys =
        remember(selected?.id, nativeActions) {
            TerminalKeyBarController { key ->
                nativeActions?.key(
                    if (key == KitTerminalBarKey.Ctrl) TerminalKey.Control else TerminalKey.Alt
                )
            }
        }
    LaunchedEffect(keys, selected?.control, selected?.alt) {
        keys.sync(selected?.control == true, selected?.alt == true)
    }
    LaunchedEffect(selected?.id, selected?.transcript, keyboardVisible, state.linkTip) {
        actions.linkTipMoment(keyboardVisible)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact =
            maxHeight - with(LocalDensity.current) { keyboard.toDp() } < LOCAL_TERMINAL_COMPACT
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.libraryTerminalTitle),
                    subtitle =
                        if (compact) null
                        else
                            state.devServerPort?.let {
                                stringResource(Res.string.terminalNavDevServer)
                            },
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                    more = { _ ->
                        KitMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            items =
                                buildList {
                                    add(
                                        KitMenuItem(
                                            stringResource(Res.string.localTerminalNewShell),
                                            actions::create,
                                            KitIcons.add,
                                            enabled = held,
                                        )
                                    )
                                    if (state.devServerPort != null)
                                        add(
                                            KitMenuItem(
                                                stringResource(Res.string.terminalNavOpenPreview),
                                                actions::openDevPreview,
                                            )
                                        )
                                    if (selected != null) {
                                        add(
                                            KitMenuItem(
                                                stringResource(
                                                    Res.string.terminalScreenReadableMode
                                                ),
                                                { actions.setAccessible(state.accessible != true) },
                                                checked = state.accessible == true,
                                            )
                                        )
                                        add(
                                            KitMenuItem(
                                                stringResource(
                                                    if (selected.running)
                                                        Res.string.localTerminalStopShell
                                                    else Res.string.localTerminalCloseShell
                                                ),
                                                if (selected.running) actions::stop
                                                else actions::close,
                                                if (selected.running) KitIcons.stop
                                                else KitIcons.close,
                                                destructive = true,
                                                enabled = held,
                                            )
                                        )
                                    }
                                    if (nativeActions != null)
                                        for (server in state.servers) {
                                            val name =
                                                server.name.trim().ifBlank {
                                                    stringResource(Res.string.terminalSourceServer)
                                                }
                                            add(
                                                KitMenuItem(
                                                    name,
                                                    { nativeActions.server(server.id) },
                                                    KitIcons.server,
                                                )
                                            )
                                        }
                                },
                            anchor = {
                                KitIconButton(
                                    KitIcons.more,
                                    stringResource(Res.string.kitMore),
                                    { menu = true },
                                )
                            },
                        )
                    },
                )
            },
            header =
                if (!compact && state.shells.size > 1)
                    ({
                        KitSessionTabs(
                            state.shells.map { shell ->
                                KitSessionTab(
                                    stringResource(Res.string.localTerminalShellName, shell.number),
                                    shell.id == state.selected,
                                    { actions.select(shell.id) },
                                    ended = !shell.running,
                                )
                            },
                            label = stringResource(Res.string.terminalSessionsLabel),
                            endedLabel = stringResource(Res.string.terminalSessionEnded),
                            modifier = Modifier.padding(bottom = Space.S1.dp),
                        )
                    })
                else null,
            page = true,
            width = KitScreenWidth.Full,
            loading = state.working,
            loadingLabel = stringResource(Res.string.localTerminalStarting),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.failed) {
                    KitStateView(
                        KitStateKind.Error,
                        stringResource(
                            if (state.notInstalled) Res.string.localTerminalNotSetUpTitle
                            else Res.string.localTerminalFailedTitle
                        ),
                        body =
                            stringResource(
                                if (state.notInstalled) Res.string.localTerminalNotSetUpBody
                                else Res.string.localTerminalFailedBody
                            ),
                        primary =
                            KitAction(
                                stringResource(Res.string.localTerminalTryAgain),
                                actions::create,
                            ),
                    )
                } else if (selected != null) {
                    KitTip(
                        message = stringResource(Res.string.tipTerminalLinks),
                        visible = state.linkTip && !keyboardVisible,
                        onDismissed = actions::dismissLinkTip,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        anchor = {
                            KitTerminalView(
                                platformView = selected.view.native,
                                title =
                                    stringResource(
                                        Res.string.localTerminalShellName,
                                        selected.number,
                                    ),
                                transcript = selected.transcript,
                                accessibleMode = state.accessible,
                                onAccessibleModeChange = actions::setAccessible,
                                onSend = actions::send,
                                inputEnabled = selected.running,
                                defaultKeyBar = nativeActions == null && selected.running,
                                showAccessibleSwitch = false,
                                modifier = Modifier.fillMaxSize(),
                                words =
                                    KitTerminalWords(
                                        terminal = stringResource(Res.string.libraryTerminalTitle),
                                        accessibleMode =
                                            stringResource(Res.string.terminalScreenReadableMode),
                                        transcript = stringResource(Res.string.e7SetupTranscript),
                                        noOutput = stringResource(Res.string.e7SetupNoOutput),
                                        sendToTerminal =
                                            stringResource(Res.string.e7SetupCommandInput),
                                        send = stringResource(Res.string.e7SetupSendCommand),
                                        inputUnavailable =
                                            stringResource(Res.string.e7SetupInputUnavailable),
                                    ),
                            )
                        },
                    )
                    if (selected.running && nativeActions != null)
                        TerminalKeyBar(
                            controller = keys,
                            singleRow = true, // F: lib/ui/kit/kit_terminal_view.dart:371
                            compact = compact,
                            safeArea = true,
                            onKey = { key, _, _ ->
                                when (key) {
                                    KitTerminalBarKey.Interrupt,
                                    KitTerminalBarKey.EndOfInput ->
                                        terminalBarKeyInput(key)?.let(actions::send)
                                    else -> nativeActions.key(localTerminalKey(key))
                                }
                            },
                        )
                    if (!selected.running)
                        KitInlineState(
                            title = stringResource(Res.string.localTerminalEndedTitle),
                            icon = KitIcons.terminal,
                            body =
                                stringResource(
                                    Res.string.localTerminalEndedBody,
                                    selected.exitCode ?: -1,
                                ),
                            primary =
                                KitAction(
                                    stringResource(Res.string.localTerminalRestart),
                                    actions::restart,
                                ),
                        )
                }
            }
            // The confirmation shares this page's modal host. Closing the page never stops a shell.
            KitConfirm(
                state.confirmStop?.let {
                    KitConfirmSpec(
                        title =
                            stringResource(
                                Res.string.localTerminalStopNamedTitle,
                                stringResource(
                                    Res.string.localTerminalShellName,
                                    state.shells.firstOrNull { shell -> shell.id == it }?.number
                                        ?: it,
                                ),
                            ),
                        body = stringResource(Res.string.localTerminalStopBody),
                        confirmLabel = stringResource(Res.string.localTerminalStop),
                        kind = KitConfirmKind.Stop,
                    )
                },
                actions::confirmStop,
                actions::dismissStop,
            )
        }
    }
}

internal fun localTerminalKey(key: KitTerminalBarKey): TerminalKey =
    when (key) {
        KitTerminalBarKey.Ctrl -> TerminalKey.Control
        KitTerminalBarKey.Alt -> TerminalKey.Alt
        KitTerminalBarKey.Esc -> TerminalKey.Escape
        KitTerminalBarKey.Tab -> TerminalKey.Tab
        KitTerminalBarKey.Slash -> TerminalKey.Slash
        KitTerminalBarKey.Dash -> TerminalKey.Minus
        KitTerminalBarKey.Pipe -> TerminalKey.Pipe
        KitTerminalBarKey.Tilde -> TerminalKey.Tilde
        KitTerminalBarKey.Home -> TerminalKey.Home
        KitTerminalBarKey.Up -> TerminalKey.Up
        KitTerminalBarKey.End -> TerminalKey.End
        KitTerminalBarKey.PageUp -> TerminalKey.PageUp
        KitTerminalBarKey.Left -> TerminalKey.Left
        KitTerminalBarKey.Down -> TerminalKey.Down
        KitTerminalBarKey.Right -> TerminalKey.Right
        KitTerminalBarKey.PageDown -> TerminalKey.PageDown
        KitTerminalBarKey.Interrupt,
        KitTerminalBarKey.EndOfInput -> error("Interrupts use the text input path")
    }
