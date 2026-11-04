package io.github.eslamasabry.oc.engine.agents.sessionimport

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.failed
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/** One HTTP exchange as the tiny transport port sees it. [path] is absolute (`/api/...`) and carries no query. */
public data class RouteRequest(
    val method: String,
    val path: String,
    val query: ImmutableMap<String, String> = persistentMapOf(),
    val headers: ImmutableMap<String, String> = persistentMapOf(),
    val body: JsonElement? = null,
) {
    override fun toString(): String = "RouteRequest($method $path)" // headers hold the password: never printed
}

/** The decoded reply. A body that was not JSON is null. */
public data class RouteAnswer(val status: Int, val body: JsonElement?)

/**
 * The transport port for the session import and active-context routes (port of the Dart `Api2Transport` calls these
 * features make). A transport-level failure (no network, timeout, TLS) is an [Outcome.Failed]; any HTTP status is an
 * [RouteAnswer]. Implementations never retry a write.
 */
public fun interface RouteTransport {
    public suspend fun send(request: RouteRequest): Outcome<RouteAnswer>
}

/** OpenCode 2 Basic credentials. `toString()` never prints the password. */
public class Oc2BasicAuth(public val user: String, private val password: String) {
    public val header: String
        get() = "Basic " + Base64.Default.encode("$user:$password".encodeToByteArray())

    override fun toString(): String = "Oc2BasicAuth($user, redacted)"
}

/** The `/api` prefix and Basic auth for every route (AGENTS.md: "Basic auth, `/api` prefix"). */
public class Oc2Routes(private val transport: RouteTransport, private val auth: Oc2BasicAuth? = null) {
    public suspend fun get(path: String): Outcome<RouteAnswer> = send("GET", path, null)

    public suspend fun post(path: String, body: JsonElement): Outcome<RouteAnswer> = send("POST", path, body)

    private suspend fun send(method: String, path: String, body: JsonElement?): Outcome<RouteAnswer> {
        val headers = buildMap {
            if (auth != null) put("authorization", auth.header)
            if (body != null) put("content-type", "application/json")
        }.toImmutableMap()
        return transport.send(RouteRequest(method, "/api$path", headers = headers, body = body))
    }

    public companion object {
        /** A path segment as Dart `Uri.encodeComponent` writes it. */
        public fun segment(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt() and 0xff
                val ch = c.toChar()
                if (ch.isLetterOrDigit() && c < 0x80 || ch in "-_.!~*'()") append(ch)
                else append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
            }
        }

        private const val HEX = "0123456789ABCDEF"
    }
}

/** The server's `_tag` (or `code`) when the error body has one. */
public fun RouteAnswer.tag(): WireTag? {
    val obj = body as? JsonObject ?: return null
    val raw = (obj["_tag"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (obj["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return raw?.let(::WireTag)
}

/** True for 404 on a route this server does not have, 405 and 501: the optional feature is absent (plan 04 §11.5). */
public fun RouteAnswer.routeMissing(): Boolean =
    status == 405 || status == 501 || (status == 404 && tag()?.raw != SESSION_NOT_FOUND)

/** The flat failure for a non-2xx answer (plan 04 §3.8). The status survives in every subtype that carries one. */
public fun RouteAnswer.failure(): Failure {
    val tag = tag()
    return when {
        status == 401 -> Failure.SignIn(SignInWhy.PasswordRejected)
        status == 403 -> Failure.SignIn(SignInWhy.Forbidden)
        status == 404 -> Failure.NotFound(tag)
        status == 409 -> Failure.Conflict(tag)
        status >= 500 -> Failure.Server(status, tag)
        else -> Failure.Rejected(status, tag)
    }
}

public fun RouteAnswer.isSuccess(): Boolean = status in 200..299

/** The `data` member of a success envelope. */
public fun RouteAnswer.data(): JsonElement? = (body as? JsonObject)?.get("data")

internal const val SESSION_NOT_FOUND: String = "SessionNotFoundError"

internal fun <T> Outcome<RouteAnswer>.then(onAnswer: (RouteAnswer) -> Outcome<T>): Outcome<T> =
    when (this) {
        is Outcome.Ok -> onAnswer(value)
        is Outcome.Failed -> failure.failed()
    }
