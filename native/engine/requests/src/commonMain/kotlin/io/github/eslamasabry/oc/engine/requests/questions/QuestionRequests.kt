package io.github.eslamasabry.oc.engine.requests.questions

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.QuestionGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.engine.requests.forms.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.forms.HeldAnswers
import io.github.eslamasabry.oc.engine.requests.forms.SendResolution
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

/** What the person decided on a question sheet. */
public sealed interface QuestionReply {
    public data class Answers(val answers: ImmutableList<ImmutableList<String>>) : QuestionReply

    /** Dismiss (confirmed by the sheet first: nobody can restore a dismissed question). */
    public data object Dismiss : QuestionReply
}

public enum class QuestionAnswerResult {
    Held,

    /** Already answered (held or on the wire), or the question is gone. */
    Ignored,

    /** The answers break the question's rules ([QuestionSheet.block]): nothing is sent. */
    Invalid,

    /** The sheet named another conversation than the question's own: nothing is sent (B-CHAT-072). */
    WrongSession,
}

/**
 * Question asks that go through a sheet (several prompts or several answers; plan 09 §5.4) on the session-scoped
 * question endpoints (capability `legacyQuestionRequests`). OpenCode 2 sends its `question` tool as a form instead
 * (`FormSpec.isQuestion`, [io.github.eslamasabry.oc.engine.requests.forms.FormRequests]).
 *
 * Asks are never replayed: [refresh] hydrates them (connect, reconnect). Answers are held 3 s with Undo and sent once
 * through one write slot; `QuestionNotFound` (404) resolves locally (B-PROTO-048); any other failure keeps the
 * question with the reason. Questions are never answered automatically (B-AGENT-221).
 */
public class QuestionRequests(
    private val gateway: QuestionGateway,
    scope: CoroutineScope,
    hold: Duration = HeldAnswers.HOLD,
    patience: Duration = HeldAnswers.PATIENCE,
) {
    private val _pending = MutableStateFlow<ImmutableList<PendingQuestion>>(persistentListOf())

    private val lane =
        HeldAnswers<RequestId, QuestionReply>(
            scope = scope,
            send = { id, reply -> send(id, reply) },
            resolve = { id, outcome -> resolve(id, outcome) },
            hold = hold,
            patience = patience,
        )

    public val pending: StateFlow<ImmutableList<PendingQuestion>> = _pending.asStateFlow()

    public val phases: StateFlow<ImmutableMap<RequestId, AnswerPhase<QuestionReply>>> = lane.phases

    public fun inSession(session: SessionId): ImmutableList<PendingQuestion> =
        _pending.value.filter { it.sessionId == session }.toImmutableList()

    /** A deleted conversation drops its questions. */
    public fun onEvent(event: GatewayEvent) {
        if (event is GatewayEvent.SessionDeleted) {
            _pending.value.filter { it.sessionId == event.sessionId }.forEach { leave(it.id) }
        }
    }

    /** Refetch (connect, reconnect). Gone questions leave unless an answer is in flight; a failed refetch keeps all. */
    public suspend fun refresh(): Outcome<Unit> {
        val listed =
            when (val o = gateway.pendingQuestions()) {
                is Outcome.Ok -> o.value
                is Outcome.Failed -> return o
            }
        val ids = listed.map { it.id }.toSet()
        _pending.value.filter { it.id !in ids && !lane.isInFlight(it.id) }.forEach { leave(it.id) }
        _pending.update { current ->
            val kept = current.filter { it.id !in ids }
            (kept + listed).toImmutableList()
        }
        return Unit.ok()
    }

    /**
     * Answers [id], asked in [session], with the sheet's [drafts]. The engine checks the session and the rules itself:
     * a mismatch or an invalid answer never reaches the wire.
     */
    public fun answer(id: RequestId, session: SessionId, drafts: List<PromptDraft>): QuestionAnswerResult {
        val question = find(id) ?: return QuestionAnswerResult.Ignored
        if (question.sessionId != session) return QuestionAnswerResult.WrongSession
        val answers = QuestionSheet.answers(question, drafts) ?: return QuestionAnswerResult.Invalid
        return if (lane.answer(id, QuestionReply.Answers(answers))) QuestionAnswerResult.Held else QuestionAnswerResult.Ignored
    }

    public fun dismiss(id: RequestId, session: SessionId): QuestionAnswerResult {
        val question = find(id) ?: return QuestionAnswerResult.Ignored
        if (question.sessionId != session) return QuestionAnswerResult.WrongSession
        return if (lane.answer(id, QuestionReply.Dismiss)) QuestionAnswerResult.Held else QuestionAnswerResult.Ignored
    }

    public fun undo(id: RequestId): Boolean = lane.undo(id)

    public suspend fun flushHeld(session: SessionId?) {
        lane.flush { id -> session == null || find(id)?.sessionId == session }
    }

    private fun find(id: RequestId): PendingQuestion? = _pending.value.firstOrNull { it.id == id }

    private fun leave(id: RequestId) {
        lane.forget(id)
        removeOnly(id)
    }

    private fun removeOnly(id: RequestId) {
        _pending.update { list -> list.filterNot { it.id == id }.toImmutableList() }
    }

    private suspend fun send(id: RequestId, reply: QuestionReply): Outcome<Unit> {
        val question = find(id) ?: return Unit.ok()
        return when (reply) {
            is QuestionReply.Answers -> gateway.answerQuestion(question.sessionId, id, reply.answers)
            QuestionReply.Dismiss -> gateway.rejectQuestion(question.sessionId, id)
        }
    }

    private fun resolve(id: RequestId, outcome: Outcome<Unit>): SendResolution =
        when {
            outcome is Outcome.Ok -> SendResolution.Settled.also { removeOnly(id) }
            (outcome as Outcome.Failed).failure is Failure.NotFound -> SendResolution.Settled.also { removeOnly(id) }
            else -> SendResolution.Keep(outcome.failure)
        }
}
