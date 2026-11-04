package io.github.eslamasabry.oc.engine.transcript.ledger

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolOutputView
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundShellToolPart
import io.github.eslamasabry.oc.engine.transcript.turnfacts.isBackgroundTaskNotice
import io.github.eslamasabry.oc.engine.transcript.turnfacts.settleStaleLive
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolDurationMs
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * How one step went, after settling (B-CHAT-117). Failed is drawn neutral; NotRun = the server
 * never ran it.
 */
public enum class StepState {
    Pending,
    Running,
    Done,
    Failed,
    Stopped,
    NotRun,
}

/**
 * One step of a turn's work: the tool call's facts (plain-word phrase, subject, counts), its
 * settled state, how long it took ([durationMs], null until both times are known) and what it put
 * out (text with whitespace kept, JSON pretty).
 */
public data class TurnStep(
    val partId: String,
    val facts: ToolFacts,
    val state: StepState,
    val durationMs: Long?,
    /** When it started, for a running step's own clock; null if the server gave no time. */
    val startedAtMs: Long?,
    val output: ToolOutputView,
)

/**
 * What the work did, counted for the work line's words (Dart `_workCounts`, transcript_turns.dart).
 */
public data class WorkCounts(
    val read: Int = 0,
    val searched: Int = 0,
    val listed: Int = 0,
    val edited: Int = 0,
    val ran: Int = 0,
    val fetched: Int = 0,
    val delegated: Int = 0,
    val other: Int = 0,
    val notRun: Int = 0,
    val steps: Int = 0,
)

/** Where a turn's work stands (Dart `KitWorkState`). */
public enum class WorkLineState {
    Running,
    WaitingForYou,
    Done,
    EndedFailed,
    Stopped,
}

/**
 * A turn's work folded under its one line (B-CHAT-063): steps oldest first, counts, state, the step
 * running now.
 */
public data class TurnWork(
    val turnId: String,
    val steps: List<TurnStep>,
    val counts: WorkCounts,
    val state: WorkLineState,
    /** The newest step still running, for the folded line's "what is happening now". */
    val now: TurnStep?,
)

/**
 * The work of every turn in [snapshot] (CHAT-TRANSCRIPT, B-CHAT-063/117): tool calls settle first
 * (a spinner stops once later words of its turn exist or the run ended; background processes keep
 * running), then each turn with at least one step gets its [TurnWork]. [busy]: a run is going;
 * [stopped]: the last run was stopped by the person or server; [waitingForYou]: a request waits for
 * the person (the running line never spins then).
 */
public fun turnWork(
    snapshot: LedgerSnapshot,
    busy: Boolean,
    stopped: Boolean = false,
    waitingForYou: Boolean = false,
): Map<String, TurnWork> {
    if (snapshot.messages.isEmpty()) return emptyMap()
    val settled = settleStaleLive(snapshot.messages, busy)
    val turns = if (settled === snapshot.messages) snapshot.turns else TurnLedger.group(settled)
    val out = LinkedHashMap<String, TurnWork>()
    for ((index, turn) in turns.withIndex()) {
        val newest = index == turns.lastIndex
        work(
                turn,
                newest = newest,
                busy = busy,
                stopped = stopped && newest,
                waiting = waitingForYou && newest,
            )
            ?.let { out[turn.turnId] = it }
    }
    return out
}

