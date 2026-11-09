package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the team's projects, boards and tasks open on the workspace they last read,
 * then the live read replaces it in place. The app binds this to its last-known store (per profile and server,
 * size-capped, cleared with the profile); without a binding the team loads as before.
 */
public interface TeamLastKnown {
    public fun peek(profileId: String): KeptTeam?

    public suspend fun read(profileId: String): KeptTeam?

    public suspend fun keep(profileId: String, kept: KeptTeam)
}

/**
 * A workspace as the pages draw it, in the engine's own schema-1 JSON (so [TeamWorkspaceJson] reads it back). Never
 * kept: task patches, role instructions, older spec versions. Team auth lives in the Keystore and is not in a workspace.
 */
@Serializable
public data class KeptTeam(val workspace: String) {
    public fun decode(): TeamWorkspace? = TeamWorkspaceJson.decode(workspace)?.takeIf { it.projects.isNotEmpty() || it.servers.isNotEmpty() }

    public companion object {
        public const val MAX_CHARS: Int = 160_000
        private const val MAX_MESSAGES = 10
        private const val MAX_TIMELINE = 30

        /** Trimmed in steps until it fits [MAX_CHARS]: full rows first, then without conversations and findings. */
        public fun of(ws: TeamWorkspace): KeptTeam? {
            for (level in 0..1) {
                val text = DartJson.encode(workspace(ws, level))
                if (text.length <= MAX_CHARS) return KeptTeam(text)
            }
            return null
        }

        private fun workspace(ws: TeamWorkspace, level: Int): Map<String, Any?> =
            linkedMapOf(
                "schemaVersion" to 1,
                "revision" to ws.revision,
                "simulated" to ws.simulated,
                "projects" to ws.projects.map { project(it, level) },
                "servers" to ws.servers.map(TeamJson::server),
                "roles" to ws.roles.map { TeamJson.role(it.copy(instructions = "")) },
                "defaultSettings" to ws.defaultSettings?.let(TeamJson::settings),
            )

        private fun project(p: TeamProject, level: Int): Map<String, Any?> =
            linkedMapOf(
                "id" to p.id,
                "name" to p.name,
                "status" to p.status,
                "revision" to p.revision,
                "settings" to TeamJson.settings(p.settings),
                "repos" to p.repos.map(TeamJson::repo),
                "specDraft" to TeamJson.spec(p.specDraft),
                "phases" to p.phases.map(TeamJson::phase),
                "tasks" to
                    p.tasks.map {
                        TeamJson.task(
                            if (level == 0) it.copy(diff = "", messages = it.messages.takeLast(MAX_MESSAGES))
                            else it.copy(diff = "", messages = emptyList(), findings = emptyList())
                        )
                    },
                "requests" to
                    p.requests.map {
                        linkedMapOf(
                            "id" to it.id, "kind" to it.kind, "title" to it.title, "taskId" to it.taskId,
                            "phaseId" to it.phaseId, "createdAt" to it.createdAt, "answered" to it.answered,
                            "answer" to it.answer,
                        )
                    },
                "mergeQueue" to
                    p.mergeQueue.map {
                        linkedMapOf(
                            "id" to it.id, "taskId" to it.taskId, "repoId" to it.repoId, "status" to it.status,
                            "reason" to it.reason, "checksPassed" to it.checksPassed,
                        )
                    },
                "timeline" to
                    (if (level == 0) p.timeline.takeLast(MAX_TIMELINE) else emptyList()).map {
                        linkedMapOf(
                            "id" to it.id, "kind" to it.kind, "text" to it.text, "actor" to it.actor, "at" to it.at,
                            "taskId" to it.taskId,
                        )
                    },
                "timelineTruncated" to p.timelineTruncated,
                "spendDay" to p.spendDay,
                "planningState" to
                    p.planningState?.let {
                        linkedMapOf("jobId" to it.jobId, "stage" to it.stage, "reason" to it.reason, "updatedAt" to it.updatedAt)
                    },
                "spent" to p.spent,
                "spentToday" to p.spentToday,
                "digestReadAt" to p.digestReadAt,
                "updatedAt" to p.updatedAt,
                "planApproved" to p.planApproved,
                "quickTask" to p.quickTask,
                "usageReported" to p.usageReported,
                "budgetWarning" to p.budgetWarning,
                "simulated" to p.simulated,
            )
    }
}
