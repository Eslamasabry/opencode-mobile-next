package io.github.eslamasabry.oc.engine.connection

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Slice-0 skeleton of the connection-scoped session (status, capabilities); the reconnect loop is slice 1. */
@SingleIn(ConnectionScope::class)
@Inject
public class ConnectionSession(
    public val profile: ServerProfile,
    private val gateway: ServerGateway,
) {
    private val mutableStatus = MutableStateFlow(StreamStatus.Connecting)
    public val status: StateFlow<StreamStatus> = mutableStatus.asStateFlow()
    public val capabilities: StateFlow<ServerCapabilities> get() = gateway.capabilities

    internal fun markConnected() {
        mutableStatus.value = StreamStatus.Connected
    }

    internal suspend fun close() {
        mutableStatus.value = StreamStatus.Disconnected
        gateway.close()
    }
}
