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
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentView
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
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
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsHelpFilesBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpFilesTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpInstallBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpInstallTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpKeysBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpKeysTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpNeedsBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpNeedsTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpNotBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpNotTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpWhatBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpWhatTitle
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
import org.jetbrains.compose.resources.StringResource
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

/** The sheet's form state, kept per open/agent key and never in saved instance state. */
private class OwnAgentForm(agent: OwnAgentView?) {
    val name = TextFieldState(agent?.name.orEmpty())
    val start = TextFieldState(agent?.start.orEmpty())
    val install = TextFieldState(agent?.install.orEmpty())
    val env = mutableStateListOf<EnvLine>().apply { agent?.envNames?.forEach { add(EnvLine(it, saved = true)) } }
}

/**
 * "Your own agent": name, start command, install command and environment values; Save, then Check (starts it and asks
 * it to answer as an ACP agent) and Install when an install command is given. Kit parts only.
 */
@Composable
internal fun OwnAgentSheet(state: OwnAgentsUi, actions: OwnAgentActions) {
    val agent = state.editingId?.let { id -> state.agents.firstOrNull { it.id == id } }
    // A fresh form for each agent opened (and for "add"); the saved agent keeps the same form after its first save.
    val formKey = state.sheetOpen to (agent?.id ?: "new")
    // Never rememberSaveable: values must not enter saved instance state.
    val form = remember(formKey) { OwnAgentForm(agent) }
    LaunchedEffect(formKey) {
        snapshotFlow {
            listOf(
                form.name.text.toString(),
                form.start.text.toString(),
                form.install.text.toString(),
                form.env.size.toString(),
            )
        }
            .collect { actions.editedOwnAgent() }
    }
    val enabled =
        if (state.working) KitEnabled.Disabled(stringResource(Res.string.serversWorking)) else KitEnabled.Enabled
    val title = agent?.name ?: stringResource(Res.string.agentsOwnAgentTitleNew)
    val save = {
        val env =
            form.env
                .filter { it.name.text.isNotBlank() }
                .map { it.name.text.toString().trim() to it.value.text.toString() }
        actions.saveOwnAgent(
            form.name.text.toString(),
            form.start.text.toString(),
            form.install.text.toString(),
            env,
        )
    }
    KitSheet(
        visible = state.sheetOpen,
        onDismissRequest = actions::closeOwnAgent,
        title = title,
        icon = KitIcons.terminal,
        height = KitSheetHeight.Full,
        dismissible = !state.working,
        primary =
            KitAction(
                stringResource(Res.string.agentsOwnAgentSave),
                save,
                enabled = enabled,
                working = state.working && !state.checking && !state.installing,
            ),
        secondary = KitAction(stringResource(Res.string.serversCancel), actions::closeOwnAgent, enabled = enabled),
        tertiary = ownAgentTertiary(state, agent, actions, enabled),
    ) {
        OwnAgentSheetBody(state, form, formKey, agent?.name ?: title, enabled)
    }
}

@Composable
private fun ownAgentTertiary(
    state: OwnAgentsUi,
    agent: OwnAgentView?,
    actions: OwnAgentActions,
    enabled: KitEnabled,
): List<KitAction> {
    if (agent == null) return emptyList()
    return buildList {
        add(
            KitAction(
                stringResource(Res.string.agentsOwnAgentCheck, agent.name),
                actions::checkOwnAgent,
                enabled = enabled,
                working = state.checking,
            )
        )
        if (agent.install != null) {
            add(
                KitAction(
                    stringResource(Res.string.agentsOwnAgentInstallAction, agent.name),
                    actions::installOwnAgent,
                    enabled = enabled,
                    working = state.installing,
                )
            )
        }
        add(
            KitAction(
                stringResource(Res.string.agentsOwnAgentRemove, agent.name),
                actions::removeOwnAgent,
                enabled = enabled,
                destructive = true,
            )
        )
    }
}

