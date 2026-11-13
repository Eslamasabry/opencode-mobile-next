package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest

/** QA wave 1 B1: "QA PC couldn't load" with more than 100 conversations. */
class SessionPagesTest {
    private class Paged(private val pages: Map<String?, ServerPage<Session>>, private val failAt: String? = null) :
        io.github.eslamasabry.oc.core.domain.ServerGateway by FakeServerGateway() {
        val asked = mutableListOf<String?>()

        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> {
            asked += cursor
            if (cursor != null && cursor == failAt) return Outcome.Failed(Failure.Network(NetworkWhy.Refused))
            return Outcome.Ok(pages.getValue(cursor))
        }
    }

    private fun page(from: Int, to: Int, next: String?) =
        ServerPage((from until to).map { Session(SessionId("s$it")) }.toImmutableList(), next)

    @Test
    fun `B1 QA wave1 a second page is read and the list is complete, not a failure`() = runTest {
        val server = Paged(mapOf(null to page(0, 100, "c1"), "c1" to page(100, 130, null)))
        val read = assertIs<Outcome.Ok<SessionPages>>(readSessionPages(server)).value
        assertEquals(130, read.sessions.size)
        assertTrue(read.complete, "more pages is never 'couldn't load'")
        assertEquals(listOf(null, "c1"), server.asked)
    }

    @Test
    fun `B1 B-NAV-031 the page cap and a repeated cursor stop the read without a failure but say the list is incomplete`() = runTest {
        val looping = Paged(mapOf(null to page(0, 100, "c1"), "c1" to page(100, 200, "c1")))
        val read = assertIs<Outcome.Ok<SessionPages>>(readSessionPages(looping)).value
        assertEquals(200, read.sessions.size)
        // Codex chat-bugs 4: a repeated cursor leaves conversations unread; the feed must not claim it is complete.
        assertFalse(read.complete)
        val endless = Paged((0 until 20).associate { (if (it == 0) null else "c$it") to page(it * 100, it * 100 + 100, "c${it + 1}") })
        val capped = assertIs<Outcome.Ok<SessionPages>>(readSessionPages(endless, maxPages = 3)).value
        assertEquals(300, capped.sessions.size)
        assertEquals(3, endless.asked.size)
        assertFalse(capped.complete, "a page-capped feed is incomplete (Flutter)")
    }

    @Test
    fun `B1 QA wave1 a failed later page keeps the rows and marks the list incomplete`() = runTest {
        val server = Paged(mapOf(null to page(0, 100, "c1")), failAt = "c1")
        val read = assertIs<Outcome.Ok<SessionPages>>(readSessionPages(server)).value
        assertEquals(100, read.sessions.size)
        assertFalse(read.complete)
    }

    @Test
    fun `B-235 a page with rows that could not be decoded is counted and the list is not called complete`() = runTest {
        val bad = ServerPage((0 until 3).map { Session(SessionId("s$it")) }.toImmutableList(), null, unreadable = 2)
        val read = assertIs<Outcome.Ok<SessionPages>>(readSessionPages(Paged(mapOf(null to bad)))).value
        assertEquals(3, read.sessions.size)
        assertEquals(2, read.unreadable)
        assertFalse(read.complete)
    }

    @Test
    fun `B-235 a short page that still carries a cursor is followed to the next one`() = runTest {
        val server = Paged(mapOf(null to page(0, 40, "c1"), "c1" to page(40, 70, null)))
        val read = assertIs<Outcome.Ok<SessionPages>>(readSessionPages(server)).value
        assertEquals(70, read.sessions.size)
        assertEquals(listOf(null, "c1"), server.asked)
        assertTrue(read.complete)
    }
}
