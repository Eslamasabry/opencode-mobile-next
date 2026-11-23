package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentCheckResult
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentProblem
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentActions
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentPickup
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderActions
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.SecretFieldPresence
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsHelpTitle
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentAdd
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentAddBody
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentAnswered
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentBadEnvName
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentBadEnvValue
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentBadInstall
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentBadName
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentBadStart
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentCheck
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentChecking
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentCouldNotRun
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentEnvAdd
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentEnvHint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentEnvName
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentEnvTitle
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentEnvValue
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentFailed
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentInstall
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentInstallAction
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentInstallFailed
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentInstallHint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentInstalled
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentInstalling
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentName
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentNoAnswer
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentNotAcp
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentNotFound
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentReadyLater
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentReadyNow
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentRemove
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentRowBody
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentSave
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentStart
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentStartHint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentStopped
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentTitleNew
import io.github.eslamasabry.oc.ui.words.res.agentsOwnAgentTooMany
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.serversCancel
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import org.jetbrains.compose.resources.stringResource

/** The person's own agents as rows of the page's own kind, then "Add your own agent" (issue 147). */
@Composable
internal fun OwnAgentRows(state: OwnAgentsUi, actions: OwnAgentActions, modifier: Modifier = Modifier) {
    for (agent in state.agents) {
        KitRow(
            title = agent.name,
            modifier = modifier,
            supporting = stringResource(Res.string.agentsOwnAgentRowBody, agent.start),
            leading = KitRowLeading.Glyph(KitIcons.terminal),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.editOwnAgent(agent.id) },
        )
    }
    KitRow(
        title = stringResource(Res.string.agentsOwnAgentAdd),
        modifier = modifier,
        supporting = stringResource(Res.string.agentsOwnAgentAddBody),
        leading = KitRowLeading.Glyph(KitIcons.add),
        trailing = KitRowTrailing.Chevron,
        onClick = actions::addOwnAgent,
    )
}

/** One environment line of the sheet: a name and a secret value (never prefilled; a saved one shows Saved · Replace). */
private class EnvLine(name: String, val saved: Boolean) {
    val name = TextFieldState(name)
    val value = TextFieldState()
    var replacing by mutableStateOf(!saved)
}

/**
 * "Your own agent": name, start command, install command and environment values; Save, then Check (starts it and asks
 * it to answer as an ACP agent) and Install when an install command is given. Kit parts only.
 */
