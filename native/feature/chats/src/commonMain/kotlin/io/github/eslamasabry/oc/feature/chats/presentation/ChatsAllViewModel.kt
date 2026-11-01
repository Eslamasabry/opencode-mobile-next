package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Route-keyed ViewModel (plan 03 S5): the route argument arrives typed through an assisted factory. */
@AssistedInject
public class ChatsAllViewModel(@Assisted private val route: ChatsRoute.All, private val nav: AppNavigator) :
    ViewModel(), ChatsAllActions {
    public val state: StateFlow<ChatsAllUi> = MutableStateFlow(ChatsAllUi(route.filter)).asStateFlow()

    override fun back(): Unit = nav.back()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatsRoute.All): ChatsAllViewModel
    }
}
