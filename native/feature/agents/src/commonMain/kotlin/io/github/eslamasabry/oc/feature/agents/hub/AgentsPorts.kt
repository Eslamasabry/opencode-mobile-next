package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings › Agents (plan 07 §5 G01, Flutter `AgentsScreen`): the agents this phone can reach and
 * the one act each needs. Two places run agents: this phone's built-in Linux ([PhoneAgents]) and a
 * computer running Paseo ([AgentComputers], Flutter's Paseo server kind). The app binds both in
 * `AgentsWiring`.
 */
public interface AgentComputers {
    /** Saved Paseo computers, in the Servers order. */
    public val computers: StateFlow<ImmutableList<AgentComputer>>

    /** The pairing check (address, optional daemon password, project folder). Saves nothing. */
    public suspend fun check(
        address: String,
        password: String,
        folder: String,
    ): Outcome<ComputerCheck>

    /**
     * Saves the computer (password in secure storage only) and connects it. Its conversations then
     * join the list.
     */
    public suspend fun add(
        name: String,
        address: String,
        password: String,
        folder: String,
    ): Outcome<ProfileId>

    /** Connects (if needed) and reads which agents are ready there. */
    public suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>>

    /**
     * A new conversation on that computer; the model picker's provider chooses Claude Code or Pi.
     */
    public suspend fun newConversation(id: ProfileId): Outcome<ConversationRef>
}

public data class AgentComputer(
    val id: ProfileId,
    val name: String,
    val address: String,
    val folder: String,
    val connected: Boolean,
)

/**
 * What a passed check proved. [agents] are display names of the agents ready there; empty is a real
 * answer.
 */
public sealed interface ComputerCheck {
    public data class Ready(val version: String?, val agents: ImmutableList<String>) : ComputerCheck

    /** The input was refused before anything was sent. */
    public data class Invalid(val address: Boolean, val folder: Boolean, val password: Boolean) :
        ComputerCheck
}

/** This phone's built-in agents (Claude Code, Codex, … in the phone's Linux). */
public interface PhoneAgents {
    public val state: StateFlow<PhoneAgentsState>

    public suspend fun refresh(): Outcome<Unit> = Outcome.Ok(Unit)

    public suspend fun resetSavedSignIns(): Outcome<Unit> =
        Outcome.Failed(Failure.SecretUnavailable())

    public suspend fun usePreviousVersion(id: String): Outcome<Unit> =
        Outcome.Failed(Failure.SecretUnavailable())

    public suspend fun install(id: String): Outcome<PhoneAgentInstallResult> =
        Outcome.Ok(PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable))

    public suspend fun cancelInstall(): Outcome<Unit> = Outcome.Ok(Unit)

    public suspend fun remove(id: String): Outcome<PhoneAgentRemoval> =
        Outcome.Ok(PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Unavailable))
}

public data class PhoneAgentsState(
    /**
     * False when this build has no built-in Linux agent host: the screen says so in one line, never
     * an empty page.
     */
    val available: Boolean = false,
    val agents: ImmutableList<PhoneAgentRow> = persistentListOf(),
    val installation: PhoneAgentInstallation? = null,
    val needsRestart: Boolean = false,
    val helperStoppedUnexpectedly: Boolean = false,
    val helperMayHaveBeenStoppedByAndroid: Boolean = false,
)

public data class PhoneAgentRow(
    val id: String,
    val name: String,
    val status: PhoneAgentStatus,
    val removable: Boolean = false,
    val installable: Boolean = false,
    val previousVersionAvailable: Boolean = false,
)

public sealed interface PhoneAgentRemoval {
    public data class Removed(val freedBytes: Long) : PhoneAgentRemoval

    public data class Refused(val issue: PhoneAgentRemovalIssue) : PhoneAgentRemoval
}

public enum class PhoneAgentRemovalIssue {
    Busy,
    Unsafe,
    Unconfirmed,
    Unavailable,
}

/** The row states of Flutter `agentsState*` (17 §1.4 order); the words live in the screen. */
public enum class PhoneAgentStatus {
    Ready,
    NotInstalled,
    SignInNeeded,
    Checking,
    PhoneCheck,
    Stopped,
    Limit,
    Unavailable,
}

/** Default: no agent host on this phone (desktop, or an app build without the built-in Linux). */
public class NoPhoneAgents(override val state: StateFlow<PhoneAgentsState>) : PhoneAgents

/** A failure the screen turns into one plain line plus Details. */
public data class AgentsProblem(val kind: AgentsIssue, val failure: Failure? = null)

public enum class AgentsIssue {
    CheckFailed,
    AddFailed,
    AgentsFailed,
    StartFailed,
    Added,
}

public data class PhoneAgentInstallation(
    val id: String,
    val phase: PhoneAgentInstallPhase,
    val fraction: Double?,
)

public enum class PhoneAgentInstallPhase {
    Installing,
    Done,
    Failed,
    Interrupted,
}

public sealed interface PhoneAgentInstallResult {
    public data object Started : PhoneAgentInstallResult

    public data class Refused(val issue: PhoneAgentInstallIssue) : PhoneAgentInstallResult
}

public enum class PhoneAgentInstallIssue {
    Busy,
    Space,
    Unavailable,
}

/** The visible local helper action. Platform bindings must use native owner admission. */
public fun interface PhoneAgentHostStarter {
    public suspend fun connect(): Outcome<Unit>
}

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface PhoneAgentHostBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneAgentHostStarters: Set<PhoneAgentHostStarter>
}
