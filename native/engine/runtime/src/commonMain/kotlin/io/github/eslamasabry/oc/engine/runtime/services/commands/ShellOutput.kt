package io.github.eslamasabry.oc.engine.runtime.services.commands

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.engine.runtime.devservers.TerminalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** Output owned by one visible viewer. Reads use server byte cursors, never rendered string lengths. */
public data class ShellOutputState(
    val shell: ManagedShell? = null,
    val text: String = "",
    val cursor: Long = 0,
    val size: Long = 0,
    val trimmed: Boolean = false,
    val available: Boolean = true,
    val serverRestarted: Boolean = false,
    val refreshing: Boolean = false,
    val failure: Failure? = null,
) {
    public val hasMore: Boolean get() = cursor < size
    public val displayText: String get() = TerminalText.plain(text)
}

public class ShellOutput(
    private val shellId: String,
    port: ManagedShellPort,
    private val isCurrent: () -> Boolean,
) {
    private var port = port
    private var generation = 0
    private var closed = false
    private var identity: String? = null
    private var gate = Mutex()
    private val mutable = MutableStateFlow(ShellOutputState())
    public val state: StateFlow<ShellOutputState> = mutable.asStateFlow()

    /** Retire every in-flight response before adopting another live connection. */
    public fun bind(next: ManagedShellPort) {
        if (next === port) return
        port = next
        generation++
        gate = Mutex()
        mutable.update { it.copy(refreshing = false) }
    }

    public suspend fun refresh(): Outcome<Unit> {
        if (closed || !isCurrent()) return stale()
        val readGate = gate
        if (!readGate.tryLock()) return Outcome.Ok(Unit)
        val run = generation
        val repository = port
        fun current(): Boolean = !closed && generation == run && isCurrent()
        mutable.update { it.copy(refreshing = true) }
        try {
            val identityRead = repository.serverIdentity()
            if (!current()) return stale()
            if (identityRead is Outcome.Failed) return failed(identityRead.failure)
            val nextIdentity = (identityRead as Outcome.Ok).value
            val restarted = identity != null && nextIdentity != null && identity != nextIdentity
            identity = nextIdentity
            val info = repository.get(shellId)
            if (!current()) return stale()
            if (info is Outcome.Failed) return failed(info.failure)
            val shell = (info as Outcome.Ok).value
            mutable.update { it.copy(shell = shell ?: it.shell, available = shell != null, serverRestarted = it.serverRestarted || restarted) }
            if (shell == null) {
                mutable.update { it.copy(failure = null) }
                return Outcome.Ok(Unit)
            }
            val before = mutable.value
            var read = repository.output(shellId, before.cursor)
            if (!current()) return stale()
            if (read is Outcome.Failed) return failed(read.failure)
            var page = (read as Outcome.Ok).value
            val reset = page.cursor < before.cursor || page.size < before.cursor
            if (reset) {
                read = repository.output(shellId, 0)
                if (!current()) return stale()
                if (read is Outcome.Failed) return failed(read.failure)
                page = (read as Outcome.Ok).value
            }
            val base = if (reset) 0 else before.cursor
            if (page.cursor < base || page.size < 0 || page.cursor > page.size || (page.cursor == base && page.text.isNotEmpty())) {
                return failed(Failure.UnreadableReply())
            }
            var text = if (reset) "" else before.text
            if (page.cursor > base) text += page.text
            var trimmed = !reset && before.trimmed
            if (text.length > MAX_CHARACTERS) {
                var start = text.length - MAX_CHARACTERS
                if (text[start].isLowSurrogate()) start++
                text = text.substring(start)
                trimmed = true
            }
            mutable.update { it.copy(text = text, cursor = page.cursor, size = page.size, trimmed = trimmed, failure = null) }
            return Outcome.Ok(Unit)
        } finally {
            if (current()) mutable.update { it.copy(refreshing = false) }
            readGate.unlock()
        }
    }

    public fun close() {
        closed = true
        generation++
    }

    private fun failed(failure: Failure): Outcome<Unit> {
        mutable.update { it.copy(failure = failure) }
        return Outcome.Failed(failure)
    }

    private fun stale(): Outcome<Unit> = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    public companion object {
        public const val MAX_CHARACTERS: Int = 128 * 1024
    }
}
