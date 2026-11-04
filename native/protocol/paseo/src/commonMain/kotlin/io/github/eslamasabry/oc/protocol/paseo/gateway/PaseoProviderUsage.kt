package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.serialization.json.JsonObject

/** The daemon-read provider usage contract. Reuses the already connected host; never starts one. */
public suspend fun PaseoGateway.providerUsagePayload(): Outcome<JsonObject> =
    attempt({ Failure.Network() }) {
        val epoch = transport.epoch
        transport.request(
            "provider.usage.list.request",
            JsonObject(emptyMap()),
            timeoutMs = 10_000,
            expectedEpoch = epoch,
        )
    }
