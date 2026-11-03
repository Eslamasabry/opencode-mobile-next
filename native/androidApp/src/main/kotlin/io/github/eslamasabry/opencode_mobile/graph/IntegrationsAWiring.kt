package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorServer
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsSource
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2Connectors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * Unit BUILD-INTEGRATIONS-A: the Android side of the Connectors page (Settings › Agent › Tools). It reads the live
 * connection graphs the preview runtime opened (shared with Search/Usage) and talks OC2's MCP wire through
 * Oc2Connectors; any other server says it cannot list connectors (Failure.Unavailable), never a guess.
 */

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ConnectorsSource>())
@Inject
class AndroidConnectorsSource(private val shared: SearchUsageServers, appScope: AppCoroutineScope) : ConnectorsSource {
    private val scope = CoroutineScope(appScope.coroutineContext)

    override val servers: StateFlow<ImmutableList<ConnectorServer>> =
        shared.live
            .map { list -> list.map { (id, name) -> ConnectorServer(id, name) }.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    override val active: StateFlow<ProfileId?> = shared.active

    override suspend fun list(profile: ProfileId): Outcome<ImmutableList<McpServerInfo>> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.servers(gateway) ?: unavailable()
    }

    override suspend fun connect(profile: ProfileId, name: String): Outcome<Unit> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.connect(gateway, name) ?: unavailable()
    }

    override suspend fun disconnect(profile: ProfileId, name: String): Outcome<Unit> {
        val gateway = shared.gateway(profile) ?: return stale()
        return Oc2Connectors.disconnect(gateway, name) ?: unavailable()
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun unavailable(): Outcome<Nothing> = Failure.Unavailable(FeatureId("serverCatalog")).failed()
}
