package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of the correlated (OpenCode 1) group of `test/command_receipt_transport_test.dart` (row B-PROTO-041). */
class CommandReceiptTransportTest {
    private class Correlated(
        val fake: FakeServerGateway = FakeServerGateway(),
        var page: (String?) -> ServerPage<MessageWithParts> = { ServerPage(persistentListOf(), null) },
    ) : ServerGateway by fake, CorrelatedPromptGateway {
        val sends = mutableListOf<Triple<String, String, String>>()
        val cursors = mutableListOf<String?>()

        override fun createPromptMessageId(): MessageId = MessageId("msg_generated")

        override suspend fun promptWithMessageId(session: SessionId, messageId: MessageId, prompt: OutgoingPrompt, beforeSend: () -> Boolean): Outcome<PromptAdmission> {
            if (!beforeSend()) return Outcome.Failed(Failure.Unexpected())
            sends += Triple(session.raw, messageId.raw, prompt.text)
            return PromptAdmission.Confirmed(messageId.raw).ok()
        }

        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            cursors += cursor
            return page(cursor).ok()
        }
    }

    private fun gateway(enabled: Boolean = true) = Correlated().apply {
        fake.capabilities.value = ServerCapabilities(clientPromptMessageID = enabled, commandReceipts = enabled)
        fake.events.connect()
    }

    private fun message(id: String = "msg_1", session: String = "ses_1", role: Role = Role.User) =
        MessageWithParts(MessageInfo(id = id, sessionId = session, role = role), persistentListOf())

    private fun link(g: Correlated) = GatewaySendLink(P1, "http://127.0.0.1:4097", g)

    private suspend fun lookup(g: Correlated) = link(g).lookupAdmission(SessionId("ses_1"), MessageId("msg_1"))

    @Test
    fun `B-PROTO-041 dispatch supplies the caller's command id to the existing prompt transport`() = runTest {
        val g = gateway()
        val link = link(g)
        assertTrue(link.correlated)
        link.prompt(SessionId("ses_1"), OutgoingPrompt("Ask", clientMessageId = MessageId("msg_1"))) { true }
        assertEquals(listOf(Triple("ses_1", "msg_1", "Ask")), g.sends)
        assertTrue(g.fake.sentPrompts.isEmpty())
    }

    @Test
    fun `B-PROTO-041 the exact user message id on an older page confirms admission`() = runTest {
        val g = gateway()
        g.page = { cursor ->
            if (cursor == null) ServerPage(persistentListOf(message(id = "another")), "older")
            else ServerPage(persistentListOf(message()), null)
        }
        assertEquals(Outcome.Ok(true), lookup(g))
        assertEquals(listOf(null, "older"), g.cursors)
        assertTrue(g.sends.isEmpty(), "a lookup never sends")
    }

    @Test
    fun `B-PROTO-041 matching text with a wrong id, session or role does not confirm`() = runTest {
        for (wrong in listOf(message(role = Role.Assistant), message(session = "ses_other"), message(id = "msg_other"))) {
            val g = gateway()
            g.page = { ServerPage(persistentListOf(wrong), null) }
            assertEquals(Outcome.Ok(false), lookup(g))
            assertTrue(g.sends.isEmpty())
        }
    }

    @Test
    fun `B-PROTO-041 paging is bounded - a repeated continuation stops and changing ones end at 100 pages`() = runTest {
        val same = gateway()
        same.page = { ServerPage(persistentListOf(), "same") }
        assertEquals(Outcome.Ok(false), lookup(same))
        assertEquals(listOf(null, "same"), same.cursors)

        val changing = gateway()
        changing.page = { ServerPage(persistentListOf(), "page_${changing.cursors.size}") }
        assertEquals(Outcome.Ok(false), lookup(changing))
        assertEquals(GatewaySendLink.MAX_PAGES, changing.cursors.size)
        assertEquals(100, GatewaySendLink.MAX_PAGES)
    }

    @Test
    fun `B-PROTO-041 with the capability off nothing is dispatched with an id and no lookup is made`() = runTest {
        val g = gateway(enabled = false)
        val link = link(g)
        assertFalse(link.correlated)
        assertNull(link.newClientMessageId(), "no receipt id is minted, so no receipt dispatch happens")
        assertEquals(Outcome.Ok(false), lookup(g))
        assertTrue(g.sends.isEmpty())
        assertTrue(g.cursors.isEmpty())
    }

    @Test
    fun `B-PROTO-041 a closed connection cannot confirm a receipt`() = runTest {
        val g = gateway()
        g.fake.events.status.value = StreamStatus.Stopped(Failure.Unexpected())
        assertEquals(Outcome.Ok(false), lookup(g))
        assertTrue(g.cursors.isEmpty())
    }
}
