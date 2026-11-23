package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ConnectorSearchOutcome
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolDetail
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chat.presentation.StepEntry
import io.github.eslamasabry.oc.feature.chat.presentation.StepFamily
import io.github.eslamasabry.oc.feature.chat.presentation.StepFold
import io.github.eslamasabry.oc.feature.chat.presentation.StepListCut
import io.github.eslamasabry.oc.feature.chat.presentation.StepListRules
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnResultUi
import io.github.eslamasabry.oc.feature.chat.presentation.WorkEntryUi
import io.github.eslamasabry.oc.feature.chat.presentation.WorkLineRules
import io.github.eslamasabry.oc.feature.chat.requests.ui.LocalInPlaceRequests
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.request.KitRequestWords
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeActions
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeWords
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStep
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.tool.KIT_COMMAND_OUTPUT_LINES
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFold
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFoldLine
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepGroup
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepItem
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepList
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.tool.kitWorkIcon
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_ACTIONS_EDGE_INSET
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchEmpty
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchNotLoaded
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchOff
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchUnavailable
import io.github.eslamasabry.oc.ui.words.res.chatStepDeclinedNote
import io.github.eslamasabry.oc.ui.words.res.chatStepPlanStopped
import io.github.eslamasabry.oc.ui.words.res.kitToolPlanStopped
import io.github.eslamasabry.oc.ui.words.res.approvalDeny
import io.github.eslamasabry.oc.ui.words.res.chatUiGeneratedFile
import io.github.eslamasabry.oc.ui.words.res.edgeLightActionOn
import io.github.eslamasabry.oc.ui.words.res.edgeLightAnswer
import io.github.eslamasabry.oc.ui.words.res.edgeLightHideSubAgents
import io.github.eslamasabry.oc.ui.words.res.edgeLightMoreInside
import io.github.eslamasabry.oc.ui.words.res.edgeLightMoreSubAgents
import io.github.eslamasabry.oc.ui.words.res.edgeLightNotStarted
import io.github.eslamasabry.oc.ui.words.res.edgeLightPausedUntilAnswer
import io.github.eslamasabry.oc.ui.words.res.edgeLightShowSubAgents
import io.github.eslamasabry.oc.ui.words.res.edgeLightStop
import io.github.eslamasabry.oc.ui.words.res.edgeLightWorking
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.kitToolDeclined
import io.github.eslamasabry.oc.ui.words.res.kitToolFailed
import io.github.eslamasabry.oc.ui.words.res.kitToolStopped
import io.github.eslamasabry.oc.ui.words.res.kitToolUnanswered
import io.github.eslamasabry.oc.ui.words.res.kitWorkFoldDeclined
import io.github.eslamasabry.oc.ui.words.res.kitWorkFoldFailed
import io.github.eslamasabry.oc.ui.words.res.kitWorkHideEarlierSteps
import io.github.eslamasabry.oc.ui.words.res.kitCommandFailedNoOutput
import io.github.eslamasabry.oc.ui.words.res.kitCommandNoOutput
import io.github.eslamasabry.oc.ui.words.res.kitCommandStoppedEarly
import io.github.eslamasabry.oc.ui.words.res.kitWorkSeparator
import io.github.eslamasabry.oc.ui.words.res.kitWorkSubAgentSpoken
import io.github.eslamasabry.oc.ui.words.res.kitWorkSubAgents
import io.github.eslamasabry.oc.ui.words.res.kitWorkSubAgentsDone
import io.github.eslamasabry.oc.ui.words.res.kitWorkSubAgentsStopped
import io.github.eslamasabry.oc.ui.words.res.kitWorkSubAgentsWorking
import io.github.eslamasabry.oc.ui.words.res.kitCodeShowAll
import io.github.eslamasabry.oc.ui.words.res.toolCardRunCommandAgain
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.kitMessageThought
import io.github.eslamasabry.oc.ui.words.res.kitToolOpenConversation
import io.github.eslamasabry.oc.ui.words.res.kitWorkEarlierSteps
import io.github.eslamasabry.oc.ui.words.res.kitWorkWaitingForYou
import io.github.eslamasabry.oc.ui.words.res.liveStepDelegating
import io.github.eslamasabry.oc.ui.words.res.liveStepEditing
import io.github.eslamasabry.oc.ui.words.res.liveStepFetching
import io.github.eslamasabry.oc.ui.words.res.liveStepListing
import io.github.eslamasabry.oc.ui.words.res.liveStepReading
import io.github.eslamasabry.oc.ui.words.res.liveStepRunning
import io.github.eslamasabry.oc.ui.words.res.liveStepRunningCommand
import io.github.eslamasabry.oc.ui.words.res.liveStepSearching
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What is open in the transcript beside the rows: work lines, steps, results and the step outputs
 * shown in full. Screen-local (a fold is not an engine fact); keyed by row/step ids, never by
 * index.
 */
@Stable
internal class TranscriptFolds(
    val open: SnapshotStateMap<String, Boolean>,
    val full: SnapshotStateMap<String, Boolean>,
    val earlier: SnapshotStateMap<String, Int>,
) {
    /** Something the person opened is still open (the bar shows Collapse all steps). */
    val anyOpen: Boolean
        get() = open.values.any { it } || full.values.any { it }

    /** One tap folds every open step and fold on the page (Flutter `_collapseAllSteps`). */
    fun collapseAll() {
        open.keys.toList().forEach { open[it] = false }
        full.keys.toList().forEach { full[it] = false }
    }
}

