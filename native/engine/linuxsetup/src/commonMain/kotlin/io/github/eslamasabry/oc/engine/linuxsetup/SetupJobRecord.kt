package io.github.eslamasabry.oc.engine.linuxsetup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Native failures whose category is safe to use independently of log text. */
public enum class SetupFailureKind { Persistence }

/** setup.json, read (port of SetupJobRecord in setup_engine/job_record.dart). */
public data class SetupJobRecord(
    val jobId: String,
    /** running | done | failed | cancelled | interrupted */
    val state: String,
    val components: List<SetupJobComponent>,
    val current: String? = null,
    val startedAt: Long = 0,
    val updatedAt: Long = 0,
    val error: String? = null,
    val logTail: String = "",
    val params: Map<String, Map<String, String>> = emptyMap(),
    /** Missing only for legacy built-in jobs. */
    val host: String? = null,
    val failureKind: SetupFailureKind? = null,
) {
    val canContinue: Boolean get() = state == "failed" || state == "interrupted" || state == "cancelled"

    public fun component(id: String): SetupJobComponent? = components.firstOrNull { it.id == id }

    public companion object {
        private val json = Json { isLenient = false }

        /**
         * Null for no job or text that is not a job; unknown fields are ignored and missing ones take their empty
         * value, so an older or newer runner never breaks the screen.
         */
        public fun parse(text: String?): SetupJobRecord? {
            if (text == null || text.isBlank()) return null
            val decoded = decode(text) as? JsonObject ?: return null
            val jobId = decoded.string("jobId") ?: return null
            val componentMap = decoded["components"] as? JsonObject ?: JsonObject(emptyMap())
            val order = decoded["order"]
            val ids =
                if (order is JsonArray) {
                    order.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                } else {
                    componentMap.keys.toList()
                }
            val params = linkedMapOf<String, Map<String, String>>()
            (decoded["params"] as? JsonObject)?.forEach { (key, value) ->
                if (value is JsonObject) params[key] = strings(value)
            }
            return SetupJobRecord(
                jobId = jobId,
                host = decoded.string("host"),
                failureKind = if (decoded.string("errorCode") == "setup_persistence") SetupFailureKind.Persistence else null,
                state = decoded.string("state") ?: "interrupted",
                current = decoded.string("current"),
                components = ids.mapNotNull { id -> (componentMap[id] as? JsonObject)?.let { SetupJobComponent.fromMap(id, it) } },
                startedAt = decoded.long("startedAt") ?: 0,
                updatedAt = decoded.long("updatedAt") ?: 0,
                error = decoded.string("error"),
                logTail = decoded.string("logTail") ?: "",
                params = params,
            )
        }

        private fun decode(text: String): JsonElement? =
            try {
                json.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                null // kotlinx SerializationException is an IllegalArgumentException
            }
    }
}

/** One component's row in setup.json. */
public data class SetupJobComponent(
    val id: String,
    /** pending | running | done | failed | skipped */
    val state: String,
    val weight: Double = 1.0,
    val stage: String? = null,
    val done: Long? = null,
    val total: Long? = null,
    val percent: Double? = null,
    val version: String? = null,
    val error: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val data: Map<String, String> = emptyMap(),
) {
    val componentState: ComponentState
        get() =
            when (state) {
                "running" -> ComponentState.Running
                "done" -> ComponentState.Done
                "failed" -> ComponentState.Failed
                "skipped" -> ComponentState.Skipped
                else -> ComponentState.Pending
            }

    public companion object {
        public fun fromMap(id: String, map: JsonObject): SetupJobComponent =
            SetupJobComponent(
                id = id,
                state = map.string("state") ?: "pending",
                weight = map.number("weight") ?: 1.0,
                stage = map.string("stage"),
                done = map.long("done"),
                total = map.long("total"),
                percent = map.number("percent"),
                version = map.string("version"),
                error = map.string("error"),
                startedAt = map.long("startedAt"),
                endedAt = map.long("endedAt"),
                data = (map["data"] as? JsonObject)?.let(::strings) ?: emptyMap(),
            )
    }
}

private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.string(key: String): String? =
    primitive(key)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

private fun JsonObject.number(key: String): Double? = primitive(key)?.takeIf { !it.isString }?.doubleOrNull

private fun JsonObject.long(key: String): Long? = number(key)?.toLong()

private fun strings(value: JsonObject): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for ((key, field) in value) {
        when (field) {
            is JsonNull -> Unit
            is JsonPrimitive -> out[key] = if (field.isString) field.content else (field.booleanOrNull?.toString() ?: field.content)
            else -> out[key] = field.toString()
        }
    }
    return out
}
