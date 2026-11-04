package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.JsonObject

/**
 * Read-only configuration layers in server priority order. Register credentials before projection.
 */
public interface SetupInspectionGateway {
    public suspend fun setupConfigSources(): Outcome<JsonObject>
}
