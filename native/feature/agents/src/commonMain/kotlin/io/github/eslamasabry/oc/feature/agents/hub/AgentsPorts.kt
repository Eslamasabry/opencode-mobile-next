package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.install.AgentInstaller
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings › Agents (plan 07 §5 G01, Flutter `AgentsScreen`): the agents this phone can reach and
 * the one act each needs. Two places run agents: this phone's built-in Linux ([PhoneAgents]) and a
 * computer running Paseo ([AgentComputers], Flutter's Paseo server kind). The app binds both in
 * `AgentsWiring`.
 */
public enum class AgentComputerKind {
    Paseo,
    Codex,
}

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

    public suspend fun check(
        address: String,
        password: String,
        folder: String,
        kind: AgentComputerKind,
    ): Outcome<ComputerCheck> =
        if (kind == AgentComputerKind.Paseo) check(address, password, folder)
        else Outcome.Failed(Failure.Unavailable(FeatureId("codex")))

    public suspend fun add(
        name: String,
        address: String,
        password: String,
        folder: String,
        kind: AgentComputerKind,
    ): Outcome<ProfileId> =
        if (kind == AgentComputerKind.Paseo) add(name, address, password, folder)
        else Outcome.Failed(Failure.Unavailable(FeatureId("codex")))

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
    val kind: AgentComputerKind = AgentComputerKind.Paseo,
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

    /**
     * Read installed program/account projections only. Never installs, signs in, or starts the
     * helper.
     */
    public suspend fun refresh(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneAgents")))

    public suspend fun resetSavedSignIns(): Outcome<Unit> =
        Outcome.Failed(Failure.SecretUnavailable())

    public suspend fun usePreviousVersion(id: String): Outcome<Unit> =
        Outcome.Failed(Failure.SecretUnavailable())

    public suspend fun install(id: String): Outcome<PhoneAgentInstallResult> =
        Outcome.Ok(PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable))

    public suspend fun cancelInstall(): Outcome<Unit> = Outcome.Ok(Unit)

    /** Puts away the notice of the last install that failed or was interrupted. */
    public suspend fun dismissInstallResult(): Outcome<Unit> = Outcome.Ok(Unit)

    /**
     * The free space installing [id] needs and the space this phone has now, read before the install starts (never
     * behind a running read: it waits for nothing); null when either is unknown.
     */
    public suspend fun installSpace(id: String): PhoneAgentSpace? = null

    /**
     * Runs the agent's own logout. `true` only when a fresh status read says it is signed out;
     * `false` means not confirmed. Conversations are never touched.
     */
    public suspend fun signOut(id: String): Outcome<Boolean> =
        Outcome.Failed(Failure.Unavailable(FeatureId("phoneAgentSignOut")))

    public suspend fun remove(id: String): Outcome<PhoneAgentRemoval> =
        Outcome.Ok(PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Unavailable))
}

public data class PhoneAgentsState(
    /**
     * True only when the built-in Linux is installed. Android can expose its pinned catalog before
     * setup; builds without a phone host keep an empty catalog.
     */
    val available: Boolean = false,
    val agents: ImmutableList<PhoneAgentRow> = persistentListOf(),
    val installation: PhoneAgentInstallation? = null,
    val needsRestart: Boolean = false,
    val helperStoppedUnexpectedly: Boolean = false,
    val helperMayHaveBeenStoppedByAndroid: Boolean = false,
    /** Runtime owner, independent of the currently selected remote server. */
    val owner: ProfileId? = null,
    /**
     * Where the phone agents' conversations live once they are connected (the helper's own profile). It is not a
     * computer: the Agents page never lists it under On computers.
     */
    val conversations: ProfileId? = null,
    /** This phone as the status line names it ("arm64 · Android 15"); null where it is not known. */
    val device: PhoneDevice? = null,
)

/** The phone's processor ("arm64") and Android version ("15"), as read by the platform. */
public data class PhoneDevice(val processor: String, val android: String)

