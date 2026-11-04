package io.github.eslamasabry.oc.feature.servers.account.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Opaque source identity includes the saved profile, connected gateway and selected location. */
public data class AgentAccountSource(
    val scope: Any?,
    val name: String,
    val connected: Boolean,
    val supported: Boolean,
)

/** One real engine owner; closing drains only the login owned by this account session. */
public interface AgentAccountHandle {
    public val active: Boolean
    public val state: StateFlow<AccountPanelState>

    public suspend fun refresh()

    public suspend fun signIn()

    public suspend fun cancel()

    public suspend fun close()
}

public sealed interface AgentAccountLink {
    public data object Opened : AgentAccountLink

    public data class Ask(val host: String) : AgentAccountLink
}

public interface AgentAccountPort {
    public fun watch(profile: ProfileId): Flow<AgentAccountSource>

    public suspend fun open(profile: ProfileId, scope: Any): Outcome<AgentAccountHandle>

    public fun openLink(
        profile: ProfileId,
        scope: Any,
        url: String,
        confirmed: Boolean,
    ): Outcome<AgentAccountLink>

    public fun copyCode(profile: ProfileId, scope: Any, code: String): Outcome<Unit>

    public fun moment(epochMs: Long): String

    public fun number(value: Long): String
}

public enum class AgentAccountPhase {
    Loading,
    Panel,
    Disconnected,
    Unsupported,
    ScopeLost,
}

public data class AgentAccountUi(
    val phase: AgentAccountPhase = AgentAccountPhase.Loading,
    val profileName: String = "",
    val panel: AccountPanelState = AccountPanelState(),
    val failure: Failure? = null,
    val askedHost: String? = null,
    val copied: Boolean = false,
    val updated: String? = null,
    val nowMs: Long = 0,
    val lifetime: String? = null,
    val peak: String? = null,
    val resetMoments: Map<Long, String> = emptyMap(),
) {
    override fun toString(): String =
        "AgentAccountUi(phase=$phase, status=${panel.status}, login=${panel.loginStatus})"
}

public interface AgentAccountActions {
    public fun back()

    public fun refresh()

    public fun signIn()

    public fun cancel()

    public fun openSignIn()

    public fun confirmOpen()

    public fun dismissOpen()

    public fun copyCode()
}
