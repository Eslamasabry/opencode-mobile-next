package io.github.eslamasabry.oc.feature.team.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.feature.team.ui.ageWords
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitChecklist
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStep
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestFrame
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestLook
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestNeedsYouTag
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The readiness line keys the section knows a label for, in the order of the design (Dart `teamMergeLineLabel`). */
@Composable
internal fun mergeLineLabel(key: String): String =
    when (key) {
        "work" -> stringResource(Res.string.teamUiMergeLineWork)
        "tests" -> stringResource(Res.string.teamUiMergeLineTests)
        "build" -> stringResource(Res.string.teamUiMergeLineBuild)
        "review" -> stringResource(Res.string.teamUiMergeLineReview)
        "conflicts" -> stringResource(Res.string.teamUiMergeLineConflicts)
        "acceptance" -> stringResource(Res.string.teamUiMergeLineAcceptance)
        // A configured check is shown as the host named it.
        else -> key
    }

@Composable
private fun filesWords(files: Int, additions: Int, deletions: Int): String =
    if (files == 0) stringResource(Res.string.teamUiMergeFiles__eq0) else pluralStringResource(Res.plurals.teamUiMergeFiles, files, files, additions, deletions)

@Composable
private fun workStateWord(state: WorkState): String =
    stringResource(
        when (state) {
            WorkState.Queued -> Res.string.teamUiWorkStateQueued
            WorkState.Ready -> Res.string.teamUiWorkStateReady
            WorkState.Working -> Res.string.teamUiWorkStateWorking
            WorkState.Waiting -> Res.string.teamUiWorkStateWaiting
            WorkState.Blocked -> Res.string.teamUiWorkStateBlocked
            WorkState.NeedsInput -> Res.string.teamUiWorkStateNeedsInput
            WorkState.Review -> Res.string.teamUiWorkStateReview
            WorkState.Failed -> Res.string.teamUiWorkStateFailed
            WorkState.Completed -> Res.string.teamUiWorkStateCompleted
            WorkState.Cancelled -> Res.string.teamUiWorkStateCancelled
            WorkState.Unknown -> Res.string.teamUiWorkStateUnknown
        },
    )

/** The readiness lines as one checklist: done, still running on the host, or failed with its detail (Dart `_Lines`). */
@Composable
private fun Lines(lines: List<FleetMergeLineUi>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    val pending = stringResource(Res.string.teamUiMergePending)
    KitChecklist(
        steps =
            lines.map { l ->
                KitStep(
                    title = mergeLineLabel(l.key),
                    state =
                        when (l.state) {
                            FleetMergeLineState.Done -> KitStepState.Done
                            FleetMergeLineState.Working -> KitStepState.Working
                            FleetMergeLineState.Failed -> KitStepState.Failed
                        },
                    supporting = if (l.pending) pending else l.detail,
                    testTag = "team-merge-line-${l.key}",
                )
            },
        modifier = modifier.testTag("team-merge-lines"),
    )
}

/**
 * The task's merge gate (Dart `TeamMergeSection`, TEAM-205): shown only once every work item of the run is done or
 * review-ready and the host has merge roles (its front); a team on this phone merges by itself and shows nothing.
 *
 * Ready, it is the conversation's one question card: "Merge 6 changes into main?", the files and the checks in one line,
 * Review changes, and the next step as the primary (Approve the merge while the request waits for it, then Merge into
 * main). A rule that blocks, or a team that is not answering, turns the step off and says why on the button. A refused
 * merge stays with its reason and Try again. Not ready (a check failing or still running), a calm list of the checks and
 * why Merge is off. Merged, one quiet line saying where it landed. Force-merge, branch reset and worktree deletion do not
 * exist here, on the host front, or anywhere on the phone.
 *
 * The embedding page must sit under a sheet host (a kit screen places one) for Review changes. [onOpenWork] opens a work
 * item from the changes sheet; null leaves those rows plain.
 */
