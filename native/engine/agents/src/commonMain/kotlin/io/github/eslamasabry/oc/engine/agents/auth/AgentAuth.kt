package io.github.eslamasabry.oc.engine.agents.auth

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.host.AuthAction
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.LegacySignInStatus
import io.github.eslamasabry.oc.engine.agents.host.TransientLabel
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** The last account answer for one agent. [account] is shown, never logged. */
public data class AgentAccount(
    val state: AuthState,
    val account: TransientLabel?,
    val error: AuthProbeError?,
    val readAt: Instant,
) {
    public val projection: AuthProjection
        get() = AuthProjection(state, account, error)
}

public enum class SignOutResult {
    /** A following probe said signed out. */
    SignedOut,

    /**
     * The agent still reads signed in, or the read could not run: the account is kept as it was.
     */
    NotConfirmed,

    /** The bridge cannot sign this agent out; the person uses its terminal. */
    Unsupported,
}

/**
 * Auth probe, sign-out and account label for one OWNER (plan 17 §1.5, AGENTS-BACKENDS-15; port of
 * the Dart `_PhoneAgentAuthObservation` and `signOutAgent`).
 *
 * - Every native read takes turns on one chain per owner: the native side allows one read per
 *   profile and answered a second one as `HostUnavailable`, which read as "Sign in needed"
 *   (claude-sign-in-2026-10-17).
 * - [refresh] reuses an answer younger than [freshFor] and SHARES an in-flight read for the same
 *   agent (never a second native call). [recheck] always asks: it queues behind the chain, so an
 *   older read can never complete after it.
 * - Timeouts: every probe 10 s, sign-out 20 s. Every check ends.
 * - A read that cannot run (`HostUnavailable`, `TimedOut`) keeps the last real answer.
 * - Sign-out counts only when the following probe says `SignedOut` (the kept script runs logout
 *   then status; an exit code is never proof).
 */
