package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.engine.runtime.services.TerminalText

/**
 * A failed setup's immutable local attachment; no capture for running, successful or interrupted
 * jobs.
 */
public data class FailedSetupReport
private constructor(
    val jobId: String,
    val component: String,
    val reason: String,
    val logExcerpt: String,
) {
    /** Fixed technical labels; the surrounding Report a problem screen supplies localized UI. */
    public fun attachment(): String = buildString {
        appendLine("Failed setup job: $jobId")
        appendLine("Component: $component")
        if (reason.isNotEmpty()) appendLine("Reason: $reason")
        append(
            if (logExcerpt.isEmpty()) "Log excerpt: unavailable" else "Log excerpt:\n$logExcerpt"
        )
    }

    public companion object {
        public const val MAX_LOG_LINES: Int = 120
        public const val MAX_LOG_CHARACTERS: Int = 16_384

        /**
         * Register loaded credentials before passing the app redactor. Mask complete input before
         * bounding.
         */
        public fun from(
            job: SetupJobRecord,
            redact: (String) -> String,
            componentId: String? = null,
        ): FailedSetupReport? {
            if (job.state != "failed") return null
            val selected =
                if (componentId == null) {
                    job.current?.let(job::component)?.takeIf { it.state == "failed" }
                        ?: job.components.firstOrNull { it.state == "failed" }
                } else job.component(componentId)?.takeIf { it.state == "failed" } ?: return null
            fun clean(input: String): String = redact(TerminalText.withoutTerminalCodes(input))
            val fullLog = clean(job.logTail)
            val tail =
                fullLog
                    .lineSequence()
                    .filterNot { it.contains("OCTRACE") }
                    .toList()
                    .takeLast(MAX_LOG_LINES)
                    .joinToString("\n")
                    .trimEnd()
            return FailedSetupReport(
                jobId = clean(job.jobId).take(256),
                component = clean(selected?.id ?: job.current ?: "setup").take(256),
                reason = clean(selected?.error ?: job.error.orEmpty()).take(2048),
                logExcerpt = tail.takeLast(MAX_LOG_CHARACTERS),
            )
        }
    }
}
