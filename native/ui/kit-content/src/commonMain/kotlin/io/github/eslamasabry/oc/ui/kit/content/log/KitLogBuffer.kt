package io.github.eslamasabry.oc.ui.kit.content.log

/** How a log line reads: a plain line, a warning or an error (said in words, not only colour). */
public enum class KitLogLevel { Normal, Warning, Error }

/** One line of a log. */
public data class KitLogLine(val text: String, val level: KitLogLevel = KitLogLevel.Normal)

/**
 * The bounded line buffer behind a log view (B-KIT-021). Port of Dart `KitLogBuffer` (`lib/ui/kit/kit_log_panel.dart`):
 * output chunks split into lines at `\n` (a trailing `\r` is dropped), the unfinished last line stays visible as a
 * partial line and is completed by the next chunk, and past [capacity] the oldest lines are dropped and counted in
 * [dropped] so the view can say how many earlier lines it does not show. Only the lines that survive a chunk are
 * materialized: a large polled log does not allocate its discarded prefix.
 */
public class KitLogBuffer(public val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val kept = ArrayDeque<KitLogLine>()
    private var partial = ""
    private var partialLevel = KitLogLevel.Normal

    /** The visible lines, oldest first, the partial line last. */
    public var lines: List<KitLogLine> = emptyList()
        private set

    /** Lines dropped past [capacity] since the last [clear] or [replaceText]. */
    public var dropped: Int = 0
        private set

    public fun add(line: KitLogLine) {
        closePartial()
        kept.addLast(line)
        publish()
    }

    /** Appends one output chunk. */
    public fun appendText(chunk: String, level: KitLogLevel = KitLogLevel.Normal) {
        if (chunk.isEmpty()) return
        val text = partial + chunk
        var end = text.lastIndexOf('\n')
        partial = text.substring(end + 1)
        val keep = if (partial.isEmpty()) capacity else capacity - 1
        val tail = ArrayList<KitLogLine>()
        var completed = 0
        while (end >= 0) {
            val start = if (end == 0) -1 else text.lastIndexOf('\n', end - 1)
            completed++
            if (tail.size < keep) tail += KitLogLine(strip(text.substring(start + 1, end)), level)
            end = start
        }
        if (completed > keep) {
            dropped += kept.size + completed - keep
            kept.clear()
        }
        tail.asReversed().forEach { kept.addLast(it) }
        partialLevel = level
        publish()
    }

    /** A source that re-sends its whole output: replaces every line. */
    public fun replaceText(text: String) {
        kept.clear()
        partial = ""
        dropped = 0
        appendText(text)
        if (text.isEmpty()) publish()
    }

    public fun clear() {
        kept.clear()
        partial = ""
        dropped = 0
        publish()
    }

    private fun closePartial() {
        if (partial.isEmpty()) return
        kept.addLast(KitLogLine(strip(partial), partialLevel))
        partial = ""
    }

    private fun publish() {
        val keep = (if (partial.isEmpty()) capacity else capacity - 1).coerceAtLeast(0)
        while (kept.size > keep) {
            kept.removeFirst()
            dropped++
        }
        lines = buildList {
            addAll(kept)
            if (partial.isNotEmpty()) add(KitLogLine(strip(partial), partialLevel))
        }
    }

    private fun strip(s: String) = s.removeSuffix("\r")

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 2000
    }
}
