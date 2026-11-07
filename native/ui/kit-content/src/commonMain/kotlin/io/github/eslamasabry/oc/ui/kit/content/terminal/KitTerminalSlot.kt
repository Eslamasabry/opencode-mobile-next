package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription

/**
 * The live terminal's platform slot (plan 06 §7.7 `.live`, ADR §7.3 item 3): draws the platform view a terminal host
 * made (on Android a Termux `TerminalView`) from the `native` value of its `PlatformViewHandle` (`:core:common`). Only
 * kit `androidMain` casts that value; the Android app root provides `AndroidKitTerminalSlot` through [LocalKitTerminalSlot]. Desktop and
 * iOS provide none: there the live grid draws nothing and accessible mode is the only view.
 */
@Stable
public interface KitTerminalSlot {
    @Composable
    public fun Live(platformView: Any, modifier: Modifier)
}

/** The platform's live terminal slot; null where the platform has no terminal view. */
public val LocalKitTerminalSlot: ProvidableCompositionLocal<KitTerminalSlot?> = staticCompositionLocalOf { null }

/** Test tag of the live grid's box (the only node the grid has in the semantics tree). */
public const val KIT_TERMINAL_LIVE_TAG: String = "terminal-live"

/**
 * The live character grid ([platformView] = `PlatformViewHandle.native`; kit-content does not see `:core:common`
 * yet, S3-TERMMIN report). A screen reader cannot read a grid, so the whole box is one node: [terminalLabel] as its
 * description and [title] as its state (`clearAndSetSemantics`); no grid cell reaches the semantics tree. The platform
 * slot also hides the view's own accessibility children (06 §7.7 "The grid").
 */
@Composable
public fun KitTerminalLive(platformView: Any, title: String, terminalLabel: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .testTag(KIT_TERMINAL_LIVE_TAG)
            .clearAndSetSemantics {
                contentDescription = terminalLabel
                stateDescription = title
            },
    ) {
        LocalKitTerminalSlot.current?.Live(platformView, Modifier.fillMaxSize())
    }
}

/** A colour a program set on a cell: the view's own, one of the 256 palette colours, or a true colour (0xRRGGBB). */
@Immutable
public sealed interface KitTerminalColor {
    public data object Default : KitTerminalColor

    public data class Palette(val index: Int) : KitTerminalColor

    public data class Rgb(val r: Int, val g: Int, val b: Int) : KitTerminalColor
}

/** What a program set on one cell (Dart xterm `CellData`: colours and the bold, faint, italic, underline, inverse flags). */
@Immutable
public data class KitTerminalCellStyle(
    val foreground: KitTerminalColor = KitTerminalColor.Default,
    val background: KitTerminalColor = KitTerminalColor.Default,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
)

/** One cell: its character (one code point, a surrogate pair kept together) and its style. */
@Immutable
public data class KitTerminalCell(val char: String, val style: KitTerminalCellStyle)

/** One buffer line (Dart xterm `BufferLine`): [width] cells and whether it continues the line above it. */
public class KitTerminalLine(width: Int) {
    internal var cells: Array<KitTerminalCell?> = arrayOfNulls(width)
    public var isWrapped: Boolean = false
        internal set

    public val width: Int get() = cells.size

    public operator fun get(x: Int): KitTerminalCell? = cells.getOrNull(x)

    /** The text of the line, empty cells as spaces, trailing ones dropped. */
    public val text: String
        get() = buildString { for (i in 0 until trimmedLength()) append(cells[i]?.char ?: " ") }

    internal fun resize(width: Int) {
        cells = cells.copyOf(width)
    }

    /** Dart `BufferLine.getTrimmedLength`: the cells up to the last one with content. */
    internal fun trimmedLength(cols: Int = cells.size): Int {
        for (i in minOf(cols, cells.size) - 1 downTo 0) if (cells[i] != null) return i + 1
        return 0
    }
}

