package io.github.eslamasabry.oc.feature.servers.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.StateFlow

public enum class ServerMonitorStatus {
    Waiting,
    Current,
    Unavailable,
    WifiRequired,
    Paused,
}

public data class ServerMonitorUi(
    val enabled: Boolean = false,
    val available: Boolean = true,
    val wifiOnly: Boolean = false,
    val checkInMinutes: Int? = null,
    val status: ServerMonitorStatus = ServerMonitorStatus.Waiting,
    val pending: Int? = null,
)

/**
 * Explicit enrollment for the bounded background attention reader. Absent on unsupported platforms.
 */
public interface ServerMonitoring {
    public val rows: StateFlow<Map<ProfileId, ServerMonitorUi>>

    public suspend fun enabled(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun wifiOnly(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun checkIn(profile: ProfileId, minutes: Int?): Outcome<Unit>
}

@ContributesTo(AppScope::class)
public interface ServerMonitoringBindings {
    @Multibinds(allowEmpty = true) public val serverMonitoring: Set<ServerMonitoring>
}
