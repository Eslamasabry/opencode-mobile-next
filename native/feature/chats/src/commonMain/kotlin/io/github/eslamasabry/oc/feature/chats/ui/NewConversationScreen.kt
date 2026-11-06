package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A first message, a real project/model/agent choice, and one explicit Start; drafts survive
 * leaving.
 */
@Composable
public fun NewConversationScreen(state: NewConversationUi, actions: NewConversationActions) {
    LifecycleStartEffect(actions) {
        actions.shown()
        onStopOrDispose {}
    }
    val field = rememberTextFieldState()
    LaunchedEffect(state.textRevision) {
        if (field.text.toString() != state.text) field.edit { replace(0, length, state.text) }
    }
    val working = stringResource(Res.string.serversWorking)
    val enabled =
        if (state.deliveryUnconfirmed)
            KitEnabled.Disabled(stringResource(Res.string.p12NewDeliveryUnconfirmed))
        else if (state.busy) KitEnabled.Disabled(working) else KitEnabled.Enabled
    KitBackGuard(enabled = state.sheet == null && state.profile != null, onBack = actions::back)
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.chatsHomeNewChat),
                    centred = true,
                    exit = KitTopBarExit.Close,
                    exitLabel = stringResource(Res.string.kitSheetClose),
                    onExit = actions::back,
                )
            },
            width = KitScreenWidth.Reading,
            loading = state.busy,
            loadingLabel = working,
            bottom = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KitButton(
                        action =
                            KitAction(
                                label =
                                    stringResource(
                                        Res.string.chatsNewStartWith,
                                        state.setup
                                            ?.models
                                            ?.firstOrNull { it.model == state.model }
                                            ?.runtime ?: state.setup?.defaultRuntime ?: "OpenCode",
                                    ),
                                onClick = actions::start,
                                enabled =
                                    if (
                                        !state.busy &&
                                            !state.creationUnconfirmed &&
                                            !state.deliveryUnconfirmed &&
                                            state.setup != null &&
                                            state.directory != null &&
                                            state.text.isNotBlank()
                                    )
                                        KitEnabled.Enabled
                                    else
                                        KitEnabled.Disabled(
                                            when {
                                                state.busy -> working
                                                state.deliveryUnconfirmed ->
                                                    stringResource(Res.string.p12NewDeliveryUnconfirmed)
                                                state.creationUnconfirmed ->
                                                    stringResource(Res.string.p12NewCreationUnconfirmed)
                                                state.directory == null ->
                                                    stringResource(Res.string.chatsNewNeedProject)
                                                else -> stringResource(Res.string.chatsNewBuildPrompt)
                                            }
                                        ),
                                working = state.busy,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        hero = true,
                    )
                }
            },
        ) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Space.S6.dp, bottom = Space.S4.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                if (state.servers.isEmpty()) {
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = stringResource(Res.string.serversEmpty),
                        primary =
                            KitAction(stringResource(Res.string.serversAdd), actions::addServer),
                    )
                } else {
                    if (state.servers.size > 1 || state.profile == null)
                        KitRowGroup {
                            state.servers.forEach { server ->
                                KitRow(
                                    title = server.name,
                                    selected = state.profile == server.profile,
                                    enabled = !state.busy && !state.attempted,
                                    disabledReason = working,
                                    onClick = { actions.chooseServer(server.profile) },
                                )
                            }
                        }
                    KitStartBox(
                        field,
                        stringResource(Res.string.chatsNewBuildPrompt),
                        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp).padding(bottom = Space.S1.dp).testTag("chats-new-field"),
                        enabled = enabled,
                        minLines = 4, // F: lib/ui/screens/chats/new_chat_screen.dart:537
                        autofocus = !state.busy,
                        onTextChanged = actions::draftChanged,
                    )
                    KitRowGroup(leadingTiles = true, note = if (state.directory == null) stringResource(Res.string.chatsNewNeedProject) else null) {
                        KitRow(
                            title = stringResource(Res.string.chatsNewModelRow),
                            leading = KitRowLeading.Glyph(KitIcons.model),
                            trailing =
                                KitRowTrailing.Value(
                                    state.setup
                                        ?.models
                                        ?.firstOrNull { it.model == state.model }
                                        ?.name ?: stringResource(Res.string.modelServerDefault),
                                    prominent = true,
                                ),
                            enabled = !state.busy,
                            disabledReason = working,
                            onClick = { actions.sheet(NewConversationSheet.Models) },
                        )
                        KitRow(
                            title = stringResource(Res.string.chatsNewProjectRow),
                            leading = KitRowLeading.Glyph(KitIcons.files),
                            trailing =
                                KitRowTrailing.Value(
                                    state.directory?.let(::importFolder) ?: stringResource(Res.string.chatsNewChooseProject),
                                    prominent = true,
                                ),
                            enabled =
                                !state.busy &&
                                    !state.attempted &&
                                    state.setup?.canChangeProject == true,
                            disabledReason =
                                if (state.setup?.canChangeProject == false)
                                    stringResource(Res.string.p12NewBoundProject)
                                else working,
                            onClick = { actions.sheet(NewConversationSheet.Projects) },
                        )
                        if (!state.setup?.agents.isNullOrEmpty())
                            KitRow(
                                title = stringResource(Res.string.p12NewAgent),
                                trailing =
                                    KitRowTrailing.Value(
                                        state.agent ?: stringResource(Res.string.p12NewDefaultAgent)
                                    ),
                                enabled = !state.busy,
                                disabledReason = working,
                                onClick = { actions.sheet(NewConversationSheet.Agents) },
                            )
                        if (state.canCopy)
                            KitSwitchRow(
                                title = stringResource(Res.string.chatsNewSeparateCopy),
                                leading = KitRowLeading.Glyph(KitIcons.fork),
                                supporting = stringResource(Res.string.chatsNewSeparateCopyDetail),
                                checked = state.separateCopy,
                                enabled =
                                    if (state.attempted) KitEnabled.Disabled(working) else enabled,
                                onCheckedChange = actions::separateCopy,
                            )
                        KitRow(
                            title = stringResource(Res.string.agentsSectionTitle),
                            onClick = actions::agents,
                        )
                        if (
                            state.directory != null &&
                                state.setup?.importDirectory == state.directory
                        )
                            KitRow(
                                title = stringResource(Res.string.claudeImportTitle),
                                leading = KitRowLeading.Glyph(KitIcons.download),
                                enabled = !state.busy,
                                disabledReason = working,
                                onClick = { actions.sheet(NewConversationSheet.Import) },
                            )
                    }
                    if (state.deliveryUnconfirmed) {
                        KitNotice(stringResource(Res.string.p12NewDeliveryUnconfirmed))
                        if (state.reviewConversation != null)
                            KitButton(
                                stringResource(Res.string.p12NewOpenCreated),
                                actions::openCreatedConversation,
                                role = KitButtonRole.Secondary,
                            )
                    }
                    if (state.draftFailed) KitNotice(stringResource(Res.string.p12NewDraftFailed))
                    if (state.repairing) KitNotice(stringResource(Res.string.gitRepairLooking))
                    state.repair?.let { result ->
                        KitNotice(
                            stringResource(
                                when (result.result) {
                                    GitLinkRepairResult.Healthy -> Res.string.linuxGitRepairHealthy
                                    GitLinkRepairResult.Repaired -> Res.string.linuxGitRepairHealthy
                                    GitLinkRepairResult.MissingObjects ->
                                        Res.string.linuxGitRepairMissing
                                    GitLinkRepairResult.TermuxMissing ->
                                        Res.string.linuxGitRepairTermuxMissing
                                    GitLinkRepairResult.TermuxPermission ->
                                        Res.string.linuxGitRepairPermission
                                    GitLinkRepairResult.TermuxUnavailable ->
                                        Res.string.linuxGitRepairNoAnswer
                                    GitLinkRepairResult.NotRepository ->
                                        Res.string.linuxGitRepairNotProject
                                }
                            )
                        )
                        if (result.result == GitLinkRepairResult.TermuxPermission)
                            KitButton(
                                stringResource(Res.string.linuxGitRepairAppSettings),
                                actions::repairSettings,
                                role = KitButtonRole.Tertiary,
                            )
                    }
                    if (state.failure != null) {
                        KitErrorNotice(
                            message =
                                stringResource(
                                    if (state.creationUnconfirmed)
                                        Res.string.p12NewCreationUnconfirmed
                                    else if (
                                        state.repairAvailable &&
                                            state.failure is Failure.ProjectGit
                                    )
                                        Res.string.gitRepairFailureWords
                                    else Res.string.chatsNewFailed
                                ),
                            failure = state.failure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                        )
                        if (
                            !state.creationUnconfirmed &&
                                !state.deliveryUnconfirmed &&
                                state.repairAvailable &&
                                state.failure is Failure.ProjectGit
                        )
                            KitButton(
                                stringResource(Res.string.gitRepairAction),
                                actions::repair,
                                role = KitButtonRole.Tertiary,
                            )
                        if (!state.creationUnconfirmed && !state.deliveryUnconfirmed)
                            KitButton(
                                stringResource(Res.string.commonRetry),
                                if (state.setup == null) actions::retryLoad else actions::start,
                                role = KitButtonRole.Tertiary,
                            )
                    }
                }
            }
        }
        NewConversationSheet(state, actions)
    }
}

