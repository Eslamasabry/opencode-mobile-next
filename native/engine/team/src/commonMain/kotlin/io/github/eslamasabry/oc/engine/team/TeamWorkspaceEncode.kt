package io.github.eslamasabry.oc.engine.team

/*
 * The typed values a command carries, written as Dart's `toJson` writes them (lib/domain/team_project.dart), so the
 * phone engine reads a native edit exactly as one Flutter sent: every field, in Dart's order, nulls kept.
 */
public object TeamJson {
    public fun readSettings(j: Map<*, *>): TeamProjectSettings = TeamWorkspaceJson.settings(j)

    public fun readRepo(j: Map<*, *>): TeamRepo = TeamWorkspaceJson.repo(j)

    public fun readMilestone(j: Map<*, *>): TeamMilestone = TeamWorkspaceJson.milestone(j)

    public fun readTask(j: Map<*, *>): TeamTask = TeamWorkspaceJson.task(j)

    public fun readPhase(j: Map<*, *>): TeamProjectPhase = TeamWorkspaceJson.phase(j)

    public fun readRole(j: Map<*, *>): TeamRole = TeamWorkspaceJson.role(j)

    public fun settings(s: TeamProjectSettings): Map<String, Any?> =
        linkedMapOf(
            "keepWorkingScreenOff" to s.keepWorkingScreenOff,
            "mode" to s.mode,
            "maxLanes" to s.maxLanes,
            "reviewLevel" to s.reviewLevel,
            "chargingOnly" to s.chargingOnly,
            "autoFix" to s.autoFix,
            "maxFixRounds" to s.maxFixRounds,
            "budget" to
                linkedMapOf(
                    "chosen" to s.budget.chosen,
                    "unlimited" to s.budget.unlimited,
                    "daily" to s.budget.daily,
                    "total" to s.budget.total,
                    "taskTokens" to s.budget.taskTokens,
                ),
        )

    public fun milestone(m: TeamMilestone): Map<String, Any?> =
        linkedMapOf("id" to m.id, "title" to m.title, "criteria" to m.criteria, "accepted" to m.accepted)

    public fun spec(s: TeamSpec): Map<String, Any?> =
        linkedMapOf(
            "contextFiles" to s.contextFiles,
            "version" to s.version,
            "goal" to s.goal,
            "constraints" to s.constraints,
            "decisions" to s.decisions,
            "outOfScope" to s.outOfScope,
            "milestones" to s.milestones.map(::milestone),
            "approvedBy" to s.approvedBy,
            "approvedAt" to s.approvedAt,
        )

    public fun repo(r: TeamRepo): Map<String, Any?> =
        linkedMapOf(
            "id" to r.id,
            "name" to r.name,
            "serverId" to r.serverId,
            "path" to r.path,
            "devCommit" to r.devCommit,
            "mainCommit" to r.mainCommit,
            "checkCommand" to r.checkCommand,
            "sharedRemote" to r.sharedRemote,
        )

    public fun phase(p: TeamProjectPhase): Map<String, Any?> =
        linkedMapOf("id" to p.id, "milestoneId" to p.milestoneId, "title" to p.title, "risky" to p.risky, "accepted" to p.accepted)

    public fun task(t: TeamTask): Map<String, Any?> =
        linkedMapOf(
            "criterionResults" to t.criterionResults.map { linkedMapOf("criterion" to it.criterion, "status" to it.status) },
            "id" to t.id,
            "title" to t.title,
            "phaseId" to t.phaseId,
            "roleId" to t.roleId,
            "repoId" to t.repoId,
            "serverId" to t.serverId,
            "status" to t.status,
            "dependsOn" to t.dependsOn,
            "criteria" to t.criteria,
            "branch" to t.branch,
            "reason" to t.reason,
            "changedAt" to t.changedAt,
            "steps" to t.steps,
            "tokens" to t.tokens,
            "fixRounds" to t.fixRounds,
            "affected" to t.affected,
            "findings" to
                t.findings.map {
                    linkedMapOf(
                        "id" to it.id,
                        "severity" to it.severity,
                        "criterion" to it.criterion,
                        "location" to it.location,
                        "text" to it.text,
                        "status" to it.status,
                    )
                },
            "messages" to t.messages.map { linkedMapOf("id" to it.id, "actor" to it.actor, "text" to it.text, "at" to it.at) },
            "diff" to t.diff,
        )

    public fun role(r: TeamRole): Map<String, Any?> =
        linkedMapOf(
            "id" to r.id,
            "name" to r.name,
            "instructions" to r.instructions,
            "model" to r.model,
            "fallbackModel" to r.fallbackModel,
            "readOnly" to r.readOnly,
        )

    public fun server(s: TeamServer): Map<String, Any?> =
        linkedMapOf(
            "id" to s.id,
            "name" to s.name,
            "phone" to s.phone,
            "online" to s.online,
            "laneCap" to s.laneCap,
            "chatWaiting" to s.chatWaiting,
            "reason" to s.reason,
            "memoryMb" to s.memoryMb,
        )
}
