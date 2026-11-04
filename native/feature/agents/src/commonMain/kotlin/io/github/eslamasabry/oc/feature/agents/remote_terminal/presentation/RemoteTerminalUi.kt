package io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation

public data class RemoteTerminalItem(val id: String, val title: String, val running: Boolean)

public enum class RemoteTerminalIssue {
    Unavailable,
    Failed,
    InputUncertain,
}

public data class RemoteTerminalUi(
    val available: Boolean = false,
    val busy: Boolean = false,
    val terminals: List<RemoteTerminalItem> = emptyList(),
    val selected: RemoteTerminalItem? = null,
    val connected: Boolean = false,
    val transcript: String = "",
    val issue: RemoteTerminalIssue? = null,
    val ending: Boolean = false,
    val devServerPort: Int? = null,
)

public interface RemoteTerminalActions {
    public fun refresh()

    public fun create()

    public fun connect(id: String)

    public fun disconnect()

    public fun showList()

    public fun send(value: String)

    public fun interrupt()

    public fun endOfInput()

    public fun escape()

    public fun tab()

    public fun askEnd()

    public fun cancelEnd()

    public fun confirmEnd()

    public fun back()

    public fun openDevPreview() {}
}
