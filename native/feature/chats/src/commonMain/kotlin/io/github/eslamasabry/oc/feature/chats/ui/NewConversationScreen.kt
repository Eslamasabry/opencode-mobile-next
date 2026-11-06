package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.feature.chats.presentation.NEW_CONVERSATION_MODEL_SEARCH_FROM
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationStarter
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.presentation.filterNewConversationModels
import io.github.eslamasabry.oc.feature.chats.presentation.isStartableAgent
import io.github.eslamasabry.oc.feature.chats.presentation.newConversationStarters
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetStep
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitStartingConversation
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitStartingPhase
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
    if (state.openingChat) {
        StartingConversation(state, actions)
        return
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
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    KitButton(
                        action =
                            KitAction(
                                label = stringResource(Res.string.upgradeNewStart),
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
                                                    stringResource(
                                                        Res.string.p12NewDeliveryUnconfirmed
                                                    )
                                                state.creationUnconfirmed ->
                                                    stringResource(
                                                        Res.string.p12NewCreationUnconfirmed
                                                    )
                                                state.directory == null ->
                                                    stringResource(Res.string.chatsNewNeedProject)
                                                else ->
                                                    stringResource(Res.string.chatsNewBuildPrompt)
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
                    if (state.profile == null)
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
                        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                            .padding(bottom = Space.S1.dp)
                            .testTag("chats-new-field"),
                        enabled = enabled,
                        minLines = 2,
                        autofocus = !state.busy,
                        onTextChanged = actions::draftChanged,
                        focusKey = state.focusRevision.takeIf { it > 0 },
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = LocalKitTokens.current.gutter.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    ) {
                        // Three chips only: who works (with Plan when chosen), the model, the
                        // project. Build/Plan lives inside the agent sheet.
                        KitChip(
                            label = agentChipLabel(state),
                            icon = KitIcons.agent,
                            opensMenu = true,
                            expanded = state.sheet == NewConversationSheet.Agents,
                            onClick = { actions.sheet(NewConversationSheet.Agents) },
                        )
                        KitChip(
                            label = modelLabel(state),
                            icon = KitIcons.model,
                            opensMenu = true,
                            expanded = state.sheet == NewConversationSheet.Models,
                            onClick = { actions.sheet(NewConversationSheet.Models) },
                        )
                        KitChip(
                            label =
                                state.directory?.let(::importFolder)
                                    ?: stringResource(Res.string.chatsNewProjectRow),
                            icon = KitIcons.files,
                            opensMenu = true,
                            expanded = state.sheet == NewConversationSheet.Projects,
                            onClick = { actions.sheet(NewConversationSheet.Projects) },
                        )
                    }
                    // Ways to start fill the empty prompt; nothing is sent until Start.
                    KitReveal(
                        visible =
                            state.text.isBlank() &&
                                state.setup != null &&
                                !state.busy &&
                                !state.deliveryUnconfirmed
                    ) {
                        NewConversationStarters(state, actions)
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
                                        state.repairAvailable && state.failure is Failure.ProjectGit
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
    KitPageOverlay(
        current == NewConversationSheet.Import,
        stringResource(Res.string.claudeImportTitle),
        { actions.sheet(null) },
    ) {
        KitScreen(
            topBar = {
                KitTopBar(
                    stringResource(Res.string.claudeImportTitle),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = { actions.sheet(null) },
                )
            },
            width = KitScreenWidth.Reading,
        ) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Space.S3.dp)
            ) {
                ClaudeImportBody(state, actions)
            }
        }
    }
    KitSheet(
        visible = current == NewConversationSheet.Projects,
        onDismissRequest = { actions.sheet(null) },
        title = stringResource(Res.string.chatsNewProjectRow),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
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
            if (state.canCopy)
                KitSwitchRow(
                    title = stringResource(Res.string.chatsNewSeparateCopy),
                    supporting = stringResource(Res.string.chatsNewSeparateCopyDetail),
                    checked = state.separateCopy,
                    onCheckedChange = actions::separateCopy,
                )
            if (state.setup?.importDirectory == state.directory && state.directory != null)
                KitRow(
                    title = stringResource(Res.string.claudeImportTitle),
                    leading = KitRowLeading.Glyph(KitIcons.download),
                    onClick = { actions.sheet(NewConversationSheet.Import) },
                )
            KitField(
                directory,
                stringResource(Res.string.p12NewFolder),
                kind = KitFieldKind.Path,
            )
            KitButton(
                stringResource(Res.string.p12NewUseFolder),
                { actions.chooseDirectory(directory.text.toString()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    AgentModelSheet(state, actions)
}

/**
 * One sheet for who works and how: at half height the agents a person can start, Build/Plan as a
 * stepped control and the model row; the model step grows the same sheet to full height in place
 * (search when the list is long) instead of opening a second sheet.
 */
@Composable
private fun AgentModelSheet(state: NewConversationUi, actions: NewConversationActions) {
    val step =
        state.sheet?.takeIf {
            it == NewConversationSheet.Agents || it == NewConversationSheet.Models
        }
    // While the sheet leaves, it keeps showing the step it had.
    var lastStep by remember { mutableStateOf(NewConversationSheet.Agents) }
    if (step != null && step != lastStep) SideEffect { lastStep = step }
    val shown = step ?: lastStep
    val models = shown == NewConversationSheet.Models
    KitSheet(
        visible = step != null,
        onDismissRequest = { actions.sheet(null) },
        title =
            stringResource(
                if (models) Res.string.agentsModelTitle else Res.string.upgNewChatChoicesTitle
            ),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = if (models) KitSheetHeight.Full else KitSheetHeight.Half,
        growsInPlace = true,
        leading =
            if (models)
                KitAction(
                    label = stringResource(Res.string.upgNewChatBackToAgents),
                    onClick = { actions.sheet(NewConversationSheet.Agents) },
                    icon = KitIcons.back,
                )
            else null,
        primary =
            if (models) null
            else KitAction(stringResource(Res.string.upgNewChatDone), { actions.sheet(null) }),
    ) {
        KitSheetStep(shown) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                if (current == NewConversationSheet.Models) NewConversationModelStep(state, actions)
                else NewConversationAgentStep(state, actions)
            }
        }
    }
}

/** Half height: the agents a person can start, then Build/Plan, then the model row that grows. */
@Composable
private fun NewConversationAgentStep(state: NewConversationUi, actions: NewConversationActions) {
    val defaultName = state.setup?.defaultRuntime ?: "OpenCode"
    val modes = state.setup?.modes.orEmpty()
    val namedAgent = state.agent != null && state.agent !in modes
    KitRowGroup(label = stringResource(Res.string.upgNewChatAgents)) {
        KitRow(
            title = defaultName,
            leading = KitRowLeading.Logo("opencode", defaultName, agent = true),
            selected = !namedAgent,
            trailing = if (!namedAgent) KitRowTrailing.Check else KitRowTrailing.None,
            supporting = stringResource(Res.string.upgradeAgentReady),
            // Already the default: a tap keeps the chosen Build or Plan.
            onClick = { if (namedAgent) actions.chooseAgent(null) },
        )
        state.setup?.agents.orEmpty().filter(::isStartableAgent).distinct().forEach { agent ->
            KitRow(
                title = agent,
                leading = KitRowLeading.Logo(agent.lowercase(), agent, agent = true),
                selected = state.agent == agent,
                trailing = if (state.agent == agent) KitRowTrailing.Check else KitRowTrailing.None,
                supporting = stringResource(Res.string.upgradeAgentReady),
                onClick = { actions.chooseAgent(agent) },
            )
        }
        KitRow(
            title = stringResource(Res.string.kitCapAgentA2aEnable),
            leading = KitRowLeading.Glyph(KitIcons.add),
            onClick = actions::agents,
        )
    }
    // The server's own Build/Plan: a stepped control inside this sheet, never a chip of its own.
    if (modes.size in 2..4) {
        val modeWord = stringResource(Res.string.upgradeMode)
        KitSectionLabel(modeWord)
        KitSegmented(
            segments = modes.map { mode -> KitSegment(mode, modeLabel(mode)) },
            selected =
                state.agent?.takeIf { it in modes }
                    ?: modes.firstOrNull { it.lowercase() == "build" }
                    ?: modes.first(),
            semanticsLabel = modeWord,
            enabled =
                if (namedAgent)
                    KitEnabled.Disabled(
                        stringResource(Res.string.upgNewChatModeOnlyFor, defaultName)
                    )
                else KitEnabled.Enabled,
            onChange = actions::chooseMode,
        )
        if (!namedAgent)
            KitText(stringResource(Res.string.upgNewChatModeHint), role = TypeRole.Secondary)
    }
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.upgNewChatModel),
            leading = KitRowLeading.Glyph(KitIcons.model),
            trailing = KitRowTrailing.Value(modelLabel(state)),
            onClick = { actions.sheet(NewConversationSheet.Models) },
        )
    }
}

