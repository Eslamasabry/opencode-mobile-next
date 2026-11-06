package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test

/** N-22: only the foreground modal owns accessibility, with page state preserved underneath. */
@OptIn(ExperimentalTestApi::class)
class KitModalSemanticsTest {
    @Test
    fun `B-KIT-032 sheet and dialog expose their contents and restore the page after close`() =
        runComposeUiTest {
            var sheet by mutableStateOf(true)
            var dialog by mutableStateOf(false)
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    FakeKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    KitSheetHost {
                        KitText("Conversation underneath")
                        KitSheet(sheet, { sheet = false }, "Message timeline") {
                            KitText("Run the shell command")
                        }
                        KitAlert(
                            visible = dialog,
                            title = "Confirm action",
                            onDismissRequest = { dialog = false },
                            body = "Review the action",
                        )
                    }
                }
            }
            onNodeWithText("Message timeline").assertExists()
            onNodeWithText("Run the shell command").assertExists()
            onNodeWithContentDescription("Close").assertExists()
            onNodeWithText("Conversation underneath").assertDoesNotExist()
            runOnIdle { dialog = true }
            onNodeWithText("Confirm action").assertExists()
            onNodeWithText("Review the action").assertExists()
            onNodeWithText("Run the shell command").assertDoesNotExist()
            runOnIdle { dialog = false }
            onNodeWithText("Run the shell command").assertExists()
            onNodeWithContentDescription("Close").performClick()
            onNodeWithText("Conversation underneath").assertExists()
            onNodeWithText("Run the shell command").assertDoesNotExist()
        }
}
