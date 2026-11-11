package io.github.eslamasabry.oc.feature.handoff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.feature.handoff.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The person reviews the exact bounded record and edits the first message before one explicit send.
 */
@Composable
public fun AgentHandoffScreen(state: AgentHandoffUi, actions: AgentHandoffActions) {
    val sourceReview = state.sourceReview
    val attempt = state.attempt
    val draft = sourceReview?.draft ?: attempt.draft
    val reviewing = attempt.phase == HandoffPhase.Ready
    val page = state.page()
    val locked = state.busy || state.loading || attempt.phase == HandoffPhase.Uncertain
    val working = stringResource(Res.string.handoffNativeWorking)
    val disabled = if (locked) KitEnabled.Disabled(working) else KitEnabled.Enabled
    val field = rememberTextFieldState()
    val path = draft?.relativePath
    val initialPrompt = stringResource(Res.string.handoffNativeInitialPrompt, path.orEmpty())
    LaunchedEffect(path, state.busy) {
        if (path != null && !state.busy && state.prompt.isEmpty() && reviewing)
            actions.editPrompt(initialPrompt)
    }
    LaunchedEffect(state.prompt) {
        if (field.text.toString() != state.prompt) field.setTextAndPlaceCursorAtEnd(state.prompt)
    }
    // Back from Agents (the set-up), the list of ready agents must be read again. Only a real return counts:
    // the effect is not keyed on the page, or each reload would trigger the next one.
    val currentPage by rememberUpdatedState(page)
    var wasAway by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (wasAway && currentPage == HandoffPage.NoAgent) {
            wasAway = false
            actions.refresh()
        }
        onPauseOrDispose { wasAway = true }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.handoffNativeTitle),
                subtitle = state.title.takeIf { it.isNotBlank() },
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            if (state.loading) item { KitSkeletonRows(count = 3) }
            val view = state.stateView()
            if (view != null)
                item {
                    val refresh =
                        KitAction(stringResource(Res.string.handoffNativeRefresh), actions::refresh)
                    val agents =
                        KitAction(stringResource(Res.string.handoffNativeSetup), actions::setup)
                    KitStateView(
                        kind = KitStateKind.Calm,
                        title =
                            stringResource(
                                when (view.headline) {
                                    HandoffHeadline.Unavailable ->
                                        Res.string.handoffNativeUnavailable
                                    HandoffHeadline.NoAgentReady ->
                                        Res.string.handoffNativeNoReadyTitle
                                }
                            ),
                        body = stringResource(
                            when (view.body) {
                                HandoffBody.Computer -> Res.string.handoffNativeBlockComputer
                                HandoffBody.Folder -> Res.string.handoffNativeBlockFolder
                                HandoffBody.FolderUnknown -> Res.string.handoffUiUnavailableDirectory
                                HandoffBody.Unconfirmed -> Res.string.handoffNativeBlockUnconfirmed
                                HandoffBody.NotStarted -> Res.string.handoffNativeNotStartedBody
                                HandoffBody.NoAgentReady -> Res.string.handoffNativeNoTargets
                            }
                        ),
                        primary = if (view.primary == HandoffButton.OpenAgents) agents else refresh,
                        // The calm state draws its primary and its quiet (tertiary) actions only.
                        tertiary = if (view.secondary == null) emptyList() else listOf(refresh),
                    )
                }
            if (!state.connected && sourceReview != null && view != null)
                item {
                    RecordDisclosure(sourceReview.draft.record.markdown)
                }
            state.failure?.let { failure ->
                item {
                    KitErrorNotice(
                        message = stringResource(Res.string.handoffNativeFailed),
                        failure = failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
                }
            }
            if (state.connected) {
                if (state.directory.isNotBlank()) item { KitText(state.directory) }
                if (state.links.isNotEmpty())
                    item {
                        KitRowGroup(label = stringResource(Res.string.handoffNativeLinks)) {
                            for (link in state.links) {
                                KitRow(
                                    title =
                                        link.source.ifBlank {
                                            stringResource(Res.string.handoffNativeUntitled)
                                        },
                                    trailing =
                                        KitRowTrailing.Action(
                                            KitLineAction(
                                                stringResource(
                                                    Res.string.handoffNativeOpenOriginal
                                                ),
                                                { actions.openLink(link.id, false) },
                                                enabled = !locked,
                                                disabledReason = working,
                                            )
                                        ),
                                )
                                KitRow(
                                    title = link.target,
                                    trailing =
                                        KitRowTrailing.Action(
                                            KitLineAction(
                                                stringResource(
                                                    Res.string.handoffNativeOpenContinued
                                                ),
                                                { actions.openLink(link.id, true) },
                                                enabled = !locked,
                                                disabledReason = working,
                                            )
                                        ),
                                )
                            }
                        }
                    }
                if (page == HandoffPage.Choose) {
                    item { KitNotice(stringResource(Res.string.handoffNativeExplanation)) }
                    item {
                        KitRowGroup(label = stringResource(Res.string.handoffNativeChoose)) {
                            for (choice in state.choices) KitRow(
                                title = choice.agent,
                                supporting = choice.model,
                                selected = choice.id == state.selected,
                                enabled = !locked && choice.issue == null,
                                disabledReason =
                                    if (choice.issue == HandoffChoiceIssue.PhoneCheck)
                                        stringResource(Res.string.handoffNativePhoneCheck)
                                    else working,
                                onClick = { actions.select(choice.id) },
                            )
                        }
                    }
                    item {
                        KitButton(
                            stringResource(Res.string.handoffNativeReview),
                            actions::prepare,
                            enabled =
                                if (state.selected == null || locked)
                                    KitEnabled.Disabled(
                                        stringResource(Res.string.handoffNativeChoose)
                                    )
                                else KitEnabled.Enabled,
                        )
                    }
                }
                if (state.busy) item { KitNotice(working) }
                attempt.failure?.let { kind -> item { KitNotice(failureText(kind)) } }
                if (attempt.phase == HandoffPhase.Uncertain)
                    item {
                        KitNotice(stringResource(Res.string.handoffNativeUncertain))
                    }
                if (reviewing) {
                    item { KitNotice(stringResource(Res.string.handoffNativeReviewBody)) }
                    item {
                        KitField(
                            state = field,
                            label = stringResource(Res.string.handoffNativeFirstMessage),
                            kind = KitFieldKind.Multiline,
                            maxLength = 16384,
                            enabled = disabled,
                            helper = stringResource(Res.string.handoffNativePromptHelp),
                            onTextChanged = actions::editPrompt,
                            multilineLines = 5..12,
                        )
                    }
                    item { RecordDisclosure(draft?.record?.markdown.orEmpty()) }
                    item {
                        val target = state.choices.firstOrNull { it.id == state.selected }?.agent
                        KitButton(
                            if (target != null)
                                stringResource(Res.string.handoffNativeContinueWith, target)
                            else stringResource(Res.string.handoffNativeSend),
                            actions::send,
                            enabled =
                                if (state.prompt.isBlank() || locked)
                                    KitEnabled.Disabled(working)
                                else KitEnabled.Enabled,
                        )
                    }
                }
                if (
                    attempt.phase == HandoffPhase.Complete ||
                        attempt.failure == HandoffFailureKind.LinkFailed
                ) {
                    item { KitNotice(stringResource(Res.string.handoffNativeSent)) }
                    item {
                        KitButton(
                            stringResource(Res.string.handoffNativeOpenOriginal),
                            actions::openOriginal,
                            role = KitButtonRole.Secondary,
                            enabled = disabled,
                        )
                    }
                    item {
                        KitButton(
                            stringResource(Res.string.handoffNativeOpenContinued),
                            actions::openCreated,
                            enabled = disabled,
                        )
                    }
                }
                if (attempt.failure == HandoffFailureKind.LinkFailed)
                    item {
                        KitButton(
                            stringResource(Res.string.handoffNativeSaveLink),
                            actions::retryLink,
                            role = KitButtonRole.Secondary,
                            enabled = disabled,
                        )
                    }
                if (
                    attempt.phase == HandoffPhase.Failed &&
                        attempt.failure != HandoffFailureKind.LinkFailed
                )
                    item {
                        KitButton(
                            stringResource(Res.string.handoffNativeRefresh),
                            actions::refresh,
                            role = KitButtonRole.Secondary,
                            enabled = disabled,
                        )
                    }
            }
        }
    }
}

@Composable
private fun failureText(kind: HandoffFailureKind): String =
    stringResource(
        when (kind) {
            HandoffFailureKind.SourceChanged -> Res.string.handoffNativeSourceChanged
            HandoffFailureKind.StaleScope,
            HandoffFailureKind.ProjectMismatch -> Res.string.handoffNativeStale
            HandoffFailureKind.InvalidDraft -> Res.string.handoffNativeInvalidPrompt
            HandoffFailureKind.CreationUnknown,
            HandoffFailureKind.DeliveryUnknown -> Res.string.handoffNativeUncertain
            HandoffFailureKind.LinkFailed -> Res.string.handoffNativeLinkFailed
            else -> Res.string.handoffNativeFailed
        }
    )

/** The record is supporting detail: folded by default, never the main content. */
@Composable
private fun RecordDisclosure(markdown: String) {
    var open by remember { mutableStateOf(false) }
    KitExpandRow(
        title = stringResource(Res.string.handoffNativeWhatHandedOver),
        expanded = open,
        onExpandedChange = { open = it },
    ) {
        KitText(markdown)
    }
}
