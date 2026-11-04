package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stable reasons (Flutter `GenUiSetupProblem`); the words layer says them. Never command or
 * exception text.
 */
public enum class CardsProblem {
    UnsupportedHost,
    RuntimeMissing,
    NotQualified,
    PermissionDenied,
    Conflict,
    InstallationFailed,
    RegistrationFailed,
    VerificationFailed,
    RemovalFailed,
    StorageFailed,
    Busy,
}

/** An agent that can show cards, by its product name ("Claude Code", "OpenCode 2"). */
public data class CardAgent(val id: String, val displayName: String) {
    public companion object {
        public val Claude: CardAgent = CardAgent("claude", "Claude Code")
        public val OpenCode2: CardAgent = CardAgent("openCode2", "OpenCode 2")
    }
}

/** What the Cards row says (Flutter `GenUiSetupStatus`), per agent. */
public sealed interface CardsStatus {
    public val agents: List<CardAgent>
        get() = emptyList()

    public data object Off : CardsStatus

    public data object Checking : CardsStatus

    public data class On(override val agents: List<CardAgent>) : CardsStatus

    public data class Partial(
        override val agents: List<CardAgent>,
        val reason: CardsProblem,
        val affected: List<CardAgent>,
    ) : CardsStatus

    public data class RestartRequired(override val agents: List<CardAgent>) : CardsStatus

    public data class Unavailable(
        val reason: CardsProblem,
        val affected: List<CardAgent> = emptyList(),
    ) : CardsStatus

    public data class Failed(
        val reason: CardsProblem,
        val affected: List<CardAgent> = emptyList(),
    ) : CardsStatus
}

/** The one stored preference: `oc.genui.enabled.<profileId>`, off by default (plan 09 §13.1). */
public interface CardsPrefs {
    public fun enabled(profileId: String): Boolean?

    public suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit>

    public companion object {
        public fun enabledKey(profileId: String): String = "oc.genui.enabled.$profileId"

        public fun coveredKey(profileId: String): String = "oc.genui.covered.$profileId"

        public fun journalKey(profileId: String): String = "oc.genui.journal.$profileId"
    }
}

/**
 * Proves which agents can show cards here (registration plus the MCP self-check). A registration
 * alone is never "ready". [install] runs explicit setup on a managed host; unmanaged hosts never
 * reach it.
 */
public interface CardsQualifier {
    /** False = this host cannot be set up from the phone (a remote server we do not manage). */
    public fun managed(profileId: String): Boolean

    /** The agents of this connection (phone agents plus the connected server's runtime). */
    public fun agentsHere(profileId: String): List<CardAgent>

    /**
     * Qualified transport and runtime for the exact conversation, never another provider on a mixed
     * host.
     */
    public fun permitsSession(profileId: String, sessionId: String): Boolean = true

    /**
     * Ready agents, or a problem (Outcome.Failed is a storage/transport problem:
     * VerificationFailed).
     */
    public suspend fun verify(profileId: String): Outcome<CardsVerdict>

    /** Explicit setup on a managed host; default: nothing to install. */
    public suspend fun install(profileId: String): Outcome<CardsVerdict> = verify(profileId)

    /** Best-effort removal; failure keeps the desired state off anyway. */
    public suspend fun remove(profileId: String): Outcome<Unit> = Outcome.Ok(Unit)
}

public data class CardsVerdict(
    val ready: List<CardAgent>,
    val problem: CardsProblem? = null,
    val affected: List<CardAgent> = emptyList(),
)

/**
 * Cards availability (B-CHAT-034..036, Flutter `_ConnectionControllerGenUi`): the desired setting
 * (what the person chose) is kept apart from the effective capability (what was proven). Off by
 * default even where the server could; rechecked when the connection changes; a newer request
 * always wins over an older one still running; disabling blocks card actions before removal
 * finishes; unmanaged profiles never run setup.
 */
public class CardsSetup(private val prefs: CardsPrefs, private val qualifier: CardsQualifier) {
    private val statuses = HashMap<String, CardsStatus>()
    private val desired = HashMap<String, Boolean>()
    private val generation = HashMap<String, Int>()
    private val running = HashMap<String, Int>()
    private val mutable = MutableStateFlow(0L)

    /** +1 whenever a status changes (screens re-read [status]). */
    public val changes: StateFlow<Long> = mutable.asStateFlow()

    public fun permitsSession(profileId: String, sessionId: String): Boolean =
        qualifier.permitsSession(profileId, sessionId)

    public fun desired(profileId: String): Boolean =
        desired[profileId] ?: prefs.enabled(profileId) ?: false

    /** Effective: on, proven, for at least one agent here, and not being turned off. */
    public fun effective(profileId: String, agent: CardAgent? = null): Boolean {
        if (!desired(profileId)) return false
        val s = status(profileId)
        val ready = s.agents
        return (s is CardsStatus.On || s is CardsStatus.Partial) &&
            (if (agent == null) ready.isNotEmpty() else agent in ready)
    }

