package io.github.eslamasabry.oc.feature.git.servertools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The server's project tools remain visible even when the current folder has no Git repository. */
@Composable
public fun GitServerToolsPanel(state: GitServerToolsUi, actions: GitServerToolsActions) {
    if (!state.available) return
    KitRow(
        title = stringResource(Res.string.protoProjectTools),
        supporting = state.directory,
        trailing = KitRowTrailing.Chevron,
        onClick = actions::show,
    )
    KitSheet(
        visible = state.open,
        onDismissRequest = actions::close,
        title = stringResource(Res.string.protoProjectTools),
    ) {
        val name = rememberTextFieldState()
        val held =
            if (state.busy) KitEnabled.Disabled(stringResource(Res.string.protoWorking))
            else KitEnabled.Enabled
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            state.directory?.let { KitText(it) }
            if (state.failed) KitNotice(stringResource(Res.string.protoGitToolsFailed))
            state.result?.let {
                KitNotice(
                    stringResource(
                        when (it) {
                            ServerToolsResult.Initialized -> Res.string.protoGitInitialized
                            ServerToolsResult.WorktreeCreated -> Res.string.protoWorktreeCreated
                            ServerToolsResult.ShellSaved -> Res.string.protoShellSaved
                        }
                    )
                )
            }
            state.createdDirectory?.let { KitText(it) }
            if (state.gitInit)
                KitButton(
                    stringResource(Res.string.protoGitInitialize),
                    actions::initialize,
                    enabled = held,
                )
            if (state.worktreeCreate) {
                if (state.invalidName) KitNotice(stringResource(Res.string.protoWorktreeInvalid))
                KitField(name, stringResource(Res.string.protoWorktreeName), enabled = held)
                KitButton(
                    stringResource(Res.string.protoWorktreeCreate),
                    { actions.createWorktree(name.text.toString()) },
                    enabled = held,
                )
            }
            if (state.shellSettings) {
                KitRowGroup(label = stringResource(Res.string.protoShellTitle)) {
                    KitRow(
                        title = stringResource(Res.string.protoShellDefault),
                        selected = state.shells?.selected?.isEmpty() == true,
                        enabled = !state.busy,
                        disabledReason = stringResource(Res.string.protoWorking),
                        onClick = { actions.selectShell("") },
                    )
                    for (shell in state.shells?.options.orEmpty()) {
                        KitRow(
                            title = shell.name,
                            supporting = shell.path,
                            selected = shell.path == state.shells?.selected,
                            enabled = !state.busy && shell.acceptable,
                            disabledReason =
                                stringResource(
                                    if (state.busy) Res.string.protoWorking
                                    else Res.string.protoShellUnavailable
                                ),
                            onClick = { actions.selectShell(shell.path) },
                        )
                    }
                }
                if (state.shells == null && !state.busy)
                    KitButton(stringResource(Res.string.commonRetry), actions::readShells)
            }
        }
    }
}