/** Full height: every model the server offers, with search once the list is long. */
@Composable
private fun NewConversationModelStep(state: NewConversationUi, actions: NewConversationActions) {
    val models = state.setup?.models.orEmpty()
    val named = filterNewConversationModels(models, "")
    val search = rememberTextFieldState()
    var query by remember { mutableStateOf("") }
    if (named.size >= NEW_CONVERSATION_MODEL_SEARCH_FROM)
        KitSearchField(
            label = stringResource(Res.string.upgNewChatModelsSearch),
            onSearch = { query = it },
            state = search,
            modifier = Modifier.fillMaxWidth(),
        )
    if (query.isNotBlank() && filterNewConversationModels(models, query).isEmpty())
        KitSearchNoMatch(
            query = query,
            onClear = {
                search.clearText()
                query = ""
            },
        )
    else NewConversationModelChoices(models, state.model, actions::chooseModel, query)
    if (named.isEmpty())
        KitText(stringResource(Res.string.upgNewChatModelsNone), role = TypeRole.Secondary)
}

/** Ways to start: each fills the empty prompt and leaves sending to Start. */
@Composable
private fun NewConversationStarters(state: NewConversationUi, actions: NewConversationActions) {
    val ways = stringResource(Res.string.chatStartSuggestionsLabel)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = LocalKitTokens.current.gutter.dp)
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = ways }
            .testTag("chats-new-starters"),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        newConversationStarters(state.directory).forEach { starter ->
            val label = starterLabel(starter)
            KitChip(label = label, onClick = { actions.chooseStarter(starter, label) })
        }
    }
}

