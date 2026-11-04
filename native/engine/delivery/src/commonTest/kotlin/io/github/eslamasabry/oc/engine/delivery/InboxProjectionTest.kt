package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.InboxGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InboxChange
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.core.model.InboxItem
import io.github.eslamasabry.oc.engine.delivery.live.InboxProjection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of the "inbox state" group of `test/connection_interaction_test.dart` (row B-PROTO-045). */
class InboxProjectionTest {
    private val session = SessionId("ses_1")

    private fun item(id: String, delivery: InboxDelivery = InboxDelivery.Queue, text: String = "queued prompt", at: Long = 1) =
        InboxItem(InboxItemId(id), session, at, "user", JsonObject(mapOf("text" to JsonPrimitive(text))), delivery)

    private class Server : InboxGateway {
        val listed = mutableListOf<InboxItem>()
        var cancelError: Failure? = null
        val cancels = mutableListOf<Pair<String, String>>()
        val deliveries = mutableListOf<Triple<String, String, InboxDelivery>>()

        override suspend fun inbox(session: SessionId): Outcome<ImmutableList<InboxItem>> =
            listed.filter { it.sessionId == session }.toImmutableList().ok()

        override suspend fun cancelInboxItem(session: SessionId, item: InboxItemId): Outcome<Unit> {
            cancels += session.raw to item.raw
            cancelError?.let { return Outcome.Failed(it) }
            listed.removeAll { it.id == item }
            return Unit.ok()
        }

        override suspend fun setInboxDelivery(session: SessionId, item: InboxItemId, delivery: InboxDelivery): Outcome<Unit> {
            deliveries += Triple(session.raw, item.raw, delivery)
            listed.replaceAll { if (it.id == item) it.copy(delivery = delivery) else it }
            return Unit.ok()
        }
    }

    private fun changed(id: String, change: InboxChange) = GatewayEvent.InboxChanged(session, InboxItemId(id), change)

    @Test
    fun `B-PROTO-045 enqueued, delivery changed and delivered events project the pending sends`() = runTest {
        val server = Server()
        val inbox = InboxProjection(server)
        server.listed += item("msg_1", at = 1)
        inbox.onEvent(changed("msg_1", InboxChange.Enqueued))
        server.listed += item("msg_2", InboxDelivery.Steer, "next", at = 2)
        inbox.onEvent(changed("msg_2", InboxChange.Enqueued))
        val items = inbox.itemsFor(session)
        assertEquals(2, items.size)
        assertEquals("msg_1", items.first().id.raw)
        assertEquals(InboxDelivery.Queue, items.first().delivery)
        assertEquals("queued prompt", InboxProjection.promptText(items.first()))

        server.listed[0] = server.listed[0].copy(delivery = InboxDelivery.Steer)
        inbox.onEvent(changed("msg_1", InboxChange.DeliveryChanged))
        assertEquals(InboxDelivery.Steer, inbox.itemsFor(session).first().delivery)

        inbox.onEvent(changed("msg_1", InboxChange.Delivered))
        inbox.onEvent(changed("msg_2", InboxChange.Cancelled))
        assertTrue(inbox.itemsFor(session).isEmpty())
    }

    @Test
    fun `B-PROTO-045 refresh reconciles a session from REST`() = runTest {
        val server = Server()
        val inbox = InboxProjection(server)
        server.listed += item("msg_stale")
        inbox.refresh(session)
        server.listed.clear()
        server.listed += item("msg_9", text = "from rest", at = 5)
        inbox.refresh(session)
        assertEquals(listOf("msg_9"), inbox.itemsFor(session).map { it.id.raw })
    }

    @Test
    fun `B-PROTO-045 cancelling returns the text to the composer`() = runTest {
        val server = Server()
        val inbox = InboxProjection(server)
        server.listed += item("msg_1", text = "bring me back")
        inbox.refresh(session)
        assertEquals(Outcome.Ok("bring me back"), inbox.cancel(session, InboxItemId("msg_1")))
        assertEquals(listOf("ses_1" to "msg_1"), server.cancels)
        assertTrue(inbox.itemsFor(session).isEmpty())
    }

    @Test
    fun `B-PROTO-045 a 409 already-delivered cancel drops the item and still fails`() = runTest {
        val server = Server().apply { cancelError = Failure.Conflict(WireTag("ConflictError")) }
        val inbox = InboxProjection(server)
        server.listed += item("msg_1")
        inbox.refresh(session)
        assertIs<Outcome.Failed>(inbox.cancel(session, InboxItemId("msg_1")))
        assertTrue(inbox.itemsFor(session).isEmpty())
    }

    @Test
    fun `B-PROTO-045 changing the delivery mode updates the server and the visible item`() = runTest {
        val server = Server()
        val inbox = InboxProjection(server)
        server.listed += item("msg_1", InboxDelivery.Queue)
        inbox.refresh(session)
        assertEquals(Outcome.Ok(Unit), inbox.setDelivery(session, InboxItemId("msg_1"), InboxDelivery.Steer))
        assertEquals(listOf(Triple("ses_1", "msg_1", InboxDelivery.Steer)), server.deliveries)
        assertEquals(InboxDelivery.Steer, inbox.itemsFor(session).single().delivery)
    }
}
