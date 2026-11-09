@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-11-09 (phone screenshot): the chat header's line read "new · This phone ·   Git": the short branch word
 * sat centred in its 48 dp target, so the dot hung off the project and a wide gap opened before "Git".
 */
class KitTopBarScopeSpacingTest {
    @Test
    fun `E20-S13 the scope line's dot sits evenly between the project and a short branch`() = runComposeUiTest {
        setContent {
            KitTheme(platform = FakeKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(360.dp)) {
                    KitTopBarScope("new", "This phone", "Files of new", onProject = {}, branch = "Git", onBranch = {})
                }
            }
        }
        waitForIdle()
        val project = onNodeWithText("new · This phone", useUnmergedTree = true).getBoundsInRoot()
        val dot = onNodeWithText("·", useUnmergedTree = true).getBoundsInRoot()
        val branch = onNodeWithText("Git", useUnmergedTree = true).getBoundsInRoot()
        val before = dot.left - project.right
        val after = branch.left - dot.right
        assertTrue(abs((after - before).value) <= 1f, "dot gaps: ${before} before it, ${after} after it")
    }
}
