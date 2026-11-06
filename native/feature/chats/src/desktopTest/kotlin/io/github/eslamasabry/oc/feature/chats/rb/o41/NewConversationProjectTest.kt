package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalTestApi::class)
class NewConversationProjectTest {
    private class Actions : NewConversationActions {
        var selected: String? = null

        override fun back() {}

        override fun addServer() {}

        override fun chooseServer(profile: ProfileId) {}

        override fun draftChanged(text: String) {}

        override fun chooseDirectory(directory: String) {
            selected = directory
        }

        override fun chooseModel(model: ModelRef?) {}

        override fun chooseAgent(agent: String?) {}

        override fun separateCopy(on: Boolean) {}

        override fun sheet(sheet: NewConversationSheet?) {}

        override fun start() {}

        override fun import(conversation: ImportableConversation) {}

        override fun retryLoad() {}

        var askedName = false
        var created: String? = null

        override fun askNewProject() {
            askedName = true
        }

        override fun createProject(name: String?) {
            created = name
        }
    }

    @Test
    fun `B07 New project sits under the recent projects and asks for a name`() =
        runComposeUiTest {
            val actions = Actions()
            val setup =
                NewConversationSetup(
                    directory = "/root/projects/shop",
                    projects = listOf(NewConversationProject("/root/projects/shop", "shop", false)),
                    models = emptyList(),
                    agents = emptyList(),
                )
            var state by
                mutableStateOf(
                    NewConversationUi(
                        profile = ProfileId("p1"),
                        setup = setup,
                        directory = "/root/projects/shop",
                        sheet = NewConversationSheet.Projects,
                        canCreateProject = true,
                    )
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    NewConversationScreen(state, actions)
                }
            }
            onNodeWithText("shop").assertExists()
            onNodeWithText("New project").performClick()
            runOnIdle { assertEquals(true, actions.askedName) }
            runOnIdle { state = state.copy(askingProjectName = true) }
            onNodeWithText("Create").assertExists()
            runOnIdle { state = state.copy(canCreateProject = false, askingProjectName = false) }
            onNodeWithText("New project").assertDoesNotExist()
        }

    @Test
    fun `B07 project picker starts from the loaded project and preserves current typing`() =
        runComposeUiTest {
            var state by mutableStateOf(NewConversationUi())
            val actions = Actions()
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    NewConversationScreen(state, actions)
                }
            }
            runOnIdle {
                state = state.copy(directory = "/work/shop", sheet = NewConversationSheet.Projects)
            }
            // The typed path is folded under its own row (recent projects and New project come first).
            onNodeWithText("Choose a folder by path").performClick()
            val folder = onNode(hasContentDescription("Project folder") and hasSetTextAction())
            folder.assertTextEquals("/work/shop")
            folder.performTextReplacement("/work/custom")
            runOnIdle { state = state.copy(text = "A background draft update") }
            folder.assertTextEquals("/work/custom")
            onNodeWithText("Use this folder").performClick()
            runOnIdle { assertEquals("/work/custom", actions.selected) }

            runOnIdle { state = state.copy(sheet = null, directory = "/work/other") }
            onNode(hasContentDescription("Project folder") and hasSetTextAction())
                .assertDoesNotExist()
            runOnIdle { state = state.copy(sheet = NewConversationSheet.Projects) }
            onNodeWithText("Choose a folder by path").performClick()
            folder.assertTextEquals("/work/other")
            onNodeWithText("Use this folder").performClick()
            runOnIdle { assertEquals("/work/other", actions.selected) }
        }
}
