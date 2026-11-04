package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.FormGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.FormAnswer
import io.github.eslamasabry.oc.core.model.FormSpec as WireForm
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/** One waiting form: the adapted [spec] the sheet draws, and the [wire] form it answers. */
public data class PendingForm(val spec: FormSpec, val wire: WireForm) {
    val id: FormId
        get() = spec.id

    /** The conversation it belongs to; null for a connection-level MCP elicitation. */
    val sessionId: SessionId?
        get() = (spec.owner as? FormOwner.Session)?.sessionId
}

/** What the person decided. */
public sealed interface FormReply {
    /**
     * The assembled payload ([FormRules.payload]): active fields only, hidden ones with their
     * default.
     */
    public data class Submit(val values: ImmutableMap<String, JsonElement>) : FormReply

    /**
     * "Decline this request" (confirmed by the sheet first: nobody can restore a dismissed form).
     */
    public data object Decline : FormReply
}

/** What [FormRequests.answer] did. */
public enum class FormAnswerResult {
    /** Held for Undo, then sent once. */
    Held,

    /** An answer is already held or on the wire, or the form is gone: nothing changed. */
    Ignored,

    /**
     * The answers break the form's own rules: nothing is sent (B-CHAT-072 "invalid values never
     * send").
     */
    Invalid,
}

/** Said once, then acknowledged. */
public sealed interface FormNotice {
    /** 409 `FormAlreadySettledError`: the form closed because it was answered on another device. */
    public data class AnsweredElsewhere(val formId: FormId, val title: String) : FormNotice
}

/**
 * OpenCode 2 forms of one connection (plan 09 §5.5, B-PROTO-044): a created event adds the form, a
 * settled event removes it, [refresh] hydrates the pending ones (asks are never replayed, so a
 * reconnect refetches: forms come back after a forced reconnect), deleting a session drops its
 * forms. Answers go through the 3 s hold and one write slot, exactly once ([HeldAnswers]).
 *
 * Reply errors (plan 09 §5.5 error contract): 400 `FormInvalidAnswerError` — or any failure but the
 * two below — keeps the form open with the reason; 409 `FormAlreadySettledError` closes it and says
 * once it was answered elsewhere; 404 means it is gone already and closes it without an error.
 * Questions and forms are never answered automatically (B-AGENT-221): nothing here answers without
 * a person's call.
 *
 * The owner gates it on `ServerCapabilities.forms`; without it the gateway's defaults keep the list
 * empty.
 */
