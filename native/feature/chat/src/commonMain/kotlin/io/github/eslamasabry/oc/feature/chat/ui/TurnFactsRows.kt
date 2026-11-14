package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnResultUi
import io.github.eslamasabry.oc.feature.chat.presentation.WorkEntryUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.tool.kitWorkIcon
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KIT_WORK_STEP_CAP
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchEmpty
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchNotLoaded
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchOff
import io.github.eslamasabry.oc.ui.words.res.chatUiConnectorSearchUnavailable
import io.github.eslamasabry.oc.ui.words.res.chatStepDeclinedNote
import io.github.eslamasabry.oc.ui.words.res.chatUiGeneratedFile
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

private fun WorkLineState.toKit(): KitWorkState =
    when (this) {
        WorkLineState.Running -> KitWorkState.Running
        WorkLineState.WaitingForYou -> KitWorkState.WaitingForYou
        WorkLineState.Done -> KitWorkState.Done
        WorkLineState.EndedFailed -> KitWorkState.EndedFailed
        WorkLineState.Stopped -> KitWorkState.Stopped
    }

/**
 * A turn's work under ONE line (B-CHAT-063, plan 09 chat-transcript-turn-model). Folded, it says
 * what was done (or, while running, what is happening now); open, it lists the steps oldest first,
 * each a [KitToolRow] in plain words with its status, duration and counts (B-CHAT-109/111), opening
 * to its output (B-CHAT-112). Ended-failed work opens by itself until the person folds it.
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
    /**
     * Non-null while a step runs and the person tapped the live line to see the steps (Flutter `openWhileRunning`):
     * the line is drawn opened, and tapping it hides the steps again.
     */
    openWhileRunning: (() -> Unit)? = null,
) {
    val key = "work:${work.turnId}"
    val kitState = work.state.toKit()
    val expanded = openWhileRunning != null || (folds.open[key] ?: kitState.opensByDefault)
    val summary = workSummary(work)
    val now = work.now
    // Folded while the turn runs, the line names the real current step with its count; opened, it says the summary
    // and the running step on the rail carries the progress (Flutter `KitWorkLine`).
    val nowLabel = if (now != null) nowWords(now, work.counts) else null
    val c = work.counts
    // F: kit_work_line.dart:273-281 and transcript_rows.dart:489-491: a running line keeps its newest three steps in
    // view (when at least three more came before them); the earlier ones fold behind "Show N earlier steps".
    val window =
        if (work.state == WorkLineState.Running && work.steps.size >= RUNNING_TAIL + 3) RUNNING_TAIL
        else KIT_WORK_STEP_CAP
    val hidden =
        if (work.state == WorkLineState.Running)
            (work.steps.size - window - (folds.earlier[key] ?: 0)).coerceAtLeast(0)
        else 0
    val earlier =
        if (expanded && hidden > 0) {
            val n = minOf(hidden, KIT_WORK_STEP_CAP)
            KitLineAction(
                pluralStringResource(Res.plurals.kitWorkEarlierSteps, n, n),
                {
                    folds.earlier[key] = (folds.earlier[key] ?: 0) + n
                },
            )
        } else null
    KitWorkLine(
        label = summary,
        stateWords = workStateWords(work.state),
        state = kitState,
        expanded = expanded,
        onToggle = { if (openWhileRunning != null) openWhileRunning() else folds.open[key] = it },
        earlier = earlier,
        icon = kitWorkIcon(c.read, c.searched, c.listed, c.edited, c.ran, c.fetched, c.delegated, c.other),
        now = nowLabel,
        // The reply's 16 dp edge: the line's glyph and the rail sit on it, the steps' words one column in.
        modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-work-line"),
    ) {
        if (entries.isEmpty()) {
            val shown = work.steps.drop(hidden).distinctBy(::stepIdentity)
            shown.forEach { step ->
                StepRow(step, nowMs, folds, onOpenSession, onOpenFile, reply, waitingForYou = stepWaitsForYou(step.state, work.state))
            }
        } else {
            var skipped = 0
            // N-45: the same step reported twice (same part, same command, same state) is one row.
            val seen = HashSet<String>()
            val stepQueues = work.steps.groupBy { it.partId }.mapValues { ArrayDeque(it.value) }
            for (entry in entries) when (entry) {
                is WorkEntryUi.Step -> {
                    val step = stepQueues[entry.partId]?.removeFirstOrNull() ?: continue
                    if (!seen.add(stepIdentity(step))) continue
                    if (skipped++ >= hidden)
                        StepRow(step, nowMs, folds, onOpenSession, onOpenFile, reply, waitingForYou = stepWaitsForYou(step.state, work.state))
                }
                is WorkEntryUi.Detail ->
                    if (skipped >= hidden)
                        TranscriptDetail(
                            entry.value,
                            folds,
                            reasoningExpanded,
                            reply,
                            onCompactAgain,
                            onOpenSession,
                        )
            }
        }
    }
}

/**
 * Review #13 (C-022): the work line of a turn whose only work is thinking. Reasoning is work: it folds under one line,
 * "Thought", shut by default like every finished work line, never a loose paragraph above the reply. Opened, each
 * thought is a line on the rail and opens as the chat's reasoning setting says ([reasoningExpanded]). Nothing is drawn
 * when no thought has words.
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
    val key = "work:$turnId"
    val expanded = folds.open[key] ?: false
    KitWorkLine(
        label = stringResource(Res.string.kitMessageThought),
        stateWords = null,
        state = KitWorkState.Done,
        expanded = expanded,
        onToggle = { folds.open[key] = it },
        icon = KitIcons.idea,
        // The reply's 16 dp edge, as the turn's other work lines.
        modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-work-line"),
    ) {
        shown.forEach { thought ->
            TranscriptDetail(
                thought,
                folds,
                reasoningExpanded,
                reply,
                onCompactAgain,
                onOpenSession,
                thinking = thinking && thought === thoughts.last(),
            )
        }
    }
}

/**
 * Audit 1, item 6: a thought the transcript's reading does not know (a row that arrived before the ledger held its part)
 * is still a folded "Thought" line, never its words printed as a grey paragraph; opened, it shows the thought's text.
 */
@Composable
internal fun FoldedThoughtLine(rowKey: String, text: String, folds: TranscriptFolds?) {
    val key = "thought-line:$rowKey"
    KitWorkLine(
        label = stringResource(Res.string.kitMessageThought),
        stateWords = null,
        state = KitWorkState.Done,
        expanded = folds?.open?.get(key) ?: false,
        onToggle = { folds?.open?.put(key, it) },
        icon = KitIcons.idea,
        modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-work-line"),
    ) {
        if (text.isNotBlank()) KitQuietLine(text.trim())
    }
}

/** How many of the newest steps a running work line keeps in view when opened (Dart `tail: 3`). */
private const val RUNNING_TAIL = 3

/** What makes two step rows the same step: its part, its kind, what it ran and how it ended. */
private fun stepIdentity(step: TurnStep): String =
    "${step.partId}|${step.facts.kind}|${step.facts.subject}|${step.state}"

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