@Composable
private fun starterLabel(starter: NewConversationStarter): String =
    stringResource(
        when (starter) {
            NewConversationStarter.ExplainProject -> Res.string.chatStartExplainProject
            NewConversationStarter.FindBug -> Res.string.chatStartFindBug
            NewConversationStarter.AddTests -> Res.string.chatStartAddTests
            NewConversationStarter.ListFolder -> Res.string.chatStartListFolder
        }
    )

@Composable
private fun modeLabel(mode: String): String =
    stringResource(
        if (mode.lowercase() == "plan") Res.string.upgradeModePlan else Res.string.upgradeModeBuild
    )

/**
 * The agent chip: the agent's name, with Plan beside it when Plan is chosen (Build is the norm).
 */
@Composable
private fun agentChipLabel(state: NewConversationUi): String {
    val name = state.agent?.takeIf(::isStartableAgent) ?: state.setup?.defaultRuntime ?: "OpenCode"
    return if (state.agent?.lowercase() == "plan")
        stringResource(
            Res.string.upgNewChatAgentMode,
            name,
            stringResource(Res.string.upgradeModePlan),
        )
    else name
}

@Composable
private fun modelLabel(state: NewConversationUi): String =
    state.setup?.models?.firstOrNull { it.model == state.model }?.name
        ?: stringResource(Res.string.modelServerDefault)

/**
 * The chat frame from the moment Start is tapped: the prompt is already the first bubble, the
 * chosen agent and model stay with it, and a definite failure offers Try again and Edit.
 */
