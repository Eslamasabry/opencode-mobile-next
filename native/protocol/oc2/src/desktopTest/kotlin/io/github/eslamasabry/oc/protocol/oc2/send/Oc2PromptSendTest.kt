package io.github.eslamasabry.oc.protocol.oc2.send

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.protocol.oc2.fixtures.Fixtures
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Message
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Session
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The send files of `:protocol:oc2` against the recorded Flutter fixtures (plan 04 §7.13, WP-04.8). */
class Oc2PromptSendTest {
    private val receipt = Oc2InboxItem.fromJson(Fixtures.data("api2/prompt_receipt.json"))!!
    private val receiptId = "msg_04acb4987001Ei5pD9H8pLWeKb"
    private val receiptSession = "ses_fb534b6a0ffeJ5pNlwiesRdWV2"

    @Test
    fun `B-PROTO-135 the real receipt with inbox type user confirms admission`() {
        assertEquals("user", receipt.type, "the recorded server answers type user, not prompt (N4-D13)")
        assertTrue(Oc2PromptSend.receiptConfirms(receipt, receiptSession, receiptId))
    }

    @Test
    fun `B-PROTO-135 another type, session or id stays unconfirmed`() {
        for (type in listOf("synthetic", "compaction", "move", "shell", "unknown")) {
            assertFalse(Oc2PromptSend.receiptConfirms(receipt.copy(type = type), receiptSession, receiptId), type)
        }
        assertFalse(Oc2PromptSend.receiptConfirms(receipt, "ses_other", receiptId))
        assertFalse(Oc2PromptSend.receiptConfirms(receipt, receiptSession, "msg_other"))
        assertFalse(Oc2PromptSend.receiptConfirms(receipt.copy(sessionID = null), receiptSession, receiptId))
        assertFalse(Oc2PromptSend.receiptConfirms(null, receiptSession, receiptId))
    }

    @Test
    fun `B-PROTO-042 a pending inbox item confirms, the real inbox list included`() {
        val items = Fixtures.dataList("api2/inbox_list.json").mapNotNull(Oc2InboxItem::fromJson)
        val only = items.single()
        val sid = requireNotNull(only.sessionID)
        assertTrue(Oc2PromptSend.inboxConfirms(items, sid, only.id))
        assertFalse(Oc2PromptSend.inboxConfirms(items, "ses_other", only.id), "wrong inbox session")
        assertFalse(Oc2PromptSend.inboxConfirms(items.map { it.copy(type = "shell") }, sid, only.id), "wrong type")
        assertFalse(Oc2PromptSend.inboxConfirms(emptyList(), sid, only.id), "an empty inbox proves nothing")
    }

    @Test
    fun `B-PROTO-042 only a promoted user message with our id confirms`() {
        val user = Oc2Message.fromJson(obj("id" to "msg_1", "type" to "user", "text" to "Ask"))
        val assistant = Oc2Message.fromJson(obj("id" to "msg_1", "type" to "assistant"))
        val other = Oc2Message.fromJson(obj("id" to "msg_other", "type" to "user", "text" to "Ask"))
        assertTrue(Oc2PromptSend.messageConfirms(user, "msg_1"))
        assertFalse(Oc2PromptSend.messageConfirms(assistant, "msg_1"), "assistant progress never confirms")
        assertFalse(Oc2PromptSend.messageConfirms(other, "msg_1"), "equal text under another id never confirms")
        assertFalse(Oc2PromptSend.messageConfirms(null, "msg_1"))
        assertEquals(listOf("/session/ses_1/inbox", "/session/ses_1/message/msg_1"), Oc2PromptSend.lookupPaths("ses_1", "msg_1"))
    }

