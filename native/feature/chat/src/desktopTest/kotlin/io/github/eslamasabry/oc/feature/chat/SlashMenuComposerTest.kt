package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerSlashCommand
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** S30 story 2 on the screen: typing "/" lists the app's own actions and the server's commands, then Show all. */
@OptIn(ExperimentalTestApi::class)
class SlashMenuComposerTest {
    private class Actions : BasicComposerActions {
        val drafts = mutableListOf<String>()

        override fun draftChanged(text: String) {
            drafts += text
        }

        override suspend fun send(text: String): Boolean = true
    }

    private val ran = mutableListOf<String>()
    private var showAll = 0

    private fun app(slash: String, title: String) =
        ComposerSlashCommand(slash, slash, title, "$title here", onRun = { ran += slash })

    private val commands =
        listOf(
            app("agents", "Agent"),
            app("approvals", "Approvals"),
            app("connect", "Connect provider"),
            app("new", "New session"),
            app("timeline", "Message timeline"),
            app("find", "Find"),
            ComposerSlashCommand("server-init", "init", "/init", "Create AGENTS.md", inserts = true, onRun = { ran += "init" }),
        )

    private fun chrome() =
        ComposerChrome(
            { false }, { false }, {}, { null }, {}, { null }, {}, {}, { false },
            openCommands = { showAll++ },
            slashCommands = { commands },
        )

    private val ui =
        BasicComposerUi(
            available = true, sending = false, steers = true, draft = DraftFacts(), targetKey = "p/s",
            pickingFile = false, attachmentFailure = null, filesAvailable = true,
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(actions: Actions) {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
                localeTag = "en",
            ) {
                Box(Modifier.width(400.dp)) { BasicComposer(ui, actions, chrome()) }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.type(text: String) {
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field")), useUnmergedTree = true)
            .performTextInput(text)
        waitForIdle()
    }

    @Test
    fun `S30-CHAT-002 a slash lists app actions five at most, then Show all opens the Commands sheet`() =
        runComposeUiTest {
            show(Actions())
            type("/")
            onNodeWithTag("chat-slash-menu").assertIsDisplayed()
            onNodeWithText("/agents").assertIsDisplayed()
            onNodeWithText("/approvals").assertIsDisplayed()
            onNodeWithText("/connect").assertIsDisplayed()
            onNodeWithText("Show all").performClick()
            assertEquals(1, showAll)
        }

    @Test
    fun `S30-CHAT-002 an app action leaves the box and runs, a server command fills the box`() = runComposeUiTest {
        val actions = Actions()
        show(actions)
        type("/appr")
        onNodeWithText("/approvals").performClick()
        waitForIdle()
        assertEquals(listOf("approvals"), ran)
        assertEquals("", actions.drafts.last(), "the typed /appr leaves the box")
        type("/in")
        onNodeWithText("/init").performClick()
        waitForIdle()
        assertEquals(listOf("approvals"), ran, "a server command is not run from the menu")
        assertEquals("/init ", actions.drafts.last())
    }
}
