package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.security.Redactor

// Flutter lib/domain/sv3_mcp_capabilities.dart: MCP action availability derived from current server contracts.
// Runtime add/remove and OAuth flags are never evidence for deletion, undo or expiry guarantees.

/** Inventory and runtime connection controls. Disconnecting stops the connection; it does not delete the config. */
public val ServerCapabilities.mcpRuntimeDisconnect: Boolean get() = serverCatalog

/** Deleting a named MCP entry from persistent configuration: no server contract defines it. */
@Suppress("UnusedReceiverParameter")
public val ServerCapabilities.mcpConfigDeletion: Boolean get() = false

/** Undoing a runtime Remove with the exact prior configuration: the server returns no restoration handle. */
@Suppress("UnusedReceiverParameter")
public val ServerCapabilities.mcpRuntimeRemovalUndo: Boolean get() = false

/** Telling an expired OAuth credential apart: `needs_auth` is not proof of expiry. */
@Suppress("UnusedReceiverParameter")
public val ServerCapabilities.mcpOAuthTokenExpiry: Boolean get() = false

/** Expiry-triggered reconnection: no contract supplies an expiry signal; never reconnect from guessed lifetimes. */
@Suppress("UnusedReceiverParameter")
public val ServerCapabilities.mcpExpiryAutoReconnect: Boolean get() = false

/** Stable reasons for a refused runtime disconnect; no server text. */
public enum class McpRuntimeFailure { Unavailable, InvalidName, RequestFailed }

public sealed interface McpRuntimeResult {
    public data object Disconnected : McpRuntimeResult

    public data class Refused(val reason: McpRuntimeFailure) : McpRuntimeResult
}

/**
 * Disconnects [name] for the current server/location without deleting it. The exact name is sent only when the server
 * supports it and the name is safe (no surrounding space, no control characters, nothing that looks like a secret).
 * A failed request is [McpRuntimeFailure.RequestFailed] only: it may still have happened, so callers refetch.
 */
public suspend fun disconnectMcpRuntime(
    name: String,
    capabilities: ServerCapabilities,
    redactor: Redactor,
    disconnect: suspend (String) -> Outcome<Unit>,
): McpRuntimeResult {
    if (!capabilities.mcpRuntimeDisconnect) return McpRuntimeResult.Refused(McpRuntimeFailure.Unavailable)
    if (name.isBlank() || name != name.trim() || CONTROL_CHARACTERS.containsMatchIn(name) || redactor.containsSecret(name)) {
        return McpRuntimeResult.Refused(McpRuntimeFailure.InvalidName)
    }
    return when (disconnect(name)) {
        is Outcome.Ok -> McpRuntimeResult.Disconnected
        is Outcome.Failed -> McpRuntimeResult.Refused(McpRuntimeFailure.RequestFailed)
    }
}
