package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A command shell the server runs for a session (Flutter `ManagedShell`). */
public data class ManagedShell(
    val id: String,
    val command: String,
    val running: Boolean,
    val sessionId: String? = null,
    val directory: String? = null,
    val exitCode: Int? = null,
)

/** One page of a shell's output. [cursor] and [size] are the server's absolute BYTE offsets, never string lengths. */
public data class ManagedShellOutput(val text: String, val cursor: Long, val size: Long, val truncated: Boolean)

/** The calls the output viewer needs (Flutter `ManagedShellGateway`, read side). Bound by the protocol layer. */
public interface ManagedShellOutputPort {
    /** Who the server is; a different identity after reconnect is the evidence of a restart. Null when unknown. */
    public suspend fun serverIdentity(): Outcome<String?>

    /** The shell, or null when the server no longer has it. */
    public suspend fun shell(id: String): Outcome<ManagedShell?>

    public suspend fun readOutput(id: String, cursor: Long, limit: Int = 65_536): Outcome<ManagedShellOutput>
}

/** Why the last refresh did not apply. */
public sealed interface ShellOutputError {
    /** The page's cursors contradict each other (Flutter `FormatException('Invalid command output cursor')`). */
    public data object MalformedPage : ShellOutputError

    public data class Failed(val failure: Failure) : ShellOutputError
}

public data class ShellOutputState(
    val shell: ManagedShell,
    /** The bounded raw tail; [displayText] is what a viewer draws. */
    val text: String = "",
    val cursor: Long = 0,
    val size: Long = 0,
    val trimmed: Boolean = false,
    val available: Boolean = true,
    val serverRestarted: Boolean = false,
    val refreshing: Boolean = false,
    val error: ShellOutputError? = null,
) {
    val hasMore: Boolean get() = cursor < size

    /** [text] without terminal escapes (OSC and CSI) and with every line break as `\n`. */
    val displayText: String get() = stripTerminalEscapes(text)
}

/** OSC (`ESC ] … BEL|ST`) and CSI sequences out; CRLF and lone CR become LF (Flutter `displayText`). */
public fun stripTerminalEscapes(text: String): String =
    text
        .replace(OSC, "")
        .replace(CSI, "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

private val OSC = Regex("\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)")
private val CSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

/**
 * State for one visible output viewer (Flutter `ShellOutputController`). It owns no polling and nothing persisted: the
 * route decides when to [refresh] and drops the controller on close ([dispose]). Confined to its caller's serial lane:
 * call every member from one dispatcher.
 *
 * - Overlapping refreshes coalesce: a refresh while one runs returns at once.
 * - A response from a gateway replaced by [bind], or one arriving after [dispose], is discarded.
 * - A failed or malformed read keeps the output already shown; the next good read clears the error.
 */
public class ShellOutputController(gateway: ManagedShellOutputPort, shell: ManagedShell) {
    private var gateway: ManagedShellOutputPort = gateway
    private val mutableState = MutableStateFlow(ShellOutputState(shell = shell))
    public val state: StateFlow<ShellOutputState> = mutableState.asStateFlow()

    private var serverIdentity: String? = null
    private var readIdentity = false
    private var disposed = false
    private var generation = 0

    /** Points the viewer at a new connection's gateway; anything the old one still answers is ignored. */
    public fun bind(next: ManagedShellOutputPort) {
        if (next === gateway) return
        gateway = next
        generation++
        mutableState.value = mutableState.value.copy(refreshing = false)
    }

    private fun current(generation: Int): Boolean = !disposed && generation == this.generation

    public suspend fun refresh(reconcileServer: Boolean = false) {
        if (disposed || mutableState.value.refreshing) return
        val generation = generation
        val repository = gateway
        update { copy(refreshing = true) }
        var next = mutableState.value
        try {
            next = read(repository, generation, next, reconcileServer) ?: return
        } finally {
            if (current(generation)) mutableState.value = next.copy(refreshing = false)
        }
    }

    /** Null when the generation retired mid-read: nothing may be written. */
    private suspend fun read(
        repository: ManagedShellOutputPort,
        generation: Int,
        start: ShellOutputState,
        reconcileServer: Boolean,
    ): ShellOutputState? {
        var state = start
        if (reconcileServer || !readIdentity) {
            val identity =
                when (val read = repository.serverIdentity()) {
                    is Outcome.Ok -> read.value
                    is Outcome.Failed -> return if (current(generation)) state.failed(read.failure) else null
                }
            if (!current(generation)) return null
            val known = serverIdentity
            if (known != null && identity != null && identity != known) state = state.copy(serverRestarted = true)
            serverIdentity = identity
            readIdentity = true
        }
        val shell =
            when (val read = repository.shell(state.shell.id)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return if (current(generation)) state.failed(read.failure) else null
            }
        if (!current(generation)) return null
        if (shell == null) return state.copy(available = false, error = null)
        state = state.copy(shell = shell, available = true)
        val cursor = state.cursor
        var page =
            when (val read = repository.readOutput(shell.id, cursor)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return if (current(generation)) state.failed(read.failure) else null
            }
        if (!current(generation)) return null
        var reset = false
        if (page.cursor < cursor || page.size < cursor) {
            // A reset or truncated capture must not duplicate old output or keep asking for an obsolete offset.
            page =
                when (val read = repository.readOutput(shell.id, 0)) {
                    is Outcome.Ok -> read.value
                    is Outcome.Failed -> return if (current(generation)) state.failed(read.failure) else null
                }
            if (!current(generation)) return null
            reset = true
        }
        val base = if (reset) 0L else cursor
        if (page.cursor < base || page.cursor > page.size || (page.cursor == base && page.text.isNotEmpty())) {
            return state.copy(error = ShellOutputError.MalformedPage)
        }
        var text = if (reset) "" else state.text
        var trimmed = if (reset) false else state.trimmed
        if (page.cursor > base) text += page.text
        if (text.length > MAX_CHARACTERS) {
            var from = text.length - MAX_CHARACTERS
            // Never start the bounded tail on the second half of a surrogate pair.
            if (text[from].isLowSurrogate()) from++
            text = text.substring(from)
            trimmed = true
        }
        return state.copy(text = text, trimmed = trimmed, cursor = page.cursor, size = page.size, error = null)
    }

    private fun ShellOutputState.failed(failure: Failure): ShellOutputState =
        copy(error = ShellOutputError.Failed(failure))

    private inline fun update(block: ShellOutputState.() -> ShellOutputState) {
        mutableState.value = mutableState.value.block()
    }

    /** The viewer closed: later results change nothing. */
    public fun dispose() {
        disposed = true
    }

    public companion object {
        /** The rendered tail is bounded to this many UTF-16 units. */
        public const val MAX_CHARACTERS: Int = 128 * 1024
    }
}
