package io.github.eslamasabry.oc.core.data.cache

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.KSerializer

/**
 * Cache-first (owner rule, 2026-11-09: "why not cache something to load fast? This applies everywhere"): every
 * screen that reads a server draws its REAL layout at once from the last data it saw, then refreshes in place. There
 * is no offline, cached or connecting variant of any screen; connection state lives only in the status slot or the
 * server pill.
 *
 * [LastKnown] is the one place that last-seen server data is kept, per profile:
 * - one [CacheShelf] per kind of data (the conversation list, a transcript's last page, a folder listing, …);
 * - every entry carries the [ServerMark] it was read from: an entry saved for another server address, user or folder
 *   never shows, so changing a profile's server invalidates its shelves;
 * - each shelf has a byte cap per entry and a count of entries kept; the least recently written entry goes first;
 * - the whole profile's data goes with the profile (a `ProfileResourceCleaner` calls [clear]);
 * - secrets never enter: a value whose JSON has a credential-shaped key is refused (see [CacheShelf]).
 *
 * The data are display facts the person already saw on screen. They are a cache: losing them only costs one cold
 * load, so the platform may put them where the system can reclaim space.
 */
public interface LastKnown {
    /**
     * The value already held in memory, for a screen's FIRST frame (no I/O). Null when nothing was read or written in
     * this process yet, or when the entry belongs to another server. Call [read] (or [warm]) to fill memory.
     */
    public fun <T> peek(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String = ""): T?

    /** The last value written for this entry and server; null when absent, unreadable or from another server. */
    public suspend fun <T> read(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String = ""): T?

    /**
     * Keeps [value] as the entry's last-known value. Refused (never thrown) when the encoded value is over the shelf's
     * cap or carries a credential-shaped key; the caller trims what it keeps (for example the newest messages only).
     */
    public suspend fun <T> write(
        profile: ProfileId,
        mark: ServerMark,
        shelf: CacheShelf<T>,
        item: String = "",
        value: T,
    ): Outcome<Unit>

    /** Loads [shelf]'s entries for [profile] into memory, so [peek] answers on the next screen's first frame. */
    public suspend fun warm(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<*>)

    /** Removes everything kept for [profile]. Idempotent; Ok(entries removed). */
    public suspend fun clear(profile: ProfileId): Outcome<Int>
}

/**
 * One kind of last-known data. [name] is a short camelCase word (it names a folder). [maxBytes] caps one entry's
 * encoded size; [maxEntries] is how many items the shelf keeps per profile (1 for a single list).
 */
public class CacheShelf<T>(
    public val name: String,
    public val serializer: KSerializer<T>,
    public val maxBytes: Int,
    public val maxEntries: Int = 1,
) {
    init {
        require(NAME.matches(name)) { "a shelf name is a short camelCase word" }
        require(maxBytes in 1..MAX_ENTRY_BYTES) { "a shelf entry is at most $MAX_ENTRY_BYTES bytes" }
        require(maxEntries in 1..MAX_SHELF_ENTRIES) { "a shelf keeps at most $MAX_SHELF_ENTRIES entries" }
    }

    override fun toString(): String = "CacheShelf($name)"

    public companion object {
        public const val MAX_ENTRY_BYTES: Int = 1 shl 20
        public const val MAX_SHELF_ENTRIES: Int = 200
        private val NAME = Regex("^[a-z][A-Za-z0-9]{1,39}$")
    }
}

/**
 * Which server (and place on it) an entry was read from: a hash of the parts that make data differ, such as the
 * backend kind, base URL, user name and folder. Never put a password or token in [of]'s parts.
 */
public data class ServerMark(public val value: String) {
    override fun toString(): String = "ServerMark(${value.take(8)})"

    public companion object {
        public fun of(vararg parts: String?): ServerMark = ServerMark(DartJson.sha256Hex(DartJson.encode(parts.toList())))
    }
}

/** Keys whose values would be credentials. A cached value never carries one (AGENTS.md security invariants). */
internal object CredentialKeys {
    private val SHAPE =
        Regex("(?i)^(password|passwd|secret|token|accesstoken|refreshtoken|authtoken|bearer|apikey|api_key|authorization|cookie|credentials?)$")

    fun matches(key: String): Boolean = SHAPE.matches(key)
}
