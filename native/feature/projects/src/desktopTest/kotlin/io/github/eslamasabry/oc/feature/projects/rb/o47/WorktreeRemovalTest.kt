package io.github.eslamasabry.oc.feature.projects.rb.o47

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceUi
import io.github.eslamasabry.oc.feature.projects.ui.ProjectWorkspaceSheet
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class WorktreeRemovalTest {
    @Test
    fun `B-NAV-020 inspected removal stays visible and held while its action runs`() =
        runComposeUiTest {
            val tree = WorktreeInfo("review-copy", "/work/review-copy", "review", ready = true)
            val state =
                mutableStateOf(
                    ProjectWorkspaceUi(
                        visible = true,
                        removal = tree,
                        removalChanges = persistentListOf(),
                    )
                )
            val calls = mutableListOf<String>()
            val actions =
                object : ProjectWorkspaceActions {
                    override fun confirmWorktreeRemoval() {
                        calls += "remove"
                        state.value = state.value.copy(busy = true)
                    }

                    override fun cancelWorktreeRemoval() {
                        calls += "cancel"
                    }
                }
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                    localeTag = "en",
                ) {
                    KitScreen { ProjectWorkspaceSheet(state.value, actions) }
                }
            }
            onNode(hasSetTextAction()).performTextInput(tree.name)
            onNodeWithText("Remove", useUnmergedTree = true).performClick()
            onNodeWithText("Remove “review-copy”?", useUnmergedTree = true).assertExists()
            onNode(hasSetTextAction()).assertTextContains(tree.name)
            onNodeWithText("Cancel", useUnmergedTree = true).assertIsNotEnabled()
            assertEquals(listOf("remove"), calls)
            runOnIdle {
                state.value = state.value.copy(visible = false, removal = null, busy = false)
            }
            onNodeWithText("Remove “review-copy”?", useUnmergedTree = true).assertDoesNotExist()
        }
}
