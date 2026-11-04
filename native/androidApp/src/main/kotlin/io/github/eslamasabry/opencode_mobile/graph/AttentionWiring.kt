package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.attention.ElsewhereAttention
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One observer per existing connection; removing a profile cancels its observer and drops its
 * observations.
 */
@Inject
@SingleIn(AppScope::class)
class AppElsewhereAttention(
    registry: ConnectionRegistry,
    dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private data class Entry(
        val graph: ConnectionGraph,
        val tracker: ElsewhereAttention,
        val job: Job,
    )

    private val entries = LinkedHashMap<ProfileId, Entry>()
    private val mutable = MutableStateFlow<Map<ProfileId, ElsewhereAttention>>(emptyMap())
    val trackers: StateFlow<Map<ProfileId, ElsewhereAttention>> = mutable
    private val lane = dispatchers.serial("app-elsewhere-attention")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)

    init {
        scope.launch {
            registry.live.collect { live ->
                for ((id, entry) in entries.toMap()) if (live[id] !== entry.graph) {
                    entry.job.cancel()
                    entries.remove(id)
                }
                for ((id, graph) in live) if (entries[id] == null) {
                    val tracker = ElsewhereAttention(dispatchers)
                    val job = scope.launch {
                        graph.session.server.events.all.collect { tracker.observe(it) }
                    }
                    entries[id] = Entry(graph, tracker, job)
                }
                mutable.value = entries.mapValues { it.value.tracker }
            }
        }
    }
}
