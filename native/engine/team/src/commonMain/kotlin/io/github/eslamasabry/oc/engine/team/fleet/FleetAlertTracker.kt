package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamAlertDiff
import io.github.eslamasabry.oc.engine.team.TeamAlertKind
import io.github.eslamasabry.oc.engine.team.TeamAlertRequest

/**
 * Folds successive snapshots of one computer team into alerts (Dart `TeamAlertTracker`): a new question is a decision, a
 * newly failed task a failed run, work ready to look at a review, a task that just completed a completed run. One alert
 * per gate or run, ever; the first snapshot with data is the baseline and announces nothing.
 *
 * Each request's id names the page a tap opens: `gate:<id>` is the Gate page, `run:<id>` the task page. Ids only; no title
 * or prompt ever leaves this class.
 */
public class FleetAlertTracker(private val profileId: String) {
    private var primed = false
    private val knownGates = mutableSetOf<String>()
    private val runStates = mutableMapOf<String, RunState>()
    private val announced = mutableSetOf<String>()
    private val openGateAlerts = mutableMapOf<String, String>()

    public fun observe(state: FleetState): TeamAlertDiff {
        if (!state.hasData) return TeamAlertDiff()
        val snapshot = state.snapshot
        val gateIds = snapshot.gates.map { it.id }.toSet()
        val states = snapshot.visibleRuns.associate { it.id to it.state }
        if (!primed) {
            primed = true
            knownGates += gateIds
            runStates += states
            return TeamAlertDiff()
        }
        val alerts = mutableListOf<TeamAlertRequest>()
        val settled = mutableListOf<String>()
        for (gate in snapshot.gates) {
            if (gate.id in knownGates) continue
            val kind =
                when (gate.kind) {
                    GateKind.Choice, GateKind.Confirmation, GateKind.FreeText, GateKind.Unknown -> TeamAlertKind.Decision
                    GateKind.RunFailed -> TeamAlertKind.RunFailed
                    GateKind.ReviewReady -> TeamAlertKind.Review
                    GateKind.GateBead -> null
                } ?: continue
            if (!announced.add(gate.id)) continue
            val key = "team:$profileId:gate:${gate.id}"
            openGateAlerts[gate.id] = key
            // A failed task opens its own page when the host names it; every other kind opens the gate.
            val target = if (kind == TeamAlertKind.RunFailed && !gate.runId.isNullOrEmpty()) "run:${gate.runId}" else "gate:${gate.id}"
            alerts += TeamAlertRequest(kind, target, key)
        }
        for ((id, key) in openGateAlerts.toMap()) {
            if (id in gateIds) continue
            openGateAlerts.remove(id)
            settled += key
        }
        knownGates.clear()
        knownGates += gateIds
        for ((id, now) in states) {
            val before = runStates[id]
            // A task never seen that is already complete is history; a known one that just completed is news.
            if (now != RunState.Completed || before == null || before == RunState.Completed) continue
            if (!announced.add("run:$id")) continue
            alerts += TeamAlertRequest(TeamAlertKind.Completed, "run:$id", "team:$profileId:run:$id")
        }
        runStates.clear()
        runStates += states
        return TeamAlertDiff(alerts, settled)
    }

    /** Keys of decision alerts posted and not yet settled, for a dismiss-all. */
    public val openKeys: List<String> get() = openGateAlerts.values.toList()

    public fun clearOpen() {
        openGateAlerts.clear()
    }
}