@Composable
public fun FleetMergeSection(
    ui: FleetMergeUi,
    nowMs: Long,
    actions: FleetMergeActions,
    modifier: Modifier = Modifier,
    onOpenWork: ((String) -> Unit)? = null,
) {
    when (ui.kind) {
        FleetMergeKind.Hidden -> return
        FleetMergeKind.Merged -> Merged(ui, actions, modifier)
        FleetMergeKind.Ask -> Ask(ui, nowMs, actions, modifier)
        FleetMergeKind.NotReady -> NotReady(ui, actions, modifier)
    }
    Changes(ui, actions, onOpenWork)
}

@Composable
private fun reviewAction(actions: FleetMergeActions) =
    KitAction(stringResource(Res.string.teamUiMergeReviewChanges), actions::openChanges)

/** Why the next step is off, in words that name the thing to fix (Dart `offReason` and `_notes`). */
@Composable
private fun offWords(ui: FleetMergeUi): String? {
    val missing = ui.missing
    if (ui.kind == FleetMergeKind.NotReady && missing != null) {
        val why = if (missing.pending) stringResource(Res.string.teamUiMergePending) else missing.detail.orEmpty()
        return stringResource(Res.string.teamUiMergeDisabledReason, mergeLineLabel(missing.key), why)
    }
    return when (val off = ui.off) {
        null -> null
        FleetMergeOff.Stale -> stringResource(Res.string.teamMergeGateStale)
        is FleetMergeOff.Boundary -> stringResource(Res.string.teamUiMergeBoundary, off.text)
    }
}

/** The next step as the one primary: Approve while the merge request waits for it, then Merge. */
@Composable
private fun nextStep(ui: FleetMergeUi, actions: FleetMergeActions, ask: Boolean): Pair<KitAction, KitAction?> {
    val reason = offWords(ui)
    fun enabled(on: Boolean) = if (on || reason == null) KitEnabled.Enabled else KitEnabled.Disabled(reason)
    val approve =
        KitAction(
            stringResource(if (ask) Res.string.teamMergeGateApprove else Res.string.teamUiMergeApprove),
            if (ui.canApprove) actions::approve else null,
            enabled = enabled(ui.canApprove),
            icon = KitIcons.check,
            working = ui.approving,
        )
    val merge =
        KitAction(
            // Merge names its target: "Merge into main".
            stringResource(Res.string.teamUiMergeConfirmAction, ui.branch),
            if (ui.canMerge) actions::merge else null,
            enabled = enabled(ui.canMerge),
            icon = KitIcons.branch,
            working = ui.merging,
        )
    return if (ui.approveFirst) approve to merge else merge to null
}

