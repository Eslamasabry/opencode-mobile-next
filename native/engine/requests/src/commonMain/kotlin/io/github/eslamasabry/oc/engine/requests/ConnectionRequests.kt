package io.github.eslamasabry.oc.engine.requests

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.pace.DefaultPacer
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.domain.ServerGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

/** One request engine per connection, shared across entries; no per-screen gateway writers. */
@SingleIn(ConnectionScope::class)
@Inject
public class ConnectionRequests(gateway: ServerGateway, scope: ConnectionCoroutineScope, dispatchers: AppDispatchers, clock: Clock) {
    private val visibility = MutableStateFlow(Visibility.HiddenIdle)
    private var holders = 0
    public val engine: RequestsEngine = RequestsEngine(gateway.asRequestsPort(), scope, dispatchers, clock, DefaultPacer(visibility))

    public fun watching(open: Boolean) {
        holders = (holders + if (open) 1 else -1).coerceAtLeast(0)
        visibility.value = if (holders > 0) Visibility.HiddenWatching else Visibility.HiddenIdle
    }
}
