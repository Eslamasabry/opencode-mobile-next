package io.github.eslamasabry.oc.protocol.oc2.live

import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Port of lib/api2/dialect.dart (B-PROTO-013). Call sites speak the beta's vocabulary; [route] translates a request
 * for the stable line (2.0.4+, 2.1.x) before the single network dispatch, and [response] reshapes the few stable reads
 * whose envelope changed. Unchanged requests pass through untouched (same query and body instances).
 */
internal enum class Oc2Dialect {
    Beta,
    Stable;

    fun route(
        path: String,
        method: HttpMethod,
        body: JsonObject?,
        query: Map<String, String?>,
    ): Oc2Route {
        if (this == Beta) return Oc2Route(path, method, body, query)
        fun same(p: String = path, m: HttpMethod = method, b: JsonObject? = body, q: Map<String, String?> = query) =
            Oc2Route(p, m, b, q)

        // ---- server facts
        if (method == HttpMethod.Get && (path == "/health" || path == "/server")) return same(p = "/info")
        // The current project is part of the location now.
        if (method == HttpMethod.Get && path == "/project/current") return same(p = "/location")
        if (method == HttpMethod.Get && path == "/form/request") return same(p = "/form")

        // ---- removed outright: fail clearly instead of 404
        if (path == "/workspace" || path.startsWith("/workspace/"))
            return same().copy(removed = "managedWorkspaces")
        if (method == HttpMethod.Patch && ShellTimeout.matches(path))
            return same().copy(removed = "developmentServices")

        // ---- worktrees: the project moved from the path into the request
        Worktree.matchEntire(path)?.let { match ->
            val project = match.groupValues[1].decodeURLPart()
            if (match.groupValues[2].isNotEmpty())
                return same(p = "/worktree/refresh", b = buildJsonObject { put("projectID", JsonPrimitive(project)) })
            if (method == HttpMethod.Get) return same(p = "/worktree", q = query + ("projectID" to project))
            return same(p = "/worktree", b = JsonObject((body ?: EmptyBody) + ("projectID" to JsonPrimitive(project))))
        }

        // ---- MCP writes are experimental; the list stays at /mcp
        McpServer.matchEntire(path)?.let { match ->
            if (method != HttpMethod.Get || match.groupValues[2].isNotEmpty()) return same(p = "/experimental$path")
        }
        if (method == HttpMethod.Post && path == "/generate") return same(p = "/experimental/generate")

        // ---- sessions
        if (path == "/session/import" || path == "/session/stats") return same(p = "/experimental$path")
        InboxDelivery.matchEntire(path)?.let { match ->
            if (method == HttpMethod.Post)
                return Oc2Route(
                    "/session/${match.groupValues[1]}/inbox/${match.groupValues[2]}",
                    HttpMethod.Patch,
                    buildJsonObject { put("delivery", JsonPrimitive(match.groupValues[3])) },
                    query,
                )
        }
        FormCancel.matchEntire(path)?.let { match ->
            if (method == HttpMethod.Post)
                return Oc2Route(
                    "/session/${match.groupValues[1]}/form/${match.groupValues[2]}",
                    HttpMethod.Delete,
                    null,
                    query,
                )
        }
        // A form's state is a field of the form itself now.
        FormState.matchEntire(path)?.let { match ->
            if (method == HttpMethod.Get)
                return same(p = "/session/${match.groupValues[1]}/form/${match.groupValues[2]}")
        }
        val session = SessionScoped.matchEntire(path)
        if (session != null) {
            val id = session.groupValues[1]
            val rest = session.groupValues[2]
            if (method == HttpMethod.Post && rest == "rename")
                return Oc2Route("/session/$id", HttpMethod.Patch, body, query)
            // dialect.dart: stable clears a staged revert with DELETE .../revert.
            if (method == HttpMethod.Post && rest == "revert/clear")
                return Oc2Route("/session/$id/revert", HttpMethod.Delete, null, query)
            if (rest == "skill" || rest == "wait" || rest == "export" || rest == "instructions/entries" ||
                rest.startsWith("instructions/entries/"))
                return same(p = "/experimental$path")
            // ---- renamed request fields (in place: JSON member order carries no meaning)
            if (method == HttpMethod.Post && rest == "command") return same(b = rename(body, "command", "name"))
            if (method == HttpMethod.Post && rest == "interrupt" && "continue" in query)
                return same(q = query - "continue" + ("resume" to query["continue"]))
            if (method == HttpMethod.Post && rest == "fork") {
                // {boundary: {type: before, messageID}} became {before: messageID}; "through" is an absent `before`.
                val boundary = body?.get("boundary") as? JsonObject
                val before =
                    if ((boundary?.get("type") as? JsonPrimitive)?.content == "before") boundary["messageID"] else null
                return same(b = JsonObject(if (before != null && before != JsonNull) mapOf("before" to before) else emptyMap()))
            }
            if (method == HttpMethod.Post && PermissionReply.matches(rest)) return same(b = rename(body, "reply", "decision"))
        }
        return same()
    }

    /** Flutter `stableResponse`: reshapes the few stable reads whose envelope changed; everything else is untouched. */
    fun response(method: HttpMethod, path: String, json: JsonElement): JsonElement {
        if (this == Beta || method != HttpMethod.Get || json !is JsonObject) return json
        when (path) {
            // `/info` has no `healthy` field: answering at all is the health.
            "/health" -> return JsonObject(mapOf("healthy" to JsonPrimitive(true)) + json)
            "/project/current" ->
                return JsonObject(mapOf("data" to ((json["project"] as? JsonObject) ?: EmptyBody)))
        }
        if (FormState.matches(path)) {
            val data = json["data"] as? JsonObject
            return JsonObject(mapOf("data" to (data?.get("state") ?: JsonNull)))
        }
        return json
    }

    companion object {
        private val BetaVersion = Regex("0[.]0[.]0-beta-[0-9]+")
        private val StableVersion =
            Regex("[0-9]+[.][0-9]+[.][0-9]+(?:-[0-9A-Za-z.-]+)?(?:[+][0-9A-Za-z.-]+)?")
        private val SessionScoped = Regex("^/session/([^/]+)/(.+)$")
        private val PermissionReply = Regex("^permission/[^/]+/reply$")
        private val InboxDelivery = Regex("^/session/([^/]+)/inbox/([^/]+)/(steer|queue)$")
        private val FormCancel = Regex("^/session/([^/]+)/form/([^/]+)/cancel$")
        private val FormState = Regex("^/session/([^/]+)/form/([^/]+)/state$")
        private val Worktree = Regex("^/worktree/([^/]+)(/refresh)?$")
        private val McpServer = Regex("^/mcp/([^/]+)(/connect|/disconnect)?$")
        private val ShellTimeout = Regex("^/shell/[^/]+/timeout$")
        private val EmptyBody = JsonObject(emptyMap())

        private fun rename(body: JsonObject?, from: String, to: String): JsonObject? {
            if (body == null || from !in body) return body
            return JsonObject(buildMap { for ((key, value) in body) put(if (key == from) to else key, value) })
        }

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

/** [removed] names the capability a stable server dropped (Flutter `Api2RemovedInStable`): nothing is sent. */
internal data class Oc2Route(
    val path: String,
    val method: HttpMethod,
    val body: JsonObject?,
    val query: Map<String, String?>,
    val removed: String? = null,
)
