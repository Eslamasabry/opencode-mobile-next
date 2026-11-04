package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * The saved built-in server and the current profile; no runtime is started by reading this state.
 */
public data class AgentsHubConnectionUi(
    val builtInAvailable: Boolean = false,
    val usingBuiltIn: Boolean = false,
)

public interface AgentsHubConnection {
    public val state: StateFlow<AgentsHubConnectionUi>

    /**
     * Connects the saved built-in profile, preserving the regular connection admission and failure
     * rules.
     */
    public suspend fun switchToBuiltIn(): Outcome<Unit>
}

public data class AgentsHubControlsUi(
    val connection: AgentsHubConnectionUi = AgentsHubConnectionUi(),
    val working: Boolean = false,
    val failure: Failure? = null,
)

public interface AgentsHubActions {
    public fun switchToBuiltIn()

    public fun openCardsSettings()
}
