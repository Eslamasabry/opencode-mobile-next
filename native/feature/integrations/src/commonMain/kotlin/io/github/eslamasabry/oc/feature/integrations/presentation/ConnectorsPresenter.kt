package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page's state machine, apart from the ViewModel so tests drive it with a test scope. Every read is tagged with a
 * generation: a change of server starts a new one and a late answer for the old server is dropped. A switch is bound to
 * the server it was flipped on; if the server changes before it settles, its answer is ignored (B-AGENT-134 spirit).
 */
public class ConnectorsPresenter(
    initial: ProfileId?,
    private val source: ConnectorsSource,
    private val scope: CoroutineScope,
) {
    private val local = MutableStateFlow(ConnectorsUi(server = initial))
    public val state: StateFlow<ConnectorsUi> = local.asStateFlow()

    private var generation = 0
    private var reading: Job? = null
    private val inFlight = mutableSetOf<String>()

    init {
        scope.launch {
            source.servers.collect { servers ->
                val before = local.value.server
                val chosen =
                    before?.takeIf { id -> servers.any { it.id == id } }
                        ?: source.active.value?.takeIf { id -> servers.any { it.id == id } }
                        ?: servers.firstOrNull()?.id
                local.update { it.copy(servers = servers, server = chosen) }
                if (chosen != before || generation == 0 || (chosen == null) != (local.value.phase == ConnectorsPhase.NoServer)) {
                    read(clear = chosen != before)
                }
            }
        }
    }

    public fun refresh() {
        if (local.value.refreshing) return
        read(clear = false)
    }

    public fun chooseServer(id: ProfileId) {
        if (id == local.value.server) return
        inFlight.clear()
        local.update { it.copy(server = id, problem = null) }
        read(clear = true)
    }

    public fun dismissProblem() {
        local.update { it.copy(problem = null) }
    }

    public fun toggle(name: String, on: Boolean) {
        val server = local.value.server ?: return
        if (!inFlight.add(name)) return // a double tap joins the change already in flight
        local.update { ui -> ui.copy(rows = ui.rows.map { if (it.name == name) it.copy(busy = true) else it }.toImmutableList(), problem = null) }
        val at = generation
        scope.launch {
            val result = if (on) source.connect(server, name) else source.disconnect(server, name)
            if (at != generation || local.value.server != server) return@launch // the page moved on
            inFlight.remove(name)
            if (result is Outcome.Failed) {
                local.update { ui ->
                    ui.copy(
                        rows = ui.rows.map { if (it.name == name) it.copy(busy = false) else it }.toImmutableList(),
                        problem = ConnectorProblem(name, on, result.failure),
                    )
                }
            }
            // Success or not, the row shows what the server now reports, never an assumed state.
            read(clear = false, keepGeneration = true)
        }
    }

    private fun read(clear: Boolean, keepGeneration: Boolean = false) {
        val server = local.value.server
        if (!keepGeneration) {
            generation++
            reading?.cancel()
        }
        val at = generation
        if (server == null) {
            local.update { it.copy(phase = ConnectorsPhase.NoServer, rows = persistentListOf(), refreshing = false) }
            return
        }
        local.update {
            it.copy(
                phase = if (clear || it.rows.isEmpty()) ConnectorsPhase.Loading else it.phase,
                rows = if (clear) persistentListOf() else it.rows,
                refreshing = true,
                failure = null,
            )
        }
        reading =
            scope.launch {
                val result = source.list(server)
                if (at != generation || local.value.server != server) return@launch
                when (result) {
                    is Outcome.Ok ->
                        local.update {
                            val rows = ConnectorsUi.rows(result.value, inFlight)
                            it.copy(
                                phase = if (rows.isEmpty()) ConnectorsPhase.Empty else ConnectorsPhase.Ready,
                                rows = rows,
                                refreshing = false,
                            )
                        }
                    is Outcome.Failed ->
                        local.update {
                            it.copy(
                                phase = if (result.failure is Failure.Unavailable) ConnectorsPhase.Unsupported else ConnectorsPhase.Failed,
                                refreshing = false,
                                failure = result.failure,
                            )
                        }
                }
            }
    }
}
