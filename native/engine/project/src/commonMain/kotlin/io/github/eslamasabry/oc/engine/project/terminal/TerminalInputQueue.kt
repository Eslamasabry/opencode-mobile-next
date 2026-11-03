package io.github.eslamasabry.oc.engine.project.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Serialises terminal keystrokes into one ordered write per flush (Flutter `TerminalInputQueue`). Every keystroke used
 * to reach the channel as its own send; under fast typing sends interleaved and the PTY echo arrived out of order or
 * twice. The queue appends to one FIFO buffer and flushes it as one write on the next dispatch of [scope] (the
 * terminal's serial lane), so bytes leave exactly in the order typed. Confined to that lane.
 */
public class TerminalInputQueue(private val scope: CoroutineScope, private val send: (String) -> Unit) {
    private val buffer = StringBuilder()
    private var flushScheduled = false
    private var closed = false

    public val hasPending: Boolean get() = buffer.isNotEmpty()

    public fun write(value: String) {
        if (closed || value.isEmpty()) return
        buffer.append(value)
        if (flushScheduled) return
        flushScheduled = true
        scope.launch { flush() }
    }

    /** Sends everything queued so far as one write, in order. An empty flush sends nothing. */
    public fun flush() {
        flushScheduled = false
        if (closed || buffer.isEmpty()) return
        val pending = buffer.toString()
        buffer.clear()
        send(pending)
    }

    /** Drops what has not been sent: the channel went away. */
    public fun discard() {
        buffer.clear()
    }

    public fun close() {
        closed = true
        buffer.clear()
    }
}
