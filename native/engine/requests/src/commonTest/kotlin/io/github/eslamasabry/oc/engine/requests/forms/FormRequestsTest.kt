package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** OpenCode 2 forms of one connection (plan 09 §5.1 hold/receipts, §5.5 error contract; WP-04.11). */
@OptIn(ExperimentalCoroutinesApi::class)
class FormRequestsTest {
    private val nameField = """{"key":"name","type":"string","required":true,"maxLength":40}"""
    private val form1 = wireForm(id = "frm_1", session = "ses_1", fields = arrayOf(nameField))
    private val form2 = wireForm(id = "frm_2", session = "ses_2", fields = arrayOf(nameField))
    private val named = mapOf("name" to FormValue.Text("Ada"))

    private fun TestScope.engine(gateway: ScriptedForms) = FormRequests(gateway, backgroundScope)

    @Test
    fun `B-PROTO-044 a created event adds the form, a settled event removes it, a deleted session drops its forms`() =
        runTest {
            val forms = engine(ScriptedForms())
            forms.onEvent(GatewayEvent.FormCreated(form1))
            forms.onEvent(GatewayEvent.FormCreated(form2))
            forms.onEvent(GatewayEvent.FormCreated(form1)) // a repeat never duplicates
            assertEquals(listOf("frm_1", "frm_2"), forms.pending.value.map { it.id.raw }, "oldest first")
            assertEquals(listOf("frm_2"), forms.inSession(SessionId("ses_2")).map { it.id.raw })

            forms.onEvent(GatewayEvent.FormSettled(SessionId("ses_1"), FormId("frm_1"), answered = true))
            assertEquals(listOf("frm_2"), forms.pending.value.map { it.id.raw })
            forms.onEvent(GatewayEvent.SessionDeleted(SessionId("ses_2")))
            assertTrue(forms.pending.value.isEmpty())
        }

    @Test
    fun `B-PROTO-044 an answer is held 3 s, then posted once and the form settles`() = runTest {
        val gateway = ScriptedForms()
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        assertEquals(FormAnswerResult.Held, forms.answer(FormId("frm_1"), named))
        assertIs<AnswerPhase.Held<FormReply>>(forms.phases.value[FormId("frm_1")])
        advanceTimeBy(2.9.seconds)
        assertTrue(gateway.replies.isEmpty(), "nothing leaves inside the Undo window")
        advanceTimeBy(0.2.seconds)
        runCurrent()
        assertEquals(1, gateway.replies.size)
        assertEquals("""{name="Ada"}""", gateway.replies.single().second.values.toString())
        assertTrue(forms.pending.value.isEmpty(), "an accepted answer settles the form")
        assertTrue(forms.phases.value.isEmpty())
    }

    @Test
    fun `B-CHAT-074 a second answer while one is held or sending is ignored, first decision wins`() = runTest {
        val gateway = ScriptedForms()
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.answer(FormId("frm_1"), named)
        assertEquals(FormAnswerResult.Ignored, forms.decline(FormId("frm_1")))
        assertEquals(FormAnswerResult.Ignored, forms.answer(FormId("frm_1"), mapOf("name" to FormValue.Text("Bob"))))
        advanceTimeBy(4.seconds)
        assertEquals(1, gateway.replies.size + gateway.cancels.size, "exactly one write")
        assertEquals("""{name="Ada"}""", gateway.replies.single().second.values.toString())
    }

    @Test
    fun `CHAT-GENUI-43 Undo inside the hold sends nothing and the form waits again`() = runTest {
        val gateway = ScriptedForms()
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.answer(FormId("frm_1"), named)
        advanceTimeBy(1.seconds)
        assertTrue(forms.undo(FormId("frm_1")))
        advanceTimeBy(10.seconds)
        assertTrue(gateway.replies.isEmpty())
        assertEquals(listOf("frm_1"), forms.pending.value.map { it.id.raw })
        assertEquals(FormAnswerResult.Held, forms.answer(FormId("frm_1"), named), "it can be answered again")
    }

    @Test
    fun `B-CHAT-072 an invalid draft never reaches the wire`() = runTest {
        val gateway = ScriptedForms()
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        assertEquals(FormAnswerResult.Invalid, forms.answer(FormId("frm_1"), emptyMap()))
        assertEquals(FormAnswerResult.Ignored, forms.answer(FormId("frm_unknown"), named))
        advanceTimeBy(10.seconds)
        assertTrue(gateway.replies.isEmpty())
    }

    @Test
    fun `B-PROTO-044 a 400 invalid answer keeps the form pending with the reason`() = runTest {
        val gateway = ScriptedForms().apply { next = Failure.Rejected(400, WireTag("FormInvalidAnswerError")).failed() }
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.answer(FormId("frm_1"), named)
        advanceTimeBy(4.seconds)
        assertEquals(listOf("frm_1"), forms.pending.value.map { it.id.raw })
        val refused = assertIs<AnswerPhase.Refused<FormReply>>(forms.phases.value[FormId("frm_1")])
        assertIs<Failure.Rejected>(refused.failure)
        assertEquals(FormAnswerResult.Held, forms.answer(FormId("frm_1"), named), "Send answers is the retry")
    }

