package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Counterexamples from the KITA2 review; all input values are synthetic. */
@OptIn(ExperimentalTestApi::class)
class Kita2InputReviewTest {
    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = FakeKitPlatform(),
            localeTag = "en",
            content = content,
        )
    }

    @Test
    fun `B-KIT-016 KITA2-7 blank validation blocks button and IME submission`() = runComposeUiTest {
        val results = mutableListOf<String?>()
        setContent {
            Theme {
                KitSheetHost {
                    KitInputDialog(
                        true,
                        "Rename",
                        "Name",
                        "Save",
                        initial = "Original",
                        validate = { if (it.isBlank()) "Required" else null },
                        onResult = { results += it },
                    )
                }
            }
        }
        onNode(hasSetTextAction()).performTextReplacement("")
        onNodeWithText("Save").assertIsNotEnabled().performClick()
        onNode(hasSetTextAction()).performImeAction()
        assertTrue(results.isEmpty())
        onNode(hasSetTextAction()).performTextReplacement("Valid")
        onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf<String?>("Valid"), results)
    }

    @Test
    fun `B-KIT-016 KITA2-8 reopening after cancellation starts with the current target`() =
        runComposeUiTest {
            var visible by mutableStateOf(true)
            var initial by mutableStateOf("Target A")
            val results = mutableListOf<String?>()
            setContent {
                Theme {
                    KitSheetHost {
                        KitInputDialog(
                            visible,
                            "Rename",
                            "Name",
                            "Save",
                            initial = initial,
                            onResult = {
                                results += it
                                visible = false
                            },
                        )
                    }
                }
            }
            onNode(hasSetTextAction()).performTextReplacement("Abandoned edit")
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            runOnIdle { visible = true }
            onNodeWithText("Target A").assertExists()
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            runOnIdle {
                initial = "Target B"
                visible = true
            }
            onNodeWithText("Target B").assertExists()
            onNodeWithText("Save").performClick()
            assertEquals(listOf<String?>(null, null, null), results)
        }

    @Test
    fun `B-KIT-016 KITA2-8 different targets with the same initial words start fresh`() =
        runComposeUiTest {
            var target by mutableStateOf("target-a")
            setContent {
                Theme {
                    KitSheetHost {
                        KitInputDialog(
                            true,
                            "Rename",
                            "Name",
                            "Save",
                            initial = "Same title",
                            operationKey = target,
                            onResult = {},
                        )
                    }
                }
            }
            onNode(hasSetTextAction()).performTextReplacement("Previous operation")
            runOnIdle { target = "target-b" }
            onNodeWithText("Same title").assertExists()
        }

    @Test
    fun `B-KIT-016 KITA2-8 nonsecret edits survive recreation within an operation`() =
        runComposeUiTest {
            var registry = SaveableStateRegistry(null) { true }
            var mounted by mutableStateOf(true)
            setContent {
                Theme {
                    KitSheetHost {
                        if (mounted) {
                            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                                KitInputDialog(
                                    true,
                                    "Rename",
                                    "Name",
                                    "Save",
                                    initial = "Original",
                                    operationKey = "target-a",
                                    onResult = {},
                                )
                            }
                        }
                    }
                }
            }
            onNode(hasSetTextAction()).performTextReplacement("Preserved edit")
            runOnIdle {
                registry = SaveableStateRegistry(registry.performSave()) { true }
                mounted = false
            }
            waitForIdle()
            runOnIdle { mounted = true }
            onNodeWithText("Preserved edit").assertExists()
        }
}
