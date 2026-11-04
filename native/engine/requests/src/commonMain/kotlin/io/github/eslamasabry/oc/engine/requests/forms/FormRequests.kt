package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
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
) {
    private var revision = 0L
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
            is GatewayEvent.FormCreated -> add(event.form)
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
    public suspend fun refresh(): Outcome<Unit> {
        val before = revision
        val listed =
            when (val o = gateway.pendingForms()) {
                is Outcome.Ok -> o.value
                is Outcome.Failed -> return o
            }
        // Volatile events and newer reads win over an older pending-list response.
        if (before != revision) return Unit.ok()
        revision++
        val ids = listed.map { it.id }.toSet()
        _pending.value.filter { it.id !in ids && !lane.isInFlight(it.id) }.forEach { leave(it.id) }
        listed.forEach(::add)
        return Unit.ok()
    }

    /**
     * Answers [id] with the person's [draft]. The engine re-checks the form's rules itself: a draft
     * that breaks them never reaches the wire. Starts the hold and returns at once.
     */
    public fun answer(id: FormId, draft: Map<String, FormValue>): FormAnswerResult {
        val form = find(id) ?: return FormAnswerResult.Ignored
        if (!FormRules.evaluate(form.spec, draft).canSend) return FormAnswerResult.Invalid
        val payload = FormRules.payload(form.spec, draft)
        return if (lane.answer(id, FormReply.Submit(payload))) FormAnswerResult.Held
        else FormAnswerResult.Ignored
    }

    /**
     * Declines [id] (the sheet confirmed it first). Same hold and exactly-once rules as [answer].
     */
    public fun decline(id: FormId): FormAnswerResult {
        if (find(id) == null) return FormAnswerResult.Ignored
        return if (lane.answer(id, FormReply.Decline)) FormAnswerResult.Held
        else FormAnswerResult.Ignored
    }

    /** Inside the hold only. */
    public fun undo(id: FormId): Boolean = lane.undo(id)

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
        // A held answer belongs to its captured schema, never a replacement revision with the same
        // id.
        if (previous != null && previous.spec.revision != form.spec.revision) lane.forget(form.id)
        _pending.update { list ->
            val at = list.indexOfFirst { it.id == form.id }
            if (at < 0) (list + form).toImmutableList()
            else list.toMutableList().also { it[at] = form }.toImmutableList()
        }
    }

    private fun leave(id: FormId) {
        lane.forget(id)
        _pending.update { list -> list.filterNot { it.id == id }.toImmutableList() }
    }

    private suspend fun send(id: FormId, reply: FormReply): Outcome<Unit> {
        val form = find(id) ?: return Unit.ok() // left while held: nothing to send
        val session = form.wire.sessionId
        return when (reply) {
            is FormReply.Submit -> gateway.replyForm(session, id, FormAnswer(reply.values))
            FormReply.Decline -> gateway.cancelForm(session, id)
        }
    }

    private fun resolve(id: FormId, outcome: Outcome<Unit>): SendResolution =
        when (outcome) {
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

    private fun removeOnly(id: FormId) {
        revision++
        _pending.update { list -> list.filterNot { it.id == id }.toImmutableList() }
    }
}
