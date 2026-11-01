package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.json.JsonObject

/**
 * The `_tag`-discriminated error body (`{_tag, message, …extras}`); older shapes use `code`. The status mapping to
 * `Failure` (401 always auth, 503 and `service_*` unavailable) belongs to the transport (S1-NET / `Oc2Transport`);
 * this type only reads the body, exactly as `Api2Transport.mapError` does.
 */
public data class Oc2ErrorEnvelope(
    public val tag: String?,
    public val message: String?,
    public val body: JsonObject,
) {
    /** An extra field the tagged error carries (`sessionID`, `field`, …). */
    public fun detail(key: String): String? = body[key].dartToString()

    public companion object {
        public fun fromJson(body: JsonObject): Oc2ErrorEnvelope =
            Oc2ErrorEnvelope(
                tag = body["_tag"].dartToString() ?: body["code"].dartToString(),
                message = body["message"].dartToString(),
                body = body,
            )
    }
}