    /** The status for this connection, naming only its agents (B-CHAT-035). */
    public fun status(profileId: String): CardsStatus {
        if (!desired(profileId)) return CardsStatus.Off
        val here = qualifier.agentsHere(profileId)
        val raw = statuses[profileId] ?: CardsStatus.Unavailable(CardsProblem.NotQualified, here)
        return project(raw, here)
    }

    private fun project(status: CardsStatus, here: List<CardAgent>): CardsStatus {
        fun mine(list: List<CardAgent>) = list.filter { it in here }
        val ready = mine(status.agents)
        val unchecked = CardsStatus.Unavailable(CardsProblem.NotQualified, here)
        return when (status) {
            is CardsStatus.On -> if (ready.isEmpty()) unchecked else CardsStatus.On(ready)
            is CardsStatus.Partial ->
                if (status.affected.isNotEmpty() && mine(status.affected).isEmpty()) {
                    if (ready.isEmpty()) unchecked else CardsStatus.On(ready)
                } else {
                    CardsStatus.Partial(ready, status.reason, mine(status.affected))
                }
            is CardsStatus.Unavailable ->
                if (status.affected.isNotEmpty() && mine(status.affected).isEmpty()) unchecked
                else CardsStatus.Unavailable(status.reason, mine(status.affected))
            is CardsStatus.Failed -> CardsStatus.Failed(status.reason, mine(status.affected))
            is CardsStatus.RestartRequired -> CardsStatus.RestartRequired(ready)
            CardsStatus.Off,
            CardsStatus.Checking -> status
        }
    }

    /**
     * The switch. On: store the wish, then (managed host) set up and verify, or (unmanaged) only
     * verify. Off: effective goes false at once, then removal runs. A request superseded by a newer
     * one never writes its result.
     */
    public suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
        val gen = (generation[profileId] ?: 0) + 1
        generation[profileId] = gen
        desired[profileId] = on
        if (!on) statuses[profileId] = CardsStatus.Off
        bump()
        val saved = prefs.setEnabled(profileId, on)
        if (saved is Outcome.Failed) {
            if (generation[profileId] == gen) {
                // The wish could not be stored: keep the effective capability off, say why.
                desired.remove(profileId)
                statuses[profileId] = CardsStatus.Failed(CardsProblem.StorageFailed)
                bump()
            }
            return saved
        }
        if (!on) {
            qualifier.remove(profileId)
            return Outcome.Ok(Unit)
        }
        return refresh(profileId, explicit = true, gen = gen)
    }

    /**
     * Recheck after a runtime switch, an "All projects" change or a reconnect (B-CHAT-034).
     * Automatic checks are coalesced: one runs at a time per profile.
     */
    public suspend fun recheck(profileId: String): Outcome<Unit> {
        if (!desired(profileId)) return Outcome.Ok(Unit)
        if (running.containsKey(profileId)) return Outcome.Ok(Unit)
        return refresh(profileId, explicit = false, gen = generation[profileId] ?: 0)
    }

    private suspend fun refresh(profileId: String, explicit: Boolean, gen: Int): Outcome<Unit> {
        running[profileId] = gen
        statuses[profileId] = CardsStatus.Checking
        bump()
        val managed = qualifier.managed(profileId)
        val result =
            try {
                if (explicit && managed) qualifier.install(profileId)
                else qualifier.verify(profileId)
            } finally {
                if (running[profileId] == gen) running.remove(profileId)
            }
        // A newer request (or a disable) owns the status now.
        if ((generation[profileId] ?: 0) != gen || !desired(profileId)) return Outcome.Ok(Unit)
        statuses[profileId] =
            when (result) {
                is Outcome.Failed ->
                    CardsStatus.Failed(
                        CardsProblem.VerificationFailed,
                        qualifier.agentsHere(profileId),
                    )
                is Outcome.Ok -> {
                    val v = result.value
                    when {
                        v.ready.isEmpty() && !managed && v.problem == null ->
                            CardsStatus.Unavailable(CardsProblem.UnsupportedHost)
                        v.ready.isEmpty() ->
                            CardsStatus.Unavailable(
                                v.problem ?: CardsProblem.NotQualified,
                                v.affected,
                            )
                        v.problem != null -> CardsStatus.Partial(v.ready, v.problem, v.affected)
                        else -> CardsStatus.On(v.ready)
                    }
                }
            }
        bump()
        return if (result is Outcome.Failed) result else Outcome.Ok(Unit)
    }

    /** Profile deletion: nothing of the profile's card state stays in memory. */
    public fun forget(profileId: String) {
        statuses.remove(profileId)
        desired.remove(profileId)
        generation[profileId] = (generation[profileId] ?: 0) + 1
        running.remove(profileId)
        bump()
    }

    private fun bump() {
        mutable.value = mutable.value + 1
    }
}

/**
 * The app binds lifecycle owners without making this engine depend on integrations or platform
 * code.
 */
public fun interface CardsLifecycle {
    public fun attach(setup: CardsSetup)
}
