package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.dart.DartJson

/**
 * Decodes the phone engine's `GET /v1/workspace` answer (Dart `TeamWorkspace.fromJson`). A missing field takes Dart's
 * default; the envelope itself must be the shipped contract (schema 1, a numeric revision, the three lists), or the
 * answer is refused as a whole, never shown half-read.
 */
public object TeamWorkspaceJson {
    /** The most projects, tasks and requests one answer may carry (the widget reader's bound). */
    public const val MAX_RECORDS: Int = 4096

    public fun decode(text: String): TeamWorkspace? = (DartJson.decodeOrNull(text) as? Map<*, *>)?.let(::decode)

    public fun decode(map: Map<*, *>): TeamWorkspace? {
        if ((map["schemaVersion"] as? Number)?.toLong() != 1L) return null
        if (map["revision"] !is Number) return null
        val projects = map["projects"] as? List<*> ?: return null
        val servers = map["servers"] as? List<*> ?: return null
        val roles = map["roles"] as? List<*> ?: return null
        var records = projects.size
        val decoded =
            projects.mapNotNull { raw ->
                val project = (raw as? Map<*, *>)?.let(::project) ?: return@mapNotNull null
                records += project.tasks.size + project.requests.size
                if (records > MAX_RECORDS) return null
                project
            }
        return TeamWorkspace(
            schemaVersion = 1,
            revision = map.long("revision"),
            projects = decoded,
            servers = servers.maps().map(::server),
            roles = roles.maps().map(::role),
            simulated = map.bool("simulated", true),
            defaultSettings = (map["defaultSettings"] as? Map<*, *>)?.let(::settings),
        )
    }

    private fun project(j: Map<*, *>): TeamProject? {
        val id = j.str("id")
        if (id.isEmpty()) return null
        return TeamProject(
            id = id,
            name = j.str("name"),
            status = j.str("status", "spec"),
            revision = j.long("revision"),
            settings = settings(j["settings"] as? Map<*, *> ?: emptyMap<String, Any?>()),
            repos = j.list("repos").maps().map(::repo),
            specDraft = spec(j["specDraft"] as? Map<*, *> ?: emptyMap<String, Any?>()),
            specVersions = j.list("specVersions").maps().map(::spec),
            phases = j.list("phases").maps().map(::phase),
            tasks = j.list("tasks").maps().map(::task),
            requests =
                j.list("requests").maps().map {
                    TeamRequest(
                        id = it.str("id"),
                        kind = it.str("kind", "question"),
                        title = it.str("title"),
                        taskId = it.str("taskId"),
                        phaseId = it.str("phaseId"),
                        createdAt = it.str("createdAt"),
                        answered = it.bool("answered"),
                        answer = it.str("answer"),
                    )
                },
            mergeQueue =
                j.list("mergeQueue").maps().map {
                    TeamMergeItem(
                        id = it.str("id"),
                        taskId = it.str("taskId"),
                        repoId = it.str("repoId"),
                        status = it.str("status", "queued"),
                        reason = it.str("reason"),
                        checksPassed = it.bool("checksPassed"),
                    )
                },
            receipts =
                j.list("receipts").maps().map {
                    TeamReceipt(
                        id = it.str("id"),
                        kind = it.str("kind"),
                        repoId = it.str("repoId"),
                        before = it.str("before"),
                        after = it.str("after"),
                        at = it.str("at"),
                        actor = it.str("actor", "person"),
                    )
                },
            timelineTruncated = j.bool("timelineTruncated"),
            spendDay = j.str("spendDay"),
            timeline =
                j.list("timeline").maps().map {
                    TeamTimelineEvent(
                        id = it.str("id"),
                        kind = it.str("kind"),
                        text = it.str("text"),
                        actor = it.str("actor", "fixture"),
                        at = it.str("at"),
                        taskId = it.str("taskId"),
                    )
                },
            planningState =
                (j["planningState"] as? Map<*, *>)?.let {
                    TeamPlanningState(it.str("jobId"), it.str("stage"), it.str("reason"), it.str("updatedAt"))
                },
            spent = j.double("spent") ?: 0.0,
            spentToday = j.double("spentToday") ?: 0.0,
            digestReadAt = j.str("digestReadAt"),
            updatedAt = j.str("updatedAt"),
            planApproved = j.bool("planApproved"),
            quickTask = j.bool("quickTask"),
            usageReported = j.bool("usageReported"),
            budgetWarning = j.bool("budgetWarning"),
            simulated = j.bool("simulated", true),
        )
    }

