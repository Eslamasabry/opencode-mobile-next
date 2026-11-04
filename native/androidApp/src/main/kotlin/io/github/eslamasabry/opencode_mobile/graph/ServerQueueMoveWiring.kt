package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.servers.presentation.*
import java.util.UUID
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The sheet moves persisted waiting prompts only. It never flushes, sends, steers or changes a
 * dispatch marker.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerQueueMovePort>())
@Inject
class NativeServerQueueMoves(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val redactor: Redactor,
) : ServerQueueMovePort {
    private class Held(
        val source: ProfileId,
        val destination: ProfileId,
        val graph: ConnectionGraph,
        val location: ServerLocation,
        val queue: OfflineQueue,
        val expected: List<QueuedPrompt>,
        val models: Set<Pair<String, String>>?,
        val agents: Set<String>?,
    ) {
        var result: QueueMoveResult? = null
        val mutation = Mutex()

        override fun toString(): String = "QueueMovePlan(<private>)"
    }

    private val plans = LinkedHashMap<String, Held>()

    private fun valid(held: Held): Boolean =
        stores.profiles.profile(held.source) != null &&
            stores.profiles.activeId.value == held.destination &&
            registry.live.value[held.destination] === held.graph &&
            stores.profiles.profile(held.destination)?.toServerProfile() == held.graph.profile &&
            held.graph.session.server.location.value == held.location &&
            held.graph.session.capabilities.value.offlinePromptQueue

    override suspend fun prepare(source: ProfileId): Outcome<ServerQueuePlan> {
        when (val ready = stores.warm()) {
            is Outcome.Failed -> return ready
            is Outcome.Ok -> Unit
        }
        val sourceProfile = stores.profiles.profile(source) ?: return stale()
        val destination =
            stores.profiles.activeId.value?.takeIf { it != source } ?: return unavailable()
        val target = stores.profiles.profile(destination) ?: return stale()
        val graph = registry.live.value[destination] ?: return unavailable()
        if (
            !graph.session.capabilities.value.offlinePromptQueue ||
                target.toServerProfile() != graph.profile
        )
            return unavailable()
        val queue = (graph as? ChatGraphAccessors)?.chatDelivery?.queue ?: return unavailable()
        val expected = queue.entries().toList()
        if (!queue.state.value.readable) return Failure.StorageRefused().failed()
        val location = graph.session.server.location.value
        val sessions = graph.session.server.sessionPage(limit = 100)
        if (sessions is Outcome.Failed) return sessions
        val providers = (graph.session.server.providers() as? Outcome.Ok)?.value
        val knownModels =
            providers?.providers?.flatMap { p -> p.modelIds.map { p.id to it } }?.toSet()
        val agents =
            (graph.session.server.agents() as? Outcome.Ok)
                ?.value
                ?.map { it.name }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
        val held = Held(source, destination, graph, location, queue, expected, knownModels, agents)
        if (!valid(held)) return stale()
        val token = UUID.randomUUID().toString()
        while (plans.size >= 8) plans.remove(plans.keys.first())
        plans[token] = held
        val items =
            expected
                .filter { it.profileId == source }
                .sortedBy { it.createdAt }
                .map { entry ->
                    val masked = redactor.redact(entry.text)
                    val blocked =
                        when {
                            entry.dispatched || queue.acceptedUnrecorded(entry.id) ->
                                QueueMoveBlock.Uncertain
                            queue.sending(entry.id) -> QueueMoveBlock.Sending
                            entry.attachments.any {
                                it.url.substringBefore(':', "").lowercase() !in
                                    setOf("data", "http", "https")
                            } -> QueueMoveBlock.ServerFile
                            entry.mentions.isNotEmpty() && masked != entry.text ->
                                QueueMoveBlock.SecretMentions
                            else -> null
                        }
                    QueueMoveItem(entry.id, masked.take(280), blocked)
                }
                .toImmutableList()
        return ServerQueuePlan(
                token,
                redactor.redact(sourceProfile.profile.name),
                redactor.redact(target.profile.name),
                items,
                (sessions as Outcome.Ok)
                    .value
                    .items
                    .map { QueueMoveConversation(it.id, it.title?.let(redactor::redact)) }
                    .toImmutableList(),
            )
            .ok()
    }

    override suspend fun move(
        token: String,
        ids: Set<String>,
        conversation: SessionId?,
    ): Outcome<ServerQueueMoved> {
        val held = plans[token] ?: return stale()
        return held.mutation.withLock {
            if (!valid(held) || held.result != null) return@withLock stale()
            val id =
                if (conversation != null) {
                    val target = held.graph.session.server.session(conversation)
                    if (target is Outcome.Failed) return@withLock target
                    conversation
                } else
                    when (val created = held.graph.session.server.createSession(NewSession())) {
                        is Outcome.Failed -> return@withLock created
                        is Outcome.Ok -> created.value.id
                    }
            if (!valid(held)) return@withLock stale()
            when (
                val result =
                    held.queue.move(
                        held.source,
                        ConversationRef(held.destination, id),
                        ids,
                        held.expected,
                        { valid(held) },
                        { entry ->
                            (entry.model == null ||
                                held.models == null ||
                                (entry.model!!.providerId to entry.model!!.modelId) in
                                    held.models) &&
                                (entry.agent.isNullOrEmpty() ||
                                    held.agents == null ||
                                    entry.agent in held.agents)
                        },
                        redactor::redact,
                    )
            ) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    held.result = result.value
                    ServerQueueMoved(
                            result.value.originals.size,
                            result.value.skipped,
                            result.value.selectionsReset,
                            result.value.destination,
                        )
                        .ok()
                }
            }
        }
    }

    override suspend fun undo(token: String): Outcome<Int> {
        val held = plans[token] ?: return stale()
        return held.mutation.withLock {
            val result = held.result ?: return@withLock stale()
            held.queue.undoMove(result, { valid(held) })
        }
    }

    override fun forget(token: String) {
        plans.remove(token)
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun unavailable(): Outcome<Nothing> =
        Failure.Unavailable(FeatureId("queuedPromptMoveDestination")).failed()
}
