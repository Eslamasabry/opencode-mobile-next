package io.github.eslamasabry.oc.feature.agenttools.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ServerGateway

public class AgentToolsSession(
    public val identity: Any,
    public val name: String,
    public val gateway: ServerGateway,
)

public interface AgentToolsHost {
    public fun open(profile: ProfileId): AgentToolsSession?

    public fun isCurrent(profile: ProfileId, identity: Any): Boolean

    public fun display(value: String): String
}

/**
 * Platforms without connected product gateways show the unavailable state; they never invent an
 * agent.
 */
@Inject
@ContributesBinding(AppScope::class)
public class NoAgentToolsHost : AgentToolsHost {
    override fun open(profile: ProfileId): AgentToolsSession? = null

    override fun isCurrent(profile: ProfileId, identity: Any): Boolean = false

    override fun display(value: String): String = value
}
