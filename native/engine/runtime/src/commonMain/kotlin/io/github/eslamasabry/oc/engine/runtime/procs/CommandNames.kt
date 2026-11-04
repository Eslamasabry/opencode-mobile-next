package io.github.eslamasabry.oc.engine.runtime.procs

/**
 * Port of lib/domain/ansi_text.dart (`withoutTerminalCodes`), lib/state/detected_dev_servers.dart
 * (`DetectedServer.nameFor`) and lib/ui/screens/running_work_sheet.dart (`shortCommand`): short names for command lines
 * and plain text from terminal output. Names are data (program names), never sentences.
 */
public object CommandNames {
    private val SPACES = Regex("""\s+""")
    private val RUNNERS = setOf("npm", "yarn", "pnpm", "bun", "npx", "uv", "cargo", "go")
    private val INTERPRETERS = setOf(
        "node", "python", "python3", "ruby", "bash", "sh", "php", "java", "deno", "dart", "flutter",
    )

    /** A dev server's short name from its command line: "vite", "http.server", "npm run dev", "caddy"; "" for blank. */
    public fun serverName(command: String): String {
        val tokens = command.trim().split(SPACES).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        fun base(t: String) = t.split('/').last()
        if (base(tokens.first()) in RUNNERS) return tokens.take(3).joinToString(" ")
        val head = base(tokens.first())
        if (head.startsWith("python") && tokens.size > 2 && tokens[1] == "-m") return tokens[2]
        var i = 0
        while (i < tokens.size - 1 && (base(tokens[i]) in INTERPRETERS || tokens[i].startsWith("-"))) i++
        val name = base(tokens[i])
        if (name.isEmpty()) return tokens.take(3).joinToString(" ")
        return if (name.length > 40) name.substring(0, 40) else name
    }

    private val JOIN = Regex("""\s+(&&|\|\||;|\|)\s+""")
    private val ASSIGNMENT = Regex("""^[A-Za-z_][A-Za-z0-9_]*=""")

    /**
     * A command named by what it runs, not where it lives: leading `VAR=value` words go and the program's folder is
     * dropped in each part of a `&&`/`||`/`;`/`|` chain; arguments are left alone.
     */
    public fun shortCommand(command: String): String {
        val trimmed = command.trim()
        val parts = trimmed.split(JOIN)
        val joins = JOIN.findAll(trimmed).map { it.groupValues[1] }.toList()
        val out = StringBuilder()
        for ((i, part) in parts.withIndex()) {
            val words = part.split(SPACES).filter { it.isNotEmpty() }.toMutableList()
            while (words.size > 1 && ASSIGNMENT.containsMatchIn(words.first())) words.removeAt(0)
            if (words.isNotEmpty() && words.first().contains('/')) {
                val name = words.first().split('/').last()
                if (name.isNotEmpty()) words[0] = name
            }
            if (i > 0) out.append(' ').append(joins[i - 1]).append(' ')
            out.append(words.joinToString(" "))
        }
        val result = out.toString().trim()
        return result.ifEmpty { command }
    }

    private val OSC = Regex("""\x1B\][^\x07\x1B]*(?:\x07|\x1B\\)?""")
    private val CSI = Regex("""\x1B\[[0-?]*[ -/]*[@-~]""")
    private val OTHER = Regex("""\x1B[@-Z\\-_()#][0-9A-Za-z]?""")
    private val CONTROL = Regex("""[\x00-\x08\x0B\x0C\x0E-\x1A\x1C-\x1F\x7F]""")

    /**
     * [input] without escape sequences (colour, cursor, title). Line endings become `\n`; a lone carriage return (a
     * progress bar redrawing itself) becomes a new line so each state stays readable; other control characters go.
     */
    public fun withoutTerminalCodes(input: String): String {
        if (input.isEmpty()) return input
        return input
            .replace(OSC, "")
            .replace(CSI, "")
            .replace(OTHER, "")
            .replace("\u001B", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(CONTROL, "")
    }
}
