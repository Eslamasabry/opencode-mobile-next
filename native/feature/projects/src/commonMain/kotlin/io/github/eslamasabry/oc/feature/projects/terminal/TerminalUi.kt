package io.github.eslamasabry.oc.feature.projects.terminal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.TerminalProcess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public enum class TerminalPhase {
    Opening,
    Unavailable,
    Ready,
    Connecting,
    Connected,
    Closed,
    Paused,
    Failed,
    Stale,
}

public data class TerminalUi(
    val directory: String,
    val serverName: String = "",
    val phase: TerminalPhase = TerminalPhase.Opening,
    val processes: ImmutableList<TerminalProcess> = persistentListOf(),
    val selected: String? = null,
    val transcript: String = "",
    val working: Boolean = false,
    val failure: Failure? = null,
    val rename: String? = null,
    val removing: ImmutableList<TerminalProcess> = persistentListOf(),
    val details: Boolean = false,
    val grid: io.github.eslamasabry.oc.core.common.PlatformViewHandle? = null,
    val accessibleMode: Boolean? = null,
    val selecting: Boolean = false,
    val clipboardFailed: Boolean = false,
) {
    override fun toString(): String =
        "TerminalUi(phase=$phase, processes=${processes.size}, transcript=${transcript.length} chars)"
}

public interface TerminalActions {
    public fun back()

    public fun shown(on: Boolean)

    public fun refresh()

    public fun create()

    public fun select(id: String)

    public fun send(value: String)

    public fun reconnect()

    public fun askRename()

    public fun rename(title: String?)

    public fun askRemove()

    public fun askRemoveEnded()

    public fun remove()

    public fun dismiss()

    public fun details()

    public fun copy()

    public fun paste()

    public fun accessibleMode(on: Boolean)

    public fun selectText()
}

/** Clipboard text stays in this process and is never logged or persisted. */
public interface TerminalClipboard {
    public fun copy(value: String): Boolean

    public fun read(): String?
}

/** Desktop product is deferred; no platform clipboard is opened by the gallery. */
@Inject
@ContributesBinding(AppScope::class)
public class NoTerminalClipboard : TerminalClipboard {
    override fun copy(value: String): Boolean = false

    override fun read(): String? = null
}

/** Platform renderer seam: only the kit casts the handle. Desktop uses readable mode. */
public interface TerminalGrid {
    public val view: io.github.eslamasabry.oc.core.common.PlatformViewHandle

    public fun append(text: String)

    public fun reset()

    public fun inputEnabled(on: Boolean)

    public fun selectedText(): String

    public fun selectText(on: Boolean)

    public fun paste(text: String)
}

public interface TerminalGrids {
    public fun create(send: (String) -> Unit, resize: (Int, Int) -> Unit): TerminalGrid?
}

@Inject
@ContributesBinding(AppScope::class)
public class NoTerminalGrids : TerminalGrids {
    override fun create(send: (String) -> Unit, resize: (Int, Int) -> Unit): TerminalGrid? = null
}
