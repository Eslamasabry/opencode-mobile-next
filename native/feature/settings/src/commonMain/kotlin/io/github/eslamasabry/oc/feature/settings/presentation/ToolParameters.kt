package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a tool parameter takes, in the person's words (Flutter `ToolParameterType`: "text", "number", "yes or no", …). */
public enum class ToolParameterType { Text, Number, YesNo, List, Group, Any }

public data class ToolParameter(val name: String, val type: ToolParameterType, val required: Boolean, val description: String?)

/**
 * The parameters a tool's JSON Schema declares (Flutter `toolParameters`, part of B-NAV-047): required first, in the
 * schema's own order otherwise. Anything that is not an object schema with `properties` takes nothing the app can
 * describe. A nullable type (`["integer", "null"]`) reads as its non-null type.
 */
public object ToolParameters {
    public fun of(schema: JsonElement?): ImmutableList<ToolParameter> {
        val properties = ((schema as? JsonObject)?.get("properties") as? JsonObject) ?: return persistentListOf()
        val required =
            ((schema["required"] as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .toSet()
        val all =
            properties.map { (name, property) ->
                val description =
                    ((property as? JsonObject)?.get("description") as? JsonPrimitive)
                        ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
                ToolParameter(name, typeOf(property), name in required, description)
            }
        return (all.filter { it.required } + all.filterNot { it.required }).toImmutableList()
    }

    private fun typeOf(property: JsonElement): ToolParameterType {
        val type = (property as? JsonObject)?.get("type")
        val name =
            when (type) {
                is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.firstOrNull { it != "null" }
                is JsonPrimitive -> type.takeIf { it.isString }?.content
                else -> null
            }
        return when (name) {
            "string" -> ToolParameterType.Text
            "number", "integer" -> ToolParameterType.Number
            "boolean" -> ToolParameterType.YesNo
            "array" -> ToolParameterType.List
            "object" -> ToolParameterType.Group
            else -> ToolParameterType.Any
        }
    }
}
