package io.github.eslamasabry.oc.engine.agents.context

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.sessionimport.Oc2Routes
import io.github.eslamasabry.oc.engine.agents.sessionimport.failure
import io.github.eslamasabry.oc.engine.agents.sessionimport.isSuccess
import io.github.eslamasabry.oc.engine.agents.sessionimport.routeMissing
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `GET /api/session/{id}/context` (port of the Dart `Api2OperationsGateway.loadActiveContext`). The route identifies
 * its session globally: no location query. Every message is kept (no cap; a 5001-message snapshot stays whole).
 *
 * - A malformed or duplicate snapshot is [Failure.UnreadableReply] and keeps [supported] on.
 * - A missing session (404 `SessionNotFoundError`), a refused password (401) and a server error stay recoverable
 *   wire failures; [supported] stays on.
 * - A missing route (404 with another tag, 405, 501) is [Failure.Unavailable] and latches [supported] off: later loads
 *   answer the same without a request.
 */
public class ActiveContextGateway(private val routes: Oc2Routes) {
    public var supported: Boolean = true
        private set

    public suspend fun load(sessionId: String): Outcome<List<ActiveContextMessage>> {
        if (!supported) return Failure.Unavailable(FEATURE).failed()
        val answer = when (val sent = routes.get("/session/${Oc2Routes.segment(sessionId)}/context")) {
            is Outcome.Ok -> sent.value
            is Outcome.Failed -> return sent
        }
        if (!answer.isSuccess()) {
            if (answer.routeMissing()) {
                supported = false
                return Failure.Unavailable(FEATURE).failed()
            }
            return answer.failure().failed()
        }
        val data = (answer.body as? JsonObject)?.get("data") as? JsonArray ?: return Failure.UnreadableReply().failed()
        val ids = HashSet<String>()
        val messages = ArrayList<ActiveContextMessage>(data.size)
        for (value in data) {
            val obj = value as? JsonObject ?: return Failure.UnreadableReply().failed()
            val id = (obj["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (id == null || type == null || !ids.add(id)) return Failure.UnreadableReply().failed()
            messages += ActiveContextMapper.map(obj) ?: return Failure.UnreadableReply().failed()
        }
        return messages.ok()
    }

    public companion object {
        /** Not a ServerCapabilities flag: the Dart gateway's own `activeContextSupported` latch. */
        public val FEATURE: FeatureId = FeatureId("activeContext")
    }
}
