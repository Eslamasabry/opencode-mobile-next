package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.Outcome
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
import io.github.eslamasabry.oc.engine.requests.questions.PromptDraft
import io.github.eslamasabry.oc.engine.requests.questions.QuestionPresentation
import io.github.eslamasabry.oc.engine.requests.questions.QuestionSheet
import kotlinx.collections.immutable.ImmutableList
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
) : ChatRequestActions, QuestionSheetActions {
    private data class Local(
        val confirm: RequestId? = null,
        val otherOpen: RequestId? = null,
        val drafts: PersistentMap<RequestId, String> = persistentMapOf(),
        val sheet: RequestId? = null,
        val questionDrafts: Map<RequestId, ImmutableList<PromptDraft>> = emptyMap(),
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
     * The request whose sheet the person asked for; [state] carries its current questions and draft
     * answers.
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
        local.update { it.copy(sheet = card.id) }
    }

    override fun closeSheet(id: RequestId) {
        local.update { if (it.sheet == id) it.copy(sheet = null) else it }
    }

    override fun chooseInSheet(id: RequestId, prompt: Int, label: String) {
        updateSheet(id, prompt) { question, draft ->
            if (question.choices.any { it.label == label })
                QuestionSheet.choose(question, draft, label)
            else draft
        }
    }

    override fun editSheetOther(id: RequestId, prompt: Int, text: String) {
        updateSheet(id, prompt) { question, draft ->
            if (question.custom) QuestionSheet.write(question, draft, text) else draft
        }
    }

    private fun updateSheet(
        id: RequestId,
        prompt: Int,
        edit: (io.github.eslamasabry.oc.core.model.QuestionPrompt, PromptDraft) -> PromptDraft,
    ) {
        val sheet = current().sheet?.takeIf { it.question.id == id } ?: return
        val question = sheet.question.prompts.getOrNull(prompt) ?: return
        val changed =
            sheet.drafts
                .mapIndexed { index, draft ->
                    if (index == prompt) edit(question, draft) else draft
                }
                .toImmutableList()
        local.update { it.copy(questionDrafts = it.questionDrafts + (id to changed)) }
    }

    override fun sendSheet(id: RequestId) {
        val sheet = current().sheet?.takeIf { it.question.id == id } ?: return
        val answers = QuestionSheet.answers(sheet.question, sheet.drafts) ?: return
        if (engine.answer(id, RequestAnswer.Question(answers)) is Outcome.Ok) {
            local.update { it.copy(sheet = null) }
        }
    }

    private fun sheetRequest(id: RequestId): PendingRequest.Question? =
        (engine.pending.value.firstOrNull { it.id == id && it.sessionId == session }
                as? PendingRequest.Question)
            ?.takeIf {
                current().questionSheet?.question?.id == id && it.phase == AnswerPhase.Waiting
            }

    override fun chooseSheet(id: RequestId, prompt: Int, label: String) {
        val question = sheetRequest(id)?.question ?: return
        val spec = question.prompts.getOrNull(prompt) ?: return
        if (spec.choices.none { it.label == label }) return
        local.update { mine ->
            val drafts =
                (mine.sheetDrafts[id] ?: QuestionSheet.emptyDraft(question)).toMutableList()
            sized(drafts, question.prompts.size)
            drafts[prompt] = QuestionSheet.choose(spec, drafts[prompt], label)
            mine.copy(sheetDrafts = mine.sheetDrafts.put(id, drafts.toImmutableList()))
        }
    }

    override fun writeSheet(id: RequestId, prompt: Int, text: String) {
        val question = sheetRequest(id)?.question ?: return
        val spec = question.prompts.getOrNull(prompt)?.takeIf { it.custom } ?: return
        local.update { mine ->
            val drafts =
                (mine.sheetDrafts[id] ?: QuestionSheet.emptyDraft(question)).toMutableList()
            sized(drafts, question.prompts.size)
            drafts[prompt] = QuestionSheet.write(spec, drafts[prompt], text)
            mine.copy(sheetDrafts = mine.sheetDrafts.put(id, drafts.toImmutableList()))
        }
    }

    override fun sendSheet(id: RequestId) {
        val question = sheetRequest(id)?.question ?: return
        val drafts = local.value.sheetDrafts[id] ?: QuestionSheet.emptyDraft(question)
        val answers = QuestionSheet.answers(question, drafts) ?: return
        // The engine's own check is stricter (no repeated values): the sheet stays open, with the
        // person's words, unless the answer was taken.
        if (engine.answer(id, RequestAnswer.Question(answers)) is Outcome.Ok)
            local.update { it.copy(sheet = null) }
    }

    override fun closeSheet() {
        local.update { it.copy(sheet = null) }
    }

    override fun undo(id: RequestId) {
        if (current().card?.id == id) engine.undo(id)
    }

    override fun retry(id: RequestId) {
        if (current().card?.state !is CardState.NotAccepted || current().card?.id != id) return
        scope.launch { engine.retry(id) }
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
                    )
                is PendingRequest.Question -> {
                    val prompt = request.question.prompts.firstOrNull()
                    val oneAnswer =
                        request.oneAnswer &&
                            QuestionSheet.presentation(request.question) ==
                                QuestionPresentation.InPlace
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
        val sheet =
            (request as? PendingRequest.Question)
                ?.takeIf { mine.sheet == it.id && state == CardState.Waiting }
                ?.let {
                    val drafts =
                        mine.questionDrafts[it.id]?.takeIf { drafts ->
                            drafts.size == it.question.prompts.size
                        } ?: QuestionSheet.emptyDraft(it.question)
                    QuestionSheetUi(
                        it.question,
                        drafts,
                        QuestionSheet.block(it.question, drafts) == null,
                    )
                }
        return RequestSlotUi(card, requests.moreWaiting, confirm, sheet)
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

/** Drafts kept from before a refetch match the question's prompts again (one per prompt). */
private fun sized(drafts: MutableList<PromptDraft>, size: Int) {
    while (drafts.size > size) drafts.removeAt(drafts.lastIndex)
    while (drafts.size < size) drafts.add(PromptDraft())
}
