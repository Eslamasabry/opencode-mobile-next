package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * OC2 connector (MCP) reads and runtime toggles for the native Connectors screen (unit BUILD-INTEGRATIONS-A), kept in
 * their own file so Oc2Gateway.kt is untouched. The same wire as Flutter `ops_catalog.dart`: `GET /mcp`,
 * `POST /mcp/{name}/connect|disconnect`, every call scoped to the gateway's current location. Each returns null when
 * [gateway] is not an OC2 gateway, so the caller says the server cannot do it rather than guess. Server error text is
 * never surfaced: a failed connector keeps only the fact that it failed.
 */
public object Oc2Connectors {
    /** Connectors configured at the current location (Flutter `listMcpServers`). */
    public suspend fun servers(gateway: ServerGateway): Outcome<ImmutableList<McpServerInfo>>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (val reply = oc2.json("/mcp", query = location(oc2))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val root = reply.value
                val data = (root as? JsonObject)?.get("data") ?: root
                val rows = data as? JsonArray ?: return Failure.UnreadableReply().failed()
                rows.mapNotNull { row ->
                    val item = row as? JsonObject ?: return@mapNotNull null
                    val name = (item["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val status = item["status"]
                    val word =
                        when (status) {
                            is JsonObject -> (status["status"] as? JsonPrimitive)?.contentOrNull
                            is JsonPrimitive -> status.contentOrNull
                            else -> null
                        } ?: "unknown"
                    // The server's error words stay off screen (no raw errors); only their presence is kept.
                    val failed = (status as? JsonObject)?.get("error").let { it != null && it !is JsonNull }
                    McpServerInfo(name, word, if (failed) "" else null)
                }.toImmutableList().ok()
            }
        }
    }

    /** Connects one configured connector at the current location. */
    public suspend fun connect(gateway: ServerGateway, name: String): Outcome<Unit>? = toggle(gateway, name, "connect")

    /** Disconnects one connector at the current location (it stays configured). */
    public suspend fun disconnect(gateway: ServerGateway, name: String): Outcome<Unit>? =
        toggle(gateway, name, "disconnect")

    private suspend fun toggle(gateway: ServerGateway, name: String, verb: String): Outcome<Unit>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (
            val reply = oc2.json("/mcp/${name.encodeURLPathPart()}/$verb", HttpMethod.Post, query = location(oc2))
        ) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> Unit.ok()
        }
    }

    private fun location(oc2: Oc2Gateway): Map<String, String?> {
        val at = oc2.location.value
        return mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)
    }
}
