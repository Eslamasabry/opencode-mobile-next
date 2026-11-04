package io.github.eslamasabry.oc.core.net.okhttp

import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.ChannelOverflow
import io.ktor.websocket.WebSocketChannelsConfig

/** Plan 04 §5.2: the sole opt-in site; neither raw frame queue can grow without bound. */
@OptIn(InternalAPI::class)
internal fun boundedWsChannels(): WebSocketChannelsConfig =
    WebSocketChannelsConfig().apply {
        incoming = bounded(8, ChannelOverflow.SUSPEND)
        outgoing = bounded(64, ChannelOverflow.SUSPEND)
    }
