package io.github.eslamasabry.oc.feature.chat.context

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.ApiRouteGateway
import io.github.eslamasabry.oc.engine.agents.context.ActiveContextGateway
import io.github.eslamasabry.oc.engine.agents.context.ActiveContextMessage
import io.github.eslamasabry.oc.engine.agents.sessionimport.GatewayRouteTransport
import io.github.eslamasabry.oc.engine.agents.sessionimport.Oc2Routes
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the active-context inspector shows (B-CHAT-001..003; Flutter `ActiveContextScreen`). */
public sealed interface ActiveContextUi {
    public data object Loading : ActiveContextUi

    /** The server has no such route: the inspector says so and stays closed to retries (the gateway latches it). */
    public data object Unsupported : ActiveContextUi

    /** The server, project or conversation changed since the inspector opened: reopen it from the conversation. */
    public data object Changed : ActiveContextUi

    /** The snapshot was malformed or repeated a message id. Refresh tries again. */
    public data object Invalid : ActiveContextUi

    /** The snapshot could not be read (session gone, password refused, network): recoverable. */
    public data object Failed : ActiveContextUi

    /** Every message the server returned. [refreshFailed] = the previous snapshot is still shown after a failed refresh. */
    public class Ready(public val messages: ImmutableList<ActiveContextMessage>, public val refreshFailed: Boolean = false) :
        ActiveContextUi
}

/**
 * The inspector of one open conversation. It reads `GET /api/session/{id}/context` through the conversation's own
 * connection and keeps the graph it opened on: if the conversation moves to another connection the view says it is
 * outdated rather than showing another server's context.
 */
public class ActiveContextModel(private val ref: ConversationRef, private val registry: ConnectionRegistry) {
    private val mutableState = MutableStateFlow<ActiveContextUi>(ActiveContextUi.Loading)
    public val state: StateFlow<ActiveContextUi> = mutableState.asStateFlow()
    private val mutableVisible = MutableStateFlow(false)
    public val visible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    private var bound: ConnectionGraph? = null
    private var gateway: ActiveContextGateway? = null
    private var job: Job? = null

    /**
     * False where the connected server has no route facet at all, or where this connection already answered that the
     * route is missing (Flutter hides the entry once `activeContextSupported` latches off).
     */
    public fun available(): Boolean {
        val graph = registry.graphFor(ref) ?: return false
        if (api(graph) == null) return false
        return graph !== bound || gateway?.supported != false
    }

    public fun open() {
        val graph = registry.graphFor(ref)
        mutableVisible.value = true
        // One gateway per connection, so a missing route stays latched across reopenings of the same connection.
        if (graph !== bound || gateway == null) {
            bound = graph
            gateway = graph?.let(::api)?.let { ActiveContextGateway(Oc2Routes(GatewayRouteTransport(it))) }
        }
        val source = gateway
        mutableState.value =
            when {
                source == null || !source.supported -> ActiveContextUi.Unsupported
                else -> ActiveContextUi.Loading
            }
        if (source != null && source.supported) load()
    }

    /** Hides the sheet and stops a read in flight; the connection's gateway (and its latch) is kept. */
    public fun close() {
        job?.cancel()
        mutableVisible.value = false
    }

    public fun refresh() {
        val source = gateway ?: return
        if (registry.graphFor(ref) !== bound) {
            mutableState.value = ActiveContextUi.Changed
            return
        }
        if (!source.supported) return
        load()
    }

    private fun load() {
        val source = gateway ?: return
        val graph = bound
        job?.cancel()
        job =
            registry.appScope.launch(registry.mainContext) {
                val previous = (mutableState.value as? ActiveContextUi.Ready)?.messages
                if (previous == null) mutableState.value = ActiveContextUi.Loading
                val answer = source.load(ref.sessionId.raw)
                // A result for a connection the conversation has left is never shown.
                if (registry.graphFor(ref) !== graph) {
                    mutableState.value = ActiveContextUi.Changed
                    return@launch
                }
                mutableState.value =
                    when (answer) {
                        is Outcome.Ok -> ActiveContextUi.Ready(answer.value.toImmutableList())
                        is Outcome.Failed ->
                            when {
                                previous != null && source.supported && answer.failure !is Failure.UnreadableReply ->
                                    ActiveContextUi.Ready(previous, refreshFailed = true)
                                !source.supported || answer.failure is Failure.Unavailable -> ActiveContextUi.Unsupported
                                answer.failure is Failure.UnreadableReply -> ActiveContextUi.Invalid
                                else -> ActiveContextUi.Failed
                            }
                    }
            }
    }

    private fun api(graph: ConnectionGraph): ApiRouteGateway? = (graph as ChatGraphAccessors).chatGateway as? ApiRouteGateway
}
