package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute

public interface WelcomeActions {
    /** "Connect to my computer": the server setup, where a saved and answering server finishes first run. */
    public fun connect()

    /** Language and appearance before anything else, for a person who cannot read the default language. */
    public fun openSettings()
}

/** The welcome (07 §5 A03). It holds no state: everything it says is fixed words. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class WelcomeViewModel(private val nav: AppNavigator) : ViewModel(), WelcomeActions {
    override fun connect(): Unit = nav.open(ServersRoute.Home)

    override fun openSettings(): Unit = nav.open(SettingsRoute.Home)
}