/**
 * Saveable, so what a person opened (a work line, a Thought, a step's Details, "show more") survives leaving the chat
 * and coming back, and rotation. Bounded: at most [FOLDS_SAVED_PER_KIND] entries of each kind are saved, the ones the
 * person left open first (an entry that is closed is the default again for most folds, so dropping it costs nothing).
 */
internal const val FOLDS_SAVED_PER_KIND = 200

private val foldsSaver: Saver<TranscriptFolds, Any> =
    Saver(
        save = { folds ->
            listOf(
                foldsBoolOut(folds.open),
                foldsBoolOut(folds.full),
                folds.earlier.entries.take(FOLDS_SAVED_PER_KIND).flatMap { listOf(it.key, it.value) },
            )
        },
        restore = { saved ->
            val parts = saved as List<*>
            TranscriptFolds(
                foldsBoolIn(parts[0] as List<*>),
                foldsBoolIn(parts[1] as List<*>),
                mutableStateMapOf<String, Int>().also { map ->
                    (parts[2] as List<*>).chunked(2).forEach { map[it[0] as String] = it[1] as Int }
                },
            )
        },
    )

/** Open entries first, then closed ones (a closed entry that was set explicitly differs from a default-open fold). */
private fun foldsBoolOut(map: Map<String, Boolean>): List<Any> =
    map.entries.sortedByDescending { it.value }.take(FOLDS_SAVED_PER_KIND).flatMap { listOf(it.key, it.value) }

private fun foldsBoolIn(flat: List<*>): SnapshotStateMap<String, Boolean> =
    mutableStateMapOf<String, Boolean>().also { map ->
        flat.chunked(2).forEach { map[it[0] as String] = it[1] as Boolean }
    }

@Composable
internal fun rememberTranscriptFolds(): TranscriptFolds =
    rememberSaveable(saver = foldsSaver) {
        TranscriptFolds(mutableStateMapOf(), mutableStateMapOf(), mutableStateMapOf())
    }

/**
 * A turn's work as its steps (D1 "steps only", owner 2026-11-14; B-CHAT-063, plan 09 chat-transcript-turn-model): no
 * summary line. One row per step on a thin rail, in the order it happened: each tool step a [KitToolRow] in plain words
 * with its status, duration and counts (B-CHAT-109/111), opening to its output (B-CHAT-112); a thought or short passing
 * words where they happened; sub-agents started together as one group row with its children on a small tree. The
 * newest [StepListRules.SHOWN] rows stay in view while the turn runs and after it ends; older ones fold behind one first
 * row that names what it hides that matters ("1 declined · 1 failed"). That fold is the transcript's "work:<turn>" fold,
 * so Find opens it for a match inside.
 */
@Composable
internal fun TurnWorkLine(
    work: TurnWork,
    nowMs: Long,
    folds: TranscriptFolds,
    onOpenSession: ((String) -> Unit)? = null,
    onOpenFile: ((ToolArtifact) -> Unit)? = null,
    entries: List<WorkEntryUi> = emptyList(),
    reasoningExpanded: Boolean = false,
    reply: ReplyActions? = null,
    onCompactAgain: (() -> Unit)? = null,
    /** The person pressed Stop: the turn's steps no longer say they wait for the person (the tail says Stopping). */
    stopping: Boolean = false,
    /** The turn itself is still running: keep the folded step list open between steps instead of summing to one row. */
    turnRuns: Boolean = false,
) {
    val foldKey = "work:${work.turnId}"
    val rows = remember(work, entries) { StepListRules.entries(work, entries) }
    val rawCut = remember(rows) { StepListRules.cut(rows) }
    // A turn with a delegated step whose sub-agent waits on the person opens on entry (the person's own fold wins once
    // tapped), so the way to the child, "Open its conversation", is one tap from the row.
    val childNeeds = LocalChildNeedsYou.current
    val open = folds.open[foldKey] ?: work.steps.any { it.waitsOnChild(childNeeds) }
    // Owner 2026-11-14: a finished turn's work is ONE quiet row that opens in place; a turn that runs, failed or waits
    // for the person keeps its newest rows in view.
    val summed = StepListRules.sums(work.state, turnRuns)
    val cut = if (summed) rawCut else keepOpenedRuns(rows, rawCut, folds)
    val treeWords = rememberStepTreeWords()
    // D1: a permission ask for one of these steps is answered in place, right under the step it is for.
    val inPlace = LocalInPlaceRequests.current
    val entry: @Composable (StepEntry) -> Unit = { row ->
        when (row) {
            // One call for a step and for a run of steps: a step that a second one joins keeps its row (same key, same
            // node), so it changes in place and never flashes.
            is StepEntry.Step,
            is StepEntry.Run -> {
                val run = row as? StepEntry.Run
                val steps = run?.steps ?: listOf((row as StepEntry.Step).step)
                StepRow(steps, run?.family, work, nowMs, folds, onOpenSession, onOpenFile, reply, stopping)
                val lone = (row as? StepEntry.Step)?.step
                val ask = lone?.callId?.let { inPlace?.byCall?.get(it) }
                if (inPlace != null && ask != null) inPlace.draw(ask)
            }
            is StepEntry.Detail -> TranscriptDetail(row.detail, folds, reasoningExpanded, reply, onCompactAgain, onOpenSession)
            is StepEntry.Group -> SubAgentGroup(row, work.state, onOpenSession, treeWords)
        }
    }
    val folded = if (summed) rows.size else cut.earlier.size
    // Rows that fold away while nobody tapped (a new step arrived, the turn ended) fold at the arrival's pace, so the
    // list's height holds while one row goes and another comes (owner 2026-11-14, "flashing").
    val openBefore = remember(foldKey) { booleanArrayOf(open) }
    val tapped = openBefore[0] != open
    SideEffect { openBefore[0] = open }
    KitStepList(
        // The reply's 16 dp edge: the glyphs and the rail sit on it, the steps' words one column in.
        modifier = Modifier.padding(start = Space.S4.dp, end = REPLY_ACTIONS_EDGE_INSET.dp).testTag("chat-work-line"),
        fold = if (summed) null else cut.fold?.let { stepFoldLine(it, open) { folds.open[foldKey] = !open } },
    ) {
        if (summed)
            key("summary:${work.turnId}") {
                KitStepItem { WorkSummaryRow(work, open) { folds.open[foldKey] = !open } }
            }
        // Every row in one sequence, each with its own fold (owner 2026-11-14, "clipping flashing"): opening the
        // turn's work or its earlier steps unfolds them one after another from the top (#052, #002); a row that moves
        // behind the fold as new ones arrive folds away while the new one grows in, so the reply below moves smoothly
        // and no row is composed again.
        rows.forEachIndexed { i, row ->
            key(row.key) { KitStepItem(visible = i >= folded || open, order = i, makesRoom = !tapped) { entry(row) } }
        }
    }
}

