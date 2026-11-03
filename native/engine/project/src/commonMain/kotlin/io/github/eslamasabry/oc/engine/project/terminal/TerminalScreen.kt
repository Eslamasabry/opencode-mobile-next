package io.github.eslamasabry.oc.engine.project.terminal

/**
 * One cell. A double-width character takes two: the first holds it with [width] 2, the second is a placeholder with
 * width 0. An empty cell has [codePoint] 0 and width 1.
 */
public data class TerminalCell(val codePoint: Int, val width: Int, val style: TerminalStyle) {
    public companion object {
        public val Blank: TerminalCell = TerminalCell(0, 1, TerminalStyle.Plain)
    }
}

/** One line of the buffer. [isWrapped]: it continues the line above (an automatic wrap, not a line break). */
public class TerminalLine internal constructor(cols: Int) {
    internal var cells: Array<TerminalCell> = Array(cols) { TerminalCell.Blank }
    public var isWrapped: Boolean = false
        internal set

    public val length: Int get() = cells.size

    public fun cell(x: Int): TerminalCell = cells.getOrElse(x) { TerminalCell.Blank }

    public fun style(x: Int): TerminalStyle = cell(x).style

    /** The text of cells [from] until [to] (exclusive); empty cells read as spaces. */
    public fun text(from: Int = 0, to: Int = length): String =
        buildString {
            for (x in from.coerceAtLeast(0) until to.coerceAtMost(cells.size)) {
                val cell = cells[x]
                when {
                    cell.width == 0 -> Unit
                    cell.codePoint == 0 -> append(' ')
                    else -> appendCodePointCompat(cell.codePoint)
                }
            }
        }

    internal fun resize(cols: Int) {
        if (cols == cells.size) return
        cells = Array(cols) { cells.getOrElse(it) { TerminalCell.Blank } }
    }

    internal fun clear(from: Int = 0, to: Int = length) {
        for (x in from.coerceAtLeast(0) until to.coerceAtMost(length)) cells[x] = TerminalCell.Blank
    }
}

/**
 * The screen of one terminal: the parser and buffer the Flutter app takes from its patched xterm, reduced to what a
 * shell needs. It keeps up to [maxLines] lines (scrollback plus the visible [rows]), reads UTF-16 text already decoded
 * (see [Utf8StreamDecoder]), and carries the two fixes the app made: a double-width character at the last column
 * wraps whole, and erasing the scrollback (`CSI 3 J`, what `clear` sends) keeps line indices right so a selection lands
 * on the words it covers. SGR follows [applySgr]; `ESC c` (RIS) resets everything.
 */
public class TerminalScreen(cols: Int = 80, rows: Int = 24, public val maxLines: Int = 10_000) {
    public var cols: Int = cols.coerceAtLeast(1)
        private set
    public var rows: Int = rows.coerceAtLeast(1)
        private set

    private val buffer: MutableList<TerminalLine> = MutableList(this.rows) { TerminalLine(this.cols) }

    public val lines: List<TerminalLine> get() = buffer

    public var cursorX: Int = 0
        private set

    /** The cursor's row on the visible screen. */
    public var cursorY: Int = 0
        private set

    /** Lines above the visible screen. */
    public val scrollBack: Int get() = buffer.size - rows

    /** The cursor's row as an index into [lines]. */
    public val absoluteCursorY: Int get() = scrollBack + cursorY

    /** DECCKM (`CSI ? 1 h`): programs such as vim and htop ask for application cursor keys. */
    public var cursorKeysMode: Boolean = false
        private set

    public var autoWrapMode: Boolean = true
        private set

    public var style: TerminalStyle = TerminalStyle.Plain
        private set

    private var wrapPending = false
    private var savedX = 0
    private var savedY = 0

    // Parser state, kept across writes so a sequence split between chunks still applies.
    private var mode = Mode.Ground
    private val sequence = StringBuilder()
    private var pendingHigh: Char? = null

    private enum class Mode { Ground, Escape, Csi, Osc, OscEscape, Charset }

    public fun write(text: String) {
        for (char in text) {
            val high = pendingHigh
            if (high != null) {
                pendingHigh = null
                if (char.isLowSurrogate()) {
                    feed(toCodePoint(high, char))
                    continue
                }
                feed(REPLACEMENT)
            }
            if (char.isHighSurrogate()) pendingHigh = char else feed(char.code)
        }
    }

    /** The text of the visible line [y] (an index into [lines]). */
    public fun lineText(y: Int): String = buffer.getOrNull(y)?.text().orEmpty()

    /** Text from ([fromX], [fromY]) to ([toX], [toY]) exclusive, rows as indices into [lines]; lines join with `\n`. */
    public fun text(fromX: Int, fromY: Int, toX: Int, toY: Int): String =
        buildString {
            for (y in fromY..toY) {
                val line = buffer.getOrNull(y) ?: continue
                val start = if (y == fromY) fromX else 0
                val end = if (y == toY) toX else line.length
                if (y > fromY && !line.isWrapped) append('\n')
                append(if (y == toY) line.text(start, end) else line.text(start, end).trimEnd())
            }
        }

