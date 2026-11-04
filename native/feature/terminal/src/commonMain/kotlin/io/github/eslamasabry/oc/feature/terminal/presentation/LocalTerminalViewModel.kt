package io.github.eslamasabry.oc.feature.terminal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.project.terminal.LocalShell
import io.github.eslamasabry.oc.engine.project.terminal.LocalTerminalInputPort
import io.github.eslamasabry.oc.engine.project.terminal.LocalTerminalPort
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.RemoteTerminalRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public data class TerminalServerSource(val id: ProfileId, val name: String)

public data class LocalTerminalUi(
    val servers: List<TerminalServerSource> = emptyList(),
    val shells: List<LocalShell> = emptyList(),
    val selected: Int? = null,
    val working: Boolean = false,
    val failed: Boolean = false,
    val notInstalled: Boolean = false,
    val accessible: Boolean? = null,
    val confirmStop: Int? = null,
    val devServerPort: Int? = null,
)

public interface LocalTerminalNativeActions {
    public fun key(key: TerminalKey)

    public fun server(id: ProfileId)
}

public interface LocalTerminalActions {
    public fun create()

    public fun select(id: Int)

    public fun send(text: String)

    public fun setControl(enabled: Boolean)

    public fun stop()

    public fun confirmStop()

    public fun dismissStop()

    public fun close()

    public fun restart()

    public fun setAccessible(enabled: Boolean)

    public fun back()

    public fun openDevPreview() {}
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LocalTerminalViewModel(
    private val port: LocalTerminalPort,
    private val nav: AppNavigator,
    private val registry: ConnectionRegistry,
    private val devServers:
        io.github.eslamasabry.oc.engine.runtime.devservers.TerminalDevServerHost =
        io.github.eslamasabry.oc.engine.runtime.devservers.NoTerminalDevServerHost(),
) : ViewModel(), LocalTerminalActions, LocalTerminalNativeActions {
    private val sources =
        registry.live.flatMapLatest { graphs ->
            if (graphs.isEmpty()) flowOf(emptyList())
            else
                combine(graphs.values.map { it.session.capabilities }) { caps ->
                    graphs.values
                        .zip(caps)
                        .filter { it.second.terminal }
                        .map { TerminalServerSource(it.first.profile.id, it.first.profile.name) }
                }
        }
    private val local = MutableStateFlow(LocalTerminalUi())
    public val state: StateFlow<LocalTerminalUi> =
        combine(port.shells, local, sources) { shells, ui, servers ->
                ui.copy(
                    shells = shells,
                    servers = servers,
                    selected =
                        ui.selected?.takeIf { id -> shells.any { it.id == id } }
                            ?: shells.lastOrNull()?.id,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                LocalTerminalUi(
                    shells = port.shells.value,
                    selected = port.shells.value.lastOrNull()?.id,
                ),
            )

    init {
        if (port.shells.value.isEmpty()) create()
    }

    private var devSession:
        io.github.eslamasabry.oc.engine.runtime.devservers.TerminalDevServerSession? =
        null
    private var devWatch: kotlinx.coroutines.Job? = null

    public fun resumeDevServer() {
        if (devWatch?.isActive == true) return
        devWatch = viewModelScope.launch {
            devServers.sessions().collectLatest { session ->
                devSession = session
                local.update { it.copy(devServerPort = null) }
                if (session == null) return@collectLatest
                try {
                    kotlinx.coroutines.coroutineScope {
                        launch {
                            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                                session.signal.refresh()
                                kotlinx.coroutines.delay(15_000)
                            }
                        }
                        session.signal.running.collect { port ->
                            local.update { it.copy(devServerPort = port) }
                        }
                    }
                } finally {
                    session.signal.close()
                }
            }
        }
    }

    public fun pauseDevServer() {
        devWatch?.cancel()
        devWatch = null
        devSession?.signal?.close()
        devSession = null
        local.update { it.copy(devServerPort = null) }
    }

    override fun openDevPreview() {
        val session = devSession ?: return
        val port = state.value.devServerPort ?: return
        if (session.signal.available && session.signal.running.value == port)
            nav.open(
                io.github.eslamasabry.oc.ui.routes.PreviewRoute.Page(
                    port,
                    profile = session.profile,
                )
            )
    }

    override fun onCleared() {
        pauseDevServer()
        super.onCleared()
    }

    override fun create() = run {
        val previous = state.value.shells.firstOrNull { it.id == state.value.selected }
        when (val opened = port.start(rows = previous?.rows ?: 24, cols = previous?.cols ?: 80)) {
            is Outcome.Failed -> opened
            is Outcome.Ok -> {
                local.update { it.copy(selected = opened.value) }
                Outcome.Ok(Unit)
            }
        }
    }

    override fun select(id: Int) {
        discardInput()
        local.update { it.copy(selected = id) }
    }

    override fun send(text: String) {
        val id = state.value.selected ?: return
        val input = port as? LocalTerminalInputPort
        if (input != null) {
            if (input.enqueueText(id, text) is Outcome.Failed)
                local.update { it.copy(failed = true) }
        } else viewModelScope.launch { port.send(id, text) }
    }

    private fun discardInput() {
        state.value.selected?.let { (port as? LocalTerminalInputPort)?.discardInput(it) }
    }

    override fun key(key: TerminalKey) {
        val id = state.value.selected ?: return
        val input = port as? LocalTerminalInputPort ?: return
        if (input.enqueueKey(id, key) is Outcome.Failed) local.update { it.copy(failed = true) }
    }

    override fun server(id: ProfileId) {
        val graph = registry.live.value[id] ?: return
        if (graph.session.capabilities.value.terminal) {
            discardInput()
            nav.open(RemoteTerminalRoute(id))
        }
    }

    override fun setControl(enabled: Boolean) {
        state.value.selected?.let { id -> viewModelScope.launch { port.control(id, enabled) } }
    }

    override fun stop() {
        local.update { it.copy(confirmStop = state.value.selected) }
    }

    override fun dismissStop() {
        local.update { it.copy(confirmStop = null) }
    }

    override fun confirmStop() {
        val id = local.value.confirmStop ?: return
        dismissStop()
        run { port.stop(id) }
    }

    override fun close() {
        state.value.selected?.let { id -> run { port.remove(id) } }
    }

    override fun restart() {
        state.value.selected?.let { id ->
            run {
                when (val restarted = port.restart(id)) {
                    is Outcome.Failed -> restarted
                    is Outcome.Ok -> {
                        local.update { it.copy(selected = restarted.value) }
                        Outcome.Ok(Unit)
                    }
                }
            }
        }
    }

    override fun setAccessible(enabled: Boolean) {
        local.update { it.copy(accessible = enabled) }
    }

    override fun back() {
        discardInput()
        nav.back()
    }

    private fun run(work: suspend () -> Outcome<Unit>) {
        if (local.value.working) return
        local.update { it.copy(working = true, failed = false, notInstalled = false) }
        viewModelScope.launch {
            val result = work()
            local.update {
                it.copy(
                    working = false,
                    failed = result is Outcome.Failed,
                    notInstalled =
                        (result as? Outcome.Failed)?.failure ==
                            Failure.Runtime(RuntimeProblem.NotInstalled),
                )
            }
        }
    }
}