/** Keep a run the person tapped open in view instead of folding it away behind "Show earlier steps". */
private fun keepOpenedRuns(rows: List<StepEntry>, cut: StepListCut, folds: TranscriptFolds): StepListCut {
    val firstOpen =
        rows.indexOfFirst { row ->
            row is StepEntry.Run && folds.open["run:${row.steps.first().partId}"] == true
        }
    return when {
        firstOpen < 0 || firstOpen >= cut.earlier.size -> cut
        firstOpen < 2 -> StepListCut(emptyList(), rows, null)
        else -> StepListRules.cut(rows, shown = rows.size - firstOpen)
    }
}

/**
 * A finished turn's work as one row (owner 2026-11-14: "when collapsed it takes a huge space"; the Flutter app's "one
 * quiet line that opens in place"): the glyph of what it mostly did, "Ran 11 commands · 2 other steps", what went wrong
 * if anything did, the time the steps took and the fold chevron. A tap opens every step under it on the rail.
 */
@Composable
private fun WorkSummaryRow(work: TurnWork, open: Boolean, onToggle: () -> Unit) {
    val c = WorkLineRules.split(work).done
    val sep = stringResource(Res.string.kitWorkSeparator)
    val failed = work.steps.count { it.state == StepState.Failed }
    val declined = work.steps.count { it.state == StepState.Declined }
    val note =
        listOfNotNull(
                declined.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkFoldDeclined, it, it) },
                failed.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkFoldFailed, it, it) },
            )
            .joinToString(sep)
            .ifEmpty { null }
    val total = work.steps.mapNotNull { it.durationMs }.takeIf { it.isNotEmpty() && it.size == work.steps.size }?.sum()
    KitToolRow(
        view =
            KitToolView(
                id = "summary:${work.turnId}",
                kind = KitToolKind.Other,
                title = turnWorkWords(work),
                subject = null,
                detail = note,
                status = KitToolStatus.Done,
                // One time on a folded turn: its footer says how long the turn took; opened, the row says the steps'
                // own.
                durationLabel = total?.takeIf { it >= 100 && open }?.let { durationWords(it) },
                added = null,
                removed = null,
                statusLabel = stepStatus(StepState.Done, null),
                icon =
                    kitWorkIcon(
                        read = c.read,
                        searched = c.searched,
                        listed = c.listed,
                        edited = c.edited,
                        ran = c.ran,
                        fetched = c.fetched,
                        delegated = c.delegated,
                        other = c.other,
                    ),
            ),
        opened = open,
        onToggle = onToggle,
        modifier = Modifier.testTag("chat-work-summary"),
        group = true,
    )
}

/** The fold row's words: "Show 5 earlier steps" and what lies inside ("1 declined · 1 failed"), or "Hide earlier steps". */
@Composable
private fun stepFoldLine(fold: StepFold, open: Boolean, onToggle: () -> Unit): KitStepFoldLine {
    val sep = stringResource(Res.string.kitWorkSeparator)
    val note =
        listOfNotNull(
                fold.declined.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkFoldDeclined, it, it) },
                fold.failed.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkFoldFailed, it, it) },
            )
            .joinToString(sep)
            .ifEmpty { null }
    return KitStepFoldLine(
        label =
            if (open) stringResource(Res.string.kitWorkHideEarlierSteps)
            else pluralStringResource(Res.plurals.kitWorkEarlierSteps, fold.hidden, fold.hidden),
        // Opened, the problems are in view on their own rows.
        note = if (open) null else note,
        open = open,
        onToggle = onToggle,
    )
}

/**
 * Sub-agents the agent started together (D1): one row, "2 sub-agents · 1 done · 1 working", folded until the person
 * opens it; opened, each sub-agent hangs on the shared step tree by its own description, in its own state, and a tap
 * opens the conversation it ran in. A finished one says how long its call took (its own start and end); a working one
 * says only that it works (sub-agents carry no clock of their own).
 */
