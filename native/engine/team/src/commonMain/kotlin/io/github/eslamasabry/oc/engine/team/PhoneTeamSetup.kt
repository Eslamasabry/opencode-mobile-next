package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The four steps a person watches, in order (Dart `PhoneTeamSetupStep`). */
public enum class SetupStep { Reply, Stop, Check, Server }

public enum class SetupPhase {
    /** Nothing started (or the last run was dismissed). */
    Idle,
    Running,

    /** Waiting for the person's yes to stop OpenCode and the terminals. */
    Confirming,
    Failed,
    Done,
}

/** Why a run stopped, in the plain kinds the page has words for (Dart `PhoneTeamSetupProblem`). */
public enum class SetupProblem {
    /** This phone did not prove the team's copy of the code separate. */
    Unsafe,

    /** The engine did not start or did not answer. */
    Engine,

    /** OpenCode or a terminal did not stop. */
    StopFailed,

    /** OpenCode did not come back. */
    ServerStart,

    /** Everything started but the engine still says it cannot run work. */
    NotReady,

    /** The person said no to stopping OpenCode. */
    Declined,

    /** There is no OpenCode on this phone to protect. */
    NoServer,
}

/** What became of the person's OpenCode when a run ended without the team (Dart `PhoneTeamServerState`). */
public enum class SetupServerState { Untouched, BackOn, StillOff }

/** What the phone is running right now (Dart `PhoneTeamHostState`). */
public data class SetupHost(val serverRunning: Boolean, val terminals: Int, val restartRequired: Boolean) {
    public val needsStop: Boolean
        get() = serverRunning || terminals > 0 || restartRequired
}

/** What the engine says about the phone after a start or a probe (Dart `PhoneEngineHealth`, the parts the flow reads). */
public data class SetupHealth(
    val boundary: Boolean,
    val canExecute: Boolean,
    /** The engine found an OpenCode it cannot vouch for: it must stop first. */
    val restartRequired: Boolean = false,
    /** A safe code (never free text) naming where the proof stopped; empty when unknown. */
    val boundaryReason: String = "",
    val boundaryTier: String = "none",
)

/** The loopback address of the phone's own team engine and its bearer token (never logged). */
public class PhoneEngineAddress(public val baseUrl: String, public val token: String) {
    override fun toString(): String = "PhoneEngineAddress($baseUrl)"
}

/**
 * Everything the flow asks of the phone, so tests can stand in (Dart `PhoneTeamSetupPorts`). A failure carries no
 * text the person could read: the flow words it by the step it happened at.
 */
public interface PhoneSetupPorts {
    /** Whether this phone has a supported in-app OpenCode server to set up. */
    public suspend fun hasServer(): Boolean

    /** A chat reply is being written on this phone's OpenCode. */
    public suspend fun replyRunning(): Boolean

    public suspend fun inspect(): SetupHost

    public suspend fun stopServer(): Outcome<Unit>

    /** Closes the terminals: a terminal opened before protection cannot be vouched for. */
    public suspend fun closeTerminals(): Outcome<Unit>

    /** Starts the bundled engine, which proves the boundary. A proof that failed is an Ok health with `boundary` false. */
    public suspend fun startEngine(): Outcome<SetupHealth>

    /** Stops this run's engine before a retry; keeps saved projects and team settings. */
    public suspend fun stopEngine()

    public suspend fun probeEngine(): Outcome<SetupHealth>

    /** Starts OpenCode again, protected, and waits until it answers. */
    public suspend fun startServer(): Outcome<Unit>

    /** Keeps the engine's credential in the Keystore and the team config on the profile. */
    public suspend fun saveTeam(): Outcome<Unit>

    /** Puts OpenCode back after a run that stopped it and failed. True when OpenCode is on. */
    public suspend fun restoreServer(): Boolean
}

/** One run as the page draws it: every mark comes from a real signal (a start and an end time), never a timer guess. */
public data class SetupState(
    val phase: SetupPhase = SetupPhase.Idle,
    /** The step going now; null before the first and after the last. */
    val step: SetupStep? = null,
    val failedStep: SetupStep? = null,
    val problem: SetupProblem? = null,
    /** A safe technical code for Details; the page redacts it again. */
    val details: String? = null,
    val serverState: SetupServerState = SetupServerState.Untouched,
    val terminalsClosed: Boolean = false,
    /** The confinement the engine proved, once the run is done. */
    val boundaryTier: String? = null,
    val beganAtMs: Map<SetupStep, Long> = emptyMap(),
    val tookMs: Map<SetupStep, Long> = emptyMap(),
    val skipped: Set<SetupStep> = emptySet(),
) {
    public val running: Boolean
        get() = phase == SetupPhase.Running || phase == SetupPhase.Confirming

    /** Steps with a finished time, for the strip's "Step n of 4". */
    public val finished: Int
        get() = SetupStep.entries.count { it in tookMs }
}

