package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Add an agent" in the New conversation agent sheet (Flutter b7ef1c8b4). */
@OptIn(ExperimentalTestApi::class)
class NewConversationAddAgentTest {
    private class Actions : NewConversationActions {
        var addAgentTaps = 0

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
        override fun askNewProject() {}
        override fun createProject(name: String?) {}
        override fun agents() { addAgentTaps++ }
    }

    private fun ui(agents: List<String>) =
        NewConversationUi(
            profile = ProfileId("p1"),
            // The form (and its field) shows once a server is saved; without one the page offers to add a server.
            servers = listOf(NewConversationServer(ProfileId("p1"), "OpenCode", connected = true)),
            setup =
                NewConversationSetup(
                    directory = "/root/projects/shop",
                    projects = listOf(NewConversationProject("/root/projects/shop", "shop", false)),
                    models = emptyList(),
                    agents = agents,
                ),
            directory = "/root/projects/shop",
            sheet = NewConversationSheet.Agents,
        )

    private fun ComposeUiTest.show(state: NewConversationUi, actions: Actions, tag: String = "en") =
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = tag,
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                NewConversationScreen(state, actions)
            }
        }

    @Test
    fun `B-NAV-050 with no extra agents Add an agent leads the list and opens agent setup`() = runComposeUiTest {
        val actions = Actions()
        show(ui(emptyList()), actions)
        onNodeWithText("Add an agent").assertExists()
        val add = onNodeWithTag("chats-new-add-agent").fetchSemanticsNode().boundsInRoot.top
        val default = onNodeWithText("OpenCode").fetchSemanticsNode().boundsInRoot.top
        assertTrue(add < default, "Add an agent comes before the default agent when nothing else is installed")
        onNodeWithTag("chats-new-add-agent").performClick()
        assertEquals(1, actions.addAgentTaps)
    }

    @Test
    fun `B-NAV-050 with agents installed Add an agent sits in the first rows, right after the default`() = runComposeUiTest {
        val actions = Actions()
        show(ui(listOf("claude", "codex")), actions)
        val default = onNodeWithText("OpenCode").fetchSemanticsNode().boundsInRoot.top
        val add = onNodeWithTag("chats-new-add-agent").fetchSemanticsNode().boundsInRoot.top
        val claude = onNodeWithText("claude").fetchSemanticsNode().boundsInRoot.top
        assertTrue(default < add && add < claude, "default, then Add an agent, then the installed agents")
    }

    @Test
    fun `B-NAV-050 Add an agent is in Arabic words in Arabic`() = runComposeUiTest {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
        try {
            show(ui(emptyList()), Actions(), tag = "ar")
            onNodeWithText("إضافة وكيل").assertExists()
            onNodeWithText("Add an agent").assertDoesNotExist()
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `B-098 coming back with the agent sheet open does not raise the keyboard over it`() = runComposeUiTest {
        show(ui(listOf("claude")), Actions())
        waitForIdle()
        // The open sheet hides the page behind it from the merged tree (a screen reader reads only the sheet), so the
        // field is looked up in the unmerged tree.
        onNode(hasSetTextAction(), useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun `B-098 with no sheet open the field still takes focus`() = runComposeUiTest {
        show(ui(listOf("claude")).copy(sheet = null), Actions())
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }
}
