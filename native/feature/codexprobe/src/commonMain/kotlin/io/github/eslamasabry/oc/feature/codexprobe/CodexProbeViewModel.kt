package io.github.eslamasabry.oc.feature.codexprobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PlansRoute
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class CodexProbeViewModel(private val host: CodexProbeHost, private val nav: AppNavigator) :
    ViewModel(), CodexProbeActions {
    public val state = host.state
    override val startCommand: String
        get() = host.startCommand

    override fun probe(address: String, directory: String, token: String) {
        viewModelScope.launch { host.probe(address, directory, token) }
    }

    override fun open(address: String, directory: String, token: String) {
        viewModelScope.launch { host.open(address, directory, token) }
    }

    override fun close() {
        viewModelScope.launch { host.close() }
    }

    override fun back() {
        nav.back()
    }

    override fun plans() {
        nav.open(PlansRoute)
    }
}