/**
 * The live view's screen (Dart `xterm.Terminal` with its main buffer, third_party/xterm at FLUTTER_REF): a program's
 * output written with [write] lands in [lines]; the view sizes it with [resize], which reflows long lines as xterm
 * does. It understands what a shell prompt and a line-mode program print: text, CR, LF, BS, TAB, the SGR colours and
 * styles (16, 256 and true colour), cursor moves (CUU/CUD/CUF/CUB/CUP), erase in display and line, cursor visibility
 * and cursor-keys mode, and OSC 52 clipboard writes ([onOsc52]). Full-screen programs (alternate screen, scroll
 * regions) belong to the platform terminal ([KitTerminalSlot]); this screen is the common grid's model.
 */
@Stable
public class KitTerminalScreen(
    /** The most lines kept, scrollback included (Dart `Terminal.maxLines`). */
    public val maxLines: Int = 1000, // F: third_party/xterm/lib/src/terminal.dart:74
) {
    /** Columns. */
    public var viewWidth: Int = 80 // F: third_party/xterm/lib/src/terminal.dart:116
        private set

    /** Rows on screen. */
    public var viewHeight: Int = 24 // F: third_party/xterm/lib/src/terminal.dart:118
        private set

    internal val lines: ArrayList<KitTerminalLine> = ArrayList()

    /** The buffer, scrollback first. */
    public val bufferLines: List<KitTerminalLine> get() = lines

    private var x = 0
    private var y = 0

    /** The cursor's column. */
    public val cursorX: Int get() = x.coerceIn(0, viewWidth - 1)

    /** The cursor's line in [bufferLines] (Dart `absoluteCursorY`). */
    public val absoluteCursorY: Int get() = y + lines.size - viewHeight

    /** DECTCEM: the cursor is drawn. */
    public var cursorVisible: Boolean = true
        private set

    /** DECCKM: arrows send SS3 sequences. */
    public var applicationCursorKeys: Boolean = false
        private set

    /** Called with the arguments of an OSC 52 write (the selection letters, then the payload). */
    public var onOsc52: ((List<String>) -> Unit)? = null

    /** Bumped on every change, so a view that reads it redraws. */
    public var version: Int by mutableIntStateOf(0)
        private set

    private var style = KitTerminalCellStyle()
    private var state = Parse.Normal
    private val sequence = StringBuilder()

    init {
        repeat(viewHeight) { lines += KitTerminalLine(viewWidth) }
    }

    /** Writes a program's output. */
    public fun write(data: String) {
        var i = 0
        while (i < data.length) {
            val pair = data[i].isHighSurrogate() && i + 1 < data.length && data[i + 1].isLowSurrogate()
            val last = if (pair) i + 1 else i
            feed(data.substring(i, last + 1))
            i = last + 1
        }
        version++
    }

    private fun feed(char: String) {
        val ch = char[0]
        when (state) {
            Parse.Normal -> normal(char, ch)
            Parse.Escape ->
                state =
                    when (ch) {
                        '[' -> {
                            sequence.clear()
                            Parse.Csi
                        }
                        ']' -> {
                            sequence.clear()
                            Parse.Osc
                        }
                        else -> Parse.Normal
                    }
            Parse.Csi -> {
                if (ch in '@'..'~') {
                    csi(ch, sequence.toString())
                    state = Parse.Normal
                } else {
                    sequence.append(ch)
                }
            }
            Parse.Osc ->
                when (ch) {
                    '\u0007' -> osc()
                    '\u001b' -> state = Parse.OscEscape
                    else -> sequence.append(char)
                }
            Parse.OscEscape ->
                if (ch == '\\') osc() else {
                    state = Parse.Osc
                    sequence.append(char)
                }
        }
    }

    private fun normal(char: String, ch: Char) {
        when (ch) {
            '\u001b' -> state = Parse.Escape
            '\r' -> x = 0
            '\n', '\u000b', '\u000c' -> index()
            '\b' -> x = maxOf(0, minOf(x, viewWidth) - 1)
            '\t' -> x = minOf(viewWidth - 1, (x / TAB + 1) * TAB)
            else -> if (ch >= ' ' && ch != '\u007f' && ch !in '\u0080'..'\u009f') writeChar(char)
        }
    }

    /** Dart `Buffer.writeChar`: a character past the last column wraps first. */
    private fun writeChar(char: String) {
        if (x >= viewWidth) {
            index()
            x = 0
            lines[absoluteCursorY].isWrapped = true
        }
        lines[absoluteCursorY].cells[x] = KitTerminalCell(char, style)
        if (x < viewWidth) x++
    }

    /** Dart `Buffer.index`: down a line, or a new line at the bottom (the top one goes to the scrollback). */
    private fun index() {
        if (y == viewHeight - 1) {
            lines.add(KitTerminalLine(viewWidth))
            while (lines.size > maxOf(maxLines, viewHeight)) lines.removeAt(0)
        } else {
            y++
        }
    }

    private fun osc() {
        state = Parse.Normal
        val parts = sequence.toString().split(';')
        if (parts.firstOrNull() == OSC_CLIPBOARD) onOsc52?.invoke(parts.drop(1))
    }

    private fun csi(final: Char, body: String) {
        val private = body.startsWith('?')
        val params = body.removePrefix("?").split(';').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(1, params.first())
        when (final) {
            'm' -> if (!private) sgr(params)
            'A' -> y = maxOf(0, y - n)
            'B' -> y = minOf(viewHeight - 1, y + n)
            'C' -> x = minOf(viewWidth - 1, x + n)
            'D' -> x = maxOf(0, minOf(x, viewWidth - 1) - n)
            'H', 'f' -> {
                y = (maxOf(1, params.first()) - 1).coerceIn(0, viewHeight - 1)
                x = (maxOf(1, params.getOrElse(1) { 1 }) - 1).coerceIn(0, viewWidth - 1)
            }
            'J' -> eraseDisplay(params.first())
            'K' -> eraseLine(params.first())
            'h', 'l' -> if (private) for (mode in params) decMode(mode, final == 'h')
        }
    }

    private fun decMode(mode: Int, on: Boolean) {
        when (mode) {
            1 -> applicationCursorKeys = on
            25 -> cursorVisible = on // F: third_party/xterm/lib/src/core/escape/parser.dart:1065
        }
    }

    private fun eraseLine(mode: Int) {
        val line = lines[absoluteCursorY]
        val from = if (mode == 0) minOf(x, viewWidth) else 0
        val to = if (mode == 1) minOf(x, viewWidth - 1) + 1 else viewWidth
        for (i in from until to) line.cells[i] = null
        if (mode == 2) line.isWrapped = false // F: third_party/xterm/lib/src/core/escape/parser.dart:935
    }

    private fun eraseDisplay(mode: Int) {
        val top = lines.size - viewHeight
        when (mode) {
            0 -> {
                eraseLine(0)
                for (i in absoluteCursorY + 1 until lines.size) lines[i] = KitTerminalLine(viewWidth)
            }
            1 -> {
                eraseLine(1)
                for (i in top until absoluteCursorY) lines[i] = KitTerminalLine(viewWidth)
            }
            2 -> for (i in top until lines.size) lines[i] = KitTerminalLine(viewWidth) // F: third_party/xterm/lib/src/core/escape/parser.dart:913
        }
    }

    /** Dart xterm `_csiHandleSgr` (third_party/xterm/lib/src/core/escape/parser.dart:469-711). */
    private fun sgr(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            val p = params[i]
            style =
                when (p) {
                    0 -> KitTerminalCellStyle()
                    1 -> style.copy(bold = true)
                    2 -> style.copy(faint = true) // F: third_party/xterm/lib/src/core/escape/parser.dart:486
                    3 -> style.copy(italic = true) // F: third_party/xterm/lib/src/core/escape/parser.dart:489
                    4 -> style.copy(underline = true) // F: third_party/xterm/lib/src/core/escape/parser.dart:492
                    7 -> style.copy(inverse = true) // F: third_party/xterm/lib/src/core/escape/parser.dart:509
                    21 -> style.copy(bold = false) // F: third_party/xterm/lib/src/core/escape/parser.dart:519
                    22 -> style.copy(faint = false) // F: third_party/xterm/lib/src/core/escape/parser.dart:522
                    23 -> style.copy(italic = false) // F: third_party/xterm/lib/src/core/escape/parser.dart:525
                    24 -> style.copy(underline = false) // F: third_party/xterm/lib/src/core/escape/parser.dart:528
                    27 -> style.copy(inverse = false) // F: third_party/xterm/lib/src/core/escape/parser.dart:534
                    in 30..37 -> style.copy(foreground = KitTerminalColor.Palette(p - 30)) // F: third_party/xterm/lib/src/core/escape/parser.dart:544,565 lib/ui/kit/kit_terminal_view.dart:763
                    39 -> style.copy(foreground = KitTerminalColor.Default) // F: third_party/xterm/lib/src/core/escape/parser.dart:599
                    in 40..47 -> style.copy(background = KitTerminalColor.Palette(p - 40)) // F: third_party/xterm/lib/src/core/escape/parser.dart:603,624
                    49 -> style.copy(background = KitTerminalColor.Default) // F: third_party/xterm/lib/src/core/escape/parser.dart:652
                    in 90..97 -> style.copy(foreground = KitTerminalColor.Palette(p - 90 + BRIGHT)) // F: third_party/xterm/lib/src/core/escape/parser.dart:656,677 lib/ui/kit/kit_terminal_view.dart:765
                    in 100..107 -> style.copy(background = KitTerminalColor.Palette(p - 100 + BRIGHT)) // F: third_party/xterm/lib/src/core/escape/parser.dart:681,702
                    38, 48 -> { // F: third_party/xterm/lib/src/core/escape/parser.dart:574,627
                        val (color, used) = extendedColor(params, i)
                        i += used
                        if (color == null) style
                        else if (p == 38) style.copy(foreground = color) // F: third_party/xterm/lib/src/core/escape/parser.dart:574
                        else style.copy(background = color)
                    }
                    else -> style
                }
            i++
        }
    }

    /** `38;5;n` and `38;2;r;g;b` (and the same after 48): the colour and how many arguments it took. */
    private fun extendedColor(params: List<Int>, at: Int): Pair<KitTerminalColor?, Int> =
        when (params.getOrNull(at + 1)) {
            5 -> params.getOrNull(at + 2)?.let { KitTerminalColor.Palette(it) } to 2 // F: third_party/xterm/lib/src/core/escape/parser.dart:592-596
            2 -> { // F: third_party/xterm/lib/src/core/escape/parser.dart:585
                val r = params.getOrNull(at + 2) // F: third_party/xterm/lib/src/core/escape/parser.dart:586
                val g = params.getOrNull(at + 3) // F: third_party/xterm/lib/src/core/escape/parser.dart:587
                val b = params.getOrNull(at + 4) // F: third_party/xterm/lib/src/core/escape/parser.dart:588-590
                (if (r != null && g != null && b != null) KitTerminalColor.Rgb(r, g, b) else null) to 4 // F: third_party/xterm/lib/src/core/escape/parser.dart:590
            }
            else -> null to 0
        }

    /**
     * Dart `Buffer.resize` (third_party/xterm/lib/src/core/buffer/buffer.dart:437-477): the height first (lines added
     * below or the cursor moved up), then the width, reflowing wrapped lines. The cursor keeps its screen row, as xterm's
     * does.
     */
    public fun resize(width: Int, height: Int) {
        val newWidth = maxOf(width, 1)
        val newHeight = maxOf(height, 1)
        if (newWidth == viewWidth && newHeight == viewHeight) return
        if (newHeight > viewHeight) {
            repeat(newHeight - viewHeight) {
                if (newHeight > lines.size) lines.add(KitTerminalLine(newWidth)) else y++
            }
        } else {
            repeat(viewHeight - newHeight) {
                if (y > newHeight - 1) y-- else lines.removeAt(lines.lastIndex)
            }
        }
        x = x.coerceIn(0, newWidth - 1)
        y = y.coerceIn(0, newHeight - 1)
        if (newWidth != viewWidth) {
            val result = reflow(lines, viewWidth, newWidth)
            while (result.size < newHeight) result.add(KitTerminalLine(newWidth))
            lines.clear()
            lines.addAll(result)
        }
        viewWidth = newWidth
        viewHeight = newHeight
        version++
    }

    private enum class Parse { Normal, Escape, Csi, Osc, OscEscape }

    private companion object {
        const val OSC_CLIPBOARD = "52"
        const val TAB = 8 // F: third_party/xterm/lib/src/core/tabs.dart:15
        const val BRIGHT = 8 // F: third_party/xterm/lib/src/ui/palette_builder.dart:33
    }
}

