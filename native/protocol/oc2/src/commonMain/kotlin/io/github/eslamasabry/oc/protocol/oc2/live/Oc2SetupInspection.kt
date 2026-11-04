package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Source layers stay ordered and separate; this is deliberately not an effective config or a write
 * contract.
 */
internal suspend fun Oc2Gateway.inspectSetup(redactor: Redactor): Outcome<JsonObject> {
    val at = location.value
    val query = mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)
    val reply =
        when (val read = json("/config", query = query)) {
            is Outcome.Failed -> return read
            is Outcome.Ok -> read.value
        }
    // The raw response can contain provider credentials, including those in layers the UI never
    // displays.
    redactor.registerCredentialValues(reply)
    if (location.value != at) return Failure.Refused(RefusalReason.StaleScope).failed()
    val sources =
        (if (reply is JsonObject) reply["data"] else reply) as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (sources.size > 2000) return Failure.UnreadableReply().failed()
    val selected = sources.map { value ->
        val entry = value as? JsonObject ?: return Failure.UnreadableReply().failed()
        if (entry.str("type") == null) return Failure.UnreadableReply().failed()
        buildJsonObject {
            put("type", entry.getValue("type"))
            entry.str("path")?.let { put("path", kotlinx.serialization.json.JsonPrimitive(it)) }
            (entry["info"] as? JsonObject)?.let { put("info", it) }
        }
    }
    return buildJsonObject { put("sources", JsonArray(selected)) }.ok()
}
