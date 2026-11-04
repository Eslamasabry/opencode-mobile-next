package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.domain.ConfigurationGateway
import io.github.eslamasabry.oc.core.domain.ConfigurationPreview
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.settings.presentation.ConfigurationInspection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings › Server › Server configuration. One authenticated read, with a captured server and
 * location.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@Inject
class AndroidConfigurationInspection(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    appScope: AppCoroutineScope,
) : ConfigurationInspection {
    private val scope = CoroutineScope(appScope.coroutineContext)

    private data class Target(val graph: ConnectionGraph, val location: ServerLocation)

    private val selected =
        combine(registry.live, stores.profiles.activeId) { live, active ->
                active?.let { live[it] } ?: live.values.firstOrNull()
            }
            .flatMapLatest { graph ->
                if (graph == null) flowOf(null)
                else
                    combine(graph.session.server.capabilities, graph.session.server.location) {
                        caps,
                        location ->
                        Target(graph, location).takeIf {
                            caps.setupConfigRead && graph.session.server is ConfigurationGateway
                        }
                    }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val available: StateFlow<Boolean> =
        selected.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)
    private val mutableRevision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    init {
        scope.launch { selected.collect { mutableRevision.value += 1 } }
    }

    override suspend fun read(): Outcome<ConfigurationPreview> {
        val target = selected.value ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val gateway = target.graph.session.server
        val facet =
            gateway as? ConfigurationGateway
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val result = facet.configurationPreview(target.location)
        // A reconnect, server switch, capability loss or location switch invalidates this result.
        val current =
            stores.profiles.activeId.value?.let { registry.live.value[it] }
                ?: registry.live.value.values.firstOrNull()
        if (
            current !== target.graph ||
                gateway.location.value != target.location ||
                !gateway.capabilities.value.setupConfigRead
        ) {
            return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        return result
    }
}
