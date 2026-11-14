package io.github.eslamasabry.oc.feature.chats.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationStarter
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationWait
import io.github.eslamasabry.oc.feature.chats.presentation.projectPlace
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
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.modal.KitNewProjectDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitNewProjectRow
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
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
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
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
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
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
    // The last words the field told the presenter. Words typed since, not yet reported, are newer than any state
    // text (a stored draft arriving late, say) and are never written over.
    var reported by remember { mutableStateOf("") }
    LaunchedEffect(state.textRevision) {
        val now = field.text.toString()
        if (now == state.text) {
            // Already the same words (a fill by code is not always reported back): they are the baseline now.
            reported = now
            return@LaunchedEffect
        }
        if (fieldMayTakeState(now, reported)) {
            field.edit { replace(0, length, state.text) }
            reported = state.text
        }
    }
    val working = stringResource(Res.string.serversWorking)
    // Getting the page ready is not "the current action": the button says what it waits for, and after a few
    // seconds the strip and the line under the field name the thing being read.
    val preparing = state.wait == NewConversationWait.Preparing
    val gettingReady =
        stringResource(if (state.preparingSlow) Res.string.chatsNewGettingReadySlow else Res.string.chatsNewGettingReady)
    val loadingModels = stringResource(Res.string.chat_model_loading)
    val phoneOffWords = stringResource(Res.string.homePhoneOffTitle)
    val enabled =
        if (state.phoneOff) KitEnabled.Disabled(phoneOffWords)
        else if (state.deliveryUnconfirmed)
            KitEnabled.Disabled(stringResource(Res.string.p12NewDeliveryUnconfirmed))
        // Getting ready never locks the field: the person can type ahead; only Start waits (RC1 device pass).
        else if (state.busy && !preparing) KitEnabled.Disabled(working) else KitEnabled.Enabled
    // A page that could not read its server says that, never "the conversation didn't start" (nothing was sent).
    val setupFailed = state.setup == null && state.failure != null
    val failureShown = state.failure != null && !state.deliveryUnconfirmed
    val startBlocked =
        when {
            state.phoneOff -> phoneOffWords
            preparing -> gettingReady
            state.busy -> working
            state.deliveryUnconfirmed -> stringResource(Res.string.p12NewDeliveryUnconfirmed)
            state.creationUnconfirmed -> stringResource(Res.string.p12NewCreationUnconfirmed)
            state.setup == null ->
                if (setupFailed) stringResource(Res.string.chatsNewSetupFailed) else gettingReady
            state.directory.isNullOrBlank() -> stringResource(Res.string.chatsNewNeedProject)
            state.folderMissing -> stringResource(Res.string.chatsNewFolderMissing)
            state.phoneNeedsProjectsFolder ->
                stringResource(
                    Res.string.chatsNewAgentNeedsProjectsFolder,
                    state.phoneAgentChoice?.name.orEmpty(),
                )
            state.phoneModelsBusy -> loadingModels
            state.text.isBlank() -> stringResource(Res.string.chatsNewBuildPrompt)
            else -> null
        }
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
            loadingLabel = if (preparing) gettingReady else working,
            bottom = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    if (state.servers.isNotEmpty()) NewConversationChoices(state, actions)
                    // The empty prompt already says it in the field; repeating it is the stray line (N-32).
                    val promptHint = stringResource(Res.string.chatsNewBuildPrompt)
                    // N-48: an unconfirmed send already has its notice above; the reason is said once. A failure
                    // notice on the page is that reason too: never the same sentence twice.
                    if (
                        startBlocked != null && startBlocked != promptHint && (!state.busy || preparing) && !state.phoneOff &&
                            !failureShown &&
                            !state.deliveryUnconfirmed && !state.creationUnconfirmed &&
                            !state.folderMissing
                    )
                        KitText(startBlocked, role = TypeRole.Secondary)
                    KitButton(
                        action =
                            KitAction(
                                label = stringResource(Res.string.upgradeNewStart),
                                onClick = actions::start,
                                enabled =
                                    if (startBlocked == null) KitEnabled.Enabled
                                    else KitEnabled.Disabled(startBlocked),
                                // A spinner only once Start was pressed.
                                working = state.wait == NewConversationWait.Sending,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        hero = true,
                    )
                }
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(kitPageScrollState())
                    .padding(top = Space.S3.dp, bottom = Space.S3.dp),
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
                        // B-098: not while a sheet is open; coming back from agent setup re-ran this and the keyboard covered the sheet.
                        autofocus = (!state.busy || preparing) && state.sheet == null,
                        onTextChanged = {
                            reported = it
                            actions.draftChanged(it)
                        },
                        focusKey = state.focusRevision.takeIf { it > 0 },
                    )
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
                    // The form's notices and ways forward keep the gutter like the field above them.
                    val inset = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                    // The person turned this phone's OpenCode off: say so, and the way forward is Start.
                    if (state.phoneOff)
                        KitNotice(
                            message = stringResource(Res.string.thisPhoneAnswerOffLine),
                            modifier = inset,
                            title = phoneOffWords,
                            tone = StatusTone.Failure,
                            actions =
                                listOf(
                                    KitLineAction(
                                        stringResource(Res.string.homePhoneOffStart),
                                        actions::startPhoneServer,
                                    )
                                ),
                        )
                    if (state.deliveryUnconfirmed) {
                        // N-48: the field already says it once (its own reason); under it only the ways forward: the
                        // conversation (where it can be sent again) and Discard, which opens this form again.
                        val open =
                            state.reviewConversation?.let {
                                KitAction(stringResource(Res.string.p12NewOpenCreated), actions::openCreatedConversation)
                            }
                        val discard = KitAction(stringResource(Res.string.nativePendingDiscard), actions::discardUnconfirmed)
                        // B-096: with nothing to open (the server is not answering, or the record cannot be read),
                        // "Check again" reads it once more; Discard is not the only way out.
                        val again = KitAction(stringResource(Res.string.chatReceiptCheckAgain), actions::recheckUnconfirmed)
                        KitActionBlock(
                            inset,
                            secondary = open ?: again,
                            tertiary = listOf(discard),
                        )
                    }
                    // B-063: the server still lists a project whose folder is gone; a message sent there is
                    // refused. Say it before Start, with the two ways forward.
                    if (state.folderMissing && !state.deliveryUnconfirmed) {
                        KitNotice(stringResource(Res.string.chatsNewFolderMissing), inset)
                        KitActionBlock(
                            inset,
                            secondary =
                                KitAction(
                                    stringResource(Res.string.chatsNewFolderRemake),
                                    actions::remakeFolder,
                                    working = state.remakingFolder,
                                ),
                            tertiary =
                                listOf(
                                    KitAction(
                                        stringResource(Res.string.chatsNewFolderChooseAnother),
                                        { actions.sheet(NewConversationSheet.Projects) },
                                    )
                                ),
                        )
                        state.remakeFailure?.let { failure ->
                            KitErrorNotice(
                                modifier = inset,
                                message = stringResource(Res.string.chatsNewFailed),
                                failure = failure,
                                detailsLabel = stringResource(Res.string.kitDetails),
                                copyLabel = stringResource(Res.string.kitCopyDetails),
                            )
                        }
                    }
                    if (state.draftFailed) KitNotice(stringResource(Res.string.p12NewDraftFailed), inset)
                    if (state.repairing) KitNotice(stringResource(Res.string.gitRepairLooking), inset)
                    state.repair?.let { result ->
                        KitNotice(
                            stringResource(
                                when (result.result) {
                                    GitLinkRepairResult.Healthy -> Res.string.linuxGitRepairHealthy
                                    GitLinkRepairResult.Repaired -> Res.string.linuxGitRepairHealthy
                                    GitLinkRepairResult.MissingObjects ->
                                        Res.string.linuxGitRepairMissing
                                    GitLinkRepairResult.NotRepository ->
                                        Res.string.linuxGitRepairNotProject
                                }
                            )
                        )
                    }
                    // N-48: an unconfirmed send is said once (above); a failed read behind it adds no second notice.
                    if (failureShown) {
                        KitErrorNotice(
                            modifier = inset,
                            message =
                                stringResource(
                                    if (state.creationUnconfirmed)
                                        Res.string.p12NewCreationUnconfirmed
                                    else if (setupFailed) Res.string.chatsNewSetupFailed
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
                        // The ways forward sit on the page gutter like the notice above them (a bare text button
                        // touched the screen's left edge).
                        if (!state.creationUnconfirmed) {
                            val repair =
                                KitAction(stringResource(Res.string.gitRepairAction), actions::repair)
                                    .takeIf { state.repairAvailable && state.failure is Failure.ProjectGit }
                            KitActionBlock(
                                inset,
                                secondary =
                                    KitAction(
                                        stringResource(Res.string.commonRetry),
                                        if (state.setup == null) actions::retryLoad else actions::start,
                                    ),
                                tertiary = listOfNotNull(repair),
                            )
                        }
                    }
                }
            }
        }
        NewConversationSheet(state, actions)
    }
}

