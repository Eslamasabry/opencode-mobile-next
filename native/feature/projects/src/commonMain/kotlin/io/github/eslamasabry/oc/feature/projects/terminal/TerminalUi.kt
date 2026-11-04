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
    val details: TerminalDetails? = null,
    val control: Boolean = false,
    val alt: Boolean = false,
    val teaching: Boolean = false,
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

    public fun key(key: String)

    public fun dismissTip()
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

    public fun key(key: String, control: Boolean, alt: Boolean)

    public fun publishSize()
}

public interface TerminalGrids {
    public fun create(send: (String) -> Unit, resize: (Int, Int) -> Unit): TerminalGrid?
}

@Inject
@ContributesBinding(AppScope::class)
public class NoTerminalGrids : TerminalGrids {
    override fun create(send: (String) -> Unit, resize: (Int, Int) -> Unit): TerminalGrid? = null
}

/** Technical values cross into UI only after the app redactor has scrubbed them. */
public data class TerminalDetails(
    val command: io.github.eslamasabry.oc.core.common.Redacted,
    val directory: io.github.eslamasabry.oc.core.common.Redacted,
    val pid: io.github.eslamasabry.oc.core.common.Redacted,
    val exit: io.github.eslamasabry.oc.core.common.Redacted,
)

public interface TerminalPrivacy {
    public fun describe(process: TerminalProcess): TerminalDetails?
}

@Inject
@ContributesBinding(AppScope::class)
public class NoTerminalPrivacy : TerminalPrivacy {
    override fun describe(process: TerminalProcess): TerminalDetails? = null
}

public interface TerminalTipVisit {
    public val visible: kotlinx.coroutines.flow.Flow<Boolean>

    public suspend fun offer(blocked: () -> Boolean)

    public fun used()

    public fun dismiss()
}

public interface TerminalTeaching {
    public fun visit(): TerminalTipVisit
}

@Inject
@ContributesBinding(AppScope::class)
public class NoTerminalTeaching : TerminalTeaching {
    override fun visit(): TerminalTipVisit =
        object : TerminalTipVisit {
            override val visible: kotlinx.coroutines.flow.Flow<Boolean> =
                kotlinx.coroutines.flow.flowOf(false)

            override suspend fun offer(blocked: () -> Boolean) = Unit

            override fun used() = Unit

            override fun dismiss() = Unit
        }
}
