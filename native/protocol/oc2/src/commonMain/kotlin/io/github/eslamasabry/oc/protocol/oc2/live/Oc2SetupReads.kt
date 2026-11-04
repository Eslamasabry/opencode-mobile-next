package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.model.McpResourceInfo
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.ReferenceInfo
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Skill
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*

/**
 * Flutter setup_config_adapter.dart and ops_extras.dart: same authenticated, dialect-aware reads.
 */
internal suspend fun Oc2Gateway.inspectConfig(
    location: ServerLocation,
    redactor: Redactor,
): Outcome<JsonElement> {
    val read = json("/config", query = loc(location))
    if (read is Outcome.Failed) return inspectionFailure(read.failure).failed()
    val body = (read as Outcome.Ok).value
    redactor.registerCredentialValues(body)
    val rows = (body as? JsonObject)?.get("data") ?: body
    if (rows !is JsonArray || rows.size > MAX_INSPECTION_ROWS)
        return Failure.UnreadableReply().failed()
    val sources = rows.map { value ->
        val row = value as? JsonObject ?: return Failure.UnreadableReply().failed()
        val type = row["type"] as? JsonPrimitive
        if (type?.isString != true) return Failure.UnreadableReply().failed()
        buildJsonObject {
            put("type", type)
            (row["path"] as? JsonPrimitive)?.takeIf { it.isString }?.let { put("path", it) }
            (row["info"] as? JsonObject)?.let { put("info", it) }
        }
    }
    return buildJsonObject { put("data", JsonArray(sources)) }.ok()
}

internal suspend fun Oc2Gateway.inspectMcp(location: ServerLocation): Outcome<JsonElement> {
    val read = json("/mcp", query = loc(location))
    if (read is Outcome.Failed) return inspectionFailure(read.failure).failed()
    val body = (read as Outcome.Ok).value
    val rows =
        (body as? JsonObject)?.get("data") as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (rows.size > MAX_INSPECTION_ROWS) return Failure.UnreadableReply().failed()
    if (rows.any { it !is JsonObject || (it["name"] as? JsonPrimitive)?.isString != true })
        return Failure.UnreadableReply().failed()
    // Only status is relevant to setup; drop server errors and arbitrary status metadata.
    return buildJsonObject {
        put(
            "data",
            JsonArray(
                rows.map { value ->
                    val row = value as JsonObject
                    buildJsonObject {
                        put("name", row.getValue("name"))
                        put(
                            "status",
                            buildJsonObject {
                                ((row["status"] as? JsonObject)?.get("status") as? JsonPrimitive
                                        ?: row["status"] as? JsonPrimitive)
                                    ?.takeIf { it.isString }
                                    ?.let { put("status", it) }
                            },
                        )
                    }
                }
            ),
        )
    }
        .ok()
}

internal suspend fun Oc2Gateway.inspectPlugins(location: ServerLocation): Outcome<JsonElement> {
    val read = json("/plugin", query = loc(location))
    if (read is Outcome.Failed) return inspectionFailure(read.failure).failed()
    val rows =
        ((read as Outcome.Ok).value as? JsonObject)?.get("data") as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (rows.size > MAX_INSPECTION_ROWS) return Failure.UnreadableReply().failed()
    val safe = rows.map { value ->
        val row = value as? JsonObject ?: return Failure.UnreadableReply().failed()
        val source = row["source"] as? JsonObject ?: return Failure.UnreadableReply().failed()
        val type = source["type"] as? JsonPrimitive
        val status =
            (row["status"] ?: (row["state"] as? JsonObject)?.get("status")) as? JsonPrimitive
        val features = row["features"] as? JsonObject
        val tui =
            (row["tui"] ?: features?.let { it["tui"] ?: JsonPrimitive(false) }) as? JsonPrimitive
        if (
            type?.isString != true ||
                status?.isString != true ||
                tui == null ||
                tui.isString ||
                tui.booleanOrNull == null
        )
            return Failure.UnreadableReply().failed()
        buildJsonObject {
            (row["id"] as? JsonPrimitive)
                ?.takeIf {
                    it.isString && it.content.length <= 200 && PLUGIN_ID.matches(it.content)
                }
                ?.let { put("id", it) }
            put("status", status.content.takeIf { it in setOf("active", "failed") } ?: "unknown")
            put("tui", tui)
            put(
                "source",
                buildJsonObject {
                    put(
                        "type",
                        type.content.takeIf { it in setOf("builtin", "package", "local", "sdk") }
                            ?: "unknown",
                    )
                    (source["package"] as? JsonPrimitive)
                        ?.takeIf {
                            type.content == "package" &&
                                it.isString &&
                                it.content.length <= 200 &&
                                NPM_PACKAGE.matches(it.content)
                        }
                        ?.let { put("package", it) }
                },
            )
        }
    }
    return buildJsonObject { put("data", JsonArray(safe)) }.ok()
}