/** Compact upgrade choices stay beside Start while the keyboard is open. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NewConversationChoices(state: NewConversationUi, actions: NewConversationActions) {
    // N-31: the keyboard goes first, so the sheet opens at its full size instead of squashed.
    val focus = LocalFocusManager.current
    val open: (NewConversationSheet) -> Unit = { sheet ->
        focus.clearFocus()
        actions.sheet(sheet)
    }
    // P22 (F: new_chat_screen.dart:584-641 shows every choice whole, as rows): the three chips sit above Start on one
    // line when they fit and wrap onto a second when the names are long, so the project is never cut at the edge or
    // hidden past a fade (the sideways line of N-32 hid it on a phone).
    KitChipWrap(Modifier.fillMaxWidth().testTag("chats-new-choices")) {
        // Three chips only: who works (with Plan when chosen), the model, the
        // project. Build/Plan lives inside the agent sheet.
        KitChip(
            label = agentChipLabel(state),
            icon = KitIcons.agent,
            opensMenu = true,
            expanded = state.sheet == NewConversationSheet.Agents,
            onClick = { open(NewConversationSheet.Agents) },
        )
        KitChip(
            label = modelLabel(state),
            icon = KitIcons.model,
            // With no other model to choose it only names the model in use and opens nothing.
            opensMenu = state.modelChoosable,
            expanded = state.modelChoosable && state.sheet == NewConversationSheet.Models,
            onClick = if (state.modelChoosable) ({ open(NewConversationSheet.Models) }) else null,
        )
        KitChip(
            label =
                state.directory?.let(::importFolder)
                    ?: stringResource(Res.string.chatsNewProjectRow),
            icon = KitIcons.files,
            opensMenu = true,
            expanded = state.sheet == NewConversationSheet.Projects,
            onClick = { open(NewConversationSheet.Projects) },
        )
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
            edges = KitScreenEdges.Bleed,
        ) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(kitPageScrollState())
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
            var byPath by remember { mutableStateOf(false) }
            val working = stringResource(Res.string.serversWorking)
            // Recent projects first, one tap each; then New project as an obvious row; the typed path stays folded.
            KitRowGroup(leadingTiles = true) {
                // E20-S08: one row per folder (the server listed some twice).
                state.projectChoices.forEach { project ->
                    val chosen = state.isChosen(project)
                    KitRow(
                        title = project.name,
                        // The folders it sits in tell same-named projects apart; the phone's own /root is never part of it.
                        supporting = projectPlace(project.directory),
                        leading = KitRowLeading.Glyph(KitIcons.folderOpen, tile = true, current = chosen),
                        trailing = if (chosen) KitRowTrailing.Check else KitRowTrailing.None,
                        selected = chosen,
                        onClick = { actions.chooseDirectory(project.directory) },
                    )
                }
                if (state.canCreateProject)
                    KitNewProjectRow(actions::askNewProject, working = state.creatingProject)
            }
            state.projectFailure?.let { problem ->
                KitErrorNotice(
                    message = stringResource(Res.string.projectFolderCreateFailedTitle),
                    failure = problem,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
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
            KitExpandRow(
                title = stringResource(Res.string.homeProjectSheetByPath),
                expanded = byPath,
                onExpandedChange = { byPath = it },
                leading = KitIcons.folders,
            ) {
                KitField(
                    directory,
                    stringResource(Res.string.p12NewFolder),
                    kind = KitFieldKind.Path,
                )
                KitButton(
                    stringResource(Res.string.p12NewUseFolder),
                    { actions.chooseDirectory(directory.text.toString()) },
                    role = KitButtonRole.Secondary,
                    fullWidth = true,
                )
            }
        }
    }
    NewProjectDialog(state, actions)
    AgentModelSheet(state, actions)
}

/**
 * Who works and how: at half height the agents a person can start, Build/Plan as a stepped control and
 * the model row. The model row (and the Model chip) opens the kit's one model sheet, the chat's own.
 */
