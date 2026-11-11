package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotice
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotices
import io.github.eslamasabry.oc.feature.chats.presentation.HomeNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeNotices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The home shows at most one notice: the first actionable active kind in the declared order (owner 2026-11-11). */
class HomeNoticesTest {
    private fun shown(vararg kinds: HomeNoticeKind) = HomeNotices.shown(kinds.toSet())

    @Test
    fun `three active notices show exactly one, the highest in the order`() {
        assertEquals(
            HomeNoticeKind.ListIncomplete,
            shown(HomeNoticeKind.AgentSignedOut, HomeNoticeKind.ListIncomplete, HomeNoticeKind.ReplyOffer),
        )
        assertEquals(
            HomeNoticeKind.AppCondition,
            shown(HomeNoticeKind.ReplyOffer, HomeNoticeKind.AppCondition, HomeNoticeKind.AgentStopped),
        )
    }

    @Test
    fun `closing the shown notice reveals the next one`() {
        val active = mutableSetOf(HomeNoticeKind.AppCondition, HomeNoticeKind.AgentSignedOut, HomeNoticeKind.ReplyOffer)
        assertEquals(HomeNoticeKind.AppCondition, HomeNotices.shown(active))
        active -= HomeNoticeKind.AppCondition
        assertEquals(HomeNoticeKind.AgentSignedOut, HomeNotices.shown(active))
        active -= HomeNoticeKind.AgentSignedOut
        assertEquals(HomeNoticeKind.ReplyOffer, HomeNotices.shown(active))
        active -= HomeNoticeKind.ReplyOffer
        assertNull(HomeNotices.shown(active))
    }

    @Test
    fun `a limit nobody can act on never shows, alone or beside an actionable one`() {
        val limits = HomeNoticeKind.entries.filter { !it.actionable }
        assertEquals(
            setOf(HomeNoticeKind.ListLoading, HomeNoticeKind.OnlyProject, HomeNoticeKind.BriefUnknown),
            limits.toSet(),
        )
        assertNull(HomeNotices.shown(limits.toSet()))
        assertEquals(HomeNoticeKind.ReplyOffer, HomeNotices.shown(limits.toSet() + HomeNoticeKind.ReplyOffer))
    }

    @Test
    fun `every actionable kind comes before every non-actionable one`() {
        val order = HomeNoticeKind.entries
        assertTrue(order.indexOfLast { it.actionable } < order.indexOfFirst { !it.actionable })
    }

    private fun ui(vararg items: io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem, across: Boolean = true, brief: ReturnBrief = ReturnBrief.None): ChatsUi =
        ChatsState.build(ChatFeedSnapshot(items.toList().toImmutableList(), acrossProjects = across), brief, ChatsFilters(), 10_000, true)

    private fun active(state: ChatsUi) = HomeNotices.active(state, appCondition = false, replyOffer = false)

    @Test
    fun `the case from the owner's phone shows one notice and none of the two limits`() {
        // The stopped-app notice, "can't tell what's new" and "only this project": three before, one now.
        val unknown = ReturnBrief.build(emptyList(), false, false, { true }, { false }, { null }, scope = BRIEF_SCOPE)
        val state = ui(item("a", dir = "/work/new"), across = false, brief = unknown)
        assertNull(state.brief, "the server cannot tell: no line, no block")
        assertEquals("new", state.onlyProject)
        assertEquals("new", state.projectChipName, "the chip names the one project, not All projects")
        val all = HomeNotices.active(state, appCondition = true, replyOffer = false)
        assertTrue(HomeNoticeKind.OnlyProject in all)
        assertEquals(HomeNoticeKind.AppCondition, HomeNotices.shown(all))
        assertNull(HomeNotices.shown(active(state)), "without the app condition nothing shows")
    }