@Composable
private fun StartingConversation(state: NewConversationUi, actions: NewConversationActions) {
    KitBackGuard(enabled = state.busy, onBack = actions::back)
    val phase =
        when {
            state.busy -> KitStartingPhase.Sending
            state.deliveryUnconfirmed || state.creationUnconfirmed -> KitStartingPhase.Unconfirmed
            else -> KitStartingPhase.NotSent
        }
    val notSent = phase == KitStartingPhase.NotSent
    KitStartingConversation(
        text = state.text,
        project = state.directory?.let(::importFolder).orEmpty(),
        model = modelLabel(state),
        agent = agentChipLabel(state),
        phase = phase,
        status =
            stringResource(
                when {
                    state.busy -> Res.string.upgradeStartSending
                    state.deliveryUnconfirmed -> Res.string.p12NewDeliveryUnconfirmed
                    state.creationUnconfirmed -> Res.string.p12NewCreationUnconfirmed
                    else -> Res.string.chatSendFailed
                }
            ),
        reason =
            if (!notSent) null
            else
                stringResource(
                    if (state.repairAvailable && state.failure is Failure.ProjectGit)
                        Res.string.gitRepairFailureWords
                    else Res.string.upgNewChatKept
                ),
        details = state.failure?.details?.takeIf { notSent },
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        backLabel = stringResource(Res.string.kitTopBarBack),
        onBack = actions::back,
        retryLabel = stringResource(Res.string.commonRetry),
        onRetry = if (notSent) actions::start else null,
        editLabel = stringResource(Res.string.upgNewChatEdit),
        onEdit = if (notSent) actions::editPrompt else null,
        reviewLabel = stringResource(Res.string.p12NewOpenCreated),
        onReview = if (state.reviewConversation != null) actions::openCreatedConversation else null,
    )
}

/**
 * Flutter agent_model_sheet.dart: the default first, then named models, each checked at the end.
 * [query] narrows the list by what each row shows; the default row is hidden while searching.
 */
@Composable
internal fun NewConversationModelChoices(
    models: List<NewConversationModel>,
    selected: ModelRef?,
    choose: (ModelRef?) -> Unit,
    query: String = "",
) {
    KitRowGroup {
        if (query.isBlank())
            KitRow(
                title = stringResource(Res.string.agentsModelDefault),
                selected = selected == null,
                trailing = if (selected == null) KitRowTrailing.Check else KitRowTrailing.None,
                onClick = { choose(null) },
            )
        filterNewConversationModels(models, query).forEach { model ->
            KitRow(
                title = model.name,
                supporting = model.runtime,
                selected = model.model == selected,
                trailing =
                    if (model.model == selected) KitRowTrailing.Check else KitRowTrailing.None,
                onClick = { choose(model.model) },
            )
        }
    }
}

/** Flutter import list uses the final non-empty path segment, including a trailing slash. */
private fun importFolder(directory: String): String =
    directory.trimEnd('/').substringAfterLast('/').ifEmpty { directory }

@Composable
internal fun ClaudeImportBody(state: NewConversationUi, actions: NewConversationActions) {
    val found = state.imports
    if (state.importFailed && found == null && !state.importBusy) {
        KitStateView(
            kind = KitStateKind.Error,
            title = stringResource(Res.string.claudeImportLoadFailedTitle),
            body = stringResource(Res.string.claudeImportLoadFailedBody),
            primary =
                KitAction(
                    stringResource(Res.string.commonRetry),
                    { actions.sheet(NewConversationSheet.Import) },
                ),
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
        KitNotice(
            stringResource(Res.string.claudeImportFailed),
            tone = StatusTone.Failure,
            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        )
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
                supporting =
                    stringResource(
                        Res.string.claudeImportRowDetail,
                        importFolder(item.directory),
                        kitRelativeAge(item.lastUsed.toEpochMilliseconds(), state.importsReadAtMs),
                    ),
                supportingMaxLines = 2, // F: lib/ui/screens/chats/claude_import_screen.dart:175
                onClick = { actions.import(item) },
            )
        }
    }
    if (found.alreadyImported > 0)
        KitGroupNote(
            pluralStringResource(
                Res.plurals.claudeImportAlready,
                found.alreadyImported,
                found.alreadyImported,
            )
        )
}
