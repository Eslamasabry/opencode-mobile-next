package io.github.eslamasabry.oc.ui.kit.content.terminal

/**
 * The readable transcript of a live terminal for accessible mode (plan 06 §7.7, DESKTOP-WEB-CROSS-30): terminal output
 * decoded as text, with every rendering command (CSI, OSC, DCS/SOS/PM/APC strings, charset designations, C0/C1
 * controls) removed, CR LF and lone CR read as one line break, and only the newest [MAX_CHARS] characters kept.
 *
 * Port of Dart `_TerminalTranscriptSanitizer` (`lib/ui/screens/terminal/terminal_accessible.dart`) and the cap in
 * `terminal_surface.dart`. Stateful: a sequence split across chunks is still removed. A chunk that starts with NUL while
 * no sequence is open is transport metadata (OpenCode's NUL-prefixed cursor frames) and is dropped whole.
 */
public class TerminalTranscript {
    private var state = NORMAL
    private var pendingCarriageReturn = false
    private val buffer = StringBuilder()

    /** The transcript so far: plain text, at most [MAX_CHARS] characters. */
    public val text: String get() = buffer.toString()

    /** Adds one decoded output chunk; returns the readable text it contributed. */
    public fun add(chunk: String): String {
        val readable = strip(chunk)
        if (readable.isNotEmpty()) {
            buffer.append(readable)
            if (buffer.length > MAX_CHARS) buffer.deleteRange(0, buffer.length - MAX_CHARS)
        }
        return readable
    }

    /** Forgets the text and any open sequence (a terminal that starts over). */
    public fun reset() {
        state = NORMAL
        pendingCarriageReturn = false
        buffer.clear()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun strip(chunk: String): String {
        if (chunk.isEmpty() || (state == NORMAL && chunk[0] == '\u0000')) return ""
        val out = StringBuilder()
        var i = 0
        while (i < chunk.length) {
            val rune = chunk.codePointAt(i)
            i += if (rune > 0xFFFF) 2 else 1
            if (state == NORMAL && pendingCarriageReturn) {
                pendingCarriageReturn = false
                out.append('\n')
                if (rune == 0x0a) continue
            }
            state =
                when (state) {
                    NORMAL -> normal(rune, out)
                    ESCAPE ->
                        when {
                            rune == 0x5b -> CSI
                            rune == 0x5d -> OSC
                            rune == 0x50 || rune == 0x58 || rune == 0x5e || rune == 0x5f -> CONTROL_STRING
                            rune in 0x20..0x2f -> ESCAPE_INTERMEDIATE
                            rune != 0x1b -> NORMAL
                            else -> ESCAPE
                        }
                    ESCAPE_INTERMEDIATE ->
                        when {
                            rune == 0x1b -> ESCAPE
                            rune in 0x30..0x7e -> NORMAL
                            rune < 0x20 || rune > 0x2f -> NORMAL
                            else -> ESCAPE_INTERMEDIATE
                        }
                    CSI ->
                        when {
                            rune == 0x1b -> ESCAPE
                            rune in 0x40..0x7e -> NORMAL
                            else -> CSI
                        }
                    OSC ->
                        when (rune) {
                            0x07, 0x9c -> NORMAL
                            0x1b -> OSC_ESCAPE
                            else -> OSC
                        }
                    OSC_ESCAPE ->
                        when (rune) {
                            0x5c -> NORMAL
                            0x1b -> OSC_ESCAPE
                            else -> OSC
                        }
                    CONTROL_STRING ->
                        when (rune) {
                            0x9c -> NORMAL
                            0x1b -> CONTROL_STRING_ESCAPE
                            else -> CONTROL_STRING
                        }
                    else -> // CONTROL_STRING_ESCAPE
                        when (rune) {
                            0x5c -> NORMAL
                            0x1b -> CONTROL_STRING_ESCAPE
                            else -> CONTROL_STRING
                        }
                }
        }
        return out.toString()
    }

    private fun normal(rune: Int, out: StringBuilder): Int =
        when {
            rune == 0x1b -> ESCAPE
            rune == 0x9b -> CSI
            rune == 0x9d -> OSC
            rune == 0x90 || rune == 0x98 || rune == 0x9e || rune == 0x9f -> CONTROL_STRING
            rune == 0x0d -> {
                pendingCarriageReturn = true
                NORMAL
            }
            rune == 0x0a || rune == 0x09 -> {
                out.append(rune.toChar())
                NORMAL
            }
            rune >= 0x20 && rune != 0x7f && rune !in 0x80..0x9f -> {
                out.appendCodePoint(rune)
                NORMAL
            }
            else -> NORMAL
        }

    public companion object {
        /** The newest characters kept (Dart `terminal_surface.dart`: 100,000). */
        public const val MAX_CHARS: Int = 100_000

        /** [raw] as accessible mode shows it: stripped and capped. Plain text passes through unchanged. */
        public fun forDisplay(raw: String): String = TerminalTranscript().apply { add(raw) }.text

        /** One list row per line; a trailing line break adds no empty row. */
        public fun lines(text: String): List<String> =
            if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')

        /** What the "Send to terminal" field writes: the value and a carriage return (Enter). */
        public fun sendLine(value: String): String = value + "\r"

        private const val NORMAL = 0
        private const val ESCAPE = 1
        private const val ESCAPE_INTERMEDIATE = 2
        private const val CSI = 3
        private const val OSC = 4
        private const val OSC_ESCAPE = 5
        private const val CONTROL_STRING = 6
        private const val CONTROL_STRING_ESCAPE = 7
    }
}

private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
    return high.code
}

private fun StringBuilder.appendCodePoint(rune: Int): StringBuilder {
    if (rune <= 0xFFFF) return append(rune.toChar())
    val v = rune - 0x10000
    append((0xD800 + (v shr 10)).toChar())
    return append((0xDC00 + (v and 0x3FF)).toChar())
}
