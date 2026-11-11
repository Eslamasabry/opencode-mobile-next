package io.github.eslamasabry.oc.feature.git.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.git.GitAction
import io.github.eslamasabry.oc.engine.git.GitActionNote
import io.github.eslamasabry.oc.engine.git.GitBusy
import io.github.eslamasabry.oc.engine.git.GitChangeKind
import io.github.eslamasabry.oc.engine.git.GitCheck
import io.github.eslamasabry.oc.engine.git.GitDiffView
import io.github.eslamasabry.oc.engine.git.GitFileChange
import io.github.eslamasabry.oc.engine.git.GitHubRepository
import io.github.eslamasabry.oc.engine.git.GitHubSignIn
import io.github.eslamasabry.oc.engine.git.GitOperation
import io.github.eslamasabry.oc.engine.git.GitPageView
import io.github.eslamasabry.oc.engine.git.GitProblem
import io.github.eslamasabry.oc.engine.git.GitRemoteKind
import io.github.eslamasabry.oc.engine.git.GitSnapshot
import io.github.eslamasabry.oc.engine.git.gitCloneTarget
import io.github.eslamasabry.oc.engine.git.gitRemoteAddressProblem
import io.github.eslamasabry.oc.engine.git.isValidGitHubRepositoryName
import io.github.eslamasabry.oc.engine.git.suggestGitHubRepositoryName
import io.github.eslamasabry.oc.engine.git.isValidGitBranchName
import io.github.eslamasabry.oc.engine.git.isValidGitFolderName
import io.github.eslamasabry.oc.engine.git.isValidGitRemoteName
import io.github.eslamasabry.oc.engine.git.suggestGitCloneFolder
import io.github.eslamasabry.oc.feature.git.presentation.GitActions
import io.github.eslamasabry.oc.feature.git.presentation.GitConfirm
import io.github.eslamasabry.oc.feature.git.presentation.GitSheet
import io.github.eslamasabry.oc.feature.git.presentation.GitUi
import io.github.eslamasabry.oc.feature.git.presentation.GitWay
import io.github.eslamasabry.oc.feature.git.presentation.gitSavedNeedsSignIn
import io.github.eslamasabry.oc.feature.git.presentation.gitWayForward
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitOneTimeCode
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Git page of a conversation's project (07 D09; Flutter lib/ui/git/git_sheet*.dart). Top to
 * bottom: the branch, Get latest and Send as visible rows, the changed files (tick to include, tap
 * to see the diff), the save block, recent saves, and the GitHub sign-in. Every control is a kit
 * part; every word comes from :ui:words; Git's own text only ever appears under Details.
 */
@Composable
public fun GitScreen(state: GitUi, actions: GitActions, serverTools: @Composable () -> Unit = {}) {
    val message = remember { TextFieldState() }
    var messageEdited by remember { mutableStateOf(false) }
    val defaultMessage =
        suggestedMessage(
            state.snapshot?.status?.files.orEmpty().filter { it.path in state.selected }
        )
    LaunchedEffect(defaultMessage) {
        if (!messageEdited) {
            message.edit { replace(0, length, defaultMessage) }
        }
    }
    LaunchedEffect(state.generation) {
        val saved =
            state.savedNotSent ||
                (state.result?.let { it.ok && it.note != null && it.commit != null } ?: false)
        if (saved) {
            message.edit { replace(0, length, "") }
            messageEdited = false
        }
    }
    val holdReason = state.busy?.let { gitBusyText(it) }
    val enabled = if (holdReason != null) KitEnabled.Disabled(holdReason) else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.gitSheetTitle),
                subtitle = state.snapshot?.status?.branch?.head,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.gitActionRefresh),
                            actions::refresh,
                            enabled = state.busy == null,
                            disabledReason = holdReason,
                            testTag = "git-refresh",
                        )
                    ),
            )
        },
        status = statusLine(state, actions),
        loading = state.view == GitPageView.Loading || state.working,
        loadingLabel =
            if (state.repair.running) stringResource(Res.string.linuxGitRepairWorking)
            else holdReason ?: stringResource(Res.string.gitBusyRefresh),
        width = KitScreenWidth.Reading,
        page = true,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            serverTools()
            GitRepairPanel(state, actions)
            when (val view = state.view) {
                GitPageView.Loading -> Unit
                is GitPageView.Unavailable ->
                    KitStateView(
                        kind = KitStateKind.Missing,
                        title = stringResource(Res.string.gitSheetTitle),
                        body = gitUnavailableText(view.reason),
                        modifier = Modifier.testTag("git-unavailable"),
                    )
                GitPageView.NotRepository -> {
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = stringResource(Res.string.gitNotRepositoryTitle),
                        body = stringResource(Res.string.gitNotRepositoryBody),
                    )
                    KitRowGroup {
                        // Acts in place (no page opens), so no chevron.
                        KitRow(
                            title = stringResource(Res.string.gitInitializeProject),
                            leading = KitRowLeading.Glyph(KitIcons.branch),
                            supporting = stringResource(Res.string.gitInitializeProjectHint),
                            enabled = enabled.isEnabled,
                            disabledReason = (enabled as? KitEnabled.Disabled)?.reason,
                            onClick = actions::initialize,
                            modifier = Modifier.testTag("git-row-initialize"),
                        )
                        KitRow(
                            title = stringResource(Res.string.gitCloneRow),
                            leading = KitRowLeading.Glyph(KitIcons.cloud),
                            supporting = stringResource(Res.string.gitCloneRowHint),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::openClone,
                            modifier = Modifier.testTag("git-clone-row"),
                        )
                    }
                    state.result?.failure?.let { failure ->
                        Gutter {
                            KitErrorNotice(
                                gitProblemText(failure.problem),
                                details = state.resultDetails,
                                detailsLabel = stringResource(Res.string.kitDetails),
                                copyLabel = stringResource(Res.string.kitCopyDetails),
                            )
                        }
                    }
                }
                is GitPageView.Failed ->
                    Gutter {
                        KitErrorNotice(
                            gitProblemText(view.failure.problem),
                            details = state.viewDetails,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            retry =
                                KitLineAction(
                                    stringResource(Res.string.commonRetry),
                                    actions::refresh,
                                ),
                        )
                    }
                is GitPageView.Ready ->
                    Ready(view.snapshot, state, actions, enabled, message) { messageEdited = true }
            }
            // The app's GitHub sign-in only where a push would use it; servers push with their own
            // (B-SEC-082).
            if (state.view !is GitPageView.Loading && state.offersGitHubSignIn)
                GitHubSection(state, actions)
        }
        BranchSheet(state, actions)
        RemotesSheet(state, actions)
        PublishSheet(state, actions)
        CloneSheet(state, actions)
        DiffSheet(state, actions, enabled)
        KitConfirm(
            confirmSpec(state.confirm),
            onConfirm = actions::confirm,
            onCancel = actions::dismissConfirm,
        )
    }
}

