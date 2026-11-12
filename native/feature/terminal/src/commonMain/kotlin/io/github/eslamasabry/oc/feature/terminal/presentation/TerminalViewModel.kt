package io.github.eslamasabry.oc.feature.terminal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalSource
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalState
import io.github.eslamasabry.oc.engine.runtime.terminal.TerminalLastKnown
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminals
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import io.github.eslamasabry.oc.engine.runtime.devservers.NoTerminalDevServerHost
import io.github.eslamasabry.oc.engine.runtime.devservers.TerminalDevServerHost
import io.github.eslamasabry.oc.engine.runtime.devservers.TerminalDevServerSession
import io.github.eslamasabry.oc.ui.routes.LocalTerminalRoute
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DEV_SERVER_POLL_MS = 15_000L // the phone's terminal polls at the same pace

public interface TerminalActions {
    public fun back()

    public fun refresh()

    public fun create()

    /** A new terminal named [title] ("Terminal 2", Flutter's numbering); an implementation without names makes it plain. */
    public fun create(title: String) {
        create()
    }

    public fun select(id: String)

    public fun showList()

    public fun send(id: String, text: String)

    public fun rename(id: String, title: String)

    public fun resize(id: String, rows: Int, cols: Int)

    public fun remove(id: String)

    /** The open terminal's details, for its sheet; null when none is open. */
    public fun details(): TerminalDetailsUi? = null

    /** "Remove N ended terminals": every ended terminal of the list; running ones stay. */
    public fun removeEnded() {}

    /** A dev server of this project runs: opens the in-app preview on its port (Flutter `_openPreview`). */
    public fun openDevPreview() {}

    /** "Use this phone's terminal": the other place a shell can run. */
    public fun usePhone() {}
}

@AssistedInject
public class TerminalViewModel(
    @Assisted route: TerminalRoute.Conversation,
    source: RemoteTerminalSource,
    dispatchers: AppDispatchers,
    private val nav: AppNavigator,
    private val devServers: TerminalDevServerHost = NoTerminalDevServerHost(),
    kept: Set<TerminalLastKnown> = emptySet(),
) : ViewModel(), TerminalActions {
    private val route = route
    private val terminals = RemoteTerminals(route.from, source, viewModelScope, dispatchers, kept.firstOrNull())
    public val state: StateFlow<RemoteTerminalState> = terminals.state

    private val devPort = MutableStateFlow<Int?>(null)

    /** The port of a dev server of this project that is running now; null when none (Flutter `ProjectDevServer.running`). */
    public val devServerPort: StateFlow<Int?> = devPort.asStateFlow()
    private var devSession: TerminalDevServerSession? = null
    private var devWatch: Job? = null

    init {
        refresh()
    }

    /** The page is on screen: watch the project's dev server (stops in [pauseDevServer], like the phone's own). */
    public fun resumeDevServer() {
        if (devWatch?.isActive == true) return
        devWatch =
            viewModelScope.launch {
                devServers.sessions(route.from.profileId).collectLatest { session ->
                    devSession = session
                    devPort.value = null
                    if (session == null) return@collectLatest
                    try {
                        coroutineScope {
                            launch {
                                while (currentCoroutineContext().isActive) {
                                    session.signal.refresh()
                                    delay(DEV_SERVER_POLL_MS)
                                }
                            }
                            session.signal.running.collect { devPort.value = it }
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
        devPort.value = null
    }

    override fun openDevPreview() {
        val session = devSession ?: return
        val port = devPort.value ?: return
        if (session.signal.available && session.signal.running.value == port)
            nav.open(PreviewRoute.Page(port, ref = route.from, profile = session.profile))
    }

    override fun usePhone() {
        nav.open(LocalTerminalRoute)
    }

    override fun removeEnded() {
        viewModelScope.launch { terminals.removeEnded() }
    }

    override fun onCleared() {
        pauseDevServer()
        super.onCleared()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch { terminals.refresh() }
    }

    override fun create() {
        viewModelScope.launch { terminals.create() }
    }

    override fun create(title: String) {
        viewModelScope.launch { terminals.create(title) }
    }

    override fun select(id: String) {
        viewModelScope.launch { terminals.select(id) }
    }

    override fun showList() {
        viewModelScope.launch { terminals.showList() }
    }

    override fun send(id: String, text: String) {
        viewModelScope.launch { terminals.send(id, text) }
    }

    override fun rename(id: String, title: String) {
        viewModelScope.launch { terminals.rename(id, title) }
    }

    override fun resize(id: String, rows: Int, cols: Int) {
        viewModelScope.launch { terminals.resize(id, rows, cols) }
    }

    override fun remove(id: String) {
        viewModelScope.launch { terminals.remove(id) }
    }

    override fun details(): TerminalDetailsUi? = state.value.selected?.let { TerminalDetails.of(it) }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TerminalRoute.Conversation): TerminalViewModel
    }
}
