package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MergedChatFeedTest {
    private val other = ProfileId("p2")

    @Test
    fun `B-NAV-030 merged rows sort needs-you then running then recent and equal ids from two sources stay apart`() =
        runTest(UnconfinedTestDispatcher()) {
            val one =
                MutableStateFlow(
                    OpenCodeListing(
                        sessions = listOf(session("same", "/work/a", 10), session("run", "/work/a", 1), session("ask", "/work/a", 2)),
                        statuses = mapOf(SessionId("run") to SessionStatus.Busy),
                        needsYou = setOf(SessionId("ask")),
                    ),
                )
            val two = MutableStateFlow(OpenCodeListing(sessions = listOf(session("same", "/work/b", 20))))
            val feed =
                ChatFeed(
                    listOf(OpenCodeChatFeedSource(feedProfile, "Laptop", one), OpenCodeChatFeedSource(other, "Server", two)),
                    backgroundScope,
                )
            val items = feed.snapshot.value.items
            assertEquals(listOf("ask", "run", "same", "same"), items.map { it.sessionId.raw })
            assertEquals(listOf(other, feedProfile), items.filter { it.sessionId.raw == "same" }.map { it.ref.profileId })
            assertEquals(4, items.map { it.identity }.toSet().size, "list keys are identities, never session ids")
            assertEquals("Server", items[2].sourceLabel)
        }

    @Test
    fun `B-NAV-031 a failed source keeps its rows partial and others still show`() =
        runTest(UnconfinedTestDispatcher()) {
            val one = MutableStateFlow(OpenCodeListing(sessions = listOf(session("a", "/work/a", 1))))
            val two = MutableStateFlow(OpenCodeListing(sessions = listOf(session("b", "/work/b", 2))))
            val feed =
                ChatFeed(
                    listOf(OpenCodeChatFeedSource(feedProfile, "Laptop", one), OpenCodeChatFeedSource(other, "Server", two)),
                    backgroundScope,
                )
            two.value = two.value.copy(failed = true)
            val snap = feed.snapshot.value
            assertEquals(listOf("b", "a"), snap.items.map { it.sessionId.raw })
            assertFalse(snap.complete)
            assertEquals(listOf("Server"), snap.incompleteSources)
            // A refresh never turns an empty filter into loading; the next good read recovers.
            two.value = two.value.copy(failed = false, loading = true)
            assertFalse(feed.snapshot.value.loading)
            two.value = two.value.copy(loading = false)
            assertTrue(feed.snapshot.value.complete)
        }

    @Test
    fun `B-STATE-029 a closed feed rejects late reads without closing shared sources`() =
        runTest(UnconfinedTestDispatcher()) {
            val listing = MutableStateFlow(OpenCodeListing(sessions = listOf(session("a", "/work/a", 1))))
            var refreshed = 0
            val shared = OpenCodeChatFeedSource(feedProfile, null, listing, refresh = { refreshed++; Unit.ok() })
            val feed = ChatFeed(listOf(shared), backgroundScope)

            // Child changes notify the owner until it closes.
            listing.value = listing.value.copy(sessions = listOf(session("a", "/work/a", 1), session("b", "/work/a", 2)))
            assertEquals(2, feed.snapshot.value.items.size)
            assertIs<Outcome.Ok<Unit>>(feed.refresh())
            assertEquals(1, refreshed)

            feed.close()
            val late = feed.refresh()
            assertIs<Outcome.Failed>(late)
            assertEquals(Failure.Refused(RefusalReason.ProfileClosing), late.failure)
            assertEquals(1, refreshed)
            listing.value = listing.value.copy(sessions = listOf(session("c", "/work/a", 3)))
            assertEquals(2, feed.snapshot.value.items.size, "a closed feed stops following its sources")

            // The shared source still works for its other owner.
            assertEquals(listOf("c"), shared.current().items.map { it.sessionId.raw })
            assertIs<Outcome.Ok<Unit>>(ChatFeed(listOf(shared), backgroundScope).refresh())
        }

    @Test
    fun `recent projects are real folders, newest first, with chat and running counts`() {
        val listing =
            OpenCodeListing(
                sessions =
                    listOf(
                        session("a", "/work/api", 1), session("b", "/work/web", 5), session("c", "/work/api", 9),
                        session("t", "/tmp", 99),
                    ),
                statuses = mapOf(SessionId("a") to SessionStatus.Busy),
            )
        val items = OpenCodeChatFeedSource(feedProfile, null, MutableStateFlow(listing)).current().items
        val projects = ChatFeedRules.projects(items)
        assertEquals(listOf("/work/api", "/work/web"), projects.map { it.directory })
        assertEquals(listOf("api", "web"), projects.map { it.name })
        assertEquals(2, projects[0].chatCount)
        assertEquals(1, projects[0].runningCount)
    }
}
