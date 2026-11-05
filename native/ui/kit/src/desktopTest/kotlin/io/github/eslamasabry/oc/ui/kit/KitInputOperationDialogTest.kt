package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitInputOperationDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitInputOperationDialogTest {
    @Test
    fun `B-ANDR-078 operation validates after submit and keeps text through a failed creation`() =
        runComposeUiTest {
            val submissions = mutableListOf<String>()
            var busy by mutableStateOf(false)
            var error by mutableStateOf<String?>(null)
            var closed = 0
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    KitSheetHost {
                        KitInputOperationDialog(
                            true,
                            "Create a new folder",
                            "Folder name",
                            "Create",
                            "",
                            onSubmit = {
                                submissions += it
                                busy = true
                            },
                            onDismissRequest = { closed++ },
                            validate = { if (it.isBlank()) "Enter a folder name." else null },
                            working = busy,
                            error = error,
                            hint = "my-app",
                            helper = "Made on this phone and opened as the project.",
                        )
                    }
                }
            }
            onNodeWithText("Enter a folder name.").assertDoesNotExist()
            onNodeWithText("Create").performClick()
            onNodeWithText("Enter a folder name.").assertExists()
            assertEquals(emptyList(), submissions)
            val field = onNode(hasSetTextAction())
            field.assertIsFocused()
            field.performTextInput("loyalty-app")
            field.performImeAction()
            assertEquals(listOf("loyalty-app"), submissions)
            onNodeWithText("Cancel").assertIsNotEnabled()
            field.assertIsNotEnabled()
            runOnIdle {
                busy = false
                error = "The folder could not be created. Try again."
            }
            field.assertTextContains("loyalty-app")
            onNodeWithText("The folder could not be created. Try again.").assertExists()
            onNodeWithText("Cancel").performClick()
            assertEquals(1, closed)
        }

    @Test
    fun `B-ANDR-078 supplied project suggestion submits without requiring an edit`() =
        runComposeUiTest {
            val submissions = mutableListOf<String>()
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    KitSheetHost {
                        KitInputOperationDialog(
                            true,
                            "Create a new folder",
                            "Folder name",
                            "Create",
                            "my-app",
                            onSubmit = { submissions += it },
                            onDismissRequest = {},
                        )
                    }
                }
            }
            onNodeWithText("Create").performClick()
            assertEquals(listOf("my-app"), submissions)
        }
}
