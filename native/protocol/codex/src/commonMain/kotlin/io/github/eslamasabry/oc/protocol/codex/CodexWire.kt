package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

public class CodexWireException(public val failure: Failure) : Exception("Codex operation failed")

internal fun malformed(): Nothing = throw CodexWireException(Failure.UnreadableReply())

internal fun stale(): Nothing = throw CodexWireException(Failure.Refused(RefusalReason.StaleScope))

internal fun unknown(): Nothing =
    throw CodexWireException(Failure.Refused(RefusalReason.DispatchUncertain))

internal fun unavailable(feature: String): Nothing =
    throw CodexWireException(Failure.Unavailable(FeatureId(feature)))

internal fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: malformed()

internal fun JsonElement?.str(max: Int = 4096, empty: Boolean = false): String {
    val primitive = this as? JsonPrimitive ?: malformed()
    if (!primitive.isString) malformed()
    val value = primitive.content
    if (value.length > max || (!empty && value.isEmpty())) malformed()
    return value
}

internal fun JsonElement?.list(max: Int = 10_000): JsonArray =
    (this as? JsonArray)?.takeIf { it.size <= max } ?: malformed()

internal fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun json(vararg fields: Pair<String, JsonElement?>): JsonObject =
    JsonObject(fields.filter { it.second != null }.associate { it.first to it.second!! })

internal fun s(value: String): JsonPrimitive = JsonPrimitive(value)

internal suspend fun <T> codexWireAttempt(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: CodexWireException) {
        Outcome.Failed(e.failure)
    } catch (_: Exception) {
        Outcome.Failed(Failure.ComputerDidNotAnswer())
    }

/**
 * Off-device Codex credentials always require TLS; user-info, query, fragment and control bytes are
 * refused.
 */
public fun validCodexEndpoint(endpoint: String): Boolean {
    if (
        endpoint.length > 4096 ||
            endpoint.any { it.code <= 32 || it.code == 127 } ||
            endpoint.contains('@') ||
            endpoint.contains('?') ||
            endpoint.contains('#')
    )
        return false
    val match =
        Regex("^(wss|ws)://(\\[[0-9a-fA-F:]+]|[A-Za-z0-9.-]+)(?::([0-9]{1,5}))?(/[^?#]*)?$")
            .matchEntire(endpoint) ?: return false
    val port = match.groupValues[3]
    if (port.isNotEmpty() && port.toIntOrNull() !in 1..65535) return false
    val host = match.groupValues[2].lowercase()
    return match.groupValues[1] == "wss" ||
        host == "localhost" ||
        host == "[::1]" ||
        host == "127.0.0.1"
}
