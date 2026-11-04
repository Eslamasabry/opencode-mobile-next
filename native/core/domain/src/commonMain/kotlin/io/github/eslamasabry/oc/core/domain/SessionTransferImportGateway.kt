package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.serialization.json.JsonObject

/**
 * Optional import facet. The engine validates an isolated export snapshot and explicit location
 * before dispatch.
 */
public interface SessionTransferImportGateway {
    /**
     * Exactly one mutation; no retry or fallback. The response must keep the source id and the
     * requested location.
     */
    public suspend fun importSessionDocument(body: JsonObject): Outcome<Session>
}
