package io.github.eslamasabry.oc.android.agenthost

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The phone agents' host as Outcome calls (plan 17 §1.2 `AgentHost`, Android side): what
 * `MainActivity.handleBuiltinLinux` did for the agent methods, without a channel. Every blocking
 * call runs on [AppDispatchers.io]; nothing here touches the main thread. Failures are flat
 * `Failure` values ([AgentHostCode] keeps the native code as the [WireTag]); no exception text and
 * no guest output ever leaves (only an exception's class name, under Details).
 *
 * The engine's `AgentHost` interface (`:engine:agents`) is satisfied by a thin adapter over this
 * class once that module compiles on the flat core contract (S3-AGENTHOST report, open problem P1);
 * the method set is the same.
 */
class AndroidAgentHost(
    private val processes: AgentProcesses,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val health: HelperHealth = HelperHealth.Loopback,
) {
    private val host
        get() = processes.agentHost

    private val state = MutableStateFlow(HelperStatus.Stopped)

    /** == PlatformCapabilities.builtinLinux. */
    val available: Boolean
        get() = processes.installed

    /** The last status read of the helper, pushed after every call that can change it. */
    val status: StateFlow<HelperStatus> = state.asStateFlow()

    /**
     * Starts [owner]'s helper unless it already runs the expected version, then waits until `GET
     * /api/health` answers (≤ 30 s, every 500 ms). The start holds a lifecycle ticket bound to the
     * native activity epoch, wanted revision and (for an idle resume) the idle generation, so a
     * stale screen cannot start a helper (`captureAgentHostStart`).
     */
    suspend fun ensureRunning(owner: String, start: HelperStartRequest): Outcome<HelperRunning> {
        val begun = native {
            val ticket =
                processes.captureAgentHostStart(
                    owner,
                    start.idleResumeGeneration != null,
                    start.idleResumeGeneration,
                ) ?: throw IdleResumeStale()
            processes.withAgentHostStart(ticket) {
                host.start(
                    PhoneAgentHost.StartRequest(
                        owner,
                        start.password.value,
                        start.port,
                        start.config,
                        PhoneAgentHost.HelperPolicy(
                            start.expectedHelperVersion,
                            start.restartIfVersionChanged,
                            start.forceRestart,
                        ),
                    )
                )
            }
        }
        val map =
            when (begun) {
                is Outcome.Failed -> return begun.also { refresh(owner) }
                is Outcome.Ok -> begun.value
            }
        val running = HelperStatus.from(owner, map)
        state.value = running
        if (!running.running) return AgentHostCode.AgentUnavailable.failure().failed()
        val deadline = clock.now() + HEALTH_BUDGET
        while (true) {
            if (withContext(dispatchers.io) { health.answers(start.port) }) break
            if (clock.now() >= deadline) return AgentHostCode.AgentHostExited.failure().failed()
            delay(HEALTH_PAUSE)
        }
        return HelperRunning(
                start.port,
                running.helperVersion ?: start.expectedHelperVersion,
                running.abi,
                startedFresh = true,
            )
            .ok()
    }

    /**
     * [guardedForUpdate] refuses (busy / unowned) while another agent process of the phone runs.
     */
    suspend fun stop(owner: String, guardedForUpdate: Boolean): Outcome<Unit> = native {
        host.stop(owner, guardedForUpdate)
    }.also { refresh(owner) }.unit()

    suspend fun prepareUpdate(owner: String): Outcome<Unit> = native {
        host.prepareUpdate(owner)
    }.unit()

    /**
     * Profile deletion and the last alias leaving: blocks the profile, drains helper, auth and
     * sign-in processes by exact identity, then erases `/home/oc/.oc-profiles/<owner>`. A failed
     * drain keeps everything.
     */
    suspend fun deleteHome(owner: String): Outcome<Unit> = native {
        host.delete(owner)
    }.also { refresh(owner) }.unit()

    /** The strict inventory of one agent program (`agentHostVersion`). */
    suspend fun installedVersion(
        owner: String,
        executable: String,
        expected: String,
    ): Outcome<InstalledProgram> = native {
        host.checks.version(owner, executable, expected)
    }
        .let { read ->
            when (read) {
                is Outcome.Failed -> read
                is Outcome.Ok ->
                    InstalledProgram(
                            installed = read.value["installed"] == true,
                            foundVersion = read.value["foundVersion"] as? String,
                            payloadPresent = read.value["payloadPresent"] as? Boolean,
                        )
                        .ok()
            }
        }

    /** `/root/projects` readable and writable for `oc` (`agentHostWorkspace`). */
    suspend fun workspaceCheck(owner: String): Outcome<Boolean> = native {
        host.checks.workspace(owner)
    }
        .let { read ->
            when (read) {
                is Outcome.Failed -> read
                is Outcome.Ok -> (read.value["shared"] == true).ok()
            }
        }

    /**
     * The private auth probe or sign-out. [script] is the app-authored probe
     * (`AgentPhoneScripts.authProbe` / `signOut`); the native side admits only that exact script
     * for this profile and agent. The answer is the narrow projection: a state, an optional account
     * label, or one fixed error.
     */
    suspend fun auth(
        owner: String,
        agentId: String,
        action: AuthRequestAction,
        script: String,
    ): Outcome<AuthAnswer> = native {
        processes.agentAuthProbe(
            mapOf(
                "profileId" to owner,
                "agentId" to agentId,
                "action" to action.wire,
                "script" to script,
                "timeoutSeconds" to action.timeoutSeconds(agentId),
            )
        )
    }
        .let { read ->
            when (read) {
                is Outcome.Failed -> read
                is Outcome.Ok -> AuthAnswer.from(read.value).ok()
            }
        }

    /**
     * The guest command for the sign-in terminal and the file its stand-in `xdg-open` writes the
     * page to.
     */
    suspend fun signInCommand(owner: String, program: List<String>): Outcome<SignInCommand> =
        native {
            processes.agentSignInCommand(owner, program)
        }
        .let { read ->
            when (read) {
                is Outcome.Failed -> read
                is Outcome.Ok -> SignInCommand(read.value.first, read.value.second).ok()
            }
        }

    /** Read-only status of the retired sign-in relay (AGENTS-BACKENDS-17 keeps only this read). */
    suspend fun legacySignInStatus(owner: String, agentId: String, runId: String): Outcome<String> =
        native {
            processes.agentSignIn.status(owner, agentId, runId, "browserOAuthHost")
        }
        .let { read ->
            when (read) {
                is Outcome.Failed -> read
                is Outcome.Ok -> ((read.value["phase"] as? String) ?: "failed").ok()
            }
        }

    /**
     * The helper's diagnostics record (exits, uptime, restarts) for the merged helper-exit list.
     */
    suspend fun helperDiagnostics(owner: String): Outcome<Map<String, Any?>> = native {
        processes.privateAgentDiagnostics("agent-host.$owner")
    }

    private suspend fun refresh(owner: String) {
        val read = native { host.status(owner) }
        if (read is Outcome.Ok) state.value = HelperStatus.from(owner, read.value)
    }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) { attempt({ classify(it) }) { block() } }

    private fun Outcome<*>.unit(): Outcome<Unit> =
        when (this) {
            is Outcome.Failed -> this
            is Outcome.Ok -> Unit.ok()
        }

    private class IdleResumeStale : IllegalStateException("idle_resume_stale")

    companion object {
        val HEALTH_BUDGET: Duration = 30.seconds
        val HEALTH_PAUSE: Duration = 500.milliseconds

        /**
         * What `MainActivity.handleBuiltinLinux` answered for an agent method, as a flat Failure:
         * the host's own codes keep their tag, a refused lifecycle ticket is `idle_resume_stale`,
         * the launch space guard is storage, and anything else is the agent being unavailable. Only
         * the exception's class name goes under Details.
         */
        fun classify(error: Throwable): Failure {
            val code = PhoneAgentHost.failureCode(error)?.let(AgentHostCode::fromCode)
            val details = Redacted.shape(error)
            return when {
                code != null -> code.failure(details)
                error is IdleResumeStale || error.message == "idle_resume_stale" ->
                    Failure.Runtime(RuntimeProblem.IdleResumeStale, details)
                error.message?.contains("not enough free space") == true ->
                    Failure.Runtime(RuntimeProblem.StorageUnavailable, details)
                else -> AgentHostCode.AgentUnavailable.failure(details)
            }
        }
    }
}

