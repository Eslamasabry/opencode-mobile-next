package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.http.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/**
 * Provider sign-ins retain their source even while the shared client changes projects. No write is
 * retried.
 */
internal class Oc2ProviderSignIn(
    private val gateway: Oc2Gateway,
    private val location: () -> ServerLocation,
    private val redactor: Redactor,
) {
    private data class Attempt(val integration: String, val query: Map<String, String?>)

    private val lane = Mutex()
    private val attempts = LinkedHashMap<String, Attempt>()
    private val terminal = LinkedHashMap<String, IntegrationAuthStatus>()

    private fun query(at: ServerLocation = location()): Map<String, String?> =
        mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)

    private fun valid(value: String) =
        value.isNotBlank() && value.length <= 512 && value.none { it.isISOControl() }

    private fun path(id: String) = "/integration/${id.encodeURLPathPart()}"

    private fun failed(): Outcome.Failed = Outcome.Failed(Failure.UnreadableReply())

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun safe(value: String): String = redactor.details(value).text.take(200)

    private fun text(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun <T> remember(map: LinkedHashMap<String, T>, key: String, value: T) {
        map.remove(key)
        map[key] = value
        if (map.size > 32) map.remove(map.keys.first())
    }

    suspend fun integrations(): Outcome<ImmutableList<IntegrationInfo>> {
        val scope = query()
        val result = gateway.json("/integration", query = scope)
        if (result is Outcome.Failed) return result
        val root = (result as Outcome.Ok).value
        redactor.registerCredentialValues(root)
        val rows = data(root) as? JsonArray ?: return failed()
        if (rows.size > 1000) return failed()
        val list = mutableListOf<IntegrationInfo>()
        val ids = mutableSetOf<String>()
        for (raw in rows) {
            val row = raw as? JsonObject ?: return failed()
            val id = text(row, "id")?.takeIf(::valid) ?: return failed()
            if (!ids.add(id) || redactor.containsSecret(id)) return failed()
            val methods = mutableListOf<IntegrationMethodInfo>()
            val methodRows =
                if (row["methods"] == null) JsonArray(emptyList())
                else row["methods"] as? JsonArray ?: return failed()
            if (methodRows.size > 64) return failed()
            for (methodRaw in methodRows) {
                val method = methodRaw as? JsonObject ?: return failed()
                val type = text(method, "type")?.takeIf(::valid) ?: return failed()
                val methodId = text(method, "id")?.takeIf(::valid)
                val prompts = mutableListOf<JsonObject>()
                val presets = mutableMapOf<String, String>()
                val form = method["form"] as? JsonArray
                if (form != null && form.size > 32) return failed()
                for (fieldRaw in form.orEmpty()) {
                    val field = fieldRaw as? JsonObject ?: return failed()
                    val key = text(field, "key")?.takeIf(::valid) ?: return failed()
                    if ((field["hidden"] as? JsonPrimitive)?.booleanOrNull == true) {
                        (field["default"] as? JsonPrimitive)?.contentOrNull?.let {
                            presets[key] = it
                            redactor.registerSecret(it)
                        }
                    } else {
                        prompts += buildJsonObject {
                            put("key", key)
                            put(
                                "message",
                                safe(text(field, "title") ?: text(field, "message") ?: key),
                            )
                            put(
                                "required",
                                (field["required"] as? JsonPrimitive)?.booleanOrNull == true,
                            )
                        }
                    }
                }
                methods +=
                    IntegrationMethodInfo(
                        type,
                        methodId,
                        safe(text(method, "label") ?: type),
                        prompts.toImmutableList(),
                        persistentListOf(),
                        presets.toImmutableMap(),
                    )
            }
            val connections = mutableListOf<IntegrationConnectionInfo>()
            val connectionRows =
                if (row["connections"] == null) JsonArray(emptyList())
                else row["connections"] as? JsonArray ?: return failed()
            if (connectionRows.size > 128) return failed()
            for (connectionRaw in connectionRows) {
                val entry = connectionRaw as? JsonObject ?: return failed()
                val type = text(entry, "type")?.takeIf(::valid) ?: return failed()
                val status = (entry["status"] as? JsonObject)?.let { text(it, "status") }
                connections +=
                    IntegrationConnectionInfo(
                        type,
                        text(entry, "id")?.takeIf(::valid),
                        safe(text(entry, "label") ?: text(entry, "name") ?: type),
                        status?.takeIf {
                            it in setOf("connected", "disconnected", "pending", "failed")
                        },
                    )
            }
            list +=
                IntegrationInfo(
                    id,
                    safe(text(row, "name") ?: id),
                    methods.toImmutableList(),
                    connections.toImmutableList(),
                    connections.size,
                )
        }
        return list.toImmutableList().ok()
    }

    suspend fun connectIntegrationKey(id: String, key: String, label: String?): Outcome<Unit> =
        lane.withLock {
            if (!valid(id) || key.isBlank() || key.length > 65536 || (label?.length ?: 0) > 128)
                return@withLock failed()
            val scope = query()
            redactor.registerSecret(key)
            gateway
                .json(
                    path(id) + "/connect/key",
                    HttpMethod.Post,
                    buildJsonObject {
                        put("key", key)
                        label?.let { put("label", it) }
                    },
                    scope,
                )
                .map { Unit }
        }

    suspend fun disconnectIntegration(integration: IntegrationInfo): Outcome<Unit> = lane.withLock {
        val credentialIds =
            integration.connections
                .filter { it.type == "credential" }
                .mapNotNull { it.id }
                .distinct()
        if (credentialIds.isEmpty() || credentialIds.any { !valid(it) })
            return@withLock Failure.Unavailable(FeatureId("storedCredentialRemoval")).failed()
        for (id in credentialIds) {
            val result = gateway.json("/credential/${id.encodeURLPathPart()}", HttpMethod.Delete)
            if (result is Outcome.Failed) return@withLock result
        }
        Unit.ok()
    }

    suspend fun startIntegrationOAuth(
        id: String,
        methodId: String,
        inputs: ImmutableMap<String, String>,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> = lane.withLock {
        if (
            !valid(id) ||
                !valid(methodId) ||
                inputs.size > 32 ||
                inputs.any { !valid(it.key) || it.value.length > 65536 } ||
                (label?.length ?: 0) > 128
        )
            return@withLock failed()
        val scope = query()
        inputs.values.forEach(redactor::registerSecret)
        val reply =
            gateway.json(
                path(id) + "/connect/oauth",
                HttpMethod.Post,
                buildJsonObject {
                    put("methodID", methodId)
                    if (inputs.isNotEmpty())
                        put(
                            "answer",
                            buildJsonObject { inputs.forEach { (key, value) -> put(key, value) } },
                        )
                    label?.let { put("label", it) }
                },
                scope,
            )
        if (reply is Outcome.Failed) return@withLock reply
        val value = data((reply as Outcome.Ok).value) as? JsonObject ?: return@withLock failed()
        val attempt = text(value, "attemptID")?.takeIf(::valid) ?: return@withLock failed()
        // Flutter accepts a launch without a link: device-code methods only carry instructions.
        val url = text(value, "url").orEmpty()
        if (url.length > 8192) return@withLock failed()
        remember(attempts, attempt, Attempt(id, scope))
        terminal.remove(attempt)
        val expires = ((value["time"] as? JsonObject)?.get("expires") as? JsonPrimitive)?.longOrNull
        IntegrationAuthLaunch(
                attempt,
                url,
                text(value, "instructions")?.let { redactor.details(it).text.take(2000) }.orEmpty(),
                if (text(value, "mode") == "code") IntegrationAuthMode.Code
                else IntegrationAuthMode.Auto,
                expires,
            )
            .ok()
    }

    private suspend fun status(attemptId: String): Outcome<IntegrationAuthStatus> {
        terminal[attemptId]?.let {
            return it.ok()
        }
        val attempt =
            attempts[attemptId] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val reply =
            gateway.json(
                path(attempt.integration) + "/connect/oauth/${attemptId.encodeURLPathPart()}",
                query = attempt.query,
            )
        if (reply is Outcome.Failed) return reply
        val value = data((reply as Outcome.Ok).value) as? JsonObject ?: return failed()
        val state =
            when (text(value, "status")) {
                "pending" -> IntegrationAuthState.Pending
                "complete" -> IntegrationAuthState.Complete
                "failed" -> IntegrationAuthState.Failed
                "expired" -> IntegrationAuthState.Expired
                else -> return failed()
            }
        val result = IntegrationAuthStatus(state, null, null)
        if (state != IntegrationAuthState.Pending) {
            attempts.remove(attemptId)
            remember(terminal, attemptId, result)
        }
        return result.ok()
    }

    suspend fun integrationOAuthStatus(attemptId: String): Outcome<IntegrationAuthStatus> =
        lane.withLock {
            status(attemptId)
        }

    suspend fun completeIntegrationOAuth(attemptId: String, code: String?): Outcome<Unit> =
        lane.withLock {
            if (terminal[attemptId]?.state == IntegrationAuthState.Complete)
                return@withLock Unit.ok()
            val attempt =
                attempts[attemptId]
                    ?: return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            if ((code?.length ?: 0) > 65536) return@withLock failed()
            code?.let(redactor::registerSecret)
            gateway
                .json(
                    path(attempt.integration) +
                        "/connect/oauth/${attemptId.encodeURLPathPart()}/complete",
                    HttpMethod.Post,
                    buildJsonObject { code?.let { put("code", it) } },
                    attempt.query,
                )
                .map { Unit }
        }

    suspend fun cancelIntegrationOAuth(attemptId: String): Outcome<Unit> = lane.withLock {
        if (terminal.remove(attemptId) != null) return@withLock Unit.ok()
        val attempt =
            attempts[attemptId]
                ?: return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        val reply =
            gateway.json(
                path(attempt.integration) + "/connect/oauth/${attemptId.encodeURLPathPart()}",
                HttpMethod.Delete,
                query = attempt.query,
            )
        if (reply is Outcome.Ok) attempts.remove(attemptId)
        reply.map { Unit }
    }
}
