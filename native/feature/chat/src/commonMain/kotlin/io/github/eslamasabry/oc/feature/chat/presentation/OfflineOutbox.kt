package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueueLimits
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRefusal
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import kotlin.time.Clock

/**
 * B-500: durable offline outbox over the adopted `oc.offlineQueue` store.
 *
 * Holds unsent prompts when the connection is down (including when no live connection graph exists
 * yet after an app restart) and coordinates cleanly with [OfflineQueue] so each waiting message
 * sends once on reconnect and never duplicates.
 */
internal class OfflineOutbox(
    storage: DeliveryStorage,
    private val clock: Clock? = null,
) {
    private val queueStore = OfflineQueueStore(storage.store, storage.lanes.app)
    private var seq = 0L

    /** Unsent waiting entries for [ref], oldest first. Readable even while completely offline. */
    fun readWaiting(ref: ConversationRef): List<QueuedPrompt> {
        val read = queueStore.load() as? QueueRead.Readable ?: return emptyList()
        return read.entries.filter { it.ref == ref && !it.dispatched }
    }

    /** Persists a new waiting prompt either through the active [queue] or directly to storage. */
    suspend fun enqueue(
        ref: ConversationRef,
        text: String,
        attachments: List<PromptAttachment>,
        mentions: List<PromptAgentMention>,
        model: ModelSelection?,
        agent: String?,
        queue: OfflineQueue?,
    ): Outcome<QueuedPrompt> {
        val now = clock?.now()?.toEpochMilliseconds() ?: 0L
        val existingIds = existingIds(queue)
        val entry =
            QueuedPrompt(
                id = nextId(now, existingIds),
                profileId = ref.profileId,
                sessionId = ref.sessionId,
                text = text,
                attachments = attachments,
                mentions = mentions,
                modelProviderId = model?.model?.providerId,
                modelId = model?.model?.modelId,
                agent = agent,
                variant = model?.variant,
                createdAt = now,
                dispatchedAt = null,
            )
        if (queue != null) return enqueueWithQueue(queue, entry)
        return enqueueDirect(entry, now)
    }

    /**
     * Cancels a waiting entry by [entryId]. Returns `Ok(true)` when removed (or already absent),
     * `Ok(false)` if it is already dispatched or on the wire, and `Failed` if storage refused.
     */
    suspend fun cancel(entryId: String, queue: OfflineQueue?): Outcome<Boolean> {
        if (queue != null) return queue.remove(entryId)
        val read =
            queueStore.load() as? QueueRead.Readable
                ?: return Outcome.Failed(Failure.StorageRefused())
        val existing = read.entries.firstOrNull { it.id == entryId } ?: return Outcome.Ok(true)
        if (existing.dispatched) return Outcome.Ok(false)
        val kept = read.entries.filterNot { it.id == entryId }
        return if (queueStore.save(kept)) Outcome.Ok(true)
        else Outcome.Failed(Failure.StorageRefused())
    }

    private fun existingIds(queue: OfflineQueue?): Set<String> {
        val entries =
            queue?.entries()
                ?: (queueStore.load() as? QueueRead.Readable)?.entries
                ?: emptyList()
        return entries.mapTo(HashSet()) { it.id }
    }

    private fun nextId(now: Long, existingIds: Set<String>): String {
        var id = "outbox-$now-${++seq}"
        while (id in existingIds) id = "outbox-$now-${++seq}"
        return id
    }

    private suspend fun enqueueWithQueue(
        queue: OfflineQueue,
        entry: QueuedPrompt,
    ): Outcome<QueuedPrompt> =
        when (val result = queue.queuePrompt(entry)) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                when (result.value) {
                    null -> Outcome.Ok(entry)
                    QueueRefusal.Closed ->
                        Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
                    QueueRefusal.TooLarge,
                    QueueRefusal.NoRoom,
                    QueueRefusal.DuplicateId ->
                        Outcome.Failed(Failure.Refused(RefusalReason.OfflineQueueFull))
                }
        }

    private suspend fun enqueueDirect(entry: QueuedPrompt, now: Long): Outcome<QueuedPrompt> {
        if (entry.payloadBytes > QueueLimits.MAX_ENTRY_BYTES) {
            return Outcome.Failed(Failure.Refused(RefusalReason.OfflineQueueFull))
        }
        val read =
            queueStore.load() as? QueueRead.Readable
                ?: return Outcome.Failed(Failure.StorageRefused())
        val limited = QueueLimits.enforce(read.entries + entry, now)
        if (limited.kept.none { it.id == entry.id }) {
            return Outcome.Failed(Failure.Refused(RefusalReason.OfflineQueueFull))
        }
        return if (queueStore.save(limited.kept)) Outcome.Ok(entry)
        else Outcome.Failed(Failure.StorageRefused())
    }

    companion object {
        /** A deleted conversation cannot accept offline sends; a network failure can. */
        fun isOfflineSendAllowed(load: ChatLoad): Boolean =
            !(load is ChatLoad.Failed && load.failure is Failure.NotFound)
    }
}
