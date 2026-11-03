package io.github.eslamasabry.oc.engine.project.reports

import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.core.security.Redactor

public enum class FailedJobKind { Setup, TeamSetup, TeamWork, TeamRun, DevelopmentService }

/** One component of a setup job as the report needs it. */
public data class SetupComponentRun(val id: String, val failed: Boolean, val error: String? = null)

/** A setup job's progress as the report needs it. */
public data class SetupJobRun(
    val failed: Boolean,
    val components: List<SetupComponentRun>,
    val jobId: String?,
    val current: String?,
    val error: String? = null,
    val logTail: String = "",
)

/** The built-in team's setup job: [errorLog] is the failure's own log text until the person clears it. */
public data class TeamSetupJobRun(val running: Boolean, val stage: String?, val errorLog: String?)

public data class TeamWorkItem(val id: String, val title: String, val failed: Boolean, val runId: String? = null, val sessionId: String? = null)

public enum class TeamGateKind { RunFailed, Confirmation, Other }

/** An orchestration gate. [raw] is the provider's payload: never copied into a report. */
public data class TeamGate(
    val id: String,
    val kind: TeamGateKind,
    val title: String,
    val runId: String? = null,
    val workId: String? = null,
    val prompt: String? = null,
    val raw: Map<String, String> = emptyMap(),
)

/** A saved development service and the run the app started for it. */
public data class DevServiceRun(val ownerToken: String, val shellId: String, val startedAtMs: Long, val stopped: Boolean)

public data class DevService(val id: String, val name: String, val command: String, val directory: String, val run: DevServiceRun?)

/**
 * An immutable, redacted preview of a failed job (port of lib/diagnostics/failed_job_report.dart). Creating one neither
 * saves nor uploads it. Every field is redacted before bounding, including the whole multiline log; keep the same
 * snapshot for the preview and the person's explicit Copy/Share.
 */
public class FailedJobReport private constructor(
    public val kind: FailedJobKind,
    public val jobId: String,
    public val title: String,
    public val detail: String,
    public val logExcerpt: String,
) {
    public val hasLog: Boolean get() = logExcerpt.isNotEmpty()

    /** Plain technical attachment; the UI shows this exact text before offering Copy or Share. */
    public val preview: String
        get() =
            buildList {
                add("OpenCode Mobile failed job")
                add("Kind: ${kind.wire}")
                add("Job: $jobId")
                add("Title: $title")
                if (detail.isNotEmpty()) add("Detail: $detail")
                add(if (hasLog) "Log excerpt:\n$logExcerpt" else "Log excerpt: unavailable")
            }.joinToString("\n")

    /** Never prints the log. */
    override fun toString(): String = "FailedJobReport($kind, hasLog=$hasLog)"

    /** Builds reports with one [Redactor] (the app's, with every loaded credential registered). */
    public class Factory(private val redactor: Redactor) {
        private fun make(kind: FailedJobKind, jobId: String, title: String, detail: String = "", log: String = ""): FailedJobReport =
            FailedJobReport(kind, bounded(jobId, 256), bounded(title, 256), bounded(detail, 2048), redactor.excerpt(log, MAX_LOG_LINES, MAX_LOG_CHARACTERS))

        private fun bounded(value: String, limit: Int): String {
            val plain = value.replace(ANSI_OSC, "").replace(ANSI_CSI, "").replace("\r\n", "\n").replace("\r", "\n")
            val safe = redactor.opaqueTokens(redactor.redact(plain))
            return if (safe.length <= limit) safe else safe.substring(0, limit)
        }

        /** A failed setup row gets the job's log, never a different component's invented log. */
        public fun setup(progress: SetupJobRun, componentId: String? = null): FailedJobReport? {
            if (!progress.failed) return null
            var component: SetupComponentRun? = null
            if (componentId != null) {
                component = progress.components.lastOrNull { it.id == componentId }
                if (component == null || !component.failed) return null
            }
            return make(
                FailedJobKind.Setup,
                progress.jobId ?: "unknown",
                componentId ?: progress.current ?: "setup",
                component?.error ?: progress.error ?: "",
                progress.logTail,
            )
        }

        /** Built-in team startup failures carry their script log in the failure; no raw error object is kept. */
        public fun teamSetup(job: TeamSetupJobRun): FailedJobReport? {
            if (job.running || job.errorLog == null) return null
            return make(FailedJobKind.TeamSetup, job.stage ?: "unknown", "AI Team setup", log = job.errorLog)
        }

        /** [sessionId] is the identity attached to [logTail]; a mismatched or missing session attaches no log. */
        public fun teamWork(work: TeamWorkItem, sessionId: String? = null, logTail: String = ""): FailedJobReport? {
            if (!work.failed) return null
            return make(FailedJobKind.TeamWork, work.id, work.title, log = workLog(work, sessionId, logTail))
        }

        /** The Run failed gate always offers Report; only a work item explicitly linked to it attaches its log. */
        public fun teamGate(gate: TeamGate, work: TeamWorkItem? = null, sessionId: String? = null, logTail: String = ""): FailedJobReport? {
            if (gate.kind != TeamGateKind.RunFailed) return null
            val linked =
                work != null &&
                    if (gate.workId != null) gate.workId == work.id && (gate.runId == null || gate.runId == work.runId)
                    else gate.runId != null && gate.runId == work.runId
            return make(
                FailedJobKind.TeamRun,
                gate.runId ?: gate.id,
                gate.title,
                gate.prompt ?: "",
                if (linked) workLog(work!!, sessionId, logTail) else "",
            )
        }

        /** Requires the exact owned run and an observed terminal failure. Owner tokens and commands are never exported. */
        public fun developmentService(service: DevService, shell: ManagedShell, logTail: String = ""): FailedJobReport? {
            val run = service.run ?: return null
            if (run.stopped || run.ownerToken != shell.ownerToken || run.shellId != shell.id || run.startedAtMs != shell.startedAtMs ||
                service.command != shell.command || service.directory != shell.directory
            ) {
                return null
            }
            val failed =
                shell.status == ManagedShellStatus.Timeout || shell.status == ManagedShellStatus.Killed ||
                    (shell.status == ManagedShellStatus.Exited && shell.exitCode != null && shell.exitCode != 0)
            if (!failed) return null
            val detail = shell.status.name.lowercase() + (shell.exitCode?.let { " (exit $it)" } ?: "")
            return make(FailedJobKind.DevelopmentService, shell.id, service.name, detail, logTail)
        }

        private fun workLog(work: TeamWorkItem, sessionId: String?, log: String): String =
            if (!sessionId.isNullOrEmpty() && work.sessionId == sessionId) log else ""
    }

    public companion object {
        public const val MAX_LOG_LINES: Int = 120
        public const val MAX_LOG_CHARACTERS: Int = 16_384

        private val ANSI_OSC = Regex("\u001B][^\u0007]*(?:\u0007|\u001B\\\\)")
        private val ANSI_CSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    }
}

private val FailedJobKind.wire: String
    get() =
        when (this) {
            FailedJobKind.Setup -> "setup"
            FailedJobKind.TeamSetup -> "teamSetup"
            FailedJobKind.TeamWork -> "teamWork"
            FailedJobKind.TeamRun -> "teamRun"
            FailedJobKind.DevelopmentService -> "developmentService"
        }
