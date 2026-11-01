package io.github.eslamasabry.oc.engine.requests.questions

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.QuestionGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.QuestionChoice
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import io.github.eslamasabry.oc.engine.requests.forms.AnswerPhase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Multi-question asks (plan 09 §5.4, map CHAT-GENUI-47; Dart `question_sheet.dart`, `connection/questions.dart`). */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionsTest {
    private fun prompt(
        multiple: Boolean = false,
        custom: Boolean = false,
        optional: Boolean = false,
        vararg labels: String,
    ) = QuestionPrompt(
        title = "Q",
        question = "Which?",
        multiple = multiple,
        custom = custom,
        optional = optional,
        choices = labels.map { QuestionChoice(it, "") }.toImmutableList(),
        markdown = false,
    )

    private fun question(id: String = "que_1", session: String = "ses_1", vararg prompts: QuestionPrompt) =
        PendingQuestion(RequestId(id), SessionId(session), prompts.toList().toImmutableList())

    private val single = prompt(custom = true, labels = arrayOf("Yes", "No"))
    private val many = prompt(multiple = true, custom = true, labels = arrayOf("A", "B", "C"))

    @Test
    fun `CHAT-GENUI-47 single-select replaces the choice and clears own words, multi-select keeps them`() {
        var d = QuestionSheet.write(single, PromptDraft(), "maybe")
        d = QuestionSheet.choose(single, d, "Yes")
        d = QuestionSheet.choose(single, d, "No")
        assertEquals(listOf("No"), QuestionSheet.answerOf(single, d))
        d = QuestionSheet.write(single, d, "  later ")
        assertEquals(listOf("later"), QuestionSheet.answerOf(single, d), "own words replace the chosen option")

        var m = QuestionSheet.write(many, PromptDraft(), "D")
        m = QuestionSheet.choose(many, m, "C")
        m = QuestionSheet.choose(many, m, "A")
        assertEquals(listOf("A", "C", "D"), QuestionSheet.answerOf(many, m), "offered order, then own words")
        m = QuestionSheet.choose(many, m, "A")
        assertEquals(listOf("C", "D"), QuestionSheet.answerOf(many, m))
    }

    @Test
    fun `B-CHAT-072 required answers cannot be empty and values not offered never send`() {
        val optional = prompt(optional = true, labels = arrayOf("x"))
        val closed = prompt(labels = arrayOf("x"))
        val q = question(prompts = arrayOf(single, optional, closed))
        assertEquals(QuestionBlock.Unanswered(0), QuestionSheet.block(q, QuestionSheet.emptyDraft(q)))
        val drafts = listOf(PromptDraft(chosen = persistentListOf("Yes")), PromptDraft(), PromptDraft(chosen = persistentListOf("forged")))
        assertEquals(QuestionBlock.NotOffered(2), QuestionSheet.block(q, drafts))
        assertNull(QuestionSheet.answers(q, drafts))
        val ok = drafts.toMutableList().also { it[2] = PromptDraft(chosen = persistentListOf("x")) }
        assertEquals<List<List<String>>?>(listOf(listOf("Yes"), emptyList<String>(), listOf("x")), QuestionSheet.answers(q, ok))
        // Own words on a prompt that takes none are dropped, so they cannot answer it.
        val noOwn = question(prompts = arrayOf(closed))
        assertEquals(QuestionBlock.Unanswered(0), QuestionSheet.block(noOwn, listOf(QuestionSheet.write(closed, PromptDraft(), "hi"))))
    }

    @Test
    fun `CHAT-GENUI-47 one answer goes in place, several answers open the request sheet, longer asks the full sheet`() {
        assertEquals(QuestionPresentation.InPlace, QuestionSheet.presentation(question(prompts = arrayOf(single))))
        val pickMany = prompt(multiple = true, labels = arrayOf("A", "B"))
        assertEquals(QuestionPresentation.RequestSheet, QuestionSheet.presentation(question(prompts = arrayOf(pickMany))))
        assertEquals(QuestionPresentation.FullSheet, QuestionSheet.presentation(question(prompts = arrayOf(many))))
        assertEquals(QuestionPresentation.FullSheet, QuestionSheet.presentation(question(prompts = arrayOf(single, single))))
        val longOption = prompt(labels = arrayOf("x".repeat(81)))
        assertEquals(QuestionPresentation.FullSheet, QuestionSheet.presentation(question(prompts = arrayOf(longOption))))
    }

    private class ScriptedQuestions(var listed: List<PendingQuestion>) : QuestionGateway {
        val answered = mutableListOf<Triple<SessionId, RequestId, List<List<String>>>>()
        val rejected = mutableListOf<RequestId>()
        var next: Outcome<Unit> = Unit.ok()

        override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = listed.toImmutableList().ok()

        override suspend fun answerQuestion(
            session: SessionId,
            request: RequestId,
            answers: ImmutableList<ImmutableList<String>>,
        ): Outcome<Unit> {
            answered += Triple(session, request, answers)
            return next
        }

        override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> {
            rejected += request
            return next
        }
    }

    @Test
    fun `B-CHAT-072 answers use exact typed values, once, and a wrong session never sends`() = runTest {
        val q = question(prompts = arrayOf(single, many))
        val gateway = ScriptedQuestions(listOf(q))
        val engine = QuestionRequests(gateway, backgroundScope)
        engine.refresh()
        val drafts = listOf(PromptDraft(chosen = persistentListOf("Yes")), PromptDraft(chosen = persistentListOf("B"), own = "Z"))
        assertEquals(QuestionAnswerResult.WrongSession, engine.answer(q.id, SessionId("ses_other"), drafts))
        assertEquals(QuestionAnswerResult.Invalid, engine.answer(q.id, q.sessionId, listOf(PromptDraft(), PromptDraft())))
        assertEquals(QuestionAnswerResult.Held, engine.answer(q.id, q.sessionId, drafts))
        assertEquals(QuestionAnswerResult.Ignored, engine.dismiss(q.id, q.sessionId), "first decision wins")
        advanceTimeBy(4.seconds)
        assertEquals(listOf(Triple(q.sessionId, q.id, listOf(listOf("Yes"), listOf("B", "Z")))), gateway.answered)
        assertTrue(gateway.rejected.isEmpty())
        assertTrue(engine.pending.value.isEmpty())
    }

    @Test
    fun `B-PROTO-048 QuestionNotFound resolves locally, other failures keep the question with the reason`() = runTest {
        val q1 = question(id = "que_1", prompts = arrayOf(single))
        val q2 = question(id = "que_2", prompts = arrayOf(single))
        val gateway = ScriptedQuestions(listOf(q1, q2))
        val engine = QuestionRequests(gateway, backgroundScope)
        engine.refresh()
        gateway.next = Failure.NotFound(WireTag("QuestionNotFoundError"), q1.id).failed()
        engine.dismiss(q1.id, q1.sessionId)
        advanceTimeBy(4.seconds)
        assertEquals(listOf("que_2"), engine.pending.value.map { it.id.raw }, "gone with no error")
        assertTrue(engine.phases.value.isEmpty())

        gateway.next = Failure.Server(500).failed()
        engine.answer(q2.id, q2.sessionId, listOf(PromptDraft(chosen = persistentListOf("No"))))
        advanceTimeBy(4.seconds)
        assertIs<AnswerPhase.Refused<QuestionReply>>(engine.phases.value[q2.id])
        assertEquals(listOf("que_2"), engine.pending.value.map { it.id.raw })
    }

    @Test
    fun `B-PROTO-044 a deleted conversation drops its questions and Undo sends nothing`() = runTest {
        val q1 = question(id = "que_1", session = "ses_1", prompts = arrayOf(single))
        val q2 = question(id = "que_2", session = "ses_2", prompts = arrayOf(single))
        val gateway = ScriptedQuestions(listOf(q1, q2))
        val engine = QuestionRequests(gateway, backgroundScope)
        engine.refresh()
        engine.answer(q2.id, q2.sessionId, listOf(PromptDraft(chosen = persistentListOf("Yes"))))
        assertTrue(engine.undo(q2.id))
        engine.onEvent(GatewayEvent.SessionDeleted(SessionId("ses_1")))
        advanceTimeBy(10.seconds)
        assertEquals(listOf("que_2"), engine.pending.value.map { it.id.raw })
        assertTrue(gateway.answered.isEmpty())
    }
}
