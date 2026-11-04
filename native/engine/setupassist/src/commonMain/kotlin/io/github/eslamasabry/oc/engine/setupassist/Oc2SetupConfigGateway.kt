package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** Where an OpenCode 2 request runs: `location[directory]` and `location[workspace]`. */
public data class SetupLocation(val directory: String?, val workspace: String?)

/** What the already authenticated OpenCode 2 client answered a GET with. Never carries an error body. */
public sealed interface SetupHttpReply {
    public data class Json(val body: JsonElement) : SetupHttpReply

    /** A non-2xx HTTP status. The body is dropped at the boundary. */
    public data class Status(val code: Int) : SetupHttpReply

    /** The network failed before an answer (no route, reset, timeout). */
    public data object Network : SetupHttpReply

    /** The route does not exist on this server version (`Api2RemovedInStable`). */
    public data object RemovedInStable : SetupHttpReply

    /** The answer could not be decoded or something else went wrong. */
    public data object Unreadable : SetupHttpReply
}

/**
 * The borrowed, already authenticated OpenCode 2 client (Basic auth in a header, `/api` prefix and dialect are its
 * business). [location] is read once, when the gateway is created.
 */
public interface SetupHttpClient {
    public val location: SetupLocation

    public suspend fun getJson(path: String, query: Map<String, String>): SetupHttpReply
}

/**
 * Setup inspection through the authenticated, dialect-aware client (port of lib/api2/setup_config_adapter.dart).
 * The raw config has a `sources` list in low-to-high priority order; it is not a guessed effective merge. The client's
 * later location changes cannot retarget this owner. Stable's global PATCH cannot apply setup proposals.
 */
public class Oc2SetupConfigGateway(private val client: SetupHttpClient, private val redactor: Redactor) : SetupConfigGateway {
    private val location: SetupLocation = client.location

    override val support: SetupSupport =
        SetupSupport(
            readConfig = true,
            mcpInventory = true,
            reason =
                "This server cannot safely restore setup changes. Its global shell setting cannot apply this proposal " +
                    "or protect against other edits. Apply and Undo are unavailable.",
        )

    private suspend fun get(path: String): SetupOutcome<JsonElement> {
        val query = buildMap {
            location.directory?.let { put("location[directory]", it) }
            location.workspace?.let { put("location[workspace]", it) }
        }
        return when (val reply = client.getJson(path, query)) {
            is SetupHttpReply.Json -> SetupOutcome.Ok(reply.body)
            is SetupHttpReply.Status ->
                if (reply.code == 401 || reply.code == 403) {
                    fail(SetupFailureCode.NeedsSignIn, "Sign in to this server to inspect setup.")
                } else {
                    fail(SetupFailureCode.Transport, LOAD_FAILED)
                }
            SetupHttpReply.Network -> fail(SetupFailureCode.Offline, "The server is unavailable. Reconnect and try again.")
            SetupHttpReply.RemovedInStable -> fail(SetupFailureCode.Unsupported, "Setup inspection is unavailable on this server version.")
            SetupHttpReply.Unreadable -> fail(SetupFailureCode.Transport, LOAD_FAILED)
        }
    }

    override suspend fun readConfig(): SetupOutcome<JsonObject> {
        val data = when (val got = get("/config")) {
            is SetupOutcome.Failed -> return got
            is SetupOutcome.Ok -> got.value
        }
        redactor.registerCredentialValues(data)
        val sources = if (data is JsonObject) data["data"] else data
        if (sources !is JsonArray ||
            sources.any { it !is JsonObject || (it["type"] as? JsonPrimitive)?.isString != true }
        ) {
            return fail(SetupFailureCode.Invalid, "The server returned an unsupported configuration response.")
        }
        return SetupOutcome.Ok(
            buildJsonObject {
                put(
                    "sources",
                    buildJsonArray {
                        for (entry in sources) {
                            entry as JsonObject
                            add(
                                buildJsonObject {
                                    put("type", entry.getValue("type"))
                                    val path = entry["path"]
                                    if (path is JsonPrimitive && path.isString) put("path", path)
                                    val info = entry["info"]
                                    if (info is JsonObject) put("info", info)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    override suspend fun patchConfig(patch: JsonObject): SetupOutcome<Unit> = fail(SetupFailureCode.Unsupported, support.reason)

    override suspend fun listMcpServers(): SetupOutcome<List<SetupMcpStatus>> {
        val data = when (val got = get("/mcp")) {
            is SetupOutcome.Failed -> return got
            is SetupOutcome.Ok -> got.value
        }
        val servers = (data as? JsonObject)?.get("data")
        if (servers !is JsonArray ||
            servers.any { it !is JsonObject || (it["name"] as? JsonPrimitive)?.isString != true }
        ) {
            return fail(SetupFailureCode.Invalid, "The server returned an unsupported MCP response.")
        }
        return SetupOutcome.Ok(
            servers.map { server ->
                server as JsonObject
                val status = ((server["status"] as? JsonObject)?.get("status") as? JsonPrimitive)
                    ?.takeIf { it.isString }?.content
                SetupMcpStatus(
                    name = redactor.redact((server.getValue("name") as JsonPrimitive).content),
                    status = if (status in KNOWN) status!! else "unknown",
                )
            },
        )
    }

    private companion object {
        const val LOAD_FAILED = "The server could not load setup information."
        val KNOWN = setOf("connected", "pending", "disabled", "failed", "needs_auth")

        fun fail(code: SetupFailureCode, message: String): SetupOutcome.Failed = SetupOutcome.Failed(SetupFailure(code, message))
    }
}
