package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.ledger.turnWork
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BACKGROUND_TASK_MAX_AGE_MS
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-CHAT-311..313: a command an agent's turn left running. Antigravity waits a few seconds for a command, moves it to
 * the background and ends its turn with the call still running (the Paseo mapper flags such a step `background: true`,
 * see mappers/PaseoAgentContinue.kt); the helper says nothing more until the next prompt. The engine leaves the step
 * alone in settling, but stops every running step of a turn that is not the newest busy one, so it read "Stopped before
 * it finished" while the command went on. Here such a step reads Running again and, while the chat is not busy, joins
 * the background line: for as long as the app believes background work, and never in a turn the person stopped. Its
 * own `completed` report ends it: the mapper then sets no flag, the engine reads Done, and nothing here applies.
 */
public object LeftRunningCommands {
    /**
     * [work] as the transcript reads the work of a turn that is not running: every stop-ended step settled
     * ([StoppedSteps.settle]), then the commands it left running kept Running ([keepRunning]).
     */
    public fun settle(work: TurnWork, nowMs: Long): TurnWork = keepRunning(StoppedSteps.settle(work), nowMs)

    /**
     * [work] with each command its turn left running reading [StepState.Running] again: no time yet, and counted as
     * work that ran, as a running command is. [work] itself when nothing changes, and in a turn the person stopped.
     */
    public fun keepRunning(work: TurnWork, nowMs: Long): TurnWork {
        val stopped = work.state == WorkLineState.Stopped
        if (stopped || work.steps.none { it.state == StepState.Stopped && leftSince(it, nowMs) != null }) return work
        val steps =
            work.steps.map { step ->
                if (step.state == StepState.Stopped && leftSince(step, nowMs) != null) {
                    step.copy(state = StepState.Running, durationMs = null)
                } else {
                    step
                }
            }
        val kept = work.copy(steps = steps)
        return kept.copy(counts = WorkLineRules.split(kept).done)
    }

    /**
     * The commands [work] still reads running (after [keepRunning]) as the background line's tasks, oldest first and
     * one per call id: the part's own call id, or its id when it gave none. [messages] is the conversation as the
     * ledger holds it, oldest first; each task names the message that holds its step. While the chat is [busy] the
     * line holds none of them: a running reply's command is the reply's own step.
     */
    public fun tasks(
        messages: List<MessageWithParts>,
        work: Map<String, TurnWork>,
        nowMs: Long,
        busy: Boolean = false,
    ): List<BackgroundTask> {
        val turns = if (busy) emptyList() else work.values
        val starts: Map<String, Long> =
            turns
                .filter { it.state != WorkLineState.Stopped }
                .flatMap { it.steps }
                .filter { it.state == StepState.Running }
                .mapNotNull { step -> leftSince(step, nowMs)?.let { step.partId to it } }
                .toMap()
        if (starts.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val found =
            messages.flatMap { message ->
                message.parts.mapNotNull { part ->
                    val since = starts[part.id] ?: return@mapNotNull null
                    val callId = part.callId?.takeIf { it.isNotEmpty() } ?: part.id
                    if (!seen.add(callId)) return@mapNotNull null
                    val command = commandOf(part.tool?.input)
                    BackgroundTask(callId, message.info.id, since, backgroundTaskName(command), command)
                }
            }
        return found.sortedBy { it.sinceMs }
    }

    /**
     * The work of a conversation that is not busy as the transcript reads it, for the clock (which has no work of its
     * own): [settle] over every turn.
     */
    public fun work(snapshot: LedgerSnapshot, stopped: Boolean, nowMs: Long): Map<String, TurnWork> =
        turnWork(snapshot, busy = false, stopped = stopped).mapValues { settle(it.value, nowMs) }

    /**
     * When [step]'s command started, when the step is a command its turn left running that the app still believes runs:
     * the mapper flagged it `background`, and it started less than [BACKGROUND_TASK_MAX_AGE_MS] ago. Null for any other
     * step, and for one the server gave no start time (then nothing says how long it has run).
     */
    private fun leftSince(step: TurnStep, nowMs: Long): Long? {
        val start = step.startedAtMs ?: return null
        val flag =
            (step.output.metadata?.get("background") as? JsonPrimitive)?.let {
                !it.isString && it.content == "true"
            }
        return if (flag == true && nowMs - start < BACKGROUND_TASK_MAX_AGE_MS) start else null
    }

    /** The command a shell step ran, as its input gave it; empty when it gave none. */
    private fun commandOf(input: JsonObject?): String =
        (input?.get("command") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
}
