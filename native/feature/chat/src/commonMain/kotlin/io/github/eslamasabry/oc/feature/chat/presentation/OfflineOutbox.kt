package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.DeliveryStorage
import io.github.eslamasabry.oc.engine.delivery.port.AutomationPolicy as DeliveryAutomationPolicy
import io.github.eslamasabry.oc.engine.delivery.port.DeliveryConnection
import io.github.eslamasabry.oc.engine.delivery.port.SendLink
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRefusal
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicy as ConsentAutomationPolicy
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicyOwner
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
 * B-500: durable offline outbox routed exclusively through `:engine:delivery`'s [OfflineQueue].
 *
 * Never writes directly to `oc.offlineQueue`: every enqueue and cancel runs through a real
 * [OfflineQueue] instance (preferring the active connection's queue, then any live connection's
 * queue, then a single app-scoped detached [OfflineQueue] over the same [DeliveryStorage.journals]),
 * preserving the engine queue lock, closed-profile checks, tombstone checks, and B-STATE-046
 * eviction notices.
 */
internal class OfflineOutbox(
    private val storage: DeliveryStorage,
    private val registry: ConnectionRegistry,
    private val clock: Clock? = null,
    dispatchers: AppDispatchers? = null,
) {
    private val reader = OfflineQueueStore(storage.store, storage.lanes.app)
    private val effectiveDispatchers: AppDispatchers? =
        dispatchers
            ?: (registry.mainContext[ContinuationInterceptor] as? CoroutineDispatcher)
                ?.let(::ContextDispatchers)
    private val detachedQueue: OfflineQueue? =
        effectiveDispatchers?.let { d ->
            OfflineQueue(
                store = OfflineQueueStore(storage.store, storage.lanes.app),
                journals = storage.journals,
                connection = DetachedConnection,
                policy = DeliveryAutomationPolicy { false },
                clock = clock ?: ZeroClock,
                scope = registry.appScope,
                dispatchers = d,
            )
        }
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
        when (val raw = storage.store.get(AutomationPolicyOwner.keyFor(profileId.raw))) {
            null -> true
            is PrefValue.Str ->
                ConsentAutomationPolicy.fromJson(raw.value)
                    .allows(AutomationBehavior.ReconcileQueuedSends)
            else -> false
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

    private object DetachedConnection : DeliveryConnection {
        override val link: StateFlow<SendLink?> = MutableStateFlow(null)
        override val activeProfile: StateFlow<ProfileId?> = MutableStateFlow(null)
    }

    private object ZeroClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0L)
    }

    private class ContextDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher

        override fun serial(name: String): CoroutineDispatcher = dispatcher
    }

    companion object {
        /** A deleted conversation cannot accept offline sends; a network failure can. */
        fun isOfflineSendAllowed(load: ChatLoad): Boolean =
            !(load is ChatLoad.Failed && load.failure is Failure.NotFound)
    }
}
