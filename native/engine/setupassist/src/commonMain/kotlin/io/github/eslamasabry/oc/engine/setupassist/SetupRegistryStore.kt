package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

public enum class SetupRegistryStatus { Disabled, Loading, Ready, Empty, Offline, Error }

public data class SetupRegistrySnapshot(
    val status: SetupRegistryStatus,
    val optedIn: Boolean,
    val entries: List<RegistryEntry> = emptyList(),
    val cachedAt: Instant? = null,
    val reason: String? = null,
    /** The search these entries answer; null for the saved default list. Search results are shown, never saved. */
    val query: String? = null,
)

/**
 * Phone-side registry browsing (port of lib/state/setup_registry_store.dart): disabled by default, never follows entry
 * URLs, one instance per profile. Close (and await it) before deleting or changing the profile.
 */
public class SetupRegistryStore(
    private val prefs: SetupPrefs,
    public val profileId: String,
    private val client: SetupRegistryClient,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    init {
        require(profileId.isNotEmpty() && !redactor.containsSecret(profileId)) { "A non-secret profile identifier is required." }
    }

    private val writes = Mutex()
    private var generation = 0
    private var closed = false
    private var pending: RegistryCancellation? = null
    private val state = MutableStateFlow(SetupRegistrySnapshot(SetupRegistryStatus.Disabled, optedIn = false))
    private val events = MutableSharedFlow<SetupRegistrySnapshot>(extraBufferCapacity = 64)
    private var savedEntries: List<RegistryEntry> = emptyList()
    private var savedAt: Instant? = null

    public val storageKey: String get() = SetupProfileKeys.registry(profileId)
    public val snapshot: SetupRegistrySnapshot get() = state.value
    public val snapshots: StateFlow<SetupRegistrySnapshot> = state.asStateFlow()

    /** Every published state in order (the Dart `changes` stream). */
    public val changes: SharedFlow<SetupRegistrySnapshot> = events.asSharedFlow()

    private fun ensureOpen() = check(!closed) { "The registry store is closed." }

    /** Reads the cache only; never implies consent or causes a network request. */
    public fun load() {
        ensureOpen()
        val raw = prefs.getString(storageKey) ?: return
        if (raw.length > SetupRegistryClient.MAX_RESPONSE_BYTES) return
        val data = try {
            Json.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            emit(SetupRegistrySnapshot(SetupRegistryStatus.Error, optedIn = false, reason = "The saved registry list could not be read. Set up by hand."))
            return
        }
        if (data !is JsonObject || (data["version"] as? JsonPrimitive)?.intOrNull != 1) return
        val optedIn = (data["optedIn"] as? JsonPrimitive)?.content == "true"
        val entries = ((data["entries"] as? JsonArray) ?: JsonArray(emptyList())).take(100).mapNotNull { RegistryEntry.fromJson(redactor, it) }
        val cachedAt = (data["cachedAt"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseInstant)
        emit(
            SetupRegistrySnapshot(
                status = when {
                    !optedIn -> SetupRegistryStatus.Disabled
                    entries.isEmpty() -> SetupRegistryStatus.Empty
                    else -> SetupRegistryStatus.Ready
                },
                optedIn = optedIn,
                entries = entries,
                cachedAt = cachedAt,
            ),
        )
    }

    /**
     * Consent is profile scoped. Enabling does not fetch. Disabling cancels any request, keeps the safe cached list and
     * prevents future refreshes. A failed save answers a failure and leaves the network off.
     */
    public suspend fun setOptIn(enabled: Boolean): SetupOutcome<Unit> {
        ensureOpen()
        val generation = ++generation
        pending?.cancel()
        val next = SetupRegistrySnapshot(
            status = when {
                !enabled -> SetupRegistryStatus.Disabled
                savedEntries.isEmpty() -> SetupRegistryStatus.Empty
                else -> SetupRegistryStatus.Ready
            },
            optedIn = enabled,
            entries = savedEntries,
            cachedAt = savedAt,
        )
        emit(next) // network off at once, even if saving fails
        if (persist(next, generation)) return SetupOutcome.Ok(Unit)
        if (!closed && generation == this.generation) {
            emit(SetupRegistrySnapshot(SetupRegistryStatus.Error, false, next.entries, next.cachedAt, reason = SAVE_FAILED))
        }
        return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Storage, SAVE_FAILED))
    }

    /**
     * A refresh the person asked for. Offline and opted-out paths are cache-only. A non-empty [query] asks the registry
     * for matching listings; those are shown, never saved, and [showSaved] returns to the saved list.
     */
    public suspend fun refresh(online: Boolean = true, query: String? = null) {
        ensureOpen()
        if (!snapshot.optedIn) return
        val search = query?.trim() ?: ""
        val saved = savedEntries
        val savedAt = savedAt
        if (!online) {
            generation++
            pending?.cancel()
            emit(
                SetupRegistrySnapshot(
                    SetupRegistryStatus.Offline,
                    optedIn = true,
                    entries = saved,
                    cachedAt = savedAt,
                    reason = "You are offline. Showing saved listings; set up by hand is available.",
                ),
            )
            return
        }
        val generation = ++generation
        pending?.cancel()
        val cancellation = RegistryCancellation()
        pending = cancellation
        emit(
            SetupRegistrySnapshot(
                SetupRegistryStatus.Loading,
                optedIn = true,
                entries = if (search.isEmpty()) saved else emptyList(),
                cachedAt = savedAt,
                query = search.ifEmpty { null },
            ),
        )
        val fetched = client.fetch(search.ifEmpty { null }, cancellation)
        if (fetched is SetupOutcome.Ok) {
            if (closed || generation != this.generation || !prefs.containsKey(storageKey)) return
            val entries = fetched.value
            val status = if (entries.isEmpty()) SetupRegistryStatus.Empty else SetupRegistryStatus.Ready
            if (search.isNotEmpty()) {
                emit(SetupRegistrySnapshot(status, optedIn = true, entries = entries, cachedAt = savedAt, query = search))
                return
            }
            val next = SetupRegistrySnapshot(status, optedIn = true, entries = entries, cachedAt = clock.now())
            if (persist(next, generation, requireExisting = true)) {
                if (!closed && generation == this.generation) emit(next)
                return
            }
        }
        if (!closed && generation == this.generation) {
            emit(
                SetupRegistrySnapshot(
                    SetupRegistryStatus.Error,
                    optedIn = snapshot.optedIn,
                    entries = if (search.isEmpty()) saved else emptyList(),
                    cachedAt = savedAt,
                    query = search.ifEmpty { null },
                    reason = if (search.isEmpty()) {
                        "The public registry could not be loaded. Showing saved listings."
                    } else {
                        "The public registry could not be searched."
                    },
                ),
            )
        }
    }

    /** Back from a search to the saved default list, without a request. */
    public fun showSaved() {
        ensureOpen()
        if (!snapshot.optedIn || snapshot.query == null) return
        generation++
        pending?.cancel()
        emit(
            SetupRegistrySnapshot(
                if (savedEntries.isEmpty()) SetupRegistryStatus.Empty else SetupRegistryStatus.Ready,
                optedIn = true,
                entries = savedEntries,
                cachedAt = savedAt,
            ),
        )
    }

    /** Removes both the opt-in preference and the cache without affecting profiles. */
    public suspend fun clear(): SetupOutcome<Unit> {
        ensureOpen()
        generation++
        pending?.cancel()
        emit(SetupRegistrySnapshot(SetupRegistryStatus.Disabled, optedIn = false))
        val removed = writes.withLock { prefs.remove(storageKey) }
        return if (removed) {
            SetupOutcome.Ok(Unit)
        } else {
            SetupOutcome.Failed(SetupFailure(SetupFailureCode.Storage, "The saved registry list could not be removed."))
        }
    }

    private suspend fun persist(value: SetupRegistrySnapshot, generation: Int, requireExisting: Boolean = false): Boolean {
        val encoded = redactor.redact(
            buildJsonObject {
                put("version", 1)
                put("optedIn", value.optedIn)
                put("cachedAt", value.cachedAt?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                put("entries", registryEntriesJson(redactor, value.entries))
            }.toString(),
        )
        return writes.withLock {
            if (closed || generation != this.generation || (requireExisting && !prefs.containsKey(storageKey))) {
                true
            } else {
                prefs.setString(storageKey, encoded)
            }
        }
    }

    private fun emit(value: SetupRegistrySnapshot) {
        state.value = value
        if (value.query == null) {
            savedEntries = value.entries
            savedAt = value.cachedAt
        }
        if (!closed) events.tryEmit(value)
    }

    /**
     * Await before profile deletion: writes already started drain before this returns; queued stale writes and
     * network answers are ignored.
     */
    public suspend fun close() {
        closed = true
        generation++
        pending?.cancel()
        writes.withLock { }
    }

    private companion object {
        const val SAVE_FAILED = "The registry preference could not be saved."

        fun parseInstant(text: String): Instant? = try {
            Instant.parse(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
