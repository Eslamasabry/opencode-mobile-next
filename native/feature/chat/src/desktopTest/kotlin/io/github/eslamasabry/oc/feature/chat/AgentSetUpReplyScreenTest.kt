package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsSetUpAction
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureNeedsSetUp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-AGENT-113 (set up at first use, the reply seam), drawn: omp's failed first answer shows "Oh My Pi needs setting up
 * before it can answer" and one action, "Set up Oh My Pi", which opens its set-up terminal; the person's message above it
 * stays as sent. A failed reply without that need keeps today's words.
 */
@OptIn(ExperimentalTestApi::class)
class AgentSetUpReplyScreenTest {
    private val needs: String by lazy { runBlocking { getString(Res.string.chatSendFailureNeedsSetUp, "Oh My Pi") } }
    private val setUp: String by lazy { runBlocking { getString(Res.string.agentsSetUpAction, "Oh My Pi") } }

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

    private val rows =
        persistentListOf<TranscriptRow>(
            FooterRow("footer:u1", "u1", failed = true, reason = "No model selected.", providerId = "omp-acp"),
            prompt("u1", "explain this repo"),
        )

    @Test
    fun `B-AGENT-113 omp's failed first answer says it needs setting up and opens its set-up`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene(outside = outside)
            val ui =
                scene.live().copy(
                    rows = rows,
                    setUpReplies = persistentMapOf("u1" to AgentSetUpNeed("omp-acp", "Oh My Pi")),
                )
            setContent { E20Scene { scene.Screen(ui) } }
            waitForIdle()
            onNodeWithText("explain this repo").assertExists()
            onNodeWithText(needs).assertExists()
            onNodeWithTag("error-action-sign-in-again").assertDoesNotExist()
            onNodeWithText(setUp).performClick()
            waitForIdle()
            assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "omp-acp")), scene.nav.opened.toList())
        }

    @Test
    fun `B-AGENT-113 a failed reply without that need keeps today's words`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene(outside = outside)
            setContent { E20Scene { scene.Screen(scene.live().copy(rows = rows)) } }
            waitForIdle()
            onNodeWithTag("chat-reply-failed").assertExists()
            onNodeWithText(needs).assertDoesNotExist()
            onNodeWithText(setUp).assertDoesNotExist()
        }
}
