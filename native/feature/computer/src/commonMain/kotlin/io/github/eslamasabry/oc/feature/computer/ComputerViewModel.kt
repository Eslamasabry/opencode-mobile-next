package io.github.eslamasabry.oc.feature.computer

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
import io.github.eslamasabry.oc.ui.routes.ComputerRoute
import kotlinx.coroutines.flow.StateFlow

/** One per ComputerRoute entry; all behaviour is [ComputerPresenter]'s, this only gives it a scope and navigation. */
@AssistedInject
public class ComputerViewModel(@Assisted route: ComputerRoute, tools: ComputerTools, nav: AppNavigator) :
    ViewModel(), ComputerActions {
    private val presenter =
        ComputerPresenter(viewModelScope, route.profile, tools, onBack = nav::back, openChat = { nav.openChat(it) })

    public val state: StateFlow<ComputerUi> = presenter.state

    override fun back(): Unit = presenter.back()

    override fun askUpdate(): Unit = presenter.askUpdate()

    override fun cancelUpdate(): Unit = presenter.cancelUpdate()

    override fun confirmUpdate(): Unit = presenter.confirmUpdate()

    override fun reloadImports(): Unit = presenter.reloadImports()

    override fun import(key: String): Unit = presenter.import(key)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ComputerRoute): ComputerViewModel
    }
}
