package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.StateFlow

/**
 * The explicit per-server monitor. Its switch is independent of automatic readings on the Plans
 * page.
 */
public interface ProviderUsageMonitorSettings {
    public val states: StateFlow<Map<ProfileId, ProviderUsageMonitorState>>

    public fun supported(profile: ProfileId): Boolean

    public fun state(profile: ProfileId): ProviderUsageMonitorState

    public suspend fun setEnabled(profile: ProfileId, enabled: Boolean): Outcome<Unit>

    public suspend fun refresh(profile: ProfileId): Outcome<Unit>
}

public data class ProviderUsageMonitorState(
    val enabled: Boolean = false,
    val status: PaseoUsageStatus = PaseoUsageStatus.Off,
    val lastReadAtMs: Long? = null,
    val providerCount: Int = 0,
)
