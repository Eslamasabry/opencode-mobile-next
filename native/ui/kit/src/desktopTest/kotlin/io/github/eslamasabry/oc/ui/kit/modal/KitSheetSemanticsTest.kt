@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test

/**
 * B-042 (guard): a sheet's rows are in the accessibility tree, each its own node, while the page under it is not. The
 * arrangement is the app's: the shell's window host around the page, and the page's own host (a conversation's) inside
 * it, holding the sheet.
 */
class KitSheetSemanticsTest {
    @Test
    fun `B-042 an open sheet's rows are found in the accessibility tree and the page behind is not`() = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                Box(Modifier.size(400.dp, 800.dp)) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        KitSheetHost(Modifier.fillMaxSize()) {
                            KitText("Transcript line")
                            KitSheet(visible = true, onDismissRequest = {}, title = "Message timeline") {
                                KitRow("First message", onClick = {})
                                KitRow("Second message", onClick = {})
                                KitRow("Third message", onClick = {})
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        for (row in listOf("First message", "Second message", "Third message")) onAllNodesWithText(row).assertCountEquals(1)
        onNodeWithText("Transcript line").assertDoesNotExist()
    }
}
