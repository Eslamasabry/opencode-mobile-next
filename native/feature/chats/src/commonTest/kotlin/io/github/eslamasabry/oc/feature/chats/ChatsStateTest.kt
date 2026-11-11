package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class ChatsStateTest {
    private val today = 10_000L

    private fun build(
        vararg items: io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem,
        filters: ChatsFilters = ChatsFilters(),
        loading: Boolean = false,
        brief: ReturnBrief = ReturnBrief.None,
    ) =
        ChatsState.build(
            ChatFeedSnapshot(items.toList().toImmutableList(), loading = loading),
            brief,
            filters,
            today,
            settingsAvailable = true,
        )

    @Test
    fun `JANK-HOME-1 building the same feed twice, a moment apart, gives equal screens`() {
        val feed = ChatFeedSnapshot(listOf(item("a", at = 9_000), item("b", at = 9_500, status = ChatStatus.Running)).toImmutableList())
        fun at(now: Long) = ChatsState.build(feed, ReturnBrief.None, ChatsFilters(), today, settingsAvailable = true, nowMs = now)
        assertEquals(at(10_000), at(10_007), "the clock reading must not make otherwise equal screens unequal")
    }

    @Test
    fun `A1-HOME-01 working and waiting rows lead in Right now, the rest under Recent newest first`() {
        val ui =
            build(
                item("ask", at = 2_000, status = ChatStatus.NeedsYou),
                item("run", at = 12_000, status = ChatStatus.Running),
                item(
                    "new",
                    at = 11_000,
                    title = "New session - 2026-09-02T14:47:06Z",
                    unseen = true,
                ),
                item("old", at = 3_000),
                item("child", at = 20_000, parent = "run"),
            )
        assertEquals(ChatsPhase.List, ui.phase)
        assertEquals(listOf("ask", "run"), ui.rightNow.map { it.ref.sessionId.raw }, "needs you first, then running")
        assertEquals(listOf("new", "old"), ui.recent.map { it.ref.sessionId.raw })
        assertEquals(RowTitle.New, ui.recent[0].title)
        assertEquals(RowStatus.Done, ui.recent[0].status)
        assertEquals(RowStatus.Running, ui.rightNow[1].status)
        assertEquals(1, ui.needsYou)
        assertEquals(1, ui.running)
        assertTrue(ui.settingsAvailable)
    }

    @Test
    fun `SCREENS-NAV-27 opening shows skeletons, an empty server the calm empty state, filters with no rows no-match`() {
        assertEquals(ChatsPhase.Opening, build(loading = true).phase)
        assertEquals(ChatsPhase.Empty, build().phase)
        assertEquals(
            ChatsPhase.NoMatch,
            build(item("a"), filters = ChatsFilters(running = true)).phase,
        )
    }

    @Test
    fun `B-CHAT-013 recent projects are real folders newest first and the chosen one filters the list`() {
        val rows =
            arrayOf(
                item("a", "/work/api", at = 1),
                item("b", "/work/web", at = 5),
                item("t", "/tmp/x", at = 9),
            )
        val ui = build(*rows)
        assertEquals(listOf("web", "api"), ui.projects.map { it.name })
        val filtered = build(*rows, filters = ChatsFilters(projectDirectory = "/work/api"))
        assertEquals(listOf("a"), (filtered.rightNow + filtered.recent).map { it.ref.sessionId.raw })
        assertTrue(filtered.projects.single { it.directory == "/work/api" }.selected)
    }

    @Test
    fun `SCREENS-NAV-27 search matches the cleaned title or the project`() {
        val rows =
            arrayOf(
                item("a", "/work/api", title = "Fix login<tool_call>x"),
                item("b", "/work/web", title = "Theme"),
            )
        assertEquals(
            listOf("a"),
            build(*rows, filters = ChatsFilters(query = "login")).recent.map {
                it.ref.sessionId.raw
            },
        )
        assertEquals(
            listOf("b"),
            build(*rows, filters = ChatsFilters(query = "WEB")).recent.map {
                it.ref.sessionId.raw
            },
        )
        assertEquals(
            ChatsPhase.NoMatch,
            build(*rows, filters = ChatsFilters(query = "tool_call")).phase,
        )
    }

    @Test
    fun `P-021 rows lead with the agent tile only when the list holds more than one agent`() {
        assertFalse(build(item("a"), item("b"), item("c")).showAgents)
        assertTrue(build(item("a"), item("b").copy(agentId = "codex")).showAgents)
        // A filter that leaves one agent's rows on screen leaves no tile to tell apart.
        assertFalse(
            build(
                    item("a", "/work/api"),
                    item("b", "/work/web").copy(agentId = "codex"),
                    filters = ChatsFilters(projectDirectory = "/work/api"),
                )
                .showAgents
        )
    }

    @Test
    fun `P-021 the finished rows carry their time like the Today rows`() {
        val brief =
            ReturnBrief.build(
                listOf(Session(SessionId("s1"), directory = "/work/api", time = SessionTime(idleMs = 10))),
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        val ui =
            ChatsState.build(
                ChatFeedSnapshot(listOf(item("s1", at = 1_000)).toImmutableList()),
                brief,
                ChatsFilters(),
                today,
                settingsAvailable = true,
                nowMs = 1_000 + 5 * 60_000L,
            )
        assertEquals(RelativeAge.Minutes(5), ui.brief!!.rows.single().ago)
    }

    @Test
    fun `B-CHAT-093 the brief block lists the feed rows of the unreviewed sessions and says how many more`() {
        val sessions =
            (1..4).map {
                Session(
                    SessionId("s$it"),
                    directory = "/work/api",
                    time = SessionTime(idleMs = it * 10L),
                )
            }
        val brief =
            ReturnBrief.build(
                sessions,
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        val ui = build(*(1..4).map { item("s$it") }.toTypedArray(), brief = brief)
        assertEquals(listOf("s4", "s3", "s2"), ui.brief!!.rows.map { it.ref.sessionId.raw })
        assertEquals(1, ui.brief!!.more)
        assertTrue(ui.brief!!.rows.all { it.status == RowStatus.Done })
        assertNull(build(item("a")).brief)
        val unknown =
            ReturnBrief.build(
                sessions,
                readStateKnown = false,
                inventoryPartial = false,
                { true },
                { false },
                { null },
            )
        assertTrue(build(item("a"), brief = unknown).brief!!.unknown)
    }

    @Test
    fun `B-NAV-030 finding 1 brief joins the complete profile source and folder identity`() {
        val session =
            Session(SessionId("same"), directory = "/work/api", time = SessionTime(idleMs = 10))
        val brief =
            ReturnBrief.build(
                listOf(session),
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        val right = item("same")
        val otherProfile =
            right.copy(
                sourceId = "opencode:p2",
                profileId = io.github.eslamasabry.oc.core.common.ids.ProfileId("p2"),
            )
        val otherFolder = right.copy(directory = "/work/other")
        val otherSource = right.copy(sourceId = "side:p1")
        val otherLocation = right.copy(locationKey = "other-location")
        val unknownLocation = right.copy(locationKey = null)
        val ui =
            build(
                right,
                otherProfile,
                otherFolder,
                otherSource,
                otherLocation,
                unknownLocation,
                brief = brief,
            )
        assertEquals(listOf(right.ref), ui.brief!!.rows.map { it.ref })
        assertEquals(listOf(right.identity), ui.brief!!.rows.map { it.key })
        assertTrue(
            build(
                    otherProfile,
                    otherFolder,
                    otherSource,
                    otherLocation,
                    unknownLocation,
                    brief = brief,
                )
                .brief!!
                .rows
                .isEmpty()
        )
        assertTrue(
            build(right, brief = brief.copy(scope = null)).brief!!.rows.isEmpty(),
            "unscoped briefs fail closed",
        )
    }

    @Test
    fun `B-NAV-031 sources that did not load are named`() {
        val ui =
            ChatsState.build(
                ChatFeedSnapshot(
                    persistentListOf(item("a")),
                    complete = false,
                    incompleteSources = persistentListOf("Laptop"),
                ),
                ReturnBrief.None,
                ChatsFilters(),
                today,
                false,
            )
        assertEquals(listOf("Laptop"), ui.incomplete)
    }

    @Test
    fun `A1-HOME-02 Recent shows only the five latest of the rest and counts every conversation for All`() {
        val idle = (1..8).map { item("i$it", at = it * 1_000L) }
        val ui = build(*idle.toTypedArray(), item("ask", at = 1, status = ChatStatus.NeedsYou))
        assertEquals(ChatsState.RECENT_ROWS, ui.recent.size)
        assertEquals(listOf("i8", "i7", "i6", "i5", "i4"), ui.recent.map { it.ref.sessionId.raw })
        assertEquals(9, ui.allCount, "All conversations counts the whole list, not the rows shown")
    }

    @Test
    fun `A1-HOME-03 Right now is never capped and never holds a quiet, failed or finished row`() {
        val live = (1..9).map { item("r$it", at = it * 1_000L, status = if (it % 3 == 0) ChatStatus.NeedsYou else ChatStatus.Running) }
        val ui =
            build(
                *live.toTypedArray(),
                item("failed", at = 50_000, status = ChatStatus.Failed),
                item("done", at = 60_000, unseen = true),
                item("quiet", at = 70_000),
            )
        assertEquals(9, ui.rightNow.size, "every running and waiting conversation, however many")
        assertEquals(listOf("r9", "r6", "r3"), ui.rightNow.take(3).map { it.ref.sessionId.raw }, "needs you first, newest first")
        assertTrue(ui.rightNow.all { it.status == RowStatus.NeedsYou || it.status == RowStatus.Running })
        assertEquals(listOf("quiet", "done", "failed"), ui.recent.map { it.ref.sessionId.raw })
    }

    @Test
    fun `A1-HOME-04 the project filter keeps Right now and the five Recent rows inside the chosen project`() {
        val api = (1..7).map { item("a$it", "/work/api", at = it * 1_000L) }
        val web = (1..3).map { item("w$it", "/work/web", at = 100_000L + it) }
        val ui =
            build(
                *(api + web).toTypedArray(),
                item("web-run", "/work/web", at = 200_000, status = ChatStatus.Running),
                item("api-run", "/work/api", at = 150_000, status = ChatStatus.Running),
                filters = ChatsFilters(projectDirectory = "/work/api"),
            )
        assertEquals(listOf("api-run"), ui.rightNow.map { it.ref.sessionId.raw })
        assertEquals(listOf("a7", "a6", "a5", "a4", "a3"), ui.recent.map { it.ref.sessionId.raw })
        assertFalse(ui.showProjects, "one project chosen: the rows do not name it")
        assertTrue(build(*(api + web).toTypedArray()).showProjects, "two projects listed: each row names its own")
        assertFalse(build(*api.toTypedArray()).showProjects, "one project only: no dot, no name")
    }

    @Test
    fun `A1-HOME-05 a row the return brief shows is not repeated under Recent`() {
        val brief =
            ReturnBrief.build(
                listOf(Session(SessionId("seen"), directory = "/work/api", time = SessionTime(idleMs = 10))),
                true,
                false,
                { true },
                { false },
                { null },
                scope = BRIEF_SCOPE,
            )
        val ui = build(item("seen", at = 2_000), item("old", at = 1_000), brief = brief)
        assertEquals(listOf("seen"), ui.brief?.rows?.map { it.ref.sessionId.raw })
        assertEquals(listOf("old"), ui.recent.map { it.ref.sessionId.raw })
    }

    @Test
    fun `H3 a server that cannot list across projects names the one project it shows`() {
        val items = listOf(item("a", at = 2_000)).toImmutableList()
        fun ui(across: Boolean) =
            ChatsState.build(ChatFeedSnapshot(items, acrossProjects = across), ReturnBrief.None, ChatsFilters(), today, settingsAvailable = true)
        assertNull(ui(true).onlyProject)
        assertEquals("api", ui(false).onlyProject)
    }

    @Test
    fun `a server that lists across projects shows every project, ranks the far rows by status and narrows by chip`() {
        val items =
            listOf(
                item("far-ask", "/work/web", at = 1_000, status = ChatStatus.NeedsYou),
                item("far-run", "/work/web", at = 2_000, status = ChatStatus.Running),
                item("near", "/work/api", at = 3_000),
            ).toImmutableList()
        fun ui(filters: ChatsFilters = ChatsFilters()) =
            ChatsState.build(ChatFeedSnapshot(items, acrossProjects = true), ReturnBrief.None, filters, today, settingsAvailable = true)
        val all = ui()
        assertNull(all.onlyProject)
        assertEquals(listOf("far-ask", "far-run"), all.rightNow.map { it.ref.sessionId.raw })
        assertEquals(setOf("/work/api", "/work/web"), all.projects.map { it.directory }.toSet())
        assertTrue(all.showProjects)
        val web = ui(ChatsFilters(projectDirectory = "/work/web"))
        assertEquals(listOf("far-ask", "far-run"), (web.rightNow + web.recent).map { it.ref.sessionId.raw })
        val api = ui(ChatsFilters(projectDirectory = "/work/api"))
        assertEquals(listOf("near"), (api.rightNow + api.recent).map { it.ref.sessionId.raw })
    }
}
