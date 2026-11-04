package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.engine.delivery.SendRoute
import io.github.eslamasabry.oc.engine.runtime.devservers.DetectedDevServers
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesHost
import io.github.eslamasabry.oc.feature.preview.presentation.NoPreviewTools
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewSent
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewTools
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Preview requests retain the conversation/model/agent and use its existing durable delivery lane.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoPreviewTools::class])
@Inject
class AndroidPreviewTools(
    private val registry: ConnectionRegistry,
    private val connections: ServerConnections,
    private val stores: PreviewStores,
    private val devServices: DevServicesHost,
    private val clock: Clock,
    dispatchers: AppDispatchers,
) : PreviewTools {
    private val serial = dispatchers.serial("preview-tools")
    private val sends = Mutex()

    private fun refused(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    private fun graph(ref: ConversationRef?): ConnectionGraph? {
        val profile =
            ref?.profileId
                ?: connections.servers.value.firstOrNull { it.connected }?.id
                ?: return null
        if (stores.profiles.profile(profile) == null) return null
        return registry.live.value[profile]
    }

    override fun canAsk(ref: ConversationRef): Boolean = graph(ref) is ChatGraphAccessors

    override fun canReadLogs(ref: ConversationRef?): Boolean =
        graph(ref)?.let {
            it.profile.managedOnPhone || it.session.capabilities.value.developmentServices
        } == true

    override fun delivery(ref: ConversationRef): PreviewSent =
        when ((graph(ref) as? ChatGraphAccessors)?.chatDelivery?.engine?.routeFor(ref)) {
            SendRoute.Steer,
            SendRoute.JoinTurn -> PreviewSent.JoinsTurn
            SendRoute.RestartTurn -> PreviewSent.RestartsTurn
            SendRoute.AfterReply -> PreviewSent.AfterReply
            else -> PreviewSent.Now
        }

    override suspend fun send(
        ref: ConversationRef,
        text: String,
        allowRestart: Boolean,
    ): Outcome<PreviewSent> =
        withContext(serial) {
            sends.withLock {
                val graph = graph(ref) ?: return@withLock refused()
                val gateway = graph.session.server
                when (val ready = gateway.awaitActionTransport()) {
                    is Outcome.Failed -> return@withLock ready
                    is Outcome.Ok -> Unit
                }
                val session =
                    when (val read = gateway.session(ref.sessionId)) {
                        is Outcome.Failed -> return@withLock read
                        is Outcome.Ok -> read.value
                    }
                if (graph(ref) !== graph) return@withLock refused()
                val delivery =
                    (graph as? ChatGraphAccessors)?.chatDelivery?.engine
                        ?: return@withLock refused()
                if (delivery.routeFor(ref) == SendRoute.RestartTurn && !allowRestart)
                    return@withLock refused()
                val selection = session.selection
                val model =
                    selection?.model?.let {
                        ModelSelection(it, selection.variant.takeIf { value -> value.isNotEmpty() })
                    }
                when (
                    val sent =
                        delivery.send(
                            SendRequest(
                                ref = ref,
                                text = text,
                                model = model,
                                agent = selection?.agent ?: session.agent,
                            )
                        )
                ) {
                    is Outcome.Failed -> sent
                    is Outcome.Ok ->
                        Outcome.Ok(
                            when (
                                delivery.pending.value
                                    .firstOrNull { it.localId == sent.value }
                                    ?.route
                            ) {
                                SendRoute.Steer,
                                SendRoute.JoinTurn -> PreviewSent.JoinsTurn
                                SendRoute.RestartTurn -> PreviewSent.RestartsTurn
                                SendRoute.AfterReply -> PreviewSent.AfterReply
                                else -> PreviewSent.Now
                            }
                        )
                }
            }
        }

    override suspend fun readLogs(ref: ConversationRef?, port: Int): Outcome<String> =
        withContext(serial) {
            val graph = graph(ref) ?: return@withContext refused()
            val directory =
                if (ref == null) graph.session.server.location.value.directory
                else
                    when (val read = graph.session.server.session(ref.sessionId)) {
                        is Outcome.Ok -> read.value.directory
                        is Outcome.Failed -> return@withContext read
                    }
            if (directory == null || graph(ref) !== graph) return@withContext refused()
            val context =
                devServices.open(graph.profile.id, directory) ?: return@withContext refused()
            val detector =
                DetectedDevServers(
                    directory,
                    context.shells,
                    { graph(ref) === graph && context.isCurrent() },
                    context.phone,
                    clock = clock,
                )
            try {
                detector.refresh()
                if (graph(ref) !== graph) return@withContext refused()
                val server =
                    detector.servers.firstOrNull { port in it.ports && it.hasLogs }
                        ?: return@withContext Outcome.Failed(
                            Failure.Unavailable(FeatureId("devServerLog"))
                        )
                detector.readLog(server)
            } finally {
                detector.invalidate()
            }
        }
}
