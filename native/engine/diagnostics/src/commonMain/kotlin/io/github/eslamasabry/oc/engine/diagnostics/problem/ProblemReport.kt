package io.github.eslamasabry.oc.engine.diagnostics.problem

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.diagnostics.ErrorEntry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What one report event is (port of Dart `ProblemEventKind`); [wire] is the word the report prints.
 */
public enum class ProblemEventKind(public val wire: String) {
    Error("error"),
    Timing("timing"),
    AndroidExit("androidExit"),
    Thermal("thermal"),
}

/**
 * One thing the app noticed, for the page's list and the report (port of Dart
 * `ProblemReportEvent`).
 */
public data class ProblemReportEvent(
    val kind: ProblemEventKind,
    val time: Instant,
    val source: String,
    val message: String,
    val stack: String = "",
    val occurrences: Int = 1,
    /** The in-memory entry's id, when the event came from there. */
    val id: Long? = null,
) {
    val isError: Boolean
        get() = kind == ProblemEventKind.Error || kind == ProblemEventKind.AndroidExit
}

/**
 * The error a surface attached to its Report button (port of Dart `KitReport`). [log] non-null = a
 * failed job.
 */
public data class AttachedError(
    val title: String,
    val details: String? = null,
    val source: String? = null,
    val errorType: String? = null,
    val log: String? = null,
)

/** Where the platform label comes from (port of `bugReportPlatformLabel`'s cases). */
public enum class ReportPlatform(public val label: String) {
    Android("Android"),
    Windows("Windows desktop (experimental — contributor-tested)"),
    Linux("Linux desktop (alpha)"),
    MacOs("macOS (untested)"),
    Ios("iOS (untested)"),
    Web("Web browser (preview)"),
}

/** Where the diagnostics went when the GitHub link could not carry them. */
public enum class ProblemReportClipboard {
    /** The link carries the whole report; nothing is copied. */
    None,

    /** The link carries everything but the diagnostics, which are copied. */
    Diagnostics,

    /** Even the description was too long: the whole report is copied. */
    Whole,
}

/** The prefilled GitHub form ([uri], app-authored) and what, if anything, is copied first. */
public data class ProblemReportLink(
    val uri: String,
    val clipboard: ProblemReportClipboard,
    val clipboardText: String?,
)

/**
 * Exactly what a report sends (port of `ProblemReport`, B-SEC-126..129): [text] is what the preview
 * shows and what Copy and Share send; the GitHub form gets the same parts in its fields. Every part
 * is scrubbed by [ProblemReports.scrub].
 */
