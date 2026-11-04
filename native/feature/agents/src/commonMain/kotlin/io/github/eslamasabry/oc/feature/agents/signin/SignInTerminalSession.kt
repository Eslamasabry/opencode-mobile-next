package io.github.eslamasabry.oc.feature.agents.signin

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Why a sign-in terminal stopped or never started. [code] is shown only under Details; words are
 * fixed.
 */
public sealed interface SignInProblem {
    public val code: String?

    /** "Allow notifications before signing in, then try again." */
    public data object NotificationsBlocked : SignInProblem {
        override val code: String?
            get() = null
    }

    /** Android refused the claim or it was not active: `sign-in-admission/<code>`. */
    public data class Admission(val admission: String) : SignInProblem {
        override val code: String
            get() = "sign-in-admission/$admission"
    }

    /** Protection was lost while the sign-in ran: `sign-in-lost/<reason>`. */
    public data class Lost(val reason: String) : SignInProblem {
        override val code: String
            get() = "sign-in-lost/$reason"
    }

    /** The native start failed (its text is never shown, B-SEC-098). */
    public data object Start : SignInProblem {
        override val code: String
            get() = "sign-in-start"
    }

    /**
     * The agent's command was refused before anything ran (not a phone agent, or not allowlisted).
     */
    public data object Unavailable : SignInProblem {
        override val code: String?
            get() = null
    }
}

public sealed interface SignInSessionState {
    public data object Idle : SignInSessionState

    /** Waiting for the foreground claim, then for the native start. */
    public data object Starting : SignInSessionState

    /**
     * The terminal shows; [exitCode] is set once the program ended (protection is still held for
     * the check).
     */
    public data class Running(val terminal: TerminalModel, val exitCode: Int? = null) :
        SignInSessionState

    public data class Failed(val problem: SignInProblem) : SignInSessionState

    /** Drained and released. */
    public data object Ended : SignInSessionState
}

/**
 * One sign-in terminal and its foreground claim (port of Dart
 * `LocalTerminalSessions.startSignIn/endSignIn`; plan 17 §1.5, plan 11 §9.1 `SignIn` row and
 * W11-08a; B-LINUX-094…096, B-SEC-098, B-AGENT-129).
 *
 * - The claim is reserved at once and the PTY starts only after Android admitted it **and** it is
 *   active; a refused or inactive admission never launches one (fixed codes, never CLI text).
 * - [end] is the one way out: cancelling during admission ends at once; during a pending native
 *   start it waits for the late session and drains exactly it; the claim is released only after the
 *   native removal succeeded. A failed drain keeps the claim and the session, and calling [end]
 *   again retries the same one. Concurrent ends share one drain.
 * - The program's exit keeps the claim through the account check, until [end].
 * - Loss of protection drains only this sign-in. Every URL handoff rechecks protection first; after
 *   [end] no URL is ever handed on.
 *
 * Not thread-safe by design: [scope] is the screen's single main-thread scope.
 */
