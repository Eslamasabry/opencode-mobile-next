package io.github.eslamasabry.oc.android.widgets.snapshot

import io.github.eslamasabry.oc.android.widgets.WidgetPersistence
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import kotlin.math.roundToInt
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** One limit window of a quota reading the monitor accepted as fresh. */
public data class UsageQuotaWindow(val seconds: Long?, val remainingPercent: Double?, val resetsAt: Long?)

/**
 * A provider quota reading (the display half of Dart `ProviderQuotaSnapshot`). [usable] is Dart `canShowWindows`:
 * the reading is ok and belongs to the matched account. Account references, plans and errors have no field here.
 */
public data class UsageQuotaReading(
    val provider: String,
    val usable: Boolean,
    val fetchedAt: Long,
    val windows: List<UsageQuotaWindow>,
)

/** One window a daemon reported: a length in seconds, or its own label when it has none. */
public data class UsageReadingWindow(val seconds: Long?, val label: String?, val remainingPercent: Double, val resetsAt: Long?)

/** A provider a daemon reported (Dart `ProviderUsageReading`): cleaned name and numbers only. */
public data class UsageProviderReading(
    val providerId: String,
    val displayName: String,
    val fetchedAt: Long,
    val needsSignIn: Boolean,
    val windows: List<UsageReadingWindow>,
)

/**
 * The stored shape of the Usage widget (Flutter `UsageWidgetSnapshot`): one key per server,
 * `oc.widgetUsage.<profileId>` = `{name, rows: {provider: {state, fetchedAt, windows, action?, name?}}, local?}`.
 * Pure: each change maps the stored text to the next one, or to null when nothing is left.
 */
public object UsageWidgetSnapshotRows {
    public const val KEY_PREFIX: String = "oc.widgetUsage"
    public const val MAX_WINDOWS_PER_PROVIDER: Int = 3
    public const val SIGN_IN: String = "sign_in"
    public const val ADD_KEY: String = "add_key"

    public fun key(profileId: String): String = "$KEY_PREFIX.$profileId"

    /** GLM and MiniMax take a key; the rest sign in. */
    public fun actionFor(providerId: String): String = if (providerId == "glm" || providerId == "minimax") ADD_KEY else SIGN_IN

    /** Dart `quotaCollectionAvailable`: Claude's collection is off, so it can never appear. */
    public fun collected(provider: String): Boolean = provider == "codex" || provider == "minimax" || provider == "glm"

    private val providerId = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

    public fun validProviderId(id: String): Boolean = providerId.matches(id)

    internal class Rows(var name: String, val rows: LinkedHashMap<String, LinkedHashMap<String, JsonElement>>)

