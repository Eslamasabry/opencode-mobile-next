package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
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
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
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
                    exit = KitTopBarExit.Close,
                    exitLabel = stringResource(Res.string.kitSheetClose),
                    onExit = actions::back,
                )
            },
            width = KitScreenWidth.Reading,
            loading = state.busy,
            loadingLabel = working,
            bottom = {
                KitActionBlock(
                    primary =
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
                        )
                )
            },
        ) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
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
                    KitField(
                        field,
                        stringResource(Res.string.chatsNewBuildPrompt),
                        Modifier.testTag("chats-new-field"),
                        kind = KitFieldKind.Multiline,
                        enabled = enabled,
                        multilineLines = 4..10,
                        onTextChanged = actions::draftChanged,
                    )
                    KitRowGroup {
                        KitRow(
                            title = stringResource(Res.string.chatsNewProjectRow),
                            trailing =
                                KitRowTrailing.Value(
                                    state.directory?.substringAfterLast('/')?.ifBlank {
                                        state.directory
                                    } ?: stringResource(Res.string.chatsNewChooseProject)
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
                        KitRow(
                            title = stringResource(Res.string.chatsNewModelRow),
                            trailing =
                                KitRowTrailing.Value(
                                    state.setup
                                        ?.models
                                        ?.firstOrNull { it.model == state.model }
                                        ?.name ?: stringResource(Res.string.p12NewDefaultModel)
                                ),
                            enabled = !state.busy,
                            disabledReason = working,
                            onClick = { actions.sheet(NewConversationSheet.Models) },
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
                    if (state.directory == null)
                        KitNotice(stringResource(Res.string.chatsNewNeedProject))
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
                                    else Res.string.chatsNewFailed
                                ),
                            failure = state.failure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                        )
                        if (
                            !state.creationUnconfirmed &&
                                !state.deliveryUnconfirmed &&
                                state.repairAvailable
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
    val directory = rememberTextFieldState(state.directory.orEmpty())
    KitSheet(
        visible = current != null,
        onDismissRequest = { actions.sheet(null) },
        title =
            stringResource(
                when (current) {
                    NewConversationSheet.Models -> Res.string.chatsNewModelRow
                    NewConversationSheet.Agents -> Res.string.p12NewAgent
                    NewConversationSheet.Import -> Res.string.claudeImportTitle
                    else -> Res.string.chatsNewProjectRow
                }
            ),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            when (current) {
                NewConversationSheet.Projects -> {
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
                NewConversationSheet.Models ->
                    KitRowGroup {
                        KitRow(
                            title = stringResource(Res.string.p12NewDefaultModel),
                            selected = state.model == null,
                            onClick = { actions.chooseModel(null) },
                        )
                        state.setup?.models.orEmpty().forEach { model ->
                            KitRow(
                                title = model.name,
                                supporting = model.model.providerId,
                                selected = model.model == state.model,
                                onClick = { actions.chooseModel(model.model) },
                            )
                        }
                    }
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
                NewConversationSheet.Import -> {
                    KitText(
                        stringResource(
                            Res.string.claudeImportIntro,
                            state.directory?.substringAfterLast('/').orEmpty(),
                        )
                    )
                    if (state.importFailed) {
                        KitNotice(stringResource(Res.string.claudeImportFailed))
                        KitButton(
                            stringResource(Res.string.commonRetry),
                            { actions.sheet(NewConversationSheet.Import) },
                        )
                    }
                    if (state.importBusy) KitSkeletonRows()
                    else
                        state.imports?.let { found ->
                            if (found.items.isEmpty())
                                KitStateView(
                                    kind = KitStateKind.Empty,
                                    title = stringResource(Res.string.claudeImportEmptyTitle),
                                    body = stringResource(Res.string.claudeImportEmptyBody),
                                )
                            else
                                KitRowGroup {
                                    found.items.forEach { item ->
                                        KitRow(
                                            title =
                                                item.displayTitle
                                                    ?: stringResource(
                                                        Res.string.claudeImportUntitled
                                                    ),
                                            supporting =
                                                stringResource(
                                                    Res.string.claudeImportRowDetail,
                                                    item.directory.substringAfterLast('/'),
                                                    item.lastUsed.toString(),
                                                ),
                                            supportingMaxLines = 2,
                                            onClick = { actions.import(item) },
                                        )
                                    }
                                }
                            if (found.alreadyImported > 0)
                                KitText(
                                    pluralStringResource(
                                        Res.plurals.claudeImportAlready,
                                        found.alreadyImported,
                                        found.alreadyImported,
                                    )
                                )
                        }
                }
                null -> Unit
            }
        }
    }
}
