@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.ui.kit.rb.o49

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

class RowMultilineTest {
    @Test fun `a custom neutral notice glyph keeps the state in readable words`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KitNotice("Cancellation is not confirmed", icon = KitIcons.warning)
        } }
        onNodeWithText("Cancellation is not confirmed").assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
    @Test fun `a requested multiline account status grows rather than truncating to one line`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            KitRow("Allowance could not be read from this computer", Modifier.width(180.dp), titleMaxLines = 3)
        } }
        onNodeWithTag("kit-row-title").assertHeightIsAtLeast(40.dp)
    }
}
