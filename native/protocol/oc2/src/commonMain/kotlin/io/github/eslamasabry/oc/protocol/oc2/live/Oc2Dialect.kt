package io.github.eslamasabry.oc.protocol.oc2.live

import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.*

/** Frozen lib/api2/dialect.dart: translation happens before the single network dispatch. */
internal enum class Oc2Dialect {
    Beta,
    Stable;

    fun route(
        path: String,
        method: HttpMethod,
        body: JsonObject?,
        query: Map<String, String?>,
    ): Oc2Route {
        fun same(
            p: String = path,
            m: HttpMethod = method,
            b: JsonObject? = body,
            q: Map<String, String?> = query,
        ) = Oc2Route(p, m, b, q)
        if (this == Beta) return same()
        if (path == "/workspace" || path.startsWith("/workspace/"))
            return same().copy(removedFeature = "managedWorkspaces")
        if (method == HttpMethod.Patch && ShellTimeout.matches(path))
            return same().copy(removedFeature = "managedShellTimeout")
        if (method == HttpMethod.Get) {
            when (path) {
                "/health",
                "/server" -> return same(p = "/info")
                "/project/current" -> return same(p = "/location")
                "/form/request" -> return same(p = "/form")
            }
        }
        Worktree.matchEntire(path)?.let {
            val project = it.groupValues[1].decodeURLPart()
            if (it.groupValues[2].isNotEmpty())
                return same(
                    p = "/worktree/refresh",
                    b = buildJsonObject { put("projectID", project) },
                )
            if (method == HttpMethod.Get)
                return same(p = "/worktree", q = query + ("projectID" to project))
            return same(
                p = "/worktree",
                b =
                    JsonObject(
                        (body ?: JsonObject(emptyMap())) + ("projectID" to JsonPrimitive(project))
                    ),
            )
        }
        if (
            McpServer.matches(path) &&
                (method != HttpMethod.Get ||
                    path.endsWith("/connect") ||
                    path.endsWith("/disconnect"))
        )
            return same(p = "/experimental$path")
        if (
            (path == "/generate" && method == HttpMethod.Post) ||
                path == "/session/import" ||
                path == "/session/stats"
        )
            return same(p = "/experimental$path")
        if (method == HttpMethod.Post) {
            InboxDelivery.matchEntire(path)?.let {
                return same(
                    p = "/session/${it.groupValues[1]}/inbox/${it.groupValues[2]}",
                    m = HttpMethod.Patch,
                    b = buildJsonObject { put("delivery", it.groupValues[3]) },
                )
            }
            FormCancel.matchEntire(path)?.let {
                return same(
                    p = "/session/${it.groupValues[1]}/form/${it.groupValues[2]}",
                    m = HttpMethod.Delete,
                    b = null,
                )
            }
        }
        if (method == HttpMethod.Get)
            FormState.matchEntire(path)?.let {
                return same(p = "/session/${it.groupValues[1]}/form/${it.groupValues[2]}")
            }
        val session = SessionScoped.matchEntire(path) ?: return same()
        val id = session.groupValues[1]
        val rest = session.groupValues[2]
        if (
            rest in setOf("skill", "wait", "export", "instructions/entries") ||
                rest.startsWith("instructions/entries/")
        )
            return same(p = "/experimental$path")
        if (method != HttpMethod.Post) return same()
        when {
            rest == "rename" -> return same(p = "/session/$id", m = HttpMethod.Patch)
            rest == "revert/clear" ->
                return same(p = "/session/$id/revert", m = HttpMethod.Delete, b = null)
            rest == "interrupt" && "continue" in query ->
                return same(q = query - "continue" + ("resume" to query["continue"]))
            rest == "fork" -> {
                val boundary = body?.get("boundary") as? JsonObject
                return same(
                    b =
                        buildJsonObject {
                            if ((boundary?.get("type") as? JsonPrimitive)?.content == "before")
                                boundary?.get("messageID")?.let { put("before", it) }
                        }
                )
            }
            rest == "command" && body != null && "command" in body ->
                return same(b = JsonObject(body - "command" + ("name" to body.getValue("command"))))
            PermissionReply.matches(rest) && body != null && "reply" in body ->
                return same(b = JsonObject(body - "reply" + ("decision" to body.getValue("reply"))))
        }
        return same()
    }

    /** Preserve the beta-facing envelope for the few stable reads whose shape changed. */
    fun response(path: String, method: HttpMethod, value: JsonElement): JsonElement {
        if (this == Beta || method != HttpMethod.Get || value !is JsonObject) return value
        return when {
            path == "/health" -> JsonObject(mapOf("healthy" to JsonPrimitive(true)) + value)
            path == "/project/current" ->
                buildJsonObject {
                    put("data", value["project"] as? JsonObject ?: JsonObject(emptyMap()))
                }
            FormState.matches(path) ->
                buildJsonObject {
                    put("data", (value["data"] as? JsonObject)?.get("state") ?: JsonNull)
                }
            else -> value
        }
    }

    companion object {
        private val BetaVersion = Regex("0[.]0[.]0-beta-[0-9]+")
        private val StableVersion =
            Regex("[0-9]+[.][0-9]+[.][0-9]+(?:-[0-9A-Za-z.-]+)?(?:[+][0-9A-Za-z.-]+)?")
        private val SessionScoped = Regex("^/session/([^/]+)/(.+)$")
        private val PermissionReply = Regex("^permission/[^/]+/reply$")
        private val Worktree = Regex("^/worktree/([^/]+)(/refresh)?$")
        private val McpServer = Regex("^/mcp/([^/]+)(/connect|/disconnect)?$")
        private val InboxDelivery = Regex("^/session/([^/]+)/inbox/([^/]+)/(steer|queue)$")
        private val FormCancel = Regex("^/session/([^/]+)/form/([^/]+)/cancel$")
        private val FormState = Regex("^/session/([^/]+)/form/([^/]+)/state$")
        private val ShellTimeout = Regex("^/shell/[^/]+/timeout$")

        fun ofVersion(version: String?): Oc2Dialect? =
            when {
                version == null -> null
                BetaVersion.matches(version) -> Beta
                StableVersion.matches(version) &&
                    (version.substringBefore('.').toIntOrNull() ?: 0) >= 2 -> Stable
                else -> null
            }
    }
}

internal data class Oc2Route(
    val path: String,
    val method: HttpMethod,
    val body: JsonObject?,
    val query: Map<String, String?>,
    val removedFeature: String? = null,
)
