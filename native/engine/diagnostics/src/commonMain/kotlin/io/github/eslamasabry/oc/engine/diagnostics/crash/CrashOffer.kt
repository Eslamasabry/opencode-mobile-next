package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.engine.diagnostics.exit.LaunchReport
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What an opt-in crash report says about the crash (Flutter 2.1.5 `CrashOfferFacts`, story E02-S91): only facts the
 * app already holds in a bounded, safe form. The exception type and stack come from [NativeCrashRecord] (Java symbols
 * and a fixed set of messages only). Nothing here is sent by itself: the person reviews it on Report a problem and
 * files it there.
 */
public data class CrashOfferFacts(
    /** When the crash happened; also the "already offered" mark. */
    val at: Instant,
    /** `java.lang.IllegalStateException`; null when only Android's exit record says it crashed. */
    val exceptionType: String? = null,
    /** One of [NativeCrashRecord.MESSAGES] ("Invalid state"). */
    val exceptionMessage: String? = null,
    /** The rendered stack lines, newest frame first, then any causes. */
    val stack: List<String> = emptyList(),
    /** Android's own words for the exit, when its record said crash. */
    val exitReason: String? = null,
    val androidRelease: String = "",
    val sdk: Int = 0,
    val manufacturer: String = "",
    val model: String = "",
) {
    public companion object {
        /** The JVM record lends its type and stack only when it is the same crash as Android's exit record. */
        private val SAME_CRASH: Duration = 2.minutes

        /**
         * The crash in [report] not offered yet ([lastOfferedMs]), or null: nothing after a normal exit, an update or
         * a stop, nothing older than [CrashOffer.MAX_AGE], and nothing already offered.
         */
        public fun fromLaunch(report: LaunchReport, lastOfferedMs: Long, now: Instant): CrashOfferFacts? {
            fun fresh(at: Instant) =
                at.toEpochMilliseconds() > lastOfferedMs && at <= now + 1.days && now - at <= CrashOffer.MAX_AGE
            val exit = report.exit?.takeIf { it.kind == ExitKind.Crash && fresh(it.at) }
            val jvm = report.lastCrash?.takeIf { fresh(it.timestamp) }
            if (exit == null && jvm == null) return null
            val paired = jvm?.takeIf { exit == null || (exit.at - it.timestamp).absoluteValue <= SAME_CRASH }
            val at = listOfNotNull(exit?.at, jvm?.timestamp).max()
            return CrashOfferFacts(
                at = at,
                exceptionType = paired?.exceptionClass,
                exceptionMessage = paired?.message,
                stack = paired?.diagnosticStack?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
                exitReason = exit?.let { exitWords(it.reason) },
            )
        }

        private fun exitWords(reason: Int): String =
            when (reason) {
                ExitReason.CRASH_NATIVE -> "native crash"
                ExitReason.ANR -> "not responding (ANR)"
                ExitReason.INITIALIZATION_FAILURE -> "failed to start"
                else -> "crash"
            }
    }

    /**
     * The Details Report a problem shows and sends (Flutter `_details`): fixed labels, the time in UTC, the safe
     * message, the device and at most [maxStackLines] stack lines, the rest counted.
     */
    public fun details(maxStackLines: Int = 12): String =
        buildList {
            add("Crashed: ${utc(at)}")
            exitReason?.let { add("Android exit: $it") }
            exceptionMessage?.let { add("Message: $it") }
            val android = listOfNotNull(androidRelease.ifEmpty { null }, if (sdk > 0) "(API $sdk)" else null).joinToString(" ")
            val device = listOfNotNull(manufacturer.ifEmpty { null }, model.ifEmpty { null }).joinToString(" ")
            add("Android: ${android.ifEmpty { "unknown" }}")
            add("Device: ${device.ifEmpty { "unknown" }}")
            if (stack.isEmpty()) {
                add("Stack: not captured")
            } else {
                val kept = maxStackLines.coerceIn(0, stack.size)
                val left = stack.size - kept
                add(if (left == 0) "Stack:" else "Stack (first $kept of ${stack.size} lines):")
                addAll(stack.take(kept))
                if (left > 0) {
                    add("… $left more ${if (left == 1) "line" else "lines"} left out to keep the GitHub link short")
                }
            }
        }.joinToString("\n")

    private fun utc(time: Instant): String {
        // ISO-8601 "2026-11-08T10:15:00Z" → "2026-11-08 10:15:00 UTC", seconds only.
        val iso = Instant.fromEpochSeconds(time.epochSeconds).toString()
        return iso.removeSuffix("Z").replace('T', ' ') + " UTC"
    }
}

/** Where the offer keeps its marks: Flutter's `oc.crashReports.lastOffered` and `oc.crashReports.offer`. */
public interface CrashOfferMarks {
    public fun lastOfferedMs(): Long

    /** False when the mark could not be saved: then nothing is offered, so it can never show on every start. */
    public suspend fun markOffered(atMs: Long): Boolean

    /** "Offer crash reports"; on unless turned off. */
    public fun enabled(): Boolean
}

/**
 * The one-time offer after a crash (Flutter 2.1.5 `CrashReportOffer`, E02-S91): read at start from the same launch
 * report the exit notice reads, held until the person answers.
 *
 * Each crash is offered at most once: its time is saved as seen before the notice shows, so a dismissed, ignored or
 * answered offer never returns, and an offer turned off in Settings marks crashes seen without showing anything
 * (turning it on later does not raise an old one). It never sends anything: "Review and send" opens Report a problem.
 */
public class CrashOffer(private val marks: CrashOfferMarks, private val clock: Clock) {
    private val mutablePending = MutableStateFlow<CrashOfferFacts?>(null)

    /** The crash to offer, until the person answers or dismisses it. */
    public val pending: StateFlow<CrashOfferFacts?> = mutablePending.asStateFlow()

    private var considered = false

    /** Runs once per process; later calls do nothing. [device] adds the phone facts only when there is an offer. */
    public suspend fun consider(report: LaunchReport, device: (CrashOfferFacts) -> CrashOfferFacts = { it }) {
        if (considered) return
        considered = true
        val crash = CrashOfferFacts.fromLaunch(report, marks.lastOfferedMs(), clock.now()) ?: return
        if (!marks.markOffered(crash.at.toEpochMilliseconds())) return
        if (!marks.enabled()) return
        mutablePending.value = device(crash)
    }

    /** Either answer ends the offer for this crash. Returns the facts so "Review and send" can attach them. */
    public fun take(): CrashOfferFacts? {
        val facts = mutablePending.value
        mutablePending.value = null
        return facts
    }

    public companion object {
        /** A crash older than this is not offered. */
        public val MAX_AGE: Duration = 7.days

        /** The Report a problem source line for an attached crash (Flutter `crash.last`). */
        public const val SOURCE: String = "crash.last"
    }
}