@Composable
private fun AgentModelSheet(state: NewConversationUi, actions: NewConversationActions) {
    KitSheet(
        visible = state.sheet == NewConversationSheet.Agents,
        onDismissRequest = { actions.sheet(null) },
        title = stringResource(Res.string.upgNewChatChoicesTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Half,
        primary = KitAction(stringResource(Res.string.upgNewChatDone), { actions.sheet(null) }),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            NewConversationAgentStep(state, actions)
        }
    }
    NewConversationModelSheet(state, actions)
}

@Composable
private fun AddAgentRow(actions: NewConversationActions) {
    KitRow(
        title = stringResource(Res.string.chatsNewAddAgent),
        leading = KitRowLeading.Glyph(KitIcons.add),
        onClick = actions::agents,
        modifier = Modifier.testTag("chats-new-add-agent"),
    )
}

/** Half height: the agents a person can start, then Build/Plan, then the model row that grows. */
@Composable
private fun NewConversationAgentStep(state: NewConversationUi, actions: NewConversationActions) {
    val defaultName = state.setup?.defaultRuntime ?: "OpenCode"
    val modes = state.setup?.modes.orEmpty()
    val phone = state.setup?.phone
    val onPhone = state.phoneAgent != null
    val namedAgent = (state.agent != null && state.agent !in modes) || onPhone
    val named = state.setup?.agents.orEmpty().filter(::isStartableAgent).distinct()
    val phoneRows = phone != null && (phone.choices.isNotEmpty() || phone.needsSetup.isNotEmpty())
    KitRowGroup(label = stringResource(Res.string.upgNewChatAgents)) {
        // "Add an agent" leads the empty state, and otherwise sits right after the default, in the first
        // rows (Flutter chatsNewAddAgent). With agents on this phone it closes the list, after their rows.
        // It opens agent setup; Back returns to this same unsent draft.
        if (named.isEmpty() && !phoneRows) AddAgentRow(actions)
        KitRow(
            title = defaultName,
            leading = KitRowLeading.Logo("opencode", defaultName, agent = true),
            selected = !namedAgent,
            trailing = if (!namedAgent) KitRowTrailing.Check else KitRowTrailing.None,
            supporting = stringResource(Res.string.upgradeAgentReady),
            // Already the default: a tap keeps the chosen Build or Plan.
            onClick = { if (namedAgent) actions.chooseAgent(null) },
        )
        if (named.isNotEmpty() && !phoneRows) AddAgentRow(actions)
        phone?.choices?.forEach { agent ->
            KitRow(
                title = agent.name,
                leading = KitRowLeading.Logo(agent.iconKey, agent.name, agent = true),
                selected = state.phoneAgent == agent.id,
                trailing = if (state.phoneAgent == agent.id) KitRowTrailing.Check else KitRowTrailing.None,
                supporting = stringResource(Res.string.upgradeAgentReady),
                onClick = { actions.choosePhoneAgent(agent.id) },
                modifier = Modifier.testTag("chats-new-agent-${agent.id}"),
            )
        }
        phone?.needsSetup?.forEach { agent ->
            KitRow(
                title = agent.name,
                leading = KitRowLeading.Logo(agent.iconKey, agent.name, agent = true),
                trailing = KitRowTrailing.Chevron,
                supporting = stringResource(Res.string.chatsNewSetUp),
                onClick = { actions.setUpPhoneAgent(agent.id) },
                modifier = Modifier.testTag("chats-new-agent-${agent.id}"),
            )
        }
        if (phoneRows) AddAgentRow(actions)
        named.forEach { agent ->
            KitRow(
                title = agent,
                leading = KitRowLeading.Logo(agent.lowercase(), agent, agent = true),
                selected = state.agent == agent,
                trailing = if (state.agent == agent) KitRowTrailing.Check else KitRowTrailing.None,
                supporting = stringResource(Res.string.upgradeAgentReady),
                onClick = { actions.chooseAgent(agent) },
            )
        }
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
            // Nothing to choose: the row only says which model is used and opens nothing.
            trailing = KitRowTrailing.Value(modelLabel(state), chevron = state.modelChoosable),
            onClick = if (state.modelChoosable) ({ actions.sheet(NewConversationSheet.Models) }) else null,
        )
    }
}

