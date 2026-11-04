package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The folder the chat works in: its name ends a shell question, its path makes absolute paths read
 * from it.
 */
public data class RequestPlace(val project: String? = null, val directory: String? = null)

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
        val confirm: RequestId? = null,
        val focused: RequestId? = null,
        val otherOpen: RequestId? = null,
        val drafts: PersistentMap<RequestId, String> = persistentMapOf(),
        val sheet: RequestId? = null,
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<RequestSlotUi> =
        combine(engine.pending, capabilities, place, local) { all, caps, where, mine ->
                slot(selected(all, mine.focused), caps, where, mine)
            }
            .stateIn(scope, SharingStarted.Eagerly, current())

    /**
     * The request whose sheet the person asked for (a longer question); the sheet is S3-REQFORMS.
     */
    public val sheetFor: StateFlow<RequestId?> =
        local.map { it.sheet }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Leaving the chat sends its held answers now (plan 09 §5.1). */
    public suspend fun leave() {
        engine.flushHeld(session)
    }

    override fun shown(id: RequestId) {
        if (current().card?.id == id) engine.markShown(id)
    }

    override fun allowOnce(id: RequestId) {
        permissionCard(id)?.let {
            engine.answer(id, RequestAnswer.Permission(PermissionReply.Once))
        }
    }

    override fun reject(id: RequestId) {
        permissionCard(id)?.let {
            engine.answer(id, RequestAnswer.Permission(PermissionReply.Reject))
        }
    }

    override fun askAlwaysAllow(id: RequestId) {
        val card = permissionCard(id) ?: return
        if (card.canAlwaysAllow && card.state == CardState.Waiting)
            local.update { it.copy(confirm = id) }
    }

    override fun confirmAlwaysAllow() {
        // Read before closing: an answer that landed meanwhile, or a request that left, closed the
        // confirm already.
        val confirm = current().confirmAlways
        local.update { it.copy(confirm = null) }
        if (confirm != null)
            engine.answer(confirm.id, RequestAnswer.Permission(PermissionReply.Always))
    }

    override fun cancelAlwaysAllow() {
        local.update { it.copy(confirm = null) }
    }

    override fun choose(id: RequestId, option: String) {
        val card = questionCard(id) ?: return
        if (card.oneAnswer && option in card.options)
            engine.answer(id, RequestAnswer.Question(single(option)))
    }

    override fun openOther(id: RequestId) {
        val card = questionCard(id) ?: return
        if (card.oneAnswer && card.custom) local.update { it.copy(otherOpen = id) }
    }

    override fun editOther(id: RequestId, text: String) {
        if (questionCard(id)?.other != null)
            local.update { it.copy(drafts = it.drafts.putting(id, text)) }
    }

    override fun sendOther(id: RequestId) {
        val other = questionCard(id)?.other ?: return
        if (other.canSend) engine.answer(id, RequestAnswer.Question(single(other.text.trim())))
    }

    override fun openSheet(id: RequestId) {
        val card = questionCard(id) ?: return
        if (!card.oneAnswer) local.update { it.copy(sheet = id) }
    }

    override fun undo(id: RequestId) {
        if (current().card?.id == id) engine.undo(id)
    }

    override fun retry(id: RequestId) {
        if (current().card?.state !is CardState.NotAccepted || current().card?.id != id) return
        scope.launch { engine.retry(id) }
    }

    /** The slot as of now (actions decide on this, never on a frame that may be behind). */
    /**
     * A monitor/deep link chooses one exact waiting card. This is display-only and never answers
     * it.
     */
    public fun focus(id: RequestId): Unit = local.update { it.copy(focused = id) }

    private fun selected(all: List<PendingRequest>, focused: RequestId?): SessionRequests {
        val mine = all.filter { it.sessionId == session }
        return if (mine.isEmpty()) SessionRequests.None
        else SessionRequests(mine.firstOrNull { it.id == focused } ?: mine.first(), mine.size - 1)
    }

    private fun current(): RequestSlotUi =
        slot(
            selected(engine.pending.value, local.value.focused),
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
                            if (custom && mine.otherOpen == request.id)
                                OtherAnswerUi(mine.drafts[request.id].orEmpty())
                            else null,
                    )
                }
            }
        val confirm =
            (card as? RequestCardUi.Permission)
                ?.takeIf {
                    mine.confirm == it.id && it.canAlwaysAllow && it.state == CardState.Waiting
                }
                ?.let { AlwaysConfirmUi(it.id, it.ask.always) }
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
