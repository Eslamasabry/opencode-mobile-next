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

    @Test
    fun `a refusal with no known reason or unconfirmed agents offers Agents`() {
        assertEquals(true, AgentHandoffUi(loading = false).offersAgents())
        assertEquals(
            true,
            AgentHandoffUi(loading = false, block = HandoffBlock.NotConfirmed).offersAgents(),
        )
    }

    @Test
    fun `a refusal about the conversation itself does not offer Agents`() {
        for (block in
            listOf(
                HandoffBlock.SourceOnComputer,
                HandoffBlock.FolderNotOnPhone,
                HandoffBlock.FolderUnknown,
            )) assertEquals(false, AgentHandoffUi(loading = false, block = block).offersAgents())
    }

    private fun view(ui: AgentHandoffUi) = ui.stateView()

    @Test
    fun `helper not installed is the normal no-agent page with Open Agents first`() {
        val ui = AgentHandoffUi(loading = false, block = HandoffBlock.NoAgentReady)
        assertEquals(HandoffPage.NoAgent, ui.page())
        assertEquals(
            HandoffStateView(
                HandoffHeadline.NoAgentReady,
                HandoffBody.NoAgentReady,
                HandoffButton.OpenAgents,
                HandoffButton.Refresh,
            ),
            view(ui),
        )
    }

    @Test
    fun `the no-agent tag survives the host failure round trip`() {
        assertEquals(
            HandoffBlock.NoAgentReady,
            HandoffBlock.of(HandoffBlock.NoAgentReady.failure()),
        )
    }

    @Test
    fun `helper installed but not running that cannot start says could not confirm with both buttons`() {
        val ui = AgentHandoffUi(loading = false, block = HandoffBlock.NotConfirmed)
        assertEquals(HandoffPage.Refused, ui.page())
        assertEquals(
            HandoffStateView(
                HandoffHeadline.Unavailable,
                HandoffBody.Unconfirmed,
                HandoffButton.OpenAgents,
                HandoffButton.Refresh,
            ),
            view(ui),
        )
    }

    @Test
    fun `helper running but no agent signed in is the same no-agent page`() {
        for (choices in listOf(persistentListOf(), persistentListOf(unchecked))) {
            val ui = AgentHandoffUi(loading = false, connected = true, choices = choices)
            assertEquals(
                HandoffStateView(
                    HandoffHeadline.NoAgentReady,
                    HandoffBody.NoAgentReady,
                    HandoffButton.OpenAgents,
                    HandoffButton.Refresh,
                ),
                view(ui),
            )
        }
    }

    @Test
    fun `one ready agent shows the choice and no state page`() {
        val ui =
            AgentHandoffUi(loading = false, connected = true, choices = persistentListOf(ready))
        assertEquals(HandoffPage.Choose, ui.page())
        assertEquals(null, view(ui))
    }

    @Test
    fun `an unknown refusal offers Open Agents then Refresh`() {
        assertEquals(
            HandoffStateView(
                HandoffHeadline.Unavailable,
                HandoffBody.NotStarted,
                HandoffButton.OpenAgents,
                HandoffButton.Refresh,
            ),
            view(AgentHandoffUi(loading = false)),
        )
    }

    @Test
    fun `a refusal about the conversation has Refresh only`() {
        val expected =
            mapOf(
                HandoffBlock.SourceOnComputer to HandoffBody.Computer,
                HandoffBlock.FolderNotOnPhone to HandoffBody.Folder,
                HandoffBlock.FolderUnknown to HandoffBody.FolderUnknown,
            )
        for ((block, body) in expected)
            assertEquals(
                HandoffStateView(HandoffHeadline.Unavailable, body, HandoffButton.Refresh, null),
                view(AgentHandoffUi(loading = false, block = block)),
            )
    }

    @Test
    fun `loading and active pages have no state page`() {
        assertEquals(null, view(AgentHandoffUi()))
    }
}
