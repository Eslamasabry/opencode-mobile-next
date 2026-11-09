package io.github.eslamasabry.oc.engine.runtime.terminal

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.TerminalProcess
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the terminal list opens on the rows it last listed, then the live list
 * replaces them in place. The app binds this to its last-known store; without a binding the list loads as before.
 */
public interface TerminalLastKnown {
    public fun peek(ref: ConversationRef): KeptTerminals?

    public suspend fun read(ref: ConversationRef): KeptTerminals?

    public suspend fun keep(ref: ConversationRef, kept: KeptTerminals)
}

/** The terminal rows (id, title, command, folder, status). Launch arguments are never kept. */
@Serializable
public data class KeptTerminals(val rows: List<Row> = emptyList()) {
    @Serializable
    public data class Row(
        val id: String,
        val title: String,
        val command: String,
        val directory: String,
        val running: Boolean,
        val pid: Int,
        val exitCode: Int? = null,
    )

    public fun toProcesses(): kotlinx.collections.immutable.ImmutableList<TerminalProcess> =
        rows.map { TerminalProcess(it.id, it.title, it.command, persistentListOf(), it.directory, it.running, it.pid, it.exitCode) }
            .toImmutableList()

    public companion object {
        public const val MAX_ROWS: Int = 100

        public fun of(processes: List<TerminalProcess>): KeptTerminals =
            KeptTerminals(
                processes.take(MAX_ROWS).map {
                    Row(it.id, it.title, it.command, it.directory, it.running, it.pid, it.exitCode)
                }
            )
    }
}
