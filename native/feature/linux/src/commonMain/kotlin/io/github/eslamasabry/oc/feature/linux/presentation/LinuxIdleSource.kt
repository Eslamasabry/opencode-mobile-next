package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

public data class LinuxIdleUi(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val minutes: Int? = null,
    val stopped: Boolean = false,
    val helperStopped: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Exact native owner/generation receipts authorize resume; configuring a policy never starts work.
 */
public interface LinuxIdleSource {
    public val state: StateFlow<LinuxIdleUi>

    public suspend fun refresh()

    public suspend fun configure(enabled: Boolean, minutes: Int): Outcome<Unit>

    public suspend fun resume(): Outcome<Unit>
}
