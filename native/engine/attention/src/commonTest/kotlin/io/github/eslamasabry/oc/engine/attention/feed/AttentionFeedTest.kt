package io.github.eslamasabry.oc.engine.attention.feed

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.attention.elsewhere.AttentionEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AttentionFeedTest {
    private val clock = TestClock()
    private val redactor = Redactor()
    private val request = AttentionRequest(id = "request-1", sessionId = "same-session", kind = AttentionRequestKind.Permission)

    private fun active(): SelectedTransportAttention =
        SelectedTransportAttention(clock).apply {
            streamConnected(true)
            requestsRead(AttentionKind.Permission, listOf(request))
            requestsRead(AttentionKind.Question, emptyList())
        }

    private fun monitored(profileId: String, enabled: Boolean) =
        if (!enabled) {
            AttentionServerSnapshot(profileId, AttentionSourceStatus.Disabled)
        } else {
            AttentionServerSnapshot(
                profileId = profileId,
                status = AttentionSourceStatus.Current,
                checkedAt = clock.now(),
                requests = listOf(request),
                complete = true,
                attentionComplete = true,
            )
        }

    private fun feed(source: SelectedTransportAttention, otherEnabled: Boolean, saved: List<String> = listOf("profile-1", "profile-2")) =
        AttentionFeed.fromServers(
            saved.map { id ->
                AttentionServer(
                    id,
                    "Server $id",
                    if (id == "profile-1") source.snapshot(id, "/work", null, supportsForms = false) else monitored(id, otherEnabled),
                )
            },
            now = clock.now(),
            redactor = redactor,
        )

    @Test
    fun `B-STATE-010 one feed combines the selected transport and an opted-in other server once`() {
        val source = active()
        val first = feed(source, otherEnabled = true)
        assertEquals(2, first.items.size)
        assertEquals(setOf("profile-1", "profile-2"), first.items.map { it.profileId }.toSet())
        assertEquals(2, first.items.map { it.identity }.toSet().size)
        assertEquals(first.knownAttentionCount, first.items.size)
        for (item in first.items) {
            assertEquals("same-session", item.target.sessionId)
            assertEquals("request-1", item.target.requestId)
            assertTrue(item.isFresh)
        }
        // A rebuild is a projection: it does not move the receipt time.
        clock.advance(1.seconds)
        assertEquals(first.items.first().observedAt, feed(source, otherEnabled = true).items.first().observedAt)
        // A duplicated saved server is listed once.
        val twice = AttentionFeed.fromServers(
            listOf(AttentionServer("profile-2", "A", monitored("profile-2", true)), AttentionServer("profile-2", "B", monitored("profile-2", true))),
            clock.now(), redactor,
        )
        assertEquals(1, twice.items.size)
        assertEquals(1, twice.servers.size)
    }

    @Test
    fun `B-STATE-010 reconnect does not refresh cached requests and successful reads do`() {
        val source = active()
        val at = feed(source, false).items.single().observedAt
        source.streamConnected(false)
        assertFalse(feed(source, false).items.single().isFresh)
        source.streamConnected(true)
        assertFalse(feed(source, false).items.single().isFresh)
        assertEquals(at, feed(source, false).items.single().observedAt)
        source.requestsRead(AttentionKind.Permission, listOf(request))
        source.requestsRead(AttentionKind.Question, emptyList())
        assertTrue(feed(source, false).items.single().isFresh)
        assertEquals(AttentionCheckState.Partial, feed(source, false).servers.first().state)
    }

    @Test
    fun `B-STATE-010 a confirmed failure shows no raw error, newer work retires it and removed rows are excluded`() {
        val source = active()
        source.handle(
            AttentionEvent(
                "session.error", "/work",
                buildJsonObject {
                    put("sessionID", "failed-session")
                    putJsonObject("error") { put("message", "fixture diagnostic") }
                },
            ),
        )
        val failed = feed(source, false).items.single { it.kind == AttentionKind.FailedRun }
        assertEquals("failed-session", failed.target.sessionId)
        assertNull(failed.target.requestId)
        assertNull(failed.title)
        assertFalse(failed.toString().contains("fixture diagnostic"))
        // Failed runs follow requests.
        assertEquals(AttentionKind.Permission, feed(source, false).items.first().kind)
        source.handle(
            AttentionEvent(
                "session.status", "/work",
                buildJsonObject {
                    put("sessionID", "failed-session")
                    putJsonObject("status") { put("type", "busy") }
                },
            ),
        )
        assertTrue(feed(source, false).items.none { it.kind == AttentionKind.FailedRun })
        val removed = feed(source, false, saved = listOf("profile-2"))
        assertTrue(removed.items.isEmpty())
        assertEquals(listOf("profile-2"), removed.servers.map { it.profileId })
    }

    @Test
    fun `B-STATE-010 a read failure makes the server unavailable and the feed never claims all clear`() {
        val source = active()
        source.requestsReadFailed(AttentionKind.Question)
        val result = feed(source, otherEnabled = false)
        assertEquals(AttentionCheckState.Unavailable, result.servers.first { it.profileId == "profile-1" }.state)
        assertFalse(result.isComplete)
        assertNull(result.attentionCount)
        val mismatched = AttentionFeed.fromServers(
            listOf(AttentionServer("profile-3", "x", AttentionServerSnapshot("other", AttentionSourceStatus.Current))),
            clock.now(), redactor,
        )
        assertEquals(AttentionCheckState.Unavailable, mismatched.servers.single().state)
        assertTrue(mismatched.items.isEmpty())
    }
}