    @Test
    fun `B-PROTO-042 the prompt posts the client id first, then the text`() {
        val body = Oc2PromptSend.body(OutgoingPrompt(text = "Ask", clientMessageId = MessageId("msg_1")))
        assertEquals(listOf("id", "text"), body.keys.toList())
        assertEquals("msg_1", (body["id"] as JsonPrimitive).content)
        assertEquals("Ask", (body["text"] as JsonPrimitive).content)
        assertEquals("/session/ses_1/prompt", Oc2PromptSend.path("ses_1"))
        assertFalse("id" in Oc2PromptSend.body(OutgoingPrompt(text = "Ask")), "no client id → no id key")
    }

    @Test
    fun `B-PROTO-025 queue delivery rides when chosen, the default steer is omitted`() {
        assertEquals("queue", (Oc2PromptSend.body(OutgoingPrompt("later please", delivery = PromptDelivery.Queue))["delivery"] as JsonPrimitive).content)
        assertFalse("delivery" in Oc2PromptSend.body(OutgoingPrompt("now")), "unset = the server's default steer")
        assertEquals("steer", (Oc2PromptSend.body(OutgoingPrompt("now", delivery = PromptDelivery.Steer))["delivery"] as JsonPrimitive).content)
    }

    @Test
    fun `B-PROTO-023 files and agent mentions keep Dart's wire shape, model and agent stay off the prompt`() {
        val body = Oc2PromptSend.body(
            OutgoingPrompt(
                text = "look @src/a.kt with @helper",
                attachments = persistentListOf(
                    PromptAttachment("text/plain", "a.kt", "file:///w/src/a.kt", mentionPath = "src/a.kt", mentionStart = 5, mentionEnd = 14),
                    PromptAttachment("image/png", "", "data:image/png;base64,AA=="),
                ),
                mentions = persistentListOf(PromptAgentMention("helper", "@helper", 20, 27)),
                agent = "build",
            ),
        )
        assertEquals(listOf("text", "files", "agents"), body.keys.toList())
        assertEquals(
            """[{"uri":"file:///w/src/a.kt","name":"a.kt","mention":{"start":5,"end":14,"text":"@src/a.kt"}},{"uri":"data:image/png;base64,AA=="}]""",
            body["files"].toString(),
        )
        assertEquals("""[{"name":"helper","mention":{"start":20,"end":27,"text":"@helper"}}]""", body["agents"].toString())
    }

    @Test
    fun `B-PROTO-043 a staged revert refuses the prompt before the POST`() {
        val staged = Oc2Session.fromJson(buildJsonObject { put("id", "ses_1"); put("revert", buildJsonObject { put("messageID", "msg_old") }) })!!
        val clean = Oc2Session.fromJson(obj("id" to "ses_1"))!!
        assertTrue(Oc2PromptSend.revertBlocksPrompt(staged))
        assertFalse(Oc2PromptSend.revertBlocksPrompt(clean))
    }

    @Test
    fun `client ids have OpenCode's sortable shape and never the queue id`() {
        val clock = TestClock(Instant.fromEpochMilliseconds(1_787_961_231_753))
        val ids = Oc2ClientIds(clock, Random(7))
        val message = ids.message()
        val session = ids.session()
        assertEquals(30, message.length)
        assertTrue(message.startsWith("msg_04acb4989000"), message) // (ms * 4096) & 0xffffffffffff
        assertTrue(session.startsWith("ses_fb534b676fff"), session) // XOR: newest session sorts first
        assertTrue(Oc2ClientIds.isClientId(message) && Oc2ClientIds.isClientId(session))
        assertFalse(Oc2ClientIds.isClientId("queue:q1"))
        clock.now = Instant.fromEpochMilliseconds(1_787_961_231_754)
        val later = ids.message()
        assertTrue(later > message, "message ids ascend across milliseconds")
        assertTrue(ids.session() < session, "session ids descend across milliseconds")
        assertNotEquals(later.substring(16), message.substring(16))
        assertTrue(Oc2ClientIds.isClientId(receiptId), "the server's own id has the same shape")
    }

    private fun obj(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }
}
