@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.servers.rb.o45

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.servers.ui.HostSetupContentSheet
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals

class HostSetupSheetTest {
    /**
     * What the app's window root provides (App.kt, 0d38a8abc): the redactor the command blocks draw through and the
     * modal layer an unframed KitScreen's sheet opens in.
     */
    @Composable
    private fun WindowRoot(content: @Composable () -> Unit) =
        KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                KitSheetHost { KitScreen (edges = KitScreenEdges.Bleed) { content() } }
            }
        }

    @Test
    fun `host commands and walkthrough share the owning sheet scroll`() = runComposeUiTest {
        var opened = 0
        setContent {
            WindowRoot {
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
                WindowRoot {
                    HostSetupContentSheet(
                        visible = true,
                        onDismiss = {},
                        supportsUsbHostBridge = false,
                    )
                }
            }
            onNodeWithTag("host-command-install").assertExists()
            onNodeWithTag("host-command-adb-reverse").assertDoesNotExist()
            onNodeWithText("Full walkthrough (opens in browser)").assertDoesNotExist()
        }
}
