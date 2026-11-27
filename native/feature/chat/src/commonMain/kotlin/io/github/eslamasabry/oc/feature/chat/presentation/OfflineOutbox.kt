package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueueEviction
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRefusal
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicy
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicyOwner
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
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

/** Raw read result for a profile's `oc.automation.<id>` preference. */
internal sealed interface AutomationPrefRead {
    data object Absent : AutomationPrefRead

    data class Text(val raw: String) : AutomationPrefRead

    data object Invalid : AutomationPrefRead
}

/**
 * B-500: durable offline outbox routed exclusively through `:engine:delivery`'s [OfflineQueue].
 *
 * Never writes directly to `oc.offlineQueue`: every enqueue and cancel runs through a real
 * [OfflineQueue] instance (preferring the active connection's queue, then any live connection's
 * queue, then the app-scoped detached [OfflineQueue] over [DeliveryStorage.journals] with
 * compare-and-commit writes on [DeliveryStorage.lanes.app]). Callers read [takeDetachedEviction]
 * so evictions decided while no connection is live still reach the chat once.
 */
internal class OfflineOutbox(
    storage: DeliveryStorage,
    private val registry: ConnectionRegistry,
    private val detachedQueue: OfflineQueue?,
    private val readAutomationPref: (String) -> AutomationPrefRead,
    private val clock: Clock? = null,
) {
    private val reader = OfflineQueueStore(storage.store, storage.lanes.app)
    private var seq = 0L

    /** Read-only snapshot of unsent waiting entries for [ref], oldest first. Never mutates disk. */
    fun readWaiting(ref: ConversationRef): List<QueuedPrompt> {
        val read = reader.load() as? QueueRead.Readable ?: return emptyList()
        return read.entries.filter { it.ref == ref && !it.dispatched }
    }

    /**
     * Honors the user's `reconcileQueuedSends` automation consent for [profileId]. Absent uses the
     * default (`true`); corrupt or wrongly typed storage grants nothing (`false`).
     */
    fun allowsAutomaticQueuedSends(profileId: ProfileId): Boolean =
        when (val raw = readAutomationPref(AutomationPolicyOwner.keyFor(profileId.raw))) {
            AutomationPrefRead.Absent -> true
            is AutomationPrefRead.Text ->
                AutomationPolicy.fromJson(raw.raw).allows(AutomationBehavior.ReconcileQueuedSends)
            AutomationPrefRead.Invalid -> false
        }

    /** Persists a new waiting prompt through [OfflineQueue.queuePrompt]. */
    suspend fun enqueue(draft: OutboxDraft, queue: OfflineQueue?): Outcome<QueuedPrompt> {
        val target = queueFor(queue) ?: return Outcome.Failed(Failure.StorageRefused())
        val now = clock?.now()?.toEpochMilliseconds() ?: 0L
        val existingIds = target.entries().mapTo(HashSet()) { it.id }
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
        return enqueueWithQueue(target, entry)
    }

    /**
     * Removes a waiting entry by [entryId] through [OfflineQueue.remove]. Returns `Ok(true)` when
     * removed (or already absent), `Ok(false)` when on the wire, and `Failed` if storage refused.
     */
    suspend fun cancel(entryId: String, queue: OfflineQueue?): Outcome<Boolean> {
        val target = queueFor(queue) ?: return Outcome.Failed(Failure.StorageRefused())
        return target.remove(entryId)
    }

    /** Reads and clears any eviction recorded by the detached no-connection queue (shown once). */
    fun takeDetachedEviction(): QueueEviction? = detachedQueue?.takeEviction()

    private fun queueFor(preferred: OfflineQueue?): OfflineQueue? =
        preferred
            ?: registry.live.value.values.firstNotNullOfOrNull {
                (it as? ChatGraphAccessors)?.chatDelivery?.queue
            }
            ?: detachedQueue

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

    companion object {
        /** A deleted conversation cannot accept offline sends; a network failure can. */
        fun isOfflineSendAllowed(load: ChatLoad): Boolean =
            !(load is ChatLoad.Failed && load.failure is Failure.NotFound)
    }
}
