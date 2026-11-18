package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind

/** One row of a turn's step list (D1 "steps only", owner 2026-11-14), in the turn's real order. */
public sealed interface StepEntry {
    public val key: String

    /** One tool step: its own row with the kind's glyph, its words, counts and time. */
    public data class Step(val step: TurnStep) : StepEntry {
        override val key: String
            get() = "step:${step.partId}"
    }

    /** A thought, or short words the agent said while it worked, on the rail where they happened. */
    public data class Detail(val detail: TranscriptDetailUi) : StepEntry {
        override val key: String
            get() = detail.key
    }

    /** Sub-agents the agent ran at the same time: one row, its children on a small tree. */
    public data class Group(val steps: List<TurnStep>) : StepEntry {
        override val key: String
            get() = "group:${steps.first().partId}"
    }

    /**
     * Steps of one [family] one after another (owner 2026-11-14: eleven command rows read the same): one row with a
     * count ("Ran 6 commands") that opens in place to each step. Its key is its first step's row key, so a step a second
     * one joins keeps its row (it does not leave and come back) and nothing flashes.
     */
    public data class Run(val family: StepFamily, val steps: List<TurnStep>) : StepEntry {
        override val key: String
            get() = "step:${steps.first().partId}"
    }
}

/** The kinds of step that fold into one row when they come one after another. Edits stay rows of their own. */
public enum class StepFamily { Commands, Reads, Searches, Lists }

/** What the fold row says: [hidden] rows behind it, and how many [declined] and [failed] steps lie among them. */
public data class StepFold(val hidden: Int, val declined: Int, val failed: Int)

/** A turn's step list cut for the screen: [earlier] fold behind [fold] (null = nothing folds), [shown] stay in view. */
public data class StepListCut(val earlier: List<StepEntry>, val shown: List<StepEntry>, val fold: StepFold?)

/** How many sub-agents of a group stand where (the group row's quiet words). */
public data class GroupCounts(
    val done: Int,
    val working: Int,
    val failed: Int,
    val declined: Int,
    val stopped: Int,
    /** Sub-agents with an open request of their own: they are neither done nor working, they wait on the person. */
    val waiting: Int = 0,
)

/**
 * The rules of a turn's step list (D1): which rows it has, which sub-agents make one group, and what folds.
 *
 * Fold rule (owner 2026-11-14, "when collapsed it takes a huge space"; the Flutter app's one quiet line): a turn that
 * ended well ([sums]) is ONE row that opens in place to every step. While the turn runs, and when it ended on a failure,
 * was stopped (it "shows its step", said Stopped once) or waits for the person, the newest [SHOWN] rows stay in view; older rows fold behind ONE first
 * row only when at least two would fold (one row behind a row saves nothing). That row names the declined and failed
 * steps inside it (a group counts its children), so a problem is never folded away silently. A step that waits for the
 * person is the newest row, so it is never folded. (D1's first fold rule kept five rows in view after the turn ended
 * too; it was the plan's own choice, not an owner decision, and the owner's picture showed it took a quarter of the
 * screen.)
 */
public object StepListRules {
    /** Rows that stay in view while a turn runs: the newest three, live. */
    public const val SHOWN: Int = 3

    /**
     * A turn in [state] shows its work as one summary row: it ended well. A turn that runs, waits for the person, was
     * stopped or ended on a failure stays open at its newest steps, so the step where it stands is in view.
     */
    public fun sums(state: WorkLineState): Boolean = state == WorkLineState.Done

    /**
     * The rows of [work] in the order they happened. [entries] is the reading's order of steps and folded words (empty
     * when the reading has none: then the steps alone, oldest first). The same step reported twice (same part, kind,
     * subject and state) is one row. Sub-agents started together become one [StepEntry.Group].
     */
    public fun entries(work: TurnWork, entries: List<WorkEntryUi>): List<StepEntry> {
        val seen = HashSet<String>()
        val rows = ArrayList<StepEntry>()
        if (entries.isEmpty()) {
            for (step in work.steps) if (seen.add(identity(step))) rows += StepEntry.Step(step)
        } else {
            val queues = work.steps.groupBy { it.partId }.mapValues { ArrayDeque(it.value) }
            for (entry in entries) {
                when (entry) {
                    is WorkEntryUi.Step -> {
                        val step = queues[entry.partId]?.removeFirstOrNull() ?: continue
                        if (seen.add(identity(step))) rows += StepEntry.Step(step)
                    }
                    is WorkEntryUi.Detail -> rows += StepEntry.Detail(entry.value)
                }
            }
        }
        return runs(group(rows), waiting = work.state == WorkLineState.WaitingForYou)
    }