/**
 * The native agent-host codes (plan 17 CR17-1 appends them to `RuntimeProblem`). Until that lands
 * they travel on the flat Failure leaves below with the code as the [WireTag], the same encoding
 * `:engine:agents` decodes.
 */
enum class AgentHostCode(val code: String) {
    AgentUnavailable("agent_unavailable"),
    HelperUpdateRequired("helper_update_required"),
    AgentHostBusy("agent_host_busy"),
    AgentHostUnowned("agent_host_unowned"),
    AgentHostExited("agent_host_exited");

    fun failure(details: Redacted? = null): Failure =
        when (this) {
            AgentUnavailable -> Failure.Unavailable(FeatureId("builtinLinux"), details)
            HelperUpdateRequired -> Failure.Busy(BusyKind.Starting, WireTag(code), details)
            AgentHostBusy -> Failure.Busy(BusyKind.Overloaded, WireTag(code), details)
            AgentHostUnowned -> Failure.Conflict(WireTag(code), details = details)
            AgentHostExited -> Failure.Busy(BusyKind.Failed, WireTag(code), details)
        }

    companion object {
        fun fromCode(code: String): AgentHostCode? = entries.firstOrNull { it.code == code }

        /** The code a failure carries, or null. */
        fun of(failure: Failure): AgentHostCode? =
            when (failure) {
                is Failure.Unavailable ->
                    if (failure.feature.name == "builtinLinux") AgentUnavailable else null
                is Failure.Busy -> failure.tag?.let { fromCode(it.raw) }
                is Failure.Conflict -> failure.tag?.let { fromCode(it.raw) }
                else -> null
            }
    }
}

