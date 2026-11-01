package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.host.runtimeProblem
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What one loopback probe of the phone server answered (user `opencode`, our password). A pid is never evidence. */
public enum class ProbeAnswer {
    /** Answered with our password. */
    Answered,

    /** Answered, but rejected our password: it never starts accepting it. */
    NeedsPassword,

    /** No answer. */
    NoAnswer,
}

/** How the server is launched (plan 11 §6.2 step 4). */
public sealed interface LaunchMode {
    /** A person's Start, setup, or healing: `server.start(…, recipe)`. */
    public data object Manual : LaunchMode

    /** The app-open start: refused natively if a Stop moved the generation first (`server.startForLaunch`). */
    public data class ForAppOpen(val expectedGeneration: Long) : LaunchMode

    /** Automatic recovery: `server.restart(…, expectedGeneration)`, confirmed with `recovery.confirm`. */
    public data class Recovery(val expectedGeneration: Long) : LaunchMode

    /** The one restart after an OpenCode component update (R7): `server.restart(…, expectedGeneration)`, no budget. */
    public data class AfterUpdate(val expectedGeneration: Long) : LaunchMode
}

/**
 * What the one start path needs from the phone (plan 11 §6.2). The Android binding implements it over `RuntimeHost`
 * (guest.run with the rendered password and history scripts, server.start/startForLaunch/restart with the
 * `RestoreRecipe(version = 1, profileId, runtime = "openCode2")`, refreshStatus, recovery.confirm) and the loopback
 * probe. The password goes only into [writePassword]: never a command line, never a log.
 */
public interface PhoneServerPort {
    /** The OpenCode 1 history move admission check (only for a profile that still needs the move). */
    public suspend fun checkHistoryMove(): Outcome<Unit>

    /** `umask 077`, write `server.password.tmp`, `mv` to `/root/.oc-builtin/server.password` (60 s). */
    public suspend fun writePassword(password: String): Outcome<Unit>

    public suspend fun launch(profile: ProfileId, mode: LaunchMode): Outcome<Unit>

    public suspend fun probe(password: String): ProbeAnswer

    public suspend fun refreshStatus(): Outcome<RuntimeStatus>

    public suspend fun confirmRecovery(expectedGeneration: Long): Outcome<Unit>
}

/** The profile facts a start needs. [password] is empty when the Keystore lost it. */
public data class StartTarget(
    val profile: ProfileId,
    val password: String,
    val requiresOpenCode2Migration: Boolean = false,
    val passwordNeedsReentry: Boolean = false,
) {
    override fun toString(): String = "StartTarget(profile=${profile.raw}, password=<hidden>)"
}

/** What kind of thing stopped a start (the same 8 values as Flutter's `BuiltinStartProblem`, plan 11 §5.2). */
public enum class StartProblem {
    /** OpenCode's process ended before it answered. */
    Exited,

    /** OpenCode kept running but did not answer in time. */
    TimedOut,

    /** The start was withdrawn (the app left the screen, a Stop, a policy change). */
    Interrupted,

    /** The saved server password is missing: setup has to make a new one. */
    PasswordMissing,

    /** The password file inside Ubuntu could not be written. */
    PasswordNotSaved,

    /** The phone did not let the app start OpenCode. */
    Refused,

    /** Android process ownership could not be proven; restarting the app is required. */
    ComponentRecovery,

    /** Another start is already running. */
    Busy,
}

/** The step that failed, for the Details fold (never a script or output). */
public enum class StartStep { Password, Start, Probe, Status, Confirm }

/** Why the start was interrupted, so the words can say which (a Stop, or the OpenCode 2 move). */
public enum class InterruptReason { NotConfirmed, NeedsOpenCode2Move, HistoryMoveRefused }

/**
 * Why a start did not end with the server answering our password. [details] is the phone's own Details-only text,
 * already redacted; [step] and [problem] name what failed. `:ui:words` maps [problem] to one sentence and one fix.
 */
public data class StartFailure(
    val problem: StartProblem,
    val step: StartStep? = null,
    val timeout: Duration? = null,
    val interrupt: InterruptReason? = null,
    val details: Redacted? = null,
) {
    /**
     * A fast failure one more start can fix. A timeout already waited long, a withdrawn start was not wanted, and a
     * missing password needs setup, so none of those is tried again on its own.
     */
    val retryable: Boolean
        get() = problem == StartProblem.Exited || problem == StartProblem.PasswordNotSaved || problem == StartProblem.Refused

    public companion object {
        public fun interrupted(reason: InterruptReason = InterruptReason.NotConfirmed): StartFailure =
            StartFailure(StartProblem.Interrupted, interrupt = reason)
    }
}

