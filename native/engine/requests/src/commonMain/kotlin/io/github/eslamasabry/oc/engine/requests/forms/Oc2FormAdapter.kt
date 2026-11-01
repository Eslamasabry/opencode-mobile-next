package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.model.FormSpec as WireForm
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * OpenCode 2 `Form.Info` → [FormSpec] (plan 09 §5.5; port of Dart `Api2FormField.fromJson` and the kind choice in
 * `form_renderer.dart`). Reads each field's complete wire object, tolerantly: a field without a key is dropped, an
 * unknown `type` is dropped from the form (it can never be answered), wrong-typed limits read as absent.
 *
 * Text kinds: a string with options is a select; `format: date` / `date-time` is a date; otherwise multi-line unless a
 * `maxLength` of 120 or less says the answer is short (Dart `form_renderer.dart:936`).
 */
public object Oc2FormAdapter {
    /** The OpenCode 2 sentinel session id of MCP elicitation forms. */
    public const val GLOBAL_SESSION: String = "global"

    private const val SHORT_TEXT_MAX = 120

    public fun adapt(form: WireForm): FormSpec {
        val metadata = form.metadata
        return FormSpec(
            id = form.id,
            owner = if (form.sessionId.raw == GLOBAL_SESSION) FormOwner.Connection else FormOwner.Session(form.sessionId),
            title = form.title.orEmpty(),
            description = metadata?.string("description"),
            fields = form.fields.mapNotNull { field(it.raw) }.toImmutableList(),
            submitLabel = metadata?.string("submitLabel"),
            revision = FormRevision.of(form),
            isQuestion = metadata?.string("kind") == "question",
        )
    }

    internal fun field(raw: JsonObject): FieldSpec? {
        val key = raw.string("key") ?: return null
        val format = raw.string("format")
        val options =
            (raw["options"] as? JsonArray).orEmpty().mapNotNull { option ->
                val o = option as? JsonObject ?: return@mapNotNull null
                val value = o.string("value") ?: return@mapNotNull null
                FieldOption(value = value, label = o.string("label") ?: value, description = o.string("description"))
            }
        val maxLength = raw.int("maxLength")
        val kind =
            when (raw.string("type")) {
                "string", "select" ->
                    when {
                        options.isNotEmpty() -> FieldKind.Select
                        format == "date" -> FieldKind.Date
                        format == "date-time" -> FieldKind.DateTime
                        maxLength != null && maxLength <= SHORT_TEXT_MAX -> FieldKind.Text
                        else -> FieldKind.Multiline
                    }
                "number" -> FieldKind.Number
                "integer" -> FieldKind.Integer
                "boolean" -> FieldKind.Toggle
                "multiselect" -> FieldKind.MultiSelect
                "external" -> FieldKind.ExternalLink
                else -> return null
            }
        return FieldSpec(
            key = key,
            label = raw.string("title") ?: key,
            description = raw.string("description"),
            kind = kind,
            required = raw.bool("required") ?: false,
            hidden = raw.bool("hidden") ?: false,
            default = raw["default"]?.let(::value),
            placeholder = raw.string("placeholder"),
            options = options.toImmutableList(),
            min = raw.double("minimum"),
            max = raw.double("maximum"),
            minLength = raw.int("minLength"),
            maxLength = maxLength,
            pattern = raw.string("pattern"),
            format =
                when (format) {
                    null -> null
                    "email" -> StringFormat.Email
                    "uri", "url" -> StringFormat.Uri
                    "date" -> StringFormat.Date
                    "date-time" -> StringFormat.DateTime
                    else -> StringFormat.Other
                },
            whenAll =
                (raw["when"] as? JsonArray).orEmpty().mapNotNull { c ->
                    val o = c as? JsonObject ?: return@mapNotNull null
                    val on = o.string("key") ?: return@mapNotNull null
                    FieldCondition(
                        key = on,
                        op = if (o.string("op") == "neq") ConditionOp.Neq else ConditionOp.Eq,
                        value = o["value"]?.let(::value),
                    )
                }.toImmutableList(),
            custom = raw.bool("custom") ?: false,
            minItems = raw.int("minItems"),
            maxItems = raw.int("maxItems"),
            url = raw.string("url"),
        )
    }

    /** A wire value as a typed answer; objects and mixed lists have no typed form and read as absent. */
    internal fun value(element: JsonElement): FormValue? =
        when (element) {
            is JsonPrimitive ->
                when {
                    element.isString -> FormValue.Text(element.content)
                    element.booleanOrNull != null -> FormValue.Toggle(element.booleanOrNull!!)
                    element.doubleOrNull != null -> FormValue.Number(element.doubleOrNull!!)
                    else -> null // JSON null
                }
            is JsonArray -> {
                val items = element.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                if (items.any { it == null }) null else FormValue.Choices(items.filterNotNull().toImmutableList())
            }
            is JsonObject -> null
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
}
