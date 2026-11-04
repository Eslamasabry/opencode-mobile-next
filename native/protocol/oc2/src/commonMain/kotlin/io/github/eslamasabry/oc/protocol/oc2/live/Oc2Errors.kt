package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.WireExtras
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ErrorEnvelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Flutter `Api2Transport.mapError` (B-PROTO-038): a non-2xx status and its `_tag`/`code` body become one typed
 * [Failure]. 401 is always sign-in, even with an empty body; 503 (`service_*` boot tags or none) is Busy; a tagged
 * 4xx/404/409 keeps its tag and its symbolic extras. Server prose (`message`) is never carried.
 *
 * Live wiring needs the error body, which `NetClient.exchange` does not hand to protocols yet (core:net proposal);
 * until then the live gateway sees the status-only [StatusMapping] result, which this refines.
 */
public object Oc2Errors {
    public fun failureFor(status: Int, body: String?, credentialsSent: Boolean = true): Failure {
        val base = StatusMapping.failureFor(status, credentialsSent)
        if (status == 401 || status == 403) return base
        val envelope =
            body?.let { Oc2Wire.parse(it) as? JsonObject }?.let(Oc2ErrorEnvelope::fromJson)
        val tag = envelope?.tag?.takeIf { it.isNotBlank() }?.let(::WireTag) ?: return base
        val extras = extrasOf(envelope.body)
        return when (base) {
            is Failure.NotFound -> base.copy(tag = tag)
            is Failure.Conflict -> base.copy(tag = tag)
            is Failure.Busy ->
                base.copy(
                    kind =
                        when (tag.raw) {
                            "service_starting" -> BusyKind.Starting
                            "service_stopping" -> BusyKind.Stopping
                            "service_failed" -> BusyKind.Failed
                            else -> base.kind
                        },
                    tag = tag,
                )
            is Failure.Server -> base.copy(tag = tag)
            is Failure.Rejected -> base.copy(tag = tag, extras = extras)
            else -> base
        }
    }

    /** Symbolic scalar fields only; `_tag`, `code` and `message` (server prose) are dropped. */
    private fun extrasOf(body: JsonObject): WireExtras {
        val fields =
            body
                .filterKeys { it != "_tag" && it != "code" && it != "message" }
                .mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
                }
                .toMap()
        return if (fields.isEmpty()) WireExtras.None else WireExtras(fields.toImmutableMap())
    }

    /** The extra a tagged failure carries (`sessionID`, `field`, …), for logic only. */
    public fun detail(failure: Failure, key: String): String? =
        (failure as? Failure.Rejected)?.extras?.fields?.get(key)
}
