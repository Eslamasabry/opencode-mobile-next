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
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminals
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public interface TerminalActions {
    public fun back()

    public fun refresh()

    public fun create()

    public fun select(id: String)

    public fun showList()

    public fun send(id: String, text: String)

    public fun rename(id: String, title: String)

    public fun resize(id: String, rows: Int, cols: Int)

    public fun remove(id: String)
}

@AssistedInject
public class TerminalViewModel(
    @Assisted route: TerminalRoute.Conversation,
    source: RemoteTerminalSource,
    dispatchers: AppDispatchers,
    private val nav: AppNavigator,
) : ViewModel(), TerminalActions {
    private val terminals = RemoteTerminals(route.from, source, viewModelScope, dispatchers)
    public val state: StateFlow<RemoteTerminalState> = terminals.state

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch { terminals.refresh() }
    }

    override fun create() {
        viewModelScope.launch { terminals.create() }
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

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TerminalRoute.Conversation): TerminalViewModel
    }
}
