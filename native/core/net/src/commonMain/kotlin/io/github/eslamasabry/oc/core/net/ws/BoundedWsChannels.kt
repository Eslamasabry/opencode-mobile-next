package io.github.eslamasabry.oc.core.net.ws

import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.ChannelOverflow
import io.ktor.websocket.WebSocketChannelsConfig

/**
 * One undelivered raw frame and bounded outgoing traffic, with TCP backpressure rather than drops.
 */
@OptIn(InternalAPI::class)
internal fun boundedWsChannels(): WebSocketChannelsConfig =
    WebSocketChannelsConfig().apply {
        incoming = bounded(1, ChannelOverflow.SUSPEND)
        outgoing = bounded(1, ChannelOverflow.SUSPEND)
    }
