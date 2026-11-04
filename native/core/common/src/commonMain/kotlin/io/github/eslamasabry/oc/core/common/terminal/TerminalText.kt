package io.github.eslamasabry.oc.core.common.terminal

/**
 * The theme role a piece of terminal text is drawn in (colours come from roles only; never danger
 * or attention).
 */
public enum class TerminalRole {
    Text1,
    Text2,
    Text3,
    Success,
    Accent,
    CodeType,
    CodeString,
}

/** One drawn piece of a line: [role] null = the view's default text colour. */
public data class TerminalSpan(
    val text: String,
    val role: TerminalRole? = null,
    val weight: Int = 400,
)

/** The keys of the terminal key bar that send control bytes. */
public enum class TerminalBarKey {
    Interrupt,
    EndOfInput,
    Escape,
    Tab,
}

/**
 * Terminal text as spans (B-XPLAT-011), port of Dart `KitTerminalText` and `sendTerminalBarKey`
 * (`lib/ui/kit/kit_terminal_view.dart`): pure functions, no Compose. Errors are text1 semibold
 * (never red), warnings text2 (never amber), passes success; an ANSI red maps to text1. Secrets are
 * found in the text without its escapes, so a value coloured apart from its name is still masked; a
 * line holding one drops its colours. [redact] is the app's one redactor (`Redactor.redact`).
 */
public object TerminalText {
    private val ANSI_ESCAPE = Regex("""\u001B(?:\[[0-?]*[ -/]*[@-~]|[@-Z\\-_])""")
    private val SGR = Regex("""\u001B\[([0-9;]*)m""")
    private val COMMAND_TOKEN =
        Regex(""""(?:[^"\\]|\\.)*"|'[^']*'|&&|\|\||[|;<>]|\s+|[^\s|;<>"']+""")
    private val OPERATORS = setOf("&&", "||", "|", ";", "<", ">")
    private val ASSIGNMENT = Regex("""^[A-Za-z_][A-Za-z0-9_]*=""")
    private val FAILURE =
        Regex(
            """(?<!(?:\b\d{1,9}|\bno) )\b(error|errors|failed|failure|fatal|exception|traceback|panic)\b|✗|✖|\bFAIL\b""",
            RegexOption.IGNORE_CASE,
        )
    private val WARNING = Regex("""\bwarn(ing|ings)?\b|⚠""", RegexOption.IGNORE_CASE)
    private val PASS =
        Regex(
            """\b(all tests passed|passed|succeeded|successfully|no issues found)\b|✓|✔""",
            RegexOption.IGNORE_CASE,
        )
    private val TEST_PROGRESS = Regex("""^(\d+:\d+ )(\+\d+)( ~\d+)?( -\d+)?(:)""")
    private const val SEMIBOLD = 600
    private const val BOLD = 700

    /** The bytes a bar key sends: Ctrl-C 0x03, Ctrl-D 0x04; with Alt, ESC first. */
    public fun keyBytes(key: TerminalBarKey, alt: Boolean = false): String {
        val bytes =
            when (key) {
                TerminalBarKey.Interrupt -> "\u0003"
                TerminalBarKey.EndOfInput -> "\u0004"
                TerminalBarKey.Escape -> "\u001b"
                TerminalBarKey.Tab -> "\t"
            }
        return if (alt) "\u001b" + bytes else bytes
    }

    /** ANSI escape sequences removed. */
    public fun strip(text: String): String = ANSI_ESCAPE.replace(text, "")

    /** Only the selected part of [text] ([start] until [end]); empty when nothing is selected. */
    public fun selectedText(text: String, start: Int, end: Int): String {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        return text.substring(a, b)
    }

