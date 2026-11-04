package io.github.eslamasabry.oc.feature.monitor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.MonitorRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AssistedInject
public class MonitorViewModel(
    @Assisted private val route: MonitorRoute,
    private val host: MonitorHost,
    private val nav: AppNavigator,
) : ViewModel(), MonitorActions {
    private var opened = false
    private var cleared = false
    public val state: StateFlow<MonitorUi> =
        host.state
            .map { value ->
                value.copy(
                    rows =
                        value.rows
                            .filter { route.profile == null || it.id == route.profile }
                            .toImmutableList()
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorUi())

    init {
        viewModelScope.launch {
            withContext(NonCancellable) {
                host.open()
                if (cleared) host.close() else opened = true
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun setEnabled(profile: ProfileId, enabled: Boolean) {
        viewModelScope.launch { host.setEnabled(profile, enabled) }
    }

    override fun check() {
        viewModelScope.launch { host.check() }
    }

    override fun openSession(profile: ProfileId, session: String) {
        host.sessionTarget(profile, session)?.let { nav.openChat(it) }
    }

    override fun onCleared() {
        cleared = true
        if (opened) host.close()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: MonitorRoute): MonitorViewModel
    }
}