@Composable
private fun Gutter(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp)) {
        content()
    }
}

/**
 * One status line: the running action, else the last success. Failures are notices in the body
 * (with Details).
 */
@Composable
private fun statusLine(state: GitUi, actions: GitActions): KitStatus? {
    val busy = state.busy
    if (busy != null)
        return KitStatus(
            KitStatusKind.Work,
            gitBusyText(busy),
            id = "git-busy-$busy",
            tone = StatusTone.Progress,
        )
    val result = state.result ?: return null
    if (!result.ok) return null
    return KitStatus(
        KitStatusKind.Info,
        gitDoneText(result),
        id = "git-done-${state.generation}",
        tone = StatusTone.Ok,
        onDismiss = actions::dismissResult,
        dismissLabel = stringResource(Res.string.kitSheetClose),
    )
}

@Composable
private fun Ready(
    snapshot: GitSnapshot,
    state: GitUi,
    actions: GitActions,
    enabled: KitEnabled,
    message: TextFieldState,
    onMessageEdited: () -> Unit,
) {
    val branch = snapshot.status.branch
    // F: lib/state/git_page_controller.dart:153-160
    val operationInProgress = snapshot.operation != GitOperation.None
    val branchEnabled =
        if (operationInProgress)
            KitEnabled.Disabled(stringResource(Res.string.gitProblemOperationInProgress))
        else enabled
    val canPull = !branch.detached && branch.hasUpstream && !operationInProgress
    val noPullRemote = !snapshot.hasRemote && !branch.detached && !operationInProgress
    val pullReason =
        (enabled as? KitEnabled.Disabled)?.reason
            ?: when {
                operationInProgress -> stringResource(Res.string.gitProblemOperationInProgress)
                branch.detached -> stringResource(Res.string.gitProblemDetachedHead)
                else -> stringResource(Res.string.gitPullNotOnRemote)
            }
    val ownSignIn =
        state.placeName?.let { stringResource(Res.string.gitUsesOwnSignInOn, it) }
            ?: stringResource(Res.string.gitUsesOwnSignIn)
    // A failed action, or a failed refresh over the last snapshot (B-NAV-020): plain words, Git's
    // text under Details.
    val failed = state.result?.failure
    // Set identity puts the person in the identity block's first empty field; a focused field scrolls into view.
    val identityField = remember { FocusRequester() }
    if (failed != null) {
        Gutter {
            val place = placeOf(state, snapshot)
            val words = gitProblemText(failed.problem, state.offersGitHubSignIn)
            // Flutter `_wayForward`: the one step that fixes it, next to the words.
            val way =
                gitWayForward(
                    failed.problem,
                    state.offersGitHubSignIn,
                    canSignIn = state.signIn is GitHubSignIn.SignedOut,
                )
            val close = KitLineAction(stringResource(Res.string.kitSheetClose), actions::dismissResult)
            val step =
                when (way) {
                    GitWay.SignIn -> KitLineAction(stringResource(Res.string.gitHubSignIn), actions::signIn)
                    GitWay.GetLatest -> KitLineAction(stringResource(Res.string.gitGetLatest), actions::pull)
                    GitWay.SetIdentity ->
                        KitLineAction(
                            stringResource(Res.string.gitIdentityTitle),
                            { identityField.requestFocus() },
                        )
                    null -> null
                }
            KitErrorNotice(
                when {
                    state.savedNotSent &&
                        gitSavedNeedsSignIn(failed.problem, state.offersGitHubSignIn, place.isNotEmpty()) ->
                        stringResource(Res.string.gitSavedSignInFirst, place)
                    state.savedNotSent -> stringResource(Res.string.gitSavedNotSent, words)
                    else -> words
                },
                details = state.resultDetails,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                retry = step ?: close,
                dismiss = close.takeIf { step != null },
            )
        }
    }
    state.refreshFailure?.let { failure ->
        Gutter {
            KitErrorNotice(
                gitProblemText(failure.problem),
                details = state.refreshDetails,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                retry = KitLineAction(stringResource(Res.string.commonRetry), actions::refresh),
            )
        }
    }
    gitOperationText(snapshot.operation)?.let {
        Gutter { KitNotice(it, tone = StatusTone.Attention) }
    }

    // Branch, Get latest and Send: the obvious actions as visible rows.
    KitRowGroup(label = stringResource(Res.string.gitDetailsBranch)) {
        KitRow(
            title = branch.head ?: stringResource(Res.string.gitDetachedHead),
            leading = KitRowLeading.Glyph(KitIcons.branch),
            supporting = if (branch.unborn) stringResource(Res.string.gitNoCommitsYet) else null,
            trailing = KitRowTrailing.Value(stringResource(Res.string.gitSwitchRow)),
            enabled = branchEnabled.isEnabled,
            disabledReason = (branchEnabled as? KitEnabled.Disabled)?.reason,
            onClickLabel = stringResource(Res.string.gitActionSwitchBranch),
            onClick = actions::openBranches,
            modifier = Modifier.testTag("git-branch-row"),
        )
        KitRow(
            title = stringResource(Res.string.gitGetLatest),
            leading = KitRowLeading.Glyph(KitIcons.download),
            supporting =
                when {
                    !snapshot.hasRemote -> stringResource(Res.string.gitPullNoRemote)
                    !branch.hasUpstream -> stringResource(Res.string.gitPullNotOnRemote)
                    state.check == GitCheck.Done && branch.behind > 0 ->
                        pluralStringResource(
                            Res.plurals.gitGetNewFrom,
                            branch.behind,
                            branch.behind,
                            placeOf(state, snapshot),
                            branch.upstream.orEmpty(),
                        )
                    // "Up to date" only after the quiet look at the remote (Flutter
                    // checkForUpdates).
                    state.check == GitCheck.Done -> stringResource(Res.string.gitStateUpToDate)
                    state.check == GitCheck.Failed -> stringResource(Res.string.gitCheckFailed)
                    else -> stringResource(Res.string.gitChecking)
                },
            enabled = enabled.isEnabled && (canPull || noPullRemote),
            disabledReason =
                if (enabled.isEnabled && (canPull || noPullRemote)) null else pullReason,
            trailing = if (noPullRemote) KitRowTrailing.Chevron else KitRowTrailing.None,
            onClick = if (noPullRemote) actions::openRemotes else actions::pull,
            modifier = Modifier.testTag("git-pull-row"),
        )
        KitRow(
            title = stringResource(Res.string.gitSend),
            leading = KitRowLeading.Glyph(KitIcons.upload),
            supporting =
                when {
                    !snapshot.hasRemote -> stringResource(Res.string.gitSendNoRemote)
                    !branch.hasUpstream -> stringResource(Res.string.gitStateFirstSend)
                    branch.ahead > 0 ->
                        pluralStringResource(Res.plurals.gitAhead, branch.ahead, branch.ahead)
                    else -> stringResource(Res.string.gitStateNothingToSend)
                },
            enabled = enabled.isEnabled && !branch.detached && !branch.unborn,
            // A disabled row always says why (KitRow refuses a silent one).
            disabledReason =
                (enabled as? KitEnabled.Disabled)?.reason
                    ?: when {
                        branch.detached -> stringResource(Res.string.gitDetachedHead)
                        branch.unborn -> stringResource(Res.string.gitNoCommitsYet)
                        else -> null
                    },
            onClick = if (!snapshot.hasRemote) actions::openRemotes else actions::push,
            modifier = Modifier.testTag("git-push-row"),
        )
    }
    // Create a GitHub repository: only while signed in to the app's GitHub and nothing is connected yet.
    if (!snapshot.hasRemote && state.offersGitHubSignIn && state.signIn is GitHubSignIn.SignedIn) {
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.gitPublishToGitHub),
                leading = KitRowLeading.Glyph(KitIcons.github),
                supporting = stringResource(Res.string.gitPublishToGitHubHint),
                trailing = KitRowTrailing.Chevron,
                enabled = enabled.isEnabled,
                disabledReason = (enabled as? KitEnabled.Disabled)?.reason,
                onClick = actions::openPublish,
                modifier = Modifier.testTag("git-row-publish-github"),
            )
        }
    }
    // Remotes and Clone: visible rows, so a project without a remote can get one (B-BUILD-014).
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.gitRemotesRow),
            leading = KitRowLeading.Glyph(KitIcons.link),
            supporting =
                if (snapshot.remotes.isEmpty()) stringResource(Res.string.gitRemotesRowNone)
                else snapshot.remotes.joinToString(", ") { it.name },
            trailing = KitRowTrailing.Chevron,
            enabled = enabled.isEnabled,
            disabledReason = (enabled as? KitEnabled.Disabled)?.reason,
            onClick = actions::openRemotes,
            modifier = Modifier.testTag("git-remotes-row"),
        )
        KitRow(
            title = stringResource(Res.string.gitCloneRow),
            supporting = stringResource(Res.string.gitCloneRowHint),
            trailing = KitRowTrailing.Chevron,
            enabled = enabled.isEnabled,
            disabledReason = (enabled as? KitEnabled.Disabled)?.reason,
            onClick = actions::openClone,
            modifier = Modifier.testTag("git-clone-row"),
        )
    }
    // Every host today pushes with the Git sign-in set up where the project lives (the app never
    // hands over its token).
    Gutter { KitText(ownSignIn, role = KitTextRole.Supporting) }

    // Changed files: tick to include in the next save; "Changes" opens the file's diff.
    val files = snapshot.status.files
    if (files.isEmpty()) {
        Gutter { KitText(stringResource(Res.string.gitCleanBody)) }
    } else {
        KitRowGroup(
            label = pluralStringResource(Res.plurals.gitSaveChangesCount, files.size, files.size)
        ) {
            for (file in files) {
                val supporting =
                    listOfNotNull(gitChangeLabel(file), gitCounts(file)).joinToString(" · ")
                if (file.isConflicted) {
                    KitRow(
                        title = file.path,
                        supporting = supporting,
                        titleIsFileName = true,
                        onClick = { actions.openDiff(file.path) },
                    )
                } else {
                    KitSwitchRow(
                        title = file.path,
                        checked = file.path in state.selected,
                        supporting = supporting,
                        enabled = enabled,
                        onCheckedChange = { actions.toggleFile(file.path) },
                        modifier = Modifier.testTag("git-file-${file.path}"),
                    )
                    KitRow(
                        title = stringResource(Res.string.chatUiChanges),
                        supporting = file.path,
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.openDiff(file.path) },
                        modifier = Modifier.testTag("git-diff-${file.path}"),
                    )
                }
            }
        }
        if (snapshot.status.hasConflicts) {
            val count = snapshot.status.files.count { it.isConflicted }
            Gutter {
                KitNotice(
                    pluralStringResource(Res.plurals.gitConflictCount, count, count),
                    tone = StatusTone.Attention,
                )
            }
            // Flutter `_askConflictsRow`: one tap sends the agent the files to resolve, from this conversation.
            if (state.canAskAgent) {
                val paths = snapshot.status.files.filter { it.isConflicted }.map { it.path }
                val prompt = stringResource(Res.string.gitAskAgentFixConflictsPrompt, paths.joinToString(", "))
                KitRow(
                    title = stringResource(Res.string.gitAskAgentFixConflicts),
                    supporting = pluralStringResource(Res.plurals.gitConflictCount, count, count),
                    leading = KitRowLeading.Glyph(KitIcons.sparkle),
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.askAgent(prompt) },
                    modifier = Modifier.testTag("git-row-ask-conflicts"),
                )
            }
            if (state.askFailed) {
                Gutter {
                    KitErrorNotice(
                        stringResource(Res.string.chatSendFailedRefused),
                        retry = KitLineAction(stringResource(Res.string.kitSheetClose), actions::dismissAskFailure),
                    )
                }
            }
        }
        // The save block. Steer, never queue: every button holds while an action runs.
        Gutter {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitField(
                    message,
                    stringResource(Res.string.gitCommitMessageHint),
                    kind = KitFieldKind.Multiline,
                    enabled = enabled,
                    modifier = Modifier.testTag("git-message"),
                    onTextChanged = { onMessageEdited() },
                )
                // Flutter git_sheet_steps.dart: the agent already said what it did; one tap makes that the message,
                // which can still be edited.
                state.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    KitButton(
                        stringResource(Res.string.gitWriteItForMe),
                        {
                            message.edit { replace(0, length, summary) }
                            onMessageEdited()
                        },
                        role = KitButtonRole.Tertiary,
                        icon = KitIcons.sparkle,
                        enabled = enabled,
                        modifier = Modifier.testTag("git-save-write-for-me"),
                    )
                }
                val known = files.filterNot { it.isConflicted }.map { it.path }.toSet()
                val count = state.selected.count { it in known }
                val saveEnabled =
                    when {
                        snapshot.status.hasConflicts ->
                            KitEnabled.Disabled(stringResource(Res.string.gitProblemConflicts))
                        count == 0 ->
                            KitEnabled.Disabled(
                                stringResource(Res.string.gitProblemNothingSelected)
                            )
                        message.text.toString().trim().isEmpty() ->
                            KitEnabled.Disabled(stringResource(Res.string.gitProblemEmptyMessage))
                        else -> enabled
                    }
                KitButton(
                    pluralStringResource(Res.plurals.gitSaveButton, count, count),
                    { actions.commit(message.text.toString()) },
                    enabled = saveEnabled,
                    working = state.busy == GitBusy.Commit,
                    modifier = Modifier.testTag("git-save"),
                )
                if (snapshot.hasRemote && !branch.detached && !branch.unborn) {
                    KitButton(
                        stringResource(Res.string.gitSaveAndSendTitle),
                        { actions.commitAndPush(message.text.toString()) },
                        role = KitButtonRole.Secondary,
                        enabled = saveEnabled,
                        modifier = Modifier.testTag("git-save-send"),
                    )
                }
            }
        }
    }

    // Who the saves are signed by, when Git has no name yet (or a placeholder).
    if (
        snapshot.identity?.needsSetup == true || state.result?.problem == GitProblem.IdentityMissing
    )
        IdentityBlock(snapshot, actions, enabled, identityField)

    // Recent saves.
    if (snapshot.commits.isNotEmpty()) {
        KitRowGroup(label = stringResource(Res.string.searchRecentTitle)) {
            for (commit in snapshot.commits.take(RECENT)) {
                KitRow(title = commit.subject, supporting = commit.shortId, supportingMaxLines = 1)
            }
        }
    }
}

