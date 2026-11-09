package io.github.eslamasabry.oc.core.data.cache

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * [LastKnown] on files: `<root>/<profile hash>/<shelf>/<item hash>.json`, each published through a sibling `.tmp` and
 * an atomic rename, plus one `_order.json` per shelf (newest first) that decides eviction. Profile ids and items are
 * hashed, so no path segment carries a server's words. Memory holds decoded values up to [memoryBudgetBytes] (least
 * recently used goes first) so [peek] can answer a first frame without I/O. [io] is the injected I/O dispatcher.
 */
public class FileLastKnown(
    private val root: Path,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val fs: FileSystem = SystemFileSystem,
    private val memoryBudgetBytes: Long = DEFAULT_MEMORY_BUDGET,
) : LastKnown {
    private val log = AppLog.tagged("LastKnown")
    private val disk = Mutex()
    private val memory = AtomicReference<PersistentMap<String, Held>>(persistentMapOf())
    private val ticks = AtomicLong(0)

    private class Held(
        val mark: ServerMark,
        val serializer: KSerializer<*>,
        val value: Any?,
        val bytes: Long,
        val tick: Long,
    )

    override fun <T> peek(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String): T? {
        val held = memory.load()[key(profile, shelf, item)] ?: return null
        if (held.mark != mark || held.serializer !== shelf.serializer) return null
        @Suppress("UNCHECKED_CAST")
        return held.value as T
    }

    override suspend fun <T> read(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String): T? {
        peek(profile, mark, shelf, item)?.let { return it }
        return withContext(io) {
            disk.withLock {
                val file = entryFile(profile, shelf, item)
                val text = readText(file, shelf.maxBytes + ENVELOPE_SLACK) ?: return@withLock null
                when (val decoded = decode(text, mark, shelf)) {
                    null -> {
                        // Another server's data (or an unreadable file) never shows; drop it now.
                        remove(file)
                        null
                    }
                    else -> {
                        hold(profile, shelf, item, mark, decoded.first, decoded.second)
                        decoded.first
                    }
                }
            }
        }
    }

    override suspend fun <T> write(
        profile: ProfileId,
        mark: ServerMark,
        shelf: CacheShelf<T>,
        item: String,
        value: T,
    ): Outcome<Unit> {
        val data =
            try {
                JSON.encodeToJsonElement(shelf.serializer, value)
            } catch (e: SerializationException) {
                log.w(e) { "shelf ${shelf.name}: value would not encode" }
                return Outcome.Failed(Failure.StorageRefused())
            }
        if (carriesCredential(data)) {
            log.w { "shelf ${shelf.name}: refused a value with a credential-shaped key" }
            return Outcome.Failed(Failure.StorageRefused())
        }
        val payload = data.toString()
        if (payload.length > shelf.maxBytes || payload.encodeToByteArray().size > shelf.maxBytes)
            return Outcome.Failed(Failure.StorageRefused())
        val envelope =
            buildJsonObject {
                    put("v", VERSION)
                    put("mark", mark.value)
                    put("at", clock.now().toEpochMilliseconds())
                    put("data", data)
                }
                .toString()
        return withContext(io) {
            disk.withLock {
                try {
                    val dir = shelfDir(profile, shelf)
                    fs.createDirectories(dir)
                    val file = entryFile(profile, shelf, item)
                    publish(file, envelope)
                    val order = listOf(itemName(item)) + readOrder(dir).filter { it != itemName(item) }
                    val kept = order.take(shelf.maxEntries)
                    for (gone in order.drop(shelf.maxEntries)) {
                        remove(Path(dir, "$gone.json"))
                        forget("${profileName(profile)}/${shelf.name}/$gone")
                    }
                    publish(Path(dir, ORDER_FILE), DartJson.encode(kept))
                    hold(profile, shelf, item, mark, value, payload.length.toLong())
                    Outcome.Ok(Unit)
                } catch (e: IOException) {
                    log.w(e) { "shelf ${shelf.name}: write failed" }
                    Outcome.Failed(Failure.StorageRefused())
                }
            }
        }
    }

    override suspend fun warm(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<*>) {
        val items =
            withContext(io) { disk.withLock { readOrder(shelfDir(profile, shelf)) } }
        // Only the single-entry shelves and the newest few items: warming never reads a whole shelf of transcripts.
        for (name in items.take(WARM_ITEMS)) readByName(profile, mark, shelf, name)
    }

    private suspend fun <T> readByName(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, name: String) {
        val memoryKey = "${profileName(profile)}/${shelf.name}/$name"
        if (memory.load()[memoryKey]?.mark == mark) return
        withContext(io) {
            disk.withLock {
                val file = Path(shelfDir(profile, shelf), "$name.json")
                val text = readText(file, shelf.maxBytes + ENVELOPE_SLACK) ?: return@withLock
                val decoded = decode(text, mark, shelf) ?: return@withLock
                holdKey(memoryKey, mark, shelf, decoded.first, decoded.second)
            }
        }
    }

    override suspend fun clear(profile: ProfileId): Outcome<Int> =
        withContext(io) {
            disk.withLock {
                val prefix = "${profileName(profile)}/"
                memory.update { map -> map.keys.filter { it.startsWith(prefix) }.fold(map) { m, k -> m.removing(k) } }
                try {
                    Outcome.Ok(removeTree(Path(root, profileName(profile))))
                } catch (e: IOException) {
                    log.w(e) { "clearing a profile's last-known data failed" }
                    Outcome.Failed(Failure.StorageRefused())
                }
            }
        }

    private fun <T> decode(text: String, mark: ServerMark, shelf: CacheShelf<T>): Pair<T, Long>? =
        try {
            val envelope = JSON.parseToJsonElement(text).jsonObject
            val version = envelope["v"]?.jsonPrimitive?.longOrNull
            val saved = envelope["mark"]?.jsonPrimitive?.contentOrNull
            val data = envelope["data"]
            if (version != VERSION.toLong() || saved != mark.value || data == null) null
            else JSON.decodeFromJsonElement(shelf.serializer, data) to text.length.toLong()
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun <T> hold(profile: ProfileId, shelf: CacheShelf<T>, item: String, mark: ServerMark, value: T, bytes: Long) =
        holdKey(key(profile, shelf, item), mark, shelf, value, bytes)

    private fun <T> holdKey(key: String, mark: ServerMark, shelf: CacheShelf<T>, value: T, bytes: Long) {
        val held = Held(mark, shelf.serializer, value, bytes, ticks.fetchAndAdd(1))
        memory.update { map ->
            var next = map.putting(key, held)
            var total = next.values.sumOf { it.bytes }
            while (total > memoryBudgetBytes && next.size > 1) {
                val oldest = next.entries.filter { it.key != key }.minBy { it.value.tick }
                next = next.removing(oldest.key)
                total -= oldest.value.bytes
            }
            next
        }
    }

    private fun forget(key: String) = memory.update { it.removing(key) }

    private inline fun AtomicReference<PersistentMap<String, Held>>.update(
        change: (PersistentMap<String, Held>) -> PersistentMap<String, Held>
    ) {
        while (true) {
            val current = load()
            if (compareAndSet(current, change(current))) return
        }
    }

    private fun readOrder(dir: Path): List<String> {
        val text = readText(Path(dir, ORDER_FILE), ORDER_MAX_BYTES) ?: return emptyList()
        return try {
            (JSON.parseToJsonElement(text) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(ITEM::matches) }
                .orEmpty()
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    private fun readText(file: Path, maxBytes: Int): String? {
        val meta = fs.metadataOrNull(file) ?: return null
        if (!meta.isRegularFile || meta.size > maxBytes) return null
        return try {
            fs.source(file).buffered().use { it.readString() }
        } catch (e: IOException) {
            null
        }
    }

    private fun publish(file: Path, text: String) {
        val temporary = Path(file.parent!!, "${file.name}.tmp")
        fs.sink(temporary).buffered().use { it.writeString(text) }
        fs.atomicMove(temporary, file)
    }

    private fun remove(file: Path) {
        try {
            fs.delete(file, mustExist = false)
        } catch (e: IOException) {
            log.w(e) { "a stale last-known entry could not be removed" }
        }
    }

    private fun removeTree(path: Path): Int {
        val meta = fs.metadataOrNull(path) ?: return 0
        var removed = 0
        if (meta.isDirectory) {
            for (child in fs.list(path)) removed += removeTree(child)
        } else if (path.name.endsWith(".json") && path.name != ORDER_FILE) {
            removed++
        }
        fs.delete(path, mustExist = false)
        return removed
    }

    private fun profileName(profile: ProfileId): String = DartJson.sha256Hex("profile:${profile.raw}").take(HASH_CHARS)

    private fun itemName(item: String): String =
        if (item.isEmpty()) SINGLE_ITEM else DartJson.sha256Hex("item:$item").take(HASH_CHARS)

    private fun shelfDir(profile: ProfileId, shelf: CacheShelf<*>): Path =
        Path(Path(root, profileName(profile)), shelf.name)

    private fun entryFile(profile: ProfileId, shelf: CacheShelf<*>, item: String): Path =
        Path(shelfDir(profile, shelf), "${itemName(item)}.json")

    private fun key(profile: ProfileId, shelf: CacheShelf<*>, item: String): String =
        "${profileName(profile)}/${shelf.name}/${itemName(item)}"

    private fun carriesCredential(element: JsonElement): Boolean =
        when (element) {
            is JsonObject -> element.any { (k, v) -> CredentialKeys.matches(k) || carriesCredential(v) }
            is JsonArray -> element.any(::carriesCredential)
            else -> false
        }

    public companion object {
        /** Decoded values held for first frames: a list per profile and a handful of recent transcripts. */
        public const val DEFAULT_MEMORY_BUDGET: Long = 6L shl 20
        private const val VERSION = 1
        private const val HASH_CHARS = 32
        private const val SINGLE_ITEM = "_"
        private const val ORDER_FILE = "_order.json"
        private const val ORDER_MAX_BYTES = 64 * 1024
        private const val ENVELOPE_SLACK = 512
        private const val WARM_ITEMS = 3
        private val ITEM = Regex("^(_|[0-9a-f]{32})$")
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    }
}