    internal fun decode(raw: String?, profileName: String): Rows {
        val rows = LinkedHashMap<String, LinkedHashMap<String, JsonElement>>()
        var name = profileName
        if (raw != null) {
            try {
                val decoded = Json.parseToJsonElement(raw) as? JsonObject
                if (decoded != null) {
                    if (name.isEmpty()) name = (decoded["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    (decoded["rows"] as? JsonObject)?.forEach { (k, v) -> if (v is JsonObject) rows[k] = LinkedHashMap(v) }
                }
            } catch (_: SerializationException) {
                // Unreadable payload: start over rather than keep unknown content.
            } catch (_: IllegalArgumentException) {
            }
        }
        return Rows(name, rows)
    }

    /** The next stored text, or null when no provider is left. */
    internal fun encode(rows: Rows, local: Boolean): String? {
        if (rows.rows.isEmpty()) return null
        return buildJsonObject {
            put("name", rows.name)
            put("rows", JsonObject(rows.rows.mapValues { JsonObject(it.value) }))
            if (local) put("local", true)
        }.toString()
    }

    private fun Long?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    internal fun recordRow(reading: UsageQuotaReading): LinkedHashMap<String, JsonElement>? {
        if (!collected(reading.provider) || !reading.usable) return null
        val windows =
            reading.windows
                .filter { it.remainingPercent != null && it.seconds != null }
                .take(MAX_WINDOWS_PER_PROVIDER)
                .map {
                    buildJsonObject {
                        put("seconds", it.seconds)
                        put("left", it.remainingPercent!!.coerceIn(0.0, 100.0).roundToInt())
                        put("resetsAt", it.resetsAt.json())
                    }
                }
        if (windows.isEmpty()) return null
        return linkedMapOf("state" to JsonPrimitive("ok"), "fetchedAt" to JsonPrimitive(reading.fetchedAt), "windows" to JsonArray(windows))
    }

    internal fun readingRows(readings: List<UsageProviderReading>): LinkedHashMap<String, LinkedHashMap<String, JsonElement>> {
        val rows = LinkedHashMap<String, LinkedHashMap<String, JsonElement>>()
        for (r in readings) {
            if (r.needsSignIn && r.windows.isEmpty()) {
                rows[r.providerId] =
                    linkedMapOf(
                        "state" to JsonPrimitive("signin"),
                        "action" to JsonPrimitive(actionFor(r.providerId)),
                        "name" to JsonPrimitive(r.displayName),
                        "fetchedAt" to JsonPrimitive(r.fetchedAt),
                        "windows" to JsonArray(emptyList()),
                    )
                continue
            }
            if (r.windows.isEmpty()) continue
            rows[r.providerId] =
                linkedMapOf(
                    "state" to JsonPrimitive("ok"),
                    "name" to JsonPrimitive(r.displayName),
                    "fetchedAt" to JsonPrimitive(r.fetchedAt),
                    "windows" to
                        JsonArray(
                            r.windows.take(MAX_WINDOWS_PER_PROVIDER).map { w ->
                                buildJsonObject {
                                    if (w.seconds != null) put("seconds", w.seconds) else put("label", w.label.orEmpty())
                                    put("left", w.remainingPercent.coerceIn(0.0, 100.0).roundToInt())
                                    put("resetsAt", w.resetsAt.json())
                                }
                            },
                        ),
                )
        }
        return rows
    }

    internal fun hasWindows(row: Map<String, JsonElement>): Boolean = (row["windows"] as? JsonArray)?.isNotEmpty() == true
}

/**
 * Persists the display numbers of the Usage widgets (Flutter `UsageWidgetSnapshot`, B-ANDR-100). The widget may be
 * drawn while the app is gone, so it reads these stored numbers and never the network. Only what it prints is kept:
 * the provider, the server's saved name, each window's length, remaining percent and reset time, and when they were
 * measured. A change that leaves the stored text equal neither writes nor redraws. Callers serialize per profile.
 */
public class UsageWidgetSnapshotStore(
    private val capabilities: PlatformCapabilities,
    private val read: (key: String) -> String?,
    /** Writes [value] (null removes the key); true when the store acknowledged it. */
    private val write: suspend (key: String, value: String?) -> Boolean,
    private val redraw: () -> Unit,
    private val now: () -> Long,
) {
    /** A measurement the monitor accepted as fresh. Claude and unusable readings never appear. */
    public suspend fun record(profileId: String, profileName: String, reading: UsageQuotaReading) {
        val row = UsageWidgetSnapshotRows.recordRow(reading) ?: return
        mutate(profileId, profileName) { it[reading.provider] = row }
    }

    /** Replaces everything stored for a server with what its daemon just reported. */
    public suspend fun recordReadings(profileId: String, profileName: String, readings: List<UsageProviderReading>, thisPhone: Boolean = false) {
        mutate(profileId, profileName, local = thisPhone) {
            it.clear()
            it.putAll(UsageWidgetSnapshotRows.readingRows(readings))
        }
    }

    /** The stored sign-in stopped working: keep the last numbers (a row of its own when none) and offer the fix. */
    public suspend fun markAuthNeeded(profileId: String, profileName: String, provider: String) {
        if (!UsageWidgetSnapshotRows.collected(provider)) return
        mutate(profileId, profileName) { rows ->
            val row = rows.getOrPut(provider) { linkedMapOf("fetchedAt" to JsonPrimitive(now()), "windows" to JsonArray(emptyList())) }
            row["state"] = JsonPrimitive("auth")
            row["action"] = JsonPrimitive(UsageWidgetSnapshotRows.actionFor(provider))
        }
    }

    /** Not signed in where the app reads it: numbers are never invented; a row with numbers keeps them. */
    public suspend fun markNotSignedIn(profileId: String, profileName: String, providerId: String, displayName: String = "", thisPhone: Boolean = false) {
        if (!UsageWidgetSnapshotRows.validProviderId(providerId)) return
        mutate(profileId, profileName, local = thisPhone) { rows ->
            val row = rows.getOrPut(providerId) { linkedMapOf("fetchedAt" to JsonPrimitive(now()), "windows" to JsonArray(emptyList())) }
            if (UsageWidgetSnapshotRows.hasWindows(row)) return@mutate
            row["state"] = JsonPrimitive("signin")
            row["action"] = JsonPrimitive(UsageWidgetSnapshotRows.actionFor(providerId))
            if (displayName.isNotEmpty()) row["name"] = JsonPrimitive(displayName)
        }
    }

    /** Monitoring of this provider ended; the last provider gone removes the server's entry. */
    public suspend fun removeProvider(profileId: String, provider: String) {
        mutate(profileId, "") { it.remove(provider) }
    }

    /** Drops everything stored for [profileId]; true when nothing is left. */
    public suspend fun clearForProfile(profileId: String): Boolean {
        if (profileId.isEmpty()) return true
        val key = UsageWidgetSnapshotRows.key(profileId)
        if (read(key) == null) return true
        if (!write(key, null)) return false
        if (capabilities.homeWidgets) redraw()
        return true
    }

    private suspend fun mutate(
        profileId: String,
        profileName: String,
        local: Boolean = false,
        change: (LinkedHashMap<String, LinkedHashMap<String, JsonElement>>) -> Unit,
    ) {
        if (!capabilities.homeWidgets || profileId.isEmpty()) return
        val key = UsageWidgetSnapshotRows.key(profileId)
        val raw = read(key)
        val rows = UsageWidgetSnapshotRows.decode(raw, profileName)
        change(rows.rows)
        val next = UsageWidgetSnapshotRows.encode(rows, local)
        if (next == raw) return
        if (!write(key, next)) return
        redraw()
    }

    public companion object {
        /** Over the graph's one widget store, on the profile's writer lane; redraws through [redraw]. */
        public fun over(
            persistence: WidgetPersistence,
            capabilities: PlatformCapabilities,
            redraw: () -> Unit,
        ): UsageWidgetSnapshotStore =
            UsageWidgetSnapshotStore(
                capabilities = capabilities,
                read = persistence::string,
                write = { key, value ->
                    val profile = key.removePrefix(UsageWidgetSnapshotRows.KEY_PREFIX + ".")
                    persistence.store.commit(persistence.lanes.forProfile(ProfileId(profile))) {
                        if (value == null) remove(key) else putString(key, value)
                    } is Outcome.Ok
                },
                redraw = redraw,
                now = { persistence.clock.now().toEpochMilliseconds() },
            )
    }
}
