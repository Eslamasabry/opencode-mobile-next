package io.github.eslamasabry.oc.ui.kit.content.terminal

public typealias TerminalText = io.github.eslamasabry.oc.core.common.terminal.TerminalText

public typealias TerminalRole = io.github.eslamasabry.oc.core.common.terminal.TerminalRole

public typealias TerminalSpan = io.github.eslamasabry.oc.core.common.terminal.TerminalSpan

public typealias TerminalBarKey = io.github.eslamasabry.oc.core.common.terminal.TerminalBarKey

/**
 * One piece of an ANSI-coloured output line as the output form draws it: [color] is the ANSI colour (0-7: black, red,
 * green, yellow, blue, magenta, cyan, white), [bright] picks the bright row of the palette, null colour is the view's
 * own text colour; [faint] draws in `text3`.
 */
public data class TerminalAnsiSpan(
    val text: String,
    val color: Int? = null,
    val bright: Boolean = false,
    val bold: Boolean = false,
    val faint: Boolean = false,
)

/**
 * The output form's ANSI colours (Dart `KitTerminalText._ansi`, lib/ui/kit/kit_terminal_view.dart): unlike
 * [TerminalText.line], which folds the colour into a role, the colour is kept so the view draws it from the terminal
 * palette ([kitTerminalTheme]), as Flutter does with `KitTerminalView.themeOf`.
 */
public object TerminalAnsi {
    private val SGR = Regex("""\u001B\[([0-9;]*)m""")

    /**
     * Whether [line] keeps its own colours: it has an escape and no secret (Dart `line`: `\x1B[` and not
     * `containsSecret`). A line with a secret is drawn masked and tinted by [TerminalText.line].
     */
    public fun keepsColours(line: String, redact: (String) -> String): Boolean {
        val stripped = TerminalText.strip(line)
        return "\u001B[" in line && redact(stripped) == stripped
    }

    /** [line]'s pieces, each escape-free and masked, with the SGR state it was printed in. */
    public fun spans(line: String, redact: (String) -> String): List<TerminalAnsiSpan> {
        val spans = mutableListOf<TerminalAnsiSpan>()
        var color: Int? = null
        var bright = false
        var bold = false
        var faint = false
        var at = 0
        fun emit(text: String) {
            val clean = redact(TerminalText.strip(text))
            if (clean.isNotEmpty()) spans += TerminalAnsiSpan(clean, color, bright, bold, faint)
        }
        for (m in SGR.findAll(line)) {
            emit(line.substring(at, m.range.first))
            at = m.range.last + 1
            val codes =
                if (m.groupValues[1].isEmpty()) listOf(0)
                else m.groupValues[1].split(';').map { it.toIntOrNull() ?: 0 }
            var i = 0
            while (i < codes.size) {
                val code = codes[i]
                when {
                    code == 0 -> {
                        color = null
                        bold = false
                        faint = false
                    }
                    code == 1 -> bold = true
                    code == 2 -> faint = true // F: lib/ui/kit/kit_terminal_view.dart:757
                    code == 22 -> { // F: lib/ui/kit/kit_terminal_view.dart:759
                        bold = false
                        faint = false
                    }
                    code in 30..37 -> { // F: lib/ui/kit/kit_terminal_view.dart:762
                        color = code - 30 // F: lib/ui/kit/kit_terminal_view.dart:763
                        bright = false
                    }
                    code in 90..97 -> { // F: lib/ui/kit/kit_terminal_view.dart:764
                        color = code - 90 // F: lib/ui/kit/kit_terminal_view.dart:765
                        bright = true
                    }
                    code == 39 -> color = null // F: lib/ui/kit/kit_terminal_view.dart:766
                    // 256-colour and true-colour: skip their arguments.
                    code == 38 || code == 48 -> // F: lib/ui/kit/kit_terminal_view.dart:768
                        i += if (i + 1 < codes.size && codes[i + 1] == 5) 2 else 4 // F: lib/ui/kit/kit_terminal_view.dart:770
                }
                i++
            }
        }
        emit(line.substring(at))
        return spans.ifEmpty { listOf(TerminalAnsiSpan("")) }
    }
}
