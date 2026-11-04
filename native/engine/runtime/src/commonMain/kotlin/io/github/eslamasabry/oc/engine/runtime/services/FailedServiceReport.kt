package io.github.eslamasabry.oc.engine.runtime.services

/*
 * Port of lib/diagnostics/failed_job_report.dart (`FailedJobReport.developmentService`, its cleaning and bounding) and
 * lib/domain/ansi_text.dart (`withoutTerminalCodes`).
 */

/**
 * Terminal output as plain text: colour and cursor codes removed, line endings `\n`, other control
 * characters dropped.
 */
public object TerminalText {
    private val osc = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)?")
    private val csi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val other = Regex("\u001B[@-Z\\\\-_()#][0-9A-Za-z]?")
    private val control = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001A\u001C-\u001F\u007F]")

    public fun withoutTerminalCodes(input: String): String {
        if (input.isEmpty()) return input
        return input
            .replace(osc, "")
            .replace(csi, "")
            .replace(other, "")
            .replace("\u001B", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(control, "")
    }
}

/**
 * An immutable, redacted preview of one failed, owned service run. Creating one neither saves nor
 * uploads it. [detail] is the plain technical attachment line (`exited (exit 2)`), not localized
 * copy: the Report page shows it inside the report text. [logExcerpt] is redacted with the injected
 * redactor before it is bounded.
 */
public class FailedServiceReport
private constructor(
    public val jobId: String,
    public val title: String,
    public val status: ManagedShellStatus,
    public val exitCode: Int?,
    public val logExcerpt: String,
) {
    public val hasLog: Boolean
        get() = logExcerpt.isNotEmpty()

    public val detail: String
        get() = statusName(status) + (exitCode?.let { " (exit $it)" } ?: "")

    public fun attachment(): String = buildString {
        appendLine("Failed development service: $title")
        appendLine("Job: $jobId")
        appendLine("Outcome: $detail")
        append(if (hasLog) "Log excerpt:\n$logExcerpt" else "Log excerpt: unavailable")
    }

    public companion object {
        public const val MAX_LOG_LINES: Int = 120
        public const val MAX_LOG_CHARACTERS: Int = 16384

        /**
         * Requires an owned, terminal failure. Unknown exits and deliberate stops are not failures;
         * a receipt that does not match the shell exactly (owner, id, start, command, directory)
         * reports nothing.
         */
        public fun of(
            service: DevelopmentService,
            shell: ManagedShell,
            logTail: String,
            redact: (String) -> String,
        ): FailedServiceReport? {
            val run = service.run ?: return null
            if (
                run.stopped ||
                    run.ownerToken != shell.ownerToken ||
                    run.shellId != shell.id ||
                    run.startedAt != shell.startedAt ||
                    service.command != shell.command ||
                    service.directory != shell.directory
            ) {
                return null
            }
            val failed =
                shell.status == ManagedShellStatus.Timeout ||
                    shell.status == ManagedShellStatus.Killed ||
                    (shell.status == ManagedShellStatus.Exited &&
                        shell.exitCode != null &&
                        shell.exitCode != 0)
            if (!failed) return null
            return FailedServiceReport(
                jobId = bounded(shell.id, 256, redact),
                title = bounded(service.name, 256, redact),
                status = shell.status,
                exitCode = shell.exitCode,
                logExcerpt = excerpt(logTail, redact),
            )
        }

        private fun statusName(status: ManagedShellStatus): String =
            when (status) {
                ManagedShellStatus.Running -> "running"
                ManagedShellStatus.Exited -> "exited"
                ManagedShellStatus.Timeout -> "timeout"
                ManagedShellStatus.Killed -> "killed"
                ManagedShellStatus.Unknown -> "unknown"
            }

        private val oscCodes = Regex("\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)")
        private val csiCodes = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

        private fun clean(value: String, redact: (String) -> String): String =
            redact(
                value
                    .replace(oscCodes, "")
                    .replace(csiCodes, "")
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
            )

        private fun bounded(value: String, limit: Int, redact: (String) -> String): String {
            val safe = clean(value, redact)
            return if (safe.length <= limit) safe else safe.substring(0, limit)
        }

        /**
         * Timing lines (`OCTRACE`) stay in the device log: in an excerpt they would push the real
         * error out.
         */
        private fun excerpt(log: String, redact: (String) -> String): String {
            val safe = clean(log, redact)
            val forPeople =
                if (!safe.contains("OCTRACE")) safe
                else safe.split('\n').filterNot { it.contains("OCTRACE") }.joinToString("\n")
            val lines = forPeople.trimEnd().split('\n')
            val tail =
                lines
                    .drop(if (lines.size > MAX_LOG_LINES) lines.size - MAX_LOG_LINES else 0)
                    .joinToString("\n")
            return if (tail.length <= MAX_LOG_CHARACTERS) tail
            else tail.substring(tail.length - MAX_LOG_CHARACTERS)
        }
    }
}
