package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsHost
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsSession
import io.github.eslamasabry.oc.feature.agenttools.presentation.NoAgentToolsHost

@Inject
@ContributesBinding(AppScope::class, replaces = [NoAgentToolsHost::class])
class GraphAgentToolsHost(
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
) : AgentToolsHost {
    private class Identity(val graph: ConnectionGraph, val location: ServerLocation)

    override fun open(profile: ProfileId): AgentToolsSession? {
        val graph = registry.live.value[profile] ?: return null
        return AgentToolsSession(
            Identity(graph, graph.session.server.location.value),
            redactor.redact(graph.profile.name),
            graph.session.server,
        )
    }

    override fun isCurrent(profile: ProfileId, identity: Any): Boolean {
        val scope = identity as? Identity ?: return false
        return registry.live.value[profile] === scope.graph &&
            scope.graph.session.server.location.value == scope.location
    }

    override fun display(value: String): String = redactor.redact(value)
}
