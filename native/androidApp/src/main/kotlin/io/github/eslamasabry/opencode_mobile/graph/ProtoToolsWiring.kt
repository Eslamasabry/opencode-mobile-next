package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.agents.tools.presentation.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@ContributesTo(ConnectionScope::class)
interface ToolsConnectionAccess {
    val toolsOperations: ServerOperationsGateway
}

/**
 * Existing authenticated graph only: a page never changes the active server or creates a parallel
 * client.
 */
@Inject
@ContributesBinding(AppScope::class, binding = binding<ToolsSource>())
class ProtoToolsSource(private val registry: ConnectionRegistry) : ToolsSource {
    override suspend fun connect(profile: ProfileId): Outcome<ToolsConnection> {
        val graph =
            registry.live.value[profile]
                ?: return Failure.Unavailable(FeatureId("serverCatalog")).failed()
        val server = graph.session.server
        val location = server.location.value
        val operations = (graph as ToolsConnectionAccess).toolsOperations
        return ToolsConnection(
                server,
                operations,
                (server as? LibraryGateway) ?: (operations as? LibraryGateway),
                isCurrent = {
                    registry.live.value[profile] === graph && server.location.value == location
                },
                hints =
                    server.events.project
                        .filter { envelope ->
                            val event = envelope.event
                            event is GatewayEvent.RefreshHint &&
                                event.kind.raw in
                                    setOf("plugin", "mcp", "catalog", "config", "skill")
                        }
                        .map { Unit },
            )
            .ok()
    }
}
