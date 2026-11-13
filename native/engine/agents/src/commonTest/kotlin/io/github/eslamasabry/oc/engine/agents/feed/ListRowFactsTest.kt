package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/** H6 and H4 at the source: what the OpenCode source says about a row beyond its status. */
class ListRowFactsTest {
    private fun source(
        listing: OpenCodeListing,
        needsNewChat: (io.github.eslamasabry.oc.core.model.Session) -> Boolean? = { null },
    ) = OpenCodeChatFeedSource(feedProfile, null, MutableStateFlow(listing), needsNewChat = needsNewChat)

    @Test
    fun `H6 a run the person stopped is a stopped row, only while it is idle`() {
        val sessions = listOf(session("a", "/w", 5), session("b", "/w", 4))
        val items =
            source(
                    OpenCodeListing(
                        sessions = sessions,
                        finishedUnseen = setOf(SessionId("a"), SessionId("b")),
                        stoppedByPerson = setOf(SessionId("a")),
                    )
                )
                .current()
                .items
        assertTrue(items.first { it.sessionId.raw == "a" }.stoppedByPerson)
        assertFalse(items.first { it.sessionId.raw == "b" }.stoppedByPerson)
    }

    @Test
    fun `a stop the person made reads Stopped after a restart, a run that was truly lost reads Interrupted`() {
        val lost = session("lost", "/w", 5).copy(lastRunOutcome = io.github.eslamasabry.oc.core.model.SessionRunOutcome.Interrupted)
        val stopped = session("stopped", "/w", 4).copy(lastRunOutcome = io.github.eslamasabry.oc.core.model.SessionRunOutcome.Interrupted)
        val items =
            source(
                    OpenCodeListing(
                        sessions = listOf(lost, stopped),
                        finishedUnseen = setOf(SessionId("lost"), SessionId("stopped")),
                        // What the delivery now reloads from disk after a restart.
                        stoppedByPerson = setOf(SessionId("stopped")),
                    )
                )
                .current()
                .items
        val a = items.first { it.sessionId.raw == "stopped" }
        assertTrue(a.stoppedByPerson)
        assertFalse(a.interrupted)
        val b = items.first { it.sessionId.raw == "lost" }
        assertTrue(b.interrupted)
        assertFalse(b.stoppedByPerson)
    }

    @Test
    fun `H6 a stopped conversation that runs again is not stopped any more`() {
        val item =
            source(
                    OpenCodeListing(
                        sessions = listOf(session("a", "/w", 5)),
                        statuses = mapOf(SessionId("a") to io.github.eslamasabry.oc.core.model.SessionStatus.Busy),
                        stoppedByPerson = setOf(SessionId("a")),
                    )
                )
                .current()
                .items
                .single()
        assertFalse(item.stoppedByPerson)
    }

    @Test
    fun `H4 the helper's answer reaches the row, unknown leaves it openable`() {
        val sessions = listOf(session("old", "/w", 5), session("live", "/w", 4), session("unknown", "/w", 3))
        val items =
            source(OpenCodeListing(sessions = sessions)) { if (it.id.raw == "old") true else if (it.id.raw == "live") false else null }
                .current()
                .items
        assertTrue(items.first { it.sessionId.raw == "old" }.needsNewChat)
        assertFalse(items.first { it.sessionId.raw == "live" }.needsNewChat)
        assertFalse(items.first { it.sessionId.raw == "unknown" }.needsNewChat)
        assertEquals(true, items.all { it.canReopen })
    }

    @Test
    fun `H1 a waiting question and form ride on their own conversation's row only`() {
        val ask = FeedQuestion("q1", "Pick", "npm or pnpm?", listOf("npm", "pnpm"), inPlace = true)
        val form = FeedForm("f1", "Settings", 2)
        val items =
            source(
                    OpenCodeListing(
                        sessions = listOf(session("a", "/w", 5), session("b", "/w", 4)),
                        questions = mapOf(SessionId("a") to ask),
                        forms = mapOf(SessionId("b") to form),
                    )
                )
                .current()
                .items
        val a = items.first { it.sessionId.raw == "a" }
        val b = items.first { it.sessionId.raw == "b" }
        assertEquals(ask, a.question)
        assertEquals(null, a.form)
        assertEquals(form, b.form)
        assertEquals(null, b.question)
    }
}