@Composable
private fun SubAgentGroup(
    group: StepEntry.Group,
    work: WorkLineState,
    onOpenSession: ((String) -> Unit)?,
    words: KitStepTreeWords,
) {
    val mask = rememberKitMask()
    val childNeeds = LocalChildNeedsYou.current
    val c = StepListRules.counts(group, childNeeds)
    val sep = stringResource(Res.string.kitWorkSeparator)
    val waitingWords = stringResource(Res.string.kitWorkWaitingForYou)
    val note =
        listOfNotNull(
                c.done.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkSubAgentsDone, it, it) },
                c.working.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkSubAgentsWorking, it, it) },
                waitingWords.takeIf { c.waiting > 0 },
                c.failed.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkFoldFailed, it, it) },
                c.declined.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkFoldDeclined, it, it) },
                c.stopped.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkSubAgentsStopped, it, it) },
            )
            .joinToString(sep)
            .ifEmpty { null }
    val stopped = stringResource(Res.string.kitToolStopped)
    val sessions = group.steps.associate { it.partId to it.facts.childSessionId }
    val children =
        group.steps.map { s ->
            val name = s.facts.subject?.takeIf { it.isNotBlank() }?.let(mask) ?: stepTitle(s.facts)
            KitTreeStep(
                key = s.partId,
                name = name,
                state = if (s.waitsOnChild(childNeeds)) KitTreeStepState.NeedsYou else subAgentState(s.state),
                seconds = s.durationMs?.takeIf { s.state == StepState.Done }?.let { it / 1_000 },
                denied = s.state == StepState.Declined,
                word = if (s.state == StepState.Stopped || s.state == StepState.NotRun) stopped else null,
            )
        }
    KitStepGroup(
        name = pluralStringResource(Res.plurals.kitWorkSubAgents, group.steps.size, group.steps.size),
        note = note,
        children = children,
        words = words,
        modifier = Modifier.testTag("chat-step-group-${group.steps.first().partId}"),
        live = c.working > 0 && work == WorkLineState.Running,
        actions =
            KitStepTreeActions(
                onOpen = { step -> sessions[step.key.toString()]?.let { id -> onOpenSession?.invoke(id) } }
            ),
    )
}

/** Sessions with an open request of their own, so a delegated step's sub-agent that waits on the person shows it. */
internal val LocalChildNeedsYou = androidx.compose.runtime.compositionLocalOf<Set<String>> { emptySet() }

/** This delegated step's sub-agent has an open request: the step reads "Waiting for you" and opens its conversation. */
private fun TurnStep.waitsOnChild(open: Set<String>): Boolean = facts.childSessionId?.let { it in open } == true

/** A sub-agent's place on the tree, from its call's state (a declined one is drawn failed, said "You declined this"). */
private fun subAgentState(state: StepState): KitTreeStepState =
    when (state) {
        StepState.Pending,
        StepState.Running -> KitTreeStepState.Working
        StepState.Done -> KitTreeStepState.Done
        StepState.Failed,
        StepState.Declined -> KitTreeStepState.Failed
        StepState.Stopped,
        StepState.NotRun -> KitTreeStepState.NotStarted
    }

/** The shared step tree's words in the conversation (the edge light's own words where they are the same). */
@Composable
internal fun rememberStepTreeWords(): KitStepTreeWords {
    val working = stringResource(Res.string.edgeLightWorking)
    val notStarted = stringResource(Res.string.edgeLightNotStarted)
    val needsYou = stringResource(Res.string.kitWorkWaitingForYou)
    val paused = stringResource(Res.string.edgeLightPausedUntilAnswer)
    val denied = stringResource(Res.string.kitToolDeclined)
    val failed = stringResource(Res.string.kitToolFailed)
    val moreInside = stringResource(Res.string.edgeLightMoreInside)
    val more = stringResource(Res.string.edgeLightMoreSubAgents)
    val stop = stringResource(Res.string.edgeLightStop)
    val spoken = stringResource(Res.string.kitWorkSubAgentSpoken)
    val unfold = stringResource(Res.string.edgeLightShowSubAgents)
    val fold = stringResource(Res.string.edgeLightHideSubAgents)
    val allowOnce = stringResource(Res.string.kitRequestAllowOnce)
    val deny = stringResource(Res.string.approvalDeny)
    val answer = stringResource(Res.string.edgeLightAnswer)
    val actionOn = stringResource(Res.string.edgeLightActionOn)
    return remember(working, notStarted, needsYou, paused, denied, failed, moreInside, more, stop, spoken, unfold, fold, allowOnce, deny, answer, actionOn) {
        fun on(action: String, target: String) = actionOn.replace("%1\$s", action).replace("%2\$s", target)
        KitStepTreeWords(
            working = working,
            notStarted = notStarted,
            needsYou = needsYou,
            paused = paused,
            denied = denied,
            failed = failed,
            moreInside = { moreInside.replace("%1\$s", Numbers.format(it)) },
            more = { more.replace("%1\$s", Numbers.format(it)) },
            stop = { stop.replace("%1\$s", it) },
            spoken = { step, said -> spoken.replace("%1\$s", step.name).replace("%2\$s", said) },
            unfold = unfold,
            fold = fold,
            request =
                KitRequestWords(
                    allowOnce = allowOnce,
                    deny = deny,
                    answer = answer,
                    allowOn = { on(allowOnce, it) },
                    denyOn = { on(deny, it) },
                    answerOn = { on(answer, it) },
                ),
        )
    }
}

/**
 * Review #13 (C-022): the work of a turn whose only work is thinking. Reasoning is work: each thought is a "Thought"
 * row on the step list's rail (D1), folded like every step and opening as the chat's reasoning setting says
 * ([reasoningExpanded]); never a loose paragraph above the reply. Nothing is drawn when no thought has words.
 */