/**
 * The one start path (port of builtin_server.dart `startBuiltinServer`; plan 11 §6.2, BUILTIN-LINUX-15): setup, the
 * opening card, healing and recovery all use it, so they can never start the server differently. Writes the password
 * file, launches the server and probes every [pollInterval] until it answers, up to [readyTimeout]; each miss reads
 * the status, and a server no longer running is [StartProblem.Exited]. [stillWanted] is asked before every step that
 * acts: a Stop or a withdrawn screen ends the start as [StartProblem.Interrupted], never as the phone saying no.
 *
 * A lost Keystore password is replaced through [renewPassword] (32 random bytes, base64url, saved under `pw.<id>`)
 * before the write, and the start continues (B-LINUX-025). A start that ends answering clears the person's Stop mark.
 */
public class PhoneServerStarter(
    private val port: PhoneServerPort,
    private val clock: Clock,
    public val readyTimeout: Duration = 90.seconds,
    public val pollInterval: Duration = 2.seconds,
    private val renewPassword: (suspend (ProfileId) -> String?)? = null,
    /** Cleared after every start that ended with the server answering (plan 11 §6.2 step 6). */
    private val deliberateStop: DeliberateStop? = null,
) {
    public suspend fun start(
        target: StartTarget,
        mode: LaunchMode = LaunchMode.Manual,
        migrateLegacyHistory: Boolean = false,
        stillWanted: () -> Boolean = { true },
    ): StartFailure? {
        val cancelled = StartFailure.interrupted()
        if (!stillWanted()) return cancelled
        if (target.requiresOpenCode2Migration && !migrateLegacyHistory) {
            return StartFailure.interrupted(InterruptReason.NeedsOpenCode2Move)
        }
        var password = target.password
        if ((password.isEmpty() || target.passwordNeedsReentry) && renewPassword != null) {
            password = renewPassword.invoke(target.profile).orEmpty()
        }
        if (password.isEmpty()) return StartFailure(StartProblem.PasswordMissing, StartStep.Password)

        if (target.requiresOpenCode2Migration && port.checkHistoryMove() !is Outcome.Ok) {
            return StartFailure.interrupted(InterruptReason.HistoryMoveRefused)
        }
        when (val written = port.writePassword(password)) {
            is Outcome.Failed ->
                return if (!stillWanted()) cancelled else StartFailure(StartProblem.PasswordNotSaved, StartStep.Password, details = written.failure.details)
            is Outcome.Ok -> Unit
        }
        if (!stillWanted()) return cancelled
        when (val launched = port.launch(target.profile, mode)) {
            is Outcome.Failed -> return if (!stillWanted()) cancelled else refusal(launched, StartStep.Start)
            is Outcome.Ok -> Unit
        }
        val deadline = clock.now() + readyTimeout
        while (stillWanted()) {
            val answer = port.probe(password)
            if (!stillWanted()) return cancelled
            if (answer == ProbeAnswer.Answered) {
                if (mode is LaunchMode.Recovery) {
                    val confirmed = port.confirmRecovery(mode.expectedGeneration)
                    if (!stillWanted()) return cancelled
                    if (confirmed is Outcome.Failed) return refusal(confirmed, StartStep.Confirm)
                }
                deliberateStop?.clear(target.profile)
                return null
            }
            when (val status = port.refreshStatus()) {
                is Outcome.Failed -> return if (!stillWanted()) cancelled else refusal(status, StartStep.Status)
                is Outcome.Ok -> if (!status.value.serverRunning) return StartFailure(StartProblem.Exited, StartStep.Status)
            }
            if (clock.now() >= deadline) return StartFailure(StartProblem.TimedOut, StartStep.Probe, timeout = readyTimeout)
            delay(pollInterval)
        }
        return cancelled
    }

    private fun refusal(call: Outcome.Failed, step: StartStep): StartFailure =
        StartFailure(
            problem = if (call.runtimeProblem == RuntimeProblem.OwnershipUnknown) StartProblem.ComponentRecovery else StartProblem.Refused,
            step = step,
            details = call.failure.details,
        )
}
