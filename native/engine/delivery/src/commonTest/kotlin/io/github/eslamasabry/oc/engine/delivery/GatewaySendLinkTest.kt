package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.InboxItem
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.engine.delivery.port.GatewaySendLink
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The production [GatewaySendLink] over a gateway and its facets. */
class GatewaySendLinkTest {
    private class Gateway(
        val fake: FakeServerGateway = FakeServerGateway(),
        var inboxItems: List<InboxItem> = emptyList(),
        var pages: Map<String?, ServerPage<MessageWithParts>> = mapOf(null to ServerPage(persistentListOf(), null)),
    ) : ServerGateway by fake, CorrelatedPromptGateway {
        val correlatedSends = mutableListOf<MessageId>()
        val reads = mutableListOf<String>()

        override fun createPromptMessageId(): MessageId = MessageId("msg_04acb4989000AAAAAAAAAAAAAA")

        override suspend fun promptWithMessageId(session: SessionId, messageId: MessageId, prompt: OutgoingPrompt, beforeSend: () -> Boolean): Outcome<PromptAdmission> {
            if (!beforeSend()) return io.github.eslamasabry.oc.core.common.Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope).let { Outcome.Failed(it) }
            correlatedSends += messageId
            return PromptAdmission.Confirmed(messageId.raw).ok()
        }

        override suspend fun inbox(session: SessionId): Outcome<ImmutableList<InboxItem>> {
            reads += "GET inbox"
            return persistentListOf(*inboxItems.toTypedArray()).ok()
        }

        override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
            reads += "GET page $cursor"
            return pages.getValue(cursor).ok()
        }
    }

    private fun user(id: String, session: String = "ses_1", role: Role = Role.User) =
        MessageWithParts(MessageInfo(id = id, sessionId = session, role = role), persistentListOf())

    private fun item(id: String, type: String = "user", session: String = "ses_1") =
        InboxItem(InboxItemId(id), SessionId(session), null, type, JsonObject(emptyMap()), null)

    @Test
    fun `B-PROTO-042 a pending inbox item confirms without reading a message, a promoted user message confirms after`() = runTest {
        val g = Gateway(inboxItems = listOf(item("msg_1")))
        g.fake.capabilities.value = ServerCapabilities(inbox = true, clientPromptMessageID = true, commandReceipts = true)
        val link = GatewaySendLink(P1, "http://127.0.0.1:4097", g)
        assertEquals(Outcome.Ok(true), link.lookupAdmission(S1, MessageId("msg_1")))
        assertEquals(listOf("GET inbox"), g.reads)

        g.inboxItems = emptyList()
        g.pages = mapOf(null to ServerPage(persistentListOf(user("msg_other")), "c2"), "c2" to ServerPage(persistentListOf(user("msg_1")), null))
        assertEquals(Outcome.Ok(true), link.lookupAdmission(S1, MessageId("msg_1")))
        assertTrue(g.fake.sentPrompts.isEmpty() && g.correlatedSends.isEmpty(), "a lookup never posts")
    }

    @Test
    fun `B-PROTO-135 another inbox type, session, an assistant message or a looping cursor never confirms`() = runTest {
        val g = Gateway(inboxItems = listOf(item("msg_1", type = "shell"), item("msg_1", session = "ses_other"), item("msg_1", type = "synthetic")))
        g.fake.capabilities.value = ServerCapabilities(inbox = true, commandReceipts = true)
        g.pages = mapOf(null to ServerPage(persistentListOf(user("msg_1", role = Role.Assistant), user("msg_1", session = "ses_other")), "loop"),
            "loop" to ServerPage(persistentListOf(), "loop"))
        val link = GatewaySendLink(P1, "http://h", g)
        assertEquals(Outcome.Ok(false), link.lookupAdmission(S1, MessageId("msg_1")))
    }

    @Test
    fun `B-PROTO-042 a client id goes through the correlated facet, and a refused fence posts nothing`() = runTest {
        val g = Gateway()
        g.fake.capabilities.value = ServerCapabilities(clientPromptMessageID = true)
        val link = GatewaySendLink(P1, "http://h", g)
        val id = link.newClientMessageId()!!
        assertEquals(Outcome.Ok(PromptAdmission.Confirmed(id.raw)), link.prompt(S1, OutgoingPrompt("hi", clientMessageId = id)) { true })
        assertIs<Outcome.Failed>(link.prompt(S1, OutgoingPrompt("hi", clientMessageId = id)) { false })
        assertIs<Outcome.Failed>(link.prompt(S1, OutgoingPrompt("plain")) { false })
        assertEquals(listOf(id), g.correlatedSends)
        assertTrue(g.fake.sentPrompts.isEmpty())
    }

    @Test
    fun `B-PROTO-043 the revert preflight reads the session and the scope follows the gateway location`() = runTest {
        val g = Gateway(FakeServerGateway(listOf(io.github.eslamasabry.oc.core.model.Session(S1, reverted = true))))
        val link = GatewaySendLink(P1, "http://h", g)
        assertEquals(Outcome.Ok(true), link.revertPending(S1))
        val before = link.scope
        g.setLocation(io.github.eslamasabry.oc.core.model.ServerLocation(directory = "/another-project"))
        assertTrue(before != link.scope, "a location change changes the dispatch scope (and the receipt scope)")
        assertTrue(before.receiptScope != link.scope.receiptScope)
    }
}