    /** Every non-empty line, right-trimmed, joined with `\n`: what a person reads. */
    public fun visibleText(): String = buffer.map { it.text().trimEnd() }.filter { it.isNotEmpty() }.joinToString("\n")

    public fun resize(cols: Int, rows: Int) {
        val newCols = cols.coerceAtLeast(1)
        val newRows = rows.coerceAtLeast(1)
        if (newCols != this.cols) {
            buffer.forEach { it.resize(newCols) }
            this.cols = newCols
        }
        val cursorLine = absoluteCursorY
        if (newRows > this.rows) {
            // Grow downwards: blank lines below the content.
            repeat(newRows - this.rows) { buffer.add(TerminalLine(newCols)) }
        } else if (newRows < this.rows) {
            // Shrink from the bottom while those lines are blank and below the cursor; the rest scroll up.
            var drop = this.rows - newRows
            while (drop > 0 && buffer.size - 1 > cursorLine && buffer.last().text().isBlank()) {
                buffer.removeAt(buffer.size - 1)
                drop--
            }
        }
        this.rows = newRows
        while (buffer.size < newRows) buffer.add(TerminalLine(newCols))
        cursorY = (cursorLine - scrollBack).coerceIn(0, newRows - 1)
        cursorX = cursorX.coerceIn(0, newCols - 1)
        wrapPending = false
        trim()
    }

    // ---- parsing

    private fun feed(codePoint: Int) {
        when (mode) {
            Mode.Ground -> ground(codePoint)
            Mode.Escape -> escape(codePoint)
            Mode.Csi -> csi(codePoint)
            Mode.Osc ->
                when (codePoint) {
                    BEL -> mode = Mode.Ground
                    ESC -> mode = Mode.OscEscape
                    else -> Unit
                }
            Mode.OscEscape -> mode = if (codePoint == '\\'.code) Mode.Ground else Mode.Osc
            Mode.Charset -> mode = Mode.Ground
        }
    }

    private fun ground(codePoint: Int) {
        when (codePoint) {
            ESC -> mode = Mode.Escape
            '\r'.code -> carriageReturn()
            '\n'.code, 0x0b, 0x0c -> lineFeed()
            '\b'.code -> {
                wrapPending = false
                if (cursorX > 0) cursorX--
            }
            '\t'.code -> {
                wrapPending = false
                cursorX = ((cursorX / 8 + 1) * 8).coerceAtMost(cols - 1)
            }
            BEL, 0 -> Unit
            in 0x01..0x1f, 0x7f -> Unit
            else -> print(codePoint)
        }
    }

    private fun escape(codePoint: Int) {
        mode = Mode.Ground
        when (codePoint.toChar()) {
            '[' -> {
                sequence.clear()
                mode = Mode.Csi
            }
            ']' -> mode = Mode.Osc
            'c' -> reset()
            '7' -> {
                savedX = cursorX
                savedY = cursorY
            }
            '8' -> {
                cursorX = savedX.coerceIn(0, cols - 1)
                cursorY = savedY.coerceIn(0, rows - 1)
                wrapPending = false
            }
            'D' -> lineFeed()
            'E' -> {
                carriageReturn()
                lineFeed()
            }
            'M' -> reverseIndex()
            '(', ')', '*', '+' -> mode = Mode.Charset
            else -> Unit
        }
    }

    private fun csi(codePoint: Int) {
        if (codePoint in 0x40..0x7e) {
            mode = Mode.Ground
            val raw = sequence.toString()
            sequence.clear()
            handleCsi(raw, codePoint.toChar())
        } else if (codePoint in 0x20..0x3f) {
            sequence.append(codePoint.toChar())
            if (sequence.length > MAX_SEQUENCE) {
                sequence.clear()
                mode = Mode.Ground
            }
        } else {
            // A control inside a CSI ends it unapplied.
            sequence.clear()
            mode = Mode.Ground
            ground(codePoint)
        }
    }

    private fun handleCsi(raw: String, final: Char) {
        val prefix = raw.firstOrNull()?.takeIf { it in '<'..'?' }
        val params = CsiParams.parse(if (prefix != null) raw.substring(1) else raw)
        val first = params.values.firstOrNull() ?: 0
        val count = first.coerceAtLeast(1)
        when (final) {
            'm' -> if (prefix == null) style = style.applySgr(params)
            'h', 'l' -> if (prefix == '?') params.values.forEach { setDecMode(it, final == 'h') }
            'H', 'f' -> {
                val row = params.values.getOrNull(0)?.coerceAtLeast(1) ?: 1
                val col = params.values.getOrNull(1)?.coerceAtLeast(1) ?: 1
                moveTo(col - 1, row - 1)
            }
            'A' -> moveTo(cursorX, cursorY - count)
            'B' -> moveTo(cursorX, cursorY + count)
            'C' -> moveTo(cursorX + count, cursorY)
            'D' -> moveTo(cursorX - count, cursorY)
            'G' -> moveTo(count - 1, cursorY)
            'd' -> moveTo(cursorX, count - 1)
            'J' -> eraseDisplay(first)
            'K' -> eraseLine(first)
            else -> Unit
        }
    }

