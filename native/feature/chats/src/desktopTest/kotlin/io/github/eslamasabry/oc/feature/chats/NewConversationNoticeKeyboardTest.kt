package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderReach
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue 147, device finding (S22, keyboard up): a notice with its actions sat under the page's bottom edge, one line of
 * it showing and both actions out of sight. With the room a keyboard leaves (360 x 460 dp), the notice and both of its
 * actions are whole above the chips.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationNoticeKeyboardTest {
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

    private class Counting : NewConversationActions by Actions {
        var fixes = 0

        override fun fixPhoneFolder() {
            fixes++
        }
    }

    private fun main(height: Int) =
        kitUiTest(360, height) {
            val dir = "/storage/emulated/0/octest"
            val base =
                NewConversationUi(
                    profile = ProfileId("p1"),
                    servers = listOf(NewConversationServer(ProfileId("p1"), "This phone", connected = true)),
                    setup = NewConversationSetup(directory = dir, projects = emptyList(), models = emptyList(), agents = emptyList()),
                    directory = dir,
                    text = "Run pwd then append the word world to note.txt then run cat note.txt and tell me both outputs. " + "Then list the folder and say what you see in it, one line for each file, and stop. ".repeat(2),
                )
            var ui by androidx.compose.runtime.mutableStateOf(base.copy(phoneFolder = PhoneFolderReach.Restart))
            val actions = Counting()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(ui, actions)
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            // The notice blocks starting: its primary action stands in Start's place, whole, above the keyboard.
            val main = onNodeWithTag("chats-new-main-action")
            main.assertIsDisplayed()
            val b = main.getBoundsInRoot()
            assertTrue((b.bottom - b.top) >= 36.dp, "the main button shows only ${b.bottom - b.top}")
            onAllNodesWithText("Start").assertCountEquals(0)
            main.performClick()
            assertEquals(1, actions.fixes, "the main button does the notice's action")
            // Resolved: the notice is gone and Start is back.
            runOnIdle { ui = base.copy(phoneFolder = PhoneFolderReach.Ready) }
            waitForIdle()
            onAllNodesWithTag("chats-new-main-action").assertCountEquals(0)
            onNodeWithText("Start").assertIsDisplayed()
        }

    @Test
    fun `a blocking notice hands its main action to the bottom block at 400 dp, keyboard up`() = main(400)

    @Test
    fun `a blocking notice hands its main action to the bottom block at 460 dp, keyboard up`() = main(460)
}