/**
 * Dart xterm `reflow` (third_party/xterm/lib/src/core/reflow.dart): each logical line (a line and the wrapped lines
 * after it) is laid out again at [newWidth]. Empty lines are kept as they are; a narrower width splits a line into
 * wrapped pieces, a wider one joins the pieces back.
 */
internal fun reflow(lines: List<KitTerminalLine>, oldWidth: Int, newWidth: Int): MutableList<KitTerminalLine> {
    val result = mutableListOf<KitTerminalLine>()
    var i = 0
    while (i < lines.size) {
        val reflow = LineReflow(oldWidth, newWidth)
        reflow.add(lines[i])
        while (i + 1 < lines.size && lines[i + 1].isWrapped) {
            i++
            reflow.add(lines[i])
        }
        result.addAll(reflow.finish())
        i++
    }
    for (line in result) line.resize(newWidth)
    return result
}

/** Dart xterm `_LineReflow`: one logical line being laid out again. */
private class LineReflow(private val oldWidth: Int, private val newWidth: Int) {
    private val lines = mutableListOf<KitTerminalLine>()
    private var builder = KitTerminalLine(newWidth)
    private var length = 0

    fun add(line: KitTerminalLine) {
        val trimmed = line.trimmedLength(oldWidth)
        if (trimmed == 0) {
            lines += line
            return
        }
        if (lines.isNotEmpty() || length != 0) {
            addPart(line, 0, trimmed)
            return
        }
        if (newWidth >= oldWidth) {
            // Reuse the line as the builder's buffer.
            builder = line
            length = trimmed
        } else {
            lines += line
            if (trimmed > newWidth) addPart(line, newWidth, trimmed)
        }
        line.resize(newWidth)
    }

    private fun addPart(line: KitTerminalLine, start: Int, to: Int) {
        var from = start
        var left = to - from
        while (left > 0) {
            val room = newWidth - length
            val copy = minOf(left, room)
            if (builder.width < newWidth) builder.resize(newWidth)
            for (k in 0 until copy) builder.cells[length + k] = line.cells.getOrNull(from + k)
            length += copy
            from += copy
            left -= copy
            if (copy >= room) lines += take(wrapped = lines.isNotEmpty())
        }
    }

    private fun take(wrapped: Boolean): KitTerminalLine {
        val result = builder
        result.isWrapped = wrapped
        builder = KitTerminalLine(newWidth)
        length = 0
        return result
    }

    fun finish(): List<KitTerminalLine> {
        if (length != 0) lines += take(wrapped = lines.isNotEmpty())
        return lines
    }
}