@Composable
private fun NewConversationSheet(state: NewConversationUi, actions: NewConversationActions) {
    val current = state.sheet
    KitPageOverlay(current == NewConversationSheet.Import, stringResource(Res.string.claudeImportTitle), { actions.sheet(null) }) {
        KitScreen(
            topBar = { KitTopBar(stringResource(Res.string.claudeImportTitle), exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = { actions.sheet(null) }) },
            width = KitScreenWidth.Reading,
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S3.dp)) { ClaudeImportBody(state, actions) }
        }
    }
    KitSheet(
        visible = current != null && current != NewConversationSheet.Import,
        onDismissRequest = { actions.sheet(null) },
        title =
            stringResource(
                when (current) {
                    NewConversationSheet.Models -> Res.string.agentsModelTitle
                    NewConversationSheet.Agents -> Res.string.p12NewAgent
                    NewConversationSheet.Import -> Res.string.claudeImportTitle
                    else -> Res.string.chatsNewProjectRow
                }
            ),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            when (current) {
                NewConversationSheet.Projects -> {
                    // The picker starts from the selected project on each visit, after setup has loaded.
                    val directory = rememberTextFieldState(state.directory.orEmpty())
                    KitRowGroup {
                        state.setup?.projects.orEmpty().forEach { project ->
                            KitRow(
                                title = project.name,
                                supporting = project.directory,
                                selected = project.directory == state.directory,
                                onClick = { actions.chooseDirectory(project.directory) },
                            )
                        }
                    }
                    KitField(
                        directory,
                        stringResource(Res.string.p12NewFolder),
                        kind = KitFieldKind.Path,
                    )
                    KitButton(
                        stringResource(Res.string.p12NewUseFolder),
                        { actions.chooseDirectory(directory.text.toString()) },
                    )
                }
                NewConversationSheet.Models -> NewConversationModelChoices(state.setup?.models.orEmpty(), state.model, actions::chooseModel)
                NewConversationSheet.Agents ->
                    KitRowGroup {
                        KitRow(
                            title = stringResource(Res.string.p12NewDefaultAgent),
                            selected = state.agent == null,
                            onClick = { actions.chooseAgent(null) },
                        )
                        state.setup?.agents.orEmpty().forEach { agent ->
                            KitRow(
                                title = agent,
                                selected = agent == state.agent,
                                onClick = { actions.chooseAgent(agent) },
                            )
                        }
                    }
                NewConversationSheet.Import -> Unit
                null -> Unit
            }
        }
    }
}

