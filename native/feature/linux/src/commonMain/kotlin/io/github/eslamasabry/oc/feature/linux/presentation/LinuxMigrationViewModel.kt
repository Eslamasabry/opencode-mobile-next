package io.github.eslamasabry.oc.feature.linux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AssistedInject
public class LinuxMigrationViewModel(
    @Assisted route: LinuxRoute.Migration,
    sessions: LinuxMigrationSessions,
    private val nav: AppNavigator,
) : ViewModel(), LinuxMigrationActions {
    private val session = sessions.session(route.source.raw)
    private val confirmation = MutableStateFlow<LinuxMigrationCommand?>(null)
    public val state: StateFlow<LinuxMigrationUi> =
        combine(session.state, confirmation) { ui, ask -> ui.copy(confirmation = ask) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value)

    init {
        viewModelScope.launch { session.open() }
    }

    override fun toggle(id: String, selected: Boolean): Unit = session.toggle(id, selected)

    override fun command(command: LinuxMigrationCommand) {
        if (command == LinuxMigrationCommand.Stop || command == LinuxMigrationCommand.Discard)
            confirmation.value = command
        else viewModelScope.launch { session.command(command) }
    }

    override fun confirm() {
        val command = confirmation.value ?: return
        confirmation.value = null
        viewModelScope.launch { session.command(command) }
    }

    override fun dismiss() {
        confirmation.value = null
    }

    override fun back() {
        if (state.value.busy) confirmation.value = LinuxMigrationCommand.Stop else nav.back()
    }

    override fun onCleared() {
        session.close()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: LinuxRoute.Migration): LinuxMigrationViewModel
    }
}
