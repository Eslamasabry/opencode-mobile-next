package io.github.eslamasabry.oc.engine.attention.elsewhere

import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.attention.feed.AttentionKind
import io.github.eslamasabry.oc.engine.attention.feed.AttentionPhase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ElsewhereAttentionTest {
    private val receipt = Instant.parse("2026-09-28T12:00:00Z")
    private val clock = TestClock(receipt)

    private fun props(vararg pairs: Pair<String, Any?>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to json(v) })

    private fun json(v: Any?): JsonElement =
        when (v) {
            null -> JsonNull
            is String -> JsonPrimitive(v)
            is JsonElement -> v
            is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to json(x) })
            else -> error("unsupported")
        }

    private fun event(type: String, directory: String, vararg pairs: Pair<String, Any?>, workspace: String? = null) =
        AttentionEvent(type, directory, props(*pairs), workspace)

    private fun ElsewhereAttention.feed(type: String, directory: String, vararg pairs: Pair<String, Any?>, workspace: String? = null) =
        handle(event(type, directory, *pairs, workspace = workspace))

    @Test
    fun `B-AGENT-077 projects are tallied per project and readers leave out the one they are in`() {
        val tracker = ElsewhereAttention(clock)
        val start = tracker.revision.value
        tracker.feed("session.status", "/work/app", "sessionID" to "here", "status" to mapOf("type" to "busy"))
        assertTrue(tracker.activity(except = "/work/app").isEmpty())
        tracker.feed("session.status", "/work/fin", "sessionID" to "s1", "status" to mapOf("type" to "busy"))
        tracker.feed("permission.v2.asked", "/work/fin", "id" to "req1", "sessionID" to "s1")
        tracker.feed("session.status", "/work/site", "sessionID" to "s9", "status" to "busy")
        val activity = tracker.activity(except = "/work/app")
        assertEquals(listOf("/work/fin", "/work/site"), activity.map { it.directory })
        assertEquals(setOf("s1"), activity.first().running)
        assertEquals(setOf("s1"), activity.first().waiting)
        assertEquals(1, tracker.waitingCount())
        assertEquals(4, tracker.revision.value - start)
    }

    @Test
    fun `B-AGENT-077 an answer or the end of the run clears what was waiting`() {
        val tracker = ElsewhereAttention(clock)
        tracker.feed("question.asked", "/work/fin", "id" to "q1", "sessionID" to "s1")
        tracker.feed("permission.asked", "/work/fin", "id" to "p1", "sessionID" to "s2")
        assertEquals(2, tracker.waitingCount())
        tracker.feed("question.replied", "/work/fin", "requestID" to "q1") // a reply names the request
        assertEquals(1, tracker.waitingCount())
        tracker.feed("session.status", "/work/fin", "sessionID" to "s2", "status" to mapOf("type" to "busy"))
        tracker.feed("session.idle", "/work/fin", "sessionID" to "s2")
        assertEquals(0, tracker.waitingCount())
        assertTrue(tracker.activity().isEmpty())
    }

    @Test
    fun `B-AGENT-077 switching into a project hands it back to the app`() {
        val tracker = ElsewhereAttention(clock)
        tracker.feed("permission.asked", "/work/fin", "id" to "p1", "sessionID" to "s1")
        assertEquals(0, tracker.waitingCount(except = "/work/fin"))
        assertTrue(tracker.activity(except = "/work/fin").isEmpty())
        assertEquals(setOf("s1"), tracker.forProject("/work/fin")!!.waiting)
        tracker.clear()
        assertTrue(tracker.activity().isEmpty())
    }

    @Test
    fun `B-AGENT-077 the AI Team's own folders are never tallied as the person's projects`() {
        val tracker = ElsewhereAttention(clock)
        for (folder in listOf("/root/aiteam/city", "/home/x/.oc/aiteam", "/p/city/.gc/worktrees/rig/a", "/root/./aiteam/")) {
            tracker.feed("permission.asked", folder, "id" to "p", "sessionID" to "s")
        }
        assertTrue(tracker.observations().isEmpty())
        tracker.feed("permission.asked", "/root/projects/my-app", "id" to "p", "sessionID" to "s")
        assertEquals(1, tracker.observations().size)
    }

    @Test
    fun `B-AGENT-078 observations keep typed card targets and receipt time`() {
        val tracker = ElsewhereAttention(clock)
        for (type in listOf("permission.v2.asked", "question.v2.asked")) {
            tracker.feed(type, "/other", "id" to "same-id", "sessionID" to "conversation", workspace = "workspace")
        }
        tracker.feed(
            "form.v2.created", "/other",
            "form" to mapOf("id" to "same-id", "sessionID" to "conversation", "title" to "Ignored content"),
            workspace = "workspace",
        )
        val observations = tracker.observations()
        assertEquals(3, observations.size)
        assertEquals(3, observations.map { it.identity }.toSet().size)
        assertEquals(setOf(AttentionKind.Permission, AttentionKind.Question, AttentionKind.Form), observations.map { it.kind }.toSet())
        for (o in observations) {
            assertEquals("same-id", o.requestId)
            assertEquals("conversation", o.sessionId)
            assertEquals("/other", o.directory)
            assertEquals("workspace", o.workspace)
            assertEquals(receipt, o.observedAt)
            assertEquals(AttentionPhase.NeedsYou, o.phase)
            assertNull(o.title)
        }
        assertEquals(1, tracker.waitingCount())
        assertTrue(tracker.observations(except = "/other").isEmpty())
        tracker.feed("question.v2.replied", "/other", "requestID" to "same-id", "sessionID" to "conversation", workspace = "workspace")
        assertEquals(2, tracker.observations().size)
        assertFalse(tracker.observations().any { it.kind == AttentionKind.Question })
        tracker.feed("form.v2.cancelled", "/other", "id" to "same-id", "sessionID" to "conversation", workspace = "workspace")
        assertEquals(AttentionKind.Permission, tracker.observations().single().kind)
    }

    @Test
    fun `B-AGENT-078 replies and session settlement do not remove another folder's card`() {
        val tracker = ElsewhereAttention(clock)
        for (folder in listOf("/one", "/two")) tracker.feed("permission.asked", folder, "id" to "same-id", "sessionID" to "same-session")
        tracker.feed("permission.replied", "/one", "requestID" to "same-id", "sessionID" to "different-session")
        assertEquals(2, tracker.observations().size)
        tracker.feed("permission.replied", "/one", "requestID" to "same-id")
        assertEquals("/two", tracker.observations().single().directory)
        tracker.feed("question.asked", "/one", "id" to "same-id", "sessionID" to "same-session")
        tracker.feed("session.idle", "/one", "sessionID" to "same-session")
        assertEquals("/two", tracker.observations().single().directory)
        tracker.clear()
        assertTrue(tracker.observations().isEmpty())
    }

    @Test
    fun `B-AGENT-078 duplicate requests refresh one observation and malformed requests are omitted`() {
        val tracker = ElsewhereAttention(clock)
        val asked = event("permission.asked", "/work", "id" to "request", "sessionID" to "session")
        tracker.handle(asked)
        clock.advance(1.minutes)
        tracker.handle(asked)
        assertEquals(1, tracker.observations().size)
        assertEquals(receipt + 1.minutes, tracker.observations().single().observedAt)
        tracker.feed("permission.asked", "/work", "id" to "missing-session")
        tracker.feed("form.v2.created", "/work", "form" to "malformed")
        assertEquals(1, tracker.observations().size)
        tracker.feed("session.deleted", "/work", "info" to mapOf("id" to "session"))
        assertTrue(tracker.observations().isEmpty())
    }

    @Test
    fun `B-AGENT-079 an observed failure survives idle and clears only on new work or removal`() {
        val tracker = ElsewhereAttention(clock)
        fun failed(directory: String) =
            tracker.feed("session.error", directory, "sessionID" to "session", "error" to mapOf("message" to "Raw server text must never be retained"))
        failed("/one")
        failed("/two")
        assertEquals(2, tracker.observations().size)
        val failure = tracker.observations().first()
        assertEquals(AttentionKind.FailedRun, failure.kind)
        assertEquals(AttentionPhase.Failed, failure.phase)
        assertEquals(receipt, failure.observedAt)
        assertEquals("session", failure.sessionId)
        assertNull(failure.requestId)
        assertNull(failure.title)
        assertFalse(failure.toString().contains("Raw server text"))
        assertTrue(tracker.activity().isEmpty())
        assertEquals(0, tracker.waitingCount())
        tracker.feed("session.idle", "/one", "sessionID" to "session")
        tracker.feed("session.status", "/one", "sessionID" to "session", "status" to mapOf("type" to "idle"))
        assertEquals(2, tracker.observations().size)
        tracker.feed("session.status", "/one", "sessionID" to "session", "status" to mapOf("type" to "busy"))
        assertEquals("/two", tracker.observations().single().directory)
        assertEquals(setOf("session"), tracker.forProject("/one")!!.running)
        failed("/one")
        tracker.feed("session.deleted", "/one", "info" to mapOf("id" to "session"))
        assertEquals("/two", tracker.observations().single().directory)
        tracker.feed("session.status", "/two", "sessionID" to "session", "status" to "retry")
        assertTrue(tracker.observations().isEmpty())
        failed("/one")
        tracker.feed("message.updated", "/one", "info" to mapOf("role" to "user", "sessionID" to "session"))
        assertTrue(tracker.observations().isEmpty())
        failed("/one")
        tracker.clear()
        assertTrue(tracker.observations().isEmpty())
    }

    @Test
    fun `B-AGENT-079 a Stop is not a failure to look at`() {
        val tracker = ElsewhereAttention(clock)
        tracker.feed("session.status", "/one", "sessionID" to "session", "status" to "busy")
        tracker.feed("session.error", "/one", "sessionID" to "session", "error" to mapOf("name" to "MessageAbortedError"))
        assertTrue(tracker.observations().isEmpty())
        assertTrue(tracker.activity().isEmpty())
    }

    @Test
    fun `B-AGENT-079 unknown status and request events do not invent busy or failure`() {
        val tracker = ElsewhereAttention(clock)
        for (status in listOf<Any?>(null, "unknown", mapOf("type" to "future-status"))) {
            tracker.feed("session.status", "/work", "sessionID" to "session", "status" to status)
        }
        assertTrue(tracker.activity().isEmpty())
        tracker.feed("permission.asked", "/work", "sessionID" to "session", "id" to "permission")
        assertEquals(AttentionKind.Permission, tracker.observations().single().kind)
        assertEquals(1, tracker.waitingCount())
        assertTrue(tracker.forProject("/work")!!.running.isEmpty())
    }

    @Test
    fun `B-AGENT-079 after a stream replacement old rows stay stale until observed again`() {
        val tracker = ElsewhereAttention(clock)
        tracker.feed("permission.asked", "/work", "sessionID" to "session", "id" to "permission")
        tracker.feed("session.error", "/other", "sessionID" to "failed")
        val before = tracker.observations().first().observedAt
        tracker.markStale()
        assertTrue(tracker.observations().all { !it.isFresh })
        assertEquals(before, tracker.observations().first().observedAt)
        tracker.feed("permission.asked", "/work", "sessionID" to "session", "id" to "permission")
        assertTrue(tracker.observations().first().isFresh)
        assertFalse(tracker.observations().last().isFresh)
    }
}
