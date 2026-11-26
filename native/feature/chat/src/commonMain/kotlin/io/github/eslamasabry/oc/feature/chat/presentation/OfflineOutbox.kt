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

/** Immutable payload of one prompt held in the offline outbox. */
internal data class OutboxDraft(
    val ref: ConversationRef,
    val text: String,
    val attachments: List<PromptAttachment>,
    val mentions: List<PromptAgentMention>,
    val model: ModelSelection?,
    val agent: String?,
)

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
    suspend fun enqueue(draft: OutboxDraft, queue: OfflineQueue?): Outcome<QueuedPrompt> {
        val now = clock?.now()?.toEpochMilliseconds() ?: 0L
        val existingIds = existingIds(queue)
        val entry =
            QueuedPrompt(
                id = nextId(now, existingIds),
                profileId = draft.ref.profileId,
                sessionId = draft.ref.sessionId,
                text = draft.text,
                attachments = draft.attachments,
                mentions = draft.mentions,
                modelProviderId = draft.model?.model?.providerId,
                modelId = draft.model?.model?.modelId,
                agent = draft.agent,
                variant = draft.model?.variant,
                createdAt = now,
                dispatchedAt = null,
            )
        return if (queue != null) enqueueWithQueue(queue, entry) else enqueueDirect(entry, now)
    }

    /**
     * Cancels a waiting entry by [entryId]. Returns `Ok(true)` when removed (or already absent),
     * `Ok(false)` if it is already dispatched or on the wire, and `Failed` if storage refused.
     */
    suspend fun cancel(entryId: String, queue: OfflineQueue?): Outcome<Boolean> =
        if (queue != null) queue.remove(entryId) else cancelDirect(entryId)

    private suspend fun cancelDirect(entryId: String): Outcome<Boolean> {
        val read = queueStore.load() as? QueueRead.Readable
        val existing = read?.entries?.firstOrNull { it.id == entryId }
        return when {
            read == null -> Outcome.Failed(Failure.StorageRefused())
            existing == null -> Outcome.Ok(true)
            existing.dispatched -> Outcome.Ok(false)
            queueStore.save(read.entries.filterNot { it.id == entryId }) -> Outcome.Ok(true)
            else -> Outcome.Failed(Failure.StorageRefused())
        }
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
        val read = queueStore.load() as? QueueRead.Readable
        val limited = read?.let { QueueLimits.enforce(it.entries + entry, now) }
        return when {
            entry.payloadBytes > QueueLimits.MAX_ENTRY_BYTES ->
                Outcome.Failed(Failure.Refused(RefusalReason.OfflineQueueFull))
            read == null || limited == null ->
                Outcome.Failed(Failure.StorageRefused())
            limited.kept.none { it.id == entry.id } ->
                Outcome.Failed(Failure.Refused(RefusalReason.OfflineQueueFull))
            queueStore.save(limited.kept) -> Outcome.Ok(entry)
            else -> Outcome.Failed(Failure.StorageRefused())
        }
    }

    companion object {
        /** A deleted conversation cannot accept offline sends; a network failure can. */
        fun isOfflineSendAllowed(load: ChatLoad): Boolean =
            !(load is ChatLoad.Failed && load.failure is Failure.NotFound)
    }
}
