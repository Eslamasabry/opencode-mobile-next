package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.model.SessionRunOutcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

internal val feedProfile = ProfileId("p1")

internal fun session(
    id: String,
    dir: String?,
    updated: Long,
    title: String? = "Chat $id",
    parent: String? = null,
    archived: Boolean = false,
    cost: Double? = null,
): Session =
    Session(
        id = SessionId(id),
        title = title,
        directory = dir,
        parentId = parent?.let(::SessionId),
        time =
            SessionTime(
                createdMs = 1,
                updatedMs = updated,
                archivedMs = if (archived) updated else null,
            ),
        cost = cost,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ChatFeedTest {
    @Test
    fun `B-CHAT-013 temporary folders are never shown as projects`() {
        val temporary =
            listOf(
                "/tmp",
                "/tmp/",
                "tmp",
                "/tmp/scratch",
                "/var/tmp/x",
                "/private/tmp",
                "/var/folders/ab/cd",
                "/data/local/tmp",
                "C:\\Windows\\Temp",
                "C:/Users/me/AppData/Local/Temp/x",
                "/",
                "global",
                "",
                null,
            )
        temporary.forEach { assertTrue(FeedFolders.isTemporary(it), "$it") }
        listOf("/work/app", "/home/me/tmpl", "/root/projects/tmp2").forEach {
            assertFalse(FeedFolders.isTemporary(it), it)
        }
        assertEquals("Home", FeedFolders.otherFolderLabel("/home/user"))
        assertEquals("tmp", FeedFolders.otherFolderLabel("/tmp"))

        val listing =
            OpenCodeListing(
                sessions =
                    listOf(
                        session("t", "/tmp/scratch", 9),
                        session("h", "/root", 8),
                        session("r", "/work/app", 7),
                    )
            )
        val source = OpenCodeChatFeedSource(feedProfile, "Laptop", MutableStateFlow(listing))
        val items = source.current().items
        // Listed, but their folder is never offered as a project.
        assertEquals(listOf("t", "h", "r"), items.map { it.sessionId.raw })
        assertTrue(items.first { it.sessionId.raw == "t" }.inOtherFolder)
        assertTrue(items.first { it.sessionId.raw == "h" }.inOtherFolder)
        assertEquals(listOf("/work/app"), ChatFeedRules.projects(items).map { it.directory })
        // The "other folders" filter shows exactly those.
        val other = ChatFeedFilter(otherFolders = true).apply(items)
        assertEquals(listOf("t", "h"), other.map { it.sessionId.raw })
    }

    @Test
    fun `B-NAV-030 finding 1 sources retain the location used by brief identities`() {
        val listing =
            MutableStateFlow(OpenCodeListing(sessions = listOf(session("same", "/work/app", 1))))
        val one =
            OpenCodeChatFeedSource(feedProfile, null, listing, locationKey = "one")
                .current()
                .items
                .single()
        val two =
            OpenCodeChatFeedSource(feedProfile, null, listing, locationKey = "two")
                .current()
                .items
                .single()
        assertEquals("one", one.locationKey)
        assertEquals("two", two.locationKey)
        assertNotEquals(one.identity, two.identity)
    }

    @Test
    fun `B-CHAT-014 the feed revision changes only when visible content changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val listing =
                MutableStateFlow(
                    OpenCodeListing(
                        sessions =
                            listOf(session("a", "/work/app", 1), session("b", "/work/app", 2))
                    )
                )
            val feed =
                ChatFeed(
                    listOf(OpenCodeChatFeedSource(feedProfile, null, listing)),
                    backgroundScope,
                )
            assertEquals(listOf("b", "a"), feed.snapshot.value.items.map { it.sessionId.raw })
            val revision = feed.snapshot.value.revision

            // Identical data, and a field no row shows (cost), leave the revision alone.
            repeat(20) {
                listing.value = listing.value.copy(sessions = listing.value.sessions.toList())
            }
            listing.value =
                listing.value.copy(
                    sessions =
                        listOf(
                            session("a", "/work/app", 1, cost = 3.0),
                            session("b", "/work/app", 2),
                        )
                )
            assertEquals(revision, feed.snapshot.value.revision)

            // A title, a time and a status show at once.
            listing.value =
                listing.value.copy(
                    sessions =
                        listOf(
                            session("a", "/work/app", 1, title = "Renamed"),
                            session("b", "/work/app", 2),
                        )
                )
            val afterTitle = feed.snapshot.value.revision
            assertNotEquals(revision, afterTitle)
            listing.value =
                listing.value.copy(
                    sessions =
                        listOf(
                            session("a", "/work/app", 5, title = "Renamed"),
                            session("b", "/work/app", 2),
                        )
                )
            assertEquals(listOf("a", "b"), feed.snapshot.value.items.map { it.sessionId.raw })
            val afterTime = feed.snapshot.value.revision
            assertNotEquals(afterTitle, afterTime)
            listing.value =
                listing.value.copy(statuses = mapOf(SessionId("b") to SessionStatus.Busy))
            assertNotEquals(afterTime, feed.snapshot.value.revision)
            assertEquals(ChatStatus.Running, feed.snapshot.value.items.first().status)
        }

    @Test
    fun `B-CHAT-014 sub-agent and archived sessions are not Home rows`() {
        val listing =
            OpenCodeListing(
                sessions =
                    listOf(
                        session("root", "/work/app", 3),
                        session("child", "/work/app", 4, parent = "root"),
                        session("old", "/work/app", 5, archived = true),
                    )
            )
        val items =
            OpenCodeChatFeedSource(feedProfile, null, MutableStateFlow(listing)).current().items
        assertEquals(listOf("child", "root"), items.map { it.sessionId.raw })
        assertEquals(listOf("root"), ChatFeedFilter().apply(items).map { it.sessionId.raw })
        assertEquals(2, ChatFeedFilter(includeSubagents = true).apply(items).size)
    }

    @Test
    fun `conversations of other projects keep their folder, run and ask facts, and the source says across projects`() {
        val ask = SessionId("far-ask")
        val listing =
            OpenCodeListing(
                sessions =
                    listOf(
                        session("here", "/work/app", 5),
                        session("far-run", "/work/other", 9),
                        session("far-ask", "/work/web", 7),
                        session("far-idle", "/work/other", 3),
                    ),
                statuses = mapOf(SessionId("far-run") to SessionStatus.Busy, ask to SessionStatus.Busy),
                needsYou = setOf(ask),
                acrossProjects = true,
            )
        val state = OpenCodeChatFeedSource(feedProfile, null, MutableStateFlow(listing)).current()
        assertTrue(state.acrossProjects)
        val byId = state.items.associateBy { it.sessionId.raw }
        assertEquals(ChatStatus.Running, byId.getValue("far-run").status)
        assertEquals(ChatStatus.NeedsYou, byId.getValue("far-ask").status)
        assertEquals(ChatStatus.Idle, byId.getValue("far-idle").status)
        assertEquals(
            setOf("/work/app", "/work/other", "/work/web"),
            state.items.mapNotNull { it.directory }.toSet(),
        )
        // The same project filter narrows to one project's rows.
        assertEquals(
            listOf("far-run", "far-idle"),
            ChatFeedFilter(projectDirectory = "/work/other").apply(state.items).map { it.sessionId.raw },
        )
    }

    @Test
    fun `the server's word for the last run reaches the row, and Stopped by the person outranks Interrupted`() {
        fun rows(listing: OpenCodeListing) =
            OpenCodeChatFeedSource(feedProfile, null, MutableStateFlow(listing)).current().items.associateBy { it.sessionId.raw }
        val cut = session("cut", "/work/app", 5).copy(lastRunOutcome = SessionRunOutcome.Interrupted)
        val bad = session("bad", "/work/app", 4).copy(lastRunOutcome = SessionRunOutcome.Failed)
        val fine = session("fine", "/work/app", 3).copy(lastRunOutcome = SessionRunOutcome.Succeeded)
        val unknown = session("unknown", "/work/app", 2)
        val byPerson = session("byPerson", "/work/app", 1).copy(lastRunOutcome = SessionRunOutcome.Interrupted)
        val listing =
            OpenCodeListing(
                sessions = listOf(cut, bad, fine, unknown, byPerson),
                finishedUnseen = setOf(cut.id, bad.id, byPerson.id),
                stoppedByPerson = setOf(byPerson.id),
            )
        val byId = rows(listing)
        assertTrue(byId.getValue("cut").interrupted)
        assertEquals(ChatStatus.Idle, byId.getValue("cut").status)
        assertEquals(ChatStatus.Failed, byId.getValue("bad").status)
        assertFalse(byId.getValue("fine").interrupted)
        assertFalse(byId.getValue("unknown").interrupted)
        assertEquals(ChatStatus.Idle, byId.getValue("unknown").status)
        assertFalse(byId.getValue("byPerson").interrupted)
        assertTrue(byId.getValue("byPerson").stoppedByPerson)
        // Once the person opened it (no longer unseen), the row stops saying so.
        assertFalse(rows(listing.copy(finishedUnseen = emptySet())).getValue("cut").interrupted)
        // A run that is working again is Running, never Interrupted.
        val again = rows(listing.copy(statuses = mapOf(cut.id to SessionStatus.Busy))).getValue("cut")
        assertEquals(ChatStatus.Running, again.status)
        assertFalse(again.interrupted)
    }
}
