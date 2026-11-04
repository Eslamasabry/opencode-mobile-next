package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.JsonObject

/** Provider usage from the authenticated host. Consumers project numeric fields before storage. */
public interface ProviderUsageGateway {
    public suspend fun providerUsage(): Outcome<JsonObject>
}
