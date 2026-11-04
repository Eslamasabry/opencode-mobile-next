package io.github.eslamasabry.oc.feature.terminal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalNativeActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalKey
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalKeyBar
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** F15 local shells. The only terminal surface is the kit's platform slot / accessible terminal. */
@Composable
public fun LocalTerminalScreen(state: LocalTerminalUi, actions: LocalTerminalActions) {
    val nativeActions = actions as? LocalTerminalNativeActions
    val selected = state.shells.firstOrNull { it.id == state.selected }
    val enabled =
        if (state.working) KitEnabled.Disabled(stringResource(Res.string.localTerminalStarting))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.libraryTerminalTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.localTerminalNewShell),
                            actions::create,
                        )
                    ),
            )
        },
        page = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            val builtIn = stringResource(Res.string.terminalSourceBuiltIn)
            val unnamed = stringResource(Res.string.terminalSourceServer)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                KitText(builtIn)
                state.servers
                    .takeIf { nativeActions != null }
                    .orEmpty()
                    .forEach { server ->
                        val name = server.name.trim().ifBlank { unnamed }
                        val label =
                            if (name == builtIn)
                                stringResource(Res.string.terminalSourceServerRuntime, name)
                            else name
                        KitButton(
                            label,
                            { nativeActions?.server(server.id) },
                            role = KitButtonRole.Secondary,
                        )
                    }
            }
            if (state.working)
                KitStateView(KitStateKind.Working, stringResource(Res.string.localTerminalStarting))
            if (state.failed)
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
            if (state.failed && !state.notInstalled)
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes = listOf("terminal-start"),
                )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.shells.forEach { shell ->
                    KitButton(
                        label = stringResource(Res.string.localTerminalShellName, shell.number),
                        onClick = { actions.select(shell.id) },
                        role =
                            if (shell.id == state.selected) KitButtonRole.Primary
                            else KitButtonRole.Tertiary,
                    )
                }
            }
            if (selected != null) {
                if (!selected.running)
                    KitNotice(
                        title = stringResource(Res.string.localTerminalEndedTitle),
                        message =
                            stringResource(
                                Res.string.localTerminalEndedBody,
                                selected.exitCode ?: -1,
                            ),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.localTerminalRestart),
                                    actions::restart,
                                )
                            ),
                    )
                KitTerminalView(
                    platformView = selected.view.native,
                    title = stringResource(Res.string.localTerminalShellName, selected.number),
                    transcript = selected.transcript,
                    accessibleMode = state.accessible,
                    onAccessibleModeChange = actions::setAccessible,
                    onSend = actions::send,
                    inputEnabled = selected.running,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    words =
                        KitTerminalWords(
                            terminal = stringResource(Res.string.libraryTerminalTitle),
                            accessibleMode = stringResource(Res.string.terminalScreenReadableMode),
                            transcript = stringResource(Res.string.e7SetupTranscript),
                            noOutput = stringResource(Res.string.e7SetupNoOutput),
                            sendToTerminal = stringResource(Res.string.e7SetupCommandInput),
                            send = stringResource(Res.string.e7SetupSendCommand),
                            inputUnavailable = stringResource(Res.string.e7SetupInputUnavailable),
                        ),
                )
                if (nativeActions != null)
                    KitTerminalKeyBar(
                        keys =
                            TerminalKey.entries.map { key ->
                                val label =
                                    stringResource(
                                        when (key) {
                                            TerminalKey.Control -> Res.string.terminalKeyControl
                                            TerminalKey.Alt -> Res.string.terminalKeyAlt
                                            TerminalKey.Escape -> Res.string.terminalKeyEscape
                                            TerminalKey.Tab -> Res.string.terminalKeyTab
                                            TerminalKey.Up -> Res.string.terminalKeyUp
                                            TerminalKey.Down -> Res.string.terminalKeyDown
                                            TerminalKey.Left -> Res.string.terminalKeyLeft
                                            TerminalKey.Right -> Res.string.terminalKeyRight
                                            TerminalKey.Home -> Res.string.terminalKeyHome
                                            TerminalKey.End -> Res.string.terminalKeyEnd
                                            TerminalKey.PageUp -> Res.string.terminalKeyPageUp
                                            TerminalKey.PageDown -> Res.string.terminalKeyPageDown
                                            TerminalKey.Pipe -> Res.string.terminalKeyPipe
                                            TerminalKey.Slash -> Res.string.terminalKeySlash
                                            TerminalKey.Backslash -> Res.string.terminalKeyBackslash
                                            TerminalKey.Tilde -> Res.string.terminalKeyTilde
                                            TerminalKey.Minus -> Res.string.terminalKeyMinus
                                            TerminalKey.Underscore ->
                                                Res.string.terminalKeyUnderscore
                                        }
                                    )
                                KitTerminalKey(
                                    label,
                                    when (key) {
                                        TerminalKey.Control -> selected.control
                                        TerminalKey.Alt -> selected.alt
                                        else -> null
                                    },
                                ) {
                                    nativeActions?.key(key)
                                }
                            },
                        enabled =
                            if (selected.running) KitEnabled.Enabled
                            else
                                KitEnabled.Disabled(
                                    stringResource(Res.string.localTerminalEndedTitle)
                                ),
                    )
                if (selected.running)
                    KitButton(
                        stringResource(Res.string.localTerminalStopShell),
                        actions::stop,
                        enabled = enabled,
                    )
                else
                    KitButton(
                        stringResource(Res.string.localTerminalCloseShell),
                        actions::close,
                        enabled = enabled,
                        role = KitButtonRole.Tertiary,
                    )
            }
        }
    }
    KitConfirm(
        spec =
            state.confirmStop?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.localTerminalStopShell),
                    body = stringResource(Res.string.localTerminalStopBody),
                    confirmLabel = stringResource(Res.string.localTerminalStop),
                    kind = KitConfirmKind.Destructive,
                )
            },
        onConfirm = actions::confirmStop,
        onCancel = actions::dismissStop,
    )
}
