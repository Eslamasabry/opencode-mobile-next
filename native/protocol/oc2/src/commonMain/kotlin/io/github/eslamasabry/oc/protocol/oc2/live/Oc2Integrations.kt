package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** OC2 key/OAuth credentials. Attempts retain their original location, never the gateway's later selection. */
internal class Oc2Integrations(private val gateway: Oc2Gateway, private val redactor: Redactor) {
    private data class Attempt(val integration: String, val location: ServerLocation)
    private val attempts = LinkedHashMap<String, Attempt>()
    private val terminal = LinkedHashMap<String, IntegrationAuthStatus>()
    private val lane = Mutex()

    suspend fun list(): Outcome<ImmutableList<IntegrationInfo>> {
        val at = gateway.location.value
        return when (val response = gateway.json("/integration", query = loc(at))) {
            is Outcome.Failed -> response
            is Outcome.Ok -> {
                val rows = data(response.value) as? JsonArray ?: return invalid()
                rows.filterIsInstance<JsonObject>().mapNotNull(::integration).toImmutableList().ok()
            }
        }
    }

    suspend fun connectKey(id: String, key: String, label: String?): Outcome<Unit> {
        if (!validId(id) || key.isBlank()) return refused()
        redactor.registerSecret(key)
        val at = gateway.location.value
        return gateway.json("/integration/${id.encodeURLPathPart()}/connect/key", HttpMethod.Post,
            buildJsonObject { put("key", key); label?.let { put("label", it) } }, loc(at)).map {}
    }

    suspend fun disconnect(info: IntegrationInfo): Outcome<Unit> {
        val ids = info.connections.filter { it.type == "credential" }.mapNotNull { it.id?.takeIf(::validId) }.distinct()
        if (ids.isEmpty()) return refused()
        for (id in ids) when(val result = gateway.json("/credential/${id.encodeURLPathPart()}", HttpMethod.Delete)) {
            is Outcome.Failed -> return result
            is Outcome.Ok -> Unit
        }
        return Unit.ok()
    }

