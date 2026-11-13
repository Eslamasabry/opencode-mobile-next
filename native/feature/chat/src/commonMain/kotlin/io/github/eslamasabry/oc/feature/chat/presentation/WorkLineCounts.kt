package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts

/**
 * P-012: the work line's words in two parts. [done] counts only finished steps (said in the past tense: "edited 1
 * file"); the rest are still waiting or running and are said as "waiting to edit 1 file", "waiting to run 1 command",
 * "1 step waiting".
 */
public data class WorkLineCounts(
    val done: WorkCounts,
    val waitingEdit: Int = 0,
    val waitingRun: Int = 0,
    val waitingOther: Int = 0,
) {
    val waiting: Int
        get() = waitingEdit + waitingRun + waitingOther
}

public object WorkLineRules {
    private val editTools = setOf("edit", "write", "patch", "apply_patch", "multiedit")
    private val runTools = setOf("bash", "shell")

    /**
     * Not yet doing its work: a step the server has not started, or (while a request waits for the person) the one
     * that asks. A step that simply runs is counted with the finished ones, as Flutter's `_workCounts` does ("Ran 5
     * commands"), never as "waiting to run" (owner's merge41 screenshot, 2026-11-09).
     */
    private fun unfinished(step: TurnStep, asking: Boolean): Boolean =
        step.state == StepState.Pending || (asking && step.state == StepState.Running)

    /**
     * Splits [work]'s counts into finished and unfinished steps. With every step finished it is the engine's own
     * counts, unchanged; otherwise the finished ones are counted again by the same rules (`TurnWork.count`): a file
     * written then edited is one file, a step the server never ran is "not run".
     */
    public fun split(work: TurnWork): WorkLineCounts {
        // P-102 / review #2: a stopped, declined or failed step did not do its work; it is not counted as "edited",
        // "read" or "ran" (the engine's counts include failed steps, so any of them means counting again).
        val asking = work.state == WorkLineState.WaitingForYou
        if (work.steps.none { unfinished(it, asking) || it.state == StepState.Stopped || it.state == StepState.Declined || it.state == StepState.Failed })
            return WorkLineCounts(work.counts)
        var out = WorkLineCounts(WorkCounts(steps = work.counts.steps))
        val editedFiles = HashSet<String>()
        val waitingFiles = HashSet<String>()
        for (step in work.steps) {
            out =
                if (unfinished(step, asking)) waiting(out, step, waitingFiles)
                else out.copy(done = finished(out.done, step, editedFiles))
        }
        return out
    }

    private fun path(step: TurnStep): String? = step.facts.subject?.trim()?.takeIf { it.isNotEmpty() }

    private fun name(step: TurnStep): String = step.facts.toolName.trim().lowercase()

    private fun waiting(c: WorkLineCounts, step: TurnStep, files: MutableSet<String>): WorkLineCounts =
        when (name(step)) {
            in editTools -> {
                val path = path(step)
                if (path == null || files.add(path)) c.copy(waitingEdit = c.waitingEdit + 1) else c
            }
            in runTools -> c.copy(waitingRun = c.waitingRun + 1)
            else -> c.copy(waitingOther = c.waitingOther + 1)
        }

    /**
     * Review #2 (C-058): only work that happened is said in the past tense. A stopped or declined step did nothing; a
     * failed one did nothing either (a failed edit leaves the file as it was, a failed read read nothing, and
     * OpenCode 2 ends a call the person refused with a note as an error carrying only the note), except a command
     * that ran and exited with an error: its exit code is the proof that it ran.
     */
    private fun didItsWork(step: TurnStep): Boolean =
        when (step.state) {
            StepState.Stopped,
            StepState.Declined -> false
            StepState.Failed -> name(step) in runTools && step.facts.exitCode != null
            else -> true
        }

    private fun finished(c: WorkCounts, step: TurnStep, files: MutableSet<String>): WorkCounts {
        if (step.state == StepState.NotRun) return c.copy(notRun = c.notRun + 1)
        if (!didItsWork(step)) return c
        return when (name(step)) {
            "read" -> c.copy(read = c.read + 1)
            "glob",
            "grep" -> c.copy(searched = c.searched + 1)
            "list" -> c.copy(listed = c.listed + 1)
            in editTools -> {
                val path = path(step)
                if (path == null || files.add(path)) c.copy(edited = c.edited + 1) else c
            }
            in runTools -> c.copy(ran = c.ran + 1)
            "webfetch",
            "websearch" -> c.copy(fetched = c.fetched + 1)
            "task",
            "subagent" -> c.copy(delegated = c.delegated + 1)
            else -> c.copy(other = c.other + 1)
        }
    }
}
