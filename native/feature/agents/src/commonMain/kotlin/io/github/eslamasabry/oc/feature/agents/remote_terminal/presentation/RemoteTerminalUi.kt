package io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation

import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey

public data class RemoteTerminalItem(
    val id: String,
    val title: String,
    val running: Boolean,
    val process: TerminalProcess? = null,
)

public enum class RemoteTerminalIssue {
    Unavailable,
    Failed,
    InputUncertain,
    RerunUncertain,
}

public data class RemoteTerminalUi(
    val available: Boolean = false,
    val builtInAvailable: Boolean = false,
    val serverName: String = "",
    val platformView: PlatformViewHandle? = null,
    val accessible: Boolean? = null,
    val control: Boolean = false,
    val alt: Boolean = false,
    val canRerun: Boolean = false,
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
    public fun key(key: TerminalKey)

    public fun setAccessible(enabled: Boolean)

    public fun builtIn()

    public fun rerun()

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