/** Flutter agent_model_sheet.dart: the default first, then named models, each checked at the end. */
@Composable
internal fun NewConversationModelChoices(models: List<NewConversationModel>, selected: ModelRef?, choose: (ModelRef?) -> Unit) {
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.agentsModelDefault),
            selected = selected == null,
            trailing = if (selected == null) KitRowTrailing.Check else KitRowTrailing.None,
            onClick = { choose(null) },
        )
        models.filter { it.model.modelId != "default" }.forEach { model ->
            KitRow(
                title = model.name,
                selected = model.model == selected,
                trailing = if (model.model == selected) KitRowTrailing.Check else KitRowTrailing.None,
                onClick = { choose(model.model) },
            )
        }
    }
}

/** Flutter import list uses the final non-empty path segment, including a trailing slash. */
private fun importFolder(directory: String): String = directory.trimEnd('/').substringAfterLast('/').ifEmpty { directory }

@Composable
internal fun ClaudeImportBody(state: NewConversationUi, actions: NewConversationActions) {
    val found = state.imports
    if (state.importFailed && found == null && !state.importBusy) {
        KitStateView(
            kind = KitStateKind.Error,
            title = stringResource(Res.string.claudeImportLoadFailedTitle),
            body = stringResource(Res.string.claudeImportLoadFailedBody),
            primary = KitAction(stringResource(Res.string.commonRetry), { actions.sheet(NewConversationSheet.Import) }),
        )
        return
    }
    if (found == null) {
        KitSkeletonRows(count = 6) // F: lib/ui/screens/chats/claude_import_screen.dart:115
        return
    }
    if (found.items.isEmpty()) {
        KitStateView(
            kind = KitStateKind.Empty,
            icon = KitIcons.history,
            title = stringResource(Res.string.claudeImportEmptyTitle),
            body = stringResource(Res.string.claudeImportEmptyBody),
        )
        return
    }
    KitText(
        stringResource(Res.string.claudeImportIntro, importFolder(state.directory.orEmpty())),
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        role = TypeRole.Secondary,
    )
    Spacer(Modifier.height(Space.S3.dp))
    if (state.importFailed) {
        KitNotice(stringResource(Res.string.claudeImportFailed), tone = StatusTone.Failure,
            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp))
        Spacer(Modifier.height(Space.S3.dp))
    }
    KitRowGroup(leadingTiles = true) {
        found.items.forEach { item ->
            KitRow(
                leading = KitRowLeading.Glyph(KitIcons.history),
                enabled = !state.importBusy,
                disabledReason = stringResource(Res.string.claudeImportWorking),
                title = item.displayTitle ?: stringResource(Res.string.claudeImportUntitled),
                titleMaxLines = 2, // F: lib/ui/screens/chats/claude_import_screen.dart:162
                supporting = stringResource(
                    Res.string.claudeImportRowDetail,
                    importFolder(item.directory),
                    kitRelativeAge(item.lastUsed.toEpochMilliseconds(), state.importsReadAtMs),
                ),
                supportingMaxLines = 2, // F: lib/ui/screens/chats/claude_import_screen.dart:175
                onClick = { actions.import(item) },
            )
        }
    }
    if (found.alreadyImported > 0) KitGroupNote(
        pluralStringResource(Res.plurals.claudeImportAlready, found.alreadyImported, found.alreadyImported),
    )
}
