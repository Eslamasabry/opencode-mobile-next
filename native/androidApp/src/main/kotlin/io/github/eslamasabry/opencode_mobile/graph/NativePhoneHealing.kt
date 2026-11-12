package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.lifecycle.ReadyReconnect
import io.github.eslamasabry.oc.engine.runtime.lifecycle.ReturnToForeground
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App-lifetime health follows lifecycle and link changes, never streamed message updates. */
@Inject
@SingleIn(AppScope::class)
class NativePhoneHealing(
    private val app: Application,
    private val runtime: BuiltinRuntime,
    private val stores: PreviewStores,
    private val policies: AppAutomationPolicies,
    private val connections: PreviewRuntime,
    private val registry: ConnectionRegistry,
    private val clock: Clock,
    dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("phone-healing"))
    private val checkLane = Mutex()
    private val connectLane = Mutex()
    private var started = false
    private var lastAttempt: Instant? = null
    private var failures = 0
    private var attemptOwner: ProfileId? = null
    private var watched: ConnectionGraph? = null
    private var watch: Job? = null
    private val away = ReturnToForeground()

    fun start() {
        if (started) return
        started = true
        // Never awaited inside the start: the start may be the connection gate's restart, which holds the locks this
        // connect needs (M1, "Connecting" for good after a phone restart).
        val afterStart = ReadyReconnect(scope) { connect(automatic = false) }
        runtime.onManualReady = { afterStart.announce() }
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val wake = away.returned(clock.now())
                    scope.launch {
                        check()
                        if (wake) wakeConnected()
                    }
                }

                override fun onActivityPaused(activity: Activity) = away.left(clock.now())

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            stores.profiles.profiles.collectLatest {
                followConnection()
                if (runtime.isForeground) check()
            }
        }
        scope.launch {
            registry.live.collect { followConnection() }
        }
        scope.launch {
            while (true) {
                delay(15.seconds)
                if (runtime.isForeground) check()
            }
        }
    }

    /**
     * Back after a while out of sight: a stream that still reads Connected may be dead (sleep, a network
     * switch). One wake re-dials it and the readers refetch. Only the open server, only if the person's
     * reconnect setting allows it; a server that is disconnected has no live graph, so it is left alone.
     */
    private suspend fun wakeConnected() = connectLane.withLock {
        val id = stores.profiles.activeId.value ?: return@withLock
        if (!runtime.isForeground || !reconnectAllowed(id)) return@withLock
        val graph = registry.live.value[id] ?: return@withLock
        if (graph.session.server.events.status.value == StreamStatus.Connected)
            graph.session.server.events.retryNow()
    }

    private fun followConnection() {
        val owner = runtime.healingOwner()
        val graph = owner?.let { registry.live.value[it] }
        if (watched === graph) return
        watch?.cancel()
        watched = graph
        if (graph == null) return
        watch = scope.launch {
            var down = false
            graph.session.server.events.status.collect { status ->
                val nowDown =
                    status is StreamStatus.Reconnecting || status is StreamStatus.Disconnected
                if (
                    nowDown &&
                        !down &&
                        runtime.isForeground &&
                        registry.live.value[graph.profile.id] === graph
                )
                    check()
                down = nowDown
                if (status == StreamStatus.Connected) {
                    failures = 0
                    lastAttempt = null
                }
            }
        }
    }

    private suspend fun check() = checkLane.withLock {
        if (!runtime.isForeground || stores.warm() is Outcome.Failed) return@withLock
        followConnection()
        val healthy = runtime.foregroundHealth()
        if (healthy is Outcome.Ok && healthy.value) connect(automatic = true)
    }

    /**
     * Try again and explicit Start bypass only reconnect backoff; neither can bypass native start
     * admission.
     */
    private suspend fun connect(automatic: Boolean) = connectLane.withLock {
        val owner = runtime.healingOwner() ?: return@withLock
        if (
            !runtime.isForeground ||
                !runtime.readable(owner) ||
                stores.profiles.activeId.value != owner
        )
            return@withLock
        if (automatic && !reconnectAllowed(owner)) return@withLock
        val graph = registry.live.value[owner]
        if (graph?.session?.server?.events?.status?.value == StreamStatus.Connected) return@withLock
        if (attemptOwner != owner) {
            attemptOwner = owner
            failures = 0
            lastAttempt = null
        }
        val wait = (10 * (1 shl failures.coerceIn(0, 3))).coerceAtMost(60).seconds
        if (automatic && lastAttempt?.let { clock.now() - it < wait } == true) return@withLock
        lastAttempt = clock.now()
        if (graph != null) {
            // The protocol owns reconnect and refetch. This wakes that loop; it never replays a
            // command.
            graph.session.server.events.retryNow()
            failures++
        } else {
            when (connections.connect(owner)) {
                is Outcome.Failed -> failures++
                is Outcome.Ok -> {
                    failures = 0
                    lastAttempt = null
                }
            }
        }
    }

    private fun reconnectAllowed(owner: ProfileId): Boolean =
        policies.allows(
            owner,
            io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior.Reconnect,
        )
}

@ContributesTo(AppScope::class)
interface NativeHealingAccessors {
    val nativePhoneHealing: NativePhoneHealing
}
