package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.*
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SourceSwitchTest {
    @Test fun `B04 always shown source has an explanation instead of an actionable switch`() = kitUiTest {
        var flips = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSwitchRow("Main server", checked = true, locked = true, supporting = "Current connection",
                    lockedLabel = "Always shown", onCheckedChange = { flips++ })
            }
        }
        onNodeWithTag("kit-switch-locked").assertIsNotEnabled().assertHasNoClickAction()
        onNodeWithText("Always shown", useUnmergedTree = true).assertExists()
        onNodeWithText("Current connection", useUnmergedTree = true).assertExists()
        assertEquals(0, flips)
    }
}