/** The helper password: in memory only, written to the child's stdin; never printed. */
class HelperPassword(val value: String) {
    override fun toString(): String = "•••"
}

data class HelperStartRequest(
    val password: HelperPassword,
    /** Paseo `config.json`, rendered by the agents engine (`PaseoConfig.render`). */
    val config: String,
    /** "0.11.2" from the pins resource. */
    val expectedHelperVersion: String,
    val restartIfVersionChanged: Boolean,
    val forceRestart: Boolean = false,
    /** The idle generation of `RuntimeHost.idle.resume`, or null for an ordinary start. */
    val idleResumeGeneration: Long? = null,
    /**
     * 4099 stable, 4199 preview (`AppBuildInfo.ports.agentHost`, S11-6). The kept host admits only
     * 4099 until seam E8.
     */
    val port: Int = 4099,
) {
    override fun toString(): String =
        "HelperStartRequest(helper=$expectedHelperVersion, port=$port)"
}

data class HelperStatus(
    val running: Boolean,
    val abi: String?,
    val helperVersion: String?,
    val owner: String?,
) {
    companion object {
        val Stopped = HelperStatus(false, null, null, null)

        internal fun from(owner: String, map: Map<String, Any?>): HelperStatus {
            val running = map["running"] == true
            return HelperStatus(
                running,
                (map["abi"] as? String)?.takeIf { it.isNotEmpty() },
                map["helperVersion"] as? String,
                owner.takeIf { running },
            )
        }
    }
}

data class HelperRunning(
    val port: Int,
    val helperVersion: String,
    val abi: String?,
    val startedFresh: Boolean,
)

/**
 * A program as the strict inventory sees it; [payloadPresent] null = the inventory would not follow
 * a link.
 */
data class InstalledProgram(
    val installed: Boolean,
    val foundVersion: String?,
    val payloadPresent: Boolean?,
)

enum class AuthRequestAction(val wire: String) {
    Probe("probe"),
    SignOut("logout");

    /** Claude's probe 65 s (cold start under PRoot), other probes 10 s, sign-out 20 s. */
    fun timeoutSeconds(agentId: String): Int =
        when (this) {
            Probe -> if (agentId == "claude") 65 else 10
            SignOut -> 20
        }
}

/**
 * The narrow projection: `signedIn` / `signedOut` / `error` with a fixed code, plus a transient
 * account label.
 */
data class AuthAnswer(val state: String, val account: AccountLabel?, val error: String?) {
    companion object {
        internal fun from(map: Map<String, Any?>): AuthAnswer =
            AuthAnswer(
                state = map["state"] as? String ?: "error",
                account = (map["accountDisplayName"] as? String)?.let(::AccountLabel),
                error = map["error"] as? String,
            )
    }
}

/** Shown, never logged (16 SI-2). */
class AccountLabel(val value: String) {
    override fun toString(): String = "•••"

    override fun equals(other: Any?): Boolean = other is AccountLabel && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/** The proot argv of the sign-in terminal and the file the page to open appears in. */
data class SignInCommand(val argv: List<String>, val openUrlFile: File)

/** `GET /api/health` on the helper's loopback port (no password). */
fun interface HelperHealth {
    suspend fun answers(port: Int): Boolean

    companion object {
        val Loopback = HelperHealth { port -> PaseoHealth.answers(port) }
    }
}
