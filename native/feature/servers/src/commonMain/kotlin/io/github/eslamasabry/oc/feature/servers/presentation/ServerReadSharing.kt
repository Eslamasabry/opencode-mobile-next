package io.github.eslamasabry.oc.feature.servers.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.StateFlow

public data class ServerReadSharingUi(
    val available: Boolean,
    val enabled: Boolean,
    val saving: Boolean,
)

/** Local consent only; changing it never sends a receipt or opens a connection. */
public interface ServerReadSharing {
    public val revision: StateFlow<Long>

    public fun state(profile: ProfileId): ServerReadSharingUi

    public suspend fun set(profile: ProfileId, enabled: Boolean): Outcome<Unit>
}

@ContributesTo(AppScope::class)
public interface ServerReadSharingBindings {
    @Multibinds(allowEmpty = true) public val serverReadSharing: Set<ServerReadSharing>
}
