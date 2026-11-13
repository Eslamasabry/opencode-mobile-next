package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationWait
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NewConversationStartTest {
    private object Actions : NewConversationActions {
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) {}
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val profile = ProfileId("p1")
    private val server = listOf(NewConversationServer(profile, "OpenCode", connected = true))
    private val setup = NewConversationSetup(directory = "/root/projects/a", projects = emptyList(), models = emptyList(), agents = emptyList())

    @Test
    fun `NEW-WAIT presenter: each state names what the page waits on`() {
        assertEquals(NewConversationWait.Preparing, NewConversationUi(servers = server, profile = profile, busy = true).wait)
        assertEquals(NewConversationWait.None, NewConversationUi(servers = server, profile = profile, setup = setup).wait)
        assertEquals(
            NewConversationWait.Sending,
            NewConversationUi(servers = server, profile = profile, setup = setup, busy = true, text = "hi").wait,
        )
        assertEquals(NewConversationWait.Sending, NewConversationUi(openingChat = true, busy = true).wait)
    }

    @Test
    fun `NEW-WAIT while preparing Start is off and says Getting ready, never the generic wait line`() = runComposeUiTest {
        setContent { Themed { NewConversationScreen(NewConversationUi(servers = server, profile = profile, busy = true), Actions) } }
        assertTrue(onAllNodesWithText("Getting ready…").fetchSemanticsNodes().isNotEmpty())
        onAllNodesWithText("Please wait for the current action.").assertCountEquals(0)
    }

    @Test
    fun `NEW-WAIT an empty prompt on a ready page asks for the prompt, not a wait`() = runComposeUiTest {
        setContent { Themed { NewConversationScreen(NewConversationUi(servers = server, profile = profile, setup = setup, directory = "/root/projects/a"), Actions) } }
        onAllNodesWithText("Getting ready…").assertCountEquals(0)
        onAllNodesWithText("Please wait for the current action.").assertCountEquals(0)
    }
}
