package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetEngine
import io.github.eslamasabry.oc.engine.team.fleet.FleetMergeResult
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the merge section does (Dart `TeamMergeSection`). */
public interface FleetMergeActions {
    /** Approves the merge request the host holds for this task; reversible on the host, so it goes at once with its receipt. */
    public fun approve()

    /** Merges: the question card asked ("Merge 6 changes into main?"), so the tap is the answer. */
    public fun merge()

    public fun openChanges()

    public fun closeChanges()

    /** Reads the host's readiness again (Try again, and the page's Refresh). */
    public fun reload()
}

/**
 * The merge gate of one task on a computer's team (Dart `TeamMergeSection`'s state): reads the host front's readiness once
 * the task's work is done (a task with open work never costs the host a readiness computation), and sends Approve and
 * Merge as one receipt each, never re-sent. The run page owns the lifetime: it creates this with its view-model scope and
 * embeds [FleetMergeSection] with [state].
 *
 * [fleet] is the page's own collected fleet state (it keeps the connection alive).
 */
public class FleetMergeController(
    private val engine: FleetEngine,
    private val profileId: String,
    private val runId: String,
    private val fleet: StateFlow<FleetState>,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) : FleetMergeActions {
    private val local = MutableStateFlow(FleetMergeLocal())

    public val state: StateFlow<FleetMergeUi> =
        combine(fleet, local) { team, mine -> FleetMergePresent.section(team, runId, mine, nowMs()) }
            .stateIn(scope, SharingStarted.Eagerly, FleetMergeUi())

    /** Dart `teamMergeWaitsOnYou`, for the page's header count. */
    public fun waitsOnYou(): Boolean = FleetMergePresent.waitsOnYou(fleet.value, runId, local.value)

    init {
        scope.launch {
            fleet.first { FleetMergePresent.eligible(it, runId) }
            read()
        }
    }

    private suspend fun read() {
        local.update { it.copy(read = FleetMergeRead.Loading) }
        val found = engine.mergeReadiness(profileId, runId)
        local.update { it.copy(read = found.toRead()) }
    }

    override fun reload() {
        if (!FleetMergePresent.eligible(fleet.value, runId)) return
        scope.launch { read() }
    }

    override fun approve() {
        val request = state.value.requestId ?: return
        if (!state.value.canApprove) return
        send(approving = true) { engine.approveMerge(profileId, request) }
    }

    override fun merge() {
        if (!state.value.canMerge) return
        send(approving = false) { engine.merge(profileId, runId) }
    }

    private fun send(approving: Boolean, call: suspend () -> FleetMergeResult) {
        var admitted = false
        local.update {
            // One act at a time: a second tap while one is on its way is the same tap.
            admitted = it.approve?.status != FleetWriteStatus.Sending && it.merge?.status != FleetWriteStatus.Sending
            when {
                !admitted -> it
                approving -> it.copy(approve = FleetWrite.Sending)
                else -> it.copy(merge = FleetWrite.Sending, mergeResult = null)
            }
        }
        if (!admitted) return
        scope.launch {
            val result = call()
            val write = FleetMergePresent.write(result)
            local.update { if (approving) it.copy(approve = write) else it.copy(merge = write, mergeResult = result) }
            // Dart `approveMergeRequest` / `mergeRun`: the readiness is read again afterwards.
            read()
        }
    }

    override fun openChanges() = local.update { it.copy(changesOpen = true) }

    override fun closeChanges() = local.update { it.copy(changesOpen = false) }
}