    private fun setDecMode(mode: Int, enabled: Boolean) {
        when (mode) {
            1 -> cursorKeysMode = enabled
            7 -> autoWrapMode = enabled
            else -> Unit
        }
    }

    // ---- buffer operations

    private fun line(y: Int): TerminalLine = buffer[scrollBack + y]

    private fun print(codePoint: Int) {
        val width = terminalCharWidth(codePoint)
        if (width == 0) return
        if (wrapPending && autoWrapMode) wrap()
        wrapPending = false
        if (width == 2 && cursorX == cols - 1 && cols > 1) {
            // A wide character never goes half past the edge: the last column stays blank and it wraps whole.
            if (autoWrapMode) wrap() else return
        }
        val line = line(cursorY)
        if (width == 2 && cursorX + 1 < cols) {
            line.cells[cursorX] = TerminalCell(codePoint, 2, style)
            line.cells[cursorX + 1] = TerminalCell(0, 0, style)
        } else {
            line.cells[cursorX] = TerminalCell(codePoint, 1, style)
        }
        val next = cursorX + width
        if (next >= cols) {
            cursorX = cols - 1
            wrapPending = true
        } else {
            cursorX = next
        }
    }

    private fun wrap() {
        cursorX = 0
        lineFeed()
        line(cursorY).isWrapped = true
    }

    private fun carriageReturn() {
        cursorX = 0
        wrapPending = false
    }

    private fun lineFeed() {
        wrapPending = false
        if (cursorY < rows - 1) {
            cursorY++
            return
        }
        buffer.add(TerminalLine(cols))
        trim()
    }

    private fun reverseIndex() {
        wrapPending = false
        if (cursorY > 0) {
            cursorY--
            return
        }
        buffer.add(scrollBack, TerminalLine(cols))
        buffer.removeAt(buffer.size - 1)
    }

    private fun trim() {
        val extra = buffer.size - maxOf(maxLines, rows)
        if (extra > 0) repeat(extra) { buffer.removeAt(0) }
    }

    private fun moveTo(x: Int, y: Int) {
        cursorX = x.coerceIn(0, cols - 1)
        cursorY = y.coerceIn(0, rows - 1)
        wrapPending = false
    }

    private fun eraseDisplay(how: Int) {
        when (how) {
            0 -> {
                eraseLine(0)
                for (y in cursorY + 1 until rows) line(y).clear()
            }
            1 -> {
                eraseLine(1)
                for (y in 0 until cursorY) line(y).clear()
            }
            2 -> for (y in 0 until rows) line(y).apply { clear(); isWrapped = false }
            3 -> eraseScrollback()
        }
    }

    /** Drops the lines above the screen; what stays is re-indexed from 0, so the cursor row is still right. */
    private fun eraseScrollback() {
        if (scrollBack <= 0) return
        repeat(scrollBack) { buffer.removeAt(0) }
    }

    private fun eraseLine(how: Int) {
        val line = line(cursorY)
        when (how) {
            0 -> line.clear(cursorX, cols)
            1 -> line.clear(0, cursorX + 1)
            2 -> line.clear()
        }
    }

    /** RIS: a fresh screen, plain style, default modes. */
    private fun reset() {
        buffer.clear()
        repeat(rows) { buffer.add(TerminalLine(cols)) }
        cursorX = 0
        cursorY = 0
        wrapPending = false
        style = TerminalStyle.Plain
        cursorKeysMode = false
        autoWrapMode = true
        savedX = 0
        savedY = 0
    }

    private companion object {
        const val ESC = 0x1b
        const val BEL = 0x07
        const val REPLACEMENT = 0xFFFD
        const val MAX_SEQUENCE = 256
    }
}

private fun toCodePoint(high: Char, low: Char): Int = ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000

internal fun StringBuilder.appendCodePointCompat(codePoint: Int) {
    if (codePoint < 0x10000) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset shr 10)).toChar())
        append((0xDC00 + (offset and 0x3ff)).toChar())
    }
}

/** Columns a code point takes: 0 for combining marks and joiners, 2 for East Asian wide and most emoji, else 1. */
public fun terminalCharWidth(codePoint: Int): Int =
    when {
        codePoint in 0x0300..0x036F || codePoint in 0x200B..0x200F || codePoint in 0xFE00..0xFE0F ||
            codePoint in 0x20D0..0x20FF || codePoint == 0x00AD -> 0
        codePoint in 0x1100..0x115F || codePoint in 0x2E80..0x303E || codePoint in 0x3041..0x33FF ||
            codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF || codePoint in 0xA000..0xA4CF ||
            codePoint in 0xAC00..0xD7A3 || codePoint in 0xF900..0xFAFF || codePoint in 0xFE30..0xFE4F ||
            codePoint in 0xFF00..0xFF60 || codePoint in 0xFFE0..0xFFE6 || codePoint in 0x1F300..0x1F64F ||
            codePoint in 0x1F900..0x1F9FF || codePoint in 0x1FA70..0x1FAFF || codePoint in 0x20000..0x3FFFD -> 2
        else -> 1
    }
