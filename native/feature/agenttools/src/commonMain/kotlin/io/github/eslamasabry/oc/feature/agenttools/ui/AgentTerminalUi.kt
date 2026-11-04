package io.github.eslamasabry.oc.feature.agenttools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentTerminalActions
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentTerminalUi
import io.github.eslamasabry.oc.feature.agenttools.presentation.validTerminalName
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalWords
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AgentTerminalRows(state: AgentTerminalUi, actions: AgentTerminalActions) {
    var menu by remember { mutableStateOf<String?>(null) }
    val working = stringResource(Res.string.agentToolsWorking)
    val disabled =
        if (state.busy || state.uncertain)
            KitEnabled.Disabled(
                if (state.uncertain) stringResource(Res.string.agentToolsUncertain) else working
            )
        else KitEnabled.Enabled
    if (state.listed && state.terminals.isEmpty() && state.failure == null)
        KitText(stringResource(Res.string.agentToolsNoTerminals))
    for (terminal in state.terminals) KitMenu(
        expanded = menu == terminal.id,
        title = terminal.title,
        onDismissRequest = { menu = null },
        items =
            listOf(
                KitMenuItem(
                    stringResource(Res.string.agentToolsRenameTerminal),
                    {
                        menu = null
                        actions.askRenameTerminal(terminal.id)
                    },
                    enabled = disabled,
                ),
                KitMenuItem(
                    stringResource(Res.string.agentToolsCloseTerminal),
                    {
                        menu = null
                        actions.askCloseTerminal(terminal.id)
                    },
                    destructive = true,
                    enabled = disabled,
                ),
            ),
    ) {
        KitRow(
            title = terminal.title,
            supporting = terminal.directory,
            trailing =
                KitRowTrailing.Action(
                    KitLineAction(
                        stringResource(Res.string.agentToolsTerminalMore),
                        { menu = terminal.id },
                        enabled = !state.busy,
                        disabledReason = working,
                    )
                ),
            onClick = { actions.openTerminal(terminal.id) },
            enabled = !state.busy,
            disabledReason = working,
        )
    }
    state.failure?.let { failure ->
        KitErrorNotice(
            message = stringResource(Res.string.agentToolsTerminalFailed),
            failure = failure,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            retry =
                KitLineAction(
                    stringResource(Res.string.agentToolsReadTerminals),
                    actions::listTerminals,
                ),
        )
    }
    if (state.uncertain) KitNotice(stringResource(Res.string.agentToolsUncertain))
}

@Composable
internal fun AgentTerminalDialogs(state: AgentTerminalUi, actions: AgentTerminalActions) {
    val invalid = stringResource(Res.string.agentToolsTerminalNameInvalid)
    KitInputDialog(
        visible = state.askingCreate,
        title = stringResource(Res.string.agentToolsNewTerminal),
        fieldLabel = stringResource(Res.string.agentToolsTerminalName),
        confirmLabel = stringResource(Res.string.agentToolsOpenTerminal),
        operationKey = "new-remote-terminal",
        validate = { if (validTerminalName(it)) null else invalid },
        onResult = actions::finishTerminalName,
    )
    KitInputDialog(
        visible = state.rename != null,
        title = stringResource(Res.string.agentToolsRenameTerminal),
        fieldLabel = stringResource(Res.string.agentToolsTerminalName),
        confirmLabel = stringResource(Res.string.agentToolsRenameTerminal),
        initial = state.rename?.title.orEmpty(),
        operationKey = state.rename?.id,
        validate = { if (validTerminalName(it)) null else invalid },
        onResult = actions::finishTerminalRename,
    )
    KitConfirm(
        spec =
            state.close?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.agentToolsCloseTerminalQuestion, it.title),
                    confirmLabel = stringResource(Res.string.agentToolsCloseTerminal),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.agentToolsCloseTerminalBody),
                )
            },
        onConfirm = actions::confirmCloseTerminal,
        onCancel = actions::cancelCloseTerminal,
    )
}

/** A live Termux TerminalView slot and its built-in accessible transcript/input mode. */
@Composable
internal fun AgentTerminalScreen(state: AgentTerminalUi, actions: AgentTerminalActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = state.title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::leaveTerminal,
            )
        },
        width = KitScreenWidth.Full,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitNotice(stringResource(Res.string.agentToolsTerminalViewBody))
            state.failure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.agentToolsTerminalFailed),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
            KitTerminalView(
                platformView = state.view?.native,
                title = state.title,
                transcript = state.transcript,
                accessibleMode = state.accessibleMode,
                onAccessibleModeChange = actions::terminalAccessible,
                onSend = actions::sendTerminal,
                words =
                    KitTerminalWords(
                        terminal = stringResource(Res.string.agentToolsTerminal),
                        accessibleMode = stringResource(Res.string.agentToolsTerminalAccessible),
                        transcript = stringResource(Res.string.agentToolsTerminalTranscript),
                        noOutput = stringResource(Res.string.agentToolsTerminalNoOutput),
                        sendToTerminal = stringResource(Res.string.agentToolsTerminalSendTo),
                        send = stringResource(Res.string.agentToolsTerminalSend),
                        inputUnavailable = stringResource(Res.string.agentToolsTerminalInputOff),
                    ),
                modifier = Modifier.weight(1f),
                inputEnabled = state.inputEnabled,
            )
            if (state.inputEnabled)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitButton(
                        stringResource(Res.string.agentToolsTerminalInterrupt),
                        { actions.sendTerminal("\u0003") },
                        role = KitButtonRole.Tertiary,
                    )
                    KitButton(
                        stringResource(Res.string.agentToolsTerminalTab),
                        { actions.sendTerminal("\t") },
                        role = KitButtonRole.Tertiary,
                    )
                    KitButton(
                        stringResource(Res.string.agentToolsTerminalEscape),
                        { actions.sendTerminal("\u001b") },
                        role = KitButtonRole.Tertiary,
                    )
                }
            else if (!state.busy)
                KitButton(
                    stringResource(Res.string.agentToolsTerminalReconnect),
                    { state.liveId?.let(actions::openTerminal) },
                    role = KitButtonRole.Secondary,
                )
        }
    }
}
