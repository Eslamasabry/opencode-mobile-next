package io.github.eslamasabry.oc.feature.devservices.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.DevServicesRoute
import kotlinx.coroutines.flow.StateFlow

/** DevServicesRoute.Home: one thin ViewModel over [DevServicesModel]. */
@AssistedInject
public class DevServicesViewModel(
    @Assisted route: DevServicesRoute.Home,
    host: DevServicesHost,
    pacer: Pacer,
    nav: AppNavigator,
) : ViewModel(), DevServicesActions {
    private val model =
        DevServicesModel(route.profile, route.directory, host, pacer, viewModelScope, nav::back)
    public val state: StateFlow<DevServicesUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun refresh(): Unit = model.refresh()

    override fun add(): Unit = model.add()

    override fun saveFound(id: String): Unit = model.saveFound(id)

    override fun edit(name: String, command: String, url: String): Unit =
        model.edit(name, command, url)

    override fun save(): Unit = model.save()

    override fun closeEditor(): Unit = model.closeEditor()

    override fun start(id: String): Unit = model.start(id)

    override fun askStop(id: String): Unit = model.askStop(id)

    override fun askRestart(id: String): Unit = model.askRestart(id)

    override fun askForget(id: String): Unit = model.askForget(id)

    override fun askRemove(id: String): Unit = model.askRemove(id)

    override fun logs(id: String): Unit = model.logs(id)

    override fun refreshOutput(): Unit = model.refreshOutput()

    override fun closeOutput(): Unit = model.closeOutput()

    override fun visit(id: String): Unit = model.visit(id)

    override fun askStopFound(id: String): Unit = model.askStopFound(id)

    override fun foundLogs(id: String): Unit = model.foundLogs(id)

    override fun refreshLogs(): Unit = model.refreshLogs()

    override fun closeLogs(): Unit = model.closeLogs()

    override fun confirm(): Unit = model.confirm()

    override fun cancel(): Unit = model.cancel()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()

    override fun onCleared() {
        model.close()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: DevServicesRoute.Home): DevServicesViewModel
    }
}
