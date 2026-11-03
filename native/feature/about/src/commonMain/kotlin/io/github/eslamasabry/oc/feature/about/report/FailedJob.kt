package io.github.eslamasabry.oc.feature.about.report

public enum class FailedJobKind { Setup, TeamSetup, TeamWork, TeamRun, DevelopmentService }

/** A team work item as the failed-job snapshot needs it. */
public data class JobWork(
    val id: String,
    val title: String,
    val failed: Boolean,
    val sessionId: String? = null,
    val runId: String? = null,
)

/** A team gate; only a Run failed gate offers Report. */
public data class JobGate(
    val id: String,
    val title: String,
    val runFailed: Boolean,
    val runId: String? = null,
    val workId: String? = null,
    val prompt: String? = null,
)

/** How a managed shell ended (Flutter `ManagedShellStatus`). */
public enum class ShellStatus { Running, Exited, Killed, Timeout, Unknown }

/** A dev service's own record of the run it started. */
public data class ServiceRun(val shellId: String, val ownerToken: String, val startedAtMillis: Long, val stopped: Boolean)

public data class DevService(val name: String, val command: String, val directory: String, val run: ServiceRun?)

public data class ManagedShell(
    val id: String,
    val command: String,
    val directory: String,
    val ownerToken: String,
    val startedAtMillis: Long,
    val status: ShellStatus,
    val exitCode: Int? = null,
)

/**
 * An immutable, scrubbed snapshot of a failed job (Flutter `FailedJobReport`, P8.4): creating one neither saves nor
 * uploads anything. Every field is cleaned before it is bounded; the log is its last [MAX_LOG_LINES] lines without
 * timing lines. Ownership tokens and commands never enter it.
 */
public class FailedJob private constructor(
    public val kind: FailedJobKind,
    jobId: String,
    title: String,
    detail: String,
    log: String,
    scrub: ReportScrub,
) {
    public val jobId: String = bounded(scrub, jobId, 256)
    public val title: String = bounded(scrub, title, 256)
    public val detail: String = bounded(scrub, detail, 2048)
    public val logExcerpt: String = excerpt(scrub, log)
    public val hasLog: Boolean get() = logExcerpt.isNotEmpty()

    /** This snapshot as Report a problem's attachment; [title] is the failure as the offering surface words it. */
    public fun toAttachment(title: String? = null): ReportAttachment =
        ReportAttachment(
            title = if (title.isNullOrBlank()) this.title else title,
            details = detail.ifEmpty { null },
            source = "failed job · ${kind.name.replaceFirstChar { it.lowercase() }} · $jobId",
            log = logExcerpt,
        )

    override fun toString(): String = "FailedJob(kind=$kind, hasLog=$hasLog)"

    public companion object {
        public const val MAX_LOG_LINES: Int = 120
        public const val MAX_LOG_CHARACTERS: Int = 16_384

        /** A failed work item; its log only when [sessionId] is the session the log came from (never another agent's). */
        public fun teamWork(work: JobWork, sessionId: String? = null, logTail: String = "", scrub: ReportScrub = ReportScrub()): FailedJob? {
            if (!work.failed) return null
            return FailedJob(FailedJobKind.TeamWork, work.id, work.title, "", workLog(work, sessionId, logTail), scrub)
        }

        /** The Run failed gate always offers Report; it attaches only a work item explicitly linked to it. */
        public fun teamGate(
            gate: JobGate,
            work: JobWork? = null,
            sessionId: String? = null,
            logTail: String = "",
            scrub: ReportScrub = ReportScrub(),
        ): FailedJob? {
            if (!gate.runFailed) return null
            val linked =
                work != null &&
                    if (gate.workId != null) gate.workId == work.id && (gate.runId == null || gate.runId == work.runId)
                    else gate.runId != null && gate.runId == work.runId
            val log = if (linked && work != null) workLog(work, sessionId, logTail) else ""
            return FailedJob(FailedJobKind.TeamRun, gate.runId ?: gate.id, gate.title, gate.prompt.orEmpty(), log, scrub)
        }

        /** A failed AI Team start: the setup's own output is its log. */
        public fun teamSetup(stage: String?, output: String, scrub: ReportScrub = ReportScrub()): FailedJob =
            FailedJob(FailedJobKind.TeamSetup, stage ?: "unknown", "AI Team setup", "", output, scrub)

        /**
         * A dev service's run that this app started and that ended in failure (timeout, killed, non-zero exit). A run
         * that is still going, was stopped on purpose, or belongs to another owner or shell is not a failed job.
         */
        public fun developmentService(service: DevService, shell: ManagedShell, logTail: String = "", scrub: ReportScrub = ReportScrub()): FailedJob? {
            val run = service.run ?: return null
            if (run.stopped || run.ownerToken != shell.ownerToken || run.shellId != shell.id ||
                run.startedAtMillis != shell.startedAtMillis || service.command != shell.command || service.directory != shell.directory
            ) {
                return null
            }
            val failed =
                shell.status == ShellStatus.Timeout || shell.status == ShellStatus.Killed ||
                    (shell.status == ShellStatus.Exited && shell.exitCode != null && shell.exitCode != 0)
            if (!failed) return null
            val detail = shell.status.name.lowercase() + (shell.exitCode?.let { " (exit $it)" } ?: "")
            return FailedJob(FailedJobKind.DevelopmentService, shell.id, service.name, detail, logTail, scrub)
        }

        private fun workLog(work: JobWork, sessionId: String?, log: String): String =
            if (!sessionId.isNullOrEmpty() && work.sessionId == sessionId) log else ""

        private fun bounded(scrub: ReportScrub, value: String, limit: Int): String =
            scrub.clean(value).let { if (it.length <= limit) it else it.take(limit) }

        /** Timing lines (OCTRACE) stay in the device log: in a failed job's tail they push the real error out. */
        private fun excerpt(scrub: ReportScrub, log: String): String {
            val people = log.lineSequence().filterNot { "OCTRACE" in it }.joinToString("\n")
            val lines = scrub.clean(people).trimEnd().split('\n')
            val tail = lines.drop((lines.size - MAX_LOG_LINES).coerceAtLeast(0)).joinToString("\n")
            return if (tail.length <= MAX_LOG_CHARACTERS) tail else tail.takeLast(MAX_LOG_CHARACTERS)
        }
    }
}
