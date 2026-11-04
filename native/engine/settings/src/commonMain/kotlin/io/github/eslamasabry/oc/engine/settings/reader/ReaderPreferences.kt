package io.github.eslamasabry.oc.engine.settings.reader

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Display choices of the file reader; they belong to a server profile, never to source content. */
public data class ReaderPreferences(val sourceFirst: Boolean = false, val wrapCode: Boolean? = null)

/**
 * Port of lib/state/reader_preferences.dart (B-STATE-069): the reader's display choices per profile, under
 * `oc.readerPreferences.<profileId>` (registered with [ProfileScope], so profile deletion sweeps it). A choice is
 * published only after storage accepts it; a refused write keeps the last saved choice. Writes go through the
 * profile's own writer lane, so deletion (which closes and drains that lane before the sweep) waits for an accepted
 * save and a late save after deletion is refused.
 */
public class ReaderPreferencesStore(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val profileId: ProfileId,
) {
    private val mutableValue = MutableStateFlow(read(store, profileId))
    public val value: StateFlow<ReaderPreferences> = mutableValue.asStateFlow()
    private val lock = Mutex()

    /** Saves the given choices (null = keep). True once storage accepted them; false keeps the prior choices. */
    public suspend fun update(sourceFirst: Boolean? = null, wrapCode: Boolean? = null): Boolean = lock.withLock {
        val previous = read(store, profileId)
        val next = ReaderPreferences(sourceFirst ?: previous.sourceFirst, wrapCode ?: previous.wrapCode)
        val encoded = buildJsonObject {
            put("sourceFirst", next.sourceFirst)
            put("wrapCode", next.wrapCode)
        }.toString()
        val result = store.commit(lanes.forProfile(profileId)) { putString(keyFor(profileId), encoded) }
        if (result is Outcome.Ok) mutableValue.value = next
        result is Outcome.Ok
    }

    public companion object {
        public const val PREFIX: String = "oc.readerPreferences."

        public fun keyFor(id: ProfileId): String = PREFIX + id.raw

        /** The ledger row; register once with the app's [ProfileScope]. */
        public val SHAPE: KeyShape =
            KeyShape(PREFIX, KeyScope.Profile, PrefType.Str, ":engine:settings", "file reader display choices")

        public fun register(scope: ProfileScope) {
            scope.register(SHAPE)
        }

        /** Unreadable or missing → defaults (never throws). */
        public fun read(store: KeyValueStore, id: ProfileId): ReaderPreferences {
            val raw = (store.get(keyFor(id)) as? PrefValue.Str)?.value ?: return ReaderPreferences()
            val data =
                try {
                    Json.parseToJsonElement(raw) as? JsonObject
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return ReaderPreferences()
            return ReaderPreferences(
                sourceFirst = (data["sourceFirst"] as? JsonPrimitive)?.booleanOrNull == true,
                wrapCode = (data["wrapCode"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull,
            )
        }
    }
}
