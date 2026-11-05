@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.servers.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.servers.ui.HostSetupContentSheet
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class HostSetupSheetTest {
    @Test
    fun `host commands and walkthrough share the owning sheet scroll`() = runComposeUiTest {
        var opened = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitScreen {
                    HostSetupContentSheet(
                        visible = true,
                        onDismiss = {},
                        serverName = "Laptop",
                        port = 8443,
                        supportsUsbHostBridge = true,
                        onGuide = { opened++ },
                    )
                }
            }
        }
        onNodeWithText("These commands run on Laptop's computer; copy each into a terminal there.")
            .assertExists()
        onNodeWithTag("host-command-install").assertExists()
        onNodeWithTag("host-command-adb-reverse").performScrollTo().assertIsDisplayed()
        onNodeWithText("Full walkthrough (opens in browser)").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `host instructions omit USB and guide when their capabilities are absent`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    KitScreen {
                        HostSetupContentSheet(
                            visible = true,
                            onDismiss = {},
                            supportsUsbHostBridge = false,
                        )
                    }
                }
            }
            onNodeWithTag("host-command-install").assertExists()
            onNodeWithTag("host-command-adb-reverse").assertDoesNotExist()
            onNodeWithText("Full walkthrough (opens in browser)").assertDoesNotExist()
        }
}
