package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.tasks.presentation.ChatTask
import io.github.eslamasabry.oc.feature.chat.tasks.presentation.ChatTaskList
import io.github.eslamasabry.oc.feature.chat.tasks.presentation.ChatTaskStatus
import io.github.eslamasabry.oc.feature.chat.tasks.ui.ChatTasksSheet
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItem
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItemState
import io.github.eslamasabry.oc.ui.kit.state.KitTaskLines
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChatTasksScreenTest {
    @Test
    fun `C01 task sheet keeps cancelled tasks outside the tracked count and closes without mutation`() =
        runComposeUiTest {
            var closes = 0
            val tasks =
                ChatTaskList(
                    listOf(
                        ChatTask("Reproduce the flaky run", ChatTaskStatus.Completed),
                        ChatTask("Wait for the settled state", ChatTaskStatus.InProgress),
                        ChatTask("Run checkout twenty times", ChatTaskStatus.Pending),
                        ChatTask("Rewrite the page", ChatTaskStatus.Cancelled),
                    )
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    // A kit sheet draws only inside a sheet host (KitScreen places one in the app).
                    KitSheetHost { ChatTasksSheet(tasks, true, onDismiss = { closes++ }, running = false) }
                }
            }
            onNodeWithText("1 of 3 done").assertExists()
            onNodeWithContentDescription("In progress").assertExists()
            onNodeWithContentDescription("Cancelled").assertExists()
            onNodeWithText("Rewrite the page").assertExists()
            onNodeWithContentDescription("Dismiss").performClick()
            runOnIdle { assertEquals(1, closes) }
        }

    @Test
    fun `C01 kit task mark stops when the agent stops while retaining the task status`() =
        runComposeUiTest {
            var running by mutableStateOf(true)
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    KitTaskLines(
                        listOf(
                            KitTaskItem(
                                "Wait for the settled state",
                                KitTaskItemState.InProgress,
                                "In progress",
                            )
                        ),
                        running = running,
                    )
                }
            }
            onNodeWithTag("kit-task-mark-InProgress", useUnmergedTree = true).assertExists()
            runOnIdle { running = false }
            onNodeWithTag("kit-task-mark-Todo", useUnmergedTree = true).assertExists()
            onNodeWithTag("kit-task-mark-InProgress", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithContentDescription("In progress").assertExists()
            onNodeWithText("Wait for the settled state").assertExists()
        }
}
