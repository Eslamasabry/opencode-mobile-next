package io.github.eslamasabry.oc.engine.diagnostics.job

import io.github.eslamasabry.oc.core.security.Redactor

/** The built-in Linux setup job's state (port of Dart `SetupState`). */
public enum class SetupJobState { Idle, Running, Done, Failed, Interrupted, Cancelled }

/** One component's state in a setup job (port of Dart `ComponentState`). */
public enum class SetupComponentState { Pending, Checking, Running, Done, Failed, Skipped }

public data class SetupComponent(val id: String, val state: SetupComponentState, val error: String? = null)

/** What the report reads from the setup job (v1 and v2 hosts answer the same shape). */
public data class SetupJobSnapshot(
    val state: SetupJobState,
    val components: List<SetupComponent> = emptyList(),
    val jobId: String? = null,
    val current: String? = null,
    val error: String? = null,
    val logTail: String = "",
)

public enum class FailedJobKind { Setup, TeamSetup, TeamWork, TeamRun, DevelopmentService }

/** What the Report a problem page attaches (port of Dart `KitReport` for a job): [log] empty = no log was kept. */
public data class FailedJobAttachment(val title: String, val details: String?, val source: String, val log: String)

/**
 * An immutable, redacted failed-job preview (port of `FailedJobReport`, B-AGENT-108). Every field is redacted before
 * it is bounded; the log is an excerpt without the app's `OCTRACE` timing lines. Creating one saves or sends nothing.
 */
public class FailedJobReport private constructor(
    public val kind: FailedJobKind,
    public val jobId: String,
    public val title: String,
    public val detail: String,
    public val logExcerpt: String,
) {
    public val hasLog: Boolean get() = logExcerpt.isNotEmpty()

    /** Plain technical attachment, shown whole before Copy or Share. */
    public val preview: String
        get() =
            listOfNotNull(
                "OpenCode Mobile failed job",
                "Kind: ${kind.wire}",
                "Job: $jobId",
                "Title: $title",
                detail.takeIf { it.isNotEmpty() }?.let { "Detail: $it" },
                if (hasLog) "Log excerpt:\n$logExcerpt" else "Log excerpt: unavailable",
            ).joinToString("\n")

    /** The Report a problem attachment; [title] is the surface's own (localized) words, blank keeps [this.title]. */
    public fun toAttachment(title: String? = null): FailedJobAttachment =
        FailedJobAttachment(
            title = if (title.isNullOrBlank()) this.title else title,
            details = detail.ifEmpty { null },
            source = "failed job · ${kind.wire} · $jobId",
            log = logExcerpt,
        )

    public companion object {
        public const val MAX_LOG_LINES: Int = 120
        public const val MAX_LOG_CHARACTERS: Int = 16_384

        /**
         * A failed setup job, or null when it did not fail. With [componentId], only that component's failure: a
         * component that did not fail, or is missing, gets no report (never a different component's invented log).
         */
        public fun setup(redactor: Redactor, progress: SetupJobSnapshot, componentId: String? = null): FailedJobReport? {
            if (progress.state != SetupJobState.Failed) return null
            val component =
                if (componentId == null) {
                    null
                } else {
                    progress.components.lastOrNull { it.id == componentId }
                        ?.takeIf { it.state == SetupComponentState.Failed } ?: return null
                }
            return FailedJobReport(
                kind = FailedJobKind.Setup,
                jobId = bounded(redactor, progress.jobId ?: "unknown", 256),
                title = bounded(redactor, componentId ?: progress.current ?: "setup", 256),
                detail = bounded(redactor, component?.error ?: progress.error ?: "", 2048),
                logExcerpt = redactor.excerpt(progress.logTail, MAX_LOG_LINES, MAX_LOG_CHARACTERS),
            )
        }

        private val ANSI_OSC = Regex("""\u001B][^\u0007]*(?:\u0007|\u001B\\)""")
        private val ANSI_CSI = Regex("""\u001B\[[0-?]*[ -/]*[@-~]""")

        private fun bounded(redactor: Redactor, value: String, limit: Int): String {
            val plain = value.replace(ANSI_OSC, "").replace(ANSI_CSI, "").replace("\r\n", "\n").replace("\r", "\n")
            val safe = redactor.opaqueTokens(redactor.redact(plain))
            return if (safe.length <= limit) safe else safe.substring(0, limit)
        }

        private val FailedJobKind.wire: String get() = name.replaceFirstChar { it.lowercaseChar() }
    }
}
