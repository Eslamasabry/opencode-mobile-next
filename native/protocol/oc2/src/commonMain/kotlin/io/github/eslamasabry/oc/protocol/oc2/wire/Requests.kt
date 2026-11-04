package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ported from lib/api2/models/models_inbox.dart: inbox items, permissions and forms.

/**
 * The receipt of `POST /api/session/{id}/prompt`, and the entries of `GET /api/session/{id}/inbox`.
 */
@Serializable
public data class Oc2InboxItem(
    public val id: String,
    public val sessionID: String? = null,
    public val timeCreated: Long? = null,
    public val type: String = "unknown",
    public val payload: JsonObject = EmptyObject,
    public val delivery: Oc2Delivery? = null,
) {
    public val promptText: String?
        get() = payload.str("text")

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2InboxItem? = Oc2Wire.decode(serializer(), obj)
    }
}

@Serializable
public data class Oc2PermissionSource(
    public val type: String? = null,
    public val messageID: String? = null,
    public val id: String? = null,
)

@Serializable
public data class Oc2PermissionRequest(
    public val id: String,
    public val sessionID: String? = null,
    public val action: String = "",
    public val resources: List<String> = emptyList(),
    public val save: List<String> = emptyList(),
    public val metadata: JsonObject? = null,
    public val source: Oc2PermissionSource? = null,
    public val message: String? = null,
) {
    public companion object {
        /**
         * Current OC2 fields and the permission event aliases retain the same request identity
         * (B-PROTO-073).
         */
        public fun fromJson(obj: JsonObject): Oc2PermissionRequest? {
            val normalized = obj.toMutableMap()
            if ("action" !in normalized)
                normalized["action"] = obj["permission"] ?: obj["type"] ?: JsonPrimitive("")
            if ("resources" !in normalized) {
                val patterns = obj["patterns"] ?: obj["pattern"]
                normalized["resources"] =
                    when (patterns) {
                        is JsonArray -> patterns
                        is JsonPrimitive ->
                            if (patterns.isString) JsonArray(listOf(patterns))
                            else JsonArray(emptyList())
                        else -> JsonArray(emptyList())
                    }
            }
            if ("save" !in normalized) normalized["save"] = obj["always"] ?: JsonArray(emptyList())
            if ("source" !in normalized) {
                val tool = obj["tool"] as? JsonObject
                val message = tool?.get("messageID") ?: obj["messageID"]
                val call = tool?.get("callID") ?: tool?.get("id") ?: obj["callID"]
                if (message != null || call != null)
                    normalized["source"] =
                        JsonObject(
                            buildMap {
                                message?.let { put("messageID", it) }
                                call?.let { put("id", it) }
                            }
                        )
            }
            return Oc2Wire.decode(serializer(), JsonObject(normalized))?.takeIf {
                it.id.isNotBlank() && !it.sessionID.isNullOrBlank()
            }
        }
    }
}

@Serializable
public data class Oc2PermissionRule(
    public val action: String? = null,
    public val resource: String? = null,
    public val effect: Oc2PermissionEffect? = null,
)

@Serializable
public data class Oc2SavedPermission(
    public val id: String,
    public val projectID: String? = null,
    public val action: String? = null,
    public val resource: String? = null,
) {
    public companion object {
        public fun fromJson(obj: JsonObject): Oc2SavedPermission? =
            Oc2Wire.decode(serializer(), obj)
    }
}

@Serializable
public data class Oc2FormOption(
    public val value: String,
    public val label: String? = null,
    public val description: String? = null,
)

/** `{key, op: "eq"|"neq", value}`; `eq` against a multiselect answer means "includes". */
@Serializable
public data class Oc2FormCondition(
    public val key: String,
    public val op: String = "eq",
    public val value: JsonElement? = null,
) {
    public fun holds(answers: JsonObject): Boolean {
        if (key !in answers) return false
        val current = answers[key]
        val matches =
            if (current is JsonArray) current.any { jsonValueEquals(it, value) }
            else jsonValueEquals(current, value)
        return if (op == "neq") !matches else matches
    }
}

@Serializable
public data class Oc2FormField(
    public val key: String,
    public val type: Oc2FormFieldType = Oc2FormFieldType.Unknown,
    public val title: String? = null,
    public val description: String? = null,
    public val required: Boolean = false,
    public val `when`: List<Oc2FormCondition> = emptyList(),
    public val format: String? = null,
    public val minLength: Int? = null,
    public val maxLength: Int? = null,
    public val pattern: String? = null,
    public val placeholder: String? = null,
    @SerialName("default") public val defaultValue: JsonElement? = null,
    public val options: List<Oc2FormOption> = emptyList(),
    public val custom: Boolean = false,
    public val minimum: Double? = null,
    public val maximum: Double? = null,
    public val minItems: Int? = null,
    public val maxItems: Int? = null,
    public val url: String? = null,
) {
    /** Every `when` condition must hold for the field to be active. */
    public fun activeFor(answers: JsonObject): Boolean = `when`.all { it.holds(answers) }
}

/** A form; [sessionID] is a session id or the sentinel `"global"` (MCP elicitation). */
@Serializable
public data class Oc2FormInfo(
    public val id: String,
    public val sessionID: String = "",
    public val title: String? = null,
    public val metadata: JsonObject? = null,
    public val fields: List<Oc2FormField> = emptyList(),
) {
    public companion object {
        public fun fromJson(obj: JsonObject): Oc2FormInfo? = Oc2Wire.decode(serializer(), obj)
    }
}

@Serializable
public data class Oc2FormState(
    public val status: Oc2FormStatus = Oc2FormStatus.Unknown,
    public val answer: JsonObject? = null,
) {
    public companion object {
        public fun fromJson(element: JsonElement?): Oc2FormState =
            Oc2Wire.decode(serializer(), element) ?: Oc2FormState()
    }
}
