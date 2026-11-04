package io.github.eslamasabry.oc.engine.runtime.terminal

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.TerminalGateway
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public class RemoteTerminalTarget(
    public val gateway: TerminalGateway,
    public val current: () -> Boolean,
)

public fun interface RemoteTerminalSource {
    public suspend fun resolve(ref: ConversationRef): Outcome<RemoteTerminalTarget>
}

public data class RemoteTerminalState(
    val loading: Boolean = true,
    val terminals: ImmutableList<TerminalProcess> = persistentListOf(),
    val selected: TerminalProcess? = null,
    val transcript: String = "",
    val connected: Boolean = false,
    val failure: Failure? = null,
    val working: Boolean = false,
)

/**
 * One screen's PTY owner. Commands revalidate the exact graph/project; no reconnect ever resends
 * input.
 */
public class RemoteTerminals(
    private val ref: ConversationRef,
    private val source: RemoteTerminalSource,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    private val lane = Mutex()
    private val local = MutableStateFlow(RemoteTerminalState())
    public val state: StateFlow<RemoteTerminalState> = local.asStateFlow()
    private var channel: TerminalChannel? = null
    private var reader: Job? = null
    private var target: RemoteTerminalTarget? = null
    private var generation = 0

    public suspend fun refresh(): Unit = lane.withLock {
        local.update { it.copy(loading = true, failure = null) }
        when (val live = source.resolve(ref)) {
            is Outcome.Failed -> fail(live.failure)
            is Outcome.Ok ->
                when (val listed = live.value.gateway.listTerminals()) {
                    is Outcome.Failed -> fail(listed.failure)
                    is Outcome.Ok ->
                        if (live.value.current())
                            local.update { it.copy(loading = false, terminals = listed.value) }
                        else fail(Failure.Refused(RefusalReason.StaleScope))
                }
        }
    }

    public suspend fun create(): Unit = lane.withLock {
        val live = resolve() ?: return@withLock
        local.update { it.copy(working = true, failure = null) }
        when (val created = live.gateway.createTerminal()) {
            is Outcome.Failed -> fail(created.failure)
            is Outcome.Ok -> {
                if (!live.current()) {
                    fail(Failure.Refused(RefusalReason.StaleScope))
                    return@withLock
                }
                local.update {
                    it.copy(terminals = immutable(it.terminals + created.value), working = false)
                }
                open(live, created.value, resume = false)
            }
        }
    }

    public suspend fun select(id: String): Unit = lane.withLock {
        val live = resolve() ?: return@withLock
        val terminal =
            local.value.terminals.firstOrNull { it.id == id }
                ?: run {
                    fail(Failure.NotFound())
                    return@withLock
                }
        open(live, terminal, resume = local.value.selected?.id == id)
    }

    private suspend fun open(
        live: RemoteTerminalTarget,
        terminal: TerminalProcess,
        resume: Boolean,
    ) {
        generation += 1
        val mine = generation
        val cursor = if (resume) channel?.cursor else null
        reader?.cancelAndJoin()
        channel?.close()
        channel = null
        target = null
        local.update {
            it.copy(
                selected = terminal,
                connected = false,
                transcript = if (resume) it.transcript else "",
                working = true,
                failure = null,
            )
        }
        if (!live.current()) {
            fail(Failure.Refused(RefusalReason.StaleScope))
            return
        }
        when (val opened = live.gateway.connectTerminal(terminal.id, cursor)) {
            is Outcome.Failed -> fail(opened.failure)
            is Outcome.Ok -> {
                if (!live.current()) {
                    opened.value.close()
                    fail(Failure.Refused(RefusalReason.StaleScope))
                    return
                }
                channel = opened.value
                target = live
                local.update { it.copy(connected = true, working = false) }
                reader =
                    scope.launch(dispatchers.default) {
                        try {
                            opened.value.output.collect { text ->
                                if (mine == generation && live.current())
                                    local.update {
                                        it.copy(
                                            transcript = (it.transcript + text).takeLast(100_000)
                                        )
                                    }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (mine == generation) fail(Failure.Network())
                        } finally {
                            if (mine == generation) local.update { it.copy(connected = false) }
                            withContext(NonCancellable) { opened.value.close() }
                        }
                    }
            }
        }
    }

    public suspend fun showList(): Unit = lane.withLock {
        generation += 1
        reader?.cancelAndJoin()
        channel?.close()
        channel = null
        target = null
        local.update {
            it.copy(selected = null, connected = false, transcript = "", failure = null)
        }
    }

    public suspend fun send(id: String, text: String): Unit = lane.withLock {
        val live = target
        val socket = channel
        if (
            local.value.selected?.id != id ||
                live == null ||
                socket == null ||
                !local.value.connected ||
                !live.current()
        ) {
            fail(Failure.Refused(RefusalReason.StaleScope))
            return@withLock
        }
        when (val sent = attempt({ Failure.Network() }) { socket.write(text) }) {
            is Outcome.Failed -> fail(sent.failure)
            is Outcome.Ok -> Unit
        }
    }

    public suspend fun resize(id: String, rows: Int, cols: Int): Unit = lane.withLock {
        val live = resolve() ?: return@withLock
        val selected =
            local.value.selected?.takeIf { it.id == id }
                ?: run {
                    fail(Failure.Refused(RefusalReason.StaleScope))
                    return@withLock
                }
        if (rows !in 1..1000 || cols !in 1..1000) {
            fail(Failure.Rejected(null))
            return@withLock
        }
        when (val result = live.gateway.resizeTerminal(selected.id, rows, cols)) {
            is Outcome.Failed -> fail(result.failure)
            is Outcome.Ok -> Unit
        }
    }

    public suspend fun rename(id: String, title: String): Unit = lane.withLock {
        val live = resolve() ?: return@withLock
        val selected =
            local.value.selected?.takeIf { it.id == id }
                ?: run {
                    fail(Failure.Refused(RefusalReason.StaleScope))
                    return@withLock
                }
        val value = title.trim().takeIf { it.isNotEmpty() } ?: return@withLock
        when (val result = live.gateway.renameTerminal(selected.id, value)) {
            is Outcome.Failed -> fail(result.failure)
            is Outcome.Ok ->
                if (live.current())
                    local.update {
                        val renamed = selected.copy(title = value)
                        it.copy(
                            selected = renamed,
                            terminals =
                                it.terminals
                                    .map { t -> if (t.id == selected.id) renamed else t }
                                    .let(::immutable),
                        )
                    }
        }
    }

    public suspend fun remove(id: String): Unit = lane.withLock {
        val live = resolve() ?: return@withLock
        val selected =
            local.value.selected?.takeIf { it.id == id }
                ?: run {
                    fail(Failure.Refused(RefusalReason.StaleScope))
                    return@withLock
                }
        when (val result = live.gateway.removeTerminal(selected.id)) {
            is Outcome.Failed -> fail(result.failure)
            is Outcome.Ok -> {
                generation += 1
                reader?.cancelAndJoin()
                channel?.close()
                channel = null
                target = null
                local.update {
                    it.copy(
                        selected = null,
                        connected = false,
                        transcript = "",
                        terminals = immutable(it.terminals.filter { t -> t.id != selected.id }),
                    )
                }
            }
        }
    }

    private suspend fun resolve(): RemoteTerminalTarget? =
        when (val live = source.resolve(ref)) {
            is Outcome.Failed -> {
                fail(live.failure)
                null
            }
            is Outcome.Ok ->
                if (live.value.current()) live.value
                else {
                    fail(Failure.Refused(RefusalReason.StaleScope))
                    null
                }
        }

    private fun fail(failure: Failure) {
        local.update { it.copy(loading = false, working = false, failure = failure) }
    }

    private fun immutable(items: List<TerminalProcess>): ImmutableList<TerminalProcess> =
        kotlinx.collections.immutable.persistentListOf<TerminalProcess>().addAll(items)
}
