package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsSetUpAction
import io.github.eslamasabry.oc.ui.words.res.chatSendFailed
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureNeedsSetUp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-AGENT-113 (set up at first use, the chat seam), drawn: a failed send whose agent was never set up shows one plain
 * line and one action, "Set up <agent>", in place of Not sent / Try again; the typed words stay in the bubble and the
 * action opens the agent's set-up terminal. A failure without that need keeps today's Not sent / Try again.
 */
@OptIn(ExperimentalTestApi::class)
class AgentSetUpBubbleTest {
    private val needs: String by lazy { runBlocking { getString(Res.string.chatSendFailureNeedsSetUp, "Qwen Code") } }
    private val setUp: String by lazy { runBlocking { getString(Res.string.agentsSetUpAction, "Qwen Code") } }
    private val notSent: String by lazy { runBlocking { getString(Res.string.chatSendFailed) } }

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

    private val failure = Failure.Unavailable(FeatureId("agentStart"))

    @Test
    fun `B-AGENT-113 a send that needs its agent set up says so once and opens the set-up terminal`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene(outside = outside)
            val bubble =
                PendingSend(7L, "fix the tests", PendingState.Failed, failure, setUp = AgentSetUpNeed("qwen", "Qwen Code"))
            setContent { E20Scene { scene.Screen(scene.live().copy(pending = persistentListOf(bubble))) } }
            waitForIdle()
            onNodeWithText("fix the tests").assertExists()
            onNodeWithText(needs).assertExists()
            onNodeWithText(notSent).assertDoesNotExist()
            onNodeWithTag("chat-send-error-try-again").assertDoesNotExist()
            onNodeWithText(setUp).performClick()
            waitForIdle()
            assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "qwen")), scene.nav.opened.toList())
            assertEquals(emptyList<String>(), scene.world.calls.filter { it.startsWith("retrySend") }, "nothing resent")
        }

    @Test
    fun `B-AGENT-113 a plain failure keeps Not sent and Try again`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene(outside = outside)
            val bubble = PendingSend(7L, "fix the tests", PendingState.Failed, failure)
            setContent { E20Scene { scene.Screen(scene.live().copy(pending = persistentListOf(bubble))) } }
            waitForIdle()
            onNodeWithText(notSent).assertExists()
            onNodeWithText(needs).assertDoesNotExist()
            onNodeWithText(setUp).assertDoesNotExist()
            onNodeWithTag("chat-send-error-try-again").performClick()
            waitForIdle()
            assertEquals(listOf("retrySend 7"), scene.world.calls.filter { it.startsWith("retrySend") })
        }
}