private fun work(
    turn: LedgerTurn,
    newest: Boolean,
    busy: Boolean,
    stopped: Boolean,
    waiting: Boolean,
): TurnWork? {
    val steps = ArrayList<TurnStep>()
    var counts = WorkCounts()
    var modelSteps = 0
    for (m in turn.messages) {
        if (m.info.role != Role.Assistant) continue
        for (rawPart in m.parts) {
            val p = backgroundShellToolPart(rawPart) ?: rawPart
            if (p.type == PartType.StepStart) modelSteps++
            if (p.type != PartType.Tool) continue
            // Claude Code's "a background task ended" notice is a notice, not a step of this turn.
            if (isBackgroundTaskNotice(p.toolName ?: "")) continue
            val step = step(p, live = newest && busy) ?: continue
            steps += step
            counts = count(counts, p, step)
        }
    }
    if (steps.isEmpty()) return null
    counts = counts.copy(steps = maxOf(modelSteps, 1))
    val now = steps.lastOrNull { it.state == StepState.Running || it.state == StepState.Pending }
    val state =
        when {
            waiting -> WorkLineState.WaitingForYou
            now != null && newest && busy -> WorkLineState.Running
            stopped -> WorkLineState.Stopped
            steps.last().state == StepState.Failed && !answeredAfterLastStep(turn) ->
                WorkLineState.EndedFailed
            else -> WorkLineState.Done
        }
    return TurnWork(
        turn.turnId,
        steps,
        counts,
        state,
        now.takeIf { state == WorkLineState.Running },
    )
}

private fun step(p: Part, live: Boolean): TurnStep? {
    val tool = p.tool ?: return null
    val state =
        when {
            !tool.ran ->
                if (live && tool.status == ToolStatus.Pending) StepState.Pending
                else StepState.NotRun
            else ->
                when (tool.status) {
                    ToolStatus.Pending -> StepState.Pending
                    ToolStatus.Running -> StepState.Running
                    ToolStatus.Completed -> StepState.Done
                    ToolStatus.Error -> StepState.Failed
                    ToolStatus.Cancelled -> StepState.Stopped
                }
        }
    // A run that ended leaves no spinner behind (settling already finished what it could; the rest
    // stopped).
    val settled =
        if (!live && (state == StepState.Running || state == StepState.Pending)) StepState.Stopped
        else state
    // A stopped synthetic shell result still carries useful output. Other canceled tools keep their
    // normal
    // empty-output policy; this marker is created only by the display projection above.
    val outputTool =
        if (
            tool.status == ToolStatus.Cancelled &&
                (tool.metadata?.get("backgroundShellResult") as? JsonPrimitive)?.booleanOrNull ==
                    true
        )
            tool.copy(status = ToolStatus.Completed)
        else tool
    return TurnStep(
        p.id,
        toolFacts(p),
        settled,
        toolDurationMs(tool),
        tool.startedAtMs,
        toolOutputView(outputTool, p.toolName),
    )
}

private fun count(c: WorkCounts, p: Part, step: TurnStep): WorkCounts {
    if (step.state == StepState.NotRun) return c.copy(notRun = c.notRun + 1)
    return when (p.toolName?.trim()?.lowercase() ?: "") {
        "read" -> c.copy(read = c.read + 1)
        "glob",
        "grep" -> c.copy(searched = c.searched + 1)
        "list" -> c.copy(listed = c.listed + 1)
        "edit",
        "write",
        "patch",
        "apply_patch",
        "multiedit" -> c.copy(edited = c.edited + 1)
        "bash",
        "shell" -> c.copy(ran = c.ran + 1)
        "webfetch",
        "websearch" -> c.copy(fetched = c.fetched + 1)
        "task",
        "subagent" -> c.copy(delegated = c.delegated + 1)
        else -> c.copy(other = c.other + 1)
    }
}

/**
 * Words after the last failed step mean the agent got past it: the work is Done, the failure stays
 * quiet.
 */
private fun answeredAfterLastStep(turn: LedgerTurn): Boolean {
    var afterTool = false
    for (m: MessageWithParts in turn.messages) {
        if (m.info.role != Role.Assistant) continue
        for (rawPart in m.parts) {
            val p = backgroundShellToolPart(rawPart) ?: rawPart
            if (p.type == PartType.Tool && !isBackgroundTaskNotice(p.toolName ?: ""))
                afterTool = false
            else if (p.type == PartType.Text && p.text.isNotBlank()) afterTool = true
        }
    }
    return afterTool
}
