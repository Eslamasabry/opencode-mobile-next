package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

/**
 * P5 / P6 (device QA sweep A, 2026-11-11): with the projects chip on "second" the "Since you were away" block still
 * listed three conversations of the other project, and with Running on "Nothing running" stood beside them. One
 * filtered set feeds every section of the home: the brief, Right now, Recent and the All conversations count.
 */
class HomeFiltersEverySectionTest {
    private fun away(id: String, dir: String, idle: Long) = Session(SessionId(id), directory = dir, time = SessionTime(idleMs = idle))

    /** Three finished runs in api (the block's three) and two more in api past it, one in web. */
    private val brief =
        ReturnBrief.build(
            listOf(
                away("a1", API, 50),
                away("a2", API, 40),
                away("a3", API, 30),
                away("a4", API, 20),
                away("a5", API, 10),
                away("w1", WEB, 5),
            ),
            true,
            false,
            { true },
            { false },
            { null },
            scope = BRIEF_SCOPE,
        )

    private val feed =
        listOf(
            item("a1", API, at = 9_000),
            item("a2", API, at = 8_000),
            item("a3", API, at = 7_000),
            item("a4", API, at = 6_000),
            item("a5", API, at = 5_000),
            item("w1", WEB, at = 4_000),
            item("w-run", WEB, at = 3_000, status = ChatStatus.Running),
            item("old", API, at = 1_000),
        )

    private fun build(filters: ChatsFilters, items: List<ChatFeedItem> = feed): ChatsUi =
        ChatsState.build(ChatFeedSnapshot(items.toImmutableList()), brief, filters, 10_000, settingsAvailable = true)

    private fun ChatsUi.everyRow() = brief?.rows.orEmpty() + rightNow + recent

    @Test
    fun `P5 a chosen project keeps the away block to its own conversations`() {
        val second = build(ChatsFilters(projectDirectory = WEB))
        assertTrue(second.everyRow().all { it.directory == WEB }, "no row of another project: ${second.everyRow().map { it.key }}")
        assertEquals(listOf("w1"), second.brief?.rows?.map { it.ref.sessionId.raw }, "web's own finished run is still said")
        assertEquals(0, second.brief?.more, "the hidden api runs are not counted under web")
        assertFalse(second.brief!!.unknown, "a run the filter hides is not a run the brief cannot name")
        assertEquals(listOf("w1"), second.brief.acknowledgement.unreviewed.map { it.session.id.raw }, "Mark all seen marks only what is shown")
        assertEquals(2, second.shownCount, "All conversations counts web's two")
        assertEquals(8, second.allCount, "the projects sheet's All projects still counts everything")
    }

    @Test
    fun `P5 under the project that owns them the block keeps its three and its more`() {
        val api = build(ChatsFilters(projectDirectory = API))
        assertEquals(listOf("a1", "a2", "a3"), api.brief?.rows?.map { it.ref.sessionId.raw })
        assertEquals(2, api.brief?.more, "the two api runs past the block, and not web's")
        assertTrue(api.recent.none { it.directory != API })
    }

    @Test
    fun `P6 Running with nothing running in the project is one empty state, nothing beside it`() {
        val ui = build(ChatsFilters(projectDirectory = API, running = true))
        assertEquals(ChatsPhase.NoMatch, ui.phase)
        assertNull(ui.brief, "no away block beside Nothing running")
        assertTrue(ui.rightNow.isEmpty() && ui.recent.isEmpty())
        assertEquals(0, ui.shownCount)
    }

    @Test
    fun `P6 Running keeps only working rows in every section`() {
        val ui = build(ChatsFilters(running = true))
        assertEquals(ChatsPhase.List, ui.phase)
        assertNull(ui.brief, "the finished runs are not running")
        assertEquals(listOf("w-run"), ui.everyRow().map { it.ref.sessionId.raw })
        assertEquals(1, ui.shownCount)
    }

    @Test
    fun `P5 every chip combination feeds every section from the one filtered set`() {
        val combos =
            listOf(
                ChatsFilters(),
                ChatsFilters(projectDirectory = API),
                ChatsFilters(projectDirectory = WEB),
                ChatsFilters(running = true),
                ChatsFilters(projectDirectory = WEB, running = true),
                ChatsFilters(projectDirectory = API, running = true),
                ChatsFilters(identities = kotlinx.collections.immutable.persistentSetOf(feed[0].identity, feed[5].identity)),
                ChatsFilters(query = "a3"),
                ChatsFilters(otherFolders = true),
            )
        for (filters in combos) {
            val ui = build(filters)
            val lets =
                feed.filter { item ->
                    (filters.projectDirectory == null || item.directory == filters.projectDirectory) &&
                        (!filters.running || item.status == ChatStatus.Running) &&
                        (filters.identities == null || item.identity in filters.identities) &&
                        (filters.query.isBlank() || "Chat ${item.sessionId.raw}".contains(filters.query)) &&
                        !filters.otherFolders
                }.mapTo(hashSetOf()) { it.identity }
            assertTrue(ui.everyRow().all { it.key in lets }, "$filters let through $lets but showed ${ui.everyRow().map { it.key }}")
            if (filters.any) assertEquals(lets.size, ui.shownCount, "$filters: the All conversations count")
            else assertNull(ui.shownCount, "no filter: the row counts every conversation")
            if (lets.isEmpty()) {
                assertEquals(ChatsPhase.NoMatch, ui.phase, "$filters")
                assertTrue(ui.everyRow().isEmpty() && ui.brief == null, "$filters: one empty state and nothing beside it")
            }
        }
    }

    @Test
    fun `no filter leaves the away block as it was`() {
        val ui = build(ChatsFilters())
        assertEquals(listOf("a1", "a2", "a3"), ui.brief?.rows?.map { it.ref.sessionId.raw })
        assertEquals(3, ui.brief?.more, "two api runs and web's one past the block")
        assertNull(ui.shownCount)
    }

    private companion object {
        const val API = "/work/api"
        const val WEB = "/work/web"
    }
}
