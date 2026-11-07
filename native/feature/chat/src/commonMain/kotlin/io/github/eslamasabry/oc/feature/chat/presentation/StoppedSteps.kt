package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.CommandFact

/**
 * P-102: after Stop the server ends every unfinished tool with an error ("Tool execution interrupted", OC1
 * `MessageAbortedError`). That is a stop, not a failure: the step reads Stopped with no time, it is not counted as work
 * done ("ran 1 command" when nothing ran), and the work line keeps the stop mark after a relaunch, when the run's own
 * Interrupted outcome is no longer known and only the steps' errors are left.
 */
public object StoppedSteps {
    private val STOP_TEXT =
        Regex(
            "^\\s*(the )?(step|run|tool execution|operation|request)?\\s*(was )?(interrupted|aborted|cancell?ed)\\b" +
                "|MessageAbortedError|AbortError",
            RegexOption.IGNORE_CASE,
        )

    /** The error a stopped step carries, not a real failure of the tool. */
    public fun isStopError(text: String?): Boolean = text != null && STOP_TEXT.containsMatchIn(text)

    /** A step the server ended because the run stopped. */
    public fun stoppedByRun(step: TurnStep): Boolean =
        step.state == StepState.Failed && isStopError(step.output.errorText)

    /**
     * [work] with every stop-ended step as [StepState.Stopped] (no time), the counts without them, and a turn whose
     * last step was stopped marked Stopped rather than "didn't finish". Returns [work] itself when nothing changes.
     */
    public fun settle(work: TurnWork): TurnWork {
        if (work.steps.none(::stoppedByRun)) return work
        val steps =
            work.steps.map { if (stoppedByRun(it)) it.copy(state = StepState.Stopped, durationMs = null) else it }
        val state =
            if (work.state == WorkLineState.EndedFailed && steps.last().state != StepState.Failed) WorkLineState.Stopped
            else work.state
        val settled = work.copy(steps = steps, state = state)
        return settled.copy(counts = WorkLineRules.split(settled).done)
    }

    /** The commands a turn really ran: a command the stop ended never ran to an end. */
    public fun ranCommands(commands: List<CommandFact>, work: TurnWork?): List<CommandFact> {
        if (work == null) return commands
        val stopped = work.steps.filter { it.state == StepState.Stopped || stoppedByRun(it) }.map { it.partId }.toSet()
        if (stopped.isEmpty()) return commands
        return commands.filterNot { it.failed && it.exitCode == null && it.partId in stopped }
    }
}
