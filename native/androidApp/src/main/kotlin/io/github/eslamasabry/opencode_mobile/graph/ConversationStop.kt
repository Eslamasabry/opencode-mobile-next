package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.delivery.StopAnswer
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors

/**
 * Stops [ref]'s running turn from outside its chat (the edge dock, the live notification) by the chat's own Stop path
 * (`GraphChatWorld.stopTurn`): the delivery engine records the person's Stop and calls off waiting sends before it
 * interrupts; the requests that turn raised are dropped once the stop is asked; a stale fence (or a connection without
 * a delivery engine) falls back to the server's own stop, as the chat does. Null or a graph without chat access refuses.
 */
internal suspend fun stopLikeTheChat(graph: ConnectionGraph?, ref: ConversationRef): Outcome<Unit> {
    val chat = graph as? ChatGraphAccessors ?: return Outcome.Failed(STOP_STALE)
    val requests = chat.chatRequests.engine
    val captured = requests.pending.value.filter { it.sessionId == ref.sessionId }
    chat.chatDelivery.engine?.let { engine ->
        when (val stopped = engine.stopTurn(ref)) {
            is Outcome.Ok -> {
                if (stopped.value == StopAnswer.Asked) requests.discardStopped(captured)
                return Outcome.Ok(Unit)
            }
            is Outcome.Failed -> if (stopped.failure != STOP_STALE) return stopped
        }
    }
    return when (val r = chat.chatGateway.stop(ref.sessionId)) {
        is Outcome.Ok -> {
            requests.discardStopped(captured)
            Outcome.Ok(Unit)
        }
        is Outcome.Failed -> r
    }
}

private val STOP_STALE = Failure.Refused(RefusalReason.StaleScope)
