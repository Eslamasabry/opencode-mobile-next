package io.github.eslamasabry.oc.engine.delivery.queue

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The result of reading `oc.offlineQueue`. */
public sealed interface QueueRead {
    public data class Readable(val entries: List<QueuedPrompt>) : QueueRead

    /**
     * Corrupt JSON, a wrongly typed preference or a bad entry: nothing is sent or overwritten until
     * cleared (B-STATE-034).
     */
    public data object Unreadable : QueueRead
}

/**
 * `oc.offlineQueue` over the adopted Flutter preferences (plan 04 §13.2: switch release; Room comes
 * at switch+1 behind the same calls). App-wide, written through the app [lane] with `commit()`.
 * Port of `OfflineQueueStore`.
 *
 * - Reads never tidy: limits are the engine's job ([QueueLimits]), on its first read.
 * - Writes are serialized (FIFO lane) and take a snapshot of the list at call time, so an older
 *   save can never resurrect a prompt removed by a later one (B-STATE-035).
 * - A non-empty save over an unreadable blob is refused; only the explicit empty save (the person's
 *   "clear") replaces it (B-STATE-034).
 */
public class OfflineQueueStore(private val store: KeyValueStore, private val lane: WriterLane) {
    private var loaded = false
    public var loadedStamp: PrefValue? = null
        private set

    public fun load(): QueueRead {
        val value = store.get(KEY)
        loadedStamp = value
        loaded = true
        if (value == null) return QueueRead.Readable(emptyList())
        val raw = (value as? PrefValue.Str)?.value ?: return QueueRead.Unreadable
        return try {
            QueueRead.Readable(QueuedPromptCodec.decodeQueue(raw))
        } catch (e: QueueFormatException) {
            QueueRead.Unreadable
        }
    }

    public val readable: Boolean
        get() = load() is QueueRead.Readable

    /**
     * A private comparison value (the stored blob as it is now), never logged. Every connection
     * queue observes external acknowledged edits.
     */
    public fun version(): PrefValue? = store.get(KEY)

    public fun profilesVersion(): PrefValue? = store.get("oc.profiles")

    public val updates: Flow<Set<String>> = store.changes.filter { KEY in it }

    public fun encodedVersion(entries: List<QueuedPrompt>): PrefValue? =
        if (entries.isEmpty()) null else PrefValue.Str(QueuedPromptCodec.encodeQueue(entries))

    /**
     * A stale connection cache may not reintroduce entries removed by Settings or another
     * connection.
     */
    public suspend fun saveIfUnchanged(
        entries: List<QueuedPrompt>,
        expected: PrefValue?,
        expectedProfiles: PrefValue?,
    ): Boolean {
        if (!readable) return false
        val next = encodedVersion(entries)
        val written =
            store
                .compareAndCommit(lane, mapOf(KEY to expected, "oc.profiles" to expectedProfiles)) {
                    if (next == null) remove(KEY) else putString(KEY, (next as PrefValue.Str).value)
                }
                .let { it is Outcome.Ok && it.value }
        if (written) loadedStamp = next
        return written
    }

    /**
     * Delete only unsent work; dispatched and journal-bearing entries remain reviewable. Unknown
     * storage refuses.
     */
    public suspend fun discardUnsent(
        expectedJournals: Map<String, PrefValue?>,
        protectedIds: Set<String>,
    ): Outcome<Int> = order.withLock {
        val before = version()
        val read =
            load() as? QueueRead.Readable
                ?: return@withLock Outcome.Failed(
                    io.github.eslamasabry.oc.core.common.Failure.StorageRefused()
                )
        val kept = read.entries.filter { it.dispatched || it.id in protectedIds }
        val removed = read.entries.size - kept.size
        val next = encodedVersion(kept)
        when (
            val result =
                store.compareAndCommit(lane, expectedJournals + (KEY to before)) {
                    if (next == null) remove(KEY) else putString(KEY, (next as PrefValue.Str).value)
                }
        ) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                if (result.value) Outcome.Ok(removed).also { loadedStamp = next }
                else Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.StorageRefused())
        }
    }

    /**
     * Characters the stored blob holds, for the settings readout (Dart `storedBytes`); 0 when
     * absent or not a string.
     */
    public fun storedBytes(): Long =
        ((store.get(KEY) as? PrefValue.Str)?.value?.length ?: 0).toLong()

    /** True only when storage acknowledged the write. */
    public suspend fun save(entries: List<QueuedPrompt>): Boolean {
        val snapshot = entries.toList()
        val encoded = if (snapshot.isEmpty()) null else QueuedPromptCodec.encodeQueue(snapshot)
        return order.withLock {
            if (!loaded) load()
            val expected = loadedStamp
            if (
                encoded != null &&
                    expected != null &&
                    (expected !is PrefValue.Str ||
                        try {
                            QueuedPromptCodec.decodeQueue(expected.value)
                            false
                        } catch (_: QueueFormatException) {
                            true
                        })
            )
                return@withLock false
            val written =
                store.compareAndCommit(lane, mapOf(KEY to expected)) {
                    if (encoded == null) remove(KEY) else putString(KEY, encoded)
                }
            if (written !is Outcome.Ok || !written.value) return@withLock false
            loadedStamp = encoded?.let(PrefValue::Str)
            true
        }
    }

    /**
     * FIFO (fair mutex), like Dart's chained `_writeTail`: the readability check and the commit are
     * one step.
     */
    private val order = Mutex()

    public companion object {
        /**
         * Logical key (file key `flutter.oc.offlineQueue`). App-wide: entries carry their own
         * profile id.
         */
        public const val KEY: String = "oc.offlineQueue"
    }
}