/** MCP inventory and runtime controls. No persistent configuration removal is promised. */
internal suspend fun Oc2Gateway.mcpReadServers(
    redactor: Redactor
): Outcome<ImmutableList<McpServerInfo>> {
    val pinned = location.value
    val read = inspectMcp(pinned)
    if (read is Outcome.Failed) return read
    val rows = (((read as Outcome.Ok).value as JsonObject).getValue("data") as JsonArray)
    val servers = rows.map { value ->
        val row = value as JsonObject
        val name = (row.getValue("name") as JsonPrimitive).content
        if (!validMcpName(name, redactor)) return Failure.UnreadableReply().failed()
        val status = ((row["status"] as? JsonObject)?.get("status") as? JsonPrimitive)?.content
        McpServerInfo(name, status?.takeIf { it in MCP_STATUSES } ?: "unknown", null)
    }
    return servers.toImmutableList().ok()
}

internal suspend fun Oc2Gateway.mcpReadResources(
    redactor: Redactor
): Outcome<ImmutableList<McpResourceInfo>> {
    val pinned = location.value
    val read = json("/mcp/resource", query = loc(pinned))
    if (read is Outcome.Failed) return inspectionFailure(read.failure).failed()
    val root =
        (read as Outcome.Ok).value as? JsonObject ?: return Failure.UnreadableReply().failed()
    val data = root["data"] as? JsonObject ?: return Failure.UnreadableReply().failed()
    val resources = data["resources"] as? JsonArray ?: return Failure.UnreadableReply().failed()
    if (resources.size > MAX_INSPECTION_ROWS) return Failure.UnreadableReply().failed()
    val safe = resources.map { value ->
        val row = value as? JsonObject ?: return Failure.UnreadableReply().failed()
        fun text(key: String): String? =
            (row[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val name =
            text("name")?.takeIf { it.isNotBlank() } ?: return Failure.UnreadableReply().failed()
        McpResourceInfo(
            redactor.diagnostics(name),
            redactor.diagnostics(text("server").orEmpty()),
            redactor.diagnostics(text("uri").orEmpty()),
            text("description")?.let { redactor.diagnostics(it) },
            text("mimeType")?.let { redactor.diagnostics(it) },
        )
    }
    return safe.toImmutableList().ok()
}

internal suspend fun Oc2Gateway.mcpRuntimeAction(
    name: String,
    action: String,
    redactor: Redactor,
): Outcome<Unit> {
    if (!validMcpName(name, redactor) || action !in setOf("connect", "disconnect", "remove"))
        return Failure.Refused(RefusalReason.StaleScope).failed()
    val pinned = location.value
    val path = "/mcp/${name.encodeURLPathPart()}" + if (action == "remove") "" else "/$action"
    val result =
        json(
            path,
            if (action == "remove") HttpMethod.Delete else HttpMethod.Post,
            query = loc(pinned),
        )
    return when (result) {
        is Outcome.Failed -> inspectionFailure(result.failure).failed()
        is Outcome.Ok -> Unit.ok()
    }
}

/** The server's declared skill catalog, pinned to this call's project location. */
internal suspend fun Oc2Gateway.readSkills(): Outcome<ImmutableList<SkillInfo>> {
    val pinned = location.value
    val read = json("/skill", query = loc(pinned))
    if (read is Outcome.Failed) return inspectionFailure(read.failure).failed()
    val rows =
        ((read as Outcome.Ok).value as? JsonObject)?.get("data") as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (rows.size > MAX_INSPECTION_ROWS) return Failure.UnreadableReply().failed()
    val skills = rows.map { value ->
        val row = value as? JsonObject ?: return Failure.UnreadableReply().failed()
        val skill = Oc2Skill.fromJson(row) ?: return Failure.UnreadableReply().failed()
        if (skill.id.isBlank()) return Failure.UnreadableReply().failed()
        SkillInfo(
            skill.id,
            skill.name ?: skill.id,
            skill.description,
            skill.location.orEmpty(),
            skill.content.orEmpty(),
            skill.slash,
        )
    }
    return skills.toImmutableList().ok()
}

/** Hidden references never cross into the product inventory; malformed visible rows are unknown. */
internal suspend fun Oc2Gateway.readReferences(): Outcome<ImmutableList<ReferenceInfo>> {
    val pinned = location.value
    val read = json("/reference", query = loc(pinned))
    if (read is Outcome.Failed) return inspectionFailure(read.failure).failed()
    val rows =
        ((read as Outcome.Ok).value as? JsonObject)?.get("data") as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (rows.size > MAX_INSPECTION_ROWS) return Failure.UnreadableReply().failed()
    val references = mutableListOf<ReferenceInfo>()
    for (value in rows) {
        val row = value as? JsonObject ?: return Failure.UnreadableReply().failed()
        val hidden = row["hidden"]
        if (
            hidden != null &&
                hidden != JsonNull &&
                (hidden !is JsonPrimitive || hidden.isString || hidden.booleanOrNull == null)
        )
            return Failure.UnreadableReply().failed()
        if ((hidden as? JsonPrimitive)?.booleanOrNull == true) continue
        fun text(key: String): String? =
            (row[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val name =
            text("name")?.takeIf { it.isNotBlank() } ?: return Failure.UnreadableReply().failed()
        for (key in listOf("path", "location", "description")) {
            val member = row[key]
            if (
                member != null &&
                    member != JsonNull &&
                    (member !is JsonPrimitive || !member.isString)
            )
                return Failure.UnreadableReply().failed()
        }
        references +=
            ReferenceInfo(name, text("path") ?: text("location").orEmpty(), text("description"))
    }
    return references.toImmutableList().ok()
}

private fun validMcpName(name: String, redactor: Redactor): Boolean =
    name.isNotBlank() &&
        name.length <= 200 &&
        name == name.trim() &&
        name.none { it.code < 0x20 || it.code == 0x7f } &&
        !redactor.containsSecret(name)

/** Error bodies, request ids, server tags and details never leave these inspection calls. */
private fun inspectionFailure(failure: Failure): Failure =
    when (failure) {
        is Failure.Network -> Failure.Network(failure.why)
        is Failure.TimedOut -> Failure.TimedOut()
        is Failure.Certificate -> Failure.Certificate(failure.why)
        is Failure.SignIn -> Failure.SignIn(failure.why)
        is Failure.NotFound -> Failure.NotFound()
        is Failure.Conflict -> Failure.Conflict()
        is Failure.Busy -> Failure.Busy(failure.kind)
        is Failure.Server -> Failure.Server(failure.status)
        is Failure.Rejected -> Failure.Rejected(failure.status)
        is Failure.Unavailable -> Failure.Unavailable(failure.feature)
        is Failure.Refused -> Failure.Refused(failure.reason)
        is Failure.Incompatible -> Failure.Incompatible(failure.kind)
        else -> Failure.UnreadableReply()
    }

private const val MAX_INSPECTION_ROWS = 1000
private val PLUGIN_ID = Regex("""^@?[A-Za-z0-9][A-Za-z0-9_./@-]*$""")
private val NPM_PACKAGE =
    Regex("""^(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*(?:@[A-Za-z0-9][A-Za-z0-9._+-]*)?$""")
private val MCP_STATUSES = setOf("connected", "pending", "disabled", "failed", "needs_auth")
