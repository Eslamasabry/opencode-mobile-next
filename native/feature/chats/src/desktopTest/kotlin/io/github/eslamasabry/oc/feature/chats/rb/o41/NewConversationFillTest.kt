package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
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

/** The prompt box takes what the presenter puts in it (a starter, a second starter, the clear after Start). */
@OptIn(ExperimentalTestApi::class)
class NewConversationFillTest {
    // Words typed are never reported back to the state here, as when the report is slower than the fill.
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

    private val base =
        NewConversationUi(
            profile = ProfileId("p1"),
            servers = listOf(NewConversationServer(ProfileId("p1"), "OpenCode", connected = true)),
            setup =
                NewConversationSetup(
                    directory = "/root/projects/shop",
                    projects = listOf(NewConversationProject("/root/projects/shop", "shop", false)),
                    models = emptyList(),
                    agents = emptyList(),
                ),
            directory = "/root/projects/shop",
        )

    @Test
    fun `one starter fills the box, a second replaces it and the clear after Start empties it`() = runComposeUiTest {
        var state by mutableStateOf(base)
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                NewConversationScreen(state, Actions)
            }
        }
        waitForIdle()
        runOnIdle { state = state.copy(text = "Find a bug in ", textRevision = 1) }
        waitForIdle()
        onNode(hasSetTextAction()).assertTextEquals("Find a bug in ")
        runOnIdle { state = state.copy(text = "Add tests for ", textRevision = 2) }
        waitForIdle()
        onNode(hasSetTextAction()).assertTextEquals("Add tests for ")
        runOnIdle { state = state.copy(text = "", textRevision = 3) }
        waitForIdle()
        onNode(hasSetTextAction()).assertTextEquals("")
    }
}