@Composable
internal fun ThoughtWorkLine(
    turnId: String,
    thoughts: List<TranscriptDetailUi>,
    folds: TranscriptFolds,
    reasoningExpanded: Boolean,
    reply: ReplyActions?,
    onCompactAgain: (() -> Unit)?,
    onOpenSession: ((String) -> Unit)?,
    /** The newest thought is still being written. */
    thinking: Boolean = false,
) {
    val shown = thoughts.filter { hasThoughtWords(it.part.text) || thinking && it === thoughts.last() }
    if (shown.isEmpty()) return
    val foldKey = "work:$turnId"
    val rows = remember(shown) { shown.map { StepEntry.Detail(it) } }
    val cut = remember(rows) { StepListRules.cut(rows) }
    val open = folds.open[foldKey] == true
    val entry: @Composable (StepEntry.Detail) -> Unit = { row ->
        TranscriptDetail(
            row.detail,
            folds,
            reasoningExpanded,
            reply,
            onCompactAgain,
            onOpenSession,
            thinking = thinking && row.detail === thoughts.last(),
        )
    }
    val folded = cut.earlier.size
    KitStepList(
        // The reply's 16 dp edge, as the turn's other steps.
        modifier = Modifier.padding(start = Space.S4.dp, end = REPLY_ACTIONS_EDGE_INSET.dp).testTag("chat-work-line"),
        fold = cut.fold?.let { stepFoldLine(it, open) { folds.open[foldKey] = !open } },
    ) {
        (cut.earlier + cut.shown).forEachIndexed { i, row ->
            key(row.key) { KitStepItem(visible = i >= folded || open, order = i) { entry(row as StepEntry.Detail) } }
        }
    }
}

/**
 * Audit 1, item 6: a thought the transcript's reading does not know (a row that arrived before the ledger held its part)
 * is still a folded "Thought" step, never its words printed as a grey paragraph; opened, it shows the thought's text.
 */
@Composable
internal fun FoldedThoughtLine(rowKey: String, text: String, folds: TranscriptFolds?) {
    val key = "thought-line:$rowKey"
    val open = folds?.open?.get(key) ?: false
    KitStepList(modifier = Modifier.padding(start = Space.S4.dp, end = REPLY_ACTIONS_EDGE_INSET.dp).testTag("chat-work-line")) {
        KitStepFold(
            title = stringResource(Res.string.kitMessageThought),
            expanded = open,
            onToggle = { folds?.open?.put(key, !open) },
            icon = KitIcons.idea,
        ) {
            if (text.isNotBlank()) KitQuietLine(text.trim())
        }
    }
}

@Composable
internal fun nowWords(step: TurnStep, counts: WorkCounts): String {
    val f = step.facts
    // B-SEC-094 / R18: the subject is a command or path the agent wrote; it reads redacted like
    // every mono surface.
    val mask = rememberKitMask()
    // "Reading 6 files", "Editing 2 files": the count is the steps of that kind so far this turn
    // (edited counts distinct files, a file written then edited is one; the other kinds count steps).
    return when (f.kind) {
        ToolKind.Read,
        ToolKind.Lsp -> liveStepCount(Res.plurals.liveStepReading, counts.read)
        ToolKind.Grep,
        ToolKind.Glob,
        ToolKind.ConnectorSearch -> liveStepCount(Res.plurals.liveStepSearching, counts.searched)
        ToolKind.List -> liveStepCount(Res.plurals.liveStepListing, counts.listed)
        ToolKind.Edit,
        ToolKind.Write,
        ToolKind.Patch -> liveStepCount(Res.plurals.liveStepEditing, counts.edited)
        ToolKind.WebFetch,
        ToolKind.WebSearch -> liveStepCount(Res.plurals.liveStepFetching, counts.fetched)
        ToolKind.Task,
        ToolKind.AgentMessage -> liveStepCount(Res.plurals.liveStepDelegating, counts.delegated)
        ToolKind.Shell -> {
            val command = f.subject?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            if (command.isNullOrBlank()) stringResource(Res.string.liveStepRunningCommand)
            else stringResource(Res.string.liveStepRunning, mask(command))
        }
        else -> {
            val title = stepTitle(f)
            val subject = f.subject
            if (subject.isNullOrBlank()) title else "$title · ${mask(subject)}"
        }
    }
}

@Composable
private fun liveStepCount(res: PluralStringResource, count: Int): String =
    pluralStringResource(res, count.coerceAtLeast(1), count.coerceAtLeast(1))

/**
 * One row of a turn's steps: a step, or a run of steps of one family ([family] non-null, two or more [steps]: "Ran 6
 * commands", the running one's words or what they did as its quiet second label, the fold chevron) that opens in place
 * to each of its steps on the same rail. Both are the same row (one [KitToolRow] call), so a step that a second one joins
 * keeps its node and never flashes.
 */
@Composable
private fun StepRow(
    steps: List<TurnStep>,
    family: StepFamily?,
    work: TurnWork,
    nowMs: Long,
    folds: TranscriptFolds,
    onOpenSession: ((String) -> Unit)?,
    onOpenFile: ((ToolArtifact) -> Unit)?,
    reply: ReplyActions?,
    stopping: Boolean,
) {
    val lead = steps.first()
    val childNeeds = LocalChildNeedsYou.current
    val run = family != null && steps.size > 1
    if (run && family != null) {
        val runKey = "run:${lead.partId}"
        val runOpen = folds.open[runKey] == true
        KitToolRow(
            view = runView(steps, family, nowMs),
            opened = runOpen,
            onToggle = { folds.open[runKey] = !runOpen },
            modifier = Modifier.testTag("chat-step-run-${lead.partId}"),
            group = true,
        )
        steps.forEachIndexed { i, s ->
            key(s.partId) {
                // The run's own steps stand one step in, the rail bending in from the run's node to them.
                KitStepItem(visible = runOpen, order = i, nested = true) {
                    StepRow(listOf(s), null, work, nowMs, folds, onOpenSession, onOpenFile, reply, stopping)
                }
            }
        }
        return
    }
    OneStep(
        lead,
        nowMs,
        folds,
        onOpenSession,
        onOpenFile,
        reply,
        waitingForYou = !stopping && (stepWaitsForYou(lead.state, work.state) || lead.waitsOnChild(childNeeds)),
        stopSaidBelow = lead === work.steps.lastOrNull(),
    )
}

