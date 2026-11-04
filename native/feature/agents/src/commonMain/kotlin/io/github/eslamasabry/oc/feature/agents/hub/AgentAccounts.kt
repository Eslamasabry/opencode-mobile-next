package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.account.AgentAccountSession
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** Connected runtimes with an account facet. Opening the panel never starts sign-in. */
public interface AgentAccounts {
    public val accounts: StateFlow<ImmutableList<AgentAccountTarget>>

    public suspend fun open(profileId: ProfileId): Outcome<AgentAccountSession>

    /** App binding uses ExternalLinkPolicy then ExternalLinkOpener, including host confirmation. */
    public suspend fun openSignIn(url: String): Outcome<Unit>
}

public data class AgentAccountTarget(val id: ProfileId, val name: String)

/**
 * Desktop has no connected account runtime until its product phase; Android replaces this
 * capability binding.
 */
@dev.zacsweers.metro.Inject
@dev.zacsweers.metro.ContributesBinding(dev.zacsweers.metro.AppScope::class)
public class NoAgentAccounts : AgentAccounts {
    override val accounts: StateFlow<ImmutableList<AgentAccountTarget>> =
        kotlinx.coroutines.flow.MutableStateFlow(kotlinx.collections.immutable.persistentListOf())

    override suspend fun open(profileId: ProfileId): Outcome<AgentAccountSession> =
        Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("agentAccount")
            )
        )

    override suspend fun openSignIn(url: String): Outcome<Unit> =
        Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("agentAccount")
            )
        )
}
