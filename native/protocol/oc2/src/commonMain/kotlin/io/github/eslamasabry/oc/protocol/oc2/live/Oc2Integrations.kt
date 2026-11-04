package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.IntegrationGateway
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/**
 * OC2's integration-scoped attempts and location-scoped MCP runtime configuration (protocol notes
 * §11).
 */
internal class Oc2Integrations(
    private val gateway: Oc2Gateway,
    private val location: () -> ServerLocation,
    private val redactor: Redactor,
) : IntegrationGateway, McpGateway {
    private data class Attempt(val integration: String, val query: Map<String, String?>)

    private val attempts = LinkedHashMap<String, Attempt>()
    private val terminal = LinkedHashMap<String, IntegrationAuthStatus>()

    private fun query() =
        location().let {
            mapOf("location[directory]" to it.directory, "location[workspace]" to it.workspace)
        }

    private fun validId(id: String) =
        id.isNotBlank() &&
            id != "." &&
            id != ".." &&
            id.length <= 2048 &&
            id.none { it.code < 32 || it.code in 127..159 }

    private fun segment(value: String) = value.encodeURLPathPart()

    private fun bad(): Outcome<Nothing> = Failure.Rejected(400).failed()

    private fun unreadable(): Outcome<Nothing> = Failure.UnreadableReply().failed()

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun text(value: JsonElement?): String? =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.string(key: String): String? = text(this[key])

    private fun clean(value: String): String =
        redactor.redact(value).replace(Regex("[\\x00-\\x1f\\x7f-\\x9f]"), " ").take(160)

    override suspend fun integrations(): Outcome<ImmutableList<IntegrationInfo>> {
        val result = gateway.json("/integration", query = query())
        if (result is Outcome.Failed) return result
        val root = (result as Outcome.Ok).value
        redactor.registerCredentialValues(root)
        val rows = data(root) as? JsonArray ?: return unreadable()
        val parsed = rows.mapNotNull { raw ->
            val obj = raw as? JsonObject ?: return@mapNotNull null
            val key = obj.string("id")?.takeIf(::validId) ?: return@mapNotNull null
            val methods =
                (obj["methods"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull methods@{ entry ->
                        val method = entry as? JsonObject ?: return@methods null
                        val type =
                            method.string("type")?.takeIf {
                                it in setOf("key", "oauth", "command", "env")
                            } ?: return@methods null
                        val preset = LinkedHashMap<String, String>()
                        val prompts =
                            (method["form"] as? JsonArray)
                                .orEmpty()
                                .mapNotNull fields@{ field ->
                                    val source = field as? JsonObject ?: return@fields null
                                    val fieldKey = source.string("key") ?: return@fields null
                                    (source["default"] as? JsonPrimitive)
                                        ?.takeIf { it.isString }
                                        ?.content
                                        ?.let(redactor::registerSecret)
                                    if (
                                        (source["hidden"] as? JsonPrimitive)?.booleanOrNull == true
                                    ) {
                                        source["default"]?.let {
                                            preset[fieldKey] =
                                                (it as? JsonPrimitive)?.content ?: return@let
                                        }
                                        return@fields null
                                    }
                                    val options = source["options"] as? JsonArray
                                    buildJsonObject {
                                        put("key", fieldKey)
                                        put(
                                            "message",
                                            clean(
                                                source.string("title")
                                                    ?: source.string("message")
                                                    ?: fieldKey
                                            ),
                                        )
                                        put(
                                            "type",
                                            if (
                                                !options.isNullOrEmpty() &&
                                                    (source["custom"] as? JsonPrimitive)
                                                        ?.booleanOrNull != true
                                            )
                                                "select"
                                            else "text",
                                        )
                                        if (options != null) put("options", options)
                                        put(
                                            "required",
                                            (source["required"] as? JsonPrimitive)?.booleanOrNull ==
                                                true,
                                        )
                                        source["default"]?.let { put("default", it) }
                                    }
                                }
                                .toImmutableList()
                        IntegrationMethodInfo(
                            type = type,
                            id = method.string("id"),
                            label = clean(method.string("label") ?: type),
                            prompts = prompts,
                            environmentNames =
                                (method["names"] as? JsonArray)
                                    .orEmpty()
                                    .mapNotNull(::text)
                                    .map(::clean)
                                    .toImmutableList(),
                            presetAnswers = preset.toImmutableMap(),
                        )
                    }
                    .toImmutableList()
            val connections =
                (obj["connections"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull connections@{ entry ->
                        val connection = entry as? JsonObject ?: return@connections null
                        val type =
                            connection.string("type")?.takeIf { it == "credential" || it == "env" }
                                ?: return@connections null
                        IntegrationConnectionInfo(
                            type,
                            connection.string("id"),
                            clean(connection.string("label") ?: connection.string("name") ?: type),
                            (connection["status"] as? JsonObject)?.string("status")?.takeIf {
                                it in setOf("connected", "pending", "failed", "expired")
                            },
                        )
                    }
                    .toImmutableList()
            IntegrationInfo(
                key,
                clean(obj.string("name") ?: key),
                methods,
                connections,
                connections.size,
            )
        }
        return parsed.toImmutableList().ok()
    }

    override suspend fun connectIntegrationKey(
        id: String,
        key: String,
        label: String?,
    ): Outcome<Unit> {
        if (!validId(id) || key.isBlank()) return bad()
        redactor.registerSecret(key)
        return gateway
            .json(
                "/integration/${segment(id)}/connect/key",
                HttpMethod.Post,
                buildJsonObject {
                    put("key", key)
                    label?.let { put("label", it) }
                },
                query(),
            )
            .map {}
    }

    override suspend fun disconnectIntegration(integration: IntegrationInfo): Outcome<Unit> {
        val ids =
            integration.connections
                .filter { it.type == "credential" }
                .mapNotNull { it.id }
                .distinct()
        if (ids.isEmpty() || ids.any { !validId(it) }) return bad()
        for (credential in ids) {
            val result = gateway.json("/credential/${segment(credential)}", HttpMethod.Delete)
            if (result is Outcome.Failed) return result
        }
        return Unit.ok()
    }

    override suspend fun startIntegrationOAuth(
        id: String,
        methodId: String,
        inputs: ImmutableMap<String, String>,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> {
        if (!validId(id) || !validId(methodId)) return bad()
        inputs.values.forEach(redactor::registerSecret)
        val at = query()
        val result =
            gateway.json(
                "/integration/${segment(id)}/connect/oauth",
                HttpMethod.Post,
                buildJsonObject {
                    put("methodID", methodId)
                    if (inputs.isNotEmpty())
                        put("answer", JsonObject(inputs.mapValues { JsonPrimitive(it.value) }))
                    label?.let { put("label", it) }
                },
                at,
            )
        if (result is Outcome.Failed) return result
        val root = data((result as Outcome.Ok).value) as? JsonObject ?: return unreadable()
        redactor.registerCredentialValues(root)
        val attempt = root.string("attemptID")?.takeIf(::validId) ?: return unreadable()
        val url = root.string("url") ?: return unreadable()
        val mode =
            when (root.string("mode")) {
                "code" -> IntegrationAuthMode.Code
                "auto" -> IntegrationAuthMode.Auto
                else -> return unreadable()
            }
        attempts[attempt] = Attempt(id, at)
        while (attempts.size > 32) attempts.remove(attempts.keys.first())
        terminal.remove(attempt)
        return IntegrationAuthLaunch(
                attempt,
                url,
                "",
                mode,
                ((root["time"] as? JsonObject)?.get("expires") as? JsonPrimitive)?.longOrNull,
            )
            .ok()
    }

    override suspend fun integrationOAuthStatus(attemptId: String): Outcome<IntegrationAuthStatus> {
        terminal[attemptId]?.let {
            return it.ok()
        }
        val attempt =
            attempts[attemptId] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val result =
            gateway.json(
                "/integration/${segment(attempt.integration)}/connect/oauth/${segment(attemptId)}",
                query = attempt.query,
            )
        if (result is Outcome.Failed) return result
        val root = data((result as Outcome.Ok).value) as? JsonObject ?: return unreadable()
        val status =
            when (root.string("status")) {
                "pending" -> IntegrationAuthState.Pending
                "complete" -> IntegrationAuthState.Complete
                "failed" -> IntegrationAuthState.Failed
                "expired" -> IntegrationAuthState.Expired
                else -> return unreadable()
            }
        // Provider messages may contain credentials and executable instructions: only the fixed
        // state leaves here.
        val answer = IntegrationAuthStatus(status, null, null)
        if (status != IntegrationAuthState.Pending) {
            attempts.remove(attemptId)
            terminal[attemptId] = answer
            while (terminal.size > 32) terminal.remove(terminal.keys.first())
        }
        return answer.ok()
    }

    override suspend fun completeIntegrationOAuth(attemptId: String, code: String?): Outcome<Unit> {
        if (terminal[attemptId]?.state == IntegrationAuthState.Complete) return Unit.ok()
        val attempt =
            attempts[attemptId] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        code?.let(redactor::registerSecret)
        return gateway
            .json(
                "/integration/${segment(attempt.integration)}/connect/oauth/${segment(attemptId)}/complete",
                HttpMethod.Post,
                buildJsonObject { code?.let { put("code", it) } },
                attempt.query,
            )
            .map {}
    }

    override suspend fun cancelIntegrationOAuth(attemptId: String): Outcome<Unit> {
        if (terminal.remove(attemptId) != null) return Unit.ok()
        val attempt =
            attempts[attemptId] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        return gateway
            .json(
                "/integration/${segment(attempt.integration)}/connect/oauth/${segment(attemptId)}",
                HttpMethod.Delete,
                query = attempt.query,
            )
            .map {
                attempts.remove(attemptId)
                Unit
            }
    }

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> = mcpServersAt(query())

    private suspend fun mcpServersAt(
        at: Map<String, String?>
    ): Outcome<ImmutableList<McpServerInfo>> {
        val result = gateway.json("/mcp", query = at)
        if (result is Outcome.Failed) return result
        val rows = data((result as Outcome.Ok).value) as? JsonArray ?: return unreadable()
        return rows
            .mapNotNull { row ->
                val obj = row as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name")?.takeIf(::validId) ?: return@mapNotNull null
                val status =
                    ((obj["status"] as? JsonObject)?.string("status") ?: obj.string("status"))
                        ?.takeIf {
                            it in
                                setOf(
                                    "connected",
                                    "pending",
                                    "disabled",
                                    "failed",
                                    "needs_auth",
                                    "needs_client_registration",
                                )
                        } ?: "unknown"
                McpServerInfo(name, status, null)
            }
            .toImmutableList()
            .ok()
    }

    override suspend fun mcpResources(): Outcome<ImmutableList<McpResourceInfo>> {
        val result = gateway.json("/mcp/resource", query = query())
        if (result is Outcome.Failed) return result
        val root = data((result as Outcome.Ok).value) as? JsonObject ?: return unreadable()
        val rows = root["resources"] as? JsonArray ?: return unreadable()
        return rows
            .mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name") ?: return@mapNotNull null
                McpResourceInfo(
                    clean(name),
                    clean(obj.string("server").orEmpty()),
                    obj.string("uri").orEmpty(),
                    obj.string("description")?.let(::clean),
                    obj.string("mimeType"),
                )
            }
            .toImmutableList()
            .ok()
    }

    override suspend fun connectMcp(name: String): Outcome<Unit> = mcpAction(name, "connect")

    override suspend fun disconnectMcp(name: String): Outcome<Unit> = mcpAction(name, "disconnect")

    private suspend fun mcpAction(name: String, action: String): Outcome<Unit> =
        if (!validId(name)) bad()
        else gateway.json("/mcp/${segment(name)}/$action", HttpMethod.Post, query = query()).map {}

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> {
        if (scope != McpConfigScope.RuntimeLocation)
            return Failure.Unavailable(FeatureId("mcpConfigWrites")).failed()
        val name = draft.name.trim()
        if (!validId(name) || draft.timeoutMs?.let { it <= 0 } == true) return bad()
        if (
            (draft.headers + draft.environment).any { (key, value) ->
                key.isBlank() ||
                    key.any { it == '=' || it.code < 32 || it.code == 127 } ||
                    value.any { it.code < 32 || it.code == 127 }
            }
        )
            return bad()
        draft.headers.values.forEach(redactor::registerSecret)
        draft.environment.values.forEach(redactor::registerSecret)
        val config = buildJsonObject {
            when (draft.kind) {
                McpServerKind.Remote -> {
                    val raw = draft.url?.trim().orEmpty()
                    val url =
                        try {
                            Url(raw)
                        } catch (_: IllegalArgumentException) {
                            return bad()
                        }
                    if (
                        url.protocol.name !in setOf("https", "http") ||
                            url.host.isBlank() ||
                            url.user != null ||
                            url.password != null ||
                            '@' in raw.substringAfter("://").substringBefore('/')
                    )
                        return bad()
                    put("type", "remote")
                    put("url", raw)
                    if (draft.headers.isNotEmpty())
                        put(
                            "headers",
                            JsonObject(draft.headers.mapValues { JsonPrimitive(it.value) }),
                        )
                    if (!draft.detectOAuth) put("oauth", false)
                }
                McpServerKind.Local -> {
                    if (
                        draft.command.isEmpty() ||
                            draft.command.any {
                                it.isBlank() || it.any { c -> c.code < 32 || c.code == 127 }
                            }
                    )
                        return bad()
                    put("type", "local")
                    put("command", JsonArray(draft.command.map { JsonPrimitive(it.trim()) }))
                    draft.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
                    if (draft.environment.isNotEmpty())
                        put(
                            "environment",
                            JsonObject(draft.environment.mapValues { JsonPrimitive(it.value) }),
                        )
                }
            }
            draft.timeoutMs?.let { timeout ->
                put(
                    "timeout",
                    buildJsonObject {
                        put("startup", timeout)
                        put("catalog", timeout)
                        put("execution", timeout)
                    },
                )
            }
        }
        val at = query()
        when (val existing = mcpServersAt(at)) {
            is Outcome.Failed -> return existing
            is Outcome.Ok ->
                if (existing.value.any { it.name == name }) return Failure.Conflict().failed()
        }
        return gateway
            .json(
                "/mcp/${segment(name)}",
                HttpMethod.Put,
                buildJsonObject { put("config", config) },
                at,
            )
            .map {}
    }
}
