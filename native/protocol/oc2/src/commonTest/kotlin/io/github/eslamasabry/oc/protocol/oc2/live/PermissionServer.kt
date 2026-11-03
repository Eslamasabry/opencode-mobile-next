package io.github.eslamasabry.oc.protocol.oc2.live

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * A session's permission layer as an OC2 server keeps it: `GET /session/{id}` shows the installed rules,
 * `PATCH /session/{id}` replaces them, `GET /agent` lists [agentJson] first. Other requests go to [rest].
 */
internal class PermissionServer(
    private val sessionId: String = "s",
    private val agentJson: String =
        """{"id":"build","hidden":false,"mode":"primary","permissions":[{"action":"*","resource":"*","effect":"allow"}]}""",
    var extraSessionFields: String = "",
    /** false: the PATCH answers 200 but changes nothing (a server that ignores it). */
    var honourPatch: Boolean = true,
) {
    var installed: JsonObject? = null
    val patches = mutableListOf<JsonObject>()

    fun session(): String {
        val p = installed
        val extras = if (extraSessionFields.isEmpty()) "" else ",$extraSessionFields"
        return if (p == null) """{"data":{"id":"$sessionId"$extras}}"""
        else """{"data":{"id":"$sessionId","permissions":${p["permissions"]},"metadata":${p["metadata"]}$extras}}"""
    }

    suspend fun MockRequestHandleScope.answer(
        request: HttpRequestData,
        rest: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            request.method == HttpMethod.Get && path == "/api/agent" -> respond("""{"data":[$agentJson]}""", HttpStatusCode.OK)
            request.method == HttpMethod.Get && path.startsWith("/api/agent/") -> respond("""{"data":$agentJson}""", HttpStatusCode.OK)
            request.method == HttpMethod.Get && path == "/api/session/$sessionId" -> respond(session(), HttpStatusCode.OK)
            request.method == HttpMethod.Patch && path == "/api/session/$sessionId" -> {
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                patches += body
                if (honourPatch) installed = body
                respond("""{"data":{"id":"$sessionId"}}""", HttpStatusCode.OK)
            }
            else -> rest(request)
        }
    }

    /** The effect the installed layer gives an action no explicit rule names (the leading `* *` rule). */
    fun defaultEffect(): String? =
        ((installed?.get("permissions") as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("effect")?.let { (it as JsonPrimitive).content }
}
