package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.McpServerInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** A connected server whose connectors can be read. */
public data class ConnectorServer(val id: ProfileId, val name: String)

/**
 * The live servers' connectors (MCP servers). The host reads them from the connection graph; a server that cannot list
 * connectors answers `Failure.Unavailable`. Connect and disconnect act on the server's current location only.
 */
public interface ConnectorsSource {
    public val servers: StateFlow<ImmutableList<ConnectorServer>>

    public val active: StateFlow<ProfileId?>

    public suspend fun list(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>>

    public suspend fun connect(profile: ProfileId, name: String): Outcome<Unit>

    public suspend fun disconnect(profile: ProfileId, name: String): Outcome<Unit>
}
