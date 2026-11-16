package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * I147-NOTICE (build 10053, phone with the keyboard up): the blocking notice's action took Start's place, but the
 * notice's own sentence was out of sight and "Choose another project" was cut in half by the box's foot. With the room
 * a keyboard leaves (360 x 476 dp) the sentence and every action are whole and visible at rest: nothing is cut.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationNoticeWholeTest {
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

    private fun scene(height: Int) =
        runDesktopComposeUiTest(360, height) {
            val dir = "/storage/emulated/0/octest"
            val ui =
                NewConversationUi(
                    profile = ProfileId("p1"),
                    servers = listOf(NewConversationServer(ProfileId("p1"), "This phone", connected = true)),
                    setup = NewConversationSetup(directory = dir, projects = emptyList(), models = emptyList(), agents = emptyList()),
                    directory = dir,
                    text = "",
                    phoneFolder = PhoneFolderReach.Restart,
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(ui, Actions)
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            val main = onNodeWithTag("chats-new-main-action").getBoundsInRoot()
            for (text in listOf("started before you chose this folder", "Choose another project")) {
                val node = onNodeWithText(text, substring = true)
                val whole = node.getBoundsInRoot()
                val seen = node.fetchSemanticsNode().boundsInRoot
                val density = density.density
                assertTrue(
                    seen.height / density >= (whole.bottom - whole.top).value - 0.5f,
                    "\"$text\" is cut: shows ${seen.height / density} dp of ${(whole.bottom - whole.top).value} dp",
                )
                assertTrue(whole.top >= 0.dp && whole.bottom <= main.top, "\"$text\" is above the main action, in the page")
            }
        }

    @Test fun `I147-NOTICE sentence and every action whole with the keyboard up at 380 dp`() = scene(380)

    @Test fun `I147-NOTICE sentence and every action whole with the keyboard up at 420 dp`() = scene(420)

    @Test fun `I147-NOTICE sentence and every action whole with the keyboard up at 476 dp`() = scene(476)
}
