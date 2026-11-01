package io.github.eslamasabry.oc.engine.agents.approval

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** A request waiting for a person. Only [PendingKind.Permission] can ever be answered automatically. */
public enum class PendingKind { Permission, Question, Form }

public data class PendingRequest(
    val id: RequestId,
    val sessionId: SessionId,
    val kind: PendingKind,
    /** The action as the server named it (`bash`, `edit`, `shell`…). Shown in the in-chat line, never logged. */
    val action: String,
    val patterns: ImmutableList<String> = persistentListOf(),
) {
    override fun toString(): String = "PendingRequest(${id.raw}, $kind)"
}

/** What the server said about an automatic "once" reply. */
public enum class AutoReplyReceipt {
    /** The reply landed and the request is resolved. */
    Confirmed,

    /** The server no longer has the request (answered elsewhere): resolved, but not our approval. */
    AlreadyResolved,
}

/**
 * Sends one "once" reply on the request's own contract (v1, v2 or the legacy shape; the requests engine owns the
 * wire). Never "always". A `Failed` outcome leaves the request pending and visible.
 */
public fun interface PermissionReplyPort {
    public suspend fun replyOnce(request: PendingRequest): Outcome<AutoReplyReceipt>
}

/** Why an automatic reply did not land; words keys ("Could not confirm automatic approval. Review the request."). */
public enum class AutoApprovalFailure { Unconfirmed }

/** One permission this phone answered automatically, for the quiet in-chat line. In memory only. */
public data class AutoApprovedPermission(
    val requestId: RequestId,
    val sessionId: SessionId,
    val action: String,
    val patterns: ImmutableList<String>,
    val atEpochMillis: Long,
)

public data class AutoApprovalState(
    /** Every known pending request, including those an automatic reply is answering right now. */
    val pending: ImmutableMap<RequestId, PendingRequest> = persistentMapOf(),
    /** Requests whose automatic reply is on the wire: no surface asks a person about them unless it fails. */
    val approving: ImmutableSet<RequestId> = persistentSetOf(),
    val failures: ImmutableMap<RequestId, AutoApprovalFailure> = persistentMapOf(),
    /** Recent automatic approvals per session, oldest first, at most [AutoApproval.MAX_RECORDED_PER_SESSION]. */
    val recent: ImmutableMap<SessionId, ImmutableList<AutoApprovedPermission>> = persistentMapOf(),
) {
    /** Requests that need a person. */
    public val awaiting: List<PendingRequest> get() = pending.values.filter { it.id !in approving }

    public fun awaitingFor(session: SessionId): List<PendingRequest> = awaiting.filter { it.sessionId == session }

    public fun recentFor(session: SessionId): ImmutableList<AutoApprovedPermission> = recent[session] ?: persistentListOf()
}

/**
 * Per-session auto-approval for one connection graph (plan 17 §1.10; port of the `_maybeAutoApprove` /
 * `_sweepAutoApprovals` / `_autoApprove` logic of `lib/state/connection/permissions.dart`). The requests engine feeds
 * it what it sees ([onAsked], [onHydrated], [onResolved]); this decides and sends the "once" replies through [port].
 *
 * Rules: replies are once only; they apply to requests as they arrive, to ones a reconnect hydration finds, and to
 * those already waiting when an automatic mode is switched on (sweep); questions and forms never take this path; a
 * failed reply leaves the request pending with [AutoApprovalFailure]; a request the server no longer has is resolved
 * but not recorded as ours; turning approval off never recalls a reply already sent. The person's own choice is the
 * consent: AI Team supervision does not apply to conversations.
 *
 * [scope] is the engine's serial lane: every call and every reply completion runs on it.
 */
