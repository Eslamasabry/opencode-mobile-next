package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.McpServerInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

public enum class ConnectorsPhase { Loading, Ready, Empty, Unsupported, Failed, NoServer }

/** What a connector is doing, in the words a person reads (never the server's own error text). */
public enum class ConnectorState {
    Connected,
    Starting,
    Off,
    NeedsSignIn,
    Failed,
    Unknown,
    ;

    /** A switch shows it on while it runs or is coming up. */
    public val on: Boolean get() = this == Connected || this == Starting

    public companion object {
        /** OC2 `status.status` words (`GET /api/mcp`); anything new reads as Unknown, never as connected. */
        public fun of(wire: String, failed: Boolean = false): ConnectorState =
            when (wire.lowercase()) {
                "connected" -> Connected
                "pending", "connecting", "starting" -> Starting
                "disabled", "disconnected" -> Off
                "needs_auth", "needs_client_registration" -> NeedsSignIn
                "failed", "error" -> Failed
                else -> if (failed) Failed else Unknown
            }
    }
}

public data class ConnectorRow(val name: String, val state: ConnectorState, val busy: Boolean = false)

/** What last went wrong with a switch, for the notice (plain line + Details). */
public data class ConnectorProblem(val name: String, val turningOn: Boolean, val failure: Failure)

public data class ConnectorsUi(
    val servers: ImmutableList<ConnectorServer> = persistentListOf(),
    val server: ProfileId? = null,
    val phase: ConnectorsPhase = ConnectorsPhase.Loading,
    val rows: ImmutableList<ConnectorRow> = persistentListOf(),
    val refreshing: Boolean = false,
    val failure: Failure? = null,
    val problem: ConnectorProblem? = null,
) {
    val serverName: String?
        get() = servers.firstOrNull { it.id == server }?.name

    public companion object {
        /** Running connectors first, then the rest, each by name (the catalogue's "on first" order, B-AGENT-212). */
        public fun rows(list: List<McpServerInfo>, busy: Set<String> = emptySet()): ImmutableList<ConnectorRow> =
            list
                .map { ConnectorRow(it.name, ConnectorState.of(it.status, it.error != null), it.name in busy) }
                .sortedWith(compareBy<ConnectorRow> { if (it.state.on) 0 else 1 }.thenBy { it.name.lowercase() })
                .toImmutableList()
    }
}

public interface ConnectorsActions {
    public fun back()

    public fun refresh()

    public fun chooseServer(id: ProfileId)

    public fun toggle(name: String, on: Boolean)

    public fun dismissProblem()

    public fun openServers()
}
