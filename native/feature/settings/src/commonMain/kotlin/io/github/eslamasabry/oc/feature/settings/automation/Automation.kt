package io.github.eslamasabry.oc.feature.settings.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public data class AutomationChoice(val id: String, val enabled: Boolean)

public data class AutomationServer(
    val id: String,
    val name: String,
    val choices: ImmutableList<AutomationChoice>,
)

public data class AutomationUi(
    val servers: ImmutableList<AutomationServer> = persistentListOf(),
    val saving: Boolean = false,
    val failed: Boolean = false,
)

public interface AutomationHost {
    public val state: StateFlow<AutomationUi>

    public suspend fun refresh(): Outcome<Unit>

    public suspend fun setBehavior(
        profile: String,
        behavior: String,
        enabled: Boolean,
    ): Outcome<Unit>
}

public fun interface AutomationActions {
    public fun setBehavior(profile: String, behavior: String, enabled: Boolean)
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AutomationViewModel(private val host: AutomationHost) :
    ViewModel(), AutomationActions {
    public val state: StateFlow<AutomationUi> = host.state

    init {
        viewModelScope.launch { host.refresh() }
    }

    override fun setBehavior(profile: String, behavior: String, enabled: Boolean) {
        viewModelScope.launch { host.setBehavior(profile, behavior, enabled) }
    }
}
