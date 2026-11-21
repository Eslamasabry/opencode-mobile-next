package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPaneColumn
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertTrue

/** Big screens: a step's status in words ("Waiting for you") ends on the step list's right edge, as a time does. */
@OptIn(ExperimentalTestApi::class)
class StepWaitingEdgeTest {
    @Test
    fun `B-NAV-235 beside the list Waiting for you sits on the step list's right edge`() = runComposeUiTest {
        setContent {
            ChatTestTheme {
                CompositionLocalProvider(LocalKitRedactor provides Redactor(), LocalKitPaneColumn provides 900.dp) {
                    Box(Modifier.width(900.dp).testTag("column")) {
                        KitStepList {
                            KitToolRow(
                                KitToolView(
                                    "edit", KitToolKind.Edit, "Edit", "a.txt", null, KitToolStatus.WaitingForYou, null,
                                    null, null, "Waiting for you",
                                ),
                                opened = false,
                                onToggle = {},
                            )
                        }
                    }
                }
            }
        }
        val column = onNodeWithTag("column").getBoundsInRoot()
        val words = onNodeWithText("Waiting for you", useUnmergedTree = true).getBoundsInRoot()
        val gap = (column.right - words.right).value
        assertTrue(gap <= 40f, "the words end $gap dp short of the list's right edge (column ${column.right}, words ${words.right})")
    }
}
