package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderProblem
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderActions
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
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
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
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
import io.github.eslamasabry.oc.ui.words.res.agentsHelpProviderBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpProviderTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpRow
import io.github.eslamasabry.oc.ui.words.res.agentsHelpRowBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpTitle
import io.github.eslamasabry.oc.ui.words.res.agentsHelpWhatBody
import io.github.eslamasabry.oc.ui.words.res.agentsHelpWhatTitle
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderBadEndpoint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderBadKey
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderBadModel
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderBody
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderEndpoint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderEndpointHint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderFailed
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderKey
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderKeyHint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderModel
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderModelHint
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderRemove
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderSave
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderTitle
import io.github.eslamasabry.oc.ui.words.res.agentsOwnProviderUnsupported
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.serversCancel
import io.github.eslamasabry.oc.ui.words.res.serversWorking
import org.jetbrains.compose.resources.stringResource

/**
 * The phone tab's one row for issue 147 (a row of the page's own kind, under the agents, never a new level): what runs
 * on this phone and how to use an own provider, in [AgentsHelpSheet].
 */
@Composable
internal fun AgentsHelpRow(actions: OwnProviderActions, modifier: Modifier = Modifier) {
    KitRow(
        title = stringResource(Res.string.agentsHelpRow),
        modifier = modifier,
        supporting = stringResource(Res.string.agentsHelpRowBody),
        supportingMaxLines = 2,
        leading = KitRowLeading.Glyph(KitIcons.info),
        trailing = KitRowTrailing.Chevron,
        onClick = actions::openAgentsHelp,
    )
}

/** "How agents work on this phone": short, one title and one or two sentences per question the reporter asked. */
@Composable
internal fun AgentsHelpSheet(own: OwnProviderUi, actions: OwnProviderActions) {
    KitSheet(
        visible = own.helpOpen,
        onDismissRequest = actions::closeAgentsHelp,
        title = stringResource(Res.string.agentsHelpTitle),
        icon = KitIcons.info,
        height = KitSheetHeight.Half,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            HelpPart(stringResource(Res.string.agentsHelpWhatTitle), stringResource(Res.string.agentsHelpWhatBody))
            HelpPart(stringResource(Res.string.agentsHelpProviderTitle), stringResource(Res.string.agentsHelpProviderBody))
            HelpPart(stringResource(Res.string.agentsHelpInstallTitle), stringResource(Res.string.agentsHelpInstallBody))
            HelpPart(stringResource(Res.string.agentsHelpFilesTitle), stringResource(Res.string.agentsHelpFilesBody))
            HelpPart(stringResource(Res.string.agentsHelpKeysTitle), stringResource(Res.string.agentsHelpKeysBody))
            HelpPart(stringResource(Res.string.agentsHelpNeedsTitle), stringResource(Res.string.agentsHelpNeedsBody))
            HelpPart(stringResource(Res.string.agentsHelpNotTitle), stringResource(Res.string.agentsHelpNotBody))
        }
    }
}

/** One question of the help sheet: its title and one or two sentences. */
@Composable
internal fun HelpPart(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        KitText(title, role = TypeRole.RowTitle)
        KitText(body, tone = TextTone.Secondary)
    }
}

/**
 * "Your own provider for {agent}": endpoint, key and model. The key field is never prefilled: a saved key shows
 * "Saved · Replace", and saving without replacing it keeps the saved one. The typed key lives only in this field until
 * Save hands it to the port.
 */
@Composable
internal fun OwnProviderSheet(state: AgentsUi, own: OwnProviderUi, actions: OwnProviderActions) {
    val agent = own.editing
    val name = agent?.let { id -> state.phone.agents.firstOrNull { it.id == id }?.name } ?: ""
    val saved = agent?.let { own.saved[it] }
    val endpoint = remember(agent) { TextFieldState(saved?.endpoint.orEmpty()) }
    val model = remember(agent) { TextFieldState(saved?.model.orEmpty()) }
    // Never rememberSaveable: a key must not enter saved instance state.
    val key = remember(agent) { TextFieldState() }
    var replacing by remember(agent) { mutableStateOf(saved == null) }
    LaunchedEffect(agent) {
        snapshotFlow { Triple(endpoint.text.toString(), key.text.length, model.text.toString()) }
            .collect { actions.editedOwnProvider() }
    }
    val enabled = if (own.working) KitEnabled.Disabled(stringResource(Res.string.serversWorking)) else KitEnabled.Enabled
    val save = {
        if (agent != null)
            actions.saveOwnProvider(agent, endpoint.text.toString(), key.text.toString(), model.text.toString())
    }
    KitSheet(
        visible = agent != null,
        onDismissRequest = actions::closeOwnProvider,
        title = stringResource(Res.string.agentsOwnProviderTitle, name),
        icon = KitIcons.cloud,
        dismissible = !own.working,
        primary =
            agent?.let {
                KitAction(
                    stringResource(Res.string.agentsOwnProviderSave, name),
                    save,
                    enabled = enabled,
                    working = own.working,
                )
            },
        secondary = KitAction(stringResource(Res.string.serversCancel), actions::closeOwnProvider, enabled = enabled),
        tertiary =
            buildList {
                if (agent != null && saved != null)
                    add(
                        KitAction(
                            stringResource(Res.string.agentsOwnProviderRemove, name),
                            { actions.removeOwnProvider(agent) },
                            enabled = enabled,
                            destructive = true,
                        )
                    )
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitText(stringResource(Res.string.agentsOwnProviderBody, name))
            ProviderHelpFold(agent)
            val problem = own.problem
            if (problem == OwnProviderProblem.Unsupported)
                KitErrorNotice(stringResource(Res.string.agentsOwnProviderUnsupported, name))
            KitField(
                endpoint,
                stringResource(Res.string.agentsOwnProviderEndpoint),
                kind = KitFieldKind.Url,
                helper = stringResource(Res.string.agentsOwnProviderEndpointHint),
                error = if (problem == OwnProviderProblem.Endpoint) stringResource(Res.string.agentsOwnProviderBadEndpoint) else null,
                enabled = enabled,
            )
            KitField(
                key,
                stringResource(Res.string.agentsOwnProviderKey),
                kind = KitFieldKind.Secret,
                helper = stringResource(Res.string.agentsOwnProviderKeyHint),
                error = if (problem == OwnProviderProblem.Key) stringResource(Res.string.agentsOwnProviderBadKey) else null,
                secretPresence = if (replacing) SecretFieldPresence.Editing else SecretFieldPresence.Saved,
                onReplaceSecret = { replacing = true },
                enabled = enabled,
            )
            KitField(
                model,
                stringResource(Res.string.agentsOwnProviderModel),
                kind = KitFieldKind.Mono,
                helper = stringResource(Res.string.agentsOwnProviderModelHint),
                error = if (problem == OwnProviderProblem.Model) stringResource(Res.string.agentsOwnProviderBadModel) else null,
                onSubmit = save,
                enabled = enabled,
            )
            own.failure?.let { failure ->
                KitErrorNotice(
                    stringResource(Res.string.agentsOwnProviderFailed),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
        }
    }
}

@Composable
private fun ProviderHelpFold(resetKey: String?) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    KitExpandRow(
        stringResource(Res.string.agentsHelpProviderTitle),
        expanded,
        onExpandedChange = { expanded = it },
        inset = false,
    ) {
        KitText(stringResource(Res.string.agentsHelpProviderBody), tone = TextTone.Secondary)
    }
}
