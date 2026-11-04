package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Skill
import io.ktor.http.*
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/**
 * Flutter ops_catalog/ops_integrations: location-bound calls, metadata-only recovery, no mutation
 * retries.
 */
internal class Oc2Library(private val owner: Oc2Gateway, private val redactor: Redactor) :
    CatalogGateway,
    McpGateway,
    IntegrationGateway,
    McpRemovalGateway,
    IntegrationCredentialGateway,
    IntegrationCommandGateway,
    IntegrationAuthRecoveryGateway,
    SessionSkillGateway {
    private data class Attempt(val integration: String, val query: Map<String, String?>)

    private data class Attempts(
        val oauth: Map<String, Attempt> = emptyMap(),
        val terminal: Map<String, IntegrationAuthStatus> = emptyMap(),
        val command: Map<Pair<String, String>, Map<String, String?>> = emptyMap(),
    )

    private val attempts = AtomicReference(Attempts())
    private val skillSupport = AtomicReference(true)
    override val sessionSkillsSupported: Boolean
        get() = skillSupport.load()

    private fun update(change: (Attempts) -> Attempts) {
        while (true) {
            val before = attempts.load()
            if (attempts.compareAndSet(before, change(before))) return
        }
    }

    private fun <K, V> Map<K, V>.bounded(): Map<K, V> =
        entries.toList().takeLast(32).associate { it.key to it.value }

    private fun query(): Map<String, String?> = locationQuery(owner.location.value)

    private fun locationQuery(at: ServerLocation) =
        mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)

    private fun segment(value: String) = value.encodeURLPathPart()

    private fun validId(value: String) =
        value.isNotBlank() && value != "." && value != ".." && !CONTROL.containsMatchIn(value)

    private fun data(root: JsonElement): JsonElement = (root as? JsonObject)?.get("data") ?: root

    private fun obj(root: JsonElement): JsonObject? = data(root) as? JsonObject

    private fun rows(root: JsonElement): List<JsonObject>? {
        val values = data(root) as? JsonArray ?: return null
        if (values.size > 10000 || values.any { it !is JsonObject }) return null
        return values.map { it as JsonObject }
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.flag(key: String): Boolean =
        (get(key) as? JsonPrimitive)?.booleanOrNull == true

    /**
     * Authentication failures keep their kind only; provider error text, URLs, codes and extras are
     * discarded.
     */
    private fun safe(failure: Failure): Failure =
        when (failure) {
            is Failure.NotFound -> Failure.NotFound(tag = failure.tag)
            is Failure.Rejected -> Failure.Rejected(failure.status, failure.tag)
            is Failure.Server -> Failure.Server(failure.status, failure.tag)
            is Failure.Network -> Failure.Network(failure.why)
            is Failure.TimedOut -> Failure.TimedOut()
            is Failure.SignIn -> Failure.SignIn(failure.why)
            is Failure.Certificate -> Failure.Certificate(failure.why)
            is Failure.Conflict -> Failure.Conflict(tag = failure.tag)
            is Failure.Busy -> Failure.Busy(failure.kind, failure.tag)
            is Failure.Unavailable -> Failure.Unavailable(failure.feature)
            else -> Failure.Unknown()
        }

    private suspend fun request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
        at: Map<String, String?> = query(),
    ): Outcome<JsonElement> =
        when (val sent = owner.json(path, method, body, at)) {
            is Outcome.Ok -> sent
            is Outcome.Failed -> safe(sent.failure).failed()
        }

    private suspend fun unit(
        path: String,
        method: HttpMethod,
        body: JsonObject? = null,
        at: Map<String, String?> = query(),
    ): Outcome<Unit> = request(path, method, body, at).map {}

    private suspend fun <T> read(path: String, decode: (JsonElement) -> T?): Outcome<T> =
        when (val read = request(path)) {
            is Outcome.Failed -> read
            is Outcome.Ok -> decode(read.value)?.ok() ?: Failure.UnreadableReply().failed()
        }

    override suspend fun skills(): Outcome<ImmutableList<SkillInfo>> =
        read("/skill") { root ->
            rows(root)
                ?.mapNotNull { row ->
                    Oc2Skill.fromJson(row)?.let {
                        SkillInfo(
                            it.id,
                            it.name ?: it.id,
                            it.description,
                            it.location ?: "",
                            it.content ?: "",
                            it.slash,
                        )
                    }
                }
                ?.toImmutableList()
        }

    override suspend fun references(): Outcome<ImmutableList<ReferenceInfo>> =
        read("/reference") { root ->
            rows(root)
                ?.mapNotNull { row ->
                    val name =
                        row.text("name")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    if (row.flag("hidden")) null
                    else
                        ReferenceInfo(
                            name,
                            row.text("path") ?: row.text("location") ?: "",
                            row.text("description"),
                        )
                }
                ?.toImmutableList()
        }

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> =
        read("/mcp") { root ->
            rows(root)?.mapNotNull(::mcpRow)?.toImmutableList()
        }

    private fun mcpRow(row: JsonObject): McpServerInfo? {
        val name = row.text("name")?.takeIf { it.isNotEmpty() } ?: return null
        val status = row["status"]
        return McpServerInfo(
            name,
            (status as? JsonObject)?.text("status") ?: row.text("status") ?: "unknown",
            (status as? JsonObject)?.text("error")?.let(redactor::redact),
        )
    }

    override suspend fun mcpResources(): Outcome<ImmutableList<McpResourceInfo>> =
        read("/mcp/resource") { root ->
            val resources =
                obj(root)?.get("resources") as? JsonArray ?: return@read persistentListOf()
            resources
                .mapNotNull { raw ->
                    val row = raw as? JsonObject ?: return@mapNotNull null
                    val name =
                        row.text("name")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    McpResourceInfo(
                        name,
                        row.text("server") ?: "",
                        row.text("uri") ?: "",
                        row.text("description"),
                        row.text("mimeType"),
                    )
                }
                .toImmutableList()
        }

    override suspend fun connectMcp(name: String): Outcome<Unit> =
        if (validId(name)) unit("/mcp/${segment(name)}/connect", HttpMethod.Post) else invalid()

    override suspend fun disconnectMcp(name: String): Outcome<Unit> =
        if (validId(name)) unit("/mcp/${segment(name)}/disconnect", HttpMethod.Post) else invalid()

    override suspend fun removeMcpServer(name: String): Outcome<Unit> =
        if (validId(name)) unit("/mcp/${segment(name)}", HttpMethod.Delete) else invalid()

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> {
        if (scope != McpConfigScope.RuntimeLocation)
            return Failure.Unavailable(FeatureId("mcpConfigWrites")).failed()
        val name = draft.name.trim()
        if (!validId(name) || draft.timeoutMs?.let { it <= 0 } == true) return invalid()
        fun pairs(values: Map<String, String>): Boolean = values.all { (k, v) ->
            k.isNotBlank() &&
                !Regex("[\\r\\n=]").containsMatchIn(k) &&
                !Regex("[\\r\\n]").containsMatchIn(v)
        }
        val remoteUrl = draft.url?.trim().orEmpty()
        if (draft.kind == McpServerKind.Remote) {
            if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(remoteUrl))
                return invalid()
            val parsed =
                when (val parsed = attempt({ Failure.Rejected(400) }) { Url(remoteUrl) }) {
                    is Outcome.Failed -> return parsed
                    is Outcome.Ok -> parsed.value
                }
            if (
                parsed.protocol.name !in setOf("http", "https") ||
                    parsed.host.isBlank() ||
                    parsed.user != null ||
                    parsed.password != null ||
                    !pairs(draft.headers)
            )
                return invalid()
        } else {
            val command = draft.command.map(String::trim)
            if (command.isEmpty() || command.any(String::isEmpty) || !pairs(draft.environment))
                return invalid()
        }
        val config = buildJsonObject {
            when (draft.kind) {
                McpServerKind.Remote -> {
                    put("type", "remote")
                    put("url", remoteUrl)
                    if (draft.headers.isNotEmpty())
                        put(
                            "headers",
                            JsonObject(draft.headers.mapValues { JsonPrimitive(it.value) }),
                        )
                    if (!draft.detectOAuth) put("oauth", false)
                }
                McpServerKind.Local -> {
                    val command = draft.command.map(String::trim)
                    put("type", "local")
                    put("command", JsonArray(command.map(::JsonPrimitive)))
                    draft.cwd?.trim()?.takeIf(String::isNotEmpty)?.let { put("cwd", it) }
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
        draft.headers.values.forEach(redactor::registerSecret)
        draft.environment.values.forEach(redactor::registerSecret)
        val at = query()
        val inventory = request("/mcp", at = at)
        if (inventory is Outcome.Failed) return inventory
        val existing =
            rows((inventory as Outcome.Ok).value) ?: return Failure.UnreadableReply().failed()
        if (existing.any { it.text("name") == name }) return Failure.Conflict().failed()
        if (at != query()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return unit(
            "/mcp/${segment(name)}",
            HttpMethod.Put,
            buildJsonObject { put("config", config) },
            at,
        )
    }

    override suspend fun integrations(): Outcome<ImmutableList<IntegrationInfo>> =
        read("/integration") { root ->
            rows(root)?.mapNotNull(::integration)?.toImmutableList()
        }

    private fun integration(row: JsonObject): IntegrationInfo? {
        val id = row.text("id")?.takeIf { it.isNotEmpty() } ?: return null
        val methods =
            (row["methods"] as? JsonArray).orEmpty().mapNotNull { raw ->
                val method = raw as? JsonObject ?: return@mapNotNull null
                val type = method.text("type")?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val presets = mutableMapOf<String, String>()
                val prompts =
                    (method["form"] as? JsonArray).orEmpty().mapNotNull { field ->
                        val form = field as? JsonObject ?: return@mapNotNull null
                        if (form.flag("hidden")) {
                            val key = form.text("key")
                            val value = (form["default"] as? JsonPrimitive)?.contentOrNull
                            if (key != null && value != null) presets[key] = value
                            null
                        } else normalizePrompt(form)
                    }
                IntegrationMethodInfo(
                    type,
                    method.text("id"),
                    method.text("label") ?: type,
                    prompts.toImmutableList(),
                    (method["names"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .toImmutableList(),
                    presets.toImmutableMap(),
                )
            }
        val connections =
            (row["connections"] as? JsonArray)
                .orEmpty()
                .mapNotNull { raw ->
                    val c = raw as? JsonObject ?: return@mapNotNull null
                    val type = c.text("type") ?: ""
                    IntegrationConnectionInfo(
                        type,
                        c.text("id"),
                        c.text("label") ?: c.text("name") ?: type,
                        (c["status"] as? JsonObject)?.text("status"),
                    )
                }
                .toImmutableList()
        return IntegrationInfo(
            id,
            row.text("name") ?: id,
            methods.toImmutableList(),
            connections,
            connections.size,
        )
    }

    private fun normalizePrompt(field: JsonObject): JsonObject {
        val type = field.text("type")
        if (type == "text" || type == "select") return field
        val options =
            (field["options"] as? JsonArray).orEmpty().mapNotNull { raw ->
                val option = raw as? JsonObject ?: return@mapNotNull null
                val value = option["value"] as? JsonPrimitive ?: return@mapNotNull null
                buildJsonObject {
                    put("value", value.content)
                    put("label", option.text("label") ?: value.content)
                }
            }
        return buildJsonObject {
            put("key", field["key"] ?: JsonNull)
            put("message", field["title"] ?: field["message"] ?: field["key"] ?: JsonNull)
            put("type", if (options.isNotEmpty() && !field.flag("custom")) "select" else "text")
            if (options.isNotEmpty()) put("options", JsonArray(options))
            put("required", field.flag("required"))
            for (key in listOf("placeholder", "default", "when")) field[key]?.let { put(key, it) }
        }
    }

    override suspend fun connectIntegrationKey(
        id: String,
        key: String,
        label: String?,
    ): Outcome<Unit> {
        if (!validId(id) || key.isBlank()) return invalid()
        redactor.registerSecret(key)
        return unit(
            "/integration/${segment(id)}/connect/key",
            HttpMethod.Post,
            buildJsonObject {
                put("key", key)
                label?.let { put("label", it) }
            },
        )
    }

    override suspend fun disconnectIntegration(integration: IntegrationInfo): Outcome<Unit> {
        val ids =
            integration.connections
                .filter { it.type == "credential" }
                .mapNotNull { it.id }
                .distinct()
        if (ids.isEmpty() || ids.any { !validId(it) }) return invalid()
        for (id in ids) {
            val result = unit("/credential/${segment(id)}", HttpMethod.Delete, at = emptyMap())
            if (result is Outcome.Failed) return result
        }
        return Unit.ok()
    }

    override suspend fun renameCredential(id: String, label: String): Outcome<Unit> {
        val value = label.trim()
        if (
            !validId(id) ||
                value.isEmpty() ||
                value.codePointsCount() > 128 ||
                CONTROL.containsMatchIn(label)
        )
            return invalid()
        return unit(
            "/credential/${segment(id)}",
            HttpMethod.Patch,
            buildJsonObject { put("label", value) },
        )
    }

    override suspend fun activateCredential(id: String): Outcome<Unit> =
        if (validId(id)) unit("/credential/${segment(id)}/activate", HttpMethod.Post) else invalid()

    override suspend fun removeCredential(id: String): Outcome<Unit> =
        if (validId(id)) unit("/credential/${segment(id)}", HttpMethod.Delete) else invalid()

    override fun restoreIntegrationAuthAttempt(
        integrationId: String,
        attemptId: String,
        command: Boolean,
        directory: String?,
        workspace: String?,
    ) {
        if (!validId(integrationId) || !validId(attemptId)) return
        val at = mapOf("location[directory]" to directory, "location[workspace]" to workspace)
        update {
            if (command)
                it.copy(command = (it.command + ((integrationId to attemptId) to at)).bounded())
            else
                it.copy(
                    oauth = (it.oauth + (attemptId to Attempt(integrationId, at))).bounded(),
                    terminal = it.terminal - attemptId,
                )
        }
    }

    override suspend fun startIntegrationOAuth(
        id: String,
        methodId: String,
        inputs: ImmutableMap<String, String>,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> = start(id, methodId, false, inputs, label)

    override suspend fun startIntegrationCommand(
        integrationId: String,
        methodId: String,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> =
        start(integrationId, methodId, true, persistentMapOf(), label)

    private suspend fun start(
        id: String,
        methodId: String,
        command: Boolean,
        inputs: Map<String, String>,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> {
        if (!validId(id) || !validId(methodId)) return invalid()
        val at = query()
        val kind = if (command) "command" else "oauth"
        val sent =
            request(
                "/integration/${segment(id)}/connect/$kind",
                HttpMethod.Post,
                buildJsonObject {
                    put("methodID", methodId)
                    if (inputs.isNotEmpty())
                        put("answer", JsonObject(inputs.mapValues { JsonPrimitive(it.value) }))
                    label?.let { put("label", it) }
                },
                at,
            )
        if (sent is Outcome.Failed) return sent
        val value = obj((sent as Outcome.Ok).value) ?: return Failure.UnreadableReply().failed()
        val attempt =
            value.text("attemptID")?.takeIf(::validId) ?: return Failure.UnreadableReply().failed()
        val expiry =
            expiry(value)
                ?: if (hasInvalidExpiry(value)) return Failure.UnreadableReply().failed() else null
        restoreIntegrationAuthAttempt(
            id,
            attempt,
            command,
            at["location[directory]"],
            at["location[workspace]"],
        )
        return IntegrationAuthLaunch(
                attempt,
                if (command) "" else value.text("url") ?: "",
                if (command) "" else value.text("instructions") ?: "",
                if (!command && value.text("mode") == "code") IntegrationAuthMode.Code
                else IntegrationAuthMode.Auto,
                expiry,
            )
            .ok()
    }

    override suspend fun integrationOAuthStatus(attemptId: String): Outcome<IntegrationAuthStatus> {
        val snapshot = attempts.load()
        snapshot.terminal[attemptId]?.let {
            return it.ok()
        }
        val attempt = snapshot.oauth[attemptId] ?: return stale()
        val result =
            status(
                "/integration/${segment(attempt.integration)}/connect/oauth/${segment(attemptId)}",
                attempt.query,
            )
        if (result is Outcome.Ok && result.value.state != IntegrationAuthState.Pending) {
            update {
                if (it.oauth[attemptId] == attempt)
                    it.copy(
                        oauth = it.oauth - attemptId,
                        terminal = (it.terminal + (attemptId to result.value)).bounded(),
                    )
                else it
            }
        }
        return result
    }

    override suspend fun completeIntegrationOAuth(attemptId: String, code: String?): Outcome<Unit> {
        if (attempts.load().terminal[attemptId]?.state == IntegrationAuthState.Complete)
            return Unit.ok()
        val attempt = attempts.load().oauth[attemptId] ?: return stale()
        code?.let(redactor::registerSecret)
        return unit(
            "/integration/${segment(attempt.integration)}/connect/oauth/${segment(attemptId)}/complete",
            HttpMethod.Post,
            buildJsonObject { code?.let { put("code", it) } },
            attempt.query,
        )
    }

    override suspend fun cancelIntegrationOAuth(attemptId: String): Outcome<Unit> {
        if (attempts.load().terminal.containsKey(attemptId)) {
            update { it.copy(terminal = it.terminal - attemptId) }
            return Unit.ok()
        }
        val attempt = attempts.load().oauth[attemptId] ?: return stale()
        val result =
            unit(
                "/integration/${segment(attempt.integration)}/connect/oauth/${segment(attemptId)}",
                HttpMethod.Delete,
                at = attempt.query,
            )
        if (result is Outcome.Ok)
            update {
                if (it.oauth[attemptId] == attempt) it.copy(oauth = it.oauth - attemptId) else it
            }
        return result
    }

    override suspend fun integrationCommandStatus(
        integrationId: String,
        attemptId: String,
    ): Outcome<IntegrationAuthStatus> {
        if (!validId(integrationId) || !validId(attemptId)) return invalid()
        val at = attempts.load().command[integrationId to attemptId] ?: return stale()
        return status(
            "/integration/${segment(integrationId)}/connect/command/${segment(attemptId)}",
            at,
        )
    }

    override suspend fun cancelIntegrationCommand(
        integrationId: String,
        attemptId: String,
    ): Outcome<Unit> {
        if (!validId(integrationId) || !validId(attemptId)) return invalid()
        val key = integrationId to attemptId
        val at = attempts.load().command[key] ?: return stale()
        val result =
            unit(
                "/integration/${segment(integrationId)}/connect/command/${segment(attemptId)}",
                HttpMethod.Delete,
                at = at,
            )
        if (result is Outcome.Ok) update { it.copy(command = it.command - key) }
        return result
    }

    private suspend fun status(
        path: String,
        at: Map<String, String?>,
    ): Outcome<IntegrationAuthStatus> {
        val sent = request(path, at = at)
        if (sent is Outcome.Failed) return sent
        val value = obj((sent as Outcome.Ok).value) ?: return Failure.UnreadableReply().failed()
        val state =
            when (value.text("status")) {
                "pending" -> IntegrationAuthState.Pending
                "complete" -> IntegrationAuthState.Complete
                "failed" -> IntegrationAuthState.Failed
                "expired" -> IntegrationAuthState.Expired
                else -> return Failure.UnreadableReply().failed()
            }
        if (hasInvalidExpiry(value)) return Failure.UnreadableReply().failed()
        return IntegrationAuthStatus(state, null, expiry(value)).ok()
    }

    private fun expiry(value: JsonObject): Long? =
        ((value["time"] as? JsonObject)?.get("expires") as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.longOrNull
            ?.takeIf { it >= 0 }

    private fun hasInvalidExpiry(value: JsonObject): Boolean {
        val time = value["time"] ?: return false
        if (time == JsonNull) return false
        val obj = time as? JsonObject ?: return true
        val expires = obj["expires"] ?: return false
        return expires != JsonNull && expiry(value) == null
    }

    override suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
    ): Outcome<Unit> {
        if (!sessionSkillsSupported) return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        if (!validId(session.raw) || !validId(skillId)) return invalid()
        val result =
            owner.json(
                "/session/${segment(session.raw)}/skill",
                HttpMethod.Post,
                buildJsonObject {
                    put("skill", skillId)
                    put("resume", resume)
                },
            )
        if (result is Outcome.Ok) return Unit.ok()
        val failure = (result as Outcome.Failed).failure
        val missing =
            failure is Failure.NotFound &&
                failure.tag?.raw !in setOf("SessionNotFoundError", "SkillNotFoundError")
        val unsupported = failure is Failure.Rejected && failure.status in setOf(405, 501)
        if (missing || unsupported) {
            skillSupport.store(false)
            return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        }
        if (
            failure is Failure.Server ||
                failure is Failure.Network ||
                failure is Failure.TimedOut ||
                failure is Failure.UnreadableReply
        ) {
            return Failure.Refused(RefusalReason.DispatchUncertain).failed()
        }
        return safe(failure).failed()
    }

    private fun String.codePointsCount(): Int = indices.count { i ->
        !this[i].isLowSurrogate() || i == 0 || !this[i - 1].isHighSurrogate()
    }

    private fun <T> invalid(): Outcome<T> = Failure.Rejected(400).failed()

    private fun <T> stale(): Outcome<T> = Failure.Refused(RefusalReason.StaleScope).failed()

    private companion object {
        val CONTROL = Regex("[\\x00-\\x1f\\x7f-\\x9f]")
    }
}
