package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLoadAgent
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OMP-OPEN guard: the load-failure page of a conversation on this phone's agents never names OpenCode. For every agent
 * the phone's helper runs it names that agent, says what happened (the agents do not have the conversation, or have
 * not answered) and offers "Set up <agent>", which opens that agent's set-up terminal. Fails on the page before the
 * fix, which read "Try again when OpenCode answers." for all of them.
 */
@OptIn(ExperimentalTestApi::class)
class PhoneAgentLoadPageTest {
    private val owner = ProfileId("phone")

    private val outside =
        object : ChatOutside {
            override fun phoneAgentOwner(ref: ConversationRef): ProfileId = owner

            override fun mask(text: String): String = text

            override fun copy(text: String): Boolean = false

            override fun share(text: String): Boolean = false

            override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

            override fun openLink(url: String): Boolean = false
        }

    /** Every text on the load-failure page, joined: what a person reads there. */
    private fun androidx.compose.ui.test.ComposeUiTest.screenText(): String {
        fun words(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::words)
        return words(onNodeWithTag("chat-load-error", useUnmergedTree = true).fetchSemanticsNode()).joinToString("\n")
    }

    @Test
    fun `OMP-OPEN-9 no phone agent's load-failure page names OpenCode, each names its agent and offers its set-up`() {
        val agents = AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }
        for (agent in agents) {
            for (failure in listOf(Failure.NotFound(), Failure.Network(NetworkWhy.Offline))) {
                kitUiTest(412, 915) {
                    val scene = ChatScene(outside = outside)
                    val phase = ChatPhase.Failed(failure, ChatLoadAgent(agent.name, agent.id.raw))
                    setContent { E20Scene { scene.Screen(scene.live().copy(phase = phase, rows = persistentListOf())) } }
                    waitForIdle()
                    val text = screenText()
                    assertTrue("OpenCode" !in text, "${agent.id.raw} ${failure::class.simpleName}: the page reads\n$text")
                    assertTrue(agent.name in text, "${agent.id.raw}: the page names the agent\n$text")
                    if (failure is Failure.NotFound)
                        assertTrue("doesn't have this conversation" in text, "${agent.id.raw}: says what happened\n$text")
                    onNodeWithText("Set up ${agent.name}").performClick()
                    assertEquals(
                        listOf<Any>(AgentsRoute.SignInTerminal(owner, agent.id.raw)),
                        scene.nav.opened.toList(),
                        agent.id.raw,
                    )
                }
            }
        }
    }

    @Test
    fun `OMP-OPEN-10 a phone agents' conversation whose agent is unknown speaks of the phone's agents, never OpenCode`() =
        kitUiTest(412, 915) {
            val scene = ChatScene(outside = outside)
            val phase = ChatPhase.Failed(Failure.NotFound(), ChatLoadAgent(null, null))
            setContent { E20Scene { scene.Screen(scene.live().copy(phase = phase, rows = persistentListOf())) } }
            waitForIdle()
            val text = screenText()
            assertTrue("OpenCode" !in text, text)
            assertTrue("The phone's agents don't have this conversation" in text, text)
        }
}
