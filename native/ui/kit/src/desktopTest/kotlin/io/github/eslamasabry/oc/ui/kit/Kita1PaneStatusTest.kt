package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class Kita1PaneStatusTest {
    @Test
    fun `B-KIT-035 finding 5 sibling panes share status priority override and removal`() =
        runComposeUiTest {
            val detailVisible = mutableStateOf(true)
            val conditions = mutableStateOf(emptyList<KitStatus>())
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    FakeKitPlatform(),
                    "en",
                    stillFrame = true,
                ) {
                    KitStatusScope(conditions.value) {
                        InWindow(1280, 800) {
                            KitPaneScreen(
                                list = { KitText("List") },
                                status = KitStatus(KitStatusKind.Update, "Update ready"),
                                detail = {
                                    if (detailVisible.value) {
                                        KitScreen(
                                            page = true,
                                            status = KitStatus(KitStatusKind.Work, "Working"),
                                        ) {
                                            KitText("Detail")
                                        }
                                    }
                                },
                                emptyDetail = {},
                            )
                        }
                    }
                }
            }
            onAllNodesWithTag("kit-status-line").assertCountEquals(1)
            onNodeWithText("Working").assertExists()
            onNodeWithText("Update ready").assertDoesNotExist()
            conditions.value = listOf(KitStatus(KitStatusKind.Connection, "Connecting"))
            waitForIdle()
            onAllNodesWithTag("kit-status-line").assertCountEquals(1)
            onNodeWithText("Connecting").assertExists()
            onNodeWithText("Working").assertDoesNotExist()
            conditions.value = emptyList()
            detailVisible.value = false
            waitForIdle()
            onAllNodesWithTag("kit-status-line").assertCountEquals(1)
            onNodeWithText("Update ready").assertExists()
            onNodeWithText("Working").assertDoesNotExist()
        }
}