    @Test
    fun `B-PROTO-044 an already-settled reply resolves locally and says once it was answered elsewhere`() = runTest {
        val gateway = ScriptedForms().apply { next = Failure.Conflict(WireTag("FormAlreadySettledError")).failed() }
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.answer(FormId("frm_1"), named)
        advanceTimeBy(4.seconds)
        assertTrue(forms.pending.value.isEmpty())
        val notice = forms.notices.value.single()
        assertEquals(FormNotice.AnsweredElsewhere(FormId("frm_1"), "Deploy settings"), notice)
        forms.acknowledge(notice)
        assertTrue(forms.notices.value.isEmpty())
    }

    @Test
    fun `B-PROTO-048 a late failure after the server settled the form shows no error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = ScriptedForms().apply {
            this.gate = gate
            next = Failure.Network().failed()
        }
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.answer(FormId("frm_1"), named)
        advanceTimeBy(4.seconds)
        assertIs<AnswerPhase.Sending<FormReply>>(forms.phases.value[FormId("frm_1")])
        forms.onEvent(GatewayEvent.FormSettled(SessionId("ses_1"), FormId("frm_1"), answered = true))
        gate.complete(Unit)
        runCurrent()
        assertTrue(forms.pending.value.isEmpty())
        assertTrue(forms.phases.value.isEmpty(), "no Not accepted line for a form that already left")
    }

    @Test
    fun `STATE-10 a send that takes over 8 s says Not confirmed yet`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = ScriptedForms().apply { this.gate = gate }
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.answer(FormId("frm_1"), named)
        advanceTimeBy(3.seconds + 7.seconds)
        assertEquals(false, (forms.phases.value[FormId("frm_1")] as AnswerPhase.Sending).slow)
        advanceTimeBy(2.seconds)
        assertEquals(true, (forms.phases.value[FormId("frm_1")] as AnswerPhase.Sending).slow)
        gate.complete(Unit)
        runCurrent()
        assertTrue(forms.pending.value.isEmpty())
    }

    @Test
    fun `WP-04_11 forms come back after a forced reconnect and a held answer reaches the server once`() = runTest {
        val gateway = ScriptedForms(listed = listOf(form1, form2))
        val forms = engine(gateway)
        // The stream dropped before FormCreated arrived: asks are never replayed, the refetch hydrates them.
        forms.refresh()
        assertEquals(listOf("frm_1", "frm_2"), forms.pending.value.map { it.id.raw })

        forms.answer(FormId("frm_1"), named)
        // Forced reconnect inside the Undo window: the refetch must not drop or resend the held answer.
        forms.refresh()
        forms.refresh()
        // BackgroundIdle: held answers go now.
        forms.flushHeld(null)
        runCurrent()
        advanceTimeBy(10.seconds)
        assertEquals(listOf("frm_1"), gateway.replies.map { it.first.raw }, "exactly once across the reconnect")

        // Answered on another device while we were away: gone after refetch.
        gateway.listed = emptyList()
        forms.refresh()
        assertTrue(forms.pending.value.isEmpty())
    }

    @Test
    fun `B-PROTO-048 a failed refetch keeps what is known`() = runTest {
        val gateway = ScriptedForms(listed = listOf(form1))
        val forms = engine(gateway)
        forms.refresh()
        gateway.listFails = Outcome.Failed(Failure.Network())
        forms.refresh()
        assertEquals(listOf("frm_1"), forms.pending.value.map { it.id.raw })
    }

    @Test
    fun `CHAT-GENUI-43 leaving one chat sends only its held answers`() = runTest {
        val gateway = ScriptedForms()
        val forms = engine(gateway)
        forms.onEvent(GatewayEvent.FormCreated(form1))
        forms.onEvent(GatewayEvent.FormCreated(form2))
        forms.answer(FormId("frm_1"), named)
        forms.decline(FormId("frm_2"))
        forms.flushHeld(SessionId("ses_2"))
        runCurrent()
        assertEquals(listOf("frm_2"), gateway.cancels.map { it.raw })
        assertTrue(gateway.replies.isEmpty())
        advanceTimeBy(4.seconds)
        assertEquals(listOf("frm_1"), gateway.replies.map { it.first.raw })
        assertEquals(listOf("frm_2"), gateway.cancels.map { it.raw }, "the flushed decline is never sent twice")
    }

    @Test
    fun `B-AGENT-221 forms are never answered without a person`() = runTest {
        val gateway = ScriptedForms(listed = listOf(form1))
        val forms = engine(gateway)
        forms.refresh()
        forms.onEvent(GatewayEvent.FormCreated(form2))
        advanceTimeBy(120.seconds)
        assertTrue(gateway.replies.isEmpty() && gateway.cancels.isEmpty())
    }
}
