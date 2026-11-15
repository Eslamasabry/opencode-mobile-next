package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.core.common.Failure
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Analytics truth (intake 2026-11-16, stages 1 to 3): each stage of a prompt is its own number, and none can be read
 * wrongly. Send = the server took it; reply = the first output the person can see; turn = how it ended, `done` only
 * with visible output. A fake clock and a recording vendor drive the journey the way the delivery feed does.
 */
class ChatStagesTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    private class Once : FirstReplyOnce {
        var marked = false

        override fun seen() = marked

        override fun mark() {
            marked = true
        }
    }

    private class Rig(scope: TestScope) {
        val vendor = RecordingVendor()
        var now = 50_000L
        var done = 0

        init {
            val service = TelemetryService(vendor, available = true, scope = scope)
            service.start(TelemetryConsent(usage = true), NoStore, MemoryTelemetryReportStore())
            AppTelemetry.install(service)
        }

        val journey = ChatJourney({ now }, HealthFlow(), Once()) { done++ }

        fun all(name: String) = vendor.events.filter { it.first == name }.map { it.second }

        fun last(name: String) = all(name).last()
    }

    @Test
    fun `B-JOURNEY-050 send, reasoning, tool, text, done - each stage reads its own number`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = false, PromptTraits(agent = "build"))
        r.journey.accepted("s1", error = false)
        r.journey.settled("s1", accepted = true)
        // Stage 1 is known the moment the server took the send: no reply decides it, and no reply range rides on it.
        assertEquals(
            listOf(mapOf("result" to "ok", "agent" to "build", "input" to "text", "server_kind" to "computer")),
            r.all("send_result"),
        )
        r.journey.busy("s1")
        r.journey.message("s1", "m1", assistant = true)
        r.now += 1_000
        r.journey.part("s1", "m1", "step-start")
        r.journey.part("s1", "m1", "reasoning")
        r.journey.delta("s1") // a reasoning piece streaming in
        assertTrue(r.all("first_reply").isEmpty(), "reasoning and step markers show nothing: not a reply")
        r.now += 4_000
        r.journey.part("s1", "m1", "tool")
        r.journey.tool("s1", "p1") // a tool row shows: the first visible output, 5 s after Send
        r.now += 20_000
        r.journey.text("s1", "m1", streamed = true)
        r.journey.ended("s1")
        assertEquals(listOf(mapOf("server_kind" to "computer", "first_reply_range" to "2-10s")), r.all("first_reply"))
        assertEquals("done", r.last("chat_turn").getValue("outcome"))
        assertEquals("2-10s", r.last("chat_turn").getValue("first_reply_range"))
        assertEquals(1, r.all("send_result").size)
        assertEquals(1, r.done, "a turn with visible output that ended cleanly is a finished reply")
    }

    @Test
    fun `B-JOURNEY-051 a reasoning-only turn has no first reply and ends no_output`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.settled("s1", accepted = true)
        r.journey.busy("s1")
        r.journey.message("s1", "m1", assistant = true)
        r.now += 3_000
        r.journey.part("s1", "m1", "step-start")
        r.journey.part("s1", "m1", "reasoning")
        r.journey.delta("s1")
        r.journey.part("s1", "m1", "text") // an empty text part: the feed reports no visible text for it
        r.journey.part("s1", "m1", "step-finish")
        r.now += 10_000
        r.journey.ended("s1")
        assertEquals("none", r.last("chat_turn").getValue("first_reply_range"))
        assertEquals("no_output", r.last("chat_turn").getValue("outcome"))
        assertTrue(r.all("first_reply").isEmpty())
        assertEquals(0, r.done, "nothing was shown, so no reply finished")
        assertEquals("ok", r.last("send_result").getValue("result"))
        assertTrue(HealthWords.turnOutcomes.contains("no_output") && productValueIsSafeWord("no_output"))
    }

    @Test
    fun `B-JOURNEY-052 a connection lost before any output reads ok send, connection_lost turn, no first reply`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.accepted("s1", error = false)
        r.journey.settled("s1", accepted = true)
        r.journey.busy("s1")
        r.journey.part("s1", "m1", "reasoning")
        r.journey.stream(onPhone = true, connected = false)
        r.now += ChatJourney.LOST_AFTER_MS + 1
        r.journey.checkStalls()
        assertEquals(listOf(mapOf("result" to "ok", "agent" to "other", "input" to "text", "server_kind" to "phone")), r.all("send_result"))
        assertEquals("connection_lost", r.last("chat_turn").getValue("outcome"))
        assertEquals("none", r.last("chat_turn").getValue("first_reply_range"))
        assertTrue(r.all("first_reply").isEmpty())
    }

    @Test
    fun `B-JOURNEY-053 a send the server never started on ends by the two-minute rule, not by eviction`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.settled("s1", accepted = true)
        r.now += ChatJourney.LOST_AFTER_MS - 1
        r.journey.checkStalls()
        assertTrue(r.all("chat_turn").isEmpty(), "still within the limit")
        r.now += 2
        r.journey.checkStalls()
        assertEquals("no_output", r.last("chat_turn").getValue("outcome"))
        assertEquals("none", r.last("chat_turn").getValue("first_reply_range"))
        r.journey.checkStalls()
        assertEquals(1, r.all("chat_turn").size, "ended once")
        // The same with its stream down: the turn is lost, not silent.
        r.journey.sent("s2", onPhone = true)
        r.journey.settled("s2", accepted = true)
        r.journey.stream(onPhone = true, connected = false)
        r.now += ChatJourney.LOST_AFTER_MS + 1
        r.journey.checkStalls()
        assertEquals("connection_lost", r.last("chat_turn").getValue("outcome"))
    }

    @Test
    fun `B-JOURNEY-054 a refused send carries agent, input and server kind like an ok one and starts no turn`() = runTest {
        val r = Rig(this)
        r.journey.noteVoice()
        r.journey.sent("s1", onPhone = true, PromptTraits(agent = "plan", hasAttachment = false))
        r.journey.accepted("s1", error = true)
        r.journey.settled("s1", accepted = false, failure = Failure.Rejected(413))
        assertEquals(
            listOf(mapOf("result" to "fail", "fail_kind" to "too_large", "agent" to "plan", "input" to "voice", "server_kind" to "phone")),
            r.all("send_result"),
        )
        r.journey.text("s1", "m1", streamed = true)
        r.now += ChatJourney.LOST_AFTER_MS + 1
        r.journey.checkStalls()
        assertTrue(r.all("chat_turn").isEmpty(), "a refused send is no turn")
    }

    @Test
    fun `B-JOURNEY-055 the person's own prompt text is not the reply, the assistant's text is`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.settled("s1", accepted = true)
        r.journey.busy("s1")
        r.journey.message("s1", "u1", assistant = false)
        r.journey.part("s1", "u1", "text")
        r.journey.text("s1", "u1", streamed = false)
        assertTrue(r.all("first_reply").isEmpty())
        r.journey.message("s1", "a1", assistant = true)
        r.now += 12_000
        r.journey.text("s1", "a1", streamed = false)
        r.journey.ended("s1")
        assertEquals("10-30s", r.last("chat_turn").getValue("first_reply_range"))
        assertEquals("done", r.last("chat_turn").getValue("outcome"))
    }

    @Test
    fun `B-JOURNEY-056 a steer is its own send, and a refused steer leaves the running turn alone`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.settled("s1", accepted = true)
        r.journey.busy("s1")
        r.journey.sent("s1", onPhone = true)
        r.journey.accepted("s1", error = true)
        r.journey.settled("s1", accepted = false, failure = Failure.TimedOut())
        assertEquals(listOf("ok", "fail"), r.all("send_result").map { it.getValue("result") })
        r.journey.reply("s1")
        r.journey.ended("s1")
        assertEquals("done", r.last("chat_turn").getValue("outcome"))
        assertEquals("steer", r.last("chat_turn").getValue("follow_up"))
        assertFalse(r.all("send_result").any { "first_reply_range" in it })
    }

    private fun ChatJourney.reply(session: String) {
        message(session, "m9", assistant = true)
        text(session, "m9", streamed = false)
    }

    private fun productValueIsSafeWord(word: String) = io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe(word)
}
