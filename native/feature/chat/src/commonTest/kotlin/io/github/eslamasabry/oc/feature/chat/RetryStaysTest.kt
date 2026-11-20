package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * B-CHAT-RETRY-001: the retry block of a failing run stays between attempts. The next attempt starting, or the server
 * opening an empty reply for it, is not output; only streamed words, a step or the run ending take the block away.
 * The page must not change height from one attempt to the next (the owner saw it flash on build 10113).
 */
class RetryStaysTest {
    private val sid = Ref.sessionId.raw
    private val session = SessionId(sid)

    private fun keeper(): TurnFeedKeeper {
        val k = TurnFeedKeeper(sid)
        k.link(ChatLink.Live)
        k.event(GatewayEvent.RunStarted(session), 0)
        k.event(GatewayEvent.RunRetrying(session, attempt = 2, nextAtMs = 3_000, tag = null, details = null), 1_000)
        return k
    }

    private fun emptyReply(id: String) =
        GatewayEvent.MessageUpserted(MessageInfo(id, sid, Role.Assistant, time = MsgTime(5_000, null)))

    @Test
    fun `B-CHAT-RETRY-001 the next attempt starting keeps the retry block, with its attempt number`() {
        val k = keeper()
        k.event(GatewayEvent.RunStarted(session), 3_000)
        val retry = assertNotNull(k.feed.value.retry, "the block stays while the next attempt is in flight")
        assertEquals(2, retry.attempt)
    }

    @Test
    fun `B-CHAT-RETRY-002 an empty reply opened for the next attempt keeps the retry block`() {
        val k = keeper()
        k.event(GatewayEvent.RunStarted(session), 3_000)
        k.event(emptyReply("m9"), 3_100)
        assertNotNull(k.feed.value.retry)
    }

    @Test
    fun `B-CHAT-RETRY-003 streamed words take the retry block away, because the retry worked`() {
        val k = keeper()
        k.event(GatewayEvent.RunStarted(session), 3_000)
        k.event(emptyReply("m9"), 3_100)
        k.event(GatewayEvent.PartDelta(session, MessageId("m9"), PartId("x"), DeltaField.Text, "Once "), 3_200)
        assertNull(k.feed.value.retry)
    }

    @Test
    fun `B-CHAT-RETRY-004 the run ending takes the retry block away`() {
        val k = keeper()
        k.event(GatewayEvent.RunStarted(session), 3_000)
        k.event(GatewayEvent.RunEnded(session, RunOutcome.Idle), 4_000)
        assertNull(k.feed.value.retry)
    }

    @Test
    fun `B-CHAT-RETRY-005 a later attempt's own retry replaces the block's numbers in place`() {
        val k = keeper()
        k.event(GatewayEvent.RunStarted(session), 3_000)
        k.event(GatewayEvent.RunRetrying(session, attempt = 3, nextAtMs = 6_000, tag = null, details = null), 3_500)
        assertEquals(3, assertNotNull(k.feed.value.retry).attempt)
    }
}
