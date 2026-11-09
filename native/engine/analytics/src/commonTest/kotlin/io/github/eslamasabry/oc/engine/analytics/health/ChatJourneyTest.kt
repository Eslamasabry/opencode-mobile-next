package io.github.eslamasabry.oc.engine.analytics.health

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The chat journey, driven the way the prompt timeline drives it: the event sequence is the contract. */
class ChatJourneyTest {
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
        var now = 10_000L
        val once = Once()

        init {
            val service = TelemetryService(vendor, available = true, scope = scope)
            service.start(TelemetryConsent(usage = true), NoStore, MemoryTelemetryReportStore())
            AppTelemetry.install(service)
        }

        val journey = ChatJourney({ now }, HealthFlow(), once)

        fun names() = vendor.events.map { it.first }

        fun last(name: String) = vendor.events.last { it.first == name }.second
    }

    private fun ChatJourney.reply(session: String, message: String = "m1") {
        message(session, message, assistant = true)
        part(session, message, "text")
    }

    @Test
    fun `B-JOURNEY-010 a normal turn sends first reply, then one chat_turn done with its times and tools`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true, PromptTraits(agent = "Build"))
        r.journey.accepted("s1", error = false)
        r.journey.busy("s1")
        r.now += 3_000
        r.journey.reply("s1")
        r.journey.tool("s1", "p1")
        r.journey.tool("s1", "p1") // a call's later updates are not a second call
        r.journey.tool("s1", "p2")
        r.now += 20_000
        r.journey.ended("s1")
        assertEquals(listOf("send_result", "first_reply", "chat_turn"), r.names().filter { it != "feature_used" })
        assertEquals(
            mapOf("result" to "ok", "first_reply_range" to "2-10s", "agent" to "build", "input" to "text", "server_kind" to "phone"),
            r.vendor.events.first { it.first == "send_result" }.second,
        )
        assertEquals(mapOf("server_kind" to "phone", "first_reply_range" to "2-10s"), r.last("first_reply"))
        assertEquals(
            mapOf(
                "outcome" to "done", "agent" to "build", "server_kind" to "phone", "first_reply_range" to "2-10s",
                "ms_range" to "10-30s", "tools_range" to "1-3", "recovered" to "none", "follow_up" to "none",
            ),
            r.last("chat_turn"),
        )
    }

    @Test
    fun `B-JOURNEY-011 first_reply is sent once per install`() = runTest {
        val r = Rig(this)
        repeat(2) { n ->
            r.journey.sent("s$n", true)
            r.journey.busy("s$n")
            r.journey.reply("s$n")
            r.journey.ended("s$n")
        }
        assertEquals(1, r.names().count { it == "first_reply" })
        assertEquals(2, r.names().count { it == "chat_turn" })
    }

    @Test
    fun `B-JOURNEY-012 how the prompt was made rides on send_result, and voice and attachments are counted as features`() = runTest {
        val r = Rig(this)
        r.journey.noteVoice()
        r.journey.sent("s1", false, PromptTraits(hasAttachment = true))
        r.journey.busy("s1")
        r.journey.reply("s1")
        assertEquals("voice_attachment", r.last("send_result").getValue("input"))
        assertEquals(
            listOf("voice", "file_attach"),
            r.vendor.events.filter { it.first == "feature_used" }.map { it.second.getValue("feature") },
        )
    }

    @Test
    fun `B-JOURNEY-013 stop is stopped_by_user, an error carries its kind, never its text`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", true)
        r.journey.busy("s1")
        r.journey.stopped("s1")
        r.journey.ended("s1")
        assertEquals("stopped_by_user", r.last("chat_turn").getValue("outcome"))
        r.journey.sent("s2", true)
        r.journey.busy("s2")
        r.journey.ended("s2", ChatJourney.errorKindWord("ContextOverflow"))
        assertEquals("error", r.last("chat_turn").getValue("outcome"))
        assertEquals("context_overflow", r.last("chat_turn").getValue("fail_kind"))
        assertEquals("other", ChatJourney.errorKindWord("SomethingNew"))
        assertEquals("server", ChatJourney.errorKindWord(null))
    }

    @Test
    fun `B-JOURNEY-014 a stream drop that comes back is recovered yes, one that does not is connection_lost`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", true)
        r.journey.busy("s1")
        r.journey.stream(onPhone = true, connected = false)
        r.now += 20_000
        r.journey.stream(onPhone = true, connected = true)
        r.journey.reply("s1")
        r.journey.ended("s1")
        assertEquals("yes", r.last("chat_turn").getValue("recovered"))
        assertEquals("done", r.last("chat_turn").getValue("outcome"))

        r.journey.sent("s2", true)
        r.journey.busy("s2")
        r.journey.stream(onPhone = true, connected = false)
        r.now += ChatJourney.LOST_AFTER_MS + 1
        r.journey.checkStalls()
        assertEquals("connection_lost", r.last("chat_turn").getValue("outcome"))
        assertEquals("no", r.last("chat_turn").getValue("recovered"))
        r.journey.checkStalls() // the turn is gone: nothing more is sent
        assertEquals(2, r.names().count { it == "chat_turn" })
    }

    @Test
    fun `B-JOURNEY-015 a prompt into a running turn is a steer, the next one after a failure is after_fail`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", true)
        r.journey.busy("s1")
        r.journey.sent("s1", true) // steer: no second turn
        r.journey.ended("s1", "server")
        assertEquals("steer", r.last("chat_turn").getValue("follow_up"))
        r.now += 30_000
        r.journey.sent("s1", true)
        r.journey.busy("s1")
        r.journey.ended("s1")
        assertEquals("after_fail", r.last("chat_turn").getValue("follow_up"))
        assertEquals(2, r.names().count { it == "chat_turn" })
    }

    @Test
    fun `B-JOURNEY-016 a quiet turn is a stall with its stage, once per range`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", true)
        r.journey.busy("s1")
        r.journey.part("s1", "m1", "tool")
        r.now += 61_000
        r.journey.checkStalls()
        r.journey.checkStalls()
        assertEquals(mapOf("stage" to "waiting_tool", "minutes_range" to "1-5"), r.last("turn_stall"))
        r.journey.approvalOpen("s1", +1)
        r.now += 5 * 60_000
        r.journey.checkStalls()
        assertEquals("waiting_approval", r.last("turn_stall").getValue("stage"))
        assertEquals(2, r.names().count { it == "turn_stall" })
    }

    @Test
    fun `B-JOURNEY-017 an approval is asked, answered with its wait, or expires and ends the turn as approval_timeout`() = runTest {
        val r = Rig(this)
        val approvals = ApprovalMeter({ r.now }, HealthFlow()) { r.journey }
        r.journey.sent("s1", true)
        r.journey.busy("s1")
        approvals.asked("a1", "s1", ApprovalMeter.kindOf("bash"))
        r.now += 12_000
        approvals.answered("a1", "allowed")
        approvals.gone("a1")
        assertEquals(mapOf("kind" to "run", "result" to "allowed", "ms_range" to "10-30s"), r.last("approval"))
        approvals.asked("a2", "s1", ApprovalMeter.kindOf("edit"))
        r.now += 700_000
        approvals.gone("a2") // left without an answer
        assertEquals(mapOf("kind" to "edit", "result" to "expired", "ms_range" to ">10m"), r.last("approval"))
        r.journey.ended("s1", "server")
        assertEquals("approval_timeout", r.last("chat_turn").getValue("outcome"))
    }

    @Test
    fun `B-JOURNEY-018 nothing is sent while the person has not agreed`() = runTest {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(usage = false), NoStore, MemoryTelemetryReportStore())
        AppTelemetry.install(service)
        val j = ChatJourney({ 0L }, HealthFlow(), FirstReplyOnce.Never)
        j.sent("s1", true)
        j.busy("s1")
        j.ended("s1")
        assertEquals(0, vendor.events.size)
    }
}