    /**
     * "$ " then the program (its folder dimmed), flags, strings and operators, each in its own
     * role.
     */
    public fun command(command: String): List<TerminalSpan> {
        val spans = mutableListOf(TerminalSpan("$ ", TerminalRole.Text3))
        var expectProgram = true
        for (m in COMMAND_TOKEN.findAll(command)) {
            val token = m.value
            when {
                token.isBlank() -> spans += TerminalSpan(token)
                token in OPERATORS -> {
                    spans += TerminalSpan(token, TerminalRole.Text3, BOLD)
                    expectProgram = true
                }
                token.startsWith('"') || token.startsWith('\'') -> {
                    spans += TerminalSpan(token, TerminalRole.CodeString)
                    expectProgram = false
                }
                expectProgram && ASSIGNMENT.containsMatchIn(token) ->
                    spans += TerminalSpan(token, TerminalRole.Text3)
                expectProgram -> {
                    val slash = token.lastIndexOf('/')
                    val folder = slash > 0 && slash < token.length - 1
                    if (folder)
                        spans += TerminalSpan(token.substring(0, slash + 1), TerminalRole.Text3)
                    spans +=
                        TerminalSpan(
                            if (folder) token.substring(slash + 1) else token,
                            TerminalRole.Accent,
                            BOLD,
                        )
                    expectProgram = false
                }
                token.startsWith('-') -> spans += TerminalSpan(token, TerminalRole.CodeType)
                else -> spans += TerminalSpan(token)
            }
        }
        return spans
    }

    /**
     * One output line, secrets masked: its own ANSI colours when it has them, otherwise a tint from
     * what it says.
     */
    public fun line(line: String, redact: (String) -> String): List<TerminalSpan> {
        val stripped = strip(line)
        if ("\u001B[" in line && redact(stripped) == stripped) return ansi(line, redact)
        val plain = redact(stripped)
        TEST_PROGRESS.find(plain)?.let { p ->
            return buildList {
                add(TerminalSpan(p.groupValues[1], TerminalRole.Text3))
                add(TerminalSpan(p.groupValues[2], TerminalRole.Success))
                if (p.groupValues[3].isNotEmpty())
                    add(TerminalSpan(p.groupValues[3], TerminalRole.Text2))
                if (p.groupValues[4].isNotEmpty())
                    add(TerminalSpan(p.groupValues[4], TerminalRole.Text1, SEMIBOLD))
                add(TerminalSpan(p.groupValues[5]))
                addAll(tinted(plain.substring(p.range.last + 1)))
            }
        }
        return tinted(plain)
    }

    private fun tinted(text: String): List<TerminalSpan> =
        listOf(
            when {
                FAILURE.containsMatchIn(text) -> TerminalSpan(text, TerminalRole.Text1, SEMIBOLD)
                WARNING.containsMatchIn(text) -> TerminalSpan(text, TerminalRole.Text2, SEMIBOLD)
                PASS.containsMatchIn(text) -> TerminalSpan(text, TerminalRole.Success)
                else -> TerminalSpan(text)
            }
        )

    /**
     * The role an ANSI colour (0-7 normal, 8-15 bright) maps to: red reads as text1, never as an
     * alarm colour.
     */
    public fun ansiRole(index: Int): TerminalRole =
        when (index % 8) {
            0 -> TerminalRole.Text3
            1 -> TerminalRole.Text1
            2 -> TerminalRole.Success
            3 -> TerminalRole.Text2
            4,
            5,
            6 -> TerminalRole.Accent
            else -> TerminalRole.Text1
        }

    private fun ansi(line: String, redact: (String) -> String): List<TerminalSpan> {
        val spans = mutableListOf<TerminalSpan>()
        var role: TerminalRole? = null
        var bold = false
        var faint = false
        var at = 0
        fun emit(text: String) {
            val clean = redact(strip(text))
            if (clean.isNotEmpty())
                spans +=
                    TerminalSpan(
                        clean,
                        if (faint) TerminalRole.Text3 else role,
                        if (bold) BOLD else 400,
                    )
        }
        for (m in SGR.findAll(line)) {
            emit(line.substring(at, m.range.first))
            at = m.range.last + 1
            val codes =
                if (m.groupValues[1].isEmpty()) listOf(0)
                else m.groupValues[1].split(';').map { it.toIntOrNull() ?: 0 }
            var i = 0
            while (i < codes.size) {
                val c = codes[i]
                when {
                    c == 0 -> {
                        role = null
                        bold = false
                        faint = false
                    }
                    c == 1 -> bold = true
                    c == 2 -> faint = true
                    c == 22 -> {
                        bold = false
                        faint = false
                    }
                    c in 30..37 -> role = ansiRole(c - 30)
                    c in 90..97 -> role = ansiRole(c - 90 + 8)
                    c == 39 -> role = null
                    c == 38 || c == 48 -> i += if (i + 1 < codes.size && codes[i + 1] == 5) 2 else 4
                }
                i++
            }
        }
        emit(line.substring(at))
        return spans.ifEmpty { listOf(TerminalSpan("")) }
    }
}
