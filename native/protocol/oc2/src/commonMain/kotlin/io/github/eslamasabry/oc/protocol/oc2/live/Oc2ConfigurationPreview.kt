package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.domain.ConfigurationPreview
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** B-SEC-040: mask by field name before configuration leaves the authenticated adapter. */
internal class Oc2ConfigurationPreview(private val redactor: Redactor) {
    fun read(root: JsonElement): ConfigurationPreview? {
        redactor.registerCredentialValues(root)
        val entries = ((root as? JsonObject)?.get("data") ?: root) as? JsonArray ?: return null
        if (entries.any { it !is JsonObject || (it["type"] as? JsonPrimitive)?.isString != true })
            return null
        val sources = entries.map { entry ->
            val value = entry as JsonObject
            JsonObject(
                buildMap {
                    put("type", value.getValue("type"))
                    (value["path"] as? JsonPrimitive)
                        ?.takeIf { it.isString }
                        ?.let { put("path", it) }
                    (value["info"] as? JsonObject)?.let { put("info", it) }
                }
            )
        }
        val safe = mask(JsonObject(mapOf("sources" to JsonArray(sources))))
        return ConfigurationPreview(PREVIEW_JSON.encodeToString(JsonElement.serializer(), safe))
    }

    private fun mask(value: JsonElement, key: String = ""): JsonElement {
        if (PRIVATE_FIELD.containsMatchIn(key)) return JsonPrimitive(Redactor.MASK)
        return when (value) {
            is JsonObject ->
                JsonObject(
                    value.entries.associate { (name, field) ->
                        redactor.redact(name) to mask(field, name)
                    }
                )
            is JsonArray -> JsonArray(value.map { mask(it, key) })
            is JsonPrimitive ->
                if (value.isString) JsonPrimitive(redactor.redact(value.content)) else value
        }
    }

    private companion object {
        val PREVIEW_JSON = Json { prettyPrint = true }
        val PRIVATE_FIELD =
            Regex(
                "key|token|secret|password|credential|environment|headers|options|request|command|template|prompt|system|content",
                RegexOption.IGNORE_CASE,
            )
    }
}
