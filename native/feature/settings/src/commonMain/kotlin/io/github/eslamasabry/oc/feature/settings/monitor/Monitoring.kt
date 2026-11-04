package io.github.eslamasabry.oc.feature.settings.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public enum class MonitoringState {
    Off,
    Waiting,
    Checking,
    Current,
    Unavailable,
    WifiRequired,
    Paused,
}

public data class MonitoringServer(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val checkInMinutes: Int?,
    val state: MonitoringState,
    val pending: Int?,
)

public data class MonitoringUi(
    val servers: ImmutableList<MonitoringServer> = persistentListOf(),
    val busy: Boolean = false,
    val saveFailed: Boolean = false,
)

public interface MonitoringHost {
    public val state: StateFlow<MonitoringUi>

    public suspend fun setEnabled(id: String, on: Boolean): Outcome<Unit>

    public suspend fun setCheckIn(id: String, minutes: Int?): Outcome<Unit>
}

public interface MonitoringActions {
    public fun enabled(id: String, on: Boolean)

    public fun checkIn(id: String, minutes: Int?)
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class MonitoringViewModel(private val host: MonitoringHost) :
    ViewModel(), MonitoringActions {
    public val state: StateFlow<MonitoringUi> = host.state

    override fun enabled(id: String, on: Boolean) {
        viewModelScope.launch { host.setEnabled(id, on) }
    }

    override fun checkIn(id: String, minutes: Int?) {
        viewModelScope.launch { host.setCheckIn(id, minutes) }
    }
}
