package io.github.eslamasabry.oc.feature.agents.remote_terminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A text terminal with visible input/control keys; every visual part belongs to the kit. */
@Composable
public fun RemoteTerminalScreen(state: RemoteTerminalUi, actions: RemoteTerminalActions) {
    val working = stringResource(Res.string.remoteTerminalWorking)
    val enabled = if (state.busy) KitEnabled.Disabled(working) else KitEnabled.Enabled
    val title =
        state.selected?.title?.takeIf { it.isNotBlank() }
            ?: stringResource(Res.string.remoteTerminalTitle)
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                subtitle =
                    state.devServerPort?.let { stringResource(Res.string.terminalNavDevServer) },
                actions =
                    state.devServerPort?.let {
                        listOf(
                            io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction(
                                stringResource(Res.string.terminalNavOpenPreview),
                                actions::openDevPreview,
                            )
                        )
                    } ?: emptyList(),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = state.busy,
        loadingLabel = working,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            state.issue?.let { issue ->
                KitNotice(
                    stringResource(
                        when (issue) {
                            RemoteTerminalIssue.Unavailable -> Res.string.remoteTerminalUnavailable
                            RemoteTerminalIssue.Failed -> Res.string.remoteTerminalFailed
                            RemoteTerminalIssue.InputUncertain ->
                                Res.string.remoteTerminalInputUncertain
                        }
                    )
                )
            }
            if (!state.available) {
                KitNotice(stringResource(Res.string.remoteTerminalUnavailable))
            } else if (state.selected == null) {
                KitText(stringResource(Res.string.remoteTerminalIntro))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitButton(
                        stringResource(Res.string.remoteTerminalCreate),
                        actions::create,
                        enabled = enabled,
                    )
                    KitButton(
                        stringResource(Res.string.remoteTerminalRefresh),
                        actions::refresh,
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                    )
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (state.terminals.isEmpty() && !state.busy)
                        KitText(stringResource(Res.string.remoteTerminalEmpty))
                    for (terminal in state.terminals) {
                        KitRow(
                            title =
                                terminal.title.takeIf { it.isNotBlank() }
                                    ?: stringResource(Res.string.remoteTerminalTitle),
                            supporting =
                                stringResource(
                                    if (terminal.running) Res.string.remoteTerminalRunning
                                    else Res.string.remoteTerminalEnded
                                ),
                            trailing = KitRowTrailing.Chevron,
                            enabled = !state.busy,
                            disabledReason = working,
                            onClick = { actions.connect(terminal.id) },
                        )
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitButton(
                        stringResource(Res.string.remoteTerminalList),
                        actions::showList,
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                    )
                    if (state.connected) {
                        KitButton(
                            stringResource(Res.string.remoteTerminalDisconnect),
                            actions::disconnect,
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                        )
                    } else {
                        KitButton(
                            stringResource(Res.string.remoteTerminalReconnect),
                            { actions.connect(state.selected.id) },
                            enabled = enabled,
                        )
                    }
                    KitButton(
                        stringResource(Res.string.remoteTerminalEnd),
                        actions::askEnd,
                        role = KitButtonRole.Tertiary,
                        destructive = true,
                        enabled = enabled,
                    )
                }
                if (!state.connected && !state.busy)
                    KitNotice(stringResource(Res.string.remoteTerminalDisconnected))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    val input =
                        if (state.connected && !state.busy) KitEnabled.Enabled
                        else KitEnabled.Disabled(stringResource(Res.string.remoteTerminalInputOff))
                    KitButton(
                        stringResource(Res.string.remoteTerminalInterrupt),
                        actions::interrupt,
                        role = KitButtonRole.Secondary,
                        enabled = input,
                    )
                    KitButton(
                        stringResource(Res.string.remoteTerminalEndInput),
                        actions::endOfInput,
                        role = KitButtonRole.Secondary,
                        enabled = input,
                    )
                    KitButton(
                        stringResource(Res.string.remoteTerminalEscape),
                        actions::escape,
                        role = KitButtonRole.Secondary,
                        enabled = input,
                    )
                    KitButton(
                        stringResource(Res.string.remoteTerminalTab),
                        actions::tab,
                        role = KitButtonRole.Secondary,
                        enabled = input,
                    )
                }
                KitTerminalView(
                    platformView = null,
                    title = title,
                    transcript = state.transcript,
                    accessibleMode = true,
                    onAccessibleModeChange = {},
                    onSend = actions::send,
                    inputEnabled = state.connected && !state.busy,
                    modifier = Modifier.weight(1f),
                    words =
                        KitTerminalWords(
                            terminal = stringResource(Res.string.remoteTerminalTitle),
                            accessibleMode = stringResource(Res.string.remoteTerminalReadable),
                            transcript = stringResource(Res.string.remoteTerminalTranscript),
                            noOutput = stringResource(Res.string.remoteTerminalNoOutput),
                            sendToTerminal = stringResource(Res.string.remoteTerminalInput),
                            send = stringResource(Res.string.remoteTerminalSend),
                            inputUnavailable = stringResource(Res.string.remoteTerminalInputOff),
                        ),
                )
            }
        }
        KitSheet(
            visible = state.ending,
            onDismissRequest = actions::cancelEnd,
            title = stringResource(Res.string.remoteTerminalEndQuestion),
            primary =
                KitAction(
                    stringResource(Res.string.remoteTerminalEnd),
                    actions::confirmEnd,
                    destructive = true,
                ),
            secondary =
                KitAction(stringResource(Res.string.remoteTerminalKeep), actions::cancelEnd),
        ) {
            KitText(stringResource(Res.string.remoteTerminalEndBody))
        }
    }
}