/** The receipts under the card or panel: sending, not confirmed, the approval, and a refused merge with its way forward. */
@Composable
private fun Receipts(ui: FleetMergeUi, actions: FleetMergeActions) {
    ui.approveWrite?.let { w ->
        if (w.status != FleetWriteStatus.Confirmed || ui.approveFirst) {
            FleetWriteLine(w, "team-merge-approve-receipt", null)
        } else {
            KitText(stringResource(Res.string.teamUiMergeApproveConfirmed), Modifier.testTag("team-merge-approve-receipt"), role = TypeRole.Secondary, tone = TextTone.Success)
        }
    }
    ui.approvedBy?.takeIf { it.isNotEmpty() }?.let {
        KitText(stringResource(Res.string.teamUiMergeApprovedBy, it), Modifier.testTag("team-merge-approved-by"), role = TypeRole.Secondary, tone = TextTone.Success)
    }
    val merge = ui.mergeWrite ?: return
    when (merge.status) {
        FleetWriteStatus.Refused -> {
            // The host refused: why (a rule is already words), and what to do next. The host's own text stays under Details.
            val title = ui.refusedOn?.let { stringResource(Res.string.teamUiMergeBoundary, it) } ?: stringResource(Res.string.teamUiMergeRefused)
            KitNotice(
                stringResource(Res.string.teamMergeFailedNext),
                Modifier.testTag("team-merge-receipt"),
                title = title,
                tone = StatusTone.Failure,
                icon = KitIcons.error,
                actions = if (ui.canMerge) listOf(KitLineAction(stringResource(Res.string.teamUiCardRetry), actions::merge, testTag = "team-merge-retry")) else emptyList(),
            )
            val words = merge.hostWords?.trim().orEmpty()
            if (ui.refusedOn == null && words.isNotEmpty()) {
                KitDetailsFold(
                    label = stringResource(Res.string.teamUiTechnicalDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    modifier = Modifier.testTag("team-merge-receipt-details"),
                    notes = listOf(stringResource(Res.string.teamDispatchHostWords)),
                    text = LocalKitRedactor.current.details(words),
                )
            }
        }
        FleetWriteStatus.Sending, FleetWriteStatus.NotConfirmed -> FleetWriteLine(merge, "team-merge-receipt", if (merge.status == FleetWriteStatus.NotConfirmed && ui.canMerge) actions::merge else null)
        FleetWriteStatus.Confirmed -> Unit
    }
}

/** Ready: the conversation's question card (Dart `_gate`, the question look). */
@Composable
private fun Ask(ui: FleetMergeUi, nowMs: Long, actions: FleetMergeActions, modifier: Modifier) {
    val question =
        if (ui.files == 0) stringResource(Res.string.teamMergeGateQuestion__eq0, ui.files, ui.branch)
        else pluralStringResource(Res.plurals.teamMergeGateQuestion, ui.files, ui.files, ui.branch)
    val detail =
        listOfNotNull(
            if (ui.files > 0) filesWords(ui.files, ui.additions, ui.deletions) else null,
            if (ui.checks > 0) pluralStringResource(Res.plurals.teamMergeGateChecksPassed, ui.checks, ui.checks) else null,
        ).joinToString(" · ")
    val (primary, _) = nextStep(ui, actions, ask = true)
    Column(modifier.fillMaxWidth().padding(top = Space.S2.dp).testTag("team-merge-section"), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitEntrance {
            KitRequestFrame(
                look = KitRequestLook.Question,
                answers = { KitActionBlock(primary = primary, tertiary = listOf(reviewAction(actions))) },
            ) {
                KitRequestNeedsYouTag(ageWords(ui.readySinceMs, nowMs))
                KitText(question, Modifier.testTag("team-merge-title"), role = TypeRole.Title)
                if (ui.mergeWrite == null && ui.approveWrite == null) {
                    KitText(stringResource(Res.string.teamMergeGateIfIgnored), role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
                if (detail.isNotEmpty()) KitText(detail, Modifier.testTag("team-merge-files"), role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
        Receipts(ui, actions)
    }
}

/** Not ready, or readiness unknown: a calm list that says why Merge is off; nobody can act yet, so it is never amber. */
@Composable
private fun NotReady(ui: FleetMergeUi, actions: FleetMergeActions, modifier: Modifier) {
    Column(modifier.fillMaxWidth().testTag("team-merge-section"), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitSectionLabel(stringResource(Res.string.teamUiMergeTitleNotReady), Modifier.testTag("team-merge-title"))
        ui.requestId?.let { KitText(stringResource(Res.string.teamUiMergeRequest, it), Modifier.testTag("team-merge-request"), role = TypeRole.Secondary, tone = TextTone.Secondary) }
        if (ui.lines.isNotEmpty()) {
            Lines(ui.lines)
            KitText(filesWords(ui.files, ui.additions, ui.deletions), Modifier.testTag("team-merge-files"), role = TypeRole.Secondary, tone = TextTone.Secondary)
        } else if (ui.loading) {
            KitText(stringResource(Res.string.teamUiMergeLoading), Modifier.testTag("team-merge-loading"), role = TypeRole.Secondary, tone = TextTone.Secondary)
        } else if (ui.unavailable == FleetMergeUnavailable.Failed) {
            KitNotice(
                stringResource(Res.string.teamUiMergeUnavailable, stringResource(Res.string.teamUiStateUnreachableTitle)),
                Modifier.testTag("team-merge-unavailable"),
                actions = listOf(KitLineAction(stringResource(Res.string.teamUiCardRetry), actions::reload, testTag = "team-merge-reload")),
            )
        } else {
            KitText(stringResource(Res.string.teamUiMergeNoRoles), Modifier.testTag("team-merge-unavailable"), role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        // With no readiness there is nothing to approve or merge yet: no button that cannot act.
        if (ui.lines.isNotEmpty() || ui.requestId != null) {
            val (primary, secondary) = nextStep(ui, actions, ask = false)
            KitActionBlock(primary = primary, secondary = secondary, tertiary = listOf(reviewAction(actions)))
        }
        Receipts(ui, actions)
    }
}

/** Merged: one quiet line in the transcript, where it landed, that opens what was merged (Dart `_mergedLine`). */
@Composable
private fun Merged(ui: FleetMergeUi, actions: FleetMergeActions, modifier: Modifier) {
    val commit = ui.mergedCommit
    val line =
        if (commit == null) stringResource(Res.string.teamUiMergeAlready, ui.mergedBranch)
        else stringResource(Res.string.teamUiMergeMerged, ui.mergedBranch, FleetMergePresent.shortCommit(commit))
    KitEntrance(modifier.padding(top = Space.S2.dp).testTag("team-merge-section")) {
        KitRow(
            title = line,
            supporting = stringResource(Res.string.teamUiMergeReviewChanges),
            leading = KitRowLeading.Glyph(KitIcons.branch),
            trailing = KitRowTrailing.Chevron,
            modifier = Modifier.testTag("team-merge-receipt"),
            onClick = actions::openChanges,
        )
    }
}

/** Review changes: the checks, the changed files with their counts, and the task's steps (there is no diff to open). */
@Composable
private fun Changes(ui: FleetMergeUi, actions: FleetMergeActions, onOpenWork: ((String) -> Unit)?) {
    KitSheet(
        visible = ui.changesOpen,
        onDismissRequest = actions::closeChanges,
        title = stringResource(Res.string.teamUiMergeChangesTitle),
        subtitle = if (ui.lines.isEmpty() && ui.changes.isEmpty()) null else filesWords(ui.files, ui.additions, ui.deletions),
        icon = KitIcons.file,
        height = KitSheetHeight.Half,
    ) {
        Column(Modifier.testTag("team-merge-changes"), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            Lines(ui.lines)
            if (ui.changes.isEmpty()) {
                KitText(stringResource(Res.string.teamUiMergeChangesEmpty), Modifier.testTag("team-merge-changes-empty"), tone = TextTone.Secondary)
            } else {
                KitRowGroup {
                    ui.changes.forEach { c ->
                        KitRow(
                            title = c.path,
                            supporting = "+${c.additions} / −${c.deletions}",
                            leading = KitRowLeading.Glyph(KitIcons.file),
                            titleIsFileName = true,
                        )
                    }
                }
            }
            if (ui.work.isNotEmpty()) {
                KitRowGroup(label = stringResource(Res.string.teamUiMergeChangesWork)) {
                    ui.work.forEach { w ->
                        KitRow(
                            title = w.title,
                            supporting = workStateWord(w.state),
                            titleMaxLines = 2,
                            state = KitRowState(null, workTone(w.state)),
                            trailing = if (onOpenWork != null) KitRowTrailing.Chevron else KitRowTrailing.None,
                            modifier = Modifier.testTag("team-merge-work-${w.id}"),
                            onClick = onOpenWork?.let { open -> { open(w.id) } },
                        )
                    }
                }
            }
        }
    }
}

private fun workTone(state: WorkState): KitRowStateTone =
    when (state) {
        WorkState.NeedsInput -> KitRowStateTone.NeedsYou
        WorkState.Working -> KitRowStateTone.Running
        WorkState.Failed -> KitRowStateTone.Failed
        else -> KitRowStateTone.Quiet
    }
