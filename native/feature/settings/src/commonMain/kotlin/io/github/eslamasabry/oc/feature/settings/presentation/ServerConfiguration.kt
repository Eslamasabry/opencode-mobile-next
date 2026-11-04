package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConfigurationPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Only masked configuration crosses the gateway. This port never writes, copies or shares it. */
public interface ConfigurationInspection {
    public val available: StateFlow<Boolean>
    /**
     * Changes when the selected server, its connection, location or inspection capability changes.
     */
    public val revision: StateFlow<Long>

    public suspend fun read(): Outcome<ConfigurationPreview>
}

public sealed interface ServerConfigurationUi {
    public data object Loading : ServerConfigurationUi

    public data object Unavailable : ServerConfigurationUi

    public data class Ready(val text: String) : ServerConfigurationUi
}

public fun interface ServerConfigurationActions {
    public fun refresh()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ServerConfigurationViewModel(host: SettingsHost) :
    ViewModel(), ServerConfigurationActions {
    private val source = host.configuration
    private val mutable = MutableStateFlow<ServerConfigurationUi>(ServerConfigurationUi.Loading)
    public val state: StateFlow<ServerConfigurationUi> = mutable.asStateFlow()
    private var read: Job? = null
    private var generation = 0L

    init {
        val inspection = source
        if (inspection == null) mutable.value = ServerConfigurationUi.Unavailable
        else viewModelScope.launch { inspection.revision.collect { refresh() } }
    }

    override fun refresh() {
        val inspection = source ?: return
        val ticket = ++generation
        read?.cancel()
        mutable.value = ServerConfigurationUi.Loading
        read = viewModelScope.launch {
            val result = inspection.read()
            if (ticket != generation) return@launch
            mutable.value =
                when (result) {
                    is Outcome.Ok -> ServerConfigurationUi.Ready(result.value.text)
                    is Outcome.Failed -> ServerConfigurationUi.Unavailable
                }
        }
    }
}
