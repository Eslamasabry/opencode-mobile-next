package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.PowerManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.DeliveryAccessors
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedServerPorts
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedServerWatch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope

/**
 * B-146: watches the saved servers that have a queued prompt and opens one once it answers, so its
 * own offline queue sends the prompt exactly once. Only while the app is in use, online and not in
 * battery saver; at most three servers a round, with a per-server backoff ([QueuedServerWatch]).
 */
@SingleIn(AppScope::class)
@Inject
class AppQueuedServerWatch(
    private val application: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val visibility: AppVisibility,
    private val network: NetworkStatus,
    private val clock: Clock,
    dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : QueuedServerPorts {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.serial("queued-servers"))
    private val opened = ConcurrentHashMap<ProfileId, ConnectionGraph>()
    private val watch = QueuedServerWatch(this, scope) { clock.now().toEpochMilliseconds() }
    @Volatile private var warm = false

    fun start() = watch.start()

    override fun queue(): QueueRead =
        if (!warm) QueueRead.Readable(emptyList())
        else OfflineQueueStore(stores.prefs, stores.lanes.app).load()

    override fun live(profile: ProfileId): Boolean = registry.live.value[profile] != null

    // B-146: reading the delivery creates it (a background connection has no chat screen to do that), and it sends
    // the queue once its stream is connected.
    override fun nudge(profile: ProfileId) {
        val graph = registry.live.value[profile] ?: return
        (graph as DeliveryAccessors).connectionDelivery.sendWaiting()
    }

    override fun mayWatch(): Boolean {
        if (!warm) {
            // Nothing is read before the migration gate opened and the stores are warm.
            warm = runCatchingWarm()
            if (!warm) return false
        }
        val power = application.getSystemService(PowerManager::class.java)
        return visibility.state.value == Visibility.Foreground &&
            network.state.value.online != NetworkState.Online.No &&
            power?.isPowerSaveMode != true
    }

    private fun runCatchingWarm(): Boolean =
        try {
            stores.requirePrepared()
            true
        } catch (_: IllegalStateException) {
            false
        }

    override suspend fun open(profile: ProfileId): Outcome<Unit> {
        val loaded = stores.profiles.profile(profile) ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        if (loaded.cleartextUnconfirmed) return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val server = loaded.toServerProfile() ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        return when (val graph = registry.open(server)) {
            is Outcome.Failed -> graph
            is Outcome.Ok -> {
                opened[profile] = graph.value
                Unit.ok()
            }
        }
    }

    override suspend fun release(profile: ProfileId) {
        val graph = opened.remove(profile) ?: return
        // The server the person is using stays connected.
        if (stores.profiles.activeId.value != profile) registry.closeIfCurrent(profile, graph)
    }
}

@ContributesTo(AppScope::class)
interface QueuedServerWatchAccessors {
    val queuedServerWatch: AppQueuedServerWatch
}
