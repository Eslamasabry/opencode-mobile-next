package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

/**
 * OpenCode 2.4+ experimental server automations (`/api/experimental/automation`, B-605).
 * Decodes tolerantly and redacts server-supplied output and pause reasons before returning.
 */
internal class Oc2Automations(
    private val gateway: Oc2Gateway,
    private val redactor: Redactor,
    private val onUnsupported: () -> Unit,
) {
    suspend fun list(
        sessionId: SessionId? = null,
        directory: String? = null,
    ): Outcome<ImmutableList<ServerAutomation>> {
        val query =
            buildMap {
                sessionId?.raw?.takeIf { it.isNotBlank() }?.let { put("sessionID", it) }
                directory?.takeIf { it.isNotBlank() }?.let { put("directory", it) }
            }
        val result = gateway.json(BASE_PATH, HttpMethod.Get, query = query)
        if (result is Outcome.Failed) return handleFailure(result, treatNotFoundAsUnsupported = true)
        val body = (result as Outcome.Ok).value
        val items = extractAutomationArray(body) ?: return Failure.UnreadableReply().failed()
        val parsed = items.mapNotNull { (it as? JsonObject)?.let { obj -> parseAutomation(obj, redactor) } }
        return parsed.toImmutableList().ok()
    }

    suspend fun get(id: String): Outcome<ServerAutomation> =
        readOne("$BASE_PATH/${id.encodeURLPathPart()}", HttpMethod.Get)

    suspend fun pause(id: String): Outcome<ServerAutomation> =
        readOne("$BASE_PATH/${id.encodeURLPathPart()}/pause", HttpMethod.Post)

    suspend fun resume(id: String): Outcome<ServerAutomation> =
        readOne("$BASE_PATH/${id.encodeURLPathPart()}/resume", HttpMethod.Post)

    suspend fun delete(id: String): Outcome<Unit> {
        val result = gateway.json("$BASE_PATH/${id.encodeURLPathPart()}", HttpMethod.Delete)
        if (result is Outcome.Failed) return handleFailure(result, treatNotFoundAsUnsupported = false)
        return result.map {}
    }

    private suspend fun readOne(path: String, method: HttpMethod): Outcome<ServerAutomation> {
        val result = gateway.json(path, method)
        if (result is Outcome.Failed) return handleFailure(result, treatNotFoundAsUnsupported = false)
        val body = (result as Outcome.Ok).value
        val obj = extractAutomationObject(body) ?: return Failure.UnreadableReply().failed()
        return parseAutomation(obj, redactor)?.ok() ?: Failure.UnreadableReply().failed()
    }

    private fun handleFailure(
        failed: Outcome.Failed,
        treatNotFoundAsUnsupported: Boolean,
    ): Outcome.Failed {
        val f = failed.failure
        val unsupported =
            (treatNotFoundAsUnsupported && f is Failure.NotFound) ||
                (f is Failure.Rejected && f.status in UNSUPPORTED_STATUSES) ||
                (f is Failure.Server && f.status == HTTP_NOT_IMPLEMENTED)
        if (unsupported) {
            onUnsupported()
            return Failure.Unavailable(FeatureId(FEATURE_NAME)).failed()
        }
        return failed
    }

    private companion object {
        private const val BASE_PATH = "/experimental/automation"
        private const val FEATURE_NAME = "serverAutomations"
        private const val HTTP_METHOD_NOT_ALLOWED = 405
        private const val HTTP_NOT_IMPLEMENTED = 501
        private val UNSUPPORTED_STATUSES = setOf(HTTP_METHOD_NOT_ALLOWED, HTTP_NOT_IMPLEMENTED)
    }
}
