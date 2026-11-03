package io.github.eslamasabry.oc.engine.connectors.catalogue

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryClient
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** Dart `SetupRegistryStatus`. */
public enum class CatalogueStatus { Disabled, Loading, Ready, Empty, Offline, Error }

/** Why the catalogue shows a notice (Dart's fixed `reason` copy; the UI maps it to words). */
public enum class CatalogueReason {
    /** "The saved registry list could not be read. Set up by hand." */
    SavedListUnreadable,

    /** "The registry preference could not be saved." */
    PreferenceNotSaved,

    /** "You are offline. Showing saved listings; set up by hand is available." */
    Offline,

    /** "The public registry could not be loaded. Showing saved listings." */
    LoadFailed,

    /** "The public registry could not be searched." */
    SearchFailed,
}

/** Dart `SetupRegistrySnapshot`. [query] non-null: search results, shown and never saved. */
public data class CatalogueSnapshot(
    val status: CatalogueStatus,
    val optedIn: Boolean,
    val entries: List<RegistryEntry> = emptyList(),
    val cachedAtMs: Long? = null,
    val reason: CatalogueReason? = null,
    val query: String? = null,
)

/**
 * PORT over the per-profile preference `oc.setupRegistry.<profileId>` (a JSON string in the Flutter format:
 * `{"version":1,"optedIn":bool,"cachedAt":ISO-8601|null,"entries":[…]}`). [KeyValueCatalogueCache] is the adapter.
 */
public interface CatalogueCache {
    public fun read(profileId: String): String?

    public fun contains(profileId: String): Boolean = read(profileId) != null

    public suspend fun write(profileId: String, value: String): Outcome<Unit>

    public suspend fun remove(profileId: String): Outcome<Unit>

    public companion object {
        public const val PREFIX: String = "oc.setupRegistry."

        public fun key(profileId: String): String = PREFIX + profileId

        /** The ledger row; the Flutter migration ledger names `:engine:integrations` as the owner. */
        public val Shape: KeyShape =
            KeyShape(PREFIX, KeyScope.Profile, PrefType.Str, ":engine:integrations", "MCP catalogue consent and list")
    }
}

/** [CatalogueCache] over the app's [KeyValueStore], writing in the profile's [lane]. */
public class KeyValueCatalogueCache(private val store: KeyValueStore, private val lane: (String) -> WriterLane) :
    CatalogueCache {
    override fun read(profileId: String): String? = store.string(CatalogueCache.key(profileId))

    override suspend fun write(profileId: String, value: String): Outcome<Unit> =
        store.commit(lane(profileId)) { putString(CatalogueCache.key(profileId), value) }

    override suspend fun remove(profileId: String): Outcome<Unit> =
        store.commit(lane(profileId)) { remove(CatalogueCache.key(profileId)) }
}

/**
 * The per-profile catalogue (Dart `SetupRegistryStore`, lib/state/setup_registry_store.dart). Off by default; loading
 * reads the cache only and never implies consent or a request; turning it on does not fetch; turning it off keeps the
 * safe saved list and stops refreshes. A search answer is shown and never saved as the default list.
 * Instantiate per profile; [dispose] before deleting or changing the profile.
 */
