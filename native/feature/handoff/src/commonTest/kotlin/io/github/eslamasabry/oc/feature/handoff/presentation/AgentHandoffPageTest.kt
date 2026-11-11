package io.github.eslamasabry.oc.feature.handoff.presentation

import io.github.eslamasabry.oc.engine.agents.handoff.HandoffPhase
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class AgentHandoffPageTest {
    private val ready = AgentHandoffChoice("p\u0000m", "Claude Code", "Sonnet")
    private val unchecked =
        AgentHandoffChoice("q\u0000m", "Claude Code", "Sonnet", HandoffChoiceIssue.PhoneCheck)

    @Test
    fun `loading until the session is read`() {
        assertEquals(HandoffPage.Loading, AgentHandoffUi().page())
    }

    @Test
    fun `a refused folder is the refused page with its reason`() {
        val ui = AgentHandoffUi(loading = false, block = HandoffBlock.FolderNotOnPhone)
        assertEquals(HandoffPage.Refused, ui.page())
    }

    @Test
    fun `folder fine and a qualified agent offers the choice`() {
        val ui =
            AgentHandoffUi(
                loading = false,
                connected = true,
                choices = persistentListOf(unchecked, ready),
            )
        assertEquals(HandoffPage.Choose, ui.page())
    }

    @Test
    fun `folder fine but no qualified agent leads to Agents`() {
        assertEquals(
            HandoffPage.NoAgent,
            AgentHandoffUi(loading = false, connected = true).page(),
        )
        assertEquals(
            HandoffPage.NoAgent,
            AgentHandoffUi(
                    loading = false,
                    connected = true,
                    choices = persistentListOf(unchecked),
                )
                .page(),
        )
    }

    @Test
    fun `a review in progress is active`() {
        val ui =
            AgentHandoffUi(
                loading = false,
                connected = true,
                choices = persistentListOf(ready),
                attempt = HandoffState(phase = HandoffPhase.Ready),
            )
        assertEquals(HandoffPage.Active, ui.page())
    }
}