private const val RECENT = 5

@Composable
private fun placeOf(state: GitUi, snapshot: GitSnapshot): String {
    val remote = snapshot.pushRemote
    if (remote?.isGitHub == true) return stringResource(Res.string.gitPlaceGitHub)
    val address = remote?.address
    if (address != null) {
        val scp = Regex("^[^@/\\s]+@([^:/\\s]+):").find(address)?.groupValues?.get(1)
        val host =
            scp
                ?: address
                    .substringAfter("://", "")
                    .substringBefore('/')
                    .substringAfterLast('@')
                    .substringBefore(':')
        if (host.isNotEmpty()) return host
    }
    return remote?.name ?: state.placeName.orEmpty()
}

@Composable
private fun IdentityBlock(snapshot: GitSnapshot, actions: GitActions, enabled: KitEnabled, focus: FocusRequester) {
    val name = remember { TextFieldState(snapshot.identity?.name.orEmpty()) }
    val email = remember { TextFieldState(snapshot.identity?.email.orEmpty()) }
    val typedName = name.text.toString().trim()
    val typedEmail = email.text.toString().trim()
    val ready = typedName.isNotEmpty() && '@' in typedEmail
    val toEmail = remember { snapshot.identity?.name.isNullOrBlank().not() && snapshot.identity?.email.isNullOrBlank() }
    Gutter {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitText(stringResource(Res.string.gitIdentityTitle), role = KitTextRole.Title)
            KitText(stringResource(Res.string.gitIdentityWhy), role = KitTextRole.Supporting)
            KitField(
                name,
                stringResource(Res.string.gitIdentityName),
                enabled = enabled,
                modifier = Modifier.testTag("git-identity-name"),
                focusRequester = focus.takeUnless { toEmail },
            )
            KitField(
                email,
                stringResource(Res.string.gitIdentityEmail),
                modifier = Modifier.testTag("git-identity-email"),
                kind = KitFieldKind.Text,
                enabled = enabled,
                focusRequester = focus.takeIf { toEmail },
            )
            KitButton(
                stringResource(Res.string.gitActionSaveIdentity),
                { actions.setIdentity(typedName, typedEmail) },
                role = KitButtonRole.Secondary,
                enabled =
                    if (ready) enabled
                    else KitEnabled.Disabled(stringResource(Res.string.gitIdentityEmail)),
            )
        }
    }
}

