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
import io.github.eslamasabry.oc.engine.requests.consent.PermissionInvitationAuthority
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val invitations: PermissionInvitationAuthority? = null,
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
                    engine.answerNow(request.id, RequestAnswer.Permission(PermissionReply.Once))
                },
                clock = clock,
            )
        return combine(
                engine.pending,
                store.revision,
                gateway.capabilities,
                invitations?.revision ?: flowOf(0L),
            ) { all, _, _, _ ->
                all
            }
            .onEach { all ->
                if (!gateway.capabilities.value.permissionRequests) return@onEach
                val asks =
                    all.filterIsInstance<PendingRequest.Permission>()
                        .filter { it.phase == AnswerPhase.Waiting }
                        .map { it.request }
                val unresolved = HashSet<SessionId>()
                for (ask in asks) {
                    // A child inherits its parent's mode: its parents are read before the mode is
                    // decided.
                    var current: SessionId? = ask.sessionId
                    var depth = 0
                    val seen = HashSet<SessionId>()
                    while (current != null && depth < 8 && seen.add(current)) {
                        if (current !in parents) {
                            val read = gateway.session(current)
                            if (read !is Outcome.Ok) {
                                unresolved += ask.sessionId
                                break
                            }
                            parents[current] = read.value.parentId
                        }
                        current = parents[current]
                        depth++
                    }
                    if (current != null && depth >= 8) unresolved += ask.sessionId
                }
                engine.observeInvitations(invitations?.forProfile(profile)) { session ->
                    // If inheritance could not be read, omit the invitation rather than guess a
                    // mode.
                    session in unresolved ||
                        session !in parents ||
                        ApprovalModes.effective(store.settings(profile), session) { parents[it] }
                            .automatic
                }
                sweep.sweep(asks)
            }
            .map {}
    }
}
