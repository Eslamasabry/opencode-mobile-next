package io.github.eslamasabry.oc.engine.analytics.health

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The app's own short log (Flutter 2.1.5 `AppLogRing`): the last [capacity] lines, in memory only.
 *
 * It holds the breadcrumb words, the analytics events and traces that were sent, and the app's own log lines (already
 * redacted by the app's log writer). It never leaves the phone by itself: its only reader is "Send a problem report",
 * which redacts it again, shows it to the person and sends it only when they tap Send (section E).
 */
public class AppLogRing(public val capacity: Int = DEFAULT_CAPACITY, private val clock: Clock = Clock.System) {
    private val kept = MutableStateFlow<List<String>>(emptyList())

    /** Adds [line] with the UTC time of day, dropping the oldest past [capacity]. A long line is cut. */
    public fun add(line: String, at: Instant = clock.now()) {
        if (capacity <= 0) return
        var text = WHITESPACE.replace(line, " ").trim()
        if (text.isEmpty()) return
        if (text.length > MAX_LINE_LENGTH) text = text.take(MAX_LINE_LENGTH) + "…"
        val entry = "${timeOfDay(at)} $text"
        kept.update { lines -> (lines + entry).let { if (it.size > capacity) it.takeLast(capacity) else it } }
    }

    /** The kept lines, oldest first. */
    public fun lines(): List<String> = kept.value

    public fun clear() {
        kept.value = emptyList()
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 500

        /** One line is kept short: a longer line is cut. */
        public const val MAX_LINE_LENGTH: Int = 300
        private val WHITESPACE = Regex("\\s+")
        private const val DAY_SECONDS = 86_400L

        /** `HH:MM:SS` in UTC. */
        public fun timeOfDay(at: Instant): String {
            val s = at.epochSeconds.mod(DAY_SECONDS)
            fun two(v: Long) = v.toString().padStart(2, '0')
            return "${two(s / 3600)}:${two(s / 60 % 60)}:${two(s % 60)}"
        }
    }
}