@Composable
private fun GitHubSection(state: GitUi, actions: GitActions) {
    KitRowGroup(
        label = stringResource(Res.string.gitHubSignInTitle),
        note = stringResource(Res.string.gitHubPushOverHttps),
    ) {
        when (val sign = state.signIn) {
            GitHubSignIn.Loading ->
                KitRow(
                    title = stringResource(Res.string.gitHubSignInTitle),
                    enabled = false,
                    disabledReason = stringResource(Res.string.gitChecking),
                )
            is GitHubSignIn.SignedOut ->
                KitRow(
                    title = stringResource(Res.string.gitHubSignIn),
                    supporting =
                        sign.problem?.let { gitHubProblemText(it) }
                            ?: stringResource(Res.string.gitHubSignedOutHint),
                    supportingMaxLines = 3,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::signIn,
                    modifier = Modifier.testTag("github-sign-in"),
                )
            GitHubSignIn.RequestingCode -> {
                Gutter {
                    KitNotice(
                        stringResource(Res.string.gitHubRequestingCode),
                        tone = StatusTone.Progress,
                    )
                }
                KitRow(
                    title = stringResource(Res.string.gitHubCancel),
                    onClick = actions::cancelSignIn,
                )
            }
            is GitHubSignIn.WaitingForUser -> {
                Gutter {
                    Column {
                        KitText(stringResource(Res.string.gitHubEnterCode))
                        Spacer(Modifier.height(Space.S3.dp))
                        KitOneTimeCode(
                            sign.userCode,
                            stringResource(Res.string.gitHubCopyCode),
                            Modifier.testTag("github-code"),
                        )
                        Spacer(Modifier.height(Space.S3.dp))
                        KitText(
                            stringResource(Res.string.gitHubWaiting),
                            role = KitTextRole.Supporting,
                        )
                    }
                }
                KitRow(
                    title = stringResource(Res.string.gitHubOpenGitHub),
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::openGitHub,
                )
                KitRow(
                    title = stringResource(Res.string.gitHubCancel),
                    onClick = actions::cancelSignIn,
                )
            }
            GitHubSignIn.Finishing ->
                Gutter {
                    KitNotice(
                        stringResource(Res.string.gitHubFinishing),
                        tone = StatusTone.Progress,
                    )
                }
            is GitHubSignIn.SignedIn -> {
                KitRow(
                    title = stringResource(Res.string.gitHubSignedInAs, sign.login),
                    leading = KitRowLeading.Glyph(KitIcons.globe),
                )
                KitRow(
                    title = stringResource(Res.string.gitHubManageAccess),
                    leading = KitRowLeading.Glyph(KitIcons.link),
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::manageGitHubAccess,
                )
                KitRow(
                    title = stringResource(Res.string.gitHubSignOut),
                    onClick = actions::askSignOut,
                    modifier = Modifier.testTag("github-sign-out"),
                )
            }
        }
    }
    val details = state.signInDetails
    if (details != null) {
        Gutter {
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                text = details,
            )
        }
    }
}