public class FormRequests(
    private val gateway: FormGateway,
    scope: CoroutineScope,
    hold: Duration = HeldAnswers.HOLD,
    patience: Duration = HeldAnswers.PATIENCE,
    private val sourceIdentity: () -> Any? = { Unit },
) {
    private data class Capture(val form: PendingForm, val source: Any?)

    private val captures = HashMap<FormId, Capture>()
    private val attempts = HashSet<Pair<FormId, String>>()
    private val readSlot = Mutex()
    private var revision = 0L
    private val changedAt = HashMap<FormId, Long>()
    private val settled = LinkedHashSet<FormId>()
    private val watchedSessions = HashSet<SessionId>()
    private val _pending = MutableStateFlow<ImmutableList<PendingForm>>(persistentListOf())
    private val _notices = MutableStateFlow<ImmutableList<FormNotice>>(persistentListOf())

    private val lane =
        HeldAnswers<FormId, FormReply>(
            scope = scope,
            send = { id, reply -> send(id, reply) },
            resolve = { id, outcome -> resolve(id, outcome) },
            hold = hold,
            patience = patience,
        )

    /** Every waiting form of this connection, oldest first. */
    public val pending: StateFlow<ImmutableList<PendingForm>> = _pending.asStateFlow()

    /** Held, sending and refused answers by form; a form with no entry waits. */
    public val phases: StateFlow<ImmutableMap<FormId, AnswerPhase<FormReply>>> = lane.phases

    public val notices: StateFlow<ImmutableList<FormNotice>> = _notices.asStateFlow()

    /** The forms of one conversation, oldest first. */
    public fun inSession(session: SessionId): ImmutableList<PendingForm> =
        _pending.value.filter { it.sessionId == session }.toImmutableList()

    public fun onEvent(event: GatewayEvent) {
        if (
            event is GatewayEvent.FormCreated ||
                event is GatewayEvent.FormSettled ||
                event is GatewayEvent.SessionDeleted
        )
            revision++
        when (event) {
            is GatewayEvent.FormCreated -> {
                settled.remove(event.form.id)
                add(event.form)
            }
            is GatewayEvent.FormSettled -> leave(event.formId)
            is GatewayEvent.SessionDeleted ->
                _pending.value.filter { it.sessionId == event.sessionId }.forEach { leave(it.id) }
            else -> Unit
        }
    }

    /**
     * Refetch every pending form (connect, reconnect, the stall tracker). A form the server no
     * longer lists left (answered elsewhere, timed out) unless an answer for it is held or on the
     * wire — that answer's own outcome decides. A failed refetch changes nothing.
     */
    public suspend fun refresh(): Outcome<Unit> = readSlot.withLock {
        val began = revision
        val source = sourceIdentity()
        val response =
            withTimeoutOrNull(3_000) { gateway.pendingForms() }
                ?: return@withLock Failure.TimedOut().failed()
        val listed =
            when (response) {
                is Outcome.Ok -> response.value
                is Outcome.Failed -> return@withLock response
            }
        if (sourceIdentity() != source)
            return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        val ids = listed.map { it.id }.toSet()
        _pending.value
            .filter {
                it.wire.sessionId !in watchedSessions &&
                    it.id !in ids &&
                    !lane.isInFlight(it.id) &&
                    (changedAt[it.id] ?: 0) <= began
            }
            .forEach { leave(it.id) }
        listed.filter { (changedAt[it.id] ?: 0) <= began && it.id !in settled }.forEach(::add)
        Unit.ok()
    }

    /** Session endpoints work without moving the shared connection onto that chat's folder. */
    public suspend fun refreshSession(session: SessionId): Outcome<Unit> = readSlot.withLock {
        val began = revision
        val source = sourceIdentity()
        val response =
            withTimeoutOrNull(3_000) { gateway.sessionForms(session) }
                ?: return@withLock Failure.TimedOut().failed()
        val listed =
            when (response) {
                is Outcome.Ok -> response.value
                is Outcome.Failed -> return@withLock response
            }
        if (sourceIdentity() != source)
            return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        if (listed.any { it.sessionId != session })
            return@withLock Failure.UnreadableReply().failed()
        val ids = listed.map { it.id }.toSet()
        _pending.value
            .filter {
                it.wire.sessionId == session &&
                    it.id !in ids &&
                    !lane.isInFlight(it.id) &&
                    (changedAt[it.id] ?: 0) <= began
            }
            .forEach { leave(it.id) }
        listed.filter { (changedAt[it.id] ?: 0) <= began && it.id !in settled }.forEach(::add)
        Unit.ok()
    }

    internal fun watchSession(session: SessionId, open: Boolean) {
        if (open) watchedSessions += session else watchedSessions -= session
    }

    /**
     * Answers [id] with the person's [draft]. The engine re-checks the form's rules itself: a draft
     * that breaks them never reaches the wire. Starts the hold and returns at once.
     */
    public fun answer(id: FormId, draft: Map<String, FormValue>): FormAnswerResult {
        val form = find(id) ?: return FormAnswerResult.Ignored
        if (!FormRules.evaluate(form.spec, draft).canSend) return FormAnswerResult.Invalid
        val payload = FormRules.payload(form.spec, draft)
        if (!lane.answer(id, FormReply.Submit(payload))) return FormAnswerResult.Ignored
        captures[id] = Capture(form, sourceIdentity())
        return FormAnswerResult.Held
    }

    /**
     * Declines [id] (the sheet confirmed it first). Same hold and exactly-once rules as [answer].
     */
    public fun decline(id: FormId): FormAnswerResult {
        val form = find(id) ?: return FormAnswerResult.Ignored
        if (!lane.answer(id, FormReply.Decline)) return FormAnswerResult.Ignored
        captures[id] = Capture(form, sourceIdentity())
        return FormAnswerResult.Held
    }

    /** Inside the hold only. */
    public fun undo(id: FormId): Boolean = lane.undo(id).also { if (it) captures.remove(id) }

    /**
     * Leaving the chat (or going idle in the background) sends held answers now; null = every
     * session.
     */
    public suspend fun flushHeld(session: SessionId?) {
        lane.flush { id -> session == null || find(id)?.sessionId == session }
    }

    public fun acknowledge(notice: FormNotice) {
        _notices.update { list -> list.filterNot { it == notice }.toImmutableList() }
    }

    private fun find(id: FormId): PendingForm? = _pending.value.firstOrNull { it.id == id }

    private fun add(wire: WireForm) {
        val form = PendingForm(Oc2FormAdapter.adapt(wire), wire)
        val previous = find(form.id)
        if (
            previous != null &&
                previous.spec.revision != form.spec.revision &&
                lane.phases.value[form.id] !is AnswerPhase.Sending
        ) {
            lane.forget(form.id)
            captures.remove(form.id)
        }
        changedAt[form.id] = ++revision
        _pending.update { list ->
            val at = list.indexOfFirst { it.id == form.id }
            if (at < 0) (list + form).toImmutableList()
            else list.toMutableList().also { it[at] = form }.toImmutableList()
        }
    }

    private fun leave(id: FormId) {
        lane.forget(id)
        captures.remove(id)
        removeOnly(id)
    }

    private suspend fun send(id: FormId, reply: FormReply): Outcome<Unit> {
        val captured = captures[id] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val form = captured.form
        fun current(): Boolean =
            sourceIdentity() == captured.source &&
                find(id)?.let {
                    it.spec.revision == form.spec.revision &&
                        it.wire.sessionId == form.wire.sessionId
                } == true
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val key = id to form.spec.revision
        if (key in attempts) return Failure.Refused(RefusalReason.DispatchUncertain).failed()
        val fresh =
            withTimeoutOrNull(3_000) {
                if (form.sessionId == null) gateway.pendingForms()
                else gateway.sessionForms(form.wire.sessionId)
            } ?: return Failure.TimedOut().failed()
        if (fresh is Outcome.Failed) return fresh
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val latest =
            (fresh as Outcome.Ok).value.firstOrNull {
                it.id == id && it.sessionId == form.wire.sessionId
            }
        if (latest == null || FormRevision.of(latest) != form.spec.revision) {
            if (latest == null) leave(id) else add(latest)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        attempts += key
        val result =
            withTimeoutOrNull(15_000) {
                when (reply) {
                    is FormReply.Submit ->
                        gateway.replyForm(form.wire.sessionId, id, FormAnswer(reply.values))
                    FormReply.Decline -> gateway.cancelForm(form.wire.sessionId, id)
                }
            } ?: Failure.Refused(RefusalReason.DispatchUncertain).failed()
        val failure = (result as? Outcome.Failed)?.failure
        if (failure is Failure.Rejected && failure.status == 400) attempts.remove(key)
        return result
    }

    private fun resolve(id: FormId, outcome: Outcome<Unit>): SendResolution {
        val captured = captures.remove(id)
        val current = find(id)
        if (
            captured != null &&
                (sourceIdentity() != captured.source ||
                    (current != null && current.spec.revision != captured.form.spec.revision))
        )
            return SendResolution.Settled
        return when (outcome) {
            is Outcome.Ok -> SendResolution.Settled.also { removeOnly(id) }
            is Outcome.Failed ->
                when (val f = outcome.failure) {
                    is Failure.Conflict -> {
                        val title = find(id)?.spec?.title.orEmpty()
                        _notices.update {
                            (it + FormNotice.AnsweredElsewhere(id, title)).toImmutableList()
                        }
                        removeOnly(id)
                        SendResolution.Settled
                    }
                    is Failure.NotFound -> SendResolution.Settled.also { removeOnly(id) }
                    else -> SendResolution.Keep(f)
                }
        }
    }

    private fun removeOnly(id: FormId) {
        changedAt[id] = ++revision
        settled += id
        while (settled.size > 512) settled.remove(settled.first())
        _pending.update { list -> list.filterNot { it.id == id }.toImmutableList() }
    }
}
