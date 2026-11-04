package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.serialization.json.JsonElement

/**
 * Only the two read-only setup endpoints, through the existing authenticated and dialect-aware
 * connection.
 */
public object Oc2SetupInspection {
    public suspend fun read(
        gateway: ServerGateway,
        location: ServerLocation,
        path: String,
    ): Outcome<JsonElement> {
        if (path !in setOf("/config", "/mcp"))
            return Failure.Refused(RefusalReason.StaleScope).failed()
        if (!gateway.capabilities.value.setupConfigRead || gateway.location.value != location) {
            return Failure.Unavailable(FeatureId("setup.read")).failed()
        }
        val oc2 =
            gateway as? Oc2Gateway ?: return Failure.Unavailable(FeatureId("setup.read")).failed()
        val result =
            oc2.json(
                path,
                query =
                    mapOf(
                        "location[directory]" to location.directory,
                        "location[workspace]" to location.workspace,
                    ),
            )
        return if (gateway.location.value == location) result
        else Failure.Refused(RefusalReason.StaleScope).failed()
    }
}