/**
 * Whether the presenter's text may replace what the field holds: not when the field holds words the presenter has not
 * heard yet (typed in a burst, before the field's report arrived).
 */
internal fun fieldMayTakeState(fieldText: String, reportedText: String): Boolean = fieldText == reportedText

/** N-51: the chat's model sheet, with this server's favourites; choosing a model closes it. */
@Composable
private fun NewConversationModelSheet(state: NewConversationUi, actions: NewConversationActions) {
    val models = state.shownModels
    val options = remember(models) { newConversationModelOptions(models) }
    val favorites =
        remember(options, state.favorites) {
            state.favorites.mapNotNull { ref -> options.firstOrNull { it.matches(ref) } }
        }
    KitModelSheet(
        visible = state.sheet == NewConversationSheet.Models,
        onDismissRequest = { actions.sheet(null) },
        options = options,
        current = state.shownModel?.let { ref -> options.firstOrNull { it.matches(ref) }?.key },
        onChoose = { option -> actions.chooseModel(option?.let { ModelRef(it.providerId, it.modelId) }) },
        favorites = favorites,
        onToggleFavorite = { actions.toggleFavorite(ModelRef(it.providerId, it.modelId)) },
        resetKey = state.profile,
        // A read of the agent's models is running: the kit says "Loading models…" (no list yet) or marks the last
        // known list while it is read again.
        loading = state.modelsLoading,
        above = {
            // "No models" only after a read ended with none (the kit draws no `above` while loading, this keeps it so).
            if (options.isEmpty() && !state.modelsLoading)
                KitText(stringResource(Res.string.upgNewChatModelsNone), role = TypeRole.Secondary)
        },
        below = {
            // The chosen model's thinking efforts, as the chat's model sheet offers them (Default first).
            val levels = state.shownVariants
            if (options.isNotEmpty() && levels.isNotEmpty()) {
                KitSectionLabel(stringResource(Res.string.composerFullThinking))
                KitFilterChips(Modifier.fillMaxWidth().testTag("new-thinking-choices")) {
                    (listOf<String?>(null) + levels).forEach { level ->
                        KitChip(
                            label =
                                if (level == null) stringResource(Res.string.composerFullThinkingDefault)
                                else effortWords(level),
                            kind = io.github.eslamasabry.oc.ui.kit.chip.KitChipKind.Action,
                            selected = level == state.variant,
                            onClick = { actions.chooseVariant(level) },
                        )
                    }
                }
            }
        },
    )
}

