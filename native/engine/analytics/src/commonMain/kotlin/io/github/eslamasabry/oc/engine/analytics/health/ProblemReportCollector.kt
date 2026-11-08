package io.github.eslamasabry.oc.engine.analytics.health

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Builds the text of "Send a problem report" (section E; Flutter 2.1.5 `ProblemReportCollector`): the last 500 lines of
 * the app's own log, the custom keys, the recent process ends and the device context, all of them words, numbers and
 * ranges. No description field: free text is the one thing this report does not take.
 *
 * [build] returns the text already passed through [ReportRedaction]: it is what the person previews and exactly what
 * [Telemetry.sendProblemReport] sends.
 */
public class ProblemReportCollector(
    private val telemetry: Telemetry,
    private val redaction: ReportRedaction,
    private val health: HealthTelemetry? = null,
    private val platform: HealthPlatform? = null,
    private val clock: Clock = Clock.System,
) {
    public suspend fun build(): String {
        val out = StringBuilder()
        out.appendLine("OpenCode Mobile problem report")
        out.appendLine("Made: ${utc(clock.now())}")
        out.appendLine()

        out.appendLine("Keys")
        val keys = telemetry.healthKeys
        if (keys.isEmpty()) out.appendLine("  none") else keys.forEach { (k, v) -> out.appendLine("  $k=$v") }
        out.appendLine()

        out.appendLine("Device")
        val properties = health?.deviceContextForReport()?.properties.orEmpty()
        if (properties.isEmpty()) out.appendLine("  none") else properties.forEach { (k, v) -> out.appendLine("  $k=$v") }
        out.appendLine()

        out.appendLine("Process ends")
        val exits = runCatching { platform?.exits(limit = 5).orEmpty() }.getOrDefault(emptyList())
        if (exits.isEmpty()) out.appendLine("  none")
        for (exit in exits) {
            out.appendLine("  ${utc(Instant.fromEpochMilliseconds(exit.atMs))} ${exitReasonWord(exit.reason) ?: "normal"}")
        }
        out.appendLine()

        val lines = telemetry.ring.lines().takeLast(MAX_LINES)
        out.appendLine("Log (last ${lines.size} lines)")
        if (lines.isEmpty()) out.appendLine("  empty")
        lines.forEach { out.appendLine("  $it") }
        return redaction.redact(out.toString())
    }

    public companion object {
        public const val MAX_LINES: Int = 500

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
