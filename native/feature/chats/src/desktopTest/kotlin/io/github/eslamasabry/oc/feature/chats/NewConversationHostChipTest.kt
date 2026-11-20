package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.test.assertEquals
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/** Where it runs: the project chip names the connection, and the sheet it opens offers the saved connections. */
@OptIn(ExperimentalTestApi::class)
class NewConversationHostChipTest {
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

    private class Recording : NewConversationActions by Actions {
        val chosen = mutableListOf<ProfileId>()

        override fun chooseServer(profile: ProfileId) {
            chosen += profile
        }
    }

    @Test
    fun `I147-FIRST-RUN the project chip names the connection and the sheet offers the other saved connection`() =
        kitUiTest(360, 900) {
            val a = ProfileId("a")
            val b = ProfileId("b")
            val dir = "/root/projects/my-app"
            fun ui(sheet: NewConversationSheet?) =
                NewConversationUi(
                    profile = b,
                    servers = listOf(NewConversationServer(a, "This phone", connected = true), NewConversationServer(b, "Dev box", connected = true)),
                    setup = NewConversationSetup(dir, listOf(NewConversationProject(dir, "my-app", false)), emptyList(), emptyList()),
                    directory = dir,
                    sheet = sheet,
                )
            var current by androidx.compose.runtime.mutableStateOf(ui(null))
            val actions = Recording()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(current, actions)
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithText("my-app · Dev box").assertIsDisplayed()
            runOnIdle { current = ui(NewConversationSheet.Projects) }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithTag("chats-new-host-a").performClick()
            assertEquals(listOf(a), actions.chosen)
            // The one already chosen is not chosen again.
            onNodeWithTag("chats-new-host-b").performClick()
            assertEquals(listOf(a), actions.chosen)
        }
}
