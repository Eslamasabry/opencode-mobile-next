package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.StateFlow

public data class ServerRowFacts(
    val backend: BackendKind,
    val version: String?,
    val directory: String,
    val needsSecret: Boolean,
    val waiting: Int?,
    val running: Int?,
    val queued: Int,
    val account: Boolean = false,
)

public data class ServerRowsState(
    val facts: ImmutableMap<ProfileId, ServerRowFacts> = persistentMapOf(),
    val moveDestination: ProfileId? = null,
)

public interface ServerRowsPort {
    public val state: StateFlow<ServerRowsState>
}
