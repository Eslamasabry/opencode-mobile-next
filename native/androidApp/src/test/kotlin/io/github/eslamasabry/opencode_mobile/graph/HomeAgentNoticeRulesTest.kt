package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotice
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Device QA basics-3 B1: "fx stopped in the background / Resume fx" at app open, although nothing had stopped. */
class HomeAgentNoticeRulesTest {
    // fx installed and proved; the helper simply has not been started in this app process yet.
    private val fx = PhoneAgentRow("fx", "fx", PhoneAgentStatus.Stopped)
    private val notCertified: (String) -> Boolean = { false }

    @Test
    fun `after an app start the helper that never ran is not called stopped in the background`() {
        val opened = PhoneAgentsState(available = true, agents = persistentListOf(fx))
        assertTrue(HomeAgentNoticeRules.notices(opened, notCertified).lines.isEmpty())
    }

    @Test
    fun `a helper seen to stop on its own still names the agent and offers its Resume`() {
        val stopped = HomeAgentNotice("fx", "fx", HomeAgentNoticeKind.Stopped)
        val unexpected =
            PhoneAgentsState(available = true, agents = persistentListOf(fx), helperStoppedUnexpectedly = true)
        assertEquals(listOf(stopped), HomeAgentNoticeRules.notices(unexpected, notCertified).lines)
        val byAndroid =
            PhoneAgentsState(available = true, agents = persistentListOf(fx), helperMayHaveBeenStoppedByAndroid = true)
        assertEquals(listOf(stopped), HomeAgentNoticeRules.notices(byAndroid, notCertified).lines)
    }

    @Test
    fun `sign-in and plan lines keep their rule`() {
        val rows =
            persistentListOf(
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded),
                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.Limit),
                PhoneAgentRow("goose", "Goose", PhoneAgentStatus.SignInNeeded),
            )
        val state = PhoneAgentsState(available = true, agents = rows)
        val lines = HomeAgentNoticeRules.notices(state) { it != "goose" }.lines
        assertEquals(
            listOf(
                HomeAgentNotice("claude", "Claude Code", HomeAgentNoticeKind.SignedOut),
                HomeAgentNotice("codex", "Codex", HomeAgentNoticeKind.LimitReached),
            ),
            lines,
        )
        assertTrue(HomeAgentNoticeRules.notices(state.copy(available = false)) { true }.lines.isEmpty())
    }
}