    suspend fun start(id: String, method: String, inputs: ImmutableMap<String, String>, label: String?): Outcome<IntegrationAuthLaunch> = lane.withLock {
        if (!validId(id) || !validId(method)) return@withLock refused()
        inputs.values.filter { it.isNotEmpty() }.forEach(redactor::registerSecret)
        val at = gateway.location.value
        when(val reply = gateway.json("/integration/${id.encodeURLPathPart()}/connect/oauth", HttpMethod.Post,
            buildJsonObject { put("methodID", method); if(inputs.isNotEmpty()) put("answer", JsonObject(inputs.mapValues { JsonPrimitive(it.value) })); label?.let { put("label",it) } }, loc(at))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val row = data(reply.value) as? JsonObject ?: return@withLock invalid()
                val attempt = row.text("attemptID")?.takeIf(::validId) ?: return@withLock invalid()
                val expiry = expiry(row) ?: if(row["time"] == null || (row["time"] as? JsonObject)?.get("expires") == null) null else return@withLock invalid()
                attempts[attempt] = Attempt(id,at)
                trim(attempts)
                terminal.remove(attempt)
                IntegrationAuthLaunch(attempt,row.text("url").orEmpty(),row.text("instructions").orEmpty(),if(row.text("mode") == "code") IntegrationAuthMode.Code else IntegrationAuthMode.Auto,expiry).ok()
            }
        }
    }

    suspend fun status(id: String): Outcome<IntegrationAuthStatus> = lane.withLock {
        terminal[id]?.let { return@withLock it.ok() }
        val attempt = attempts[id] ?: return@withLock stale()
        when(val reply = gateway.json(path(attempt,id), query = loc(attempt.location))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val row = data(reply.value) as? JsonObject ?: return@withLock invalid()
                val state = when(row.text("status") ?: (reply.value as? JsonObject)?.text("status")) {
                    "pending" -> IntegrationAuthState.Pending
                    "complete" -> IntegrationAuthState.Complete
                    "failed" -> IntegrationAuthState.Failed
                    "expired" -> IntegrationAuthState.Expired
                    else -> return@withLock invalid()
                }
                // Provider status/error text is deliberately not retained.
                val result = IntegrationAuthStatus(state,null,null)
                if(state != IntegrationAuthState.Pending) {
                    attempts.remove(id)
                    terminal[id] = result
                    trim(terminal)
                }
                result.ok()
            }
        }
    }

    suspend fun complete(id: String, code: String?): Outcome<Unit> = lane.withLock {
        if(terminal[id]?.state == IntegrationAuthState.Complete) return@withLock Unit.ok()
        val attempt = attempts[id] ?: return@withLock stale()
        code?.takeIf { it.isNotEmpty() }?.let(redactor::registerSecret)
        gateway.json(path(attempt,id)+"/complete",HttpMethod.Post,buildJsonObject { code?.let { put("code",it) } },loc(attempt.location)).map {}
    }

    suspend fun cancel(id: String): Outcome<Unit> = lane.withLock {
        if(terminal.remove(id) != null) return@withLock Unit.ok()
        val attempt = attempts[id] ?: return@withLock stale()
        gateway.json(path(attempt,id),HttpMethod.Delete,query=loc(attempt.location)).map { attempts.remove(id); Unit }
    }

    private fun path(attempt: Attempt,id: String): String = "/integration/${attempt.integration.encodeURLPathPart()}/connect/oauth/${id.encodeURLPathPart()}"
    private fun <T> trim(map: LinkedHashMap<String,T>) { while(map.size>32) map.remove(map.keys.first()) }
    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun validId(value: String): Boolean = value.isNotBlank() && value != "." && value != ".." && value.length <= 2048 && value.none { it.code < 32 || it.code in 127..159 }
    private fun expiry(row: JsonObject): Long? = ((row["time"] as? JsonObject)?.get("expires") as? JsonPrimitive)?.longOrNull?.takeIf { it>=0 }
    private fun invalid(): Outcome<Nothing> = Failure.UnreadableReply().failed()
    private fun refused(): Outcome<Nothing> = Failure.Rejected(null).failed()
    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun integration(row: JsonObject): IntegrationInfo? {
        val id = row.text("id")?.takeIf(::validId) ?: return null
        val methods = (row["methods"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { method ->
            val type = method.text("type")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val presets = LinkedHashMap<String,String>()
            val prompts = (method["form"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { field ->
                val key = field.text("key")
                if(field["hidden"] == JsonPrimitive(true)) {
                    if(key!=null && field["default"] is JsonPrimitive) presets[key]=(field["default"] as JsonPrimitive).content
                    null
                } else prompt(field)
            }
            IntegrationMethodInfo(type,method.text("id"),method.text("label") ?: type,prompts.toImmutableList(),
                (method["names"] as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>().map { it.content }.toImmutableList(),presets.toImmutableMap())
        }
        val connections=(row["connections"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().map { connection ->
            val type=connection.text("type").orEmpty()
            IntegrationConnectionInfo(type,connection.text("id"),connection.text("label") ?: connection.text("name") ?: type,(connection["status"] as? JsonObject)?.text("status"))
        }
        return IntegrationInfo(id,row.text("name") ?: id,methods.toImmutableList(),connections.toImmutableList(),connections.size)
    }

    private fun prompt(field: JsonObject): JsonObject {
        if(field.text("type") in setOf("text","select")) return field
        val options=(field["options"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { option ->
            val value=option["value"] as? JsonPrimitive ?: return@mapNotNull null
            buildJsonObject { put("value",value.content);put("label",(option["label"] as? JsonPrimitive)?.content ?: value.content) }
        }
        return buildJsonObject {
            field["key"]?.let { put("key",it) }
            put("message",field.text("title") ?: field.text("message") ?: field.text("key").orEmpty())
            put("type",if(options.isNotEmpty() && field["custom"] != JsonPrimitive(true)) "select" else "text")
            if(options.isNotEmpty()) put("options",JsonArray(options))
            put("required",field["required"] == JsonPrimitive(true))
            field["placeholder"]?.let { put("placeholder",it) }
            (field["default"] as? JsonPrimitive)?.let { put("default",it.content) }
            (field["when"] as? JsonObject)?.let { put("when",it) }
        }
    }
}
