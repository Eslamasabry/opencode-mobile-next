package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.DeliveryAccessors
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueue
import io.github.eslamasabry.oc.engine.delivery.queue.QueueMoveResult
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.servers.presentation.QueueMoveBlock as PageBlock
import io.github.eslamasabry.oc.feature.servers.presentation.QueueMoveConversation
import io.github.eslamasabry.oc.feature.servers.presentation.QueueMoveItem
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueueMovePort
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueueMoved
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueuePlan
import io.github.eslamasabry.oc.engine.delivery.queue.QueueMoveBlock as EngineBlock
import kotlinx.collections.immutable.toImmutableList

/**
 * Moving a whole server's waiting prompts to the connected server (Flutter `moveQueuedPrompts`). The rules and
 * the single queue write live in the offline queue ([OfflineQueue.moveMany], [OfflineQueue.undoMove]); this port
 * only reads the connected server's conversations, model and agent lists, opens a new conversation when asked,
 * and holds the Undo token. Nothing is sent: the destination's own flush sends the prompts.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerQueueMovePort>())
@Inject
class NativeServerQueueMoves(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
) : ServerQueueMovePort {
    private val unavailable = Failure.Unavailable(FeatureId("offlinePromptQueue"))

    private class Held(
        val source: ProfileId,
        val destination: ProfileId,
        val sourceName: String,
        val destinationName: String,
        val conversations: Set<SessionId>,
        var done: QueueMoveResult? = null,
    )

    private val plans = LinkedHashMap<String, Held>()

    /** The connected, active server that keeps a queue; null when there is none (Flutter `queuedPromptMoveDestination`). */
    private fun destination(): Triple<ProfileId, OfflineQueue, ServerGateway>? {
        val active = stores.profiles.activeId.value ?: return null
        val graph = registry.live.value[active] ?: return null
        if (graph.session.capabilities.value.offlinePromptQueue != true) return null
        val queue = (graph as? DeliveryAccessors)?.connectionDelivery?.queue ?: return null
        val gateway = (graph as? ChatGraphAccessors)?.chatGateway ?: return null
        return Triple(active, queue, gateway)
    }

    private fun nameOf(id: ProfileId): String =
        redactor.redact(stores.profiles.profile(id)?.profile?.name.orEmpty())

    override suspend fun prepare(source: ProfileId): Outcome<ServerQueuePlan> {
        val (dest, queue, gateway) = destination() ?: return unavailable.failed()
        if (dest == source) return Failure.Refused(RefusalReason.StaleScope).failed()
        val waiting = queue.entries().filter { it.profileId == source }
        val sessions =
            when (val read = readSessionPages(gateway)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value.sessions.filter { it.parentId == null }
            }
        val token = java.util.UUID.randomUUID().toString()
        synchronized(plans) {
            while (plans.size >= 8) plans.remove(plans.keys.first())
            plans[token] =
                Held(source, dest, nameOf(source), nameOf(dest), sessions.map { it.id }.toSet())
        }
        return ServerQueuePlan(
                token = token,
                sourceName = nameOf(source),
                destinationName = nameOf(dest),
                items =
                    waiting
                        .map { entry ->
                            QueueMoveItem(
                                entry.id,
                                redactor.redact(entry.text).take(PREVIEW),
                                when (queue.moveBlock(entry, redactor)) {
                                    EngineBlock.Uncertain -> PageBlock.Uncertain
                                    EngineBlock.ServerFile -> PageBlock.ServerFile
                                    EngineBlock.SecretMentions -> PageBlock.SecretMentions
                                    EngineBlock.Sending -> PageBlock.Sending
                                    null -> null
                                },
                            )
                        }
                        .toImmutableList(),
                conversations =
                    sessions.take(CONVERSATIONS).map { QueueMoveConversation(it.id, it.title) }.toImmutableList(),
            )
            .ok()
    }

    override suspend fun move(
        token: String,
        ids: Set<String>,
        conversation: SessionId?,
    ): Outcome<ServerQueueMoved> {
        val held = synchronized(plans) { plans[token] } ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (held.done != null) return Failure.Refused(RefusalReason.StaleScope).failed()
        val (dest, queue, gateway) = destination() ?: return unavailable.failed()
        // The connected server changed since the page opened: nothing moves on a guess.
        if (dest != held.destination) return Failure.Refused(RefusalReason.StaleScope).failed()
        val session =
            if (conversation != null) {
                if (conversation !in held.conversations) return Failure.Refused(RefusalReason.StaleScope).failed()
                conversation
            } else
                when (val created = gateway.createSession()) {
                    is Outcome.Failed -> return created
                    is Outcome.Ok -> created.value.id
                }
        // An unknown catalog or agent list drops nothing.
        val models: Set<ModelRef>? =
            (gateway.configuredProviders() as? Outcome.Ok)?.value?.let { response ->
                response.providers.flatMap { p -> p.modelIds.map { ModelRef(p.id, it) } }.toSet()
            }
        val agents: Set<String>? =
            (gateway.agents() as? Outcome.Ok)?.value?.map { it.name }?.toSet()
        val result =
            when (
                val moved =
                    queue.moveMany(
                        held.source,
                        ids,
                        ConversationRef(dest, session),
                        newConversation = conversation == null,
                        models = models,
                        agents = agents,
                        redactor = redactor,
                    )
            ) {
                is Outcome.Failed -> return moved
                is Outcome.Ok -> moved.value ?: return Failure.Refused(RefusalReason.StaleScope).failed()
            }
        held.done = result
        return ServerQueueMoved(
                count = result.moved,
                skipped = result.skipped,
                reset = result.selectionsReset,
                conversation = ConversationRef(dest, session),
            )
            .ok()
    }

    override suspend fun undo(token: String): Outcome<Int> {
        val held = synchronized(plans) { plans[token] } ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val done = held.done ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val (dest, queue, _) = destination() ?: return unavailable.failed()
        if (dest != held.destination) return Failure.Refused(RefusalReason.StaleScope).failed()
        return queue.undoMove(done)
    }

    override fun forget(token: String) {
        // The Undo token outlives the page's plan only while the page is open; leaving it ends Undo.
        synchronized(plans) { plans.remove(token) }
    }

    private companion object {
        const val PREVIEW = 120
        const val CONVERSATIONS = 40
    }
}
