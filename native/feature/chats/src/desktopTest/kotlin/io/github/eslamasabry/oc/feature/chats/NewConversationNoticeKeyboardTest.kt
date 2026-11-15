package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
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

    @Test
    fun `a two-line notice with two actions is whole and reachable above the chips when the keyboard leaves little room`() =
        runDesktopComposeUiTest(360, 460) {
            val dir = "/storage/emulated/0/octest"
            val ui =
                NewConversationUi(
                    profile = ProfileId("p1"),
                    servers = listOf(NewConversationServer(ProfileId("p1"), "This phone", connected = true)),
                    setup = NewConversationSetup(directory = dir, projects = emptyList(), models = emptyList(), agents = emptyList()),
                    directory = dir,
                    text = "Run pwd then append the word world to note.txt then run cat note.txt and tell me both outputs. " + "Then list the folder and say what you see in it, one line for each file, and stop. ".repeat(2),
                    phoneFolder = PhoneFolderReach.Restart,
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(ui, Actions)
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val chips = onNodeWithTag("chats-new-choices").getBoundsInRoot()
            for (word in listOf("Restart", "Choose another project")) {
                val found = onAllNodesWithText(word, substring = true).fetchSemanticsNodes()
                assertTrue(found.isNotEmpty(), "$word is on the page")
                val b = onAllNodesWithText(word, substring = true)[0].getBoundsInRoot()
                assertTrue(b.bottom <= chips.top, "$word ends at ${b.bottom}, the chips start at ${chips.top}: it is under them")
                assertTrue(b.top >= 0.dp, "$word starts at ${b.top}: above the page")
                // Bounds are clipped to what shows: an action cut by the page's edge is short, one scrolled away is absent.
                assertTrue((b.bottom - b.top) >= 36.dp, "$word shows only ${b.bottom - b.top} of its height: cut off by the page's edge")
                onAllNodesWithText(word, substring = true)[0].assertIsDisplayed()
            }
        }
}
