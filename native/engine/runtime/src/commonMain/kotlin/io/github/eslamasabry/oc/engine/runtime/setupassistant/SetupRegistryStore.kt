package io.github.eslamasabry.oc.engine.runtime.setupassistant

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Public listing metadata only. Listings are never executed or followed by this browser. */
public data class SetupRegistryEntry(
    val name: String,
    val version: String,
    val title: String,
    val description: String,
) {
    public val id: String
        get() = "$name@$version"

    internal fun json(): JsonObject = buildJsonObject {
        put("name", name)
        put("version", version)
        put("title", title)
        put("description", description)
    }
}

public enum class SetupRegistryPhase {
    Disabled,
    Loading,
    Ready,
    Empty,
    Offline,
    Failed,
}

public data class SetupRegistryState(
    val phase: SetupRegistryPhase = SetupRegistryPhase.Disabled,
    val optedIn: Boolean = false,
    val entries: ImmutableList<SetupRegistryEntry> = persistentListOf(),
    val cachedAt: Instant? = null,
    val query: String? = null,
)

/**
 * The single unauthenticated public endpoint; platform binding enforces byte, redirect and timeout
 * limits.
 */
public fun interface SetupRegistryReader {
    public suspend fun fetch(search: String?): Outcome<String>
}

/** Profile-scoped consent/cache with a serial writer and generation-fenced explicit refresh. */
public class SetupRegistryStore(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val profile: ProfileId,
    private val reader: SetupRegistryReader,
    private val redactor: Redactor,
    private val clock: Clock,
    private val present: () -> Boolean,
) {
    private val write = Mutex()
    @Volatile private var closed = false
    private val generation = AtomicInt(0)
    private var saved = SetupRegistryState()
    private val mutable = MutableStateFlow(SetupRegistryState())
    public val state: StateFlow<SetupRegistryState> = mutable.asStateFlow()
    public val key: String = "oc.setupRegistry.${profile.raw}"

    public fun load() {
        if (closed || !present()) return
        val raw = store.string(key) ?: return
        if (raw.length > MAX_BYTES) return
        val objectValue = parse(raw) as? JsonObject ?: return
        if ((objectValue["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != 1)
            return
        val opted = (objectValue["optedIn"] as? JsonPrimitive)?.booleanOrNull == true
        val entries = entries(objectValue["entries"] as? JsonArray ?: JsonArray(emptyList()))
        val at =
            objectValue["cachedAt"].string()?.let {
                try {
                    Instant.parse(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        saved = SetupRegistryState(phase(opted, entries), opted, entries, at)
        mutable.value = saved
    }

    /** Turning off fences requests immediately, then waits behind every already accepted write. */
    public suspend fun setOptIn(enabled: Boolean): Outcome<Unit> {
        if (closed || !present()) return stale()
        val mine = generation.incrementAndFetch()
        val next = saved.copy(phase = phase(enabled, saved.entries), optedIn = enabled)
        mutable.value = next
        return write.withLock {
            if (closed || !present() || mine != generation.load()) return@withLock stale()
            when (val committed = persist(next)) {
                is Outcome.Ok -> {
                    if (!closed && mine == generation.load()) {
                        saved = next
                        mutable.value = next
                    }
                    committed
                }
                is Outcome.Failed -> {
                    if (!closed && mine == generation.load())
                        mutable.value =
                            next.copy(phase = SetupRegistryPhase.Failed, optedIn = false)
                    committed
                }
            }
        }
    }

    /**
     * No automatic networking. Search results are visible only; they never replace the saved
     * default list.
     */
    public suspend fun refresh(online: Boolean, query: String? = null): Outcome<Unit> {
        if (closed || !present() || !mutable.value.optedIn) return stale()
        val term = query?.trim()?.takeIf { it.isNotEmpty() }
        if (term != null && (term.length > 100 || redactor.containsSecret(term)))
            return Outcome.Failed(Failure.StorageRefused())
        val mine = generation.incrementAndFetch()
        if (!online) {
            mutable.value = saved.copy(phase = SetupRegistryPhase.Offline)
            return stale()
        }
        mutable.value =
            saved.copy(
                phase = SetupRegistryPhase.Loading,
                entries = if (term == null) saved.entries else persistentListOf(),
                query = term,
            )
        val reply =
            when (val fetched = attempt({ Failure.Device() }) { reader.fetch(term) }) {
                is Outcome.Ok -> fetched.value
                is Outcome.Failed -> fetched
            }
        if (!current(mine)) return stale()
        val decoded =
            (reply as? Outcome.Ok)?.value?.takeIf { it.length <= MAX_BYTES }?.let(::parse)
                as? JsonObject
        val rows = decoded?.get("servers") as? JsonArray
        if (rows == null) {
            mutable.value =
                saved.copy(
                    phase = SetupRegistryPhase.Failed,
                    query = term,
                    entries = if (term == null) saved.entries else persistentListOf(),
                )
            return (reply as? Outcome.Failed) ?: Outcome.Failed(Failure.UnreadableReply())
        }
        val listings =
            rows.take(100).mapNotNull { raw ->
                val row = raw as? JsonObject ?: return@mapNotNull null
                val official =
                    ((row["_meta"] as? JsonObject)?.get("io.modelcontextprotocol.registry/official")
                            as? JsonObject)
                        ?.get("status")
                        .string()
                if (official == "deleted" || official == "deprecated") null else row["server"]
            }
        val found = entries(JsonArray(listings))
        val next =
            SetupRegistryState(
                phase(true, found),
                true,
                found,
                if (term == null) clock.now() else saved.cachedAt,
                term,
            )
        if (term != null) {
            mutable.value = next
            return Outcome.Ok(Unit)
        }
        return write.withLock {
            if (!current(mine) || store.string(key) == null) return@withLock stale()
            when (val committed = persist(next)) {
                is Outcome.Ok -> {
                    if (current(mine)) {
                        saved = next
                        mutable.value = next
                    }
                    committed
                }
                is Outcome.Failed -> {
                    if (current(mine)) mutable.value = saved.copy(phase = SetupRegistryPhase.Failed)
                    committed
                }
            }
        }
    }

    public fun showSaved() {
        if (!closed) {
            generation.incrementAndFetch()
            mutable.value = saved
        }
    }

    public suspend fun clear(): Outcome<Unit> {
        if (closed || !present()) return stale()
        val mine = generation.incrementAndFetch()
        saved = SetupRegistryState()
        mutable.value = saved
        return write.withLock {
            if (closed || !present() || mine != generation.load()) return@withLock stale()
            val result = store.commit(lanes.forProfile(profile)) { remove(key) }
            if (mine == generation.load() && !closed) {
                if (result is Outcome.Ok) saved = SetupRegistryState()
                else mutable.value = SetupRegistryState(phase = SetupRegistryPhase.Failed)
            }
            result
        }
    }

    public fun stopAdmission() {
        closed = true
        generation.incrementAndFetch()
    }

    public suspend fun close() {
        closed = true
        generation.incrementAndFetch()
        write.withLock { mutable.value = SetupRegistryState() }
    }

    private fun current(mine: Int): Boolean =
        !closed && present() && mine == generation.load() && mutable.value.optedIn

    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    private suspend fun persist(next: SetupRegistryState): Outcome<Unit> {
        val encoded = buildJsonObject {
            put("version", 1)
            put("optedIn", next.optedIn)
            put("cachedAt", next.cachedAt?.toString())
            put("entries", JsonArray(next.entries.map { it.json() }))
        }
            .toString()
        return store.commit(lanes.forProfile(profile)) { putString(key, encoded) }
    }

    private fun entries(rows: JsonArray): ImmutableList<SetupRegistryEntry> =
        rows
            .take(100)
            .mapNotNull { raw ->
                val row = raw as? JsonObject ?: return@mapNotNull null
                val name =
                    row["name"].string()?.takeIf {
                        it.length in 1..200 &&
                            IDENTIFIER.matches(it) &&
                            !redactor.containsSecret(it)
                    } ?: return@mapNotNull null
                val version =
                    row["version"].string()?.takeIf {
                        it.length in 1..200 &&
                            IDENTIFIER.matches(it) &&
                            !redactor.containsSecret(it)
                    } ?: return@mapNotNull null
                SetupRegistryEntry(
                    name,
                    version,
                    redactor.redact(row["title"].string().orEmpty()).take(120),
                    redactor.redact(row["description"].string().orEmpty()).take(1000),
                )
            }
            .toImmutableList()

    private fun phase(opted: Boolean, entries: List<SetupRegistryEntry>): SetupRegistryPhase =
        when {
            !opted -> SetupRegistryPhase.Disabled
            entries.isEmpty() -> SetupRegistryPhase.Empty
            else -> SetupRegistryPhase.Ready
        }

    private fun parse(raw: String): JsonElement? =
        try {
            Json.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val MAX_BYTES = 512 * 1024
        val IDENTIFIER = Regex("^[A-Za-z0-9._/@:+-]+$")
    }
}
