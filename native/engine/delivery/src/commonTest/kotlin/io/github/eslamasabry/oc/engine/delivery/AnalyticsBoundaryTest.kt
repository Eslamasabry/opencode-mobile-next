package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.analytics.health.AppTelemetry
import io.github.eslamasabry.oc.engine.analytics.health.NoTelemetry
import io.github.eslamasabry.oc.engine.analytics.health.Telemetry
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Analytics truth (intake 2026-11-16): the delivery engine reports each fact once, at the boundary that knows it. A
 * send's `send_result` is decided where the server takes or refuses it; a flush says how many went (`queue_flush`); a
 * connection's stream changes carry that connection (an id that stays in memory) and a drop word, or none when the app
 * let the stream rest itself.
 */
class AnalyticsBoundaryTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private class Recorder : Telemetry by NoTelemetry {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        override fun track(event: ProductEvent, props: Map<String, Any?>) {
            events += event.wire to props
        }

        fun of(name: String) = events.filter { it.first == name }.map { it.second }
    }

    private fun recorder() = Recorder().also { AppTelemetry.install(it) }

    @Test
    fun `B-JOURNEY-070 an accepted send is one ok send_result at once, a refused one is one fail with its kind`() = runTest {
        val r = recorder()
        val w = World(Disk(), this)
        val d = w.delivery()
        d.send(req("hello"))
        testScheduler.runCurrent()
        assertEquals(listOf("hello"), w.link.deliveredTexts())
        assertEquals(listOf("ok"), r.of("send_result").map { it["result"] }, "taken by the server: ok, before any reply")
        w.link.failBeforeWire = Failure.Rejected(413)
        d.send(req("too big"))
        testScheduler.runCurrent()
        assertEquals(listOf("ok", "fail"), r.of("send_result").map { it["result"] })
        assertEquals("too_large", r.of("send_result").last()["fail_kind"])
    }

    @Test
    fun `B-JOURNEY-071 a flush that sent prompts is one queue_flush with count ranges, and an empty one says nothing`() = runTest {
        val r = recorder()
        val w = World(Disk(), this)
        w.queue.queuePrompt(prompt("a", createdAt = NOW_MS - 20))
        w.queue.queuePrompt(prompt("b", createdAt = NOW_MS - 10))
        w.queue.queuePrompt(prompt("other", profile = P2, createdAt = NOW_MS - 30))
        w.queue.flush()
        assertEquals(listOf(mapOf<String, Any?>("sent" to "2-5", "failed" to "0")), r.of("queue_flush"))
        w.queue.flush()
        assertEquals(1, r.of("queue_flush").size, "nothing left to send: no second event")
    }

    private class Trace : PromptTracing {
        val calls = mutableListOf<String>()

        override fun sent(sessionId: String) {
            calls += "sent $sessionId"
        }

        override fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean, connection: String) {
            calls += "sent $sessionId via $connection"
        }

        override fun stream(connection: String, onPhone: Boolean, connected: Boolean, dropReason: String?) {
            calls += "stream $connection $connected $dropReason"
        }

        override fun accepted(sessionId: String, error: Boolean) {}

        override fun message(sessionId: String, messageId: String, assistant: Boolean) {}

        override fun part(sessionId: String, messageId: String, kind: String) {}

        override fun delta(sessionId: String) {}

        override fun busy(sessionId: String) {}

        override fun idle(sessionId: String) {}

        override fun error(sessionId: String) {}
    }

    @Test
    fun `B-JOURNEY-072 each connection names itself on sends and stream changes, and a stream the app let rest is no drop`() {
        val base = Trace()
        val one = base.forConnection("c1")
        val two = base.forConnection("c2")
        one.sent("s1", onPhone = false, agent = null, hasAttachment = false)
        two.stream(onPhone = false, connected = false)
        assertEquals(listOf("sent s1 via c1", "stream c2 false null"), base.calls)
        assertEquals("network_change", streamDropReason(StreamStatus.WaitingForNetwork))
        assertEquals("server_closed", streamDropReason(StreamStatus.Stopped(Failure.Unexpected())))
        assertEquals("other", streamDropReason(StreamStatus.Reconnecting(1, null)))
        assertNull(streamDropReason(StreamStatus.Suspended), "the app in the background let it rest: not a drop")
        assertNull(streamDropReason(StreamStatus.Connected))
    }
}
