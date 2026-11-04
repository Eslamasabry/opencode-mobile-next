package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The string preferences the setup stores use (logical keys, `oc.<what>.<profileId>`). Reads see acknowledged values;
 * a write answers false when the platform refused it, and the refused value is never visible afterwards.
 */
public interface SetupPrefs {
    public fun getString(key: String): String?

    public fun containsKey(key: String): Boolean

    public fun keys(): Set<String>

    public suspend fun setString(key: String, value: String): Boolean

    public suspend fun remove(key: String): Boolean
}

/** The per-profile keys the setup assistant owns; both match the Flutter sweep rule (`oc.<what>.<profileId>`). */
public object SetupProfileKeys {
    public fun audit(profileId: String): String = "oc.setupAudit.$profileId"

    public fun registry(profileId: String): String = "oc.setupRegistry.$profileId"

    /** The key prefixes to register with `ProfileScope` (Profile scope, string values). */
    public val prefixes: List<String> = listOf("oc.setupAudit.", "oc.setupRegistry.")
}

/** Whether a profile still exists. The default reads the `oc.profiles` list, as the Flutter audit store did. */
public fun interface ProfilePresence {
    public fun isPresent(profileId: String): Boolean

    public companion object {
        public fun fromProfilesKey(prefs: SetupPrefs): ProfilePresence = ProfilePresence { id ->
            val raw = prefs.getString("oc.profiles") ?: return@ProfilePresence false
            val decoded = try {
                Json.parseToJsonElement(raw)
            } catch (_: IllegalArgumentException) {
                return@ProfilePresence false
            }
            decoded is JsonArray &&
                decoded.any { (it as? JsonObject)?.get("id").let { v -> v is JsonPrimitive && v.isString && v.content == id } }
        }
    }
}

/** [SetupPrefs] over the app's one key-value store, writing in [lane]. */
public class KeyValueSetupPrefs(private val store: KeyValueStore, private val lane: WriterLane) : SetupPrefs {
    override fun getString(key: String): String? = (store.get(key) as? PrefValue.Str)?.value

    override fun containsKey(key: String): Boolean = store.get(key) != null

    override fun keys(): Set<String> = store.keys()

    override suspend fun setString(key: String, value: String): Boolean = store.commit(lane) { putString(key, value) } is Outcome.Ok

    override suspend fun remove(key: String): Boolean = store.commit(lane) { remove(key) } is Outcome.Ok
}
