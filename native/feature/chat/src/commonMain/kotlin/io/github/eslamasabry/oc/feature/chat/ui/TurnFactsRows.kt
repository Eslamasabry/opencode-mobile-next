package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import io.github.eslamasabry.oc.feature.chat.presentation.StepFold
import io.github.eslamasabry.oc.feature.chat.presentation.StepListRules
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnResultUi
import io.github.eslamasabry.oc.feature.chat.presentation.WorkEntryUi
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
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFold
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFoldLine
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepGroup
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepList
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchEmpty
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchNotLoaded
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchOff
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchUnavailable
import io.github.eslamasabry.oc.ui.words.res.chatStepDeclinedNote
import io.github.eslamasabry.oc.ui.words.res.approvalDeny
import io.github.eslamasabry.oc.ui.words.res.chatUiDurationMinutesSeconds
import io.github.eslamasabry.oc.ui.words.res.chatUiDurationSeconds
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
import io.github.eslamasabry.oc.ui.words.res.kitWorkFoldDeclined
import io.github.eslamasabry.oc.ui.words.res.kitWorkFoldFailed
import io.github.eslamasabry.oc.ui.words.res.kitWorkHideEarlierSteps
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
) {
    val foldKey = "work:${work.turnId}"
    val rows = remember(work, entries) { StepListRules.entries(work, entries) }
    val cut = remember(rows) { StepListRules.cut(rows) }
    val open = folds.open[foldKey] == true
    val treeWords = rememberStepTreeWords()
    val entry: @Composable (StepEntry) -> Unit = { row ->
        key(row.key) {
            when (row) {
                is StepEntry.Step ->
                    StepRow(row.step, nowMs, folds, onOpenSession, onOpenFile, reply, waitingForYou = stepWaitsForYou(row.step.state, work.state))
                is StepEntry.Detail -> TranscriptDetail(row.detail, folds, reasoningExpanded, reply, onCompactAgain, onOpenSession)
                is StepEntry.Group -> SubAgentGroup(row, work.state, onOpenSession, treeWords)
            }
        }
    }
    val earlier: (@Composable ColumnScope.() -> Unit)? =
        if (cut.earlier.isEmpty()) null
        else {
            { cut.earlier.forEach { entry(it) } }
        }
    KitStepList(
        // The reply's 16 dp edge: the glyphs and the rail sit on it, the steps' words one column in.
        modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-work-line"),
        fold = cut.fold?.let { stepFoldLine(it, open) { folds.open[foldKey] = !open } },
        earlier = earlier,
    ) {
        cut.shown.forEach { entry(it) }
    }
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
    val c = StepListRules.counts(group)
    val sep = stringResource(Res.string.kitWorkSeparator)
    val note =
        listOfNotNull(
                c.done.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkSubAgentsDone, it, it) },
                c.working.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.kitWorkSubAgentsWorking, it, it) },
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
                state = subAgentState(s.state),
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
    val seconds = stringResource(Res.string.chatUiDurationSeconds)
    val minutes = stringResource(Res.string.chatUiDurationMinutesSeconds)
    val stop = stringResource(Res.string.edgeLightStop)
    val spoken = stringResource(Res.string.kitWorkSubAgentSpoken)
    val unfold = stringResource(Res.string.edgeLightShowSubAgents)
    val fold = stringResource(Res.string.edgeLightHideSubAgents)
    val allowOnce = stringResource(Res.string.kitRequestAllowOnce)
    val deny = stringResource(Res.string.approvalDeny)
    val answer = stringResource(Res.string.edgeLightAnswer)
    val actionOn = stringResource(Res.string.edgeLightActionOn)
    return remember(working, notStarted, needsYou, paused, denied, failed, moreInside, more, seconds, minutes, stop, spoken, unfold, fold, allowOnce, deny, answer, actionOn) {
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
            duration = { s ->
                if (s < 60) seconds.replace("%1\$s", Numbers.format(s))
                else minutes.replace("%1\$s", Numbers.format(s / 60)).replace("%2\$s", (s % 60).toString().padStart(2, '0'))
            },
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
        key(row.key) {
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
    }
    val earlier: (@Composable ColumnScope.() -> Unit)? =
        if (cut.earlier.isEmpty()) null
        else {
            { cut.earlier.forEach { entry(it as StepEntry.Detail) } }
        }
    KitStepList(
        // The reply's 16 dp edge, as the turn's other steps.
        modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-work-line"),
        fold = cut.fold?.let { stepFoldLine(it, open) { folds.open[foldKey] = !open } },
        earlier = earlier,
    ) {
        cut.shown.forEach { entry(it as StepEntry.Detail) }
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
    KitStepList(modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-work-line")) {
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

@Composable
private fun StepRow(
    step: TurnStep,
    nowMs: Long,
    folds: TranscriptFolds,
    onOpenSession: ((String) -> Unit)?,
    onOpenFile: ((ToolArtifact) -> Unit)?,
    reply: ReplyActions? = null,
    waitingForYou: Boolean = false,
) {
    val key = "step:${step.partId}"
    val f = step.facts
    val out = step.output
    // RC1: a step the person declined with a note says the note on its own row ("Your note: “use rc2.txt”"), under
    // "You declined this"; it showed only after a tap that was easy to land on the row above.
    val noteMask = rememberKitMask()
    val declinedWith =
        declinedStepNote(step.state, out.errorText)?.let { stringResource(Res.string.chatStepDeclinedNote, noteMask(it)) }
    val view =
        KitToolView(
            id = step.partId,
            kind = f.kind.toKit(),
            title = stepTitle(f),
            // E20-S14: a waiting Write (Read, Edit) names its file as soon as the call's input has the path.
            subject =
                stepRowSubject(f.kind, step.state, f.subject, out.input, out.rawInput)
                    ?: stepSubjectWords(f),
            detail = declinedWith ?: stepDetailWords(f),
            // P-105: a step blocked on the person reads "Waiting for you" with the attention mark and no clock.
            status = if (waitingForYou) KitToolStatus.WaitingForYou else step.state.toKit(),
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
                else stepStatus(step.state, f.exitCode),
        )
    val text = out.text.takeIf { it.isNotEmpty() }
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
    KitToolRow(
        view = view,
        opened = folds.open[key] == true,
        onToggle = { folds.open[key] = !(folds.open[key] ?: false) },
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
