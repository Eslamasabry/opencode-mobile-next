package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.model.FormSpec as WireForm
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of Dart `form_request.dart` (B-CHAT-028). The revision hashes every part of the schema that can change how the
 * form renders, validates or is read: id, session, title, metadata and every field's complete wire object (conditions,
 * limits, pattern, default, options…). Object key order is not identity; list order and JSON value types are
 * (`"1"`, `1` and `1.0` are three different values).
 */
public object FormRevision {
    public fun of(form: WireForm): String =
        hash(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(form.id.raw),
                    "sessionID" to JsonPrimitive(form.sessionId.raw),
                    "title" to (form.title?.let(::JsonPrimitive) ?: JsonNull),
                    "metadata" to (form.metadata ?: JsonNull),
                    "fields" to JsonArray(form.fields.map { it.raw }),
                ),
            ),
        )

    /**
     * The identity of one captured form (Dart `FormRequestIdentity.key`): drafts, holds and receipts are keyed by it, so
     * a stale sheet can never share state with a newer revision. The parts are hashed as one JSON list, so no part can
     * borrow another's delimiter (`"a|b", "c"` and `"a", "b|c"` differ).
     */
    public fun identityKey(
        profileId: String,
        directory: String,
        workspace: String?,
        sessionId: String,
        formId: String,
        revision: String,
    ): String =
        hash(
            JsonArray(
                listOf(profileId, directory, workspace, sessionId, formId, revision).map {
                    it?.let(::JsonPrimitive) ?: JsonNull
                },
            ),
        )

    private fun hash(value: JsonElement): String = DartJson.sha256Hex(canonical(value))

    /** Sorted keys at every depth; primitives keep their wire spelling, so types survive. */
    internal fun canonical(value: JsonElement): String =
        StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: JsonElement) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                value.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) out.append(',')
                    out.append(JsonPrimitive(key).toString()).append(':')
                    write(out, value.getValue(key))
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            is JsonPrimitive -> out.append(value.toString())
        }
    }
}
