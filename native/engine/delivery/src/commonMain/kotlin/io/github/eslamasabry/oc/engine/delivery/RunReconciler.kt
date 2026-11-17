package io.github.eslamasabry.oc.engine.delivery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.domain.RunRecoveryGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.model.StreamStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

/** App visibility, independent of whether background work still watches the connection. */
public fun interface RunRecoveryVisibility {
    public fun state(): StateFlow<Visibility>
}

@ContributesTo(AppScope::class)
public interface RunRecoveryBindings {
    @Multibinds(allowEmpty = true) public val runRecoveryVisibility: Set<RunRecoveryVisibility>
}

/**
 * One connection-owned reader. The finite tier waits are cancelled by progress, idle or hiding; no polling loop.
 * Reconnects and tier checks coalesce in a conflated channel on the existing delivery reconcile lane.
 */
internal class RunReconciler(
    private val recovery: RunRecoveryGateway,
    private val events: GatewayEvents,
    private val visible: StateFlow<Visibility>,
    private val now: () -> Long,
    scope: CoroutineScope,
    lane: CoroutineContext,
    private val checked: (SessionId, Boolean?) -> Unit = { _, _ -> },
) {
    private data class Run(val at: Long, val tier: Int = -1)
    private val runs = MutableStateFlow<Map<SessionId, Run>>(emptyMap())
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(lane) {
            events.all.collect { observe(it.event) }
        }
        scope.launch(lane) {
            combine(visible, events.status, events.connects) { v, s, n -> Triple(v, s, n) }
                .collectLatest { (v, s, _) ->
                    if (v != Visibility.Foreground || s != StreamStatus.Connected) return@collectLatest
                    requests.trySend(Unit)
                    requests.receiveAsFlow().collect {
                        val snapshot = runs.value
                        if (snapshot.isEmpty()) return@collect
                        // Assertion-red keeps the interfaces but preserves the base's lack of recovery.
                        if (!ENABLED) return@collect
                        val result = withTimeoutOrNull(10_000) { recovery.reconcileRuns(snapshot.keys) }
                        if (visible.value != Visibility.Foreground) return@collect
                        if (result !is Outcome.Ok) {
                            snapshot.keys.forEach { checked(it, null) }
                            recovery.reconnectAfterRunCheck()
                        } else {
                            snapshot.keys.forEach { id ->
                                checked(id, result.value[id] == io.github.eslamasabry.oc.core.model.SessionStatus.Busy ||
                                    result.value[id] == io.github.eslamasabry.oc.core.model.SessionStatus.Retry)
                            }
                        }
                    }
                }
        }
        scope.launch(lane) {
            combine(visible, events.status, runs) { v, s, r -> Triple(v, s, r) }
                .collectLatest { (v, s, snapshot) ->
                    if (v != Visibility.Foreground || s != StreamStatus.Connected || snapshot.isEmpty()) return@collectLatest
                    val next = snapshot.values.minOfOrNull { run -> STEPS.getOrNull(run.tier + 1)?.plus(run.at) ?: Long.MAX_VALUE }
                        ?: return@collectLatest
                    if (next == Long.MAX_VALUE) return@collectLatest
                    delay((next - now()).coerceAtLeast(0))
                    val at = now()
                    runs.value = runs.value.mapValues { (_, run) ->
                        run.copy(tier = maxOf(run.tier, STEPS.indexOfLast { at - run.at >= it }))
                    }
                    requests.trySend(Unit)
                }
        }
    }

    private fun observe(event: GatewayEvent) {
        val id = when (event) {
            is GatewayEvent.RunStarted -> event.sessionId
            is GatewayEvent.RunRetrying -> event.sessionId
            is GatewayEvent.RunEnded -> event.sessionId
            is GatewayEvent.PartDelta -> event.sessionId
            is GatewayEvent.PartUpserted -> SessionId(event.part.sessionId)
            else -> return
        }
        when (event) {
            is GatewayEvent.RunEnded -> runs.value -= id
            is GatewayEvent.RunStarted, is GatewayEvent.RunRetrying -> runs.value += id to Run(now(), runs.value[id]?.tier ?: -1)
            else -> if (id in runs.value) runs.value += id to Run(now(), runs.value.getValue(id).tier)
        }
    }

    private companion object {
        const val ENABLED = false
        val STEPS = listOf(60_000L, 300_000L, 900_000L)
    }
}
