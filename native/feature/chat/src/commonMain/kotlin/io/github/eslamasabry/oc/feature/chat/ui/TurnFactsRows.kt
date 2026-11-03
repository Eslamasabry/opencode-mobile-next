package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.feature.chat.presentation.TurnResultUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitCommandBox
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolOutput
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KIT_WORK_STEP_CAP
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCodeShowAll
import io.github.eslamasabry.oc.ui.words.res.kitToolFailed
import io.github.eslamasabry.oc.ui.words.res.kitToolOpenConversation
import io.github.eslamasabry.oc.ui.words.res.kitWorkEarlierSteps
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What is open in the transcript beside the rows: work lines, steps, results and the step outputs shown in full.
 * Screen-local (a fold is not an engine fact); keyed by row/step ids, never by index.
 */
@Stable
internal class TranscriptFolds(
    val open: SnapshotStateMap<String, Boolean>,
    val full: SnapshotStateMap<String, Boolean>,
    val earlier: SnapshotStateMap<String, Int>,
)

@Composable
internal fun rememberTranscriptFolds(): TranscriptFolds =
    remember { TranscriptFolds(mutableStateMapOf(), mutableStateMapOf(), mutableStateMapOf()) }

private fun WorkLineState.toKit(): KitWorkState =
    when (this) {
        WorkLineState.Running -> KitWorkState.Running
        WorkLineState.WaitingForYou -> KitWorkState.WaitingForYou
        WorkLineState.Done -> KitWorkState.Done
        WorkLineState.EndedFailed -> KitWorkState.EndedFailed
        WorkLineState.Stopped -> KitWorkState.Stopped
    }

/**
 * A turn's work under ONE line (B-CHAT-063, plan 09 chat-transcript-turn-model). Folded, it says what was done (or,
 * while running, what is happening now); open, it lists the steps oldest first, each a [KitToolRow] in plain words
 * with its status, duration and counts (B-CHAT-109/111), opening to its output (B-CHAT-112). Ended-failed work opens
 * by itself until the person folds it.
 */
@Composable
internal fun TurnWorkLine(work: TurnWork, nowMs: Long, folds: TranscriptFolds, onOpenSession: ((String) -> Unit)? = null) {
    val key = "work:${work.turnId}"
    val kitState = work.state.toKit()
    val expanded = folds.open[key] ?: kitState.opensByDefault
    val summary = workSummary(work.counts)
    val now = work.now
    val label = if (now != null && !expanded) nowWords(now) else summary
    val hidden = (work.steps.size - KIT_WORK_STEP_CAP - (folds.earlier[key] ?: 0)).coerceAtLeast(0)
    val earlier =
        if (expanded && hidden > 0) {
            val n = minOf(hidden, KIT_WORK_STEP_CAP)
            KitLineAction(pluralStringResource(Res.plurals.kitWorkEarlierSteps, n, n), {
                folds.earlier[key] = (folds.earlier[key] ?: 0) + n
            })
        } else null
    KitWorkLine(
        label = label,
        stateWords = workStateWords(work.state),
        state = kitState,
        expanded = expanded,
        onToggle = { folds.open[key] = it },
        earlier = earlier,
        modifier = Modifier.testTag("chat-work-line"),
    ) {
        for (step in work.steps.drop(hidden)) StepRow(step, nowMs, folds, onOpenSession)
    }
}

@Composable
private fun nowWords(step: TurnStep): String {
    val title = stepTitle(step.facts)
    val subject = step.facts.subject
    // B-SEC-094 / R18: the subject is a command or path the agent wrote; it reads redacted like every mono surface.
    val mask = rememberKitMask()
    return if (subject.isNullOrBlank()) title else "$title · ${mask(subject)}"
}

