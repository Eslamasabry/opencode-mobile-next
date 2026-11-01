package io.github.eslamasabry.oc.engine.delivery.queue

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The result of reading `oc.offlineQueue`. */
public sealed interface QueueRead {
    public data class Readable(val entries: List<QueuedPrompt>) : QueueRead

    /** Corrupt JSON, a wrongly typed preference or a bad entry: nothing is sent or overwritten until cleared (B-STATE-034). */
    public data object Unreadable : QueueRead
}

/**
 * `oc.offlineQueue` over the adopted Flutter preferences (plan 04 §13.2: switch release; Room comes at switch+1 behind
 * the same calls). App-wide, written through the app [lane] with `commit()`. Port of `OfflineQueueStore`.
 *
 * - Reads never tidy: limits are the engine's job ([QueueLimits]), on its first read.
 * - Writes are serialized (FIFO lane) and take a snapshot of the list at call time, so an older save can never
 *   resurrect a prompt removed by a later one (B-STATE-035).
 * - A non-empty save over an unreadable blob is refused; only the explicit empty save (the person's "clear") replaces it
 *   (B-STATE-034).
 */
public class OfflineQueueStore(private val store: KeyValueStore, private val lane: WriterLane) {
    public fun load(): QueueRead {
        val value = store.get(KEY) ?: return QueueRead.Readable(emptyList())
        val raw = (value as? PrefValue.Str)?.value ?: return QueueRead.Unreadable
        return try {
            QueueRead.Readable(QueuedPromptCodec.decodeQueue(raw))
        } catch (e: QueueFormatException) {
            QueueRead.Unreadable
        }
    }

    public val readable: Boolean get() = load() is QueueRead.Readable

    /** Characters the stored blob holds, for the settings readout (Dart `storedBytes`); 0 when absent or not a string. */
    public fun storedBytes(): Long = ((store.get(KEY) as? PrefValue.Str)?.value?.length ?: 0).toLong()

    /** True only when storage acknowledged the write. */
    public suspend fun save(entries: List<QueuedPrompt>): Boolean {
        val snapshot = entries.toList()
        val encoded = if (snapshot.isEmpty()) null else QueuedPromptCodec.encodeQueue(snapshot)
        return order.withLock {
            if (encoded != null && !readable) return@withLock false
            store.commit(lane) { if (encoded == null) remove(KEY) else putString(KEY, encoded) } is Outcome.Ok
        }
    }

    /** FIFO (fair mutex), like Dart's chained `_writeTail`: the readability check and the commit are one step. */
    private val order = Mutex()

    public companion object {
        /** Logical key (file key `flutter.oc.offlineQueue`). App-wide: entries carry their own profile id. */
        public const val KEY: String = "oc.offlineQueue"
    }
}
