package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.serialization.json.JsonElement

/**
 * Fixed, read-only inspection of an authenticated server at one captured location. The setup
 * payload may contain credentials and belongs only to the setup engine, which publishes a redacted
 * snapshot. There is deliberately no arbitrary HTTP path or configuration write here.
 */
public interface ServerInspectionGateway {
    public suspend fun readSetupConfig(location: ServerLocation): Outcome<JsonElement>

    public suspend fun readSetupMcp(location: ServerLocation): Outcome<JsonElement>

    /** Safe metadata only: no plugin options, paths, URLs or loader error bodies. */
    public suspend fun readPluginInventory(location: ServerLocation): Outcome<JsonElement>
}
