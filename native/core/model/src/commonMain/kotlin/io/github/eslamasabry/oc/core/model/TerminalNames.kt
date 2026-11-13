package io.github.eslamasabry.oc.core.model

/**
 * What a terminal is called to the person: the name they gave it, or else a plain number in the order the
 * terminals were opened. The server's own default title ("Terminal <random id>") is not a name.
 */
public sealed interface TerminalName {
    public data class Given(val text: String) : TerminalName

    /** The [number]th terminal; the first one is just "Terminal". */
    public data class Plain(val number: Int) : TerminalName
}

private val appDefaultTitle = Regex("^Terminal( [0-9]+)?$")

/**
 * The person's own name for [this] terminal, or null when it was never renamed. Not a name: an empty title, digits
 * only, the app's old default ("Terminal", "Terminal <digits>"), and the server's default, which is exactly
 * "Terminal " plus the last four characters of this terminal's own id (OpenCode's pty create stores
 * `title || "Terminal " + id.slice(-4)`, read from the shipped CLI binaries, 1 and 2). Anything else is kept.
 */
public fun TerminalProcess.givenName(): String? {
    val text = title.trim()
    if (text.isEmpty() || text.all { it.isDigit() || it == ' ' }) return null
    if (appDefaultTitle.matches(text)) return null
    if (text == "Terminal " + id.takeLast(4)) return null
    return text
}

/** The name of the terminal [id] among [processes], which are in the order they were opened. */
public fun terminalNameOf(processes: List<TerminalProcess>, id: String): TerminalName {
    val process = processes.firstOrNull { it.id == id }
    process?.givenName()?.let {
        return TerminalName.Given(it)
    }
    return TerminalName.Plain(processes.indexOfFirst { it.id == id }.coerceAtLeast(0) + 1)
}

public fun terminalNameOf(processes: List<TerminalProcess>, process: TerminalProcess): TerminalName =
    terminalNameOf(processes, process.id)
