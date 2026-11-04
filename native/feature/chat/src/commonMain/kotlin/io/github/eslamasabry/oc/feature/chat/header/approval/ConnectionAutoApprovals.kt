package io.github.eslamasabry.oc.feature.chat.header.approval

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalModes
import io.github.eslamasabry.oc.engine.requests.consent.AutoApprovalSweep
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Codex chat-header #4 (B-AGENT-225): a saved "Auto" or "Approve everything" keeps working while
 * the server stays connected, also when no chat is open (Flutter answers at connection scope). One
 * sweep per live connection, over every waiting permission ask and every change of the saved modes.
 * It answers through the same idempotent write as the chip's own sweep (`answerNow`), so both
 * running at once answer an ask once.
 */
public class ConnectionAutoApprovals(
    private val registry: ConnectionRegistry,
    private val store: ApprovalStore,
    private val clock: Clock,
) {
    private var started = false

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    public fun start() {
        if (started) return
        started = true
        registry.appScope.launch(registry.mainContext) {
            registry.live
                .flatMapLatest { graphs -> merge(*graphs.values.map(::sweep).toTypedArray()) }
                .collect()
        }
    }

    private fun sweep(graph: ConnectionGraph): Flow<Unit> {
        val services = graph as? ChatGraphAccessors ?: return emptyFlow()
        val engine = services.chatRequests.engine
        val gateway = services.chatGateway
        val profile = graph.profile.id
        val parents = HashMap<SessionId, SessionId?>()
        val sweep =
            AutoApprovalSweep(
                automatic = { session ->
                    ApprovalModes.effective(store.settings(profile), session) { parents[it] }
                        .automatic
                },
                reply = { request ->
                    engine.answerNow(
                        request.id,
                        RequestAnswer.Permission(PermissionReply.Once),
                        PendingRequest.Permission(request),
                    )
                },
                clock = clock,
            )
        return combine(engine.pending, store.revision) { all, _ -> all }
            .onEach { all ->
                if (!gateway.capabilities.value.permissionRequests) return@onEach
                val asks =
                    all.filterIsInstance<PendingRequest.Permission>()
                        .filter { it.phase == AnswerPhase.Waiting }
                        .map { it.request }
                for (ask in asks) {
                    // A child inherits its parent's mode: its parents are read before the mode is
                    // decided.
                    var current: SessionId? = ask.sessionId
                    var depth = 0
                    while (current != null && current !in parents && depth < 8) {
                        val read = gateway.session(current)
                        if (read !is Outcome.Ok) break
                        parents[current] = read.value.parentId
                        current = read.value.parentId
                        depth++
                    }
                }
                sweep.sweep(asks)
            }
            .map {}
    }
}