/**
 * Flutter's one-tap "Turn on AI Team on this phone" (Dart `PhoneTeamSetupController`): the running reply finishes, the
 * unprotected OpenCode and the terminals stop only after the person says yes, the engine proves this phone safe,
 * OpenCode comes back protected and the team is kept. A failure after OpenCode was stopped brings it back and stops the
 * engine, so a failed run leaves the phone as it was. Once started a run is never cut short by a page closing: it
 * finishes or fails on its own, and a question nobody is left to answer counts as no.
 */
public class PhoneTeamSetup(
    private val ports: PhoneSetupPorts,
    private val clock: Clock,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val readyAttempts: Int = 15,
    private val readyGapMs: Long = 2_000,
    /** The longest the flow waits for a running reply (a stale "reply in flight" would otherwise hold it forever). */
    private val replyWaitMs: Long = 180_000,
    private val replyPollMs: Long = 2_000,
    /** The longest any one call to the phone may take before the run ends in a failure with a reason. */
    private val stepWaitMs: Long = 90_000,
    private val serverWaitMs: Long = 240_000,
) {
    private val mutable = MutableStateFlow(SetupState())
    public val state: StateFlow<SetupState> = mutable.asStateFlow()

    private val admit = Mutex()
    private var confirm: CompletableDeferred<Boolean>? = null
    private var serverStopped = false
    private var engineAttempted = false
    private var hostTerminals = 0

    /** The person's answer to the stop question. True stops OpenCode and closes the terminals; false ends the run. */
    public fun answer(yes: Boolean) {
        confirm?.complete(yes)
    }

    /** Forgets a finished or failed run so the page shows its start again. */
    public fun dismiss() {
        mutable.update { if (it.running) it else SetupState() }
    }

    /** Runs the whole flow; a second call while it runs returns at once (the page joins by watching [state]). */
    public suspend fun run() {
        if (!admit.tryLock()) return
        try {
            withContext(NonCancellable) { execute() }
        } finally {
            admit.unlock()
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun begin(step: SetupStep) =
        mutable.update { it.copy(step = step, beganAtMs = it.beganAtMs + (step to now())) }

    private fun end(step: SetupStep) =
        mutable.update { it.copy(tookMs = it.tookMs + (step to (now() - (it.beganAtMs[step] ?: now())))) }

    private fun skip(step: SetupStep) =
        mutable.update {
            it.copy(
                skipped = it.skipped + step,
                beganAtMs = it.beganAtMs + (step to now()),
                tookMs = it.tookMs + (step to 0L),
            )
        }

    /** One call to the phone, bounded; null when it never answered. */
    private suspend fun <T : Any> bounded(limitMs: Long = stepWaitMs, call: suspend () -> T): T? =
        withTimeoutOrNull(limitMs) { call() }

    private suspend fun execute() {
        mutable.value = SetupState(phase = SetupPhase.Running)
        serverStopped = false
        hostTerminals = 0
        // A failed or timed-out cleanup still owns its original engine: finish it before starting another.
        if (engineAttempted) {
            bounded { ports.stopEngine() }
            engineAttempted = false
        }
        if (!ports.hasServer()) return failWith(SetupProblem.NoServer, SetupStep.Reply, "noServerProfile")

        // a) A running reply is never cut short.
        begin(SetupStep.Reply)
        if (!replyEnded()) return failWith(SetupProblem.Engine, SetupStep.Reply, "timeout:replyStillRunning")
        end(SetupStep.Reply)

        // b) The unprotected OpenCode and terminals: only with a yes.
        val host = bounded { ports.inspect() } ?: return failWith(SetupProblem.Engine, SetupStep.Reply, "timeout:inspect")
        hostTerminals = host.terminals
        var stopped = false
        if (host.needsStop) {
            if (!stopForProtection()) return
            stopped = true
        } else {
            skip(SetupStep.Stop)
        }

        // c) The engine proves this phone safe.
        begin(SetupStep.Check)
        engineAttempted = true
        var health = startEngine() ?: return
        if (health.restartRequired && !stopped) {
            // The engine found an old OpenCode it cannot vouch for: ask now.
            mutable.update { it.copy(tookMs = it.tookMs - SetupStep.Stop, skipped = it.skipped - SetupStep.Stop) }
            if (!stopForProtection()) return
            begin(SetupStep.Check)
            health = startEngine() ?: return
        }
        if (!health.boundary) {
            return failWith(
                SetupProblem.Unsafe,
                SetupStep.Check,
                health.boundaryReason.ifEmpty { "boundary_unverified" },
            )
        }
        end(SetupStep.Check)

        // d) OpenCode back, protected; probe until the engine can run work.
        begin(SetupStep.Server)
        when (bounded(serverWaitMs) { ports.startServer() }) {
            null -> return failWith(SetupProblem.ServerStart, SetupStep.Server, "timeout:startServer")
            is Outcome.Failed -> return failWith(SetupProblem.ServerStart, SetupStep.Server, "serverStart")
            is Outcome.Ok -> Unit
        }
        var last: SetupHealth? = null
        for (attempt in 0 until readyAttempts) {
            last = (bounded { ports.probeEngine() } as? Outcome.Ok)?.value
            if (last?.canExecute == true) break
            if (attempt + 1 < readyAttempts) pause(readyGapMs)
        }
        val ready = last
        if (ready == null || !ready.canExecute) {
            return failWith(
                SetupProblem.NotReady,
                SetupStep.Server,
                if (ready == null) "engineUnavailable" else ready.boundaryReason.ifEmpty { "notExecutable" },
            )
        }
        when (bounded { ports.saveTeam() }) {
            null -> return failWith(SetupProblem.Engine, SetupStep.Server, "timeout:saveTeam")
            is Outcome.Failed -> return failWith(SetupProblem.Engine, SetupStep.Server, "saveFailed")
            is Outcome.Ok -> Unit
        }
        engineAttempted = false
        end(SetupStep.Server)
        mutable.update { it.copy(phase = SetupPhase.Done, step = null, boundaryTier = ready.boundaryTier) }
    }

    /** True once no reply is being written; false when one still was after [replyWaitMs]. */
    private suspend fun replyEnded(): Boolean {
        var waited = 0L
        while (ports.replyRunning()) {
            if (waited >= replyWaitMs) return false
            pause(replyPollMs)
            waited += replyPollMs
        }
        return true
    }

    /** Starts the engine; null after it ended the run in failure. */
    private suspend fun startEngine(): SetupHealth? =
        when (val started = bounded { ports.startEngine() }) {
            null -> failWith(SetupProblem.Engine, SetupStep.Check, "timeout:startEngine").let { null }
            is Outcome.Failed ->
                failWith(SetupProblem.Engine, SetupStep.Check, started.failure.details?.text ?: "engineFailed").let { null }
            is Outcome.Ok -> started.value
        }

    private suspend fun stopForProtection(): Boolean {
        val ask = CompletableDeferred<Boolean>()
        confirm = ask
        mutable.update { it.copy(phase = SetupPhase.Confirming) }
        begin(SetupStep.Stop)
        val yes = ask.await()
        confirm = null
        if (!yes) {
            failWith(SetupProblem.Declined, SetupStep.Stop, "declined")
            return false
        }
        // The wait for the person's answer is not work: the step's time starts at the yes.
        mutable.update { it.copy(phase = SetupPhase.Running, beganAtMs = it.beganAtMs + (SetupStep.Stop to now())) }
        serverStopped = true
        when (bounded { ports.stopServer() }) {
            null -> return stopFailed("timeout")
            is Outcome.Failed -> return stopFailed("stopFailed")
            is Outcome.Ok -> Unit
        }
        when (bounded { ports.closeTerminals() }) {
            null -> return stopFailed("timeout")
            is Outcome.Failed -> return stopFailed("stopFailed")
            is Outcome.Ok -> Unit
        }
        mutable.update { it.copy(terminalsClosed = hostTerminals > 0) }
        end(SetupStep.Stop)
        return true
    }

    private suspend fun stopFailed(code: String): Boolean {
        failWith(SetupProblem.StopFailed, SetupStep.Stop, code)
        return false
    }

    /**
     * Ends the run in failure, releasing the attempted engine before putting OpenCode back. A readiness refusal still
     * owns a live native daemon.
     */
    private suspend fun failWith(problem: SetupProblem, step: SetupStep, code: String) {
        var details = code
        if (engineAttempted) {
            if (bounded { ports.stopEngine() } == null) details = "$details; engineStopFailed" else engineAttempted = false
        }
        var server = SetupServerState.Untouched
        if (serverStopped) {
            server =
                if (problem == SetupProblem.ServerStart) {
                    // It just failed to start; say so instead of waiting for it again.
                    SetupServerState.StillOff
                } else if (bounded(serverWaitMs) { ports.restoreServer() } == true) {
                    SetupServerState.BackOn
                } else {
                    SetupServerState.StillOff
                }
        }
        mutable.update {
            it.copy(
                phase = SetupPhase.Failed,
                failedStep = step,
                problem = problem,
                details = details,
                serverState = server,
            )
        }
    }
}
