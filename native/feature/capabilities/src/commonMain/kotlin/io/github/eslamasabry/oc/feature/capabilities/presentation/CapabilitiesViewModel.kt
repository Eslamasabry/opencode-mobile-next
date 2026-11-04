package io.github.eslamasabry.oc.feature.capabilities.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.CapabilityEnableFlow
import io.github.eslamasabry.oc.ui.routes.CapabilityEnableRequest
import io.github.eslamasabry.oc.ui.routes.CapabilityFlowLimit
import io.github.eslamasabry.oc.ui.routes.CapabilityFlows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class CapabilitiesUi(
    val flows: List<CapabilityEnableFlow>,
    val unavailable: Map<CapabilityEnableFlow, CapabilityFlowLimit> = emptyMap(),
    val opening: CapabilityEnableFlow? = null,
    val failure: Failure? = null,
)

public interface CapabilitiesActions {
    public fun onBack()

    public fun onEnable(flow: CapabilityEnableFlow)
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class CapabilitiesViewModel(
    private val registry: CapabilityFlows,
    private val nav: AppNavigator,
) : ViewModel(), CapabilitiesActions {
    private val mutable =
        MutableStateFlow(
            CapabilitiesUi(
                CapabilityEnableFlow.entries.filter { it in registry.resolved },
                registry.unavailable,
            )
        )
    public val state: StateFlow<CapabilitiesUi> = mutable.asStateFlow()

    override fun onBack() = nav.back()

    override fun onEnable(flow: CapabilityEnableFlow) {
        if (mutable.value.opening != null || flow !in registry.registered) return
        mutable.update { it.copy(opening = flow, failure = null) }
        viewModelScope.launch {
            try {
                val result = registry.enable(CapabilityEnableRequest(flow))
                if (result is Outcome.Failed) mutable.update { it.copy(failure = result.failure) }
            } finally {
                mutable.update { it.copy(opening = null) }
            }
        }
    }
}