@Composable
private fun OwnAgentSheetBody(
    state: OwnAgentsUi,
    form: OwnAgentForm,
    formKey: Any,
    who: String,
    enabled: KitEnabled,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        val problem = state.problem
        if (problem == OwnAgentProblem.TooMany) KitErrorNotice(stringResource(Res.string.agentsOwnAgentTooMany))
        KitField(
            form.name,
            stringResource(Res.string.agentsOwnAgentName),
            error = if (problem == OwnAgentProblem.Name) stringResource(Res.string.agentsOwnAgentBadName) else null,
            maxLength = 40,
            enabled = enabled,
        )
        KitField(
            form.start,
            stringResource(Res.string.agentsOwnAgentStart),
            kind = KitFieldKind.Mono,
            helper = stringResource(Res.string.agentsOwnAgentStartHint),
            error = if (problem == OwnAgentProblem.Start) stringResource(Res.string.agentsOwnAgentBadStart) else null,
            enabled = enabled,
        )
        OwnAgentHelpFold(Res.string.agentsHelpWhatTitle, Res.string.agentsHelpWhatBody, formKey)
        OwnAgentHelpFold(Res.string.agentsHelpNotTitle, Res.string.agentsHelpNotBody, formKey)
        KitField(
            form.install,
            stringResource(Res.string.agentsOwnAgentInstall),
            kind = KitFieldKind.Mono,
            helper = stringResource(Res.string.agentsOwnAgentInstallHint),
            error =
                if (problem == OwnAgentProblem.Install) stringResource(Res.string.agentsOwnAgentBadInstall) else null,
            enabled = enabled,
        )
        OwnAgentHelpFold(Res.string.agentsHelpInstallTitle, Res.string.agentsHelpInstallBody, formKey)
        OwnAgentEnvSection(problem, form.env, formKey, enabled)
        OwnAgentStatusSection(state, who)
        OwnAgentHelpDetails(formKey)
    }
}

@Composable
private fun OwnAgentEnvSection(
    problem: OwnAgentProblem?,
    env: MutableList<EnvLine>,
    formKey: Any,
    enabled: KitEnabled,
) {
    KitSectionLabel(
        stringResource(Res.string.agentsOwnAgentEnvTitle),
        explanation = stringResource(Res.string.agentsOwnAgentEnvHint),
        action =
            KitLineAction(
                stringResource(Res.string.agentsOwnAgentEnvAdd),
                { env.add(EnvLine("", saved = false)) },
                enabled = enabled.isEnabled,
            ),
    )
    OwnAgentHelpFold(Res.string.agentsHelpKeysTitle, Res.string.agentsHelpKeysBody, formKey)
    if (problem == OwnAgentProblem.EnvName) KitErrorNotice(stringResource(Res.string.agentsOwnAgentBadEnvName))
    if (problem == OwnAgentProblem.EnvValue) KitErrorNotice(stringResource(Res.string.agentsOwnAgentBadEnvValue))
    for (line in env) {
        KitField(
            line.name,
            stringResource(Res.string.agentsOwnAgentEnvName),
            kind = KitFieldKind.Mono,
            enabled = enabled,
        )
        KitField(
            line.value,
            stringResource(Res.string.agentsOwnAgentEnvValue),
            kind = KitFieldKind.Secret,
            secretPresence = if (line.replacing) SecretFieldPresence.Editing else SecretFieldPresence.Saved,
            onReplaceSecret = { line.replacing = true },
            enabled = enabled,
        )
    }
}

@Composable
private fun OwnAgentStatusSection(state: OwnAgentsUi, who: String) {
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

@Composable
private fun OwnAgentHelpFold(title: StringResource, body: StringResource, resetKey: Any) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    KitExpandRow(stringResource(title), expanded, onExpandedChange = { expanded = it }, inset = false) {
        KitText(stringResource(body), tone = TextTone.Secondary)
    }
}

@Composable
private fun OwnAgentHelpDetails(resetKey: Any) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    KitDetailsFold(
        label = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        expanded = expanded,
        onExpansionChanged = { expanded = it },
        child = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                HelpPart(
                    stringResource(Res.string.agentsHelpFilesTitle),
                    stringResource(Res.string.agentsHelpFilesBody),
                )
                HelpPart(
                    stringResource(Res.string.agentsHelpNeedsTitle),
                    stringResource(Res.string.agentsHelpNeedsBody),
                )
            }
        },
    )
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
