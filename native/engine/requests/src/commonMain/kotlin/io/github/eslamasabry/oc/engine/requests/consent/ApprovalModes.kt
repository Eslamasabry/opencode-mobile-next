package io.github.eslamasabry.oc.engine.requests.consent

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/** Ask first, or answer every permission ask "once" (never "always": a saved rule would outlive the session). */
public enum class ApprovalMode { Ask, AutoOnce }

/** One conversation's explicit choice (`oc.autoApprove.<profileId>`, format unchanged; plan 17 §1.10). */
public data class SessionApproval(val mode: ApprovalMode, val inheritToChildren: Boolean = false)

/** The resolved mode for one conversation and where it came from. */
public data class EffectiveApproval(
    val mode: ApprovalMode,
    /** The conversation's own setting decided. */
    val explicit: Boolean = false,
    /** An ancestor passed its mode down. */
    val inheritedFrom: SessionId? = null,
    /** "Approve everything" decided. */
    val serverWide: Boolean = false,
) {
    val automatic: Boolean get() = mode == ApprovalMode.AutoOnce
}

/**
 * The effective-mode walk (plan 09 §5.3; port of Dart `SessionAutoApprovalStore.effectiveFor`). The store and the
 * mode-change flow live with `AutoApproval` (plan 17 §1.10, `:engine:agents`); this is the pure rule the requests
 * engine applies through the policy, kept here so the sweep and its tests run without the store.
 */
public object ApprovalModes {
    /** The reserved key of "Approve everything" in the same map. */
    public const val SERVER_WIDE: String = "*"

    /**
     * The conversation's own setting wins (an explicit Ask keeps asking under "Approve everything"); else the nearest
     * ancestor with a setting decides — it passes its mode down when it inherits, and stops the walk when it does not;
     * else "Approve everything"; else ask. Cycles and unknown parents end the walk.
     */
    public fun effective(
        settings: Map<String, SessionApproval>,
        session: SessionId,
        parentOf: (SessionId) -> SessionId?,
    ): EffectiveApproval {
        settings[session.raw]?.let { return EffectiveApproval(it.mode, explicit = true) }
        val seen = hashSetOf(session)
        var parent = parentOf(session)
        while (parent != null && parent.raw.isNotEmpty() && seen.add(parent)) {
            val setting = settings[parent.raw]
            if (setting != null) {
                return if (setting.inheritToChildren) EffectiveApproval(setting.mode, inheritedFrom = parent) else fallback(settings)
            }
            parent = parentOf(parent)
        }
        return fallback(settings)
    }

    private fun fallback(settings: Map<String, SessionApproval>): EffectiveApproval =
        settings[SERVER_WIDE]?.let { EffectiveApproval(it.mode, serverWide = true) } ?: EffectiveApproval(ApprovalMode.Ask)
}

/** One automatic approval, for the approvals sheet (latest 5 per conversation). Patterns never reach the history. */
public data class AutoApproved(val requestId: RequestId, val sessionId: SessionId, val permission: String, val at: Instant)

/**
 * Answers waiting permission asks "once" where the effective mode is automatic (plan 09 §5.3 step 2): as each ask
 * arrives, for asks a reconnect hydration found, and for every ask already waiting when a mode is switched on. Each ask
 * is answered at most once ([reply] is the same write path as a tap on Allow once). Questions and forms never take
 * this path. A failed automatic reply leaves the ask visible with the reason ([failures]); turning approval off
 * mid-flight does not recall a reply already sent. The history is bounded and cleared with the connection.
 */
public class AutoApprovalSweep(
    private val automatic: (SessionId) -> Boolean,
    private val reply: suspend (PermissionRequest) -> Outcome<Unit>,
    private val clock: Clock,
) {
    private val lock = Mutex()
    private val inFlight = HashSet<RequestId>()
    private val answered = HashSet<RequestId>()
    private val _failures = MutableStateFlow<ImmutableMap<RequestId, Failure>>(persistentMapOf())
    private val _history = MutableStateFlow<ImmutableMap<SessionId, ImmutableList<AutoApproved>>>(persistentMapOf())

    /** Asks whose automatic reply failed: they stay visible, and the card says why it asks after all. */
    public val failures: StateFlow<ImmutableMap<RequestId, Failure>> = _failures.asStateFlow()

    public val history: StateFlow<ImmutableMap<SessionId, ImmutableList<AutoApproved>>> = _history.asStateFlow()

    /** Applies the effective mode to [waiting]; returns the asks it answered. */
    public suspend fun sweep(waiting: List<PermissionRequest>): List<RequestId> =
        waiting.filter { answer(it) }.map { it.id }

    /** One ask (arrival or hydration). True when this call answered it. */
    public suspend fun answer(request: PermissionRequest): Boolean {
        val claimed =
            lock.withLock {
                if (!automatic(request.sessionId) || request.id in answered || request.id in _failures.value) {
                    false
                } else {
                    inFlight.add(request.id)
                }
            }
        if (!claimed) return false
        val outcome = reply(request)
        lock.withLock {
            inFlight.remove(request.id)
            when (outcome) {
                is Outcome.Ok -> {
                    answered += request.id
                    record(AutoApproved(request.id, request.sessionId, request.permission, clock.now()))
                }
                is Outcome.Failed -> _failures.update { (it + (request.id to outcome.failure)).toImmutableMap() }
            }
        }
        return outcome is Outcome.Ok
    }

    /** A person answered the failed ask by hand, or it left: forget its failure. */
    public fun settled(request: RequestId) {
        _failures.update { (it - request).toImmutableMap() }
    }

    /** The connection closed: the record goes with it. */
    public fun clear() {
        _failures.value = persistentMapOf()
        _history.value = persistentMapOf()
    }

    private fun record(entry: AutoApproved) {
        _history.update { all ->
            val list = (all[entry.sessionId] ?: persistentListOf()) + entry
            (all + (entry.sessionId to list.takeLast(HISTORY).toImmutableList())).toImmutableMap()
        }
    }

    private companion object {
        const val HISTORY = 5
    }
}