@Composable
private fun BranchSheet(state: GitUi, actions: GitActions) {
    val snapshot = state.snapshot
    val newName = remember { TextFieldState() }
    val name = newName.text.toString().trim()
    val invalid = name.isNotEmpty() && !isValidGitBranchName(name)
    val head = snapshot?.status?.branch?.head
    val busyReason = state.busy?.let { gitBusyText(it) }
    val hold = busyReason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled
    val reason =
        when {
            name.isEmpty() -> stringResource(Res.string.gitBranchNameHint)
            invalid -> stringResource(Res.string.gitProblemInvalidBranchName)
            else -> null
        }
    KitSheet(
        visible = state.sheet == GitSheet.Branches && snapshot != null,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.gitSwitchRow),
        subtitle = head?.let { stringResource(Res.string.gitSwitchRowOn, it) },
        closeLabel = stringResource(Res.string.kitSheetClose),
        loading = state.working,
        primary =
            KitAction(
                head?.let { stringResource(Res.string.gitNewBranchFrom, it) }
                    ?: stringResource(Res.string.gitActionCreateBranch),
                { actions.createBranch(name) },
                icon = KitIcons.add,
                working = state.busy == GitBusy.CreateBranch,
                enabled = reason?.let { KitEnabled.Disabled(it) } ?: hold,
            ),
    ) {
        val branches = snapshot?.branches ?: return@KitSheet
        val choices =
            branches.local.map { branch ->
                KitChoice(
                    branch.name,
                    branch.name,
                    supporting =
                        if (branch.current) stringResource(Res.string.gitBranchCurrent)
                        else branch.upstream,
                    enabled = hold,
                )
            } +
                branches.remoteOnly.map { branch ->
                    KitChoice(
                        branch.localName,
                        branch.localName,
                        supporting =
                            stringResource(
                                Res.string.gitBranchRemoteOnly,
                                branch.remoteName.orEmpty(),
                            ),
                        enabled = hold,
                    )
                }
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            SheetResult(state, actions, GitAction.SwitchBranch)
            SheetResult(state, actions, GitAction.CreateBranch)
            KitChoiceList(
                choices,
                head,
                stringResource(Res.string.gitSwitchRow),
                modifier = Modifier.testTag("git-branch-list"),
                empty = {},
                onSelect = { if (it != head && !state.working) actions.switchBranch(it) },
            )
            KitField(
                newName,
                stringResource(Res.string.gitBranchNameHint),
                enabled = hold,
                error =
                    if (invalid) stringResource(Res.string.gitProblemInvalidBranchName) else null,
                modifier = Modifier.testTag("git-new-branch"),
            )
        }
    }
}

