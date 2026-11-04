package io.github.eslamasabry.oc.engine.runtime.services.commands

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A person-reviewed command and destination, scoped to one saved server and project folder (port of
 * lib/domain/development_service.dart). Saving it never runs the command.
 */
public data class DevelopmentService(
    val id: String,
    val name: String,
    val command: String,
    val directory: String,
    val url: String,
    val workspace: String? = null,
    val run: DevelopmentServiceRun? = null,
) {
    public fun withRun(value: DevelopmentServiceRun?): DevelopmentService = copy(run = value)
}

/**
 * The ownership receipt. Written BEFORE the create request, so a lost response never silently authorizes a duplicate
 * start. Only the matching server metadata can recover an unknown id; an arbitrary discovered shell is never adopted.
 */
public data class DevelopmentServiceRun(
    val ownerToken: String,
    val shellId: String? = null,
    val startedAtMillis: Long? = null,
    val stopped: Boolean = false,
)

public enum class DevelopmentServiceStatus { NotStarted, Running, Stopped, Unknown }

/** The saved JSON, field for field the Flutter shape so a migrated `oc.developmentServices.<profileId>` reads as-is. */
internal object DevelopmentServiceJson {
    /** Null when anything is invalid (the Dart `FormatException`). */
    fun decode(element: JsonElement): DevelopmentService? {
        val json = element as? JsonObject ?: return null
        fun value(key: String, max: Int): String? =
            json.string(key)?.takeIf { it.isNotBlank() && it.length <= max }
        val run = when (val raw = json["run"]) {
            null, JsonNull -> null
            is JsonObject -> decodeRun(raw) ?: return null
            else -> return null
        }
        return DevelopmentService(
            id = value("id", 100) ?: return null,
            name = value("name", 80) ?: return null,
            command = value("command", 4096) ?: return null,
            directory = value("directory", 4096) ?: return null,
            workspace = json.string("workspace"),
            url = json.string("url") ?: "",
            run = run,
        )
    }

    private fun decodeRun(json: JsonObject): DevelopmentServiceRun? {
        val owner = json.string("owner")?.takeIf { it.isNotEmpty() && it.length <= 100 } ?: return null
        return DevelopmentServiceRun(
            ownerToken = owner,
            shellId = json.string("shellID"),
            startedAtMillis = (json["startedAt"] as? JsonPrimitive)?.longOrNull,
            stopped = (json["stopped"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    fun encode(service: DevelopmentService): JsonObject = buildJsonObject {
        put("id", service.id)
        put("name", service.name)
        put("command", service.command)
        put("directory", service.directory)
        put("workspace", service.workspace)
        put("url", service.url)
        val run = service.run
        if (run == null) {
            put("run", JsonNull)
        } else {
            put(
                "run",
                buildJsonObject {
                    put("owner", run.ownerToken)
                    put("shellID", run.shellId)
                    put("startedAt", run.startedAtMillis)
                    put("stopped", run.stopped)
                },
            )
        }
    }

    fun encodeAll(services: List<DevelopmentService>): JsonObject = buildJsonObject {
        put("version", 1)
        put("services", JsonArray(services.map(::encode)))
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
