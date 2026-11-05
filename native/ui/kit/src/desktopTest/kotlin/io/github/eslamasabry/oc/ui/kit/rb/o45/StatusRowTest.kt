@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitStatusRow
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import kotlin.test.*

class StatusRowTest {
    @Test
    fun `marked row preserves its explanation and separate below action`() = runComposeUiTest {
        var opened = false
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitStatusRow(
                    "Sign in and connect",
                    KitStepState.Waiting,
                    "To do - OpenCode cannot check this",
                    action = KitAction("Open Tailscale", { opened = true }),
                )
            }
        }
        onNodeWithContentDescription("Sign in and connect, To do - OpenCode cannot check this")
            .assertExists()
        onNodeWithText("Open Tailscale").performClick()
        assertTrue(opened)
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }
}