public class ProblemReport
private constructor(
    public val title: String,
    public val whatHappened: String,
    public val version: String,
    public val diagnostics: String,
) {
    /** The whole report; a title taken from the description's first line is not written twice. */
    public val text: String
        get() =
            listOfNotNull(
                    title.takeUnless { whatHappened.startsWith(it) },
                    whatHappened,
                    diagnostics.takeIf { it.isNotEmpty() }?.let { "Diagnostics\n$it" },
                )
                .joinToString("\n\n")

    private fun uri(what: String, logs: String): String {
        val params = buildList {
            add("template" to "bug_report.yml")
            add("title" to title)
            add("app-version" to version)
            add("what-happened" to what)
            if (logs.isNotEmpty()) add("logs" to logs)
        }
        return "${ProblemReports.REPO_URL}/issues/new?" +
            params.joinToString("&") { (k, v) ->
                "${ProblemReports.formEncode(k)}=${ProblemReports.formEncode(v)}"
            }
    }

    /**
     * The prefilled form, as long as the external-link policy accepts: everything when it fits;
     * otherwise the diagnostics (or, for a very long description, the whole report) go to the
     * clipboard and the field says to paste.
     */
    public fun link(maxLength: Int = ProblemReports.MAX_LINK_LENGTH): ProblemReportLink {
        val full = uri(whatHappened, diagnostics)
        if (full.length <= maxLength)
            return ProblemReportLink(full, ProblemReportClipboard.None, null)
        if (diagnostics.isNotEmpty()) {
            val noLogs = uri(whatHappened, PASTE_DIAGNOSTICS)
            if (noLogs.length <= maxLength)
                return ProblemReportLink(noLogs, ProblemReportClipboard.Diagnostics, diagnostics)
        }
        return ProblemReportLink(uri(PASTE_WHOLE, ""), ProblemReportClipboard.Whole, text)
    }

    override fun toString(): String =
        "ProblemReport(title=${title.length} chars, diagnostics=${diagnostics.length} chars)"

    public companion object {
        public const val PASTE_DIAGNOSTICS: String =
            "The diagnostics were copied to the clipboard: paste them here."
        public const val PASTE_WHOLE: String =
            "The whole report was copied to the clipboard: paste it here."

        /**
         * Builds the report. [events] are newest first; errors keep their stack's first lines,
         * timings only the newest few. [connection] is the connection in words with no address or
         * name; [fallbackTitle] titles a report with no attached error and no description (the
         * newest error kept).
         */
        public fun build(
            redactor: Redactor,
            description: String,
            version: String,
            platform: String,
            error: AttachedError? = null,
            connection: String? = null,
            fallbackTitle: String? = null,
            events: List<ProblemReportEvent> = emptyList(),
            maxErrors: Int = 10,
            maxTimings: Int = 10,
            maxStackLines: Int = 8,
        ): ProblemReport {
            fun scrub(text: String) = ProblemReports.scrub(redactor, text)
            val said = scrub(description.trim())
            val errorTitle = error?.let { scrub(it.title) }.orEmpty()
            val firstLine = said.split('\n').first().trim()
            val title =
                clip(
                    when {
                        errorTitle.isNotEmpty() -> errorTitle
                        firstLine.isNotEmpty() -> firstLine
                        !fallbackTitle.isNullOrBlank() -> scrub(fallbackTitle.trim())
                        else -> "Problem report"
                    },
                    80,
                )
            val what = StringBuilder(if (said.isEmpty()) "(not described)" else clip(said, 4000))
            if (error != null) {
                val facts =
                    listOfNotNull(
                        error.errorType?.takeIf { it.isNotEmpty() }?.let { "Type: ${scrub(it)}" },
                        error.source?.takeIf { it.isNotEmpty() }?.let { "Where: ${scrub(it)}" },
                        error.details
                            ?.takeIf { it.isNotBlank() }
                            ?.let { clip(scrub(it.trim()), 2000) },
                    )
                if (facts.isNotEmpty()) what.append("\n\nError\n").append(facts.joinToString("\n"))
            }
            val safeVersion = scrub(version).ifBlank { ProblemReports.UNKNOWN_VERSION }
            what
                .append("\n\nEnvironment\n")
                .append("App: $safeVersion\n")
                .append("Platform: ${scrub(platform)}")
            if (!connection.isNullOrBlank())
                what.append("\nConnection: ${scrub(connection.trim())}")

            val lines = mutableListOf<String>()
            // A failed job's log leads the diagnostics: it is part of the attached failure.
            error?.log?.let { log ->
                val excerpt = scrub(log.trimEnd())
                lines +=
                    if (excerpt.isEmpty()) "Failed job log: none was kept"
                    else "Failed job log (last lines)"
                if (excerpt.isNotEmpty()) lines += excerpt
                lines += ""
            }
            var errors = 0
            var timings = 0
            // A failed job's report leaves timings out: they would bury its reason under status
            // polls.
            val withTimings = error?.log == null
            for (event in events) {
                if (event.kind == ProblemEventKind.Timing) {
                    if (!withTimings || timings++ >= maxTimings) continue
                } else if (errors++ >= maxErrors) {
                    continue
                }
                val times = if (event.occurrences > 1) " ×${event.occurrences}" else ""
                lines += "[${utc(event.time)}] ${event.kind.wire} · ${scrub(event.source)}$times"
                lines += clip(scrub(event.message.trim()), 1000)
                val stack = event.stack.trim()
                if (stack.isNotEmpty() && event.isError) {
                    val frames = stack.split('\n')
                    for (frame in frames.take(maxStackLines)) lines += "    ${scrub(frame.trim())}"
                    if (frames.size > maxStackLines)
                        lines += "    … ${frames.size - maxStackLines} more lines"
                }
                lines += ""
            }
            return ProblemReport(
                title,
                what.toString(),
                safeVersion,
                lines.joinToString("\n").trim(),
            )
        }

        /** At most [limit] code points, then an ellipsis. */
        internal fun clip(text: String, limit: Int): String {
            var points = 0
            var i = 0
            while (i < text.length) {
                if (points == limit) return text.substring(0, i) + "…"
                i +=
                    if (
                        text[i].isHighSurrogate() &&
                            i + 1 < text.length &&
                            text[i + 1].isLowSurrogate()
                    )
                        2
                    else 1
                points++
            }
            return text
        }

        /** "2026-09-27 10:05:00 UTC". */
        internal fun utc(time: Instant): String {
            val seconds = time.epochSeconds
            val days = floorDiv(seconds, 86_400L)
            val secOfDay = seconds - days * 86_400L
            // Howard Hinnant's civil-from-days.
            val z = days + 719_468L
            val era = floorDiv(z, 146_097L)
            val doe = z - era * 146_097L
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = doy - (153 * mp + 2) / 5 + 1
            val month = if (mp < 10) mp + 3 else mp - 9
            val year = yoe + era * 400 + if (month <= 2) 1 else 0
            fun two(v: Long) = v.toString().padStart(2, '0')
            return "$year-${two(month)}-${two(day)} ${two(secOfDay / 3600)}:${two(secOfDay % 3600 / 60)}:${two(secOfDay % 60)} UTC"
        }

        private fun floorDiv(a: Long, b: Long): Long {
            val q = a / b
            return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
        }
    }
}