public class AgentAuth(
    private val host: AgentHost,
    private val owner: OwnerId,
    private val clock: Clock,
    /**
     * The agents whose bridge can sign out (`AgentPhoneScripts.supportsSignOut`: Claude and fx).
     */
    private val signOutAgents: Set<AgentId> = DEFAULT_SIGN_OUT_AGENTS,
    private val freshFor: Duration = SIGN_IN_FRESH,
) {
    private val chain = Mutex()
    private val shareLock = Mutex()
    private val inFlight = mutableMapOf<AgentId, CompletableDeferred<AuthProjection>>()
    private val mutableAccounts =
        MutableStateFlow<PersistentMap<AgentId, AgentAccount>>(persistentMapOf())
    private var closed = false

    public val accounts: StateFlow<ImmutableMap<AgentId, AgentAccount>> =
        mutableAccounts.asStateFlow()

    public fun account(agent: AgentId): AgentAccount? = mutableAccounts.value[agent]

    public fun canSignOut(agent: AgentId): Boolean = host.available && agent in signOutAgents

    /** A list refresh: a recent real answer is reused; an in-flight read is shared. */
    public suspend fun refresh(agent: AgentId): AuthProjection {
        account(agent)?.let { last ->
            if (last.state != AuthState.Error && clock.now() - last.readAt < freshFor)
                return last.projection
        }
        val (deferred, leader) =
            shareLock.withLock {
                inFlight[agent]?.let { it to false }
                    ?: CompletableDeferred<AuthProjection>().also { inFlight[agent] = it } to true
            }
        if (!leader) return deferred.await()
        var result: AuthProjection = AuthProjectionJson.failed(AuthProbeError.HostUnavailable)
        try {
            result = read(agent)
        } finally {
            shareLock.withLock { if (inFlight[agent] === deferred) inFlight.remove(agent) }
            deferred.complete(result)
        }
        return result
    }

    /**
     * "Check again" and the end of a sign-in terminal: always a new read, after any read already
     * queued.
     */
    public suspend fun recheck(agent: AgentId): AuthProjection = read(agent)

    /** The sign-in terminal ended: signed in only on a `SignedIn` projection. */
    public suspend fun confirmSignIn(agent: AgentId): Boolean =
        recheck(agent).state == AuthState.SignedIn

    public suspend fun signOut(agent: AgentId): SignOutResult {
        if (!canSignOut(agent)) return SignOutResult.Unsupported
        return chain.withLock {
            val outcome =
                withTimeoutOrNull(SIGN_OUT_TIMEOUT) {
                    host.auth(owner, agent, AuthAction.SignOut, SIGN_OUT_TIMEOUT)
                }
            val projection = (outcome as? Outcome.Ok)?.value
            when (projection?.state) {
                AuthState.SignedOut -> {
                    publish(agent, AuthProjection(AuthState.SignedOut))
                    SignOutResult.SignedOut
                }
                AuthState.SignedIn -> {
                    publish(agent, projection)
                    SignOutResult.NotConfirmed
                }
                AuthState.Error,
                null -> SignOutResult.NotConfirmed
            }
        }
    }

    /** Profile close or owner change: later answers are dropped. */
    public fun close() {
        closed = true
    }

    private suspend fun read(agent: AgentId): AuthProjection = chain.withLock {
        val fresh = probeOnce(agent)
        val previous = account(agent)
        val couldNotRun =
            fresh.error == AuthProbeError.HostUnavailable || fresh.error == AuthProbeError.TimedOut
        if (couldNotRun && previous != null && previous.state != AuthState.Error) {
            previous.projection
        } else {
            publish(agent, fresh)
            fresh
        }
    }

    private suspend fun probeOnce(agent: AgentId): AuthProjection {
        val timeout = if (agent == AgentId.CLAUDE) CLAUDE_PROBE_TIMEOUT else PROBE_TIMEOUT
        val outcome =
            withTimeoutOrNull(timeout) { host.auth(owner, agent, AuthAction.Probe, timeout) }
                ?: return AuthProjectionJson.failed(AuthProbeError.TimedOut)
        val projection =
            when (outcome) {
                is Outcome.Ok -> outcome.value
                is Outcome.Failed ->
                    return AuthProjectionJson.failed(AuthProbeError.HostUnavailable)
            }
        if (agent != AgentId.CLAUDE || projection.error != AuthProbeError.ProbeUnsupported)
            return projection
        // Without the multi-agent bridge, only Claude's direct status read stands (never setup
        // output or provider
        // error words). AGENTS-BACKENDS-17 keeps this read of the retired relay.
        val legacy =
            withTimeoutOrNull(PROBE_TIMEOUT) { host.legacySignInStatus(owner, agent) }
                ?: return AuthProjectionJson.failed(AuthProbeError.TimedOut)
        return when ((legacy as? Outcome.Ok)?.value) {
            LegacySignInStatus.SignedIn -> AuthProjection(AuthState.SignedIn)
            LegacySignInStatus.SignedOut -> AuthProjection(AuthState.SignedOut)
            LegacySignInStatus.LimitReached,
            LegacySignInStatus.Unknown -> AuthProjection(AuthState.Error)
            null -> AuthProjectionJson.failed(AuthProbeError.HostUnavailable)
        }
    }

    private fun publish(agent: AgentId, projection: AuthProjection) {
        if (closed) return
        val next = AgentAccount(projection.state, projection.account, projection.error, clock.now())
        mutableAccounts.update { it.put(agent, next) }
    }

    public companion object {
        public val CLAUDE_PROBE_TIMEOUT: Duration = 65.seconds
        public val PROBE_TIMEOUT: Duration = 10.seconds
        public val SIGN_OUT_TIMEOUT: Duration = 20.seconds

        /** A list refresh reuses an answer younger than this (Dart `paSignInFresh`). */
        public val SIGN_IN_FRESH: Duration = 10.minutes
        public val DEFAULT_SIGN_OUT_AGENTS: Set<AgentId> = setOf(AgentId.CLAUDE, AgentId("fx"))
    }
}
