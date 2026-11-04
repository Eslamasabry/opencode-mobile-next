package io.github.eslamasabry.oc.engine.agents.sessionimport

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The session the server says it imported, with the location it landed in. */
public data class ImportedSession(
    val id: String,
    val title: String?,
    val directory: String,
    val workspaceId: String?,
)

/**
 * `POST /api/session/import` (port of the Dart `Api2OperationsGateway.importSession`). The body keeps the source ids
 * and the explicit destination, with no query string. One request per call: a failure is never retried and never
 * changes ids. 405/501 and a 404 that is not `SessionNotFoundError` latch [supported] off (a 404 with that tag can mean
 * the parent session must be imported first, so it keeps the capability). A 2xx whose session is not the source's id
 * or has no directory is [Failure.UnreadableReply]: the import is not confirmed.
 */
public class SessionImportGateway(private val routes: Oc2Routes) {
    public var supported: Boolean = true
        private set

    public suspend fun importSession(
        document: SessionImportDocument,
        destination: SessionImportDestination,
    ): Outcome<ImportedSession> {
        if (!supported) return Failure.Unavailable(FEATURE).failed()
        val body = when (val built = document.requestBody(destination)) {
            is SessionImportResult.Ok -> built.value
            is SessionImportResult.Refused -> return Failure.Refused(RefusalReason.InvalidAddress).failed()
        }
        return routes.post("/session/import", body).then { answer ->
            when {
                answer.isSuccess() -> session(answer.data() as? JsonObject, document.id)
                answer.routeMissing() -> {
                    supported = false
                    Failure.Unavailable(FEATURE).failed()
                }
                else -> answer.failure().failed()
            }
        }
    }

    private fun session(data: JsonObject?, expectedId: String): Outcome<ImportedSession> {
        val id = (data?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val location = data?.get("location") as? JsonObject
        val directory = (location?.get("directory") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (id != expectedId || directory.isNullOrEmpty()) return Failure.UnreadableReply().failed()
        return ImportedSession(
            id = id,
            title = (data["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            directory = directory,
            workspaceId = (location["workspaceID"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        ).ok()
    }

    public companion object {
        public val FEATURE: FeatureId = FeatureId("sessionImportExport")
    }
}
