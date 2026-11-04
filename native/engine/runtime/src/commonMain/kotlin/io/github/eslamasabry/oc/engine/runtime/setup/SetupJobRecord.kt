package io.github.eslamasabry.oc.engine.runtime.setup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Port of lib/builtin/setup/setup_engine/job_record.dart (setup.json, as SetupRunner.kt writes it). Unknown fields are
 * ignored and missing ones take their empty value, so an older or newer runner never breaks the screen.
 */
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
    /** `errorCode == "setup_persistence"`: the runner could not save setup.json. */
    val persistenceFailure: Boolean = false,
) {
    val canContinue: Boolean get() = state == "failed" || state == "interrupted" || state == "cancelled"

    public fun component(id: String): SetupJobComponent? = components.firstOrNull { it.id == id }

    public companion object {
        /** Null for no job or text that is not a job. */
        public fun parse(text: String?): SetupJobRecord? {
            if (text == null || text.isBlank()) return null
            val decoded = try {
                Json.parseToJsonElement(text)
            } catch (_: SerializationException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (decoded !is JsonObject) return null
            val jobId = decoded.string("jobId") ?: return null
            val componentMap = decoded["components"] as? JsonObject ?: JsonObject(emptyMap())
            val order = decoded["order"]
            val ids = if (order is JsonArray) {
                order.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            } else {
                componentMap.keys.toList()
            }
            val params = buildMap {
                (decoded["params"] as? JsonObject)?.forEach { (key, value) ->
                    if (value is JsonObject) put(key, strings(value))
                }
            }
            val errorCode = (decoded["errorCode"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return SetupJobRecord(
                jobId = jobId,
                host = decoded.string("host"),
                persistenceFailure = errorCode == "setup_persistence",
                state = decoded.string("state") ?: "interrupted",
                current = decoded.string("current"),
                components = ids.mapNotNull { id ->
                    (componentMap[id] as? JsonObject)?.let { SetupJobComponent.fromJson(id, it) }
                },
                startedAt = decoded.long("startedAt") ?: 0,
                updatedAt = decoded.long("updatedAt") ?: 0,
                error = decoded.string("error"),
                logTail = decoded.string("logTail") ?: "",
                params = params,
            )
        }
    }
}

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
        get() = when (state) {
            "running" -> ComponentState.Running
            "done" -> ComponentState.Done
            "failed" -> ComponentState.Failed
            "skipped" -> ComponentState.Skipped
            else -> ComponentState.Pending
        }

    public companion object {
        internal fun fromJson(id: String, map: JsonObject): SetupJobComponent = SetupJobComponent(
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

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

private fun JsonObject.long(key: String): Long? = number(key)?.toLong()

private fun strings(value: JsonObject): Map<String, String> = buildMap {
    for ((key, element) in value) {
        val text = text(element) ?: continue
        put(key, text)
    }
}

/** Dart's `'${entry.value}'` for a non-null value. */
private fun text(element: JsonElement): String? = when (element) {
    JsonNull -> null
    is JsonPrimitive -> element.content
    else -> element.toString()
}
