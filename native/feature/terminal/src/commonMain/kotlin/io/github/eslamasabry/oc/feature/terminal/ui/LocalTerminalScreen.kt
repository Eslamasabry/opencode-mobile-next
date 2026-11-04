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
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalActions
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
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
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SetupCommandInput
import io.github.eslamasabry.oc.ui.words.res.e7SetupInputUnavailable
import io.github.eslamasabry.oc.ui.words.res.e7SetupNoOutput
import io.github.eslamasabry.oc.ui.words.res.e7SetupSendCommand
import io.github.eslamasabry.oc.ui.words.res.e7SetupTranscript
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.localTerminalCloseShell
import io.github.eslamasabry.oc.ui.words.res.localTerminalEndedBody
import io.github.eslamasabry.oc.ui.words.res.localTerminalEndedTitle
import io.github.eslamasabry.oc.ui.words.res.localTerminalFailedBody
import io.github.eslamasabry.oc.ui.words.res.localTerminalFailedTitle
import io.github.eslamasabry.oc.ui.words.res.localTerminalKeyCtrl
import io.github.eslamasabry.oc.ui.words.res.localTerminalNewShell
import io.github.eslamasabry.oc.ui.words.res.localTerminalNotSetUpBody
import io.github.eslamasabry.oc.ui.words.res.localTerminalNotSetUpTitle
import io.github.eslamasabry.oc.ui.words.res.localTerminalRestart
import io.github.eslamasabry.oc.ui.words.res.localTerminalShellName
import io.github.eslamasabry.oc.ui.words.res.localTerminalStarting
import io.github.eslamasabry.oc.ui.words.res.localTerminalStop
import io.github.eslamasabry.oc.ui.words.res.localTerminalStopBody
import io.github.eslamasabry.oc.ui.words.res.localTerminalStopShell
import io.github.eslamasabry.oc.ui.words.res.localTerminalTryAgain
import io.github.eslamasabry.oc.ui.words.res.terminalNavDevServer
import io.github.eslamasabry.oc.ui.words.res.terminalNavOpenPreview
import io.github.eslamasabry.oc.ui.words.res.terminalScreenReadableMode
import org.jetbrains.compose.resources.stringResource

/** F15 local shells. The only terminal surface is the kit's platform slot / accessible terminal. */
@Composable
public fun LocalTerminalScreen(state: LocalTerminalUi, actions: LocalTerminalActions) {
    val selected = state.shells.firstOrNull { it.id == state.selected }
    val enabled =
        if (state.working) KitEnabled.Disabled(stringResource(Res.string.localTerminalStarting))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.libraryTerminalTitle),
                subtitle =
                    state.devServerPort?.let { stringResource(Res.string.terminalNavDevServer) },
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOfNotNull(
                        state.devServerPort?.let {
                            KitLineAction(
                                stringResource(Res.string.terminalNavOpenPreview),
                                actions::openDevPreview,
                            )
                        },
                        KitLineAction(
                            stringResource(Res.string.localTerminalNewShell),
                            actions::create,
                        ),
                    ),
            )
        },
        page = true,
    ) {
        Column(Modifier.fillMaxSize()) {
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
                KitSwitchRow(
                    title = stringResource(Res.string.localTerminalKeyCtrl),
                    checked = selected.control,
                    onCheckedChange = actions::setControl,
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
