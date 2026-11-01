package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Plain screen ViewModel through Metro (`metroViewModel<ChatsViewModel>()`, plan 03 §13.7). */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ChatsViewModel(registry: ConnectionRegistry, private val nav: AppNavigator) : ViewModel(), ChatsActions {
    public val state: StateFlow<ChatsUi> =
        registry.live
            .map { ChatsUi(liveConnections = it.size) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUi())

    override fun showAll(): Unit = nav.open(ChatsRoute.All(filter = null))
}