public data class PhoneAgentRow(
    val id: String,
    val name: String,
    val status: PhoneAgentStatus,
    val removable: Boolean = false,
    val installable: Boolean = false,
    val previousVersionAvailable: Boolean = false,
    /** Current processor's pinned payload, not an estimated complete setup size. */
    val downloadBytes: Long? = null,
    val supportedInstallation: Boolean = installable,
    /** Only an installed agent with a supported sign-in method may open sign-in. */
    val signInAvailable: Boolean = false,
    /** Signed in now, and the agent's bridge can run its own logout. */
    val signOutAvailable: Boolean = false,
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

/**
 * A refusal that may pass by itself because the helper was only just started: nobody answered yet, or it answered
 * half-ready (a dropped or garbled first hello). A refused password, a protocol mismatch or an app refusal will not.
 */
public fun Failure.mayAnswerSoon(): Boolean =
    this is Failure.Network ||
        this is Failure.TimedOut ||
        this is Failure.ComputerDidNotAnswer ||
        this is Failure.UnreadableReply ||
        this is Failure.Unexpected ||
        this is Failure.Server ||
        this is Failure.Busy

/** Installed agents: the ones whose helper connection is worth making (Flutter `_installed`). */
public fun phoneInstalledAgents(state: PhoneAgentsState): Set<String> =
    state.agents.filter { it.status.installed }.map { it.id }.toSet()

/** Installed on this phone: not missing, not out of reach and not still being looked for. */
public val PhoneAgentStatus.installed: Boolean
    get() =
        this != PhoneAgentStatus.NotInstalled &&
            this != PhoneAgentStatus.Unavailable &&
            this != PhoneAgentStatus.Checking

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
    /** Why a failed or interrupted install ended, in a closed set; the words live in the screen. */
    val failure: PhoneAgentInstallFailure? = null,
    /** The technical side, already redacted; shown only under Details. */
    val details: io.github.eslamasabry.oc.core.common.Redacted? = null,
    /**
     * For [PhoneAgentInstallFailure.Version] and [PhoneAgentInstallFailure.Withdrawn]: the version the downloaded
     * program named (Version only) and the one this app installs, when the install said so (version text only; nothing
     * else the program printed).
     */
    val versions: PhoneAgentVersions? = null,
    /** The step a running install is on; null before the job names one. */
    val step: PhoneAgentInstallStep? = null,
    /** Measured bytes of the running step (a download), when the job counts them. */
    val doneBytes: Long? = null,
    val totalBytes: Long? = null,
)

/** The version an agent's download [found] (null when it never ran) and the one this app [expected]. */
public data class PhoneAgentVersions(val found: String?, val expected: String)

/** The steps of a phone agent install, in order (AgentInstaller.components); the words live in the screen. */
public enum class PhoneAgentInstallStep {
    /** The phone's Linux and its shared packages (base, essentials, Python, the agent user). */
    Linux,
    /** Node.js, which every phone agent runs on. */
    Runtime,
    /** The agent helper (Paseo) the app talks to. */
    Helper,
    /** The agent itself. */
    Agent,
}

/** The step a setup job's component belongs to ([componentId] as AgentInstaller names them); null when unknown. */
public fun phoneInstallStep(componentId: String?, agentId: String): PhoneAgentInstallStep? =
    when (componentId) {
        null -> null
        AgentInstaller.LINUX,
        AgentInstaller.ESSENTIALS,
        AgentInstaller.PYTHON,
        AgentInstaller.AGENT_USER -> PhoneAgentInstallStep.Linux
        AgentInstaller.AGENT_NODE -> PhoneAgentInstallStep.Runtime
        AgentInstaller.AGENT_PASEO -> PhoneAgentInstallStep.Helper
        "agent-$agentId" -> PhoneAgentInstallStep.Agent
        else -> null
    }

/**
 * True when an agent the phone's setup installed is present: its install already brought the shared parts (the Linux
 * packages, Python, Node.js, the agent helper), so a next install adds only its own agent. A row still being read
 * (Checking) or unavailable proves nothing, so the shared parts are then counted (the figure errs toward warning).
 */
public fun phoneSharedPartsInPlace(agents: List<PhoneAgentRow>): Boolean =
    agents.any {
        when (it.status) {
            PhoneAgentStatus.Ready,
            PhoneAgentStatus.SignInNeeded,
            PhoneAgentStatus.PhoneCheck,
            PhoneAgentStatus.Stopped,
            PhoneAgentStatus.Limit -> true
            PhoneAgentStatus.NotInstalled,
            PhoneAgentStatus.Checking,
            PhoneAgentStatus.Unavailable -> false
        }
    }

/** Free space for an install: what it needs (with any shared parts still to add) and what this phone has. */
public data class PhoneAgentSpace(val neededBytes: Long, val freeBytes: Long) {
    public val short: Boolean
        get() = freeBytes < neededBytes
}

public enum class PhoneAgentInstallFailure {
    Download,
    Checksum,
    Storage,
    Interrupted,
    /** The download named another version than the one this app installs; trying again does not change it. */
    Version,
    Unknown,
    /** The download did not answer its version check (too slow, crashed, or printed none); trying again may help. */
    NotStarted,
    /** The pinned version is no longer offered for download; trying again does not change it. */
    Withdrawn,
}

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

/**
 * Starts this phone's agent helper and connects its conversations. The Agents page calls it on its own once an agent is
 * installed (never as a button, never while none is). Platform bindings must use native owner admission.
 */
public fun interface PhoneAgentHostStarter {
    public suspend fun connect(): Outcome<Unit>
}

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface PhoneAgentHostBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneAgentHostStarters: Set<PhoneAgentHostStarter>

    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneAgentCheckers: Set<PhoneAgentChecker>
}

/**
 * The phone check for one agent (Flutter `runAgentPhoneCheck`): installs or refreshes what the agent needs, starts the
 * helper, asks it to answer, and stores the proof. Answers `null` when the agent passed, otherwise the one reason it
 * did not (each has a sentence in the screen). It reports no progress of its own: an install it starts shows on the
 * agent's tile like any install.
 */
public fun interface PhoneAgentChecker {
    public suspend fun check(id: String): Outcome<io.github.eslamasabry.oc.engine.agents.check.PhoneCheckFailure?>
}
