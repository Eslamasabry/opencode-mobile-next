package io.github.eslamasabry.oc.feature.agents.tools.presentation

import kotlinx.serialization.json.*

/** The first paragraph/list item, stopped at its first sentence, without the list marker. */
public fun toolSummary(description: String): String {
    val marker = Regex("^([-*•]|\\d+[.)])\\s+")
    val first = mutableListOf<String>()
    for (line in description.trim().lines().map(String::trim)) {
        if (line.isEmpty() || (first.isNotEmpty() && marker.containsMatchIn(line))) break
        first += line.replaceFirst(marker, "")
    }
    val text = first.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    val end = Regex("[.!?](?=\\s|$)").find(text)
    return if (end == null) text else text.substring(0, end.range.last + 1)
}

public data class ToolParameter(
    val name: String,
    val required: Boolean,
    val type: String?,
    val description: String?,
)

/** Preserve the server's declaration order within required and optional groups. */
public fun toolParameters(schema: JsonElement?): List<ToolParameter> {
    val root = schema as? JsonObject ?: return emptyList()
    val properties = root["properties"] as? JsonObject ?: return emptyList()
    val required =
        (root["required"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .toSet()
    return properties
        .map { (name, value) ->
            val details = value as? JsonObject
            ToolParameter(
                name,
                name in required,
                (details?.get("type") as? JsonPrimitive)?.contentOrNull,
                (details?.get("description") as? JsonPrimitive)?.contentOrNull,
            )
        }
        .sortedByDescending { it.required }
}
