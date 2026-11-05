@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o48

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.status.*
import kotlin.test.*

class BackgroundKitTest {
    @Test
    fun `long switch cost remains spoken and a custom status mark preserves its words`() =
        runComposeUiTest {
            var on = false
            val cost =
                "Updates runs while the app is closed. Uses more battery and shows a persistent notification."
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column {
                        KitSwitchRow(
                            "Stay connected",
                            false,
                            supportingMaxLines = 4,
                            leading = KitRowLeading.Glyph(KitIcons.sync),
                            supporting = cost,
                            onCheckedChange = { on = it },
                        )
                        KitStatusLine(
                            KitStatus(
                                KitStatusKind.AppStopped,
                                "Paused by Android",
                                icon = KitIcons.batteryWarning,
                            )
                        )
                    }
                }
            }
            onNodeWithText(cost).assertExists()
            onNodeWithText("Stay connected").performClick()
            assertTrue(on)
            onNodeWithText("Paused by Android").assertExists()
        }
}
