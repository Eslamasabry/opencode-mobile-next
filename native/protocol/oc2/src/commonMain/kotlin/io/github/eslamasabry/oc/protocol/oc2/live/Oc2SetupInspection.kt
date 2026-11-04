package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.*

/** Inspection never follows later changes to the gateway's shared project location. */
internal class Oc2SetupInspection(
    private val gateway: Oc2Gateway,
    private val redactor: Redactor,
    private val location: ServerLocation,
) : SetupInspector {
    private val query =
        mapOf(
            "location[directory]" to location.directory,
            "location[workspace]" to location.workspace,
        )

    override suspend fun historyMigration(): Outcome<SetupHistoryMigration> {
        val reply = gateway.json("/experimental/migration/v1")
        if (reply is Outcome.Failed) return reply
        val root =
            (reply as Outcome.Ok).value as? JsonObject ?: return Failure.UnreadableReply().failed()
        val data = root["data"] as? JsonObject ?: root
        val phase =
            when ((data["status"] as? JsonPrimitive)?.content) {
                "required" -> SetupHistoryPhase.Required
                "running" -> SetupHistoryPhase.Running
                "completed" -> SetupHistoryPhase.Completed
                "error" -> SetupHistoryPhase.Error
                else -> return Failure.UnreadableReply().failed()
            }
        val progress = data["progress"] as? JsonObject
        fun count(key: String): Long? =
            (progress?.get(key) as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }
        return SetupHistoryMigration(phase, count("numerator"), count("denominator")).ok()
    }

    override suspend fun readConfig(): Outcome<SetupConfigSnapshot> {
        val reply = gateway.json("/config", query = query)
        if (reply is Outcome.Failed) return reply
        val data = (reply as Outcome.Ok).value
        redactor.registerCredentialValues(data)
        val sources =
            (if (data is JsonObject) data["data"] else data) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        val result = ArrayList<SetupConfigSource>()
        for (entry in sources) {
            val item = entry as? JsonObject ?: return Failure.UnreadableReply().failed()
            val type =
                (item["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return Failure.UnreadableReply().failed()
            val path = (item["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val info = item["info"] as? JsonObject
            result +=
                SetupConfigSource(
                    redactor.redact(type).take(120),
                    path?.let { redactor.redact(it).take(2048) },
                    info?.let { safeConfigInfo(it).take(12000) },
                )
        }
        return SetupConfigSnapshot(result.toList()).ok()
    }

    override suspend fun mcpInventory(): Outcome<List<SetupMcpStatus>> {
        val reply = gateway.json("/mcp", query = query)
        if (reply is Outcome.Failed) return reply
        val data =
            (reply as Outcome.Ok).value as? JsonObject ?: return Failure.UnreadableReply().failed()
        val entries = data["data"] as? JsonArray ?: return Failure.UnreadableReply().failed()
        val result = ArrayList<SetupMcpStatus>()
        val known = setOf("connected", "pending", "disabled", "failed", "needs_auth")
        for (entry in entries) {
            val item = entry as? JsonObject ?: return Failure.UnreadableReply().failed()
            val name =
                (item["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return Failure.UnreadableReply().failed()
            val status = ((item["status"] as? JsonObject)?.get("status") as? JsonPrimitive)?.content
            result +=
                SetupMcpStatus(
                    redactor.redact(name).take(256),
                    status?.takeIf { it in known } ?: "unknown",
                )
        }
        return result.toList().ok()
    }

    /**
     * The shared redactor avoids registering very short values globally; inspection still hides
     * them locally.
     */
    private fun safeConfigInfo(info: JsonObject): String {
        val secrets = ArrayList<String>()
        val credentialFields =
            setOf(
                "key",
                "apikey",
                "password",
                "passwd",
                "secret",
                "clientsecret",
                "token",
                "accesstoken",
                "refreshtoken",
                "access",
                "refresh",
                "authorization",
                "proxyauthorization",
                "xapikey",
                "cookie",
                "setcookie",
            )
        fun collect(value: JsonElement) {
            when (value) {
                is JsonArray -> value.forEach(::collect)
                is JsonObject ->
                    value.forEach { (key, item) ->
                        if (
                            key.lowercase().replace(Regex("[-_]"), "") in credentialFields &&
                                item is JsonPrimitive &&
                                item.isString &&
                                item.content.isNotEmpty()
                        )
                            secrets += item.content
                        if (item is JsonArray || item is JsonObject) collect(item)
                    }
                else -> Unit
            }
        }
        collect(info)
        var clean = info.toString()
        for (secret in secrets.sortedByDescending { it.length }) {
            val encoded = JsonPrimitive(secret).toString().drop(1).dropLast(1)
            clean = clean.replace(encoded, Redactor.MASK)
        }
        return redactor.redact(clean)
    }

    override suspend fun patchConfig(patch: JsonObject): Outcome<Unit> =
        Failure.Unavailable(FeatureId("setupConfigWrites")).failed()
}
