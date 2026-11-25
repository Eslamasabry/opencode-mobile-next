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
import io.github.eslamasabry.oc.feature.chat.presentation.FactsClock
import io.github.eslamasabry.oc.feature.chat.presentation.StepEntry
import io.github.eslamasabry.oc.feature.chat.presentation.StepFamily
import io.github.eslamasabry.oc.feature.chat.presentation.StepFold
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
    /** Called when the person toggles a fold directly (never for app writes). */
    var onPerson: (() -> Unit)? = null

    /** Records a person's fold toggle and writes to [open]. */
    fun set(key: String, v: Boolean) {
        onPerson?.invoke()
        open[key] = v
    }

    /** Records a person's "show all" toggle and writes to [full]. */
    fun setFull(key: String, v: Boolean) {
        onPerson?.invoke()
        full[key] = v
    }

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
@Suppress("LongParameterList")
@Composable
internal fun TurnWorkLine(
    work: TurnWork,
    clock: FactsClock,
    folds: TranscriptFolds,
    onOpenSession: ((String) -> Unit)? = null,
    onOpenFile: ((ToolArtifact) -> Unit)? = null,
    entries: List<WorkEntryUi> = emptyList(),
    reasoningExpanded: Boolean = false,
    reply: ReplyActions? = null,
    onCompactAgain: (() -> Unit)? = null,
    /** The person pressed Stop: the turn's steps no longer say they wait for the person (the tail says Stopping). */
    stopping: Boolean = false,
) {
    val foldKey = "work:${work.turnId}"
    val rows = remember(work, entries) { StepListRules.entries(work, entries) }
    // Owner 2026-11-14: a finished turn's work is ONE quiet row that opens in place; a turn that runs, failed or waits
    // for the person keeps its newest rows in view.
    val summed = StepListRules.sums(work.state, turnRuns = LocalLiveTurnId.current == work.turnId)
    val shownRows = remember(rows, summed) { flattenSingleRun(rows, summed) }
    val cut = remember(shownRows) { StepListRules.cut(shownRows) }
    // A turn with a delegated step whose sub-agent waits on the person opens on entry (the person's own fold wins once
    // tapped), so the way to the child, "Open its conversation", is one tap from the row.
    val childNeeds = LocalChildNeedsYou.current
    val open = folds.open[foldKey] ?: work.steps.any { it.waitsOnChild(childNeeds) }
    val treeWords = rememberStepTreeWords()
    // D1: a permission ask for one of these steps is answered in place, right under the step it is for.
    val inPlace = LocalInPlaceRequests.current
    val host = StepRowHost(work, clock, folds, onOpenSession, onOpenFile, reply, stopping)
    val entry: @Composable (StepEntry) -> Unit = { row ->
        when (row) {
            // One call for a step and for a run of steps: a step that a second one joins keeps its row (same key, same
            // node), so it changes in place and never flashes.
            is StepEntry.Step,
            is StepEntry.Run -> {
                val run = row as? StepEntry.Run
                val steps = run?.steps ?: listOf((row as StepEntry.Step).step)
                StepRow(steps, run?.family, host)
                val lone = (row as? StepEntry.Step)?.step
                val ask = lone?.callId?.let { inPlace?.byCall?.get(it) }
                if (inPlace != null && ask != null) inPlace.draw(ask)
            }
            is StepEntry.Detail -> TranscriptDetail(row.detail, folds, reasoningExpanded, reply, onCompactAgain, onOpenSession)
            is StepEntry.Group -> SubAgentGroup(row, work.state, onOpenSession, treeWords)
        }
    }
    val folded = if (summed) shownRows.size else cut.earlier.size
    // Rows that fold away while nobody tapped (a new step arrived, the turn ended) fold at the arrival's pace, so the
    // list's height holds while one row goes and another comes (owner 2026-11-14, "flashing").
    val openBefore = remember(foldKey) { booleanArrayOf(open) }
    val tapped = openBefore[0] != open
    SideEffect { openBefore[0] = open }
    KitStepList(
        // The reply's 16 dp edge: the glyphs and the rail sit on it, the steps' words one column in.
        modifier = Modifier.padding(start = Space.S4.dp, end = REPLY_ACTIONS_EDGE_INSET.dp).testTag("chat-work-line"),
        fold = if (summed) null else cut.fold?.let { stepFoldLine(it, open) { folds.set(foldKey, !open) } },
    ) {
        if (summed)
            key("summary:${work.turnId}") {
                KitStepItem { WorkSummaryRow(work, open) { folds.set(foldKey, !open) } }
            }
        // Every row in one sequence, each with its own fold (owner 2026-11-14, "clipping flashing"): opening the
        // turn's work or its earlier steps unfolds them one after another from the top (#052, #002); a row that moves
        // behind the fold as new ones arrive folds away while the new one grows in, so the reply below moves smoothly
        // and no row is composed again.
        shownRows.forEachIndexed { i, row ->
            key(row.key) { KitStepItem(visible = i >= folded || open, order = i, makesRoom = !tapped) { entry(row) } }
        }
    }
}

