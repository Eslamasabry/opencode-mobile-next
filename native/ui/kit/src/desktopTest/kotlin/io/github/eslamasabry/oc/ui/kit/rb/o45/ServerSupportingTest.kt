@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.*
import kotlin.test.*

class ServerSupportingTest {
    @Test
    fun `attention and queue share one wrapping supporting paragraph and one row action`() =
        runComposeUiTest {
            var opened = false
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Box(Modifier.width(280.dp)) {
                        KitRow(
                            "Laptop",
                            supporting =
                                kitServerSupporting(
                                    "OpenCode 2",
                                    waiting = 1,
                                    running = 2,
                                    queued = 1,
                                ),
                            state = KitRowState(null, KitRowStateTone.NeedsYou),
                            supportingMaxLines = 2,
                            onClick = { opened = true },
                        )
                    }
                }
            }
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithTag("kit-row-supporting", useUnmergedTree = true)
                .assertTextEquals("Needs you · 2 working · 1 prompt waiting to send · OpenCode 2")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(2, layouts.single().lineCount)
            onNodeWithText("Laptop").performClick()
            assertTrue(opened)
            onAllNodes(hasClickAction()).assertCountEquals(1)
        }

    @Test
    fun `disabled rich row replaces all state words with its reason`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitRow(
                    "Laptop",
                    supporting = kitServerSupporting("OpenCode 2", connected = true),
                    enabled = false,
                    disabledReason = "Saving the server",
                    onClick = {},
                )
            }
        }
        onNodeWithText("Saving the server", useUnmergedTree = true).assertExists()
        onNodeWithText("Connected · OpenCode 2", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Laptop").assertIsNotEnabled()
    }
}
