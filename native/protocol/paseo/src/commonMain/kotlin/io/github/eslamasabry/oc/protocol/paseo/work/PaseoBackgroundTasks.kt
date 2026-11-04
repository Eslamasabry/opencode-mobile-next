package io.github.eslamasabry.oc.protocol.paseo.work

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import kotlinx.coroutines.flow.Flow

/**
 * The application-owned background-work book. The app isolates this view by profile and server
 * identity; protocol code supplies only the daemon's own agent ids and already-mapped messages.
 */
public interface PaseoBackgroundTasks {
    public val changes: Flow<Unit>

    public fun replace(agentId: String, messages: List<MessageWithParts>)

    public fun observe(agentId: String, message: MessageWithParts)

    public fun runtimeGap(agentId: String, atMs: Long)

    public fun running(agentId: String, nowMs: Long): Boolean

    public fun turnFailed(agentId: String): Boolean
}