@Composable
internal fun OwnAgentSheet(state: OwnAgentsUi, actions: OwnAgentActions, help: OwnProviderActions?) {
    val agent = state.editingId?.let { id -> state.agents.firstOrNull { it.id == id } }
    // A fresh form for each agent opened (and for "add"); the saved agent keeps the same form after its first save.
    val formKey = state.sheetOpen to (agent?.id ?: "new")
    val name = remember(formKey) { TextFieldState(agent?.name.orEmpty()) }
    val start = remember(formKey) { TextFieldState(agent?.start.orEmpty()) }
    val install = remember(formKey) { TextFieldState(agent?.install.orEmpty()) }
    // Never rememberSaveable: values must not enter saved instance state.
    val env = remember(formKey) { mutableStateListOf<EnvLine>().apply { agent?.envNames?.forEach { add(EnvLine(it, saved = true)) } } }
    LaunchedEffect(formKey) {
        snapshotFlow { listOf(name.text.toString(), start.text.toString(), install.text.toString(), env.size.toString()) }
            .collect { actions.editedOwnAgent() }
    }
    val enabled = if (state.working) KitEnabled.Disabled(stringResource(Res.string.serversWorking)) else KitEnabled.Enabled
    val title = agent?.name ?: stringResource(Res.string.agentsOwnAgentTitleNew)
    val save = {
        actions.saveOwnAgent(
            name.text.toString(),
            start.text.toString(),
            install.text.toString(),
            env.filter { it.name.text.isNotBlank() }.map { it.name.text.toString().trim() to it.value.text.toString() },
        )
    }
    KitSheet(
        visible = state.sheetOpen,
        onDismissRequest = actions::closeOwnAgent,
        title = title,
        icon = KitIcons.terminal,
        height = KitSheetHeight.Full,
        dismissible = !state.working,
        primary = KitAction(stringResource(Res.string.agentsOwnAgentSave), save, enabled = enabled, working = state.working && !state.checking && !state.installing),
        secondary = KitAction(stringResource(Res.string.serversCancel), actions::closeOwnAgent, enabled = enabled),
        tertiary =
            buildList {
                if (agent != null) {
                    add(KitAction(stringResource(Res.string.agentsOwnAgentCheck, agent.name), actions::checkOwnAgent, enabled = enabled, working = state.checking))
                    if (agent.install != null)
                        add(KitAction(stringResource(Res.string.agentsOwnAgentInstallAction, agent.name), actions::installOwnAgent, enabled = enabled, working = state.installing))
                    add(KitAction(stringResource(Res.string.agentsOwnAgentRemove, agent.name), actions::removeOwnAgent, enabled = enabled, destructive = true))
                }
                if (help != null) add(KitAction(stringResource(Res.string.agentsHelpTitle), help::openAgentsHelp))
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            val problem = state.problem
            if (problem == OwnAgentProblem.TooMany) KitErrorNotice(stringResource(Res.string.agentsOwnAgentTooMany))
            KitField(
                name,
                stringResource(Res.string.agentsOwnAgentName),
                error = if (problem == OwnAgentProblem.Name) stringResource(Res.string.agentsOwnAgentBadName) else null,
                maxLength = 40,
                enabled = enabled,
            )
            KitField(
                start,
                stringResource(Res.string.agentsOwnAgentStart),
                kind = KitFieldKind.Mono,
                helper = stringResource(Res.string.agentsOwnAgentStartHint),
                error = if (problem == OwnAgentProblem.Start) stringResource(Res.string.agentsOwnAgentBadStart) else null,
                enabled = enabled,
            )
            KitField(
                install,
                stringResource(Res.string.agentsOwnAgentInstall),
                kind = KitFieldKind.Mono,
                helper = stringResource(Res.string.agentsOwnAgentInstallHint),
                error = if (problem == OwnAgentProblem.Install) stringResource(Res.string.agentsOwnAgentBadInstall) else null,
                enabled = enabled,
            )
            KitSectionLabel(
                stringResource(Res.string.agentsOwnAgentEnvTitle),
                explanation = stringResource(Res.string.agentsOwnAgentEnvHint),
                action = KitLineAction(stringResource(Res.string.agentsOwnAgentEnvAdd), { env.add(EnvLine("", saved = false)) }, enabled = enabled.isEnabled),
            )
            if (problem == OwnAgentProblem.EnvName) KitErrorNotice(stringResource(Res.string.agentsOwnAgentBadEnvName))
            if (problem == OwnAgentProblem.EnvValue) KitErrorNotice(stringResource(Res.string.agentsOwnAgentBadEnvValue))
            for (line in env) {
                KitField(line.name, stringResource(Res.string.agentsOwnAgentEnvName), kind = KitFieldKind.Mono, enabled = enabled)
                KitField(
                    line.value,
                    stringResource(Res.string.agentsOwnAgentEnvValue),
                    kind = KitFieldKind.Secret,
                    secretPresence = if (line.replacing) SecretFieldPresence.Editing else SecretFieldPresence.Saved,
                    onReplaceSecret = { line.replacing = true },
                    enabled = enabled,
                )
            }
            val who = agent?.name ?: title
            when {
                state.checking -> {
                    KitProgressView(KitProgress.Waiting())
                    KitText(stringResource(Res.string.agentsOwnAgentChecking))
                }
                state.installing -> {
                    KitProgressView(KitProgress.Waiting())
                    KitText(stringResource(Res.string.agentsOwnAgentInstalling))
                }
            }
            state.checked?.let { result ->
                if (result == OwnAgentCheckResult.Answered)
                    KitNotice(stringResource(Res.string.agentsOwnAgentAnswered, who), tone = StatusTone.Ok)
                else KitErrorNotice(checkWords(result, who))
            }
            // After a save: when the phone's agent helper takes it.
            when (state.pickup) {
                OwnAgentPickup.Ready -> KitNotice(stringResource(Res.string.agentsOwnAgentReadyNow), tone = StatusTone.Ok)
                OwnAgentPickup.WhenIdle -> KitNotice(stringResource(Res.string.agentsOwnAgentReadyLater))
                OwnAgentPickup.None -> Unit
            }
            state.installed?.let { ok ->
                if (ok) KitNotice(stringResource(Res.string.agentsOwnAgentInstalled), tone = StatusTone.Ok)
                else KitErrorNotice(stringResource(Res.string.agentsOwnAgentInstallFailed))
            }
            state.failure?.let { failure ->
                KitErrorNotice(
                    stringResource(Res.string.agentsOwnAgentFailed),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
        }
    }
}

@Composable
private fun checkWords(result: OwnAgentCheckResult, name: String): String =
    when (result) {
        OwnAgentCheckResult.Answered -> stringResource(Res.string.agentsOwnAgentAnswered, name)
        OwnAgentCheckResult.NoAnswer -> stringResource(Res.string.agentsOwnAgentNoAnswer, name)
        OwnAgentCheckResult.NotAcp -> stringResource(Res.string.agentsOwnAgentNotAcp, name)
        OwnAgentCheckResult.NotFound -> stringResource(Res.string.agentsOwnAgentNotFound)
        OwnAgentCheckResult.Stopped -> stringResource(Res.string.agentsOwnAgentStopped, name)
        OwnAgentCheckResult.CouldNotRun -> stringResource(Res.string.agentsOwnAgentCouldNotRun)
    }
