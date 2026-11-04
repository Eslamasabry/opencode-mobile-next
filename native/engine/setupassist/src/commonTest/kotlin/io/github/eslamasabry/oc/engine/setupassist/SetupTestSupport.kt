package io.github.eslamasabry.oc.engine.setupassist

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Instant

/** In-memory string preferences with the Flutter test disks' seams: a write gate and a refusal switch. */
internal class FakePrefs(initial: Map<String, String> = emptyMap()) : SetupPrefs {
    val values = LinkedHashMap(initial)
    var refuse: (String) -> Boolean = { false }

    /** When set, a write to a key matching [gateKey] waits on [gate] after completing [entered]. */
    var gate: CompletableDeferred<Unit>? = null
    var gateKey: String = ""
    var entered = CompletableDeferred<Unit>()

    override fun getString(key: String): String? = values[key]

    override fun containsKey(key: String): Boolean = key in values

    override fun keys(): Set<String> = values.keys.toSet()

    override suspend fun setString(key: String, value: String): Boolean {
        val pending = gate
        if (pending != null && key == gateKey) {
            entered.complete(Unit)
            pending.await()
        }
        if (refuse(key)) return false
        values[key] = value
        return true
    }

    override suspend fun remove(key: String): Boolean {
        values.remove(key)
        return true
    }
}

internal class FixedClock(var now: Instant = Instant.parse("2026-11-03T10:00:00Z")) : Clock {
    override fun now(): Instant = now
}

internal fun json(text: String): JsonElement = Json.parseToJsonElement(text)

internal fun obj(text: String): JsonObject = json(text).jsonObject
