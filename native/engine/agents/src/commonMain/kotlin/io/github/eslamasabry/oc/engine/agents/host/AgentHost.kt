package io.github.eslamasabry.oc.engine.agents.host

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * The on-phone agent helper (Paseo daemon, uid 1000 `oc`) and the agent CLIs it drives (plan 17 §1.2, verbatim
 * shape). Every call is keyed by the OWNER profile ([io.github.eslamasabry.oc.engine.agents.ownership.AgentOwnership]),
 * never by an alias. No call takes or returns a raw string from a guest process except under Details.
 *
 * Android binds `AndroidAgentHost` (`:android:agenthost`, S3-AGENTHOST); desktop and iOS bind [UnavailableAgentHost].
 * Errors: until CR17-1 lands `Failure.Runtime(RuntimeProblem)`, host problems travel as the existing leaves listed in
 * [AgentHostProblem.asFailure] and come back with [agentHostProblem] (lossless: the channel code is the tag).
 */
public interface AgentHost {
    /** == PlatformCapabilities.builtinLinux. */
    public val available: Boolean

    /** Pushed on every native change. */
    public val status: StateFlow<AgentHostStatus>

    /**
     * Starts the helper when it is not running and returns once `GET /api/health` answers (≤ 30 s). Never restarts a
     * helper that already runs the expected version (starting replaces a running one). Admission (kept Kotlin): 64-hex
     * password, one start at a time, requireNoOtherOwner, MIN_LAUNCH_BYTES, HelperPolicy, requireAgentHostUpdateIdle,
     * lifecycle ticket bound to the native idle generation.
     */
    public suspend fun ensureRunning(owner: OwnerId, start: HelperStart): Outcome<HelperRunning>

    /** No I/O on the caller; [guardedForUpdate] refuses while a conversation or a permission reply is in flight. */
    public suspend fun stop(owner: OwnerId, guardedForUpdate: Boolean): Outcome<Unit>

    public suspend fun prepareUpdate(owner: OwnerId): Outcome<Unit>

    /**
     * Profile deletion and the last alias leaving: blocks the profile, drains helper + auth + sign-in processes by exact
     * identity, then erases `/home/oc/.oc-profiles/<owner>`. Idempotent; a failed drain keeps everything.
     */
    public suspend fun deleteHome(owner: OwnerId): Outcome<Unit>

    public suspend fun installedVersion(agent: AgentId): Outcome<InstalledAgent?>

    /** `/root/projects` readable and writable for `oc`. */
    public suspend fun workspaceCheck(owner: OwnerId): Outcome<WorkspaceAccess>

    public suspend fun auth(owner: OwnerId, agent: AgentId, action: AuthAction, timeout: Duration): Outcome<AuthProjection>

    /**
     * The guest command for the sign-in terminal (allowlisted program, ≤ 5 args of `[A-Za-z0-9-]{1,32}`, private HOME,
     * stand-in xdg-open/open-url writing `<home>/.oc-open-url`). `TerminalHost` runs it (17 §2.6).
     */
    public fun signInCommand(owner: OwnerId, agent: AgentId, args: List<String>): Outcome<GuestCommand>

    /** Read-only status of the retired relay (AGENTS-BACKENDS-17 keeps only this read). */
    public suspend fun legacySignInStatus(owner: OwnerId, agent: AgentId): Outcome<LegacySignInStatus>

    /** Exits of the helper, merged for diagnostics. */
    public suspend fun helperExits(limit: Int = 10): Outcome<List<AgentHelperExit>>
}

/** A credential held in memory only. `toString()` never prints it; it is written to the child's stdin only. */
public class Secret(public val value: String) {
    override fun toString(): String = "•••"