@Composable
private fun RemotesSheet(state: GitUi, actions: GitActions) {
    val snapshot = state.snapshot
    val name = remember { TextFieldState() }
    val url = remember { TextFieldState() }
    val busyReason = state.busy?.let { gitBusyText(it) }
    val hold = busyReason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled
    val remotes = snapshot?.remotes.orEmpty()
    val typedName = name.text.toString().trim()
    val typedUrl = url.text.toString().trim()
    val nameProblem =
        if (typedName.isEmpty() || isValidGitRemoteName(typedName)) null
        else GitProblem.InvalidRemoteName
    val urlProblem = if (typedUrl.isEmpty()) null else gitRemoteAddressProblem(typedUrl)
    val ready =
        typedName.isNotEmpty() && typedUrl.isNotEmpty() && nameProblem == null && urlProblem == null
    // A remote that was just added clears the fields once (the new one shows in the list above).
    LaunchedEffect(state.generation) {
        if (state.result?.note == GitActionNote.RemoteAdded) {
            name.edit { replace(0, length, "") }
            url.edit { replace(0, length, "") }
        }
    }
    KitSheet(
        visible = state.sheet == GitSheet.Remotes && snapshot != null,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.gitRemotesTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        loading = state.working,
        primary =
            KitAction(
                stringResource(Res.string.gitActionAddRemote),
                { actions.addRemote(typedName, typedUrl) },
                icon = KitIcons.add,
                working = state.busy == GitBusy.AddRemote,
                enabled =
                    when {
                        busyReason != null -> KitEnabled.Disabled(busyReason)
                        !ready ->
                            KitEnabled.Disabled(stringResource(Res.string.gitRemoteAddNeedsFields))
                        else -> KitEnabled.Enabled
                    },
            ),
    ) {
        SheetResult(state, actions, GitAction.AddRemote)
        if (remotes.isEmpty()) {
            KitText(stringResource(Res.string.gitRemotesEmpty), role = KitTextRole.Supporting)
        } else {
            KitRowGroup {
                for (remote in remotes) {
                    KitRow(
                        title = remote.name,
                        supporting = remote.address,
                        supportingMaxLines = 3,
                        modifier = Modifier.testTag("git-remote-${remote.name}"),
                    )
                }
            }
        }
        if (state.offersGitHubSignIn && remotes.any { it.kind == GitRemoteKind.GitHubSsh })
            KitText(
                stringResource(Res.string.gitHubPushOverHttps),
                modifier = Modifier.padding(top = Space.S2.dp),
                role = KitTextRole.Supporting,
            )
        // Fill the address from the signed-in account's repositories, so nobody types or pastes the URL.
        // It unfolds (and folds) quietly when the sign-in changes; reduced motion shows it at once.
        KitReveal(state.signIn is GitHubSignIn.SignedIn) {
            Column(
                Modifier.padding(top = Space.S3.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitButton(
                    label = stringResource(Res.string.gitRemotePickGitHub),
                    onClick = actions::pickGitHubRepository,
                    role = KitButtonRole.Tertiary,
                    icon = KitIcons.github,
                    enabled =
                        if (state.repoPicker.loading)
                            KitEnabled.Disabled(stringResource(Res.string.gitRemotePickGitHub))
                        else hold,
                    working = state.repoPicker.loading,
                    modifier = Modifier.testTag("git-remote-pick-github"),
                )
                KitReveal(state.repoPicker.none) {
                    KitText(
                        stringResource(Res.string.gitRemotePickGitHubNone),
                        role = KitTextRole.Supporting,
                        modifier = Modifier.testTag("git-remote-pick-none"),
                    )
                }
            }
        }
        Column(
            Modifier.padding(top = Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitSectionLabel(stringResource(Res.string.gitRemoteAddTitle))
            KitField(
                name,
                enabled = hold,
                label = stringResource(Res.string.gitRemoteNameLabel),
                placeholder = if (remotes.isEmpty()) "origin" else null,
                error = nameProblem?.let { gitProblemText(it) },
                modifier = Modifier.testTag("git-remote-name"),
            )
            KitField(
                url,
                enabled = hold,
                label = stringResource(Res.string.gitRemoteAddressLabel),
                kind = KitFieldKind.Url,
                placeholder = "https://github.com/owner/name",
                error = urlProblem?.let { gitProblemText(it) },
                modifier = Modifier.testTag("git-remote-url"),
            )
        }
    }
    RepositoryChoiceSheet(state, actions) { picked ->
        if (name.text.toString().trim().isEmpty()) name.edit { replace(0, length, "origin") }
        url.edit { replace(0, length, picked.remoteUrl) }
    }
}

/** The GitHub repositories to pick a remote from; picking fills the Remotes sheet's fields and closes this list. */
@Composable
private fun RepositoryChoiceSheet(
    state: GitUi,
    actions: GitActions,
    onPick: (GitHubRepository) -> Unit,
) {
    val repos = state.repoPicker.choices.orEmpty()
    val privateWord = stringResource(Res.string.gitRemoteRepoPrivate)
    KitSheet(
        visible = state.repoPicker.choices != null && state.sheet == GitSheet.Remotes,
        onDismissRequest = actions::closeRepositoryChoices,
        title = stringResource(Res.string.gitRemotePickGitHubTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        KitChoiceList(
            repos.map {
                KitChoice(
                    it.fullName,
                    KitBidi.ltr(it.fullName),
                    supporting = if (it.isPrivate) privateWord else null,
                )
            },
            null,
            stringResource(Res.string.gitRemotePickGitHubTitle),
            modifier = Modifier.testTag("git-remote-github-repos"),
            empty = {},
            onSelect = { fullName ->
                repos.firstOrNull { it.fullName == fullName }?.let(onPick)
                actions.closeRepositoryChoices()
            },
        )
    }
}

/** Create a repository on GitHub for this project and connect it as the remote; nothing is sent yet. */
@Composable
private fun PublishSheet(state: GitUi, actions: GitActions) {
    val visible = state.sheet == GitSheet.Publish && state.snapshot != null
    val name = remember { TextFieldState() }
    var isPrivate by remember { mutableStateOf(true) }
    // A fresh sheet starts from the folder's name, private (the safe choice).
    LaunchedEffect(visible) {
        if (visible) {
            val suggestion = suggestGitHubRepositoryName(state.directory)
            name.edit { replace(0, length, suggestion) }
            isPrivate = true
        }
    }
    val typed = name.text.toString().trim()
    val invalid = typed.isNotEmpty() && !isValidGitHubRepositoryName(typed)
    val busyReason = state.busy?.let { gitBusyText(it) }
    val hold = busyReason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled
    val invalidWords = stringResource(Res.string.gitProblemInvalidGitHubRepositoryName)
    val hint = stringResource(Res.string.gitPublishNameHint)
    KitSheet(
        visible = visible,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.gitPublishTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        loading = state.working,
        primary =
            KitAction(
                stringResource(Res.string.gitPublishButton),
                { actions.publish(typed, isPrivate) },
                icon = KitIcons.github,
                working = state.busy == GitBusy.Publish,
                enabled =
                    when {
                        busyReason != null -> KitEnabled.Disabled(busyReason)
                        typed.isEmpty() -> KitEnabled.Disabled(hint)
                        invalid -> KitEnabled.Disabled(invalidWords)
                        else -> KitEnabled.Enabled
                    },
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            SheetResult(state, actions, GitAction.Publish)
            KitField(
                name,
                enabled = hold,
                label = stringResource(Res.string.gitPublishName),
                error = if (invalid) invalidWords else null,
                modifier = Modifier.testTag("git-publish-name"),
            )
            KitSectionLabel(stringResource(Res.string.gitPublishVisibility))
            KitSegmented(
                listOf(
                    KitSegment(true, stringResource(Res.string.gitPublishPrivate)),
                    KitSegment(false, stringResource(Res.string.gitPublishPublic)),
                ),
                isPrivate,
                stringResource(Res.string.gitPublishVisibility),
                modifier = Modifier.testTag("git-publish-visibility"),
                enabled = hold,
                onChange = { isPrivate = it },
            )
            // The explanation follows the choice with a quick cross-fade (instant under reduced motion).
            KitSwap(isPrivate) { shownPrivate ->
                KitText(
                    stringResource(
                        if (shownPrivate) Res.string.gitPublishPrivateHint
                        else Res.string.gitPublishPublicHint
                    ),
                    role = KitTextRole.Supporting,
                    modifier = Modifier.testTag("git-publish-visibility-hint"),
                )
            }
        }
    }
}

@Composable
private fun CloneSheet(state: GitUi, actions: GitActions) {
    val url = remember { TextFieldState() }
    val folder = remember { TextFieldState() }
    val busyReason = state.busy?.let { gitBusyText(it) }
    val hold = busyReason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled
    val typedUrl = url.text.toString().trim()
    var folderTouched by remember { mutableStateOf(false) }
    val typedFolder = folder.text.toString().trim()
    val urlProblem = if (typedUrl.isEmpty()) null else gitRemoteAddressProblem(typedUrl)
    val folderProblem =
        if (typedFolder.isEmpty() || isValidGitFolderName(typedFolder)) null
        else GitProblem.InvalidFolderName
    val ready =
        typedUrl.isNotEmpty() &&
            urlProblem == null &&
            typedFolder.isNotEmpty() &&
            folderProblem == null
    LaunchedEffect(state.generation) {
        if (state.result?.note == GitActionNote.Cloned) {
            url.edit { replace(0, length, "") }
            folder.edit { replace(0, length, "") }
            folderTouched = false
        }
    }
    KitSheet(
        visible = state.sheet == GitSheet.Clone && state.directory != null,
        onDismissRequest = actions::closeSheet,
        title = stringResource(Res.string.gitCloneRow),
        closeLabel = stringResource(Res.string.kitSheetClose),
        loading = state.working,
        primary =
            KitAction(
                stringResource(Res.string.gitCloneButton),
                { actions.clone(typedUrl, typedFolder) },
                icon = KitIcons.cloud,
                working = state.busy == GitBusy.Clone,
                enabled =
                    when {
                        busyReason != null -> KitEnabled.Disabled(busyReason)
                        !ready ->
                            KitEnabled.Disabled(stringResource(Res.string.gitCloneAddressLabel))
                        else -> KitEnabled.Enabled
                    },
            ),
    ) {
        SheetResult(state, actions, GitAction.Clone)
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitField(
                url,
                enabled = hold,
                label = stringResource(Res.string.gitCloneAddressLabel),
                kind = KitFieldKind.Url,
                placeholder = "https://github.com/owner/name",
                error = urlProblem?.let { gitProblemText(it) },
                modifier = Modifier.testTag("git-clone-url"),
                onTextChanged = { value ->
                    if (!folderTouched)
                        folder.edit { replace(0, length, suggestGitCloneFolder(value)) }
                },
            )
            KitField(
                folder,
                enabled = hold,
                label = stringResource(Res.string.gitCloneFolderLabel),
                error = folderProblem?.let { gitProblemText(it) },
                helper =
                    if (typedFolder.isEmpty()) null
                    else
                        stringResource(
                            Res.string.gitCloneInto,
                            gitCloneTarget(state.directory ?: "/", typedFolder),
                        ),
                modifier = Modifier.testTag("git-clone-folder"),
                onTextChanged = { folderTouched = true },
            )
        }
    }
}

/**
 * A failed add-remote or clone, said inside its own sheet (plain words, Git's text under Details).
 */
@Composable
private fun SheetResult(state: GitUi, actions: GitActions, action: GitAction) {
    val failed = state.result?.takeIf { it.action == action }?.failure ?: return
    Gutter {
        KitErrorNotice(
            gitProblemText(failed.problem),
            details = state.resultDetails,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            retry = KitLineAction(stringResource(Res.string.kitSheetClose), actions::dismissResult),
        )
    }
}

@Composable
private fun DiffSheet(state: GitUi, actions: GitActions, enabled: KitEnabled) {
    val diff = state.diff
    KitSheet(
        visible = diff != null,
        onDismissRequest = actions::closeDiff,
        title =
            diff?.file?.path?.substringAfterLast('/') ?: stringResource(Res.string.chatUiChanges),
        subtitle = diff?.file?.let { gitChangeLabel(it) },
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
        tertiary =
            if (diff != null && !diff.file.isConflicted) {
                listOf(
                    KitAction(
                        stringResource(Res.string.gitActionDiscard),
                        { actions.askDiscard(diff.file.path) },
                        enabled = enabled,
                        destructive = true,
                    )
                )
            } else {
                emptyList()
            },
    ) {
        when (diff) {
            null -> Unit
            is GitDiffView.Loading -> Gutter { KitText(stringResource(Res.string.gitChecking)) }
            is GitDiffView.Failed ->
                Gutter {
                    KitErrorNotice(
                        stringResource(Res.string.kitDiffLoadFailed),
                        details = null,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
                }
            is GitDiffView.Ready -> {
                val files =
                    remember(diff.patch) { DiffModel.parsePatch(diff.patch, diff.file.path) }
                when {
                    diff.binary -> Gutter { KitText(stringResource(Res.string.kitDiffBinary)) }
                    files.isEmpty() ->
                        Gutter { KitText(stringResource(Res.string.kitDiffNoChanges)) }
                    else -> {
                        for (file in files) {
                            KitDiffView(
                                file,
                                maxLines = MAX_DIFF_LINES,
                                hunkLabel = { _, _, start, end -> "$start–$end" },
                                gapLabel = { hidden -> "… $hidden" },
                                modifier = Modifier.testTag("git-diff-view"),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_DIFF_LINES = 400

@Composable
private fun confirmSpec(confirm: GitConfirm?): KitConfirmSpec? =
    when (confirm) {
        null -> null
        is GitConfirm.Discard ->
            KitConfirmSpec(
                title = stringResource(Res.string.kitDiscardTitle),
                confirmLabel = stringResource(Res.string.gitActionDiscard),
                kind = KitConfirmKind.Destructive,
                consequences = listOf(confirm.file.path),
            )
        GitConfirm.SignOut ->
            KitConfirmSpec(
                title = stringResource(Res.string.gitHubSignOutTitle),
                confirmLabel = stringResource(Res.string.gitHubSignOutConfirm),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.gitHubSignOutBody),
            )
    }

/**
 * A truthful editable save message follows the chosen filenames until the person writes their own.
 */
@Composable
private fun suggestedMessage(files: List<GitFileChange>): String {
    fun name(file: GitFileChange): String = file.path.substringAfterLast('/')
    return when (files.size) {
        0 -> ""
        1 ->
            stringResource(
                if (
                    files[0].kind == GitChangeKind.Added || files[0].kind == GitChangeKind.Untracked
                )
                    Res.string.gitMessageAddOne
                else Res.string.gitMessageUpdateOne,
                name(files[0]),
            )
        2 -> stringResource(Res.string.gitMessageUpdateTwo, name(files[0]), name(files[1]))
        else ->
            pluralStringResource(
                Res.plurals.gitMessageUpdateMany,
                files.size - 1,
                files.size - 1,
                name(files[0]),
            )
    }
}