/** A thinking effort as people read it (the chat's model sheet, Flutter `presentedEffort`); unknown ids as they are. */
@Composable
private fun effortWords(id: String): String =
    when (id.trim().lowercase()) {
        "none" -> stringResource(Res.string.modelEffortNone)
        "minimal" -> stringResource(Res.string.modelEffortMinimal)
        "low" -> stringResource(Res.string.modelEffortLow)
        "medium" -> stringResource(Res.string.modelEffortMedium)
        "high" -> stringResource(Res.string.modelEffortHigh)
        "xhigh" -> stringResource(Res.string.modelEffortExtraHigh)
        "max" -> stringResource(Res.string.modelEffortMax)
        else -> id
    }

private fun KitModelOption.matches(ref: ModelRef): Boolean =
    providerId == ref.providerId && modelId == ref.modelId

/**
 * The models a person can name (never the server's "default" alias), each under its provider, its host app
 * as the second line where it differs from the provider.
 */
internal fun newConversationModelOptions(models: List<NewConversationModel>): List<KitModelOption> =
    filterNewConversationModels(models, "").map { model ->
        val provider = model.providerName ?: model.model.providerId
        KitModelOption(
            providerId = model.model.providerId,
            modelId = model.model.modelId,
            label = model.name,
            providerName = provider,
            supporting = model.runtime?.takeIf { it != provider },
        )
    }

/** Ways to start: each fills the empty prompt and leaves sending to Start. */
@Composable
private fun NewConversationStarters(state: NewConversationUi, actions: NewConversationActions) {
    val ways = stringResource(Res.string.chatStartSuggestionsLabel)
    KitFilterChips(
        Modifier.semantics { contentDescription = ways }.testTag("chats-new-starters"),
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
    state.phoneAgentChoice?.let { return it.name }
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
    state.shownModels.firstOrNull { it.model == state.shownModel }?.name
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
        recheckLabel = stringResource(Res.string.chatReceiptCheckAgain),
        onRecheck = if (phase == KitStartingPhase.Unconfirmed && state.deliveryUnconfirmed && state.reviewConversation == null) actions::recheckUnconfirmed else null,
        discardLabel = stringResource(Res.string.nativePendingDiscard),
        onDiscard = if (phase == KitStartingPhase.Unconfirmed) actions::discardUnconfirmed else null,
    )
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

/** New project's name (the same dialog and rules as Projects › New project): a Git folder under the projects root. */
@Composable
private fun NewProjectDialog(state: NewConversationUi, actions: NewConversationActions) {
    if (!state.askingProjectName) return
    val empty = stringResource(Res.string.stateFolderNameEmpty)
    val segment = stringResource(Res.string.stateFolderNameSegment)
    val characters = stringResource(Res.string.stateFolderNameCharacters)
    KitNewProjectDialog(
        initial = "",
        working = state.creatingProject,
        validate = { name ->
            when (io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName.problem(name)) {
                io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderNameProblem.Empty -> empty
                io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderNameProblem.NotSingleSegment -> segment
                io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderNameProblem.InvalidCharacters -> characters
                null -> null
            }
        },
        onSubmit = { name -> actions.createProject(name) },
        onDismissRequest = { actions.createProject(null) },
    )
}