/** A run's row: "Ran 6 commands", what they did (or the running one's words), how long; failures named. */
@Composable
private fun runView(steps: List<TurnStep>, family: StepFamily, nowMs: Long): KitToolView {
    val mask = rememberKitMask()
    val live = steps.lastOrNull { it.state == StepState.Running || it.state == StepState.Pending }
    val ended = steps.count { it.state != StepState.Running && it.state != StepState.Pending }
    val sep = stringResource(Res.string.kitWorkSeparator)
    val failed = steps.count { it.state == StepState.Failed }
    val declined = steps.count { it.state == StepState.Declined }
    val parts = ArrayList<String>()
    if (failed > 0) parts += pluralStringResource(Res.plurals.kitWorkFoldFailed, failed, failed)
    if (declined > 0) parts += pluralStringResource(Res.plurals.kitWorkFoldDeclined, declined, declined)
    // A live run leads with what runs now ("Searching files · 6 commands"), never past and present in one row.
    if (live != null) parts += runCount(family, steps.size)
    else
        for (s in steps) {
            val said =
                if (family == StepFamily.Commands) stepRowTitle(s.facts, stepTense(s.state))
                else s.facts.subject?.takeIf { it.isNotBlank() }?.let(mask) ?: stepTitle(s.facts)
            if (said !in parts) parts += said
        }
    val total = steps.mapNotNull { it.durationMs }.takeIf { it.size == steps.size }?.sum()
    val shownMs =
        if (live != null) shownStepDurationMs(live.state, live.durationMs, live.startedAtMs, nowMs)
        else shownStepDurationMs(StepState.Done, total, null, nowMs)
    val status = if (live != null) KitToolStatus.Running else KitToolStatus.Done
    return KitToolView(
        id = "run:${steps.first().partId}",
        kind =
            when (family) {
                StepFamily.Commands -> KitToolKind.Shell
                StepFamily.Reads -> KitToolKind.Read
                StepFamily.Searches -> KitToolKind.Search
                StepFamily.Lists -> KitToolKind.List
            },
        title = if (live != null) stepRowTitle(live.facts, stepTense(live.state)) else runTitle(family, if (ended > 0) ended else steps.size),
        subject = null,
        detail = parts.joinToString(sep).ifEmpty { null },
        status = status,
        durationLabel = shownMs?.let { durationWords(it) },
        added = null,
        removed = null,
        statusLabel = stepStatus(if (live != null) StepState.Running else StepState.Done, null),
        // What runs now, or what all of them did when they did one thing ("Ran 3 commands" of tests: the flask).
        icon =
            if (live != null) stepGlyph(live.facts)
            else steps.map { stepGlyph(it.facts) }.distinct().singleOrNull(),
    )
}

/**
 * B-CHAT-117: why a step the run ended on did not finish, only when the app knows it: the person was shown its request
 * and did not answer, or the server had started it. A step that is plainly stopped says no reason.
 */
@Composable
private fun endedReason(step: TurnStep): String? {
    val marks = step.output.metadata?.keys.orEmpty()
    val ended = step.state == StepState.Stopped || step.state == StepState.NotRun
    return when {
        !ended -> null
        io.github.eslamasabry.oc.engine.transcript.turnfacts.UNANSWERED_KEY in marks ->
            stringResource(Res.string.kitToolUnanswered)
        step.state == StepState.Stopped &&
            io.github.eslamasabry.oc.engine.transcript.turnfacts.STOPPED_EARLY_KEY in marks ->
            stringResource(Res.string.kitCommandStoppedEarly)
        else -> null
    }
}

