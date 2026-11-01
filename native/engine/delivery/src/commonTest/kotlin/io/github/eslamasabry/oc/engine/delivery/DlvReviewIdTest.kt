package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DlvReviewIdTest {
    @Test
    fun `B-STATE-043 DLV-5 check again uses the local id returned by send`() = runTest {
        val w = World(Disk(), this, link = FakeLink(capabilities = ServerCapabilities(commandReceipts = true, clientPromptMessageID = true), correlated = true))
        w.link.answer = { FakeLink.Answer.LostResponse }
        val d = w.delivery()
        val id = (d.send(req("hello")) as Outcome.Ok).value
        testScheduler.runCurrent()
        assertTrue(d.pending.value.isEmpty())
        assertEquals(Outcome.Ok(ReceiptLook.Confirmed), d.checkAgain(id))
        assertTrue(w.queue.entries().isEmpty())
        assertEquals(1, w.link.attempts.size)
    }

    @Test
    fun `B-STATE-044 DLV-5 explicit resend uses the local id returned by send`() = runTest {
        val w = World(Disk(), this)
        w.link.answer = { FakeLink.Answer.LostResponse }
        val d = w.delivery()
        val id = (d.send(req("hello")) as Outcome.Ok).value
        testScheduler.runCurrent()
        assertEquals(Outcome.Ok(true), d.resend(id, confirmedByPerson = true))
        w.link.answer = { FakeLink.Answer.Confirmed }
        testScheduler.runCurrent()
        assertEquals(2, w.link.attempts.size, "only the explicit resend permits a second attempt")
        assertTrue(w.restart().queue.entries().isEmpty())
    }
}