public class SignInTerminalSession(
    private val scope: CoroutineScope,
    private val host: TerminalHost,
    private val foreground: SignInForeground,
) {
    private val mutableState = MutableStateFlow<SignInSessionState>(SignInSessionState.Idle)
    public val state: StateFlow<SignInSessionState> = mutableState.asStateFlow()

    private val urls = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * Pages the guest asked to open, already rechecked against protection. Open them only through
     * the link policy.
     */
    public val openUrls: Flow<String> = urls.asSharedFlow()

    private sealed interface Open {
        data object NotIssued : Open

        data object Pending : Open

        data class Done(val terminal: TerminalModel?) : Open
    }

    private sealed interface Drain {
        data object Idle : Drain

        data object Running : Drain

        data class Done(val outcome: Outcome<Unit>) : Drain
    }

    private val opening = MutableStateFlow<Open>(Open.NotIssued)
    private val drain = MutableStateFlow<Drain>(Drain.Idle)
    private var lease: SignInForegroundLease? = null
    private var released = false
    private var ending = false
    public val canOpenLinks: Boolean
        get() = !ending && lease?.active == true

    private var cancelStart: (() -> Unit)? = null
    private val stopWatching = mutableListOf<() -> Unit>()

    /** Starts [command] once; a session that started already ignores further starts. */
    public fun start(command: SignInCommand) {
        if (mutableState.value != SignInSessionState.Idle) return
        val claim = foreground.reserve()
        lease = claim
        mutableState.value = SignInSessionState.Starting
        val job = scope.launch { admitThenOpen(claim, command) }
        cancelStart = { job.cancel() }
    }

    private suspend fun admitThenOpen(claim: SignInForegroundLease, command: SignInCommand) {
        val admission = claim.admitted()
        if (ending) return
        val refused =
            when (admission) {
                SignInAdmission.Ready ->
                    if (claim.active) null else SignInProblem.Admission("service-inactive")
                SignInAdmission.NotificationsBlocked -> SignInProblem.NotificationsBlocked
                is SignInAdmission.Refused -> SignInProblem.Admission(admissionCode(admission.code))
            }
        if (refused != null) {
            mutableState.value = SignInSessionState.Failed(refused)
            releaseOnce()
            return
        }
        opening.value = Open.Pending
        val terminal =
            withContext(NonCancellable) {
                host.open(TerminalTarget.SignIn(command)).getOrNull().also {
                    opening.value = Open.Done(it)
                }
            }
        if (ending) return // end() is waiting for exactly this session and drains it
        if (terminal == null) {
            mutableState.value = SignInSessionState.Failed(SignInProblem.Start)
            releaseOnce()
            return
        }
        mutableState.value = SignInSessionState.Running(terminal)
        val events = scope.launch { terminal.events.collect { onEvent(it) } }
        val losses = scope.launch { claim.lost.collect { onLost(it) } }
        stopWatching += { events.cancel() }
        stopWatching += { losses.cancel() }
    }

    private fun onEvent(event: TerminalEvent) {
        if (ending) return
        val running = mutableState.value as? SignInSessionState.Running ?: return
        when (event) {
            is TerminalEvent.OpenUrl ->
                if (lease?.active == true) urls.tryEmit(event.url) else onLost(LOST_DEFAULT)
            is TerminalEvent.Exited -> mutableState.value = running.copy(exitCode = event.code)
        }
    }

    private fun onLost(reason: String) {
        if (ending) return
        val problem = SignInProblem.Lost(if (reason in LOST_REASONS) reason else LOST_DEFAULT)
        // A fresh coroutine: end() stops the collectors, and this may be running inside one of
        // them.
        scope.launch {
            end()
            mutableState.value = SignInSessionState.Failed(problem)
        }
    }

    /**
     * Ends the sign-in: drains the exact native session (also a late one), then releases the claim.
     * Returns the drain's outcome; a failure keeps the claim, and calling again retries the same
     * session.
     */
    public suspend fun end(): Outcome<Unit> {
        when (val now = drain.value) {
            Drain.Running -> return (drain.first { it is Drain.Done } as Drain.Done).outcome
            is Drain.Done -> if (now.outcome is Outcome.Ok) return now.outcome
            Drain.Idle -> Unit
        }
        drain.value = Drain.Running
        val outcome =
            try {
                drainNow()
            } catch (stopped: Throwable) {
                // The caller was cancelled mid-drain: a later end() starts over instead of waiting
                // forever.
                drain.value = Drain.Done(Failure.Unexpected().failed())
                throw stopped
            }
        drain.value = Drain.Done(outcome)
        return outcome
    }

    private suspend fun drainNow(): Outcome<Unit> {
        ending = true
        stopWatching.forEach { it() }
        stopWatching.clear()
        if (opening.value == Open.NotIssued) {
            cancelStart?.invoke()
            val released = releaseOnce()
            if (released is Outcome.Failed) return released
            if (mutableState.value !is SignInSessionState.Failed)
                mutableState.value = SignInSessionState.Ended
            return Unit.ok()
        }
        val terminal = (opening.first { it is Open.Done } as Open.Done).terminal
        if (terminal != null) {
            val closed = terminal.close()
            if (closed is Outcome.Failed) return closed
        }
        val released = releaseOnce()
        if (released is Outcome.Failed) return released
        if (mutableState.value !is SignInSessionState.Failed)
            mutableState.value = SignInSessionState.Ended
        return Unit.ok()
    }

    private suspend fun releaseOnce(): Outcome<Unit> {
        if (released) return Unit.ok()
        val held = lease
        held?.release()
        if (held != null && !held.releaseConfirmed) return Failure.Device().failed()
        released = true
        return Unit.ok()
    }

    private companion object {
        /**
         * plan 17 §1.5 `sign-in-lost/{…}`; anything else is reported as the generic loss, never
         * echoed.
         */
        val LOST_REASONS =
            setOf(
                "android-time-limit",
                "paused-by-you",
                "service-gone",
                "live-turned-off",
                "app-closed",
                "owner-closed",
            )
        const val LOST_DEFAULT = "service-gone"
        private val ADMISSION = Regex("^[a-z0-9-]{1,40}$")

        fun admissionCode(code: String): String =
            if (ADMISSION.matches(code)) code else "service-refused"
    }
}