    internal fun settings(j: Map<*, *>): TeamProjectSettings {
        val budget = j["budget"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return TeamProjectSettings(
            keepWorkingScreenOff = j.bool("keepWorkingScreenOff"),
            mode = j.str("mode"),
            maxLanes = j.int("maxLanes", 1),
            reviewLevel = j.str("reviewLevel", "milestones"),
            chargingOnly = j.bool("chargingOnly"),
            autoFix = j.bool("autoFix", true),
            maxFixRounds = j.int("maxFixRounds", 2),
            budget =
                TeamBudget(
                    chosen = budget.bool("chosen"),
                    unlimited = budget.bool("unlimited"),
                    daily = budget.double("daily"),
                    total = budget.double("total"),
                    taskTokens = (budget["taskTokens"] as? Number)?.toLong(),
                ),
        )
    }

    internal fun repo(j: Map<*, *>): TeamRepo =
        TeamRepo(
            id = j.str("id"),
            name = j.str("name"),
            serverId = j.str("serverId"),
            path = j.str("path"),
            devCommit = j.str("devCommit", "fixture-base"),
            mainCommit = j.str("mainCommit", "fixture-base"),
            checkCommand = j.str("checkCommand", "flutter test"),
            sharedRemote = j.bool("sharedRemote"),
        )

    internal fun phase(j: Map<*, *>): TeamProjectPhase =
        TeamProjectPhase(j.str("id"), j.str("milestoneId"), j.str("title"), j.bool("risky"), j.bool("accepted"))

    internal fun milestone(j: Map<*, *>): TeamMilestone =
        TeamMilestone(j.str("id"), j.str("title"), j.list("criteria").filterIsInstance<String>(), j.bool("accepted"))

    internal fun spec(j: Map<*, *>): TeamSpec =
        TeamSpec(
            version = j.int("version", 1),
            goal = j.str("goal"),
            milestones = j.list("milestones").maps().map(::milestone),
            approvedAt = j.str("approvedAt"),
            contextFiles = j.list("contextFiles").filterIsInstance<String>(),
            constraints = j.str("constraints"),
            decisions = j.str("decisions"),
            outOfScope = j.str("outOfScope"),
            approvedBy = j.str("approvedBy"),
        )

    internal fun task(j: Map<*, *>): TeamTask =
        TeamTask(
            id = j.str("id"),
            title = j.str("title"),
            phaseId = j.str("phaseId"),
            roleId = j.str("roleId", "frontend"),
            repoId = j.str("repoId"),
            serverId = j.str("serverId"),
            status = j.str("status", "queued"),
            dependsOn = j.list("dependsOn").filterIsInstance<String>(),
            criteria = j.list("criteria").filterIsInstance<String>(),
            branch = j.str("branch"),
            reason = j.str("reason"),
            changedAt = j.str("changedAt"),
            steps = j.int("steps", 0),
            tokens = j.long("tokens"),
            fixRounds = j.int("fixRounds", 0),
            messages =
                j.list("messages").maps().map { TeamMessage(it.str("id"), it.str("actor", "person"), it.str("text"), it.str("at")) },
            findings =
                j.list("findings").maps().map {
                    TeamFinding(
                        it.str("id"),
                        it.str("severity", "major"),
                        it.str("criterion"),
                        it.str("location"),
                        it.str("text"),
                        it.str("status", "open"),
                    )
                },
            criterionResults =
                j.list("criterionResults").maps().map { TeamCriterionResult(it.str("criterion"), it.str("status", "unmet")) },
            affected = j.bool("affected"),
            diff = j.str("diff"),
        )

    internal fun server(j: Map<*, *>): TeamServer =
        TeamServer(
            id = j.str("id"),
            name = j.str("name"),
            phone = j.bool("phone"),
            online = j.bool("online", true),
            laneCap = j.int("laneCap", 8),
            chatWaiting = j.bool("chatWaiting"),
            reason = j.str("reason"),
            memoryMb = j.double("memoryMb"),
        )

    internal fun role(j: Map<*, *>): TeamRole =
        TeamRole(j.str("id"), j.str("name"), j.str("model"), j.str("fallbackModel"), j.bool("readOnly"), j.str("instructions"))

    private fun Map<*, *>.str(key: String, default: String = ""): String = this[key] as? String ?: default

    private fun Map<*, *>.bool(key: String, default: Boolean = false): Boolean = this[key] as? Boolean ?: default

    private fun Map<*, *>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0

    private fun Map<*, *>.int(key: String, default: Int): Int = (this[key] as? Number)?.toInt() ?: default

    private fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: emptyList<Any?>()

    private fun List<*>.maps(): List<Map<*, *>> = filterIsInstance<Map<*, *>>()
}
