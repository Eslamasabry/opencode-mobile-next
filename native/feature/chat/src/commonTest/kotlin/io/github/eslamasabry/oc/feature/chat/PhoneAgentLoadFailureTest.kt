package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLoadAgent
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OMP-OPEN (owner 2026-11-15: an Oh My Pi conversation said "Couldn't open this conversation. Nothing is lost. Try again
 * when OpenCode answers."): the load-failure page of a conversation on this phone's agents names that agent, and offers
 * its set-up, for every agent the phone's helper runs. A server's own conversation is unchanged.
 */
class PhoneAgentLoadFailureTest {
    private val helperRef = ConversationRef(ProfileId("phone-agents"), SessionId("draft-1"))
    private val owner = ProfileId("phone")

    private class Outside(private val owner: ProfileId?) : ChatOutside {
        override fun phoneAgentOwner(ref: ConversationRef): ProfileId? = owner

        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

        override fun openLink(url: String): Boolean = false
    }

    private class HelperWorld(ref: ConversationRef, var runtime: String?, var helper: Boolean = true) :
        FakeChatWorld(ref) {
        override val serverIsAgentHelper: Boolean
            get() = helper

        override fun runtimeId(): String? = runtime
    }

    private val phoneAgents = AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }

    @Test
    fun `OMP-OPEN-6 every phone agent's conversation that cannot be opened names that agent and offers its set-up`() {
        assertTrue(phoneAgents.any { it.id.raw == "omp-acp" }, "Oh My Pi is one of them")
        for (agent in phoneAgents) {
            val nav = RecordingNavigator()
            val world = HelperWorld(helperRef, agent.providerId)
            val p = ChatPresenter(world, nav, Immediate, outside = Outside(owner))
            world.load.value = ChatLoad.Failed(Failure.NotFound())
            assertEquals(
                ChatPhase.Failed(Failure.NotFound(), ChatLoadAgent(agent.name, agent.id.raw)),
                p.current().phase,
                agent.id.raw,
            )
            p.setUpAgent(agent.id.raw)
            assertEquals(listOf<Any>(AgentsRoute.SignInTerminal(owner, agent.id.raw)), nav.opened.toList(), agent.id.raw)
        }
    }

    @Test
    fun `OMP-OPEN-7 a phone agents' conversation whose agent is no longer known speaks of the phone's agents, without a set-up`() {
        val world = HelperWorld(helperRef, runtime = null)
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = Outside(owner))
        world.load.value = ChatLoad.Failed(Failure.NotFound())
        assertEquals(ChatPhase.Failed(Failure.NotFound(), ChatLoadAgent(null, null)), p.current().phase)
        // Where this phone has no set-up terminal for the conversation's agents, the agent is named but not offered.
        val noOwner = HelperWorld(helperRef, runtime = "omp-acp")
        val q = ChatPresenter(noOwner, RecordingNavigator(), Immediate, outside = Outside(null))
        noOwner.load.value = ChatLoad.Failed(Failure.NotFound())
        assertEquals(ChatPhase.Failed(Failure.NotFound(), ChatLoadAgent("Oh My Pi", null)), q.current().phase)
    }

    @Test
    fun `OMP-OPEN-8 a server's own conversation keeps its page`() {
        val world = HelperWorld(helperRef, runtime = "omp-acp", helper = false)
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = Outside(owner))
        world.load.value = ChatLoad.Failed(Failure.Network(NetworkWhy.Offline))
        assertEquals(ChatPhase.Failed(Failure.Network(NetworkWhy.Offline)), p.current().phase)
    }
}
