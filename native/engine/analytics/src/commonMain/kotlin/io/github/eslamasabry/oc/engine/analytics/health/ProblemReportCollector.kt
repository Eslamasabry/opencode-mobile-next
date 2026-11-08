package io.github.eslamasabry.oc.engine.analytics.health

import kotlin.time.Clock
import kotlin.time.Instant

/** The last time the app closed when it should not have: its contract word and when (ApplicationExitInfo). */
public data class ReportClose(val kind: String, val atMs: Long)

/** The server the app was connected to, without its address: `OpenCode`, `Paseo` or `Codex`, and its version. */
public data class ReportConnection(val kind: String, val version: String?)

/** One kind of problem the app met recently (a [failKindOf] word) and how many times. */
public data class ReportProblem(val kind: String, val count: Int)

/**
 * What one problem report holds (R1, 2026-11-09): the person's words, the last unexpected close, the app, the phone,
 * the connection without its address, and the kinds of problem met recently. Nothing else: no log, no conversation,
 * no file, no password, key or server address. "What's included" lists every field, and [text] is exactly what Send
 * sends and Copy text copies.
 *
 * [words] is already through [ReportRedaction]; every other field is a fixed word, a number or a range.
 */
public data class ProblemReportContent(
    val words: String = "",
    val lastClose: ReportClose? = null,
    val appVersion: String = "",
    val appBuild: String = "",
    val android: String = "",
    val abi: String? = null,
    /** A [HealthRanges.ram] word (`8-12`, gigabytes). */
    val ramRange: String? = null,
    val connection: ReportConnection? = null,
    val problems: List<ReportProblem> = emptyList(),
    val madeMs: Long = 0,
) {
    /** The report as it leaves the phone: fixed English labels, since the developer reads it. */
    val text: String
        get() = buildString {
            appendLine("OpenCode Mobile problem report")
            appendLine("Made: ${ProblemReportCollector.utc(Instant.fromEpochMilliseconds(madeMs))}")
            appendLine()
            appendLine("What happened")
            appendLine(words.ifBlank { "(not described)" })
            appendLine()
            lastClose?.let {
                appendLine("Last time the app closed: ${it.kind}, ${ProblemReportCollector.utc(Instant.fromEpochMilliseconds(it.atMs))}")
            }
            appendLine("App: OpenCode Mobile $appVersion ($appBuild)")
            appendLine("Phone: " + listOfNotNull("Android $android", abi, ramRange?.let { "$it GB memory" }).joinToString(" · "))
            appendLine(
                "Connection: " +
                    (connection?.let { listOfNotNull(it.kind, it.version).joinToString(" ") + " · address taken out" }
                        ?: "not connected")
            )
            append(
                "Recent problems: " +
                    problems.joinToString(", ") { "${it.kind} ×${it.count}" }.ifEmpty { "none" }
            )
        }

    /** The same report with the person's [redacted] words. */
    public fun withWords(redacted: String): ProblemReportContent = copy(words = redacted)
}

/**
 * Builds a problem report's facts (R1; section E of the health contract): the newest process end when it was
 * unexpected, the app and phone, the connection the host names, and the problem kinds [ProblemTally] counted in the
 * app's own ring. Reading it sends nothing; [ProblemReportContent.text] leaves only when the person taps Send.
 */
public class ProblemReportCollector(
    private val telemetry: Telemetry,
    private val redaction: ReportRedaction,
    private val health: HealthTelemetry? = null,
    private val platform: HealthPlatform? = null,
    private val connection: suspend () -> ReportConnection? = { null },
    private val clock: Clock = Clock.System,
) {
    /** The facts, with no words yet ([words] adds them). Never throws: a part that cannot be read is left out. */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun build(): ProblemReportContent {
        val now = clock.now().toEpochMilliseconds()
        val device = health?.deviceContextForReport()
        val newest =
            try {
                platform?.exits(limit = 1)?.firstOrNull()
            } catch (_: Throwable) {
                null
            }
        val close =
            newest
                ?.let { exit -> exitReasonWord(exit.reason)?.takeIf { it in UNEXPECTED }?.let { ReportClose(it, exit.atMs) } }
                ?.takeIf { now - it.atMs in 0..CLOSE_MAX_AGE_MS }
        val linked =
            try {
                connection()
            } catch (_: Throwable) {
                null
            }
        return ProblemReportContent(
            lastClose = close,
            appVersion = platform?.appVersion.orEmpty(),
            appBuild = platform?.appBuild.orEmpty(),
            android = platform?.osRelease.orEmpty(),
            abi = device?.abi?.takeIf { it in HealthWords.abis },
            ramRange = device?.ramRange,
            connection = linked?.let { ReportConnection(it.kind, it.version?.let(::versionOnly)) },
            problems = ProblemTally.count(telemetry.ring.lines()),
            madeMs = now,
        )
    }

    /** The person's words as they leave: redacted and cut at [MAX_WORDS] characters. */
    public fun words(text: String): String = redaction.redact(text.trim().take(MAX_WORDS))

    public companion object {
        /** A close older than a week is not "what happened" any more. */
        public const val CLOSE_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000

        public const val MAX_WORDS: Int = 4000

        /** The closes a person would call a problem; a force stop, an update or a signal is not one. */
        public val UNEXPECTED: Set<String> = setOf("crash", "crash_native", "anr", "low_memory", "excessive_resource")

        private val VERSION = Regex("^v?\\d{1,3}(\\.\\d{1,4}){0,3}")

        /** `2.1.1` from whatever the server said, or null: never free text. */
        private fun versionOnly(raw: String): String? = VERSION.find(raw.trim())?.value?.removePrefix("v")

        /** `2026-11-08 12:00:00 UTC`, from the epoch alone (no time-zone data needed). */
        public fun utc(at: Instant): String {
            val seconds = at.epochSeconds
            val days = seconds.floorDiv(86_400L)
            val (y, m, d) = civil(days)
            fun two(v: Long) = v.toString().padStart(2, '0')
            return "$y-${two(m)}-${two(d)} ${AppLogRing.timeOfDay(at)} UTC"
        }

        /** Days since 1970-01-01 to year, month, day (Howard Hinnant's civil-from-days). */
        private fun civil(days: Long): Triple<Long, Long, Long> {
            val z = days + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            val y = yoe + era * 400 + if (m <= 2) 1 else 0
            return Triple(y, m, d)
        }
    }
}

/**
 * The kinds of problem the app met, kept as `problem <kind>` lines in the app's own ring whatever the switches say:
 * the ring never leaves the phone by itself, and only a report the person sends carries the counts.
 */
public object ProblemTally {
    private const val PREFIX: String = "problem "

    /** Every word [failKindOf] answers. */
    public val kinds: List<String> =
        listOf("timeout", "auth", "tls", "server_5xx", "protocol", "dns", "refused", "offline", "too_large", "rejected", "other")

    public fun note(ring: AppLogRing, kind: String) {
        if (kind in kinds) ring.add(PREFIX + kind)
    }

    /** The counted kinds, most frequent first (ties in [kinds] order). */
    public fun count(lines: List<String>): List<ReportProblem> {
        val counts = LinkedHashMap<String, Int>()
        for (line in lines) {
            val at = line.indexOf(' ')
            if (at < 0 || !line.startsWith(PREFIX, at + 1)) continue
            val kind = line.substring(at + 1 + PREFIX.length)
            if (kind in kinds) counts[kind] = (counts[kind] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { kinds.indexOf(it.key) })
            .map { ReportProblem(it.key, it.value) }
    }
}
