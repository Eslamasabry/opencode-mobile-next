package io.github.eslamasabry.oc.feature.phonesetup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.linuxsetup.SetupEngine
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlinx.coroutines.flow.StateFlow

/** PhoneSetupNavigator → the routes of :ui:routes. */
@Inject
@ContributesBinding(AppScope::class)
public class RoutedPhoneSetupNavigator(private val nav: AppNavigator) : PhoneSetupNavigator {
    override fun back(): Unit = nav.back()

    override fun thisPhone() {
        nav.back()
        nav.open(PhoneRoute.ThisPhone)
    }
}

/** Thin Metro ViewModel (plan 03 §13.7): hands viewModelScope to [PhoneSetupModel]. SetupEngine and the ports are
 *  bound by androidApp's graph/PhoneSetupWiring.kt. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PhoneSetupViewModel(
    engine: SetupEngine,
    device: PhoneSetupDevice,
    jobs: PhoneSetupJobs,
    nav: PhoneSetupNavigator,
) : ViewModel(), PhoneSetupActions {
    private val model = PhoneSetupModel(engine, device, jobs, nav, viewModelScope)
    public val state: StateFlow<PhoneSetupUi> = model.state

    override fun back(): Unit = model.back()

    override fun setUp(): Unit = model.setUp()

    override fun continueSetup(): Unit = model.continueSetup()

    override fun askStop(): Unit = model.askStop()

    override fun keepGoing(): Unit = model.keepGoing()

    override fun stop(): Unit = model.stop()

    override fun openStorage(): Unit = model.openStorage()

    override fun done(): Unit = model.done()
}
