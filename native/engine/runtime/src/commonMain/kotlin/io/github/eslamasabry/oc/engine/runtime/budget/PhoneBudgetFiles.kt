package io.github.eslamasabry.oc.engine.runtime.budget

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The agent's own instructions and thread caps, preserving everything the person supplied. */
public object PhoneBudgetFiles {
    public const val PREFERENCE_KEY: String = "oc.fullSpeed"
    public const val BEGIN: String = "<!-- oc-phone-budget:begin -->"
    public const val END: String = "<!-- oc-phone-budget:end -->"

    public val note: String =
        "$BEGIN\n" +
            "# This is a phone\n\n" +
            "You run on a phone with a few cores, limited memory and a battery. " +
            "Heavy work heats it up and slows everything down. Please:\n\n" +
            "- Run tests and analyzers one or two at a time. Flutter: " +
            "`flutter test --concurrency=1`; other tools: `-j1` or `-j2`.\n" +
            "- Run one build at a time, and one test file before the whole suite.\n" +
            "- Never leave watch modes or dev servers running; stop what you started when you are done.\n" +
            "- Start as few background programs as you can: Android stops an app that has more than about 30 child processes.\n\n" +
            "If the person asks for something heavier, do it.\n" +
            "$END\n"

    public val environment: Map<String, String> = linkedMapOf(
        "MAKEFLAGS" to "-j2",
        "CMAKE_BUILD_PARALLEL_LEVEL" to "2",
        "CARGO_BUILD_JOBS" to "2",
        "GOMAXPROCS" to "2",
        "OMP_NUM_THREADS" to "2",
        "UV_THREADPOOL_SIZE" to "2",
        "GRADLE_OPTS" to "-Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false",
    )

    public fun withBlock(current: String, remove: Boolean): String {
        val from = current.indexOf(BEGIN)
        val to = if (from < 0) -1 else current.indexOf(END, from)
        var before = current
        var after = ""
        if (from >= 0 && to > from) {
            before = current.substring(0, from)
            after = current.substring(to + END.length).replaceFirst("\n", "")
        }
        if (remove) {
            val head = before.trimEnd()
            val joined = if (head.isEmpty()) after else "$head\n$after"
            return if (joined.isBlank()) "" else joined
        }
        val lead = when {
            before.isEmpty() || before.endsWith("\n\n") -> before
            before.endsWith("\n") -> "$before\n"
            else -> "$before\n\n"
        }
        return lead + note + after
    }

    /** Null refuses unreadable/non-object settings; empty means the file may be removed. */
    public fun settings(current: String, fullSpeed: Boolean): String? {
        val root = try {
            if (current.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(current) as? JsonObject ?: return null
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val rawEnv = root["env"]
        if (rawEnv != null && rawEnv != JsonNull && rawEnv !is JsonObject) return null
        val env = (rawEnv as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        var changed = false
        for ((key, value) in environment) {
            if (fullSpeed) {
                if (env[key] == JsonPrimitive(value)) {
                    env.remove(key)
                    changed = true
                }
            } else if (key !in env) {
                env[key] = JsonPrimitive(value)
                changed = true
            }
        }
        if (!changed) return current
        val result = root.toMutableMap()
        if (env.isEmpty()) result.remove("env") else result["env"] = JsonObject(env)
        return if (result.isEmpty()) "" else JsonObject(result).toString() + "\n"
    }
}