private fun flattenSingleRun(rows: List<StepEntry>, summed: Boolean): List<StepEntry> {
    val singleRun =
        if (summed) rows.filter { it !is StepEntry.Detail }.singleOrNull() as? StepEntry.Run else null
    return if (singleRun == null) rows
    else rows.flatMap { if (it === singleRun) singleRun.steps.map(StepEntry::Step) else listOf(it) }
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

/** Turn id of the currently running turn, so its step list stays unfolded between steps. */
internal val LocalLiveTurnId = androidx.compose.runtime.compositionLocalOf<String?> { null }

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
        fold = cut.fold?.let { stepFoldLine(it, open) { folds.set(foldKey, !open) } },
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

@Suppress("LongParameterList")
private class StepRowHost(
    val work: TurnWork,
    val clock: FactsClock,
    val folds: TranscriptFolds,
    val onOpenSession: ((String) -> Unit)?,
    val onOpenFile: ((ToolArtifact) -> Unit)?,
    val reply: ReplyActions?,
    val stopping: Boolean,
)

/**
 * One row of a turn's steps: a step, or a run of steps of one family ([family] non-null, two or more [steps]) that
 * opens in place to each of its steps on the same rail. Both share one [KitToolRow] call so nothing flashes.
 */
@Composable
private fun StepRow(
    steps: List<TurnStep>,
    family: StepFamily?,
    host: StepRowHost,
) {
    val lead = steps.first()
    val childNeeds = LocalChildNeedsYou.current
    val run = family != null && steps.size > 1
    if (run && family != null) {
        val runKey = "run:${lead.partId}"
        val runOpen = host.folds.open[runKey] == true
        KitToolRow(
            view = runView(steps, family, host.work, host.clock),
            opened = runOpen,
            onToggle = { host.folds.set(runKey, !runOpen) },
            modifier = Modifier.testTag("chat-step-run-${lead.partId}"),
            group = true,
        )
        steps.forEachIndexed { i, s ->
            key(s.partId) {
                // The run's own steps stand one step in, the rail bending in from the run's node to them.
                KitStepItem(visible = runOpen, order = i, nested = true) {
                    StepRow(listOf(s), null, host)
                }
            }
        }
        return
    }
    val waiting = !host.stopping && (stepWaitsForYou(lead.state, host.work.state) || lead.waitsOnChild(childNeeds))
    OneStep(
        lead,
        host.clock,
        host,
        waitingForYou = waiting,
        stopSaidBelow = lead === host.work.steps.lastOrNull(),
    )
}

@Composable
private fun runDetailWords(steps: List<TurnStep>, family: StepFamily, live: TurnStep?): String? {
    val mask = rememberKitMask()
    val sep = stringResource(Res.string.kitWorkSeparator)
    val failed = steps.count { it.state == StepState.Failed }
    val declined = steps.count { it.state == StepState.Declined }
    val parts = ArrayList<String>()
    if (failed > 0) parts += pluralStringResource(Res.plurals.kitWorkFoldFailed, failed, failed)
    if (declined > 0) parts += pluralStringResource(Res.plurals.kitWorkFoldDeclined, declined, declined)
    // A live run leads with what runs now ("Searching files · 6 commands"), never past and present in one row.
    if (live != null) {
        parts += runCount(family, steps.size)
    } else {
        for (s in steps) {
            val said =
                if (family == StepFamily.Commands) stepRowTitle(s.facts, stepTense(s.state))
                else s.facts.subject?.takeIf { it.isNotBlank() }?.let(mask) ?: stepTitle(s.facts)
            if (said !in parts) parts += said
        }
    }
    return parts.joinToString(sep).ifEmpty { null }
}

private fun StepFamily.toKitKind(): KitToolKind =
    when (this) {
        StepFamily.Commands -> KitToolKind.Shell
        StepFamily.Reads -> KitToolKind.Read
        StepFamily.Searches -> KitToolKind.Search
        StepFamily.Lists -> KitToolKind.List
    }

/** A run's row: "Ran 6 commands", what they did (or the running one's words), how long; failures named. */
@Composable
private fun runView(steps: List<TurnStep>, family: StepFamily, work: TurnWork, clock: FactsClock): KitToolView {
    val live = steps.lastOrNull { it.state == StepState.Running || it.state == StepState.Pending }
    val ended = steps.count { it.state != StepState.Running && it.state != StepState.Pending }
    val total = steps.mapNotNull { it.durationMs }.takeIf { it.size == steps.size }?.sum()
    val shownMs =
        if (live != null) {
            val nowMs = if (live.state == StepState.Running) clock.read() else 0L
            shownStepDurationMs(live.state, live.durationMs, live.startedAtMs, nowMs)
        } else {
            shownStepDurationMs(StepState.Done, total, null, 0L)
        }
    val title =
        if (live != null) stepRowTitle(live.facts, stepTense(live.state))
        else runTitle(family, if (ended > 0) ended else steps.size)
    val icon =
        if (live != null) stepGlyph(live.facts)
        else steps.map { stepGlyph(it.facts) }.distinct().singleOrNull()
    val running = live?.state == StepState.Running
    return KitToolView(
        id = "run:${steps.first().partId}",
        kind = family.toKitKind(),
        title = title,
        subject = null,
        detail = runDetailWords(steps, family, live),
        status = if (live != null) KitToolStatus.Running else KitToolStatus.Done,
        durationLabel = shownMs?.let { durationWords(it) },
        added = null,
        removed = null,
        statusLabel = stepStatus(if (live != null) StepState.Running else StepState.Done, null),
        icon = icon,
        still = running && work.state != WorkLineState.Running,
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
    clock: FactsClock,
    host: StepRowHost,
    waitingForYou: Boolean = false,
    /**
     * Owner 2026-11-13 ("Stopped" once): the step the person's Stop ended is the turn's last; the reply's footer says the
     * turn stopped, so its row draws no second "Stopped" (TalkBack still hears it in the row's state).
     */
    stopSaidBelow: Boolean = false,
) {
    SideEffect {
        if (TranscriptProbe.on) {
            TranscriptProbe.steps[step.partId] = (TranscriptProbe.steps[step.partId] ?: 0) + 1
        }
    }
    val key = "step:${step.partId}"
    val nowMs = if (!waitingForYou && step.state == StepState.Running) clock.read() else 0L
    val still = step.state == StepState.Running && host.work.state != WorkLineState.Running
    val view = stepToolView(step, nowMs, waitingForYou, stopSaidBelow, still)
    val text = stepOutputShown(step, host.folds.full[key] == true)
    val child = step.facts.childSessionId?.takeIf { host.onOpenSession != null }
    val files = if (host.onOpenFile == null) emptyList() else step.output.artifacts.filter(::canOpenArtifact)
    val connector = step.facts.details.filterIsInstance<ToolDetail.ConnectorSearch>().firstOrNull()
    val hasBody = stepHasBody(step, text, connector, child, files)
    val autoOpen = opensItself(step, waitingForYou, text != null) || (waitingForYou && child != null)
    if (autoOpen) {
        LaunchedEffect(key) { if (host.folds.open[key] == null) host.folds.open[key] = true }
    }
    val isOpen = host.folds.open[key] ?: autoOpen
    KitToolRow(
        view = view,
        opened = isOpen,
        onToggle = { host.folds.set(key, !isOpen) },
        modifier = Modifier.testTag("chat-step-${step.partId}"),
        body =
            if (!hasBody) null
            else {
                { OneStepBody(step, key, text, host) }
            },
    )
}

private fun stepHasBody(
    step: TurnStep,
    text: String?,
    connector: ToolDetail.ConnectorSearch?,
    child: String?,
    files: List<ToolArtifact>,
): Boolean {
    if (!stepOpens(step.state)) return false
    val out = step.output
    val f = step.facts
    val hasInput = out.input.isNotEmpty() && inputHasBody(f.kind, step.state)
    val hasShell = f.kind == ToolKind.Shell && !f.subject.isNullOrBlank()
    return connector != null ||
        text != null ||
        out.pruned ||
        hasInput ||
        hasShell ||
        child != null ||
        files.isNotEmpty()
}

@Composable
private fun stepDeclinedDetail(step: TurnStep): String? {
    val out = step.output
    val noteMask = rememberKitMask()
    if (step.state == StepState.Declined && stoppedByPlan(out.metadata)) {
        return stringResource(Res.string.chatStepPlanStopped)
    }
    return declinedStepNote(step.state, out.errorText)?.let {
        stringResource(Res.string.chatStepDeclinedNote, noteMask(it))
    }
}

@Composable
private fun stepStatusLabel(step: TurnStep, waitingForYou: Boolean): String =
    when {
        waitingForYou -> stringResource(Res.string.kitWorkWaitingForYou)
        step.state == StepState.Declined && stoppedByPlan(step.output.metadata) ->
            stringResource(Res.string.kitToolPlanStopped)
        else -> stepStatus(step.state, step.facts.exitCode)
    }

@Composable
private fun stepToolView(
    step: TurnStep,
    nowMs: Long,
    waitingForYou: Boolean,
    stopSaidBelow: Boolean,
    still: Boolean,
): KitToolView {
    val f = step.facts
    val out = step.output
    val subject =
        if (f.kind == ToolKind.Shell && f.shell != null) null
        else stepRowSubject(f.kind, step.state, f.subject, out.input, out.rawInput) ?: stepSubjectWords(f)
    val status =
        when {
            waitingForYou -> KitToolStatus.WaitingForYou
            stopSaidBelow && step.state == StepState.Stopped -> KitToolStatus.Done
            else -> step.state.toKit()
        }
    val durationLabel =
        if (waitingForYou) null
        else shownStepDurationMs(step.state, step.durationMs, step.startedAtMs, nowMs)?.let { durationWords(it) }
    return KitToolView(
        id = step.partId,
        kind = f.kind.toKit(),
        title = stepRowTitle(f, stepTense(step.state, waiting = waitingForYou)),
        icon = stepGlyph(f),
        subject = subject,
        detail = stepDeclinedDetail(step) ?: endedReason(step) ?: stepDetailWords(f),
        status = status,
        durationLabel = durationLabel,
        added = f.added,
        removed = f.removed,
        statusLabel = stepStatusLabel(step, waitingForYou),
        still = still,
    )
}

@Composable
private fun OneStepBody(step: TurnStep, key: String, text: String?, host: StepRowHost) {
    val f = step.facts
    val out = step.output
    val connector = f.details.filterIsInstance<ToolDetail.ConnectorSearch>().firstOrNull()
    if (connector != null) ConnectorSearchNote(connector)
    val child = f.childSessionId?.takeIf { host.onOpenSession != null }
    if (child != null && host.onOpenSession != null) {
        KitButton(
            stringResource(Res.string.kitToolOpenConversation),
            { host.onOpenSession.invoke(child) },
            role = KitButtonRole.Tertiary,
            modifier = Modifier.testTag("chat-step-open-session"),
        )
    }
    val files = if (host.onOpenFile == null) emptyList() else out.artifacts.filter(::canOpenArtifact)
    for ((i, file) in files.withIndex()) {
        KitButton(
            file.displayName ?: stringResource(Res.string.chatUiGeneratedFile),
            { host.onOpenFile?.invoke(file) },
            role = KitButtonRole.Tertiary,
            modifier = Modifier.testTag("chat-step-file-$i"),
        )
    }
    val shell = f.kind == ToolKind.Shell
    if (shell) ShellStepCommandBox(step, key, text, host.folds)
    val rerun = host.reply?.onRerunCommand
    val again = if (rerun == null) null else rerunCommand(f.kind, step.state, out.input, f.subject)
    if (shell && rerun != null && again != null) {
        KitButton(
            stringResource(Res.string.toolCardRunCommandAgain),
            { rerun(again) },
            role = KitButtonRole.Tertiary,
            icon = KitIcons.retry,
            modifier = Modifier.testTag("chat-step-rerun"),
        )
    } else {
        ToolStepBody(step, host.reply)
    }
}

@Composable
private fun ConnectorSearchNote(connector: ToolDetail.ConnectorSearch) {
    KitQuietLine(
        stringResource(
            when (connector.outcome) {
                ConnectorSearchOutcome.Empty -> Res.string.chatUiConnectorSearchEmpty
                ConnectorSearchOutcome.NotLoaded -> Res.string.chatUiConnectorSearchNotLoaded
                ConnectorSearchOutcome.Off -> Res.string.chatUiConnectorSearchOff
                ConnectorSearchOutcome.Unavailable -> Res.string.chatUiConnectorSearchUnavailable
            }
        )
    )
}

@Composable
private fun ShellStepCommandBox(step: TurnStep, key: String, text: String?, folds: TranscriptFolds) {
    val full = folds.full[key] == true
    val lines = remember(text) { text?.lines()?.size ?: 0 }
    KitCommandBox(
        command = step.facts.subject ?: "",
        output = text,
        maxLines = if (full) Int.MAX_VALUE else KIT_COMMAND_OUTPUT_LINES,
        moreLabel = pluralStringResource(Res.plurals.kitCodeShowAll, lines, lines),
        onMore = { folds.setFull(key, true) },
        exitLabel = null,
        emptyLabel =
            when (step.state) {
                StepState.Done -> stringResource(Res.string.kitCommandNoOutput)
                StepState.Failed -> stringResource(Res.string.kitCommandFailedNoOutput)
                StepState.Stopped -> stringResource(Res.string.kitCommandStoppedEarly)
                else -> null
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
            onExpandedChange = { folds.set(key, it) },
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