@Composable
private fun OneStep(
    step: TurnStep,
    nowMs: Long,
    folds: TranscriptFolds,
    onOpenSession: ((String) -> Unit)?,
    onOpenFile: ((ToolArtifact) -> Unit)?,
    reply: ReplyActions? = null,
    waitingForYou: Boolean = false,
    /**
     * Owner 2026-11-13 ("Stopped" once): the step the person's Stop ended is the turn's last; the reply's footer says the
     * turn stopped, so its row draws no second "Stopped" (TalkBack still hears it in the row's state).
     */
    stopSaidBelow: Boolean = false,
) {
    val key = "step:${step.partId}"
    val f = step.facts
    val out = step.output
    // RC1: a step the person declined with a note says the note on its own row ("Your note: “use rc2.txt”"), under
    // "You declined this"; it showed only after a tap that was easy to land on the row above.
    val noteMask = rememberKitMask()
    val declinedWith =
        if (step.state == StepState.Declined && stoppedByPlan(out.metadata)) stringResource(Res.string.chatStepPlanStopped)
        else declinedStepNote(step.state, out.errorText)?.let { stringResource(Res.string.chatStepDeclinedNote, noteMask(it)) }
    val view =
        KitToolView(
            id = step.partId,
            kind = f.kind.toKit(),
            // Owner 2026-11-14: a shell step says what it did ("Ran tests"); its command is in the opened step.
            title = stepRowTitle(f, stepTense(step.state, waiting = waitingForYou)),
            // Owner 2026-11-14: the glyph follows what the step did (tests, a search, git), not only its tool.
            icon = stepGlyph(f),
            // E20-S14: a waiting Write (Read, Edit) names its file as soon as the call's input has the path.
            subject =
                if (f.kind == ToolKind.Shell && f.shell != null) null
                else stepRowSubject(f.kind, step.state, f.subject, out.input, out.rawInput) ?: stepSubjectWords(f),
            // A step the run ended on before the person answered says why it did not run, under its one name.
            detail = declinedWith ?: endedReason(step) ?: stepDetailWords(f),
            // P-105: a step blocked on the person reads "Waiting for you" with the attention mark and no clock.
            status =
                if (waitingForYou) KitToolStatus.WaitingForYou
                else if (stopSaidBelow && step.state == StepState.Stopped) KitToolStatus.Done
                else step.state.toKit(),
            // E20-S14: under a tenth of a second there is no time to show ("0.0s" said nothing).
            durationLabel =
                if (waitingForYou) null
                else
                    shownStepDurationMs(step.state, step.durationMs, step.startedAtMs, nowMs)?.let {
                        durationWords(it)
                    },
            added = f.added,
            removed = f.removed,
            statusLabel =
                if (waitingForYou) stringResource(Res.string.kitWorkWaitingForYou)
                else if (step.state == StepState.Declined && stoppedByPlan(out.metadata)) stringResource(Res.string.kitToolPlanStopped)
                else stepStatus(step.state, f.exitCode),
        )
    // A running command shows what it has put out so far, its last lines, growing in place; a finished one its answer.
    val text = stepOutputShown(step, folds.full[key] == true)
    val shell = f.kind == ToolKind.Shell
    val child = f.childSessionId?.takeIf { onOpenSession != null }
    // B-CHAT-114: an explicit file the step produced (an attachment, a `filePath` output) is a
    // visible action.
    val files = if (onOpenFile == null) emptyList() else out.artifacts.filter(::canOpenArtifact)
    val connector = f.details.filterIsInstance<ToolDetail.ConnectorSearch>().firstOrNull()
    // A declined call has nothing more to open: its row already says what the person decided and wrote.
    val hasBody =
        stepOpens(step.state) && (connector != null ||
            text != null ||
            out.pruned ||
            (out.input.isNotEmpty() && inputHasBody(f.kind, step.state)) ||
            (shell && !f.subject.isNullOrBlank()) ||
            child != null ||
            files.isNotEmpty())
    val full = folds.full[key] == true
    // Issue 147 (F-033): a command that is running and has put something out opens by itself, so its growing tail is in
    // view without a tap. It stays open when the command ends (its output stays readable); the person's own fold wins.
    // A delegated step whose sub-agent waits on the person opens by itself too: its "Open its conversation" is in view.
    val autoOpen = opensItself(step, waitingForYou, text != null) || (waitingForYou && child != null)
    if (autoOpen)
        LaunchedEffect(key) { if (folds.open[key] == null) folds.open[key] = true }
    val isOpen = folds.open[key] ?: autoOpen
    KitToolRow(
        view = view,
        opened = isOpen,
        onToggle = { folds.open[key] = !isOpen },
        modifier = Modifier.testTag("chat-step-${step.partId}"),
        body =
            if (!hasBody) null
            else {
                {
                    if (connector != null)
                        KitQuietLine(
                            stringResource(
                                when (connector.outcome) {
                                    ConnectorSearchOutcome.Empty ->
                                        Res.string.chatUiConnectorSearchEmpty
                                    ConnectorSearchOutcome.NotLoaded ->
                                        Res.string.chatUiConnectorSearchNotLoaded
                                    ConnectorSearchOutcome.Off ->
                                        Res.string.chatUiConnectorSearchOff
                                    ConnectorSearchOutcome.Unavailable ->
                                        Res.string.chatUiConnectorSearchUnavailable
                                }
                            )
                        )
                    if (child != null && onOpenSession != null)
                        KitButton(
                            stringResource(Res.string.kitToolOpenConversation),
                            { onOpenSession(child) },
                            role = KitButtonRole.Tertiary,
                            modifier = Modifier.testTag("chat-step-open-session"),
                        )
                    for ((i, file) in files.withIndex()) KitButton(
                        file.displayName ?: stringResource(Res.string.chatUiGeneratedFile),
                        { onOpenFile?.invoke(file) },
                        role = KitButtonRole.Tertiary,
                        modifier = Modifier.testTag("chat-step-file-$i"),
                    )
                    if (shell) {
                        // Counted only where it is shown (an open shell step), once per output.
                        val lines = remember(text) { text?.lines()?.size ?: 0 }
                        KitCommandBox(
                            command = f.subject ?: "",
                            output = text,
                            maxLines =
                                if (full) Int.MAX_VALUE
                                else
                                    io.github.eslamasabry.oc.ui.kitchat.tool
                                        .KIT_COMMAND_OUTPUT_LINES,
                            moreLabel = pluralStringResource(Res.plurals.kitCodeShowAll, lines, lines),
                            onMore = { folds.full[key] = true },
                            exitLabel = null,
                            // Ended and printed nothing: say so (never a blank box that reads as lost output).
                            emptyLabel =
                                when (step.state) {
                                    StepState.Done -> stringResource(Res.string.kitCommandNoOutput)
                                    StepState.Failed -> stringResource(Res.string.kitCommandFailedNoOutput)
                                    StepState.Stopped -> stringResource(Res.string.kitCommandStoppedEarly)
                                    else -> null
                                },
                        )
                    }
                    val rerun = reply?.onRerunCommand
                    val again = if (rerun == null) null else rerunCommand(f.kind, step.state, out.input, f.subject)
                    if (shell && rerun != null && again != null)
                        KitButton(
                            stringResource(Res.string.toolCardRunCommandAgain),
                            { rerun(again) },
                            role = KitButtonRole.Tertiary,
                            icon = KitIcons.retry,
                            modifier = Modifier.testTag("chat-step-rerun"),
                        )
                    else ToolStepBody(step, reply)
                }
            },
    )
}

