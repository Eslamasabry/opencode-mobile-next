package io.github.eslamasabry.oc.ui.kit.rb.o27

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitTaskItemState
import io.github.eslamasabry.oc.ui.kit.team.KitTaskSheet
import io.github.eslamasabry.oc.ui.kit.team.KitTaskSheetItem
import io.github.eslamasabry.oc.ui.kit.team.KitTaskSheetRules
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCancelled
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCompleted
import io.github.eslamasabry.oc.ui.words.res.mobileTaskInProgress
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPending
import kotlin.test.Test
import kotlin.test.assertEquals

/** part-kit_task_sheet: `lib/ui/kit/kit_task_sheet.dart` at 8302de537. */
@OptIn(ExperimentalTestApi::class)
class KitTaskSheetParityTest {
    @Test
    fun `each state has its spoken label as the Dart switch`() {
        assertEquals(Res.string.mobileTaskPending, KitTaskSheetRules.label(KitTaskItemState.Todo))
        assertEquals(Res.string.mobileTaskInProgress, KitTaskSheetRules.label(KitTaskItemState.InProgress))
        assertEquals(Res.string.mobileTaskCompleted, KitTaskSheetRules.label(KitTaskItemState.Done))
        assertEquals(Res.string.mobileTaskCancelled, KitTaskSheetRules.label(KitTaskItemState.Cancelled))
    }

    @Test
    fun `the task in progress shows a still ring when the agent is not running`() {
        assertEquals(KitTaskItemState.InProgress, KitTaskSheetRules.markState(KitTaskItemState.InProgress, running = true))
        assertEquals(KitTaskItemState.Todo, KitTaskSheetRules.markState(KitTaskItemState.InProgress, running = false))
        for (state in listOf(KitTaskItemState.Todo, KitTaskItemState.Done, KitTaskItemState.Cancelled))
            assertEquals(state, KitTaskSheetRules.markState(state, running = false))
        assertEquals("task-sheet", KitTaskSheetRules.SHEET_TAG)
    }

    @Test
    fun `the sheet is titled Tasks with N of M done and lists every task`() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), localeTag = "en", stillFrame = true) {
                KitSheetHost {
                    KitTaskSheet(
                        visible = open,
                        onDismissRequest = { open = false },
                        items =
                            listOf(
                                KitTaskSheetItem("Read the failing test", KitTaskItemState.Done),
                                KitTaskSheetItem("Fix the parser", KitTaskItemState.InProgress),
                                KitTaskSheetItem("Run the suite", KitTaskItemState.Todo),
                            ),
                        done = 1,
                        total = 3,
                        running = false,
                    )
                }
            }
        }
        onNodeWithText("Tasks").assertExists()
        onNodeWithText("1 of 3 done").assertExists()
        onNodeWithTag("task-sheet").assertExists()
        onNodeWithText("Fix the parser").assertExists()
        // Not running: the in-progress task keeps its words but shows the still (waiting) ring.
        onNodeWithTag("kit-task-mark-Todo", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(false, open)
    }
}
