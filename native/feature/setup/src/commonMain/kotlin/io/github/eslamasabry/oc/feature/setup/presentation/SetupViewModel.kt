package io.github.eslamasabry.oc.feature.setup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow

/** Thin Metro ViewModel (plan 03 §13.7): hands viewModelScope to [SetupModel], whose rules are tested on virtual time. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class SetupViewModel(source: SetupSource, nav: SetupNavigator, details: SetupDetails) : ViewModel(), SetupActions {
    private val model = SetupModel(source, nav, viewModelScope, details)
    public val state: StateFlow<SetupUi> = model.state

    override fun start(): Unit = model.start()

    override fun pause(): Unit = model.pause()

    override fun resume(): Unit = model.resume()

    override fun openAppSettings(): Unit = model.openAppSettings()

    override fun back(): Unit = model.back()
}