    override fun equals(other: Any?): Boolean = other is Secret && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/** An account label: shown, never logged (16 SI-2). */
@JvmInline
public value class TransientLabel(public val value: String) {
    override fun toString(): String = "•••"
}

/** `config.json` for the helper, rendered by the engine ([io.github.eslamasabry.oc.engine.agents.host.PaseoConfig]). */
public class PaseoConfigFile(public val json: String) {
    override fun toString(): String = "PaseoConfigFile(${json.length} chars)"
}

public data class IdleResume(val expectedIdleGeneration: Long)

public data class HelperStart(
    val password: Secret,
    val config: PaseoConfigFile,
    /** "0.11.2" from the pins resource. */
    val expectedHelperVersion: String,
    val restartIfVersionChanged: Boolean,
    val forceRestart: Boolean = false,
    val idleResume: IdleResume? = null,
)

public enum class Abi(public val wire: String) { Arm64V8a("arm64-v8a"), X86_64("x86_64") }

public data class HelperRunning(val port: Int, val helperVersion: String, val abi: Abi, val startedFresh: Boolean)

/** AGENTS-BACKENDS-11 idle fields; null = the native side did not report them (legacy build). */
public data class PhoneAgentIdleState(
    val serverIdlePolicySupported: Boolean?,
    val serverIdleStopped: Boolean?,
    val serverIdleHelperStopped: Boolean?,
    val serverIdleGeneration: Long?,
    val serverRestartWanted: Boolean?,
    val serverRunning: Boolean?,
) {
    /**
     * Whether an automatic helper start is allowed: always when the idle policy is unsupported or the fields are absent
     * (legacy build); when supported, only if not idle-stopped, the helper is not stopped, generation is 0, a server
     * restart is wanted and the server runs. Any missing field under a supported policy denies.
     */
    public val automaticStartAllowed: Boolean
        get() = when (serverIdlePolicySupported) {
            null, false -> true
            true ->
                serverIdleStopped == false && serverIdleHelperStopped == false && serverIdleGeneration == 0L &&
                    serverRestartWanted == true && serverRunning == true
        }
}

public data class AgentHostStatus(
    val running: Boolean,
    val abi: Abi?,
    val helperVersion: String?,
    val owner: OwnerId?,
    val idle: PhoneAgentIdleState?,
) {
    public companion object {
        public val Stopped: AgentHostStatus = AgentHostStatus(false, null, null, null, null)
    }
}

/**
 * An agent as the strict inventory reports it. [version] is the version that runs (an older version that runs still
 * counts as installed); null with [payloadPresent] is a partial payload that stays removable after a restart.
 */
public data class InstalledAgent(val version: String?, val payloadPresent: Boolean = true) {
    public val installed: Boolean get() = version != null
}

public data class WorkspaceAccess(val readable: Boolean, val writable: Boolean) {
    public val usable: Boolean get() = readable && writable
}

public enum class AuthAction { Probe, SignOut }

public enum class AuthState { SignedIn, SignedOut, Error }

public enum class AuthProbeError {
    ProbeUnsupported, InvalidResponse, TimedOut, HostUnavailable, NotInstalled, InvalidContext, SignInExpired,
    SignOutFailed,
}

/** The narrow projection the kept probe returns (≤ 2 KB): state + optional transient account label. */
public data class AuthProjection(val state: AuthState, val account: TransientLabel? = null, val error: AuthProbeError? = null)

/** A program + argv + env to run inside the guest. Arguments are data, never shell. */
public data class GuestCommand(
    val program: String,
    val args: ImmutableList<String>,
    val env: ImmutableMap<String, String>,
    val home: String,
) {
    override fun toString(): String = "GuestCommand($program, ${args.size} args)"
}

/** The retired relay's read (`agentSignInStatus`); start/submit/cancel are not ported (NG8). */
public enum class LegacySignInStatus { SignedIn, SignedOut, LimitReached, Unknown }

public enum class HelperExitKind { Unexpected, LimitOrMemory, Requested }

/** One observed helper exit; no profile data. [exitCode] is the process code when the native side saw one. */
public data class AgentHelperExit(val atEpochMillis: Long, val kind: HelperExitKind, val exitCode: Long? = null)

/** The native agent-host error codes (11 §5.5 RuntimeProblem values appended by CR17-1). */
public enum class AgentHostProblem(public val code: String) {
    AgentUnavailable("agent_unavailable"),
    HelperUpdateRequired("helper_update_required"),
    AgentHostBusy("agent_host_busy"),
    AgentHostUnowned("agent_host_unowned"),
    AgentHostExited("agent_host_exited"),
    ;

    /** Interim encoding on today's Failure tree (no Failure.Runtime yet). The code is kept as the WireTag. */
    public fun asFailure(): Failure =
        when (this) {
            AgentUnavailable -> Failure.Unavailable(FeatureId("builtinLinux"))
            HelperUpdateRequired -> Failure.Busy(BusyKind.Starting, WireTag(code))
            AgentHostBusy -> Failure.Busy(BusyKind.Overloaded, WireTag(code))
            AgentHostUnowned -> Failure.Conflict(WireTag(code))
            AgentHostExited -> Failure.Busy(BusyKind.Failed, WireTag(code))
        }

    public companion object {
        public fun fromCode(code: String): AgentHostProblem? = entries.firstOrNull { it.code == code }
    }
}

/** The [AgentHostProblem] a failure carries, or null. */
public fun Failure.agentHostProblem(): AgentHostProblem? =
    when (this) {
        is Failure.Unavailable -> if (feature.name == "builtinLinux") AgentHostProblem.AgentUnavailable else null
        is Failure.Busy -> tag?.let { AgentHostProblem.fromCode(it.raw) }
        is Failure.Conflict -> tag?.let { AgentHostProblem.fromCode(it.raw) }
        else -> null
    }

/** Desktop and iOS: no built-in Linux, so every call is AgentUnavailable and the status never runs. */
public class UnavailableAgentHost : AgentHost {
    private val unavailable = AgentHostProblem.AgentUnavailable.asFailure()
    private val stopped = MutableStateFlow(AgentHostStatus.Stopped)

    override val available: Boolean get() = false
    override val status: StateFlow<AgentHostStatus> = stopped.asStateFlow()

    override suspend fun ensureRunning(owner: OwnerId, start: HelperStart): Outcome<HelperRunning> = unavailable.failed()
    override suspend fun stop(owner: OwnerId, guardedForUpdate: Boolean): Outcome<Unit> = unavailable.failed()
    override suspend fun prepareUpdate(owner: OwnerId): Outcome<Unit> = unavailable.failed()
    override suspend fun deleteHome(owner: OwnerId): Outcome<Unit> = Outcome.Ok(Unit) // nothing to erase off-phone
    override suspend fun installedVersion(agent: AgentId): Outcome<InstalledAgent?> = unavailable.failed()
    override suspend fun workspaceCheck(owner: OwnerId): Outcome<WorkspaceAccess> = unavailable.failed()
    override suspend fun auth(owner: OwnerId, agent: AgentId, action: AuthAction, timeout: Duration): Outcome<AuthProjection> =
        unavailable.failed()
    override fun signInCommand(owner: OwnerId, agent: AgentId, args: List<String>): Outcome<GuestCommand> = unavailable.failed()
    override suspend fun legacySignInStatus(owner: OwnerId, agent: AgentId): Outcome<LegacySignInStatus> = unavailable.failed()
    override suspend fun helperExits(limit: Int): Outcome<List<AgentHelperExit>> = unavailable.failed()
}