public class CatalogueStore(
    private val cache: CatalogueCache,
    public val profileId: String,
    private val client: RegistryClient,
    private val nowMs: () -> Long,
    private val redactor: Redactor,
) {
    init {
        require(profileId.isNotEmpty() && !redactor.containsSecret(profileId)) {
            "A non-secret profile id is required."
        }
    }

    private val state = MutableStateFlow(CatalogueSnapshot(CatalogueStatus.Disabled, optedIn = false))
    public val snapshot: StateFlow<CatalogueSnapshot> = state.asStateFlow()
    private val writes = Mutex()
    private var generation = 0
    private var disposed = false
    private var savedEntries: List<RegistryEntry> = emptyList()
    private var savedAtMs: Long? = null

    /** Read the cache only. */
    public fun load() {
        checkOpen()
        try {
            val raw = cache.read(profileId) ?: return
            if (raw.length > RegistryTransport.MAX_RESPONSE_BYTES) return
            val data = Json.parseToJsonElement(raw) as? JsonObject ?: return
            if ((data["version"] as? JsonPrimitive)?.let { !it.isString && it.content == "1" } != true) return
            val optedIn = (data["optedIn"] as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true
            val entries =
                (data["entries"] as? JsonArray)
                    ?.take(100)
                    ?.mapNotNull { RegistryEntry.fromJson(it, redactor) }
                    .orEmpty()
            val cachedAt = (data["cachedAt"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseInstantMs)
            emit(
                CatalogueSnapshot(
                    status =
                        when {
                            !optedIn -> CatalogueStatus.Disabled
                            entries.isEmpty() -> CatalogueStatus.Empty
                            else -> CatalogueStatus.Ready
                        },
                    optedIn = optedIn,
                    entries = entries,
                    cachedAtMs = cachedAt,
                ),
            )
        } catch (_: SerializationException) {
            emit(UNREADABLE)
        } catch (_: IllegalArgumentException) {
            emit(UNREADABLE)
        }
    }

    /** Consent is profile-scoped. Enabling does not fetch; disabling keeps the safe saved list. */
    public suspend fun setOptIn(enabled: Boolean): Outcome<Unit> {
        checkOpen()
        val gen = ++generation
        val next =
            CatalogueSnapshot(
                status =
                    when {
                        !enabled -> CatalogueStatus.Disabled
                        savedEntries.isEmpty() -> CatalogueStatus.Empty
                        else -> CatalogueStatus.Ready
                    },
                optedIn = enabled,
                entries = savedEntries,
                cachedAtMs = savedAtMs,
            )
        emit(next) // network off immediately, even if saving fails
        val saved = persist(next, gen, requireExisting = false)
        if (saved is Outcome.Failed) {
            if (!disposed && gen == generation) {
                emit(
                    next.copy(
                        status = CatalogueStatus.Error,
                        optedIn = false,
                        reason = CatalogueReason.PreferenceNotSaved,
                    ),
                )
            }
            return Failure.StorageRefused().failed()
        }
        return Unit.ok()
    }

    /** A person-requested refresh. [query] non-blank asks the registry's search; its answer is never saved. */
    public suspend fun refresh(online: Boolean = true, query: String? = null) {
        checkOpen()
        if (!state.value.optedIn) return
        val search = query?.trim().orEmpty()
        val saved = savedEntries
        val savedAt = savedAtMs
        if (!online) {
            generation++
            emit(CatalogueSnapshot(CatalogueStatus.Offline, true, saved, savedAt, CatalogueReason.Offline))
            return
        }
        val gen = ++generation
        emit(
            CatalogueSnapshot(
                CatalogueStatus.Loading,
                optedIn = true,
                entries = if (search.isEmpty()) saved else emptyList(),
                cachedAtMs = savedAt,
                query = search.ifEmpty { null },
            ),
        )
        val fetched = client.fetch(search.ifEmpty { null })
        if (fetched is Outcome.Failed) {
            if (!disposed && gen == generation) {
                emit(
                    CatalogueSnapshot(
                        CatalogueStatus.Error,
                        optedIn = state.value.optedIn,
                        entries = if (search.isEmpty()) saved else emptyList(),
                        cachedAtMs = savedAt,
                        reason = if (search.isEmpty()) CatalogueReason.LoadFailed else CatalogueReason.SearchFailed,
                        query = search.ifEmpty { null },
                    ),
                )
            }
            return
        }
        val entries = (fetched as Outcome.Ok).value
        if (disposed || gen != generation || !cache.contains(profileId)) return
        val status = if (entries.isEmpty()) CatalogueStatus.Empty else CatalogueStatus.Ready
        if (search.isNotEmpty()) {
            emit(CatalogueSnapshot(status, true, entries, savedAt, query = search))
            return
        }
        val next = CatalogueSnapshot(status, true, entries, nowMs())
        val written = persist(next, gen, requireExisting = true)
        if (written is Outcome.Failed) {
            if (!disposed && gen == generation) {
                emit(CatalogueSnapshot(CatalogueStatus.Error, true, saved, savedAt, CatalogueReason.LoadFailed))
            }
            return
        }
        if (!disposed && gen == generation) emit(next)
    }

    /** Back from a search to the saved default list, without a request. */
    public fun showSaved() {
        checkOpen()
        val now = state.value
        if (!now.optedIn || now.query == null) return
        generation++
        val status = if (savedEntries.isEmpty()) CatalogueStatus.Empty else CatalogueStatus.Ready
        emit(CatalogueSnapshot(status, true, savedEntries, savedAtMs))
    }

    /** Removes both the consent and the list. */
    public suspend fun clear(): Outcome<Unit> {
        checkOpen()
        generation++
        emit(CatalogueSnapshot(CatalogueStatus.Disabled, optedIn = false))
        return writes.withLock { cache.remove(profileId) }
    }

    /** Pending network answers and queued stale writes are ignored after this. */
    public suspend fun dispose() {
        disposed = true
        generation++
        writes.withLock { }
    }

    private suspend fun persist(value: CatalogueSnapshot, gen: Int, requireExisting: Boolean): Outcome<Unit> {
        val encoded =
            redactor.redact(
                buildJsonObject {
                    put("version", 1)
                    put("optedIn", value.optedIn)
                    val cachedAt = value.cachedAtMs?.let { JsonPrimitive(Instant.fromEpochMilliseconds(it).toString()) }
                    put("cachedAt", cachedAt ?: JsonNull)
                    put("entries", JsonArray(value.entries.map { it.toJson(redactor) }))
                }.toString(),
            )
        return writes.withLock {
            if (disposed || gen != generation || (requireExisting && !cache.contains(profileId))) {
                Unit.ok()
            } else {
                cache.write(profileId, encoded)
            }
        }
    }

    private fun emit(value: CatalogueSnapshot) {
        if (value.query == null) {
            savedEntries = value.entries
            savedAtMs = value.cachedAtMs
        }
        if (!disposed) state.value = value
    }

    private fun checkOpen() {
        check(!disposed) { "The catalogue store is closed." }
    }

    public companion object {
        private val UNREADABLE =
            CatalogueSnapshot(CatalogueStatus.Error, optedIn = false, reason = CatalogueReason.SavedListUnreadable)

        /** Dart `DateTime.tryParse` for the cache's ISO-8601 `cachedAt`; null when unreadable. */
        public fun parseInstantMs(text: String): Long? =
            try {
                Instant.parse(text).toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
