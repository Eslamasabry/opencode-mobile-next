package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
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
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/** F-041: Start tapped on an empty box answers at the box, never with a toast over the title. */
@OptIn(ExperimentalTestApi::class)
class NewConversationEmptyStartTest {
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
    fun `I147-FIRST-RUN Start on an empty box drops no toast over the title`() =
        kitUiTest(360, 800) {
            val phone = ProfileId("p1")
            val ui =
                NewConversationUi(
                    profile = phone,
                    servers = listOf(NewConversationServer(phone, "This phone", connected = true)),
                    setup = NewConversationSetup("/root/projects/a", emptyList(), emptyList(), emptyList()),
                    directory = "/root/projects/a",
                )
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(ui, Actions)
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithText("Start").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(500)
            onAllNodesWithTag("kit-toast").assertCountEquals(0)
        }
}
