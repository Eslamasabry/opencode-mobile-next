package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import io.github.eslamasabry.oc.engine.requests.RequestsEngine
import io.github.eslamasabry.oc.engine.requests.SessionRequests
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The folder the chat works in: its name ends a shell question, its path makes absolute paths read
 * from it.
 */
public data class RequestPlace(val project: String? = null, val directory: String? = null)

/**
 * A same-id, same-text question asked again is still a new request and gets no earlier typed draft.
 */
private class QuestionDraftKey(val question: PendingQuestion) {
    override fun equals(other: Any?): Boolean =
        other is QuestionDraftKey && question === other.question

    override fun hashCode(): Int = question.hashCode()
}

/**
 * The chat's request slot (plan 09 §5.1, §6.4): the ChatViewModel owns one per open conversation
 * and passes [state] and this object (as [ChatRequestActions]) to the screen. It reads the
 * connection's [RequestsEngine] and holds only screen state of its own: the open Always allow
 * confirm, the "Something else" drafts and the sheet request. Every action checks that it names
 * this chat's current card, so a stale tap never answers another request.
 */
public class ChatRequestsModel(
    private val engine: RequestsEngine,
    private val session: SessionId,
    private val capabilities: StateFlow<ServerCapabilities>,
    private val place: StateFlow<RequestPlace>,
    private val scope: CoroutineScope,
) : ChatRequestActions {
    private data class Local(
        val confirm: PendingRequest.Permission? = null,
        val otherOpen: PendingRequest.Question? = null,
        val drafts: PersistentMap<QuestionDraftKey, String> = persistentMapOf(),
        val sheet: PendingRequest.Question? = null,
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<RequestSlotUi> =
        combine(engine.forSession(session), capabilities, place, local) {
                requests,
                caps,
                where,
                mine ->
                slot(requests, caps, where, mine)
            }
            .stateIn(scope, SharingStarted.Eagerly, current())

    /**
     * The request whose sheet the person asked for (a longer question); the sheet is S3-REQFORMS.
     */
    public val sheetFor: StateFlow<RequestId?> =
        combine(local, engine.pending) { mine, requests ->
                mine.sheet
                    ?.takeIf { reviewed ->
                        requests.any {
                            it is PendingRequest.Question &&
                                it.question === reviewed.question &&
                                it.phase == AnswerPhase.Waiting
                        }
                    }
                    ?.id
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Leaving the chat sends its held answers now (plan 09 §5.1). */
    public suspend fun leave() {
        engine.flushHeld(session)
    }

    override fun shown(id: RequestId) {
        if (current().card?.id == id) engine.markShown(id)
    }

    override fun allowOnce(id: RequestId) {
        permissionCard(id)?.let { performReviewed(it, ReviewedRequestAction.AllowOnce) }
    }

    override fun reject(id: RequestId) {
        permissionCard(id)?.let { performReviewed(it, ReviewedRequestAction.Reject) }
    }

    override fun askAlwaysAllow(id: RequestId) {
        permissionCard(id)?.let { performReviewed(it, ReviewedRequestAction.AskAlways) }
    }

    override fun confirmAlwaysAllow() {
        current().confirmAlways?.let(::confirmAlwaysReviewed)
    }

    override fun confirmAlwaysReviewed(confirm: AlwaysConfirmUi) {
        val reviewed = confirm.captured ?: return
        val current = current().confirmAlways?.captured ?: return
        if (
            reviewed.id != confirm.id ||
                current.request !== reviewed.request ||
                !engine.isCurrent(reviewed)
        )
            return
        local.update { it.copy(confirm = null) }
        engine.answer(reviewed.id, RequestAnswer.Permission(PermissionReply.Always), reviewed)
    }

    override fun cancelAlwaysAllow() {
        local.update { it.copy(confirm = null) }
    }

    override fun choose(id: RequestId, option: String) {
        questionCard(id)?.let { performReviewed(it, ReviewedRequestAction.Choose(option)) }
    }

    override fun openOther(id: RequestId) {
        questionCard(id)?.let { performReviewed(it, ReviewedRequestAction.OpenOther) }
    }

    override fun editOther(id: RequestId, text: String) {
        questionCard(id)?.let { performReviewed(it, ReviewedRequestAction.EditOther(text)) }
    }

    override fun sendOther(id: RequestId) {
        questionCard(id)?.let { performReviewed(it, ReviewedRequestAction.SendOther) }
    }

    override fun openSheet(id: RequestId) {
        questionCard(id)?.let { performReviewed(it, ReviewedRequestAction.OpenSheet) }
    }

    override fun undo(id: RequestId) {
        current()
            .card
            ?.takeIf { it.id == id }
            ?.let { performReviewed(it, ReviewedRequestAction.Undo) }
    }

    override fun retry(id: RequestId) {
        current()
            .card
            ?.takeIf { it.id == id }
            ?.let { performReviewed(it, ReviewedRequestAction.Retry) }
    }

    /**
     * The rendered card is the consent boundary; equality with a newer card's wire id alone never
     * admits a reply.
     */
    override fun performReviewed(card: RequestCardUi, action: ReviewedRequestAction) {
        val reviewed = card.captured ?: return
        val currentCard = current().card ?: return
        if (
            reviewed.id != card.id ||
                reviewed.sessionId != session ||
                !engine.isCurrent(reviewed) ||
                currentCard.id != card.id
        )
            return
        when (action) {
            ReviewedRequestAction.Undo -> if (currentCard.state !is CardState.Held) return
            ReviewedRequestAction.Retry ->
                if (currentCard.state !is CardState.NotAccepted || currentCard.state != card.state)
                    return
            else -> if (currentCard.state != CardState.Waiting) return
        }
        when (action) {
            ReviewedRequestAction.AllowOnce ->
                if (reviewed is PendingRequest.Permission && card.state == CardState.Waiting) {
                    engine.answer(
                        reviewed.id,
                        RequestAnswer.Permission(PermissionReply.Once),
                        reviewed,
                    )
                }
            ReviewedRequestAction.Reject ->
                if (reviewed is PendingRequest.Permission && card.state == CardState.Waiting) {
                    engine.answer(
                        reviewed.id,
                        RequestAnswer.Permission(PermissionReply.Reject),
                        reviewed,
                    )
                }
            ReviewedRequestAction.AskAlways ->
                if (
                    reviewed is PendingRequest.Permission &&
                        card is RequestCardUi.Permission &&
                        card.canAlwaysAllow &&
                        card.state == CardState.Waiting
                ) {
                    local.update { it.copy(confirm = reviewed) }
                }
            is ReviewedRequestAction.Choose ->
                if (
                    reviewed is PendingRequest.Question &&
                        card is RequestCardUi.Question &&
                        card.state == CardState.Waiting &&
                        card.oneAnswer &&
                        action.option in card.options
                ) {
                    engine.answer(
                        reviewed.id,
                        RequestAnswer.Question(single(action.option)),
                        reviewed,
                    )
                }
            ReviewedRequestAction.OpenOther ->
                if (
                    reviewed is PendingRequest.Question &&
                        card is RequestCardUi.Question &&
                        card.state == CardState.Waiting &&
                        card.oneAnswer &&
                        card.custom
                ) {
                    local.update { it.copy(otherOpen = reviewed) }
                }
            is ReviewedRequestAction.EditOther ->
                if (reviewed is PendingRequest.Question && questionCard(card.id)?.other != null) {
                    local.update {
                        it.copy(
                            drafts =
                                it.drafts.putting(QuestionDraftKey(reviewed.question), action.text)
                        )
                    }
                }
            ReviewedRequestAction.SendOther ->
                if (reviewed is PendingRequest.Question) {
                    val other = questionCard(card.id)?.other
                    if (other?.canSend == true)
                        engine.answer(
                            reviewed.id,
                            RequestAnswer.Question(single(other.text.trim())),
                            reviewed,
                        )
                }
            ReviewedRequestAction.OpenSheet ->
                if (
                    reviewed is PendingRequest.Question &&
                        card is RequestCardUi.Question &&
                        !card.oneAnswer &&
                        card.state == CardState.Waiting
                ) {
                    local.update { it.copy(sheet = reviewed) }
                }
            ReviewedRequestAction.Undo -> engine.undo(reviewed.id, reviewed)
            ReviewedRequestAction.Retry ->
                if (card.state is CardState.NotAccepted)
                    scope.launch { engine.retry(reviewed.id, reviewed) }
        }
    }

    /** The slot as of now (actions decide on this, never on a frame that may be behind). */
    private fun current(): RequestSlotUi =
        slot(
            SessionRequests.of(engine.pending.value, session),
            capabilities.value,
            place.value,
            local.value,
        )

    private fun permissionCard(id: RequestId): RequestCardUi.Permission? =
        (current().card as? RequestCardUi.Permission)?.takeIf { it.id == id }

    private fun questionCard(id: RequestId): RequestCardUi.Question? =
        (current().card as? RequestCardUi.Question)?.takeIf {
            it.id == id && it.state == CardState.Waiting
        }

    private fun slot(
        requests: SessionRequests,
        caps: ServerCapabilities,
        where: RequestPlace,
        mine: Local,
    ): RequestSlotUi {
        val request = requests.current ?: return RequestSlotUi()
        val state = cardState(request.phase)
        val card =
            when (request) {
                is PendingRequest.Permission ->
                    RequestCardUi.Permission(
                        id = request.id,
                        waitingSinceMs = request.firstShownAtMs,
                        state = state,
                        ask =
                            PermissionAsk.of(
                                request.request,
                                project = where.project,
                                directory = where.directory,
                            ),
                        canAlwaysAllow =
                            caps.persistentPermissionGrants && request.request.canAlwaysAllow,
                        captured = request.copy(phase = AnswerPhase.Waiting),
                    )
                is PendingRequest.Question -> {
                    val prompt = request.question.prompts.firstOrNull()
                    val oneAnswer = request.oneAnswer
                    val custom = oneAnswer && prompt?.custom == true
                    RequestCardUi.Question(
                        id = request.id,
                        waitingSinceMs = request.firstShownAtMs,
                        state = state,
                        title = prompt?.title.orEmpty(),
                        question = prompt?.question.orEmpty(),
                        options =
                            if (oneAnswer)
                                prompt?.choices.orEmpty().map { it.label }.toImmutableList()
                            else persistentListOf(),
                        oneAnswer = oneAnswer,
                        custom = custom,
                        other =
                            if (custom && mine.otherOpen?.question === request.question)
                                OtherAnswerUi(
                                    mine.drafts[QuestionDraftKey(request.question)].orEmpty()
                                )
                            else null,
                        captured = request.copy(phase = AnswerPhase.Waiting),
                    )
                }
            }
        val confirm =
            (card as? RequestCardUi.Permission)
                ?.takeIf {
                    mine.confirm?.request === it.captured?.request &&
                        mine.confirm != null &&
                        it.canAlwaysAllow &&
                        it.state == CardState.Waiting
                }
                ?.let { AlwaysConfirmUi(it.id, it.ask.always, it.captured) }
        return RequestSlotUi(card, requests.moreWaiting, confirm)
    }

    private fun cardState(phase: AnswerPhase): CardState =
        when (phase) {
            AnswerPhase.Waiting -> CardState.Waiting
            is AnswerPhase.Held -> CardState.Held(words(phase.answer))
            is AnswerPhase.Sending -> CardState.Sending(words(phase.answer))
            is AnswerPhase.Sent -> CardState.Sent(words(phase.answer))
            is AnswerPhase.NotAccepted -> CardState.NotAccepted(words(phase.answer))
        }

    private fun words(answer: RequestAnswer): AnswerWords =
        when (answer) {
            is RequestAnswer.Permission ->
                when (answer.reply) {
                    PermissionReply.Once -> AnswerWords.Allowed
                    PermissionReply.Always -> AnswerWords.AlwaysAllowed
                    PermissionReply.Reject -> AnswerWords.Rejected
                }
            is RequestAnswer.Question ->
                AnswerWords.Chose(answer.answers.flatten().joinToString(", "))
            RequestAnswer.Decline -> AnswerWords.Dismissed
        }

    private fun single(answer: String) = persistentListOf(persistentListOf(answer))
}
