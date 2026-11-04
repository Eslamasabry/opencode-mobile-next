package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.SessionNoteSaveResult
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Optional session endpoints. Capability absence is remembered; ordinary failures never erase it.
 */
internal class Oc2SessionTransfer(
    private val gateway: Oc2Gateway,
    private val at: () -> ServerLocation,
    private val disable: (String) -> Unit,
) {
    private var exportSupported = true
    private var importSupported = true
    private var notesSupported = true

    suspend fun exportSession(session: SessionId, sanitize: Boolean): Outcome<ByteArray> {
        if (!exportSupported) return unavailable("sessionImportExport")
        // Globally identified session: never attach the current project. Preserve complete response
        // bytes.
        return when (
            val result =
                gateway.rawBytes(
                    "/session/${session.raw.encodeURLPathPart()}/export",
                    mapOf("sanitize" to sanitize.toString()),
                )
        ) {
            is Outcome.Ok -> Outcome.Ok(result.value.copyOf())
            is Outcome.Failed ->
                if (missing(result.failure)) {
                    exportSupported = false
                    disable("sessionImportExport")
                    unavailable("sessionImportExport")
                } else result
        }
    }

    suspend fun importSession(body: JsonObject): Outcome<Session> {
        if (!importSupported) return unavailable("sessionImportExport")
        val info = body["info"] as? JsonObject ?: return Failure.UnreadableReply().failed()
        val id = info.str("id") ?: return Failure.UnreadableReply().failed()
        val destination =
            body["location"] as? JsonObject ?: return Failure.UnreadableReply().failed()
        if (destination.str("directory").isNullOrBlank() || body["messages"] !is JsonArray)
            return Failure.UnreadableReply().failed()
        // One write; no query, generated ids or replay after uncertainty.
        return when (val result = gateway.json("/session/import", HttpMethod.Post, body)) {
            is Outcome.Failed ->
                if (missing(result.failure)) {
                    importSupported = false
                    disable("sessionImportExport")
                    unavailable("sessionImportExport")
                } else result
            is Outcome.Ok -> {
                val session = gateway.sessionValue(data(result.value))
                if (session == null || session.id.raw != id || session.directory.isNullOrBlank())
                    Failure.UnreadableReply().failed()
                else session.ok()
            }
        }
    }

    suspend fun loadSessionNote(session: SessionId): Outcome<String?> {
        if (!notesSupported) return unavailable("sessionNotes")
        val query = scope()
        return when (val result = noteRequest(path(session), HttpMethod.Get, null, query)) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val rows =
                    data(result.value) as? JsonArray ?: return Failure.UnreadableReply().failed()
                val owned = rows.filterIsInstance<JsonObject>().filter { it.str("key") == KEY }
                if (owned.isEmpty()) null.ok()
                else if (
                    owned.size != 1 ||
                        owned.single()["value"] !is JsonPrimitive ||
                        !(owned.single()["value"] as JsonPrimitive).isString
                )
                    Failure.UnreadableReply().failed()
                else (owned.single()["value"] as JsonPrimitive).content.ok()
            }
        }
    }

    suspend fun saveSessionNote(session: SessionId, value: String): Outcome<SessionNoteSaveResult> {
        if (!notesSupported) return unavailable("sessionNotes")
        if (JsonPrimitive(value).toString().encodeToByteArray().size > MAX_BYTES)
            return SessionNoteSaveResult.TooLarge(MAX_BYTES).ok()
        val query = scope()
        return when (
            val result =
                noteRequest(
                    path(session) + "/$KEY",
                    HttpMethod.Put,
                    buildJsonObject { put("value", value) },
                    query,
                )
        ) {
            is Outcome.Ok -> SessionNoteSaveResult.Saved.ok()
            is Outcome.Failed -> {
                val failure = result.failure
                if (
                    failure is Failure.Rejected &&
                        failure.status == 413 &&
                        failure.tag?.raw == "InstructionEntryValueTooLargeError"
                )
                    SessionNoteSaveResult.TooLarge(
                            failure.extras.fields["maxBytes"]?.toIntOrNull()?.takeIf { it > 0 }
                                ?: MAX_BYTES
                        )
                        .ok()
                else result
            }
        }
    }

    suspend fun removeSessionNote(session: SessionId): Outcome<Unit> {
        if (!notesSupported) return unavailable("sessionNotes")
        return noteRequest(path(session) + "/$KEY", HttpMethod.Delete, null, scope()).map {}
    }

    private suspend fun noteRequest(
        path: String,
        method: HttpMethod,
        body: JsonObject?,
        query: Map<String, String?>,
    ): Outcome<JsonElement> =
        when (val result = gateway.json(path, method, body, query)) {
            is Outcome.Ok -> result
            is Outcome.Failed ->
                if (missing(result.failure)) {
                    notesSupported = false
                    disable("sessionNotes")
                    unavailable("sessionNotes")
                } else result
        }

    private fun scope(): Map<String, String?> {
        val captured = at()
        return mapOf(
            "location[directory]" to captured.directory,
            "location[workspace]" to captured.workspace,
        )
    }

    private fun path(session: SessionId): String =
        "/session/${session.raw.encodeURLPathPart()}/instructions/entries"

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun missing(failure: Failure): Boolean =
        when (failure) {
            is Failure.NotFound -> failure.tag?.raw != "SessionNotFoundError"
            is Failure.Rejected -> failure.status == 405
            is Failure.Server -> failure.status == 501
            else -> false
        }

    private fun <T> unavailable(feature: String): Outcome<T> =
        Failure.Unavailable(FeatureId(feature)).failed()

    private companion object {
        const val KEY = "mobile.note"
        const val MAX_BYTES = 8192
    }
}
