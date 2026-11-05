@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals

class SwitchGlyphTest {
    @Test
    fun `a leading decorative glyph preserves one switch and one toggle action`() =
        runComposeUiTest {
            var changed: Boolean? = null
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    KitSwitchRow(
                        "Include sign-ins",
                        false,
                        leading = KitIcons.locked,
                        supporting = "Private export",
                    ) {
                        changed = it
                    }
                }
            }
            onNodeWithText("Include sign-ins").performClick()
            assertEquals(true, changed)
            onAllNodes(hasClickAction()).assertCountEquals(1)
        }
}