/** The report's helpers that do not need a built report. */
public object ProblemReports {
    /**
     * The one first-party destination for "something is broken": this app's repository issue form.
     * App-authored.
     */
    public const val REPO_URL: String = "https://github.com/Eslamasabry/opencode-mobile-next"

    /** The longest link the app hands to the external-link policy. */
    public const val MAX_LINK_LENGTH: Int = 2048

    public const val UNKNOWN_VERSION: String = "unknown+0"

    private val SCHEME_HOST = Regex("""\b([a-zA-Z][a-zA-Z0-9+.-]*://)([^/\s?#"<>]+)""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b""")
    private val OPAQUE = Regex("""\b[A-Za-z0-9_-]{32,}\b""")

    /**
     * Text safe for a public issue: [Redactor.redact] (provider keys, tokens, passwords, registered
     * secrets), then server addresses and long opaque tokens. The path of a URL stays: it says
     * which call failed.
     */
    public fun scrub(redactor: Redactor, text: String): String {
        var safe = redactor.redact(text)
        safe = SCHEME_HOST.replace(safe) { "${it.groupValues[1]}[server]" }
        safe = IPV4.replace(safe, "[address]")
        return OPAQUE.replace(safe, Redactor.MASK)
    }

    /**
     * Newest first. The persisted store wins when present ([stored] oldest first); else this run's
     * errors.
     */
    public fun events(
        stored: List<ProblemReportEvent>?,
        diagnostics: List<ErrorEntry>,
    ): List<ProblemReportEvent> =
        stored?.asReversed()?.toList()
            ?: diagnostics.asReversed().map {
                ProblemReportEvent(
                    ProblemEventKind.Error,
                    it.at,
                    it.source,
                    it.message,
                    it.stack,
                    it.occurrences,
                    it.id,
                )
            }

    /**
     * "1.0.44+50", or "unknown+0" when the platform fails or does not answer within [timeout] (null
     * waits).
     */
    public suspend fun appVersion(
        timeout: Duration? = 2.seconds,
        read: suspend () -> Pair<String, String>?,
    ): String {
        val read =
            attempt({ Failure.Device() }) {
                if (timeout == null) read() else withTimeoutOrNull(timeout) { read() }
            }
        val info = (read as? Outcome.Ok)?.value ?: return UNKNOWN_VERSION
        return "${info.first}+${info.second}"
    }

    /** The link only opens through the policy; an app-authored issue link answers Open. */
    public fun approve(policy: ExternalLinkPolicy, link: ProblemReportLink): LinkDecision.Open? =
        policy.check(link.uri, LinkOrigin.AppAuthored) as? LinkDecision.Open

    /** application/x-www-form-urlencoded, as Dart's `Uri.replace(queryParameters:)` writes it. */
    internal fun formEncode(value: String): String = buildString {
        for (b in value.encodeToByteArray()) {
            val c = b.toInt() and 0xff
            when {
                c in 'a'.code..'z'.code ||
                    c in 'A'.code..'Z'.code ||
                    c in '0'.code..'9'.code ||
                    c == '-'.code ||
                    c == '.'.code ||
                    c == '_'.code ||
                    c == '~'.code -> append(c.toChar())
                c == ' '.code -> append('+')
                else -> append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
