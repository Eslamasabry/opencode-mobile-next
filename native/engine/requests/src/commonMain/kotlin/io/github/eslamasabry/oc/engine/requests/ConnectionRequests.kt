package io.github.eslamasabry.oc.engine.requests

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.ids.SessionId
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
    private val formSessions = HashMap<SessionId, Int>()
    private val formGateway = gateway

    init {
        formScope.launch {
            gateway.events.project.collect { envelope ->
                forms.onEvent(envelope.event)
                if (envelope.event == GatewayEvent.Connected && gateway.capabilities.value.forms)
                    refreshForms()
            }
        }
        formScope.launch {
            if (gateway.capabilities.value.forms) refreshForms()
            DefaultPacer(visibility).ticks(RequestsTimers.REREAD).collect {
                if (gateway.capabilities.value.forms) refreshForms()
            }
        }
    }

    private suspend fun refreshForms() {
        forms.refresh()
        for (session in formSessions.keys.toList()) {
            if (session in formSessions) forms.refreshSession(session)
        }
    }

    /** Watching one chat also hydrates its forms outside the server's default folder. */
    public fun watchingFormSession(session: SessionId, open: Boolean) {
        watching(open)
        formScope.launch {
            val previous = formSessions[session] ?: 0
            val next = (previous + if (open) 1 else -1).coerceAtLeast(0)
            if (next == 0) formSessions.remove(session) else formSessions[session] = next
            forms.watchSession(session, next > 0)
            if (open && previous == 0 && formGateway.capabilities.value.forms)
                forms.refreshSession(session)
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
