package io.github.eslamasabry.oc.protocol.oc2.live

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

/**
 * Implemented subset of frozen lib/api2/dialect.dart; changes happen before the single network
 * dispatch.
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
        if (method == HttpMethod.Get && path == "/health")
            return Oc2Route("/info", method, body, query)
        val session = SessionScoped.matchEntire(path)
        if (session != null && method == HttpMethod.Post) {
            val id = session.groupValues[1]
            val rest = session.groupValues[2]
            if (rest == "rename") return Oc2Route("/session/$id", HttpMethod.Patch, body, query)
            if (rest == "interrupt" && "continue" in query) {
                return Oc2Route(
                    path,
                    method,
                    body,
                    query - "continue" + ("resume" to query["continue"]),
                )
            }
            if (PermissionReply.matches(rest) && body != null && "reply" in body) {
                return Oc2Route(
                    path,
                    method,
                    JsonObject(
                        buildMap {
                            for ((key, value) in body) put(
                                if (key == "reply") "decision" else key,
                                value,
                            )
                        }
                    ),
                    query,
                )
            }
        }
        return Oc2Route(path, method, body, query)
    }

    companion object {
        private val BetaVersion = Regex("0[.]0[.]0-beta-[0-9]+")
        private val SessionScoped = Regex("^/session/([^/]+)/(.+)$")
        private val PermissionReply = Regex("^permission/[^/]+/reply$")

        fun ofVersion(version: String?): Oc2Dialect? =
            when {
                version == null -> null
                BetaVersion.matches(version) -> Beta
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
)
