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

    fun start() {
        if (started) return
        started = true
        runtime.onManualReady = { connect(automatic = false) }
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    scope.launch { check() }
                }

                override fun onActivityPaused(activity: Activity) = Unit

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
