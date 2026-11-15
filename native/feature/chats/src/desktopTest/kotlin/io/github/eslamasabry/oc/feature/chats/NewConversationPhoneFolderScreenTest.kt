package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
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
import kotlin.test.assertEquals

/**
 * Issue 147: New conversation names a folder in the phone's storage in full once, with what that storage can't do
 * behind Why, and when the folder is not ready it says why and offers the way forward instead of Start.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationPhoneFolderScreenTest {
    private val folder = "/storage/emulated/0/MyProj"

    private class Actions : NewConversationActions {
        var fixes = 0

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

        override fun fixPhoneFolder() {
            fixes++
        }
    }

    private fun state(directory: String, reach: PhoneFolderReach?) =
        NewConversationUi(
            profile = ProfileId("p1"),
            servers = listOf(NewConversationServer(ProfileId("p1"), "This phone", connected = true)),
            setup = NewConversationSetup(directory = directory, projects = emptyList(), models = emptyList(), agents = emptyList()),
            directory = directory,
            text = "hello",
            phoneFolder = reach,
        )

    @Test
    fun `I147-STORAGE a ready folder in the phone's storage is named in full once, with Why opening the help`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state(folder, PhoneFolderReach.Ready), Actions())
                }
            }
            onAllNodesWithTag("chats-new-phone-folder", useUnmergedTree = true).assertCountEquals(1)
            onAllNodesWithText(folder, substring = true).assertCountEquals(1)
            onNodeWithText("Why").performClick()
            waitForIdle()
            onAllNodesWithText("Working in the phone’s storage").assertCountEquals(1)
            onAllNodesWithText("npm install --no-bin-links", substring = true).assertCountEquals(1)
        }

    @Test
    fun `I147-STORAGE a project in the app's own folder gets no such line`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state("/root/projects/shop", null), Actions())
                }
            }
            onAllNodesWithTag("chats-new-phone-folder", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `I147-STORAGE without access the notice offers to allow it, and the action is the page's own`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state(folder, PhoneFolderReach.AppAccess), actions)
                }
            }
            onAllNodesWithTag("chats-new-phone-folder", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithText("Allow access to phone storage").performClick()
            assertEquals(1, actions.fixes)
        }
}