public class AutoApproval(
    private val profile: ProfileId,
    private val store: AutoApprovalStore,
    private val port: PermissionReplyPort,
    private val parentOf: (SessionId) -> SessionId?,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(AutoApprovalState())
    public val state: StateFlow<AutoApprovalState> = mutable.asStateFlow()

    private var connected = false
    private val resolved = mutableSetOf<RequestId>()

    public fun effectiveFor(session: SessionId): EffectiveAutoApproval = store.effectiveFor(profile, session, parentOf)

    public val approvesEverything: Boolean get() = store.approvesEverything(profile)

    /** Connection up: requests may be answered. Down: nothing is sent and the in-memory record is cleared. */
    public fun setConnected(value: Boolean) {
        connected = value
        if (!value) mutable.update { it.copy(recent = persistentMapOf()) }
    }

    public fun onAsked(request: PendingRequest) {
        resolved.remove(request.id)
        mutable.update { it.copy(pending = it.pending.plusEntry(request.id, request)) }
        maybeAutoApprove(request)
    }

    /** Requests found by a reconnect hydration; those in automatic sessions are answered too. */
    public fun onHydrated(requests: List<PendingRequest>) {
        mutable.update { state -> state.copy(pending = requests.fold(state.pending) { acc, r -> acc.plusEntry(r.id, r) }) }
        sweep()
    }

    /** The server (or a person's own reply) resolved [id]: it leaves every list and its failure note clears. */
    public fun onResolved(id: RequestId) {
        resolved += id
        mutable.update { it.copy(pending = it.pending.minusKey(id), failures = it.failures.minusKey(id)) }
    }

    public suspend fun setSession(session: SessionId, setting: SessionAutoApproval?): Outcome<Unit> =
        store.set(profile, session, setting).also { if (it is Outcome.Ok) sweep() }

    /** The server-wide "approve everything" (the UI confirms before enabling). */
    public suspend fun setApprovesEverything(value: Boolean): Outcome<Unit> =
        store.setApprovesEverything(profile, value).also { if (it is Outcome.Ok) sweep() }

    private fun sweep() {
        for (request in mutable.value.pending.values.toList()) maybeAutoApprove(request)
    }

    private fun maybeAutoApprove(request: PendingRequest) {
        if (!connected || profile.raw.isEmpty()) return
        if (request.kind != PendingKind.Permission) return
        if (!effectiveFor(request.sessionId).automatic) return
        if (request.id in resolved) return
        if (request.id in mutable.value.approving) return
        mutable.update { it.copy(approving = it.approving.plusItem(request.id)) }
        scope.launch { reply(request) }
    }

    private suspend fun reply(request: PendingRequest) {
        val result = port.replyOnce(request)
        val at = clock.now().toEpochMilliseconds()
        mutable.update { state ->
            val approving = state.approving.minusItem(request.id)
            when {
                result is Outcome.Ok && result.value == AutoReplyReceipt.Confirmed -> {
                    val record = (state.recent[request.sessionId] ?: persistentListOf()).let { list ->
                        val trimmed = if (list.size >= MAX_RECORDED_PER_SESSION) list.drop(list.size - MAX_RECORDED_PER_SESSION + 1) else list
                        (trimmed + AutoApprovedPermission(request.id, request.sessionId, request.action, request.patterns, at))
                            .toImmutableList()
                    }
                    state.copy(
                        pending = state.pending.minusKey(request.id),
                        approving = approving,
                        failures = state.failures.minusKey(request.id),
                        recent = state.recent.plusEntry(request.sessionId, record),
                    )
                }
                result is Outcome.Ok ->
                    state.copy(pending = state.pending.minusKey(request.id), approving = approving)
                request.id in state.pending ->
                    state.copy(approving = approving, failures = state.failures.plusEntry(request.id, AutoApprovalFailure.Unconfirmed))
                else -> state.copy(approving = approving)
            }
        }
        if (result is Outcome.Ok) resolved += request.id
    }

    public companion object {
        public const val MAX_RECORDED_PER_SESSION: Int = 20
    }
}

private fun <K, V> ImmutableMap<K, V>.plusEntry(key: K, value: V): ImmutableMap<K, V> =
    (this as? kotlinx.collections.immutable.PersistentMap<K, V>)?.put(key, value)
        ?: kotlinx.collections.immutable.persistentMapOf<K, V>().putAll(this).put(key, value)

private fun <K, V> ImmutableMap<K, V>.minusKey(key: K): ImmutableMap<K, V> =
    (this as? kotlinx.collections.immutable.PersistentMap<K, V>)?.remove(key)
        ?: kotlinx.collections.immutable.persistentMapOf<K, V>().putAll(this).remove(key)

private fun <T> ImmutableSet<T>.plusItem(item: T): ImmutableSet<T> =
    (this as? kotlinx.collections.immutable.PersistentSet<T>)?.add(item) ?: persistentSetOf<T>().addAll(this).add(item)

private fun <T> ImmutableSet<T>.minusItem(item: T): ImmutableSet<T> =
    (this as? kotlinx.collections.immutable.PersistentSet<T>)?.remove(item) ?: persistentSetOf<T>().addAll(this).remove(item)
