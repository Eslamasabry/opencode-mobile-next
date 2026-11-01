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
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatsStateTest {
    private val today = 10_000L

    private fun build(
        vararg items: io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem,
        filters: ChatsFilters = ChatsFilters(),
        loading: Boolean = false,
        brief: ReturnBrief = ReturnBrief.None,
    ) = ChatsState.build(ChatFeedSnapshot(items.toList().toImmutableList(), loading = loading), brief, filters, today, settingsAvailable = true)

    @Test
    fun `SCREENS-NAV-27 one list by urgency in Today and Earlier sections only`() {
        val ui =
            build(
                item("ask", at = 2_000, status = ChatStatus.NeedsYou),
                item("run", at = 12_000, status = ChatStatus.Running),
                item("new", at = 11_000, title = "New session - 2026-09-02T14:47:06Z", unseen = true),
                item("old", at = 3_000),
                item("child", at = 20_000, parent = "run"),
            )
        assertEquals(ChatsPhase.List, ui.phase)
        assertEquals(listOf("run", "new"), ui.today.map { it.ref.sessionId.raw })
        assertEquals(listOf("ask", "old"), ui.earlier.map { it.ref.sessionId.raw })
        assertEquals(RowTitle.New, ui.today[1].title)
        assertEquals(RowStatus.Done, ui.today[1].status)
        assertEquals(RowStatus.Running, ui.today[0].status)
        assertEquals(1, ui.needsYou)
        assertEquals(1, ui.running)
        assertTrue(ui.settingsAvailable)
    }

    @Test
    fun `SCREENS-NAV-27 opening shows skeletons, an empty server the calm empty state, filters with no rows no-match`() {
        assertEquals(ChatsPhase.Opening, build(loading = true).phase)
        assertEquals(ChatsPhase.Empty, build().phase)
        assertEquals(ChatsPhase.NoMatch, build(item("a"), filters = ChatsFilters(running = true)).phase)
    }

    @Test
    fun `B-CHAT-013 recent projects are real folders newest first and the chosen one filters the list`() {
        val rows = arrayOf(item("a", "/work/api", at = 1), item("b", "/work/web", at = 5), item("t", "/tmp/x", at = 9))
        val ui = build(*rows)
        assertEquals(listOf("web", "api"), ui.projects.map { it.name })
        val filtered = build(*rows, filters = ChatsFilters(projectDirectory = "/work/api"))
        assertEquals(listOf("a"), (filtered.today + filtered.earlier).map { it.ref.sessionId.raw })
        assertTrue(filtered.projects.single { it.directory == "/work/api" }.selected)
    }

    @Test
    fun `SCREENS-NAV-27 search matches the cleaned title or the project`() {
        val rows = arrayOf(item("a", "/work/api", title = "Fix login<tool_call>x"), item("b", "/work/web", title = "Theme"))
        assertEquals(listOf("a"), build(*rows, filters = ChatsFilters(query = "login")).earlier.map { it.ref.sessionId.raw })
        assertEquals(listOf("b"), build(*rows, filters = ChatsFilters(query = "WEB")).earlier.map { it.ref.sessionId.raw })
        assertEquals(ChatsPhase.NoMatch, build(*rows, filters = ChatsFilters(query = "tool_call")).phase)
    }

    @Test
    fun `B-CHAT-093 the brief block lists the feed rows of the unreviewed sessions and says how many more`() {
        val sessions = (1..4).map { Session(SessionId("s$it"), time = SessionTime(idleMs = it * 10L)) }
        val brief = ReturnBrief.build(sessions, true, false, { true }, { false }, { null })
        val ui = build(*(1..4).map { item("s$it") }.toTypedArray(), brief = brief)
        assertEquals(listOf("s4", "s3", "s2"), ui.brief!!.rows.map { it.ref.sessionId.raw })
        assertEquals(1, ui.brief!!.more)
        assertTrue(ui.brief!!.rows.all { it.status == RowStatus.Done })
        assertNull(build(item("a")).brief)
        val unknown = ReturnBrief.build(sessions, readStateKnown = false, inventoryPartial = false, { true }, { false }, { null })
        assertTrue(build(item("a"), brief = unknown).brief!!.unknown)
    }

    @Test
    fun `B-NAV-031 sources that did not load are named`() {
        val ui = ChatsState.build(ChatFeedSnapshot(persistentListOf(item("a")), complete = false, incompleteSources = persistentListOf("Laptop")), ReturnBrief.None, ChatsFilters(), today, false)
        assertEquals(listOf("Laptop"), ui.incomplete)
    }
}
