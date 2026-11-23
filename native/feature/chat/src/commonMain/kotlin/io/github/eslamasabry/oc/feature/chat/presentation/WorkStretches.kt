package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow

/**
 * Slices a turn's [work] down to one [stretch] between visible texts, keeping work step order and assigning
 * the stretch's own state and fold identity (`stretch.key.removePrefix("work:")`).
 */
public fun workOf(work: TurnWork, stretch: WorkStretchUi, last: Boolean): TurnWork {
    val ids = stretch.entries.filterIsInstance<WorkEntryUi.Step>().mapTo(HashSet()) { it.partId }
    val steps = work.steps.filter { it.partId in ids }
    val active = steps.any { it.state == StepState.Pending || it.state == StepState.Running }
    val state =
        when {
            last -> work.state
            work.state == WorkLineState.WaitingForYou && active -> WorkLineState.WaitingForYou
            active -> WorkLineState.Running
            steps.any { it.state == StepState.Failed } -> WorkLineState.EndedFailed
            else -> WorkLineState.Done
        }
    val now = steps.lastOrNull { it.state == StepState.Running }
    return work.copy(
        turnId = stretch.key.removePrefix("work:"),
        steps = steps,
        counts = WorkCounts(steps = steps.size),
        state = state,
        now = now,
    )
}

/**
 * Maps each visible text source (`messageId/partId`) to the key of its last [BlockRow] (highest [BlockRow.start]),
 * so stretches anchored after that text draw right beneath its final block.
 */
public fun lastBlockKeys(rows: List<TranscriptRow>): Map<String, String> {
    val best = LinkedHashMap<String, BlockRow>()
    for (row in rows) {
        if (row !is BlockRow) continue
        val source = row.key.removePrefix("b:").substringBeforeLast(':')
        val prev = best[source]
        if (prev == null || row.start > prev.start) best[source] = row
    }
    return best.mapValues { it.value.key }
}
