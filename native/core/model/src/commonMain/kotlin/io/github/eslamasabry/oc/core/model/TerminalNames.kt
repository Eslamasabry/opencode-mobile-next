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

private val serverDefaultTitle = Regex("^Terminal [A-Za-z0-9]{3,}$")

/** The person's own name for [this] terminal, or null when it was never renamed. */
public fun TerminalProcess.givenName(): String? {
    val text = title.trim()
    if (text.isEmpty() || text.all { it.isDigit() || it == ' ' }) return null
    // "Terminal" or "Terminal <digits>" is the app's own old default, never a person's name.
    if (appDefaultTitle.matches(text)) return null
    // A server default carries a short random id with at least one letter; "Terminal 2" is a person's own.
    if (serverDefaultTitle.matches(text) && text.drop("Terminal ".length).any { !it.isDigit() })
        return null
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
