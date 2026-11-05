@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.ui.kit.rb.o49

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.text.kitLocalTime
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

class RowMultilineTest {
    @Test fun `timestamp text forwards both host instants to the kit platform formatter`() = runComposeUiTest {
        val platform = object : KitPlatform by DesktopKitPlatform() {
            override fun messageTime(createdMs: Long, nowMs: Long): String? =
                if (createdMs == 1000L && nowMs == 2000L) "formatted time" else null
        }
        setContent { KitTheme(platform = platform, localeTag = "en", stillFrame = true) {
            kitLocalTime(1000, 2000)?.let { KitText(it) }
        } }
        onNodeWithText("formatted time").assertExists()
    }
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