@Composable
private fun StepRow(step: TurnStep, nowMs: Long, folds: TranscriptFolds, onOpenSession: ((String) -> Unit)?) {
    val key = "step:${step.partId}"
    val f = step.facts
    val runningFor = if (step.state == StepState.Running) step.startedAtMs?.let { (nowMs - it).coerceAtLeast(0) } else null
    val view =
        KitToolView(
            id = step.partId,
            kind = f.kind.toKit(),
            title = stepTitle(f),
            subject = f.subject,
            detail = f.detail,
            status = step.state.toKit(),
            durationLabel = (step.durationMs ?: runningFor)?.let { durationWords(it) },
            added = f.added,
            removed = f.removed,
            statusLabel = stepStatus(step.state, f.exitCode),
        )
    val out = step.output
    val text = out.text.takeIf { it.isNotEmpty() }
    val shell = f.kind == ToolKind.Shell
    val child = f.childSessionId?.takeIf { onOpenSession != null }
    val hasBody = text != null || (shell && !f.subject.isNullOrBlank()) || child != null
    val full = folds.full[key] == true
    val lines = text?.lines()?.size ?: 0
    val more = pluralStringResource(Res.plurals.kitCodeShowAll, lines, lines)
    KitToolRow(
        view = view,
        opened = folds.open[key] == true,
        onToggle = { folds.open[key] = !(folds.open[key] ?: false) },
        modifier = Modifier.testTag("chat-step-${step.partId}"),
        body =
            if (!hasBody) null
            else {
                {
                    if (child != null && onOpenSession != null)
                        KitButton(
                            stringResource(Res.string.kitToolOpenConversation),
                            { onOpenSession(child) },
                            role = KitButtonRole.Tertiary,
                            modifier = Modifier.testTag("chat-step-open-session"),
                        )
                    if (shell)
                        KitCommandBox(
                            command = f.subject ?: "",
                            output = text,
                            maxLines = if (full) Int.MAX_VALUE else io.github.eslamasabry.oc.ui.kitchat.tool.KIT_COMMAND_OUTPUT_LINES,
                            moreLabel = more,
                            onMore = { folds.full[key] = true },
                            exitLabel = null,
                        )
                    else if (text != null)
                        KitToolOutput(
                            text = text,
                            moreLabel = more,
                            onMore = { folds.full[key] = true },
                            maxLines = if (full) Int.MAX_VALUE else io.github.eslamasabry.oc.ui.kitchat.tool.KIT_COMMAND_OUTPUT_LINES,
                            errorLabel = if (out.errorText != null) stringResource(Res.string.kitToolFailed) else null,
                        )
                }
            },
    )
}

/**
 * What a finished turn did (B-CHAT-068/100/101) and how long it took (B-CHAT-092), under its footer: one quiet line
 * ("Edited 2 files · ran 3 commands") that opens to the files with their counts and the commands with their exit
 * codes. Nothing is drawn when the turn changed and ran nothing and was not timed.
 */
@Composable
internal fun TurnResultLine(turnId: String, result: TurnResultUi, folds: TranscriptFolds) {
    val summary = resultSummary(result)
    val timing = result.timing?.let { timingWords(it) }
    if (summary == null) {
        if (timing != null) KitQuietLine(timing, Modifier.testTag("chat-turn-timing"))
        return
    }
    val key = "result:$turnId"
    // B-SEC-094 / R18: a command line is server text; a key it carries never reaches the screen.
    val mask = rememberKitMask()
    Column(Modifier.testTag("chat-turn-result")) {
        KitExpandRow(
            title = summary,
            expanded = folds.open[key] == true,
            supporting = timing,
            onExpandedChange = { folds.open[key] = it },
        ) {
            for (file in result.files) KitQuietLine(fileLine(mask(file.path), file.added, file.removed))
            for (command in result.commands) {
                val exit = command.exitCode
                val line = mask(command.command)
                KitQuietLine(
                    if (exit == null) "$ $line"
                    else "$ $line  ·  " + stepStatus(if (exit == 0) StepState.Done else StepState.Failed, exit)
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
        StepState.NotRun -> KitToolStatus.Stopped
    }

private fun ToolKind.toKit(): KitToolKind =
    when (this) {
        ToolKind.Read,
        ToolKind.Lsp -> KitToolKind.Read
        ToolKind.List,
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

