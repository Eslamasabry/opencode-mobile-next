package io.github.eslamasabry.oc.engine.requests

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.pace.DefaultPacer
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.requests.forms.FormRequests
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** One request engine per connection, shared across entries; no per-screen gateway writers. */
@SingleIn(ConnectionScope::class)
@Inject
public class ConnectionRequests(
    gateway: ServerGateway,
    scope: ConnectionCoroutineScope,
    dispatchers: AppDispatchers,
    clock: Clock,
) {
    private val visibility = MutableStateFlow(Visibility.HiddenIdle)
    private var holders = 0
    public val engine: RequestsEngine =
        RequestsEngine(
            gateway.asRequestsPort(),
            scope,
            dispatchers,
            clock,
            DefaultPacer(visibility),
        )

    private val formScope = CoroutineScope(scope.coroutineContext + dispatchers.serial("forms"))
    public val forms: FormRequests =
        FormRequests(gateway, formScope, sourceIdentity = { gateway.location.value })

    init {
        formScope.launch {
            gateway.events.project.collect { envelope ->
                forms.onEvent(envelope.event)
                if (envelope.event == GatewayEvent.Connected && gateway.capabilities.value.forms)
                    forms.refresh()
            }
        }
        formScope.launch {
            if (gateway.capabilities.value.forms) forms.refresh()
            DefaultPacer(visibility).ticks(RequestsTimers.REREAD).collect {
                if (gateway.capabilities.value.forms) forms.refresh()
            }
        }
    }

    /** A form action belongs to the exact connection generation, even if its screen leaves. */
    public fun formAction(action: suspend (FormRequests) -> Unit) {
        formScope.launch { action(forms) }
    }

    public fun watching(open: Boolean) {
        holders = (holders + if (open) 1 else -1).coerceAtLeast(0)
        visibility.value = if (holders > 0) Visibility.HiddenWatching else Visibility.HiddenIdle
    }
}
