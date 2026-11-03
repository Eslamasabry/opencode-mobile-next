package io.github.eslamasabry.oc.feature.about.report

/**
 * Where Report a problem was opened from (Flutter P8.4 entry points). Every job surface carries its job, so the page
 * opens with that job's log attached: Settings › Report a problem has none; a Run failed gate, a failed AI Team start
 * and a failed dev service each carry their own (B-NAV-015).
 */
public sealed interface ReportEntry {
    public data object Settings : ReportEntry

    public data class RunFailedGate(val gate: JobGate, val work: JobWork? = null, val sessionId: String? = null, val logTail: String = "") :
        ReportEntry

    public data class FailedWork(val work: JobWork, val sessionId: String? = null, val logTail: String = "") : ReportEntry

    public data class FailedTeamStart(val stage: String?, val output: String) : ReportEntry

    public data class FailedDevService(val service: DevService, val shell: ManagedShell, val logTail: String = "") : ReportEntry {
        override fun toString(): String = "FailedDevService(service=${service.name}, status=${shell.status})" // no token
    }
}

/** What the report page says above the description. */
public enum class ReportLead {
    /** No job attached. */
    None,

    /** "This job's log is attached; it is under Details, exactly as it will be sent." */
    JobLogAttached,

    /** "This job kept no log." */
    JobKeptNoLog,
}

/** The attachment the entry carries (null: no failed job here). [title] words the failure as the surface did. */
public fun ReportEntry.attachment(title: String? = null, scrub: ReportScrub = ReportScrub()): ReportAttachment? =
    when (this) {
        ReportEntry.Settings -> null
        is ReportEntry.RunFailedGate -> FailedJob.teamGate(gate, work, sessionId, logTail, scrub)?.toAttachment(title)
        is ReportEntry.FailedWork -> FailedJob.teamWork(work, sessionId, logTail, scrub)?.toAttachment(title)
        is ReportEntry.FailedTeamStart -> FailedJob.teamSetup(stage, output, scrub).toAttachment(title)
        is ReportEntry.FailedDevService -> FailedJob.developmentService(service, shell, logTail, scrub)?.toAttachment(title)
    }

public fun ReportAttachment?.lead(): ReportLead =
    when {
        this == null || !isJob -> ReportLead.None
        hasLog -> ReportLead.JobLogAttached
        else -> ReportLead.JobKeptNoLog
    }
