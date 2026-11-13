package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeFootBarRules
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

/**
 * RC1 device pass (2026-11-13, pictures home2, home3, r0): the home's + sat in the foot bar beside All conversations
 * only while the server was open; while it opened or did not answer the bar dropped it, and the skeleton or a filter
 * that matched nothing had no + at all. Every state now keeps it in the bar, under the last row.
 */
@OptIn(ExperimentalTestApi::class)
class HomeFootBarTest {
    private val titles = listOf("Fix the reconnect loop", "Theme packs", "Write the docs", "Ship the beta", "Tidy the tests")

    private fun ui(loading: Boolean = false, empty: Boolean = false, filters: ChatsFilters = ChatsFilters()): ChatsUi =
        ChatsState.build(
            ChatFeedSnapshot(
                if (empty) emptyList<io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem>().toImmutableList()
                else titles.mapIndexed { i, t -> item("s$i", at = 9_000L - i, title = t) }.toImmutableList(),
                loading = loading,
            ),
            ReturnBrief.None,
            filters,
            10_000,
            true,
        )

    private fun opening(phase: ChatsOpeningPhase) = ChatsHomeExtraState(opening = ChatsOpeningUi(phase, "Home PC"))

    /** The + is drawn, in the lower part of the screen, and below every row shown. */
    private fun SemanticsNodeInteractionsProvider.plusInTheBar(what: String, rows: List<String>) {
        onAllNodesWithContentDescription("New conversation").assertCountEquals(1)
        val plus = onNodeWithContentDescription("New conversation").fetchSemanticsNode().boundsInRoot
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(plus.width > 0f && plus.height > 0f, "$what: the + is drawn ($plus)")
        assertTrue(plus.bottom <= root.bottom && plus.top > root.height * 0.75f, "$what: the + is in the foot bar ($plus in $root)")
        rows.forEach { title ->
            val row = onNodeWithContentDescription(title, substring = true).fetchSemanticsNode().boundsInRoot
            assertFalse(row.overlaps(plus), "$what: the + never covers the row \"$title\" ($row, $plus)")
        }
    }

    private fun shows(what: String, state: ChatsUi, rows: List<String>) = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent { Themed { ChatsScreen(state, actions) } }
        plusInTheBar(what, rows)
        onNodeWithContentDescription("New conversation").performClick()
        assertEquals(listOf("new"), actions.calls, "$what: the + is a live button")
    }

    @Test
    fun `RC1-HOME-001 connected, the + sits in the foot bar under the rows`() =
        shows("connected", ui(), titles)

    @Test
    fun `RC1-HOME-002 while the server opens, the saved rows keep the + in the foot bar`() =
        shows("connecting", ui().copy(home = opening(ChatsOpeningPhase.Connecting)), titles)

    @Test
    fun `RC1-HOME-003 a server that is not answering keeps the + in the foot bar`() =
        shows("not answering", ui().copy(home = opening(ChatsOpeningPhase.Failed)), emptyList())

    @Test
    fun `RC1-HOME-004 before the saved servers are read the + is already in the foot bar`() =
        shows("unknown server", ui().copy(home = ChatsHomeExtraState(known = false)), titles)

    @Test
    fun `RC1-HOME-005 the first read's skeleton has the + in the foot bar`() {
        val skeleton = ui(loading = true, empty = true)
        assertEquals(ChatsPhase.Opening, skeleton.phase)
        shows("skeleton", skeleton, emptyList())
    }

    @Test
    fun `RC1-HOME-006 a filter that matches nothing keeps the + in the foot bar`() {
        val none = ui(filters = ChatsFilters(running = true))
        assertEquals(ChatsPhase.NoMatch, none.phase)
        shows("no match", none, emptyList())
    }

    @Test
    fun `RC1-HOME-007 the empty home and the unread list carry New conversation on the calm page, no foot bar`() {
        assertFalse(HomeFootBarRules.shown(ChatsPhase.Empty))
        assertFalse(HomeFootBarRules.shown(ChatsPhase.NotRead))
        assertTrue(ChatsPhase.entries.filter { HomeFootBarRules.shown(it) }.containsAll(listOf(ChatsPhase.Opening, ChatsPhase.List, ChatsPhase.NoMatch)))
    }
}