    @Test
    fun `a list that reaches across projects keeps All projects, and a chosen project names itself`() {
        val state = ui(item("a", dir = "/work/api"), item("b", dir = "/work/web"))
        assertNull(state.onlyProject)
        assertNull(state.projectChipName)
        val chosen =
            ChatsState.build(
                ChatFeedSnapshot(persistentListOf(item("a", dir = "/work/api"), item("b", dir = "/work/web")), acrossProjects = true),
                ReturnBrief.None,
                ChatsFilters(projectDirectory = "/work/web"),
                10_000,
                true,
            )
        assertEquals("web", chosen.projectChipName)
    }

    @Test
    fun `a limited source beside one that reaches further names no single project`() {
        val state = ui(item("a", dir = "/work/api"), item("b", dir = "/work/web"), across = false)
        assertNull(state.onlyProject)
        assertNull(state.projectChipName)
    }

    @Test
    fun `refreshing a partly read list is progress, not a notice`() {
        val base = ui(item("a")).copy(incomplete = persistentListOf("Laptop"))
        assertEquals(HomeNoticeKind.ListIncomplete, HomeNotices.shown(active(base)))
        assertNull(HomeNotices.shown(active(base.copy(refreshing = true))))
    }

    @Test
    fun `a plan limit is said with the agent's page, after a sign-in and a stop`() {
        // New conversation does not say an agent is at its limit: without this line the person learns it from a
        // conversation that fails (Flutter keeps it on its home too, agent_notices.dart:124).
        val limit = HomeAgentNotice("claude", "Claude Code", HomeAgentNoticeKind.LimitReached)
        val stopped = HomeAgentNotice("codex", "Codex", HomeAgentNoticeKind.Stopped)
        val signedOut = HomeAgentNotice("claude", "Claude Code", HomeAgentNoticeKind.SignedOut)
        val state = ui(item("a"))
        fun shownFor(vararg lines: HomeAgentNotice) = HomeNotices.shown(active(state.copy(agentNotices = HomeAgentNotices(lines = persistentListOf(*lines)))))
        assertEquals(HomeNoticeKind.AgentLimit, shownFor(limit))
        assertEquals(HomeNoticeKind.AgentStopped, shownFor(limit, stopped))
        assertEquals(HomeNoticeKind.AgentSignedOut, shownFor(limit, stopped, signedOut))
    }

    @Test
    fun `a failed answer outranks the app condition and the reply offer`() {
        val failure = Failure.Unknown()
        val state = ui(item("a")).copy(answerFailure = failure)
        val all = HomeNotices.active(state, appCondition = true, replyOffer = true)
        assertEquals(HomeNoticeKind.AnswerFailure, HomeNotices.shown(all))
    }

    @Test
    fun `the server that failed to open leads, and the app-closed notice about it waits`() {
        // Still-down case: the app was closed, the phone's OpenCode did not come back, the home is opening it. The
        // failure card (Start / Restart / Try again) and "it stopped" would say the same thing twice.
        val failed = ChatsOpeningUi(ChatsOpeningPhase.Failed, "This phone", profile = ProfileId("phone"), onThisPhone = true)
        val state = ui(item("a")).copy(home = ChatsHomeExtraState(opening = failed))
        val all = HomeNotices.active(state, appCondition = true, replyOffer = true)
        assertEquals(HomeNoticeKind.ServerOpening, HomeNotices.shown(all))
        // Still connecting: no card, so the app condition is the one notice.
        val connecting = state.copy(home = ChatsHomeExtraState(opening = failed.copy(phase = ChatsOpeningPhase.Connecting)))
        assertEquals(HomeNoticeKind.AppCondition, HomeNotices.shown(HomeNotices.active(connecting, appCondition = true, replyOffer = false)))
        // New conversation tapped while it still connects: its "still opening" line leads in the same place.
        assertEquals(
            HomeNoticeKind.ServerOpening,
            HomeNotices.shown(HomeNotices.active(connecting.copy(newBlocked = true), appCondition = true, replyOffer = false)),
        )
    }
}
