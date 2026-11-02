package io.github.eslamasabry.oc.feature.chat.header.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalPort
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModelFacts
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Production [ChatHeaderPorts]: each port reads the conversation's own connection graph (ADR §9.1). */
@Inject
@ContributesBinding(AppScope::class)
public class GraphChatHeaderPorts(private val registry: ConnectionRegistry) : ChatHeaderPorts {
    override fun open(ref: ConversationRef): ChatHeaderPort = GraphChatHeaderPort(ref, registry).also { it.attach() }
}

/**
 * One conversation's header acts on its connection. Every act captures the graph it starts on and publishes nothing
 * when the registry replaced it meanwhile (a stale graph's answer is about another connection).
 */
internal class GraphChatHeaderPort(private val ref: ConversationRef, private val registry: ConnectionRegistry) : ChatHeaderPort {
    private val sid = SessionId(ref.sessionId.raw)
    private val mutableFacts = MutableStateFlow(HeaderFacts())
    override val facts: StateFlow<HeaderFacts> = mutableFacts.asStateFlow()
    private var stopWatching: (() -> Unit)? = null
    private var bound: ConnectionGraph? = null
    private var stops: List<() -> Unit> = emptyList()
    private val waitingAsks = MutableStateFlow<List<PermissionRequest>>(emptyList())

    override val approvals: ApprovalPort =
        object : ApprovalPort {
            override val waiting: StateFlow<List<PermissionRequest>> = waitingAsks.asStateFlow()

            override suspend fun allowOnce(id: RequestId): Outcome<Unit> {
                val graph = bound ?: return offline()
                val engine = (graph as ChatGraphAccessors).chatRequests.engine
                return engine.answerNow(id, RequestAnswer.Permission(PermissionReply.Once))
            }
        }

    fun attach() {
        if (stopWatching != null) return
        val watcher =
            registry.appScope.launch(registry.mainContext) {
                registry.live.collect {
                    val graph = registry.graphFor(ref)
                    if (graph !== bound) bind(graph)
                }
            }
        stopWatching = { watcher.cancel() }
    }

    private fun bind(graph: ConnectionGraph?) {
        stops.forEach { it() }
        stops = emptyList()
        bound = graph
        waitingAsks.value = emptyList()
        if (graph == null) {
            mutableFacts.update { it.copy(connected = false) }
            return
        }
        val gateway = (graph as ChatGraphAccessors).chatGateway
        mutableFacts.value =
            HeaderFacts(serverName = graph.profile.name, directory = gateway.location.value.directory, connected = true)
        val jobs =
            listOf(
                graph.scope.launch(registry.mainContext) { read(graph, gateway) },
                graph.scope.launch(registry.mainContext) {
                    (graph as ChatGraphAccessors).chatRequests.engine.pending.collect { all ->
                        waitingAsks.value =
                            all.filterIsInstance<PendingRequest.Permission>()
                                .filter { it.phase == AnswerPhase.Waiting }
                                .map { it.request }
                    }
                },
                graph.scope.launch(registry.mainContext) {
                    gateway.events.all.collect { envelope ->
                        val event = envelope.event
                        if (event is GatewayEvent.SessionChanged && event.sessionId == sid && event.directory != null) {
                            mutableFacts.update { it.copy(directory = event.directory) }
                        }
                    }
                },
            )
        stops = jobs.map { job -> { job.cancel() } }
    }

    private suspend fun read(graph: ConnectionGraph, gateway: ServerGateway): Outcome<Unit> =
        when (val s = gateway.session(sid)) {
            is Outcome.Ok -> {
                if (bound === graph) mutableFacts.update { publish(it, s.value) }
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> s
        }

    private fun publish(f: HeaderFacts, s: Session): HeaderFacts =
        f.copy(
            directory = s.directory ?: f.directory,
            shareUrl = s.shareUrl,
            model = s.selection?.model?.modelId ?: s.model,
            providerId = s.selection?.model?.providerId,
            agent = s.selection?.agent ?: s.agent,
            contextTokens = s.contextUse?.tokens,
            contextLimit = s.contextUse?.limit,
            totalTokens = s.tokens?.total,
        )

    override suspend fun refresh(): Outcome<Unit> {
        val graph = bound ?: return offline()
        return read(graph, (graph as ChatGraphAccessors).chatGateway)
    }

    override suspend fun freeModelFacts(): Outcome<FreeModelFacts> {
        val graph = bound ?: return offline()
        val services = graph as ChatGraphAccessors
        val catalog = services.chatOperations.catalog()
        if (catalog is Outcome.Failed) return catalog
        val providers = services.chatGateway.configuredProviders()
        if (providers is Outcome.Failed) return providers
        if (bound !== graph) return offline()
        return Outcome.Ok(
            FreeModelFacts(
                signedIn = (providers as Outcome.Ok).value.providers.map { it.id },
                models = (catalog as Outcome.Ok).value.models,
            ),
        )
    }

    override suspend fun rename(title: String): Outcome<Unit> =
        withGateway { gateway, _ -> gateway.renameSession(sid, title) }

    override suspend fun share(): Outcome<String?> =
        withGateway { gateway, ops ->
            if (!gateway.capabilities.value.sessionShare) unavailable("sessionShare")
            else
                ops.shareSession(sid).also { r ->
                    if (r is Outcome.Ok) mutableFacts.update { it.copy(shareUrl = r.value) }
                }
        }

    override suspend fun unshare(): Outcome<Unit> =
        withGateway { gateway, ops ->
            if (!gateway.capabilities.value.sessionShare) unavailable("sessionShare")
            else
                ops.unshareSession(sid).also { r ->
                    if (r is Outcome.Ok) mutableFacts.update { it.copy(shareUrl = null) }
                }
        }

    override suspend fun fork(): Outcome<SessionId> =
        withGateway { gateway, ops ->
            if (!gateway.capabilities.value.sessionFork) unavailable("sessionFork") else ops.forkSession(sid, null)
        }

    override suspend fun archive(): Outcome<Unit> =
        withGateway { gateway, ops ->
            if (!gateway.capabilities.value.sessionArchive) unavailable("sessionArchive") else ops.archiveSession(sid)
        }

    override suspend fun delete(): Outcome<Unit> =
        withGateway { gateway, _ ->
            if (!gateway.capabilities.value.sessionDelete) unavailable("sessionDelete") else gateway.deleteSession(sid)
        }

    override fun close() {
        stopWatching?.invoke()
        stopWatching = null
        stops.forEach { it() }
        stops = emptyList()
        bound = null
    }

    /** Runs [act] on the current graph; a graph replaced while it ran publishes a refusal, never its answer. */
    private suspend fun <T> withGateway(act: suspend (ServerGateway, ServerOperationsGateway) -> Outcome<T>): Outcome<T> {
        val graph = bound ?: return offline()
        val services = graph as ChatGraphAccessors
        when (val ready = services.chatGateway.awaitActionTransport()) {
            is Outcome.Failed -> return ready
            is Outcome.Ok -> Unit
        }
        val result = act(services.chatGateway, services.chatOperations)
        if (bound !== graph) return offline()
        if (result is Outcome.Ok) read(graph, services.chatGateway)
        return result
    }

    private fun offline(): Outcome<Nothing> = Outcome.Failed(Failure.Network(NetworkWhy.Offline))

    private fun unavailable(name: String): Outcome<Nothing> = Outcome.Failed(Failure.Unavailable(FeatureId(name)))
}
