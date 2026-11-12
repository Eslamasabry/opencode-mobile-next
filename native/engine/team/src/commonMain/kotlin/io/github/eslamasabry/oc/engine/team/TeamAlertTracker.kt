package io.github.eslamasabry.oc.engine.team

/** The four AI Team changes that deserve a push; everything else stays silent (Flutter `TeamAlertTracker`). */
public enum class TeamAlertKind { Decision, Review, RunFailed, Completed }

/** One alert to post: ids only, never a title or a prompt. [key] dedupes it in the shade. */
public data class TeamAlertRequest(val kind: TeamAlertKind, val id: String, val key: String)

/** What one [TeamAlertTracker.observe] found: alerts to post, and keys of decision alerts whose request is gone. */
public data class TeamAlertDiff(val alerts: List<TeamAlertRequest> = emptyList(), val settled: List<String> = emptyList()) {
    val isEmpty: Boolean get() = alerts.isEmpty() && settled.isEmpty()
}

/**
 * Folds successive workspaces of one profile's phone team into alerts. A new unanswered request is a decision (a
 * "findings" request is a review), a task newly failed is a failed run, and a project whose tasks all became finished
 * is a completed run. One alert per id, ever: the first workspace with data is the baseline and announces nothing,
 * because what the person could already see in the app is not news.
 */
public class TeamAlertTracker(private val profileId: String) {
    private var primed = false
    private val knownRequests = mutableSetOf<String>()
    private val taskStates = mutableMapOf<String, String>()
    private val finishedProjects = mutableSetOf<String>()
    private val announced = mutableSetOf<String>()
    private val openRequestAlerts = mutableMapOf<String, String>()

    public fun observe(workspace: TeamWorkspace?): TeamAlertDiff {
        if (workspace == null) return TeamAlertDiff()
        val requests = workspace.projects.flatMap { p -> p.openRequests.map { it.id to it } }.toMap()
        val tasks = workspace.projects.flatMap { it.tasks }.associate { it.id to it.status }
        val done = workspace.projects.filter { p -> p.tasks.isNotEmpty() && p.tasks.all { it.finished } }.map { it.id }.toSet()
        if (!primed) {
            primed = true
            knownRequests += requests.keys
            taskStates += tasks
            finishedProjects += done
            return TeamAlertDiff()
        }
        val alerts = mutableListOf<TeamAlertRequest>()
        val settled = mutableListOf<String>()
        for ((id, request) in requests) {
            if (id in knownRequests || id.isBlank() || !announced.add("request:$id")) continue
            val key = "team:$profileId:gate:$id"
            openRequestAlerts[id] = key
            alerts += TeamAlertRequest(if (request.kind == "findings") TeamAlertKind.Review else TeamAlertKind.Decision, id, key)
        }
        for ((id, key) in openRequestAlerts.toMap()) {
            if (id in requests) continue
            openRequestAlerts.remove(id)
            settled += key
        }
        knownRequests.clear()
        knownRequests += requests.keys
        for ((id, status) in tasks) {
            // A task first seen already failed is history; only a known task turning failed is news.
            if (status != "failed" || id !in taskStates || taskStates[id] == "failed") continue
            if (!announced.add("task:$id")) continue
            alerts += TeamAlertRequest(TeamAlertKind.RunFailed, id, "team:$profileId:run:$id")
        }
        taskStates.clear()
        taskStates += tasks
        for (id in done) {
            if (id in finishedProjects || !announced.add("project:$id")) continue
            alerts += TeamAlertRequest(TeamAlertKind.Completed, id, "team:$profileId:run:$id")
        }
        finishedProjects.clear()
        finishedProjects += done
        return TeamAlertDiff(alerts, settled)
    }

    /** Keys of decision alerts posted and not yet settled, for a dismiss-all. */
    public val openKeys: List<String> get() = openRequestAlerts.values.toList()

    public fun clearOpen() {
        openRequestAlerts.clear()
    }
}
