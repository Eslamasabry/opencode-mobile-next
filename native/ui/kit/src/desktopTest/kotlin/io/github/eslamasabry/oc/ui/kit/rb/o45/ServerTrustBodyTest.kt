@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitServerTrustBody
import kotlin.test.Test

class ServerTrustBodyTest {
    @Test
    fun `inspected origin and complete fingerprint stay visible in Arabic`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "ar", stillFrame = true) {
                KitServerTrustBody(
                    "https://computer.example:443",
                    "Review this certificate",
                    fingerprint = "ab".repeat(32),
                    fingerprintLabel = "Fingerprint",
                    validity = "Valid today",
                )
            }
        }
        onNodeWithText("https://computer.example:443").assertExists()
        onNodeWithText(List(32) { "AB" }.joinToString(":")).assertExists()
        onNodeWithText("Valid today").assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
