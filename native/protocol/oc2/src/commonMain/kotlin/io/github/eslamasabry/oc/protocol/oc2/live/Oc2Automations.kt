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
        return when (val result = gateway.json(BASE_PATH, HttpMethod.Get, query = query)) {
            is Outcome.Failed -> handleFailure(result, treatNotFoundAsUnsupported = true)
            is Outcome.Ok ->
                extractAutomationArray(result.value)
                    ?.mapNotNull { (it as? JsonObject)?.let { obj -> parseAutomation(obj, redactor) } }
                    ?.toImmutableList()
                    ?.ok()
                    ?: Failure.UnreadableReply().failed()
        }
    }

    suspend fun get(id: String): Outcome<ServerAutomation> =
        readOne("$BASE_PATH/${id.encodeURLPathPart()}", HttpMethod.Get)

    suspend fun pause(id: String): Outcome<ServerAutomation> =
        readOne("$BASE_PATH/${id.encodeURLPathPart()}/pause", HttpMethod.Post)

    suspend fun resume(id: String): Outcome<ServerAutomation> =
        readOne("$BASE_PATH/${id.encodeURLPathPart()}/resume", HttpMethod.Post)

    suspend fun delete(id: String): Outcome<Unit> =
        when (val result = gateway.json("$BASE_PATH/${id.encodeURLPathPart()}", HttpMethod.Delete)) {
            is Outcome.Failed -> handleFailure(result, treatNotFoundAsUnsupported = false)
            is Outcome.Ok -> result.map {}
        }

    private suspend fun readOne(path: String, method: HttpMethod): Outcome<ServerAutomation> =
        when (val result = gateway.json(path, method)) {
            is Outcome.Failed -> handleFailure(result, treatNotFoundAsUnsupported = false)
            is Outcome.Ok ->
                extractAutomationObject(result.value)
                    ?.let { parseAutomation(it, redactor) }
                    ?.ok()
                    ?: Failure.UnreadableReply().failed()
        }

    private fun handleFailure(
        failed: Outcome.Failed,
        treatNotFoundAsUnsupported: Boolean,
    ): Outcome<Nothing> {
        val f = failed.failure
        val unsupported =
            (treatNotFoundAsUnsupported && f is Failure.NotFound) ||
                (f is Failure.Rejected && f.status in UNSUPPORTED_STATUSES) ||
                (f is Failure.Server && f.status == HTTP_NOT_IMPLEMENTED)
        return if (unsupported) {
            onUnsupported()
            Failure.Unavailable(FeatureId(FEATURE_NAME)).failed()
        } else {
            failed
        }
    }

    private companion object {
        private const val BASE_PATH = "/experimental/automation"
        private const val FEATURE_NAME = "serverAutomations"
        private const val HTTP_METHOD_NOT_ALLOWED = 405
        private const val HTTP_NOT_IMPLEMENTED = 501
        private val UNSUPPORTED_STATUSES = setOf(HTTP_METHOD_NOT_ALLOWED, HTTP_NOT_IMPLEMENTED)
    }
}
