package io.github.eslamasabry.oc.engine.connection

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Slice-0 skeleton of the connection-scoped session (status, capabilities); the reconnect loop is
 * slice 1.
 */
@SingleIn(ConnectionScope::class)
@Inject
public class ConnectionSession(
    public val profile: ServerProfile,
    private val gateway: ServerGateway,
    scope: ConnectionCoroutineScope,
) {
    /**
     * The gateway of this exact live graph; callers must retain the graph identity when
     * dispatching.
     */
    public val server: ServerGateway
        get() = gateway

    public val reads: ConnectionReads = ConnectionReads(gateway, scope)

    init {
        scope.launch {
            gateway.events.all.collect { envelope ->
                when (val event = envelope.event) {
                    is GatewayEvent.SessionDeleted -> reads.forget(event.sessionId)
                    is GatewayEvent.MessageUpserted ->
                        reads.invalidateTail(
                            io.github.eslamasabry.oc.core.common.ids.SessionId(
                                event.message.sessionId
                            )
                        )
                    is GatewayEvent.PartDelta -> reads.invalidateTail(event.sessionId)
                    is GatewayEvent.RevertChanged -> reads.invalidateTail(event.sessionId)
                    is GatewayEvent.TranscriptStale -> reads.invalidateTail(event.sessionId)
                    else -> Unit
                }
            }
        }
    }

    public val readState: ConnectionReadState = ConnectionReadState()

    private val mutableStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
    public val status: StateFlow<StreamStatus> = mutableStatus.asStateFlow()
    public val capabilities: StateFlow<ServerCapabilities>
        get() = gateway.capabilities

    internal fun followTransport(scope: CoroutineScope) {
        scope.launch { gateway.events.status.collect { mutableStatus.value = it } }
    }

    internal fun markConnected() {
        mutableStatus.value = StreamStatus.Connected
    }

    internal suspend fun close() {
        reads.close()
        mutableStatus.value = StreamStatus.Disconnected(attempt = 0, nextAttemptAtMs = null)
        gateway.close()
    }
}