/**
 * What a finished turn did (B-CHAT-068/100/101), under its footer: one quiet line ("Edited 2 files
 * · ran 3 commands") that opens to the files with their counts and the commands with their exit
 * codes. Timing (B-CHAT-092) lives in message Details. No result draws when the turn changed and
 * ran nothing.
 */
@Composable
internal fun TurnResultLine(turnId: String, result: TurnResultUi, folds: TranscriptFolds) {
    val summary = resultSummary(result)
    if (summary == null) return
    val key = "result:$turnId"
    // B-SEC-094 / R18: a command line is server text; a key it carries never reaches the screen.
    val mask = rememberKitMask()
    Column(Modifier.testTag("chat-turn-result")) {
        KitExpandRow(
            title = summary,
            expanded = folds.open[key] == true,
            onExpandedChange = { folds.open[key] = it },
        ) {
            for (file in result.files) KitQuietLine(
                fileLine(mask(file.path), file.added, file.removed)
            )
            for (command in result.commands) {
                val exit = command.exitCode
                val line = mask(command.command)
                KitQuietLine(
                    if (exit == null) "$ $line"
                    else
                        "$ $line  ·  " +
                            stepStatus(if (exit == 0) StepState.Done else StepState.Failed, exit)
                )
            }
        }
    }
}

private fun StepState.toKit(): KitToolStatus =
    when (this) {
        StepState.Pending -> KitToolStatus.Pending
        StepState.Running -> KitToolStatus.Running
        StepState.Done -> KitToolStatus.Done
        StepState.Failed -> KitToolStatus.Failed
        StepState.Stopped,
        StepState.Declined,
        StepState.NotRun -> KitToolStatus.Stopped
    }

private fun ToolKind.toKit(): KitToolKind =
    when (this) {
        ToolKind.Read,
        ToolKind.Lsp -> KitToolKind.Read
        ToolKind.List -> KitToolKind.List
        ToolKind.Glob,
        ToolKind.Grep,
        ToolKind.ConnectorSearch -> KitToolKind.Search
        ToolKind.Shell -> KitToolKind.Shell
        ToolKind.Edit -> KitToolKind.Edit
        ToolKind.Write -> KitToolKind.Write
        ToolKind.Patch -> KitToolKind.Patch
        ToolKind.WebFetch,
        ToolKind.WebSearch -> KitToolKind.WebFetch
        ToolKind.Task,
        ToolKind.AgentMessage -> KitToolKind.SubAgent
        ToolKind.Todo,
        ToolKind.Plan -> KitToolKind.Todo
        ToolKind.Question -> KitToolKind.Question
        ToolKind.Card -> KitToolKind.Card
        ToolKind.Skill,
        ToolKind.WorktreeSetup,
        ToolKind.BackgroundNotice,
        ToolKind.Generic -> KitToolKind.Other
    }

/**
 * Only steps whose body draws its input open on input alone (ToolStepBody returns early for the
 * rest).
 */
private fun inputHasBody(kind: ToolKind, state: StepState): Boolean =
    state != StepState.NotRun &&
        state != StepState.Stopped &&
        state != StepState.Declined &&
        kind != ToolKind.Card &&
        kind != ToolKind.Read &&
        kind != ToolKind.Shell &&
        kind != ToolKind.Lsp &&
        kind != ToolKind.ConnectorSearch

/**
 * What a step's command box shows, as text (null when there is none): a running command its last lines so far, growing in
 * place; a failed command its last lines (the error is at the end of what it printed) until "Show all"; any other step
 * the answer it gave.
 */
internal fun stepOutputShown(step: TurnStep, showAll: Boolean): String? {
    val out = step.output
    val last = KIT_COMMAND_OUTPUT_LINES
    val shown =
        when {
            // The server's own line for a stopped tool ("Tool execution interrupted: shell") is not copy: the box says
            // "Stopped before it finished" in the kit's words, the raw line stays in the message's Details.
            step.state == StepState.Stopped &&
                io.github.eslamasabry.oc.feature.chat.presentation.StoppedSteps.isStopError(out.text.trim()) -> ""
            step.state == StepState.Running -> out.liveTail.lines().takeLast(last).joinToString("\n")
            step.state == StepState.Failed && step.facts.kind == ToolKind.Shell && !showAll ->
                out.text.trimEnd().lines().takeLast(last).joinToString("\n")
            else -> out.text
        }
    return shown.takeIf { it.isNotEmpty() }
}

/** Whether a step opens by itself: a running command, not blocked on the person, that has printed something. */
internal fun opensItself(step: TurnStep, waitingForYou: Boolean, hasOutput: Boolean): Boolean =
    step.state == StepState.Running && !waitingForYou && step.facts.kind == ToolKind.Shell && hasOutput