    /** The family [step] folds into a run with, or null for a step that is always its own row. */
    public fun family(step: TurnStep): StepFamily? =
        when (step.facts.kind) {
            ToolKind.Shell -> StepFamily.Commands
            ToolKind.Read,
            ToolKind.Lsp -> StepFamily.Reads
            ToolKind.Grep,
            ToolKind.Glob -> StepFamily.Searches
            ToolKind.List -> StepFamily.Lists
            else -> null
        }

    /**
     * Steps of one family one after another become one [StepEntry.Run] (two or more); anything else between them (the
     * agent's words, a thought, another kind of step) ends the run. A step that waits for the person ([waiting]: the
     * turn waits and the step has not run) stays its own row, so its question is in view under it.
     */
    internal fun runs(rows: List<StepEntry>, waiting: Boolean = false): List<StepEntry> {
        val out = ArrayList<StepEntry>(rows.size)
        var run = ArrayList<TurnStep>()
        var kind: StepFamily? = null
        fun flush() {
            val f = kind
            when {
                run.isEmpty() -> Unit
                run.size == 1 || f == null -> run.forEach { out += StepEntry.Step(it) }
                else -> out += StepEntry.Run(f, run)
            }
            run = ArrayList()
            kind = null
        }
        for (row in rows) {
            val step = (row as? StepEntry.Step)?.step
            val f = step?.let { family(it) }
            val asks = step != null && waiting && (step.state == StepState.Running || step.state == StepState.Pending)
            if (step == null || f == null || asks) {
                flush()
                out += row
                continue
            }
            if (f != kind) flush()
            kind = f
            run += step
        }
        flush()
        return out
    }

    /** [entries] cut into what folds and what stays in view ([shown] rows, see the fold rule). */
    public fun cut(entries: List<StepEntry>, shown: Int = SHOWN): StepListCut {
        if (entries.size <= shown + 1) return StepListCut(emptyList(), entries, null)
        val earlier = entries.dropLast(shown)
        val kept = entries.takeLast(shown)
        var declined = 0
        var failed = 0
        for (row in earlier) {
            for (state in states(row)) {
                if (state == StepState.Declined) declined++
                if (state == StepState.Failed) failed++
            }
        }
        return StepListCut(earlier, kept, StepFold(earlier.size, declined, failed))
    }

    /** Where a group's sub-agents stand. */
    public fun counts(group: StepEntry.Group, waitingSessions: Set<String> = emptySet()): GroupCounts {
        var done = 0
        var working = 0
        var failed = 0
        var declined = 0
        var stopped = 0
        var waiting = 0
        for (s in group.steps) {
            // The same open-requests set that makes a parent read "Needs you": a sub-agent that asks is not done.
            if (s.facts.childSessionId?.let { it in waitingSessions } == true) {
                waiting++
                continue
            }
            when (s.state) {
                StepState.Done -> done++
                StepState.Running,
                StepState.Pending -> working++
                StepState.Failed -> failed++
                StepState.Declined -> declined++
                StepState.Stopped,
                StepState.NotRun -> stopped++
            }
        }
        return GroupCounts(done, working, failed, declined, stopped, waiting)
    }

    /**
     * Adjacent sub-agent steps that ran at the same time make one group: each one started before an earlier one of the
     * run had ended (or while it still ran). Without both times nothing proves they ran together, so they stay rows of
     * their own (a group never claims an order or a togetherness the data does not show). A run of one stays a step.
     */
    internal fun group(rows: List<StepEntry>): List<StepEntry> {
        val out = ArrayList<StepEntry>(rows.size)
        var run = ArrayList<TurnStep>()
        fun flush() {
            when (run.size) {
                0 -> Unit
                1 -> out += StepEntry.Step(run.first())
                else -> out += StepEntry.Group(run)
            }
            run = ArrayList()
        }
        for (row in rows) {
            val step = (row as? StepEntry.Step)?.step
            if (step == null || step.facts.kind != ToolKind.Task) {
                flush()
                out += row
                continue
            }
            if (run.isNotEmpty() && !together(run, step)) flush()
            run += step
        }
        flush()
        return out
    }

    /** [next] started while one of [run] still ran (both times known). */
    private fun together(run: List<TurnStep>, next: TurnStep): Boolean {
        val start = next.startedAtMs ?: return false
        return run.any { s ->
            val began = s.startedAtMs ?: return@any false
            val running = s.state == StepState.Running || s.state == StepState.Pending
            val end = s.durationMs?.let { began + it } ?: if (running) Long.MAX_VALUE else return@any false
            start in began until end
        }
    }

    private fun states(row: StepEntry): List<StepState> =
        when (row) {
            is StepEntry.Step -> listOf(row.step.state)
            is StepEntry.Group -> row.steps.map { it.state }
            is StepEntry.Run -> row.steps.map { it.state }
            is StepEntry.Detail -> emptyList()
        }

    /** What makes two step rows the same step: its part, its kind, what it ran and how it ended. */
    private fun identity(step: TurnStep): String = "${step.partId}|${step.facts.kind}|${step.facts.subject}|${step.state}"
}
