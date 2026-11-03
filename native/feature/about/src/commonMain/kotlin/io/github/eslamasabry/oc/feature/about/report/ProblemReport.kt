package io.github.eslamasabry.oc.feature.about.report

/**
 * What a surface hands Report a problem (Flutter `KitReport`). [log] is a failed job's excerpt: empty when the job
 * kept no log (the report says so), null when the report is not about a job. Never an exception's text.
 */
public data class ReportAttachment(
    val title: String,
    val details: String? = null,
    val source: String? = null,
    val errorType: String? = null,
    val log: String? = null,
) {
    /** The report page leads with "the job's log is attached" ([hasLog]) or "this job kept no log". */
    val isJob: Boolean get() = log != null
    val hasLog: Boolean get() = !log.isNullOrEmpty()
}

public enum class ReportEventKind { Error, AndroidExit, Thermal, Timing }

/** One thing the app noticed (Flutter `ProblemReportEvent`); [at] is already a UTC stamp. */
public data class ReportEvent(
    val kind: ReportEventKind,
    val at: String,
    val source: String,
    val message: String,
    val stack: String = "",
    val occurrences: Int = 1,
) {
    val isError: Boolean get() = kind == ReportEventKind.Error || kind == ReportEventKind.AndroidExit
}

/**
 * Exactly what a report sends (Flutter `ProblemReport`): [text] is what the preview shows and Copy and Share send.
 * Every part is scrubbed when built. A failed job's log leads [diagnostics], so leaving recent diagnostics out never
 * drops it (B-NAV-015).
 */
public data class ProblemReport(
    val title: String,
    val whatHappened: String,
    val version: String,
    val diagnostics: String,
) {
    val text: String
        get() =
            listOfNotNull(
                title.takeUnless { whatHappened.startsWith(it) },
                whatHappened,
                diagnostics.takeIf { it.isNotEmpty() }?.let { "Diagnostics\n$it" },
            ).joinToString("\n\n")

    public companion object {
        /** Fixed English: the report is read by maintainers. */
        public const val JOB_LOG_HEADER: String = "Failed job log (last lines)"
        public const val JOB_NO_LOG: String = "Failed job log: none was kept"

        public fun build(
            description: String,
            version: String,
            platform: String,
            error: ReportAttachment? = null,
            connection: String? = null,
            fallbackTitle: String? = null,
            events: List<ReportEvent> = emptyList(),
            scrub: ReportScrub = ReportScrub(),
            maxErrors: Int = 10,
            maxTimings: Int = 10,
            maxStackLines: Int = 8,
        ): ProblemReport {
            val said = scrub.scrub(description.trim())
            val errorTitle = error?.let { scrub.scrub(it.title) }.orEmpty()
            val firstLine = said.lineSequence().first().trim()
            val title =
                clip(
                    when {
                        errorTitle.isNotEmpty() -> errorTitle
                        firstLine.isNotEmpty() -> firstLine
                        !fallbackTitle.isNullOrBlank() -> scrub.scrub(fallbackTitle.trim())
                        else -> "Problem report"
                    },
                    80,
                )
            val what = StringBuilder(if (said.isEmpty()) "(not described)" else clip(said, 4000))
            if (error != null) {
                val facts =
                    listOfNotNull(
                        error.errorType?.takeIf { it.isNotEmpty() }?.let { "Type: $it" },
                        error.source?.takeIf { it.isNotEmpty() }?.let { "Where: ${scrub.scrub(it)}" },
                        error.details?.trim()?.takeIf { it.isNotEmpty() }?.let { clip(scrub.scrub(it), 2000) },
                    )
                if (facts.isNotEmpty()) what.append("\n\nError\n").append(facts.joinToString("\n"))
            }
            what.append("\n\nEnvironment\n").append("App: $version\n").append("Platform: $platform")
            connection?.trim()?.takeIf { it.isNotEmpty() }?.let { what.append("\nConnection: ${scrub.scrub(it)}") }

            val lines = mutableListOf<String>()
            error?.log?.let { log ->
                val excerpt = scrub.scrub(log.trimEnd())
                lines += if (excerpt.isEmpty()) JOB_NO_LOG else JOB_LOG_HEADER
                if (excerpt.isNotEmpty()) lines += excerpt
                lines += ""
            }
            // A failed job's report keeps its own lines and the errors around it; timings would bury its reason.
            val withTimings = error?.log == null
            var errors = 0
            var timings = 0
            for (event in events) {
                if (event.kind == ReportEventKind.Timing) {
                    if (!withTimings || timings++ >= maxTimings) continue
                } else if (errors++ >= maxErrors) {
                    continue
                }
                val times = if (event.occurrences > 1) " ×${event.occurrences}" else ""
                lines += "[${event.at}] ${event.kind.name.replaceFirstChar { it.lowercase() }} · ${scrub.scrub(event.source)}$times"
                lines += clip(scrub.scrub(event.message.trim()), 1000)
                val stack = event.stack.trim()
                if (stack.isNotEmpty() && event.isError) {
                    val frames = stack.split('\n')
                    frames.take(maxStackLines).forEach { lines += "    ${scrub.scrub(it.trim())}" }
                    if (frames.size > maxStackLines) lines += "    … ${frames.size - maxStackLines} more lines"
                }
                lines += ""
            }
            return ProblemReport(title, what.toString(), version, lines.joinToString("\n").trim())
        }

        private fun clip(text: String, limit: Int): String {
            val points = text.codePointCount()
            return if (points <= limit) text else text.takeCodePoints(limit) + "…"
        }

        private fun String.codePointCount(): Int {
            var n = 0
            var i = 0
            while (i < length) {
                i += if (this[i].isHighSurrogate() && i + 1 < length) 2 else 1
                n++
            }
            return n
        }

        private fun String.takeCodePoints(limit: Int): String {
            var n = 0
            var i = 0
            while (i < length && n < limit) {
                i += if (this[i].isHighSurrogate() && i + 1 < length) 2 else 1
                n++
            }
            return substring(0, i)
        }
    }
}
